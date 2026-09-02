package com.example.readtrace.util

import android.app.Activity
import android.app.Dialog
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Window
import android.widget.TextView
import android.widget.Toast
import com.example.readtrace.R
import com.example.readtrace.data.BookDatabaseHelper
import com.example.readtrace.model.BangumiSubject
import com.example.readtrace.model.Book
import com.example.readtrace.model.MediaType
import com.example.readtrace.ui.QuickLogBottomSheet

/**
 * 📋 智能剪贴板感知与极光收录胶囊 (ClipboardSnifferHelper)
 *
 * 用户在浏览器、豆瓣、微信、小红书复制书名或作品链接后切回《阅痕》：
 * 底部自动滑入浮动毛玻璃极光胶囊「发现剪贴板作品《X》，一键收录 ➔」；
 * - 纯标题（含《》包裹）→ 预填进极速速记弹窗联想收录；
 * - 豆瓣链接 → 提取 subject id，0 误差直取官方元数据直接落库；
 * - Bangumi / Steam 链接 → 引导至多源搬家中心。
 */
object ClipboardSnifferHelper {

    /** 剪贴板嗅探结果 */
    data class ClipboardSniff(
        val kind: Kind,
        val title: String?,
        val subjectId: Long?,
        val mediaType: MediaType? = null,
    )

    enum class Kind { TITLE, DOUBAN_URL, BANGUMI_URL, STEAM_URL }

    private val DOUBAN_MOVIE = Regex("""movie\.douban\.com/subject/(\d+)""")
    private val DOUBAN_MUSIC = Regex("""music\.douban\.com/subject/(\d+)""")
    private val DOUBAN_BOOK = Regex("""book\.douban\.com/subject/(\d+)""")
    private val DOUBAN_GENERAL = Regex("""(?:www\.)?douban\.com/subject/(\d+)""")
    private val DOUBAN_ISBN = Regex("""(?:book\.)?douban\.com/isbn/(\d+)""")
    private val BANGUMI_SUBJECT = Regex("""(?:bgm\.tv|bangumi\.tv|chii\.in)/subject/(\d+)""")
    private val STEAM_APP = Regex("""store\.steampowered\.com/app/(\d+)""")
    private val BOOK_TITLE_MARK = Regex("""《([^《》]{1,60})》""")

    /** 解析剪贴板文本，按 host 自动识别媒介类别 */
    fun detect(rawContent: String?): ClipboardSniff? {
        val content = rawContent?.trim().orEmpty()
        if (content.isEmpty() || content.length > 300) return null

        DOUBAN_MOVIE.find(content)?.let {
            return ClipboardSniff(Kind.DOUBAN_URL, null, it.groupValues[1].toLongOrNull(), MediaType.MOVIE)
        }
        DOUBAN_MUSIC.find(content)?.let {
            return ClipboardSniff(Kind.DOUBAN_URL, null, it.groupValues[1].toLongOrNull(), MediaType.MUSIC)
        }
        DOUBAN_BOOK.find(content)?.let {
            return ClipboardSniff(Kind.DOUBAN_URL, null, it.groupValues[1].toLongOrNull(), MediaType.BOOK)
        }
        DOUBAN_GENERAL.find(content)?.let {
            return ClipboardSniff(Kind.DOUBAN_URL, null, it.groupValues[1].toLongOrNull(), MediaType.BOOK)
        }
        DOUBAN_ISBN.find(content)?.let {
            return ClipboardSniff(Kind.DOUBAN_URL, null, it.groupValues[1].toLongOrNull(), MediaType.BOOK)
        }
        BANGUMI_SUBJECT.find(content)?.let {
            return ClipboardSniff(Kind.BANGUMI_URL, null, it.groupValues[1].toLongOrNull(), MediaType.ANIME)
        }
        STEAM_APP.find(content)?.let {
            return ClipboardSniff(Kind.STEAM_URL, null, it.groupValues[1].toLongOrNull(), MediaType.GAME)
        }
        if (content.contains("http")) return null

        // 《书名》优先；否则 2~30 字的普通短文本视为候选标题
        BOOK_TITLE_MARK.find(content)?.let {
            return ClipboardSniff(Kind.TITLE, it.groupValues[1].trim(), null)
        }
        if (content.length in 2..30 && !content.contains("\n")) {
            return ClipboardSniff(Kind.TITLE, content, null)
        }
        return null
    }

    /** 读取当前剪贴板原文 */
    fun readClipboard(context: Context): String? = runCatching {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
    }.getOrNull()

    /**
     * 在 Activity onResume 时调用：嗅探剪贴板，命中且与上次不同则弹出收录胶囊
     * @param lastSeenRaw 上次已处理/已忽略的剪贴板原文（调用方保存，避免重复打扰）
     * @return 本次实际处理的剪贴板原文（供调用方记录），未命中返回 null
     */
    fun sniffAndOffer(activity: Activity, lastSeenRaw: String?): String? {
        val raw = readClipboard(activity) ?: return null
        if (raw == lastSeenRaw) return null
        val sniff = detect(raw) ?: return null

        showCapsule(activity, sniff)
        return raw
    }

    private fun showCapsule(activity: Activity, sniff: ClipboardSniff) {
        val dialog = Dialog(activity)
        val view = LayoutInflater.from(activity).inflate(R.layout.layout_dialog_clipboard_sniffer, null)
        dialog.setContentView(view)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(
                (activity.resources.displayMetrics.widthPixels * 0.92f).toInt(),
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            setGravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
        }
        dialog.setCancelable(true)

        val message = view.findViewById<TextView>(R.id.clipSniffMessage)
        val action = view.findViewById<TextView>(R.id.clipSniffAction)
        val close = view.findViewById<TextView>(R.id.clipSniffClose)

        when (sniff.kind) {
            Kind.TITLE -> {
                message.text = "📋 发现剪贴板作品《${sniff.title}》"
                action.text = "一键收录 ➔"
                action.setOnClickListener {
                    dialog.dismiss()
                    QuickLogBottomSheet.show(activity, prefillTitle = sniff.title)
                }
            }
            Kind.DOUBAN_URL -> {
                val mediaLabel = when (sniff.mediaType) {
                    MediaType.MOVIE -> "🎬 豆瓣电影"
                    MediaType.MUSIC -> "🎵 豆瓣音乐"
                    else -> "📖 豆瓣图书"
                }
                message.text = "📋 检测到 $mediaLabel 链接，正在直取元数据…"
                action.text = "立即收录 ➔"
                action.setOnClickListener {
                    dialog.dismiss()
                    insertDoubanByLinkId(activity, sniff.subjectId, sniff.mediaType ?: MediaType.BOOK)
                }
            }
            Kind.BANGUMI_URL -> {
                message.text = "📋 检测到 Bangumi 链接，正在直取官方元数据…"
                action.text = "立即收录 ➔"
                action.setOnClickListener {
                    dialog.dismiss()
                    insertBangumiByLinkId(activity, sniff.subjectId)
                }
            }
            Kind.STEAM_URL -> {
                message.text = "📋 检测到 Steam 游戏链接，可前往搬家中心收录"
                action.text = "前往 ➔"
                action.setOnClickListener {
                    dialog.dismiss()
                    activity.startActivity(
                        Intent(activity, com.example.readtrace.DataMigrationActivity::class.java),
                    )
                }
            }
        }
        close.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    /** Bangumi 链接 0 误差直取：按 subject id 拉取官方元数据，直接以「想看」状态落库 */
    private fun insertBangumiByLinkId(activity: Activity, subjectId: Long?) {
        val id = subjectId ?: return
        Thread {
            val subject = com.example.readtrace.util.BangumiApiClient.fetchSubjectByIdSync(id)
            if (subject == null || subject.displayTitle.isBlank()) {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    Toast.makeText(activity, "未能获取该链接的作品信息", Toast.LENGTH_SHORT).show()
                }
                return@Thread
            }
            val databaseHelper = BookDatabaseHelper.getInstance(activity)
            val newId = databaseHelper.insertBook(
                Book(
                    title = subject.displayTitle,
                    author = subject.creator,
                    coverUrl = subject.coverUrl,
                    category = subject.tags.firstOrNull(),
                    tags = subject.tags,
                    status = com.example.readtrace.model.BookStatus.WISHLIST,
                    mediaType = MediaType.ANIME,
                    sourceType = subject.source,
                    sourceId = subject.id.toString(),
                    remoteRating = subject.ratingScore,
                    description = subject.summary,
                ),
            )
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                Toast.makeText(
                    activity,
                    if (newId > 0) "⚡ 已收录《${subject.displayTitle}》至想看" else "收录失败，请重试",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }.start()
    }

    /** 豆瓣链接 0 误差直取：按 subject id 及目标媒介拉取官方元数据，直接以对应想看/想读/想听落库 */
    private fun insertDoubanByLinkId(activity: Activity, subjectId: Long?, mediaType: MediaType = MediaType.BOOK) {
        val id = subjectId ?: return
        val probe = BangumiSubject(id = id, name = "", nameCn = null, coverUrl = null)
        DoubanClient.getSubjectDetail(probe, mediaType) { subject ->
            if (subject == null || subject.displayTitle.isBlank()) {
                Toast.makeText(activity, "未能获取该链接的作品信息", Toast.LENGTH_SHORT).show()
                return@getSubjectDetail
            }
            val defaultStatus = com.example.readtrace.model.BookStatus.WISHLIST
            val databaseHelper = BookDatabaseHelper.getInstance(activity)
            val newId = databaseHelper.insertBook(
                Book(
                    title = subject.displayTitle,
                    author = subject.creator,
                    coverUrl = subject.coverUrl,
                    category = subject.tags.firstOrNull(),
                    tags = subject.tags,
                    status = defaultStatus,
                    mediaType = mediaType,
                    sourceType = DoubanClient.SOURCE_DOUBAN,
                    sourceId = subject.id.toString(),
                    remoteRating = subject.ratingScore,
                    description = subject.summary,
                ),
            )
            Toast.makeText(
                activity,
                if (newId > 0) "⚡ 已收录《${subject.displayTitle}》· ${defaultStatus.getDisplayName(mediaType)}" else "收录失败，请重试",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }
}
