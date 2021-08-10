import com.intellij.openapi.vcs.changes.Change
import git4idea.GitCommit
import git4idea.history.GitHistoryUtils
import git4idea.repo.GitRepository

fun GitCommit.filterChanges(fileSuffix: String): List<Change> {
    return this.changes
        .filter {
            it.afterRevision != null
        }
        .filter {
            // not null and true
            it.virtualFile?.name?.endsWith(fileSuffix) == true
        }
}

fun GitRepository.walkAll(): List<GitCommit> {
    return GitHistoryUtils.history(this.project, this.root, "--all")
}
