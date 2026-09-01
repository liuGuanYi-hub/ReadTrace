package com.example.readtrace.util

/**
 * ✍️ 智能金句提炼引擎 (SmartQuoteDigestHelper)
 *
 * P14：让用户写下的文字具有长久生命力。当用户在作品笔记中撰写 300+ 字
 * 长评或随感时，本地轻量规则引擎自动识别并提取最具哲思与诗意的 1 句高光金句，
 * 用于「那年今日回溯便签」与「桌面灵动陪伴微卡」的核心展示语。
 *
 * 打分维度（规则启发式）：
 * - 修辞信号：比喻词（像/如/仿佛/宛如）、对偶/排比（顿号并列）、破折号；
 * - 哲思信号：抽象名词（时间/孤独/自由/命运/回忆/生命…）；
 * - 长度适中：20~60 字（过短无信息量、过长失金句感）；
 * - 位置加权：首句/末句往往是作者落笔的核心。
 */
object SmartQuoteDigestHelper {

    private val SIMILE_MARKERS = listOf("像", "如同", "仿佛", "宛如", "好似", "恰似", "一如")
    private val ABSTRACT_NOUNS = listOf(
        "时间", "孤独", "自由", "命运", "回忆", "生命", "死亡", "爱", "存在",
        "灵魂", "温柔", "勇气", "救赎", "虚无", "永恒", "宿命", "成长", "告别", "故乡",
    )

    /** 提炼结果：金句原文 + 得分（供调试与候选排序） */
    data class DigestQuote(val quote: String, val score: Double)

    /**
     * 从长文中提炼 1 句高光金句；文本不足 300 字或无可候选时返回 null
     */
    fun digest(longText: String?): DigestQuote? {
        if (longText.isNullOrBlank()) return null
        if (longText.length < 300) return null

        return candidates(longText).firstOrNull()
    }

    /**
     * 提取全部候选金句并按得分降序（供调用方展示 TopN 或自行挑选）
     */
    fun candidates(longText: String): List<DigestQuote> {
        if (longText.isBlank()) return emptyList()

        // 按句末标点切句，保留引号包裹的整句
        val sentences = longText.split(Regex("(?<=[。！？!?…”])"))
            .map { it.trim().trim('。', '！', '？', '!', '?', '；', ' ', '\n') }
            .filter { it.length in 12..80 }

        val firstIndex = 0
        val lastIndex = (sentences.size - 1).coerceAtLeast(0)

        return sentences
            .mapIndexed { index, sentence ->
                var score = 0.0
                if (SIMILE_MARKERS.any { sentence.contains(it) }) score += 2.0
                score += ABSTRACT_NOUNS.count { sentence.contains(it) } * 1.5
                if (sentence.contains("，") || sentence.contains("、")) score += 0.8 // 复句节奏感
                if (sentence.contains("——") || sentence.contains("…")) score += 0.5
                if (index == firstIndex && sentences.size > 1) score += 1.0
                if (index == lastIndex && sentences.size > 1) score += 1.2
                val lengthBonus = 1.0 - kotlin.math.abs(sentence.length - 34) / 60.0
                score += lengthBonus.coerceIn(0.0, 1.0)
                DigestQuote(sentence, score)
            }
            .filter { it.score > 1.0 }
            .sortedByDescending { it.score }
    }
}
