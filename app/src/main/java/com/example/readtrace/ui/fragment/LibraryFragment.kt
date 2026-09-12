package com.example.readtrace.ui.fragment

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.readtrace.AddBookActivity
import com.example.readtrace.BookDetailActivity
import com.example.readtrace.R
import com.example.readtrace.data.BookDatabaseHelper
import com.example.readtrace.data.UserPreferencesManager
import com.example.readtrace.model.Book
import com.example.readtrace.model.BookStatus
import com.example.readtrace.model.MediaType
import com.example.readtrace.util.CoverImageHelper
import com.example.readtrace.util.ElegantChoiceDialog
import com.example.readtrace.util.HapticFeedbackEngine
import com.example.readtrace.util.ViewAnimationHelper
import com.example.readtrace.widget.MindprintRadarView
import java.text.DecimalFormat
import java.time.LocalDate
import kotlin.math.roundToInt

class LibraryFragment : Fragment() {

    private lateinit var databaseHelper: BookDatabaseHelper

    private lateinit var libraryScroll: ScrollView
    private lateinit var btnLibraryScrollTop: View
    private var isScrollTopVisible: Boolean = false

    private lateinit var btnLibraryAdd: View
    private lateinit var mediaChipAll: TextView
    private lateinit var mediaChipBook: TextView
    private lateinit var mediaChipAnime: TextView
    private lateinit var mediaChipMovie: TextView
    private lateinit var mediaChipGame: TextView
    private lateinit var mediaChipMusic: TextView

    private lateinit var librarySearchInput: EditText
    private lateinit var librarySearchClearButton: View
    private lateinit var statusChipAll: TextView
    private lateinit var statusChipReading: TextView
    private lateinit var statusChipFinished: TextView
    private lateinit var statusChipWishlist: TextView
    private lateinit var libraryTagScroller: HorizontalScrollView
    private lateinit var libraryTagGroup: LinearLayout

    private lateinit var ratingFilterBar: LinearLayout
    private lateinit var ratingChipAll: TextView
    private lateinit var ratingChip7075: TextView
    private lateinit var ratingChip7580: TextView
    private lateinit var ratingChip8090: TextView
    private lateinit var ratingChip90Plus: TextView

    enum class RatingRange(val label: String) {
        RANGE_70_75("7.0~7.5"),
        RANGE_75_80("7.5~8.0"),
        RANGE_80_90("8.0~9.0"),
        RANGE_90_PLUS("9.0以上");

        fun matches(rating: Double?): Boolean {
            if (rating == null) return false
            return when (this) {
                RANGE_70_75 -> rating in 7.0..7.5
                RANGE_75_80 -> rating in 7.5..8.0
                RANGE_80_90 -> rating >= 8.0 && rating < 9.0
                RANGE_90_PLUS -> rating >= 9.0
            }
        }
    }
    private var selectedRatingRange: RatingRange? = null

    private lateinit var libraryCountText: TextView
    private lateinit var btnLibraryToggleView: TextView
    private lateinit var btnLibraryExportScroll: TextView
    private lateinit var libraryBooksContainer: LinearLayout
    private lateinit var libraryEmptyPanel: View

    // 整页翻页导航条
    private lateinit var libraryPagerBar: View
    private lateinit var btnLibraryPrevPage: TextView
    private lateinit var btnLibraryNextPage: TextView
    private lateinit var libraryPageIndicator: TextView

    private var selectedMediaType: MediaType? = null
    private var selectedStatus: BookStatus? = null
    private var searchKeyword: String = ""
    private var selectedTag: String? = null
    private var isGridView: Boolean = false

    // 整页翻页：currentPage 为 0 基页码，currentFilteredBooks 为当前筛选结果集
    // （endNoteView：最后一页尾部的收尾文案）
    private var currentPage: Int = 0
    private var currentFilteredBooks: List<Book> = emptyList()
    private var endNoteView: TextView? = null

    // 内存数据缓存与搜索防抖，避免频繁切标签与按键触发 SQLite 全表扫描
    private var cachedAllBooks: List<Book> = emptyList()

    /** 藏库加载时的全局缓存代际版本（T2.1）：版本变化说明外部页面写过数据，需要重查 */
    private var loadedCacheVersion = -1
    private val searchHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var searchRunnable: Runnable? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.fragment_library, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        databaseHelper = BookDatabaseHelper.getInstance(requireContext())

        isGridView = UserPreferencesManager.isLibraryGridView(requireContext())

        initViews(view)
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        // T2.1：不再无条件绕过缓存——refreshLibrary 内部按全局缓存版本号判断
        // 「外部页面发生过写操作」时才重查，纯切 Tab 零数据库查询
        refreshLibrary(forceDbReload = false)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        searchRunnable?.let { searchHandler.removeCallbacks(it) }
    }

    private fun initViews(view: View) {
        btnLibraryAdd = view.findViewById(R.id.btnLibraryAdd)
        mediaChipAll = view.findViewById(R.id.mediaChipAll)
        mediaChipBook = view.findViewById(R.id.mediaChipBook)
        mediaChipAnime = view.findViewById(R.id.mediaChipAnime)
        mediaChipMovie = view.findViewById(R.id.mediaChipMovie)
        mediaChipGame = view.findViewById(R.id.mediaChipGame)
        mediaChipMusic = view.findViewById(R.id.mediaChipMusic)

        librarySearchInput = view.findViewById(R.id.librarySearchInput)
        librarySearchClearButton = view.findViewById(R.id.librarySearchClearButton)
        statusChipAll = view.findViewById(R.id.statusChipAll)
        statusChipReading = view.findViewById(R.id.statusChipReading)
        statusChipFinished = view.findViewById(R.id.statusChipFinished)
        statusChipWishlist = view.findViewById(R.id.statusChipWishlist)
        ratingFilterBar = view.findViewById(R.id.ratingFilterBar)
        ratingChipAll = view.findViewById(R.id.ratingChipAll)
        ratingChip7075 = view.findViewById(R.id.ratingChip7075)
        ratingChip7580 = view.findViewById(R.id.ratingChip7580)
        ratingChip8090 = view.findViewById(R.id.ratingChip8090)
        ratingChip90Plus = view.findViewById(R.id.ratingChip90Plus)

        libraryTagScroller = view.findViewById(R.id.libraryTagScroller)
        libraryTagGroup = view.findViewById(R.id.libraryTagGroup)

        libraryScroll = view.findViewById(R.id.libraryScroll)
        btnLibraryScrollTop = view.findViewById(R.id.btnLibraryScrollTop)
        libraryCountText = view.findViewById(R.id.libraryCountText)
        btnLibraryToggleView = view.findViewById(R.id.btnLibraryToggleView)
        btnLibraryExportScroll = view.findViewById(R.id.btnLibraryExportScroll)
        libraryBooksContainer = view.findViewById(R.id.libraryBooksContainer)
        libraryEmptyPanel = view.findViewById(R.id.libraryEmptyPanel)

        libraryPagerBar = view.findViewById(R.id.libraryPagerBar)
        btnLibraryPrevPage = view.findViewById(R.id.btnLibraryPrevPage)
        btnLibraryNextPage = view.findViewById(R.id.btnLibraryNextPage)
        libraryPageIndicator = view.findViewById(R.id.libraryPageIndicator)

        updateMediaChips()
        updateStatusChips()
        updateRatingChips()
        updateViewModeButton()
    }

    private fun setupListeners() {
        btnLibraryAdd.setOnClickListener {
            val intent = Intent(requireContext(), AddBookActivity::class.java).apply {
                selectedMediaType?.let { putExtra("extra_default_media_type", it.databaseValue) }
            }
            startActivity(intent)
        }

        libraryScroll.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            updateScrollTopButton(scrollY)
        }

        btnLibraryPrevPage.setOnClickListener {
            HapticFeedbackEngine.lightClick(requireContext())
            goToPage(currentPage - 1)
        }

        btnLibraryNextPage.setOnClickListener {
            HapticFeedbackEngine.lightClick(requireContext())
            goToPage(currentPage + 1)
        }

        btnLibraryScrollTop.setOnClickListener {
            libraryScroll.smoothScrollTo(0, 0)
        }

        mediaChipAll.setOnClickListener { selectMediaType(null) }
        mediaChipBook.setOnClickListener { selectMediaType(MediaType.BOOK) }
        mediaChipAnime.setOnClickListener { selectMediaType(MediaType.ANIME) }
        mediaChipMovie.setOnClickListener { selectMediaType(MediaType.MOVIE) }
        mediaChipGame.setOnClickListener { selectMediaType(MediaType.GAME) }
        mediaChipMusic.setOnClickListener { selectMediaType(MediaType.MUSIC) }

        statusChipAll.setOnClickListener { selectStatus(null) }
        statusChipReading.setOnClickListener { selectStatus(BookStatus.READING) }
        statusChipFinished.setOnClickListener { selectStatus(BookStatus.FINISHED) }
        statusChipWishlist.setOnClickListener { selectStatus(BookStatus.WISHLIST) }

        ratingChipAll.setOnClickListener {
            HapticFeedbackEngine.lightClick(requireContext())
            selectRatingRange(null)
        }
        ratingChip7075.setOnClickListener {
            HapticFeedbackEngine.lightClick(requireContext())
            selectRatingRange(RatingRange.RANGE_70_75)
        }
        ratingChip7580.setOnClickListener {
            HapticFeedbackEngine.lightClick(requireContext())
            selectRatingRange(RatingRange.RANGE_75_80)
        }
        ratingChip8090.setOnClickListener {
            HapticFeedbackEngine.lightClick(requireContext())
            selectRatingRange(RatingRange.RANGE_80_90)
        }
        ratingChip90Plus.setOnClickListener {
            HapticFeedbackEngine.lightClick(requireContext())
            selectRatingRange(RatingRange.RANGE_90_PLUS)
        }

        librarySearchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim().orEmpty()
                if (searchKeyword != query) {
                    searchKeyword = query
                    currentPage = 0
                    librarySearchClearButton.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE
                    searchRunnable?.let { searchHandler.removeCallbacks(it) }
                    searchRunnable = Runnable {
                        if (isAdded) {
                            refreshLibrary(forceDbReload = false)
                        }
                    }
                    searchHandler.postDelayed(searchRunnable!!, 250)
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        librarySearchClearButton.setOnClickListener {
            librarySearchInput.setText("")
            searchRunnable?.let { searchHandler.removeCallbacks(it) }
            refreshLibrary(forceDbReload = false)
        }

        btnLibraryToggleView.setOnClickListener {
            isGridView = !isGridView
            UserPreferencesManager.setLibraryGridView(requireContext(), isGridView)
            updateViewModeButton()
            refreshShelfOnly()
        }

        btnLibraryExportScroll.setOnClickListener {
            Toast.makeText(requireContext(), "正在生成全息藏书长卷...", Toast.LENGTH_SHORT).show()
        }

        listOfNotNull<View>(
            btnLibraryAdd, btnLibraryToggleView, btnLibraryExportScroll, btnLibraryScrollTop,
            btnLibraryPrevPage, btnLibraryNextPage,
            mediaChipAll, mediaChipBook, mediaChipAnime, mediaChipMovie, mediaChipGame, mediaChipMusic,
        ).forEach { ViewAnimationHelper.attachSpringTouch(it) }
    }

    private fun updateViewModeButton() {
        btnLibraryToggleView.text = if (isGridView) "📋 列表" else "🍱 双列"
    }

    private fun selectMediaType(type: MediaType?) {
        if (selectedMediaType == type) return
        selectedMediaType = type
        if (type == MediaType.MUSIC) {
            selectedStatus = null
        }
        currentPage = 0
        updateMediaChips()
        updateStatusChips()
        refreshLibrary(forceDbReload = false)
    }

    private fun updateMediaChips() {
        val chips = listOf(
            mediaChipAll to (selectedMediaType == null),
            mediaChipBook to (selectedMediaType == MediaType.BOOK),
            mediaChipAnime to (selectedMediaType == MediaType.ANIME),
            mediaChipMovie to (selectedMediaType == MediaType.MOVIE),
            mediaChipGame to (selectedMediaType == MediaType.GAME),
            mediaChipMusic to (selectedMediaType == MediaType.MUSIC),
        )
        val ctx = context ?: return
        chips.forEach { (chip, isSelected) ->
            chip.setBackgroundResource(if (isSelected) R.drawable.bg_status_chip_selected else R.drawable.bg_status_chip)
            chip.setTextColor(ContextCompat.getColor(ctx, if (isSelected) R.color.white else R.color.readtrace_ink))
        }
    }

    private fun selectStatus(status: BookStatus?) {
        if (selectedMediaType == MediaType.MUSIC) {
            if (selectedStatus == null) return
            selectedStatus = null
        } else {
            if (selectedStatus == status) return
            selectedStatus = status
        }
        currentPage = 0
        updateStatusChips()
        refreshLibrary(forceDbReload = false)
    }

    private fun updateStatusChips() {
        val ctx = context ?: return
        if (selectedMediaType == MediaType.MUSIC) {
            // 🎵 音乐分类：无需在听/听完/想听状态筛选，仅保留「全部」按钮
            statusChipReading.visibility = View.GONE
            statusChipFinished.visibility = View.GONE
            statusChipWishlist.visibility = View.GONE

            statusChipAll.visibility = View.VISIBLE
            statusChipAll.text = "全部"
            statusChipAll.setBackgroundResource(R.drawable.bg_segmented_item_selected)
            statusChipAll.setTextColor(ContextCompat.getColor(ctx, R.color.white))
            statusChipAll.typeface = android.graphics.Typeface.DEFAULT_BOLD
            return
        }

        // 其他分类：恢复状态筛选
        statusChipReading.visibility = View.VISIBLE
        statusChipFinished.visibility = View.VISIBLE
        statusChipWishlist.visibility = View.VISIBLE
        statusChipAll.visibility = View.VISIBLE

        val (readingText, finishedText, wishlistText) = when (selectedMediaType) {
            MediaType.BOOK -> Triple("在读", "已读", "想读")
            MediaType.ANIME -> Triple("追番中", "补完", "想追")
            MediaType.MOVIE -> Triple("在看", "已看", "想看")
            MediaType.GAME -> Triple("游玩中", "通关", "想玩")
            MediaType.MUSIC -> Triple("在听", "听完", "想听")
            null -> Triple("进行中", "已完成", "愿望单")
        }
        statusChipAll.text = "全部"
        statusChipReading.text = readingText
        statusChipFinished.text = finishedText
        statusChipWishlist.text = wishlistText

        val chips = listOf(
            statusChipAll to (selectedStatus == null),
            statusChipReading to (selectedStatus == BookStatus.READING),
            statusChipFinished to (selectedStatus == BookStatus.FINISHED),
            statusChipWishlist to (selectedStatus == BookStatus.WISHLIST),
        )
        chips.forEach { (chip, isSelected) ->
            if (isSelected) {
                chip.setBackgroundResource(R.drawable.bg_segmented_item_selected)
                chip.setTextColor(ContextCompat.getColor(ctx, R.color.white))
                chip.typeface = android.graphics.Typeface.DEFAULT_BOLD
            } else {
                chip.setBackgroundResource(0)
                chip.setTextColor(ContextCompat.getColor(ctx, R.color.readtrace_muted))
                chip.typeface = android.graphics.Typeface.DEFAULT
            }
        }
    }

    private fun selectRatingRange(range: RatingRange?) {
        if (selectedRatingRange == range) return
        selectedRatingRange = range
        currentPage = 0
        updateRatingChips()
        refreshLibrary(forceDbReload = false)
    }

    private fun updateRatingChips() {
        val ctx = context ?: return
        val chips = listOf(
            ratingChipAll to (selectedRatingRange == null),
            ratingChip7075 to (selectedRatingRange == RatingRange.RANGE_70_75),
            ratingChip7580 to (selectedRatingRange == RatingRange.RANGE_75_80),
            ratingChip8090 to (selectedRatingRange == RatingRange.RANGE_80_90),
            ratingChip90Plus to (selectedRatingRange == RatingRange.RANGE_90_PLUS),
        )
        chips.forEach { (chip, isSelected) ->
            if (isSelected) {
                chip.setBackgroundResource(R.drawable.bg_segmented_item_selected)
                chip.setTextColor(ContextCompat.getColor(ctx, R.color.white))
                chip.typeface = android.graphics.Typeface.DEFAULT_BOLD
            } else {
                chip.setBackgroundResource(0)
                chip.setTextColor(ContextCompat.getColor(ctx, R.color.readtrace_muted))
                chip.typeface = android.graphics.Typeface.DEFAULT
            }
        }
    }

    private fun selectTag(tag: String?) {
        if (selectedTag == tag) return
        selectedTag = tag
        currentPage = 0
        updateTagChips()
        refreshShelfOnly()
    }

    private fun refreshLibrary(forceDbReload: Boolean = true) {
        // T2.1：全局缓存版本号检测外部写操作（详情页/速记/备份恢复等 invalidate 过缓存时重查），
        // 数据源改用轻量列表查询（不含 description/review 长文本）
        val globalVersion = BookDatabaseHelper.getBookListCacheVersion()
        if (forceDbReload || cachedAllBooks.isEmpty() || globalVersion != loadedCacheVersion) {
            cachedAllBooks = databaseHelper.getBooksForList()
            loadedCacheVersion = globalVersion
        }
        val baseFilteredBooks = cachedAllBooks.filter { book ->
            val matchesMedia = selectedMediaType == null || book.mediaType == selectedMediaType
            val matchesStatus = selectedStatus == null || book.status == selectedStatus
            val matchesRating = selectedRatingRange == null || selectedRatingRange!!.matches(book.rating)
            val matchesKeyword = searchKeyword.isEmpty() ||
                com.example.readtrace.util.PinyinSearchHelper.matchesBook(book, searchKeyword)
            matchesMedia && matchesStatus && matchesRating && matchesKeyword
        }
        renderDynamicTags(baseFilteredBooks)
        refreshShelfOnly(baseFilteredBooks)
    }

    private fun renderDynamicTags(filteredBooks: List<Book>) {
        val tagCounts = mutableMapOf<String, Int>()
        // 年份类标签（如「2024年」）与「待看清单」（愿望单/想读状态已承载）不入分类筛选
        val yearTagRegex = Regex("^\\d{4}年?$")
        filteredBooks.forEach { book ->
            book.tags.forEach { tag ->
                // 番剧分类下的标签按用户偏好白名单过滤，
                // 只保留「京阿尼 / 麻枝准 / 骨头社 / 催泪神作 / 治愈」
                if (selectedMediaType == MediaType.ANIME && !ANIME_TAG_WHITELIST.contains(tag)) return@forEach
                val clean = tag.trim()
                if (clean.isEmpty() || yearTagRegex.matches(clean) || clean == "待看清单") return@forEach
                tagCounts[clean] = (tagCounts[clean] ?: 0) + 1
            }
        }
        val tagList = tagCounts.filter { it.value > 0 }.toList().sortedByDescending { it.second }

        if (selectedTag != null && (tagCounts[selectedTag] ?: 0) == 0) {
            selectedTag = null
        }

        if (tagList.isEmpty()) {
            libraryTagScroller.visibility = View.GONE
            selectedTag = null
            return
        }

        libraryTagScroller.visibility = View.VISIBLE
        libraryTagGroup.removeAllViews()

        val ctx = context ?: return
        tagList.forEachIndexed { index, (tag, count) ->
            val chip = TextView(ctx).apply {
                val isSelected = selectedTag == tag
                text = if (isSelected) "✓ $tag ($count)" else "$tag ($count)"
                textSize = 11.5f
                gravity = android.view.Gravity.CENTER
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    dpToPx(26),
                ).apply {
                    if (index > 0) marginStart = dpToPx(6)
                }
                layoutParams = params
                setPadding(dpToPx(10), 0, dpToPx(10), 0)
                setBackgroundResource(if (isSelected) R.drawable.bg_tag_outline_chip_selected else R.drawable.bg_tag_outline_chip)
                setTextColor(ContextCompat.getColor(ctx, if (isSelected) R.color.white else R.color.readtrace_ink))
                isClickable = true
                isFocusable = true
                setOnClickListener { selectTag(if (selectedTag == tag) null else tag) }
            }
            libraryTagGroup.addView(chip)
        }
    }

    private fun updateTagChips() {
        val ctx = context ?: return
        for (i in 0 until libraryTagGroup.childCount) {
            val chip = libraryTagGroup.getChildAt(i) as? TextView ?: continue
            val fullText = chip.text.toString()
            val rawTag = fullText.removePrefix("✓ ").substringBeforeLast(" (")
            val countPart = fullText.substringAfterLast(" (", "")
            val isSelected = selectedTag == rawTag
            chip.setBackgroundResource(if (isSelected) R.drawable.bg_tag_outline_chip_selected else R.drawable.bg_tag_outline_chip)
            chip.setTextColor(ContextCompat.getColor(ctx, if (isSelected) R.color.white else R.color.readtrace_ink))
            chip.text = if (isSelected) "✓ $rawTag ($countPart" else "$rawTag ($countPart"
        }
    }

    private fun refreshShelfOnly(baseBooks: List<Book>? = null) {
        val candidates = baseBooks ?: run {
            val allBooks = databaseHelper.getCachedBooks()
            allBooks.filter { book ->
                val matchesMedia = selectedMediaType == null || book.mediaType == selectedMediaType
                val matchesStatus = selectedStatus == null || book.status == selectedStatus
                val matchesRating = selectedRatingRange == null || selectedRatingRange!!.matches(book.rating)
                val matchesKeyword = searchKeyword.isEmpty() ||
                    com.example.readtrace.util.PinyinSearchHelper.matchesBook(book, searchKeyword)
                matchesMedia && matchesStatus && matchesRating && matchesKeyword
            }
        }

        val books = if (selectedTag != null) {
            candidates.filter { it.tags.contains(selectedTag) }
        } else {
            candidates
        }
        currentFilteredBooks = books

        libraryCountText.text = "共 ${books.size} 部藏品"

        if (books.isEmpty()) {
            libraryBooksContainer.removeAllViews()
            libraryBooksContainer.visibility = View.GONE
            libraryPagerBar.visibility = View.GONE
            libraryEmptyPanel.clearAnimation()
            libraryEmptyPanel.visibility = View.VISIBLE
            libraryEmptyPanel.alpha = 0f
            libraryEmptyPanel.animate().alpha(1f).setDuration(200).start()
            return
        }

        libraryEmptyPanel.clearAnimation()
        libraryEmptyPanel.visibility = View.GONE
        libraryBooksContainer.visibility = View.VISIBLE

        // 整页翻页：每次只渲染当前页的 PAGE_SIZE 部（页码越界由 renderPage 内部收敛）
        renderPage()

        libraryScroll.post {
            if (isAdded) {
                updateScrollTopButton(libraryScroll.scrollY)
            }
        }
    }

    // ---------------------------------------------------------------- 整页翻页（每页 PAGE_SIZE 部）

    /** 渲染当前页区间 [currentPage * PAGE_SIZE, +PAGE_SIZE)，页码越界时自动收敛 */
    private fun renderPage() {
        val books = currentFilteredBooks
        libraryBooksContainer.removeAllViews()
        endNoteView = null

        if (books.isEmpty()) {
            libraryPagerBar.visibility = View.GONE
            return
        }

        val totalPages = pageCount()
        currentPage = currentPage.coerceIn(0, totalPages - 1)
        val from = currentPage * PAGE_SIZE
        val to = minOf(books.size, from + PAGE_SIZE)

        renderCardsRange(from, to)
        updateEndNote()
        updatePager(totalPages)
    }

    /** 翻到指定页：越界即忽略；换页后回到列表顶部 */
    private fun goToPage(page: Int) {
        val totalPages = pageCount()
        if (page < 0 || page >= totalPages || page == currentPage) return
        currentPage = page
        renderPage()
        libraryScroll.scrollTo(0, 0)
        updateScrollTopButton(0)
    }

    private fun pageCount(): Int {
        val total = currentFilteredBooks.size
        return if (total <= 0) 0 else (total + PAGE_SIZE - 1) / PAGE_SIZE
    }

    /** 同步底部页码条：只有一页时整条隐藏，首/末页时置灰对应按钮 */
    private fun updatePager(totalPages: Int = pageCount()) {
        if (totalPages <= 1) {
            libraryPagerBar.visibility = View.GONE
            return
        }
        libraryPagerBar.visibility = View.VISIBLE
        libraryPageIndicator.text = "${currentPage + 1} / $totalPages"
        setPagerButtonEnabled(btnLibraryPrevPage, currentPage > 0)
        setPagerButtonEnabled(btnLibraryNextPage, currentPage < totalPages - 1)
    }

    /**
     * 仅切换可用态与置灰观感，刻意保留 clickable：
     * 若禁用时一并把 clickable 置为 false，按钮就不再消费触摸事件，
     * 于是在首页点「上一页」、末页点「下一页」会穿透到底层卡片并误开作品详情页。
     * 保持 clickable=true 时，禁用按钮只吞掉事件、不触发 onClick，恰好是所需行为。
     */
    private fun setPagerButtonEnabled(button: TextView, enabled: Boolean) {
        button.isEnabled = enabled
        button.isClickable = true
        button.alpha = if (enabled) 1f else 0.35f
    }

    /**
     * 渲染 [from, to) 区间：整页翻页每次只渲染一页，两页之间互不牵连。
     * 列表模式逐卡追加；双列模式两两成行，本页末尾落单的卡片独占最后一行左侧。
     */
    private fun renderCardsRange(from: Int, to: Int) {
        if (from >= to) return
        val books = currentFilteredBooks
        if (!isGridView) {
            for (i in from until to) {
                val card = createBookCard(books[i])
                libraryBooksContainer.addView(card)
                if (i - from < 8) ViewAnimationHelper.staggerFadeIn(card, i - from)
            }
            return
        }
        val ctx = context ?: return
        var index = from
        var rowIndex = 0
        while (index + 1 < to) {
            val row = createGridRow(ctx)
            row.addView(buildGridCard(books[index], isLeft = true))
            row.addView(buildGridCard(books[index + 1], isLeft = false))
            libraryBooksContainer.addView(row)
            if (rowIndex < 4) ViewAnimationHelper.staggerFadeIn(row, rowIndex)
            rowIndex++
            index += 2
        }
        if (index < to) {
            val row = createGridRow(ctx)
            row.addView(buildGridCard(books[index], isLeft = true))
            libraryBooksContainer.addView(row)
            if (rowIndex < 4) ViewAnimationHelper.staggerFadeIn(row, rowIndex)
        }
    }

    private fun createGridRow(ctx: Context): LinearLayout = LinearLayout(ctx).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dpToPx(8) }
        orientation = LinearLayout.HORIZONTAL
        weightSum = 2f
    }

    private fun buildGridCard(book: Book, isLeft: Boolean): View = createBookGridCard(book).apply {
        val p = layoutParams as LinearLayout.LayoutParams
        if (isLeft) p.marginEnd = dpToPx(4) else p.marginStart = dpToPx(4)
        layoutParams = p
    }

    /** 尾部静态文案：翻到最后一页才显示「已展示全部 N 部」，并始终保持在容器最后一个子视图 */
    private fun updateEndNote() {
        val ctx = context ?: return
        val total = currentFilteredBooks.size
        val note = endNoteView ?: TextView(ctx).apply {
            textSize = 12f
            gravity = android.view.Gravity.CENTER
            setTextColor(ContextCompat.getColor(ctx, R.color.readtrace_muted))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = dpToPx(18)
                bottomMargin = dpToPx(14)
            }
            endNoteView = this
        }
        if (total > 0 && currentPage >= pageCount() - 1) {
            note.text = "已展示全部 $total 部"
            note.visibility = View.VISIBLE
        } else {
            note.visibility = View.GONE
        }
        if (note.parent != null) libraryBooksContainer.removeView(note)
        libraryBooksContainer.addView(note)
    }

    private fun updateScrollTopButton(scrollY: Int) {
        val screenHeight = if (libraryScroll.height > 0) libraryScroll.height else resources.displayMetrics.heightPixels
        val shouldShow = scrollY > screenHeight
        if (shouldShow != isScrollTopVisible) {
            isScrollTopVisible = shouldShow
            btnLibraryScrollTop.animate().cancel()
            if (shouldShow) {
                btnLibraryScrollTop.visibility = View.VISIBLE
                btnLibraryScrollTop.alpha = 0f
                btnLibraryScrollTop.scaleX = 0.85f
                btnLibraryScrollTop.scaleY = 0.85f
                btnLibraryScrollTop.translationY = dpToPx(10).toFloat()
                btnLibraryScrollTop.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .translationY(0f)
                    .setDuration(220L)
                    .setInterpolator(DecelerateInterpolator(1.8f))
                    .start()
            } else {
                btnLibraryScrollTop.animate()
                    .alpha(0f)
                    .scaleX(0.85f)
                    .scaleY(0.85f)
                    .translationY(dpToPx(10).toFloat())
                    .setDuration(180L)
                    .setInterpolator(DecelerateInterpolator(1.5f))
                    .withEndAction {
                        if (!isScrollTopVisible) {
                            btnLibraryScrollTop.visibility = View.GONE
                        }
                    }
                    .start()
            }
        }
    }

    private fun createBookCard(book: Book): View {
        val swipeLayout = com.example.readtrace.widget.SwipeableActionLayout(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
        }
        val card = LayoutInflater.from(requireContext()).inflate(R.layout.item_book_card, swipeLayout, false)
        val coverImageView = card.findViewById<ImageView>(R.id.bookCardCoverImage)
        CoverImageHelper.loadCover(coverImageView, book.coverUrl)

        card.findViewById<TextView>(R.id.bookCardTitle).text = book.title
        card.findViewById<TextView>(R.id.bookCardAuthor).text = book.author ?: getString(R.string.unknown_author)

        val ratingLabel = book.rating?.let {
            getString(R.string.rating_format, RATING_FORMAT.format(it))
        } ?: getString(R.string.unrated)
        card.findViewById<TextView>(R.id.bookCardMeta).visibility = View.GONE
        card.findViewById<View>(R.id.bookCardSummaryRow).visibility = View.VISIBLE
        card.findViewById<TextView>(R.id.bookCardMediaBadge).text = book.mediaType.emoji
        val statusPill = card.findViewById<TextView>(R.id.bookCardStatusPill)
        if (book.mediaType == MediaType.MUSIC) {
            statusPill.visibility = View.GONE
        } else {
            statusPill.visibility = View.VISIBLE
            statusPill.text = book.status.getDisplayName(book.mediaType)
            statusPill.setOnClickListener {
                showChangeStatusDialog(book)
            }
            ViewAnimationHelper.attachSpringTouch(statusPill, 0.92f)
        }

        card.findViewById<TextView>(R.id.bookCardRating).text = ratingLabel
        card.findViewById<TextView>(R.id.bookCardCategory).apply {
            val category = book.category?.trim()
            if (category.isNullOrEmpty()) {
                visibility = View.GONE
            } else {
                visibility = View.VISIBLE
                text = category
            }
        }

        card.findViewById<TextView>(R.id.bookCardTags).apply {
            if (book.tags.isEmpty()) {
                visibility = View.GONE
            } else {
                visibility = View.VISIBLE
                text = book.tags.joinToString(" · ")
            }
        }
        card.findViewById<TextView>(R.id.bookCardComment).apply {
            val comment = book.shortComment?.trim()
            if (comment.isNullOrEmpty()) {
                visibility = View.GONE
            } else {
                visibility = View.VISIBLE
                text = comment
            }
        }
        card.setOnClickListener {
            startActivity(BookDetailActivity.createIntent(requireContext(), book.id))
        }
        card.setOnLongClickListener {
            showChangeStatusDialog(book)
            true
        }
        ViewAnimationHelper.attachSpringTouch(card, 0.97f)
        swipeLayout.addView(card)

        swipeLayout.onSwipeRightTriggered = {
            handleQuickMarkReading(book)
        }
        swipeLayout.onSwipeLeftTriggered = {
            handleQuickTrash(book)
        }

        return swipeLayout
    }

    private fun handleQuickMarkReading(book: Book) {
        val oldStatus = book.status
        val updated = book.copy(status = BookStatus.READING)
        databaseHelper.updateBook(updated)
        refreshLibrary(forceDbReload = true)

        val undoCapsule = view?.findViewById<com.example.readtrace.widget.UndoCapsuleBar>(R.id.libraryUndoCapsule)
        undoCapsule?.showCapsule(
            message = "已标记《${book.title}》为在读",
            onUndo = {
                databaseHelper.updateBook(book.copy(status = oldStatus))
                refreshLibrary(forceDbReload = true)
            },
        )
    }

    private fun handleQuickTrash(book: Book) {
        databaseHelper.archiveBook(book.id)
        refreshLibrary(forceDbReload = true)

        val undoCapsule = view?.findViewById<com.example.readtrace.widget.UndoCapsuleBar>(R.id.libraryUndoCapsule)
        undoCapsule?.showCapsule(
            message = "已移入回收站《${book.title}》",
            onUndo = {
                databaseHelper.restoreBook(book.id)
                refreshLibrary(forceDbReload = true)
            },
        )
    }

    private fun createBookGridCard(book: Book): View {
        val card = LayoutInflater.from(requireContext()).inflate(R.layout.item_book_grid_card, null, false)
        val coverImg = card.findViewById<ImageView>(R.id.bookGridCoverImage)
        CoverImageHelper.loadCover(coverImg, book.coverUrl)

        card.findViewById<TextView>(R.id.bookGridMediaBadge).text = book.mediaType.emoji
        val statusPill = card.findViewById<TextView>(R.id.bookGridStatusPill)
        if (book.mediaType == MediaType.MUSIC) {
            statusPill.visibility = View.GONE
        } else {
            statusPill.visibility = View.VISIBLE
            statusPill.text = book.status.getDisplayName(book.mediaType)
            statusPill.setOnClickListener {
                showChangeStatusDialog(book)
            }
            ViewAnimationHelper.attachSpringTouch(statusPill, 0.92f)
        }
        card.findViewById<TextView>(R.id.bookGridTitle).text = book.title
        card.findViewById<TextView>(R.id.bookGridAuthor).text = book.author ?: getString(R.string.unknown_author)

        val ratingLabel = book.rating?.let {
            getString(R.string.rating_format, RATING_FORMAT.format(it))
        } ?: getString(R.string.unrated)
        card.findViewById<TextView>(R.id.bookGridRating).text = ratingLabel

        card.findViewById<TextView>(R.id.bookGridCategory).apply {
            val category = book.category?.trim()
            if (category.isNullOrEmpty()) {
                visibility = View.GONE
            } else {
                visibility = View.VISIBLE
                text = category
            }
        }

        val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        card.layoutParams = params
        card.setOnClickListener {
            startActivity(BookDetailActivity.createIntent(requireContext(), book.id))
        }
        card.setOnLongClickListener {
            showChangeStatusDialog(book)
            true
        }
        ViewAnimationHelper.attachSpringTouch(card, 0.96f)
        return card
    }

    /**
     * 🏷️ 高质感作品状态切换对话框：自适应 5 大媒介类型，磨砂暗夜和纸质感，支持即时撤销
     */
    private fun showChangeStatusDialog(book: Book) {
        val statuses = listOf(
            BookStatus.WISHLIST,
            BookStatus.READING,
            BookStatus.FINISHED,
            BookStatus.PAUSED,
            BookStatus.DROPPED,
        )

        val choices = statuses.map { status ->
            val label = status.getDisplayName(book.mediaType)
            val (emoji, subtitle) = when (status) {
                BookStatus.READING -> Pair("📖", when (book.mediaType) {
                    MediaType.ANIME -> "正在热烈追更中"
                    MediaType.MOVIE -> "正在品味播放中"
                    MediaType.GAME -> "正在探索攻关中"
                    MediaType.MUSIC -> "正在单曲循环中"
                    else -> "正在用心翻阅中"
                })
                BookStatus.FINISHED -> Pair("🏆", when (book.mediaType) {
                    MediaType.ANIME -> "已追完，全篇大圆满"
                    MediaType.MOVIE -> "已看毕，留下深刻印记"
                    MediaType.GAME -> "已通关，征服全成就"
                    MediaType.MUSIC -> "已赏毕，余音绕梁"
                    else -> "已读毕，收获满篇心迹"
                })
                BookStatus.WISHLIST -> Pair("⏳", when (book.mediaType) {
                    MediaType.ANIME -> "加入待追番单，静候空闲"
                    MediaType.MOVIE -> "加入待看片单，安排观影"
                    MediaType.GAME -> "加入心愿单，择期开坑"
                    MediaType.MUSIC -> "收藏至待听，稍后品鉴"
                    else -> "加入书单待读，静待翻启"
                })
                BookStatus.PAUSED -> Pair("⏸️", "暂时搁置，稍后再续")
                BookStatus.DROPPED -> Pair("🍂", "暂不合心意，停止记录")
            }
            ElegantChoiceDialog.Choice(
                label = label,
                subtitle = subtitle,
                leadingEmoji = emoji,
            )
        }

        val selectedIndex = statuses.indexOf(book.status).takeIf { it >= 0 } ?: 0

        ElegantChoiceDialog.show(
            activity = requireActivity(),
            title = "🏷️ 更改作品状态 · 《${book.title}》",
            choices = choices,
            selectedIndex = selectedIndex,
        ) { which ->
            val newStatus = statuses.getOrNull(which) ?: return@show
            if (newStatus == book.status) return@show

            HapticFeedbackEngine.stampImpact(requireContext())

            val now = LocalDate.now().toString()
            val oldBook = book
            val updated = when {
                newStatus == BookStatus.FINISHED && book.finishDate.isNullOrBlank() ->
                    book.copy(status = newStatus, finishDate = now)
                newStatus == BookStatus.READING && book.startDate.isNullOrBlank() ->
                    book.copy(status = newStatus, startDate = now)
                else ->
                    book.copy(status = newStatus)
            }

            databaseHelper.updateBook(updated)
            refreshLibrary(forceDbReload = true)

            view?.findViewById<com.example.readtrace.widget.UndoCapsuleBar>(R.id.libraryUndoCapsule)
                ?.showCapsule(
                    message = "已将《${book.title}》标记为【${newStatus.getDisplayName(book.mediaType)}】",
                    onUndo = {
                        databaseHelper.updateBook(oldBook)
                        refreshLibrary(forceDbReload = true)
                    },
                )
        }
    }

    private fun dpToPx(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    companion object {
        private val RATING_FORMAT = DecimalFormat("0.#")

        // 番剧分类标签白名单（用户偏好 2026-09-01）：
        // 只保留这些社团/制作人/类型标签，其余一律不展示在筛选条上。
        // 如需增删，直接修改本集合即可。
        private val ANIME_TAG_WHITELIST = setOf(
            "京阿尼",
            "麻枝准",
            "骨头社",
            "催泪神作",
            "治愈",
        )

        // 整页翻页：每页展示的作品数量
        private const val PAGE_SIZE = 20
    }
}
