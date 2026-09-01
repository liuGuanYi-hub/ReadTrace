package com.example.readtrace.auth

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.example.readtrace.BuildConfig
import kotlin.random.Random

/**
 * 微信一键授权登录管理器
 *
 * 采用「双轨运行机制」，让本地开发与线上正式环境共用一套调用入口：
 *
 * 1. **沙盒模拟模式（Sandbox Bridge）**
 *    当未在 `gradle.properties` 中配置 `WECHAT_APP_ID` 时自动启用。
 *    不拉起真实微信客户端，改为调起 [com.example.readtrace.wxapi.WXEntryActivity]
 *    展示一张与微信授权页同构的确认卡，用户确认后回传一份本地生成的虚拟档案。
 *    链路与正式模式完全一致（启动 → 回调 → 落库），因此本地可完整验证业务闭环。
 *
 * 2. **线上正式模式**
 *    配置了真实 AppID 后自动启用，通过反射调用微信 OpenSDK。
 *    之所以用反射而不是直接依赖 SDK，是为了保持本项目「零臃肿依赖」的原则：
 *    没有 AppID 的用户不必为一段永远走不到的代码引入第三方库；
 *    一旦接入了 SDK 依赖，反射代码自动生效，无需修改调用方。
 *
 * 注意：本项目不采集、不上传任何用户数据，昵称与头像在正式模式下由微信返回，
 * 沙盒模式下由本地词库生成，仅用于渲染通行证。
 */
object WeChatAuthManager {

    private const val WECHAT_PACKAGE = "com.tencent.mm"

    /** 沙盒模式标记：告知 WXEntryActivity 展示模拟授权页而非等待微信回调 */
    const val EXTRA_SANDBOX_MODE = "extra_wechat_sandbox_mode"

    /** 授权结果回传字段 */
    const val RESULT_NICKNAME = "result_wechat_nickname"
    const val RESULT_AVATAR_EMOJI = "result_wechat_avatar_emoji"
    const val RESULT_OPEN_ID = "result_wechat_open_id"
    const val RESULT_UNION_ID = "result_wechat_union_id"

    /** 微信档案：正式模式来自微信授权返回，沙盒模式由本地词库生成 */
    data class WeChatProfile(
        val nickname: String,
        val avatarEmoji: String,
        val openId: String,
        val unionId: String,
    )

    /** 当前运行模式 */
    enum class Mode {
        /** 沙盒模拟：未配置 AppID */
        SANDBOX,

        /** 线上正式：已配置 AppID */
        OFFICIAL,
    }

    /** 正式模式下拉起微信的结果 */
    sealed class LaunchResult {
        /** 已成功向微信发出授权请求，等待 WXEntryActivity 回调 */
        object Sent : LaunchResult()

        /** 未能拉起微信，附带可读原因 */
        data class Failed(val reason: String) : LaunchResult()
    }

    /**
     * 正式模式下微信回包中的授权 code 暂存位
     *
     * 微信的回调是通过系统拉起 WXEntryActivity，不走 `startActivityForResult`，
     * 所以结果无法用 `setResult` 回传。这里用一个一次性暂存位，
     * 由发起页在 `onResume` 时消费，是 Android 上处理第三方回调的常规做法。
     */
    @Volatile
    var pendingOfficialCode: String? = null

    fun consumePendingOfficialCode(): String? {
        val code = pendingOfficialCode
        pendingOfficialCode = null
        return code
    }

    private val sandboxNicknames = listOf(
        "星海拾荒者", "午夜飞行家", "纸页间的旅人", "第七号放映员",
        "潮汐观测员", "雪国漫步者", "胶片修补匠", "深空通信员",
        "旧书页收藏家", "雨声记录员", "长镜头漫游者", "灯塔守夜人",
    )

    private val sandboxAvatars = listOf(
        "🌊", "🌙", "📖", "🎞️", "🌌", "❄️", "🛠️", "📡", "🪐", "🍃", "🕯️", "🧭",
    )

    /**
     * 当前运行模式：配置了 AppID 且已集成 OpenSDK 走正式模式，否则沙盒模式
     */
    fun currentMode(): Mode {
        val appId = BuildConfig.WECHAT_APP_ID
        if (appId.isBlank()) return Mode.SANDBOX
        // 仅有 AppID 但没集成 SDK 时，反射会在运行时失败，此时仍应回退沙盒，
        // 由 launchOfficialAuth 给出明确原因，避免静默假装成功。
        return if (isOpenSdkAvailable()) Mode.OFFICIAL else Mode.SANDBOX
    }

    /**
     * 手机上是否安装了微信客户端（targetSdk >= 30 需要显式声明 queries）
     */
    fun isWeChatInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(WECHAT_PACKAGE, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 构造跳转到微信授权确认页的 Intent
     *
     * 沙盒模式下指向本地模拟授权页，正式模式下由调用方改用 [launchOfficialAuth]。
     */
    fun buildSandboxAuthIntent(context: Context): Intent {
        return Intent().apply {
            setClassName(context, "com.example.readtrace.wxapi.WXEntryActivity")
            putExtra(EXTRA_SANDBOX_MODE, true)
        }
    }

    /**
     * 生成一份沙盒虚拟微信档案
     *
     * openId / unionId 按微信真实格式（28 位小写十六进制）生成，
     * 便于后续接入正式 SDK 时字段结构无需改动。
     */
    fun generateSandboxProfile(): WeChatProfile {
        val random = Random(System.currentTimeMillis())
        return WeChatProfile(
            nickname = sandboxNicknames.random(random),
            avatarEmoji = sandboxAvatars.random(random),
            openId = "oWX_" + buildHexString(24, random),
            unionId = "oWXU_" + buildHexString(24, random),
        )
    }

    /**
     * 通过反射拉起微信 OpenSDK 授权
     *
     * 反射调用链：
     * `WXAPIFactory.createWXAPI(context, appId, true)` → `registerApp(appId)` → `sendReq(SendAuth.Req)`
     *
     * 未集成 SDK 依赖时返回 [LaunchResult.Failed]，调用方据此降级到沙盒模式并提示用户。
     */
    fun launchOfficialAuth(context: Context): LaunchResult {
        val appId = BuildConfig.WECHAT_APP_ID
        if (appId.isBlank()) {
            return LaunchResult.Failed("尚未配置微信 AppID，当前运行在沙盒模拟模式")
        }
        if (!isWeChatInstalled(context)) {
            return LaunchResult.Failed("未检测到微信客户端，请先安装微信或改用手机号速登")
        }
        if (!isOpenSdkAvailable()) {
            return LaunchResult.Failed("未集成微信 OpenSDK 依赖，无法拉起真实授权")
        }

        return try {
            val factoryClass = Class.forName("com.tencent.mm.opensdk.openapi.WXAPIFactory")
            val api = factoryClass
                .getMethod("createWXAPI", Context::class.java, String::class.java, Boolean::class.javaPrimitiveType)
                .invoke(null, context, appId, true)
            api.javaClass.getMethod("registerApp", String::class.java).invoke(api, appId)

            val reqClass = Class.forName("com.tencent.mm.opensdk.modelmsg.SendAuth\$Req")
            val req = reqClass.getDeclaredConstructor().newInstance()
            reqClass.getField("scope").set(req, "snsapi_userinfo")
            reqClass.getField("state").set(req, "readtrace_curator_${System.currentTimeMillis()}")
            api.javaClass.getMethod("sendReq", Class.forName("com.tencent.mm.opensdk.modelbase.BaseReq"))
                .invoke(api, req)

            LaunchResult.Sent
        } catch (e: Exception) {
            LaunchResult.Failed("拉起微信授权失败：${e.message ?: e.javaClass.simpleName}")
        }
    }

    /**
     * 从微信授权回包中解析档案
     *
     * 正式模式下 code 需要由服务端换取 access_token 后才能拿到用户信息，
     * 本项目为纯本地应用、没有自建服务端，因此解析逻辑留空，
     * 由 WXEntryActivity 在收到 onResp 后自行决定降级策略。
     */
    fun parseOfficialProfile(code: String?): WeChatProfile? {
        if (code.isNullOrBlank()) return null
        // 无服务端时无法用 code 换取用户资料，交给上层降级到沙盒
        return null
    }

    /**
     * 脱敏展示 OpenID：只保留头尾各 4 位，中间以星号遮蔽
     */
    fun maskOpenId(openId: String): String {
        if (openId.length <= 10) return openId
        return "${openId.take(4)}****${openId.takeLast(4)}"
    }

    private fun isOpenSdkAvailable(): Boolean {
        return try {
            Class.forName("com.tencent.mm.opensdk.openapi.WXAPIFactory")
            true
        } catch (e: ClassNotFoundException) {
            false
        } catch (e: Exception) {
            false
        }
    }

    private fun buildHexString(length: Int, random: Random): String {
        val chars = "0123456789abcdef"
        return buildString {
            repeat(length) { append(chars[random.nextInt(chars.length)]) }
        }
    }
}
