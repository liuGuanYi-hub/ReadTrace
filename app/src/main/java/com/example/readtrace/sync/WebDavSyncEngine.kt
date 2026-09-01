package com.example.readtrace.sync

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.example.readtrace.data.BookDatabaseHelper
import com.example.readtrace.data.UserPreferencesManager
import com.example.readtrace.util.BackupHelper
import java.time.OffsetDateTime
import java.util.concurrent.Executors
import org.json.JSONObject

/**
 * 🛡️ Local-First WebDAV 无感增量同步引擎 (WebDavSyncEngine)
 *
 * 零中心化服务器依赖，数据绝对归属用户自己（坚果云 / Nextcloud / NAS）。
 * 增量合并算法（内容级去重 + 双向拉推）：
 * 1. 拉取远端 `readtrace/backup.json`（404 视为首次同步）；
 * 2. 远端有数据 → 走 [BackupHelper.parseJsonBackup] + `importFullBackup`
 *    事务级联合入（作品按标题+创作者去重、笔记按内容去重、6 大高阶资产按语义去重）；
 * 3. 合并完成后，把本地（含远端合并结果）全量导出，重新 PUT 回云端，
 *    保证多端最终收敛一致；封面图片体积大，仍走本地存储（跨机可重新匹配）。
 */
object WebDavSyncEngine {

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private const val REMOTE_DIR = "/readtrace"
    private const val REMOTE_BACKUP = "/readtrace/backup.json"
    private const val REMOTE_MANIFEST = "/readtrace/manifest.json"

    data class SyncResult(
        val success: Boolean,
        val pulledWorks: Int,
        val pulledNotes: Int,
        val pushedWorks: Int,
        val firstSync: Boolean,
        val message: String,
    )

    data class WebDavConfig(
        val serverUrl: String,
        val username: String,
        val password: String,
    ) {
        val isConfigured: Boolean get() = serverUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank()
    }

    fun loadConfig(context: Context): WebDavConfig = WebDavConfig(
        serverUrl = UserPreferencesManager.getWebDavServer(context),
        username = UserPreferencesManager.getWebDavUser(context),
        password = UserPreferencesManager.getWebDavPassword(context),
    )

    fun saveConfig(context: Context, config: WebDavConfig) {
        UserPreferencesManager.setWebDavServer(context, config.serverUrl.trim())
        UserPreferencesManager.setWebDavUser(context, config.username.trim())
        UserPreferencesManager.setWebDavPassword(context, config.password)
    }

    fun toClientConfig(config: WebDavConfig) = WebDavClient.Config(config.serverUrl, config.username, config.password)

    /**
     * 触发一次双向增量同步（后台线程执行，回调统一切主线程）
     */
    fun performSync(context: Context, onComplete: (SyncResult) -> Unit) {
        val appContext = context.applicationContext
        executor.execute {
            val result = runCatching { doSync(appContext) }
                .getOrElse {
                    SyncResult(false, 0, 0, 0, false, "同步失败: ${it.message ?: "网络异常"}")
                }
            mainHandler.post { onComplete(result) }
        }
    }

    private fun doSync(context: Context): SyncResult {
        val config = loadConfig(context)
        if (!config.isConfigured) {
            return SyncResult(false, 0, 0, 0, false, "尚未配置 WebDAV 服务器")
        }
        val clientConfig = toClientConfig(config)

        // 1. 拉取远端备份（404 = 首次同步）
        val remote = WebDavClient.getText(clientConfig, REMOTE_BACKUP)
        if (!remote.success) {
            return SyncResult(false, 0, 0, 0, false, "远端不可达: ${remote.message ?: "HTTP ${remote.httpCode}"}")
        }
        var pulledWorks = 0
        var pulledNotes = 0
        var firstSync = remote.body == null

        // 2. 远端有数据 → 合入本地（内容级去重，幂等）
        if (remote.body != null) {
            val (items, _) = BackupHelper.parseJsonBackup(remote.body)
            if (items.isNotEmpty()) {
                val (works, notes) = BookDatabaseHelper.getInstance(context).importFullBackup(items)
                pulledWorks = works
                pulledNotes = notes
            }
        }

        // 3. 本地（含合并结果）全量导出并推送
        val dbHelper = BookDatabaseHelper.getInstance(context)
        val fullBackup = dbHelper.getAllFullWorkBackups()
        val pushJson = BackupHelper.generateJsonBackup(fullBackup)
        val put = WebDavClient.putText(clientConfig, REMOTE_BACKUP, pushJson)
        if (!put.success) {
            return SyncResult(false, pulledWorks, pulledNotes, 0, firstSync, "上传失败: ${put.message ?: "HTTP ${put.httpCode}"}")
        }

        // 4. 更新远端清单
        WebDavClient.makeCollection(clientConfig, REMOTE_DIR)
        WebDavClient.putText(clientConfig, REMOTE_MANIFEST, buildManifest(context, fullBackup.size))
        UserPreferencesManager.setWebDavLastSyncAt(context, System.currentTimeMillis())

        return SyncResult(
            success = true,
            pulledWorks = pulledWorks,
            pulledNotes = pulledNotes,
            pushedWorks = fullBackup.size,
            firstSync = firstSync,
            message = if (firstSync) {
                "首次上传完成：${fullBackup.size} 部藏品已备份至云端"
            } else {
                "同步完成：拉入 $pulledWorks 部作品 / $pulledNotes 条笔记，云端共 ${fullBackup.size} 部"
            },
        )
    }

    private fun buildManifest(context: Context, worksCount: Int): String =
        JSONObject().apply {
            put("app", "ReadTrace")
            put("schemaVersion", 4)
            put("worksCount", worksCount)
            put("lastSyncAt", OffsetDateTime.now().toString())
            put("device", android.os.Build.MODEL ?: "Android")
        }.toString()
}
