package org.jetbrains.research.commentupdater.processors

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.psi.JavaRecursiveElementVisitor
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiMethod
import com.intellij.psi.javadoc.PsiDocComment
import org.jetbrains.research.commentupdater.CodeCommentDiffs
import org.jetbrains.research.commentupdater.utils.qualifiedName

object MethodChangesExtractor {
    private val LOG: Logger =
        Logger.getInstance(javaClass)

    fun checkMethodChanged(oldComment: String, oldCode: String, newComment: String, newCode: String): Boolean {
        val filterSubTokens = { subToken: String ->
            val hasLetters: Boolean = subToken.any { it.isLetter() }
            hasLetters
        }

        val oldSubTokens = CodeCommentTokenizer.subTokenizeCode(oldCode).filter(filterSubTokens)
        val newSubTokens = CodeCommentTokenizer.subTokenizeCode(newCode).filter(filterSubTokens)

        val commentSubTokens = CodeCommentTokenizer.subTokenizeComment(newComment).filter(filterSubTokens)

        val oldCommentSubTokens = CodeCommentTokenizer.subTokenizeComment(oldComment).filter(filterSubTokens)

        val (_, _, diffCommands) = CodeCommentDiffs.computeCodeDiffs(oldSubTokens, newSubTokens)

        val commentSpans = CodeCommentDiffs.computeMinimalCommentDiffs(oldCommentSubTokens, commentSubTokens)

        val changedCodeLen = diffCommands.count {
            it != CodeCommentDiffs.KEEP
        }

        val changeCommentLen = commentSpans.size

        // If code and comment are unchanged, we aren't interested
        if (changedCodeLen == 0 && changeCommentLen == 0) {
            return false
        }
        return true
    }

    fun getOldMethod(method: PsiMethod, change: Change, oldName: String): PsiMethod? {
        val psiFile = method.containingFile
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
                        if (comment != null && comment.owner is PsiMethod) {
                            val name = (comment.owner as PsiMethod).qualifiedName
                            if (name == oldName)
                                oldMethod = comment.owner as PsiMethod
                        }
                        super.visitDocComment(comment)
                    }
                }

            )
            return oldMethod
        } catch (e: VcsException) {
            LOG.error("[CommentUpdater] Failed to get a file's content from the last revision.", e.message)
        }
        return null
    }


    fun getOldMethod(method: PsiMethod): PsiMethod? {
        val methodFullName = method.qualifiedName
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
                                val name = (comment.owner as PsiMethod).qualifiedName
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
            LOG.error("[CommentUpdater] Failed to get a file's content from the last revision.", e.message)
        }
        return null
    }

}