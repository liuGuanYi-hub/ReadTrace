package com.example.readtrace.auth

import android.content.Context
import android.content.SharedPreferences
import com.example.readtrace.model.AuthSession
import com.example.readtrace.model.AuthStatus
import com.example.readtrace.model.CuratorAccount
import com.example.readtrace.model.CuratorCardTheme
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
     */
    fun login(
        email: String,
        nickname: String = "先锋策展人",
        avatarKey: String = "statue_david",
        onSuccess: (CuratorAccount) -> Unit,
    ) {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val account = CuratorAccount(
            userId = "RT-${(1000..9999).random()}-2026",
            email = email,
            nickname = if (nickname.isBlank()) "星河馆长" else nickname,
            bio = "在书海与光影中，雕刻精神的永恒轮廓。",
            avatarKey = avatarKey,
            curatorTitle = "先锋终身馆长",
            cardTheme = CuratorCardTheme.OBSIDIAN_GOLD,
            joinedDate = today,
            lastSyncTime = System.currentTimeMillis(),
            isBiometricEnabled = false,
        )
        saveAccount(account, isLoggedIn = true)
        onSuccess(account)
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
                nickname = json.optString("nickname", "先锋策展人"),
                bio = json.optString("bio", "在书海与光影中，雕刻精神的永恒轮廓。"),
                avatarKey = json.optString("avatarKey", "statue_david"),
                curatorTitle = json.optString("curatorTitle", "特约星河馆长"),
                cardTheme = theme,
                joinedDate = json.optString("joinedDate", "2026-09-01"),
                lastSyncTime = json.optLong("lastSyncTime", 0L),
                isBiometricEnabled = json.optBoolean("isBiometricEnabled", false),
                totalCurations = json.optInt("totalCurations", 0),
            )
        } catch (e: Exception) {
            createDefaultGuestAccount()
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
