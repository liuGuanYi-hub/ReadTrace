package com.example.readtrace.util

import com.example.readtrace.model.Book
import com.example.readtrace.model.BookMindprint
import com.example.readtrace.model.BookStatus
import com.example.readtrace.model.MediaType
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.time.LocalDate

/**
 * 🚚 多源 0 门槛资产搬家解析引擎 (Multi-Source Migration Engine)
 * 支持豆瓣 (Douban)、Bangumi 番组计划、Steam 游戏库与 Notion/通用 CSV 的智能识别与无损入库。
 */
object MultiSourceMigrationHelper {

    enum class SourcePlatform(val displayName: String, val emoji: String) {
        DOUBAN("豆瓣", "📗"),
        BANGUMI("Bangumi", "🌸"),
        STEAM("Steam", "🎮"),
        NOTION_CSV("通用表格", "📋"),
    }

    /**
     * 智能自动嗅探文本内容属于哪一类数据源
     */
    fun sniffPlatform(rawContent: String): SourcePlatform {
        val trimmed = rawContent.trim()
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            if (trimmed.contains("appid") || trimmed.contains("playtime_forever") || trimmed.contains("games")) {
                return SourcePlatform.STEAM
            }
            if (trimmed.contains("subject_id") || trimmed.contains("ep_status") || trimmed.contains("subject")) {
                return SourcePlatform.BANGUMI
            }
        }
        if (trimmed.contains("豆瓣") || trimmed.contains("我的评分") || trimmed.contains("条目链接") || trimmed.contains("读过时间")) {
            return SourcePlatform.DOUBAN
        }
        return SourcePlatform.NOTION_CSV
    }

    /**
     * 解析任意来源文本
     */
    fun parseContent(rawContent: String, platform: SourcePlatform? = null): List<BookCsvParser.ParsedBookRecord> {
        val targetPlatform = platform ?: sniffPlatform(rawContent)
        return when (targetPlatform) {
            SourcePlatform.DOUBAN -> parseDoubanCsv(rawContent)
            SourcePlatform.BANGUMI -> parseBangumiJson(rawContent)
            SourcePlatform.STEAM -> parseSteamJson(rawContent)
            SourcePlatform.NOTION_CSV -> BookCsvParser.parseRecords(rawContent.byteInputStream(Charsets.UTF_8))
        }
    }

    /**
     * 1. 豆瓣 CSV/文本导出解析器
     */
    fun parseDoubanCsv(csvText: String): List<BookCsvParser.ParsedBookRecord> {
        val lines = csvText.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()

        val results = mutableListOf<BookCsvParser.ParsedBookRecord>()
        val headerLine = lines.first()
        val headers = parseCsvLine(headerLine)

        val titleIndex = headers.indexOfFirst { it.contains("标题") || it.contains("名称") || it.contains("书名") || it.contains("片名") }
        val authorIndex = headers.indexOfFirst { it.contains("作者") || it.contains("导演") || it.contains("制作") }
        val ratingIndex = headers.indexOfFirst { it.contains("评分") || it.contains("星级") }
        val tagsIndex = headers.indexOfFirst { it.contains("标签") }
        val commentIndex = headers.indexOfFirst { it.contains("短评") || it.contains("简评") || it.contains("评价") }
        val dateIndex = headers.indexOfFirst { it.contains("时间") || it.contains("日期") || it.contains("读过") }

        for (i in 1 until lines.size) {
            val cols = parseCsvLine(lines[i])
            if (cols.isEmpty()) continue

            val rawTitle = if (titleIndex in cols.indices) cols[titleIndex].trim() else cols.firstOrNull()?.trim() ?: ""
            if (rawTitle.isBlank()) continue

            val author = if (authorIndex in cols.indices) cols[authorIndex].trim().takeIf { it.isNotBlank() } else null
            val ratingRaw = if (ratingIndex in cols.indices) cols[ratingIndex].trim() else ""
            val rating = parseDoubanRating(ratingRaw)

            val tags = if (tagsIndex in cols.indices) {
                cols[tagsIndex].split(" ", ",", "，", ";", "；").map { it.trim() }.filter { it.isNotEmpty() }
            } else emptyList()

            val comment = if (commentIndex in cols.indices) cols[commentIndex].trim().takeIf { it.isNotBlank() } else null
            val finishDateStr = if (dateIndex in cols.indices) cols[dateIndex].trim() else null
            val cleanDate = finishDateStr?.take(10)?.takeIf { it.isNotBlank() }

            // 智能识别媒介类型（通过片名、书名或表头特征）
            val mediaType = when {
                headerLine.contains("电影") || headerLine.contains("影片") -> MediaType.MOVIE
                headerLine.contains("音乐") || headerLine.contains("专辑") -> MediaType.MUSIC
                else -> MediaType.BOOK
            }

            val seed = (rating ?: 8.0).coerceIn(1.0, 10.0)
            val book = Book(
                title = rawTitle,
                author = author,
                mediaType = mediaType,
                rating = rating,
                tags = tags,
                shortComment = comment,
                status = if (cleanDate != null) BookStatus.FINISHED else BookStatus.READING,
                finishDate = cleanDate,
                sourceType = "douban",
            )
            val mindprint = BookMindprint(
                bookId = 0,
                depthScore = seed,
                artistryScore = seed,
                emotionScore = seed,
                logicScore = seed,
                difficultyScore = 5.0,
                healingScore = seed,
            )
            results.add(BookCsvParser.ParsedBookRecord(book, mindprint))
        }

        return results
    }

    /**
     * 2. Bangumi 番组计划 JSON 解析器
     */
    fun parseBangumiJson(jsonText: String): List<BookCsvParser.ParsedBookRecord> {
        val results = mutableListOf<BookCsvParser.ParsedBookRecord>()
        runCatching {
            val root = if (jsonText.trim().startsWith("[")) {
                JSONArray(jsonText)
            } else {
                val obj = JSONObject(jsonText)
                obj.optJSONArray("data") ?: obj.optJSONArray("items") ?: JSONArray().apply { put(obj) }
            }

            for (i in 0 until root.length()) {
                val item = root.optJSONObject(i) ?: continue
                val subject = item.optJSONObject("subject") ?: item

                val nameCn = subject.optString("name_cn").takeIf { it.isNotBlank() }
                val rawName = subject.optString("name").takeIf { it.isNotBlank() } ?: "未知番剧"
                val title = nameCn ?: rawName

                val ratingValue = item.optDouble("rate", item.optDouble("rating", 0.0))
                val rating = if (ratingValue > 0.0) ratingValue.coerceIn(1.0, 10.0) else null

                val comment = item.optString("comment").takeIf { it.isNotBlank() }
                val typeId = subject.optInt("type", 2)
                val mediaType = if (typeId == 1) MediaType.BOOK else MediaType.ANIME

                val statusType = item.optInt("type", 2)
                val status = when (statusType) {
                    1 -> BookStatus.WISHLIST
                    2 -> BookStatus.FINISHED
                    3 -> BookStatus.READING
                    else -> BookStatus.FINISHED
                }

                val coverObj = subject.optJSONObject("images")
                val coverUrl = coverObj?.optString("common") ?: coverObj?.optString("medium")

                val seed = (rating ?: 8.5).coerceIn(1.0, 10.0)
                val book = Book(
                    title = title,
                    author = rawName.takeIf { nameCn != null && nameCn != rawName },
                    mediaType = mediaType,
                    rating = rating,
                    tags = listOf("Bangumi", if (mediaType == MediaType.ANIME) "动画" else "漫画"),
                    shortComment = comment,
                    status = status,
                    coverUrl = coverUrl,
                    sourceType = "bangumi",
                    sourceId = subject.optString("id"),
                )
                val mindprint = BookMindprint(
                    bookId = 0,
                    depthScore = seed,
                    artistryScore = seed,
                    emotionScore = seed,
                    logicScore = seed,
                    difficultyScore = 5.0,
                    healingScore = seed,
                )
                results.add(BookCsvParser.ParsedBookRecord(book, mindprint))
            }
        }
        return results
    }

    /**
     * 3. Steam 游戏库 JSON 解析器
     */
    fun parseSteamJson(jsonText: String): List<BookCsvParser.ParsedBookRecord> {
        val results = mutableListOf<BookCsvParser.ParsedBookRecord>()
        runCatching {
            val root = JSONObject(jsonText)
            val response = root.optJSONObject("response") ?: root
            val games = response.optJSONArray("games") ?: JSONArray()

            for (i in 0 until games.length()) {
                val game = games.optJSONObject(i) ?: continue
                val appid = game.optLong("appid", 0L)
                val name = game.optString("name").takeIf { it.isNotBlank() } ?: continue
                val playtimeForeverMin = game.optInt("playtime_forever", 0)
                val playtimeHours = playtimeForeverMin / 60

                val status = if (playtimeHours > 20) BookStatus.FINISHED else if (playtimeHours > 0) BookStatus.READING else BookStatus.WISHLIST
                val comment = if (playtimeHours > 0) "Steam 累计游玩时长:  小时" else "Steam 愿望单/库藏待探索"

                val book = Book(
                    title = name,
                    author = "Steam AppID: ",
                    mediaType = MediaType.GAME,
                    rating = if (playtimeHours > 100) 10.0 else if (playtimeHours > 50) 9.0 else if (playtimeHours > 10) 8.0 else null,
                    tags = listOf("Steam", "PC", if (playtimeHours > 50) "百小时神作" else "已入手"),
                    shortComment = comment,
                    status = status,
                    sourceType = "steam",
                    sourceId = appid.toString(),
                )
                val seed = (book.rating ?: 8.0).coerceIn(1.0, 10.0)
                val mindprint = BookMindprint(
                    bookId = 0,
                    depthScore = seed,
                    artistryScore = seed,
                    emotionScore = seed,
                    logicScore = seed,
                    difficultyScore = 6.0,
                    healingScore = seed,
                )
                results.add(BookCsvParser.ParsedBookRecord(book, mindprint))
            }
        }
        return results
    }

    private fun parseDoubanRating(raw: String): Double? {
        if (raw.isBlank()) return null
        val num = raw.filter { it.isDigit() }.toIntOrNull() ?: return null
        return when {
            num in 1..5 -> num * 2.0 // 1~5 星制转化为 2~10 分
            num in 6..10 -> num.toDouble()
            num in 11..100 -> num / 10.0
            else -> 8.0
        }
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false

        for (c in line) {
            when {
                c == '\"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> {
                    result.add(sb.toString().trim())
                    sb.clear()
                }
                else -> sb.append(c)
            }
        }
        result.add(sb.toString().trim())
        return result.map { it.removeSurrounding("\"").trim() }
    }
}