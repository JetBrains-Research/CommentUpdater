import com.intellij.vcs.test.VcsPlatformTest
import mygit.test.GitSingleRepoTest
import mygit.test.createRepository

class GitTest : GitSingleRepoTest() {

    fun `test set up working`() {
        println(repo.info)
    }
}