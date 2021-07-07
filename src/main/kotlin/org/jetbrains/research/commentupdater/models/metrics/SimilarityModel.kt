package org.jetbrains.research.commentupdater.models.metrics

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.beust.klaxon.Klaxon
import org.jetbrains.bio.viktor.F64Array
import org.jetbrains.research.commentupdater.models.ONNXTensorUtils
import org.jetbrains.research.commentupdater.models.config.EmbeddingConfig
import org.jetbrains.research.commentupdater.models.config.ModelFilesConfig
import org.ojalgo.array.Primitive64Array
import org.ojalgo.function.constant.PrimitiveMath
import org.ojalgo.matrix.Primitive64Matrix
import org.ojalgo.matrix.store.Primitive64Store
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

    fun compute(tokens1: List<String>, tokens2: List<String>, useCodeVocab: Boolean): Double {
        val (vocab, embeddings) = if(useCodeVocab) {
            embeddingConfig.codeVocab to codeEmbeddingsSession
        } else {
            embeddingConfig.commentVocab to commentEmbeddingSession
        }

        val modifier = PrimitiveMath.ROOT.parameter(2)
        val storeFactory = Primitive64Store.FACTORY
        val matrixFactory = Primitive64Matrix.FACTORY
        // PrimitiveMatrix.Factory and PrimitiveDenseStore.Factory are very similar.
        // Every factory in ojAlgo that makes 2D-structures
        // extends/implements the same interface.

        //val matrix1 = storeFactory.makeEye(tokens1Size.toLong(), EMBEDDING_SIZE.toLong())
        val matrix1 = storeFactory.make(tokens1.size.toLong(), EMBEDDING_SIZE.toLong())

        for (i in 0 until tokens1.size) {
            val id = getIdOrUnk(tokens1[i], vocab)
            val idTensor = ONNXTensorUtils.oneDListToTensor(listOf(id.toLong()), env)
            val inputs = mapOf("id" to idTensor)
            embeddings.run(inputs).use { results ->
                val embedding = results[0] as OnnxTensor
                for (j in 0 until EMBEDDING_SIZE) {
                    matrix1.set(i.toLong(), j.toLong(), embedding.floatBuffer[j].toDouble())
                }
            }
            idTensor?.close()
        }
        val norm1 = matrix1.multiply(matrix1.transpose()).operateOnAll(modifier).sliceDiagonal()
        val norm1Array = Primitive64Array.FACTORY.copy(norm1)
        norm1Array.modifyAll(PrimitiveMath.INVERT)
        val norm1Matrix = storeFactory.makeEye(tokens1.size.toLong(), tokens1.size.toLong())
        norm1Matrix.fillDiagonal(norm1Array)
        val matrix2 = storeFactory.make(tokens2.size.toLong(), EMBEDDING_SIZE.toLong())
        for (i in 0 until tokens2.size) {
            val id = getIdOrUnk(tokens2[i], vocab)
            val idTensor = ONNXTensorUtils.oneDListToTensor(listOf(id.toLong()), env)
            val inputs = mapOf("id" to idTensor)
            embeddings.run(inputs).use { results ->
                val embedding = results[0] as OnnxTensor
                for (j in 0 until EMBEDDING_SIZE) {
                    matrix2.set(i.toLong(), j.toLong(), embedding.floatBuffer[j].toDouble())
                }
            }
            idTensor?.close()
        }
        val norm2 = matrix2.multiply(matrix2.transpose()).operateOnAll(modifier).sliceDiagonal()
        val norm2Array = Primitive64Array.FACTORY.copy(norm2)
        norm2Array.modifyAll(PrimitiveMath.INVERT)
        val norm2Matrix = storeFactory.makeEye(tokens2.size.toLong(), tokens2.size.toLong())
        norm2Matrix.fillDiagonal(norm2Array)
        val cosSimArray = norm1Matrix.multiply(matrix1.multiply(matrix2.transpose())).multiply(norm2Matrix)

        // Liu2018 similarity definition
        val simS1S2 = (0 until tokens1.size).sumOf {
            i ->
            (0 until tokens2.size).maxOf {
                j ->
                cosSimArray.get(i.toLong(), j.toLong())
            }
        } / tokens1.size
        val simS2S1 = (0 until tokens2.size).sumOf {
                j ->
            (0 until tokens1.size).maxOf {
                    i ->
                cosSimArray.get(i.toLong(), j.toLong())
            }
        } / tokens2.size

        return (simS1S2 + simS2S1) / 2.0
    }

    fun getIdOrUnk(token: String, vocab: MutableMap<String, Int>): Int {
        return vocab[token] ?: vocab.getOrDefault(embeddingConfig.unknownToken, 0)
    }
}
