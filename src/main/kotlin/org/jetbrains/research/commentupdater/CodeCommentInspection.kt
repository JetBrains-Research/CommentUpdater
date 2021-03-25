package org.jetbrains.research.commentupdater

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor

class CodeCommentInspection : AbstractBaseJavaLocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        //TODO: implement the visitor, check the methods that have comments, process the code and the related comment.
        return super.buildVisitor(holder, isOnTheFly)
    }
}