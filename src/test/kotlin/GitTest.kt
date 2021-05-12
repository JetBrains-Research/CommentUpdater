import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtil.writeToFile
import com.intellij.psi.PsiFileFactory
import com.intellij.testFramework.UsefulTestCase
import com.intellij.vcs.test.VcsPlatformTest
import git4idea.history.GitHistoryUtils
import git4idea.repo.GitRepository
import junit.framework.TestCase
import mygit.test.*
import java.io.File

class GitTest : GitSingleRepoTest() {

    val pluginRunner = PluginRunner()


    fun GitRepository.logAll() {
        println(repo.log("--oneline --decorate --all --graph"))
    }

    fun writeAndCommit(file: File, content: String) {
        file.writeText(content)
        repo.add()
        repo.commit(content)
    }

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


    // Massive, not unit test, may be divided into parts
    fun `test get changes`() {
        val file = File(projectPath, "test.java")
        file.writeText("""
            class sample {
            }
        """.trimIndent())
        repo.addCommit("test")

        // not unit testing!
        var lastCommit = pluginRunner.walkRepo(repo).find {
            it.id.toString() == repo.last()
        }
        assertNotNull(lastCommit)
        assertEmpty(pluginRunner.getChanges(lastCommit!!, ".java"))


        val newFile = File(projectPath, "test2.java")
        newFile.writeText("""
            class sample2 {
            
            }
        """.trimIndent())

        file.writeText("""
            class sample {
                public int function () {
                    return 1;
                }
            }
        """.trimIndent())
        repo.addCommit("add function")
        lastCommit = pluginRunner.walkRepo(repo).find {
            it.id.toString() == repo.last()
        }
        assertNotNull(lastCommit)
        assertEquals(pluginRunner.getChanges(lastCommit!!, ".java").map {
                it.virtualFile?.name ?: ""
            },
            listOf("test.java")
        )

        newFile.writeText("""
            class sample2 {
                // some changes
            }
        """.trimIndent())

        repo.addCommit("change new file")
        lastCommit = pluginRunner.walkRepo(repo).find {
            it.id.toString() == repo.last()
        }
        assertNotNull(lastCommit)
        assertEquals(pluginRunner.getChanges(lastCommit!!, ".java").map {
            it.virtualFile?.name ?: ""
        },
            listOf("test2.java")
        )


        file.writeText("""
            class sample {
                /**
                * add doc comment
                */
                public int function () {
                    return 1;
                }
            }
        """.trimIndent())

        newFile.writeText("""
            class sample2 {
                // some changes
                public int function() {
                    return 1;
                }
            }
        """.trimIndent())

        repo.addCommit("change new file and file")
        lastCommit = pluginRunner.walkRepo(repo).find {
            it.id.toString() == repo.last()
        }
        assertNotNull(lastCommit)
        assertEquals(pluginRunner.getChanges(lastCommit!!, ".java").map {
            it.virtualFile?.name ?: ""
        }.sorted(),
            listOf("test.java", "test2.java").sorted()
        )

    }

    fun `test extract name changes one method simple renaming`() {
        val file = File(projectPath, "test.java")
        file.writeText("""
            class A {
                public int method(String a) {
                    return a.size();
                }
            }
        """.trimIndent())
        repo.addCommit("method")

        file.writeText("""
            class A {
                public int method1(String a) {
                    return a.size();
                }
            }
        """.trimIndent())
        repo.addCommit("newMethod")
        val lastCommit = pluginRunner.walkRepo(repo).find {
            it.id.toString() == repo.last()
        }
        assertNotNull(lastCommit)

        val changes = pluginRunner.getChanges(lastCommit!!, ".java")
        assertSize(1, changes)
        // todo: doesn't find any refactoring, UML model inside extract doesn't find any class either
        // todo: maybe problem in creating temp files, should i make this on higher level?
        assertEquals(hashMapOf("newMethod" to "method"), pluginRunner.extractNameChanges(changes[0]))
    }

}