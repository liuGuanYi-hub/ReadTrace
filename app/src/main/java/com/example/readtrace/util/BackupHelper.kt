package com.example.readtrace.util

import com.example.readtrace.model.Book
import com.example.readtrace.model.BookStatus
import com.example.readtrace.model.MediaType
import com.example.readtrace.model.Note
import com.example.readtrace.model.NoteType
import org.json.JSONArray
import org.json.JSONObject
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

object BackupHelper {

    /**
     * 生成全量 JSON 备份字符串
     */
    fun generateJsonBackup(items: List<Pair<Book, List<Note>>>): String {
        val root = JSONObject().apply {
            put("app", "ReadTrace")
            put("version", "2.0")
            put("schemaVersion", 3)
            put("exportedAt", OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
            put("worksCount", items.size)
            put("notesCount", items.sumOf { it.second.size })

            val worksArray = JSONArray()
            items.forEach { (book, notes) ->
                val bookObj = JSONObject().apply {
                    put("title", book.title)
                    put("author", book.author.orEmpty())
                    put("coverUrl", book.coverUrl.orEmpty())
                    put("category", book.category.orEmpty())
                    put("status", book.status.databaseValue)
                    put("mediaType", book.mediaType.databaseValue)
                    if (book.rating != null) put("rating", book.rating)
                    put("tags", JSONArray(book.tags))
                    put("shortComment", book.shortComment.orEmpty())
                    put("review", book.review.orEmpty())
                    put("startDate", book.startDate.orEmpty())
                    put("finishDate", book.finishDate.orEmpty())
                    put("createdAt", book.createdAt)
                    put("updatedAt", book.updatedAt)

                    val notesArray = JSONArray()
                    notes.forEach { note ->
                        val noteObj = JSONObject().apply {
                            put("content", note.content)
                            put("noteType", note.noteType.databaseValue)
                            put("page", note.page.orEmpty())
                            put("chapter", note.chapter.orEmpty())
                            put("createdAt", note.createdAt)
                            put("updatedAt", note.updatedAt)
                        }
                        notesArray.put(noteObj)
                    }
                    put("notes", notesArray)
                }
                worksArray.put(bookObj)
            }
            put("works", worksArray)
        }

        return root.toString(2)
    }

    /**
     * 解析 JSON 备份文件
     * @return Pair(解析出的作品与笔记列表, 备份导出时间)
     */
    fun parseJsonBackup(jsonString: String): Pair<List<Pair<Book, List<Note>>>, String> {
        val root = JSONObject(jsonString)
        val exportedAt = root.optString("exportedAt", "未知时间")
        val worksArray = root.optJSONArray("works") ?: JSONArray()

        val results = mutableListOf<Pair<Book, List<Note>>>()

        for (i in 0 until worksArray.length()) {
            val bookObj = worksArray.getJSONObject(i)
            val title = bookObj.getString("title").trim()
            if (title.isEmpty()) continue

            val author = bookObj.optString("author").trim().takeIf { it.isNotEmpty() }
            val coverUrl = bookObj.optString("coverUrl").trim().takeIf { it.isNotEmpty() }
            val category = bookObj.optString("category").trim().takeIf { it.isNotEmpty() }
            val status = BookStatus.fromDatabaseValue(bookObj.optString("status"))
            val mediaType = MediaType.fromDatabaseValue(bookObj.optString("mediaType"))
            val rating = if (bookObj.has("rating") && !bookObj.isNull("rating")) bookObj.getDouble("rating") else null

            val tagsList = mutableListOf<String>()
            val tagsArray = bookObj.optJSONArray("tags")
            if (tagsArray != null) {
                for (t in 0 until tagsArray.length()) {
                    tagsArray.optString(t).trim().takeIf { it.isNotEmpty() }?.let(tagsList::add)
                }
            }

            val shortComment = bookObj.optString("shortComment").trim().takeIf { it.isNotEmpty() }
            val review = bookObj.optString("review").trim().takeIf { it.isNotEmpty() }
            val startDate = bookObj.optString("startDate").trim().takeIf { it.isNotEmpty() }
            val finishDate = bookObj.optString("finishDate").trim().takeIf { it.isNotEmpty() }
            val createdAt = bookObj.optString("createdAt")
            val updatedAt = bookObj.optString("updatedAt")

            val book = Book(
                id = 0,
                title = title,
                author = author,
                coverUrl = coverUrl,
                category = category,
                status = status,
                mediaType = mediaType,
                rating = rating,
                tags = tagsList,
                shortComment = shortComment,
                review = review,
                startDate = startDate,
                finishDate = finishDate,
                createdAt = createdAt,
                updatedAt = updatedAt,
            )

            val notesList = mutableListOf<Note>()
            val notesArray = bookObj.optJSONArray("notes")
            if (notesArray != null) {
                for (n in 0 until notesArray.length()) {
                    val noteObj = notesArray.getJSONObject(n)
                    val content = noteObj.getString("content").trim()
                    if (content.isEmpty()) continue

                    val noteType = NoteType.fromDatabaseValue(noteObj.optString("noteType"))
                    val page = noteObj.optString("page").trim().takeIf { it.isNotEmpty() }
                    val chapter = noteObj.optString("chapter").trim().takeIf { it.isNotEmpty() }
                    val noteCreatedAt = noteObj.optString("createdAt")
                    val noteUpdatedAt = noteObj.optString("updatedAt")

                    notesList.add(
                        Note(
                            id = 0,
                            bookId = 0,
                            content = content,
                            noteType = noteType,
                            page = page,
                            chapter = chapter,
                            createdAt = noteCreatedAt,
                            updatedAt = noteUpdatedAt,
                        ),
                    )
                }
            }

            results.add(Pair(book, notesList))
        }

        return Pair(results, exportedAt)
    }

    /**
     * 生成适合 Obsidian / Notion / Logseq 导入的精美 Markdown 文集
     */
    fun generateMarkdownArchive(items: List<Pair<Book, List<Note>>>): String {
        val sb = StringBuilder()
        val nowTime = OffsetDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))

        sb.append("# 阅痕 ReadTrace · 个人精神印记文集\n\n")
        sb.append("> 导出时间：$nowTime  \n")
        sb.append("> 累计收藏作品：${items.size} 部 | 摘录随想：${items.sumOf { it.second.size }} 条\n\n")
        sb.append("---\n\n")

        // 按媒介类型分组
        val grouped = items.groupBy { it.first.mediaType }

        grouped.forEach { (mediaType, works) ->
            sb.append("## ${mediaType.emoji} ${mediaType.displayName}篇 (${works.size})\n\n")

            works.forEachIndexed { index, (book, notes) ->
                sb.append("### ${index + 1}. 《${book.title}》\n\n")

                sb.append("- **${mediaType.creatorLabel}**：${book.author ?: "未知"}\n")
                sb.append("- **状态**：${book.status.getDisplayName(mediaType)}\n")
                if (book.rating != null) {
                    sb.append("- **评分**：${book.rating} / 10\n")
                }
                if (!book.category.isNullOrBlank()) {
                    sb.append("- **分类**：${book.category}\n")
                }
                if (book.tags.isNotEmpty()) {
                    sb.append("- **标签**：${book.tags.joinToString(" #", prefix = "#")}\n")
                }
                if (!book.startDate.isNullOrBlank() || !book.finishDate.isNullOrBlank()) {
                    sb.append("- **时间**：${book.startDate ?: "—"} 至 ${book.finishDate ?: "—"}\n")
                }
                sb.append("\n")

                if (!book.shortComment.isNullOrBlank()) {
                    sb.append("**【一句话感悟】**\n")
                    sb.append("> ${book.shortComment}\n\n")
                }

                if (!book.review.isNullOrBlank()) {
                    sb.append("**【深度复盘】**\n\n")
                    sb.append("${book.review}\n\n")
                }

                if (notes.isNotEmpty()) {
                    sb.append("**【痕迹与摘录】(${notes.size})**\n\n")
                    notes.forEach { note ->
                        val loc = listOfNotNull(
                            note.chapter?.takeIf { it.isNotBlank() },
                            note.page?.takeIf { it.isNotBlank() }?.let { "P.$it" },
                        ).joinToString(" · ")
                        val locPrefix = if (loc.isNotEmpty()) " *($loc)*" else ""

                        sb.append("> 💬 **[${note.noteType.displayName}]**${locPrefix}：  \n")
                        sb.append("> ${note.content.replace("\n", "\n> ")}\n\n")
                    }
                }

                sb.append("---\n\n")
            }
        }

        return sb.toString()
    }

    /**
     * 生成标准 CSV 导出表格
     */
    fun generateCsvExport(items: List<Pair<Book, List<Note>>>): String {
        val sb = StringBuilder()
        sb.append("标题,创作者,作品类型,状态,评分,分类,标签,短评,深度评价,开始时间,完成时间,摘录总数\n")

        items.forEach { (book, notes) ->
            val row = listOf(
                escapeCsv(book.title),
                escapeCsv(book.author.orEmpty()),
                escapeCsv(book.mediaType.displayName),
                escapeCsv(book.status.getDisplayName(book.mediaType)),
                book.rating?.toString().orEmpty(),
                escapeCsv(book.category.orEmpty()),
                escapeCsv(book.tags.joinToString(";")),
                escapeCsv(book.shortComment.orEmpty()),
                escapeCsv(book.review.orEmpty()),
                escapeCsv(book.startDate.orEmpty()),
                escapeCsv(book.finishDate.orEmpty()),
                notes.size.toString(),
            )
            sb.append(row.joinToString(",")).append("\n")
        }

        return sb.toString()
    }

    private fun escapeCsv(value: String): String {
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\""
        }
        return value
    }
}
