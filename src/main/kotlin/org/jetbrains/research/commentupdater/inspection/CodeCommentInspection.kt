package org.jetbrains.research.commentupdater.inspection

import com.intellij.codeInspection.*
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.psi.*
import com.intellij.psi.javadoc.PsiDocComment
import gr.uom.java.xmi.diff.*
import org.jetbrains.research.commentupdater.CodeCommentDiffs
import org.jetbrains.research.commentupdater.JITDetector
import org.jetbrains.research.commentupdater.processors.CodeCommentTokenizer
import org.jetbrains.research.commentupdater.processors.MethodChangesExtractor
import org.jetbrains.research.commentupdater.processors.RefactoringExtractor
import org.refactoringminer.api.Refactoring
import org.refactoringminer.api.RefactoringType
import kotlin.io.path.ExperimentalPathApi

class CodeCommentInspection : AbstractBaseJavaLocalInspectionTool() {

    private val LOG: Logger =
        Logger.getInstance("#org.jetbrains.research.commentupdater.inspection.CodeCommentInspection")
    val detector = JITDetector()

    var currentFile = ""
    var currentChanges: Change? = null
    var currentMethodsRefactorings = hashMapOf<String, MutableList<Refactoring>>()

    val DESCRIPTION_TEMPLATE = "Inconsistent comment found"

    override fun inspectionStarted(session: LocalInspectionToolSession, isOnTheFly: Boolean) {
        LOG.info("Inspection started")

        // Extract changes

        currentFile = session.file.name
        val changeListManager = ChangeListManager.getInstance(session.file.project)
        currentChanges = changeListManager.getChange(session.file.virtualFile)

        // Extract Refactorings
        currentChanges?.let {
            val refactorings = RefactoringExtractor.extract(it)
            currentMethodsRefactorings = RefactoringExtractor.methodsToRefactoringTypes(refactorings)
        }

        LOG.info("File ${currentFile}, Refactorings: $currentMethodsRefactorings")

        super.inspectionStarted(session, isOnTheFly)
    }

    override fun inspectionFinished(session: LocalInspectionToolSession, problemsHolder: ProblemsHolder) {
        LOG.info("Inspection finished")
        super.inspectionFinished(session, problemsHolder)
    }


    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : JavaElementVisitor() {

            override fun visitDocComment(comment: PsiDocComment?) {
                LOG.info("I am visiting DocComment inside $currentFile")
                if (comment != null) {
                    LOG.info("Found comment" + comment.text)
                    if (comment.owner is PsiMethod) {

                        LOG.info("Method for that comment:" + (comment.owner as PsiMethod).name)

                        val newMethod = (comment.owner as PsiMethod)
                        val newName = MethodChangesExtractor.extractFullyQualifiedName(newMethod)
                        val oldName = currentMethodsRefactorings.getOrElse(
                            newName
                        ) { null }?.filterIsInstance<RenameOperationRefactoring>()?.getOrNull(0)?.let {
                            it.originalOperation.className + "." + it.originalOperation.name
                        }
                        val oldMethod = (oldName ?: newName).let { name ->
                            currentChanges?.let { change ->
                                val oldMethod = MethodChangesExtractor.getOldMethod(newMethod, change, name)
                                LOG.info("NewMethod: $newName oldMethod: $name")
                                oldMethod
                            }
                        }
                        oldMethod?.let {

                            // todo: compare whether oldMethod != newMethod
                            val prediction = detector.predict(oldMethod, newMethod)

                            val inconsistency = if (prediction == null) {
                                LOG.info("Prediction error!")
                                false
                            } else {
                                prediction
                            }

                            if (inconsistency) {
                                holder.registerProblem(
                                    comment, DESCRIPTION_TEMPLATE
                                )
                            }

                            LOG.info("Predicted ${inconsistency}")
                        }
                    }
                }
                super.visitDocComment(comment)
            }
        }
    }
}