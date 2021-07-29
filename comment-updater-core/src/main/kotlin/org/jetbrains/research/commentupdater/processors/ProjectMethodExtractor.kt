package org.jetbrains.research.commentupdater.processors

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil
import org.apache.commons.lang.ObjectUtils
import org.jetbrains.research.commentupdater.utils.RefactoringUtils
import org.refactoringminer.api.Refactoring

object ProjectMethodExtractor {
    /**
     * @return: List of pairs oldMethod to newMethod
     */
    fun extractChangedMethods(
        project: Project,
        change: Change,
        refactorings: List<Refactoring>,
        statisticContext: HashMap<String, Int> = hashMapOf()
    ): MutableList<Triple<PsiMethod, PsiMethod, Boolean>> {
        val before = change.beforeRevision?.content ?: ""
        val after = change.afterRevision?.content ?: return mutableListOf()

        val renameMapping = RefactoringUtils.extractNameChanges(refactorings)

        val changedMethodPairs = mutableListOf<Triple<PsiMethod, PsiMethod, Boolean>>()

        val oldNamesToMethods = extractNamesToMethods(project, before, statisticContext)

        val newMethods = extractMethodsWithNames(project, after)

        newMethods.forEach { (afterName, newMethod) ->
            val beforeName = if (renameMapping.containsKey(afterName)) {
                renameMapping[afterName]
            } else {
                afterName
            }

            if (!oldNamesToMethods.containsKey(beforeName)) {
                changedMethodPairs.add(Triple(newMethod, newMethod, true))
            }

            oldNamesToMethods[beforeName]?.let { oldMethod ->
                changedMethodPairs.add(Triple(oldMethod, newMethod, false))
            }

        }
        return changedMethodPairs
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
        var numOfMethods: Int = 0
        var numOfDocMethods: Int = 0

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