package com.example.readtrace

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.readtrace.data.BookDatabaseHelper
import com.example.readtrace.model.ArchivedNoteItem
import com.example.readtrace.model.Book
import com.example.readtrace.model.NoteType
import com.example.readtrace.util.ElegantConfirmDialog
import com.example.readtrace.util.FloatingBack
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

class TrashActivity : AppCompatActivity() {
    private lateinit var databaseHelper: BookDatabaseHelper
    private lateinit var tabBooks: TextView
    private lateinit var tabNotes: TextView
    private lateinit var booksContainer: LinearLayout
    private lateinit var notesContainer: LinearLayout
    private lateinit var emptyText: TextView

    private var currentTabIsBooks: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_trash)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.trashRoot)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        databaseHelper = BookDatabaseHelper(this)
        tabBooks = findViewById(R.id.trashTabBooks)
        tabNotes = findViewById(R.id.trashTabNotes)
        booksContainer = findViewById(R.id.trashBooksContainer)
        notesContainer = findViewById(R.id.trashNotesContainer)
        emptyText = findViewById(R.id.trashEmptyText)

        FloatingBack.install(this)
        findViewById<View>(R.id.trashClearAllButton).setOnClickListener { confirmClearAllTrash() }

        tabBooks.setOnClickListener { switchTab(true) }
        tabNotes.setOnClickListener { switchTab(false) }

        findViewById<View>(R.id.trashContent)
            .startAnimation(AnimationUtils.loadAnimation(this, R.anim.home_enter))
    }

    override fun onResume() {
        super.onResume()
        refreshList()
    }

    private fun switchTab(showBooks: Boolean) {
        if (currentTabIsBooks == showBooks) return
        currentTabIsBooks = showBooks
        updateTabStyles()
        refreshList()
    }

    private fun updateTabStyles() {
        tabBooks.setBackgroundResource(
            if (currentTabIsBooks) R.drawable.bg_status_chip_selected else R.drawable.bg_status_chip,
        )
        tabBooks.setTextColor(
            ContextCompat.getColor(this, if (currentTabIsBooks) R.color.white else R.color.readtrace_ink),
        )

        tabNotes.setBackgroundResource(
            if (!currentTabIsBooks) R.drawable.bg_status_chip_selected else R.drawable.bg_status_chip,
        )
        tabNotes.setTextColor(
            ContextCompat.getColor(this, if (!currentTabIsBooks) R.color.white else R.color.readtrace_ink),
        )
    }

    private fun refreshList() {
        if (currentTabIsBooks) {
            renderArchivedBooks()
        } else {
            renderArchivedNotes()
        }
    }

    private fun renderArchivedBooks() {
        booksContainer.visibility = View.VISIBLE
        notesContainer.visibility = View.GONE
        booksContainer.removeAllViews()

        val books = databaseHelper.getArchivedBooks()
        if (books.isEmpty()) {
            emptyText.visibility = View.VISIBLE
            emptyText.setText(R.string.trash_empty_books)
            return
        }

        emptyText.visibility = View.GONE
        books.forEach { book ->
            val item = LayoutInflater.from(this).inflate(R.layout.item_trash_book, booksContainer, false)
            item.findViewById<TextView>(R.id.trashBookTitle).text = book.title
            val author = book.author?.takeIf { it.isNotEmpty() } ?: getString(R.string.unknown_author)
            item.findViewById<TextView>(R.id.trashBookMeta).text = "$author · ${book.status.displayName}"
            item.findViewById<TextView>(R.id.trashBookDeletedAt).text = formatDeletedAt(book.deletedAt)

            item.findViewById<View>(R.id.trashBookRestoreBtn).setOnClickListener {
                restoreBook(book.id)
            }
            item.findViewById<View>(R.id.trashBookDeleteBtn).setOnClickListener {
                confirmHardDeleteBook(book)
            }
            booksContainer.addView(item)
        }
    }

    private fun renderArchivedNotes() {
        booksContainer.visibility = View.GONE
        notesContainer.visibility = View.VISIBLE
        notesContainer.removeAllViews()

        val archivedNotes = databaseHelper.getArchivedNotes()
        if (archivedNotes.isEmpty()) {
            emptyText.visibility = View.VISIBLE
            emptyText.setText(R.string.trash_empty_notes)
            return
        }

        emptyText.visibility = View.GONE
        archivedNotes.forEach { itemData ->
            val note = itemData.note
            val item = LayoutInflater.from(this).inflate(R.layout.item_trash_note, notesContainer, false)
            item.findViewById<TextView>(R.id.trashNoteTypeBadge).apply {
                text = note.noteType.displayName
                setTextColor(
                    ContextCompat.getColor(
                        context,
                        if (note.noteType == NoteType.QUOTE) R.color.readtrace_accent else R.color.readtrace_muted,
                    ),
                )
            }
            item.findViewById<TextView>(R.id.trashNoteBookTitle).text =
                itemData.bookTitle?.let { "《$it》" } ?: getString(R.string.not_recorded)
            item.findViewById<TextView>(R.id.trashNoteContent).text = note.content
            item.findViewById<TextView>(R.id.trashNoteDeletedAt).text = formatDeletedAt(note.deletedAt)

            item.findViewById<View>(R.id.trashNoteRestoreBtn).setOnClickListener {
                restoreNote(note.id)
            }
            item.findViewById<View>(R.id.trashNoteDeleteBtn).setOnClickListener {
                confirmHardDeleteNote(note)
            }
            notesContainer.addView(item)
        }
    }

    private fun restoreBook(bookId: Long) {
        val success = databaseHelper.restoreBook(bookId)
        if (success) {
            Toast.makeText(this, R.string.restore_book_success, Toast.LENGTH_SHORT).show()
            renderArchivedBooks()
        } else {
            Toast.makeText(this, R.string.restore_book_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun restoreNote(noteId: Long) {
        val success = databaseHelper.restoreNote(noteId)
        if (success) {
            Toast.makeText(this, R.string.restore_note_success, Toast.LENGTH_SHORT).show()
            renderArchivedNotes()
        } else {
            Toast.makeText(this, R.string.restore_note_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmHardDeleteBook(book: Book) {
        ElegantConfirmDialog.show(
            activity = this,
            title = "⚠️ " + getString(R.string.hard_delete_confirm_title),
            message = getString(R.string.hard_delete_confirm_message),
            confirmText = getString(R.string.action_hard_delete),
            isDanger = true,
            onConfirm = {
                val deleted = databaseHelper.hardDeleteBook(book.id)
                if (deleted) {
                    Toast.makeText(this, R.string.hard_delete_book_success, Toast.LENGTH_SHORT).show()
                    renderArchivedBooks()
                } else {
                    Toast.makeText(this, R.string.hard_delete_book_failed, Toast.LENGTH_SHORT).show()
                }
            },
        )
    }

    private fun confirmHardDeleteNote(note: com.example.readtrace.model.Note) {
        ElegantConfirmDialog.show(
            activity = this,
            title = "⚠️ " + getString(R.string.hard_delete_note_confirm_title),
            message = getString(R.string.hard_delete_note_confirm_message),
            confirmText = getString(R.string.action_hard_delete),
            isDanger = true,
            onConfirm = {
                val deleted = databaseHelper.hardDeleteNote(note.id)
                if (deleted) {
                    Toast.makeText(this, R.string.hard_delete_note_success, Toast.LENGTH_SHORT).show()
                    renderArchivedNotes()
                } else {
                    Toast.makeText(this, R.string.hard_delete_note_failed, Toast.LENGTH_SHORT).show()
                }
            },
        )
    }

    private fun confirmClearAllTrash() {
        ElegantConfirmDialog.show(
            activity = this,
            title = "🧹 " + getString(R.string.clear_all_trash_confirm_title),
            message = getString(R.string.clear_all_trash_confirm_message),
            confirmText = getString(R.string.action_clear_all_trash),
            isDanger = true,
            onConfirm = {
                val (booksCount, notesCount) = databaseHelper.clearAllTrash()
                if (booksCount > 0 || notesCount > 0) {
                    Toast.makeText(
                        this,
                        getString(R.string.clear_trash_success_format, booksCount, notesCount),
                        Toast.LENGTH_SHORT,
                    ).show()
                    refreshList()
                } else {
                    Toast.makeText(this, R.string.clear_trash_empty, Toast.LENGTH_SHORT).show()
                }
            },
        )
    }

    private fun formatDeletedAt(value: String?): String {
        if (value.isNullOrBlank()) return getString(R.string.not_recorded)
        return runCatching {
            OffsetDateTime.parse(value).format(DISPLAY_TIME_FORMAT)
        }.getOrDefault(value)
    }

    override fun onDestroy() {
        databaseHelper.close()
        super.onDestroy()
    }

    companion object {
        private val DISPLAY_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

        fun createIntent(context: Context): Intent =
            Intent(context, TrashActivity::class.java)
    }
}
