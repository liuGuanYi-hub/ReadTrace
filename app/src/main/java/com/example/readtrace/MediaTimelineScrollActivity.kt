package com.example.readtrace

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.readtrace.data.BookDatabaseHelper
import com.example.readtrace.model.Book
import com.example.readtrace.model.MediaType
import com.example.readtrace.util.FloatingBack
import com.example.readtrace.util.ThemeHelper
import com.example.readtrace.util.ViewAnimationHelper
import com.example.readtrace.widget.MediaTimelineScrollView
import java.io.File
import java.io.FileOutputStream

open class MediaTimelineScrollActivity : AppCompatActivity() {

    protected lateinit var databaseHelper: BookDatabaseHelper
    protected lateinit var timelineScrollView: MediaTimelineScrollView
    protected lateinit var scrollMainTitle: TextView
    protected lateinit var scrollSubTitle: TextView

    private lateinit var chipBook: TextView
    private lateinit var chipAnime: TextView
    private lateinit var chipMovie: TextView
    private lateinit var chipGame: TextView
    private lateinit var chipMusic: TextView

    /** 当前画卷媒介；仅保留五个单独分类，默认书籍（P38 后用户决策：移除「全部全景」） */
    protected var selectedMediaType: MediaType = MediaType.BOOK

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_media_timeline_scroll)

        databaseHelper = BookDatabaseHelper.getInstance(this)
        timelineScrollView = findViewById(R.id.mediaTimelineScrollView)
        scrollMainTitle = findViewById(R.id.scrollMainTitle)
        scrollSubTitle = findViewById(R.id.scrollSubTitle)

        chipBook = findViewById(R.id.chipMediaBook)
        chipAnime = findViewById(R.id.chipMediaAnime)
        chipMovie = findViewById(R.id.chipMediaMovie)
        chipGame = findViewById(R.id.chipMediaGame)
        chipMusic = findViewById(R.id.chipMediaMusic)

        val btnShareScroll = findViewById<TextView>(R.id.btnShareScroll)

        listOf(
            btnShareScroll,
            chipBook,
            chipAnime,
            chipMovie,
            chipGame,
            chipMusic,
        ).forEach { ViewAnimationHelper.attachSpringTouch(it) }

        FloatingBack.install(this)

        // 解析传入的目标媒介类型；未指定时默认书籍
        val initialMediaVal = intent.getStringExtra(EXTRA_MEDIA_TYPE)
        selectedMediaType = initialMediaVal?.let { MediaType.fromDatabaseValue(it) } ?: MediaType.BOOK

        // 同步暗黑模式
        timelineScrollView.isDarkMode = ThemeHelper.isDarkMode(this)

        btnShareScroll.setOnClickListener {
            exportAndShareScroll()
        }

        timelineScrollView.onBookClickListener = { book ->
            val intent = BookDetailActivity.createIntent(this, book.id)
            startActivity(intent)
        }

        setupMediaChips()
        updateChipSelectionUI()
        loadTimelineData()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val newMediaVal = intent.getStringExtra(EXTRA_MEDIA_TYPE)
        selectedMediaType = newMediaVal?.let { MediaType.fromDatabaseValue(it) } ?: MediaType.BOOK
        updateChipSelectionUI()
        loadTimelineData()
    }

    private fun setupMediaChips() {
        chipBook.setOnClickListener { selectMediaType(MediaType.BOOK) }
        chipAnime.setOnClickListener { selectMediaType(MediaType.ANIME) }
        chipMovie.setOnClickListener { selectMediaType(MediaType.MOVIE) }
        chipGame.setOnClickListener { selectMediaType(MediaType.GAME) }
        chipMusic.setOnClickListener { selectMediaType(MediaType.MUSIC) }
    }

    private fun selectMediaType(mediaType: MediaType) {
        if (selectedMediaType == mediaType) return
        selectedMediaType = mediaType
        updateChipSelectionUI()
        loadTimelineData()
    }

    private fun updateChipSelectionUI() {
        val chipPairs = listOf(
            chipBook to (selectedMediaType == MediaType.BOOK),
            chipAnime to (selectedMediaType == MediaType.ANIME),
            chipMovie to (selectedMediaType == MediaType.MOVIE),
            chipGame to (selectedMediaType == MediaType.GAME),
            chipMusic to (selectedMediaType == MediaType.MUSIC),
        )

        chipPairs.forEach { (chip, isSelected) ->
            chip.setBackgroundResource(if (isSelected) R.drawable.bg_status_chip_selected else R.drawable.bg_status_chip)
            chip.setTextColor(ContextCompat.getColor(this, if (isSelected) R.color.white else R.color.readtrace_ink))
        }
    }

    private fun loadTimelineData() {
        val filteredBooks = databaseHelper.getBooks().filter { it.mediaType == selectedMediaType }

        // 更新顶部标题
        scrollMainTitle.text = when (selectedMediaType) {
            MediaType.BOOK -> "📜 阅历编年史 · 文学画卷"
            MediaType.ANIME -> "📜 追番编年史 · 心智画卷"
            MediaType.MOVIE -> "📜 光影编年史 · 影画长卷"
            MediaType.GAME -> "📜 游戏编年史 · 征程画卷"
            MediaType.MUSIC -> "📜 乐音编年史 · 旋律画卷"
        }

        // 计算年份跨度
        val allYears = filteredBooks.mapNotNull { timelineScrollView.extractYearInt(it) }
        val minYear = allYears.minOrNull()
        val maxYear = allYears.maxOrNull()

        val unitName = when (selectedMediaType) {
            MediaType.BOOK -> "本藏书"
            MediaType.MOVIE -> "部光影"
            MediaType.GAME -> "款神作"
            MediaType.MUSIC -> "首曲目"
            MediaType.ANIME -> "部番剧"
        }

        val timeSpanStr = if (minYear != null && maxYear != null) {
            if (minYear == maxYear) "$minYear 年" else "$minYear ~ $maxYear"
        } else {
            "全景时光"
        }

        scrollSubTitle.text = "$timeSpanStr · 共收录 ${filteredBooks.size} $unitName"
        timelineScrollView.setTimelineData(filteredBooks, selectedMediaType)
    }

    private fun exportAndShareScroll() {
        val mediaName = selectedMediaType.displayName
        Toast.makeText(this, "正在离屏渲染 1080P 超清${mediaName}画卷...", Toast.LENGTH_SHORT).show()

        Thread {
            runCatching {
                val bitmap = timelineScrollView.exportUltraHdBitmap()
                val cacheDir = File(cacheDir, "scrolls").apply { if (!exists()) mkdirs() }
                val mediaTag = selectedMediaType.databaseValue
                val file = File(cacheDir, "readtrace_${mediaTag}_timeline_${System.currentTimeMillis()}.png")

                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }

                val uri: Uri = FileProvider.getUriForFile(
                    this,
                    "${applicationContext.packageName}.fileprovider",
                    file,
                )

                val books = timelineScrollView.bookList
                val allYears = books.mapNotNull { timelineScrollView.extractYearInt(it) }
                val minYear = allYears.minOrNull()
                val maxYear = allYears.maxOrNull()
                val shareSpan = if (minYear != null && maxYear != null) {
                    val span = maxYear - minYear + 1
                    "历经 $span 载光阴 ($minYear~$maxYear)"
                } else {
                    "全景岁月"
                }

                val shareSubject = when (selectedMediaType) {
                    MediaType.BOOK -> "文学阅历画卷"
                    MediaType.ANIME -> "追番心智画卷"
                    MediaType.MOVIE -> "光影视听长卷"
                    MediaType.GAME -> "游戏征程卷轴"
                    MediaType.MUSIC -> "音乐旋律长卷"
                }

                runOnUiThread {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "image/png"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(
                            Intent.EXTRA_TEXT,
                            "✨ 这是我在《阅痕》生成的「${shareSubject}」，记录了${shareSpan}的精神共鸣与 ${books.size} 部作品坐标。",
                        )
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(Intent.createChooser(shareIntent, "分享我的「${shareSubject}」"))
                }
            }.onFailure {
                runOnUiThread {
                    Toast.makeText(this, "导出画卷失败: ${it.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    companion object {
        const val EXTRA_MEDIA_TYPE = "extra_media_type"

        fun createIntent(context: Context, mediaType: MediaType = MediaType.BOOK): Intent {
            return Intent(context, MediaTimelineScrollActivity::class.java).apply {
                putExtra(EXTRA_MEDIA_TYPE, mediaType.databaseValue)
            }
        }
    }
}
