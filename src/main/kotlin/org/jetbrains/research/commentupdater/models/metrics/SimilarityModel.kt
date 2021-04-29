package org.jetbrains.research.commentupdater.models.metrics

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.beust.klaxon.Klaxon
import org.jetbrains.bio.viktor.F64Array
import org.jetbrains.research.commentupdater.models.ONNXTensorUtils
import org.jetbrains.research.commentupdater.models.config.EmbeddingConfig
import org.jetbrains.research.commentupdater.models.config.ModelFilesConfig
import java.io.File
import kotlin.math.pow


class SimilarityModel {

    val embeddingConfig: EmbeddingConfig
    val env: OrtEnvironment
    val codeEmbeddingsSession: OrtSession
    val commentEmbeddingSession: OrtSession
    val EMBEDDING_SIZE = 64

    init {
        embeddingConfig = Klaxon().parse<EmbeddingConfig>(File(ModelFilesConfig.EMBEDDING_FILE))
            ?: throw Exception("can't load embeddings")


        env = OrtEnvironment.getEnvironment()
        codeEmbeddingsSession = env.createSession(ModelFilesConfig.CODE_EMBEDDING_ONNX_FILE, OrtSession.SessionOptions())
        commentEmbeddingSession = env.createSession(ModelFilesConfig.COMMENT_EMBEDDING_ONNX_FILE, OrtSession.SessionOptions())
    }


    fun compute(tokens1: List<String>, tokens2: List<String>, isCode1: Boolean, isCode2: Boolean): Double {
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
        // Victor library by JBR: https://blog.jetbrains.com/kotlin/2021/03/viktor-efficient-vectorized-computations-in-kotlin/
        val array1 = F64Array(tokens1.size, EMBEDDING_SIZE)
        for(i in 0 until tokens1.size) {
            val id = getIdOrUnk(tokens1[i], vocab1)
            val inputs = mapOf("id" to ONNXTensorUtils.oneDListToTensor(listOf(id.toLong()), env))
            val embedding = embeddings1.run(inputs)[0] as OnnxTensor
            for(j in 0 until EMBEDDING_SIZE) {
                array1[i, j] = embedding.floatBuffer[j].toDouble()
            }
        }
        val array2 = F64Array(tokens2.size, EMBEDDING_SIZE)
        for (i in 0 until tokens2.size) {
            val id = getIdOrUnk(tokens2[i], vocab2)
            val inputs = mapOf("id" to ONNXTensorUtils.oneDListToTensor(listOf(id.toLong()), env))
            val embedding = embeddings2.run(inputs)[0] as OnnxTensor
            for(j in 0 until EMBEDDING_SIZE) {
                array2[i, j] = embedding.floatBuffer[j].toDouble()
            }
        }
        val cosSimArray = F64Array(tokens1.size, tokens2.size)
        for(i in 0 until tokens1.size) {
            for(j in 0 until tokens2.size) {
                val dot = array1.V[i].dot(array2.V[j])
                val norm1 = array1.V[i].dot(array1.V[i]).pow(0.5)
                val norm2 = array2.V[j].dot(array2.V[j]).pow(0.5)
                cosSimArray[i, j] = try {
                    dot / (norm1 * norm2)
                } catch (e: ArithmeticException) {
                    0.0
                }
            }
        }

        // Liu2018 similarity definition
        val simS1S2 = (0 until tokens1.size).sumOf {
            i ->
            (0 until tokens2.size).maxOf {
                j ->
                cosSimArray[i, j]
            }
        } / tokens1.size
        val simS2S1 = (0 until tokens2.size).sumOf {
                j ->
            (0 until tokens1.size).maxOf {
                    i ->
                cosSimArray[i, j]
            }
        } / tokens2.size

        return (simS1S2 + simS2S1) / 2.0
    }

    fun getIdOrUnk(token: String, vocab: MutableMap<String, Int>): Int {
        return vocab[token] ?: vocab.getOrDefault(embeddingConfig.unk, 0)
    }
}
