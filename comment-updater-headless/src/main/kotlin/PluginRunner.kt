import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationStarter
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl
import git4idea.GitCommit
import git4idea.GitVcs
import git4idea.repo.GitRepositoryManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.jetbrains.research.commentupdater.dataset.DatasetSample
import org.jetbrains.research.commentupdater.models.MetricsCalculator
import org.jetbrains.research.commentupdater.processors.ProjectMethodExtractor
import org.jetbrains.research.commentupdater.processors.RefactoringExtractor
import org.jetbrains.research.commentupdater.utils.qualifiedName
import org.jetbrains.research.commentupdater.utils.textWithoutDoc
import java.io.File
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread
import kotlin.system.exitProcess

class PluginRunner : ApplicationStarter {
    override fun getCommandName(): String = "CommentUpdater"

    override fun main(args: Array<String>) {
        CodeCommentExtractor().main(args)
    }
}

class CodeCommentExtractor {
    // Reading project paths
    private val inputPath = HeadlessConfig.INPUT_FILE

    private val exampleWriter = SampleWriter()

    private val statsHandler = StatisticHandler()

    // Metric model
    private val metricsModel = MetricsCalculator()

    companion object {
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

    fun main(args: Array<out String>) {
        log(LogLevel.INFO, "Starting Application")

        val inputFile = File(inputPath)

        val projectPaths = inputFile.readLines()

        // You want to launch your processing work in different thread,
        // because runnable inside runAfterInitialization is executed after mappings and after this main method ends
        thread(start = true) {
            projectPaths.forEachIndexed { index, projectPath ->

                exampleWriter.setProjectFile(projectPath)
                projectTag = exampleWriter.projectName
                projectProcess = "${index + 1}/${projectPaths.size}"

                onStart()

                collectProjectExamples(projectPath)

                onFinish()
            }

            projectTag = ""
            log(LogLevel.INFO, "Finished with ${statsHandler.totalExamplesNumber.get()} examples found.")
            exitProcess(0)
        }
    }

    private fun onStart() {
        log(LogLevel.INFO, "Open project")
        exampleWriter.open()
    }

    private fun onFinish() {
        log(LogLevel.INFO, "Close project. ${statsHandler.report()}")
        exampleWriter.close()
        statsHandler.refresh()
    }

    private fun collectProjectExamples(projectPath: String) {
        val project = ProjectUtil.openOrImport(projectPath, null, true)
        if (project == null) {
            log(LogLevel.WARN, "Can't open project $projectPath")
            return
        }

        val vcsManager = ServiceManager.getService(
            project,
            ProjectLevelVcsManager::class.java
        ) as ProjectLevelVcsManagerImpl

        val gitRepoManager = ServiceManager.getService(
            project,
            GitRepositoryManager::class.java
        )

        // Checkout https://intellij-support.jetbrains.com/hc/en-us/community/posts/206105769-Get-project-git-repositories-in-a-project-component
        // To understand why we should call runAfterInitialization and create this runnable

        val latch = CountDownLatch(1)
        val runnable = {
            try {
                val gitRoots = vcsManager.getRootsUnderVcs(GitVcs.getInstance(project))
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
                latch.countDown()
            }
        }

        vcsManager.runAfterInitialization(runnable)
        latch.await()
    }

    private fun processCommit(
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

    private fun collectChange(
        change: Change,
        commit: GitCommit,
        project: Project
    ) {
        statsHandler.processedFileChanges.incrementAndGet()

        val newFileName = change.afterRevision?.file?.name ?: ""

        val refactorings = RefactoringExtractor.extract(change)
        val methodsRefactorings = RefactoringExtractor.methodsToRefactoringTypes(refactorings)

        val changedMethods = try {
            ProjectMethodExtractor.extractChangedMethods(project, change, refactorings)
        } catch (e: VcsException) {
            log(LogLevel.WARN, "Unexpected VCS exception: ${e.message}", logThread = true)
            null
        }

        changedMethods?.let {
            for ((oldMethod, newMethod) in it) {
                statsHandler.processedMethods.incrementAndGet()
                lateinit var newMethodName: String
                lateinit var oldMethodName: String
                lateinit var oldCode: String
                lateinit var newCode: String
                lateinit var oldComment: String
                lateinit var newComment: String

                ApplicationManager.getApplication().runReadAction {
                    newMethodName = newMethod.qualifiedName
                    oldMethodName = oldMethod.qualifiedName
                    oldCode = oldMethod.textWithoutDoc
                    newCode = newMethod.textWithoutDoc
                    oldComment = oldMethod.docComment?.text ?: ""
                    newComment = newMethod.docComment?.text ?: ""
                }

                if (oldCode.trim() == newCode.trim() && oldComment.trim() == newComment.trim()) {
                    continue
                }

                val methodRefactorings = methodsRefactorings.getOrDefault(
                    newMethodName,
                    mutableListOf()
                )

                val metric =
                    metricsModel.calculateMetrics(oldCode, newCode, oldComment, newComment, methodRefactorings)
                        ?: continue

                statsHandler.foundExamples.incrementAndGet()

                val datasetExample = DatasetSample(
                    oldCode = oldCode,
                    newCode = newCode,
                    oldComment = oldComment,
                    newComment = newComment,
                    commitId = commit.id.toString(),
                    newFileName = newFileName,
                    metric = metric,
                    commitTime = commit.timestamp.toString(),
                    oldMethodName = oldMethodName,
                    newMethodName = newMethodName
                )
                exampleWriter.saveMetrics(datasetExample)

            }
        }
    }
}


