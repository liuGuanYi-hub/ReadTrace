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
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class AddBookActivity : AppCompatActivity() {
    private lateinit var databaseHelper: BookDatabaseHelper
    private lateinit var formTitle: TextView
    private lateinit var formSubtitle: TextView
    private lateinit var titleInput: EditText
    private lateinit var authorLabel: TextView
    private lateinit var authorInput: EditText
    private lateinit var coverUrlInput: EditText
    private lateinit var categoryInput: EditText
    private lateinit var statusInput: Spinner
    private lateinit var ratingInput: EditText
    private lateinit var tagsInput: EditText
    private lateinit var shortCommentInput: EditText
    private lateinit var reviewInput: EditText
    private lateinit var startDateInput: TextView
    private lateinit var finishDateInput: TextView
    private lateinit var saveButton: TextView

    private lateinit var mediaTypeBook: TextView
    private lateinit var mediaTypeMovie: TextView
    private lateinit var mediaTypeGame: TextView
    private lateinit var mediaTypePodcast: TextView

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

        databaseHelper = BookDatabaseHelper(this)
        editingBookId = intent.getLongExtra(EXTRA_BOOK_ID, NO_BOOK_ID)
        bindViews()
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
        titleInput = findViewById(R.id.titleInput)
        authorLabel = findViewById(R.id.authorLabel)
        authorInput = findViewById(R.id.authorInput)
        coverUrlInput = findViewById(R.id.coverUrlInput)
        categoryInput = findViewById(R.id.categoryInput)
        statusInput = findViewById(R.id.statusInput)
        ratingInput = findViewById(R.id.ratingInput)
        tagsInput = findViewById(R.id.tagsInput)
        shortCommentInput = findViewById(R.id.shortCommentInput)
        reviewInput = findViewById(R.id.reviewInput)
        startDateInput = findViewById(R.id.startDateInput)
        finishDateInput = findViewById(R.id.finishDateInput)
        saveButton = findViewById(R.id.saveButton)

        mediaTypeBook = findViewById(R.id.mediaTypeBook)
        mediaTypeMovie = findViewById(R.id.mediaTypeMovie)
        mediaTypeGame = findViewById(R.id.mediaTypeGame)
        mediaTypePodcast = findViewById(R.id.mediaTypePodcast)

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
            mediaTypeMovie to MediaType.MOVIE,
            mediaTypeGame to MediaType.GAME,
            mediaTypePodcast to MediaType.PODCAST,
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
        authorLabel.text = selectedMediaType.creatorLabel
        authorInput.hint = selectedMediaType.creatorHint
    }

    private fun configureFormMode() {
        if (editingBookId == NO_BOOK_ID) return
        formTitle.setText(R.string.edit_book_title)
        formSubtitle.setText(R.string.edit_book_subtitle)
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
        ratingInput.setText(book.rating?.let { RATING_FORMAT.format(it) }.orEmpty())
        tagsInput.setText(book.tags.joinToString("，"))
        shortCommentInput.setText(book.shortComment.orEmpty())
        reviewInput.setText(book.review.orEmpty())
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
        findViewById<View>(R.id.backButton).setOnClickListener { finish() }
        mediaTypeBook.setOnClickListener { selectMediaType(MediaType.BOOK) }
        mediaTypeMovie.setOnClickListener { selectMediaType(MediaType.MOVIE) }
        mediaTypeGame.setOnClickListener { selectMediaType(MediaType.GAME) }
        mediaTypePodcast.setOnClickListener { selectMediaType(MediaType.PODCAST) }

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

        val rating = parseRating() ?: if (ratingInput.text.toString().isBlank()) {
            null
        } else {
            ratingInput.error = getString(R.string.error_rating)
            ratingInput.requestFocus()
            return
        }

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

    private fun parseRating(): Double? {
        val raw = ratingInput.text.toString().trim()
        if (raw.isEmpty()) return null
        if (!RATING_PATTERN.matches(raw)) return null
        return raw.toDoubleOrNull()?.takeIf { it in 1.0..10.0 }
    }

    private fun parseTags(raw: String): List<String> =
        raw.split(TAG_SEPARATOR)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

    private fun EditText.normalizedText(): String? =
        text.toString().trim().takeIf { it.isNotEmpty() }

    private fun clearErrors() {
        titleInput.error = null
        ratingInput.error = null
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

    override fun onDestroy() {
        databaseHelper.close()
        super.onDestroy()
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
