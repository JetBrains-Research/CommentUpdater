package org.jetbrains.research.commentupdater.dataset

import com.beust.klaxon.Json
import org.jetbrains.research.commentupdater.models.MethodMetric

enum class MethodUpdateType(@Json val typeName: String) {
    ADD("ADD"),
    MOVE("MOVE"),
    CHANGE("CHANGE")
}

data class RawDatasetSample(
    @Json
    val update: MethodUpdateType,
    @Json
    val code: String,
    @Json
    val comment: String,
    @Json
    val methodName: String,
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
    val label: CommentUpdateLabel
)
