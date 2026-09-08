package com.example.readtrace.util

import android.content.Context
import android.util.Log
import com.example.readtrace.model.BangumiSubject
import com.example.readtrace.model.MediaType
import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap

/**
 * 精选策展画廊展牌定义
 */
data class CuratedShelf(
    val id: String,
    val title: String,
    val subtitle: String,
    val badge: String,
    val mediaType: MediaType,
    val assetFileName: String,
    val gradientStartColor: Int,
    val gradientEndColor: Int,
    val accentColor: Int,
)

/**
 * 精选热门榜单货架数据仓库（P39 Phase 1 核心组件）。
 *
 * 职责：
 * - 集中管理离线静态策展数据包（豆瓣 Top 250、Steam 好评、Bangumi 殿堂、滚石 500 等）；
 * - 提供极速流式解析与内存 Lru/ConcurrentHashMap 高速缓存，达到 0 网络依赖与 0ms 瞬间切换体验；
 * - 统一转换为全站通用的 [BangumiSubject] 模型，无缝对接现有网格渲染与一键批量建库链路。
 */
object CuratedShelfRepository {

    private const val TAG = "CuratedShelfRepo"
    private val memoryCache = ConcurrentHashMap<String, List<BangumiSubject>>()

    /** 获取所有官方精选策展画廊展牌 */
    fun getShelves(): List<CuratedShelf> = listOf(
        CuratedShelf(
            id = "shelf_movie_douban250",
            title = "豆瓣电影 TOP 250",
            subtitle = "传世影史神作殿堂",
            badge = "🏆 影史必看",
            mediaType = MediaType.MOVIE,
            assetFileName = "curated/shelf_movie_douban250.json",
            gradientStartColor = 0xFF2A231C.toInt(), // 深黑褐金
            gradientEndColor = 0xFF171513.toInt(),
            accentColor = 0xFFE5A93C.toInt(),      // 金曜
        ),
        CuratedShelf(
            id = "shelf_book_douban250",
            title = "豆瓣读书 TOP 250",
            subtitle = "思想与文学传世经典",
            badge = "📖 精神基石",
            mediaType = MediaType.BOOK,
            assetFileName = "curated/shelf_book_douban250.json",
            gradientStartColor = 0xFF1C2820.toInt(), // 墨绿羊皮
            gradientEndColor = 0xFF121A15.toInt(),
            accentColor = 0xFF4E9A68.toInt(),      // 翡绿
        ),
        CuratedShelf(
            id = "shelf_game_steam_top",
            title = "Steam 历史好评神作",
            subtitle = "好评如潮第九艺术",
            badge = "🕹️ 压倒性好评",
            mediaType = MediaType.GAME,
            assetFileName = "curated/shelf_game_steam_top.json",
            gradientStartColor = 0xFF1A2130.toInt(), // 赛博曜蓝
            gradientEndColor = 0xFF10141D.toInt(),
            accentColor = 0xFF4A90E2.toInt(),      // 霓虹天蓝
        ),
        CuratedShelf(
            id = "shelf_anime_bangumi_top",
            title = "Bangumi 殿堂神作",
            subtitle = "二次元核心评分最高",
            badge = "🌸 动漫神作",
            mediaType = MediaType.ANIME,
            assetFileName = "curated/shelf_anime_bangumi_top.json",
            gradientStartColor = 0xFF2C1B26.toInt(), // 极光暮紫粉
            gradientEndColor = 0xFF191116.toInt(),
            accentColor = 0xFFD86B9E.toInt(),      // 樱粉
        ),
        CuratedShelf(
            id = "shelf_music_rolling_stone",
            title = "滚石 500 经典黑胶",
            subtitle = "时代回响与概念专辑",
            badge = "💽 经典唱片",
            mediaType = MediaType.MUSIC,
            assetFileName = "curated/shelf_music_rolling_stone.json",
            gradientStartColor = 0xFF28201A.toInt(), // 唱片古铜
            gradientEndColor = 0xFF15110E.toInt(),
            accentColor = 0xFFD48344.toInt(),      // 暖铜
        ),
    )

    /** 加载指定货架的作品列表（带高速内存缓存） */
    fun loadShelfSubjects(context: Context, shelf: CuratedShelf): List<BangumiSubject> {
        memoryCache[shelf.id]?.let { return it }

        val items = mutableListOf<BangumiSubject>()
        try {
            context.assets.open(shelf.assetFileName).use { inputStream ->
                val reader = BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8))
                val jsonString = reader.readText()
                val jsonArray = JSONArray(jsonString)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val id = obj.optLong("id")
                    val name = obj.optString("name", "")
                    val nameCn = if (obj.has("name_cn") && !obj.isNull("name_cn")) obj.optString("name_cn").takeIf { it.isNotBlank() } else null
                    val coverUrl = if (obj.has("cover_url") && !obj.isNull("cover_url")) obj.optString("cover_url").takeIf { it.isNotBlank() } else null
                    val summary = if (obj.has("summary") && !obj.isNull("summary")) obj.optString("summary").takeIf { it.isNotBlank() } else null
                    val ratingScore = if (obj.has("rating_score") && !obj.isNull("rating_score")) obj.optDouble("rating_score") else null
                    val date = if (obj.has("date") && !obj.isNull("date")) obj.optString("date").takeIf { it.isNotBlank() } else null
                    val creator = if (obj.has("creator") && !obj.isNull("creator")) obj.optString("creator").takeIf { it.isNotBlank() } else null
                    val subjectType = obj.optInt("subject_type", shelf.mediaType.toBangumiType())
                    val source = obj.optString("source", "curated_${shelf.mediaType.databaseValue}")

                    val tagsList = mutableListOf<String>()
                    val tagsArray = obj.optJSONArray("tags")
                    if (tagsArray != null) {
                        for (t in 0 until tagsArray.length()) {
                            val tag = tagsArray.optString(t)
                            if (!tag.isNullOrBlank()) tagsList += tag
                        }
                    }

                    items += BangumiSubject(
                        id = id,
                        name = name,
                        nameCn = nameCn,
                        coverUrl = coverUrl,
                        summary = summary,
                        ratingScore = ratingScore,
                        date = date,
                        tags = tagsList,
                        creator = creator,
                        subjectType = subjectType,
                        source = source,
                    )
                }
            }
            memoryCache[shelf.id] = items
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load curated shelf: ${shelf.assetFileName}", e)
        }
        return items
    }

    private fun MediaType.toBangumiType(): Int = when (this) {
        MediaType.BOOK -> 1
        MediaType.ANIME -> 2
        MediaType.MUSIC -> 3
        MediaType.GAME -> 4
        MediaType.MOVIE -> 6
    }
}
