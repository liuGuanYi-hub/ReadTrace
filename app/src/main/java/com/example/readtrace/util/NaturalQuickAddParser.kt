package com.example.readtrace.util

import com.example.readtrace.model.BookStatus

/**
 * ✍️ 一句话自然语言速记分词器 (NaturalQuickAddParser)
 *
 * 极简极客输入：`读完 仿生人会梦见电子羊吗 9分 #科幻 #赛博朋克`
 * 本地轻量正则分词，自动提取：
 * - 状态：读完 → FINISHED
 * - 书名：剩余文本
 * - 评分：9分 → 9.0（9星 → 18 越界自动钳制）
 * - 标签：#科幻 #赛博朋克
 */
object NaturalQuickAddParser {

    /** 分词解析结果 */
    data class ParsedQuickLog(
        val title: String,
        val status: BookStatus?,
        val rating: Double?,
        val tags: List<String>,
    )

    /** 状态关键词映射（按匹配优先级排序：长词在前避免「在读」吃掉「已读」类前缀） */
    private val STATUS_KEYWORDS: List<Pair<String, BookStatus>> = listOf(
        "想看" to BookStatus.WISHLIST,
        "想读" to BookStatus.WISHLIST,
        "在看" to BookStatus.READING,
        "在读" to BookStatus.READING,
        "读完" to BookStatus.FINISHED,
        "看完" to BookStatus.FINISHED,
        "已读" to BookStatus.FINISHED,
        "已看" to BookStatus.FINISHED,
        "暂停" to BookStatus.PAUSED,
        "弃读" to BookStatus.DROPPED,
        "弃坑" to BookStatus.DROPPED,
    )

    private val RATING_FEN = Regex("""(\d{1,2}(?:\.\d)?)\s*分""")
    private val RATING_XING = Regex("""(\d{1,2})\s*星""")
    private val TAG_TOKEN = Regex("""#([^\s#，,。]+)""")

    /** 是否像一句话速记：含状态词或评分或标签记号中的至少两个信号 */
    fun looksLikeQuickLog(input: String): Boolean {
        if (input.isBlank() || input.contains("http")) return false
        var signals = 0
        if (STATUS_KEYWORDS.any { (kw, _) -> input.contains(kw) }) signals++
        if (input.contains("#")) signals++
        if (RATING_FEN.containsMatchIn(input) || RATING_XING.containsMatchIn(input)) signals++
        return signals >= 2
    }

    /**
     * 解析一句话速记
     */
    fun parse(input: String): ParsedQuickLog? {
        var text = input.trim()
        if (text.isEmpty()) return null

        // 1. 提取标签
        val tags = TAG_TOKEN.findAll(text).map { it.groupValues[1].trim() }.filter { it.isNotEmpty() }.toList()
        text = TAG_TOKEN.replace(text, " ")

        // 2. 提取评分：优先「X分」，其次「X星」×2；钳制到 1~10
        var rating: Double? = RATING_FEN.find(text)?.let { it.groupValues[1].toDoubleOrNull() }
        if (rating == null) {
            rating = RATING_XING.find(text)?.let { it.groupValues[1].toDoubleOrNull()?.times(2.0) }
        }
        rating = rating?.coerceIn(1.0, 10.0)
        text = RATING_XING.replace(text, " ")
        text = RATING_FEN.replace(text, " ")

        // 3. 提取状态关键词（首个命中；不在意出现位置）
        var status: BookStatus? = null
        for ((keyword, mapped) in STATUS_KEYWORDS) {
            if (text.contains(keyword)) {
                status = mapped
                text = text.replaceFirst(keyword, " ")
                break
            }
        }

        // 4. 书名 = 状态/评分/标签剔除后的全部剩余文本（P38-G8：整段保留，
        //    多词英文书名如 "Snow Crash" 不再被空格截成首词；书名号已抹平）
        val title = text.replace("《", " ").replace("》", " ")
            .trim()
            .replace(Regex("\\s+"), " ")
            .takeIf { it.isNotEmpty() }
            ?: return null

        return ParsedQuickLog(
            title = title,
            status = status,
            rating = rating,
            tags = tags,
        )
    }
}
