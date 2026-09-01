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
 * Steam 热门单机游戏客户端（v1.0.4 换源：SteamSpy 被 Cloudflare 反爬后改杉果商城）。
 *
 * 背景：SteamSpy（steamspy.com）2026 年起对 API 返回 403（Cloudflare 人机挑战），
 * 且旧缓存曾被污染（"Steam Game N" 占位名）。为让「发现 → 游戏」榜单展示玩家熟悉的
 * Steam 单机大作，改从国内正版游戏商城「杉果（sonkwo.hk）」首页抓取——
 * 其首页轮播即热门单机（黑神话/FF16/人中之龙系列/生化危机/文明VI 等），
 * 国内可达、带中文标题/封面/简介，无需 key。
 *
 * - 榜单：GET https://www.sonkwo.hk/ → 解析内嵌 game_sku JSON（title + itemable_id + 简介）+ 轮播卡片封面
 * - 搜索：在已抓榜单内按名称本地过滤（覆盖首页热门，搜索冷门词走 Bangumi 补位）
 * - 封面：https://s8.sonkwo.com/...（国内 CDN）
 * - id：杉果 itemable_id（Steam 平台游戏，跨源防重走 source=steam + id）
 *
 * 覆盖：GAME。合规：公开网页，低频，24h 缓存。
 */
object SteamClient {

    /** 条目来源标识：落库 sourceType 与跨源防重用 */
    const val SOURCE_STEAM = "steam"

    private const val RANK_URL = "https://www.sonkwo.hk/"
    private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 ReadTrace/1.0.4"
    private const val CONNECT_TIMEOUT_MS = 8000
    private const val READ_TIMEOUT_MS = 15000
    private const val CACHE_DIR = "steam_cache"
    private const val CACHE_TTL_MS = 24L * 60 * 60 * 1000
    private const val CACHE_MAX_FILES = 20

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val lastRequestAt = AtomicInteger(0)

    /**
     * v1.0.4 榜单整表同步版（杉果首页热门单机），必须在后台线程调用，
     * 由 RankRepository 统一编排切片分页与搜索过滤。
     * 返回整表；null = 网络失败且无缓存兜底。
     */
    fun fetchRankListSync(context: Context, forceRefresh: Boolean): List<BangumiSubject>? {
        val appContext = context.applicationContext
        val rankKey = cacheKeyOf("")
        if (!forceRefresh) {
            readCache(appContext, rankKey)?.let { list -> return list }
        }
        val html = fetch(RANK_URL)
        val rank = html?.let { parseRankHtml(it) }
        if (rank != null && rank.isNotEmpty()) writeCache(appContext, rankKey, rank)
        if (rank == null) {
            readCache(appContext, rankKey)?.let { list -> return list }
            return null
        }
        return rank
    }

    /** 详情：杉果榜单已含中文简介，直接返回原条目（无额外网络请求） */
    fun getSubjectDetail(subject: BangumiSubject, onResult: (BangumiSubject?) -> Unit) {
        executor.execute {
            mainHandler.post { onResult(subject) }
        }
    }

    /**
     * 解析杉果首页 HTML 中的热门单机游戏。
     *
     * 页面内嵌 JSON（`"game_sku":[{...}]`）提供：中文标题、itemable_id、中文简介；
     * 轮播卡片提供封面（s8.sonkwo.com 国内 CDN）。两种结构各自解析后按标题去重合并。
     */
    private fun parseRankHtml(html: String): List<BangumiSubject>? = runCatching {
        val out = mutableListOf<BangumiSubject>()
        val seenTitles = mutableSetOf<String>()

        // 结构一：内嵌 JSON game_sku 块（title + itemable_id + recommend_text.default 简介）
        val skuPattern = Regex(""""game_sku"\s*:\s*\[(.*?)\]""", RegexOption.DOT_MATCHES_ALL)
        for (m in skuPattern.findAll(html)) {
            val block = m.groupValues[1]
            val titles = Regex(""""title"\s*:\s*"([^"]{2,60})"""").findAll(block)
                .map { it.groupValues[1] }.toList()
            val ids = Regex(""""itemable_id"\s*:\s*"(\d+)"""").findAll(block)
                .map { it.groupValues[1] }.toList()
            val summaries = Regex(""""default"\s*:\s*"([^"]{0,300})"""").findAll(block)
                .map { it.groupValues[1] }.toList()
            for (i in titles.indices) {
                val name = titles[i].trim()
                if (name.isEmpty() || !seenTitles.add(name)) continue
                val appId = ids.getOrNull(i)?.toLongOrNull() ?: continue
                out += BangumiSubject(
                    id = appId,
                    name = name,
                    nameCn = name,
                    coverUrl = null, // 封面从结构二补充
                    ratingScore = null,
                    creator = null,
                    summary = summaries.getOrNull(i)?.takeIf { it.isNotBlank() },
                    subjectType = 4,
                    source = SOURCE_STEAM,
                )
            }
        }

        // 结构二：轮播卡片 <div class="img-wrap"><img src=封面 /></div><div class="game-name">标题</div>
        val cardPattern = Regex(
            """<div class="img-wrap"><img src="([^"]+)"[^>]*></div><div class="game-name">([^<]{2,60})</div>""",
        )
        for (m in cardPattern.findAll(html)) {
            val cover = m.groupValues[1].trim()
            val name = m.groupValues[2].trim()
            if (name.isEmpty()) continue
            // 已从 JSON 结构收录过的标题补封面；新标题追加
            val existing = out.firstOrNull { it.name == name }
            if (existing != null) {
                if (existing.coverUrl.isNullOrBlank()) {
                    val idx = out.indexOf(existing)
                    out[idx] = existing.copy(coverUrl = cover)
                }
                continue
            }
            if (!seenTitles.add(name)) continue
            out += BangumiSubject(
                id = (out.size + 1).toLong(), // 轮播卡片无 itemable_id，用占位自增 id
                name = name,
                nameCn = name,
                coverUrl = cover,
                ratingScore = null,
                creator = null,
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
                val name = o.optString("name")
                // 防御脏缓存：旧版可能落盘了占位名条目（"Steam Game N"），读取时一并过滤
                if (name.isBlank() || name.startsWith("Steam Game", ignoreCase = true)) continue
                add(
                    BangumiSubject(
                        id = o.optLong("id"),
                        name = name,
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