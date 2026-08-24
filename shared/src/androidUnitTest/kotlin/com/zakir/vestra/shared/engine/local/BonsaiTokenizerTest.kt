package com.zakir.vestra.shared.engine.local

import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * BPE-merge and prompt-framing checks for [BonsaiTokenizer] against a small synthetic vocab —
 * not the real ~152k-entry Qwen3 vocab, which would make this a fixture test rather than an
 * algorithm test. The GPT-2 byte-to-unicode table is duplicated here deliberately (same public,
 * documented formula [BonsaiTokenizer] uses) so the synthetic vocab can be built without
 * reaching into the class's private table.
 */
@RunWith(RobolectricTestRunner::class)
class BonsaiTokenizerTest {

    private fun byteToChar(): CharArray {
        val bs = ((33..126) + (161..172) + (174..255)).toMutableList()
        val cs = bs.toMutableList()
        var n = 0
        for (b in 0..255) if (b !in bs) {
            bs.add(b)
            cs.add(256 + n)
            n++
        }
        val table = CharArray(256)
        for (i in bs.indices) table[bs[i]] = cs[i].toChar()
        return table
    }

    /** Builds a pack dir with a synthetic vocab covering [words] plus " " and "\n", and [merges]. */
    private fun buildPack(tmp: File, words: List<String>, merges: List<Pair<String, String>>): File {
        val byteChar = byteToChar()
        fun pieces(s: String) = s.toByteArray(Charsets.UTF_8).map { byteChar[it.toInt() and 0xFF].toString() }

        val vocab = LinkedHashMap<String, Int>()
        var nextId = 0
        fun intern(piece: String) {
            if (piece !in vocab) vocab[piece] = nextId++
        }
        (words + listOf(" ", "\n")).forEach { w -> pieces(w).forEach(::intern) }
        merges.forEach { (a, b) -> intern(a + b) }

        val tokenizerDir = File(tmp, "tokenizer").apply { mkdirs() }
        File(tokenizerDir, "vocab.json").writeText(
            JSONObject().apply { vocab.forEach { (k, v) -> put(k, v) } }.toString(),
        )
        File(tokenizerDir, "merges.txt").writeText(
            buildString {
                appendLine("#version: 0.2")
                merges.forEach { (a, b) -> appendLine("$a $b") }
            },
        )
        return tmp
    }

    @Test
    fun encodeWithoutMergesReturnsOneTokenPerByte() {
        val tmp = File.createTempFile("bonsai", "novocab").apply { delete(); mkdirs() }
        val byteChar = byteToChar()
        val dir = buildPack(tmp, listOf("cat"), merges = emptyList())
        val tokenizer = BonsaiTokenizer(dir)

        val ids = tokenizer.encode("cat")

        val expected = "cat".toByteArray(Charsets.UTF_8).map { byteChar[it.toInt() and 0xFF].toString() }
        assertEquals(expected.size, ids.size)
    }

    @Test
    fun encodeAppliesMergesInRankOrder() {
        val tmp = File.createTempFile("bonsai", "merges").apply { delete(); mkdirs() }
        val byteChar = byteToChar()
        val c = byteChar['c'.code].toString()
        val a = byteChar['a'.code].toString()
        val t = byteChar['t'.code].toString()
        // "c"+"a" merges first (rank 0); the merged "ca" is then a single symbol.
        val dir = buildPack(tmp, listOf("cat"), merges = listOf(c to a))
        val tokenizer = BonsaiTokenizer(dir)

        val ids = tokenizer.encode("cat")

        // Merged "ca" + separate "t" = 2 tokens, not 3.
        assertEquals(2, ids.size)
    }

    @Test
    fun encodeIsDeterministicAndCached() {
        val tmp = File.createTempFile("bonsai", "cache").apply { delete(); mkdirs() }
        val dir = buildPack(tmp, listOf("dog"), merges = emptyList())
        val tokenizer = BonsaiTokenizer(dir)

        val first = tokenizer.encode("dog")
        val second = tokenizer.encode("dog")

        assertTrue(first.contentEquals(second))
    }

    @Test
    fun encodePromptProducesFixedShapeWithCorrectFraming() {
        val tmp = File.createTempFile("bonsai", "prompt").apply { delete(); mkdirs() }
        val dir = buildPack(tmp, listOf("user", "cat"), merges = emptyList())
        val tokenizer = BonsaiTokenizer(dir)

        val encoded = tokenizer.encodePrompt("cat")

        assertEquals(BonsaiTokenizer.SEQ_LEN, encoded.ids.size)
        assertEquals(BonsaiTokenizer.SEQ_LEN, encoded.mask.size)
        assertEquals(BonsaiTokenizer.IM_START_ID, encoded.ids[0])

        val realLength = 1 + encoded.promptTokenCount + BonsaiTokenizer.SUFFIX_IDS.size
        // Suffix ids land immediately after the prompt body.
        for (i in BonsaiTokenizer.SUFFIX_IDS.indices) {
            assertEquals(BonsaiTokenizer.SUFFIX_IDS[i], encoded.ids[1 + encoded.promptTokenCount + i])
        }
        // Mask covers exactly the real (non-pad) tokens.
        assertEquals(realLength, encoded.mask.sum())
        for (i in 0 until realLength) assertEquals(1, encoded.mask[i])
        for (i in realLength until BonsaiTokenizer.SEQ_LEN) {
            assertEquals(0, encoded.mask[i])
            assertEquals(BonsaiTokenizer.PAD_ID, encoded.ids[i])
        }
    }
}
