import com.intellij.ide.highlighter.JavaFileType
import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationStarter
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vcs.changes.Change
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
import javassist.CtNewMethod
import org.jetbrains.research.commentupdater.models.MethodMetric
import org.jetbrains.research.commentupdater.models.MetricsCalculator
import org.jetbrains.research.commentupdater.processors.MethodChangesExtractor
import org.jetbrains.research.commentupdater.processors.RefactoringExtractor
import org.jetbrains.research.commentupdater.utils.qualifiedName
import org.jetbrains.research.commentupdater.utils.textWithoutDoc
import org.refactoringminer.api.Refactoring
import org.refactoringminer.api.RefactoringType
import kotlin.system.exitProcess

data class DatasetExample(
    val oldCode: String,
    val newCode: String,
    val oldComment: String,
    val newComment: String,
    val commitId: String,
    val fileName: String,
    val metric: MethodMetric
)

class PluginRunner : ApplicationStarter {
    override fun getCommandName(): String = "CommentUpdater"

    val datasetExamples = mutableListOf<DatasetExample>()
    val metricsModel = MetricsCalculator()
    private val LOG: Logger = Logger.getInstance("#org.jetbrains.research.commentupdater.PluginRunner")

    override fun main(args: Array<out String>) {
        LOG.info("[HeadlessCommentUpdater] Starting application")

        val projectPath = "C:\\Users\\pavlo\\IdeaProjects\\sample"

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
            for (root in gitRoots) {
                val repo = gitRepoManager.getRepositoryForRoot(root) ?: continue
                repo // todo: debug purpose (suitable breakpoint line), remove in future
                walkRepo(repo).forEach { commit ->
                    getChanges(commit, ".java").forEach { change ->

                        val fileName = change.afterRevision?.file?.name ?: ""
                        LOG.info("[HeadlessCommentUpdater] Commit: ${commit.id} File changed: ${fileName}")

                        val refactorings = RefactoringExtractor.extract(change)
                        val methodsRefactorings = RefactoringExtractor.methodsToRefactoringTypes(refactorings)

                        val changedMethods = try {
                            extractChangedMethods(project, change, refactorings)
                        } catch (e: VcsException) {
                            //todo: figure out what causes an exception on RefactorInsight repo
                            LOG.warn("[HeadlessCommentUpdater] Unexpected VCS exception: ${e.stackTrace}")
                            null
                        }

                        LOG.info(
                            "[HeadlessCommentUpdater] method changes: ${
                                (changedMethods ?: mutableListOf()).map {
                                    it.first.name to it.second.name
                                }
                            }"
                        )

                        changedMethods?.let {
                            for ((oldMethod, newMethod) in it) {
                                lateinit var methodName: String
                                lateinit var oldCode: String
                                lateinit var newCode: String
                                lateinit var oldComment: String
                                lateinit var newComment: String

                                ApplicationManager.getApplication().runReadAction {
                                    methodName = newMethod.qualifiedName
                                    oldCode = oldMethod.textWithoutDoc
                                    newCode = newMethod.textWithoutDoc
                                    oldComment = oldMethod.docComment?.text ?: ""
                                    newComment = newMethod.docComment?.text ?: ""
                                }

                                if (oldCode.trim() == newCode.trim()) {
                                    continue
                                }

                                val methodRefactorings = methodsRefactorings.getOrDefault(
                                    methodName,
                                    mutableListOf()
                                )

                                val metric =
                                    metricsModel.calculateMetrics(oldCode, newCode, newComment, methodRefactorings)
                                        ?: continue

                                saveMetric(commit.id.toShortString(), fileName, oldCode, newCode, oldComment, newComment, metric)

                            }
                        }
                    }
                }

            }
            println(datasetExamples)
            exitProcess(0)
        }
    }

    fun saveMetric(commitId: String, fileName: String, oldCode: String, newCode: String, oldComment: String, newComment: String,
                   metric: MethodMetric) {
        datasetExamples.add(
            DatasetExample(
                oldCode,
                newCode,
                oldComment,
                newComment,
                commitId,
                fileName,
                metric
            )
        )

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

            namesToMethods = hashMapOf<String, PsiMethod>(*methods.map {
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

    fun walkRepo(repo: GitRepository): List<GitCommit> {
        return GitHistoryUtils.history(repo.project, repo.root, "--all")
    }
}


