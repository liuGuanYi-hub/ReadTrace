package com.example.readtrace

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.example.readtrace.data.BookDatabaseHelper
import com.example.readtrace.model.Book
import com.example.readtrace.util.CoverImageHelper
import com.example.readtrace.util.HapticFeedbackEngine
import com.example.readtrace.util.SonicHapticMatrix
import com.example.readtrace.widget.DioramaBoxView
import com.example.readtrace.widget.EditorialBadgeView
import com.example.readtrace.widget.ExLibrisStampView
import com.example.readtrace.util.FloatingBack
import java.io.File
import java.io.FileOutputStream

/**
 * 📜 典藏藏书票与生成式艺术工坊 (Ex-Libris Generative Studio Activity)
 * 对标 One Page Love / Land-book 瑞士排版海报与欧洲古籍藏书票。
 */
class ExLibrisStudioActivity : AppCompatActivity() {

    private lateinit var databaseHelper: BookDatabaseHelper
    private lateinit var exLibrisStampView: ExLibrisStampView
    private lateinit var dioramaStampContainer: DioramaBoxView
    private lateinit var studioBadge: EditorialBadgeView
    private lateinit var etCustomQuote: EditText

    private var currentBook: Book? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ex_libris_studio)

        databaseHelper = BookDatabaseHelper(this)
        initViews()
        loadBookData()
    }

    private fun initViews() {
        exLibrisStampView = findViewById(R.id.exLibrisStampView)
        dioramaStampContainer = findViewById(R.id.dioramaStampContainer)
        studioBadge = findViewById(R.id.studioBadge)
        etCustomQuote = findViewById(R.id.etCustomQuote)

        studioBadge.setBadgeContent("EX-LIBRIS", "#C8A265")

        FloatingBack.install(this)

        val btnSwitch = findViewById<android.widget.TextView>(R.id.btnSwitchExLibrisBook)
        btnSwitch?.setOnClickListener {
            val allBooks = databaseHelper.getBooks()
            if (allBooks.isEmpty()) {
                Toast.makeText(this, "书库中暂无藏书数据", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            com.example.readtrace.ui.bottomsheet.WorkPickerBottomSheet.show(
                fragmentManager = supportFragmentManager,
                title = "📜 选择要定制藏书票的作品",
                works = allBooks,
                selectedWorkId = currentBook?.id,
                onSelected = { book ->
                    currentBook = book
                    HapticFeedbackEngine.pageTurnRustle(this)
                    loadBookData()
                    Toast.makeText(this, "已切换为《${book.title}》", Toast.LENGTH_SHORT).show()
                },
            )
        }
        com.example.readtrace.util.ViewAnimationHelper.attachSpringTouch(btnSwitch)

        // 主题滤镜切换
        findViewById<Button>(R.id.btnThemeParchment).setOnClickListener {
            SonicHapticMatrix.playParchmentRustle(this)
            exLibrisStampView.currentTheme = ExLibrisStampView.Theme.PARCHMENT
        }
        findViewById<Button>(R.id.btnThemeMidnight).setOnClickListener {
            SonicHapticMatrix.playParchmentRustle(this)
            exLibrisStampView.currentTheme = ExLibrisStampView.Theme.MIDNIGHT
        }
        findViewById<Button>(R.id.btnThemeWoodcut).setOnClickListener {
            SonicHapticMatrix.playParchmentRustle(this)
            exLibrisStampView.currentTheme = ExLibrisStampView.Theme.WOODCUT
        }
        findViewById<Button>(R.id.btnThemeCyber).setOnClickListener {
            SonicHapticMatrix.playParchmentRustle(this)
            exLibrisStampView.currentTheme = ExLibrisStampView.Theme.CYBER
        }

        // 箴言即时编辑
        etCustomQuote.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                exLibrisStampView.quoteText = s?.toString() ?: ""
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // 4K 海报导出与分享
        findViewById<Button>(R.id.btnExportExLibrisPoster).setOnClickListener {
            exportAndSharePoster()
        }
    }

    private fun loadBookData() {
        val bookId = intent.getLongExtra(EXTRA_BOOK_ID, -1L)
        currentBook = if (bookId != -1L) {
            databaseHelper.getBook(bookId)
        } else {
            databaseHelper.getBooks().firstOrNull()
        }

        currentBook?.let { book ->
            exLibrisStampView.bookTitle = book.title
            exLibrisStampView.authorName = book.author ?: "未知创作者"
            exLibrisStampView.serialNumber = "#EXL-${System.currentTimeMillis() % 100000} // NO.${book.id}"
            if (!book.shortComment.isNullOrBlank()) {
                exLibrisStampView.quoteText = book.shortComment
                etCustomQuote.setText(book.shortComment)
            }
            CoverImageHelper.loadCoverBitmap(book.coverUrl) { bmp ->
                exLibrisStampView.coverBitmap = bmp
            }
        }
    }

    private fun exportAndSharePoster() {
        SonicHapticMatrix.playWaxSealThud(this)
        try {
            val bitmap = exLibrisStampView.createHighResBitmap()
            val cachePath = File(cacheDir, "posters")
            cachePath.mkdirs()
            val file = File(cachePath, "ex_libris_${System.currentTimeMillis()}.png")
            val stream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.close()

            val contentUri: Uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "分享您的 4K 典藏藏书票"))
        } catch (e: Exception) {
            Toast.makeText(this, "导出失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private const val EXTRA_BOOK_ID = "extra_book_id"

        fun createIntent(context: Context, bookId: Long): Intent {
            return Intent(context, ExLibrisStudioActivity::class.java).apply {
                putExtra(EXTRA_BOOK_ID, bookId)
            }
        }
    }
}
