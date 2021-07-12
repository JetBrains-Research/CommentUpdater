import com.intellij.ide.highlighter.JavaFileType
import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationStarter
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil
import git4idea.GitCommit
import git4idea.GitVcs
import git4idea.repo.GitRepositoryManager
import gr.uom.java.xmi.diff.RenameOperationRefactoring
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.jetbrains.research.commentupdater.models.MetricsCalculator
import org.jetbrains.research.commentupdater.processors.RefactoringExtractor
import org.jetbrains.research.commentupdater.utils.qualifiedName
import org.jetbrains.research.commentupdater.utils.textWithoutDoc
import org.refactoringminer.api.*
import java.io.File
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread
import kotlin.system.exitProcess


class PluginRunner: ApplicationStarter {
    override fun getCommandName(): String = "CommentUpdater"
    override fun main(args: Array<String>) {
        CodeCommentExtractor().main(args)
    }
}


class CodeCommentExtractor {

    // Reading project paths
    val inputPath = HeadlessConfig.INPUT_FILE

    val exampleWriter = ExampleWriter()

    val statsHandler = StatisticHandler()

    // Metric model
    val metricsModel = MetricsCalculator()


    companion object {
        enum class LogLevel {
            INFO, WARN, ERROR
        }

        var projectTag: String = ""
        var projectProcess: String = ""

        fun  log(level: LogLevel, message: String, logThread: Boolean = false,
                 applicationTag: String = "[HeadlessCommentUpdater]") {
            val fullLogMessage = "$level ${if (logThread) Thread.currentThread().name else ""} $applicationTag [$projectTag $projectProcess] $message"

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
        thread (start = true){
            projectPaths.forEachIndexed {
                index, projectPath ->

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


    fun onStart() {
        log(LogLevel.INFO, "Open project")
        exampleWriter.open()
    }

    fun onFinish() {
        log(LogLevel.INFO, "Close project. ${statsHandler.report()}")
        exampleWriter.close()
        statsHandler.refresh()
    }


    fun collectProjectExamples(projectPath: String) {
        val latch = CountDownLatch(1)

        val project = ProjectUtil.openOrImport(projectPath, null, true)
        if( project == null) {
            log(LogLevel.WARN, "Can't open project $projectPath")
            return
        }
        val projectPsiFiles = mutableListOf<PsiFile>()

        ApplicationManager.getApplication().runReadAction {
            ProjectRootManager.getInstance(project).contentRoots.mapNotNull { root ->
                VfsUtilCore.iterateChildrenRecursively(root, null) { virtualFile ->
                    if (virtualFile.extension != "java" || virtualFile.canonicalPath == null) {
                        return@iterateChildrenRecursively true
                    }
                    val psi =
                        PsiManager.getInstance(project).findFile(virtualFile) ?: return@iterateChildrenRecursively true
                    projectPsiFiles.add(psi)
                }
            }
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

        val runnable = {
            try {
                val gitRoots = vcsManager.getRootsUnderVcs(GitVcs.getInstance(project))
                for (root in gitRoots) {
                    val repo = gitRepoManager.getRepositoryForRoot(root) ?: continue
                    runBlocking {
                        val commits = repo.walkAll()
                        val numOfCommits = commits.size

                        commits.map { commit ->
                            async(Dispatchers.Default) {
                                processCommit(commit, numOfCommits, project)
                            }
                        }.awaitAll()
                    }

                }
            } catch (e: Exception) {
                log(LogLevel.ERROR, "Failed with an exception: $e")
                e.printStackTrace()
            }
            finally {
                latch.countDown()
            }
        }

        vcsManager.runAfterInitialization(runnable)
        latch.await()
    }

    fun processCommit(
        commit: GitCommit,
        numOfCommits: Int,
        project: Project
    ) {
        try {
            getChanges(commit, ".java").forEach { change ->
                val fileName = change.afterRevision?.file?.name ?: ""
                log(
                    LogLevel.INFO,
                    "Commit: ${commit.id.toShortString()} num ~ ${statsHandler.processedCommits.get()}" +
                            "/$numOfCommits File changed: $fileName",
                    logThread = true
                )

                collectChange(change, commit, project)
            }
            statsHandler.processedCommits.incrementAndGet()
        } catch (e: Exception) {
            // In case of exception inside commit processing, just continue working and log exception
            // We don't want to fall because of strange mistake on single commit
            log(LogLevel.ERROR, "Error during commit ${commit.id.toShortString()} processing", logThread = true)
            e.printStackTrace()
        }
    }


    fun collectChange(
        change: Change,
        commit: GitCommit,
        project: Project) {
        statsHandler.processedFileChanges.incrementAndGet()

        val fileName = change.afterRevision?.file?.name ?: ""

        val refactorings = RefactoringExtractor.extract(change)
        val methodsRefactorings = RefactoringExtractor.methodsToRefactoringTypes(refactorings)

        val changedMethods = try {
            extractChangedMethods(project, change, refactorings)
        } catch (e: VcsException) {
            log(LogLevel.WARN, "Unexpected VCS exception", logThread = true)
            e.printStackTrace()
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


                val datasetExample = DatasetExample(
                    oldCode = oldCode,
                    newCode = newCode,
                    oldComment = oldComment,
                    newComment = newComment,
                    commitId = commit.id.toString(),
                    fileName = fileName,
                    metric = metric,
                    commitTime = commit.timestamp.toString(),
                    oldMethodName = oldMethodName,
                    newMethodName = newMethodName
                )
                exampleWriter.saveMetric(datasetExample)

            }
        }
    }



    /**
     * @return: Map from old method name to new method name
     */
    fun extractNameChanges(refactorings: List<Refactoring>): HashMap<String, String> {
        return hashMapOf(*refactorings.filter {
            it.refactoringType == RefactoringType.RENAME_METHOD
        }.map {
            val renameRefactoring = (it as RenameOperationRefactoring)
            (renameRefactoring.renamedOperation.className + "." + renameRefactoring.renamedOperation.name
                    to
                    renameRefactoring.originalOperation.className + "." + renameRefactoring.originalOperation.name)
        }.toTypedArray())
    }

    /**
     * @return: List of pairs oldMethod to newMethod
     */
    fun extractChangedMethods(
        project: Project,
        change: Change,
        refactorings: List<Refactoring>
    ): MutableList<Pair<PsiMethod, PsiMethod>> {
        val before = change.beforeRevision?.content ?: return mutableListOf()
        val after = change.afterRevision?.content ?: return mutableListOf()

        val renameMapping = extractNameChanges(refactorings)

        val changedMethodPairs = mutableListOf<Pair<PsiMethod, PsiMethod>>()

        val oldNamesToMethods = extractNamesToMethods(project, before)

        val newMethods = extractMethodsWithNames(project, after)

        newMethods.forEach { (afterName, newMethod) ->
            val beforeName = if (renameMapping.containsKey(afterName)) {
                renameMapping[afterName]
            } else {
                afterName
            }
            oldNamesToMethods.get(beforeName)?.let { oldMethod ->
                changedMethodPairs.add(oldMethod to newMethod)
            }


        }
        return changedMethodPairs
    }

    fun extractMethodsWithNames(project: Project, content: String): List<Pair<String, PsiMethod>> {
        lateinit var psiFile: PsiFile
        lateinit var methodsWithNames: List<Pair<String, PsiMethod>>

        ApplicationManager.getApplication().runReadAction {
            psiFile = PsiFileFactory.getInstance(project).createFileFromText(
                "extractMethodsWithNamesFile",
                JavaFileType.INSTANCE,
                content
            )

            methodsWithNames = PsiTreeUtil.findChildrenOfType(psiFile, PsiMethod::class.java).filter {
                it.docComment != null
            }.map {
                ((it.containingClass?.qualifiedName ?: "") + "." + it.name) to it
            }

        }

        return methodsWithNames
    }

    fun extractNamesToMethods(project: Project, content: String): HashMap<String, PsiMethod> {
        lateinit var psiFile: PsiFile
        lateinit var methods: List<PsiMethod>
        lateinit var namesToMethods: HashMap<String, PsiMethod>

        ApplicationManager.getApplication().runReadAction {
            psiFile = PsiFileFactory.getInstance(project).createFileFromText(
                "extractNamesToMethodsFile",
                JavaFileType.INSTANCE,
                content
            )

            methods = PsiTreeUtil.findChildrenOfType(psiFile, PsiMethod::class.java).filter {
                it.docComment != null
            }

            namesToMethods = hashMapOf(*methods.map {
                ((it.containingClass?.qualifiedName ?: "") + "." + it.name) to it
            }.toTypedArray())
        }

        return namesToMethods
    }

    fun getChanges(commit: GitCommit, fileSuffix: String): List<Change> {
        return commit.changes
            .filter {
                it.afterRevision != null
                        &&
                        it.beforeRevision != null
            }
            .filter {
                // not null and true
                it.virtualFile?.name?.endsWith(fileSuffix) == true
            }
    }


}


