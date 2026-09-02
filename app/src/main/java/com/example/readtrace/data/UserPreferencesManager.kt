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

    // --- 🖤 主题扩展旗标（OLED 纯黑等，readtrace_theme_prefs） ---

    fun isThemeFlag(context: Context, key: String): Boolean =
        context.getSharedPreferences(PREFS_THEME, Context.MODE_PRIVATE)
            .getBoolean(key, false)

    fun setThemeFlag(context: Context, key: String, value: Boolean) {
        context.getSharedPreferences(PREFS_THEME, Context.MODE_PRIVATE)
            .edit().putBoolean(key, value).apply()
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
    private const val KEY_WEBDAV_LAST_SYNC = "webdav_last_sync_at"

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

    fun getWebDavPassword(context: Context): String =
        context.getSharedPreferences(PREFS_WEBDAV, Context.MODE_PRIVATE)
            .getString(KEY_WEBDAV_PASSWORD, "").orEmpty()

    fun setWebDavPassword(context: Context, password: String) {
        context.getSharedPreferences(PREFS_WEBDAV, Context.MODE_PRIVATE)
            .edit().putString(KEY_WEBDAV_PASSWORD, password).apply()
    }

    fun getWebDavLastSyncAt(context: Context): Long =
        context.getSharedPreferences(PREFS_WEBDAV, Context.MODE_PRIVATE)
            .getLong(KEY_WEBDAV_LAST_SYNC, 0L)

    fun setWebDavLastSyncAt(context: Context, timestamp: Long) {
        context.getSharedPreferences(PREFS_WEBDAV, Context.MODE_PRIVATE)
            .edit().putLong(KEY_WEBDAV_LAST_SYNC, timestamp).apply()
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
