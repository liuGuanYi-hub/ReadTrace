package com.example.readtrace.community.repository

import android.content.Context
import com.example.readtrace.community.model.CommunityComment
import com.example.readtrace.community.model.CommunityExhibition
import com.example.readtrace.data.BookDatabaseHelper
import com.example.readtrace.model.Book
import com.example.readtrace.model.BookStatus
import com.example.readtrace.model.MediaType
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

object CommunityRepository {
    private val memoryExhibitions = mutableListOf<CommunityExhibition>()
    private val memoryComments = mutableMapOf<String, MutableList<CommunityComment>>()
    private var isInitialized = false

    // ===== 用户行为持久化（P38-G4）：点赞/留言/发布展厅落 SharedPreferences，进程重启不再丢失 =====
    private const val PREFS_NAME = "readtrace_community_user"
    private const val KEY_LIKED = "liked_exhibition_ids"
    private const val KEY_COMMENTS = "user_comments"
    private const val KEY_PUBLISHED = "published_exhibitions"
    private const val USER_EXHIBITION_PREFIX = "user-"

    private var appContext: Context? = null

    private fun userPrefs(context: Context? = null): android.content.SharedPreferences? =
        (appContext ?: context?.applicationContext?.also { appContext = it })
            ?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getExhibitions(category: String = "全部", query: String = ""): List<CommunityExhibition> {
        ensureSeedData()
        return memoryExhibitions.filter { exhibition ->
            val matchCategory = category == "全部" || exhibition.tags.contains(category) ||
                    exhibition.curatedBooks.any { it.category.equals(category, ignoreCase = true) }
            val matchQuery = query.isBlank() ||
                    exhibition.title.contains(query, ignoreCase = true) ||
                    exhibition.authorName.contains(query, ignoreCase = true) ||
                    exhibition.themeDescription.contains(query, ignoreCase = true) ||
                    exhibition.curatedBooks.any { it.title.contains(query, ignoreCase = true) || (it.author?.contains(query, ignoreCase = true) == true) }
            matchCategory && matchQuery
        }
    }

    fun getFeaturedExhibitions(): List<CommunityExhibition> {
        ensureSeedData()
        return memoryExhibitions.sortedByDescending { it.likeCount }.take(5)
    }

    fun getExhibitionById(id: String): CommunityExhibition? {
        ensureSeedData()
        return memoryExhibitions.firstOrNull { it.id == id }
    }

    fun toggleLike(id: String, context: Context? = null): Boolean {
        val exhibition = getExhibitionById(id) ?: return false
        if (exhibition.isLiked) {
            exhibition.likeCount = (exhibition.likeCount - 1).coerceAtLeast(0)
            exhibition.isLiked = false
        } else {
            exhibition.likeCount += 1
            exhibition.isLiked = true
        }
        persistLikes(context)
        return exhibition.isLiked
    }

    fun getComments(exhibitionId: String): List<CommunityComment> {
        ensureSeedData()
        return memoryComments[exhibitionId] ?: emptyList()
    }

    fun addComment(exhibitionId: String, userName: String, content: String, context: Context? = null): CommunityComment {
        val now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
        val comment = CommunityComment(
            id = UUID.randomUUID().toString(),
            exhibitionId = exhibitionId,
            userName = userName.ifBlank { "漫游读者" },
            userAvatar = listOf("🌿", "🌌", "🕯️", "🌊", "🌙", "🦉", "🎨").random(),
            content = content,
            createdAt = now,
        )
        memoryComments.getOrPut(exhibitionId) { mutableListOf() }.add(0, comment)
        // 同步递增展厅留言计数（此前恒为种子值，P38-G4 顺带修复）
        getExhibitionById(exhibitionId)?.let { it.commentCount += 1 }
        persistComments(context)
        return comment
    }

    fun publishExhibition(
        authorName: String,
        authorAvatar: String,
        title: String,
        description: String,
        books: List<Book>,
        tags: List<String>,
        featuredTheme: String,
        context: Context? = null,
    ): CommunityExhibition {
        val now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
        val exhibition = CommunityExhibition(
            id = USER_EXHIBITION_PREFIX + UUID.randomUUID().toString(),
            authorName = authorName.ifBlank { "深居漫步者" },
            authorAvatar = authorAvatar.ifBlank { "🦉" },
            title = title,
            themeDescription = description,
            curatedBooks = books,
            tags = tags,
            likeCount = 1,
            isLiked = true,
            commentCount = 0,
            createdAt = now,
            featuredTheme = featuredTheme,
        )
        memoryExhibitions.add(0, exhibition)
        persistPublished(context)
        return exhibition
    }

    /**
     * 将社区展厅中的书籍一键转存导入到本地 SQLite 数据库
     */
    fun saveBookToLocalShelf(context: Context, book: Book): Boolean {
        val helper = BookDatabaseHelper.getInstance(context)
        val copy = book.copy(
            id = 0,
            status = BookStatus.WISHLIST,
            coverUrl = null, // 转存到本地保留元数据
            createdAt = "",
            updatedAt = "",
        )
        return helper.insertBook(copy) > 0
    }

    private fun ensureSeedData() {
        if (isInitialized) return
        isInitialized = true

        val booksGroup1 = listOf(
            Book(id = 101, title = "小王子", author = "圣埃克苏佩里", category = "哲学", rating = 9.8, status = BookStatus.FINISHED, shortComment = "正因为你在玫瑰上耗费的时间，才使你的玫瑰变得如此重要。", review = "一部关于爱、驯服与生命责任的永恒诗篇。"),
            Book(id = 102, title = "月亮与六便士", author = "毛姆", category = "小说", rating = 9.2, status = BookStatus.FINISHED, shortComment = "满地都是六便士，他却抬头看见了月亮。", review = "对纯粹艺术狂热的最残酷也是最壮丽的白描。"),
            Book(id = 103, title = "局外人", author = "阿尔贝·加缪", category = "哲学", rating = 9.5, status = BookStatus.FINISHED, shortComment = "今天，妈妈死了。也许是昨天，我不知道。", review = "直面存在的荒谬，并在阳光与虚无中保持纯粹诚实。"),
        )

        val booksGroup2 = listOf(
            Book(id = 201, title = "1984", author = "乔治·奥威尔", category = "科幻", rating = 9.7, status = BookStatus.FINISHED, shortComment = "谁控制了过去，谁就控制了未来；谁控制了现在，谁就控制了过去。", review = "思想禁锢与权力极权的深刻警世预言。"),
            Book(id = 202, title = "动物农场", author = "乔治·奥威尔", category = "小说", rating = 9.6, status = BookStatus.FINISHED, shortComment = "所有动物生来平等，但有些动物比其他动物更平等。", review = "极其辛辣而凝练的寓言讽刺文学巅峰。"),
            Book(id = 203, title = "鼠疫", author = "阿尔贝·加缪", category = "哲学", rating = 9.3, status = BookStatus.FINISHED, shortComment = "对抗瘟疫的唯一方式，就是诚实。", review = "在不可抗拒的灾难中，普通人彼此扶持的坚韧与尊严。"),
        )

        val booksGroup3 = listOf(
            Book(id = 301, title = "白夜行", author = "东野圭吾", category = "悬疑", rating = 9.5, status = BookStatus.FINISHED, shortComment = "我的天空里没有太阳，总是黑夜，但并不暗，因为有东西代替了太阳。", review = "残酷而深沉的灵魂共生悲歌。"),
            Book(id = 302, title = "解忧杂货店", author = "东野圭吾", category = "治愈", rating = 9.1, status = BookStatus.FINISHED, shortComment = "如果把你的地图比作一张白纸，那么你才可以随心所欲地描绘任何壮丽的地图。", review = "跨越三十年的时空信箱，温暖人心的善良回响。"),
            Book(id = 303, title = "人间失格", author = "太宰治", category = "随笔", rating = 8.8, status = BookStatus.FINISHED, shortComment = "回首往事，我的一生充斥着可耻的记忆。在这个世间，唯一可以称得上真理的，是一切都会过去的。", review = "对脆弱心灵的极致解剖。"),
        )

        val booksGroup4 = listOf(
            Book(id = 401, title = "活着", author = "余华", category = "历史", rating = 9.6, status = BookStatus.FINISHED, shortComment = "人是为了活着本身而活着的，而不是为了活着之外的任何事物所活着。", review = "苦难中生生不息的中国民间生命力。"),
            Book(id = 402, title = "老人与海", author = "海明威", category = "小说", rating = 9.4, status = BookStatus.FINISHED, shortComment = "一个人并不是生来要给打败的，你尽可以消灭他，可就是打不败他。", review = "坚硬硬汉灵魂对大海与命运的壮烈搏击。"),
        )

        memoryExhibitions.add(
            CommunityExhibition(
                id = "ex-001",
                authorName = "林栖阁主",
                authorAvatar = "🌌",
                title = "荒谬与微光：存在主义思想展台",
                themeDescription = "精选加缪、圣埃克苏佩里与毛姆的代表作。在荒谬的世界中寻得内心的平静与热爱。",
                curatedBooks = booksGroup1,
                tags = listOf("哲学", "经典", "存在主义"),
                likeCount = 382,
                isLiked = false,
                commentCount = 28,
                createdAt = "2026-08-20 18:30",
                featuredTheme = "星空漫想",
            )
        )

        memoryExhibitions.add(
            CommunityExhibition(
                id = "ex-002",
                authorName = "墨白书童",
                authorAvatar = "🕯️",
                title = "警世寓言：反乌托邦与群体反思",
                themeDescription = "奥威尔与加缪笔下的极权预言与人道抗争，让人在沉思中保持清醒。",
                curatedBooks = booksGroup2,
                tags = listOf("科幻", "小说", "思辨"),
                likeCount = 296,
                isLiked = false,
                commentCount = 19,
                createdAt = "2026-08-21 14:15",
                featuredTheme = "暖木书房",
            )
        )

        memoryExhibitions.add(
            CommunityExhibition(
                id = "ex-003",
                authorName = "秋水寻光",
                authorAvatar = "🌿",
                title = "东野圭吾的白夜与暖阳",
                themeDescription = "从极致黑暗的《白夜行》到极致治愈的《解忧杂货店》，探索人性的两极。",
                curatedBooks = booksGroup3,
                tags = listOf("悬疑", "治愈", "小说"),
                likeCount = 451,
                isLiked = false,
                commentCount = 36,
                createdAt = "2026-08-22 09:40",
                featuredTheme = "禅意绿洲",
            )
        )

        memoryExhibitions.add(
            CommunityExhibition(
                id = "ex-004",
                authorName = "沧海一粟",
                authorAvatar = "🌊",
                title = "韧性与生生不息：大地上的搏击",
                themeDescription = "从富贵到圣地亚哥，看普通生命如何在命运巨浪中活出不屈尊严。",
                curatedBooks = booksGroup4,
                tags = listOf("历史", "经典", "小说"),
                likeCount = 217,
                isLiked = false,
                commentCount = 15,
                createdAt = "2026-08-23 10:20",
                featuredTheme = "星空漫想",
            )
        )

        memoryComments["ex-001"] = mutableListOf(
            CommunityComment("c-1", "ex-001", "风语者", "🌿", "3D 视角看这个展厅太有质感了！已转存《局外人》到我的书架。", "2026-08-20 19:10"),
            CommunityComment("c-2", "ex-001", "素年锦时", "☕", "加缪和圣埃克苏佩里的组合深得我心，支持阁主！", "2026-08-21 08:30"),
        )
        memoryComments["ex-003"] = mutableListOf(
            CommunityComment("c-3", "ex-003", "夜行船", "🌙", "白夜行的短评写得太戳心了，“我的天空里没有太阳，但并不暗”。", "2026-08-22 11:05"),
        )

        restoreUserData()
    }

    // ===== 持久化实现 =====

    private fun restoreUserData() {
        val prefs = userPrefs(null) ?: return
        // 1. 点赞状态回放
        val liked = prefs.getStringSet(KEY_LIKED, emptySet()) ?: emptySet()
        if (liked.isNotEmpty()) {
            memoryExhibitions.forEach { ex ->
                if (ex.id in liked && !ex.isLiked) {
                    ex.isLiked = true
                    ex.likeCount += 1
                }
            }
        }
        // 2. 用户发布的展厅回放（user- 前缀）
        prefs.getString(KEY_PUBLISHED, null)?.let { json ->
            runCatching {
                val arr = JSONArray(json)
                for (i in 0 until arr.length()) {
                    runCatching { memoryExhibitions.add(0, parseExhibition(arr.getJSONObject(i))) }
                }
            }
        }
        // 3. 用户留言回放
        prefs.getString(KEY_COMMENTS, null)?.let { json ->
            runCatching {
                val arr = JSONArray(json)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    memoryComments.getOrPut(obj.optString("exhibitionId")) { mutableListOf() }.add(
                        CommunityComment(
                            id = obj.optString("id"),
                            exhibitionId = obj.optString("exhibitionId"),
                            userName = obj.optString("userName"),
                            userAvatar = obj.optString("userAvatar"),
                            content = obj.optString("content"),
                            createdAt = obj.optString("createdAt"),
                        ),
                    )
                }
            }
        }
    }

    private fun persistLikes(context: Context? = null) {
        val prefs = userPrefs(context) ?: return
        prefs.edit().putStringSet(KEY_LIKED, memoryExhibitions.filter { it.isLiked }.map { it.id }.toSet()).apply()
    }

    private fun persistComments(context: Context? = null) {
        val prefs = userPrefs(context) ?: return
        val arr = JSONArray()
        memoryComments.values.forEach { list -> list.forEach { c -> arr.put(commentToJson(c)) } }
        prefs.edit().putString(KEY_COMMENTS, arr.toString()).apply()
    }

    private fun persistPublished(context: Context? = null) {
        val prefs = userPrefs(context) ?: return
        val arr = JSONArray()
        memoryExhibitions.filter { it.id.startsWith(USER_EXHIBITION_PREFIX) }.forEach { arr.put(exhibitionToJson(it)) }
        prefs.edit().putString(KEY_PUBLISHED, arr.toString()).apply()
    }

    private fun commentToJson(c: CommunityComment) = JSONObject().apply {
        put("id", c.id)
        put("exhibitionId", c.exhibitionId)
        put("userName", c.userName)
        put("userAvatar", c.userAvatar)
        put("content", c.content)
        put("createdAt", c.createdAt)
    }

    private fun exhibitionToJson(e: CommunityExhibition) = JSONObject().apply {
        put("id", e.id)
        put("authorName", e.authorName)
        put("authorAvatar", e.authorAvatar)
        put("title", e.title)
        put("themeDescription", e.themeDescription)
        put("tags", JSONArray(e.tags))
        put("likeCount", e.likeCount)
        put("isLiked", e.isLiked)
        put("commentCount", e.commentCount)
        put("createdAt", e.createdAt)
        put("featuredTheme", e.featuredTheme)
        val booksArr = JSONArray()
        e.curatedBooks.forEach { b ->
            booksArr.put(JSONObject().apply {
                put("id", b.id)
                put("title", b.title)
                put("author", b.author.orEmpty())
                put("category", b.category.orEmpty())
                if (b.rating != null) put("rating", b.rating)
                put("status", b.status.databaseValue)
                put("mediaType", b.mediaType.databaseValue)
                put("shortComment", b.shortComment.orEmpty())
                put("review", b.review.orEmpty())
            })
        }
        put("curatedBooks", booksArr)
    }

    private fun parseExhibition(obj: JSONObject): CommunityExhibition {
        val books = mutableListOf<Book>()
        obj.optJSONArray("curatedBooks")?.let { arr ->
            for (i in 0 until arr.length()) {
                val b = arr.getJSONObject(i)
                runCatching {
                    books.add(
                        Book(
                            id = b.optLong("id"),
                            title = b.optString("title"),
                            author = b.optString("author").takeIf { it.isNotEmpty() },
                            category = b.optString("category").takeIf { it.isNotEmpty() },
                            rating = if (b.has("rating") && !b.isNull("rating")) b.getDouble("rating") else null,
                            status = BookStatus.fromDatabaseValue(b.optString("status")),
                            mediaType = MediaType.fromDatabaseValue(b.optString("mediaType")),
                            shortComment = b.optString("shortComment").takeIf { it.isNotEmpty() },
                            review = b.optString("review").takeIf { it.isNotEmpty() },
                        ),
                    )
                }
            }
        }
        val tags = mutableListOf<String>()
        obj.optJSONArray("tags")?.let { arr ->
            for (i in 0 until arr.length()) tags.add(arr.optString(i))
        }
        return CommunityExhibition(
            id = obj.optString("id"),
            authorName = obj.optString("authorName"),
            authorAvatar = obj.optString("authorAvatar"),
            title = obj.optString("title"),
            themeDescription = obj.optString("themeDescription"),
            curatedBooks = books,
            tags = tags,
            likeCount = obj.optInt("likeCount", 1),
            isLiked = obj.optBoolean("isLiked", true),
            commentCount = obj.optInt("commentCount", 0),
            createdAt = obj.optString("createdAt"),
            featuredTheme = obj.optString("featuredTheme"),
        )
    }
}
