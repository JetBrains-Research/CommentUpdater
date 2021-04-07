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



