import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.types.file
import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationStarter
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.Change
import com.intellij.serviceContainer.AlreadyDisposedException
import git4idea.GitCommit
import git4idea.GitVcs
import git4idea.repo.GitRepositoryManager
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.research.commentupdater.dataset.RawDatasetSample
import org.jetbrains.research.commentupdater.models.MetricsCalculator
import org.jetbrains.research.commentupdater.models.config.ModelFilesConfig
import org.jetbrains.research.commentupdater.processors.MethodChangesExtractor
import org.jetbrains.research.commentupdater.processors.ProjectMethodExtractor
import org.jetbrains.research.commentupdater.processors.RefactoringExtractor
import org.jetbrains.research.commentupdater.utils.PsiUtils
import org.jetbrains.research.commentupdater.utils.qualifiedName
import org.jetbrains.research.commentupdater.utils.textWithoutDoc
import java.util.concurrent.*
import kotlin.system.exitProcess


class PluginRunner : ApplicationStarter {
    override fun getCommandName(): String = "CommentUpdater"

    override fun main(args: Array<String>) {
        CodeCommentExtractor().main(args.drop(1))
    }
}

class CodeCommentExtractor : CliktCommand() {
    private val writeMutex = Mutex()

    private val dataset by argument(help = "Path to dataset").file(mustExist = true, canBeDir = false)
    private val output by argument(help = "Output directory").file(canBeFile = false)
    private val config by argument(help = "Model config").file(canBeFile = false)
    private val statsOutput by argument(help = "Output file for statistic").file(canBeDir = false)

    lateinit var rawSampleWriter: RawSampleWriter
    lateinit var statisticWriter: StatisticWriter

    private val statsHandler = StatisticHandler()

    private lateinit var metricsModel: MetricsCalculator

    companion object {
        private val LOG: Logger =
            Logger.getInstance(PluginRunner::class.java)

        enum class LogLevel {
            INFO, WARN, ERROR
        }

        var projectTag: String = ""
        var projectProcess: String = ""

        fun log(
            level: LogLevel, message: String, logThread: Boolean = false,
            applicationTag: String = "[HeadlessCommentUpdater]"
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
        LOG.info("Logging startup")
        log(LogLevel.INFO, "Starting Application")

        val inputFile = dataset

        metricsModel = MetricsCalculator(ModelFilesConfig(config))

        rawSampleWriter = RawSampleWriter(output)
        statisticWriter = StatisticWriter(statsOutput)
        statisticWriter.open()

        val projectPaths = inputFile.readLines()

        // You want to launch your processing work in different thread,
        // because runnable inside runAfterInitialization is executed after mappings and after this main method ends

        projectPaths.forEachIndexed { index, projectPath ->
            rawSampleWriter.setProjectFile(projectPath)
            projectTag = rawSampleWriter.projectName
            projectProcess = "${index + 1}/${projectPaths.size}"

            try {
                collectProjectExamples(projectPath)

                statisticWriter.saveStatistics(
                    StatisticWriter.ProjectStatistic(
                        projectName = projectTag,
                        numOfMethods = statsHandler.numOfMethods.get(),
                        numOfDocMethods = statsHandler.numOfDocMethods.get()
                    )
                )
                onFinish()
            } catch (e: Exception) {
                log(LogLevel.ERROR, "Failed to process project $projectTag due to $e")
            }


        }

        projectTag = ""
        statisticWriter.close()
        log(LogLevel.INFO, "Finished with ${statsHandler.totalExamplesNumber.get()} examples found.")
        exitProcess(0)

    }


    private fun onFinish() {
        log(LogLevel.INFO, "Close project. ${statsHandler.reportSamples()}")
        rawSampleWriter.close()
        statsHandler.refresh()
    }

    /**
     * Function to close project. The close should be forced to avoid physical changes to data.
     * TODO: Avoid using extended API (check if available in community version)
     */
   fun closeProject(project: Project) =
        try {
            ProjectManagerEx.getInstanceEx().forceCloseProject(project)
        } catch (e: AlreadyDisposedException) {
            // TODO: figure out why this happened
            println(e.message)
        }

    private fun collectProjectExamples(projectPath: String) {
        log(LogLevel.INFO, "Opening project...")
        val project = ProjectUtil.openOrImport(projectPath, null, true)

        if (project == null) {
            log(LogLevel.WARN, "Can't open project $projectPath")
            return
        }
        log(LogLevel.INFO, "Opened!")

        log(LogLevel.INFO, "Initializing vcs..")
        val vcsManager = PsiUtils.vcsSetup(project, projectPath)
        log(LogLevel.INFO, "Initialized!")

        val gitRepoManager = ServiceManager.getService(
            project,
            GitRepositoryManager::class.java
        )

        try {
            val gitRoots = vcsManager.getRootsUnderVcs(GitVcs.getInstance(project))
            log(LogLevel.INFO, "Found ${gitRoots.size} roots for $projectTag")
            for (root in gitRoots) {
                val repo = gitRepoManager.getRepositoryForRoot(root) ?: continue
                runBlocking {
                    val commits = repo.walkAll()
                    statsHandler.numberOfCommits = commits.size

                    commits.map { commit ->
                        async(Dispatchers.Default) {
                            processCommit(commit, project)
                        }
                    }.awaitAll()
                }

            }
        } catch (e: Exception) {
            log(LogLevel.ERROR, "Failed with an exception: ${e.message}")
        } finally {
            closeProject(project)
        }
    }

    private suspend fun processCommit(
        commit: GitCommit,
        project: Project
    ) {
        try {
            commit.filterChanges(".java").forEach { change ->
                val fileName = change.afterRevision?.file?.name ?: ""
                log(
                    LogLevel.INFO,
                    "Commit: ${commit.id.toShortString()} num ~ ${statsHandler.processedCommits.get()}" +
                            "/${statsHandler.numberOfCommits} File changed: $fileName",
                    logThread = true
                )

                collectChange(change, commit, project)
            }
            statsHandler.processedCommits.incrementAndGet()
        } catch (e: Exception) {
            // In case of exception inside commit processing, just continue working and log exception
            // We don't want to fall because of strange mistake on single commit
            log(
                LogLevel.ERROR,
                "Error during commit ${commit.id.toShortString()} processing: ${e.message}",
                logThread = true
            )
        }
    }

    private suspend fun collectChange(
        change: Change,
        commit: GitCommit,
        project: Project
    ) {
        statsHandler.processedFileChanges.incrementAndGet()

        val newFileName = change.afterRevision?.file?.name ?: ""

        val refactorings = RefactoringExtractor.extract(change)
        val methodsStatistic = hashMapOf<String, Int>()
        val changedMethods = try {
            ProjectMethodExtractor.extractChangedMethods(
                project, change, allRefactorings, fileRefactorings,
                statisticContext = methodsStatistic
            )
        } catch (e: VcsException) {
            log(LogLevel.WARN, "Unexpected VCS exception: ${e.message}", logThread = true)
            null
        }

        statsHandler.numOfMethods.addAndGet(methodsStatistic["numOfMethods"]!!)
        statsHandler.numOfDocMethods.addAndGet(methodsStatistic["numOfDocMethods"]!!)

        changedMethods?.let {
            for ((newMethod, updateType) in it) {
                statsHandler.processedMethods.incrementAndGet()
                lateinit var methodName: String
                lateinit var code: String
                lateinit var comment: String

                ApplicationManager.getApplication().runReadAction {
                    methodName = newMethod.qualifiedName
                    code = newMethod.textWithoutDoc
                    comment = newMethod.docComment?.text ?: ""

                }

                statsHandler.foundExamples.incrementAndGet()

                val datasetExample = RawDatasetSample(
                    update = updateType,
                    commitId = commit.id.toString(),
                    newFileName = newFileName,
                    commitTime = commit.timestamp.toString(),
                    code = code,
                    comment = comment,
                    methodName = methodName
                )

                writeMutex.withLock {
                    rawSampleWriter.saveMetrics(datasetExample)
                }
            }
        }
    }
}


