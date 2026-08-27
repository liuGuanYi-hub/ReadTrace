package com.example.readtrace.reader

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.viewpager2.widget.ViewPager2
import com.example.readtrace.R
import com.example.readtrace.data.BookDatabaseHelper
import com.example.readtrace.model.Book
import com.example.readtrace.model.Note
import com.example.readtrace.model.NoteType
import com.example.readtrace.ui.BookFlipPageTransformer
import com.example.readtrace.util.FloatingBack

class Book3DReaderActivity : AppCompatActivity() {

    private lateinit var databaseHelper: BookDatabaseHelper
    private lateinit var viewPager: ViewPager2
    private lateinit var pageAdapter: ReaderPageAdapter
    private lateinit var pageIndicator: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var bookTitleText: TextView
    private lateinit var authorNameText: TextView

    private lateinit var themeParchment: TextView
    private lateinit var themeMint: TextView
    private lateinit var themeNight: TextView

    private var bookId: Long = NO_BOOK_ID
    private var currentBook: Book? = null
    private var pages: List<ReaderPage> = emptyList()

    private val importTxtLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null && bookId != NO_BOOK_ID) {
            val success = TxtReaderHelper.importTxtFromUri(this, bookId, uri)
            if (success) {
                Toast.makeText(this, R.string.reader_import_txt_success, Toast.LENGTH_SHORT).show()
                loadBookContent()
            } else {
                Toast.makeText(this, R.string.reader_import_txt_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_book_3d_reader)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.readerRoot)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        databaseHelper = BookDatabaseHelper(this)
        bookId = intent.getLongExtra(EXTRA_BOOK_ID, NO_BOOK_ID)

        if (bookId == NO_BOOK_ID) {
            Toast.makeText(this, R.string.book_not_found, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        bindViews()
        loadBookContent()
    }

    private fun bindViews() {
        viewPager = findViewById(R.id.readerViewPager)
        pageIndicator = findViewById(R.id.readerPageIndicator)
        seekBar = findViewById(R.id.readerSeekBar)
        bookTitleText = findViewById(R.id.readerBookTitle)
        authorNameText = findViewById(R.id.readerAuthorName)

        themeParchment = findViewById(R.id.themeParchment)
        themeMint = findViewById(R.id.themeMint)
        themeNight = findViewById(R.id.themeNight)

        FloatingBack.install(this)

        findViewById<View>(R.id.readerImportTxtButton).setOnClickListener {
            importTxtLauncher.launch(arrayOf("text/plain", "*/*"))
        }

        findViewById<View>(R.id.readerAddExcerptButton).setOnClickListener {
            extractCurrentPageToNote()
        }

        configureThemes()
    }

    private fun loadBookContent() {
        currentBook = databaseHelper.getBook(bookId)
        val book = currentBook
        if (book == null) {
            // 社区展览等入口可能传入本地不存在或已删除的书籍，提示后退出，避免空白页与后续 lateinit 崩溃
            Toast.makeText(this, R.string.book_not_found, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        bookTitleText.text = book.title
        authorNameText.text = book.author ?: "未知创作者"

        val fullText = TxtReaderHelper.loadBookText(this, book, databaseHelper)
        pages = TxtPageSplitter.splitTextIntoPages(fullText)

        pageAdapter = ReaderPageAdapter(pages, book.title, book.author ?: "")
        viewPager.adapter = pageAdapter
        // 绑定 3D 拟真纸张翻页效果
        viewPager.setPageTransformer(BookFlipPageTransformer())

        // 恢复上次阅读进度
        val savedPage = databaseHelper.getReadingPage(bookId).coerceIn(0, pages.size - 1)
        viewPager.setCurrentItem(savedPage, false)
        updateProgressDisplay(savedPage)

        var isInitialSetup = true
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateProgressDisplay(position)
                databaseHelper.saveReadingPage(bookId, position)
                if (!isInitialSetup) {
                    com.example.readtrace.util.HapticFeedbackEngine.pageTurnRustle(this@Book3DReaderActivity)
                    com.example.readtrace.util.SpatialAudioEngine.playPageTurn()
                }
                isInitialSetup = false
            }
        })

        seekBar.max = kotlin.math.max(1, pages.size - 1)
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && progress in pages.indices) {
                    viewPager.setCurrentItem(progress, false)
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
    }

    private fun updateProgressDisplay(position: Int) {
        if (pages.isEmpty()) return
        val current = position + 1
        val total = pages.size
        val percentage = (current * 100) / total
        pageIndicator.text = getString(R.string.reader_page_indicator_format, current, total, percentage)
        seekBar.progress = position
    }

    private fun extractCurrentPageToNote() {
        val currentPosition = viewPager.currentItem
        if (currentPosition !in pages.indices) return
        val page = pages[currentPosition]

        val input = EditText(this).apply {
            hint = "输入你的随想灵感（选填）..."
            setPadding(40, 30, 40, 30)
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.reader_btn_add_excerpt)
            .setMessage("将本页内容沉淀至作品摘录库：\n\n“${page.content.take(120)}...”")
            .setView(input)
            .setPositiveButton("立即沉淀") { _, _ ->
                val thought = input.text.toString().trim()
                val finalContent = if (thought.isNotEmpty()) {
                    "${page.content.trim()}\n\n【随想】$thought"
                } else {
                    page.content.trim()
                }
                val note = Note(
                    bookId = bookId,
                    content = finalContent,
                    noteType = NoteType.QUOTE,
                    page = "第 ${page.pageIndex} 页",
                    chapter = page.chapterTitle,
                )
                databaseHelper.insertNote(note)
                Toast.makeText(this, R.string.reader_excerpt_added_toast, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun configureThemes() {
        val themes = listOf(
            themeParchment to ReaderPageAdapter.ReaderTheme.PARCHMENT,
            themeMint to ReaderPageAdapter.ReaderTheme.MINT,
            themeNight to ReaderPageAdapter.ReaderTheme.NIGHT,
        )

        themes.forEach { (view, theme) ->
            view.setOnClickListener {
                pageAdapter.setTheme(theme)
                updateThemeChips(theme)
            }
        }
    }

    private fun updateThemeChips(selected: ReaderPageAdapter.ReaderTheme) {
        val themeViews = listOf(
            themeParchment to ReaderPageAdapter.ReaderTheme.PARCHMENT,
            themeMint to ReaderPageAdapter.ReaderTheme.MINT,
            themeNight to ReaderPageAdapter.ReaderTheme.NIGHT,
        )
        themeViews.forEach { (view, theme) ->
            val isCurrent = theme == selected
            view.setBackgroundResource(
                if (isCurrent) R.drawable.bg_status_chip_selected else R.drawable.bg_status_chip,
            )
            view.setTextColor(
                ContextCompat.getColor(this, if (isCurrent) R.color.white else R.color.readtrace_ink),
            )
        }
    }

    override fun onDestroy() {
        com.example.readtrace.widget.CurrentlyReadingWidgetProvider.refreshWidgets(this)
        databaseHelper.close()
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_BOOK_ID = "extra_book_id"
        private const val NO_BOOK_ID = -1L

        fun createIntent(context: Context, bookId: Long): Intent =
            Intent(context, Book3DReaderActivity::class.java).apply {
                putExtra(EXTRA_BOOK_ID, bookId)
            }
    }
}
