package com.zakir.vestra.shared.engine.pro

import org.json.JSONObject
import java.io.File
import java.text.Normalizer

/**
 * CLIP BPE tokenizer for SD1.5 text encoding. Loads [vocab.json] and
 * [merges.txt] shipped in the Pro model pack.
 */
class ClipTokenizer(packDir: String) {

    private val encoder: Map<String, Int>
    private val bpeRanks: Map<Pair<String, String>, Int>
    private val byteEncoder: Map<Int, String> = buildByteEncoder()
    private val cache = mutableMapOf<String, String>()

    init {
        val vocabJson = JSONObject(File(packDir, "vocab.json").readText())
        encoder = buildMap {
            vocabJson.keys().forEach { key -> put(key, vocabJson.getInt(key)) }
        }
        bpeRanks = File(packDir, "merges.txt").readLines()
            .drop(1)
            .mapIndexed { index, line ->
                val parts = line.split(" ")
                require(parts.size == 2) { "Bad merge line: $line" }
                parts[0] to parts[1] to index
            }
            .toMap()
    }

    fun encode(text: String, maxLength: Int = MAX_LENGTH): LongArray {
        val tokens = mutableListOf(startToken)
        for (piece in tokenize(clean(text))) {
            encoder[piece]?.let { tokens += it }
        }
        tokens += endToken
        return LongArray(maxLength) { index -> tokens.getOrNull(index)?.toLong() ?: 0L }
    }

    private fun tokenize(text: String): List<String> =
        Regex("""'s|'t|'re|'ve|'m|'ll|'d|[^\s]+""").findAll(text).flatMap { match ->
            bpe(match.value).split(" ").filter { it.isNotEmpty() }
        }.toList()

    private fun bpe(token: String): String = cache.getOrPut(token) {
        var word = token.toByteArray(Charsets.UTF_8)
            .map { byteEncoder[it.toInt() and 0xFF]!! }
        var pairs = pairsOf(word)
        while (pairs.isNotEmpty()) {
            val bigram = pairs.minByOrNull { bpeRanks[it] ?: Int.MAX_VALUE } ?: break
            if (bigram !in bpeRanks) break
            val (first, second) = bigram
            val merged = first + second
            val next = mutableListOf<String>()
            var i = 0
            while (i < word.size) {
                if (i < word.size - 1 && word[i] == first && word[i + 1] == second) {
                    next += merged
                    i += 2
                } else {
                    next += word[i]
                    i += 1
                }
            }
            word = next
            pairs = pairsOf(word)
        }
        word.joinToString(" ")
    }

    private fun pairsOf(word: List<String>): Set<Pair<String, String>> {
        if (word.size < 2) return emptySet()
        return buildSet {
            for (i in 0 until word.size - 1) add(word[i] to word[i + 1])
        }
    }

    private fun clean(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFC)
            .replace(Regex("""\s+"""), " ")
            .trim()
            .lowercase()

    companion object {
        const val MAX_LENGTH = 77
        private const val startToken = 49406
        private const val endToken = 49407

        fun isAvailable(packDir: String): Boolean {
            val dir = File(packDir)
            return File(dir, "vocab.json").exists() && File(dir, "merges.txt").exists()
        }

        private fun buildByteEncoder(): Map<Int, String> {
            val bs = mutableListOf<Int>()
            bs += (33..126)
            bs += (161..172)
            bs += (174..255)
            var n = 0
            val out = mutableMapOf<Int, String>()
            for (b in bs) out[b] = b.toChar().toString()
            for (b in 0..255) {
                if (b !in out) {
                    out[b] = (256 + n).toChar().toString()
                    n++
                }
            }
            return out
        }
    }
}
