package org.jetbrains.research.commentupdater.models.jit

import com.intellij.psi.PsiComment
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiReturnStatement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.research.commentupdater.CodeCommentDiffs
import org.jetbrains.research.commentupdater.processors.CodeCommentTokenizer

/**
 * Functions for extracting code/comment features, were mostly taken from
 * https://github.com/panthap2/deep-jit-inconsistency-detection
 */
object JITModelFeatureExtractor {

    val NUM_CODE_FEATURES = 19
    val NUM_NL_FEATURES = 53

    // todo: import stop words, not hardcode
    val STOP_WORDS = listOf(
        "i", "me", "my", "myself", "we",
        "our", "ours", "ourselves", "you", "you're", "you've",
        "you'll", "you'd", "your", "yours", "yourself",
        "yourselves", "he", "him", "his", "himself", "she",
        "she's", "her", "hers", "herself", "it", "it's", "its",
        "itself", "they", "them", "their", "theirs", "themselves",
        "what", "which", "who", "whom", "this", "that", "that'll",
        "these", "those", "am", "is", "are", "was", "were", "be",
        "been", "being", "have", "has", "had", "having", "do",
        "does", "did", "doing", "a", "an", "the", "and", "but",
        "if", "or", "because", "as", "until", "while", "of", "at",
        "by", "for", "with", "about", "against", "between", "into",
        "through", "during", "before", "after", "above", "below", "to",
        "from", "up", "down", "in", "out", "on", "off", "over", "under",
        "again", "further", "then", "once", "here", "there", "when", "where",
        "why", "how", "all", "any", "both", "each", "few", "more", "most",
        "other", "some", "such", "no", "nor", "not", "only", "own", "same",
        "so", "than", "too", "very", "s", "t", "can", "will", "just", "don",
        "don't", "should", "should've", "now", "d", "ll", "m", "o", "re",
        "ve", "y", "ain", "aren", "aren't", "couldn", "couldn't", "didn",
        "didn't", "doesn", "doesn't", "hadn", "hadn't", "hasn", "hasn't",
        "haven", "haven't", "isn", "isn't", "ma", "mightn", "mightn't",
        "mustn", "mustn't", "needn", "needn't", "shan", "shan't", "shouldn",
        "shouldn't", "wasn", "wasn't", "weren", "weren't", "won", "won't",
        "wouldn", "wouldn't"
    )

    val JAVA_KEYWORDS = listOf(
        "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class",
        "continue", "default", "do", "double", "else", "enum", "extends", "final", "finally",
        "float", "for", "if", "implements", "import", "instanceof", "int", "interface", "long",
        "native", "new", "null", "package", "private", "protected", "public", "return", "short",
        "static", "strictfp", "super", "switch", "synchronized", "this", "throw", "throws", "transient",
        "try", "void", "volatile", "while"
    )

    /**
     * Remove Comments from Method text
     */
    fun extractCode(method: PsiMethod): String {
        return method.children.filter { it !is PsiComment }.joinToString(separator = "", transform = { it.text })
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

    fun getCodeFeatures(
        spanSequence: List<String>, commentTokens: List<String>,
        oldFeatures: HashMap<String, List<String>>, newFeatures: HashMap<String, List<String>>,
        maxCodeLen: Int
    ): Array<IntArray> {
        val oldTypeSet = (oldFeatures["return_type"] ?: listOf()).toSet()
        val newTypeSet = (newFeatures["return_type"] ?: listOf()).toSet()
        val intersectionType = oldTypeSet.intersect(newTypeSet)

        val oldReturnStatements = (oldFeatures["return_statements"] ?: listOf()).toSet()
        val newReturnStatements = (newFeatures["return_statements"] ?: listOf()).toSet()
        val intersectionStatements = oldReturnStatements.intersect(newReturnStatements)

        val commentTokensSet = commentTokens.toSet()

        val features = Array(maxCodeLen) {
            IntArray(NUM_CODE_FEATURES) { 0 }
        }

        var lastCommand = ""

        for (it in spanSequence.withIndex()) {
            if (it.index >= maxCodeLen) {
                break
            }
            when (it.value) {
                in intersectionType -> {
                    features[it.index][0] = 1
                }
                in oldTypeSet -> {
                    features[it.index][1] = 1
                }
                in newTypeSet -> {
                    features[it.index][2] = 1
                }
                else -> {
                    features[it.index][3] = 1
                }
            }

            when (it.value) {
                in intersectionStatements -> {
                    features[it.index][4] = 1
                }
                in oldReturnStatements -> {
                    features[it.index][5] = 1
                }
                in newReturnStatements -> {
                    features[it.index][6] = 1
                }
                else -> {
                    features[it.index][7] = 1
                }
            }

            when {
                CodeCommentDiffs.isEdit(it.value) -> {
                    features[it.index][8] = 1
                }
                it.value in JAVA_KEYWORDS -> {
                    features[it.index][9] = 1

                }
                it.value.all { !it.isLetterOrDigit() } -> {
                    // is operator
                    features[it.index][10] = 1
                }
                it.value in commentTokensSet -> {
                    features[it.index][11] = 1
                }
            }

            if (!CodeCommentDiffs.isEdit(it.value)) {
                when (lastCommand) {
                    CodeCommentDiffs.KEEP -> {
                        features[it.index][12] = 1
                    }
                    CodeCommentDiffs.INSERT -> {
                        features[it.index][13] = 1
                    }
                    CodeCommentDiffs.DELETE -> {
                        features[it.index][14] = 1
                    }
                    CodeCommentDiffs.REPLACE_NEW -> {
                        features[it.index][15] = 1
                    }
                    else -> {
                        features[it.index][16] = 1
                    }
                }
            } else {
                lastCommand = it.value
            }

            // todo: Add code subToken labels/indicies
            // it is hard, because code already spanned with edit tokens and so on
            // it isn't obvious to figure out, whether token was taken from new or old sequence
            // now we consider every subtoken as token
            //features[it.index][17] = subTokenLabels[it.index]
            //features[it.index][18] = subTokenIndices[it.index]

        }
        return features
    }

    fun Boolean.toInt() = if (this) 1 else 0

    fun getSubTokenLabels(tokens: List<String>, parseComment: Boolean = false): Pair<List<Int>, List<Int>> {
        val labels = mutableListOf<Int>()
        val indices = mutableListOf<Int>()
        for (token in tokens) {
            val tokenSubs = if (parseComment && token in listOf("@return", "@param")) {
                listOf(token)
            } else {
                CodeCommentTokenizer.subTokenizeTokens(listOf(token), lowerCase = true)
            }
            if (tokenSubs.size == 1) {
                labels.add(0)
                indices.add(0)
            } else {
                for (index in 0 until tokenSubs.size) {
                    labels.add(1)
                    indices.add(index)
                }
            }
        }
        return labels to indices
    }

    fun getCommentFeatures(
        oldCommentTokens: List<String>, oldCommentSubTokens: List<String>, tokenDiffCodeSubTokens: List<String>,
        oldCodeFeatures: HashMap<String, List<String>>, newCodeFeatures: HashMap<String, List<String>>,
        maxCommentLen: Int
    ): Array<IntArray> {
        val duplicates = oldCommentSubTokens.groupingBy { it }.eachCount().filter { it.value > 1 }.keys.toSet()
        val insertCodeTokens = tokenDiffCodeSubTokens.zipWithNext().filter {
            it.first == CodeCommentDiffs.INSERT
        }.map {
            it.second
        }.toSet()
        val keepCodeTokens = tokenDiffCodeSubTokens.zipWithNext().filter {
            it.first == CodeCommentDiffs.KEEP
        }.map {
            it.second
        }.toSet()
        val deleteCodeTokens = tokenDiffCodeSubTokens.zipWithNext().filter {
            it.first == CodeCommentDiffs.DELETE
        }.map {
            it.second
        }.toSet()
        val replaceOldCodeTokens = tokenDiffCodeSubTokens.zipWithNext().filter {
            it.first == CodeCommentDiffs.REPLACE_OLD
        }.map {
            it.second
        }.toSet()
        val replaceNewCodeTokens = tokenDiffCodeSubTokens.zipWithNext().filter {
            it.first == CodeCommentDiffs.REPLACE_NEW
        }.map {
            it.second
        }.toSet()

        val oldTypeSet = (oldCodeFeatures["return_type"] ?: listOf()).toSet()
        val newTypeSet = (newCodeFeatures["return_type"] ?: listOf()).toSet()
        val intersectionType = oldTypeSet.intersect(newTypeSet)

        val oldReturnStatements = (oldCodeFeatures["return_statements"] ?: listOf()).toSet()
        val newReturnStatements = (newCodeFeatures["return_statements"] ?: listOf()).toSet()
        val intersectionStatements = oldReturnStatements.intersect(newReturnStatements)

        val (commentSubTokenLabels, commentSubTokenIndices) = getSubTokenLabels(oldCommentTokens, parseComment = true)

        val features = Array(maxCommentLen) {
            IntArray(NUM_NL_FEATURES) { 0 }
        }
        // all comment tokens tagged as OTHER part of speech
        // todo: add POS tagger
        val otherIndex = 35

        for (it in oldCommentSubTokens.withIndex()) {
            if (it.index >= maxCommentLen) {
                break
            }
            val token = it.value.toLowerCase()
            when (token) {
                in intersectionType -> {
                    features[it.index][0] = 1
                }
                in oldTypeSet -> {
                    features[it.index][1] = 1
                }
                in newTypeSet -> {
                    features[it.index][2] = 1
                }
                else -> {
                    features[it.index][3] = 1
                }
            }

            when (token) {
                in intersectionStatements -> {
                    features[it.index][4] = 1
                }
                in oldReturnStatements -> {
                    features[it.index][5] = 1
                }
                in newReturnStatements -> {
                    features[it.index][6] = 1
                }
                else -> {
                    features[it.index][7] = 1
                }
            }

            features[it.index][8] = (token in insertCodeTokens).toInt()
            features[it.index][9] = (token in keepCodeTokens).toInt()
            features[it.index][10] = (token in deleteCodeTokens).toInt()
            features[it.index][11] = (token in replaceOldCodeTokens).toInt()
            features[it.index][12] = (token in replaceNewCodeTokens).toInt()
            features[it.index][13] = (token in STOP_WORDS).toInt()
            features[it.index][14] = (token in duplicates).toInt()

            features[it.index][15] = commentSubTokenLabels[it.index]
            features[it.index][16] = commentSubTokenIndices[it.index]
            features[it.index][17 + otherIndex] = 1
        }
        return features
    }
}