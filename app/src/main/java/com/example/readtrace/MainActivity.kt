package com.example.readtrace

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.readtrace.community.ui.CommunityActivity
import com.example.readtrace.data.BookDatabaseHelper
import com.example.readtrace.model.Book
import com.example.readtrace.model.BookStatus
import com.example.readtrace.model.MediaType
import com.example.readtrace.util.BookCsvParser
import com.example.readtrace.util.CoverImageHelper
import com.example.readtrace.util.MilestoneBadgeHelper
import com.example.readtrace.util.ThemeHelper
import com.example.readtrace.util.ViewAnimationHelper
import com.example.readtrace.widget.MindprintRadarView
import java.text.DecimalFormat
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private lateinit var databaseHelper: BookDatabaseHelper

    // Header & Stats
    private lateinit var homeTitle: TextView
    private lateinit var homeSubtitle: TextView
    private lateinit var themeToggleButton: TextView
    private lateinit var addBtn: TextView
    private lateinit var importPresetBtn: TextView
    private lateinit var backupBtn: TextView
    private lateinit var trashBtn: TextView

    private lateinit var statTotalValue: TextView
    private lateinit var statReadingValue: TextView
    private lateinit var statFinishedValue: TextView
    private lateinit var statAverageValue: TextView

    // Four Big Media Hub Cards
    private lateinit var hubCardBook: View
    private lateinit var hubBookCountBadge: TextView
    private lateinit var hubBookSubtitle: TextView
    private lateinit var hubBookCoversPreview: LinearLayout
    private lateinit var btnEnterBookHub: View
    private lateinit var btnQuickReader3D: View

    private lateinit var hubCardAnime: View
    private lateinit var hubAnimeCountBadge: TextView
    private lateinit var hubAnimeSubtitle: TextView
    private lateinit var hubAnimeCoversPreview: LinearLayout
    private lateinit var btnEnterAnimeHub: View
    private lateinit var btnQuickAnimeTimeline: View
    private lateinit var btnQuickAnimePassport: View

    private lateinit var hubCardMovie: View
    private lateinit var hubMovieCountBadge: TextView
    private lateinit var hubMovieSubtitle: TextView
    private lateinit var hubMovieCoversPreview: LinearLayout
    private lateinit var btnEnterMovieHub: View
    private lateinit var btnQuickMovieTicket: View

    private lateinit var hubCardGame: View
    private lateinit var hubGameCountBadge: TextView
    private lateinit var hubGameSubtitle: TextView
    private lateinit var hubGameCoversPreview: LinearLayout
    private lateinit var btnEnterGameHub: View
    private lateinit var btnQuickGameCartridge: View
    private lateinit var btnQuickGamePassport: View

    // Explorations & Insights
    private lateinit var homeConstellationPanel: View
    private lateinit var homeGalleryPanel: View
    private lateinit var homeGallerySummary: TextView
    private lateinit var homeBadgePanel: View
    private lateinit var homeBadgeSummary: TextView
    private lateinit var homeCommunityPanel: View

    private lateinit var memoryPanel: View
    private lateinit var memoryTitle: TextView
    private lateinit var memoryBookTitle: TextView
    private lateinit var memoryBookQuote: TextView

    private lateinit var annualPersonaPanel: View
    private lateinit var annualPersonaBadge: TextView
    private lateinit var annualPersonaDesc: TextView
    private lateinit var annualMindprintRadar: MindprintRadarView

    private val selectCsvLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            importCustomCsv(uri)
        }
    }

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

        initViews()
        setupListeners()

        findViewById<View>(R.id.homeContent)
            .startAnimation(AnimationUtils.loadAnimation(this, R.anim.home_enter))
    }

    override fun onResume() {
        super.onResume()
        updateThemeToggleIcon()
        refreshDashboard()
    }

    private fun initViews() {
        homeTitle = findViewById(R.id.homeTitle)
        homeSubtitle = findViewById(R.id.homeSubtitle)
        themeToggleButton = findViewById(R.id.themeToggleButton)
        addBtn = findViewById(R.id.addButton)
        importPresetBtn = findViewById(R.id.importPresetButton)
        backupBtn = findViewById(R.id.backupButton)
        trashBtn = findViewById(R.id.trashButton)

        statTotalValue = findViewById(R.id.statTotalValue)
        statReadingValue = findViewById(R.id.statReadingValue)
        statFinishedValue = findViewById(R.id.statFinishedValue)
        statAverageValue = findViewById(R.id.statAverageValue)

        // Hubs
        hubCardBook = findViewById(R.id.hubCardBook)
        hubBookCountBadge = findViewById(R.id.hubBookCountBadge)
        hubBookSubtitle = findViewById(R.id.hubBookSubtitle)
        hubBookCoversPreview = findViewById(R.id.hubBookCoversPreview)
        btnEnterBookHub = findViewById(R.id.btnEnterBookHub)
        btnQuickReader3D = findViewById(R.id.btnQuickReader3D)

        hubCardAnime = findViewById(R.id.hubCardAnime)
        hubAnimeCountBadge = findViewById(R.id.hubAnimeCountBadge)
        hubAnimeSubtitle = findViewById(R.id.hubAnimeSubtitle)
        hubAnimeCoversPreview = findViewById(R.id.hubAnimeCoversPreview)
        btnEnterAnimeHub = findViewById(R.id.btnEnterAnimeHub)
        btnQuickAnimeTimeline = findViewById(R.id.btnQuickAnimeTimeline)
        btnQuickAnimePassport = findViewById(R.id.btnQuickAnimePassport)

        hubCardMovie = findViewById(R.id.hubCardMovie)
        hubMovieCountBadge = findViewById(R.id.hubMovieCountBadge)
        hubMovieSubtitle = findViewById(R.id.hubMovieSubtitle)
        hubMovieCoversPreview = findViewById(R.id.hubMovieCoversPreview)
        btnEnterMovieHub = findViewById(R.id.btnEnterMovieHub)
        btnQuickMovieTicket = findViewById(R.id.btnQuickMovieTicket)

        hubCardGame = findViewById(R.id.hubCardGame)
        hubGameCountBadge = findViewById(R.id.hubGameCountBadge)
        hubGameSubtitle = findViewById(R.id.hubGameSubtitle)
        hubGameCoversPreview = findViewById(R.id.hubGameCoversPreview)
        btnEnterGameHub = findViewById(R.id.btnEnterGameHub)
        btnQuickGameCartridge = findViewById(R.id.btnQuickGameCartridge)
        btnQuickGamePassport = findViewById(R.id.btnQuickGamePassport)

        // Explorations
        homeConstellationPanel = findViewById(R.id.homeConstellationPanel)
        homeGalleryPanel = findViewById(R.id.homeGalleryPanel)
        homeGallerySummary = findViewById(R.id.homeGallerySummary)
        homeBadgePanel = findViewById(R.id.homeBadgePanel)
        homeBadgeSummary = findViewById(R.id.homeBadgeSummary)
        homeCommunityPanel = findViewById(R.id.homeCommunityPanel)

        memoryPanel = findViewById(R.id.memoryPanel)
        memoryTitle = findViewById(R.id.memoryTitle)
        memoryBookTitle = findViewById(R.id.memoryBookTitle)
        memoryBookQuote = findViewById(R.id.memoryBookQuote)

        annualPersonaPanel = findViewById(R.id.annualPersonaPanel)
        annualPersonaBadge = findViewById(R.id.annualPersonaBadge)
        annualPersonaDesc = findViewById(R.id.annualPersonaDesc)
        annualMindprintRadar = findViewById(R.id.annualMindprintRadar)
    }

    private fun setupListeners() {
        themeToggleButton.setOnClickListener {
            ThemeHelper.toggleDarkMode(this)
            updateThemeToggleIcon()
        }

        addBtn.setOnClickListener { startActivity(Intent(this, AddBookActivity::class.java)) }
        importPresetBtn.setOnClickListener { showImportCsvDialog() }
        trashBtn.setOnClickListener { startActivity(TrashActivity.createIntent(this)) }
        backupBtn.setOnClickListener { startActivity(Intent(this, BackupActivity::class.java)) }

        // 1. 文学书房
        val openBookHub = { startActivity(MediaHubActivity.createIntent(this, MediaType.BOOK)) }
        hubCardBook.setOnClickListener { openBookHub() }
        btnEnterBookHub.setOnClickListener { openBookHub() }
        btnQuickReader3D.setOnClickListener {
            val firstBook = databaseHelper.getBooks().firstOrNull { it.mediaType == MediaType.BOOK }
            if (firstBook != null) {
                startActivity(com.example.readtrace.reader.Book3DReaderActivity.createIntent(this, firstBook.id))
            } else {
                Toast.makeText(this, "书房暂无藏书，请先添加或导入", Toast.LENGTH_SHORT).show()
            }
        }

        // 2. 追番殿堂
        val openAnimeHub = { startActivity(MediaHubActivity.createIntent(this, MediaType.ANIME)) }
        hubCardAnime.setOnClickListener { openAnimeHub() }
        btnEnterAnimeHub.setOnClickListener { openAnimeHub() }
        btnQuickAnimeTimeline.setOnClickListener {
            startActivity(Intent(this, AnimeTimelineScrollActivity::class.java))
        }
        btnQuickAnimePassport.setOnClickListener {
            startActivity(CulturalPassportActivity.createIntent(this, MediaType.ANIME))
        }

        // 3. 光影剧场
        val openMovieHub = { startActivity(MediaHubActivity.createIntent(this, MediaType.MOVIE)) }
        hubCardMovie.setOnClickListener { openMovieHub() }
        btnEnterMovieHub.setOnClickListener { openMovieHub() }
        btnQuickMovieTicket.setOnClickListener {
            val firstMovie = databaseHelper.getBooks().firstOrNull { it.mediaType == MediaType.MOVIE }
            if (firstMovie != null) {
                startActivity(MovieTicketPosterActivity.createIntent(this, firstMovie.id))
            } else {
                Toast.makeText(this, "剧场暂无电影记录", Toast.LENGTH_SHORT).show()
            }
        }

        // 4. 游戏宝库
        val openGameHub = { startActivity(MediaHubActivity.createIntent(this, MediaType.GAME)) }
        hubCardGame.setOnClickListener { openGameHub() }
        btnEnterGameHub.setOnClickListener { openGameHub() }
        btnQuickGameCartridge.setOnClickListener {
            val firstGame = databaseHelper.getBooks().firstOrNull { it.mediaType == MediaType.GAME }
            if (firstGame != null) {
                startActivity(GameCartridgePosterActivity.createIntent(this, firstGame.id))
            } else {
                Toast.makeText(this, "游戏库暂无作品", Toast.LENGTH_SHORT).show()
            }
        }
        btnQuickGamePassport.setOnClickListener {
            startActivity(CulturalPassportActivity.createIntent(this, MediaType.GAME))
        }

        // 探索区
        homeConstellationPanel.setOnClickListener {
            startActivity(MindprintConstellationActivity.createIntent(this))
        }
        homeGalleryPanel.setOnClickListener {
            startActivity(Gallery3DActivity.createIntent(this))
        }
        homeBadgePanel.setOnClickListener {
            startActivity(BadgesActivity.createIntent(this))
        }
        homeCommunityPanel.setOnClickListener {
            startActivity(CommunityActivity.createIntent(this))
        }

        listOfNotNull(
            themeToggleButton, addBtn, importPresetBtn, backupBtn, trashBtn,
            hubCardBook, hubCardAnime, hubCardMovie, hubCardGame,
            btnEnterBookHub, btnQuickReader3D,
            btnEnterAnimeHub, btnQuickAnimeTimeline, btnQuickAnimePassport,
            btnEnterMovieHub, btnQuickMovieTicket,
            btnEnterGameHub, btnQuickGameCartridge, btnQuickGamePassport,
            homeConstellationPanel, homeGalleryPanel, homeBadgePanel, homeCommunityPanel,
        ).forEach { ViewAnimationHelper.attachSpringTouch(it) }
    }

    private fun updateThemeToggleIcon() {
        themeToggleButton.text = if (ThemeHelper.isDarkMode(this)) "☀️" else "🌙"
    }

    private fun refreshDashboard() {
        val allBooks = databaseHelper.getBooks()
        val total = allBooks.size
        val reading = allBooks.count { it.status == BookStatus.READING }
        val finished = allBooks.count { it.status == BookStatus.FINISHED }
        val rated = allBooks.mapNotNull { it.rating }

        statTotalValue.text = total.toString()
        statReadingValue.text = reading.toString()
        statFinishedValue.text = finished.toString()
        statAverageValue.text = if (rated.isEmpty()) "均分 ★ -" else "均分 ★ ${RATING_FORMAT.format(rated.average())}"

        // 渲染四大展馆卡片
        renderHubCard(
            mediaType = MediaType.BOOK,
            allWorks = allBooks.filter { it.mediaType == MediaType.BOOK },
            badgeView = hubBookCountBadge,
            subtitleView = hubBookSubtitle,
            coversContainer = hubBookCoversPreview,
            unit = "本藏书",
        )
        renderHubCard(
            mediaType = MediaType.ANIME,
            allWorks = allBooks.filter { it.mediaType == MediaType.ANIME },
            badgeView = hubAnimeCountBadge,
            subtitleView = hubAnimeSubtitle,
            coversContainer = hubAnimeCoversPreview,
            unit = "部番剧",
        )
        renderHubCard(
            mediaType = MediaType.MOVIE,
            allWorks = allBooks.filter { it.mediaType == MediaType.MOVIE },
            badgeView = hubMovieCountBadge,
            subtitleView = hubMovieSubtitle,
            coversContainer = hubMovieCoversPreview,
            unit = "部光影",
        )
        renderHubCard(
            mediaType = MediaType.GAME,
            allWorks = allBooks.filter { it.mediaType == MediaType.GAME },
            badgeView = hubGameCountBadge,
            subtitleView = hubGameSubtitle,
            coversContainer = hubGameCoversPreview,
            unit = "款神作",
        )

        renderGallerySummary()
        renderBadgesSummary()
        renderMemoryCard()
        renderAnnualPersonaInsight()
    }

    private fun renderHubCard(
        mediaType: MediaType,
        allWorks: List<Book>,
        badgeView: TextView,
        subtitleView: TextView,
        coversContainer: LinearLayout,
        unit: String,
    ) {
        badgeView.text = "${allWorks.size} $unit"
        val readingCount = allWorks.count { it.status == BookStatus.READING }
        val finishedCount = allWorks.count { it.status == BookStatus.FINISHED }

        val statusSummary = when (mediaType) {
            MediaType.BOOK -> "已读完 $finishedCount 本 · $readingCount 本在读"
            MediaType.ANIME -> "已补完 $finishedCount 部 · $readingCount 部追番中"
            MediaType.MOVIE -> "已观影 $finishedCount 部"
            MediaType.GAME -> "白金通关 $finishedCount 款 · $readingCount 款游玩中"
            else -> "已完成 $finishedCount · 进行中 $readingCount"
        }
        subtitleView.text = statusSummary

        coversContainer.removeAllViews()
        val previewItems = allWorks.take(4)
        if (previewItems.isEmpty()) {
            coversContainer.visibility = View.GONE
            return
        }

        coversContainer.visibility = View.VISIBLE
        previewItems.forEachIndexed { index, book ->
            val cardView = CardView(this).apply {
                radius = dpToPx(6).toFloat()
                cardElevation = dpToPx(2).toFloat()
                val params = LinearLayout.LayoutParams(dpToPx(48), dpToPx(72)).apply {
                    if (index > 0) marginStart = dpToPx(8)
                }
                layoutParams = params
            }

            val imageView = ImageView(this).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                scaleType = ImageView.ScaleType.CENTER_CROP
            }
            cardView.addView(imageView)
            CoverImageHelper.loadCover(imageView, book.coverUrl)

            cardView.setOnClickListener {
                startActivity(BookDetailActivity.createIntent(this, book.id))
            }
            coversContainer.addView(cardView)
        }
    }

    private fun renderBadgesSummary() {
        val badges = MilestoneBadgeHelper.calculateBadges(databaseHelper)
        val unlockedCount = badges.count { it.isUnlocked }
        homeBadgeSummary.text = "已解锁 $unlockedCount / ${badges.size} 枚专属精神荣誉勋章"
    }

    private fun renderGallerySummary() {
        val featuredCount = databaseHelper.getGalleryFeaturedWorks(24).size
        homeGallerySummary.text = if (featuredCount > 0) {
            "基于 OpenGL 的 360° 环形悬浮立体展台（已精选 $featuredCount 部藏品）"
        } else {
            "基于 OpenGL 的 360° 环形悬浮立体展台与全息封面流"
        }
    }

    private fun renderMemoryCard() {
        val memoryPair = databaseHelper.getMemoryBook()
        if (memoryPair == null) {
            memoryPanel.visibility = View.GONE
            return
        }
        val memoryBook = memoryPair.first
        memoryPanel.visibility = View.VISIBLE
        memoryBookTitle.text = "《${memoryBook.title}》· ${memoryBook.author ?: "未知作者"}"
        val quote = memoryPair.second.ifBlank { memoryBook.shortComment ?: "重温那段在字里行间沉淀的记忆时光。" }
        memoryBookQuote.text = quote
        memoryPanel.setOnClickListener {
            startActivity(BookDetailActivity.createIntent(this, memoryBook.id))
        }
    }

    private fun renderAnnualPersonaInsight() {
        val persona = databaseHelper.getAnnualMindprintPersona()
        if (persona == null) {
            annualPersonaPanel.visibility = View.GONE
            return
        }
        annualPersonaPanel.visibility = View.VISIBLE
        annualPersonaBadge.text = persona.personaTitle
        annualPersonaDesc.text = "${persona.personaDesc}（已深度量化分析 ${persona.finishedBooksCount} 部作品）"
        annualMindprintRadar.setMindprint(persona.avgMindprint, animate = false)
    }

    private fun showImportCsvDialog() {
        val options = arrayOf(
            "📚 导入预设名著经典 (54 本)",
            "🌸 导入预设追番史 (70 部)",
            "🎬 导入预设经典电影 (11 部)",
            "🎮 导入预设 Steam 游戏 (67 款)",
            "🌟 一键全量合入 (202 部神作)",
            "📂 选择本地 CSV 文件...",
        )
        AlertDialog.Builder(this)
            .setTitle("📥 批量导入精神清单")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> importAssetCsv("preset_books.csv", MediaType.BOOK, "名著书单")
                    1 -> importAssetCsv("preset_anime.csv", MediaType.ANIME, "追番清单")
                    2 -> importAssetCsv("preset_movies.csv", MediaType.MOVIE, "电影清单")
                    3 -> importAssetCsv("preset_games.csv", MediaType.GAME, "游戏清单")
                    4 -> importAllPresetCsvs()
                    5 -> selectCsvLauncher.launch(arrayOf("text/*", "text/comma-separated-values", "application/csv"))
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun importAssetCsv(assetFileName: String, defaultMedia: MediaType, categoryName: String) {
        val count = try {
            assets.open(assetFileName).use { stream ->
                val records = BookCsvParser.parseRecords(stream, defaultMedia)
                databaseHelper.importParsedRecords(records)
            }
        } catch (e: Exception) {
            0
        }
        if (count > 0) {
            Toast.makeText(this, "成功导入 $count 部 $categoryName！", Toast.LENGTH_SHORT).show()
            refreshDashboard()
        } else {
            Toast.makeText(this, "未发现新作品或已全部存在", Toast.LENGTH_SHORT).show()
        }
    }

    private fun importAllPresetCsvs() {
        var total = 0
        val presets = listOf(
            "preset_books.csv" to MediaType.BOOK,
            "preset_anime.csv" to MediaType.ANIME,
            "preset_movies.csv" to MediaType.MOVIE,
            "preset_games.csv" to MediaType.GAME,
        )
        presets.forEach { (assetFile, media) ->
            try {
                assets.open(assetFile).use { stream ->
                    val records = BookCsvParser.parseRecords(stream, media)
                    total += databaseHelper.importParsedRecords(records)
                }
            } catch (_: Exception) {}
        }
        Toast.makeText(this, "全量预设导入完成，共新增 $total 部作品！", Toast.LENGTH_LONG).show()
        refreshDashboard()
    }

    private fun importCustomCsv(uri: Uri) {
        val count = try {
            contentResolver.openInputStream(uri)?.use { stream ->
                val records = BookCsvParser.parseRecords(stream, MediaType.BOOK)
                databaseHelper.importParsedRecords(records)
            } ?: 0
        } catch (e: Exception) {
            0
        }
        if (count > 0) {
            Toast.makeText(this, "成功从 CSV 导入 $count 部作品！", Toast.LENGTH_SHORT).show()
            refreshDashboard()
        } else {
            Toast.makeText(this, "导入失败或未发现有效记录", Toast.LENGTH_SHORT).show()
        }
    }

    private fun dpToPx(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    override fun onDestroy() {
        databaseHelper.close()
        super.onDestroy()
    }

    companion object {
        private val RATING_FORMAT = DecimalFormat("0.#")
    }
}
