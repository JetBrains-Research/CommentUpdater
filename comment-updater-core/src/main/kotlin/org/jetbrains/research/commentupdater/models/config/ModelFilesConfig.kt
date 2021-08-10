package org.jetbrains.research.commentupdater.models.config

import java.io.File

class ModelFilesConfig(dataDir: File = File("/Users/Ivan.Pavlov/IdeaProjects/CommentUpdater1/modelConfig")) {
    val CODE_EMBEDDING_ONNX_FILE = dataDir.resolve("code_embeddings.onnx").path
    val COMMENT_EMBEDDING_ONNX_FILE = dataDir.resolve("comment_embeddings.onnx").path

    val JIT_ONNX_FILE = dataDir.resolve("model.onnx").path
    val EMBEDDING_FILE = dataDir.resolve("model_embedding_config.json").path
}
