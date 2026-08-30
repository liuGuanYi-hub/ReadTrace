package com.example.readtrace

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.readtrace.data.BookDatabaseHelper
import com.example.readtrace.model.Note
import com.example.readtrace.model.NoteType
import com.example.readtrace.util.ElegantConfirmDialog
import com.example.readtrace.util.FloatingBack
import com.example.readtrace.util.HapticFeedbackEngine

class AddNoteActivity : AppCompatActivity() {
    private lateinit var databaseHelper: BookDatabaseHelper
    private lateinit var formTitle: TextView
    private lateinit var formSubtitle: TextView
    private lateinit var chipTypeNote: TextView
    private lateinit var chipTypeQuote: TextView
    private var selectedNoteType: NoteType = NoteType.NOTE
    private lateinit var contentInput: EditText
    private lateinit var pageInput: EditText
    private lateinit var chapterInput: EditText
    private lateinit var saveButton: TextView
    private lateinit var archiveButton: TextView

    private var editingNoteId: Long = NO_NOTE_ID
    private var bookId: Long = NO_BOOK_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_add_note)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.addNoteRoot)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        databaseHelper = BookDatabaseHelper.getInstance(this)
        editingNoteId = intent.getLongExtra(EXTRA_NOTE_ID, NO_NOTE_ID)
        bookId = intent.getLongExtra(EXTRA_BOOK_ID, NO_BOOK_ID)
        bindViews()
        configureFormMode()
        updateMediaTypeLabels()
        if (savedInstanceState == null) {
            loadNoteForEditing()
            if (isFinishing) return
        }
        configureActions()

        findViewById<View>(R.id.addNoteContent)
            .startAnimation(AnimationUtils.loadAnimation(this, R.anim.home_enter))
    }

    private fun bindViews() {
        formTitle = findViewById(R.id.formTitle)
        formSubtitle = findViewById(R.id.formSubtitle)
        chipTypeNote = findViewById(R.id.chipTypeNote)
        chipTypeQuote = findViewById(R.id.chipTypeQuote)
        contentInput = findViewById(R.id.contentInput)
        pageInput = findViewById(R.id.pageInput)
        chapterInput = findViewById(R.id.chapterInput)
        saveButton = findViewById(R.id.saveButton)
        archiveButton = findViewById(R.id.archiveButton)
    }

    private fun selectNoteType(type: NoteType) {
        selectedNoteType = type
        HapticFeedbackEngine.lightClick(this)
        updateNoteTypeSelectionUI()
    }

    private fun updateNoteTypeSelectionUI() {
        val chips = listOf(
            chipTypeNote to NoteType.NOTE,
            chipTypeQuote to NoteType.QUOTE,
        )
        chips.forEach { (chip, type) ->
            val isSelected = selectedNoteType == type
            chip.setBackgroundResource(
                if (isSelected) R.drawable.bg_status_chip_selected else R.drawable.bg_status_chip,
            )
            chip.setTextColor(
                ContextCompat.getColor(this, if (isSelected) R.color.white else R.color.readtrace_ink),
            )
        }
    }

    private fun configureFormMode() {
        if (editingNoteId == NO_NOTE_ID) return
        formTitle.setText(R.string.edit_note_title)
        formSubtitle.setText(R.string.edit_note_subtitle)
        archiveButton.visibility = View.VISIBLE
    }

    private fun updateMediaTypeLabels() {
        val book = databaseHelper.getBook(bookId)
        val mediaType = book?.mediaType ?: com.example.readtrace.model.MediaType.BOOK
        val labelPage = findViewById<TextView>(R.id.labelNotePage)
        val labelChapter = findViewById<TextView>(R.id.labelNoteChapter)

        when (mediaType) {
            com.example.readtrace.model.MediaType.BOOK -> {
                labelPage.text = "页码"
                pageInput.hint = "例如：P.120"
                labelChapter.text = "所属章节"
                chapterInput.hint = "例如：第三章 · 驯服狐狸"
            }
            com.example.readtrace.model.MediaType.ANIME -> {
                labelPage.text = "集数 / 时间点"
                pageInput.hint = "例如：第 12 话 / 14:20"
                labelChapter.text = "篇章 / 剧集"
                chapterInput.hint = "例如：第一季 · 决战前夜"
            }
            com.example.readtrace.model.MediaType.MOVIE -> {
                labelPage.text = "时间点"
                pageInput.hint = "例如：01:24:30"
                labelChapter.text = "场景 / 幕"
                chapterInput.hint = "例如：第二幕 · 银幕高潮"
            }
            com.example.readtrace.model.MediaType.GAME -> {
                labelPage.text = "关卡 / 进度"
                pageInput.hint = "例如：第 5 关 / Boss 战前"
                labelChapter.text = "任务 / 篇章"
                chapterInput.hint = "例如：主线任务 · 黄金树之影"
            }
            com.example.readtrace.model.MediaType.MUSIC -> {
                labelPage.text = "曲目时间点"
                pageInput.hint = "例如：01:24"
                labelChapter.text = "曲目 / 专辑"
                chapterInput.hint = "例如：真夜中 · 副歌段落"
            }
        }
    }

    private fun loadNoteForEditing() {
        if (editingNoteId == NO_NOTE_ID) {
            if (bookId == NO_BOOK_ID) {
                Toast.makeText(this, R.string.book_not_found, Toast.LENGTH_SHORT).show()
                finish()
            }
            updateMediaTypeLabels()
            return
        }
        val note = databaseHelper.getNote(editingNoteId)
        if (note == null) {
            Toast.makeText(this, R.string.note_not_found, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        bookId = note.bookId
        updateMediaTypeLabels()
        selectedNoteType = note.noteType
        updateNoteTypeSelectionUI()
        contentInput.setText(note.content)
        pageInput.setText(note.page.orEmpty())
        chapterInput.setText(note.chapter.orEmpty())
    }

    private fun configureActions() {
        FloatingBack.install(this)
        chipTypeNote.setOnClickListener { selectNoteType(NoteType.NOTE) }
        chipTypeQuote.setOnClickListener { selectNoteType(NoteType.QUOTE) }
        updateNoteTypeSelectionUI()

        saveButton.setOnClickListener { saveNote() }
        archiveButton.setOnClickListener { confirmArchive() }
    }

    private fun saveNote() {
        val content = contentInput.text.toString().trim()
        if (content.isEmpty()) {
            contentInput.error = getString(R.string.error_note_content_required)
            contentInput.requestFocus()
            return
        }

        val note = Note(
            id = editingNoteId.takeIf { it != NO_NOTE_ID } ?: 0,
            bookId = bookId,
            noteType = selectedNoteType,
            content = content,
            page = pageInput.normalizedText(),
            chapter = chapterInput.normalizedText(),
        )

        saveButton.isEnabled = false
        saveButton.alpha = 0.65f
        val isEditing = editingNoteId != NO_NOTE_ID
        runCatching {
            if (isEditing) {
                databaseHelper.updateNote(note)
            } else {
                databaseHelper.insertNote(note) > 0
            }
        }.onSuccess { saved ->
            if (saved) {
                Toast.makeText(
                    this,
                    if (isEditing) R.string.note_updated else R.string.note_saved,
                    Toast.LENGTH_SHORT,
                ).show()
                finish()
            } else {
                restoreSaveButton()
                showSaveFailure(isEditing)
            }
        }.onFailure {
            restoreSaveButton()
            showSaveFailure(isEditing)
        }
    }

    private fun showSaveFailure(isEditing: Boolean) {
        Toast.makeText(
            this,
            if (isEditing) R.string.note_update_failed else R.string.note_save_failed,
            Toast.LENGTH_SHORT,
        ).show()
    }

    private fun confirmArchive() {
        ElegantConfirmDialog.show(
            activity = this,
            title = "📦 " + getString(R.string.note_archive_confirm_title),
            message = getString(R.string.note_archive_confirm_message),
            confirmText = getString(R.string.action_archive),
            isDanger = true,
            onConfirm = {
                val archived = runCatching {
                    databaseHelper.archiveNote(editingNoteId)
                }.getOrDefault(false)
                if (archived) {
                    Toast.makeText(this, R.string.note_archive_success, Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    Toast.makeText(this, R.string.note_archive_failed, Toast.LENGTH_SHORT).show()
                }
            },
        )
    }

    private fun restoreSaveButton() {
        saveButton.isEnabled = true
        saveButton.alpha = 1f
    }

    private fun EditText.normalizedText(): String? =
        text.toString().trim().takeIf { it.isNotEmpty() }

    companion object {
        private const val EXTRA_BOOK_ID = "com.example.readtrace.extra.BOOK_ID"
        private const val EXTRA_NOTE_ID = "com.example.readtrace.extra.NOTE_ID"
        private const val NO_NOTE_ID = -1L
        private const val NO_BOOK_ID = -1L

        fun createAddIntent(context: Context, bookId: Long): Intent =
            Intent(context, AddNoteActivity::class.java)
                .putExtra(EXTRA_BOOK_ID, bookId)

        fun createEditIntent(context: Context, noteId: Long): Intent =
            Intent(context, AddNoteActivity::class.java)
                .putExtra(EXTRA_NOTE_ID, noteId)
    }
}
