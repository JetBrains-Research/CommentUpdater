package org.jetbrains.research.commentupdater

import com.github.difflib.DiffUtils
import com.github.difflib.patch.DeltaType

data class EditNode(val editType: String, val children: MutableList<String>, var prev: EditNode?, var next: EditNode?)

/**
 * This class mostly taken from https://github.com/panthap2/deep-jit-inconsistency-detection diff_utils.py
 * Python code translated to Kotlin
 */
object CodeCommentDiffs {

    const val REPLACE = "<REPLACE>"
    const val REPLACE_OLD = "<REPLACE_OLD>"
    const val REPLACE_NEW = "<REPLACE_NEW>"
    const val REPLACE_END = "<REPLACE_END>"
    const val REPLACE_OLD_KEEP_BEFORE = "<REPLACE_OLD_KEEP_BEFORE>"
    const val REPLACE_NEW_KEEP_BEFORE = "<REPLACE_NEW_KEEP_BEFORE>"
    const val REPLACE_OLD_KEEP_AFTER = "<REPLACE_OLD_KEEP_AFTER>"
    const val REPLACE_NEW_KEEP_AFTER = "<REPLACE_NEW_KEEP_AFTER>"
    const val REPLACE_OLD_DELETE_KEEP_BEFORE = "<REPLACE_OLD_DELETE_KEEP_BEFORE>"
    const val REPLACE_NEW_DELETE_KEEP_BEFORE = "<REPLACE_NEW_DELETE_KEEP_BEFORE>"
    const val REPLACE_OLD_DELETE_KEEP_AFTER = "<REPLACE_OLD_DELETE_KEEP_AFTER>"
    const val REPLACE_NEW_DELETE_KEEP_AFTER = "<REPLACE_NEW_DELETE_KEEP_AFTER>"

    const val INSERT = "<INSERT>"
    const val INSERT_OLD = "<INSERT_OLD>"
    const val INSERT_NEW = "<INSERT_NEW>"
    const val INSERT_END = "<INSERT_END>"
    const val INSERT_OLD_KEEP_BEFORE = "<INSERT_OLD_KEEP_BEFORE>"
    const val INSERT_NEW_KEEP_BEFORE = "<INSERT_NEW_KEEP_BEFORE>"
    const val INSERT_OLD_KEEP_AFTER = "<INSERT_OLD_KEEP_AFTER>"
    const val INSERT_NEW_KEEP_AFTER = "<INSERT_NEW_KEEP_AFTER>"

    const val DELETE = "<DELETE>"
    const val DELETE_END = "<DELETE_END>"

    const val KEEP = "<KEEP>"
    const val KEEP_END = "<KEEP_END>"
    const val COPY_SEQUENCE = "<COPY_SEQUENCE>"

    val REPLACE_KEYWORDS = listOf(
    REPLACE,
    REPLACE_OLD,
    REPLACE_NEW,
    REPLACE_END,
    REPLACE_OLD_KEEP_BEFORE,
    REPLACE_NEW_KEEP_BEFORE,
    REPLACE_OLD_KEEP_AFTER,
    REPLACE_NEW_KEEP_AFTER,
    REPLACE_OLD_DELETE_KEEP_BEFORE,
    REPLACE_NEW_DELETE_KEEP_BEFORE,
    REPLACE_OLD_DELETE_KEEP_AFTER,
    REPLACE_NEW_DELETE_KEEP_AFTER)

    val INSERT_KEYWORDS = listOf(
    INSERT,
    INSERT_OLD,
    INSERT_NEW,
    INSERT_END,
    INSERT_OLD_KEEP_BEFORE,
    INSERT_NEW_KEEP_BEFORE,
    INSERT_OLD_KEEP_AFTER,
    INSERT_NEW_KEEP_AFTER)

    val DELETE_KEYWORDS = listOf(DELETE, DELETE_END)
    val KEEP_KEYWORDS = listOf(KEEP, KEEP_END)

    fun isInsert(token: String): Boolean {
        return token in INSERT_KEYWORDS
    }

    fun isKeep(token: String): Boolean {
        return token in KEEP_KEYWORDS
    }

    fun isReplace(token: String): Boolean {
        return token in REPLACE_KEYWORDS
    }

    fun isDelete(token: String): Boolean {
        return token in DELETE_KEYWORDS
    }

    fun isEdit(token: String): Boolean {
        return isInsert(token) || isKeep(token) || isReplace(token) || isDelete(token)
    }

    fun simpleDiff(oldTokens: List<String>, newTokens: List<String>) {
        print(DiffUtils.diff(oldTokens, newTokens, true))
    }

    fun getCoarseDiffStructure(oldTokens: List<String>, newTokens: List<String>): List<EditNode> {
        val nodes = mutableListOf<EditNode>()
        for (delta in DiffUtils.diff(oldTokens, newTokens, true).deltas) {
            val editNode = when (delta.type) {
                DeltaType.EQUAL -> EditNode(
                    KEEP,
                    delta.source.lines,
                    null,
                    null
                )
                DeltaType.CHANGE -> EditNode(
                    REPLACE,
                    (delta.source.lines + listOf(REPLACE_NEW) + delta.target.lines).toMutableList(),
                    null,
                    null
                )
                DeltaType.INSERT -> EditNode(
                    INSERT,
                    delta.target.lines,
                    null,
                    null
                )
                else -> EditNode(
                    DELETE,
                    delta.source.lines,
                    null,
                    null
                )
            }
            nodes.add(editNode)
        }
        return if (nodes.size <= 1) {
            nodes
        } else {
            // link nodes with prev/next
            for (i in 0 until nodes.size) {
                if (i != nodes.size - 1) {
                    nodes[i].next = nodes[i + 1]
                }
                if (i != 0) {
                    nodes[i].prev = nodes[i - 1]
                }
            }
            nodes
        }
    }

    fun getValidPositions(searchString: String, fullString: String): List<Int> {
        val searchSequence = searchString.split(' ')
        val fullSequence = fullString.split(' ')

        if (searchSequence.isEmpty()) {
            return listOf()
        }

        val possiblePositions = fullSequence
            .withIndex()
            .filter { (_, token) ->
                token == searchSequence[0]
            }
            .map { it.index }
        return possiblePositions.filter { position ->
            searchSequence.withIndex().all {
                (position + it.index < fullSequence.size) && (fullSequence[position + it.index] == it.value)
            }
        }
    }

    fun getFrequency(searchString: String, fullString: String): Int {
        return getValidPositions(searchString, fullString).size
    }

    // can be safely deleted, don't need this method for detection
    fun computeMinimalCommentDiffs(oldTokens: List<String>, newTokens: List<String>): List<String> {
        val spans = mutableListOf<String>()

        val oldText = oldTokens.joinToString(separator = " ")
        val diffNodes = getCoarseDiffStructure(oldTokens, newTokens)

        val newNodes = mutableListOf<EditNode>()

        for (node in diffNodes) {
            if (node.editType == KEEP) {
                newNodes.add(node)
            } else if (node.editType == DELETE) {

                val searchString = node.children.joinToString(" ")
                if (getFrequency(searchString, oldText) == 1) {
                    node.children.add(0, DELETE)
                    newNodes.add(node)
                    continue
                }
                if (node.prev != null) {
                    if (node.prev!!.editType == KEEP) {
                        val adoptedChildren = mutableListOf<String>()
                        var foundSubString = false
                        while (!foundSubString and node.prev!!.children.isNotEmpty()) {
                            adoptedChildren.add(0, node.prev!!.children.removeLast())
                            foundSubString = getFrequency(
                                (adoptedChildren + node.children).joinToString(" "),
                                oldText
                            ) == 1
                        }
                        if (foundSubString) {
                            val newChildren = listOf(REPLACE_OLD_DELETE_KEEP_BEFORE) + adoptedChildren +
                                    node.children + listOf(REPLACE_NEW_DELETE_KEEP_BEFORE) + adoptedChildren
                            val newNode = EditNode(REPLACE, newChildren.toMutableList(), node.prev, node.next)
                            node.prev!!.next = newNode
                            if (node.next != null) {
                                node.next!!.prev = newNode
                            }
                            newNodes.add(newNode)
                            continue
                        } else {
                            node.prev!!.children.addAll(adoptedChildren)
                        }
                    }
                }

                if (node.next != null) {
                    if (node.next!!.editType == KEEP) {
                        val adoptedChildren = mutableListOf<String>()
                        var foundSubString = false
                        while (!foundSubString and node.next!!.children.isNotEmpty()) {
                            adoptedChildren.add(node.next!!.children.removeFirst())
                            foundSubString = getFrequency(
                                (node.children + adoptedChildren).joinToString(" "),
                                oldText
                            ) == 1
                        }
                        if (foundSubString) {
                            val newChildren = listOf(REPLACE_OLD_DELETE_KEEP_AFTER) + node.children +
                                    adoptedChildren + listOf(REPLACE_NEW_DELETE_KEEP_AFTER) + adoptedChildren
                            val newNode = EditNode(REPLACE, newChildren.toMutableList(), node.prev, node.next)
                            if (node.prev != null) {
                                node.prev!!.next = newNode
                            }

                            node.next!!.prev = newNode
                            newNodes.add(newNode)
                            continue
                        } else {
                            node.next!!.children.clear()
                            node.next!!.children.addAll(adoptedChildren + node.next!!.children)
                        }
                    }
                }
                return getFullReplaceSpan(oldTokens, newTokens)
            } else if (node.editType == REPLACE) {
                val repId = node.children.indexOf(REPLACE_NEW)
                val repOldChildren = node.children.slice(0 until repId)
                val repNewChildren = node.children.slice(repId until node.children.size)
                val searchString = repOldChildren.joinToString(" ")

                if (getFrequency(searchString, oldText) == 1) {
                    node.children.add(0, REPLACE_OLD)
                    newNodes.add(node)
                    continue
                }

                if (node.prev != null) {
                    if (node.prev!!.editType == KEEP) {
                        val adoptedChildren = mutableListOf<String>()
                        var foundSubString = false
                        while (!foundSubString and !node.prev!!.children.isEmpty()) {
                            adoptedChildren.add(0, node.prev!!.children.removeLast())
                            foundSubString = getFrequency(
                                (adoptedChildren + repOldChildren).joinToString(" "),
                                oldText
                            ) == 1
                        }
                        if (foundSubString) {
                            val newChildren = listOf(REPLACE_OLD_KEEP_BEFORE) + adoptedChildren +
                                    repOldChildren + listOf(REPLACE_NEW_KEEP_BEFORE) +
                                    adoptedChildren + repNewChildren
                            val newNode = EditNode(REPLACE, newChildren.toMutableList(), node.prev, node.next)
                            node.prev!!.next = newNode
                            if (node.next != null) {
                                node.next!!.prev = newNode
                            }
                            newNodes.add(newNode)
                            continue
                        } else {
                            node.prev!!.children.addAll(adoptedChildren)
                        }
                    }
                }

                if (node.next != null) {
                    if (node.next!!.editType == KEEP) {
                        val adoptedChildren = mutableListOf<String>()
                        var foundSubString = false
                        while (!foundSubString and !node.next!!.children.isEmpty()) {
                            adoptedChildren.add(node.next!!.children.removeFirst())
                            foundSubString = getFrequency(
                                (repOldChildren + adoptedChildren).joinToString(" "),
                                oldText
                            ) == 1
                        }

                        if (foundSubString) {
                            val newChildren = listOf(REPLACE_OLD_KEEP_AFTER) + repOldChildren + adoptedChildren +
                                    listOf(REPLACE_NEW_KEEP_AFTER) + repNewChildren + adoptedChildren
                            val newNode = EditNode(REPLACE, newChildren.toMutableList(), node.prev, node.next)
                            if (node.prev != null) {
                                node.prev!!.next = newNode
                            }

                            node.next!!.prev = newNode
                            newNodes.add(newNode)
                            continue
                        } else {
                            node.next!!.children.clear()
                            node.next!!.children.addAll(adoptedChildren + node.next!!.children)
                        }
                    }
                }
                return getFullReplaceSpan(oldTokens, newTokens)
            } else if (node.editType == INSERT) {
                if (node.prev != null) {
                    if (node.prev!!.editType == KEEP) {
                        val adoptedChildren = mutableListOf<String>()
                        var foundSubString = false
                        while (!foundSubString and !node.prev!!.children.isEmpty()) {
                            adoptedChildren.add(0, node.prev!!.children.removeLast())
                            foundSubString = getFrequency(
                                (adoptedChildren).joinToString(" "),
                                oldText
                            ) == 1
                        }
                        if (foundSubString) {
                            val newChildren = listOf(INSERT_OLD_KEEP_BEFORE) + adoptedChildren +
                                    listOf(INSERT_NEW_KEEP_BEFORE) + adoptedChildren + node.children
                            val newNode = EditNode(INSERT, newChildren.toMutableList(), node.prev, node.next)
                            node.prev!!.next = newNode
                            if (node.next != null) {
                                node.next!!.prev = newNode
                            }
                            newNodes.add(newNode)
                            continue
                        } else {
                            node.prev!!.children.addAll(adoptedChildren)
                        }
                    }
                }
                if (node.next != null) {
                    if (node.next!!.editType == KEEP) {
                        val adoptedChildren = mutableListOf<String>()
                        var foundSubString = false
                        while (!foundSubString and !node.next!!.children.isEmpty()) {
                            adoptedChildren.add(node.next!!.children.removeFirst())
                            foundSubString = getFrequency(
                                adoptedChildren.joinToString(" "),
                                oldText
                            ) == 1
                        }
                        if (foundSubString) {
                            val newChildren = listOf(INSERT_OLD_KEEP_AFTER) + adoptedChildren +
                                    listOf(INSERT_NEW_KEEP_AFTER) + node.children + adoptedChildren
                            val newNode = EditNode(INSERT, newChildren.toMutableList(), node.prev, node.next)
                            if (node.prev != null) {
                                node.prev!!.next = newNode
                            }

                            node.next!!.prev = newNode
                            newNodes.add(newNode)
                            continue
                        } else {
                            node.next!!.children.clear()
                            node.next!!.children.addAll(adoptedChildren + node.next!!.children)
                        }
                    }
                }
                return getFullReplaceSpan(oldTokens, newTokens)
            }
        }
        for (node in newNodes) {
            if (node.editType.contains("INSERT")) {
                spans.addAll(node.children + listOf(INSERT_END))
            } else if (node.editType.contains("REPLACE")) {
                spans.addAll(node.children + listOf(REPLACE_END))
            } else if (node.editType.contains("DELETE")) {
                spans.addAll(node.children + listOf(DELETE_END))
            }
        }
        return spans
     }

    fun getFullReplaceSpan(oldTokens: List<String>, newTokens: List<String>): List<String> {
        return listOf(REPLACE_OLD) + oldTokens + listOf(REPLACE_NEW) + newTokens + listOf(REPLACE_END)
    }

    fun computeCodeDiffs(oldTokens: List<String>, newTokens: List<String>):
            Triple<List<String>, List<String>, List<String>> {
        val spans = mutableListOf<String>()
        val tokens = mutableListOf<String>()
        val commands = mutableListOf<String>()
        for (delta in DiffUtils.diff(oldTokens, newTokens, true).deltas) {
            when (delta.type) {
                DeltaType.EQUAL -> {
                    spans.addAll(listOf(KEEP) + delta.source.lines + listOf(KEEP_END))
                    for (token in delta.source.lines) {
                        tokens.addAll(listOf(KEEP, token))
                        commands.add(KEEP)
                    }
                }
                DeltaType.CHANGE -> {
                    spans.addAll(
                        listOf(REPLACE_OLD) + delta.source.lines +
                                listOf(REPLACE_NEW) + delta.target.lines +
                                listOf(REPLACE_END))
                    for (token in delta.source.lines) {
                        tokens.addAll(listOf(REPLACE_OLD, token))
                        commands.add(REPLACE_OLD)
                    }
                    for (token in delta.target.lines) {
                        tokens.addAll(listOf(REPLACE_NEW, token))
                        commands.addAll(listOf(REPLACE_NEW, token))
                    }
                }
                DeltaType.INSERT -> {
                    spans.addAll(listOf(INSERT) + delta.target.lines + listOf(INSERT_END))
                    for (token in delta.target.lines) {
                        tokens.addAll(listOf(INSERT, token))
                        commands.addAll(listOf(INSERT, token))
                    }
                }
                else -> {
                    spans.addAll(listOf(DELETE) + delta.source.lines + listOf(DELETE_END))
                    for (token in delta.source.lines) {
                        tokens.addAll(listOf(DELETE, token))
                        commands.addAll(listOf(DELETE, token))
                    }
                }
            }
        }
        return Triple(spans, tokens, commands)
    }
}
