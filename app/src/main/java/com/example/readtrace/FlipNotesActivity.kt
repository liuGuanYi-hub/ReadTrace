package com.example.readtrace

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.viewpager2.widget.ViewPager2
import com.example.readtrace.data.BookDatabaseHelper
import com.example.readtrace.model.Book
import com.example.readtrace.model.Note
import com.example.readtrace.ui.BookFlipPageTransformer
import com.example.readtrace.ui.FlipNotesAdapter

class FlipNotesActivity : AppCompatActivity() {
    private lateinit var databaseHelper: BookDatabaseHelper
    private lateinit var viewPager: ViewPager2
    private lateinit var pageIndicator: TextView
    private lateinit var bookTitleText: TextView
    private lateinit var emptyText: TextView

    private var bookId: Long = NO_BOOK_ID
    private var initialPosition: Int = 0
    private var currentBook: Book? = null
    private var notes: List<Note> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_flip_notes)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.flipRoot)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        databaseHelper = BookDatabaseHelper(this)
        bookId = intent.getLongExtra(EXTRA_BOOK_ID, NO_BOOK_ID)
        initialPosition = intent.getIntExtra(EXTRA_INITIAL_POSITION, 0)

        if (bookId == NO_BOOK_ID) {
            Toast.makeText(this, R.string.book_not_found, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        viewPager = findViewById(R.id.flipViewPager)
        pageIndicator = findViewById(R.id.flipPageIndicator)
        bookTitleText = findViewById(R.id.flipBookTitle)
        emptyText = findViewById(R.id.flipEmptyText)

        findViewById<View>(R.id.flipBackButton).setOnClickListener { finish() }

        loadDataAndSetup()
    }

    private fun loadDataAndSetup() {
        val book = databaseHelper.getBook(bookId)
        if (book == null) {
            Toast.makeText(this, R.string.book_not_found, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        currentBook = book
        bookTitleText.text = "《${book.title}》"

        notes = databaseHelper.getNotes(bookId)
        if (notes.isEmpty()) {
            viewPager.visibility = View.GONE
            pageIndicator.visibility = View.GONE
            emptyText.visibility = View.VISIBLE
            return
        }

        emptyText.visibility = View.GONE
        viewPager.visibility = View.VISIBLE
        pageIndicator.visibility = View.VISIBLE

        val adapter = FlipNotesAdapter(book, notes)
        viewPager.adapter = adapter
        viewPager.setPageTransformer(BookFlipPageTransformer())

        val targetPosition = initialPosition.coerceIn(0, notes.size - 1)
        viewPager.setCurrentItem(targetPosition, false)
        updateIndicator(targetPosition)

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateIndicator(position)
            }
        })
    }

    private fun updateIndicator(position: Int) {
        pageIndicator.text = getString(R.string.flip_notes_page_format, position + 1, notes.size)
    }

    override fun onDestroy() {
        databaseHelper.close()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_BOOK_ID = "com.example.readtrace.extra.FLIP_BOOK_ID"
        const val EXTRA_INITIAL_POSITION = "com.example.readtrace.extra.FLIP_INITIAL_POSITION"
        private const val NO_BOOK_ID = -1L

        fun createIntent(context: Context, bookId: Long, initialPosition: Int = 0): Intent =
            Intent(context, FlipNotesActivity::class.java)
                .putExtra(EXTRA_BOOK_ID, bookId)
                .putExtra(EXTRA_INITIAL_POSITION, initialPosition)
    }
}
