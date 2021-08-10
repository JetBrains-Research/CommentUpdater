package org.jetbrains.research.commentupdater.processors

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil
import gr.uom.java.xmi.diff.MoveOperationRefactoring
import org.jetbrains.research.commentupdater.dataset.MethodUpdateType
import org.jetbrains.research.commentupdater.utils.RefactoringUtils
import org.jetbrains.research.commentupdater.utils.qualifiedName
import org.refactoringminer.api.Refactoring
import org.refactoringminer.api.RefactoringType

object ProjectMethodExtractor {
    /**
     * @return: List of pairs oldMethod to newMethod
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

        val renameMapping = RefactoringUtils.extractNameChanges(fileRefactorings)

        val changedMethods = mutableListOf<Pair<PsiMethod, MethodUpdateType>>()

        val oldNamesToMethods = extractNamesToMethods(project, before, statisticContext)

        val newMethods = extractMethodsWithNames(project, after)

        newMethods.forEach { (afterName, newMethod) ->
            val beforeName = if (renameMapping.containsKey(afterName)) {
                renameMapping[afterName]
            } else {
                afterName
            }

            if (!oldNamesToMethods.containsKey(beforeName)) {
                var isNew = true

                allRefactorings.filter { it.refactoringType == RefactoringType.MOVE_OPERATION || it.refactoringType == RefactoringType.MOVE_AND_RENAME_OPERATION }
                    .forEach { ref ->
                        val refactoring = (ref as MoveOperationRefactoring)
                        val newFullName = refactoring.movedOperation.className + "." + refactoring.movedOperation.name

                        var newMethodName = ""
                        ApplicationManager.getApplication().runReadAction {
                            newMethodName = newMethod.qualifiedName
                        }

                        if (newFullName == newMethodName) {
                            isNew = false
                        }
                    }

                changedMethods.add(
                    newMethod to if (isNew) {
                        MethodUpdateType.ADD
                    } else {
                        MethodUpdateType.MOVE
                    }
                )
            }

            oldNamesToMethods[beforeName]?.let { oldMethod ->
                changedMethods.add(oldMethod to MethodUpdateType.CHANGE)
            }
        }
        return changedMethods
    }

    private fun extractMethodsWithNames(project: Project, content: String): List<Pair<String, PsiMethod>> {
        lateinit var psiFile: PsiFile
        lateinit var methodsWithNames: List<Pair<String, PsiMethod>>

        ApplicationManager.getApplication().runReadAction {
            psiFile = PsiFileFactory.getInstance(project).createFileFromText(
                "extractMethodsWithNamesFile",
                JavaFileType.INSTANCE,
                content
            )

            methodsWithNames = PsiTreeUtil.findChildrenOfType(psiFile, PsiMethod::class.java).map {
                ((it.containingClass?.qualifiedName ?: "") + "." + it.name) to it
            }
        }

        return methodsWithNames
    }

    private fun extractNamesToMethods(
        project: Project,
        content: String,
        statisticContext: HashMap<String, Int> = hashMapOf()
    ): HashMap<String, PsiMethod> {
        lateinit var psiFile: PsiFile
        lateinit var namesToMethods: HashMap<String, PsiMethod>
        var numOfMethods = 0
        var numOfDocMethods = 0

        ApplicationManager.getApplication().runReadAction {
            psiFile = PsiFileFactory.getInstance(project).createFileFromText(
                "extractNamesToMethodsFile",
                JavaFileType.INSTANCE,
                content
            )

            val methods = PsiTreeUtil.findChildrenOfType(psiFile, PsiMethod::class.java)
            numOfMethods = methods.size

            numOfDocMethods = methods.filter {
                it.docComment != null
            }.size

            namesToMethods = hashMapOf(*methods.map {
                ((it.containingClass?.qualifiedName ?: "") + "." + it.name) to it
            }.toTypedArray())
        }

        statisticContext["numOfMethods"] = numOfMethods
        statisticContext["numOfDocMethods"] = numOfDocMethods

        return namesToMethods
    }
}
