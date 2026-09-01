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
import com.example.readtrace.util.ViewAnimationHelper
import com.example.readtrace.widget.MindprintRadarView
import java.text.DecimalFormat
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

    private lateinit var libraryCountText: TextView
    private lateinit var btnLibraryToggleView: TextView
    private lateinit var btnLibraryExportScroll: TextView
    private lateinit var libraryBooksContainer: LinearLayout
    private lateinit var libraryEmptyPanel: View

    private var selectedMediaType: MediaType? = null
    private var selectedStatus: BookStatus? = null
    private var searchKeyword: String = ""
    private var selectedTag: String? = null
    private var isGridView: Boolean = false
    private var currentDisplayLimit: Int = 24

    // v4.2.25 滚动到底自动加载：当前筛选结果集 + 增量渲染状态
    // （pendingCarry：双列模式下跨页的落单卡片；endNoteView：列表尾部「已展示全部」静态文案）
    private var currentFilteredBooks: List<Book> = emptyList()
    private var pendingCarry: Book? = null
    private var endNoteView: TextView? = null

    // 内存数据缓存与搜索防抖，避免频繁切标签与按键触发 SQLite 全表扫描
    private var cachedAllBooks: List<Book> = emptyList()
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
        refreshLibrary(forceDbReload = true)
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
        libraryTagScroller = view.findViewById(R.id.libraryTagScroller)
        libraryTagGroup = view.findViewById(R.id.libraryTagGroup)

        libraryScroll = view.findViewById(R.id.libraryScroll)
        btnLibraryScrollTop = view.findViewById(R.id.btnLibraryScrollTop)
        libraryCountText = view.findViewById(R.id.libraryCountText)
        btnLibraryToggleView = view.findViewById(R.id.btnLibraryToggleView)
        btnLibraryExportScroll = view.findViewById(R.id.btnLibraryExportScroll)
        libraryBooksContainer = view.findViewById(R.id.libraryBooksContainer)
        libraryEmptyPanel = view.findViewById(R.id.libraryEmptyPanel)

        updateMediaChips()
        updateStatusChips()
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
            // v4.2.25 滚动到底自动加载下一页（增量追加，不重建全表）
            maybeLoadNextPage()
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

        librarySearchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim().orEmpty()
                if (searchKeyword != query) {
                    searchKeyword = query
                    currentDisplayLimit = 24
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
            mediaChipAll, mediaChipBook, mediaChipAnime, mediaChipMovie, mediaChipGame, mediaChipMusic,
        ).forEach { ViewAnimationHelper.attachSpringTouch(it) }
    }

    private fun updateViewModeButton() {
        btnLibraryToggleView.text = if (isGridView) "📋 列表" else "🍱 双列"
    }

    private fun selectMediaType(type: MediaType?) {
        if (selectedMediaType == type) return
        selectedMediaType = type
        currentDisplayLimit = 24
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
        if (selectedStatus == status) return
        selectedStatus = status
        currentDisplayLimit = 24
        updateStatusChips()
        refreshLibrary(forceDbReload = false)
    }

    private fun updateStatusChips() {
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
        val ctx = context ?: return
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
        currentDisplayLimit = 24
        updateTagChips()
        refreshShelfOnly()
    }

    private fun refreshLibrary(forceDbReload: Boolean = true) {
        if (forceDbReload || cachedAllBooks.isEmpty()) {
            cachedAllBooks = databaseHelper.getCachedBooks()
        }
        val baseFilteredBooks = cachedAllBooks.filter { book ->
            val matchesMedia = selectedMediaType == null || book.mediaType == selectedMediaType
            val matchesStatus = selectedStatus == null || book.status == selectedStatus
            val matchesKeyword = searchKeyword.isEmpty() ||
                com.example.readtrace.util.PinyinSearchHelper.matchesBook(book, searchKeyword)
            matchesMedia && matchesStatus && matchesKeyword
        }
        renderDynamicTags(baseFilteredBooks)
        refreshShelfOnly(baseFilteredBooks)
    }

    private fun renderDynamicTags(filteredBooks: List<Book>) {
        val tagCounts = mutableMapOf<String, Int>()
        filteredBooks.forEach { book ->
            book.tags.forEach { tag ->
                // 番剧分类下的标签按用户偏好白名单过滤，
                // 只保留「京阿尼 / 麻枝准 / 骨头社 / 催泪神作 / 治愈」
                if (selectedMediaType == MediaType.ANIME && !ANIME_TAG_WHITELIST.contains(tag)) return@forEach
                tagCounts[tag] = (tagCounts[tag] ?: 0) + 1
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
                val matchesKeyword = searchKeyword.isEmpty() ||
                    com.example.readtrace.util.PinyinSearchHelper.matchesBook(book, searchKeyword)
                matchesMedia && matchesStatus && matchesKeyword
            }
        }

        val books = if (selectedTag != null) {
            candidates.filter { it.tags.contains(selectedTag) }
        } else {
            candidates
        }
        currentFilteredBooks = books

        libraryCountText.text = "共 ${books.size} 部藏品"
        libraryBooksContainer.removeAllViews()
        pendingCarry = null
        endNoteView = null

        if (books.isEmpty()) {
            libraryBooksContainer.visibility = View.GONE
            libraryEmptyPanel.clearAnimation()
            libraryEmptyPanel.visibility = View.VISIBLE
            libraryEmptyPanel.alpha = 0f
            libraryEmptyPanel.animate().alpha(1f).setDuration(200).start()
            return
        }

        libraryEmptyPanel.clearAnimation()
        libraryEmptyPanel.visibility = View.GONE
        libraryBooksContainer.visibility = View.VISIBLE

        // v4.2.25：首屏只渲染展示上限内的卡片，其余由滚动到底自动追加（替代手动「查看更多」）
        renderCardsRange(0, minOf(currentDisplayLimit, books.size))
        updateEndNote()

        libraryScroll.post {
            if (isAdded) {
                updateScrollTopButton(libraryScroll.scrollY)
            }
        }
    }

    // ---------------------------------------------------------------- v4.2.25 滚动自动加载

    /** 接近底部（阈值内）且仍有未展示条目时追加下一页 */
    private fun maybeLoadNextPage() {
        if (currentDisplayLimit >= currentFilteredBooks.size) return
        val content = libraryScroll.getChildAt(0) ?: return
        val bottomGap = content.bottom - (libraryScroll.scrollY + libraryScroll.height)
        if (bottomGap < dpToPx(AUTO_LOAD_TRIGGER_GAP_DP)) {
            appendNextPage()
        }
    }

    private fun appendNextPage() {
        val total = currentFilteredBooks.size
        if (currentDisplayLimit >= total) return
        val from = currentDisplayLimit
        currentDisplayLimit = minOf(total, from + PAGE_STEP)
        renderCardsRange(from, currentDisplayLimit)
        updateEndNote()
    }

    /**
     * 增量渲染 [from, to)：只在容器尾部追加，不清空重建。
     * 列表模式逐卡追加；双列模式两两成行，落单卡片由 pendingCarry 带过页边界。
     */
    private fun renderCardsRange(from: Int, to: Int) {
        if (from >= to) return
        val books = currentFilteredBooks
        val animate = from == 0
        if (!isGridView) {
            for (i in from until to) {
                val card = createBookCard(books[i])
                libraryBooksContainer.addView(card)
                if (animate && i < 8) ViewAnimationHelper.staggerFadeIn(card, i)
            }
            return
        }
        val ctx = context ?: return
        var index = from
        var rowIndex = 0
        // 上一页遗留的落单卡片与本页第一张配对
        if (pendingCarry != null) {
            val row = createGridRow(ctx)
            row.addView(buildGridCard(pendingCarry!!, isLeft = true))
            row.addView(buildGridCard(books[index], isLeft = false))
            libraryBooksContainer.addView(row)
            if (animate && rowIndex < 4) ViewAnimationHelper.staggerFadeIn(row, rowIndex)
            rowIndex++
            pendingCarry = null
            index++
        }
        while (index + 1 < to) {
            val row = createGridRow(ctx)
            row.addView(buildGridCard(books[index], isLeft = true))
            row.addView(buildGridCard(books[index + 1], isLeft = false))
            libraryBooksContainer.addView(row)
            if (animate && rowIndex < 4) ViewAnimationHelper.staggerFadeIn(row, rowIndex)
            rowIndex++
            index += 2
        }
        if (index < to) pendingCarry = books[index]
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

    /** 尾部静态文案：全部展示完才显示「已展示全部 N 部」，并始终保持在容器最后一个子视图 */
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
        if (total > 0 && currentDisplayLimit >= total) {
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
            getString(R.string.rating_format, RATING_FORMAT.format(it / 2.0))
        } ?: getString(R.string.unrated)
        card.findViewById<TextView>(R.id.bookCardMeta).visibility = View.GONE
        card.findViewById<View>(R.id.bookCardSummaryRow).visibility = View.VISIBLE
        card.findViewById<TextView>(R.id.bookCardMediaBadge).text = book.mediaType.emoji
        card.findViewById<TextView>(R.id.bookCardStatusPill).text = book.status.getDisplayName(book.mediaType)
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
            showHologramPeekDialog(book)
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
        card.findViewById<TextView>(R.id.bookGridStatusPill).text = book.status.getDisplayName(book.mediaType)
        card.findViewById<TextView>(R.id.bookGridTitle).text = book.title
        card.findViewById<TextView>(R.id.bookGridAuthor).text = book.author ?: getString(R.string.unknown_author)

        val ratingLabel = book.rating?.let {
            getString(R.string.rating_format, RATING_FORMAT.format(it / 2.0))
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
            showHologramPeekDialog(book)
            true
        }
        ViewAnimationHelper.attachSpringTouch(card, 0.96f)
        return card
    }

    private fun showHologramPeekDialog(book: Book) {
        val dialog = Dialog(requireContext())
        val view = layoutInflater.inflate(R.layout.dialog_book_hologram_peek, null)
        dialog.setContentView(view)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.90).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)

        val coverImage = view.findViewById<ImageView>(R.id.peekCoverImage)
        val titleText = view.findViewById<TextView>(R.id.peekBookTitle)
        val authorText = view.findViewById<TextView>(R.id.peekBookAuthor)
        val mediaBadge = view.findViewById<TextView>(R.id.peekMediaBadge)
        val statusBadge = view.findViewById<TextView>(R.id.peekStatusBadge)
        val ratingText = view.findViewById<TextView>(R.id.peekRating)
        val radarView = view.findViewById<MindprintRadarView>(R.id.peekMindprintRadar)
        val btnDetail = view.findViewById<View>(R.id.peekActionDetail)

        CoverImageHelper.loadCover(coverImage, book.coverUrl)
        mediaBadge.text = book.mediaType.emoji
        titleText.text = book.title
        authorText.text = "${book.category.orEmpty()} · ${book.author.orEmpty()}"
        statusBadge.text = book.status.getDisplayName(book.mediaType)
        ratingText.text = "★ ${book.rating?.div(2.0) ?: 2.5}"

        val mp = databaseHelper.getMindprint(book.id)
        radarView.setMindprint(mp, animate = false)

        btnDetail.setOnClickListener {
            dialog.dismiss()
            startActivity(BookDetailActivity.createIntent(requireContext(), book.id))
        }
        view.findViewById<View>(R.id.peekActionPoster).setOnClickListener { dialog.dismiss() }

        dialog.show()
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

        // v4.2.25 滚动自动加载：距底小于该阈值触发追加，每页追加 30 部
        private const val AUTO_LOAD_TRIGGER_GAP_DP = 800
        private const val PAGE_STEP = 30
    }
}
