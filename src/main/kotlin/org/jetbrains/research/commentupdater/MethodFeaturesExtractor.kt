package org.jetbrains.research.commentupdater

import com.intellij.psi.PsiComment
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiReturnStatement
import com.intellij.psi.util.PsiTreeUtil


object MethodFeaturesExtractor{
    fun extractFullyQualifiedName(method: PsiMethod): String {
        val classes = mutableListOf<String>()
        classes.add(method.name)
        var upperClass = method.containingClass
        while (upperClass != null) {
            classes.add(upperClass.name ?: "")
            upperClass = upperClass.containingClass
        }
        return classes.reversed().joinToString(separator = ".")
    }

    /**
     * Remove Comments from Method text
     */
    fun extractCode(method: PsiMethod): String {
        return method.children.filter { it !is PsiComment }.joinToString (separator = "", transform={ it.text })
    }

    fun extractArguments(method: PsiMethod): List<Pair<String, String>> {
        return method.parameterList.parameters.map {
            it.type.presentableText to it.name
        }
    }

    fun extractReturnType(method: PsiMethod): String {
        return method.returnType?.presentableText ?: ""
    }

    fun extractReturnStatements(method: PsiMethod): List<String> {
        return PsiTreeUtil.findChildrenOfAnyType(method, PsiReturnStatement::class.java)
            .map {
                it.returnValue?.text ?: ""
            }.filter { it != "" }
    }

    fun extractMethodFeatures(method: PsiMethod, subTokenize: Boolean): HashMap<String, List<String>> {
        val arguments = extractArguments(method)
        val returnType = extractReturnType(method)
        val returnStatements = extractReturnStatements(method)

        fun String.tokens(): List<String> {
            return if (subTokenize) {
                CodeCommentTokenizer.subTokenizeCode(this)
            } else {
                CodeCommentTokenizer.tokenizeCode(this)
            }
        }

        return hashMapOf(
            "argument_name" to arguments.map {
                it.second.tokens()
            }.flatten(),
            "argument_type" to arguments.map {
                it.first.tokens()
            }.flatten(),
            "return_type" to returnType.tokens(),
            "return_statements" to returnStatements.map {
                it.tokens()
            }.flatten(),
            "method_name" to method.name.tokens()
        )

    }


}