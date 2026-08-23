package com.example.readtrace

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.readtrace.data.BookDatabaseHelper
import com.example.readtrace.model.Book
import com.example.readtrace.model.BookStatus
import com.example.readtrace.model.MediaType
import com.example.readtrace.model.MonthlyReadingStat
import com.example.readtrace.util.BookCsvParser
import com.example.readtrace.util.CoverImageHelper
import com.example.readtrace.util.MilestoneBadgeHelper
import java.text.DecimalFormat
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {
    private lateinit var databaseHelper: BookDatabaseHelper
    private lateinit var booksContainer: LinearLayout
    private lateinit var emptyPanel: View
    private lateinit var emptyTitle: TextView
    private lateinit var emptyBody: TextView
    private lateinit var shelfCountText: TextView
    private lateinit var statTotalValue: TextView
    private lateinit var statReadingValue: TextView
    private lateinit var statFinishedValue: TextView
    private lateinit var statAverageValue: TextView
    private lateinit var memoryPanel: View
    private lateinit var memorySubPrompt: TextView
    private lateinit var memoryBookTitle: TextView
    private lateinit var memoryBookQuote: TextView
    private lateinit var monthlyStatPanel: View
    private lateinit var monthlyChartBarsContainer: LinearLayout
    private lateinit var monthlyStatEmptyText: TextView

    private lateinit var homeBadgePanel: View
    private lateinit var homeBadgeSummary: TextView
    private lateinit var mediaTabAll: TextView
    private lateinit var mediaTabBook: TextView
    private lateinit var mediaTabMovie: TextView
    private lateinit var mediaTabGame: TextView
    private lateinit var mediaTabPodcast: TextView
    private lateinit var searchInput: EditText
    private lateinit var searchClearButton: View
    private lateinit var tagScroller: HorizontalScrollView
    private lateinit var tagGroup: LinearLayout
    private lateinit var tagAll: TextView

    private var selectedMediaType: MediaType? = null
    private var selectedStatus: BookStatus? = null
    private var searchKeyword: String = ""
    private var selectedTag: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        databaseHelper = BookDatabaseHelper(this)
        booksContainer = findViewById(R.id.booksContainer)
        emptyPanel = findViewById(R.id.emptyPanel)
        emptyTitle = findViewById(R.id.emptyTitle)
        emptyBody = findViewById(R.id.emptyBody)
        shelfCountText = findViewById(R.id.shelfCountText)
        statTotalValue = findViewById(R.id.statTotalValue)
        statReadingValue = findViewById(R.id.statReadingValue)
        statFinishedValue = findViewById(R.id.statFinishedValue)
        statAverageValue = findViewById(R.id.statAverageValue)

        memoryPanel = findViewById(R.id.memoryPanel)
        memorySubPrompt = findViewById(R.id.memorySubPrompt)
        memoryBookTitle = findViewById(R.id.memoryBookTitle)
        memoryBookQuote = findViewById(R.id.memoryBookQuote)

        monthlyStatPanel = findViewById(R.id.monthlyStatPanel)
        monthlyChartBarsContainer = findViewById(R.id.monthlyChartBarsContainer)
        monthlyStatEmptyText = findViewById(R.id.monthlyStatEmptyText)

        homeBadgePanel = findViewById(R.id.homeBadgePanel)
        homeBadgeSummary = findViewById(R.id.homeBadgeSummary)
        mediaTabAll = findViewById(R.id.mediaTabAll)
        mediaTabBook = findViewById(R.id.mediaTabBook)
        mediaTabMovie = findViewById(R.id.mediaTabMovie)
        mediaTabGame = findViewById(R.id.mediaTabGame)
        mediaTabPodcast = findViewById(R.id.mediaTabPodcast)
        searchInput = findViewById(R.id.searchInput)
        searchClearButton = findViewById(R.id.searchClearButton)
        tagScroller = findViewById(R.id.tagScroller)
        tagGroup = findViewById(R.id.tagGroup)
        tagAll = findViewById(R.id.tagAll)

        configureActions()
        configureMediaTypeFilters()
        configureStatusFilters()
        configureSearchAndTags()

        findViewById<View>(R.id.homeContent)
            .startAnimation(AnimationUtils.loadAnimation(this, R.anim.home_enter))
    }

    override fun onResume() {
        super.onResume()
        refreshBooks()
    }

    private fun configureActions() {
        val openAddBook = View.OnClickListener {
            startActivity(Intent(this, AddBookActivity::class.java))
        }
        findViewById<View>(R.id.addButton).setOnClickListener(openAddBook)
        findViewById<View>(R.id.emptyAction).setOnClickListener(openAddBook)
        findViewById<View>(R.id.importPresetButton).setOnClickListener {
            confirmImportPresetBooks()
        }
        findViewById<View>(R.id.trashButton).setOnClickListener {
            startActivity(TrashActivity.createIntent(this))
        }
        homeBadgePanel.setOnClickListener {
            startActivity(BadgesActivity.createIntent(this))
        }
    }

    private fun confirmImportPresetBooks() {
        AlertDialog.Builder(this)
            .setTitle(R.string.import_preset_confirm_title)
            .setMessage(R.string.import_preset_confirm_message)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.home_import_preset) { _, _ ->
                importPresetBooks()
            }
            .show()
    }

    private fun importPresetBooks() {
        runCatching {
            assets.open("preset_books.csv").use { inputStream ->
                val books = BookCsvParser.parse(inputStream)
                val count = databaseHelper.importBooks(books)
                if (count > 0) {
                    Toast.makeText(
                        this,
                        getString(R.string.import_success_format, count),
                        Toast.LENGTH_SHORT,
                    ).show()
                    refreshBooks()
                } else {
                    Toast.makeText(this, R.string.import_no_new_books, Toast.LENGTH_SHORT).show()
                }
            }
        }.onFailure {
            Toast.makeText(this, R.string.import_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun configureSearchAndTags() {
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim().orEmpty()
                if (searchKeyword != query) {
                    searchKeyword = query
                    searchClearButton.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE
                    refreshShelfOnly()
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        searchClearButton.setOnClickListener {
            searchInput.setText("")
        }

        tagAll.setOnClickListener {
            selectTag(null)
        }
    }

    private fun configureMediaTypeFilters() {
        mediaTabAll.setOnClickListener { selectMediaType(null) }
        mediaTabBook.setOnClickListener { selectMediaType(MediaType.BOOK) }
        mediaTabMovie.setOnClickListener { selectMediaType(MediaType.MOVIE) }
        mediaTabGame.setOnClickListener { selectMediaType(MediaType.GAME) }
        mediaTabPodcast.setOnClickListener { selectMediaType(MediaType.PODCAST) }
        updateMediaTypeTabs()
    }

    private fun selectMediaType(mediaType: MediaType?) {
        if (selectedMediaType == mediaType) return
        selectedMediaType = mediaType
        updateMediaTypeTabs()
        updateStatusChips()
        refreshShelfOnly()
    }

    private fun updateMediaTypeTabs() {
        val tabs = listOf(
            mediaTabAll to null,
            mediaTabBook to MediaType.BOOK,
            mediaTabMovie to MediaType.MOVIE,
            mediaTabGame to MediaType.GAME,
            mediaTabPodcast to MediaType.PODCAST,
        )
        tabs.forEach { (tab, type) ->
            val isSelected = selectedMediaType == type
            tab.setBackgroundResource(
                if (isSelected) R.drawable.bg_status_chip_selected else R.drawable.bg_status_chip,
            )
            tab.setTextColor(
                ContextCompat.getColor(this, if (isSelected) R.color.white else R.color.readtrace_ink),
            )
        }
    }

    private fun configureStatusFilters() {
        findViewById<View>(R.id.statusAll).setOnClickListener {
            selectStatus(null)
        }
        findViewById<View>(R.id.statusWishlist).setOnClickListener {
            selectStatus(BookStatus.WISHLIST)
        }
        findViewById<View>(R.id.statusReading).setOnClickListener {
            selectStatus(BookStatus.READING)
        }
        findViewById<View>(R.id.statusFinished).setOnClickListener {
            selectStatus(BookStatus.FINISHED)
        }
        updateStatusChips()
    }

    private fun selectStatus(status: BookStatus?) {
        if (selectedStatus == status) return
        selectedStatus = status
        updateStatusChips()
        refreshShelfOnly()
    }

    private fun selectTag(tag: String?) {
        if (selectedTag == tag) return
        selectedTag = tag
        updateTagChips()
        refreshShelfOnly()
    }

    private fun updateStatusChips() {
        val statusWishlist = findViewById<TextView>(R.id.statusWishlist)
        val statusReading = findViewById<TextView>(R.id.statusReading)
        val statusFinished = findViewById<TextView>(R.id.statusFinished)

        // 动态根据当前选中的媒介类型自适应状态筛选文案
        statusWishlist.text = selectedMediaType?.wishlistLabel ?: getString(R.string.status_wishlist)
        statusReading.text = selectedMediaType?.ongoingLabel ?: getString(R.string.status_reading)
        statusFinished.text = selectedMediaType?.finishedLabel ?: getString(R.string.status_finished)

        val chips = listOf(
            findViewById<TextView>(R.id.statusAll) to null,
            statusWishlist to BookStatus.WISHLIST,
            statusReading to BookStatus.READING,
            statusFinished to BookStatus.FINISHED,
        )
        chips.forEach { (chip, status) ->
            val isSelected = selectedStatus == status
            chip.setBackgroundResource(
                if (isSelected) {
                    R.drawable.bg_status_chip_selected
                } else {
                    R.drawable.bg_status_chip
                },
            )
            chip.setTextColor(
                ContextCompat.getColor(
                    this,
                    if (isSelected) R.color.white else R.color.readtrace_ink,
                ),
            )
        }
    }

    private fun renderBadgesSummary() {
        val badges = MilestoneBadgeHelper.calculateBadges(databaseHelper)
        val unlockedCount = badges.count { it.isUnlocked }
        val totalCount = badges.size
        homeBadgeSummary.text = getString(R.string.home_badge_summary_format, unlockedCount, totalCount)
    }

    private fun renderDynamicTags() {
        val tagList = databaseHelper.getAllUniqueTags()
        if (tagList.isEmpty()) {
            tagScroller.visibility = View.GONE
            selectedTag = null
            return
        }

        tagScroller.visibility = View.VISIBLE
        // 校验选中的标签是否还存在
        if (selectedTag != null && tagList.none { it.first == selectedTag }) {
            selectedTag = null
        }

        // 移除除了 tagAll 之外的所有标签
        while (tagGroup.childCount > 1) {
            tagGroup.removeViewAt(1)
        }

        tagList.forEach { (tag, count) ->
            val chip = TextView(this, null, 0, R.style.ReadTraceStatusChip).apply {
                text = "$tag ($count)"
                textSize = 12f
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    marginStart = dpToPx(8)
                }
                layoutParams = params
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    selectTag(if (selectedTag == tag) null else tag)
                }
            }
            tagGroup.addView(chip)
        }
        updateTagChips()
    }

    private fun updateTagChips() {
        tagAll.setBackgroundResource(
            if (selectedTag == null) R.drawable.bg_status_chip_selected else R.drawable.bg_status_chip,
        )
        tagAll.setTextColor(
            ContextCompat.getColor(this, if (selectedTag == null) R.color.white else R.color.readtrace_ink),
        )

        for (i in 1 until tagGroup.childCount) {
            val chip = tagGroup.getChildAt(i) as? TextView ?: continue
            val tagText = chip.text.toString().substringBeforeLast(" (")
            val isSelected = selectedTag == tagText
            chip.setBackgroundResource(
                if (isSelected) R.drawable.bg_status_chip_selected else R.drawable.bg_status_chip,
            )
            chip.setTextColor(
                ContextCompat.getColor(this, if (isSelected) R.color.white else R.color.readtrace_ink),
            )
        }
    }

    private fun refreshBooks() {
        val allBooks = databaseHelper.getBooks()
        updateShelfInsight(allBooks, allBooks.size)
        renderBadgesSummary()
        renderMemoryCard()
        renderMonthlyStats()
        renderDynamicTags()
        refreshShelfOnly()
    }

    private fun refreshShelfOnly() {
        val allBooks = databaseHelper.getBooks()
        val books = allBooks.filter { book ->
            val matchesMedia = selectedMediaType == null || book.mediaType == selectedMediaType
            val matchesStatus = selectedStatus == null || book.status == selectedStatus
            val matchesKeyword = searchKeyword.isEmpty() ||
                book.title.contains(searchKeyword, ignoreCase = true) ||
                (book.author?.contains(searchKeyword, ignoreCase = true) == true)
            val matchesTag = selectedTag == null || book.tags.contains(selectedTag)

            matchesMedia && matchesStatus && matchesKeyword && matchesTag
        }

        shelfCountText.text = getString(R.string.home_shelf_count_format, books.size)
        booksContainer.removeAllViews()

        if (books.isEmpty()) {
            booksContainer.visibility = View.GONE
            emptyPanel.visibility = View.VISIBLE
            val isFiltered = selectedMediaType != null || selectedStatus != null || searchKeyword.isNotEmpty() || selectedTag != null
            emptyTitle.setText(
                if (searchKeyword.isNotEmpty()) {
                    R.string.search_no_results
                } else if (isFiltered) {
                    R.string.empty_filter_title
                } else {
                    R.string.empty_shelf_title
                },
            )
            emptyBody.setText(
                if (isFiltered) R.string.empty_filter_body else R.string.empty_shelf_body,
            )
            emptyPanel.startAnimation(
                AnimationUtils.loadAnimation(this, R.anim.card_enter_delayed),
            )
            return
        }

        emptyPanel.visibility = View.GONE
        booksContainer.visibility = View.VISIBLE
        books.forEachIndexed { index, book ->
            val card = createBookCard(book)
            booksContainer.addView(card)
            animateBookCard(card, index)
        }
    }

    private fun renderMemoryCard() {
        val memoryResult = databaseHelper.getMemoryBook()
        if (memoryResult == null) {
            memoryPanel.visibility = View.GONE
            return
        }
        val (book, prompt) = memoryResult
        memoryPanel.visibility = View.VISIBLE
        memorySubPrompt.text = prompt
        val authorText = book.author?.let { " · $it" } ?: ""
        memoryBookTitle.text = "《${book.title}》$authorText"

        val quoteText = book.shortComment?.trim()?.takeIf { it.isNotEmpty() }
            ?: book.review?.trim()?.takeIf { it.isNotEmpty() }
            ?: getString(R.string.today_reflection_body)
        memoryBookQuote.text = quoteText
        memoryPanel.setOnClickListener {
            openBookDetail(memoryPanel, book.id)
        }
    }

    private fun renderMonthlyStats() {
        val stats = databaseHelper.getMonthlyFinishedStats(6)
        monthlyChartBarsContainer.removeAllViews()
        if (stats.isEmpty()) {
            monthlyChartBarsContainer.visibility = View.GONE
            monthlyStatEmptyText.visibility = View.VISIBLE
            return
        }

        monthlyStatEmptyText.visibility = View.GONE
        monthlyChartBarsContainer.visibility = View.VISIBLE

        val chronologicStats = stats.reversed()
        val maxCount = chronologicStats.maxOf { it.count }.coerceAtLeast(1)

        chronologicStats.forEach { stat ->
            val col = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
            }

            val countText = TextView(this).apply {
                text = stat.count.toString()
                textSize = 11f
                gravity = Gravity.CENTER
                setTextColor(ContextCompat.getColor(context, R.color.readtrace_accent))
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            col.addView(countText)

            val barHeightDp = (stat.count.toFloat() / maxCount * 55f + 8f).roundToInt()
            val bar = View(this).apply {
                setBackgroundResource(R.drawable.bg_stat_bar)
                val params = LinearLayout.LayoutParams(
                    dpToPx(18),
                    dpToPx(barHeightDp),
                ).apply {
                    topMargin = dpToPx(4)
                    bottomMargin = dpToPx(6)
                }
                layoutParams = params
            }
            col.addView(bar)

            val monthLabel = if (stat.month.length >= 7) {
                stat.month.substring(5, 7) + "月"
            } else {
                stat.month
            }
            val monthText = TextView(this).apply {
                text = monthLabel
                textSize = 10f
                gravity = Gravity.CENTER
                setTextColor(ContextCompat.getColor(context, R.color.readtrace_muted))
            }
            col.addView(monthText)

            monthlyChartBarsContainer.addView(col)
        }
    }

    private fun dpToPx(value: Int): Int =
        (value * resources.displayMetrics.density).roundToInt()

    private fun createBookCard(book: Book): View {
        val card = LayoutInflater.from(this)
            .inflate(R.layout.item_book_card, booksContainer, false)
        val coverImageView = card.findViewById<ImageView>(R.id.bookCardCoverImage)
        CoverImageHelper.loadCover(coverImageView, book.coverUrl)

        card.findViewById<TextView>(R.id.bookCardTitle).text = book.title
        card.findViewById<TextView>(R.id.bookCardAuthor).text =
            book.author ?: getString(R.string.unknown_author)

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
            openBookDetail(card, book.id)
        }

        return card
    }

    private fun updateShelfInsight(allBooks: List<Book>, visibleCount: Int) {
        statTotalValue.text = allBooks.size.toString()
        statReadingValue.text = allBooks.count { it.status == BookStatus.READING }.toString()
        statFinishedValue.text = allBooks.count { it.status == BookStatus.FINISHED }.toString()
        val ratedBooks = allBooks.mapNotNull { it.rating }
        statAverageValue.text =
            if (ratedBooks.isEmpty()) {
                getString(R.string.home_average_empty)
            } else {
                RATING_FORMAT.format(ratedBooks.average())
            }
        shelfCountText.text = selectedStatus?.let {
            getString(R.string.home_shelf_filtered_count_format, it.displayName, visibleCount)
        } ?: getString(R.string.home_shelf_count_format, visibleCount)
    }

    private fun openBookDetail(card: View, bookId: Long) {
        card.isEnabled = false
        card.animate()
            .scaleX(0.985f)
            .scaleY(0.985f)
            .setDuration(90L)
            .withEndAction {
                card.scaleX = 1f
                card.scaleY = 1f
                card.isEnabled = true
                startActivity(BookDetailActivity.createIntent(this, bookId))
            }
            .start()
    }

    private fun animateBookCard(card: View, index: Int) {
        card.alpha = 0f
        card.translationY = 28f
        card.animate()
            .alpha(1f)
            .translationY(0f)
            .setStartDelay(index.coerceAtMost(6) * 55L)
            .setDuration(460L)
            .start()
    }

    override fun onDestroy() {
        databaseHelper.close()
        super.onDestroy()
    }

    companion object {
        private val RATING_FORMAT = DecimalFormat("0.#")
    }
}
