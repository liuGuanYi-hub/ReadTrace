package com.example.readtrace

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.readtrace.data.BookDatabaseHelper
import com.example.readtrace.model.BangumiSubject
import com.example.readtrace.model.Book
import com.example.readtrace.model.BookStatus
import com.example.readtrace.model.MediaType
import com.example.readtrace.util.BangumiApiClient
import com.example.readtrace.util.CoverImageHelper
import com.example.readtrace.util.FloatingBack
import com.example.readtrace.util.HapticFeedbackEngine
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.time.LocalDate

/**
 * 🔍 发现页（v4.2.14 外部导入最小闭环）
 *
 * Bangumi 官方开放 API 搜索 + 一键添加至纪念：
 * - 空关键词 = 按 Bangumi 排行（rank）展示热门作品，关键词 = match 搜索；
 * - 双层查重：source_id 精确命中显示「已收藏」角标并禁止重复添加；
 *   title + media_type 模糊命中弹「可能已存在」提示但不强拦；
 * - 导入只填客观骨架字段（标题/创作者/在线封面/简介/远程评分/标签），
 *   个人评分、感想、日期等全部留白，由用户在详情页慢慢补全。
 */
class DiscoverActivity : AppCompatActivity() {

    private lateinit var databaseHelper: BookDatabaseHelper
    private lateinit var searchInput: EditText
    private lateinit var searchButton: View
    private lateinit var loadingView: View
    private lateinit var emptyView: TextView
    private lateinit var gridView: RecyclerView
    private lateinit var adapter: SubjectAdapter

    private var selectedMediaType: MediaType = MediaType.BOOK
    private var existingBooks: List<Book> = emptyList()
    private var currentResults: List<BangumiSubject> = emptyList()

    private val mediaChips = mutableListOf<Pair<TextView, MediaType>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_discover)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.discoverRoot)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        databaseHelper = BookDatabaseHelper.getInstance(this)
        selectedMediaType = MediaType.fromDatabaseValue(
            intent.getStringExtra(EXTRA_MEDIA_TYPE),
        )

        FloatingBack.install(this)

        searchInput = findViewById(R.id.discoverSearchInput)
        searchButton = findViewById(R.id.discoverSearchButton)
        loadingView = findViewById(R.id.discoverLoading)
        emptyView = findViewById(R.id.discoverEmpty)
        gridView = findViewById(R.id.discoverGrid)

        setupMediaChips()

        adapter = SubjectAdapter(onItemClicked = { subject ->
            HapticFeedbackEngine.cartridgeSnap(this)
            openSubjectPreview(subject)
        })
        gridView.layoutManager = GridLayoutManager(this, 2)
        gridView.adapter = adapter

        searchButton.setOnClickListener {
            HapticFeedbackEngine.lightClick(this)
            performSearch()
        }
        searchInput.setOnEditorActionListener { _, _, _ ->
            performSearch()
            true
        }

        refreshExistingBooks()
        performSearch()
    }

    private fun setupMediaChips() {
        mediaChips += listOf(
            findViewById<TextView>(R.id.discoverMediaTypeBook) to MediaType.BOOK,
            findViewById<TextView>(R.id.discoverMediaTypeAnime) to MediaType.ANIME,
            findViewById<TextView>(R.id.discoverMediaTypeMovie) to MediaType.MOVIE,
            findViewById<TextView>(R.id.discoverMediaTypeGame) to MediaType.GAME,
            findViewById<TextView>(R.id.discoverMediaTypeMusic) to MediaType.MUSIC,
        )
        mediaChips.forEach { (chip, media) ->
            chip.setOnClickListener {
                if (selectedMediaType == media) return@setOnClickListener
                selectedMediaType = media
                HapticFeedbackEngine.lightClick(this)
                updateMediaChips()
                performSearch()
            }
        }
        updateMediaChips()
    }

    private fun updateMediaChips() {
        mediaChips.forEach { (chip, media) ->
            val isSelected = media == selectedMediaType
            chip.setBackgroundResource(
                if (isSelected) R.drawable.bg_status_chip_selected else android.R.color.transparent,
            )
            chip.setTextColor(
                androidx.core.content.ContextCompat.getColor(
                    this,
                    if (isSelected) R.color.white else R.color.readtrace_ink,
                ),
            )
            chip.paint.isFakeBoldText = isSelected
        }
    }

    private fun performSearch() {
        val keyword = searchInput.text?.toString()?.trim().orEmpty()
        loadingView.visibility = View.VISIBLE
        emptyView.visibility = View.GONE
        gridView.visibility = View.GONE
        BangumiApiClient.searchSubjects(keyword, selectedMediaType) { results ->
            loadingView.visibility = View.GONE
            currentResults = results.orEmpty()
            if (currentResults.isEmpty()) {
                emptyView.visibility = View.VISIBLE
                gridView.visibility = View.GONE
            } else {
                emptyView.visibility = View.GONE
                gridView.visibility = View.VISIBLE
                adapter.submitList(currentResults)
            }
        }
    }

    private fun refreshExistingBooks() {
        existingBooks = databaseHelper.getBooks()
    }

    /** 第一层精确 + 第二层模糊的组合判定，供列表角标使用 */
    private fun isOwned(subject: BangumiSubject): Boolean {
        if (existingBooks.any { it.sourceType == SOURCE_BANGUMI && it.sourceId == subject.id.toString() }) {
            return true
        }
        return existingBooks.any {
            it.mediaType == selectedMediaType &&
                it.title.equals(subject.displayTitle.trim(), ignoreCase = true)
        }
    }

    private fun openSubjectPreview(subject: BangumiSubject) {
        val dialog = BottomSheetDialog(this, R.style.Theme_ReadTrace_BottomSheetDialog)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_subject_preview, null)
        dialog.setContentView(view)

        val cover = view.findViewById<ImageView>(R.id.previewCover)
        val coverPlaceholder = view.findViewById<FrameLayout>(R.id.previewCoverPlaceholder)
        val titleView = view.findViewById<TextView>(R.id.previewTitle)
        val originalView = view.findViewById<TextView>(R.id.previewOriginalName)
        val metaView = view.findViewById<TextView>(R.id.previewMeta)
        val summaryView = view.findViewById<TextView>(R.id.previewSummary)
        val duplicateHint = view.findViewById<TextView>(R.id.previewDuplicateHint)
        val addButton = view.findViewById<TextView>(R.id.previewAddButton)

        titleView.text = subject.displayTitle
        originalView.text = if (subject.displayTitle != subject.name) subject.name else ""
        metaView.text = buildString {
            subject.ratingScore?.let { append("⭐ Bangumi 评分 ${String.format(java.util.Locale.US, "%.1f", it)}") }
            subject.date?.takeIf { it.isNotBlank() }?.let {
                if (isNotEmpty()) append(" · ")
                append(it)
            }
        }.ifEmpty { null } ?: getString(R.string.discover_no_meta)
        summaryView.text = subject.summary ?: getString(R.string.discover_summary_loading)
        subject.tags.take(3).let { tags ->
            if (tags.isNotEmpty()) {
                metaView.text = "${metaView.text} · ${tags.joinToString(" / ")}"
            }
        }

        // 在线封面直接走既有加载链路（内存 LRU + 磁盘缓存 + 占位图兜底）
        subject.coverUrl?.let {
            CoverImageHelper.loadCover(cover, it, coverPlaceholder)
            coverPlaceholder.findViewById<TextView>(R.id.previewCoverPlaceholderEmoji)
                .text = selectedMediaType.emoji
        } ?: run {
            cover.visibility = View.GONE
            coverPlaceholder.visibility = View.VISIBLE
            coverPlaceholder.findViewById<TextView>(R.id.previewCoverPlaceholderEmoji)
                .text = selectedMediaType.emoji
        }

        // 状态选择：添加时必选（默认想读）
        val statusChips = listOf(
            view.findViewById<TextView>(R.id.previewStatusWishlist) to BookStatus.WISHLIST,
            view.findViewById<TextView>(R.id.previewStatusReading) to BookStatus.READING,
            view.findViewById<TextView>(R.id.previewStatusFinished) to BookStatus.FINISHED,
            view.findViewById<TextView>(R.id.previewStatusPaused) to BookStatus.PAUSED,
            view.findViewById<TextView>(R.id.previewStatusDropped) to BookStatus.DROPPED,
        )
        var selectedStatus = BookStatus.WISHLIST
        statusChips.forEach { (chip, status) ->
            chip.text = selectedMediaType.getStatusLabel(status)
            chip.setOnClickListener {
                selectedStatus = status
                HapticFeedbackEngine.lightClick(this)
                statusChips.forEach { (c, s) ->
                    val isSelected = s == selectedStatus
                    c.setBackgroundResource(
                        if (isSelected) R.drawable.bg_status_chip_selected else android.R.color.transparent,
                    )
                    c.setTextColor(
                        androidx.core.content.ContextCompat.getColor(
                            this,
                            if (isSelected) R.color.white else R.color.readtrace_ink,
                        ),
                    )
                }
            }
        }

        // 双层查重决定按钮与提示形态
        val exactMatch = existingBooks.firstOrNull {
            it.sourceType == SOURCE_BANGUMI && it.sourceId == subject.id.toString()
        }
        if (exactMatch != null) {
            addButton.text = getString(R.string.discover_already_owned)
            addButton.isEnabled = false
            addButton.alpha = 0.5f
        } else {
            val fuzzy = databaseHelper.findBooksByTitleLike(subject.displayTitle, selectedMediaType)
                .filter { it.id != exactMatch?.id }
            if (fuzzy.isNotEmpty()) {
                duplicateHint.visibility = View.VISIBLE
                duplicateHint.text = getString(
                    R.string.discover_duplicate_hint,
                    fuzzy.first().title,
                )
            }
        }

        addButton.setOnClickListener {
            if (!addButton.isEnabled) return@setOnClickListener
            HapticFeedbackEngine.stampImpact(this)
            insertImportedSubject(subject, selectedStatus)
            Toast.makeText(this, R.string.discover_import_success, Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialog.show()
    }

    /** 落库：只填客观骨架字段，个人字段全部留白（导入的是骨架，纪念才是灵魂） */
    private fun insertImportedSubject(subject: BangumiSubject, status: BookStatus) {
        val book = Book(
            title = subject.displayTitle.trim(),
            author = subject.creator,
            coverUrl = subject.coverUrl, // 纯在线源：直接存 Bangumi CDN 地址
            category = subject.date,
            status = status,
            mediaType = selectedMediaType,
            tags = subject.tags.take(3),
            sourceType = SOURCE_BANGUMI,
            sourceId = subject.id.toString(),
            remoteRating = subject.ratingScore,
            description = subject.summary,
            startDate = if (status == BookStatus.READING || status == BookStatus.FINISHED) {
                LocalDate.now().toString()
            } else {
                null
            },
            finishDate = if (status == BookStatus.FINISHED) LocalDate.now().toString() else null,
        )
        databaseHelper.insertBook(book)
        refreshExistingBooks()
        // 列表角标即时刷新
        adapter.submitList(currentResults)
    }

    // ---------------------------------------------------------------- 列表适配器

    private inner class SubjectAdapter(
        private val onItemClicked: (BangumiSubject) -> Unit,
    ) : RecyclerView.Adapter<SubjectAdapter.SubjectViewHolder>() {

        private val items = mutableListOf<BangumiSubject>()

        fun submitList(newItems: List<BangumiSubject>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SubjectViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_discover_subject, parent, false)
            return SubjectViewHolder(view)
        }

        override fun onBindViewHolder(holder: SubjectViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount(): Int = items.size

        inner class SubjectViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val cover = itemView.findViewById<ImageView>(R.id.itemDiscoverCover)
            private val placeholder = itemView.findViewById<FrameLayout>(R.id.itemDiscoverPlaceholder)
            private val placeholderEmoji = itemView.findViewById<TextView>(R.id.itemDiscoverPlaceholderEmoji)
            private val ratingView = itemView.findViewById<TextView>(R.id.itemDiscoverRating)
            private val ownedBadge = itemView.findViewById<TextView>(R.id.itemDiscoverOwnedBadge)
            private val titleView = itemView.findViewById<TextView>(R.id.itemDiscoverTitle)
            private val dateView = itemView.findViewById<TextView>(R.id.itemDiscoverDate)

            init {
                itemView.setOnClickListener {
                    val pos = bindingAdapterPosition
                    if (pos in items.indices) onItemClicked(items[pos])
                }
            }

            fun bind(subject: BangumiSubject) {
                titleView.text = subject.displayTitle
                dateView.text = subject.date ?: selectedMediaType.displayName
                subject.ratingScore?.let {
                    ratingView.text = "⭐ ${String.format(java.util.Locale.US, "%.1f", it)}"
                    ratingView.visibility = View.VISIBLE
                } ?: run { ratingView.visibility = View.GONE }
                placeholderEmoji.text = selectedMediaType.emoji
                CoverImageHelper.loadCover(cover, subject.coverUrl, placeholder)
                ownedBadge.visibility = if (isOwned(subject)) View.VISIBLE else View.GONE
            }
        }
    }

    companion object {
        const val SOURCE_BANGUMI = "bangumi"
        private const val EXTRA_MEDIA_TYPE = "extra_media_type"

        fun start(from: AppCompatActivity, mediaType: MediaType) {
            from.startActivity(
                Intent(from, DiscoverActivity::class.java)
                    .putExtra(EXTRA_MEDIA_TYPE, mediaType.databaseValue),
            )
        }
    }
}
