package com.example.readtrace

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.readtrace.data.BookDatabaseHelper
import com.example.readtrace.model.Book
import com.example.readtrace.model.BookStatus
import java.text.DecimalFormat

class MainActivity : AppCompatActivity() {
    private lateinit var databaseHelper: BookDatabaseHelper
    private lateinit var booksContainer: LinearLayout
    private lateinit var emptyPanel: View
    private lateinit var emptyTitle: TextView
    private lateinit var emptyBody: TextView
    private var selectedStatus: BookStatus? = null

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

        configureActions()
        configureStatusFilters()

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
        refreshBooks()
    }

    private fun updateStatusChips() {
        val chips = listOf(
            findViewById<TextView>(R.id.statusAll) to null,
            findViewById<TextView>(R.id.statusWishlist) to BookStatus.WISHLIST,
            findViewById<TextView>(R.id.statusReading) to BookStatus.READING,
            findViewById<TextView>(R.id.statusFinished) to BookStatus.FINISHED,
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

    private fun refreshBooks() {
        val books = databaseHelper.getBooks(selectedStatus)
        booksContainer.removeAllViews()

        if (books.isEmpty()) {
            booksContainer.visibility = View.GONE
            emptyPanel.visibility = View.VISIBLE
            val isFiltered = selectedStatus != null
            emptyTitle.setText(
                if (isFiltered) R.string.empty_filter_title else R.string.empty_shelf_title,
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

    private fun createBookCard(book: Book): View {
        val card = LayoutInflater.from(this)
            .inflate(R.layout.item_book_card, booksContainer, false)
        card.findViewById<TextView>(R.id.bookCardTitle).text = book.title
        card.findViewById<TextView>(R.id.bookCardAuthor).text =
            book.author ?: getString(R.string.unknown_author)

        val ratingLabel = book.rating?.let {
            getString(R.string.rating_format, RATING_FORMAT.format(it))
        } ?: getString(R.string.unrated)
        card.findViewById<TextView>(R.id.bookCardMeta).text =
            getString(R.string.book_meta_format, book.status.displayName, ratingLabel)

        card.findViewById<TextView>(R.id.bookCardTags).apply {
            if (book.tags.isEmpty()) {
                visibility = View.GONE
            } else {
                visibility = View.VISIBLE
                text = book.tags.joinToString(" · ")
            }
        }

        return card
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
