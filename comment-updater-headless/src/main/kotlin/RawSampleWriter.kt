import com.google.gson.Gson
import org.jetbrains.research.commentupdater.dataset.RawDatasetSample
import java.io.File
import java.io.FileWriter
import java.io.Writer

class RawSampleWriter(output: File) {

    // Saving data
    val outputPath = output.path

    lateinit var projectFile: File
    lateinit var projectWriter: Writer
    lateinit var projectName: String
    val gson = Gson()

    fun setProjectFile(projectPath: String) {
        projectName = projectPath.split('/').last()
        projectFile = File(outputPath).resolve("${projectName}.jsonl")
        projectFile.createNewFile()
        projectWriter = FileWriter(projectFile, true)
    }

    fun close() {
        projectWriter.close()

        // Deleting last comma: [ex1, ex2, ex3,] -> [ex1, ex2, ex3]
        // Is there a better way? I can't use seek without RandomAccessFiles
        val fileContent = projectFile.readText()
        if (fileContent.isEmpty())
            return
        val correctedFileContent = projectFile.readText().dropLast(2) + "]"
        projectFile.writeText(correctedFileContent)
    }

    fun saveMetrics(
        rawSample: RawDatasetSample
    ) {
        val jsonSample = gson.toJson(rawSample)

        projectWriter.write(jsonSample)
        projectWriter.write("\n")
    }
}