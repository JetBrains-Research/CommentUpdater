import org.jetbrains.research.commentupdater.CodeCommentDiffs
//import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.Test

class DiffTest{

    val Diffs = CodeCommentDiffs

    @Test
    fun `Test get valid positions`() {
        val searchString = "innuendo"
        val fullString = "innuendo some other words innuendo and also other symbols: ?!@#?"
        assertEquals(
            listOf(0, 4),
            CodeCommentDiffs.getValidPositions(searchString, fullString))
    }

    @Test
    fun `Test computeMinimalCommentDiffs empty tokens`() {
        assertEquals(emptyList(), CodeCommentDiffs.computeMinimalCommentDiffs(emptyList(), emptyList()))
    }

    @Test
    fun `Test computeMinimalCommentDiffs keep tokens`() {
        val tokens = listOf("hello", "world", "i", "love", "kotlin")
        assertEquals(emptyList(), CodeCommentDiffs.computeMinimalCommentDiffs(tokens, tokens))
    }

    @Test
    fun `Test computeMinimalCommentDiffs Simple Replace`() {
        val token1 = "a"
        val token2 = "A"
        assertEquals(
            listOf(Diffs.REPLACE_OLD, token1, Diffs.REPLACE_NEW, token2, Diffs.REPLACE_END),
            Diffs.computeMinimalCommentDiffs(listOf(token1), listOf(token2))
        )
    }

    @Test
    fun `Test computeMinimalCommentDiffs Replace with context`() {
        val oldTokens = listOf("hello", "world", "its", "2020", "!")
        val newTokens = listOf("hello", "world", "its", "2021", "!")
        assertEquals(
            listOf(Diffs.REPLACE_OLD, "2020", Diffs.REPLACE_NEW, "2021", Diffs.REPLACE_END),
            Diffs.computeMinimalCommentDiffs(oldTokens, newTokens)
        )

    }

    @Test
    fun `Test computeMinimalCommentDiffs simple insert`() {
        val oldTokens = listOf("hello", "world", "!")
        val newTokens = listOf("hello", "world", "its", "2021", "!")
        assertEquals(
            listOf(Diffs.INSERT_OLD_KEEP_BEFORE, "world", Diffs.INSERT_NEW_KEEP_BEFORE, "world", "its", "2021", Diffs.INSERT_END),
            Diffs.computeMinimalCommentDiffs(oldTokens, newTokens)
        )
    }

    @Test
    fun `Test computeMinimalCommentDiffs simple delete`() {
        val oldTokens = listOf("hello", "world", "its", "2021", "!")
        val newTokens = listOf("hello", "world", "!")
        assertEquals(
            listOf(Diffs.DELETE, "its", "2021", Diffs.DELETE_END),
            Diffs.computeMinimalCommentDiffs(oldTokens, newTokens)
        )
    }

    @Test
    fun `Test computeMinimalCommentDiffs delete with before adoption`() {
        val oldTokens = listOf("A", "B", "C", "B", "C")
        val newTokens = listOf("A", "B", "C", "C")
        assertEquals(
            listOf(Diffs.REPLACE_OLD_DELETE_KEEP_BEFORE, "C", "B", Diffs.REPLACE_NEW_DELETE_KEEP_BEFORE, "C", Diffs.REPLACE_END),
            Diffs.computeMinimalCommentDiffs(oldTokens, newTokens)
        )
    }

    @Test
    fun `Test computeMinimalCommentDiffs delete with after adoption`() {
        val oldTokens = listOf("A", "B", "A", "B", "C")
        val newTokens = listOf("B", "A", "B", "C")
        assertEquals(
            listOf(Diffs.REPLACE_OLD_DELETE_KEEP_AFTER, "A", "B", "A", Diffs.REPLACE_NEW_DELETE_KEEP_AFTER, "B", "A", Diffs.REPLACE_END),
            Diffs.computeMinimalCommentDiffs(oldTokens, newTokens)
        )
    }

    @Test
    fun `Test computeMinimalCommentDiffs mixed up test`() {
        val oldTokens = listOf("cruel", "hello", "world", "!")
        val newTokens = listOf("cruel", "hello", "cruel", "world", "?")
        assertEquals(
            listOf(Diffs.INSERT_OLD_KEEP_BEFORE, "hello", Diffs.INSERT_NEW_KEEP_BEFORE, "hello", "cruel", Diffs.INSERT_END,
            Diffs.REPLACE_OLD, "!", Diffs.REPLACE_NEW, "?", Diffs.REPLACE_END),
            Diffs.computeMinimalCommentDiffs(oldTokens, newTokens)
        )
    }

}