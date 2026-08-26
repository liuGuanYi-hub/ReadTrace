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
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.readtrace.AddBookActivity
import com.example.readtrace.BookDetailActivity
import com.example.readtrace.R
import com.example.readtrace.data.BookDatabaseHelper
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

    private lateinit var btnLibraryAdd: View
    private lateinit var mediaChipAll: TextView
    private lateinit var mediaChipBook: TextView
    private lateinit var mediaChipAnime: TextView
    private lateinit var mediaChipMovie: TextView
    private lateinit var mediaChipGame: TextView

    private lateinit var librarySearchInput: EditText
    private lateinit var librarySearchClearButton: View
    private lateinit var statusChipAll: TextView
    private lateinit var statusChipReading: TextView
    private lateinit var statusChipFinished: TextView
    private lateinit var statusChipWishlist: TextView
    private lateinit var libraryTagScroller: HorizontalScrollView
    private lateinit var libraryTagGroup: LinearLayout
    private lateinit var libraryTagAll: TextView

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

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.fragment_library, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        databaseHelper = BookDatabaseHelper(requireContext())

        val prefs = requireContext().getSharedPreferences("readtrace_prefs", Context.MODE_PRIVATE)
        isGridView = prefs.getBoolean("pref_is_grid_view_library", false)

        initViews(view)
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        refreshLibrary()
    }

    private fun initViews(view: View) {
        btnLibraryAdd = view.findViewById(R.id.btnLibraryAdd)
        mediaChipAll = view.findViewById(R.id.mediaChipAll)
        mediaChipBook = view.findViewById(R.id.mediaChipBook)
        mediaChipAnime = view.findViewById(R.id.mediaChipAnime)
        mediaChipMovie = view.findViewById(R.id.mediaChipMovie)
        mediaChipGame = view.findViewById(R.id.mediaChipGame)

        librarySearchInput = view.findViewById(R.id.librarySearchInput)
        librarySearchClearButton = view.findViewById(R.id.librarySearchClearButton)
        statusChipAll = view.findViewById(R.id.statusChipAll)
        statusChipReading = view.findViewById(R.id.statusChipReading)
        statusChipFinished = view.findViewById(R.id.statusChipFinished)
        statusChipWishlist = view.findViewById(R.id.statusChipWishlist)
        libraryTagScroller = view.findViewById(R.id.libraryTagScroller)
        libraryTagGroup = view.findViewById(R.id.libraryTagGroup)
        libraryTagAll = view.findViewById(R.id.libraryTagAll)

        libraryCountText = view.findViewById(R.id.libraryCountText)
        btnLibraryToggleView = view.findViewById(R.id.btnLibraryToggleView)
        btnLibraryExportScroll = view.findViewById(R.id.btnLibraryExportScroll)
        libraryBooksContainer = view.findViewById(R.id.libraryBooksContainer)
        libraryEmptyPanel = view.findViewById(R.id.libraryEmptyPanel)

        updateViewModeButton()
    }

    private fun setupListeners() {
        btnLibraryAdd.setOnClickListener {
            val intent = Intent(requireContext(), AddBookActivity::class.java).apply {
                selectedMediaType?.let { putExtra("extra_default_media_type", it.databaseValue) }
            }
            startActivity(intent)
        }

        mediaChipAll.setOnClickListener { selectMediaType(null) }
        mediaChipBook.setOnClickListener { selectMediaType(MediaType.BOOK) }
        mediaChipAnime.setOnClickListener { selectMediaType(MediaType.ANIME) }
        mediaChipMovie.setOnClickListener { selectMediaType(MediaType.MOVIE) }
        mediaChipGame.setOnClickListener { selectMediaType(MediaType.GAME) }

        statusChipAll.setOnClickListener { selectStatus(null) }
        statusChipReading.setOnClickListener { selectStatus(BookStatus.READING) }
        statusChipFinished.setOnClickListener { selectStatus(BookStatus.FINISHED) }
        statusChipWishlist.setOnClickListener { selectStatus(BookStatus.WISHLIST) }

        libraryTagAll.setOnClickListener { selectTag(null) }

        librarySearchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim().orEmpty()
                if (searchKeyword != query) {
                    searchKeyword = query
                    currentDisplayLimit = 24
                    librarySearchClearButton.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE
                    refreshLibrary()
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        librarySearchClearButton.setOnClickListener { librarySearchInput.setText("") }

        btnLibraryToggleView.setOnClickListener {
            isGridView = !isGridView
            requireContext().getSharedPreferences("readtrace_prefs", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("pref_is_grid_view_library", isGridView)
                .apply()
            updateViewModeButton()
            refreshShelfOnly()
        }

        btnLibraryExportScroll.setOnClickListener {
            Toast.makeText(requireContext(), "正在生成全息藏书长卷...", Toast.LENGTH_SHORT).show()
        }

        listOfNotNull<View>(
            btnLibraryAdd, btnLibraryToggleView, btnLibraryExportScroll,
            mediaChipAll, mediaChipBook, mediaChipAnime, mediaChipMovie, mediaChipGame,
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
        refreshLibrary()
    }

    private fun updateMediaChips() {
        val chips = listOf(
            mediaChipAll to (selectedMediaType == null),
            mediaChipBook to (selectedMediaType == MediaType.BOOK),
            mediaChipAnime to (selectedMediaType == MediaType.ANIME),
            mediaChipMovie to (selectedMediaType == MediaType.MOVIE),
            mediaChipGame to (selectedMediaType == MediaType.GAME),
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
        refreshLibrary()
    }

    private fun updateStatusChips() {
        val (readingText, finishedText, wishlistText) = when (selectedMediaType) {
            MediaType.BOOK -> Triple("在读", "已读", "想读")
            MediaType.ANIME -> Triple("追番中", "补完", "想追")
            MediaType.MOVIE -> Triple("在看", "已看", "想看")
            MediaType.GAME -> Triple("游玩中", "通关", "想玩")
            MediaType.PODCAST -> Triple("收听中", "听完", "想听")
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
            chip.setBackgroundResource(if (isSelected) R.drawable.bg_status_chip_selected else R.drawable.bg_status_chip)
            chip.setTextColor(ContextCompat.getColor(ctx, if (isSelected) R.color.white else R.color.readtrace_ink))
        }
    }

    private fun selectTag(tag: String?) {
        if (selectedTag == tag) return
        selectedTag = tag
        currentDisplayLimit = 24
        updateTagChips()
        refreshShelfOnly()
    }

    private fun refreshLibrary() {
        val allBooks = databaseHelper.getBooks()
        val baseFilteredBooks = allBooks.filter { book ->
            val matchesMedia = selectedMediaType == null || book.mediaType == selectedMediaType
            val matchesStatus = selectedStatus == null || book.status == selectedStatus
            val matchesKeyword = searchKeyword.isEmpty() ||
                book.title.contains(searchKeyword, ignoreCase = true) ||
                (book.author?.contains(searchKeyword, ignoreCase = true) == true) ||
                (book.category?.contains(searchKeyword, ignoreCase = true) == true)
            matchesMedia && matchesStatus && matchesKeyword
        }
        renderDynamicTags(baseFilteredBooks)
        refreshShelfOnly(baseFilteredBooks)
    }

    private fun renderDynamicTags(filteredBooks: List<Book>) {
        val tagCounts = mutableMapOf<String, Int>()
        filteredBooks.forEach { book ->
            book.tags.forEach { tag ->
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
        while (libraryTagGroup.childCount > 1) {
            libraryTagGroup.removeViewAt(1)
        }

        val ctx = context ?: return
        tagList.forEach { (tag, count) ->
            val chip = TextView(ctx, null, 0, R.style.ReadTraceStatusChip).apply {
                text = "$tag ($count)"
                textSize = 11.5f
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    dpToPx(30),
                ).apply {
                    marginStart = dpToPx(6)
                }
                layoutParams = params
                isClickable = true
                isFocusable = true
                setOnClickListener { selectTag(if (selectedTag == tag) null else tag) }
            }
            libraryTagGroup.addView(chip)
        }
        updateTagChips()
    }

    private fun updateTagChips() {
        val ctx = context ?: return
        libraryTagAll.setBackgroundResource(if (selectedTag == null) R.drawable.bg_status_chip_selected else R.drawable.bg_status_chip)
        libraryTagAll.setTextColor(ContextCompat.getColor(ctx, if (selectedTag == null) R.color.white else R.color.readtrace_ink))

        for (i in 1 until libraryTagGroup.childCount) {
            val chip = libraryTagGroup.getChildAt(i) as? TextView ?: continue
            val tagText = chip.text.toString().substringBeforeLast(" (")
            val isSelected = selectedTag == tagText
            chip.setBackgroundResource(if (isSelected) R.drawable.bg_status_chip_selected else R.drawable.bg_status_chip)
            chip.setTextColor(ContextCompat.getColor(ctx, if (isSelected) R.color.white else R.color.readtrace_ink))
        }
    }

    private fun refreshShelfOnly(baseBooks: List<Book>? = null) {
        val candidates = baseBooks ?: run {
            val allBooks = databaseHelper.getBooks()
            allBooks.filter { book ->
                val matchesMedia = selectedMediaType == null || book.mediaType == selectedMediaType
                val matchesStatus = selectedStatus == null || book.status == selectedStatus
                val matchesKeyword = searchKeyword.isEmpty() ||
                    book.title.contains(searchKeyword, ignoreCase = true) ||
                    (book.author?.contains(searchKeyword, ignoreCase = true) == true) ||
                    (book.category?.contains(searchKeyword, ignoreCase = true) == true)
                matchesMedia && matchesStatus && matchesKeyword
            }
        }

        val books = if (selectedTag != null) {
            candidates.filter { it.tags.contains(selectedTag) }
        } else {
            candidates
        }

        libraryCountText.text = "共 ${books.size} 部藏品"
        libraryBooksContainer.removeAllViews()

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

        val displayBooks = books.take(currentDisplayLimit)

        if (!isGridView) {
            displayBooks.forEachIndexed { index, book ->
                val card = createBookCard(book)
                libraryBooksContainer.addView(card)
                if (index < 8) ViewAnimationHelper.staggerFadeIn(card, index)
            }
        } else {
            val rows = displayBooks.chunked(2)
            val ctx = requireContext()
            rows.forEachIndexed { rowIndex, pair ->
                val rowLayout = LinearLayout(ctx).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        topMargin = dpToPx(8)
                    }
                    orientation = LinearLayout.HORIZONTAL
                    weightSum = 2f
                }

                val leftCard = createBookGridCard(pair[0]).apply {
                    val p = layoutParams as LinearLayout.LayoutParams
                    p.marginEnd = dpToPx(4)
                    layoutParams = p
                }
                rowLayout.addView(leftCard)

                if (pair.size > 1) {
                    val rightCard = createBookGridCard(pair[1]).apply {
                        val p = layoutParams as LinearLayout.LayoutParams
                        p.marginStart = dpToPx(4)
                        layoutParams = p
                    }
                    rowLayout.addView(rightCard)
                } else {
                    val emptySpace = View(ctx).apply {
                        layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
                    }
                    rowLayout.addView(emptySpace)
                }

                libraryBooksContainer.addView(rowLayout)
                if (rowIndex < 4) ViewAnimationHelper.staggerFadeIn(rowLayout, rowIndex)
            }
        }

        // 若藏品数量超过当前展示上限，添加优雅的「加载更多」按钮
        if (books.size > currentDisplayLimit) {
            val ctx = requireContext()
            val loadMoreBtn = TextView(ctx).apply {
                text = "查看更多藏品 (剩余 ${books.size - currentDisplayLimit} 部) ↓"
                textSize = 13f
                gravity = android.view.Gravity.CENTER
                setBackgroundResource(R.drawable.bg_secondary_button)
                setTextColor(ContextCompat.getColor(ctx, R.color.readtrace_ink))
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dpToPx(42),
                ).apply {
                    topMargin = dpToPx(16)
                    bottomMargin = dpToPx(12)
                }
                layoutParams = params
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    currentDisplayLimit += 30
                    refreshShelfOnly()
                }
            }
            ViewAnimationHelper.attachSpringTouch(loadMoreBtn)
            libraryBooksContainer.addView(loadMoreBtn)
        }
    }

    private fun createBookCard(book: Book): View {
        val card = LayoutInflater.from(requireContext()).inflate(R.layout.item_book_card, libraryBooksContainer, false)
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
            val act = activity
            val intent = BookDetailActivity.createIntent(requireContext(), book.id)
            if (act != null) {
                val options = com.example.readtrace.util.TransitionHelper.createTransitionOptions(act, coverImageView)
                startActivity(intent, options.toBundle())
            } else {
                startActivity(intent)
            }
        }
        card.setOnLongClickListener {
            showHologramPeekDialog(book)
            true
        }
        ViewAnimationHelper.attachSpringTouch(card, 0.97f)
        return card
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
            val act = activity
            val intent = BookDetailActivity.createIntent(requireContext(), book.id)
            if (act != null) {
                val options = com.example.readtrace.util.TransitionHelper.createTransitionOptions(act, coverImg)
                startActivity(intent, options.toBundle())
            } else {
                startActivity(intent)
            }
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
        ratingText.text = "★ ${book.rating ?: 5.0}"

        val mp = databaseHelper.getMindprint(book.id)
        radarView.setMindprint(mp, animate = false)

        btnDetail.setOnClickListener {
            dialog.dismiss()
            startActivity(BookDetailActivity.createIntent(requireContext(), book.id))
        }
        view.findViewById<View>(R.id.peekActionTimer).setOnClickListener { dialog.dismiss() }
        view.findViewById<View>(R.id.peekActionPoster).setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    private fun dpToPx(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    override fun onDestroyView() {
        databaseHelper.close()
        super.onDestroyView()
    }

    companion object {
        private val RATING_FORMAT = DecimalFormat("0.#")
    }
}
