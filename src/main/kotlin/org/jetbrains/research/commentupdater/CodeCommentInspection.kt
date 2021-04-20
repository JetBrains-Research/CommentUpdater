package org.jetbrains.research.commentupdater

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder

import com.intellij.openapi.diagnostic.Logger;

import com.intellij.psi.*
import com.intellij.psi.javadoc.PsiDocComment


class CodeCommentInspection : AbstractBaseJavaLocalInspectionTool() {
    private val LOG: Logger = Logger.getInstance("#org.jetbrains.research.commentupdater.CodeCommentInspection")


    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : JavaElementVisitor() {

            override fun visitDocComment(comment: PsiDocComment?) {
                if (comment != null) {

                    LOG.info("Found comment" + comment.text)
                    if (comment.owner is PsiMethod) {
                        LOG.info("That's also a method comment for:" + (comment.owner as PsiMethod).name)
                        LOG.info(CodeCommentTokenizer.subTokenizeCode((comment.owner as PsiMethod).text).toString())
                        LOG.info(MethodChangesExtractor.getOldMethod(comment.owner as PsiMethod)?.text ?: "")
                    }
                }
                super.visitDocComment(comment)
            }
        }
    }

}