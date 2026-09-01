package com.example.readtrace.sync

import android.content.Context
import com.example.readtrace.data.BookDatabaseHelper
import com.example.readtrace.model.Book
import com.example.readtrace.model.BookMindprint
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/**
 * 🌐 微信小程序与跨端同构数据同步协议 (Wechat Mini-App Sync Protocol)
 * 为后续 Uni-app 微信小程序端和 Web 端的双轨数据漫游提供契约与标准格式。
 */
object WechatMinappSyncProtocol {

    const val PROTOCOL_VERSION = "1.0.0"

    data class SyncSummary(
        val totalBooks: Int,
        val totalMindprints: Int,
        val generatedAt: String,
        val protocolVersion: String = PROTOCOL_VERSION,
    )

    /**
     * 生成供小程序/跨端同步的紧凑型 JSON 数据流
     */
    fun exportToMinappPayload(databaseHelper: BookDatabaseHelper): String {
        val books = databaseHelper.getBooks()
        val root = JSONObject()
        root.put("protocol_version", PROTOCOL_VERSION)
        root.put("client", "Android Native")
        root.put("generated_at", Instant.now().toString())

        val booksArray = JSONArray()
        for (book in books) {
            val bObj = JSONObject().apply {
                put("id", book.id)
                put("title", book.title)
                put("author", book.author ?: "")
                put("category", book.category ?: "")
                put("status", book.status.databaseValue)
                put("media_type", book.mediaType.databaseValue)
                put("rating", book.rating ?: 0.0)
                put("tags", JSONArray(book.tags))
                put("short_comment", book.shortComment ?: "")
                put("review", book.review ?: "")
                put("created_at", book.createdAt)
                put("updated_at", book.updatedAt)
            }
            val mindprint = databaseHelper.getMindprint(book.id)
            if (mindprint != null) {
                bObj.put("mindprint", JSONObject().apply {
                    put("depth", mindprint.depthScore)
                    put("artistry", mindprint.artistryScore)
                    put("emotion", mindprint.emotionScore)
                    put("logic", mindprint.logicScore)
                    put("difficulty", mindprint.difficultyScore)
                    put("healing", mindprint.healingScore)
                })
            }
            booksArray.put(bObj)
        }
        root.put("works", booksArray)
        return root.toString(2)
    }

    /**
     * 解析并统计小程序/跨端同步包摘要
     */
    fun inspectPayload(payloadJson: String): SyncSummary? {
        return runCatching {
            val root = JSONObject(payloadJson)
            val version = root.optString("protocol_version", "1.0.0")
            val generatedAt = root.optString("generated_at", "")
            val works = root.optJSONArray("works") ?: JSONArray()
            var mindprintsCount = 0
            for (i in 0 until works.length()) {
                val item = works.optJSONObject(i)
                if (item?.has("mindprint") == true) mindprintsCount++
            }
            SyncSummary(
                totalBooks = works.length(),
                totalMindprints = mindprintsCount,
                generatedAt = generatedAt,
                protocolVersion = version,
            )
        }.getOrNull()
    }
}