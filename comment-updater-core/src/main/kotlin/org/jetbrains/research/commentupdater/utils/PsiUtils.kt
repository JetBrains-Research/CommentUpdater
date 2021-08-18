package org.jetbrains.research.commentupdater.utils

import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiFile
import git4idea.GitVcs
import git4idea.config.GitVcsApplicationSettings
import java.io.File

object PsiUtils {
    private val LOG = Logger.getInstance(PsiUtils::class.java)
    fun getNumberOfLine(file: PsiFile, offset: Int): Int {
        val fileViewProvider = file.viewProvider
        val document = fileViewProvider.document
        return if (document != null) document.getLineNumber(offset) + 1 else 0
    }

    /**
     * Setups VCS to get access to the project's Git root
     */
    fun vcsSetup(project: Project, projectPath: String): ProjectLevelVcsManagerImpl {
        VfsUtil.markDirtyAndRefresh(false, true, false, File(projectPath))
        val vcsManager = ProjectLevelVcsManager.getInstance(project) as ProjectLevelVcsManagerImpl
        ApplicationManager.getApplication().invokeAndWait(vcsManager::waitForInitialized)
        val vcs = GitVcs.getInstance(project)
        try {
            vcs.doActivate()
        } catch (e: VcsException) {
            LOG.error("Error occurred while VCS setup.")
        }
        val appSettings = GitVcsApplicationSettings.getInstance()
        appSettings.setPathToGit(findGitExecutable())
        return vcsManager
    }

    fun findGitExecutable(): String {
        return findExecutable("Git", "git", "git.exe", listOf("IDEA_TEST_GIT_EXECUTABLE"))
    }

    private fun findExecutable(
        programName: String,
        unixExec: String,
        winExec: String,
        envs: Collection<String>
    ): String {
        val exec = findEnvValue(envs)
        if (exec != null) {
            return exec
        }
        val fileExec = PathEnvironmentVariableUtil.findInPath(if (SystemInfo.isWindows) winExec else unixExec)
        if (fileExec != null) {
            return fileExec.absolutePath
        }
        throw IllegalStateException(
            "$programName executable not found. " + if (envs.size > 0) "Please define a valid environment variable " +
                    envs.iterator().next() +
                    " pointing to the " +
                    programName +
                    " executable." else ""
        )
    }

    private fun findEnvValue(envs: Collection<String>): String? {
        for (env in envs) {
            val `val` = System.getenv(env)
            if (`val` != null && File(`val`).canExecute()) {
                return `val`
            }
        }
        return null
    }
}
