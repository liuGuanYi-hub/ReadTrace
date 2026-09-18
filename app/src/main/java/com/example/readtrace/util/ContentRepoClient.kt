package com.example.readtrace.util

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger

/**
 * 阅痕内容仓库客户端（V0 ·「仓库即 CMS」）
 *
 * ## 为什么需要它
 *
 * 社区展厅原先完全来自 [com.example.readtrace.community.repository.CommunityRepository]
 * 的内存种子数据，**无任何远端来源**——内容永不更新，是 App 里最像静态文档的模块。
 * 本客户端让策展内容可持续更新而**不必发版**：内容托管在独立公开仓库
 * `liuGuanYi-hub/readtrace-content`，客户端只读拉取 JSON。
 *
 * ## 四级降级链（从优到劣）
 *
 * 1. **新鲜缓存**（24h 内）→ 直接返回，不发请求
 * 2. **镜像链实时拉取** → jsDelivr（国内可达性通常更好）→ raw.githubusercontent.com
 * 3. **陈旧缓存**（忽略 TTL）→ 网络不可用时的兜底
 * 4. 返回 null → 由调用方用**内置种子**兜底（保证断网冷启动正常）
 *
 * ## 安全约束
 *
 * - 只信任固定的 `<owner>/<repo>@<branch>` 路径，**不接受调用方传入任意 URL**
 * - 不执行远端返回的任何内容，只解析 JSON
 *
 * 骨架沿用 [NeteaseClient]（cache-first / 超时 / 节流 / 失败降级），
 * 刻意不引入 Retrofit/Moshi——本项目网络层一贯为裸 HttpURLConnection + org.json。
 */
object ContentRepoClient {

    private const val TAG = "ContentRepoClient"

    // ---------------------------------------------------------------- 仓库定位
    // 独立于主仓库：主仓是代码，本仓是内容。内容可被 PR 投稿，不打扰主仓 Issue 区。
    private const val OWNER_REPO = "liuGuanYi-hub/readtrace-content"
    private const val BRANCH = "main"

    private const val CDN_BASE = "https://cdn.jsdelivr.net/gh/$OWNER_REPO@$BRANCH"
    private const val RAW_BASE = "https://raw.githubusercontent.com/$OWNER_REPO/$BRANCH"

    private const val UA = "ReadTrace-Content/1.0 (+https://github.com/liuGuanYi-hub/ReadTrace)"
    private const val CONNECT_TIMEOUT_MS = 6000
    private const val READ_TIMEOUT_MS = 12_000
    private const val CACHE_DIR = "content_repo_cache"
    private const val CACHE_TTL_MS = 24L * 60 * 60 * 1000
    private const val CACHE_MAX_FILES = 12
    private const val REQUEST_INTERVAL_MS = 400L

    private val lastRequestAt = AtomicInteger(0)

    // ---------------------------------------------------------------- 资源路径

    /** 展厅策展位列表 */
    const val PATH_EXHIBITIONS = "exhibitions/index.json"

    /** 每日策展位（按日期数组，V1 的每日节律会用） */
    const val PATH_DAILY = "daily/index.json"

    /** 版本公告 / What's New 运营位 */
    const val PATH_NOTICES = "notices/index.json"

    /**
     * 拉取一份内容 JSON（cache-first，失败逐级降级）。
     *
     * **本方法会阻塞网络，必须在后台线程调用。**
     *
     * @param forceRefresh 下拉强刷 / 首次进入需强制取新时传 true，跳过新鲜缓存直接请求
     * @return 解析成功的 JSONObject；网络与缓存都不可用时返回 null（调用方用内置种子兜底）
     */
    fun fetchJsonSync(context: Context, path: String, forceRefresh: Boolean = false): JSONObject? {
        val key = cacheKeyOf(path)

        // ① 新鲜缓存
        if (!forceRefresh) {
            readCache(context, key, allowStale = false)?.let { return it }
        }

        // ② 镜像链实时拉取
        fetchWithMirrors(path)?.let { text ->
            val obj = runCatching { JSONObject(text) }.getOrNull()
            if (obj != null) {
                writeCache(context, key, obj)
                return obj
            }
            Log.w(TAG, "远端 $path 内容非合法 JSON，已丢弃")
        }

        // ③ 陈旧缓存（忽略 TTL）
        readCache(context, key, allowStale = true)?.let {
            Log.w(TAG, "网络不可用，回退陈旧缓存：$path")
            return it
        }

        // ④ 交给调用方用内置种子兜底
        return null
    }

    /**
     * 远端内容的 `version` 字段（用于增量判断与展示「已收录 N 天」）。
     * 缺省返回 0。
     */
    fun versionOf(root: JSONObject?): Int = root?.optInt("version", 0) ?: 0

    /**
     * 远端内容的 `updated_at` 文案（如 `2026-09-18`），用于以真实提交时间替代虚构的社交数字。
     */
    fun updatedAtOf(root: JSONObject?): String =
        root?.optString("updated_at").orEmpty().takeIf { it.isNotBlank() } ?: "未知"

    // ---------------------------------------------------------------- 缓存

    private fun cacheKeyOf(path: String): String = md5Hex("content|$OWNER_REPO|$path")

    private fun cacheFile(context: Context, key: String): File =
        File(File(context.filesDir, CACHE_DIR).apply { if (!exists()) mkdirs() }, "$key.json")

    private fun readCache(context: Context, key: String, allowStale: Boolean): JSONObject? = runCatching {
        val file = cacheFile(context, key)
        if (!file.exists() || file.length() == 0L) return@runCatching null

        val wrapper = JSONObject(file.readText(StandardCharsets.UTF_8))
        val age = System.currentTimeMillis() - wrapper.optLong("ts", 0L)
        if (!allowStale && age > CACHE_TTL_MS) return@runCatching null

        wrapper.optJSONObject("payload")
    }.getOrNull()

    private fun writeCache(context: Context, key: String, payload: JSONObject) {
        runCatching {
            val wrapper = JSONObject()
                .put("ts", System.currentTimeMillis())
                .put("payload", payload)
            cacheFile(context, key).writeText(wrapper.toString(), StandardCharsets.UTF_8)

            // 有界清理：超出上限时删除最旧的若干份
            val dir = File(context.filesDir, CACHE_DIR)
            val files = dir.listFiles()?.takeIf { it.size > CACHE_MAX_FILES } ?: return@runCatching
            files.sortedBy { it.lastModified() }
                .take(files.size - CACHE_MAX_FILES)
                .forEach { runCatching { it.delete() } }
        }
    }

    // ---------------------------------------------------------------- HTTP

    private fun fetchWithMirrors(path: String): String? {
        // 镜像顺序：jsDelivr 优先（国内可达性通常更好），raw 兜底
        for (base in listOf(CDN_BASE, RAW_BASE)) {
            fetch("$base/$path")?.let { return it }
            Log.w(TAG, "镜像不可达或非 200：$base/$path")
        }
        return null
    }

    private fun fetch(url: String): String? = runCatching {
        pace()
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("User-Agent", UA)
            setRequestProperty("Accept", "application/json,text/plain,*/*")
            // 内容仓库更新频率低，禁止中间层拿旧副本，保证改完仓库能较快生效
            setRequestProperty("Cache-Control", "no-cache")
        }
        val code = conn.responseCode
        if (code != HttpURLConnection.HTTP_OK) {
            conn.disconnect()
            return@runCatching null
        }
        BufferedReader(
            InputStreamReader(conn.inputStream, StandardCharsets.UTF_8),
        ).use { it.readText() }.also { conn.disconnect() }
    }.getOrNull()

    private fun pace() {
        val now = System.currentTimeMillis().toInt()
        val last = lastRequestAt.getAndSet(now)
        val wait = REQUEST_INTERVAL_MS - (now - last)
        if (wait > 0) Thread.sleep(wait)
    }

    private fun md5Hex(input: String): String =
        MessageDigest.getInstance("MD5").digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
