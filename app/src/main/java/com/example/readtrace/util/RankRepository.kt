package com.example.readtrace.util

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.readtrace.model.BangumiSubject
import com.example.readtrace.model.MediaType
import java.util.concurrent.Executors

/** 数据源单页抓取结果：items 为空表示该页合法为空或解析失败（网络彻底失败时调用方收到 null） */
data class SourcePage(
    val items: List<BangumiSubject>,
    val fromCache: Boolean,
)

/**
 * 榜单/搜索分页编排器（v4.2.23 外部导入扩量改造）。
 *
 * 职责：把页轴互不相同的四个数据源（豆瓣按页、Bangumi 按 offset、
 * Steam/网易云一次性整表）统一成「一页一页喂给 UI」的分页会话，并负责：
 * - 混合双源降级：主源页失败/结果不足 → Bangumi 官方 API 补位（按标题跨源去重）；
 * - 总量封顶 200 条、页间限流（各客户端自带串行节流）；
 * - session 令牌隔离：切分类/搜索/下拉刷新开新会话，杜绝旧请求串扰。
 *
 * 线程模型：自有单线程执行器串行执行页抓取，回调统一切主线程。
 */
object RankRepository {

    /** 单页结果：items 为本页新增条目（已全局去重） */
    data class RankPage(
        val items: List<BangumiSubject>,
        val hasMore: Boolean,
        val fromCache: Boolean,
        val sourceNote: String,
    )

    private const val TAG = "RankRepository"

    /** 全会话总量上限：冷启动 200 部起步 */
    private const val MAX_TOTAL = 200

    /** 单页目标条数（豆瓣书页为 25，其余 20，不强制对齐） */
    private const val PAGE_SIZE = 20

    /** 主源页结果低于该阈值即触发 Bangumi 补位 */
    private const val PAGE_FILL_MIN = 10

    /** 单个服务页最多消耗的 Bangumi 补位页数（限速保护：30 req/min） */
    private const val MAX_FILL_PAGES = 2

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    /** 一次「分类 + 关键词」浏览的有状态会话：游标、去重集、耗尽标记都在这里 */
    private class Session(
        val mediaType: MediaType,
        val keyword: String,
        val servedTitles: MutableSet<String> = mutableSetOf(),
        var servedCount: Int = 0,
        // 豆瓣主源游标（BOOK / MOVIE / ANIME）
        var doubanCursor: Int = 0,
        var doubanEmptyStreak: Int = 0,
        var doubanExhausted: Boolean = false,
        // Bangumi 补位游标
        var bangumiOffset: Int = 0,
        var bangumiExhausted: Boolean = false,
        // 游戏/音乐主源整表（一次性抓取后本地切片）
        var primaryList: List<BangumiSubject>? = null,
        var primaryIndex: Int = 0,
        var primaryFailed: Boolean = false,
    )

    private val sessions = HashMap<Long, Session>()
    private var nextSessionId = 1L

    /** 开启新会话，返回会话令牌；Activity 每次重置（切分类/搜索/刷新）都应开新会话 */
    @Synchronized
    fun startSession(mediaType: MediaType, keyword: String): Long {
        val token = nextSessionId++
        sessions[token] = Session(mediaType, keyword.trim())
        return token
    }

    /** 会话不再使用时释放（防泄漏） */
    @Synchronized
    fun discardSession(token: Long) {
        sessions.remove(token)
    }

    @Synchronized
    private fun sessionOf(token: Long): Session? = sessions[token]

    /** 加载会话的下一页；会话不存在时回调 null */
    fun loadPage(context: Context, token: Long, forceRefresh: Boolean, onResult: (RankPage?) -> Unit) {
        val appContext = context.applicationContext
        executor.execute {
            val session = sessionOf(token)
            if (session == null) {
                mainHandler.post { onResult(null) }
                return@execute
            }
            val page = buildPage(appContext, session, forceRefresh)
            mainHandler.post { onResult(page) }
        }
    }

    // ---------------------------------------------------------------- 页构建

    private fun buildPage(ctx: Context, session: Session, forceRefresh: Boolean): RankPage {
        if (session.servedCount >= MAX_TOTAL) {
            return RankPage(emptyList(), hasMore = false, fromCache = false, sourceNote = "已达 ${MAX_TOTAL} 部上限")
        }
        return if (session.keyword.isEmpty()) {
            buildRankPage(ctx, session, forceRefresh)
        } else {
            buildSearchPage(ctx, session, forceRefresh)
        }
    }

    /** 榜单模式：主源逐页 + Bangumi 页级补位 */
    private fun buildRankPage(ctx: Context, session: Session, forceRefresh: Boolean): RankPage {
        val items = mutableListOf<BangumiSubject>()
        val sources = mutableListOf<String>()
        var fromCache = false

        when (session.mediaType) {
            MediaType.BOOK, MediaType.MOVIE, MediaType.ANIME -> {
                val maxPages = DoubanClient.maxRankPages(session.mediaType)
                if (!session.doubanExhausted && session.doubanCursor < maxPages) {
                    val page = DoubanClient.fetchRankPageSync(ctx, session.mediaType, session.doubanCursor, forceRefresh)
                    when {
                        page == null -> {
                            Log.w(TAG, "douban page ${session.doubanCursor} failed, bangumi fills in")
                            fillFromBangumi(ctx, session, items)?.let(sources::add)
                        }
                        page.items.isEmpty() -> {
                            session.doubanEmptyStreak++
                            if (session.doubanEmptyStreak >= 2) session.doubanExhausted = true
                            fillFromBangumi(ctx, session, items)?.let(sources::add)
                        }
                        else -> {
                            session.doubanEmptyStreak = 0
                            session.doubanCursor++
                            items += takeNew(session, page.items, PAGE_SIZE)
                            sources += "豆瓣"
                            fromCache = page.fromCache
                            if (items.size < PAGE_FILL_MIN) {
                                fillFromBangumi(ctx, session, items)?.let {
                                    sources += it
                                    fromCache = false
                                }
                            }
                        }
                    }
                } else {
                    session.doubanExhausted = true
                    fillFromBangumi(ctx, session, items)?.let(sources::add)
                }
            }
            MediaType.GAME, MediaType.MUSIC -> {
                val list = ensurePrimaryList(ctx, session, forceRefresh)
                if (list != null) {
                    val slice = list.drop(session.primaryIndex).take(PAGE_SIZE)
                    session.primaryIndex += slice.size
                    items += takeNew(session, slice, PAGE_SIZE)
                    sources += primarySourceName(session.mediaType)
                }
                val primaryDone = list == null || session.primaryIndex >= list.size
                if (items.size < PAGE_FILL_MIN || primaryDone) {
                    fillFromBangumi(ctx, session, items)?.let(sources::add)
                }
            }
        }

        session.servedCount += items.size
        val hasMore = items.isNotEmpty() && session.servedCount < MAX_TOTAL && !isExhausted(session)
        val note = buildNote(sources, fromCache && items.isNotEmpty())
        return RankPage(items, hasMore, fromCache, note)
    }

    /** 搜索模式：主源单页，结果不足时 Bangumi match 补一次，此后无更多 */
    private fun buildSearchPage(ctx: Context, session: Session, forceRefresh: Boolean): RankPage {
        val items = mutableListOf<BangumiSubject>()
        val sources = mutableListOf<String>()
        var fromCache = false

        when (session.mediaType) {
            MediaType.GAME -> {
                val list = ensurePrimaryList(ctx, session, forceRefresh)
                if (list != null) {
                    items += takeNew(session, list.filter { matchesKeyword(it, session.keyword) }, MAX_TOTAL)
                    sources += primarySourceName(session.mediaType)
                }
            }
            MediaType.MUSIC -> {
                val page = NeteaseClient.fetchSearchSync(ctx, session.keyword, forceRefresh)
                if (page != null) {
                    items += takeNew(session, page.items, MAX_TOTAL)
                    sources += primarySourceName(session.mediaType)
                    fromCache = page.fromCache
                }
            }
            else -> {
                val page = DoubanClient.fetchSearchSync(ctx, session.keyword, session.mediaType, forceRefresh)
                if (page != null) {
                    items += takeNew(session, page.items, MAX_TOTAL)
                    sources += "豆瓣"
                    fromCache = page.fromCache
                }
            }
        }

        // 结果太少时用 Bangumi sort=match 补一页（书籍/番剧/影视/音乐；游戏靠 Steam 本地过滤）
        if (items.size < PAGE_FILL_MIN && session.mediaType != MediaType.GAME) {
            val result = BangumiApiClient.fetchRankPageSync(ctx, session.mediaType, session.keyword, 0, forceRefresh)
            result?.first?.let { bangumiItems ->
                val filled = takeNew(session, bangumiItems, MAX_TOTAL - items.size)
                if (filled.isNotEmpty()) {
                    items += filled
                    sources += "Bangumi 补位"
                    fromCache = false
                }
            }
        }

        session.servedCount += items.size
        val note = buildNote(sources, fromCache && items.isNotEmpty())
        return RankPage(items, hasMore = false, fromCache, note)
    }

    // ---------------------------------------------------------------- 补位与去重

    /** 用 Bangumi 补位直到本页凑满或 Bangumi 耗尽；返回来源标签（无补位返回 null） */
    private fun fillFromBangumi(
        ctx: Context,
        session: Session,
        items: MutableList<BangumiSubject>,
    ): String? {
        var filled = false
        var guard = 0
        while (items.size < PAGE_SIZE && !session.bangumiExhausted && guard < MAX_FILL_PAGES) {
            guard++
            val result = BangumiApiClient.fetchRankPageSync(
                ctx, session.mediaType, session.keyword, session.bangumiOffset, forceRefresh = false,
            )
            if (result == null) break // 网络失败：不标记耗尽，下一页再试
            val (bangumiItems, _) = result
            session.bangumiOffset += 20
            items += takeNew(session, bangumiItems, PAGE_SIZE - items.size)
            filled = true
            if (bangumiItems.size < 20) session.bangumiExhausted = true
        }
        return if (filled) "Bangumi 补位" else null
    }

    /** 按标题全局去重并登记进会话，返回本页可服务的新条目 */
    private fun takeNew(session: Session, candidates: List<BangumiSubject>, remaining: Int): List<BangumiSubject> {
        if (remaining <= 0) return emptyList()
        val out = mutableListOf<BangumiSubject>()
        for (subject in candidates) {
            if (out.size >= remaining) break
            val key = titleKeyOf(subject)
            if (key.isEmpty() || !session.servedTitles.add(key)) continue
            out += subject
        }
        return out
    }

    private fun titleKeyOf(subject: BangumiSubject): String =
        subject.displayTitle.replace("\\s+".toRegex(), "").lowercase()

    private fun matchesKeyword(subject: BangumiSubject, keyword: String): Boolean =
        subject.name.contains(keyword, ignoreCase = true) ||
            (subject.nameCn?.contains(keyword, ignoreCase = true) == true) ||
            (subject.creator?.contains(keyword, ignoreCase = true) == true)

    // ---------------------------------------------------------------- 会话状态

    private fun ensurePrimaryList(ctx: Context, session: Session, forceRefresh: Boolean): List<BangumiSubject>? {
        if (session.primaryFailed) return null
        session.primaryList?.let { return it }
        val list = when (session.mediaType) {
            MediaType.GAME -> SteamClient.fetchRankListSync(ctx, forceRefresh)
            MediaType.MUSIC -> NeteaseClient.fetchRankListSync(ctx, forceRefresh)
            else -> null
        }
        if (list == null || list.isEmpty()) {
            session.primaryFailed = true
            Log.w(TAG, "primary list failed for ${session.mediaType}, bangumi will take over")
            return null
        }
        session.primaryList = list
        return list
    }

    private fun primarySourceName(mediaType: MediaType): String = when (mediaType) {
        MediaType.GAME -> "Steam 热门"
        MediaType.MUSIC -> "网易云榜单"
        else -> "主源"
    }

    private fun isExhausted(session: Session): Boolean = when (session.mediaType) {
        MediaType.BOOK, MediaType.MOVIE, MediaType.ANIME ->
            session.doubanExhausted && session.bangumiExhausted
        MediaType.GAME, MediaType.MUSIC -> {
            val listDone = session.primaryFailed ||
                (session.primaryList != null && session.primaryIndex >= session.primaryList!!.size)
            listDone && session.bangumiExhausted
        }
    }

    private fun buildNote(sources: List<String>, fromCache: Boolean): String {
        val base = sources.distinct().joinToString(" · ").ifEmpty { "暂无数据源" }
        return if (fromCache) "$base · 缓存" else base
    }
}
