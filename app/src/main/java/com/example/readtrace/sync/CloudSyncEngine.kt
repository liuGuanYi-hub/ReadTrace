package com.example.readtrace.sync

import android.content.Context
import com.example.readtrace.auth.CuratorAccountManager
import com.example.readtrace.data.BookDatabaseHelper
import com.example.readtrace.model.AuthStatus
import java.util.concurrent.Executors

/**
 * Local-First 离线优先云同步引擎（模拟桩）
 * @deprecated 请统一迁移至 [WebDavSyncEngine]，具备真实端到端 WebDAV 增量同步能力。
 */
@Deprecated(
    message = "已由 WebDavSyncEngine 替代，提供真实 WebDAV 同步",
    replaceWith = ReplaceWith("WebDavSyncEngine"),
)
object CloudSyncEngine {

    private val executor = Executors.newSingleThreadExecutor()

    data class SyncResult(
        val success: Boolean,
        val syncedBooksCount: Int,
        val timestamp: Long,
        val message: String,
    )

    /**
     * 触发全量/增量离线优先同步
     */
    fun performSync(
        context: Context,
        onComplete: (SyncResult) -> Unit,
    ) {
        val accountManager = CuratorAccountManager.getInstance(context)
        val dbHelper = BookDatabaseHelper.getInstance(context)

        executor.execute {
            try {
                // 1. 读取本地全量书籍与藏品
                val books = dbHelper.getCachedBooks()
                val totalCount = books.size

                // 2. 模拟/执行增量同步时间戳比对
                Thread.sleep(600) // 模拟网络校验延时

                val syncTime = System.currentTimeMillis()
                accountManager.updateSyncTimestamp(syncTime)

                val result = SyncResult(
                    success = true,
                    syncedBooksCount = totalCount,
                    timestamp = syncTime,
                    message = "云端保险库同步成功（共 ${totalCount} 部精神藏品）",
                )

                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    onComplete(result)
                }
            } catch (e: Exception) {
                val failResult = SyncResult(
                    success = false,
                    syncedBooksCount = 0,
                    timestamp = System.currentTimeMillis(),
                    message = "同步失败: ${e.localizedMessage ?: "网络或存储异常"}",
                )
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    onComplete(failResult)
                }
            }
        }
    }
}
