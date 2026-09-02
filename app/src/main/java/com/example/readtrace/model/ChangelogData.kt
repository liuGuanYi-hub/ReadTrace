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
            versionName = "v1.0.5",
            releaseDate = "2026-09-02",
            isLatest = true,
            tagTitle = "🛡️ P20 缺陷清零、性能纵深与全维体验演进 (Grand Release)",
            highlights = listOf(
                "🛡️ 8大缺陷证据级清零：彻底修复径向环定位错位、文化年轮动画、真实 WebDAV 引擎串联、返回键避让及长图位图保护",
                "⚡ ISBN 连续批量扫码：CameraX 手电筒补光常开、双指 1x~5x 变焦缩放与防抖连扫闭环",
                "📋 智能剪贴板 Host 媒介判别：按 URL 域名自动精准识别电影/音乐/图书/游戏并匹配想看/想读/想听落库",
                "🎧 伴读钟白噪音与沉浸打卡：纯 PCM 内存实时合成 4 类无缝循环白噪音，伴读退出自动沉淀专注会话",
                "☁️ WebDAV 每日静默自动同步：启动时智能增量校验，守护跨端数据主权",
                "🔍 速记弹窗拼音优先置顶：输入拼音首字母秒搜本地藏品库并高亮置顶，无需网络请求即刻唤起",
                "🏆 年度年鉴自由选年：历年年鉴自由切换，专注月度分布改由实际分钟数精准聚合",
                "🚀 全链路性能纵深治理：概念网与年鉴统计全量后台异步化，长图导出内存安全保护，消除 N+1 查询",
            ),
        ),
        ChangelogVersion(
            versionName = "v1.0.4",
            releaseDate = "2026-09-02",
            isLatest = false,
            tagTitle = "🚀 极简心流与精神重逢 (Super Epoch)",
            highlights = listOf(
                "⚡ 3秒极速速记：主页「+」直弹半屏速记，边搜边选自动补齐元数据，点状态键即入库",
                "📋 智能剪贴板嗅探：复制书名或豆瓣/Bangumi 链接切回 App，一键 0 误差收录",
                "✍️ 一句话速记分词：「读完 三体 9分 #科幻」自动结构化入库，含 ISBN 扫码录入",
                "🕯️ 那年今日时光回溯：历史同日的完读记忆以羊皮纸便签静谧唤醒",
                "🌌 双链概念网：长评中书写 [[概念]]，跨媒介共鸣一触即达",
                "🛡️ WebDAV 数据主权同步：坚果云/NAS 双向增量合并，备份引擎升级收录 6 大高阶资产",
                "🏆 策展人年度精神年鉴：六页美术馆级画册 + 文化年轮图谱，一键导出印刷级长图",
                "🎛️ 长按径向快捷环与全屏边缘侧滑返回，OLED 曜石真黑熄屏模式上线",
                "🧪 思想炼金碰撞机与 432/528Hz 宇宙引力琴：跨媒介哲学对话即刻生成",
                "🌐 2.5D visionOS 空间标本盒展厅，与经典 3D 展厅双模共存",
            ),
        ),
        ChangelogVersion(
            versionName = "v1.0.3",
            releaseDate = "2026-09-01",
            isLatest = false,
            tagTitle = "🌌 顶级开源交互与底层加固 (Epic Milestone)",
            highlights = listOf(
                "🎨 封面 Palette 自适应极光漫射：根据作品封面色彩动态生成高斯漫射流体背景，沉浸感倍增",
                "🎛️ 列表项手势速滑操作：向右轻滑快捷标记在读，向左轻滑快速移入回收站，伴随轻微触感震动",
                "🌱 4秒撤销微胶囊：操作即时生效并提供 4 秒底部倒计时撤销，彻底消灭烦人的二次确认弹窗",
                "🔍 拼音首字母模糊秒搜：支持输入「st」秒搜《三体》，「nsy」命中《女神异闻录5》",
                "👑 黄金星轨 App 官方图标与深空哲人头像全分辨率切图部署上线",
                "🛡️ SQLite 单例防误关生命周期保护与 6 张子表单事务级联物理删除彻底加固",
                "📜 全景版本演进纪要与 What\'s New 升级探索微视窗上线",
            ),
        ),
        ChangelogVersion(
            versionName = "v1.0.2",
            releaseDate = "2026-08-25",
            isLatest = false,
            tagTitle = "🏛️ 策展人全息通行证与先锋视觉",
            highlights = listOf(
                "🌌 策展人 3D 全息通行证卡片与先锋徽章体系",
                "📻 磁带黑胶双模声学工坊与 432Hz 宇宙引力律动",
                "📊 精神维度拓扑雷达与六维心智模型深度交互",
                "☁️ 端到端 WebDAV 云端备份与数据安全加密",
            ),
        ),
        ChangelogVersion(
            versionName = "v1.0.1",
            releaseDate = "2026-08-10",
            isLatest = false,
            tagTitle = "✨ 泛媒介统一归藏与多维时空画卷",
            highlights = listOf(
                "🎬 书籍/番剧/电影/游戏/音乐五大媒介全链路打通",
                "📜 流体时光画卷 MediaTimeline 深度长图导出",
                "🎴 3D 陀螺仪视差画廊与实体藏书票 Ex-Libris 工坊",
                "⚡ 首页骨架屏瞬时唤醒与 60FPS 流体动效升级",
            ),
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