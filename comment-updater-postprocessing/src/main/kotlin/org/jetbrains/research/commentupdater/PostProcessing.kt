import com.beust.klaxon.Klaxon
import com.beust.klaxon.KlaxonException
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.types.file
import com.google.gson.Gson
import com.google.gson.stream.JsonReader
import com.intellij.openapi.application.ApplicationStarter
import com.intellij.openapi.diagnostic.Logger
import com.jetbrains.rd.util.string.printToString
import kotlinx.coroutines.sync.Mutex
import org.jetbrains.research.commentupdater.SampleWriter
import org.jetbrains.research.commentupdater.StatisticHandler
import org.jetbrains.research.commentupdater.dataset.CommentUpdateLabel
import org.jetbrains.research.commentupdater.dataset.DatasetSample
import org.jetbrains.research.commentupdater.dataset.RawDatasetSample
import org.jetbrains.research.commentupdater.models.MethodMetric
import org.jetbrains.research.commentupdater.models.MetricsCalculator
import org.jetbrains.research.commentupdater.models.config.ModelFilesConfig
import org.jetbrains.research.commentupdater.processors.RefactoringExtractor
import org.jetbrains.research.commentupdater.utils.MethodNameWithParam
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import kotlin.system.exitProcess

class MethodBranchHandler {
    var newBranch: Int = 0
    private val branchToInconsistencySample = hashMapOf<Int, RawDatasetSample>()
    private val methodToBranch = hashMapOf<MethodNameWithParam, Int>()
    private val branchSpoiled = hashMapOf<Int, Boolean>()
    private val branchToCommitNumber = hashMapOf<Int, Int>()
    private val branchToPreviousCommitNumber = hashMapOf<Int, Int>()

    fun updateCommitNumber(branch: Int) {
        if (!branchToCommitNumber.containsKey(branch)) {
            branchToCommitNumber[branch] = 1
        } else {
            branchToCommitNumber[branch] = branchToCommitNumber.getOrDefault(branch, 1) + 1
        }
    }

    fun branchId(methodName: MethodNameWithParam): Int {
       if (!methodToBranch.containsKey(methodName)) {
            methodToBranch[methodName] = ++newBranch
            branchSpoiled[newBranch] = false
        }

        val branch = methodToBranch[methodName]!!

        return branch
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

    fun registerNameChange(oldName: MethodNameWithParam, newName: MethodNameWithParam) {
        val oldId = branchId(oldName)
        methodToBranch[newName] = oldId
        methodToBranch.remove(oldName)
    }

    fun getJumpLen(branch: Int): Int {
        val newCommitNumber = branchToPreviousCommitNumber.getOrDefault(branch, 1)
        branchToPreviousCommitNumber[branch] = branchToCommitNumber[branch]!!
        return branchToCommitNumber[branch]!! - newCommitNumber

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
    private val gson = Gson()

    private val dataset by argument("Path to dataset").file(canBeFile = false, mustExist = true)
    private val output by argument("Path to output dir").file(canBeFile = false)
    private val config by argument("Path to Model config").file(mustExist = true, canBeFile = false)

    companion object {
        private val LOG: Logger =
            Logger.getInstance(PostProcessing::class.java)

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
                    System.out.flush()
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
        LOG.info("Logging startup")
        log(LogLevel.INFO, "Starting postprocessing")
        val projects = getProjectDataPaths(dataset, output)
        statisticHandler.numOfProjects = projects.size

        projects.mapIndexed { index, it ->
            val projectName = it.split(File.separator).last().split('.').first()
            try {
                sampleWriter.open(projectName)
                processProject(it)
                statisticHandler.processedProjects.incrementAndGet()
                log(
                    LogLevel.INFO, "Processed project $index/${projects.size} [$projectName] " +
                            "${statisticHandler.processedSamples}"
                )
                sampleWriter.close(projectName)
            } catch (e: KlaxonException) {
                log(LogLevel.WARN, "Failed to open project [$projectName] due to $e")
            } catch (e: Exception) {
                log(LogLevel.WARN, "Failed to process project [$projectName] due to $e")
            }
        }
        log(LogLevel.INFO, "Finished postprocessing. ${statisticHandler.report()}")
        exitProcess(0)
    }


    private fun getProjectDataPaths(dataset: File, output: File): List<String> {
        // make list of all files in directory from arg
        val processedProjects = output.listFiles()?.map { it.path.split(File.separator).last() } ?: emptyList()
        return dataset.listFiles()?.map { it.path }?.filter {
            !processedProjects.contains(it.split(File.separator).last())
        } ?: emptyList()
    }

    private fun processProject(projectPath: String) {
        val projectName = projectPath.split(File.separator).last().split('.').first()
        log(LogLevel.INFO, "Processing [$projectName]...")
        // From newest commit to oldest!

        log(LogLevel.INFO, "Reading...")
        val orderedSamples = readSamples(projectPath).sortedBy { -it.commitTime.toLong() }
        log(LogLevel.INFO, "Read!")

        orderedSamples.forEach { sample ->
            if (sample.oldMethodName != sample.newMethodName) {
                // we are iterating commit from new to old
                methodBranchHandler.registerNameChange(oldName = sample.newMethodName, newName = sample.oldMethodName)
            }

            val commentChanged = sample.oldComment.trim() != sample.newComment.trim()
            val branch = methodBranchHandler.branchId(sample.oldMethodName)
            methodBranchHandler.updateCommitNumber(branch)

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
                        projectName = projectPath.split(File.separator).last().split('.').first(),
                        oldCommit = sample.commitId,
                        newCommit = futureSample.commitId,
                        jumpLength = methodBranchHandler.getJumpLen(branch),
                        newFileName = sample.newFileName,
                        oldName = sample.oldMethodName,
                        newName = sample.newMethodName
                    )
                    datasetSample?.let {
                        sampleWriter.writeSample(it, projectName)
                        statisticHandler.inconsistenciesCounter.incrementAndGet()
                    }
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
                        projectName = projectPath.split(File.separator).last().split('.').first(),
                        oldCommit = sample.commitId,
                        newCommit = "",
                        newFileName = sample.newFileName,
                        jumpLength = 0,
                        oldName = sample.oldMethodName,
                        newName = sample.newMethodName
                    )
                    datasetSample?.let {
                        sampleWriter.writeSample(it, projectName)
                        statisticHandler.consistenciesCounter.incrementAndGet()
                    }
                }
            } else if (commentChanged) {
                // Should mark older comments as spoiled
                methodBranchHandler.setBranchStatus(branch, isSpoiled = true)
            }

            statisticHandler.processedSamples.incrementAndGet()
        }
    }

    private fun buildSample(
        projectName: String, oldCommit: String, newCommit: String,
        newFileName: String,
        oldComment: String, oldCode: String, newComment: String, newCode: String,
        jumpLength: Int,
        label: CommentUpdateLabel,
        oldName: MethodNameWithParam,
        newName: MethodNameWithParam
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
            project = projectName,
            oldCommit = oldCommit,
            newCommit = newCommit,
            jumpLength = jumpLength,
            newFileName = newFileName,
            oldMethodName = oldName,
            newMethodName = newName
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
        val inputStream = FileInputStream(projectPath)

        val reader = JsonReader(inputStream.reader())
        reader.isLenient = true
        try {
            reader.use {
                it.beginArray()
                val result = mutableListOf<RawDatasetSample>()
                while (it.hasNext()) {
                    val sample = try {
                        gson.fromJson<RawDatasetSample>(it, RawDatasetSample::class.java)
                    } catch (e: NullPointerException) {
                        null
                    } ?: continue
                    result.add(sample)
                }
                return result
            }
        } catch (e: EOFException) {
            // empty json file
            return emptyList()
        }
    }
}