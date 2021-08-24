package org.jetbrains.research.commentupdater.processors

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier.ABSTRACT
import com.intellij.psi.util.PsiTreeUtil
import gr.uom.java.xmi.diff.MoveOperationRefactoring
import org.jetbrains.research.commentupdater.dataset.MethodUpdateType
import org.jetbrains.research.commentupdater.utils.MethodNameWithParam
import org.jetbrains.research.commentupdater.utils.RefactoringUtils
import org.jetbrains.research.commentupdater.utils.qualifiedName
import org.jetbrains.research.commentupdater.utils.textWithoutDoc
import org.jetbrains.research.commentupdater.utils.nameWithParams
import org.refactoringminer.api.Refactoring
import org.refactoringminer.api.RefactoringType

object ProjectMethodExtractor {

    private fun checkMethod(method: PsiMethod): Boolean {
        var isCorrectMethod = true
        ApplicationManager.getApplication().runReadAction {
            if (method.isConstructor) isCorrectMethod = false
            if (method.modifierList.hasModifierProperty(ABSTRACT)) isCorrectMethod = false
            if (method.hasAnnotation("Override")) isCorrectMethod = false
            if (method.body == null) isCorrectMethod = false
            else if (method.body!!.isEmpty) isCorrectMethod = false
        }
        return isCorrectMethod
    }

    /**
     * @return: List of pairs newMethod to update type
     */
    fun extractChangedMethods(
        project: Project,
        change: Change,
        allRefactorings: List<Refactoring>,
        fileRefactorings: List<Refactoring>,
        statisticContext: HashMap<String, Int> = hashMapOf()
    ): MutableList<Pair<PsiMethod, MethodUpdateType>> {
        val before = change.beforeRevision?.content ?: ""
        val after = change.afterRevision?.content ?: return mutableListOf()

        val renameMapping = RefactoringUtils.extractFullNameChanges(fileRefactorings)

        val classRenameMapping = RefactoringUtils.extractClassRenamesAndMoves(allRefactorings)

        val changedMethods = mutableListOf<Pair<PsiMethod, MethodUpdateType>>()

        val oldMethodsWithNames = extractMethodsWithFullNames(project, before, statisticContext)

        val newMethodsWithNames = extractMethodsWithFullNames(project, after)

        newMethodsWithNames.forEach { (afterName, newMethod) ->
            val beforeName = if (renameMapping.containsKey(afterName)) {
                renameMapping[afterName]
            } else {
                afterName
            }
            if (classRenameMapping.any { (_, newClassName) ->
                    beforeName?.name?.startsWith(newClassName) == true
                }) {
                changedMethods.add(newMethod to MethodUpdateType.MOVE)
            } else if (!oldMethodsWithNames.containsKey(beforeName)) {
                val isNew =
                    allRefactorings.filter {
                        it.refactoringType == RefactoringType.MOVE_OPERATION ||
                                it.refactoringType == RefactoringType.MOVE_AND_RENAME_OPERATION
                    }
                        .all { ref ->
                            val refactoring = (ref as MoveOperationRefactoring)
                            val newFullName =
                                refactoring.movedOperation.className + "." + refactoring.movedOperation.name

                            var newMethodName = ""
                            ApplicationManager.getApplication().runReadAction {
                                newMethodName = newMethod.qualifiedName
                            }

                            newFullName != newMethodName
                        }

                changedMethods.add(
                    newMethod to if (isNew) {
                        MethodUpdateType.ADD
                    } else {
                        MethodUpdateType.MOVE
                    }
                )
            } else {
                val oldMethod = oldMethodsWithNames[beforeName]

                lateinit var newCode: String
                lateinit var oldCode: String

                ApplicationManager.getApplication().runReadAction {
                    newCode = newMethod.textWithoutDoc
                    oldCode = oldMethod!!.textWithoutDoc
                }

                if (oldCode.trim() != newCode.trim() && MethodChangesExtractor.checkMethodChanged(
                        oldCode = oldCode,
                        newCode = newCode,
                        oldComment = "",
                        newComment = ""
                    )
                ) {
                    changedMethods.add(newMethod to MethodUpdateType.CHANGE)
                }
            }
        }
        val filteredUpdatedMethods = changedMethods.filter {
            checkMethod(it.first)
        }
        return filteredUpdatedMethods.toMutableList()
    }

    private fun extractMethodsWithFullNames(
        project: Project,
        content: String,
        statisticContext: HashMap<String, Int> = hashMapOf()
    ): Map<MethodNameWithParam, PsiMethod> {
        lateinit var psiFile: PsiFile
        lateinit var methodsWithNames: Map<MethodNameWithParam, PsiMethod>
        var numOfMethods = 0
        var numOfDocMethods = 0

        ApplicationManager.getApplication().runReadAction {
            psiFile = PsiFileFactory.getInstance(project).createFileFromText(
                "extractMethodsWithNamesFile",
                JavaFileType.INSTANCE,
                content
            )

            val fileMethods = PsiTreeUtil.findChildrenOfType(psiFile, PsiMethod::class.java)
            methodsWithNames = fileMethods.associateBy {
                it.nameWithParams
            }

            numOfMethods = fileMethods.size
            numOfDocMethods = fileMethods.filter { it.docComment != null }.size
        }

        statisticContext["numOfMethods"] = numOfMethods
        statisticContext["numOfDocMethods"] = numOfDocMethods

        return methodsWithNames
    }
}
