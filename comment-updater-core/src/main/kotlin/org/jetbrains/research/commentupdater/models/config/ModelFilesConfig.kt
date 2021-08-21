package org.jetbrains.research.commentupdater.models.config

import java.io.File

class ModelFilesConfig(dataDir: File = File("/Users/Ivan.Pavlov/IdeaProjects/modelConfig/")) {
    val codeEmbeddingOnnxFile: String = dataDir.resolve("code_embeddings.onnx").path
    val commentEmbeddingOnnxFile: String = dataDir.resolve("comment_embeddings.onnx").path

    val jitOnnxFile: String = dataDir.resolve("model.onnx").path
    val embeddingFile: String = dataDir.resolve("model_embedding_config.json").path
}
