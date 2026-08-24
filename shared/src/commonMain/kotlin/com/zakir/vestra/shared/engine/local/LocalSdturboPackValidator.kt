package com.zakir.vestra.shared.engine.local

/**
 * Pure pack layout checks for `local-sdturbo-v1` — shared by Android generator and JVM tests.
 */
object LocalSdturboPackValidator {
    const val PACK_ID = "local-sdturbo-v1"
    const val MIN_GRAPH_BYTES = 1_000_000L

    fun graphNames(config: LocalImagePackConfig): List<String> = listOfNotNull(
        config.graphs?.textEncoder,
        config.graphs?.unet,
        config.graphs?.vaeDecoder,
    )

    fun missingGraphs(
        config: LocalImagePackConfig,
        fileBytes: (String) -> Long?,
    ): List<String> {
        val names = graphNames(config)
        if (names.isEmpty()) return listOf("graphs")
        return names.filter { name ->
            val bytes = fileBytes(name) ?: return@filter true
            bytes < MIN_GRAPH_BYTES
        }
    }

    fun hasTokenizer(fileExists: (String) -> Boolean): Boolean =
        fileExists("vocab.json") && fileExists("merges.txt")

    fun isComplete(
        config: LocalImagePackConfig,
        fileBytes: (String) -> Long?,
        fileExists: (String) -> Boolean,
    ): Boolean = missingGraphs(config, fileBytes).isEmpty() && hasTokenizer(fileExists)
}
