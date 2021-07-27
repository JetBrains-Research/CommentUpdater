import com.google.gson.Gson
import java.io.File
import java.io.FileWriter
import java.io.Writer


class StatisticWriter(val output: File) {

    data class ProjectStatistic(
        val projectName: String,
        val numOfMethods: Int,
        val numOfDocMethods: Int
    )

    lateinit var writer: Writer
    val gson = Gson()

    fun open() {
        writer = FileWriter(output)
        writer.write("[")
    }

    fun close() {
        writer.write("]")
        writer.close()
    }

    fun saveStatistics(stats: ProjectStatistic) {
        writer.write(
            gson.toJson(stats)
        )
        writer.write(",")
    }
}