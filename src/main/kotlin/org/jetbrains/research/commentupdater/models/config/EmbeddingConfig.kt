package org.jetbrains.research.commentupdater.models.config

import com.beust.klaxon.Json

data class EmbeddingConfig(
    @Json(name = "max_code_len")
    val maxCodeLen: Int,
    @Json(name = "max_nl_len")
    val maxCommentLen: Int,
    @Json(name = "unk")
    val unknownToken: String,
    @Json(name="pad")
    val paddingToken: String,
    @Json(name = "nl")
    val commentVocab: MutableMap<String, Int>,
    @Json(name = "code")
    val codeVocab: MutableMap<String, Int>
)
