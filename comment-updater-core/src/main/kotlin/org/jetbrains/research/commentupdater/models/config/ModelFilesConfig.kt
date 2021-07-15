package org.jetbrains.research.commentupdater.models.config

object ModelFilesConfig {
    val DATA_DIR = "/Users/Ivan.Pavlov/IdeaProjects/CommentUpdater1"

    val CODE_EMBEDDING_ONNX_FILE = "$DATA_DIR/code_embeddings.onnx"
    val COMMENT_EMBEDDING_ONNX_FILE = "$DATA_DIR/comment_embeddings.onnx"

    val JIT_ONNX_FILE = "$DATA_DIR/model.onnx"
    val EMBEDDING_FILE = "$DATA_DIR/model_embedding_config.json"
}