package com.example.readtrace.data

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.example.readtrace.model.MediaType

/**
 * 🎛️ 全局用户偏好集中管理器 (UserPreferencesManager)
 *
 * 统一收口散落各处的 SharedPreferences 访问：
 * - readtrace_prefs       → 视图模式（书架/各媒介 Hub 的网格开关）
 * - readtrace_theme_prefs → 夜间模式
 * - readtrace_version_prefs → What's New 上次展示版本
 * - readtrace_reader_prefs → 各作品阅读页码
 *
 * 内部仍按原文件名路由：历史数据零迁移、零破坏；
 *策展人账号 / 播放器 / 预置种子等单归属偏好仍保留在各域管理器内部。
 */
object UserPreferencesManager {

    private const val PREFS_MAIN = "readtrace_prefs"
    private const val PREFS_THEME = "readtrace_theme_prefs"
    private const val PREFS_VERSION = "readtrace_version_prefs"
    private const val PREFS_READER = "readtrace_reader_prefs"

    private const val KEY_PREFIX_GRID_LIBRARY = "pref_is_grid_view_library"
    private const val KEY_PREFIX_GRID_HUB = "pref_is_grid_view_hub_"
    private const val KEY_NIGHT_MODE = "night_mode"
    private const val KEY_LAST_SHOWN_VERSION = "key_last_shown_version"
    private const val KEY_PREFIX_BOOK_PAGE = "book_page_"

    // --- 🌗 夜间模式 (readtrace_theme_prefs) ---

    fun getNightMode(context: Context): Int =
        context.getSharedPreferences(PREFS_THEME, Context.MODE_PRIVATE)
            .getInt(KEY_NIGHT_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)

    fun setNightMode(context: Context, mode: Int) {
        context.getSharedPreferences(PREFS_THEME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_NIGHT_MODE, mode)
            .apply()
    }

    // --- 🗂️ 视图模式 (readtrace_prefs) ---

    fun isLibraryGridView(context: Context): Boolean =
        context.getSharedPreferences(PREFS_MAIN, Context.MODE_PRIVATE)
            .getBoolean(KEY_PREFIX_GRID_LIBRARY, false)

    fun setLibraryGridView(context: Context, isGrid: Boolean) {
        context.getSharedPreferences(PREFS_MAIN, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_PREFIX_GRID_LIBRARY, isGrid)
            .apply()
    }

    fun isHubGridView(context: Context, mediaType: MediaType): Boolean =
        context.getSharedPreferences(PREFS_MAIN, Context.MODE_PRIVATE)
            .getBoolean(KEY_PREFIX_GRID_HUB + mediaType.databaseValue, false)

    fun setHubGridView(context: Context, mediaType: MediaType, isGrid: Boolean) {
        context.getSharedPreferences(PREFS_MAIN, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_PREFIX_GRID_HUB + mediaType.databaseValue, isGrid)
            .apply()
    }

    // --- 📜 版本纪要 (readtrace_version_prefs) ---

    fun getLastShownVersion(context: Context): String =
        context.getSharedPreferences(PREFS_VERSION, Context.MODE_PRIVATE)
            .getString(KEY_LAST_SHOWN_VERSION, "").orEmpty()

    fun setLastShownVersion(context: Context, versionName: String) {
        context.getSharedPreferences(PREFS_VERSION, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_SHOWN_VERSION, versionName)
            .apply()
    }

    // --- 🛡️ WebDAV 同步配置 (readtrace_webdav_prefs) ---

    private const val PREFS_WEBDAV = "readtrace_webdav_prefs"
    private const val KEY_WEBDAV_SERVER = "webdav_server"
    private const val KEY_WEBDAV_USER = "webdav_user"
    private const val KEY_WEBDAV_PASSWORD = "webdav_password"
    private const val SECURE_KEY_WEBDAV_PASSWORD = "webdav_password"
    private const val KEY_WEBDAV_LAST_SYNC = "webdav_last_sync_at"
    private const val KEY_WEBDAV_AUTO_SYNC = "webdav_auto_sync_enabled"
    private const val KEY_WEBDAV_LAST_SYNC_ERROR = "webdav_last_sync_error"

    fun getWebDavServer(context: Context): String =
        context.getSharedPreferences(PREFS_WEBDAV, Context.MODE_PRIVATE)
            .getString(KEY_WEBDAV_SERVER, "").orEmpty()

    fun setWebDavServer(context: Context, server: String) {
        context.getSharedPreferences(PREFS_WEBDAV, Context.MODE_PRIVATE)
            .edit().putString(KEY_WEBDAV_SERVER, server).apply()
    }

    fun getWebDavUser(context: Context): String =
        context.getSharedPreferences(PREFS_WEBDAV, Context.MODE_PRIVATE)
            .getString(KEY_WEBDAV_USER, "").orEmpty()

    fun setWebDavUser(context: Context, user: String) {
        context.getSharedPreferences(PREFS_WEBDAV, Context.MODE_PRIVATE)
            .edit().putString(KEY_WEBDAV_USER, user).apply()
    }

    /** P38-G13：密码改走 AndroidKeyStore 加密仓；首次读取时把旧版本明文迁入并抹掉痕迹 */
    fun getWebDavPassword(context: Context): String {
        val secure = SecurePrefs.get(context, SECURE_KEY_WEBDAV_PASSWORD)
        if (secure.isNotEmpty()) return secure

        val legacy = context.getSharedPreferences(PREFS_WEBDAV, Context.MODE_PRIVATE)
            .getString(KEY_WEBDAV_PASSWORD, "").orEmpty()
        if (legacy.isNotEmpty()) {
            SecurePrefs.put(context, SECURE_KEY_WEBDAV_PASSWORD, legacy)
            context.getSharedPreferences(PREFS_WEBDAV, Context.MODE_PRIVATE)
                .edit().remove(KEY_WEBDAV_PASSWORD).apply()
        }
        return legacy
    }

    fun setWebDavPassword(context: Context, password: String) {
        SecurePrefs.put(context, SECURE_KEY_WEBDAV_PASSWORD, password)
        // 无论如何都清掉旧字段，防加密写入失败时明文残留
        context.getSharedPreferences(PREFS_WEBDAV, Context.MODE_PRIVATE)
            .edit().remove(KEY_WEBDAV_PASSWORD).apply()
    }

    fun getWebDavLastSyncAt(context: Context): Long =
        context.getSharedPreferences(PREFS_WEBDAV, Context.MODE_PRIVATE)
            .getLong(KEY_WEBDAV_LAST_SYNC, 0L)

    fun setWebDavLastSyncAt(context: Context, timestamp: Long) {
        context.getSharedPreferences(PREFS_WEBDAV, Context.MODE_PRIVATE)
            .edit().putLong(KEY_WEBDAV_LAST_SYNC, timestamp).apply()
    }

    /** 最近一次自动同步的失败原因（null=上次同步成功）；静默失败从此可追溯（P38-G1） */
    fun setWebDavLastSyncError(context: Context, message: String?) {
        context.getSharedPreferences(PREFS_WEBDAV, Context.MODE_PRIVATE)
            .edit().putString(KEY_WEBDAV_LAST_SYNC_ERROR, message).apply()
    }

    fun getWebDavLastSyncError(context: Context): String? =
        context.getSharedPreferences(PREFS_WEBDAV, Context.MODE_PRIVATE)
            .getString(KEY_WEBDAV_LAST_SYNC_ERROR, null)

    fun isWebDavAutoSyncEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_WEBDAV, Context.MODE_PRIVATE)
            .getBoolean(KEY_WEBDAV_AUTO_SYNC, true)

    fun setWebDavAutoSyncEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_WEBDAV, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_WEBDAV_AUTO_SYNC, enabled).apply()
    }

    // --- 🤖 AI 助手配置 (readtrace_ai_prefs) ---

    private const val PREFS_AI = "readtrace_ai_prefs"
    private const val KEY_AI_API_KEY = "ai_api_key"
    private const val KEY_AI_BASE_URL = "ai_base_url"
    private const val KEY_AI_MODEL = "ai_model"

    /** 默认接入 B.AI 聚合网关（OpenAI 兼容），任何同协议中转站均可在设置里改填 */
    const val DEFAULT_AI_BASE_URL = "https://api.b.ai/v1"

    /** 默认模型：实测四个免费模型中唯一一个长耗时流式请求仍能完整回包且史实准确的 */
    const val DEFAULT_AI_MODEL = "glm-5.3-flash"

    fun getAiApiKey(context: Context): String =
        context.getSharedPreferences(PREFS_AI, Context.MODE_PRIVATE)
            .getString(KEY_AI_API_KEY, "").orEmpty()

    fun setAiApiKey(context: Context, key: String) {
        context.getSharedPreferences(PREFS_AI, Context.MODE_PRIVATE)
            .edit().putString(KEY_AI_API_KEY, key.trim()).apply()
    }

    fun getAiBaseUrl(context: Context): String =
        context.getSharedPreferences(PREFS_AI, Context.MODE_PRIVATE)
            .getString(KEY_AI_BASE_URL, DEFAULT_AI_BASE_URL).orEmpty().ifBlank { DEFAULT_AI_BASE_URL }

    fun setAiBaseUrl(context: Context, url: String) {
        context.getSharedPreferences(PREFS_AI, Context.MODE_PRIVATE)
            .edit().putString(KEY_AI_BASE_URL, url.trim()).apply()
    }

    fun getAiModel(context: Context): String =
        context.getSharedPreferences(PREFS_AI, Context.MODE_PRIVATE)
            .getString(KEY_AI_MODEL, DEFAULT_AI_MODEL).orEmpty().ifBlank { DEFAULT_AI_MODEL }

    fun setAiModel(context: Context, model: String) {
        context.getSharedPreferences(PREFS_AI, Context.MODE_PRIVATE)
            .edit().putString(KEY_AI_MODEL, model.trim()).apply()
    }

    // --- 📖 阅读页码 (readtrace_reader_prefs) ---

    fun getReadingPage(context: Context, bookId: Long): Int =
        context.getSharedPreferences(PREFS_READER, Context.MODE_PRIVATE)
            .getInt(KEY_PREFIX_BOOK_PAGE + bookId, 0)

    fun saveReadingPage(context: Context, bookId: Long, pageIndex: Int) {
        context.getSharedPreferences(PREFS_READER, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_PREFIX_BOOK_PAGE + bookId, pageIndex)
            .apply()
    }
}
