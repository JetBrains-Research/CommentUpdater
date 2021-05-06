import com.esotericsoftware.minlog.Log
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationStarter
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.progress.Task.Backgroundable
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl
import com.intellij.openapi.vcs.impl.VcsInitObject
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil
import git4idea.GitCommit
import git4idea.GitVcs
import git4idea.history.GitHistoryUtils
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import gr.uom.java.xmi.diff.RenameOperationRefactoring
import org.jetbrains.annotations.NotNull
import org.jetbrains.research.commentupdater.processors.RefactoringExtractor
import org.refactoringminer.api.RefactoringType
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.exitProcess



class PluginRunner: ApplicationStarter {
    override fun getCommandName(): String = "CommentUpdater"

    private val LOG: Logger = Logger.getInstance("#org.jetbrains.research.commentupdater.PluginRunner")

    override fun main(args: Array<out String>) {
        println("How to run IntellijIDEA in headless mode?")
        println("I have no IDEA")

        val projectPath = "C:\\Users\\pavlo\\IdeaProjects\\exampleproject"

        inspectProject(projectPath)
    }

    fun inspectProject(projectPath: String) {
        val project = ProjectUtil.openOrImport(projectPath, null, true) ?: return
        val projectPsiFiles = mutableListOf<PsiFile>()
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


        val vcsManager = ServiceManager.getService(
            project,
            ProjectLevelVcsManager::class.java
        ) as ProjectLevelVcsManagerImpl

        val gitRepoManager = ServiceManager.getService(
            project,
            GitRepositoryManager::class.java
        )

        // Checkout https://intellij-support.jetbrains.com/hc/en-us/community/posts/206105769-Get-project-git-repositories-in-a-project-component
        // To understand why we should call addInitializationRequest
        vcsManager.addInitializationRequest(VcsInitObject.AFTER_COMMON) {
            val gitRoots = vcsManager.getRootsUnderVcs(GitVcs.getInstance(project))
            println("Roots found: ${gitRoots.size}")
            for (root in gitRoots) {
                val repo = gitRepoManager.getRepositoryForRoot(root)
                if (repo != null) {
                    repo
                    GitHistoryUtils.history(project, root).forEach {
                        commit ->
                        commit.changes.filter{
                            // Process only java file changes
                            it.virtualFile?.name?.endsWith(".java") ?: false
                        }.forEach {
                            change ->

                            println("Commit: ${commit.id} Filechanged: ${change.afterRevision?.file?.name ?: ""}")


                            try {
                                val changedMethods = extractChangedMethods(project, change)
                                println(
                                    "Commit: ${commit.id} changes: ${
                                        changedMethods.map {
                                            it.first.name to it.second.name
                                        }
                                    }"
                                )
                            } catch (e: VcsException) {
                                //todo: figure out what causes an exception on RefactorInsight repo
                                LOG.warn("Unexpected VCS exception: ${e.stackTrace}")
                            }
                        }
                    }

                } else {
                    LOG.warn("repo is null for root")
                }
            }
            exitProcess(0)
        }
    }

    fun extractChangedMethods(project: Project, change: Change): MutableList<Pair<PsiMethod, PsiMethod>> {
        val before = change.beforeRevision?.content ?: return mutableListOf()
        val after = change.afterRevision?.content ?: return mutableListOf()


        // todo: ??? Should this be so weird?
        lateinit var beforeFile: PsiFile
        lateinit var afterFile: PsiFile
        lateinit var beforeMethods: List<PsiMethod>
        lateinit var afterMethods: List<Pair<String, PsiMethod>>
        lateinit var oldNamesToMethods: HashMap<String, PsiMethod>
        ApplicationManager.getApplication().runReadAction {
            beforeFile = PsiFileFactory.getInstance(project).createFileFromText(
                "before",
                JavaFileType.INSTANCE,
                before
            )

            afterFile = PsiFileFactory.getInstance(project).createFileFromText(
                "after",
                JavaFileType.INSTANCE,
                after
            )

            beforeMethods = PsiTreeUtil.findChildrenOfType(beforeFile, PsiMethod::class.java).filter {
                it.docComment != null
            }
            afterMethods = PsiTreeUtil.findChildrenOfType(afterFile, PsiMethod::class.java).filter {
                it.docComment != null
            }.map {
                ((it.containingClass?.qualifiedName ?: "") + "." + it.name) to it
            }
            oldNamesToMethods = hashMapOf<String, PsiMethod>(*beforeMethods.map {
                ((it.containingClass?.qualifiedName ?: "") + "." + it.name) to it
            }.toTypedArray())

        }




        val renameMapping = hashMapOf(*RefactoringExtractor.extract(change).filter {
            it.refactoringType == RefactoringType.RENAME_METHOD
        }.map {
            val renameRefactoring = (it as RenameOperationRefactoring)
            (renameRefactoring.renamedOperation.className + "." + renameRefactoring.renamedOperation.name
                    to
                    renameRefactoring.originalOperation.className + "." + renameRefactoring.originalOperation.name)
        }.toTypedArray())

        val changedMethodPairs = mutableListOf<Pair<PsiMethod, PsiMethod>>()
        afterMethods.forEach {
            (afterName, it) ->
            val beforeName = if(renameMapping.containsKey(afterName)) {
                renameMapping[afterName]
            } else {
                afterName
            }
            oldNamesToMethods.get(beforeName)?.let {
                oldMethod ->
                changedMethodPairs.add(oldMethod to it)
            }
        }
        return changedMethodPairs
    }


    fun walkRepo(repo: GitRepository): List<GitCommit> {
        return GitHistoryUtils.history(repo.project, repo.root, "--all")
    }
}


