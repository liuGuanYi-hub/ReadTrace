package com.example.readtrace

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.readtrace.util.CoverImageHelper
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class QuotePosterActivity : AppCompatActivity() {

    private var bookId: Long = 0L
    private var bookTitle: String = ""
    private var bookAuthor: String? = null
    private var bookCover: String? = null
    private var quoteContent: String = ""
    private var quoteSource: String? = null

    private lateinit var posterCardContainer: FrameLayout
    private lateinit var posterCoverImage: ImageView
    private lateinit var posterBookTitle: TextView
    private lateinit var posterBookAuthor: TextView
    private lateinit var posterQuoteSymbol: TextView
    private lateinit var posterQuoteText: TextView
    private lateinit var posterQuoteSource: TextView
    private lateinit var posterDivider: View
    private lateinit var posterBottomDivider: View
    private lateinit var posterWatermark: TextView
    private lateinit var posterDateText: TextView
    private lateinit var posterSealStamp: TextView

    private lateinit var themeParchmentBtn: TextView
    private lateinit var themeCosmosBtn: TextView
    private lateinit var themeJadeBtn: TextView
    private lateinit var themeGraphiteBtn: TextView

    private enum class PosterTheme {
        PARCHMENT, COSMOS, JADE, GRAPHITE
    }

    private var currentTheme = PosterTheme.PARCHMENT

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_quote_poster)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.posterRoot)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        bookId = intent.getLongExtra(EXTRA_BOOK_ID, 0L)
        bookTitle = intent.getStringExtra(EXTRA_BOOK_TITLE).orEmpty()
        bookAuthor = intent.getStringExtra(EXTRA_BOOK_AUTHOR)
        bookCover = intent.getStringExtra(EXTRA_BOOK_COVER)
        quoteContent = intent.getStringExtra(EXTRA_QUOTE_CONTENT).orEmpty()
        quoteSource = intent.getStringExtra(EXTRA_QUOTE_SOURCE)

        bindViews()
        renderContent()
        applyTheme(PosterTheme.PARCHMENT)
    }

    private fun bindViews() {
        posterCardContainer = findViewById(R.id.posterCardContainer)
        posterCoverImage = findViewById(R.id.posterCoverImage)
        posterBookTitle = findViewById(R.id.posterBookTitle)
        posterBookAuthor = findViewById(R.id.posterBookAuthor)
        posterQuoteSymbol = findViewById(R.id.posterQuoteSymbol)
        posterQuoteText = findViewById(R.id.posterQuoteText)
        posterQuoteSource = findViewById(R.id.posterQuoteSource)
        posterDivider = findViewById(R.id.posterDivider)
        posterBottomDivider = findViewById(R.id.posterBottomDivider)
        posterWatermark = findViewById(R.id.posterWatermark)
        posterDateText = findViewById(R.id.posterDateText)
        posterSealStamp = findViewById(R.id.posterSealStamp)

        themeParchmentBtn = findViewById(R.id.themeParchment)
        themeCosmosBtn = findViewById(R.id.themeCosmos)
        themeJadeBtn = findViewById(R.id.themeJade)
        themeGraphiteBtn = findViewById(R.id.themeGraphite)

        findViewById<View>(R.id.posterBackBtn).setOnClickListener { finish() }
        findViewById<View>(R.id.posterSaveBtn).setOnClickListener { savePosterToGallery() }
        findViewById<View>(R.id.posterShareBtn).setOnClickListener { sharePoster() }

        themeParchmentBtn.setOnClickListener { applyTheme(PosterTheme.PARCHMENT) }
        themeCosmosBtn.setOnClickListener { applyTheme(PosterTheme.COSMOS) }
        themeJadeBtn.setOnClickListener { applyTheme(PosterTheme.JADE) }
        themeGraphiteBtn.setOnClickListener { applyTheme(PosterTheme.GRAPHITE) }
    }

    private fun renderContent() {
        posterBookTitle.text = if (bookTitle.isNotBlank()) "《$bookTitle》" else "阅痕摘录"
        posterBookAuthor.text = bookAuthor?.let { "$it 著" } ?: "无作者信息"
        posterQuoteText.text = quoteContent.ifBlank { "字句有痕，岁月有温。" }

        if (quoteSource.isNullOrBlank()) {
            posterQuoteSource.visibility = View.GONE
        } else {
            posterQuoteSource.visibility = View.VISIBLE
            posterQuoteSource.text = "— $quoteSource"
        }

        posterDateText.text = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy.MM.dd"))
        CoverImageHelper.loadCover(posterCoverImage, bookCover)
    }

    private fun applyTheme(theme: PosterTheme) {
        currentTheme = theme
        updateThemeButtons()

        when (theme) {
            PosterTheme.PARCHMENT -> {
                posterCardContainer.setBackgroundColor(Color.parseColor("#FAF8F5"))
                posterBookTitle.setTextColor(Color.parseColor("#2D2824"))
                posterBookAuthor.setTextColor(Color.parseColor("#7A7067"))
                posterQuoteSymbol.setTextColor(Color.parseColor("#C47D5C"))
                posterQuoteText.setTextColor(Color.parseColor("#2D2824"))
                posterQuoteSource.setTextColor(Color.parseColor("#9E9284"))
                posterDivider.setBackgroundColor(Color.parseColor("#E8E2D9"))
                posterBottomDivider.setBackgroundColor(Color.parseColor("#E8E2D9"))
                posterWatermark.setTextColor(Color.parseColor("#B0A69A"))
                posterDateText.setTextColor(Color.parseColor("#B0A69A"))
                posterSealStamp.setBackgroundResource(R.drawable.bg_status_chip_selected)
                posterSealStamp.setTextColor(Color.WHITE)
            }
            PosterTheme.COSMOS -> {
                posterCardContainer.setBackgroundColor(Color.parseColor("#0F172A"))
                posterBookTitle.setTextColor(Color.parseColor("#F8FAFC"))
                posterBookAuthor.setTextColor(Color.parseColor("#94A3B8"))
                posterQuoteSymbol.setTextColor(Color.parseColor("#38BDF8"))
                posterQuoteText.setTextColor(Color.parseColor("#F8FAFC"))
                posterQuoteSource.setTextColor(Color.parseColor("#94A3B8"))
                posterDivider.setBackgroundColor(Color.parseColor("#1E293B"))
                posterBottomDivider.setBackgroundColor(Color.parseColor("#1E293B"))
                posterWatermark.setTextColor(Color.parseColor("#64748B"))
                posterDateText.setTextColor(Color.parseColor("#64748B"))
                posterSealStamp.setBackgroundResource(R.drawable.bg_primary_button)
                posterSealStamp.setTextColor(Color.WHITE)
            }
            PosterTheme.JADE -> {
                posterCardContainer.setBackgroundColor(Color.parseColor("#F0FDF4"))
                posterBookTitle.setTextColor(Color.parseColor("#14532D"))
                posterBookAuthor.setTextColor(Color.parseColor("#166534"))
                posterQuoteSymbol.setTextColor(Color.parseColor("#16A34A"))
                posterQuoteText.setTextColor(Color.parseColor("#14532D"))
                posterQuoteSource.setTextColor(Color.parseColor("#15803D"))
                posterDivider.setBackgroundColor(Color.parseColor("#DCFCE7"))
                posterBottomDivider.setBackgroundColor(Color.parseColor("#DCFCE7"))
                posterWatermark.setTextColor(Color.parseColor("#86EFAC"))
                posterDateText.setTextColor(Color.parseColor("#86EFAC"))
                posterSealStamp.setBackgroundResource(R.drawable.bg_status_pill)
                posterSealStamp.setTextColor(Color.parseColor("#14532D"))
            }
            PosterTheme.GRAPHITE -> {
                posterCardContainer.setBackgroundColor(Color.parseColor("#18181B"))
                posterBookTitle.setTextColor(Color.parseColor("#F4F4F5"))
                posterBookAuthor.setTextColor(Color.parseColor("#A1A1AA"))
                posterQuoteSymbol.setTextColor(Color.parseColor("#F59E0B"))
                posterQuoteText.setTextColor(Color.parseColor("#F4F4F5"))
                posterQuoteSource.setTextColor(Color.parseColor("#A1A1AA"))
                posterDivider.setBackgroundColor(Color.parseColor("#27272A"))
                posterBottomDivider.setBackgroundColor(Color.parseColor("#27272A"))
                posterWatermark.setTextColor(Color.parseColor("#71717A"))
                posterDateText.setTextColor(Color.parseColor("#71717A"))
                posterSealStamp.setBackgroundResource(R.drawable.bg_secondary_button)
                posterSealStamp.setTextColor(Color.parseColor("#F59E0B"))
            }
        }
    }

    private fun updateThemeButtons() {
        val buttons = listOf(
            themeParchmentBtn to PosterTheme.PARCHMENT,
            themeCosmosBtn to PosterTheme.COSMOS,
            themeJadeBtn to PosterTheme.JADE,
            themeGraphiteBtn to PosterTheme.GRAPHITE,
        )
        buttons.forEach { (btn, theme) ->
            val isSelected = currentTheme == theme
            btn.setBackgroundResource(if (isSelected) R.drawable.bg_status_chip_selected else R.drawable.bg_status_chip)
            btn.setTextColor(ContextCompat.getColor(this, if (isSelected) R.color.white else R.color.readtrace_ink))
        }
    }

    private fun capturePosterBitmap(): Bitmap {
        val view = posterCardContainer
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        view.draw(canvas)
        return bitmap
    }

    private fun savePosterToGallery() {
        val bitmap = capturePosterBitmap()
        val filename = "ReadTrace_Poster_${System.currentTimeMillis()}.png"
        var fos: OutputStream? = null
        var success = false

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ReadTrace")
                }
                val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                if (imageUri != null) {
                    fos = resolver.openOutputStream(imageUri)
                    success = bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos!!)
                }
            } else {
                val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toString() + "/ReadTrace"
                val file = File(imagesDir)
                if (!file.exists()) file.mkdirs()
                val imageFile = File(file, filename)
                fos = FileOutputStream(imageFile)
                success = bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            fos?.close()
        }

        if (success) {
            Toast.makeText(this, "🎉 金句海报已成功保存至系统相册！", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "保存海报失败，请稍后重试", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sharePoster() {
        val bitmap = capturePosterBitmap()
        try {
            val cachePath = File(cacheDir, "images")
            cachePath.mkdirs()
            val file = File(cachePath, "share_poster.png")
            val stream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.close()

            val contentUri: Uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_SUBJECT, "《$bookTitle》金句印记 · 阅痕 ReadTrace")
                putExtra(Intent.EXTRA_TEXT, "《$bookTitle》：$quoteContent")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "分享金句海报"))
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "分享海报失败", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private const val EXTRA_BOOK_ID = "extra_book_id"
        private const val EXTRA_BOOK_TITLE = "extra_book_title"
        private const val EXTRA_BOOK_AUTHOR = "extra_book_author"
        private const val EXTRA_BOOK_COVER = "extra_book_cover"
        private const val EXTRA_QUOTE_CONTENT = "extra_quote_content"
        private const val EXTRA_QUOTE_SOURCE = "extra_quote_source"

        fun createIntent(
            context: Context,
            bookId: Long,
            bookTitle: String,
            bookAuthor: String?,
            bookCover: String?,
            quoteContent: String,
            quoteSource: String? = null,
        ): Intent =
            Intent(context, QuotePosterActivity::class.java).apply {
                putExtra(EXTRA_BOOK_ID, bookId)
                putExtra(EXTRA_BOOK_TITLE, bookTitle)
                putExtra(EXTRA_BOOK_AUTHOR, bookAuthor)
                putExtra(EXTRA_BOOK_COVER, bookCover)
                putExtra(EXTRA_QUOTE_CONTENT, quoteContent)
                putExtra(EXTRA_QUOTE_SOURCE, quoteSource)
            }
    }
}
