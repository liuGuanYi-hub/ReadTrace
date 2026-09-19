package com.example.readtrace.work

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.example.readtrace.util.ContentRepoClient
import java.util.concurrent.TimeUnit

/**
 * V1 每日节律：后台周期性刷新内容仓库。
 *
 * ## 它做什么
 *
 * 每天一次（约束：有网络），把内容仓库的三个 index 拉一遍并写入
 * [ContentRepoClient] 的磁盘缓存。这样用户下次打开社区页时命中的就是**新鲜缓存**，
 * 界面立刻是新内容，而不必等一次实时请求。
 *
 * ## 它刻意**不做**什么
 *
 * **不合并、不碰内存列表**。远端内容与内存展厅列表的合并必须发生在主线程
 * （[com.example.readtrace.community.repository.CommunityRepository.mergeRemote] 会
 * `clear()` / `addAll()` 那个被渲染路径遍历的列表），而 Worker 跑在后台线程。
 * 因此这里只负责"把新内容搬到本地缓存"，合并交给 UI 层下一次读取时自然发生。
 *
 * ## 为什么用 `Worker` 而不是 `CoroutineWorker`
 *
 * 任务是三个顺序的阻塞网络调用，没有并发需求，同步写法更简单也更容易读。
 *
 * ## 没有做的事（明确记录）
 *
 * 原计划里"每日推送通知"**未实施**：本项目此前没有任何通知基础设施
 * （无 NotificationChannel、无 POST_NOTIFICATIONS 权限），从零搭建需要新增权限声明，
 * 而阅痕是单机优先的个人印记空间——**静默保持内容新鲜**比"每天弹一次推送"更贴合它的气质。
 * 待真有需要推送的运营内容时再单独立项。
 */
class ContentRefreshWorker(
    context: Context,
    params: WorkerParameters,
) : Worker(context, params) {

    override fun doWork(): Result {
        val ctx = applicationContext
        var ok = 0

        val paths = listOf(
            ContentRepoClient.PATH_EXHIBITIONS,
            ContentRepoClient.PATH_DAILY,
            ContentRepoClient.PATH_NOTICES,
        )
        paths.forEach { path ->
            // forceRefresh = true：绕过 24h TTL，确保拿到的是当下最新的一份
            val root = runCatching {
                ContentRepoClient.fetchJsonSync(ctx, path, forceRefresh = true)
            }.getOrNull()
            if (root != null) ok++
        }

        Log.i(TAG, "内容仓库后台刷新完成：$ok/${paths.size} 份")
        // 远端不可用不算失败——内置种子与陈旧缓存已能保证 App 可用，
        // 返回 retry 只会白白消耗电量。
        return Result.success()
    }

    companion object {
        private const val TAG = "ContentRefreshWorker"
        private const val WORK_NAME = "readtrace_daily_content_refresh"

        /**
         * 注册每日刷新任务（幂等，`KEEP` 策略保证重复调用不会堆积）。
         * 在 [com.example.readtrace.ReadTraceApplication.onCreate] 中调用一次即可。
         */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                // 只在有网时跑；不要求充电——一次刷新只有三个小请求，开销可忽略
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<ContentRefreshWorker>(1, TimeUnit.DAYS)
                .setConstraints(constraints)
                .build()

            runCatching {
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request,
                )
            }.onFailure { Log.w(TAG, "注册每日刷新失败：${it.message}") }
        }
    }
}
