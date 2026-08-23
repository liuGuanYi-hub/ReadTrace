package com.example.readtrace

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.readtrace.data.BookDatabaseHelper
import com.example.readtrace.model.Book
import com.example.readtrace.model.Note
import com.example.readtrace.model.NoteType
import com.example.readtrace.util.CoverImageHelper
import java.text.DecimalFormat
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

class BookDetailActivity : AppCompatActivity() {
    private lateinit var databaseHelper: BookDatabaseHelper
    private var bookId: Long = NO_BOOK_ID
    private var currentBook: Book? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_book_detail)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.detailRoot)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        databaseHelper = BookDatabaseHelper(this)
        bookId = intent.getLongExtra(EXTRA_BOOK_ID, NO_BOOK_ID)
        if (bookId == NO_BOOK_ID) {
            showMissingBookAndClose()
            return
        }

        findViewById<View>(R.id.detailBackButton).setOnClickListener { finish() }
        findViewById<View>(R.id.detailEditButton).setOnClickListener {
            startActivity(AddBookActivity.createEditIntent(this, bookId))
        }
        findViewById<View>(R.id.detailArchiveButton).setOnClickListener {
            confirmArchive()
        }
        findViewById<View>(R.id.detailNotesAddButton).setOnClickListener {
            startActivity(AddNoteActivity.createAddIntent(this, bookId))
        }

        findViewById<View>(R.id.detailContent)
            .startAnimation(AnimationUtils.loadAnimation(this, R.anim.home_enter))
    }

    override fun onResume() {
        super.onResume()
        if (bookId == NO_BOOK_ID) return
        val book = databaseHelper.getBook(bookId)
        if (book == null) {
            showMissingBookAndClose()
            return
        }
        currentBook = book
        renderBook(book)
        renderNotes(databaseHelper.getNotes(bookId))
    }

    private fun renderBook(book: Book) {
        val coverImage = findViewById<ImageView>(R.id.detailCoverImage)
        CoverImageHelper.loadCover(coverImage, book.coverUrl)

        findViewById<TextView>(R.id.detailMediaBadge).text = "${book.mediaType.emoji} ${book.mediaType.displayName}"
        findViewById<TextView>(R.id.detailBookTitle).text = book.title
        findViewById<TextView>(R.id.detailBookAuthor).text = valueOrFallback(book.author)
        findViewById<TextView>(R.id.detailHeroMeta).text = buildHeroMeta(book)
        findViewById<TextView>(R.id.detailCategory).text = valueOrFallback(book.category)
        findViewById<TextView>(R.id.detailCoverUrl).text = valueOrFallback(book.coverUrl)
        findViewById<TextView>(R.id.detailStatus).text = book.status.getDisplayName(book.mediaType)
        findViewById<TextView>(R.id.detailRating).text = book.rating?.let {
            getString(R.string.rating_format, RATING_FORMAT.format(it))
        } ?: getString(R.string.not_recorded)
        findViewById<TextView>(R.id.detailTags).text =
            if (book.tags.isEmpty()) {
                getString(R.string.not_recorded)
            } else {
                book.tags.joinToString(" · ")
            }
        findViewById<TextView>(R.id.detailStartDate).text = valueOrFallback(book.startDate)
        findViewById<TextView>(R.id.detailFinishDate).text = valueOrFallback(book.finishDate)
        findViewById<TextView>(R.id.detailShortComment).text =
            valueOrFallback(book.shortComment)
        findViewById<TextView>(R.id.detailReview).text = valueOrFallback(book.review)
        findViewById<TextView>(R.id.detailCreatedAt).text = formatTimestamp(book.createdAt)
        findViewById<TextView>(R.id.detailUpdatedAt).text = formatTimestamp(book.updatedAt)
    }

    private fun renderNotes(notes: List<Note>) {
        val container = findViewById<LinearLayout>(R.id.detailNotesContainer)
        val emptyView = findViewById<TextView>(R.id.detailNotesEmpty)
        val countView = findViewById<TextView>(R.id.detailNotesCount)
        val flipButton = findViewById<TextView>(R.id.detailNotesFlipButton)
        container.removeAllViews()
        if (notes.isEmpty()) {
            emptyView.visibility = View.VISIBLE
            countView.visibility = View.GONE
            container.visibility = View.GONE
            flipButton.visibility = View.GONE
            return
        }

        emptyView.visibility = View.GONE
        countView.visibility = View.VISIBLE
        countView.text = getString(R.string.notes_count_format, notes.size)
        flipButton.visibility = View.VISIBLE
        flipButton.setOnClickListener {
            startActivity(FlipNotesActivity.createIntent(this, bookId, 0))
        }
        container.visibility = View.VISIBLE
        notes.forEachIndexed { index, note ->
            val item = layoutInflater.inflate(R.layout.item_detail_note, container, false)
            item.findViewById<TextView>(R.id.noteTypeBadge).apply {
                text = note.noteType.displayName
                setTextColor(
                    getColor(
                        if (note.noteType == NoteType.QUOTE) {
                            R.color.readtrace_accent
                        } else {
                            R.color.readtrace_muted
                        },
                    ),
                )
            }
            item.findViewById<TextView>(R.id.noteCreatedAt).text = formatTimestamp(note.createdAt)
            item.findViewById<TextView>(R.id.noteContent).text = note.content
            val positionMeta = buildList {
                note.page?.trim()?.takeIf { it.isNotEmpty() }?.let {
                    add(getString(R.string.note_page_format, it))
                }
                note.chapter?.trim()?.takeIf { it.isNotEmpty() }?.let(::add)
            }.joinToString(" · ")
            item.findViewById<TextView>(R.id.notePositionMeta).apply {
                text = positionMeta
                visibility = if (positionMeta.isEmpty()) View.GONE else View.VISIBLE
            }
            val params = item.layoutParams as LinearLayout.LayoutParams
            params.topMargin = dpToPx(if (index == 0) 14 else 10)
            item.layoutParams = params
            item.setOnClickListener { openNoteEditor(note.id) }
            item.setOnLongClickListener {
                confirmArchiveNote(note)
                true
            }
            container.addView(item)
        }
    }

    private fun openNoteEditor(noteId: Long) {
        startActivity(AddNoteActivity.createEditIntent(this, noteId))
    }

    private fun confirmArchiveNote(note: Note) {
        AlertDialog.Builder(this)
            .setTitle(R.string.note_archive_confirm_title)
            .setMessage(R.string.note_archive_confirm_message)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_archive) { _, _ ->
                val archived = runCatching {
                    databaseHelper.archiveNote(note.id)
                }.getOrDefault(false)
                if (archived) {
                    Toast.makeText(this, R.string.note_archive_success, Toast.LENGTH_SHORT).show()
                    renderNotes(databaseHelper.getNotes(bookId))
                } else {
                    Toast.makeText(this, R.string.note_archive_failed, Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    private fun dpToPx(value: Int): Int =
        (value * resources.displayMetrics.density).roundToInt()

    private fun confirmArchive() {
        val book = currentBook ?: return
        AlertDialog.Builder(this)
            .setTitle(R.string.archive_confirm_title)
            .setMessage(R.string.archive_confirm_message)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_archive) { _, _ ->
                archiveBook(book.id)
            }
            .show()
    }

    private fun archiveBook(id: Long) {
        val archived = runCatching { databaseHelper.archiveBook(id) }.getOrDefault(false)
        if (archived) {
            Toast.makeText(this, R.string.archive_success, Toast.LENGTH_SHORT).show()
            finish()
        } else {
            Toast.makeText(this, R.string.archive_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun valueOrFallback(value: String?): String =
        value?.trim()?.takeIf { it.isNotEmpty() } ?: getString(R.string.not_recorded)

    private fun buildHeroMeta(book: Book): String {
        val ratingLabel = book.rating?.let {
            getString(R.string.rating_format, RATING_FORMAT.format(it))
        } ?: getString(R.string.not_recorded)
        return listOfNotNull(
            book.status.getDisplayName(book.mediaType),
            ratingLabel,
            book.category?.trim()?.takeIf { it.isNotEmpty() },
        ).joinToString(" · ")
    }

    private fun formatTimestamp(value: String): String =
        runCatching {
            OffsetDateTime.parse(value).format(DISPLAY_TIME_FORMAT)
        }.getOrDefault(valueOrFallback(value))

    private fun showMissingBookAndClose() {
        Toast.makeText(this, R.string.book_not_found, Toast.LENGTH_SHORT).show()
        finish()
    }

    override fun onDestroy() {
        databaseHelper.close()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_BOOK_ID = "com.example.readtrace.extra.BOOK_ID"
        private const val NO_BOOK_ID = -1L
        private val RATING_FORMAT = DecimalFormat("0.#")
        private val DISPLAY_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

        fun createIntent(context: Context, bookId: Long): Intent =
            Intent(context, BookDetailActivity::class.java)
                .putExtra(EXTRA_BOOK_ID, bookId)
    }
}
