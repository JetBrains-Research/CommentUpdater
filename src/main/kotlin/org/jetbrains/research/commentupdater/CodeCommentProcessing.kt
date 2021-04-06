package org.jetbrains.research.commentupdater

import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiReturnStatement
import com.intellij.psi.javadoc.PsiDocComment
import com.intellij.psi.search.PsiElementProcessor
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.uast.util.isInstanceOf


class CodeCommentAST {
    // todo: implement
}

data class CodeCommentSample(val comment: String,
                             val commentSubTokens: List<String>,
                             val code: String,
                             val codeSubTokens: List<String>,
                             val ast: CodeCommentAST? = null)


object CodeCommentTokenizer {
    private val REDUNDANT_TAGS = listOf("{", "}", "@code", "@docRoot", "@inheritDic", "@link", "@linkplain", "@value")
    private val COMMENT_TAGS = listOf("@return", "@ return", "@param", "@ param", "@throws", "@ throws")

    fun createSample(code: String, comment: String): CodeCommentSample {
        return CodeCommentSample(comment=comment, commentSubTokens = subTokenizeComment(comment), code=code, codeSubTokens = subTokenizeCode(code))
    }

    fun subTokenizeComment(comment: String): List<String> {
        return subTokenizeText(comment, removeTag = true)
    }

    fun subTokenizeText(text: String, removeTag: Boolean = true, lowerCase: Boolean = true): List<String> {
        val tokens = tokenizeText(text, removeTag)
        return subTokenizeTokens(tokens, lowerCase)
    }

    fun tokenizeComment(comment: String): List<String> {
        return tokenizeText(comment, removeTag = true)
    }

    fun tokenizeText(text: String, removeTag: Boolean = true): List<String> {
        var cleanedText = text
        if(removeTag) {
            cleanedText = removeTagString(cleanedText)
            cleanedText = removeHTMLTag(cleanedText)
        }
        cleanedText = cleanedText.replace('\n', ' ')
        cleanedText = cleanedText.trim()
        return Regex("[a-zA-Z0-9]+|[^\\sa-zA-Z0-9]|[^_\\sa-zA-Z0-9]")
            .findAll(cleanedText)
            .map{
                it.groupValues[0]
            }.toList()

    }

    private fun removeHTMLTag(line: String): String {
        val cleanRegex = Regex("<.*?>")
        var cleanedLine = line.replace(cleanRegex, "")
        for(tag in REDUNDANT_TAGS) {
            cleanedLine = cleanedLine.replace(tag, "")
        }
        return cleanedLine
    }

    private fun removeTagString(line: String): String {
        var cleanedLine = line
        for(tag in COMMENT_TAGS) {
            cleanedLine = cleanedLine.replace(tag, "")
        }
        return cleanedLine
    }

    fun subTokenizeTokens(tokens: List<String>, lowerCase: Boolean): List<String> {
        val subTokens = mutableListOf<String>()
        for(token in tokens) {
            // Split tokens by lower case to upper case changes: [tokenABC] -> <token> <ABC>
            val curSubs = Regex("([a-z0-9])([A-Z])").replace(token.trim()) {
                it.groupValues.get(1) + " " + it.groupValues.get(2)
            }.split(" ")
            subTokens.addAll(curSubs)
        }
        return if(lowerCase) {
            subTokens.map { it.toLowerCase() }
        } else {
            subTokens
        }
    }

    fun subTokenizeCode(code: String, lowerCase: Boolean = true): List<String> {
        val tokens = tokenizeCode(code)
        val processedTokens = mutableListOf<String>()
        for(token in tokens) {
            processedTokens.addAll(
                Regex("[a-zA-Z0-9]+|[^\\sa-zA-Z0-9]|[^_\\sa-zA-Z0-9]")
                    .findAll(token)
                    .map{
                        it.groupValues[0]
                    }.toList()
            )
        }
        return subTokenizeTokens(processedTokens, lowerCase)
    }

    fun tokenizeCode(code: String): List<String> {
        return tokenizeText(code, removeTag = false)
    }
}




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

fun main() {
    println(CodeCommentTokenizer.tokenizeText("/**\n" +
            "   * Finds the first JavaDoc tag with the specified name.\n" +
            "   * @param name The name of the tags to find (not including the leading @ character).\n" +
            "   * @return the tag with the specified name, <some_html_tag> <some other tag> or null if not found.\n" +
            "   */"))
    println(CodeCommentTokenizer.tokenizeText("I am here and MainCaptain is here alsoChat"))
    println(CodeCommentTokenizer.subTokenizeText("I am here and MainCaptain is here alsoChat"))
    println(CodeCommentTokenizer.subTokenizeCode("""public class VowelConsonant {

    public static void main(String[] args) {
        // comment
        char ch = 'i';
        if(ch == 'a' || ch == 'e' || ch == 'i' || ch == 'o' || ch == 'u' )
            System.out.println(ch + " is vowel");
        else
            System.out.println(ch + " is consonant");

    }
}"""))

}
