package com.zakir.vestra.data

import android.content.Context
import com.zakir.vestra.shared.news.NewsRepository
import org.json.JSONArray

object NewsFeedConfig {
    fun load(context: Context): List<Pair<String, String>> =
        try {
            val text = context.assets.open("news_feeds.json").bufferedReader().readText()
            val array = JSONArray(text)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    add(obj.getString("source") to obj.getString("url"))
                }
            }
        } catch (_: Exception) {
            NewsRepository.DEFAULT_FEEDS
        }
}
