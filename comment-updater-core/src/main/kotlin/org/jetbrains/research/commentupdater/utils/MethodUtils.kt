package org.jetbrains.research.commentupdater.utils

import com.intellij.psi.PsiComment
import com.intellij.psi.PsiMethod

val PsiMethod.qualifiedName: String
    get() = (this.containingClass?.qualifiedName ?: "") + "." + this.name

val PsiMethod.textWithoutDoc: String
    get() {
        return this.children.filter { it !is PsiComment }.joinToString(separator = "", transform = { it.text })
    }
