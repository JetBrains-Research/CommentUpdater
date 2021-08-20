package org.jetbrains.research.commentupdater.models

import org.jetbrains.research.commentupdater.CodeCommentDiffs
import org.jetbrains.research.commentupdater.models.config.ModelFilesConfig
import org.jetbrains.research.commentupdater.models.metrics.SimilarityModel
import org.jetbrains.research.commentupdater.processors.CodeCommentTokenizer
import org.refactoringminer.api.Refactoring
import org.refactoringminer.api.RefactoringType
import kotlin.math.absoluteValue

data class MethodMetric(
    val isRenamed: Boolean,
    val isParamAdded: Boolean,
    val isParamRemoved: Boolean,
    val isReturnTypeChanged: Boolean,
    val isParamTypeChanged: Boolean,
    val isParamRenamed: Boolean,
    val oldCodeLen: Int,
    val newCodeLen: Int,
    val commentLen: Int,
    val changedCodeLen: Int,
    val percentageCodeChanged: Double,
    val deleteCommentIntersectionLen: Int,
    val percentageCommentIntersectionDelete: Double,
    val oldCodeCommentSim: Double,
    val newCodeCommentSim: Double,
    val newOldCodeCommentSimDistance: Double,
    val oldChangedCommentSim: Double,
    val newChangedCommentSim: Double,
    val oldNewChangedSimDist: Double,
    val addedStatementSize: Int,
    val deletedStatementSize: Int
)

class MetricsCalculator(config: ModelFilesConfig) {
    val simModel: SimilarityModel = SimilarityModel(config)

    fun calculateMetrics(
        oldCode: String,
        newCode: String,
        oldComment: String,
        newComment: String,
        methodRefactorings: MutableList<Refactoring> = mutableListOf()
    ): MethodMetric? {

        var isRenamed = false
        var isParamAdded = false
        var isParamRemoved = false
        var isReturnTypeChanged = false
        var isParamTypeChanged = false
        var isParamRenamed = false
        for (r in methodRefactorings) {
            when (r.refactoringType) {
                RefactoringType.RENAME_METHOD -> {
                    isRenamed = true
                }
                RefactoringType.RENAME_PARAMETER -> {
                    isParamRenamed = true
                }
                RefactoringType.REMOVE_PARAMETER -> {
                    isParamRemoved = true
                }
                RefactoringType.ADD_PARAMETER -> {
                    isParamAdded = true
                }
                RefactoringType.CHANGE_RETURN_TYPE -> {
                    isReturnTypeChanged = true
                }
                RefactoringType.CHANGE_PARAMETER_TYPE -> {
                    isParamTypeChanged = true
                }
                else -> {
                }
            }
        }

        val filterSubTokens = { subToken: String ->
            val hasLetters: Boolean = subToken.any { it.isLetter() }
            hasLetters
        }

        val oldSubTokens = CodeCommentTokenizer.subTokenizeCode(oldCode).filter(filterSubTokens)
        val newSubTokens = CodeCommentTokenizer.subTokenizeCode(newCode).filter(filterSubTokens)

        val oldCodeLen = oldSubTokens.size
        val newCodeLen = newSubTokens.size

        val commentSubTokens = CodeCommentTokenizer.subTokenizeComment(newComment).filter(filterSubTokens)

        val oldCommentSubTokens = CodeCommentTokenizer.subTokenizeComment(oldComment).filter(filterSubTokens)

        val commentLen = commentSubTokens.size

        val (_, diffTokens, diffCommands) = CodeCommentDiffs.computeCodeDiffs(oldSubTokens, newSubTokens)

        val commentSpans = CodeCommentDiffs.computeMinimalCommentDiffs(oldCommentSubTokens, commentSubTokens)

        val changedCodeLen = diffCommands.count {
            it != CodeCommentDiffs.KEEP
        }

        val changeCommentLen = commentSpans.size

        // If code and comment are unchanged, we aren't interested
        if (changedCodeLen == 0 && changeCommentLen == 0) {
            return null
        }

        val percentageCodeChanged: Double = changedCodeLen / oldCodeLen.toDouble()

        val deletedOrReplaced = diffTokens
            .zipWithNext()
            .filterIndexed { index, pair ->
                index % 2 == 0 &&
                        (pair.first == CodeCommentDiffs.REPLACE_OLD || pair.first == CodeCommentDiffs.DELETE)
            }
            .map { it.second }
            .toSet()

        val deleteCommentIntersectionLen: Int = commentSubTokens.count {
            it in deletedOrReplaced
        }

        val percentageCommentIntersectionDelete: Double = deleteCommentIntersectionLen / commentLen.toDouble()

        if (oldSubTokens.isEmpty() || newSubTokens.isEmpty() || commentSubTokens.isEmpty()) {
            return null
        }

        val oldCodeCommentSim = simModel.compute(
            oldSubTokens,
            commentSubTokens,
            true
        )

        val newCodeCommentSim = simModel.compute(
            newSubTokens,
            commentSubTokens,
            true
        )

        val newOldCodeCommentSimDistance = (oldCodeCommentSim - newCodeCommentSim).absoluteValue

        val removedStatements = diffTokens.zipWithNext().filter {
            it.first in listOf(CodeCommentDiffs.REPLACE_OLD, CodeCommentDiffs.DELETE)
        }.map { it.second }

        val addedStatements = diffTokens.zipWithNext().filter {
            it.first in listOf(CodeCommentDiffs.REPLACE_NEW, CodeCommentDiffs.INSERT)
        }.map { it.second }

        val oldChangedCommentSim = if (removedStatements.isNotEmpty()) {
            simModel.compute(
                removedStatements,
                commentSubTokens,
                true
            )
        } else {
            0.0
        }

        val newChangedCommentSim = if (addedStatements.isNotEmpty()) {
            simModel.compute(
                addedStatements,
                commentSubTokens,
                true
            )
        } else {
            0.0
        }

        val oldNewChangedSimDist = (newChangedCommentSim - oldChangedCommentSim).absoluteValue

        return MethodMetric(
            isRenamed = isRenamed,
            isParamAdded = isParamAdded,
            isParamRemoved = isParamRemoved,
            isReturnTypeChanged = isReturnTypeChanged,
            isParamTypeChanged = isParamTypeChanged,
            isParamRenamed = isParamRenamed,
            oldCodeLen = oldCodeLen,
            newCodeLen = newCodeLen,
            commentLen = commentLen,
            changedCodeLen = changedCodeLen,
            percentageCodeChanged = percentageCodeChanged,
            deleteCommentIntersectionLen = deleteCommentIntersectionLen,
            percentageCommentIntersectionDelete = percentageCommentIntersectionDelete,
            oldCodeCommentSim = oldCodeCommentSim,
            newCodeCommentSim = newCodeCommentSim,
            newOldCodeCommentSimDistance = newOldCodeCommentSimDistance,
            oldChangedCommentSim = oldChangedCommentSim,
            newChangedCommentSim = newChangedCommentSim,
            oldNewChangedSimDist = oldNewChangedSimDist,
            addedStatementSize = addedStatements.size,
            deletedStatementSize = removedStatements.size
        )
    }
}
