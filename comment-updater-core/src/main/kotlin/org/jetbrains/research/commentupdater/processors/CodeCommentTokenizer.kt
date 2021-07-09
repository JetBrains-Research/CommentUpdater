package org.jetbrains.research.commentupdater.processors

import com.intellij.psi.*
import com.intellij.psi.javadoc.PsiDocTag
import com.intellij.psi.javadoc.PsiDocToken
import com.intellij.psi.util.PsiTreeUtil

object CodeCommentTokenizer {
    private val REDUNDANT_TAGS = listOf("{", "}", "@code", "@docRoot", "@inheritDic", "@link", "@linkplain", "@value")
    private val COMMENT_TAGS = listOf("@return", "@ return", "@param", "@ param", "@throws", "@ throws")

    fun subTokenizeComment(comment: String): List<String> {
        val commentWithoutStars = comment.filter{it != '/' && it != '*'}
        if (commentWithoutStars.trim().startsWith("@return")) {
            return listOf("@return") + subTokenizeText(commentWithoutStars, removeTag=true)
        }
        if (commentWithoutStars.trim().startsWith("@param")) {
            return listOf("@return") + subTokenizeText(commentWithoutStars, removeTag=true)
        }
        return subTokenizeText(commentWithoutStars, removeTag = false)
    }

    fun subTokenizeText(text: String, removeTag: Boolean = true, lowerCase: Boolean = true): List<String> {
        val tokens = tokenizeText(text, removeTag)
        return subTokenizeTokens(tokens, lowerCase)
    }

    fun tokenizeComment(comment: String): List<String> {
        val commentWithoutStars = comment.filter{it != '/' && it != '*'}
        if (commentWithoutStars.trim().startsWith("@return")) {
            return listOf("@return") + tokenizeText(commentWithoutStars, removeTag=true)
        }
        if (commentWithoutStars.trim().startsWith("@param")) {
            return listOf("@return") + tokenizeText(commentWithoutStars, removeTag=true)
        }
        return tokenizeText(commentWithoutStars, removeTag = false)
    }

    fun tokenizeText(text: String, removeTag: Boolean = true): List<String> {
        var cleanedText = text
        if (removeTag) {
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
        for (tag in REDUNDANT_TAGS) {
            cleanedLine = cleanedLine.replace(tag, "")
        }
        return cleanedLine
    }

    private fun removeTagString(line: String): String {
        var cleanedLine = line
        for (tag in COMMENT_TAGS) {
            cleanedLine = cleanedLine.replace(tag, "")
        }
        return cleanedLine
    }

    fun subTokenizeTokens(tokens: List<String>, lowerCase: Boolean): List<String> {
        val subTokens = mutableListOf<String>()
        for (token in tokens) {
            // Split tokens by lower case to upper case changes: [tokenABC] -> <token> <ABC>
            val curSubs = Regex("([a-z0-9])([A-Z])").replace(token.trim()) {
                it.groupValues.get(1) + " " + it.groupValues.get(2)
            }.split(" ")
            subTokens.addAll(curSubs)
        }
        return if (lowerCase) {
            subTokens.map { it.toLowerCase() }
        } else {
            subTokens
        }
    }

    fun subTokenizeCode(code: String, lowerCase: Boolean = true): List<String> {
        val tokens = tokenizeCode(code)
        val processedTokens = mutableListOf<String>()
        for (token in tokens) {
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

    fun extractMethodCode(method: PsiMethod): String {
        return method.children.filter {
            !PsiTreeUtil.instanceOf(it, PsiComment::class.java, PsiWhiteSpace::class.java,
                PsiDocTag::class.java, PsiDocToken::class.java)
        }.map {
            it.text
        }.joinToString(" ")
    }

}



