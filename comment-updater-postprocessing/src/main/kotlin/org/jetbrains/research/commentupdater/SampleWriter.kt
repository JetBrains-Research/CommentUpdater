package org.jetbrains.research.commentupdater

import com.google.gson.Gson
import org.jetbrains.research.commentupdater.dataset.DatasetSample
import java.io.File
import java.io.FileWriter
import java.io.Writer

class SampleWriter(val outputDir: File) {

    // Saving data

    val projectFiles = mutableMapOf<String, File>()
    val projectWriters = mutableMapOf<String, Writer>()

    private val gson = Gson()

    fun open(project: String) {
        val projectFile = outputDir.resolve("$project.json")
        projectFile.createNewFile()
        val projectWriter = FileWriter(projectFile)
        projectWriter.write("[")
        projectFiles[project] = projectFile
        projectWriters[project] = projectWriter
    }

    fun close(project: String) {
        projectWriters[project]?.write("]")
        projectWriters[project]?.close()
        projectWriters.remove(project)
    }

    fun writeSample(datasetSample: DatasetSample, project: String) {
        val jsonSample = gson.toJson(datasetSample)
        projectWriters[project]?.write(jsonSample)
        projectWriters[project]?.write(",")
    }
}