package com.example.readtrace

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.readtrace.data.BookDatabaseHelper
import com.example.readtrace.util.ViewAnimationHelper
import com.example.readtrace.widget.ResonancePosterView
import com.example.readtrace.util.FloatingBack
import java.io.File
import java.io.FileOutputStream

class ResonancePosterActivity : AppCompatActivity() {

    private lateinit var databaseHelper: BookDatabaseHelper
    private lateinit var resonancePosterView: ResonancePosterView

    private var bookAId: Long = -1L
    private var bookBId: Long = -1L
    private var similarity: Int = 94
    private var resonanceTrait: String = "存在主义思辨 · 终极孤独"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_resonance_poster)

        databaseHelper = BookDatabaseHelper(this)

        bookAId = intent.getLongExtra(EXTRA_BOOK_A_ID, -1L)
        bookBId = intent.getLongExtra(EXTRA_BOOK_B_ID, -1L)
        similarity = intent.getIntExtra(EXTRA_SIMILARITY, 94)
        resonanceTrait = intent.getStringExtra(EXTRA_RESONANCE_TRAIT) ?: "存在主义思辨 · 终极孤独"

        initViews()
        loadData()

        val gyroscopeHelper = com.example.readtrace.util.GyroscopeParallaxHelper(this)
        gyroscopeHelper.bind3DParallax(resonancePosterView, maxRotation = 10f, maxTranslation = 14f)
        gyroscopeHelper.bindLifecycle(lifecycle)
    }

    private fun initViews() {
        resonancePosterView = findViewById(R.id.resonancePosterView)
        resonancePosterView.setOnClickListener {
            com.example.readtrace.util.HapticFeedbackEngine.celestialResonancePulse(this)
            com.example.readtrace.util.SpatialAudioEngine.playCelestialTone()
            com.example.readtrace.util.ConfettiBurstHelper.burstCenter(this)
        }

        FloatingBack.install(this)

        val btnShareTop = findViewById<TextView>(R.id.btnPosterShareTop)
        btnShareTop.setOnClickListener { exportAndSharePoster() }
        ViewAnimationHelper.attachSpringTouch(btnShareTop)

        val btnExport = findViewById<TextView>(R.id.btnExportResonancePoster)
        btnExport.setOnClickListener { exportAndSharePoster() }
        ViewAnimationHelper.attachSpringTouch(btnExport)

        setupThemeTabs()
    }

    private fun setupThemeTabs() {
        val themes = listOf(
            findViewById<TextView>(R.id.themeObsidian) to ResonancePosterView.PosterTheme.OBSIDIAN,
            findViewById<TextView>(R.id.themeRicePaper) to ResonancePosterView.PosterTheme.RICE_PAPER,
            findViewById<TextView>(R.id.themeCyber) to ResonancePosterView.PosterTheme.CYBER,
            findViewById<TextView>(R.id.themeSunset) to ResonancePosterView.PosterTheme.SUNSET,
        )

        themes.forEach { (tab, theme) ->
            tab.setOnClickListener {
                themes.forEach { (t, th) ->
                    val isSelected = th == theme
                    t.setBackgroundResource(if (isSelected) R.drawable.bg_status_chip_selected else R.drawable.bg_status_chip)
                    t.setTextColor(ContextCompat.getColor(this, if (isSelected) R.color.white else R.color.readtrace_ink))
                }
                resonancePosterView.setTheme(theme)
            }
            ViewAnimationHelper.attachSpringTouch(tab)
        }
    }

    private fun loadData() {
        val bookA = databaseHelper.getBook(bookAId)
        val bookB = databaseHelper.getBook(bookBId)

        if (bookA == null || bookB == null) {
            Toast.makeText(this, "未找到双生共鸣作品数据", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val mpA = databaseHelper.getMindprint(bookAId)
        val mpB = databaseHelper.getMindprint(bookBId)

        resonancePosterView.setData(
            bookA = bookA,
            mindprintA = mpA,
            bookB = bookB,
            mindprintB = mpB,
            similarity = similarity,
            resonanceTrait = resonanceTrait,
        )
    }

    private fun exportAndSharePoster() {
        runCatching {
            Toast.makeText(this, "正在生成 1080P 双生共鸣超清微卡...", Toast.LENGTH_SHORT).show()
            val bitmap = resonancePosterView.exportUltraHdBitmap(1080)
            val cacheFile = File(cacheDir, "readtrace_twin_resonance_${System.currentTimeMillis()}.png")
            FileOutputStream(cacheFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", cacheFile)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "《阅痕》双生共鸣精神微卡")
                putExtra(Intent.EXTRA_TEXT, "✨ 跨媒介灵魂共鸣：$resonanceTrait · $similarity% 契合度")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "分享双生共鸣微卡"))
        }.onFailure {
            Toast.makeText(this, "导出分享失败: ${it.message}", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        const val EXTRA_BOOK_A_ID = "extra_book_a_id"
        const val EXTRA_BOOK_B_ID = "extra_book_b_id"
        const val EXTRA_SIMILARITY = "extra_similarity"
        const val EXTRA_RESONANCE_TRAIT = "extra_resonance_trait"

        fun createIntent(
            context: Context,
            bookAId: Long,
            bookBId: Long,
            similarity: Int,
            resonanceTrait: String,
        ): Intent {
            return Intent(context, ResonancePosterActivity::class.java).apply {
                putExtra(EXTRA_BOOK_A_ID, bookAId)
                putExtra(EXTRA_BOOK_B_ID, bookBId)
                putExtra(EXTRA_SIMILARITY, similarity)
                putExtra(EXTRA_RESONANCE_TRAIT, resonanceTrait)
            }
        }
    }
}
