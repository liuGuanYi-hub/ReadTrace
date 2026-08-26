package com.example.readtrace.model

enum class TimelineEventType(val icon: String, val displayName: String, val badgeColor: String) {
    START_READING("🏁", "开启阅读之旅", "#C47D5C"),
    READING_SESSION("⏱️", "专注阅读打卡", "#0284C7"),
    NOTE_QUOTE("💬", "灵感与摘录", "#D97706"),
    LOCATION_DISCOVERED("🗺️", "空间叙事地标", "#059669"),
    OUTLINE_CHAPTER("📖", "章节大纲脑图", "#7C3AED"),
    FINISH_REVIEW("🌟", "全书完读复盘", "#DC2626"),
}

data class TimelineEvent(
    val id: String,
    val type: TimelineEventType,
    val timestamp: String,
    val title: String,
    val subtitle: String? = null,
    val content: String? = null,
    val extraMeta: String? = null,
    val rawId: Long = 0L,
    // 日期字段为占位文本（如「待整理」）时置位，展示层用友好文案代替原始时间戳
    val pendingTime: Boolean = false,
) : Comparable<TimelineEvent> {
    override fun compareTo(other: TimelineEvent): Int {
        // 时间倒序或正序
        return timestamp.compareTo(other.timestamp)
    }
}
