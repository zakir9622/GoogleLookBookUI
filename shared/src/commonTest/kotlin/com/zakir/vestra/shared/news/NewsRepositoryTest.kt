package com.zakir.vestra.shared.news

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NewsRepositoryTest {

    @Test
    fun parseRssExtractsTitlesAndLinks() {
        val xml = """
            <?xml version="1.0"?>
            <rss><channel>
              <item>
                <title><![CDATA[Modest fashion AI on device]]></title>
                <link>https://example.com/story</link>
              </item>
              <item>
                <title>Second headline</title>
                <link>https://example.com/two</link>
              </item>
            </channel></rss>
        """.trimIndent()
        val repo = NewsRepository(io.ktor.client.HttpClient())
        val items = repo.parseRssForTest(xml, "Test")
        assertEquals(2, items.size)
        assertEquals("Modest fashion AI on device", items[0].title)
        assertEquals("https://example.com/story", items[0].link)
        assertTrue(items[0].id.startsWith("Test-"))
    }

    @Test
    fun headlineContextFormatsBullets() {
        val repo = NewsRepository(io.ktor.client.HttpClient())
        repo.seedItemsForTest(
            listOf(
                NewsItem("1", "Alpha", "https://a", 1000, "Src"),
                NewsItem("2", "Beta", "https://b", 900, "Src"),
            ),
        )
        val ctx = repo.headlineContext(2)
        assertTrue(ctx.contains("Alpha"))
        assertTrue(ctx.contains("Beta"))
    }
}
