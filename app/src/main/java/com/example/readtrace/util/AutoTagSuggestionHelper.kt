package com.example.readtrace.util

import com.example.readtrace.model.BangumiSubject
import com.example.readtrace.model.MediaType

/**
 * 🏷️ 多源元数据智能标签提炼引擎 (AutoTagSuggestionHelper)
 *
 * 当用户通过搜索（豆瓣/Bangumi/Steam/网易云）选中某部作品时，
 * 从返回流中解析作品类型、流派风格与简介关键词，提炼出 6~10 个高匹配度候选标签：
 * 1. 源标签优先：保留数据源自带的题材/流派标签（过滤泛化噪声词）；
 * 2. 媒介类型兜底：确保至少含 1 个媒介属性标签（科幻/文学/番剧/游戏…按媒介语境）；
 * 3. 简介关键词补充：对 summary 做轻量分词，取高频实义词补足到目标数量。
 */
object AutoTagSuggestionHelper {

    /** 泛化噪声词：出现率过高、对检索几乎无区分度的标签 */
    private val NOISE_TAGS = setOf(
        "动画", "漫画", "游戏", "小说", "轻小说", "电影", "音乐", "日漫", "国产",
        "原创", "漫画系列", "单一", "日本", "中国", "美国", "韩国", "欧美",
        "2000s", "2010s", "2020s", "1990s", "TV", "OVA", "剧场版",
    )

    /** 停用词：简介分词时过滤 */
    private val STOPWORDS = setOf(
        "的", "了", "是", "在", "我", "他", "她", "它", "们", "这", "那", "与", "和", "或",
        "一个", "这个", "那个", "自己", "没有", "但是", "因为", "所以", "以及", "为了",
        "故事", "作品", "讲述", "描写", "关于", "以及", "还有", "并且", "然后", "开始",
        "最终", "成为", "成了", "已经", "可能", "可以", "就是", "不是", "并且", "各种",
        "the", "and", "for", "with", "from", "that", "this", "have", "has",
    )

    /** 各媒介的语境兜底标签 */
    private val MEDIA_FALLBACK_TAGS = mapOf(
        MediaType.BOOK to listOf("文学", "小说"),
        MediaType.MOVIE to listOf("电影", "影像"),
        MediaType.ANIME to listOf("番剧", "二次元"),
        MediaType.GAME to listOf("游戏", "互动叙事"),
        MediaType.MUSIC to listOf("音乐", "听觉"),
    )

    /** 简介分词的目标最大长度：超长短语/句子切片不作为标签候选 */
    private const val KEYWORD_MAX_LENGTH = 6

    /**
     * 为搜索选中的作品提炼 6~10 个候选标签（按匹配优先级排序，已去重去噪）
     */
    fun suggestTags(subject: BangumiSubject, mediaType: MediaType, limit: Int = 8): List<String> {
        val result = mutableListOf<String>()

        // 1. 源标签（保留顺序 = 原始热度序）
        subject.tags.forEach { tag ->
            val clean = tag.trim()
            if (clean.isNotEmpty() && clean.length <= KEYWORD_MAX_LENGTH &&
                !NOISE_TAGS.contains(clean) && !result.contains(clean)
            ) {
                result.add(clean)
            }
        }

        // 2. 媒介语境兜底：源标签不足时补媒介属性标签
        val fallback = MEDIA_FALLBACK_TAGS[mediaType].orEmpty()
        fallback.forEach { tag ->
            if (result.size < limit && !result.contains(tag)) result.add(tag)
        }

        // 3. 简介关键词补足
        if (result.size < limit) {
            deriveKeywordsFromText(subject.summary.orEmpty()).forEach { keyword ->
                if (result.size >= limit) return@forEach
                if (!result.contains(keyword)) result.add(keyword)
            }
        }

        return result.take(limit.coerceIn(1, 10))
    }

    /**
     * 轻量中文分词：按标点/空白切片 → 过滤停用词与短词 → 按出现频次降序取词
     */
    fun deriveKeywordsFromText(text: String, limit: Int = 6): List<String> {
        if (text.isBlank()) return emptyList()
        val slices = text.split(Regex("[，。！？、；：\"“”'（）()\\[\\]【】\\s\\n,.;:!?]+"))
        val freq = LinkedHashMap<String, Int>()
        slices.forEach { raw ->
            val word = raw.trim()
            if (word.length < 2 || word.length > KEYWORD_MAX_LENGTH) return@forEach
            if (STOPWORDS.contains(word)) return@forEach
            if (word.all { it.isDigit() }) return@forEach
            freq[word] = (freq[word] ?: 0) + 1
        }
        return freq.entries.sortedByDescending { it.value }.take(limit).map { it.key }
    }
}
