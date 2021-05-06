import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtil.writeToFile
import com.intellij.psi.PsiFileFactory
import com.intellij.vcs.test.VcsPlatformTest
import git4idea.history.GitHistoryUtils
import git4idea.repo.GitRepository
import mygit.test.*
import java.io.File

class GitTest : GitSingleRepoTest() {

    val pluginRunner = PluginRunner()

    fun `test walk repo two simple branches`() {
        /*

            *  (HEAD -> a) 5
            *  (a) 4
            *  (a) 3
            | *  (b) 8
            | *  (b) 7
            | *  (b) 6
            |/
            * (a) 2
            * (a) 1
            * (master) initial
         */

        val file = File(projectPath, "number.txt")

        repo.checkout("-b", "a")

        listOf("1", "2").forEach {
            writeAndCommit(file, it)
        }

        repo.checkout("-b", "b")

        listOf("6", "7", "8").forEach {
            writeAndCommit(file, it)
        }

        repo.checkout("a")

        listOf("3", "4", "5").forEach {
            writeAndCommit(file, it)
        }

        // Update repo.branches (without this line only master is visible for GitRepository)
        repo.update()

        repo.logAll()

        val commitMessages = pluginRunner.walkRepo(repo).map {
            it.fullMessage
        }
        assertEquals(
            commitMessages.sorted(),
            listOf("initial", "1", "2", "3", "4", "5", "6", "7", "8").sorted()
        )
    }

    fun `test walk repo branches with merge`() {
        /*

            * (HEAD -> a) 10
            | * (c) 11
            |/
            *  (a) Merge branch 'b' into a
            |\
            | * (b) 5 (duplicate)
            | * (b) 7
            | * (b) 6
            * | (a) 5
            * | (a) 4
            * | (a) 3
            |/
            * (a) 2
            * (a) 1
            * (master) initial
         */

        val file = File(projectPath, "number.txt")

        repo.checkout("-b", "a")

        listOf("1", "2").forEach {
            writeAndCommit(file, it)
        }

        repo.checkout("-b", "b")

        listOf("6", "7").forEach {
            writeAndCommit(file, it)
        }

        file.writeText("5")
        repo.add()
        repo.commit("5 (duplicate)")

        repo.checkout("a")

        listOf("3", "4", "5").forEach {
            writeAndCommit(file, it)
        }

        git("merge b")


        repo.checkout("-b", "c")

        writeAndCommit(file, "11")

        repo.checkout("a")

        writeAndCommit(file, "10")



        // Update repo.branches (without this line only master is visible for GitRepository)
        repo.update()

        val commitMessages = pluginRunner.walkRepo(repo).map {
            it.fullMessage
        }
        assertEquals(
            commitMessages.sorted(),
            listOf("initial", "1", "2", "3", "4", "5", "6", "7",
            "Merge branch 'b' into a", "5 (duplicate)", "10", "11").sorted()
        )
    }


    fun GitRepository.logAll() {
        println(repo.log("--oneline --decorate --all --graph"))
    }

    fun writeAndCommit(file: File, content: String) {
        file.writeText(content)
        repo.add()
        repo.commit(content)
    }

}