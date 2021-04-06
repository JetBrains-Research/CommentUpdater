package org.jetbrains.research.commentupdater

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder

import com.intellij.openapi.diagnostic.Logger;

import com.intellij.psi.*
import com.intellij.psi.javadoc.PsiDocComment


class CodeCommentInspection : AbstractBaseJavaLocalInspectionTool() {
    private val LOG: Logger = Logger.getInstance("#org.jetbrains.research.commentupdater.CodeCommentInspection")


    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        //TODO: implement the visitor, check the methods that have comments, process the code and the related comment.
        return object : JavaElementVisitor() {

            override fun visitDocComment(comment: PsiDocComment?) {
                if (comment != null) {

                    LOG.info("Found comment" + comment.text)
                    if (comment.owner is PsiMethod) {
                        LOG.info("That's also a method comment for:" + (comment.owner as PsiMethod).name)
                        LOG.info(CodeCommentTokenizer.subTokenizeCode((comment.owner as PsiMethod).text).toString())
                    }
                }
                CodeCommentTokenizer.createSample("", "")
                super.visitDocComment(comment)
            }

            override fun visitMethod(method: PsiMethod?) {
                LOG.info(CodeFeaturesExtractor.extractMethodFeatures(method!!, false).toString())
                LOG.info(CodeFeaturesExtractor.extractMethodFeatures(method!!, true).toString())
                super.visitMethod(method)
            }
        }
    }

}