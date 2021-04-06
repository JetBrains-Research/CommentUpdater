package org.jetbrains.research.commentupdater

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.psi.*
import com.intellij.psi.javadoc.PsiDocComment
import com.intellij.psi.util.elementType


object MethodChangesExtractor {
    private val LOG: Logger = Logger.getInstance("#org.jetbrains.research.commentupdater.MethodChangesExtractor")

    fun extract(method: PsiMethod): PsiMethod? {
        val methodFullName = MethodFeaturesExtractor.extractFullyQualifiedName(method)
        val psiFile = method.containingFile
        val changeListManager = ChangeListManager.getInstance(psiFile.project)
        val change: Change = changeListManager.getChange(psiFile.virtualFile) ?: return null
        val beforeRevision = change.beforeRevision ?: return null
        try {
            var oldMethod: PsiMethod? = null
            val content = beforeRevision.content ?: return null
            PsiFileFactory.getInstance(psiFile.project).createFileFromText(
                "tmp",
                JavaFileType.INSTANCE,
                content
            ).accept(
                object : JavaRecursiveElementVisitor() {
                    override fun visitDocComment(comment: PsiDocComment?) {
                        if (comment != null) {
                            if (comment.owner is PsiMethod) {
                                val name = MethodFeaturesExtractor.extractFullyQualifiedName(comment.owner as PsiMethod)
                                if (name == methodFullName)
                                    oldMethod = comment.owner as PsiMethod
                            }
                        }
                        super.visitDocComment(comment)
                    }
                }

            )
            return oldMethod
        } catch (e: VcsException) {
            LOG.error("[ACP] Failed to get a file's content from the last revision.", e.message)
        }
        return null
    }

}