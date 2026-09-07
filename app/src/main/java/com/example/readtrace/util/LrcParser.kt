package com.example.readtrace.util

/**
 * 歌词单行数据模型
 * @param timeMs 该行歌词的起始时间点（毫秒）
 * @param text 歌词文本内容
 */
data class LyricEntry(
    val timeMs: Long,
    val text: String,
)

/**
 * 🎶 轻量标准 LRC 歌词解析引擎
 * 支持标准 [mm:ss.xx] / [mm:ss.xxx] 时间戳，多时间戳复用行，以及元数据头过滤
 */
object LrcParser {
    private val TIME_REGEX = Regex("\\[(\\d{1,2}):(\\d{2})(?:\\.(\\d{1,3}))?]")

    fun parse(lrcContent: String?): List<LyricEntry> {
        if (lrcContent.isNullOrBlank()) return emptyList()
        val entries = mutableListOf<LyricEntry>()
        lrcContent.lines().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isBlank()) return@forEach

            // 过滤 LRC 头部元数据标签：[ti:...], [ar:...], [al:...], [by:...], [offset:...], [length:...]
            if (line.matches(Regex("^\\[(ti|ar|al|by|offset|length):.*]$", RegexOption.IGNORE_CASE))) {
                return@forEach
            }

            val matches = TIME_REGEX.findAll(line).toList()
            if (matches.isEmpty()) return@forEach

            val lyricText = line.substring(matches.last().range.last + 1).trim()
            if (lyricText.isBlank()) return@forEach

            for (match in matches) {
                val min = match.groupValues[1].toLongOrNull() ?: 0L
                val sec = match.groupValues[2].toLongOrNull() ?: 0L
                val msStr = match.groupValues.getOrNull(3).orEmpty()
                val ms = when (msStr.length) {
                    1 -> (msStr.toLongOrNull() ?: 0L) * 100L
                    2 -> (msStr.toLongOrNull() ?: 0L) * 10L
                    3 -> msStr.toLongOrNull() ?: 0L
                    else -> 0L
                }
                val totalMs = min * 60_000L + sec * 1_000L + ms
                entries.add(LyricEntry(totalMs, lyricText))
            }
        }
        return entries.sortedBy { it.timeMs }
    }
}
