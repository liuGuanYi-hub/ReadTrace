package com.example.readtrace.util

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.readtrace.model.BangumiSubject
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Steam 热门游戏客户端（v4.2.21 多源改造）。
 *
 * 背景：小黑盒（www/api/game/h5/web.xiaoheihe.cn）实测全部 404/403 不可达，
 * 按实测改用 SteamSpy（steamspy.com）公开 JSON 接口——国内直连可用（实测 200），
 * 返回全球近两周热门游戏（约 100 款），结构化数据，无需 key。
 *
 * - 榜单：GET /api.php?request=top100in2weeks → {appid: {name, developer, owners}}
 * - 搜索：SteamSpy search 接口不稳定 → 在已抓取榜单内按名称本地过滤（覆盖 100 款热门）
 * - 封面：https://cdn.cloudflare.steamstatic.com/steam/apps/{appid}/library_600x900.jpg（实测 200）
 *
 * 覆盖：GAME。合规：公开接口，低频，24h 缓存。
 */
object SteamClient {

    /** 条目来源标识：落库 sourceType 与跨源防重用 */
    const val SOURCE_STEAM = "steam"

    private const val API = "https://steamspy.com/api.php"
    private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 ReadTrace/4.2.21"
    private const val CONNECT_TIMEOUT_MS = 8000
    private const val READ_TIMEOUT_MS = 15000
    private const val CACHE_DIR = "steam_cache"
    private const val CACHE_TTL_MS = 24L * 60 * 60 * 1000
    private const val CACHE_MAX_FILES = 20

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val lastRequestAt = AtomicInteger(0)

    /**
     * v4.2.23 榜单整表同步版（top100in2weeks 约 100 款），必须在后台线程调用，
     * 由 RankRepository 统一编排切片分页与搜索过滤。
     * 返回整表；null = 网络失败且无缓存兜底。
     */
    fun fetchRankListSync(context: Context, forceRefresh: Boolean): List<BangumiSubject>? {
        val appContext = context.applicationContext
        val rankKey = cacheKeyOf("")
        if (!forceRefresh) {
            readCache(appContext, rankKey)?.let { list -> return list }
        }
        val json = fetch("$API?request=top100in2weeks")
        val rank = json?.let { parseRank(it) }
        if (rank != null && rank.isNotEmpty()) writeCache(appContext, rankKey, rank)
        if (rank == null) {
            readCache(appContext, rankKey)?.let { list -> return list }
            return null
        }
        return rank
    }

    /** 详情：SteamSpy appdetails 补简介/开发者 */
    fun getSubjectDetail(subject: BangumiSubject, onResult: (BangumiSubject?) -> Unit) {
        executor.execute {
            val result = runCatching {
                val json = fetch("$API?request=appdetails&appid=${subject.id}") ?: return@runCatching subject
                val o = JSONObject(json)
                val desc = o.optString("short_description").takeIf { it.isNotBlank() }
                val dev = o.optString("developer").takeIf { it.isNotBlank() }
                subject.copy(
                    creator = dev ?: subject.creator,
                    summary = desc,
                )
            }.getOrElse { subject }
            mainHandler.post { onResult(result) }
        }
    }

    private fun parseRank(json: String): List<BangumiSubject>? = runCatching {
        val root = JSONObject(json)
        val out = mutableListOf<BangumiSubject>()
        val keys = root.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val o = root.optJSONObject(key) ?: continue
            val appId = key.toLongOrNull() ?: continue
            val name = o.optString("name").trim()
            if (name.isEmpty()) continue
            out += BangumiSubject(
                id = appId,
                name = name,
                nameCn = name,
                coverUrl = "https://cdn.cloudflare.steamstatic.com/steam/apps/$appId/library_600x900.jpg",
                ratingScore = null,
                creator = o.optString("developer").takeIf { it.isNotBlank() },
                summary = null,
                subjectType = 4,
                source = SOURCE_STEAM,
            )
        }
        out.takeIf { it.isNotEmpty() }
    }.getOrNull()

    // ---------------------------------------------------------------- 缓存

    private fun cacheKeyOf(keyword: String): String {
        val normalized = "steam:rank|${keyword.lowercase()}"
        return md5Hex(normalized)
    }

    private fun readCache(context: Context, key: String): List<BangumiSubject>? = runCatching {
        val file = File(File(context.filesDir, CACHE_DIR).apply { if (!exists()) mkdirs() }, "$key.json")
        if (!file.exists() || file.length() == 0L) return@runCatching null
        val root = JSONObject(file.readText(StandardCharsets.UTF_8))
        if (System.currentTimeMillis() - root.optLong("ts", 0L) > CACHE_TTL_MS) return@runCatching null
        val arr = root.optJSONArray("data") ?: return@runCatching null
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                add(
                    BangumiSubject(
                        id = o.optLong("id"),
                        name = o.optString("name"),
                        nameCn = o.optString("nameCn").takeIf { it.isNotBlank() },
                        coverUrl = o.optString("cover").takeIf { it.isNotBlank() },
                        creator = o.optString("creator").takeIf { it.isNotBlank() },
                        summary = o.optString("summary").takeIf { it.isNotBlank() },
                        source = SOURCE_STEAM,
                    ),
                )
            }
        }.takeIf { it.isNotEmpty() }
    }.getOrNull()

    private fun writeCache(context: Context, key: String, subjects: List<BangumiSubject>) {
        runCatching {
            val dir = File(context.filesDir, CACHE_DIR).apply { if (!exists()) mkdirs() }
            val arr = org.json.JSONArray()
            subjects.forEach { s ->
                arr.put(
                    JSONObject().apply {
                        put("id", s.id)
                        put("name", s.name)
                        put("nameCn", s.nameCn ?: "")
                        put("cover", s.coverUrl ?: "")
                        put("creator", s.creator ?: "")
                        put("summary", s.summary ?: "")
                    },
                )
            }
            val payload = JSONObject().put("ts", System.currentTimeMillis()).put("data", arr)
            File(dir, "$key.json").writeText(payload.toString(), StandardCharsets.UTF_8)
            val files = dir.listFiles()?.takeIf { it.size > CACHE_MAX_FILES } ?: return@runCatching
            files.sortedBy { it.lastModified() }
                .take(files.size - CACHE_MAX_FILES)
                .forEach { runCatching { it.delete() } }
        }
    }

    // ---------------------------------------------------------------- HTTP

    private fun fetch(url: String): String? = runCatching {
        val now = System.currentTimeMillis().toInt()
        val last = lastRequestAt.getAndSet(now)
        val wait = 300L - (now - last)
        if (wait > 0) Thread.sleep(wait)
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("User-Agent", UA)
            setRequestProperty("Accept", "application/json")
        }
        val code = conn.responseCode
        if (code != HttpURLConnection.HTTP_OK) {
            Log.w("SteamClient", "HTTP $code for $url")
            conn.disconnect()
            return@runCatching null
        }
        BufferedReader(
            InputStreamReader(conn.inputStream, StandardCharsets.UTF_8),
        ).use { it.readText() }.also { conn.disconnect() }
    }.getOrNull()

    private fun md5Hex(input: String): String =
        MessageDigest.getInstance("MD5").digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
}