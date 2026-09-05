package com.example.readtrace.util

import com.example.readtrace.model.AudioTrackItem
import com.example.readtrace.model.Book
import com.example.readtrace.model.BookCharacter
import com.example.readtrace.model.BookLocation
import com.example.readtrace.model.BookMindprint
import com.example.readtrace.model.BookOutline
import com.example.readtrace.model.BookStatus
import com.example.readtrace.model.MediaType
import com.example.readtrace.model.Note
import com.example.readtrace.model.NoteType
import com.example.readtrace.model.ReadingSession
import org.json.JSONArray
import org.json.JSONObject
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

object BackupHelper {

    /**
     * 全量作品备份数据：在书籍与笔记之外，纳入 6 大高阶资产维度
     * （阅读打卡 / 人物角色谱 / 章节大纲 / 空间地标 / 六维心智模型 / 黑胶关联曲目）
     */
    data class WorkBackup(
        val book: Book,
        val notes: List<Note>,
        val sessions: List<ReadingSession> = emptyList(),
        val characters: List<BookCharacter> = emptyList(),
        val outlines: List<BookOutline> = emptyList(),
        val locations: List<BookLocation> = emptyList(),
        val mindprint: BookMindprint? = null,
        val audioTracks: List<AudioTrackItem> = emptyList(),
    ) {
        fun toPair(): Pair<Book, List<Note>> = Pair(book, notes)
    }

    /**
     * 生成全量 JSON 备份字符串（含 6 大高阶维度资产）
     */
    fun generateJsonBackup(items: List<WorkBackup>): String {
        val root = JSONObject().apply {
            put("app", "ReadTrace")
            put("version", "3.0")
            put("schemaVersion", 5)
            put("exportedAt", OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
            put("worksCount", items.size)
            put("notesCount", items.sumOf { it.notes.size })

            val worksArray = JSONArray()
            items.forEach { work ->
                val book = work.book
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
                    put("buyChannel", book.buyChannel.orEmpty())
                    put("shelfLocation", book.shelfLocation.orEmpty())
                    put("bindingType", book.bindingType.orEmpty())
                    if (book.buyPrice != null) put("buyPrice", book.buyPrice)
                    put("createdAt", book.createdAt)
                    put("updatedAt", book.updatedAt)
                    // v5：来源与软删除元数据——恢复来源标注/描述/远程评分，删除语义可跨设备传播（P38-G2）
                    put("sourceType", book.sourceType.orEmpty())
                    put("sourceId", book.sourceId.orEmpty())
                    put("description", book.description.orEmpty())
                    if (book.remoteRating != null) put("remoteRating", book.remoteRating)
                    put("isDeleted", book.isDeleted)
                    put("deletedAt", book.deletedAt.orEmpty())

                    val notesArray = JSONArray()
                    work.notes.forEach { note ->
                        val noteObj = JSONObject().apply {
                            put("content", note.content)
                            put("noteType", note.noteType.databaseValue)
                            put("page", note.page.orEmpty())
                            put("chapter", note.chapter.orEmpty())
                            put("createdAt", note.createdAt)
                            put("updatedAt", note.updatedAt)
                            put("isDeleted", note.isDeleted)
                            put("deletedAt", note.deletedAt.orEmpty())
                        }
                        notesArray.put(noteObj)
                    }
                    put("notes", notesArray)

                    // --- 6 大高阶资产维度 ---
                    val sessionsArray = JSONArray()
                    work.sessions.forEach { session ->
                        sessionsArray.put(
                            JSONObject().apply {
                                put("durationMinutes", session.durationMinutes)
                                put("pagesRead", session.pagesRead.orEmpty())
                                put("thought", session.thought.orEmpty())
                                put("createdAt", session.createdAt)
                            },
                        )
                    }
                    put("sessions", sessionsArray)

                    val charactersArray = JSONArray()
                    work.characters.forEach { character ->
                        charactersArray.put(
                            JSONObject().apply {
                                put("name", character.name)
                                put("roleTitle", character.roleTitle.orEmpty())
                                put("avatarEmoji", character.avatarEmoji)
                                put("description", character.description.orEmpty())
                                put("relationship", character.relationship.orEmpty())
                                put("createdAt", character.createdAt)
                            },
                        )
                    }
                    put("characters", charactersArray)

                    val outlinesArray = JSONArray()
                    work.outlines.forEach { outline ->
                        outlinesArray.put(
                            JSONObject().apply {
                                put("chapterOrder", outline.chapterOrder)
                                put("title", outline.title)
                                put("summary", outline.summary)
                                put("keyTakeaways", outline.keyTakeaways.orEmpty())
                                put("createdAt", outline.createdAt)
                            },
                        )
                    }
                    put("outlines", outlinesArray)

                    val locationsArray = JSONArray()
                    work.locations.forEach { location ->
                        locationsArray.put(
                            JSONObject().apply {
                                put("name", location.name)
                                put("locationType", location.locationType)
                                put("description", location.description.orEmpty())
                                put("significance", location.significance.orEmpty())
                                put("coordinates", location.coordinates.orEmpty())
                                put("createdAt", location.createdAt)
                            },
                        )
                    }
                    put("locations", locationsArray)

                    work.mindprint?.let { mindprint ->
                        put(
                            "mindprint",
                            JSONObject().apply {
                                put("depthScore", mindprint.depthScore)
                                put("artistryScore", mindprint.artistryScore)
                                put("emotionScore", mindprint.emotionScore)
                                put("logicScore", mindprint.logicScore)
                                put("difficultyScore", mindprint.difficultyScore)
                                put("healingScore", mindprint.healingScore)
                                put("updatedAt", mindprint.updatedAt)
                            },
                        )
                    }

                    val tracksArray = JSONArray()
                    work.audioTracks.forEach { track ->
                        tracksArray.put(
                            JSONObject().apply {
                                put("trackOrder", track.trackOrder)
                                put("title", track.title)
                                put("fileUri", track.fileUri)
                                put("durationMs", track.durationMs)
                            },
                        )
                    }
                    put("audioTracks", tracksArray)
                }
                worksArray.put(bookObj)
            }
            put("works", worksArray)
        }

        return root.toString(2)
    }

    /**
     * 解析 JSON 备份文件（向下兼容 v2 备份：仅包含书籍与笔记）
     * @return Pair(解析出的全量作品备份数据, 备份导出时间)
     */
    fun parseJsonBackup(jsonString: String): Pair<List<WorkBackup>, String> {
        val root = JSONObject(jsonString)
        val exportedAt = root.optString("exportedAt", "未知时间")
        val worksArray = root.optJSONArray("works") ?: JSONArray()

        val results = mutableListOf<WorkBackup>()

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
            val buyChannel = bookObj.optString("buyChannel").trim().takeIf { it.isNotEmpty() }
            val shelfLocation = bookObj.optString("shelfLocation").trim().takeIf { it.isNotEmpty() }
            val bindingType = bookObj.optString("bindingType").trim().takeIf { it.isNotEmpty() }
            val buyPrice = if (bookObj.has("buyPrice") && !bookObj.isNull("buyPrice")) bookObj.getDouble("buyPrice") else null
            val createdAt = bookObj.optString("createdAt")
            val updatedAt = bookObj.optString("updatedAt")
            val isDeleted = bookObj.optBoolean("isDeleted", false)
            val deletedAt = bookObj.optString("deletedAt").trim().takeIf { it.isNotEmpty() }
            val sourceType = bookObj.optString("sourceType").trim().takeIf { it.isNotEmpty() }
            val sourceId = bookObj.optString("sourceId").trim().takeIf { it.isNotEmpty() }
            val remoteRating = if (bookObj.has("remoteRating") && !bookObj.isNull("remoteRating")) bookObj.getDouble("remoteRating") else null
            val description = bookObj.optString("description").trim().takeIf { it.isNotEmpty() }

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
                buyChannel = buyChannel,
                shelfLocation = shelfLocation,
                bindingType = bindingType,
                buyPrice = buyPrice,
                createdAt = createdAt,
                updatedAt = updatedAt,
                isDeleted = isDeleted,
                deletedAt = deletedAt,
                sourceType = sourceType,
                sourceId = sourceId,
                remoteRating = remoteRating,
                description = description,
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
                            isDeleted = noteObj.optBoolean("isDeleted", false),
                            deletedAt = noteObj.optString("deletedAt").trim().takeIf { it.isNotEmpty() },
                        ),
                    )
                }
            }

            // --- 解析 6 大高阶资产（旧版备份缺失时自动留空） ---
            val sessionsList = mutableListOf<ReadingSession>()
            bookObj.optJSONArray("sessions")?.let { arr ->
                for (s in 0 until arr.length()) {
                    val obj = arr.getJSONObject(s)
                    val minutes = obj.optInt("durationMinutes", 0)
                    if (minutes <= 0) continue
                    sessionsList.add(
                        ReadingSession(
                            bookId = 0,
                            durationMinutes = minutes,
                            pagesRead = obj.optString("pagesRead").trim().takeIf { it.isNotEmpty() },
                            thought = obj.optString("thought").trim().takeIf { it.isNotEmpty() },
                            createdAt = obj.optString("createdAt"),
                        ),
                    )
                }
            }

            val charactersList = mutableListOf<BookCharacter>()
            bookObj.optJSONArray("characters")?.let { arr ->
                for (c in 0 until arr.length()) {
                    val obj = arr.getJSONObject(c)
                    val name = obj.optString("name").trim()
                    if (name.isEmpty()) continue
                    charactersList.add(
                        BookCharacter(
                            bookId = 0,
                            name = name,
                            roleTitle = obj.optString("roleTitle").trim().takeIf { it.isNotEmpty() },
                            avatarEmoji = obj.optString("avatarEmoji", "👤").ifBlank { "👤" },
                            description = obj.optString("description").trim().takeIf { it.isNotEmpty() },
                            relationship = obj.optString("relationship").trim().takeIf { it.isNotEmpty() },
                            createdAt = obj.optString("createdAt"),
                        ),
                    )
                }
            }

            val outlinesList = mutableListOf<BookOutline>()
            bookObj.optJSONArray("outlines")?.let { arr ->
                for (o in 0 until arr.length()) {
                    val obj = arr.getJSONObject(o)
                    val outlineTitle = obj.optString("title").trim()
                    val summary = obj.optString("summary").trim()
                    if (outlineTitle.isEmpty() || summary.isEmpty()) continue
                    outlinesList.add(
                        BookOutline(
                            bookId = 0,
                            chapterOrder = obj.optInt("chapterOrder", 1),
                            title = outlineTitle,
                            summary = summary,
                            keyTakeaways = obj.optString("keyTakeaways").trim().takeIf { it.isNotEmpty() },
                            createdAt = obj.optString("createdAt"),
                        ),
                    )
                }
            }

            val locationsList = mutableListOf<BookLocation>()
            bookObj.optJSONArray("locations")?.let { arr ->
                for (l in 0 until arr.length()) {
                    val obj = arr.getJSONObject(l)
                    val name = obj.optString("name").trim()
                    if (name.isEmpty()) continue
                    locationsList.add(
                        BookLocation(
                            bookId = 0,
                            name = name,
                            locationType = obj.optString("locationType", "🏙️ 现实都市").ifBlank { "🏙️ 现实都市" },
                            description = obj.optString("description").trim().takeIf { it.isNotEmpty() },
                            significance = obj.optString("significance").trim().takeIf { it.isNotEmpty() },
                            coordinates = obj.optString("coordinates").trim().takeIf { it.isNotEmpty() },
                            createdAt = obj.optString("createdAt"),
                        ),
                    )
                }
            }

            val mindprint = bookObj.optJSONObject("mindprint")?.let { mp ->
                BookMindprint(
                    bookId = 0,
                    depthScore = mp.optDouble("depthScore", 8.0),
                    artistryScore = mp.optDouble("artistryScore", 8.0),
                    emotionScore = mp.optDouble("emotionScore", 8.0),
                    logicScore = mp.optDouble("logicScore", 8.0),
                    difficultyScore = mp.optDouble("difficultyScore", 5.0),
                    healingScore = mp.optDouble("healingScore", 8.0),
                    updatedAt = mp.optString("updatedAt"),
                )
            }

            val tracksList = mutableListOf<AudioTrackItem>()
            bookObj.optJSONArray("audioTracks")?.let { arr ->
                for (t in 0 until arr.length()) {
                    val obj = arr.getJSONObject(t)
                    val trackTitle = obj.optString("title").trim()
                    val fileUri = obj.optString("fileUri").trim()
                    if (trackTitle.isEmpty() || fileUri.isEmpty()) continue
                    tracksList.add(
                        AudioTrackItem(
                            bookId = 0,
                            trackOrder = obj.optInt("trackOrder", 0),
                            title = trackTitle,
                            fileUri = fileUri,
                            durationMs = obj.optLong("durationMs", 0L),
                        ),
                    )
                }
            }

            results.add(
                WorkBackup(
                    book = book,
                    notes = notesList,
                    sessions = sessionsList,
                    characters = charactersList,
                    outlines = outlinesList,
                    locations = locationsList,
                    mindprint = mindprint,
                    audioTracks = tracksList,
                ),
            )
        }

        return Pair(results, exportedAt)
    }

    /**
     * 生成适合 Obsidian / Notion / Logseq 导入的精美 Markdown 文集
     */
    fun generateMarkdownArchive(items: List<WorkBackup>): String {
        val sb = StringBuilder()
        val nowTime = OffsetDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))

        sb.append("# 阅痕 ReadTrace · 个人精神印记文集\n\n")
        sb.append("> 导出时间：$nowTime  \n")
        sb.append("> 累计收藏作品：${items.size} 部 | 摘录随想：${items.sumOf { it.notes.size }} 条\n\n")
        sb.append("---\n\n")

        // 按媒介类型分组
        val grouped = items.groupBy { it.book.mediaType }

        grouped.forEach { (mediaType, works) ->
            sb.append("## ${mediaType.emoji} ${mediaType.displayName}篇 (${works.size})\n\n")

            works.forEachIndexed { index, work ->
                val book = work.book
                val notes = work.notes
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
                if (!book.buyChannel.isNullOrBlank() || !book.shelfLocation.isNullOrBlank() || !book.bindingType.isNullOrBlank() || book.buyPrice != null) {
                    val collectionDetails = listOfNotNull(
                        book.buyChannel?.takeIf { it.isNotBlank() }?.let { "渠道: $it" },
                        book.shelfLocation?.takeIf { it.isNotBlank() }?.let { "位置: $it" },
                        book.bindingType?.takeIf { it.isNotBlank() }?.let { "装帧: $it" },
                        book.buyPrice?.let { String.format(java.util.Locale.getDefault(), "价格: ¥%.2f", it) },
                    ).joinToString(" · ")
                    sb.append("- **实体藏本**：$collectionDetails\n")
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
    fun generateCsvExport(items: List<WorkBackup>): String {
        val sb = StringBuilder()
        sb.append("标题,创作者,作品类型,状态,评分,分类,标签,短评,深度评价,开始时间,完成时间,摘录总数\n")

        items.forEach { work ->
            val book = work.book
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
                work.notes.size.toString(),
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
