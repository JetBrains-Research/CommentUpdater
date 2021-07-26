import com.beust.klaxon.Klaxon
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.types.file
import com.intellij.openapi.application.ApplicationStarter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.research.commentupdater.SampleWriter
import org.jetbrains.research.commentupdater.StatisticHandler
import org.jetbrains.research.commentupdater.dataset.CommentUpdateLabel
import org.jetbrains.research.commentupdater.dataset.DatasetSample
import org.jetbrains.research.commentupdater.dataset.RawDatasetSample
import org.jetbrains.research.commentupdater.models.MethodMetric
import org.jetbrains.research.commentupdater.models.MetricsCalculator
import org.jetbrains.research.commentupdater.models.config.ModelFilesConfig
import org.jetbrains.research.commentupdater.processors.RefactoringExtractor
import java.io.File
import kotlin.system.exitProcess

class MethodBranchHandler {
    var newBranch: Int = 0
    private val branchToInconsistencySample = hashMapOf<Int, RawDatasetSample>()
    private val methodToBranch = hashMapOf<String, Int>()
    private val branchSpoiled = hashMapOf<Int, Boolean>()
    fun branchId(methodName: String): Int {
        if (!methodToBranch.containsKey(methodName)) {
            methodToBranch[methodName] = ++newBranch
            branchSpoiled[newBranch] = false
        }
        return methodToBranch[methodName]!!
    }

    fun getInconsistencySample(branch: Int): RawDatasetSample? {
        return branchToInconsistencySample[branch]
    }

    fun registerInconsistencySample(sample: RawDatasetSample) {
        val branch = branchId(sample.oldMethodName)
        branchToInconsistencySample[branch] = sample
    }

    fun isConsistencySpoiled(branch: Int): Boolean {
        return branchSpoiled[branch] ?: false
    }

    fun setBranchStatus(branch: Int, isSpoiled: Boolean) {
        branchSpoiled[branch] = isSpoiled
    }

    fun registerNameChange(oldName: String, newName: String) {
        val oldId = branchId(oldName)
        methodToBranch[newName] = oldId
        methodToBranch.remove(oldName)
    }
}

class PostProcessingPlugin : ApplicationStarter {
    override fun getCommandName(): String = "PostProcessing"

    override fun main(args: Array<String>) {
        PostProcessing().main(
            args.drop(1)
        )
    }
}


class PostProcessing : CliktCommand() {

    lateinit var metricsModel: MetricsCalculator
    private val klaxon = Klaxon()
    private val methodBranchHandler = MethodBranchHandler()
    private lateinit var sampleWriter: SampleWriter
    private val writeMutex = Mutex()
    private val statisticHandler = StatisticHandler()

    private val dataset by argument("Path to dataset").file(canBeFile = false, mustExist = true)
    private val output by argument("Path to output file").file()
    private val config by argument("Path to Model config").file(mustExist = true, canBeFile = false)

    companion object {
        enum class LogLevel {
            INFO, WARN, ERROR
        }

        var projectTag: String = ""
        var projectProcess: String = ""

        fun log(
            level: LogLevel, message: String, logThread: Boolean = false,
            applicationTag: String = "[PostProcessingCommentUpdater]"
        ) {
            val fullLogMessage =
                "$level ${if (logThread) Thread.currentThread().name else ""} $applicationTag [$projectTag $projectProcess] $message"

            when (level) {
                LogLevel.INFO -> {
                    println(fullLogMessage)
                }
                LogLevel.WARN -> {
                    System.err.println(fullLogMessage)
                }
                LogLevel.ERROR -> {
                    System.err.println(fullLogMessage)
                }
            }
        }
    }

    override fun run() {

        metricsModel = MetricsCalculator(ModelFilesConfig(config))
        sampleWriter = SampleWriter(output)
        sampleWriter.open()
        log(LogLevel.INFO, "Starting postprocessing")
        runBlocking {
            val projects = getProjectDataPaths(dataset)
            statisticHandler.numOfProjects = projects.size

            projects.map {
                async(Dispatchers.Default) {
                    processProject(it)
                    statisticHandler.processedProjects.incrementAndGet()
                    log(LogLevel.INFO, "Processed ${statisticHandler.processedSamples}")
                }
            }.awaitAll()
        }
        log(LogLevel.INFO, "Finished postprocessing. ${statisticHandler.report()}")
        sampleWriter.close()
        exitProcess(0)
    }


    private fun getProjectDataPaths(dataset: File): List<String> {
        // make list of all files in directory from arg

        return dataset.listFiles()?.map { it.path } ?: emptyList()
    }

    private suspend fun processProject(projectPath: String) {
        // From newest commit to oldest!
        val orderedSamples = readSamples(projectPath).sortedBy { -it.commitTime.toLong() }

        orderedSamples.forEach { sample ->
            if (sample.oldMethodName != sample.newMethodName) {
                // we are iterating commit from new to old
                methodBranchHandler.registerNameChange(oldName = sample.newMethodName, newName = sample.oldMethodName)
            }

            val commentChanged = sample.oldComment.trim() != sample.newComment.trim()
            val branch = methodBranchHandler.branchId(sample.oldMethodName)

            val codeChanged = sample.oldCode.trim() != sample.newCode.trim()
            if (codeChanged && commentChanged) {
                methodBranchHandler.setBranchStatus(branch, isSpoiled = false)
                val futureSample = methodBranchHandler.getInconsistencySample(branch)
                methodBranchHandler.registerInconsistencySample(sample)
                if (futureSample != null) {
                    val datasetSample = buildSample(
                        oldComment = sample.newComment,
                        newComment = futureSample.newComment,
                        oldCode = sample.newCode,
                        newCode = futureSample.newCode,
                        label = CommentUpdateLabel.INCONSISTENCY,
                        projectName = projectPath.split(File.separator).last().split('.').first()
                    )
                    writeMutex.withLock {
                        datasetSample?.let {
                            sampleWriter.writeSample(it)
                        }
                    }
                    statisticHandler.inconsistenciesCounter.incrementAndGet()
                }
            } else if (codeChanged) {
                if (!methodBranchHandler.isConsistencySpoiled(branch)) {
                    // consistency example
                    val datasetSample = buildSample(
                        oldComment = sample.oldComment,
                        newComment = sample.newComment,
                        oldCode = sample.oldCode,
                        newCode = sample.newCode,
                        label = CommentUpdateLabel.CONSISTENCY,
                        projectName = projectPath.split(File.separator).last().split('.').first()
                    )
                    writeMutex.withLock {
                        datasetSample?.let {
                            sampleWriter.writeSample(it)
                        }
                    }
                    statisticHandler.consistenciesCounter.incrementAndGet()
                }
            } else if (commentChanged) {
                // Should mark older comments as spoiled
                methodBranchHandler.setBranchStatus(branch, isSpoiled = true)
            }

            statisticHandler.processedSamples.incrementAndGet()
        }
    }

    private fun buildSample(
        projectName: String,
        oldComment: String, oldCode: String, newComment: String, newCode: String,
        label: CommentUpdateLabel
    ): DatasetSample? {
        val metrics = methodMetrics(
            oldComment = oldComment, newComment = newComment,
            oldCode = oldCode, newCode = newCode
        ) ?: return null
        return DatasetSample(
            oldComment = oldComment,
            newComment = newComment,
            oldCode = oldCode,
            newCode = newCode,
            metric = metrics,
            label = label,
            project = projectName
        )
    }

    private fun methodMetrics(oldComment: String, newComment: String, oldCode: String, newCode: String): MethodMetric? {
        val oldMockContent = """
            class Mock {
                $oldCode
            }
        """.trimIndent()
        val newMockContent = """
            class Mock {
                $newCode
            }
        """.trimIndent()

        val refactorings = RefactoringExtractor.extract(oldMockContent, newMockContent).toMutableList()
        return metricsModel.calculateMetrics(oldCode, newCode, oldComment, newComment, refactorings)
    }

    private fun readSamples(projectPath: String): List<RawDatasetSample> {
        val projectFile = File(projectPath)
        val textDataset = projectFile.readText()
        if (textDataset.length == 2) {
            return emptyList()
        }

        // You have to delete one last comma manually, to open json file :)
        val fixedDataset = textDataset.dropLast(2) + "]"
        return klaxon.parseArray<RawDatasetSample>(fixedDataset) ?: emptyList()
    }
}