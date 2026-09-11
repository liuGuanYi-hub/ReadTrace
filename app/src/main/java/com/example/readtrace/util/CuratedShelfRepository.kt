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
 * 策展货架二级多维流派/年代过滤器定义 (P40 Phase 2)
 */
data class ShelfFilter(
    val id: String,
    val label: String,
    val predicate: (BangumiSubject) -> Boolean,
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

    /**
     * 获取指定货架的多维流派与年代二级筛选规则 (P40 Phase 2)
     */
    fun getFiltersForShelf(shelfId: String): List<ShelfFilter> = when (shelfId) {
        "shelf_movie_douban250" -> listOf(
            ShelfFilter("all", "全部") { true },
            ShelfFilter("drama", "剧情") { it.tags.any { t -> t.contains("剧情") } || it.summary?.contains("剧情") == true },
            ShelfFilter("scifi", "科幻") { it.tags.any { t -> t.contains("科幻") } || it.summary?.contains("科幻") == true },
            ShelfFilter("suspense", "悬疑/犯罪") { it.tags.any { t -> t.contains("悬疑") || t.contains("犯罪") } || it.summary?.contains("悬疑") == true || it.summary?.contains("犯罪") == true },
            ShelfFilter("anime_fantasy", "动画/奇幻") { it.tags.any { t -> t.contains("动画") || t.contains("奇幻") } || it.summary?.contains("动画") == true || it.summary?.contains("奇幻") == true },
            ShelfFilter("era_classic", "80/90年代") { (it.date?.take(4)?.toIntOrNull() ?: 0) in 1900..1999 },
            ShelfFilter("era_modern", "2000后") { (it.date?.take(4)?.toIntOrNull() ?: 0) >= 2000 },
        )
        "shelf_book_douban250" -> listOf(
            ShelfFilter("all", "全部") { true },
            ShelfFilter("literature", "文学经典") { it.tags.any { t -> t.contains("文学") || t.contains("经典") || t.contains("小说") } },
            ShelfFilter("social_science", "社科硬核") { it.tags.any { t -> t.contains("历史") || t.contains("哲学") || t.contains("社会") || t.contains("思考") || t.contains("经济") } },
            ShelfFilter("scifi_detective", "科幻推理") { it.tags.any { t -> t.contains("科幻") || t.contains("推理") || t.contains("悬疑") } },
            ShelfFilter("healing_life", "治愈心理") { it.tags.any { t -> t.contains("治愈") || t.contains("心理") || t.contains("成长") || t.contains("人生") || t.contains("散文") } },
            ShelfFilter("chinese", "中国名著") { it.tags.any { t -> t.contains("中国") } || listOf("余华", "鲁迅", "史铁生", "钱钟书", "王小波", "刘慈欣", "曹雪芹", "老舍", "沈从文").any { author -> it.creator?.contains(author) == true } },
        )
        "shelf_game_steam_top" -> listOf(
            ShelfFilter("all", "全部") { true },
            ShelfFilter("action_soul", "动作/魂系") { it.tags.any { t -> t.contains("动作") || t.contains("魂系") || t.contains("格斗") } },
            ShelfFilter("rogue_strategy", "肉鸽/策略") { it.tags.any { t -> t.contains("肉鸽") || t.contains("Rogue", ignoreCase = true) || t.contains("策略") || t.contains("卡牌") } },
            ShelfFilter("open_world_rpg", "开放世界/RPG") { it.tags.any { t -> t.contains("开放世界") || t.contains("角色扮演") || t.contains("RPG", ignoreCase = true) } },
            ShelfFilter("narrative", "叙事/剧情") { it.tags.any { t -> t.contains("剧情") || t.contains("叙事") || t.contains("独立") } },
            ShelfFilter("casual_puzzle", "休闲/解谜") { it.tags.any { t -> t.contains("休闲") || t.contains("解谜") || t.contains("治愈") || t.contains("模拟") || t.contains("平台") } },
        )
        "shelf_anime_bangumi_top" -> listOf(
            ShelfFilter("all", "全部") { true },
            ShelfFilter("action_mecha", "热血/机战") { it.tags.any { t -> t.contains("热血") || t.contains("机战") || t.contains("动作") || t.contains("科幻") } },
            ShelfFilter("suspense_god", "悬疑/神作") { it.tags.any { t -> t.contains("神作") || t.contains("悬疑") || t.contains("心理") || t.contains("智斗") } },
            ShelfFilter("healing_daily", "治愈/日常") { it.tags.any { t -> t.contains("治愈") || t.contains("日常") || t.contains("校园") || t.contains("青春") } },
            ShelfFilter("fantasy_adventure", "奇幻/冒险") { it.tags.any { t -> t.contains("奇幻") || t.contains("冒险") || t.contains("魔法") } },
        )
        "shelf_music_rolling_stone" -> listOf(
            ShelfFilter("all", "全部") { true },
            ShelfFilter("rock", "摇滚/金属") { it.tags.any { t -> t.contains("摇滚") || t.contains("Rock", ignoreCase = true) || t.contains("金属") } },
            ShelfFilter("pop_rnb", "流行/原声") { it.tags.any { t -> t.contains("流行") || t.contains("Pop", ignoreCase = true) || t.contains("R&B", ignoreCase = true) || t.contains("原声") } },
            ShelfFilter("era_classic", "20世纪经典") { (it.date?.take(4)?.toIntOrNull() ?: 0) in 1900..1999 },
            ShelfFilter("era_modern", "千禧后时代") { (it.date?.take(4)?.toIntOrNull() ?: 0) >= 2000 },
        )
        else -> listOf(
            ShelfFilter("all", "全部") { true },
        )
    }
}
