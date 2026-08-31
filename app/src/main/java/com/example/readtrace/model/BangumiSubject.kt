package com.example.readtrace.model

/**
 * Bangumi 条目（搜索结果与详情统一模型）。
 * 文档：https://bangumi.github.io/api/
 */
data class BangumiSubject(
    val id: Long,
    val name: String,           // 原名
    val nameCn: String?,        // 中文名（可能为空）
    val coverUrl: String?,      // images.large，降级 common/medium/grid
    val summary: String? = null,
    val ratingScore: Double? = null,   // Bangumi 全站评分 0~10
    val date: String? = null,          // 上映/发售/发行日期
    val tags: List<String> = emptyList(),
    val creator: String? = null,       // 解析自 infobox（仅详情接口返回）
    val subjectType: Int = 0,          // Bangumi subject_type：1 书 / 2 动画 / 3 音乐 / 4 游戏 / 6 三次元
) {
    /** 展示标题：优先中文名，回退原名 */
    val displayTitle: String get() = nameCn?.takeIf { it.isNotBlank() } ?: name
}
