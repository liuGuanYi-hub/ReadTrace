package com.example.readtrace

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
import android.view.animation.AnimationUtils
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.readtrace.data.BookDatabaseHelper
import com.example.readtrace.model.Book
import com.example.readtrace.model.BookStatus
import com.example.readtrace.model.MediaType
import com.example.readtrace.util.CoverImageHelper
import com.example.readtrace.util.ViewAnimationHelper
import com.example.readtrace.widget.MindprintRadarView
import java.text.DecimalFormat
import kotlin.math.roundToInt

class MediaHubActivity : AppCompatActivity() {

    private lateinit var databaseHelper: BookDatabaseHelper
    private lateinit var targetMediaType: MediaType

    private lateinit var hubTitle: TextView
    private lateinit var hubSubtitle: TextView
    private lateinit var btnHubAdd: TextView
    private lateinit var btnHubToggleViewMode: TextView
    private lateinit var btnHubSpecialFeature: TextView
    private lateinit var btnHubPassport: TextView
    private lateinit var btnHubExportScroll: TextView

    private lateinit var hubSearchInput: EditText
    private lateinit var hubSearchClearButton: View
    private lateinit var hubStatusAll: TextView
    private lateinit var hubStatusReading: TextView
    private lateinit var hubStatusFinished: TextView
    private lateinit var hubStatusWishlist: TextView
    private lateinit var hubTagScroller: HorizontalScrollView
    private lateinit var hubTagGroup: LinearLayout
    private lateinit var hubTagAll: TextView

    private lateinit var hubSectionTitle: TextView
    private lateinit var hubCountText: TextView
    private lateinit var hubBooksContainer: LinearLayout
    private lateinit var hubEmptyPanel: View
    private lateinit var hubEmptyTitle: TextView
    private lateinit var hubEmptyBody: TextView

    private var selectedStatus: BookStatus? = null
    private var searchKeyword: String = ""
    private var selectedTag: String? = null
    private var isGridView: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_media_hub)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.mediaHubRoot)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val rawMedia = intent.getStringExtra(EXTRA_MEDIA_TYPE)
        targetMediaType = MediaType.fromDatabaseValue(rawMedia) ?: MediaType.BOOK

        databaseHelper = BookDatabaseHelper(this)

        val prefs = getSharedPreferences("readtrace_prefs", Context.MODE_PRIVATE)
        isGridView = prefs.getBoolean("pref_is_grid_view_hub_${targetMediaType.databaseValue}", false)

        initViews()
        setupHeaderTheme()
        configureSearchAndFilters()

        findViewById<View>(R.id.hubContent)
            .startAnimation(AnimationUtils.loadAnimation(this, R.anim.home_enter))
    }

    override fun onResume() {
        super.onResume()
        refreshData()
    }

    private fun initViews() {
        hubTitle = findViewById(R.id.hubTitle)
        hubSubtitle = findViewById(R.id.hubSubtitle)
        btnHubAdd = findViewById(R.id.btnHubAdd)
        btnHubToggleViewMode = findViewById(R.id.btnHubToggleViewMode)
        btnHubSpecialFeature = findViewById(R.id.btnHubSpecialFeature)
        btnHubPassport = findViewById(R.id.btnHubPassport)
        btnHubExportScroll = findViewById(R.id.btnHubExportScroll)

        hubSearchInput = findViewById(R.id.hubSearchInput)
        hubSearchClearButton = findViewById(R.id.hubSearchClearButton)
        hubStatusAll = findViewById(R.id.hubStatusAll)
        hubStatusReading = findViewById(R.id.hubStatusReading)
        hubStatusFinished = findViewById(R.id.hubStatusFinished)
        hubStatusWishlist = findViewById(R.id.hubStatusWishlist)
        hubTagScroller = findViewById(R.id.hubTagScroller)
        hubTagGroup = findViewById(R.id.hubTagGroup)
        hubTagAll = findViewById(R.id.hubTagAll)

        hubSectionTitle = findViewById(R.id.hubSectionTitle)
        hubCountText = findViewById(R.id.hubCountText)
        hubBooksContainer = findViewById(R.id.hubBooksContainer)
        hubEmptyPanel = findViewById(R.id.hubEmptyPanel)
        hubEmptyTitle = findViewById(R.id.hubEmptyTitle)
        hubEmptyBody = findViewById(R.id.hubEmptyBody)

        findViewById<View>(R.id.btnHubBack).setOnClickListener { finish() }

        btnHubAdd.setOnClickListener {
            val intent = Intent(this, AddBookActivity::class.java).apply {
                putExtra("extra_default_media_type", targetMediaType.databaseValue)
            }
            startActivity(intent)
        }

        btnHubToggleViewMode.setOnClickListener {
            isGridView = !isGridView
            getSharedPreferences("readtrace_prefs", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("pref_is_grid_view_hub_${targetMediaType.databaseValue}", isGridView)
                .apply()
            updateViewModeButton()
            refreshShelfOnly()
        }

        listOfNotNull(
            findViewById(R.id.btnHubBack),
            btnHubAdd,
            btnHubToggleViewMode,
            btnHubSpecialFeature,
            btnHubPassport,
            btnHubExportScroll,
        ).forEach { ViewAnimationHelper.attachSpringTouch(it) }
    }

    private fun setupHeaderTheme() {
        when (targetMediaType) {
            MediaType.BOOK -> {
                hubTitle.text = "📚 文学书房"
                hubStatusReading.text = "在读"
                hubStatusFinished.text = "读完"
                hubStatusWishlist.text = "想读"
                btnHubSpecialFeature.text = "📖 3D 翻书"
                btnHubSpecialFeature.setOnClickListener {
                    val firstBook = databaseHelper.getBooks().firstOrNull { it.mediaType == MediaType.BOOK }
                    if (firstBook != null) {
                        startActivity(com.example.readtrace.reader.Book3DReaderActivity.createIntent(this, firstBook.id))
                    } else {
                        Toast.makeText(this, "书房暂无藏书", Toast.LENGTH_SHORT).show()
                    }
                }
                btnHubPassport.visibility = View.GONE
            }
            MediaType.ANIME -> {
                hubTitle.text = "🌸 追番殿堂"
                hubStatusReading.text = "追番中"
                hubStatusFinished.text = "已补完"
                hubStatusWishlist.text = "想追"
                btnHubSpecialFeature.text = "📜 编年画卷"
                btnHubSpecialFeature.setOnClickListener {
                    startActivity(Intent(this, AnimeTimelineScrollActivity::class.java))
                }
                btnHubPassport.text = "🛂 追番签证"
                btnHubPassport.setOnClickListener {
                    startActivity(CulturalPassportActivity.createIntent(this, MediaType.ANIME))
                }
            }
            MediaType.MOVIE -> {
                hubTitle.text = "🎬 光影剧场"
                hubStatusReading.text = "在看"
                hubStatusFinished.text = "已看"
                hubStatusWishlist.text = "想看"
                btnHubSpecialFeature.text = "🎟️ 电影票根"
                btnHubSpecialFeature.setOnClickListener {
                    val firstMovie = databaseHelper.getBooks().firstOrNull { it.mediaType == MediaType.MOVIE }
                    if (firstMovie != null) {
                        startActivity(MovieTicketPosterActivity.createIntent(this, firstMovie.id))
                    } else {
                        Toast.makeText(this, "剧场暂无电影记录", Toast.LENGTH_SHORT).show()
                    }
                }
                btnHubPassport.visibility = View.GONE
            }
            MediaType.GAME -> {
                hubTitle.text = "🎮 游戏宝库"
                hubStatusReading.text = "游玩中"
                hubStatusFinished.text = "白金通关"
                hubStatusWishlist.text = "想玩"
                btnHubSpecialFeature.text = "🕹️ 全息卡带"
                btnHubSpecialFeature.setOnClickListener {
                    val firstGame = databaseHelper.getBooks().firstOrNull { it.mediaType == MediaType.GAME }
                    if (firstGame != null) {
                        startActivity(GameCartridgePosterActivity.createIntent(this, firstGame.id))
                    } else {
                        Toast.makeText(this, "游戏库暂无作品", Toast.LENGTH_SHORT).show()
                    }
                }
                btnHubPassport.text = "🛂 通关签证"
                btnHubPassport.setOnClickListener {
                    startActivity(CulturalPassportActivity.createIntent(this, MediaType.GAME))
                }
            }
            MediaType.PODCAST -> {
                hubTitle.text = "🎙️ 随心播客"
                hubStatusReading.text = "收听中"
                hubStatusFinished.text = "已听完"
                hubStatusWishlist.text = "想听"
                btnHubSpecialFeature.visibility = View.GONE
                btnHubPassport.visibility = View.GONE
            }
        }

        btnHubExportScroll.setOnClickListener {
            Toast.makeText(this, "正在生成长图...", Toast.LENGTH_SHORT).show()
        }
        updateViewModeButton()
    }

    private fun updateViewModeButton() {
        btnHubToggleViewMode.text = if (isGridView) "📋 列表" else "🍱 双列"
    }

    private fun configureSearchAndFilters() {
        hubSearchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim().orEmpty()
                if (searchKeyword != query) {
                    searchKeyword = query
                    hubSearchClearButton.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE
                    refreshShelfOnly()
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        hubSearchClearButton.setOnClickListener {
            hubSearchInput.setText("")
        }

        hubStatusAll.setOnClickListener { selectStatus(null) }
        hubStatusReading.setOnClickListener { selectStatus(BookStatus.READING) }
        hubStatusFinished.setOnClickListener { selectStatus(BookStatus.FINISHED) }
        hubStatusWishlist.setOnClickListener { selectStatus(BookStatus.WISHLIST) }

        hubTagAll.setOnClickListener { selectTag(null) }
    }

    private fun selectStatus(status: BookStatus?) {
        if (selectedStatus == status) return
        selectedStatus = status
        updateStatusChips()
        refreshShelfOnly()
    }

    private fun updateStatusChips() {
        val chips = listOf(
            hubStatusAll to (selectedStatus == null),
            hubStatusReading to (selectedStatus == BookStatus.READING),
            hubStatusFinished to (selectedStatus == BookStatus.FINISHED),
            hubStatusWishlist to (selectedStatus == BookStatus.WISHLIST),
        )
        chips.forEach { (chip, isSelected) ->
            chip.setBackgroundResource(if (isSelected) R.drawable.bg_status_chip_selected else R.drawable.bg_status_chip)
            chip.setTextColor(ContextCompat.getColor(this, if (isSelected) R.color.white else R.color.readtrace_ink))
        }
    }

    private fun selectTag(tag: String?) {
        if (selectedTag == tag) return
        selectedTag = tag
        updateTagChips()
        refreshShelfOnly()
    }

    private fun refreshData() {
        val allTargetBooks = databaseHelper.getBooks().filter { it.mediaType == targetMediaType }
        val total = allTargetBooks.size
        val finished = allTargetBooks.count { it.status == BookStatus.FINISHED }
        val reading = allTargetBooks.count { it.status == BookStatus.READING }
        val wishlist = allTargetBooks.count { it.status == BookStatus.WISHLIST }

        hubSubtitle.text = "收录 $total 部作品 · $finished 部已完成 · $reading 部进行中 · $wishlist 部在愿望单"
        renderDynamicTags(allTargetBooks)
        refreshShelfOnly()
    }

    private fun renderDynamicTags(allTargetBooks: List<Book>) {
        val tagCounts = mutableMapOf<String, Int>()
        allTargetBooks.forEach { book ->
            book.tags.forEach { tag ->
                tagCounts[tag] = (tagCounts[tag] ?: 0) + 1
            }
        }
        val tagList = tagCounts.toList().sortedByDescending { it.second }

        if (tagList.isEmpty()) {
            hubTagScroller.visibility = View.GONE
            selectedTag = null
            return
        }

        hubTagScroller.visibility = View.VISIBLE
        while (hubTagGroup.childCount > 1) {
            hubTagGroup.removeViewAt(1)
        }

        tagList.forEach { (tag, count) ->
            val chip = TextView(this, null, 0, R.style.ReadTraceStatusChip).apply {
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
            hubTagGroup.addView(chip)
        }
        updateTagChips()
    }

    private fun updateTagChips() {
        hubTagAll.setBackgroundResource(if (selectedTag == null) R.drawable.bg_status_chip_selected else R.drawable.bg_status_chip)
        hubTagAll.setTextColor(ContextCompat.getColor(this, if (selectedTag == null) R.color.white else R.color.readtrace_ink))

        for (i in 1 until hubTagGroup.childCount) {
            val chip = hubTagGroup.getChildAt(i) as? TextView ?: continue
            val tagText = chip.text.toString().substringBeforeLast(" (")
            val isSelected = selectedTag == tagText
            chip.setBackgroundResource(if (isSelected) R.drawable.bg_status_chip_selected else R.drawable.bg_status_chip)
            chip.setTextColor(ContextCompat.getColor(this, if (isSelected) R.color.white else R.color.readtrace_ink))
        }
    }

    private fun refreshShelfOnly() {
        val allBooks = databaseHelper.getBooks().filter { it.mediaType == targetMediaType }
        val books = allBooks.filter { book ->
            val matchesStatus = selectedStatus == null || book.status == selectedStatus
            val matchesKeyword = searchKeyword.isEmpty() ||
                book.title.contains(searchKeyword, ignoreCase = true) ||
                (book.author?.contains(searchKeyword, ignoreCase = true) == true) ||
                (book.category?.contains(searchKeyword, ignoreCase = true) == true)
            val matchesTag = selectedTag == null || book.tags.contains(selectedTag)

            matchesStatus && matchesKeyword && matchesTag
        }

        hubCountText.text = "共 ${books.size} 部"
        hubBooksContainer.removeAllViews()

        if (books.isEmpty()) {
            hubBooksContainer.visibility = View.GONE
            hubEmptyPanel.clearAnimation()
            hubEmptyPanel.visibility = View.VISIBLE
            hubEmptyPanel.alpha = 0f
            hubEmptyPanel.animate().alpha(1f).setDuration(200).start()
            return
        }

        hubEmptyPanel.clearAnimation()
        hubEmptyPanel.visibility = View.GONE
        hubBooksContainer.visibility = View.VISIBLE

        if (!isGridView) {
            books.forEachIndexed { index, book ->
                val card = createBookCard(book)
                hubBooksContainer.addView(card)
                if (index < 12) ViewAnimationHelper.staggerFadeIn(card, index)
            }
        } else {
            val rows = books.chunked(2)
            rows.forEachIndexed { rowIndex, pair ->
                val rowLayout = LinearLayout(this).apply {
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
                    val emptySpace = View(this).apply {
                        layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
                    }
                    rowLayout.addView(emptySpace)
                }

                hubBooksContainer.addView(rowLayout)
                if (rowIndex < 6) ViewAnimationHelper.staggerFadeIn(rowLayout, rowIndex)
            }
        }
    }

    private fun createBookCard(book: Book): View {
        val card = LayoutInflater.from(this).inflate(R.layout.item_book_card, hubBooksContainer, false)
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
            startActivity(BookDetailActivity.createIntent(this, book.id))
        }
        card.setOnLongClickListener {
            showHologramPeekDialog(book)
            true
        }
        ViewAnimationHelper.attachSpringTouch(card, 0.97f)
        return card
    }

    private fun createBookGridCard(book: Book): View {
        val card = LayoutInflater.from(this).inflate(R.layout.item_book_grid_card, null, false)
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
            startActivity(BookDetailActivity.createIntent(this, book.id))
        }
        card.setOnLongClickListener {
            showHologramPeekDialog(book)
            true
        }
        ViewAnimationHelper.attachSpringTouch(card, 0.96f)
        return card
    }

    private fun showHologramPeekDialog(book: Book) {
        val dialog = Dialog(this)
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
            startActivity(BookDetailActivity.createIntent(this, book.id))
        }
        view.findViewById<View>(R.id.peekActionTimer).setOnClickListener { dialog.dismiss() }
        view.findViewById<View>(R.id.peekActionPoster).setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    private fun dpToPx(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    override fun onDestroy() {
        databaseHelper.close()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_MEDIA_TYPE = "extra_media_type"
        private val RATING_FORMAT = DecimalFormat("0.#")

        fun createIntent(context: Context, mediaType: MediaType): Intent {
            return Intent(context, MediaHubActivity::class.java).apply {
                putExtra(EXTRA_MEDIA_TYPE, mediaType.databaseValue)
            }
        }
    }
}
