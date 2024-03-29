package org.jetbrains.research.commentupdater

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.beust.klaxon.Json
import com.beust.klaxon.Klaxon
import com.beust.klaxon.KlaxonException
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiMethod
import org.jetbrains.research.commentupdater.models.ONNXTensorUtils
import org.jetbrains.research.commentupdater.models.config.EmbeddingConfig
import org.jetbrains.research.commentupdater.models.config.ModelFilesConfig
import org.jetbrains.research.commentupdater.models.jit.JITModelFeatureExtractor
import org.jetbrains.research.commentupdater.processors.CodeCommentTokenizer
import java.io.File
import java.lang.Integer.min

data class DataSample(
    @Json(name = "nl_ids")
    val commentIds: List<List<Long>>,
    @Json(name = "nl_lens")
    val commentLens: List<Long>,
    @Json(name = "nl_features")
    val commentFeatures: List<List<List<Float>>>,
    @Json(name = "code_ids")
    val codeIds: List<List<Long>>,
    @Json(name = "code_lens")
    val codeLens: List<Long>,
    @Json(name = "code_features")
    val codeFeatures: List<List<List<Float>>>
)

const val TRUE_PROB = 0.5

class JITDetector {
    private val logger: Logger = Logger.getInstance(javaClass)

    val env: OrtEnvironment
    val session: OrtSession
    val modelPathsConfig: ModelFilesConfig

    init {
        modelPathsConfig = ModelFilesConfig()
        env = OrtEnvironment.getEnvironment()
        session = env.createSession(modelPathsConfig.jitOnnxFile, OrtSession.SessionOptions())
    }

    val embeddingConfig by lazy {
        Klaxon().parse<EmbeddingConfig>(File(modelPathsConfig.embeddingFile)) ?: throw KlaxonException(":(")
    }

    fun getPaddedIds(
        tokens: List<String>,
        vocab: MutableMap<String, Int>,
        padToSize: Int?,
        paddingElement: Int
    ): List<Int> {
        val paddedTokens = if (padToSize != null) tokens.take(padToSize) else tokens
        val ids = paddedTokens.map {
            getIdOrUnk(it, vocab)
        }
        return if (padToSize != null && ids.size != padToSize) {
            ids.plus(
                List(padToSize - ids.size) { paddingElement }
            )
        } else {
            ids
        }
    }

    fun predict(oldMethod: PsiMethod, newMethod: PsiMethod): Boolean? {
        val oldMethodFeatures = JITModelFeatureExtractor.extractMethodFeatures(oldMethod, subTokenize = true)
        val newMethodFeatures = JITModelFeatureExtractor.extractMethodFeatures(newMethod, subTokenize = true)
        val oldMethodCode = CodeCommentTokenizer.extractMethodCode(oldMethod)
        val newMethodCode = CodeCommentTokenizer.extractMethodCode(newMethod)

        val comment = newMethod.docComment ?: return null

        val commentSubTokens = CodeCommentTokenizer.subTokenizeComment(comment.text)
        val commentTokens = CodeCommentTokenizer.tokenizeComment(comment.text)

        val oldCodeSubTokens = CodeCommentTokenizer.subTokenizeCode(
            oldMethodCode, true
        )

        val newCodeSubTokens = CodeCommentTokenizer.subTokenizeCode(
            newMethodCode, true
        )

        val (spanCodeSequence, tokenCodeSequence, _) = CodeCommentDiffs.computeCodeDiffs(
            oldCodeSubTokens, newCodeSubTokens
        )
        val codeFeatures = JITModelFeatureExtractor.getCodeFeatures(
            spanCodeSequence,
            commentSubTokens,
            oldMethodFeatures,
            newMethodFeatures,
            maxCodeLen = embeddingConfig.maxCodeLen
        )
        val commentFeatures = JITModelFeatureExtractor.getCommentFeatures(
            commentTokens,
            commentSubTokens,
            tokenCodeSequence,
            oldMethodFeatures,
            newMethodFeatures,
            maxCommentLen = embeddingConfig.maxCommentLen
        )

        val commentIds = getPaddedIds(
            commentSubTokens,
            vocab = embeddingConfig.commentVocab,
            padToSize = embeddingConfig.maxCommentLen,
            paddingElement = getIdOrUnk(embeddingConfig.paddingToken, embeddingConfig.commentVocab)
        ).map { it.toLong() }
        val commentLength = min(commentSubTokens.size, embeddingConfig.maxCommentLen)

        val codeIds = getPaddedIds(
            spanCodeSequence,
            vocab = embeddingConfig.codeVocab,
            padToSize = embeddingConfig.maxCodeLen,
            paddingElement = getIdOrUnk(embeddingConfig.paddingToken, embeddingConfig.codeVocab)
        ).map { it.toLong() }
        val codeLength = min(spanCodeSequence.size, embeddingConfig.maxCodeLen)

        val commentIdsTensor = ONNXTensorUtils.twoDListToTensor(listOf(commentIds), env) ?: return null
        val commentLensTensor = ONNXTensorUtils.oneDListToTensor(listOf(commentLength.toLong()), env) ?: return null
        val commentFeaturesTensor = ONNXTensorUtils.threeDListToTensor(
            listOf(commentFeatures.map { it.toList().map { v -> v.toFloat() } }.toList()), env
        ) ?: return null

        val codeIdsTensor = ONNXTensorUtils.twoDListToTensor(listOf(codeIds), env) ?: return null
        val codeLensTensor = ONNXTensorUtils.oneDListToTensor(listOf(codeLength.toLong()), env) ?: return null
        val codeFeaturesTensor = ONNXTensorUtils.threeDListToTensor(
            listOf(codeFeatures.map { it.toList().map { v -> v.toFloat() } }.toList()),
            env
        ) ?: return null

        val inputs = mapOf(
            "nl_ids" to commentIdsTensor,
            "nl_lens" to commentLensTensor,
            "nl_features" to commentFeaturesTensor,
            "code_ids" to codeIdsTensor,
            "code_lens" to codeLensTensor,
            "code_features" to codeFeaturesTensor
        )

        var probability: Float
        session.run(inputs).use { results ->
            val modelOut = results[0] as OnnxTensor
            val zeroSoftmax = kotlin.math.exp(modelOut.floatBuffer[0])
            val oneSoftmax = kotlin.math.exp(modelOut.floatBuffer[1])
            probability = oneSoftmax / (zeroSoftmax + oneSoftmax)
        }

        // Close all opened tensors from onnx
        commentIdsTensor.close()
        commentLensTensor.close()
        commentFeaturesTensor.close()

        codeIdsTensor.close()
        codeLensTensor.close()
        codeFeaturesTensor.close()

        logger.info("[CommentUpdater] JIT model result: method ${newMethod.name}, probability: $probability")
        return probability >= TRUE_PROB
    }

    fun getIdOrUnk(token: String, vocab: MutableMap<String, Int>): Int {
        return vocab[token] ?: vocab.getOrDefault(embeddingConfig.unknownToken, 0)
    }
}
