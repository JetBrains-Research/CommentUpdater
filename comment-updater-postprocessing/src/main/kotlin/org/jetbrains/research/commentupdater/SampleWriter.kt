package org.jetbrains.research.commentupdater

import org.jetbrains.research.commentupdater.dataset.DatasetSample
import java.io.File
import com.google.gson.Gson
import java.io.Writer

class SampleWriter(output: File) {

    // Saving data
    private val outputPath: String = output.path

    lateinit var outputFile: File
    lateinit var writer: Writer
    private val gson = Gson()

    fun open() {
        outputFile = File(outputPath)
        outputFile.writeText("[")
    }

    fun close() {
        writer.write("]")
        writer.close()
    }

    fun writeSample(datasetSample: DatasetSample) {
        val jsonSample = gson.toJson(datasetSample)
        writer.write(jsonSample)
        writer.write(",")
    }
}