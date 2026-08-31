package com.example.readtrace

import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
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
import androidx.activity.addCallback
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
import com.example.readtrace.util.DoubanClient
import com.example.readtrace.util.FloatingBack
import com.example.readtrace.util.HapticFeedbackEngine
import com.example.readtrace.util.NeteaseClient
import com.example.readtrace.util.RankRepository
import com.example.readtrace.util.SteamClient
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
    private lateinit var modeTitle: TextView
    private lateinit var sourceNote: TextView
    private lateinit var swipeRefresh: androidx.swiperefreshlayout.widget.SwipeRefreshLayout
    private lateinit var clearSearchButton: View
    private lateinit var adapter: SubjectAdapter
    private lateinit var batchButton: TextView
    private lateinit var batchBar: View
    private lateinit var batchConfirm: TextView

    private var selectedMediaType: MediaType = MediaType.BOOK
    private var existingBooks: List<Book> = emptyList()

    // v4.2.23 分页会话状态：会话令牌 + 是否还有下一页 + 是否正在加载
    private var sessionToken: Long = 0L
    private var hasMore: Boolean = true
    private var isLoadingPage: Boolean = false

    // v4.2.24 批量多选：选中条目集合（键 = "来源:id"，源隔离防跨源误判）
    private val selectedKeys = LinkedHashSet<String>()
    private var selectionMode = false

    private val mediaChips = mutableListOf<Pair<TextView, MediaType>>()
    private val searchHandler = Handler(Looper.getMainLooper())
    private val searchRunnable = Runnable { performSearch() }

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
        modeTitle = findViewById(R.id.discoverModeTitle)
        sourceNote = findViewById(R.id.discoverSourceNote)
        swipeRefresh = findViewById(R.id.discoverSwipe)
        clearSearchButton = findViewById(R.id.discoverClearSearch)
        batchButton = findViewById(R.id.discoverBatchButton)
        batchBar = findViewById(R.id.discoverBatchBar)
        batchConfirm = findViewById(R.id.discoverBatchConfirm)

        // v4.2.15 下拉刷新：强制跳过缓存联网更新；顶部内容可见时才允许触发
        swipeRefresh.setOnRefreshListener {
            performSearch(forceRefresh = true)
        }
        swipeRefresh.setOnChildScrollUpCallback { _, _ ->
            gridView.canScrollVertically(-1)
        }

        setupMediaChips()

        adapter = SubjectAdapter(
            onItemClicked = { subject, position ->
                android.util.Log.d("DiscoverBatch", "click pos=$position sel=$selectionMode title=${subject.displayTitle}")
                HapticFeedbackEngine.cartridgeSnap(this)
                if (selectionMode) toggleSelection(subject, position) else openSubjectPreview(subject)
            },
            onItemLongClicked = { subject, position ->
                android.util.Log.d("DiscoverBatch", "longclick pos=$position sel=$selectionMode owned=${isOwned(subject)} title=${subject.displayTitle}")
                // 长按进入批量模式并选中该条目（已收藏条目不可选）
                if (!selectionMode) enterSelectionMode()
                if (!isOwned(subject)) toggleSelection(subject, position)
                true
            },
            onQuickAdd = { subject -> quickAddSubject(subject) },
        )
        val gridLayoutManager = GridLayoutManager(this, 2)
        // 页脚（加载中/没有更多）横跨双列
        gridLayoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int =
                if (adapter.isFooterPosition(position)) gridLayoutManager.spanCount else 1
        }
        gridView.layoutManager = gridLayoutManager
        gridView.adapter = adapter

        // v4.2.23 无限滚动：接近底部自动加载下一页
        gridView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy <= 0) return
                val layoutManager = recyclerView.layoutManager as? GridLayoutManager ?: return
                val lastVisible = layoutManager.findLastVisibleItemPosition()
                if (lastVisible >= layoutManager.itemCount - 6) loadNextPage()
            }
        })

        searchButton.setOnClickListener {
            HapticFeedbackEngine.lightClick(this)
            performSearch()
        }
        searchInput.setOnEditorActionListener { _, _, _ ->
            performSearch()
            true
        }
        // v4.2.15：输入防抖自动搜索（500ms），不必每次都点按钮
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchHandler.removeCallbacks(searchRunnable)
                searchHandler.postDelayed(searchRunnable, SEARCH_DEBOUNCE_MS)
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        // 清空输入立刻回到热门榜单
        findViewById<View>(R.id.discoverClearSearch).setOnClickListener {
            searchInput.setText("")
            HapticFeedbackEngine.lightClick(this)
            performSearch()
        }
        emptyView.setOnClickListener {
            HapticFeedbackEngine.lightClick(this)
            performSearch(forceRefresh = true)
        }

        // v4.2.24 批量纪念：入口 / 取消 / 确认
        batchButton.setOnClickListener {
            HapticFeedbackEngine.lightClick(this)
            enterSelectionMode()
        }
        findViewById<View>(R.id.discoverBatchCancel).setOnClickListener {
            HapticFeedbackEngine.lightClick(this)
            exitSelectionMode()
        }
        batchConfirm.setOnClickListener { confirmBatch() }

        // 批量模式下返回键先退出选择，而不是直接离开页面
        onBackPressedDispatcher.addCallback(this) {
            if (selectionMode) {
                exitSelectionMode()
            } else {
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        }

        refreshExistingBooks()
        performSearch()
    }

    override fun onDestroy() {
        searchHandler.removeCallbacks(searchRunnable)
        if (sessionToken != 0L) RankRepository.discardSession(sessionToken)
        super.onDestroy()
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

    private fun performSearch(forceRefresh: Boolean = false) {
        val keyword = searchInput.text?.toString()?.trim().orEmpty()
        // v4.2.24：切分类/新搜索时重置批量选择态，防止跨会话残留选中
        selectionMode = false
        selectedKeys.clear()
        batchBar.visibility = View.GONE
        batchButton.visibility = View.VISIBLE
        loadingView.visibility = View.VISIBLE
        emptyView.visibility = View.GONE
        gridView.visibility = View.GONE
        modeTitle.text = if (keyword.isEmpty()) {
            getString(R.string.discover_mode_rank)
        } else {
            getString(R.string.discover_mode_search, keyword)
        }
        clearSearchButton.visibility = if (keyword.isEmpty()) View.GONE else View.VISIBLE
        // v4.2.23：重置分页会话（旧会话作废，防陈旧回调串扰），从第 0 页重新加载
        if (sessionToken != 0L) RankRepository.discardSession(sessionToken)
        sessionToken = RankRepository.startSession(selectedMediaType, keyword)
        hasMore = true
        adapter.submitList(emptyList())
        loadNextPage(forceRefresh)
    }

    /** 加载会话下一页：无限滚动、下拉刷新与首次进入共用此入口 */
    private fun loadNextPage(forceRefresh: Boolean = false) {
        if (isLoadingPage || !hasMore) return
        isLoadingPage = true
        if (adapter.itemDataCount() > 0) {
            adapter.setFooterState(FOOTER_LOADING)
        }
        RankRepository.loadPage(this, sessionToken, forceRefresh) { result ->
            isLoadingPage = false
            swipeRefresh.isRefreshing = false
            loadingView.visibility = View.GONE
            if (result == null || result.items.isEmpty()) {
                hasMore = false
                if (adapter.itemDataCount() == 0) {
                    emptyView.visibility = View.VISIBLE
                    gridView.visibility = View.GONE
                    sourceNote.text = getString(R.string.discover_source_note)
                } else {
                    adapter.setFooterState(FOOTER_END)
                }
                updateModeTitle()
                return@loadPage
            }
            sourceNote.text = result.sourceNote.ifEmpty { getString(R.string.discover_source_note) }
            adapter.appendItems(result.items)
            hasMore = result.hasMore
            adapter.setFooterState(if (hasMore) FOOTER_NONE else FOOTER_END)
            emptyView.visibility = View.GONE
            gridView.visibility = View.VISIBLE
            updateModeTitle()
        }
    }

    private fun updateModeTitle() {
        val keyword = searchInput.text?.toString()?.trim().orEmpty()
        modeTitle.text = buildString {
            append(
                if (keyword.isEmpty()) {
                    getString(R.string.discover_mode_rank)
                } else {
                    getString(R.string.discover_mode_search, keyword)
                },
            )
            val count = adapter.itemDataCount()
            if (count > 0) append(" · 已加载 $count 部")
        }
    }

    private fun refreshExistingBooks() {
        existingBooks = databaseHelper.getBooks()
    }

    /** 第一层精确 + 第二层模糊的组合判定，供列表角标使用 */
    private fun isOwned(subject: BangumiSubject): Boolean {
        if (existingBooks.any { it.sourceType == subject.source && it.sourceId == subject.id.toString() }) {
            return true
        }
        return existingBooks.any {
            it.mediaType == selectedMediaType &&
                it.title.equals(subject.displayTitle.trim(), ignoreCase = true)
        }
    }

    /** 选中键：来源:id（不同源 id 体系互相独立，必须带源隔离） */
    private fun keyOf(subject: BangumiSubject): String = "${subject.source}:${subject.id}"

    // ---------------------------------------------------------------- v4.2.24 批量多选

    private fun enterSelectionMode() {
        if (selectionMode) return
        selectionMode = true
        batchBar.visibility = View.VISIBLE
        batchButton.visibility = View.GONE
        updateBatchBar()
        adapter.notifyDataSetChanged()
    }

    private fun exitSelectionMode() {
        selectionMode = false
        selectedKeys.clear()
        batchBar.visibility = View.GONE
        batchButton.visibility = View.VISIBLE
        adapter.notifyDataSetChanged()
    }

    private fun toggleSelection(subject: BangumiSubject, position: Int) {
        if (isOwned(subject)) {
            android.util.Log.d("DiscoverBatch", "toggle skip owned: ${subject.displayTitle}")
            return
        }
        val key = keyOf(subject)
        if (!selectedKeys.remove(key)) selectedKeys.add(key)
        android.util.Log.d("DiscoverBatch", "toggle $key size=${selectedKeys.size}")
        updateBatchBar()
        adapter.notifyItemChanged(position)
    }

    private fun updateBatchBar() {
        val count = selectedKeys.size
        batchConfirm.text = getString(R.string.discover_batch_confirm, count)
        batchConfirm.isEnabled = count > 0
        batchConfirm.alpha = if (count > 0) 1f else 0.5f
    }

    /** 批量确认：默认纪念为「已看」，逐条双层查重跳过并计数，单事务落库 */
    private fun confirmBatch() {
        if (selectedKeys.isEmpty()) return
        HapticFeedbackEngine.stampImpact(this)
        val toInsert = mutableListOf<Book>()
        val insertedTitles = mutableSetOf<String>()
        var skipped = 0
        for (subject in adapter.itemsSnapshot()) {
            if (keyOf(subject) !in selectedKeys) continue
            val title = subject.displayTitle.trim()
            // 精确（含已删除防回收站复活）+ 标题模糊 + 本批内标题互斥
            val duplicate = databaseHelper.findBookBySource(subject.source, subject.id.toString()) != null ||
                existingBooks.any { it.mediaType == selectedMediaType && it.title.equals(title, ignoreCase = true) } ||
                insertedTitles.any { it.equals(title, ignoreCase = true) }
            if (duplicate) {
                skipped++
                continue
            }
            toInsert += buildImportedBook(subject, BookStatus.FINISHED)
            insertedTitles += title
        }
        if (toInsert.isNotEmpty()) databaseHelper.insertBooksBatch(toInsert)
        refreshExistingBooks()
        Toast.makeText(
            this,
            getString(R.string.discover_batch_done, toInsert.size, skipped),
            Toast.LENGTH_LONG,
        ).show()
        exitSelectionMode()
    }

    /** 卡片「＋」快捷添加：免弹窗直接纪念为已看 */
    private fun quickAddSubject(subject: BangumiSubject) {
        if (isOwned(subject) || selectionMode) return
        HapticFeedbackEngine.stampImpact(this)
        insertImportedSubject(subject, BookStatus.FINISHED)
        Toast.makeText(this, R.string.discover_import_success, Toast.LENGTH_SHORT).show()
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

        // 详情增强：搜索结果无 infobox（创作者/完整简介），异步拉详情补齐后回填预览与落库数据
        var enrichedCreator: String? = null
        var enrichedSummary: String? = null
        val fetchDetail: (BangumiSubject?) -> Unit = { detail ->
            if (detail != null && dialog.isShowing) {
                enrichedCreator = detail.creator
                enrichedSummary = detail.summary
                detail.creator?.let { creator ->
                    metaView.text = "${metaView.text}\n${selectedMediaType.creatorLabel}：$creator"
                }
                detail.summary?.takeIf { it.isNotBlank() }?.let { full ->
                    summaryView.text = full
                }
            }
        }
        when (selectedMediaType) {
            MediaType.GAME -> SteamClient.getSubjectDetail(subject, onResult = fetchDetail)
            MediaType.MUSIC -> NeteaseClient.getSubjectDetail(subject, onResult = fetchDetail)
            else -> DoubanClient.getSubjectDetail(subject, selectedMediaType, onResult = fetchDetail)
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
            // v4.2.23 封面补全：封面缺失（豆瓣风控/字段缺失）时用标题走 Bangumi 官方接口搜一次，
            // 请求量随用户打开预览的行为自然受限，不做批量补抓
            BangumiApiClient.searchSubjects(this, subject.displayTitle, selectedMediaType) { matches, _ ->
                if (!dialog.isShowing) return@searchSubjects
                val best = matches?.firstOrNull { it.coverUrl != null }
                if (best != null) {
                    cover.visibility = View.VISIBLE
                    coverPlaceholder.visibility = View.GONE
                    CoverImageHelper.loadCover(cover, best.coverUrl, coverPlaceholder)
                    if (enrichedSummary == null) {
                        best.summary?.takeIf { it.isNotBlank() }?.let {
                            enrichedSummary = it
                            summaryView.text = it
                        }
                    }
                }
            }
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
            it.sourceType == subject.source && it.sourceId == subject.id.toString()
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
            // 详情增强优先：拉到 infobox 创作者与完整简介则覆盖搜索结果里的占位值
            val enriched = subject.copy(
                creator = enrichedCreator ?: subject.creator,
                summary = enrichedSummary ?: subject.summary,
            )
            insertImportedSubject(enriched, selectedStatus)
            Toast.makeText(this, R.string.discover_import_success, Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialog.show()
    }

    /** 落库：只填客观骨架字段，个人字段全部留白（导入的是骨架，纪念才是灵魂） */
    private fun insertImportedSubject(subject: BangumiSubject, status: BookStatus) {
        databaseHelper.insertBook(buildImportedBook(subject, status))
        refreshExistingBooks()
        // 列表角标即时刷新
        adapter.notifyDataSetChanged()
    }

    /** 导入条目 → 藏库 Book 的骨架构造（单条添加与批量纪念共用） */
    private fun buildImportedBook(subject: BangumiSubject, status: BookStatus): Book = Book(
        title = subject.displayTitle.trim(),
        author = subject.creator,
        coverUrl = subject.coverUrl, // 纯在线源：直接存源站 CDN 地址
        category = subject.date,
        status = status,
        mediaType = selectedMediaType,
        tags = subject.tags.take(3),
        sourceType = subject.source,
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

    // ---------------------------------------------------------------- 列表适配器

    private inner class SubjectAdapter(
        private val onItemClicked: (BangumiSubject, Int) -> Unit,
        private val onItemLongClicked: (BangumiSubject, Int) -> Boolean,
        private val onQuickAdd: (BangumiSubject) -> Unit,
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private val items = mutableListOf<BangumiSubject>()
        private var footerState = FOOTER_NONE

        /** 批量纪念遍历时取快照，避免遍历中列表被改动 */
        fun itemsSnapshot(): List<BangumiSubject> = items.toList()

        fun submitList(newItems: List<BangumiSubject>) {
            items.clear()
            items.addAll(newItems)
            footerState = FOOTER_NONE
            notifyDataSetChanged()
        }

        /** v4.2.23 分页追加：只在尾部插入新条目，不重建整表 */
        fun appendItems(newItems: List<BangumiSubject>) {
            if (newItems.isEmpty()) return
            val insertStart = items.size
            items.addAll(newItems)
            notifyItemRangeInserted(insertStart, newItems.size)
        }

        /** 页脚态切换：NONE=无页脚，LOADING=加载中，END=没有更多 */
        fun setFooterState(state: Int) {
            if (footerState == state) return
            footerState = state
            notifyDataSetChanged()
        }

        fun itemDataCount(): Int = items.size

        fun isFooterPosition(position: Int): Boolean =
            footerState != FOOTER_NONE && position == items.size

        override fun getItemViewType(position: Int): Int =
            if (isFooterPosition(position)) VIEW_TYPE_FOOTER else VIEW_TYPE_ITEM

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            if (viewType == VIEW_TYPE_FOOTER) {
                val footerView = TextView(parent.context).apply {
                    layoutParams = RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
                    gravity = android.view.Gravity.CENTER
                    textSize = 12f
                    val verticalPad = (20 * parent.context.resources.displayMetrics.density).toInt()
                    setPadding(0, verticalPad, 0, verticalPad)
                    setTextColor(androidx.core.content.ContextCompat.getColor(parent.context, R.color.readtrace_muted))
                }
                return FooterViewHolder(footerView)
            }
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_discover_subject, parent, false)
            return SubjectViewHolder(view)
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (holder) {
                is FooterViewHolder -> holder.bind(footerState, items.size)
                is SubjectViewHolder -> holder.bind(items[position])
            }
        }

        override fun getItemCount(): Int = items.size + if (footerState != FOOTER_NONE) 1 else 0

        private inner class FooterViewHolder(private val textView: TextView) : RecyclerView.ViewHolder(textView) {
            fun bind(state: Int, loadedCount: Int) {
                textView.text = when (state) {
                    FOOTER_LOADING -> "正在加载更多作品…"
                    FOOTER_END -> "没有更多了 · 已加载 $loadedCount 部"
                    else -> ""
                }
            }
        }

        inner class SubjectViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val cover = itemView.findViewById<ImageView>(R.id.itemDiscoverCover)
            private val placeholder = itemView.findViewById<FrameLayout>(R.id.itemDiscoverPlaceholder)
            private val placeholderEmoji = itemView.findViewById<TextView>(R.id.itemDiscoverPlaceholderEmoji)
            private val ratingView = itemView.findViewById<TextView>(R.id.itemDiscoverRating)
            private val ownedBadge = itemView.findViewById<TextView>(R.id.itemDiscoverOwnedBadge)
            private val titleView = itemView.findViewById<TextView>(R.id.itemDiscoverTitle)
            private val dateView = itemView.findViewById<TextView>(R.id.itemDiscoverDate)
            private val quickAdd = itemView.findViewById<TextView>(R.id.itemDiscoverQuickAdd)
            private val selectedRing = itemView.findViewById<TextView>(R.id.itemDiscoverSelectedRing)

            init {
                itemView.setOnClickListener {
                    val pos = bindingAdapterPosition
                    android.util.Log.d("DiscoverBatch", "holder click pos=$pos inItems=${pos in items.indices}")
                    if (pos in items.indices) onItemClicked(items[pos], pos)
                }
                itemView.setOnLongClickListener {
                    val pos = bindingAdapterPosition
                    android.util.Log.d("DiscoverBatch", "holder longclick pos=$pos")
                    if (pos in items.indices) onItemLongClicked(items[pos], pos) else false
                }
                quickAdd.setOnClickListener {
                    val pos = bindingAdapterPosition
                    if (pos in items.indices) onQuickAdd(items[pos])
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
                val owned = isOwned(subject)
                ownedBadge.visibility = if (owned) View.VISIBLE else View.GONE
                // 「＋」快捷添加：已收藏或批量选择模式下隐藏，避免与点选冲突
                quickAdd.visibility = if (!owned && !selectionMode) View.VISIBLE else View.GONE
                selectedRing.visibility =
                    if (selectionMode && keyOf(subject) in selectedKeys) View.VISIBLE else View.GONE
            }
        }
    }

    companion object {
        private const val EXTRA_MEDIA_TYPE = "extra_media_type"
        private const val SEARCH_DEBOUNCE_MS = 500L
        private const val VIEW_TYPE_ITEM = 0
        private const val VIEW_TYPE_FOOTER = 1

        // 列表页脚三态
        const val FOOTER_NONE = 0
        const val FOOTER_LOADING = 1
        const val FOOTER_END = 2

        fun start(from: AppCompatActivity, mediaType: MediaType) {
            from.startActivity(
                Intent(from, DiscoverActivity::class.java)
                    .putExtra(EXTRA_MEDIA_TYPE, mediaType.databaseValue),
            )
        }
    }
}
