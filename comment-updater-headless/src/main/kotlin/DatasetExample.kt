import org.jetbrains.research.commentupdater.models.MethodMetric

data class DatasetExample(
    val oldCode: String,
    val newCode: String,
    val oldComment: String,
    val newComment: String,
    val oldMethodName: String,
    val newMethodName: String,
    val commitId: String,
    val commitTime: String,
    val fileName: String,
    val metric: MethodMetric
)
