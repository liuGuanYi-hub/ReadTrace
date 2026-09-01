package com.example.readtrace

import com.example.readtrace.model.AudioTrackItem
import com.example.readtrace.model.Book
import com.example.readtrace.model.BookCharacter
import com.example.readtrace.model.BookLocation
import com.example.readtrace.model.BookMindprint
import com.example.readtrace.model.BookOutline
import com.example.readtrace.model.BookStatus
import com.example.readtrace.model.MediaType
import com.example.readtrace.model.Note
import com.example.readtrace.model.ReadingSession
import com.example.readtrace.util.BackupHelper
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 全量备份 JSON 引擎往返一致性测试：
 * 导出（generateJsonBackup）→ 解析（parseJsonBackup）后 6 大高阶资产应完整无损，
 * 且向下兼容无高阶节点的旧版（v2）备份。
 */
class BackupHelperJsonTest {

    private fun sampleWork(): BackupHelper.WorkBackup {
        val book = Book(
            id = 1L,
            title = "百年孤独",
            author = "马尔克斯",
            coverUrl = "https://example.com/cover.jpg",
            category = "文学",
            status = BookStatus.FINISHED,
            mediaType = MediaType.BOOK,
            rating = 9.5,
            tags = listOf("拉美文学", "魔幻现实主义"),
            shortComment = "过去都是假的，回忆是一条没有归途的路。",
            review = "多年以后，面对行刑队……",
            startDate = "2026-01-01",
            finishDate = "2026-02-01",
            createdAt = "2026-01-01T10:00:00+08:00",
            updatedAt = "2026-02-01T12:00:00+08:00",
        )
        return BackupHelper.WorkBackup(
            book = book,
            notes = listOf(
                Note(
                    id = 1L,
                    bookId = 1L,
                    content = "人生如旅，亦哭亦歌。",
                    createdAt = "2026-01-15T08:00:00+08:00",
                    updatedAt = "2026-01-15T08:00:00+08:00",
                ),
            ),
            sessions = listOf(
                ReadingSession(bookId = 1L, durationMinutes = 45, pagesRead = "P.120-135", thought = "沉浸两小时", createdAt = "2026-01-20T22:00:00+08:00"),
            ),
            characters = listOf(
                BookCharacter(bookId = 1L, name = "奥雷里亚诺", roleTitle = "上校", avatarEmoji = "🪖", createdAt = "2026-01-18T09:00:00+08:00"),
            ),
            outlines = listOf(
                BookOutline(bookId = 1L, chapterOrder = 1, title = "第一章", summary = "冰块与吉普赛人", createdAt = "2026-01-05T09:00:00+08:00"),
            ),
            locations = listOf(
                BookLocation(bookId = 1L, name = "马孔多", locationType = "🏘️ 虚构村镇", createdAt = "2026-01-06T09:00:00+08:00"),
            ),
            mindprint = BookMindprint(
                bookId = 1L,
                depthScore = 9.0,
                artistryScore = 9.5,
                emotionScore = 8.0,
                logicScore = 7.5,
                difficultyScore = 8.5,
                healingScore = 6.0,
                updatedAt = "2026-02-01T12:00:00+08:00",
            ),
            audioTracks = listOf(
                AudioTrackItem(bookId = 1L, trackOrder = 0, title = "雨落马孔多", fileUri = "content://audio/rain", durationMs = 180_000L),
            ),
        )
    }

    @Test
    fun `全量备份往返一致`() {
        val json = BackupHelper.generateJsonBackup(listOf(sampleWork()))
        val (parsed, exportedAt) = BackupHelper.parseJsonBackup(json)

        assertEquals(1, parsed.size)
        assertTrue(exportedAt.isNotBlank())

        val work = parsed[0]
        assertEquals("百年孤独", work.book.title)
        assertEquals("马尔克斯", work.book.author)
        assertEquals(9.5, work.book.rating!!, 0.001)
        assertEquals(listOf("拉美文学", "魔幻现实主义"), work.book.tags)
        assertEquals(1, work.notes.size)
        assertEquals("人生如旅，亦哭亦歌。", work.notes[0].content)

        // 6 大高阶资产逐项断言
        assertEquals(1, work.sessions.size)
        assertEquals(45, work.sessions[0].durationMinutes)
        assertEquals("P.120-135", work.sessions[0].pagesRead)

        assertEquals(1, work.characters.size)
        assertEquals("奥雷里亚诺", work.characters[0].name)
        assertEquals("🪖", work.characters[0].avatarEmoji)

        assertEquals(1, work.outlines.size)
        assertEquals("第一章", work.outlines[0].title)

        assertEquals(1, work.locations.size)
        assertEquals("马孔多", work.locations[0].name)

        assertNotNull(work.mindprint)
        assertEquals(9.0, work.mindprint!!.depthScore, 0.001)
        assertEquals(8.5, work.mindprint!!.difficultyScore, 0.001)

        assertEquals(1, work.audioTracks.size)
        assertEquals("雨落马孔多", work.audioTracks[0].title)
        assertEquals(180_000L, work.audioTracks[0].durationMs)
    }

    @Test
    fun `JSON Schema 包含 6 大高阶节点`() {
        val json = BackupHelper.generateJsonBackup(listOf(sampleWork()))
        val root = org.json.JSONObject(json)
        val workObj = root.getJSONArray("works").getJSONObject(0)

        assertTrue(workObj.getJSONObject("mindprint").length() > 0)
        assertEquals(1, workObj.getJSONArray("sessions").length())
        assertEquals(1, workObj.getJSONArray("characters").length())
        assertEquals(1, workObj.getJSONArray("outlines").length())
        assertEquals(1, workObj.getJSONArray("locations").length())
        assertEquals(1, workObj.getJSONArray("audioTracks").length())
    }

    @Test
    fun `旧版v2备份(无高阶节点)向下兼容解析`() {
        // 手工构造旧版结构：只有 works[] 内的书籍与 notes
        val legacyJson = """
        {
          "app": "ReadTrace",
          "version": "2.0",
          "schemaVersion": 3,
          "exportedAt": "2026-01-01T00:00:00+08:00",
          "works": [
            {
              "title": "局外人",
              "author": "加缪",
              "status": "finished",
              "mediaType": "book",
              "tags": ["存在主义"],
              "notes": [
                {"content": "今天，妈妈死了。", "noteType": "quote", "createdAt": "2026-01-01T00:00:00+08:00", "updatedAt": "2026-01-01T00:00:00+08:00"}
              ]
            }
          ]
        }
        """.trimIndent()

        val (parsed, _) = BackupHelper.parseJsonBackup(legacyJson)

        assertEquals(1, parsed.size)
        val work = parsed[0]
        assertEquals("局外人", work.book.title)
        assertEquals(BookStatus.FINISHED, work.book.status)
        assertEquals(1, work.notes.size)
        // 旧版备份高阶资产应为空集合而非异常
        assertTrue(work.sessions.isEmpty())
        assertTrue(work.characters.isEmpty())
        assertTrue(work.outlines.isEmpty())
        assertTrue(work.locations.isEmpty())
        assertTrue(work.audioTracks.isEmpty())
        assertNull(work.mindprint)
    }

    @Test
    fun `多作品计数汇总正确`() {
        val workA = sampleWork()
        val workB = BackupHelper.WorkBackup(
            book = workA.book.copy(id = 2L, title = "佩德罗·巴拉莫"),
            notes = emptyList(),
        )
        val json = BackupHelper.generateJsonBackup(listOf(workA, workB))
        val root = org.json.JSONObject(json)

        assertEquals(2, root.getInt("worksCount"))
        assertEquals(1, root.getInt("notesCount"))
        assertEquals(2, root.getJSONArray("works").length())
    }
}
