package com.example.readtrace.util

import android.content.Context
import com.example.readtrace.data.BookDatabaseHelper
import com.example.readtrace.data.UserPreferencesManager
import com.example.readtrace.model.BookStatus
import com.example.readtrace.model.MediaType
import org.json.JSONArray

/**
 * 🧭 个性化品味画像与跨媒介智能探索推荐引擎 (PersonalizedRecommendationEngine)
 *
 * 结合 Local-First 本地标签统计画像 + 经典高分常青库 + AI 深度定制推荐。
 */
object PersonalizedRecommendationEngine {

    data class TasteProfile(
        val topTags: List<Pair<String, Int>>,
        val dominantMediaType: MediaType,
        val highlyRatedCount: Int,
        val summary: String,
    )

    data class RecommendedWork(
        val title: String,
        val author: String,
        val mediaType: MediaType,
        val tags: List<String>,
        val rating: Double,
        val matchReason: String,
        val coverUrl: String? = null,
        val isFromAi: Boolean = false,
    )

    /**
     * 深度分析用户的本地书库，生成精神品味画像
     */
    fun analyzeUserTaste(context: Context): TasteProfile {
        val db = BookDatabaseHelper.getInstance(context)
        val allBooks = db.getBooks().filter { !it.isDeleted }
        val highRatedOrFinished = allBooks.filter { (it.rating != null && it.rating >= 7.5) || it.status == BookStatus.FINISHED }

        val tagFreq = mutableMapOf<String, Int>()
        val mediaFreq = mutableMapOf<MediaType, Int>()

        highRatedOrFinished.forEach { book ->
            mediaFreq[book.mediaType] = (mediaFreq[book.mediaType] ?: 0) + 1
            book.tags.forEach { tag ->
                val clean = tag.trim().removePrefix("#")
                if (clean.isNotBlank()) {
                    tagFreq[clean] = (tagFreq[clean] ?: 0) + 1
                }
            }
            // 从短评与笔记提取常见意象
            val text = listOfNotNull(book.shortComment, book.review).joinToString(" ")
            listOf("治愈", "温情", "科幻", "悬疑", "日常", "赛博朋克", "哲学", "历史", "冒险", "奇幻", "成长", "神作").forEach { keyword ->
                if (text.contains(keyword)) {
                    tagFreq[keyword] = (tagFreq[keyword] ?: 0) + 2
                }
            }
        }

        val topTags = tagFreq.entries.sortedByDescending { it.value }.take(5).map { it.key to it.value }
        val dominantMedia = mediaFreq.entries.maxByOrNull { it.value }?.key ?: MediaType.BOOK

        val summary = if (topTags.isNotEmpty()) {
            val tagNames = topTags.take(3).joinToString("、") { "「${it.first}」" }
            "基于你偏爱的 $tagNames 题材与 ${dominantMedia.displayName} 偏好，为你探索共鸣佳作："
        } else {
            "探索跨媒介经典高分佳作，发现属于你的下一段精神共鸣："
        }

        return TasteProfile(
            topTags = topTags,
            dominantMediaType = dominantMedia,
            highlyRatedCount = highRatedOrFinished.size,
            summary = summary,
        )
    }

    /**
     * 获取本地离线高分精选常青库推荐（自动去重已收录作品）
     */
    fun getCuratedRecommendations(context: Context, taste: TasteProfile): List<RecommendedWork> {
        val db = BookDatabaseHelper.getInstance(context)
        val existingTitles = db.getBooks().map { it.title.trim().lowercase() }.toSet()

        val catalog = getCuratedCatalog()
        val userTopTagSet = taste.topTags.map { it.first.lowercase() }.toSet()

        // 评分与标签共鸣加权排序
        val scored = catalog.filter { work ->
            !existingTitles.contains(work.title.trim().lowercase())
        }.map { work ->
            var score = work.rating
            // 匹配到用户偏好标签
            val matchedTags = work.tags.filter { userTopTagSet.contains(it.lowercase()) }
            if (matchedTags.isNotEmpty()) {
                score += matchedTags.size * 3.0
            }
            // 匹配到优势媒介
            if (work.mediaType == taste.dominantMediaType) {
                score += 1.5
            }
            val reason = if (matchedTags.isNotEmpty()) {
                "基于你喜爱的 ${matchedTags.joinToString("、") { "『$it』" }} 标签推荐"
            } else {
                work.matchReason
            }
            work.copy(matchReason = reason) to score
        }

        return scored.sortedByDescending { it.second }.take(8).map { it.first }
    }

    /**
     * AI 深度定制推荐生成
     */
    fun fetchAiRecommendations(
        context: Context,
        taste: TasteProfile,
        callback: (List<RecommendedWork>) -> Unit,
    ) {
        val apiKey = UserPreferencesManager.getAiApiKey(context)
        val baseUrl = UserPreferencesManager.getAiBaseUrl(context)
        val model = UserPreferencesManager.getAiModel(context)

        if (apiKey.isBlank()) {
            callback(getCuratedRecommendations(context, taste))
            return
        }

        Thread {
            val prompt = """
                作为一位高品位的文化策展人，请根据用户的精神品味画像推荐 4 部精选佳作：
                - 用户核心偏好标签：${taste.topTags.joinToString(", ") { "${it.first}(频次:${it.second})" }.ifBlank { "经典文学与艺术" }}
                - 优势媒介类型：${taste.dominantMediaType.displayName}

                请输出合法的纯 JSON 数组，格式如下：
                [
                  {
                    "title": "作品名",
                    "author": "创作者/导演/开发商",
                    "mediaType": "BOOK 或 ANIME 或 MOVIE 或 GAME 或 MUSIC",
                    "tags": ["治愈", "日常", "神作"],
                    "rating": 9.2,
                    "matchReason": "精准的个性化推荐理由与核心共鸣点（30字以内）"
                  }
                ]
                要求：
                1. 严禁烂俗流量作品，优先推荐口碑极高、叙事深厚的小众神作或殿堂级经典；
                2. 只返回纯 JSON 数组，不要附加 markdown 或解释说明。
            """.trimIndent()

            val content = AiChatClient.requestChatCompletion(
                baseUrl = baseUrl,
                apiKey = apiKey,
                model = model,
                systemPrompt = "你是一位精通跨媒介艺术的殿堂级策展人，只输出合法 JSON 数组。",
                userPrompt = prompt,
                temperature = 0.7,
            )
            if (content != null) {
                val parsed = parseAiRecommendations(content)
                if (parsed.isNotEmpty()) {
                    callback(parsed)
                    return@Thread
                }
            }

            callback(getCuratedRecommendations(context, taste))
        }.start()
    }

    private fun parseAiRecommendations(rawText: String): List<RecommendedWork> {
        return try {
            val clean = rawText.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val array = JSONArray(clean)
            val result = mutableListOf<RecommendedWork>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val mediaTypeStr = obj.optString("mediaType", "BOOK")
                val mediaType = try {
                    MediaType.valueOf(mediaTypeStr.uppercase())
                } catch (e: Exception) {
                    MediaType.BOOK
                }
                val tagsList = mutableListOf<String>()
                val tagsArr = obj.optJSONArray("tags")
                if (tagsArr != null) {
                    for (j in 0 until tagsArr.length()) {
                        tagsList.add(tagsArr.getString(j))
                    }
                }
                result.add(
                    RecommendedWork(
                        title = obj.optString("title", "未命名佳作"),
                        author = obj.optString("author", "未知创作者"),
                        mediaType = mediaType,
                        tags = tagsList,
                        rating = obj.optDouble("rating", 9.0),
                        matchReason = obj.optString("matchReason", "与你的精神品味高度共鸣。"),
                        isFromAi = true,
                    )
                )
            }
            result
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 内置跨媒介离线精选常青库
     */
    private fun getCuratedCatalog(): List<RecommendedWork> = listOf(
        // 治愈 / 温情 / 日常
        RecommendedWork("夏目友人帐", "绿川幸", MediaType.ANIME, listOf("治愈", "温情", "妖怪", "日常"), 9.4, "温润如清泉的妖怪绮谭，关于温柔与被温柔以待。"),
        RecommendedWork("紫罗兰永恒花园", "晓佳奈 / 京都动画", MediaType.ANIME, listOf("治愈", "成长", "爱", "唯美"), 9.1, "在代写书信的旅途中，探寻「爱」的真正含义。"),
        RecommendedWork("摇曳露营△", "Afro / C-Station", MediaType.ANIME, listOf("日常", "治愈", "露营", "美食"), 9.3, "富士山脚下的冬日篝火与热汤，极致的松弛与惬意。"),
        RecommendedWork("白兔糖", "宇仁田由美", MediaType.ANIME, listOf("治愈", "亲情", "日常", "温情"), 9.0, "单身青年与可爱养女的生活日常，抚慰心灵的人间烟火。"),
        RecommendedWork("虫师", "漆原友纪", MediaType.ANIME, listOf("治愈", "自然", "奇幻", "哲思"), 9.5, "行走在人与自然边缘的银古，诉说万物共生的静谧诗篇。"),
        RecommendedWork("解忧杂货店", "东野圭吾", MediaType.BOOK, listOf("治愈", "奇幻", "温情", "救赎"), 8.9, "穿越时空的牛奶箱信件，连接着迷茫灵魂的相互救赎。"),
        RecommendedWork("海街日记", "是枝裕和", MediaType.MOVIE, listOf("治愈", "亲情", "温情", "镰仓"), 8.8, "镰仓老宅里四姐妹的四季流转，细腻如梅子酒般的温情。"),
        RecommendedWork("星露谷物语", "ConcernedApe", MediaType.GAME, listOf("治愈", "农场", "生活", "模拟"), 9.6, "远离都市喧嚣，在属于自己的小天地里耕作与生活。"),

        // 硬核科幻 / 赛博朋克 / 宏大叙事
        RecommendedWork("星际穿越", "克里斯托弗·诺兰", MediaType.MOVIE, listOf("科幻", "太空", "爱", "硬核"), 9.4, "超越时间与维度的爱，浩瀚宇宙中的人类史诗。"),
        RecommendedWork("海伯利安", "丹·西蒙斯", MediaType.BOOK, listOf("科幻", "太空歌剧", "诗性", "神作"), 9.3, "朝圣者的坎特伯雷故事集，科幻文学史上的无上瑰宝。"),
        RecommendedWork("攻壳机动队 S.A.C.", "神山健治", MediaType.ANIME, listOf("科幻", "赛博朋克", "哲学", "神作"), 9.6, "义体化社会中的灵魂探讨，赛博朋克动画的巅峰之作。"),
        RecommendedWork("赛博朋克：边缘行者", "TRIGGER / CDPR", MediaType.ANIME, listOf("赛博朋克", "科幻", "浪漫", "悲剧"), 9.0, "夜之城传奇雇佣兵的绝唱，飞向月球的浪漫誓言。"),
        RecommendedWork("底特律：化身为人", "Quantic Dream", MediaType.GAME, listOf("科幻", "仿生人", "选择", "叙事"), 9.1, "仿生人的自我觉醒之路，每一个选择都在改写未来。"),

        // 悬疑 / 高智 / 深度剧情
        RecommendedWork("非自然死亡", "野木亚纪子", MediaType.MOVIE, listOf("悬疑", "法医", "职场", "神作"), 9.4, "法医解剖台前寻找真相，为了让生者更好地活下去。"),
        RecommendedWork("白夜行", "东野圭吾", MediaType.BOOK, listOf("悬疑", "人性", "绝望", "经典"), 9.1, "只希望能手牵手在太阳下散步，残酷又深情的宿命悲歌。"),
        RecommendedWork("命运石之门", "White Fox", MediaType.ANIME, listOf("悬疑", "时间穿越", "科幻", "神作"), 9.5, "无数条世界线收束的绝望中，为了拯救伙伴的执着抗争。"),

        // 哲学 / 文学 / 精神求索
        RecommendedWork("悉达多", "赫尔曼·黑塞", MediaType.BOOK, listOf("哲学", "灵性", "自我", "成长"), 9.2, "古印度的求道之旅，在河流的涛声中领悟生命的圆融。"),
        RecommendedWork("千与千寻", "宫崎骏", MediaType.ANIME, listOf("奇幻", "成长", "童心", "神作"), 9.4, "迷失神隐世界的少女千寻，找回自我名字的奇幻冒险。"),
    )
}
