package org.jetbrains.research.commentupdater.dataset

import com.beust.klaxon.Json
import org.jetbrains.research.commentupdater.models.MethodMetric
import org.jetbrains.research.commentupdater.utils.MethodNameWithParam

data class RawDatasetSample(
    @Json
    val oldCode: String,
    @Json
    val newCode: String,
    @Json
    val oldComment: String,
    @Json
    val newComment: String,
    @Json
    val oldMethodName: MethodNameWithParam,
    @Json
    val newMethodName: MethodNameWithParam,
    @Json
    val commitId: String,
    @Json
    val commitTime: String,
    @Json
    val newFileName: String
)

enum class CommentUpdateLabel {
    INCONSISTENCY,
    CONSISTENCY
}

data class DatasetSample(
    val project: String,
    val oldCommit: String,
    val newCommit: String,
    val oldCode: String,
    val newCode: String,
    val oldComment: String,
    val newComment: String,
    val metric: MethodMetric,
    val newFileName: String,
    val jumpLength: Int,
    val label: CommentUpdateLabel
)
