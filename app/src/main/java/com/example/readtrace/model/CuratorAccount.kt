package com.example.readtrace.model

import java.io.Serializable

/**
 * 策展人通行证卡面主题
 */
enum class CuratorCardTheme(
    val title: String,
    val primaryColorHex: String,
    val surfaceGradientStart: String,
    val surfaceGradientEnd: String,
    val accentColorHex: String,
) {
    OBSIDIAN_GOLD("曜石黑金", "#FFD700", "#1A1A1E", "#0D0D11", "#C9A050"),
    AURORA_EMERALD("极光翡绿", "#48C78E", "#11241C", "#08140E", "#3A6348"),
    COSMIC_SAPPHIRE("深空星蓝", "#5E81AC", "#101B2B", "#080E18", "#88C0D0"),
    PARCHMENT_WOOD("羊皮和纸", "#8C6E4A", "#F6F1E8", "#ECE2D0", "#9E7638"),
}

/**
 * 策展人艺术头像选项
 */
data class AvatarOption(
    val key: String,
    val name: String,
    val emoji: String,
    val description: String,
)

/**
 * 策展人通行证绑定的登录方式
 *
 * - [MANUAL] 手写入驻：本地自由填写昵称邮箱，无第三方绑定
 * - [WECHAT] 微信互联：微信 OpenID 绑定，可作为跨端恢复凭证
 * - [PHONE] 手机号速登：脱敏手机号绑定，可作为跨端恢复凭证
 */
enum class LoginType(
    val label: String,
    val badge: String,
    val isBound: Boolean,
) {
    MANUAL("手写入驻", "✍️", false),
    WECHAT("微信互联", "💬", true),
    PHONE("手机号速登", "📱", true),
}

/**
 * 策展人账号数据模型
 */
data class CuratorAccount(
    val userId: String = "RT-${(1000..9999).random()}-2026",
    val email: String = "",
    val nickname: String = "先锋策展人",
    val bio: String = "在书海与光影中，雕刻精神的永恒轮廓。",
    val avatarKey: String = "statue_david",
    val curatorTitle: String = "特约星河馆长",
    val cardTheme: CuratorCardTheme = CuratorCardTheme.OBSIDIAN_GOLD,
    val joinedDate: String = "2026-09-01",
    val lastSyncTime: Long = 0L,
    val isBiometricEnabled: Boolean = false,
    val totalCurations: Int = 0,
    // v1.1.0 多维认证扩展字段
    val loginType: LoginType = LoginType.MANUAL,
    val wechatOpenId: String = "",
    val phoneMasked: String = "",
    val thirdPartyAvatarEmoji: String = "",
) : Serializable {

    /**
     * 展示用头像 emoji：第三方登录优先使用第三方头像，否则回退预设艺术头像
     */
    fun displayAvatarEmoji(): String {
        return thirdPartyAvatarEmoji.ifBlank { getAvatarOption(avatarKey).emoji }
    }

    /**
     * 通行卡绑定徽章
     *
     * 只展示脱敏后的标识：微信取 OpenID 头尾各 4 位，手机号本身就是脱敏串。
     * 完整凭证不进入任何 UI 文案，避免在截图、录屏场景泄露。
     */
    fun bindingBadge(): String {
        return when (loginType) {
            LoginType.WECHAT -> if (wechatOpenId.length > 10) {
                "💬 微信 ${wechatOpenId.take(4)}****${wechatOpenId.takeLast(4)}"
            } else {
                "💬 微信互联"
            }
            LoginType.PHONE -> "📱 ${phoneMasked.ifBlank { "已绑手机号" }}"
            LoginType.MANUAL -> ""
        }
    }

    companion object {
        val PRESET_AVATARS = listOf(
            AvatarOption("statue_david", "大卫雕像", "🏛️", "古典理性与不朽美学"),
            AvatarOption("cosmic_star", "星云漫游", "🌌", "深空探索与无垠思维"),
            AvatarOption("cyber_monolith", "黑客方尖碑", "💎", "极客精神与数字解密"),
            AvatarOption("vintage_turntable", "复古黑胶", "💽", "模拟黑胶与模拟质感"),
            AvatarOption("film_director", "胶片导演", "🎬", "光影构图与叙事艺术"),
            AvatarOption("scroll_scholar", "卷轴学者", "📜", "沉思默想与字句漫游"),
            AvatarOption("sakura_otaku", "落樱馆长", "🌸", "二次元与物哀美学"),
            AvatarOption("pixel_knight", "像素游侠", "🕹️", "第十艺术与游戏探索"),
            AvatarOption("philosophy_owl", "密涅瓦之鸮", "🦉", "黄昏起飞的智慧印记"),
            AvatarOption("aurora_prism", "极光棱镜", "✨", "光谱色散与四时光感"),
            AvatarOption("origami_bird", "和纸千纸鹤", "🕊️", "纸张触感与手作温度"),
            AvatarOption("time_capsule", "时空胶囊", "⏳", "时光印记与记忆沉淀"),
        )

        fun getAvatarOption(key: String): AvatarOption {
            return PRESET_AVATARS.find { it.key == key } ?: PRESET_AVATARS[0]
        }
    }
}
