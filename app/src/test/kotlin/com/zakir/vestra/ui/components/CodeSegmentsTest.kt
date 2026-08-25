package com.zakir.vestra.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure-JVM tests for the fenced-block parser behind the Code tab's output. */
class CodeSegmentsTest {

    @Test
    fun plainAnswerBecomesASingleProseSegment() {
        val segments = CodeSegments.parse("Use a StateFlow in the ViewModel.")
        assertEquals(1, segments.size)
        assertTrue(segments.single() is CodeSegment.Prose)
    }

    @Test
    fun fencedBlockIsSplitFromSurroundingProse() {
        val answer = """
            Here is the card:

            ```kotlin
            @Composable
            fun Card() { }
            ```

            Drop it into your layout.
        """.trimIndent()

        val segments = CodeSegments.parse(answer)
        assertEquals(3, segments.size)
        assertTrue(segments[0] is CodeSegment.Prose)
        val code = segments[1] as CodeSegment.Code
        assertEquals("kotlin", code.language)
        assertTrue(code.code.contains("@Composable"))
        // The fence markers themselves must not survive into the copyable block.
        assertTrue(!code.code.contains("```"))
        assertTrue(segments[2] is CodeSegment.Prose)
    }

    @Test
    fun multipleBlocksEachKeepTheirOwnLanguage() {
        val answer = "First:\n```kotlin\nval a = 1\n```\nThen:\n```bash\n./gradlew build\n```"
        val blocks = CodeSegments.parse(answer).filterIsInstance<CodeSegment.Code>()
        assertEquals(2, blocks.size)
        assertEquals("kotlin", blocks[0].language)
        assertEquals("bash", blocks[1].language)
        assertEquals("./gradlew build", blocks[1].code)
    }

    @Test
    fun fenceWithoutALanguageStillParses() {
        val blocks = CodeSegments.parse("```\nplain block\n```").filterIsInstance<CodeSegment.Code>()
        assertEquals(1, blocks.size)
        assertEquals(null, blocks.single().language)
        assertEquals("plain block", blocks.single().code)
    }

    @Test
    fun emptyAnswerProducesNothingRatherThanABlankBlock() {
        assertTrue(CodeSegments.parse("").isEmpty())
        assertTrue(CodeSegments.parse("   \n  ").isEmpty())
    }

    @Test
    fun unterminatedFenceIsNotSwallowed() {
        // A truncated stream must still show the user what arrived.
        val segments = CodeSegments.parse("Here:\n```kotlin\nval a = 1")
        assertTrue(segments.isNotEmpty())
        assertTrue(segments.any { it is CodeSegment.Prose && it.text.contains("val a = 1") })
    }
}
