import com.google.gson.Gson
import org.jetbrains.research.commentupdater.dataset.DatasetSample
import java.io.File
import java.io.FileWriter
import java.io.Writer

class SampleWriter(output: File) {

    // Saving data
    val outputPath = output.path

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

        // Deleting last comma: [ex1, ex2, ex3,] -> [ex1, ex2, ex3]
        // Is there a better way? I can't use seek without RandomAccessFiles
        val fileContent = projectFile.readText()
        if (fileContent.isEmpty())
            return
        val correctedFileContent = projectFile.readText().dropLast(2) + "]"
        projectFile.writeText(correctedFileContent)
    }

    fun saveMetrics(datasetSample: DatasetSample
    ) {
        val jsonExample = gson.toJson(datasetSample)
        projectWriter.write(jsonExample)
        projectWriter.write(",")
    }
}