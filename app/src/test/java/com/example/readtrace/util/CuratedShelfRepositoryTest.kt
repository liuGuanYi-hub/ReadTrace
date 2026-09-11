package com.example.readtrace.util

import com.example.readtrace.model.BangumiSubject
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class CuratedShelfRepositoryTest {

    @Test
    fun testShelvesMetadata() {
        val shelves = CuratedShelfRepository.getShelves()
        assertEquals("精选策展应包含 5 大主题榜单", 5, shelves.size)

        val shelfIds = shelves.map { it.id }.toSet()
        assertTrue("应包含豆瓣电影 250", shelfIds.contains("shelf_movie_douban250"))
        assertTrue("应包含豆瓣读书 250", shelfIds.contains("shelf_book_douban250"))
        assertTrue("应包含 Steam 历史好评神作", shelfIds.contains("shelf_game_steam_top"))
        assertTrue("应包含 Bangumi 殿堂番剧", shelfIds.contains("shelf_anime_bangumi_top"))
        assertTrue("应包含滚石 500 黑胶", shelfIds.contains("shelf_music_rolling_stone"))

        shelves.forEach { shelf ->
            assertTrue("展牌标题不应为空", shelf.title.isNotBlank())
            assertTrue("展牌徽章不应为空", shelf.badge.isNotBlank())
            assertTrue("资产路径应位于 curated/ 下", shelf.assetFileName.startsWith("curated/"))
        }
    }

    @Test
    fun testSecondaryFiltersCoverage() {
        val shelves = CuratedShelfRepository.getShelves()
        shelves.forEach { shelf ->
            val filters = CuratedShelfRepository.getFiltersForShelf(shelf.id)
            assertTrue("每个展架应至少配置 3 个二级筛选流派", filters.size >= 3)
            assertEquals("首个筛选胶囊应为'全部'", "全部", filters.first().label)

            // 验证「全部」的谓词恒为 true
            val dummySubject = BangumiSubject(
                id = 100L,
                name = "测试作品",
                nameCn = "测试作品中文名",
                coverUrl = null,
                summary = "简介说明",
                ratingScore = 9.5,
                date = "2024-01-01"
            )
            assertTrue("全部过滤器应当命中任意作品", filters.first().predicate(dummySubject))
        }
    }

    @Test
    fun testOfflineCuratedJsonAssetsCompleteness() {
        // 在 Gradle JVM 单元测试运行时，相对路径通常为工程根目录或 app 子目录
        val candidateDirs = listOf(
            File("src/main/assets/curated"),
            File("app/src/main/assets/curated")
        )
        val curatedDir = candidateDirs.firstOrNull { it.exists() && it.isDirectory }
        assertNotNull("应当能够定位到 assets/curated 离线策展目录", curatedDir)

        val shelves = CuratedShelfRepository.getShelves()
        var totalSubjectsCount = 0

        shelves.forEach { shelf ->
            val jsonFile = File(curatedDir, shelf.assetFileName.removePrefix("curated/"))
            assertTrue("数据包 ${jsonFile.name} 应当存在", jsonFile.exists())

            val content = jsonFile.readText(Charsets.UTF_8)
            val jsonArray = JSONArray(content)
            assertTrue("数据包 ${jsonFile.name} 内容不应为空", jsonArray.length() > 0)
            totalSubjectsCount += jsonArray.length()

            // 抽样校验前 5 部作品的字段完整性
            for (i in 0 until minOf(5, jsonArray.length())) {
                val item = jsonArray.getJSONObject(i)
                assertTrue("每部作品应包含合法 id", item.optLong("id") > 0)
                val hasName = item.optString("name").isNotBlank() || item.optString("name_cn").isNotBlank()
                assertTrue("每部作品应包含非空名称", hasName)
                assertTrue("每部作品应具备客观评分", item.optDouble("rating_score", 0.0) > 0.0)
            }
        }

        assertEquals("5 大策展主题榜单离线作品总数应精确为 336 部", 336, totalSubjectsCount)
    }
}
