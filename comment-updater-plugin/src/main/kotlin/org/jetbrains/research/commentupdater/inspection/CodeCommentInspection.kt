package org.jetbrains.research.commentupdater.inspection

import CommentUpdaterBundle
import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.psi.JavaElementVisitor
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiMethod
import com.intellij.psi.javadoc.PsiDocComment
import gr.uom.java.xmi.diff.RenameOperationRefactoring
import org.jetbrains.research.commentupdater.JITDetector
import org.jetbrains.research.commentupdater.processors.MethodChangesExtractor
import org.jetbrains.research.commentupdater.processors.RefactoringExtractor
import org.jetbrains.research.commentupdater.utils.qualifiedName
import org.refactoringminer.api.Refactoring

class CodeCommentInspection : AbstractBaseJavaLocalInspectionTool() {

    private val logger: Logger =
        Logger.getInstance(javaClass)
    val detector = JITDetector()

    var currentFile = ""
    var currentChanges: Change? = null
    var currentMethodsRefactorings = hashMapOf<String, MutableList<Refactoring>>()

    override fun inspectionStarted(session: LocalInspectionToolSession, isOnTheFly: Boolean) {
        logger.info("[CommentUpdater] Inspection started")

        // Extract changes
        currentFile = session.file.name
        val changeListManager = ChangeListManager.getInstance(session.file.project)
        currentChanges = changeListManager.getChange(session.file.virtualFile)

        // Extract Refactorings
        currentChanges?.let {
            val refactorings = RefactoringExtractor.extract(it)
            currentMethodsRefactorings = RefactoringExtractor.methodsToRefactoringTypes(refactorings)
        }

        logger.info("[CommentUpdater] File $currentFile, Refactorings: $currentMethodsRefactorings")

        super.inspectionStarted(session, isOnTheFly)
    }

    override fun inspectionFinished(session: LocalInspectionToolSession, problemsHolder: ProblemsHolder) {
        logger.info("[CommentUpdater] Inspection finished")
        super.inspectionFinished(session, problemsHolder)
    }

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : JavaElementVisitor() {

            override fun visitDocComment(comment: PsiDocComment?) {
                if (comment != null) {
                    if (comment.owner is PsiMethod) {

                        val newMethod = (comment.owner as PsiMethod)
                        val newName = newMethod.qualifiedName

                        logger.info("[CommentUpdater] Processing comment in method $newName in file $currentFile.")

                        val oldName = currentMethodsRefactorings.getOrElse(
                            newName
                        ) { null }?.filterIsInstance<RenameOperationRefactoring>()?.getOrNull(0)?.let {
                            it.originalOperation.className + "." + it.originalOperation.name
                        }
                        val oldMethod = (oldName ?: newName).let { name ->
                            currentChanges?.let { change ->
                                val oldMethod = MethodChangesExtractor.getOldMethod(newMethod, change, name)
                                logger.info("[CommentUpdater] Found old method with name: $name")
                                oldMethod
                            }
                        }
                        oldMethod?.let {

                            val prediction = detector.predict(oldMethod, newMethod)

                            val hasInconsistency = if (prediction == null) {
                                logger.info("[CommentUpdater] Prediction error!")
                                false
                            } else {
                                prediction
                            }

                            if (hasInconsistency) {
                                holder.registerProblem(
                                    comment, CommentUpdaterBundle.message("description.template")
                                )
                            }

                            logger.info("[CommentUpdater] Predicted $hasInconsistency")
                        }
                    }
                }
                super.visitDocComment(comment)
            }
        }
    }
}
