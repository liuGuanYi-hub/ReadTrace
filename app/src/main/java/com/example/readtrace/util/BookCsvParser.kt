package com.example.readtrace.util

import com.example.readtrace.model.Book
import com.example.readtrace.model.BookMindprint
import com.example.readtrace.model.BookStatus
import com.example.readtrace.model.MediaType
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

object BookCsvParser {

    data class ParsedBookRecord(
        val book: Book,
        val mindprint: BookMindprint? = null,
    )

    /**
     * 兼容旧版接口：解析 CSV 输入流，返回解析出的待导入书籍列表。
     */
    fun parse(inputStream: InputStream): List<Book> {
        return parseRecords(inputStream).map { it.book }
    }

    /**
     * 解析多维度丰富 CSV 输入流，返回解析出的书籍及其六维心智模型。
     * 支持自适应表头匹配、多媒体类型、分类、状态、评分、标签、金句、长评、封面及 6 维认知心智。
     */
    fun parseRecords(
        inputStream: InputStream,
        defaultMediaType: MediaType = MediaType.BOOK,
    ): List<ParsedBookRecord> {
        val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
        val records = mutableListOf<ParsedBookRecord>()

        reader.useLines { lines ->
            var headerMap: Map<String, Int>? = null
            var isFirstLine = true

            for (rawLine in lines) {
                // 去除 UTF-8 BOM
                val cleanRaw = if (isFirstLine && rawLine.startsWith("\uFEFF")) {
                    rawLine.substring(1)
                } else {
                    rawLine
                }
                val line = cleanRaw.trim()
                if (line.isEmpty()) continue

                val parts = parseCsvLine(line)
                if (parts.isEmpty() || parts.all { it.isEmpty() }) continue

                // 探测首行是否为表头
                if (isFirstLine) {
                    isFirstLine = false
                    if (isHeaderLine(parts)) {
                        headerMap = buildHeaderIndexMap(parts)
                        continue
                    }
                }

                val record = if (headerMap != null) {
                    parseRecordWithHeader(parts, headerMap, defaultMediaType)
                } else {
                    parseRecordPositional(parts, defaultMediaType)
                }

                if (record != null && record.book.title.isNotBlank()) {
                    records.add(record)
                }
            }
        }
        return records
    }

    private fun isHeaderLine(parts: List<String>): Boolean {
        val first = parts.getOrNull(0)?.trim()?.lowercase() ?: return false
        val headerKeywords = listOf("书名", "标题", "作品名", "番剧名", "电影名", "游戏名", "title", "name")
        return headerKeywords.any { first.contains(it) }
    }

    private fun buildHeaderIndexMap(headers: List<String>): Map<String, Int> {
        val map = mutableMapOf<String, Int>()
        headers.forEachIndexed { index, rawHeader ->
            val h = rawHeader.trim().lowercase()
            when {
                h.contains("书名") || h.contains("标题") || h.contains("作品名") || h.contains("番剧名") || h.contains("电影名") || h.contains("游戏名") || h == "title" || h == "name" -> map["title"] = index
                h.contains("作者") || h.contains("创作者") || h.contains("导演") || h.contains("监督") || h.contains("制作") || h.contains("发行") || h == "author" || h == "creator" -> map["author"] = index
                h.contains("媒介") || h.contains("类型") || h.contains("作品类型") || h == "mediatype" || h == "media_type" || h == "type" -> map["media_type"] = index
                h.contains("分类") || h.contains("类别") || h == "category" || h == "genre" -> map["category"] = index
                h.contains("状态") || h == "status" -> map["status"] = index
                h.contains("评分") || h.contains("星级") || h == "rating" || h == "score" -> map["rating"] = index
                h.contains("标签") || h == "tags" || h == "tag" -> map["tags"] = index
                h.contains("短评") || h.contains("金句") || h.contains("名台词") || h == "quote" || h == "short_comment" || h == "comment" -> map["short_comment"] = index
                h.contains("深度评价") || h.contains("长评") || h.contains("影评") || h.contains("测评") || h.contains("评价") || h == "review" -> map["review"] = index
                h.contains("封面") || h == "cover" || h == "cover_url" || h == "coverurl" -> map["cover_url"] = index
                h.contains("深度评分") || (h.contains("深度") && !h.contains("评价")) || h == "depth" || h == "depth_score" -> map["depth"] = index
                h.contains("艺术") || h == "art" || h == "artistry" || h == "art_score" -> map["artistry"] = index
                h.contains("情感") || h == "emo" || h == "emotion" || h == "emotion_score" -> map["emotion"] = index
                h.contains("逻辑") || h == "log" || h == "logic" || h == "logic_score" -> map["logic"] = index
                h.contains("难度") || h == "diff" || h == "difficulty" || h == "difficulty_score" -> map["difficulty"] = index
                h.contains("治愈") || h == "heal" || h == "healing" || h == "healing_score" -> map["healing"] = index
            }
        }
        return map
    }

    private fun parseRecordWithHeader(
        parts: List<String>,
        headerMap: Map<String, Int>,
        defaultMediaType: MediaType,
    ): ParsedBookRecord? {
        val titleIdx = headerMap["title"] ?: return null
        val title = parts.getOrNull(titleIdx)?.trim().orEmpty()
        if (title.isEmpty()) return null

        val author = headerMap["author"]?.let { parts.getOrNull(it)?.trim().takeIf { it?.isNotEmpty() == true } }
        val rawMediaType = headerMap["media_type"]?.let { parts.getOrNull(it)?.trim() }
        val mediaType = parseMediaType(rawMediaType, defaultMediaType)

        val category = headerMap["category"]?.let { parts.getOrNull(it)?.trim().takeIf { it?.isNotEmpty() == true } }
        val rawStatus = headerMap["status"]?.let { parts.getOrNull(it)?.trim() }
        val status = parseBookStatus(rawStatus)

        val rating = headerMap["rating"]?.let { parts.getOrNull(it)?.trim()?.toDoubleOrNull() }
        val tags = headerMap["tags"]?.let { parts.getOrNull(it)?.let { parseTags(it) } } ?: emptyList()
        val shortComment = headerMap["short_comment"]?.let { parts.getOrNull(it)?.trim().takeIf { it?.isNotEmpty() == true } }
        val review = headerMap["review"]?.let { parts.getOrNull(it)?.trim().takeIf { it?.isNotEmpty() == true } }
        val coverUrl = headerMap["cover_url"]?.let { parts.getOrNull(it)?.trim().takeIf { it?.isNotEmpty() == true } }

        val depth = headerMap["depth"]?.let { parts.getOrNull(it)?.trim()?.toDoubleOrNull() } ?: 0.0
        val artistry = headerMap["artistry"]?.let { parts.getOrNull(it)?.trim()?.toDoubleOrNull() } ?: 0.0
        val emotion = headerMap["emotion"]?.let { parts.getOrNull(it)?.trim()?.toDoubleOrNull() } ?: 0.0
        val logic = headerMap["logic"]?.let { parts.getOrNull(it)?.trim()?.toDoubleOrNull() } ?: 0.0
        val difficulty = headerMap["difficulty"]?.let { parts.getOrNull(it)?.trim()?.toDoubleOrNull() } ?: 0.0
        val healing = headerMap["healing"]?.let { parts.getOrNull(it)?.trim()?.toDoubleOrNull() } ?: 0.0

        val book = Book(
            id = 0L,
            title = title,
            author = author,
            coverUrl = coverUrl,
            category = category,
            status = status,
            rating = rating,
            tags = tags,
            shortComment = shortComment,
            review = review,
            mediaType = mediaType,
            startDate = if (status == BookStatus.FINISHED) "2026-01-01" else null,
            finishDate = if (status == BookStatus.FINISHED) "2026-06-01" else null,
            createdAt = "",
            updatedAt = "",
            isDeleted = false,
            deletedAt = null,
        )

        val hasMindprint = depth > 0 || artistry > 0 || emotion > 0 || logic > 0 || difficulty > 0 || healing > 0
        val mindprint = if (hasMindprint) {
            BookMindprint(
                bookId = 0L,
                depthScore = depth,
                artistryScore = artistry,
                emotionScore = emotion,
                logicScore = logic,
                difficultyScore = difficulty,
                healingScore = healing,
            )
        } else null

        return ParsedBookRecord(book, mindprint)
    }

    private fun parseRecordPositional(
        parts: List<String>,
        defaultMediaType: MediaType,
    ): ParsedBookRecord? {
        val title = parts.getOrNull(0)?.trim().orEmpty()
        if (title.isEmpty()) return null

        val author = parts.getOrNull(1)?.trim().takeIf { it?.isNotEmpty() == true }

        // 如果只有 2 列（基础书单格式）
        if (parts.size <= 2) {
            val book = Book(
                id = 0L,
                title = title,
                author = author,
                mediaType = defaultMediaType,
                status = BookStatus.WISHLIST,
                tags = emptyList(),
                createdAt = "",
                updatedAt = "",
                isDeleted = false,
            )
            return ParsedBookRecord(book, null)
        }

        // 16 维位置匹配模式
        val mediaType = parseMediaType(parts.getOrNull(2)?.trim(), defaultMediaType)
        val category = parts.getOrNull(3)?.trim().takeIf { it?.isNotEmpty() == true }
        val status = parseBookStatus(parts.getOrNull(4)?.trim())
        val rating = parts.getOrNull(5)?.trim()?.toDoubleOrNull()
        val tags = parts.getOrNull(6)?.let { parseTags(it) } ?: emptyList()
        val shortComment = parts.getOrNull(7)?.trim().takeIf { it?.isNotEmpty() == true }
        val review = parts.getOrNull(8)?.trim().takeIf { it?.isNotEmpty() == true }
        val coverUrl = parts.getOrNull(9)?.trim().takeIf { it?.isNotEmpty() == true }

        val depth = parts.getOrNull(10)?.trim()?.toDoubleOrNull() ?: 0.0
        val artistry = parts.getOrNull(11)?.trim()?.toDoubleOrNull() ?: 0.0
        val emotion = parts.getOrNull(12)?.trim()?.toDoubleOrNull() ?: 0.0
        val logic = parts.getOrNull(13)?.trim()?.toDoubleOrNull() ?: 0.0
        val difficulty = parts.getOrNull(14)?.trim()?.toDoubleOrNull() ?: 0.0
        val healing = parts.getOrNull(15)?.trim()?.toDoubleOrNull() ?: 0.0

        val book = Book(
            id = 0L,
            title = title,
            author = author,
            coverUrl = coverUrl,
            category = category,
            status = status,
            rating = rating,
            tags = tags,
            shortComment = shortComment,
            review = review,
            mediaType = mediaType,
            startDate = if (status == BookStatus.FINISHED) "2026-01-01" else null,
            finishDate = if (status == BookStatus.FINISHED) "2026-06-01" else null,
            createdAt = "",
            updatedAt = "",
            isDeleted = false,
        )

        val hasMindprint = depth > 0 || artistry > 0 || emotion > 0 || logic > 0 || difficulty > 0 || healing > 0
        val mindprint = if (hasMindprint) {
            BookMindprint(
                bookId = 0L,
                depthScore = depth,
                artistryScore = artistry,
                emotionScore = emotion,
                logicScore = logic,
                difficultyScore = difficulty,
                healingScore = healing,
            )
        } else null

        return ParsedBookRecord(book, mindprint)
    }

    private fun parseMediaType(raw: String?, defaultType: MediaType): MediaType {
        if (raw.isNullOrBlank()) return defaultType
        val lower = raw.trim().lowercase()
        return when {
            lower.contains("anime") || lower.contains("番剧") || lower.contains("动漫") || lower.contains("动画") -> MediaType.ANIME
            lower.contains("movie") || lower.contains("film") || lower.contains("电影") || lower.contains("影视") -> MediaType.MOVIE
            lower.contains("game") || lower.contains("游戏") || lower.contains("主机") || lower.contains("steam") -> MediaType.GAME
            lower.contains("podcast") || lower.contains("播客") || lower.contains("电台") -> MediaType.PODCAST
            lower.contains("book") || lower.contains("书") || lower.contains("名著") || lower.contains("小说") -> MediaType.BOOK
            else -> defaultType
        }
    }

    private fun parseBookStatus(raw: String?): BookStatus {
        if (raw.isNullOrBlank()) return BookStatus.FINISHED
        val lower = raw.trim().lowercase()
        return when {
            lower == "reading" || lower.contains("在读") || lower.contains("追番中") || lower.contains("游玩中") || lower.contains("在看") -> BookStatus.READING
            lower == "finished" || lower.contains("读完") || lower.contains("补完") || lower.contains("已看") || lower.contains("通关") || lower.contains("白金") -> BookStatus.FINISHED
            lower == "wishlist" || lower.contains("想读") || lower.contains("想追") || lower.contains("想看") || lower.contains("想玩") || lower.contains("待看") -> BookStatus.WISHLIST
            lower == "dropped" || lower.contains("弃") || lower.contains("搁置") -> BookStatus.DROPPED
            else -> BookStatus.FINISHED
        }
    }

    private fun parseTags(raw: String): List<String> {
        if (raw.isBlank()) return emptyList()
        val splitRegex = Regex("[;；,，/|]")
        return raw.split(splitRegex)
            .map { it.trim().removeSurrounding("\"").trim() }
            .filter { it.isNotEmpty() }
    }

    /**
     * 基础 CSV 单行解析，支持逗号分隔与双引号包裹
     */
    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false

        for (ch in line) {
            when (ch) {
                '"' -> inQuotes = !inQuotes
                ',' -> {
                    if (inQuotes) {
                        current.append(ch)
                    } else {
                        result.add(current.toString().trim())
                        current.setLength(0)
                    }
                }
                else -> current.append(ch)
            }
        }
        result.add(current.toString().trim())
        return result
    }
}
