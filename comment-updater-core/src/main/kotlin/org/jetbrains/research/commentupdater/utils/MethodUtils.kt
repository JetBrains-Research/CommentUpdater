package org.jetbrains.research.commentupdater.utils

import com.intellij.psi.PsiComment
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType

/**
 * Full Method name is qualified method name and list of param types (in declaration order)
 */
data class MethodNameWithParam(val name: String, val paramTypes: List<String>)

val PsiMethod.nameWithParams: MethodNameWithParam
    get() = MethodNameWithParam(
        name = this.qualifiedName,
        paramTypes = this.parameters.map { param -> (param.type as PsiType).canonicalText }
    )

val PsiMethod.qualifiedName: String
    get() = (this.containingClass?.qualifiedName ?: "") + "." + this.name

val PsiMethod.textWithoutDoc: String
    get() {
        return this.children.filter { it !is PsiComment }.joinToString(separator = "", transform = { it.text })
    }
