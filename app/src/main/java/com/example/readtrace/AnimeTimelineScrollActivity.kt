package com.example.readtrace

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.example.readtrace.data.BookDatabaseHelper
import com.example.readtrace.model.MediaType
import com.example.readtrace.util.ThemeHelper
import com.example.readtrace.util.ViewAnimationHelper
import com.example.readtrace.widget.AnimeTimelineScrollView
import com.example.readtrace.util.FloatingBack
import java.io.File
import java.io.FileOutputStream

class AnimeTimelineScrollActivity : AppCompatActivity() {

    private lateinit var databaseHelper: BookDatabaseHelper
    private lateinit var timelineScrollView: AnimeTimelineScrollView
    private lateinit var scrollSubTitle: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_anime_timeline_scroll)

        databaseHelper = BookDatabaseHelper(this)
        timelineScrollView = findViewById(R.id.animeTimelineScrollView)
        scrollSubTitle = findViewById(R.id.scrollSubTitle)

        val btnToggleScrollTheme = findViewById<TextView>(R.id.btnToggleScrollTheme)
        val btnShareScroll = findViewById<TextView>(R.id.btnShareScroll)

        ViewAnimationHelper.attachSpringTouch(btnToggleScrollTheme)
        ViewAnimationHelper.attachSpringTouch(btnShareScroll)

        FloatingBack.install(this)

        // 同步暗黑模式
        timelineScrollView.isDarkMode = ThemeHelper.isDarkMode(this)

        btnToggleScrollTheme.setOnClickListener {
            timelineScrollView.isDarkMode = !timelineScrollView.isDarkMode
            Toast.makeText(
                this,
                if (timelineScrollView.isDarkMode) "已切换为「暗夜和纸」画卷" else "已切换为「白晶宣纸」画卷",
                Toast.LENGTH_SHORT,
            ).show()
        }

        btnShareScroll.setOnClickListener {
            exportAndShareScroll()
        }

        loadAnimeData()
    }

    private fun loadAnimeData() {
        val allBooks = databaseHelper.getBooks()
        val animeList = allBooks.filter { it.mediaType == MediaType.ANIME }

        scrollSubTitle.text = "共收录 ${animeList.size} 部精神坐标"
        timelineScrollView.setAnimeData(animeList)
    }

    private fun exportAndShareScroll() {
        Toast.makeText(this, "正在离屏渲染 1080P 超清追番画卷...", Toast.LENGTH_SHORT).show()

        Thread {
            runCatching {
                val bitmap = timelineScrollView.exportUltraHdBitmap()
                val cacheDir = File(cacheDir, "scrolls").apply { if (!exists()) mkdirs() }
                val file = File(cacheDir, "readtrace_anime_timeline_${System.currentTimeMillis()}.png")

                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }

                val uri: Uri = FileProvider.getUriForFile(
                    this,
                    "${applicationContext.packageName}.fileprovider",
                    file,
                )

                runOnUiThread {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "image/png"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(Intent.EXTRA_TEXT, "✨ 这是我在《阅痕》生成的追番编年史心智画卷，记录了三十载精神共鸣。")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(Intent.createChooser(shareIntent, "分享我的追番心智画卷"))
                }
            }.onFailure {
                runOnUiThread {
                    Toast.makeText(this, "导出画卷失败: ${it.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }
}
