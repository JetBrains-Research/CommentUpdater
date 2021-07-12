import git4idea.GitCommit
import git4idea.history.GitHistoryUtils
import git4idea.repo.GitRepository

fun GitRepository.walkAll(): List<GitCommit> {
    return GitHistoryUtils.history(this.project, this.root, "--all")
}