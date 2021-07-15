package org.jetbrains.research.commentupdater.models.metrics

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.beust.klaxon.Klaxon
import org.jetbrains.research.commentupdater.models.ONNXTensorUtils
import org.jetbrains.research.commentupdater.models.config.EmbeddingConfig
import org.jetbrains.research.commentupdater.models.config.ModelFilesConfig
import org.ojalgo.array.Primitive64Array
import org.ojalgo.function.constant.PrimitiveMath
import org.ojalgo.matrix.store.Primitive64Store
import java.io.File

class SimilarityModel(modelPathsConfig: ModelFilesConfig) {

    val embeddingConfig: EmbeddingConfig
    val env: OrtEnvironment
    val codeEmbeddingsSession: OrtSession
    val commentEmbeddingSession: OrtSession
    val EMBEDDING_SIZE = 64

    init {
        embeddingConfig = Klaxon().parse<EmbeddingConfig>(File(modelPathsConfig.EMBEDDING_FILE))
            ?: throw Exception("can't load embeddings")


        env = OrtEnvironment.getEnvironment()
        codeEmbeddingsSession = env.createSession(modelPathsConfig.CODE_EMBEDDING_ONNX_FILE, OrtSession.SessionOptions())
        commentEmbeddingSession = env.createSession(modelPathsConfig.COMMENT_EMBEDDING_ONNX_FILE, OrtSession.SessionOptions())
    }

    /**
     * Compute similarity between two token sequences, as described in Liu 2018 paper
     * Calculate embedding cos similarity for every pair (i,j) and (tokensA[i], tokensB[j])
     * Aggregate this pairwise similarities into one number, similarity between tokensA and tokensB sequences
     */
    fun compute(tokensA: List<String>, tokensB: List<String>, useCodeVocab: Boolean): Double {
        val (vocab, embeddings) = if (useCodeVocab) {
            embeddingConfig.codeVocab to codeEmbeddingsSession
        } else {
            embeddingConfig.commentVocab to commentEmbeddingSession
        }

        val embeddingMatrixA = buildEmbeddingMatrix(tokensA, vocab, embeddings)
        val normMatrixA = getNormMatrix(tokensA.size.toLong(), embeddingMatrixA)


        val embeddingMatrixB = buildEmbeddingMatrix(tokensB, vocab, embeddings)
        val normMatrixB = getNormMatrix(tokensB.size.toLong(), embeddingMatrixB)

        // cosSimMatrix[i, j] = cos_similarity(embedding of tokenA[i], embedding of tokenB[j])
        // Multiply embeddingA * embeddingB and then normalize by rows and columns
        // By multiplying with normalization matrices from left and right
        // cosSim = Norm1 * (E1 * E2.T) * Norm2
        val cosSimMatrix = normMatrixA.multiply(embeddingMatrixA.multiply(embeddingMatrixB.transpose())).multiply(normMatrixB)

        // Liu2018 similarity definition
        val simS1S2 = (0 until tokensA.size).sumOf {
            i ->
            (0 until tokensB.size).maxOf {
                j ->
                cosSimMatrix.get(i.toLong(), j.toLong())
            }
        } / tokensA.size
        val simS2S1 = (0 until tokensB.size).sumOf {
                j ->
            (0 until tokensA.size).maxOf {
                    i ->
                cosSimMatrix.get(i.toLong(), j.toLong())
            }
        } / tokensB.size

        return (simS1S2 + simS2S1) / 2.0
    }

    /**
     * Return diagonal matrix, with a_{i, i} := 1 / root(<embedding[i], embedding[i]>)
     */
    private fun getNormMatrix(
        tokensSize: Long,
        embeddingMatrix: Primitive64Store
    ): Primitive64Store {
        // Square root from x
        val modifier = PrimitiveMath.ROOT.parameter(2)

        // normVector[i] = root(<embedding for tokens[i], embedding for tokens[i]>)
        val normVector = embeddingMatrix.multiply(embeddingMatrix.transpose()).operateOnAll(modifier).sliceDiagonal()

        // normArray[i] = 1 / normVector[i]
        val normArray = Primitive64Array.FACTORY.copy(normVector)
        normArray.modifyAll(PrimitiveMath.INVERT)

        // Diagonal matrix with normArray values on main diagonal
        val normMatrix = Primitive64Store.FACTORY.makeEye(tokensSize, tokensSize)
        normMatrix.fillDiagonal(normArray)

        return normMatrix
    }

    private fun buildEmbeddingMatrix(
        tokens: List<String>,
        vocab: MutableMap<String, Int>,
        embeddings: OrtSession
    ): Primitive64Store {

        // Embedding matrix for tokens
        val embeddingMatrix = Primitive64Store.FACTORY.make(tokens.size.toLong(), EMBEDDING_SIZE.toLong())

        for (i in 0 until tokens.size) {
            val id = getIdOrUnk(tokens[i], vocab)
            val idTensor = ONNXTensorUtils.oneDListToTensor(listOf(id.toLong()), env)
            val inputs = mapOf("id" to idTensor)
            embeddings.run(inputs).use { results ->
                val embedding = results[0] as OnnxTensor
                for (j in 0 until EMBEDDING_SIZE) {
                    embeddingMatrix.set(i.toLong(), j.toLong(), embedding.floatBuffer[j].toDouble())
                }
            }
            idTensor?.close()
        }
        return embeddingMatrix
    }


    fun getIdOrUnk(token: String, vocab: MutableMap<String, Int>): Int {
        return vocab[token] ?: vocab.getOrDefault(embeddingConfig.unknownToken, 0)
    }
}
