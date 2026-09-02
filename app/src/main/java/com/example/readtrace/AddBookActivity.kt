package com.example.readtrace

import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.readtrace.data.BookDatabaseHelper
import com.example.readtrace.model.Book
import com.example.readtrace.model.BookStatus
import com.example.readtrace.model.MediaType
import com.example.readtrace.util.CoverImageHelper
import com.example.readtrace.util.FloatingBack
import com.example.readtrace.util.HapticFeedbackEngine
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class AddBookActivity : AppCompatActivity() {
    private lateinit var databaseHelper: BookDatabaseHelper
    private lateinit var formTitle: TextView
    private lateinit var formSubtitle: TextView
    private lateinit var titleLabel: TextView
    private lateinit var titleInput: EditText
    private lateinit var authorLabel: TextView
    private lateinit var authorInput: EditText
    private lateinit var coverUrlInput: EditText
    private lateinit var categoryLabel: TextView
    private lateinit var categoryChipGroup: com.example.readtrace.widget.FlowLayout
    private var selectedCategory: String? = null
    private lateinit var sectionRecordTitle: TextView
    private lateinit var statusLabel: TextView
    private lateinit var chipStatusWishlist: TextView
    private lateinit var chipStatusReading: TextView
    private lateinit var chipStatusFinished: TextView
    private lateinit var chipStatusPaused: TextView
    private lateinit var chipStatusDropped: TextView
    private var selectedStatus: BookStatus = BookStatus.READING
    private lateinit var starViews: List<TextView>
    private lateinit var starHint: TextView
    private var selectedScore10: Double = 8.0 // 默认 8.0 分 (10 分制)
    private lateinit var tagsInput: EditText
    private lateinit var sectionThoughtsTitle: TextView
    private lateinit var shortCommentLabel: TextView
    private lateinit var shortCommentInput: EditText
    private lateinit var reviewLabel: TextView
    private lateinit var reviewInput: EditText
    private lateinit var cardCollection: View
    private lateinit var sectionCollectionTitle: TextView
    private lateinit var buyChannelInput: EditText
    private lateinit var shelfLocationInput: EditText
    private lateinit var bindingTypeLabel: TextView
    private lateinit var bindingTypeInput: EditText
    private lateinit var buyPriceInput: EditText
    private lateinit var startDateLabel: TextView
    private lateinit var startDateInput: TextView
    private lateinit var finishDateLabel: TextView
    private lateinit var finishDateInput: TextView
    private lateinit var saveButton: TextView

    private lateinit var mediaTypeBook: TextView
    private lateinit var mediaTypeAnime: TextView
    private lateinit var mediaTypeMovie: TextView
    private lateinit var mediaTypeGame: TextView
    private lateinit var mediaTypeMusic: TextView

    private lateinit var coverPickerContainer: View
    private lateinit var coverPreviewImage: ImageView
    private lateinit var coverStatusText: TextView
    private lateinit var pickCoverButton: View
    private lateinit var removeCoverButton: View

    private var selectedMediaType: MediaType = MediaType.BOOK
    private var startDate: LocalDate? = null
    private var finishDate: LocalDate? = null
    private var editingBookId: Long = NO_BOOK_ID
    private var currentCoverPath: String? = null
    private var initialCoverPath: String? = null

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val savedPath = CoverImageHelper.cropAndSaveCover(this, uri)
            if (savedPath != null) {
                currentCoverPath = savedPath
                coverUrlInput.setText(savedPath)
                updateCoverPreview()
                Toast.makeText(this, R.string.cover_selected_success, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, R.string.cover_selected_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_add_book)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.addBookRoot)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        databaseHelper = BookDatabaseHelper.getInstance(this)
        editingBookId = intent.getLongExtra(EXTRA_BOOK_ID, NO_BOOK_ID)
        // 发现页导入入口（v4.2.14）：携带当前媒介类型进入发现页，联网搜索热门作品一键导入
        findViewById<View>(R.id.discoverEntry).setOnClickListener {
            HapticFeedbackEngine.lightClick(this)
            DiscoverActivity.start(this, selectedMediaType)
        }
        bindViews()
        val extraType = intent.getStringExtra("extra_media_type") ?: intent.getStringExtra("extra_default_media_type")
        if (extraType != null) {
            val parsed = runCatching { MediaType.valueOf(extraType) }.getOrNull()
                ?: MediaType.fromDatabaseValue(extraType)
            if (parsed != null) {
                selectedMediaType = parsed
            }
        }
        updateMediaTypeChips()
        updateCreatorFields()
        updateStatusChipsText()
        updateStatusSelectionUI()
        configureFormMode()
        if (savedInstanceState == null) {
            loadBookForEditing()
            if (isFinishing) return
        } else {
            restoreDates(savedInstanceState)
        }
        configureActions()

        findViewById<View>(R.id.addBookContent)
            .startAnimation(AnimationUtils.loadAnimation(this, R.anim.home_enter))
    }

    private fun bindViews() {
        formTitle = findViewById(R.id.formTitle)
        formSubtitle = findViewById(R.id.formSubtitle)
        titleLabel = findViewById(R.id.titleLabel)
        titleInput = findViewById(R.id.titleInput)
        authorLabel = findViewById(R.id.authorLabel)
        authorInput = findViewById(R.id.authorInput)
        coverUrlInput = findViewById(R.id.coverUrlInput)
        categoryLabel = findViewById(R.id.categoryLabel)
        categoryChipGroup = findViewById(R.id.categoryChipGroup)
        sectionRecordTitle = findViewById(R.id.sectionRecordTitle)
        statusLabel = findViewById(R.id.statusLabel)
        chipStatusWishlist = findViewById(R.id.chipStatusWishlist)
        chipStatusReading = findViewById(R.id.chipStatusReading)
        chipStatusFinished = findViewById(R.id.chipStatusFinished)
        chipStatusPaused = findViewById(R.id.chipStatusPaused)
        chipStatusDropped = findViewById(R.id.chipStatusDropped)
        starViews = listOf(
            findViewById(R.id.star1),
            findViewById(R.id.star2),
            findViewById(R.id.star3),
            findViewById(R.id.star4),
            findViewById(R.id.star5),
        )
        starHint = findViewById(R.id.starHint)
        findViewById<View>(R.id.btnOpenDimensionalScoring)?.setOnClickListener {
            com.example.readtrace.util.HapticFeedbackEngine.lightClick(this)
            com.example.readtrace.ui.DimensionalScoringBottomSheet.show(
                activity = this,
                workTitle = titleInput.text.toString().trim().ifBlank { "作品" },
                mediaType = selectedMediaType,
                currentScore = selectedScore10,
            ) { newScore ->
                selectedScore10 = newScore
                renderStarSelection()
            }
        }
        setupStarRating()
        applyCompactMode()
        tagsInput = findViewById(R.id.tagsInput)
        sectionThoughtsTitle = findViewById(R.id.sectionThoughtsTitle)
        shortCommentLabel = findViewById(R.id.shortCommentLabel)
        shortCommentInput = findViewById(R.id.shortCommentInput)
        reviewLabel = findViewById(R.id.reviewLabel)
        reviewInput = findViewById(R.id.reviewInput)
        cardCollection = findViewById(R.id.cardCollection)
        sectionCollectionTitle = findViewById(R.id.sectionCollectionTitle)
        buyChannelInput = findViewById(R.id.buyChannelInput)
        shelfLocationInput = findViewById(R.id.shelfLocationInput)
        bindingTypeLabel = findViewById(R.id.bindingTypeLabel)
        bindingTypeInput = findViewById(R.id.bindingTypeInput)
        buyPriceInput = findViewById(R.id.buyPriceInput)
        startDateLabel = findViewById(R.id.startDateLabel)
        startDateInput = findViewById(R.id.startDateInput)
        finishDateLabel = findViewById(R.id.finishDateLabel)
        finishDateInput = findViewById(R.id.finishDateInput)
        saveButton = findViewById(R.id.saveButton)

        mediaTypeBook = findViewById(R.id.mediaTypeBook)
        mediaTypeAnime = findViewById(R.id.mediaTypeAnime)
        mediaTypeMovie = findViewById(R.id.mediaTypeMovie)
        mediaTypeGame = findViewById(R.id.mediaTypeGame)
        mediaTypeMusic = findViewById(R.id.mediaTypeMusic)

        coverPickerContainer = findViewById(R.id.coverPickerContainer)
        coverPreviewImage = findViewById(R.id.coverPreviewImage)
        coverStatusText = findViewById(R.id.coverStatusText)
        pickCoverButton = findViewById(R.id.pickCoverButton)
        removeCoverButton = findViewById(R.id.removeCoverButton)

        setupTagCloud()
    }

    private fun updateCoverPreview() {
        if (!currentCoverPath.isNullOrBlank()) {
            CoverImageHelper.loadCover(coverPreviewImage, currentCoverPath)
            coverStatusText.setText(R.string.action_cover_change)
            removeCoverButton.visibility = View.VISIBLE
        } else {
            coverPreviewImage.visibility = View.GONE
            coverStatusText.setText(R.string.action_pick_cover)
            removeCoverButton.visibility = View.GONE
        }
    }

    private fun selectMediaType(mediaType: MediaType) {
        if (selectedMediaType == mediaType) return
        selectedMediaType = mediaType
        updateMediaTypeChips()
        updateCreatorFields()
        updateStatusChipsText()
        updateStatusSelectionUI()
        setupTagCloud()
    }

    private fun updateMediaTypeChips() {
        val chips = listOf(
            mediaTypeBook to MediaType.BOOK,
            mediaTypeAnime to MediaType.ANIME,
            mediaTypeMovie to MediaType.MOVIE,
            mediaTypeGame to MediaType.GAME,
            mediaTypeMusic to MediaType.MUSIC,
        )
        chips.forEach { (chip, type) ->
            val isSelected = selectedMediaType == type
            chip.setBackgroundResource(
                if (isSelected) R.drawable.bg_status_chip_selected else R.drawable.bg_status_chip,
            )
            chip.setTextColor(
                ContextCompat.getColor(this, if (isSelected) R.color.white else R.color.readtrace_ink),
            )
        }
    }

    private fun updateCreatorFields() {
        // 1. 作者与标题
        authorLabel.text = selectedMediaType.creatorLabel
        authorInput.hint = selectedMediaType.creatorHint
        titleLabel.text = when (selectedMediaType) {
            MediaType.BOOK -> "书名 *"
            MediaType.ANIME -> "番剧名称 *"
            MediaType.MOVIE -> "影视名称 *"
            MediaType.GAME -> "游戏名称 *"
            MediaType.MUSIC -> "曲目 / 专辑名称 *"
        }
        titleInput.hint = when (selectedMediaType) {
            MediaType.BOOK -> "例如：百年孤独、小王子"
            MediaType.ANIME -> "例如：新世纪福音战士、葬送的芙莉莲"
            MediaType.MOVIE -> "例如：星际穿越、千与千寻"
            MediaType.GAME -> "例如：艾尔登法环、黑神话：悟空"
            MediaType.MUSIC -> "例如：真夜中、夜鹿"
        }

        // 2. 分类
        categoryLabel.text = when (selectedMediaType) {
            MediaType.BOOK -> "书籍分类"
            MediaType.ANIME -> "番剧类型"
            MediaType.MOVIE -> "影视类型"
            MediaType.GAME -> "游戏类型"
            MediaType.MUSIC -> "曲风 / 流派"
        }
        // 分类标签随媒介类型刷新（点选即生效）
        rebuildCategoryChips()

        // 3. 记录与状态区域（严格区分阅读/追番/观影/游玩/聆听）
        sectionRecordTitle.text = when (selectedMediaType) {
            MediaType.BOOK -> "阅读记录"
            MediaType.ANIME -> "追番记录"
            MediaType.MOVIE -> "观影记录"
            MediaType.GAME -> "游玩记录"
            MediaType.MUSIC -> "聆听记录"
        }
        statusLabel.text = when (selectedMediaType) {
            MediaType.BOOK -> "阅读状态"
            MediaType.ANIME -> "追番状态"
            MediaType.MOVIE -> "观影状态"
            MediaType.GAME -> "游玩状态"
            MediaType.MUSIC -> "聆听状态"
        }
        startDateLabel.text = when (selectedMediaType) {
            MediaType.BOOK -> "开始阅读"
            MediaType.ANIME -> "开始追番"
            MediaType.MOVIE -> "观影日期"
            MediaType.GAME -> "开始游玩"
            MediaType.MUSIC -> "初次聆听"
        }
        finishDateLabel.text = when (selectedMediaType) {
            MediaType.BOOK -> "完成阅读"
            MediaType.ANIME -> "追完日期"
            MediaType.MOVIE -> "重温 / 记录日期"
            MediaType.GAME -> "通关 / 封盘日期"
            MediaType.MUSIC -> "常听 / 收藏日期"
        }

        // 4. 感悟与长评区域
        sectionThoughtsTitle.text = when (selectedMediaType) {
            MediaType.BOOK -> "留下感受"
            MediaType.ANIME -> "追番感受"
            MediaType.MOVIE -> "观影感受"
            MediaType.GAME -> "游玩感受"
            MediaType.MUSIC -> "聆听感受"
        }
        shortCommentLabel.text = when (selectedMediaType) {
            MediaType.BOOK -> "简短评价"
            MediaType.ANIME -> "名台词 / 短评"
            MediaType.MOVIE -> "金句 / 短评"
            MediaType.GAME -> "通关短评"
            MediaType.MUSIC -> "一句话听感"
        }
        shortCommentInput.hint = when (selectedMediaType) {
            MediaType.BOOK -> "一句话记下这本书带给你的触动"
            MediaType.ANIME -> "一句话记下这部番剧带给你的触动或经典名台词"
            MediaType.MOVIE -> "一句话记下这部电影的感动瞬间或高光台词"
            MediaType.GAME -> "一句话记下这款游戏的通关心得或世界观触动"
            MediaType.MUSIC -> "一句话记下这首歌或这张专辑带给你的共鸣"
        }
        reviewLabel.text = when (selectedMediaType) {
            MediaType.BOOK -> "读后感"
            MediaType.ANIME -> "追番感悟 / 漫评"
            MediaType.MOVIE -> "观影感悟 / 影评"
            MediaType.GAME -> "游玩心得 / 游评"
            MediaType.MUSIC -> "聆听感悟 / 乐评"
        }
        reviewInput.hint = when (selectedMediaType) {
            MediaType.BOOK -> "写下详细的长篇读后感或深度书评..."
            MediaType.ANIME -> "写下详细的漫评、剧情剖析或角色感悟..."
            MediaType.MOVIE -> "写下详细的影评、镜头美学或观后感..."
            MediaType.GAME -> "写下详细的游玩心得、剧情体验或关卡设计评价..."
            MediaType.MUSIC -> "写下详细的乐评、编曲细节或旋律记忆..."
        }
        tagsInput.hint = when (selectedMediaType) {
            MediaType.BOOK -> "用逗号分隔，如：治愈，成长，经典"
            MediaType.ANIME -> "用逗号分隔，如：神作，催泪，作画爆炸"
            MediaType.MOVIE -> "用逗号分隔，如：反转，奥斯卡，视觉震撼"
            MediaType.GAME -> "用逗号分隔，如：白金，剧情向，第九艺术"
            MediaType.MUSIC -> "用逗号分隔，如：循环单曲，失眠必听，Live现场"
        }

        // 5. 实体馆藏与藏本印记 (仅在书籍模式下展示)
        if (selectedMediaType == MediaType.BOOK) {
            cardCollection.visibility = View.VISIBLE
            sectionCollectionTitle.text = "💰 实体馆藏与藏本印记 (选填)"
            bindingTypeLabel.text = "装帧版次"
            bindingTypeInput.hint = "如：精装锁线 / 平装"
            shelfLocationInput.hint = "如：书架第 2 层 A 区"
        } else {
            cardCollection.visibility = View.GONE
        }

        configureFormMode()
    }

    private fun configureFormMode() {
        if (editingBookId != NO_BOOK_ID) {
            formTitle.text = when (selectedMediaType) {
                MediaType.BOOK -> "编辑书籍"
                MediaType.ANIME -> "编辑番剧"
                MediaType.MOVIE -> "编辑影视"
                MediaType.GAME -> "编辑游戏"
                MediaType.MUSIC -> "编辑音乐"
            }
            formSubtitle.text = when (selectedMediaType) {
                MediaType.BOOK -> "让这段阅读记录更接近现在的感受。"
                MediaType.ANIME -> "让这段追番记录更接近现在的感受。"
                MediaType.MOVIE -> "让这段观影记录更接近现在的感受。"
                MediaType.GAME -> "让这段游玩记录更接近现在的感受。"
                MediaType.MUSIC -> "让这段聆听记录更接近现在的感受。"
            }
            saveButton.text = "保存修改"
        } else {
            formTitle.text = when (selectedMediaType) {
                MediaType.BOOK -> "新增书籍"
                MediaType.ANIME -> "新增番剧"
                MediaType.MOVIE -> "新增影视"
                MediaType.GAME -> "新增游戏"
                MediaType.MUSIC -> "新增音乐"
            }
            formSubtitle.text = when (selectedMediaType) {
                MediaType.BOOK -> "录入一本新书，开启一段心智旅程。"
                MediaType.ANIME -> "录入一部番剧，记录精彩名场面与声优。"
                MediaType.MOVIE -> "录入一部光影作品，定格感动瞬间。"
                MediaType.GAME -> "录入一款游戏，开启通关冒险之旅。"
                MediaType.MUSIC -> "录入一首歌或一张专辑，记录旋律火花与灵感。"
            }
            saveButton.text = when (selectedMediaType) {
                MediaType.BOOK -> "保存书籍"
                MediaType.ANIME -> "保存番剧"
                MediaType.MOVIE -> "保存影视"
                MediaType.GAME -> "保存游戏"
                MediaType.MUSIC -> "保存音乐"
            }
        }
    }

    private fun loadBookForEditing() {
        if (editingBookId == NO_BOOK_ID) return
        val book = databaseHelper.getBook(editingBookId)
        if (book == null) {
            Toast.makeText(this, R.string.book_not_found, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        selectedMediaType = book.mediaType
        updateMediaTypeChips()
        updateCreatorFields()

        titleInput.setText(book.title)
        authorInput.setText(book.author.orEmpty())
        coverUrlInput.setText(book.coverUrl.orEmpty())
        currentCoverPath = book.coverUrl
        initialCoverPath = book.coverUrl
        updateCoverPreview()
        selectedCategory = book.category?.takeIf { it.isNotBlank() }
        rebuildCategoryChips()
        selectedStatus = book.status
        updateStatusChipsText()
        updateStatusSelectionUI()
        book.rating?.let { r ->
            selectedScore10 = r.coerceIn(0.0, 10.0)
            renderStarSelection()
        }
        tagsInput.setText(book.tags.joinToString("，"))
        shortCommentInput.setText(book.shortComment.orEmpty())
        reviewInput.setText(book.review.orEmpty())
        buyChannelInput.setText(book.buyChannel.orEmpty())
        shelfLocationInput.setText(book.shelfLocation.orEmpty())
        bindingTypeInput.setText(book.bindingType.orEmpty())
        buyPriceInput.setText(book.buyPrice?.let { String.format(Locale.getDefault(), "%.2f", it) }.orEmpty())
        startDate = book.startDate?.let { parseDate(it) }
        finishDate = book.finishDate?.let { parseDate(it) }
        startDate?.let { showSelectedDate(startDateInput, it) }
        finishDate?.let { showSelectedDate(finishDateInput, it) }
    }

    private fun restoreDates(savedInstanceState: Bundle?) {
        startDate = savedInstanceState
            ?.getString(STATE_START_DATE)
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        finishDate = savedInstanceState
            ?.getString(STATE_FINISH_DATE)
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        startDate?.let { showSelectedDate(startDateInput, it) }
        finishDate?.let { showSelectedDate(finishDateInput, it) }
    }

    private fun selectStatus(status: BookStatus) {
        selectedStatus = status
        HapticFeedbackEngine.lightClick(this)
        updateStatusSelectionUI()
    }

    private fun updateStatusSelectionUI() {
        val chips = listOf(
            chipStatusWishlist to BookStatus.WISHLIST,
            chipStatusReading to BookStatus.READING,
            chipStatusFinished to BookStatus.FINISHED,
            chipStatusPaused to BookStatus.PAUSED,
            chipStatusDropped to BookStatus.DROPPED,
        )
        chips.forEach { (chip, status) ->
            val isSelected = selectedStatus == status
            chip.setBackgroundResource(
                if (isSelected) R.drawable.bg_status_chip_selected else R.drawable.bg_status_chip,
            )
            chip.setTextColor(
                ContextCompat.getColor(this, if (isSelected) R.color.white else R.color.readtrace_ink),
            )
        }
    }

    private fun updateStatusChipsText() {
        chipStatusWishlist.text = when (selectedMediaType) {
            MediaType.BOOK -> "🌟 想读"
            MediaType.ANIME -> "🌟 想追"
            MediaType.MOVIE -> "🌟 想看"
            MediaType.GAME -> "🌟 想玩"
            MediaType.MUSIC -> "🌟 想听"
        }
        chipStatusReading.text = when (selectedMediaType) {
            MediaType.BOOK -> "📖 在读"
            MediaType.ANIME -> "📖 追番中"
            MediaType.MOVIE -> "🍿 在看"
            MediaType.GAME -> "🕹️ 游玩中"
            MediaType.MUSIC -> "🎧 在听"
        }
        chipStatusFinished.text = when (selectedMediaType) {
            MediaType.BOOK -> "✅ 读完"
            MediaType.ANIME -> "🌸 补完"
            MediaType.MOVIE -> "🎬 已看"
            MediaType.GAME -> "🏆 白金"
            MediaType.MUSIC -> "💿 听完"
        }
        chipStatusPaused.text = "⏸️ 搁置"
        chipStatusDropped.text = when (selectedMediaType) {
            MediaType.BOOK -> "✖️ 弃读"
            MediaType.ANIME -> "✖️ 弃追"
            MediaType.MOVIE -> "✖️ 弃看"
            MediaType.GAME -> "✖️ 弃玩"
            MediaType.MUSIC -> "✖️ 弃听"
        }
    }

    private fun configureActions() {
        FloatingBack.install(this)
        mediaTypeBook.setOnClickListener { selectMediaType(MediaType.BOOK) }
        mediaTypeAnime.setOnClickListener { selectMediaType(MediaType.ANIME) }
        mediaTypeMovie.setOnClickListener { selectMediaType(MediaType.MOVIE) }
        mediaTypeGame.setOnClickListener { selectMediaType(MediaType.GAME) }
        mediaTypeMusic.setOnClickListener { selectMediaType(MediaType.MUSIC) }

        chipStatusWishlist.setOnClickListener { selectStatus(BookStatus.WISHLIST) }
        chipStatusReading.setOnClickListener { selectStatus(BookStatus.READING) }
        chipStatusFinished.setOnClickListener { selectStatus(BookStatus.FINISHED) }
        chipStatusPaused.setOnClickListener { selectStatus(BookStatus.PAUSED) }
        chipStatusDropped.setOnClickListener { selectStatus(BookStatus.DROPPED) }

        updateStatusChipsText()
        updateStatusSelectionUI()

        coverPickerContainer.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }
        pickCoverButton.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }
        removeCoverButton.setOnClickListener {
            currentCoverPath = null
            coverUrlInput.setText("")
            updateCoverPreview()
            Toast.makeText(this, R.string.cover_removed, Toast.LENGTH_SHORT).show()
        }

        startDateInput.setOnClickListener {
            showDatePicker(startDate) { selected ->
                startDate = selected
                showSelectedDate(startDateInput, selected)
            }
        }
        finishDateInput.setOnClickListener {
            showDatePicker(finishDate) { selected ->
                finishDate = selected
                showSelectedDate(finishDateInput, selected)
            }
        }
        findViewById<View>(R.id.clearStartDateButton).setOnClickListener {
            startDate = null
            showEmptyDate(startDateInput)
        }
        findViewById<View>(R.id.clearFinishDateButton).setOnClickListener {
            finishDate = null
            showEmptyDate(finishDateInput)
        }
        saveButton.setOnClickListener { saveBook() }
    }

    private fun showDatePicker(initialDate: LocalDate?, onSelected: (LocalDate) -> Unit) {
        val date = initialDate ?: LocalDate.now()
        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                onSelected(LocalDate.of(year, month + 1, dayOfMonth))
            },
            date.year,
            date.monthValue - 1,
            date.dayOfMonth,
        ).show()
    }

    private fun showSelectedDate(view: TextView, date: LocalDate) {
        view.text = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
        view.setTextColor(ContextCompat.getColor(this, R.color.readtrace_ink))
        view.error = null
    }

    private fun showEmptyDate(view: TextView) {
        view.setText(R.string.select_date)
        view.setTextColor(ContextCompat.getColor(this, R.color.readtrace_muted))
        view.error = null
    }

    private fun saveBook() {
        clearErrors()
        val title = titleInput.text.toString().trim()
        if (title.isEmpty()) {
            titleInput.error = getString(R.string.error_title_required)
            titleInput.requestFocus()
            return
        }

        val rating = parseRating() // 5 星点选恒有值（默认 4 星）

        val selectedStartDate = startDate
        val selectedFinishDate = finishDate
        if (
            selectedStartDate != null &&
            selectedFinishDate != null &&
            selectedFinishDate.isBefore(selectedStartDate)
        ) {
            finishDateInput.error = getString(R.string.error_finish_date)
            finishDateInput.requestFocus()
            return
        }

        val finalCoverUrl = currentCoverPath?.takeIf { it.isNotEmpty() }
            ?: coverUrlInput.normalizedText()

        val book = Book(
            id = editingBookId.takeIf { it != NO_BOOK_ID } ?: 0,
            title = title,
            author = authorInput.normalizedText(),
            coverUrl = finalCoverUrl,
            category = selectedCategory?.takeIf { it.isNotBlank() },
            status = selectedStatus,
            mediaType = selectedMediaType,
            rating = rating,
            tags = parseTags(tagsInput.text.toString()),
            shortComment = shortCommentInput.normalizedText(),
            review = reviewInput.normalizedText(),
            startDate = startDate?.format(DateTimeFormatter.ISO_LOCAL_DATE),
            finishDate = finishDate?.format(DateTimeFormatter.ISO_LOCAL_DATE),
            buyChannel = buyChannelInput.normalizedText(),
            shelfLocation = shelfLocationInput.normalizedText(),
            bindingType = bindingTypeInput.normalizedText(),
            buyPrice = buyPriceInput.text.toString().trim().toDoubleOrNull(),
        )

        saveButton.isEnabled = false
        saveButton.alpha = 0.65f
        val isEditing = editingBookId != NO_BOOK_ID
        var savedBookId: Long = editingBookId
        var saveSucceeded = false

        runCatching {
            if (isEditing) {
                saveSucceeded = databaseHelper.updateBook(book)
            } else {
                val newId = databaseHelper.insertBook(book)
                savedBookId = newId
                saveSucceeded = newId > 0
            }
        }.onSuccess {
            if (saveSucceeded) {
                // 新作品自动生成六维心智：按主评分 + 媒介语境差异化推导，零填写成本
                if (!isEditing && savedBookId > 0) {
                    val mindprint = com.example.readtrace.util.SmartAssistedHelper
                        .deriveMindprint(rating ?: 8.0, selectedMediaType)
                        .copy(bookId = savedBookId)
                    databaseHelper.saveMindprint(mindprint)
                }

                // 如果是编辑模式且更换了封面，清理原旧封面文件
                if (initialCoverPath != null && initialCoverPath != finalCoverUrl) {
                    CoverImageHelper.deleteCoverFile(initialCoverPath)
                }

                setResult(RESULT_OK)
                Toast.makeText(
                    this,
                    if (isEditing) R.string.book_updated else R.string.book_saved,
                    Toast.LENGTH_SHORT,
                ).show()
                finish()
            } else {
                restoreSaveButton()
                Toast.makeText(
                    this,
                    if (isEditing) R.string.book_update_failed else R.string.book_save_failed,
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }.onFailure {
            restoreSaveButton()
            Toast.makeText(
                this,
                if (isEditing) R.string.book_update_failed else R.string.book_save_failed,
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    /** 分类点选：点中即生效，再点一次可取消选择 */
    private fun selectCategory(category: String) {
        selectedCategory = if (selectedCategory == category) null else category
        HapticFeedbackEngine.lightClick(this)
        rebuildCategoryChips()
    }

    /** 按当前媒介类型重建分类标签行 */
    private fun rebuildCategoryChips() {
        categoryChipGroup.removeAllViews()
        val density = resources.displayMetrics.density

        fun addChip(label: String, isSelected: Boolean, isCustom: Boolean = false) {
            val chip = TextView(this).apply {
                text = label
                textSize = 13.5f
                gravity = android.view.Gravity.CENTER
                minHeight = (42 * density).toInt()
                isSingleLine = true
                isClickable = true
                isFocusable = true
                setPadding((14 * density).toInt(), 0, (14 * density).toInt(), 0)
                setBackgroundResource(
                    if (isSelected) R.drawable.bg_status_chip_selected else R.drawable.bg_status_chip,
                )
                setTextColor(
                    ContextCompat.getColor(
                        this@AddBookActivity,
                        if (isSelected) R.color.white else R.color.readtrace_ink,
                    ),
                )
            }
            if (categoryChipGroup.childCount > 0) {
                val lp = ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                lp.marginStart = (10 * density).toInt()
                chip.layoutParams = lp
            } else {
                chip.layoutParams = ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
            }
            chip.setOnClickListener {
                if (isCustom) {
                    HapticFeedbackEngine.lightClick(this)
                    showCustomCategoryDialog()
                } else {
                    selectCategory(label)
                }
            }
            categoryChipGroup.addView(chip)
        }

        val presets = presetCategories(selectedMediaType)
        presets.forEach { addChip(it, it == selectedCategory) }
        // 编辑已有作品时，非预设分类作为额外标签保留展示，避免丢失原分类
        selectedCategory?.takeIf { it.isNotBlank() && it !in presets }?.let { addChip(it, true) }
        addChip("＋ 自定义分类", false, isCustom = true)
    }

    /** 各媒介类型的预设分类：以作品库真实在用的四字标签为基础，并结合主流平台常见类型扩充 */
    private fun presetCategories(mediaType: MediaType): List<String> = when (mediaType) {
        MediaType.BOOK -> listOf(
            "本格推理", "密室推理", "社会派推理", "悬疑惊悚", "硬核科幻", "奇幻冒险",
            "武侠小说", "历史演义", "哲学思辨", "存在主义", "历史哲学", "人生哲学",
            "心理自助", "人物传记", "日本文学", "欧美文学", "华语经典", "俄国文学",
            "拉美文学", "散文随笔", "诗集诗选", "言情浪漫", "纪实文学", "经济管理",
            "硬核科普", "宇宙科普", "自然博物", "社科文化",
        )
        MediaType.ANIME -> listOf(
            "治愈妖怪", "日常治愈", "奇幻治愈", "温馨日常", "搞笑日常", "萌系日常",
            "奇幻热血", "热血战斗", "热血冒险", "运动竞技", "奇幻恋爱", "搞笑恋爱",
            "青春恋爱", "青春音乐", "青春思辨", "青春校园", "超能青春", "奇幻催泪",
            "悬疑推理", "恐怖惊悚", "异世界番", "机甲科幻", "时代剑戟", "神魔奇幻",
        )
        MediaType.MOVIE -> listOf(
            "奇幻治愈", "温情治愈", "硬核科幻", "悬疑科幻", "科幻哲学", "科幻史诗",
            "悬疑烧脑", "恐怖惊悚", "动作犯罪", "武侠动作", "战争史诗", "黑帮史诗",
            "剧情经典", "爱情文艺", "动画喜剧", "喜剧合家", "超级英雄", "神话国漫",
            "温情纪录", "人文纪录",
        )
        MediaType.GAME -> listOf(
            "魂系神作", "魂系经典", "硬核动作", "动作冒险", "动作角色扮演", "日系角色扮演",
            "欧美角色扮演", "日系动作共斗", "日系炼金RPG", "开放世界", "解谜探索",
            "平台跳跃", "策略模拟", "恐怖生存", "射击竞技", "唯美治愈", "治愈养生",
            "视觉小说", "像素独立", "剧情向",
        )
        MediaType.MUSIC -> listOf(
            "日系摇滚", "哲学摇滚", "文学摇滚", "青春摇滚", "硬派摇滚", "日系抒情",
            "唯美抒情", "治愈救赎", "物哀美学", "宇宙浪漫", "夏日叙事", "静谧夜色",
            "夜光放克", "都会律动", "爵士切分", "治愈纯音", "古典交响", "国风民乐",
            "电子梦境", "嘻哈说唱",
        )
    }

    /** 自定义分类：预设不满足时保留自由输入入口（沿用 app 统一优雅弹窗风格） */
    private fun showCustomCategoryDialog() {
        com.example.readtrace.util.ElegantFormDialog.show(
            activity = this,
            title = "✨ 自定义分类",
            confirmText = "确 定",
            fields = listOf(
                com.example.readtrace.util.ElegantFormDialog.Field(
                    key = "category",
                    label = "分类名称",
                    hint = "如：东亚文学、蒸汽朋克、城市漫游",
                    preset = selectedCategory.orEmpty(),
                    required = true,
                ),
            ),
        ) { values ->
            val value = values.getValue("category")
            if (value.isNotEmpty()) {
                selectedCategory = value
                rebuildCategoryChips()
            }
        }
    }

    private fun restoreSaveButton() {
        saveButton.isEnabled = true
        saveButton.alpha = 1f
    }

    /** P11 标签词云：装配全库高频标签，点击即追加到标签输入框，免键盘输入 */
    private fun setupTagCloud() {
        val cloudRow = findViewById<LinearLayout>(R.id.tagCloudRow)
        val cloudScroll = findViewById<View>(R.id.tagCloudScroll)
        cloudRow.removeAllViews()
        val suggestions = com.example.readtrace.util.SmartAssistedHelper
            .suggestFrequentTags(databaseHelper.getAllUniqueTags(), selectedMediaType, limit = 10)
        if (suggestions.isEmpty()) {
            cloudScroll.visibility = View.GONE
            return
        }
        cloudScroll.visibility = View.VISIBLE
        suggestions.forEach { tag ->
            val chip = TextView(this).apply {
                text = "+ $tag"
                textSize = 12f
                setPadding(22, 10, 22, 10)
                background = getDrawable(R.drawable.bg_dark_chip)
                setTextColor(android.graphics.Color.parseColor("#CCFFFFFF"))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { marginEnd = 8 }
                setOnClickListener {
                    HapticFeedbackEngine.lightClick(this@AddBookActivity)
                    background = getDrawable(R.drawable.bg_dark_chip_selected)
                    appendTagToInput(tag)
                    isEnabled = false
                    alpha = 0.5f
                }
            }
            cloudRow.addView(chip)
        }
    }

    private fun appendTagToInput(tag: String) {
        val existing = tagsInput.text.toString().split("，", ",", "、")
            .map { it.trim() }.filter { it.isNotBlank() }
        if (existing.contains(tag)) return
        tagsInput.setText((existing + tag).joinToString("，"))
        tagsInput.setSelection(tagsInput.text.length)
    }

    private fun setupStarRating() {
        starViews.forEachIndexed { index, star ->
            star.setOnClickListener {
                selectedScore10 = (index + 1) * 2.0
                renderStarSelection()
                com.example.readtrace.util.HapticFeedbackEngine.lightClick(this)
            }
        }
        renderStarSelection()
    }

    private fun renderStarSelection() {
        val activeStars = (selectedScore10 / 2.0).coerceIn(0.0, 5.0)
        starViews.forEachIndexed { index, star ->
            star.setTextColor(
                if (index + 1 <= activeStars || (index < activeStars && activeStars - index >= 0.5)) {
                    android.graphics.Color.parseColor("#F4A261")
                } else {
                    android.graphics.Color.parseColor("#3A3630")
                }
            )
        }
        starHint.text = "${String.format(Locale.getDefault(), "%.1f", selectedScore10)} 分 · ${com.example.readtrace.util.DimensionalScoringEngine.getShortTierLabel(selectedScore10)}"
    }

    private fun parseRating(): Double? = selectedScore10

    /** 两步式记录：新增模式默认只展示核心字段，其余折叠待「补充详细信息」展开 */
    private val collapsedViewIds = listOf(
        R.id.coverPickerContainer,
        R.id.authorLabel, R.id.authorInput,
        R.id.coverUrlLabel, R.id.coverUrlInput,
        R.id.categoryLabel, R.id.categoryChipGroup,
        R.id.tagsLabel, R.id.tagsInput,
        R.id.startDateLabel, R.id.startDateInput, R.id.clearStartDateButton,
        R.id.finishDateLabel, R.id.finishDateInput, R.id.clearFinishDateButton,
        R.id.cardCollection,
        R.id.shortCommentLabel, R.id.shortCommentInput,
        R.id.reviewLabel, R.id.reviewInput,
    )

    private fun applyCompactMode() {
        if (editingBookId != NO_BOOK_ID) return // 编辑模式永远完整表单
        collapsedViewIds.forEach { id -> findViewById<View?>(id)?.visibility = View.GONE }
        findViewById<View?>(R.id.btnExpandForm)?.visibility = View.VISIBLE
        // 极简默认：在读 · 今天开始
        selectedStatus = BookStatus.READING
        updateStatusChipsText()
        updateStatusSelectionUI()
        startDate = java.time.LocalDate.now()
        findViewById<View?>(R.id.btnExpandForm)?.setOnClickListener { expandFullForm() }
    }

    private fun expandFullForm() {
        collapsedViewIds.forEach { id -> findViewById<View?>(id)?.visibility = View.VISIBLE }
        findViewById<View?>(R.id.btnExpandForm)?.visibility = View.GONE
    } // 底层仍存 1~10，5 星制 = 星数 × 2

    private fun parseTags(raw: String): List<String> =
        raw.split(TAG_SEPARATOR)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

    private fun EditText.normalizedText(): String? =
        text.toString().trim().takeIf { it.isNotEmpty() }

    private fun clearErrors() {
        titleInput.error = null
        startDateInput.error = null
        finishDateInput.error = null
    }

    private fun parseDate(value: String): LocalDate? =
        runCatching { LocalDate.parse(value) }.getOrNull()

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_START_DATE, startDate?.toString())
        outState.putString(STATE_FINISH_DATE, finishDate?.toString())
        super.onSaveInstanceState(outState)
    }

    companion object {
        const val EXTRA_BOOK_ID = "com.example.readtrace.extra.BOOK_ID"
        private val RATING_PATTERN = Regex("""^(10(?:\.0)?|[1-9](?:\.\d)?)$""")
        private val TAG_SEPARATOR = Regex("[,，]")
        private val RATING_FORMAT = java.text.DecimalFormat("0.#")
        private const val NO_BOOK_ID = -1L
        private const val STATE_START_DATE = "state_start_date"
        private const val STATE_FINISH_DATE = "state_finish_date"

        fun createEditIntent(context: Context, bookId: Long): Intent =
            Intent(context, AddBookActivity::class.java)
                .putExtra(EXTRA_BOOK_ID, bookId)
    }
}
