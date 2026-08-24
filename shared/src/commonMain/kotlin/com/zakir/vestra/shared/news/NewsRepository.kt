package com.zakir.vestra.shared.news

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import com.zakir.vestra.shared.time.EpochClock

@Serializable
data class NewsItem(
    val id: String,
    val title: String,
    val link: String,
    val publishedMs: Long,
    val source: String,
)

/**
 * Fetches headlines from public RSS feeds (fashion + tech AI).
 */
class NewsRepository(
    private val http: HttpClient,
    private val feeds: List<Pair<String, String>> = DEFAULT_FEEDS,
) {
    private val _items = MutableStateFlow<List<NewsItem>>(emptyList())
    val items: StateFlow<List<NewsItem>> = _items

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    suspend fun refresh() {
        val merged = mutableListOf<NewsItem>()
        var lastError: String? = null
        for ((source, url) in feeds) {
            runCatching {
                val response = http.get(url)
                if (!response.status.isSuccess()) error("HTTP ${response.status.value}")
                parseRss(response.bodyAsText(), source)
            }.onSuccess { merged += it }
                .onFailure { lastError = it.message }
        }
        _items.value = merged
            .distinctBy { it.link }
            .sortedByDescending { it.publishedMs }
            .take(30)
        _error.value = if (merged.isEmpty()) lastError else null
    }

    private fun parseRss(xml: String, source: String): List<NewsItem> {
        val items = mutableListOf<NewsItem>()
        val chunks = xml.split("<item>").drop(1)
        for ((index, chunk) in chunks.withIndex()) {
            val title = tagValue(chunk, "title") ?: continue
            val link = tagValue(chunk, "link") ?: continue
            items += NewsItem(
                id = "$source-$index-${link.hashCode()}",
                title = decodeXml(title),
                link = link.trim(),
                publishedMs = EpochClock.System.nowMs() - index * 3_600_000L,
                source = source,
            )
        }
        return items
    }

    fun headlineContext(max: Int = 5): String =
        _items.value.take(max).joinToString("\n") { "• ${it.title} (${it.source})" }

    /** Visible for unit tests. */
    internal fun parseRssForTest(xml: String, source: String): List<NewsItem> = parseRss(xml, source)

    internal fun seedItemsForTest(items: List<NewsItem>) {
        _items.value = items
    }

    private fun tagValue(xml: String, tag: String): String? {
        val open = "<$tag"
        val start = xml.indexOf(open)
        if (start < 0) return null
        val contentStart = xml.indexOf('>', start) + 1
        val end = xml.indexOf("</$tag>", contentStart)
        if (end < 0) return null
        return xml.substring(contentStart, end).trim()
            .removePrefix("<![CDATA[").removeSuffix("]]>")
    }

    private fun decodeXml(s: String): String = s
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")

    companion object {
        val DEFAULT_FEEDS = listOf(
            "BBC Tech" to "https://feeds.bbci.co.uk/news/technology/rss.xml",
            "Hugging Face" to "https://huggingface.co/blog/feed.xml",
        )
    }
}
