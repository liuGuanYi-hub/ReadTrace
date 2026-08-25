package com.example.readtrace

import android.app.Dialog
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.example.readtrace.data.BookDatabaseHelper
import com.example.readtrace.model.Book
import com.example.readtrace.model.MediaType
import com.example.readtrace.util.CoverImageHelper
import com.example.readtrace.util.ViewAnimationHelper
import com.example.readtrace.widget.CulturalPassportView
import com.example.readtrace.widget.MindprintRadarView
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

class CulturalPassportActivity : AppCompatActivity() {

    private lateinit var databaseHelper: BookDatabaseHelper
    private lateinit var culturalPassportView: CulturalPassportView
    private lateinit var tabPassportAnime: TextView
    private lateinit var tabPassportGame: TextView

    private var allBooks: List<Book> = emptyList()
    private var currentTab: MediaType = MediaType.ANIME

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cultural_passport)

        databaseHelper = BookDatabaseHelper(this)

        initViews()
        loadData()

        val gyroscopeHelper = com.example.readtrace.util.GyroscopeParallaxHelper(this)
        gyroscopeHelper.bind3DParallax(culturalPassportView, maxRotation = 10f, maxTranslation = 14f)
        gyroscopeHelper.bindLifecycle(lifecycle)
    }

    private fun initViews() {
        culturalPassportView = findViewById(R.id.culturalPassportView)
        tabPassportAnime = findViewById(R.id.tabPassportAnime)
        tabPassportGame = findViewById(R.id.tabPassportGame)

        findViewById<View>(R.id.btnPassportBack).setOnClickListener { finish() }
        findViewById<View>(R.id.btnPassportExportTop).setOnClickListener { exportAndSharePassport() }

        tabPassportAnime.setOnClickListener { switchTab(MediaType.ANIME) }
        tabPassportGame.setOnClickListener { switchTab(MediaType.GAME) }

        culturalPassportView.onStampClickListener = { book ->
            showStampDetailDialog(book)
        }

        listOfNotNull(
            findViewById(R.id.btnPassportBack),
            findViewById(R.id.btnPassportExportTop),
            tabPassportAnime,
            tabPassportGame,
        ).forEach { ViewAnimationHelper.attachSpringTouch(it) }
    }

    private fun loadData() {
        allBooks = databaseHelper.getBooks()
        culturalPassportView.setData(allBooks, currentTab)

        val animeCount = allBooks.count { it.mediaType == MediaType.ANIME }
        val gameCount = allBooks.count { it.mediaType == MediaType.GAME }

        tabPassportAnime.text = "🌸 追番入境签证 ($animeCount 部)"
        tabPassportGame.text = "🎮 游戏通关签证 ($gameCount 款)"
    }

    private fun switchTab(tab: MediaType) {
        currentTab = tab
        val isAnime = tab == MediaType.ANIME

        tabPassportAnime.setBackgroundResource(if (isAnime) R.drawable.bg_status_chip_selected else R.drawable.bg_status_chip)
        tabPassportAnime.setTextColor(if (isAnime) Color.WHITE else Color.parseColor("#C5BCAD"))

        tabPassportGame.setBackgroundResource(if (!isAnime) R.drawable.bg_status_chip_selected else R.drawable.bg_status_chip)
        tabPassportGame.setTextColor(if (!isAnime) Color.WHITE else Color.parseColor("#C5BCAD"))

        culturalPassportView.setTab(tab)
    }

    private fun showStampDetailDialog(book: Book) {
        val dialog = Dialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_book_hologram_peek, null)
        dialog.setContentView(view)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.90).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )

        val coverImage = view.findViewById<ImageView>(R.id.peekCoverImage)
        val titleText = view.findViewById<TextView>(R.id.peekBookTitle)
        val authorText = view.findViewById<TextView>(R.id.peekBookAuthor)
        val mediaBadge = view.findViewById<TextView>(R.id.peekMediaBadge)
        val statusBadge = view.findViewById<TextView>(R.id.peekStatusBadge)
        val ratingText = view.findViewById<TextView>(R.id.peekRating)
        val radarView = view.findViewById<MindprintRadarView>(R.id.peekMindprintRadar)
        val btnDetail = view.findViewById<View>(R.id.peekActionDetail)

        CoverImageHelper.loadCover(coverImage, book.coverUrl)
        mediaBadge.text = book.mediaType.emoji
        titleText.text = book.title
        authorText.text = "${book.category.orEmpty()} · ${book.author.orEmpty()}"
        statusBadge.text = book.status.getDisplayName(book.mediaType)
        ratingText.text = "★ ${book.rating ?: 5.0}"

        val mp = databaseHelper.getMindprint(book.id)
        radarView.setMindprint(mp, animate = false)

        btnDetail.setOnClickListener {
            dialog.dismiss()
            startActivity(BookDetailActivity.createIntent(this, book.id))
        }

        // 点击其他快捷按钮也关闭弹窗
        view.findViewById<View>(R.id.peekActionTimer).setOnClickListener { dialog.dismiss() }
        view.findViewById<View>(R.id.peekActionPoster).setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    private fun exportAndSharePassport() {
        runCatching {
            val label = if (currentTab == MediaType.ANIME) "追番签证" else "游戏通关签证"
            Toast.makeText(this, "正在生成 1080P 精神巡礼护照长图...", Toast.LENGTH_SHORT).show()
            val bitmap = culturalPassportView.exportUltraHdPassportBitmap()
            val cacheFile = File(cacheDir, "readtrace_passport_${System.currentTimeMillis()}.png")
            FileOutputStream(cacheFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", cacheFile)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "《阅痕》精神宇宙巡礼护照")
                putExtra(Intent.EXTRA_TEXT, "🛂 我的《阅痕》$label · 精神宇宙签证印章簿")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "分享精神巡礼护照"))
        }.onFailure {
            Toast.makeText(this, "导出分享失败: ${it.message}", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        fun createIntent(context: Context, initialTab: MediaType = MediaType.ANIME): Intent {
            return Intent(context, CulturalPassportActivity::class.java).apply {
                putExtra("extra_tab", initialTab.name)
            }
        }
    }
}
