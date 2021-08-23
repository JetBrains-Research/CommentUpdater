package org.jetbrains.research.commentupdater.dataset

import org.jetbrains.research.commentupdater.models.MethodMetric
import org.jetbrains.research.commentupdater.utils.MethodNameWithParam

data class DatasetSample(
    val oldCode: String,
    val newCode: String,
    val oldComment: String,
    val newComment: String,
    val oldMethodName: MethodNameWithParam,
    val newMethodName: MethodNameWithParam,
    val commitId: String,
    val commitTime: String,
    val newFileName: String,
    val metric: MethodMetric
)
