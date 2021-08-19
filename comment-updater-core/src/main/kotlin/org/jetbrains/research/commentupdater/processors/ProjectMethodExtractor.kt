package org.jetbrains.research.commentupdater.processors

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.research.commentupdater.utils.MethodNameWithParam
import org.jetbrains.research.commentupdater.utils.RefactoringUtils
import org.refactoringminer.api.Refactoring

object ProjectMethodExtractor {
    /**
     * @return: List of pairs oldMethod to newMethod
     */
    fun extractChangedMethods(
        project: Project,
        change: Change,
        refactorings: List<Refactoring>
    ): MutableList<Pair<PsiMethod, PsiMethod>> {
        val before = change.beforeRevision?.content ?: return mutableListOf()
        val after = change.afterRevision?.content ?: return mutableListOf()

        val renameMapping = RefactoringUtils.extractFullNameChanges(refactorings)

        val changedMethodPairs = mutableListOf<Pair<PsiMethod, PsiMethod>>()

        val oldMethodsWithNames = extractMethodsWithFullNames(project, before)

        val newMethodsWithNames = extractMethodsWithFullNames(project, after)

        newMethodsWithNames.forEach {
            afterName, newMethod ->
            val beforeName = renameMapping.getOrElse(afterName) { afterName }
            oldMethodsWithNames.get(beforeName)?.let {
                oldMethod ->
                changedMethodPairs.add(oldMethod to newMethod)
            }
        }

        return changedMethodPairs
    }

    private fun extractMethodsWithFullNames(project: Project, content: String): Map<MethodNameWithParam, PsiMethod> {
        lateinit var psiFile: PsiFile
        lateinit var methodsWithNames: Map<MethodNameWithParam, PsiMethod>

        ApplicationManager.getApplication().runReadAction {
            psiFile = PsiFileFactory.getInstance(project).createFileFromText(
                "extractMethodsWithNamesFile",
                JavaFileType.INSTANCE,
                content
            )

            methodsWithNames = PsiTreeUtil.findChildrenOfType(psiFile, PsiMethod::class.java).filter {
                it.docComment != null
            }.associateBy {
                MethodNameWithParam(
                    name = ((it.containingClass?.qualifiedName ?: "") + "." + it.name),
                    paramTypes = it.typeParameterList?.typeParameters?.map { param -> param.qualifiedName ?: ""} ?: emptyList()
                )
            }
        }

        return methodsWithNames
    }
}
