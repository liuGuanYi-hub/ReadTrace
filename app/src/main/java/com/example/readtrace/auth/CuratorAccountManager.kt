package com.example.readtrace.auth

import android.content.Context
import android.content.SharedPreferences
import com.example.readtrace.model.AuthSession
import com.example.readtrace.model.AuthStatus
import com.example.readtrace.model.CuratorAccount
import com.example.readtrace.model.CuratorCardTheme
import com.example.readtrace.model.LoginType
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 策展人账号中枢单例管理器
 * 负责本地会话持久化、登录/登出状态机流转与跨模块事件分发
 */
class CuratorAccountManager private constructor(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val listeners = mutableListOf<(CuratorAccount?) -> Unit>()

    var currentAccount: CuratorAccount? = null
        private set

    var authStatus: AuthStatus = AuthStatus.GUEST
        private set

    init {
        loadSession()
    }

    private fun loadSession() {
        val isLoggedIn = prefs.getBoolean(KEY_IS_LOGGED_IN, false)
        val jsonStr = prefs.getString(KEY_ACCOUNT_JSON, null)
        if (isLoggedIn && !jsonStr.isNullOrEmpty()) {
            currentAccount = parseAccountJson(jsonStr)
            authStatus = AuthStatus.AUTHENTICATED
        } else {
            currentAccount = createDefaultGuestAccount()
            authStatus = AuthStatus.GUEST
        }
    }

    fun addAccountListener(listener: (CuratorAccount?) -> Unit) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
            listener(currentAccount)
        }
    }

    fun removeAccountListener(listener: (CuratorAccount?) -> Unit) {
        listeners.remove(listener)
    }

    private fun notifyListeners() {
        listeners.forEach { it(currentAccount) }
    }

    /**
     * 登录
     *
     * @param loginType 认证方式，决定通行证上的绑定徽章
     * @param wechatOpenId 微信互联编号（LoginType.WECHAT 时填写）
     * @param phoneMasked 脱敏手机号（LoginType.PHONE 时填写，如 138****8848）
     * @param thirdPartyAvatarEmoji 第三方头像 emoji，为空时回退预设艺术头像
     */
    fun login(
        email: String,
        nickname: String = "阅读策展人",
        avatarKey: String = "statue_david",
        loginType: LoginType = LoginType.MANUAL,
        wechatOpenId: String = "",
        phoneMasked: String = "",
        thirdPartyAvatarEmoji: String = "",
        curatorTitle: String = "终身荣誉馆长",
        onSuccess: (CuratorAccount) -> Unit,
    ) {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val account = CuratorAccount(
            userId = "RT-${(1000..9999).random()}-2026",
            email = email,
            nickname = if (nickname.isBlank()) "星河馆长" else nickname,
            bio = "在书海与光影中，雕刻精神的永恒轮廓。",
            avatarKey = avatarKey,
            curatorTitle = curatorTitle,
            cardTheme = CuratorCardTheme.OBSIDIAN_GOLD,
            joinedDate = today,
            lastSyncTime = System.currentTimeMillis(),
            isBiometricEnabled = false,
            loginType = loginType,
            wechatOpenId = wechatOpenId,
            phoneMasked = phoneMasked,
            thirdPartyAvatarEmoji = thirdPartyAvatarEmoji,
        )
        saveAccount(account, isLoggedIn = true)
        onSuccess(account)
    }

    /**
     * 将第三方身份（微信 / 手机号）绑定到已登录的通行证上
     *
     * 用于「先本地手写入驻，后补绑第三方凭证」的场景；
     * 未登录时静默返回 false，由调用方决定提示文案。
     */
    fun bindThirdParty(
        loginType: LoginType,
        openId: String = "",
        phoneMasked: String = "",
        avatarEmoji: String = "",
        nickname: String = "",
    ): Boolean {
        val current = currentAccount ?: return false
        if (authStatus != AuthStatus.AUTHENTICATED) return false
        val updated = current.copy(
            loginType = loginType,
            wechatOpenId = openId,
            phoneMasked = phoneMasked,
            thirdPartyAvatarEmoji = avatarEmoji,
            nickname = nickname.ifBlank { current.nickname },
        )
        updateAccount(updated)
        return true
    }

    /**
     * 解绑第三方身份，退回手写入驻模式（保留昵称与资料）
     */
    fun unbindThirdParty(): Boolean {
        val current = currentAccount ?: return false
        val updated = current.copy(
            loginType = LoginType.MANUAL,
            wechatOpenId = "",
            phoneMasked = "",
            thirdPartyAvatarEmoji = "",
        )
        updateAccount(updated)
        return true
    }

    /**
     * 登出回到自由策展人模式
     */
    fun logout() {
        prefs.edit()
            .putBoolean(KEY_IS_LOGGED_IN, false)
            .remove(KEY_ACCOUNT_JSON)
            .apply()
        currentAccount = createDefaultGuestAccount()
        authStatus = AuthStatus.GUEST
        notifyListeners()
    }

    /**
     * 更新策展人资料
     */
    fun updateAccount(newAccount: CuratorAccount) {
        saveAccount(newAccount, isLoggedIn = authStatus == AuthStatus.AUTHENTICATED)
    }

    /**
     * 开启/关闭生物识别速登
     */
    fun setBiometricEnabled(enabled: Boolean) {
        val updated = currentAccount?.copy(isBiometricEnabled = enabled) ?: return
        updateAccount(updated)
    }

    /**
     * 更新最后同步时间
     */
    fun updateSyncTimestamp(timestamp: Long = System.currentTimeMillis()) {
        val updated = currentAccount?.copy(lastSyncTime = timestamp) ?: return
        updateAccount(updated)
    }

    private fun saveAccount(account: CuratorAccount, isLoggedIn: Boolean) {
        currentAccount = account
        authStatus = if (isLoggedIn) AuthStatus.AUTHENTICATED else AuthStatus.GUEST
        prefs.edit()
            .putBoolean(KEY_IS_LOGGED_IN, isLoggedIn)
            .putString(KEY_ACCOUNT_JSON, accountToJson(account))
            .apply()
        notifyListeners()
    }

    private fun createDefaultGuestAccount(): CuratorAccount {
        return CuratorAccount(
            userId = "RT-GUEST-2026",
            email = "",
            nickname = "自由漫游策展人",
            bio = "纯本地掌控 · 尚未绑定云端通行证",
            avatarKey = "statue_david",
            curatorTitle = "本地自由馆长",
            cardTheme = CuratorCardTheme.PARCHMENT_WOOD,
            joinedDate = "2026-09-01",
            lastSyncTime = 0L,
            isBiometricEnabled = false,
        )
    }

    private fun accountToJson(account: CuratorAccount): String {
        return JSONObject().apply {
            put("userId", account.userId)
            put("email", account.email)
            put("nickname", account.nickname)
            put("bio", account.bio)
            put("avatarKey", account.avatarKey)
            put("curatorTitle", account.curatorTitle)
            put("cardTheme", account.cardTheme.name)
            put("joinedDate", account.joinedDate)
            put("lastSyncTime", account.lastSyncTime)
            put("isBiometricEnabled", account.isBiometricEnabled)
            put("totalCurations", account.totalCurations)
            put("loginType", account.loginType.name)
            put("wechatOpenId", account.wechatOpenId)
            put("phoneMasked", account.phoneMasked)
            put("thirdPartyAvatarEmoji", account.thirdPartyAvatarEmoji)
        }.toString()
    }

    private fun parseAccountJson(jsonStr: String): CuratorAccount {
        return try {
            val json = JSONObject(jsonStr)
            val themeName = json.optString("cardTheme", CuratorCardTheme.OBSIDIAN_GOLD.name)
            val theme = try {
                CuratorCardTheme.valueOf(themeName)
            } catch (e: Exception) {
                CuratorCardTheme.OBSIDIAN_GOLD
            }
            CuratorAccount(
                userId = json.optString("userId", "RT-8848-2026"),
                email = json.optString("email", ""),
                nickname = json.optString("nickname", "阅读策展人"),
                bio = json.optString("bio", "在书海与光影中，雕刻精神的永恒轮廓。"),
                avatarKey = json.optString("avatarKey", "statue_david"),
                curatorTitle = json.optString("curatorTitle", "特约星河馆长"),
                cardTheme = theme,
                joinedDate = json.optString("joinedDate", "2026-09-01"),
                lastSyncTime = json.optLong("lastSyncTime", 0L),
                isBiometricEnabled = json.optBoolean("isBiometricEnabled", false),
                totalCurations = json.optInt("totalCurations", 0),
                loginType = parseLoginType(json.optString("loginType", LoginType.MANUAL.name)),
                wechatOpenId = json.optString("wechatOpenId", ""),
                phoneMasked = json.optString("phoneMasked", ""),
                thirdPartyAvatarEmoji = json.optString("thirdPartyAvatarEmoji", ""),
            )
        } catch (e: Exception) {
            createDefaultGuestAccount()
        }
    }

    private fun parseLoginType(name: String): LoginType {
        return try {
            LoginType.valueOf(name)
        } catch (e: Exception) {
            LoginType.MANUAL
        }
    }

    companion object {
        private const val PREF_NAME = "readtrace_curator_prefs"
        private const val KEY_IS_LOGGED_IN = "key_is_logged_in"
        private const val KEY_ACCOUNT_JSON = "key_account_json"

        @Volatile
        private var instance: CuratorAccountManager? = null

        fun getInstance(context: Context): CuratorAccountManager {
            return instance ?: synchronized(this) {
                instance ?: CuratorAccountManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
