package com.example.readtrace.model

/**
 * 版本演进纪要数据模型 (Changelog Version Record)
 */
data class ChangelogVersion(
    val versionName: String,
    val releaseDate: String,
    val isLatest: Boolean = false,
    val tagTitle: String,
    val highlights: List<String>,
)

object ChangelogRepository {

    val versionHistory: List<ChangelogVersion> = listOf(
        ChangelogVersion(
            versionName = "v4.3.0",
            releaseDate = "2026-09-02",
            isLatest = true,
            tagTitle = "🌌 顶级开源交互与底层加固 (Epic Milestone)",
            highlights = listOf(
                "🎨 封面 Palette 自适应极光漫射：根据作品封面色彩动态生成高斯漫射流体背景，沉浸感倍增",
                "🎛️ 列表项手势速滑操作：向右轻滑快捷标记在读，向左轻滑快速移入回收站，伴随轻微触感震动",
                "🌱 4秒撤销微胶囊：操作即时生效并提供 4 秒底部倒计时撤销，彻底消灭烦人的二次确认弹窗",
                "🔍 拼音首字母模糊秒搜：支持输入「st」秒搜《三体》，「nsy」命中《女神异闻录5》",
                "👑 黄金星轨 App 官方图标与深空哲人头像全分辨率切图部署上线",
                "🛡️ SQLite 单例防误关生命周期保护与 6 张子表单事务级联物理删除彻底加固",
                "📜 全景版本演进纪要与 What\'s New 升级探索微视窗上线"
            )
        ),
        ChangelogVersion(
            versionName = "v4.2.0",
            releaseDate = "2026-08-25",
            isLatest = false,
            tagTitle = "🏛️ 策展人全息通行证与先锋视觉",
            highlights = listOf(
                "🌌 策展人 3D 全息通行证卡片与先锋徽章体系",
                "📻 磁带黑胶双模声学工坊与 432Hz 宇宙引力律动",
                "📊 精神维度拓扑雷达与六维心智模型深度交互",
                "☁️ 端到端 WebDAV 云端备份与数据安全加密"
            )
        ),
        ChangelogVersion(
            versionName = "v4.1.0",
            releaseDate = "2026-08-10",
            isLatest = false,
            tagTitle = "✨ 泛媒介统一归藏与多维时空画卷",
            highlights = listOf(
                "🎬 书籍/番剧/电影/游戏/音乐五大媒介全链路打通",
                "📜 流体时光画卷 MediaTimeline 深度长图导出",
                "🎴 3D 陀螺仪视差画廊与实体藏书票 Ex-Libris 工坊",
                "⚡ 首页骨架屏瞬时唤醒与 60FPS 流体动效升级"
            )
        ),
        ChangelogVersion(
            versionName = "v1.0.0",
            releaseDate = "2026-06-01",
            isLatest = false,
            tagTitle = "🌱 阅痕 ReadTrace 最初启航",
            highlights = listOf(
                "📖 本地作品记录、星级评分与富文本读书笔记",
                "🏷️ 自定义标签多维筛选与年度阅读统计",
                "🔐 纯本地离线 SQLite 数据库存储，零隐私泄露"
            )
        )
    )
}