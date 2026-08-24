package com.zakir.vestra.shared.engine.local

import org.json.JSONObject
import java.io.File

/**
 * Qwen3 tokenizer (byte-level BPE) in pure Kotlin — enough to feed the Bonsai Image 4B
 * text encoder on device. Loads `vocab.json` + `merges.txt` from the downloaded
 * `local-bonsai-image-v1` pack. The chat template is applied structurally: the graph
 * input is `[<|im_start|>]` + BPE("user\n" + prompt) + a fixed assistant suffix, which
 * reproduces `transformers.apply_chat_template(..., enable_thinking=False)`. `"user\n"`
 * is BPE'd together with the prompt — a prompt starting with whitespace merges across
 * that boundary.
 *
 * Ported from the Apache-2.0 `QwenTokenizer.kt` at
 * https://github.com/john-rocky/hf-to-litertlm/tree/main/bonsai_image_work/device/BonsaiAppAndroid
 * (Daisuke Majima), itself a port of the model's Swift reference implementation.
 *
 * Not handled (irrelevant for an image-prompt box): special-token strings typed
 * literally inside the prompt are tokenized as plain text.
 */
class BonsaiTokenizer(packDir: File) {
    companion object {
        const val SEQ_LEN = 256
        const val PAD_ID = 151643 // <|endoftext|>
        const val IM_START_ID = 151644 // <|im_start|>
        // "<|im_end|>\n<|im_start|>assistant\n<think>\n\n</think>\n\n"
        val SUFFIX_IDS = intArrayOf(151645, 198, 151644, 77091, 198, 151667, 271, 151668, 271)

        // Qwen2 pre-tokenization pattern, verbatim from tokenizer.json.
        // java.util.regex: (?i:...) group works; \p{L}/\p{N} need UNICODE flags.
        private val PRETOKEN = Regex(
            "(?i:'s|'t|'re|'ve|'m|'ll|'d)|[^\\r\\n\\p{L}\\p{N}]?\\p{L}+|\\p{N}|" +
                " ?[^\\s\\p{L}\\p{N}]+[\\r\\n]*|\\s*[\\r\\n]+|\\s+(?!\\S)|\\s+",
            setOf(RegexOption.UNIX_LINES),
        )

        // GPT-2 bytes-to-unicode: printable bytes map to themselves, the rest to U+0100...
        // so every byte is a distinct printable char and ' ' never appears inside a symbol
        // (making "left right" rank keys safe).
        private val BYTE_CHAR: CharArray = run {
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
            table
        }
    }

    private val vocab = HashMap<String, Int>(160_000)
    private val ranks = HashMap<String, Int>(160_000)
    private val cache = HashMap<String, IntArray>()

    init {
        val jo = JSONObject(File(packDir, "tokenizer/vocab.json").readText())
        for (key in jo.keys()) vocab[key] = jo.getInt(key)
        File(packDir, "tokenizer/merges.txt").forEachLine { line ->
            if (line.isNotEmpty() && !line.startsWith("#")) ranks[line] = ranks.size
        }
    }

    class Encoded(val ids: IntArray, val mask: IntArray, val promptTokenCount: Int)

    /**
     * Full graph input for one user prompt: template + pad to 256, right-pad mask.
     * Over-long prompts are truncated so the assistant suffix survives.
     */
    fun encodePrompt(prompt: String): Encoded {
        var body = encode("user\n" + prompt)
        val maxBody = SEQ_LEN - 1 - SUFFIX_IDS.size
        if (body.size > maxBody) body = body.copyOf(maxBody)
        val real = 1 + body.size + SUFFIX_IDS.size
        val ids = IntArray(SEQ_LEN) { PAD_ID }
        val mask = IntArray(SEQ_LEN)
        ids[0] = IM_START_ID
        body.copyInto(ids, 1)
        SUFFIX_IDS.copyInto(ids, 1 + body.size)
        for (i in 0 until real) mask[i] = 1
        return Encoded(ids, mask, body.size)
    }

    /** Byte-level BPE of plain text (no special-token splitting). */
    fun encode(text: String): IntArray {
        val nfc = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFC)
        val out = ArrayList<Int>(nfc.length / 3 + 8)
        for (m in PRETOKEN.findAll(nfc)) {
            if (m.value.isEmpty()) continue
            bpe(m.value).forEach { out.add(it) }
        }
        return out.toIntArray()
    }

    private fun bpe(pretoken: String): IntArray {
        cache[pretoken]?.let { return it }
        var word = pretoken.toByteArray(Charsets.UTF_8).map {
            BYTE_CHAR[it.toInt() and 0xFF].toString()
        }
        while (word.size > 1) {
            var best = Int.MAX_VALUE
            var at = -1
            for (i in 0 until word.size - 1) {
                val r = ranks[word[i] + " " + word[i + 1]] ?: continue
                if (r < best) {
                    best = r
                    at = i
                }
            }
            if (at < 0) break
            val a = word[at]
            val b = word[at + 1]
            val merged = ArrayList<String>(word.size)
            var i = 0
            while (i < word.size) {
                if (i < word.size - 1 && word[i] == a && word[i + 1] == b) {
                    merged.add(a + b)
                    i += 2
                } else {
                    merged.add(word[i])
                    i += 1
                }
            }
            word = merged
        }
        // Byte-level alphabet: every symbol/merge result exists in the vocab.
        val ids = word.mapNotNull { vocab[it] }.toIntArray()
        cache[pretoken] = ids
        return ids
    }
}
