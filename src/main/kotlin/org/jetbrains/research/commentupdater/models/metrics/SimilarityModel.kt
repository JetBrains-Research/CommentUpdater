package org.jetbrains.research.commentupdater.models.metrics

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.beust.klaxon.Klaxon
import org.jetbrains.research.commentupdater.models.config.EmbeddingConfig
import org.jetbrains.research.commentupdater.models.config.ModelFilesConfig
import java.io.File




class SimilarityModel {

    val embeddingConfig: EmbeddingConfig
    val env: OrtEnvironment
    val codeEmbeddingsSession: OrtSession
    val commentEmbeddingSession: OrtSession

    init {
        embeddingConfig = Klaxon().parse<EmbeddingConfig>(File(ModelFilesConfig.EMBEDDING_FILE))
            ?: throw Exception("can't load embeddings")


        env = OrtEnvironment.getEnvironment()
        codeEmbeddingsSession = env.createSession(ModelFilesConfig.CODE_EMBEDDING_ONNX_FILE, OrtSession.SessionOptions())
        commentEmbeddingSession = env.createSession(ModelFilesConfig.COMMENT_EMBEDDING_ONNX_FILE, OrtSession.SessionOptions())
    }


    fun compute(tokens1: List<String>, tokens2: List<String>, isCode1: Boolean, isCode2: Boolean) {
        val (vocab1, embeddings1) = if(isCode1) {
            embeddingConfig.codeVocab to codeEmbeddingsSession
        } else {
            embeddingConfig.commentVocab to commentEmbeddingSession
        }
        val (vocab2, embeddings2) = if(isCode2) {
            embeddingConfig.codeVocab to codeEmbeddingsSession
        } else {
            embeddingConfig.commentVocab to commentEmbeddingSession
        }

        // todo: Add cosine similarity calculation

    }
}