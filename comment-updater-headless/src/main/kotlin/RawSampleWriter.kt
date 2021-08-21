import com.google.gson.Gson
import org.jetbrains.research.commentupdater.dataset.RawDatasetSample
import java.io.File
import java.io.FileWriter
import java.io.Writer

class RawSampleWriter(output: File) {

    // Saving data
    private val outputPath: String = output.path

    private lateinit var projectFile: File
    private lateinit var projectWriter: Writer
    lateinit var projectName: String
    private val gson = Gson()

    fun setProjectFile(projectPath: String) {
        projectName = projectPath.split('/').last()
        projectFile = File(outputPath).resolve("${projectName}.jsonl")
        projectFile.createNewFile()
        projectWriter = FileWriter(projectFile, true)
    }

    fun close() {
        projectWriter.close()
    }

    fun saveMetrics(
        rawSample: RawDatasetSample
    ) {
        val jsonSample = gson.toJson(rawSample)

        projectWriter.write(jsonSample)
        projectWriter.write("\n")
    }
}