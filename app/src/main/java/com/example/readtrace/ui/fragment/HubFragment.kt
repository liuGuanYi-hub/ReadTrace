package com.example.readtrace.ui.fragment

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import com.example.readtrace.AddBookActivity
import com.example.readtrace.AnimeTimelineScrollActivity
import com.example.readtrace.BackupActivity
import com.example.readtrace.BookDetailActivity
import com.example.readtrace.CulturalPassportActivity
import com.example.readtrace.GameCartridgePosterActivity
import com.example.readtrace.MediaHubActivity
import com.example.readtrace.MovieTicketPosterActivity
import com.example.readtrace.R
import com.example.readtrace.TrashActivity
import com.example.readtrace.data.BookDatabaseHelper
import com.example.readtrace.model.Book
import com.example.readtrace.model.BookStatus
import com.example.readtrace.model.MediaType
import com.example.readtrace.util.BookCsvParser
import com.example.readtrace.util.CoverImageHelper
import com.example.readtrace.util.ThemeHelper
import com.example.readtrace.util.ViewAnimationHelper
import java.text.DecimalFormat
import kotlin.math.roundToInt

class HubFragment : Fragment() {

    private lateinit var databaseHelper: BookDatabaseHelper

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

    private lateinit var memoryPanel: View
    private lateinit var memoryTitle: TextView
    private lateinit var memoryBookTitle: TextView
    private lateinit var memoryBookQuote: TextView

    private val selectCsvLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            importCustomCsv(uri)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.fragment_hub, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        databaseHelper = BookDatabaseHelper(requireContext())

        initViews(view)
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        updateThemeToggleIcon()
        refreshDashboard()
    }

    private fun initViews(view: View) {
        homeTitle = view.findViewById(R.id.homeTitle)
        homeSubtitle = view.findViewById(R.id.homeSubtitle)
        themeToggleButton = view.findViewById(R.id.themeToggleButton)
        addBtn = view.findViewById(R.id.addButton)
        importPresetBtn = view.findViewById(R.id.importPresetButton)
        backupBtn = view.findViewById(R.id.backupButton)
        trashBtn = view.findViewById(R.id.trashButton)

        statTotalValue = view.findViewById(R.id.statTotalValue)
        statReadingValue = view.findViewById(R.id.statReadingValue)
        statFinishedValue = view.findViewById(R.id.statFinishedValue)
        statAverageValue = view.findViewById(R.id.statAverageValue)

        hubCardBook = view.findViewById(R.id.hubCardBook)
        hubBookCountBadge = view.findViewById(R.id.hubBookCountBadge)
        hubBookSubtitle = view.findViewById(R.id.hubBookSubtitle)
        hubBookCoversPreview = view.findViewById(R.id.hubBookCoversPreview)
        btnEnterBookHub = view.findViewById(R.id.btnEnterBookHub)
        btnQuickReader3D = view.findViewById(R.id.btnQuickReader3D)

        hubCardAnime = view.findViewById(R.id.hubCardAnime)
        hubAnimeCountBadge = view.findViewById(R.id.hubAnimeCountBadge)
        hubAnimeSubtitle = view.findViewById(R.id.hubAnimeSubtitle)
        hubAnimeCoversPreview = view.findViewById(R.id.hubAnimeCoversPreview)
        btnEnterAnimeHub = view.findViewById(R.id.btnEnterAnimeHub)
        btnQuickAnimeTimeline = view.findViewById(R.id.btnQuickAnimeTimeline)
        btnQuickAnimePassport = view.findViewById(R.id.btnQuickAnimePassport)

        hubCardMovie = view.findViewById(R.id.hubCardMovie)
        hubMovieCountBadge = view.findViewById(R.id.hubMovieCountBadge)
        hubMovieSubtitle = view.findViewById(R.id.hubMovieSubtitle)
        hubMovieCoversPreview = view.findViewById(R.id.hubMovieCoversPreview)
        btnEnterMovieHub = view.findViewById(R.id.btnEnterMovieHub)
        btnQuickMovieTicket = view.findViewById(R.id.btnQuickMovieTicket)

        hubCardGame = view.findViewById(R.id.hubCardGame)
        hubGameCountBadge = view.findViewById(R.id.hubGameCountBadge)
        hubGameSubtitle = view.findViewById(R.id.hubGameSubtitle)
        hubGameCoversPreview = view.findViewById(R.id.hubGameCoversPreview)
        btnEnterGameHub = view.findViewById(R.id.btnEnterGameHub)
        btnQuickGameCartridge = view.findViewById(R.id.btnQuickGameCartridge)
        btnQuickGamePassport = view.findViewById(R.id.btnQuickGamePassport)

        memoryPanel = view.findViewById(R.id.memoryPanel)
        memoryTitle = view.findViewById(R.id.memoryTitle)
        memoryBookTitle = view.findViewById(R.id.memoryBookTitle)
        memoryBookQuote = view.findViewById(R.id.memoryBookQuote)
    }

    private fun setupListeners() {
        themeToggleButton.setOnClickListener {
            val ctx = requireContext()
            ThemeHelper.toggleDarkMode(ctx)
            updateThemeToggleIcon()
        }

        addBtn.setOnClickListener { startActivity(Intent(requireContext(), AddBookActivity::class.java)) }
        importPresetBtn.setOnClickListener { showImportCsvDialog() }
        trashBtn.setOnClickListener { startActivity(TrashActivity.createIntent(requireContext())) }
        backupBtn.setOnClickListener { startActivity(Intent(requireContext(), BackupActivity::class.java)) }

        val openBookHub = { startActivity(MediaHubActivity.createIntent(requireContext(), MediaType.BOOK)) }
        hubCardBook.setOnClickListener { openBookHub() }
        btnEnterBookHub.setOnClickListener { openBookHub() }
        btnQuickReader3D.setOnClickListener {
            val firstBook = databaseHelper.getBooks().firstOrNull { it.mediaType == MediaType.BOOK }
            if (firstBook != null) {
                startActivity(com.example.readtrace.reader.Book3DReaderActivity.createIntent(requireContext(), firstBook.id))
            } else {
                Toast.makeText(requireContext(), "书房暂无藏书，请先添加或导入", Toast.LENGTH_SHORT).show()
            }
        }

        val openAnimeHub = { startActivity(MediaHubActivity.createIntent(requireContext(), MediaType.ANIME)) }
        hubCardAnime.setOnClickListener { openAnimeHub() }
        btnEnterAnimeHub.setOnClickListener { openAnimeHub() }
        btnQuickAnimeTimeline.setOnClickListener {
            startActivity(Intent(requireContext(), AnimeTimelineScrollActivity::class.java))
        }
        btnQuickAnimePassport.setOnClickListener {
            startActivity(CulturalPassportActivity.createIntent(requireContext(), MediaType.ANIME))
        }

        val openMovieHub = { startActivity(MediaHubActivity.createIntent(requireContext(), MediaType.MOVIE)) }
        hubCardMovie.setOnClickListener { openMovieHub() }
        btnEnterMovieHub.setOnClickListener { openMovieHub() }
        btnQuickMovieTicket.setOnClickListener {
            val firstMovie = databaseHelper.getBooks().firstOrNull { it.mediaType == MediaType.MOVIE }
            if (firstMovie != null) {
                startActivity(MovieTicketPosterActivity.createIntent(requireContext(), firstMovie.id))
            } else {
                Toast.makeText(requireContext(), "剧场暂无电影记录", Toast.LENGTH_SHORT).show()
            }
        }

        val openGameHub = { startActivity(MediaHubActivity.createIntent(requireContext(), MediaType.GAME)) }
        hubCardGame.setOnClickListener { openGameHub() }
        btnEnterGameHub.setOnClickListener { openGameHub() }
        btnQuickGameCartridge.setOnClickListener {
            val firstGame = databaseHelper.getBooks().firstOrNull { it.mediaType == MediaType.GAME }
            if (firstGame != null) {
                startActivity(GameCartridgePosterActivity.createIntent(requireContext(), firstGame.id))
            } else {
                Toast.makeText(requireContext(), "游戏库暂无作品", Toast.LENGTH_SHORT).show()
            }
        }
        btnQuickGamePassport.setOnClickListener {
            startActivity(CulturalPassportActivity.createIntent(requireContext(), MediaType.GAME))
        }

        listOfNotNull(
            themeToggleButton, addBtn, importPresetBtn, backupBtn, trashBtn,
            hubCardBook, hubCardAnime, hubCardMovie, hubCardGame,
            btnEnterBookHub, btnQuickReader3D,
            btnEnterAnimeHub, btnQuickAnimeTimeline, btnQuickAnimePassport,
            btnEnterMovieHub, btnQuickMovieTicket,
            btnEnterGameHub, btnQuickGameCartridge, btnQuickGamePassport,
        ).forEach { ViewAnimationHelper.attachSpringTouch(it) }
    }

    private fun updateThemeToggleIcon() {
        val ctx = context ?: return
        themeToggleButton.text = if (ThemeHelper.isDarkMode(ctx)) "☀️" else "🌙"
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

        renderMemoryCard()
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
        val ctx = context ?: return
        previewItems.forEachIndexed { index, book ->
            val cardView = CardView(ctx).apply {
                radius = dpToPx(6).toFloat()
                cardElevation = dpToPx(2).toFloat()
                val params = LinearLayout.LayoutParams(dpToPx(48), dpToPx(72)).apply {
                    if (index > 0) marginStart = dpToPx(8)
                }
                layoutParams = params
            }

            val imageView = ImageView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                scaleType = ImageView.ScaleType.CENTER_CROP
            }
            cardView.addView(imageView)
            CoverImageHelper.loadCover(imageView, book.coverUrl)

            cardView.setOnClickListener {
                startActivity(BookDetailActivity.createIntent(requireContext(), book.id))
            }
            coversContainer.addView(cardView)
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
            startActivity(BookDetailActivity.createIntent(requireContext(), memoryBook.id))
        }
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
        AlertDialog.Builder(requireContext())
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
            requireContext().assets.open(assetFileName).use { stream ->
                val records = BookCsvParser.parseRecords(stream, defaultMedia)
                databaseHelper.importParsedRecords(records)
            }
        } catch (e: Exception) {
            0
        }
        if (count > 0) {
            Toast.makeText(requireContext(), "成功导入 $count 部 $categoryName！", Toast.LENGTH_SHORT).show()
            refreshDashboard()
        } else {
            Toast.makeText(requireContext(), "未发现新作品或已全部存在", Toast.LENGTH_SHORT).show()
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
                requireContext().assets.open(assetFile).use { stream ->
                    val records = BookCsvParser.parseRecords(stream, media)
                    total += databaseHelper.importParsedRecords(records)
                }
            } catch (_: Exception) {}
        }
        Toast.makeText(requireContext(), "全量预设导入完成，共新增 $total 部作品！", Toast.LENGTH_LONG).show()
        refreshDashboard()
    }

    private fun importCustomCsv(uri: Uri) {
        val count = try {
            requireContext().contentResolver.openInputStream(uri)?.use { stream ->
                val records = BookCsvParser.parseRecords(stream, MediaType.BOOK)
                databaseHelper.importParsedRecords(records)
            } ?: 0
        } catch (e: Exception) {
            0
        }
        if (count > 0) {
            Toast.makeText(requireContext(), "成功从 CSV 导入 $count 部作品！", Toast.LENGTH_SHORT).show()
            refreshDashboard()
        } else {
            Toast.makeText(requireContext(), "导入失败或未发现有效记录", Toast.LENGTH_SHORT).show()
        }
    }

    private fun dpToPx(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    override fun onDestroyView() {
        databaseHelper.close()
        super.onDestroyView()
    }

    companion object {
        private val RATING_FORMAT = DecimalFormat("0.#")
    }
}
