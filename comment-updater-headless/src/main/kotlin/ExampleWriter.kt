import com.google.gson.Gson
import org.jetbrains.research.commentupdater.dataset.DatasetExample
import java.io.File
import java.io.FileWriter
import java.io.Writer

class ExampleWriter {

    // Saving data
    val outputPath = HeadlessConfig.OUTPUT_DIR_PATH

    lateinit var projectFile: File
    lateinit var projectWriter: Writer
    lateinit var projectName: String
    val gson = Gson()

    fun setProjectFile(projectPath: String) {
        projectName = projectPath.split('/').last()
        projectFile = File(outputPath).resolve("${projectName}.json")
        projectWriter = FileWriter(projectFile, true)
    }

    fun open() {
        projectFile.writeText("[")
    }

    fun close() {
        projectWriter.write("]")
        projectWriter.close()
    }

    fun saveMetric(datasetExample: DatasetExample
    ) {
        val jsonExample = gson.toJson(datasetExample)
        projectWriter.write(jsonExample)
        projectWriter.write(",")
    }
}