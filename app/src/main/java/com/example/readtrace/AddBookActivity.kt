package com.example.readtrace

import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.Spinner
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
    private lateinit var categoryInput: EditText
    private lateinit var sectionRecordTitle: TextView
    private lateinit var statusLabel: TextView
    private lateinit var statusInput: Spinner
    private lateinit var starViews: List<TextView>
    private lateinit var starHint: TextView
    private var selectedStars: Double = 4.0 // 默认 4 星
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
        configureStatusInput()
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
        categoryInput = findViewById(R.id.categoryInput)
        sectionRecordTitle = findViewById(R.id.sectionRecordTitle)
        statusLabel = findViewById(R.id.statusLabel)
        statusInput = findViewById(R.id.statusInput)
        starViews = listOf(
            findViewById(R.id.star1),
            findViewById(R.id.star2),
            findViewById(R.id.star3),
            findViewById(R.id.star4),
            findViewById(R.id.star5),
        )
        starHint = findViewById(R.id.starHint)
        setupStarRating()
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
        configureStatusInput()
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
        categoryInput.hint = when (selectedMediaType) {
            MediaType.BOOK -> "如：东亚文学、科幻、哲学"
            MediaType.ANIME -> "如：热血、治愈、奇幻、日常"
            MediaType.MOVIE -> "如：剧情、悬疑、科幻、纪录片"
            MediaType.GAME -> "如：开放世界、动作RPG、类银河恶魔城"
            MediaType.MUSIC -> "如：J-Pop、后摇、流行、古典"
        }

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
        categoryInput.setText(book.category.orEmpty())
        configureStatusInput()
        statusInput.setSelection(BookStatus.values().indexOf(book.status))
        book.rating?.let { r ->
            selectedStars = (r / 2.0).toInt().coerceIn(1, 5).toDouble()
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

    private fun configureStatusInput() {
        val currentSelectedPosition = if (statusInput.adapter != null) statusInput.selectedItemPosition else 0
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            BookStatus.values().map { it.getDisplayName(selectedMediaType) },
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        statusInput.adapter = adapter
        if (currentSelectedPosition in 0 until adapter.count) {
            statusInput.setSelection(currentSelectedPosition)
        }
    }

    private fun configureActions() {
        FloatingBack.install(this)
        mediaTypeBook.setOnClickListener { selectMediaType(MediaType.BOOK) }
        mediaTypeAnime.setOnClickListener { selectMediaType(MediaType.ANIME) }
        mediaTypeMovie.setOnClickListener { selectMediaType(MediaType.MOVIE) }
        mediaTypeGame.setOnClickListener { selectMediaType(MediaType.GAME) }
        mediaTypeMusic.setOnClickListener { selectMediaType(MediaType.MUSIC) }

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
            category = categoryInput.normalizedText(),
            status = BookStatus.values()[statusInput.selectedItemPosition],
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
        runCatching {
            if (isEditing) {
                databaseHelper.updateBook(book)
            } else {
                databaseHelper.insertBook(book) > 0
            }
        }.onSuccess { saved ->
            if (saved) {
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

    private fun restoreSaveButton() {
        saveButton.isEnabled = true
        saveButton.alpha = 1f
    }

    private fun setupStarRating() {
        starViews.forEachIndexed { index, star ->
            star.setOnClickListener {
                selectedStars = (index + 1).toDouble()
                renderStarSelection()
                com.example.readtrace.util.HapticFeedbackEngine.lightClick(this)
            }
        }
        renderStarSelection()
    }

    private fun renderStarSelection() {
        starViews.forEachIndexed { index, star ->
            star.setTextColor(
                if (index < selectedStars.toInt()) android.graphics.Color.parseColor("#F4A261")
                else android.graphics.Color.parseColor("#3A3630"),
            )
        }
        val labels = listOf("", "尚可", "一般", "喜欢", "力荐", "此生挚爱")
        starHint.text = "${selectedStars.toInt()} 星 · ${labels[selectedStars.toInt()]}"
    }

    private fun parseRating(): Double? = selectedStars * 2.0 // 底层仍存 1~10，5 星制 = 星数 × 2

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
