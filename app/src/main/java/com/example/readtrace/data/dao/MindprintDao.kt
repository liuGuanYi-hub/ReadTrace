package com.example.readtrace.data

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.example.readtrace.model.Book
import com.example.readtrace.model.BookMindprint
import com.example.readtrace.model.ReadingPersona
import com.example.readtrace.model.BookStatus
import com.example.readtrace.model.MediaType
import java.time.LocalDate
import com.example.readtrace.model.ReadingSession

/**
 * 六维心智、年度人格、阅读时长与 streak 的数据访问层（P40 Phase 4 阶段 4 自 BookDatabaseHelper 抽出）。
 */
object MindprintDao {

    private fun currentTimestamp(): String = BookDatabaseHelper.currentTimestamp()

    fun saveMindprint(db: SQLiteDatabase, mindprint: BookMindprint): Long {
        val now = currentTimestamp()
        val values = ContentValues().apply {
            put(COLUMN_BOOK_ID, mindprint.bookId)
            put(COLUMN_DEPTH_SCORE, mindprint.depthScore)
            put(COLUMN_ARTISTRY_SCORE, mindprint.artistryScore)
            put(COLUMN_EMOTION_SCORE, mindprint.emotionScore)
            put(COLUMN_LOGIC_SCORE, mindprint.logicScore)
            put(COLUMN_DIFFICULTY_SCORE, mindprint.difficultyScore)
            put(COLUMN_HEALING_SCORE, mindprint.healingScore)
            put(COLUMN_UPDATED_AT, now)
        }
        return db.insertWithOnConflict(
            TABLE_BOOK_MINDPRINTS,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    fun getMindprint(db: SQLiteDatabase, bookId: Long): BookMindprint =
        db.query(
            TABLE_BOOK_MINDPRINTS,
            null,
            "$COLUMN_BOOK_ID = ?",
            arrayOf(bookId.toString()),
            null,
            null,
            null,
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.toBookMindprint() else BookMindprint(bookId = bookId)
        }

    fun getAllMindprints(db: SQLiteDatabase): Map<Long, BookMindprint> {
        val map = mutableMapOf<Long, BookMindprint>()
        runCatching {
            db.query(
                TABLE_BOOK_MINDPRINTS,
                null,
                null,
                null,
                null,
                null,
                null,
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val mp = cursor.toBookMindprint()
                    map[mp.bookId] = mp
                }
            }
        }
        return map
    }

    fun getAnnualMindprintPersona(db: SQLiteDatabase): com.example.readtrace.model.ReadingPersona? {
        val finishedBooks = BookDao.getBooks(db, BookStatus.FINISHED)
        if (finishedBooks.isEmpty()) return null

        // T2.7 + T2.8：一次取全部心智档案后内存索引（替代逐书查询 N+1）；
        // 无心智档案的作品不计入均值——原逻辑把它们按默认六维（8.0/5.0 假对象）计入，
        // 导致年度人格被系统性拉向默认值。全部无档案时直接返回 null。
        val allMindprints = getAllMindprints(db)
        val mindprints = finishedBooks.mapNotNull { allMindprints[it.id] }
        if (mindprints.isEmpty()) return null
        val count = mindprints.size.toDouble()

        val avgDepth = mindprints.sumOf { it.depthScore } / count
        val avgArtistry = mindprints.sumOf { it.artistryScore } / count
        val avgEmotion = mindprints.sumOf { it.emotionScore } / count
        val avgLogic = mindprints.sumOf { it.logicScore } / count
        val avgDifficulty = mindprints.sumOf { it.difficultyScore } / count
        val avgHealing = mindprints.sumOf { it.healingScore } / count

        val avgMindprint = com.example.readtrace.model.BookMindprint(
            bookId = 0L,
            depthScore = avgDepth,
            artistryScore = avgArtistry,
            emotionScore = avgEmotion,
            logicScore = avgLogic,
            difficultyScore = avgDifficulty,
            healingScore = avgHealing,
        )

        val dims = listOf(
            "depth" to avgDepth,
            "artistry" to avgArtistry,
            "emotion" to avgEmotion,
            "logic" to avgLogic,
            "difficulty" to avgDifficulty,
            "healing" to avgHealing,
        )
        val maxDim = dims.maxByOrNull { it.second }?.first ?: "depth"

        val (title, desc, dominant) = when (maxDim) {
            "depth" -> Triple("🧠 深邃哲思探索者", "沉醉于对世界本质与生命哲理的深度审视，在思想高原上自由漫步。", "思想深度")
            "artistry" -> Triple("🖋️ 唯美文学审美家", "对文字的韵律美、诗性意境与修辞质感具有极高的审美敏锐度。", "文笔意境")
            "emotion" -> Triple("❤️ 细腻共鸣共情家", "在字里行间捕获最真挚的人性温热，以心感应万千生灵的喜怒哀乐。", "情感共鸣")
            "logic" -> Triple("📐 严密理性格局派", "追求严丝合缝的因果规律与宏大世界构建，崇尚清晰有力的理性推演。", "逻辑构架")
            "difficulty" -> Triple("⛰️ 硬核学术攀登者", "敢于直面深奥晦涩的经典大作与思想峻岭，在攀登中享受智识蜕变。", "思想门槛")
            else -> Triple("🌿 纯粹心灵疗愈者", "在静谧的书海中寻找灵魂的安顿与精神绿洲，温和而坚定地被文字抚慰。", "心灵治愈")
        }

        return com.example.readtrace.model.ReadingPersona(
            personaTitle = title,
            personaDesc = desc,
            dominantDimension = dominant,
            avgMindprint = avgMindprint,
            finishedBooksCount = finishedBooks.size,
        )
    }

    // --- 📱 桌面小组件专享数据支持 (AppWidgets Data Providers) ---

    fun getTodayTotalReadingMinutes(db: SQLiteDatabase): Int {
        val todayStr = currentTimestamp().substringBefore("T")
        val cursor = db.rawQuery(
            """
            SELECT SUM($COLUMN_DURATION_MINUTES) 
            FROM $TABLE_READING_SESSIONS 
            WHERE $COLUMN_CREATED_AT LIKE ? AND $COLUMN_IS_DELETED = 0
            """.trimIndent(),
            arrayOf("$todayStr%"),
        )
        return cursor.use { if (it.moveToFirst() && !it.isNull(0)) it.getInt(0) else 0 }
    }

    fun getConsecutiveReadingDays(db: SQLiteDatabase): Int {
        val cursor = db.rawQuery(
            """
            SELECT DISTINCT substr($COLUMN_CREATED_AT, 1, 10) as session_date 
            FROM $TABLE_READING_SESSIONS 
            WHERE $COLUMN_IS_DELETED = 0 
            ORDER BY session_date DESC
            """.trimIndent(),
            null,
        )
        val dates = cursor.use {
            buildList {
                while (it.moveToNext()) {
                    add(it.getString(0))
                }
            }
        }
        if (dates.isEmpty()) return 0

        val today = LocalDate.now()
        var streak = 0
        var checkDate = today

        val latestDateStr = dates.first()
        val latestDate = runCatching { LocalDate.parse(latestDateStr) }.getOrNull() ?: return 0
        if (latestDate != today && latestDate != today.minusDays(1)) {
            return 0
        }
        checkDate = latestDate

        for (dateStr in dates) {
            val d = runCatching { LocalDate.parse(dateStr) }.getOrNull() ?: break
            if (d == checkDate) {
                streak++
                checkDate = checkDate.minusDays(1)
            } else if (d < checkDate) {
                break
            }
        }
        return streak
    }

    fun getRandomOrNextQuote(db: SQLiteDatabase, excludeQuote: String? = null): Pair<Book?, String> {
        val quotesCursor = db.rawQuery(
            """
            SELECT b.$COLUMN_ID, b.$COLUMN_TITLE, b.$COLUMN_AUTHOR, b.$COLUMN_COVER_URL, b.$COLUMN_MEDIA_TYPE,
                   n.$COLUMN_CONTENT
            FROM $TABLE_NOTES n
            JOIN $TABLE_BOOKS b ON n.$COLUMN_BOOK_ID = b.$COLUMN_ID
            WHERE n.$COLUMN_IS_DELETED = 0 AND b.$COLUMN_IS_DELETED = 0
            ORDER BY RANDOM()
            LIMIT 10
            """.trimIndent(),
            null,
        )
        val quotes = quotesCursor.use { c ->
            buildList {
                while (c.moveToNext()) {
                    val book = Book(
                        id = c.getLong(0),
                        title = c.getString(1),
                        author = c.getString(2),
                        coverUrl = c.getString(3),
                        mediaType = MediaType.fromDatabaseValue(c.getString(4)),
                        status = BookStatus.READING,
                    )
                    val content = c.getString(5)
                    add(book to content)
                }
            }
        }

        val selected = quotes.firstOrNull { it.second != excludeQuote } ?: quotes.firstOrNull()
        if (selected != null) {
            return selected
        }

        val books = BookDao.getBooks(db)
        val commentBooks = books.filter { !it.shortComment.isNullOrBlank() || !it.review.isNullOrBlank() }
        val randomCommentBook = commentBooks.shuffled().firstOrNull()
        if (randomCommentBook != null) {
            val quote = randomCommentBook.shortComment?.takeIf { it.isNotBlank() }
                ?: randomCommentBook.review?.takeIf { it.isNotBlank() }
                ?: "每一道心智印记，都是灵魂与文字的永恒交汇。"
            return randomCommentBook to quote
        }

        val defaultQuotes = listOf(
            "生命中真正重要的不是你遭遇了什么，而是你记住了哪些事，又是如何铭记的。",
            "给岁月以文明，而不是给文明以岁月。",
            "世界上只有一种真正的英雄主义，那就是认清生活的真相后依然热爱生活。",
            "一个人并不是生来要给打败的，你尽可以把他消灭掉，可就是打不败他。",
            "你所热爱的，就是你的生活；你所铭记的，就是你的痕迹。",
        )
        val defaultBook = books.firstOrNull()
        val quote = defaultQuotes.filter { it != excludeQuote }.randomOrNull() ?: defaultQuotes.first()
        return defaultBook to quote
    }

    fun getLatestReadingBook(db: SQLiteDatabase): Book? {
        val books = BookDao.getBooks(db, BookStatus.READING)
        if (books.isNotEmpty()) {
            return books.maxByOrNull { it.updatedAt } ?: books.first()
        }
        val allBooks = BookDao.getBooks(db)
        return allBooks.firstOrNull()
    }

    fun quickRecordReadingSession(db: SQLiteDatabase, bookId: Long, minutes: Int = 15): Long {
        val session = ReadingSession(
            bookId = bookId,
            durationMinutes = minutes,
            thought = "⚡ 桌面小组件快捷打卡 +${minutes}min",
            createdAt = currentTimestamp(),
        )
        return BookDao.insertReadingSession(db, session)
    }

}

internal fun Cursor.toBookMindprint(): BookMindprint =
    BookMindprint(
        id = getLong(getColumnIndexOrThrow(COLUMN_ID)),
        bookId = getLong(getColumnIndexOrThrow(COLUMN_BOOK_ID)),
        depthScore = getDouble(getColumnIndexOrThrow(COLUMN_DEPTH_SCORE)),
        artistryScore = getDouble(getColumnIndexOrThrow(COLUMN_ARTISTRY_SCORE)),
        emotionScore = getDouble(getColumnIndexOrThrow(COLUMN_EMOTION_SCORE)),
        logicScore = getDouble(getColumnIndexOrThrow(COLUMN_LOGIC_SCORE)),
        difficultyScore = getDouble(getColumnIndexOrThrow(COLUMN_DIFFICULTY_SCORE)),
        healingScore = getDouble(getColumnIndexOrThrow(COLUMN_HEALING_SCORE)),
        updatedAt = getString(getColumnIndexOrThrow(COLUMN_UPDATED_AT)),
    )

