package org.jetbrains.research.commentupdater.models

import com.intellij.psi.PsiMethod
import com.intellij.psi.javadoc.PsiDocComment
import org.jetbrains.research.commentupdater.CodeCommentDiffs
import org.jetbrains.research.commentupdater.models.metrics.SimilarityModel
import org.jetbrains.research.commentupdater.processors.CodeCommentTokenizer
import org.jetbrains.research.commentupdater.processors.MethodChangesExtractor
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
    val oldNewChangedSimDist: Double
)

class MetricsCalculator {

    val simModel: SimilarityModel = SimilarityModel()

    fun calculateMetrics(oldMethod: PsiMethod, newMethod: PsiMethod, comment: PsiDocComment,
    methodRefactorings: HashMap<String, MutableList<Refactoring>>): MethodMetric {
        val methodName = MethodChangesExtractor.extractFullyQualifiedName(newMethod)
        val methodRefactorings = methodRefactorings.getOrDefault(methodName, mutableListOf())
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

        // todo: filter code subtokens from braces, numbers and so on

        val oldCode = CodeCommentTokenizer.extractMethodCode(oldMethod)
        val newCode = CodeCommentTokenizer.extractMethodCode(newMethod)
        val oldSubTokens = CodeCommentTokenizer.subTokenizeCode(oldCode)
        val newSubTokens = CodeCommentTokenizer.subTokenizeCode(newCode)

        val oldCodeLen = oldSubTokens.size
        val newCodeLen = newSubTokens.size

        // todo: should subtokenizer remove *, !, ... ? (now it doesn't)
        val commentSubTokens = CodeCommentTokenizer.subTokenizeComment(comment.text)

        val commentLen = commentSubTokens.size

        val (spans, diffTokens, diffCommands) = CodeCommentDiffs.computeCodeDiffs(oldSubTokens, newSubTokens)

        val changedCodeLen = diffCommands.count {
            it != CodeCommentDiffs.KEEP
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

        val oldCodeCommentSim = simModel.compute(
            oldSubTokens,
            commentSubTokens,
            true,
            false
        )

        val newCodeCommentSim = simModel.compute(
            newSubTokens,
            commentSubTokens,
            true,
            false
        )

        val newOldCodeCommentSimDistance = (oldCodeCommentSim - newCodeCommentSim).absoluteValue

        val removedStatements = diffTokens.zipWithNext().filter {
            it.first in listOf(CodeCommentDiffs.REPLACE_OLD, CodeCommentDiffs.DELETE)
        }.map { it.second }

        val addedStatements = diffTokens.zipWithNext().filter {
            it.first in listOf(CodeCommentDiffs.REPLACE_NEW, CodeCommentDiffs.INSERT)
        }.map { it.second }

        val oldChangedCommentSim = simModel.compute(
            removedStatements,
            commentSubTokens,
            true,
            false
        )

        val newChangedCommentSim = simModel.compute(
            addedStatements,
            commentSubTokens,
            true,
            false
        )

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
            oldNewChangedSimDist = oldNewChangedSimDist
        )
    }
}
