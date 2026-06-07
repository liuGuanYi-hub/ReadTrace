package com.example.readtrace

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
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
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class AddBookActivity : AppCompatActivity() {
    private lateinit var databaseHelper: BookDatabaseHelper
    private lateinit var titleInput: EditText
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

    private var startDate: LocalDate? = null
    private var finishDate: LocalDate? = null

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
        bindViews()
        restoreDates(savedInstanceState)
        configureStatusInput()
        configureActions()

        findViewById<View>(R.id.addBookContent)
            .startAnimation(AnimationUtils.loadAnimation(this, R.anim.home_enter))
    }

    private fun bindViews() {
        titleInput = findViewById(R.id.titleInput)
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
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            BookStatus.values().map { it.displayName },
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        statusInput.adapter = adapter
    }

    private fun configureActions() {
        findViewById<View>(R.id.backButton).setOnClickListener { finish() }
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

        val book = Book(
            title = title,
            author = authorInput.normalizedText(),
            coverUrl = coverUrlInput.normalizedText(),
            category = categoryInput.normalizedText(),
            status = BookStatus.values()[statusInput.selectedItemPosition],
            rating = rating,
            tags = parseTags(tagsInput.text.toString()),
            shortComment = shortCommentInput.normalizedText(),
            review = reviewInput.normalizedText(),
            startDate = startDate?.format(DateTimeFormatter.ISO_LOCAL_DATE),
            finishDate = finishDate?.format(DateTimeFormatter.ISO_LOCAL_DATE),
        )

        saveButton.isEnabled = false
        saveButton.alpha = 0.65f
        runCatching { databaseHelper.insertBook(book) }
            .onSuccess {
                setResult(RESULT_OK)
                Toast.makeText(this, R.string.book_saved, Toast.LENGTH_SHORT).show()
                finish()
            }
            .onFailure {
                saveButton.isEnabled = true
                saveButton.alpha = 1f
                Toast.makeText(this, R.string.book_save_failed, Toast.LENGTH_SHORT).show()
            }
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
        private val RATING_PATTERN = Regex("""^(10(?:\.0)?|[1-9](?:\.\d)?)$""")
        private val TAG_SEPARATOR = Regex("[,，]")
        private const val STATE_START_DATE = "state_start_date"
        private const val STATE_FINISH_DATE = "state_finish_date"
    }
}
