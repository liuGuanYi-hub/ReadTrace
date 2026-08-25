package com.example.readtrace

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import com.example.readtrace.data.BookDatabaseHelper
import com.example.readtrace.model.Book
import com.example.readtrace.model.BookStatus
import com.example.readtrace.model.MediaType
import com.example.readtrace.model.MonthlyReadingStat
import com.example.readtrace.util.BookCsvParser
import com.example.readtrace.util.CoverImageHelper
import com.example.readtrace.util.MilestoneBadgeHelper
import java.text.DecimalFormat
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {
    private val importExternalCsvLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            importExternalCsvFile(uri)
        }
    }

    private lateinit var databaseHelper: BookDatabaseHelper
    private lateinit var booksContainer: LinearLayout
    private lateinit var emptyPanel: View
    private lateinit var emptyTitle: TextView
    private lateinit var emptyBody: TextView
    private lateinit var shelfCountText: TextView
    private lateinit var statTotalValue: TextView
    private lateinit var statReadingValue: TextView
    private lateinit var statFinishedValue: TextView
    private lateinit var statAverageValue: TextView
    private lateinit var memoryPanel: View
    private lateinit var memorySubPrompt: TextView
    private lateinit var memoryBookTitle: TextView
    private lateinit var memoryBookQuote: TextView
    private lateinit var monthlyStatPanel: View
    private lateinit var monthlyChartBarsContainer: LinearLayout
    private lateinit var monthlyStatEmptyText: TextView

    private lateinit var annualPersonaPanel: View
    private lateinit var annualPersonaBadge: TextView
    private lateinit var annualPersonaDesc: TextView
    private lateinit var annualMindprintRadar: com.example.readtrace.widget.MindprintRadarView
    private lateinit var btnExportShelfScroll: View
    private lateinit var btnToggleViewMode: TextView
    private lateinit var btnCoverGallery: TextView
    private lateinit var btnAnimeTimeline: TextView
    private lateinit var btnCulturalPassport: TextView
    private lateinit var btnBatchFetchAnimeCovers: TextView

    private lateinit var homeBadgePanel: View
    private lateinit var homeBadgeSummary: TextView
    private lateinit var homeGalleryPanel: View
    private lateinit var homeGallerySummary: TextView
    private lateinit var mediaTabAll: TextView
    private lateinit var mediaTabBook: TextView
    private lateinit var mediaTabAnime: TextView
    private lateinit var mediaTabMovie: TextView
    private lateinit var mediaTabGame: TextView
    private lateinit var mediaTabPodcast: TextView
    private lateinit var searchInput: EditText
    private lateinit var searchClearButton: View
    private lateinit var tagScroller: HorizontalScrollView
    private lateinit var tagGroup: LinearLayout
    private lateinit var tagAll: TextView

    private var selectedMediaType: MediaType? = null
    private var selectedStatus: BookStatus? = null
    private var searchKeyword: String = ""
    private var selectedTag: String? = null
    private var isGridView: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val prefs = getSharedPreferences("readtrace_prefs", Context.MODE_PRIVATE)
        isGridView = prefs.getBoolean("pref_is_grid_view", false)

        val themeToggleBtn = findViewById<TextView>(R.id.themeToggleButton)
        themeToggleBtn?.text = if (com.example.readtrace.util.ThemeHelper.isDarkMode(this)) "☀️" else "🌙"
        themeToggleBtn?.setOnClickListener {
            val isDark = com.example.readtrace.util.ThemeHelper.toggleDarkMode(this)
            themeToggleBtn.text = if (isDark) "☀️" else "🌙"
        }
        themeToggleBtn?.let { com.example.readtrace.util.ViewAnimationHelper.attachSpringTouch(it) }

        databaseHelper = BookDatabaseHelper(this)
        booksContainer = findViewById(R.id.booksContainer)
        emptyPanel = findViewById(R.id.emptyPanel)
        emptyTitle = findViewById(R.id.emptyTitle)
        emptyBody = findViewById(R.id.emptyBody)
        shelfCountText = findViewById(R.id.shelfCountText)
        statTotalValue = findViewById(R.id.statTotalValue)
        statReadingValue = findViewById(R.id.statReadingValue)
        statFinishedValue = findViewById(R.id.statFinishedValue)
        statAverageValue = findViewById(R.id.statAverageValue)

        memoryPanel = findViewById(R.id.memoryPanel)
        memorySubPrompt = findViewById(R.id.memorySubPrompt)
        memoryBookTitle = findViewById(R.id.memoryBookTitle)
        memoryBookQuote = findViewById(R.id.memoryBookQuote)

        monthlyStatPanel = findViewById(R.id.monthlyStatPanel)
        monthlyChartBarsContainer = findViewById(R.id.monthlyChartBarsContainer)
        monthlyStatEmptyText = findViewById(R.id.monthlyStatEmptyText)

        annualPersonaPanel = findViewById(R.id.annualPersonaPanel)
        annualPersonaBadge = findViewById(R.id.annualPersonaBadge)
        annualPersonaDesc = findViewById(R.id.annualPersonaDesc)
        annualMindprintRadar = findViewById(R.id.annualMindprintRadar)
        btnExportShelfScroll = findViewById(R.id.btnExportShelfScroll)
        btnExportShelfScroll.setOnClickListener {
            exportShelfScrollImage()
        }
        btnToggleViewMode = findViewById(R.id.btnToggleViewMode)
        updateViewModeButton()
        btnToggleViewMode.setOnClickListener {
            isGridView = !isGridView
            prefs.edit().putBoolean("pref_is_grid_view", isGridView).apply()
            updateViewModeButton()
            refreshShelfOnly()
        }

        btnCoverGallery = findViewById(R.id.btnCoverGallery)
        btnCoverGallery.setOnClickListener {
            startActivity(Intent(this, CoverGalleryActivity::class.java))
        }
        com.example.readtrace.util.ViewAnimationHelper.attachSpringTouch(btnCoverGallery)

        btnAnimeTimeline = findViewById(R.id.btnAnimeTimeline)
        btnAnimeTimeline.setOnClickListener {
            startActivity(Intent(this, AnimeTimelineScrollActivity::class.java))
        }
        com.example.readtrace.util.ViewAnimationHelper.attachSpringTouch(btnAnimeTimeline)

        btnCulturalPassport = findViewById(R.id.btnCulturalPassport)
        btnCulturalPassport.setOnClickListener {
            val defaultTab = if (selectedMediaType == MediaType.GAME) MediaType.GAME else MediaType.ANIME
            startActivity(CulturalPassportActivity.createIntent(this, defaultTab))
        }
        com.example.readtrace.util.ViewAnimationHelper.attachSpringTouch(btnCulturalPassport)

        btnBatchFetchAnimeCovers = findViewById(R.id.btnBatchFetchAnimeCovers)
        btnBatchFetchAnimeCovers.setOnClickListener {
            val label = selectedMediaType?.displayName ?: "书影音游"
            Toast.makeText(this, "正在联网批量检索 $label 官方高清封面...", Toast.LENGTH_SHORT).show()
            com.example.readtrace.util.AnimeCoverScraperHelper.batchFetchAnimeCovers(
                this,
                databaseHelper,
                targetMediaType = selectedMediaType,
                onProgress = { current, total, title ->
                    if (current == 1 || current % 5 == 0 || current == total) {
                        Toast.makeText(this, "[$current/$total] 正在匹配《$title》封面...", Toast.LENGTH_SHORT).show()
                    }
                },
                onComplete = { success, total ->
                    Toast.makeText(this, "✨ $label 封面匹配完成！成功抓取 $success/$total 部", Toast.LENGTH_LONG).show()
                    refreshShelfOnly()
                }
            )
        }
        com.example.readtrace.util.ViewAnimationHelper.attachSpringTouch(btnBatchFetchAnimeCovers)

        homeBadgePanel = findViewById(R.id.homeBadgePanel)
        homeBadgeSummary = findViewById(R.id.homeBadgeSummary)
        homeGalleryPanel = findViewById(R.id.homeGalleryPanel)
        homeGallerySummary = findViewById(R.id.homeGallerySummary)
        mediaTabAll = findViewById(R.id.mediaTabAll)
        mediaTabBook = findViewById(R.id.mediaTabBook)
        mediaTabAnime = findViewById(R.id.mediaTabAnime)
        mediaTabMovie = findViewById(R.id.mediaTabMovie)
        mediaTabGame = findViewById(R.id.mediaTabGame)
        mediaTabPodcast = findViewById(R.id.mediaTabPodcast)
        searchInput = findViewById(R.id.searchInput)
        searchClearButton = findViewById(R.id.searchClearButton)
        tagScroller = findViewById(R.id.tagScroller)
        tagGroup = findViewById(R.id.tagGroup)
        tagAll = findViewById(R.id.tagAll)

        configureActions()
        configureMediaTypeFilters()
        configureStatusFilters()
        configureSearchAndTags()

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
        val addBtn = findViewById<View>(R.id.addButton)
        val emptyAction = findViewById<View>(R.id.emptyAction)
        val presetBtn = findViewById<View>(R.id.importPresetButton)
        val trashBtn = findViewById<View>(R.id.trashButton)
        val backupBtn = findViewById<View>(R.id.backupButton)
        val constellationPanel = findViewById<View>(R.id.homeConstellationPanel)
        val communityPanel = findViewById<View>(R.id.homeCommunityPanel)

        addBtn.setOnClickListener(openAddBook)
        emptyAction.setOnClickListener(openAddBook)
        presetBtn.setOnClickListener { confirmImportPresetBooks() }
        trashBtn.setOnClickListener { startActivity(TrashActivity.createIntent(this)) }
        backupBtn.setOnClickListener { startActivity(Intent(this, BackupActivity::class.java)) }
        homeBadgePanel.setOnClickListener { startActivity(BadgesActivity.createIntent(this)) }
        homeGalleryPanel.setOnClickListener { startActivity(Gallery3DActivity.createIntent(this)) }
        constellationPanel?.setOnClickListener {
            startActivity(MindprintConstellationActivity.createIntent(this))
        }
        communityPanel?.setOnClickListener {
            startActivity(com.example.readtrace.community.ui.CommunityActivity.createIntent(this))
        }

        // 注入 iOS 级 Q 弹物理反馈
        listOfNotNull(
            addBtn, emptyAction, presetBtn, trashBtn, backupBtn,
            homeBadgePanel, homeGalleryPanel, constellationPanel, communityPanel,
            btnExportShelfScroll, btnToggleViewMode, memoryPanel,
        ).forEach { com.example.readtrace.util.ViewAnimationHelper.attachSpringTouch(it) }
    }

    private fun confirmImportPresetBooks() {
        val options = arrayOf(
            "📚 经典文学名著 (54 本 · preset_books.csv)",
            "🌸 经典追番编年史 (70 部 · preset_anime.csv)",
            "🎬 经典影视光影 (11 部 · preset_movies.csv)",
            "🎮 Steam 游戏宝库 (67 款 · preset_games.csv)",
            "✨ 一键全量导入 (202 部文化印记)",
            "📁 选择手机本地 CSV 文件导入..."
        )

        AlertDialog.Builder(this)
            .setTitle("📥 批量导入文化资产 (CSV)")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> importPresetAssetCsv("preset_books.csv", MediaType.BOOK, "经典文学名著")
                    1 -> importPresetAssetCsv("preset_anime.csv", MediaType.ANIME, "经典追番编年史")
                    2 -> importPresetAssetCsv("preset_movies.csv", MediaType.MOVIE, "经典影视光影")
                    3 -> importPresetAssetCsv("preset_games.csv", MediaType.GAME, "Steam 游戏宝库")
                    4 -> importAllPresetAssets()
                    5 -> {
                        runCatching {
                            importExternalCsvLauncher.launch(arrayOf("text/comma-separated-values", "text/csv", "application/csv", "*/*"))
                        }.onFailure {
                            Toast.makeText(this, "无法启动文件选择器: ${it.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun importPresetAssetCsv(assetName: String, defaultMediaType: MediaType, label: String) {
        runCatching {
            assets.open(assetName).use { inputStream ->
                val records = BookCsvParser.parseRecords(inputStream, defaultMediaType)
                val count = databaseHelper.importParsedRecords(records)
                if (count > 0) {
                    Toast.makeText(
                        this,
                        "✨ 成功导入/更新 $count 条 $label！",
                        Toast.LENGTH_SHORT,
                    ).show()
                    refreshBooks()
                } else {
                    Toast.makeText(this, R.string.import_no_new_books, Toast.LENGTH_SHORT).show()
                }
            }
        }.onFailure {
            Toast.makeText(this, "导入 $label 失败: ${it.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun importAllPresetAssets() {
        runCatching {
            var totalCount = 0
            listOf(
                "preset_books.csv" to MediaType.BOOK,
                "preset_anime.csv" to MediaType.ANIME,
                "preset_movies.csv" to MediaType.MOVIE,
                "preset_games.csv" to MediaType.GAME,
            ).forEach { (assetName, mediaType) ->
                assets.open(assetName).use { inputStream ->
                    val records = BookCsvParser.parseRecords(inputStream, mediaType)
                    totalCount += databaseHelper.importParsedRecords(records)
                }
            }

            if (totalCount > 0) {
                Toast.makeText(
                    this,
                    "🌟 成功全量导入/更新 $totalCount 部文化印记（名著/番剧/电影/游戏）！",
                    Toast.LENGTH_LONG,
                ).show()
                refreshBooks()
            } else {
                Toast.makeText(this, R.string.import_no_new_books, Toast.LENGTH_SHORT).show()
            }
        }.onFailure {
            Toast.makeText(this, "全量导入失败: ${it.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun importExternalCsvFile(uri: Uri) {
        runCatching {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val records = BookCsvParser.parseRecords(inputStream, selectedMediaType ?: MediaType.BOOK)
                val count = databaseHelper.importParsedRecords(records)
                if (count > 0) {
                    Toast.makeText(
                        this,
                        "📂 成功从 CSV 导入/更新 $count 条作品记录！",
                        Toast.LENGTH_SHORT,
                    ).show()
                    refreshBooks()
                } else {
                    Toast.makeText(this, R.string.import_no_new_books, Toast.LENGTH_SHORT).show()
                }
            } ?: run {
                Toast.makeText(this, "无法读取所选 CSV 文件", Toast.LENGTH_SHORT).show()
            }
        }.onFailure {
            Toast.makeText(this, "解析外部 CSV 失败: ${it.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun configureSearchAndTags() {
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim().orEmpty()
                if (searchKeyword != query) {
                    searchKeyword = query
                    searchClearButton.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE
                    refreshShelfOnly()
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        searchClearButton.setOnClickListener {
            searchInput.setText("")
        }

        tagAll.setOnClickListener {
            selectTag(null)
        }
    }

    private fun configureMediaTypeFilters() {
        mediaTabAll.setOnClickListener { selectMediaType(null) }
        mediaTabBook.setOnClickListener { selectMediaType(MediaType.BOOK) }
        mediaTabAnime.setOnClickListener { selectMediaType(MediaType.ANIME) }
        mediaTabMovie.setOnClickListener { selectMediaType(MediaType.MOVIE) }
        mediaTabGame.setOnClickListener { selectMediaType(MediaType.GAME) }
        mediaTabPodcast.setOnClickListener { selectMediaType(MediaType.PODCAST) }
        updateMediaTypeTabs()
    }

    private fun selectMediaType(mediaType: MediaType?) {
        if (selectedMediaType == mediaType) return
        selectedMediaType = mediaType
        updateMediaTypeTabs()
        updateStatusChips()
        refreshShelfOnly()
    }

    private fun updateMediaTypeTabs() {
        val tabs = listOf(
            mediaTabAll to null,
            mediaTabBook to MediaType.BOOK,
            mediaTabAnime to MediaType.ANIME,
            mediaTabMovie to MediaType.MOVIE,
            mediaTabGame to MediaType.GAME,
            mediaTabPodcast to MediaType.PODCAST,
        )
        tabs.forEach { (tab, type) ->
            val isSelected = selectedMediaType == type
            tab.setBackgroundResource(
                if (isSelected) R.drawable.bg_status_chip_selected else R.drawable.bg_status_chip,
            )
            tab.setTextColor(
                ContextCompat.getColor(this, if (isSelected) R.color.white else R.color.readtrace_ink),
            )
        }

        val isAnimeSelected = selectedMediaType == MediaType.ANIME
        btnAnimeTimeline.visibility = if (isAnimeSelected) View.VISIBLE else View.GONE
        btnBatchFetchAnimeCovers.visibility = View.VISIBLE
        btnBatchFetchAnimeCovers.text = when (selectedMediaType) {
            MediaType.ANIME -> "🌸 抓番剧海报"
            MediaType.BOOK -> "📖 抓书籍封面"
            MediaType.MOVIE -> "🎬 抓影视海报"
            MediaType.GAME -> "🎮 抓游戏封面"
            MediaType.PODCAST -> "🎙️ 抓播客封面"
            null -> "🌐 智能抓封面"
        }
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
        refreshShelfOnly()
    }

    private fun selectTag(tag: String?) {
        if (selectedTag == tag) return
        selectedTag = tag
        updateTagChips()
        refreshShelfOnly()
    }

    private fun updateStatusChips() {
        val statusWishlist = findViewById<TextView>(R.id.statusWishlist)
        val statusReading = findViewById<TextView>(R.id.statusReading)
        val statusFinished = findViewById<TextView>(R.id.statusFinished)

        // 动态根据当前选中的媒介类型自适应状态筛选文案
        statusWishlist.text = selectedMediaType?.wishlistLabel ?: getString(R.string.status_wishlist)
        statusReading.text = selectedMediaType?.ongoingLabel ?: getString(R.string.status_reading)
        statusFinished.text = selectedMediaType?.finishedLabel ?: getString(R.string.status_finished)

        val chips = listOf(
            findViewById<TextView>(R.id.statusAll) to null,
            statusWishlist to BookStatus.WISHLIST,
            statusReading to BookStatus.READING,
            statusFinished to BookStatus.FINISHED,
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

    private fun renderBadgesSummary() {
        val badges = MilestoneBadgeHelper.calculateBadges(databaseHelper)
        val unlockedCount = badges.count { it.isUnlocked }
        val totalCount = badges.size
        homeBadgeSummary.text = getString(R.string.home_badge_summary_format, unlockedCount, totalCount)
    }

    private fun renderDynamicTags() {
        val tagList = databaseHelper.getAllUniqueTags()
        if (tagList.isEmpty()) {
            tagScroller.visibility = View.GONE
            selectedTag = null
            return
        }

        tagScroller.visibility = View.VISIBLE
        // 校验选中的标签是否还存在
        if (selectedTag != null && tagList.none { it.first == selectedTag }) {
            selectedTag = null
        }

        // 移除除了 tagAll 之外的所有标签
        while (tagGroup.childCount > 1) {
            tagGroup.removeViewAt(1)
        }

        tagList.forEach { (tag, count) ->
            val chip = TextView(this, null, 0, R.style.ReadTraceStatusChip).apply {
                text = "$tag ($count)"
                textSize = 12f
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    marginStart = dpToPx(8)
                }
                layoutParams = params
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    selectTag(if (selectedTag == tag) null else tag)
                }
            }
            tagGroup.addView(chip)
        }
        updateTagChips()
    }

    private fun updateTagChips() {
        tagAll.setBackgroundResource(
            if (selectedTag == null) R.drawable.bg_status_chip_selected else R.drawable.bg_status_chip,
        )
        tagAll.setTextColor(
            ContextCompat.getColor(this, if (selectedTag == null) R.color.white else R.color.readtrace_ink),
        )

        for (i in 1 until tagGroup.childCount) {
            val chip = tagGroup.getChildAt(i) as? TextView ?: continue
            val tagText = chip.text.toString().substringBeforeLast(" (")
            val isSelected = selectedTag == tagText
            chip.setBackgroundResource(
                if (isSelected) R.drawable.bg_status_chip_selected else R.drawable.bg_status_chip,
            )
            chip.setTextColor(
                ContextCompat.getColor(this, if (isSelected) R.color.white else R.color.readtrace_ink),
            )
        }
    }

    private fun refreshBooks() {
        val allBooks = databaseHelper.getBooks()
        updateShelfInsight(allBooks, allBooks.size)
        renderBadgesSummary()
        renderGallerySummary()
        renderMemoryCard()
        renderMonthlyStats()
        renderAnnualPersonaInsight()
        renderDynamicTags()
        refreshShelfOnly()
    }

    private fun renderAnnualPersonaInsight() {
        val persona = databaseHelper.getAnnualMindprintPersona()
        if (persona == null) {
            annualPersonaPanel.visibility = View.GONE
            return
        }
        annualPersonaPanel.visibility = View.VISIBLE
        annualPersonaBadge.text = persona.personaTitle
        annualPersonaDesc.text = "${persona.personaDesc}（已深度量化分析 ${persona.finishedBooksCount} 部读毕作品）"
        annualMindprintRadar.setMindprint(persona.avgMindprint, animate = false)
    }

    private fun renderGallerySummary() {
        val featuredCount = databaseHelper.getGalleryFeaturedWorks(24).size
        homeGallerySummary.text = if (featuredCount > 0) {
            getString(R.string.home_gallery_badge_format, featuredCount)
        } else {
            getString(R.string.home_gallery_desc)
        }
    }

    private fun updateViewModeButton() {
        btnToggleViewMode.text = if (isGridView) "📋 列表" else "🍱 双列"
    }

    private fun refreshShelfOnly() {
        val allBooks = databaseHelper.getBooks()
        val books = allBooks.filter { book ->
            val matchesMedia = selectedMediaType == null || book.mediaType == selectedMediaType
            val matchesStatus = selectedStatus == null || book.status == selectedStatus
            val matchesKeyword = searchKeyword.isEmpty() ||
                book.title.contains(searchKeyword, ignoreCase = true) ||
                (book.author?.contains(searchKeyword, ignoreCase = true) == true)
            val matchesTag = selectedTag == null || book.tags.contains(selectedTag)

            matchesMedia && matchesStatus && matchesKeyword && matchesTag
        }

        shelfCountText.text = getString(R.string.home_shelf_count_format, books.size)
        booksContainer.removeAllViews()

        if (books.isEmpty()) {
            booksContainer.visibility = View.GONE
            emptyPanel.clearAnimation()
            emptyPanel.visibility = View.VISIBLE
            val isFiltered = selectedMediaType != null || selectedStatus != null || searchKeyword.isNotEmpty() || selectedTag != null
            emptyTitle.setText(
                if (searchKeyword.isNotEmpty()) {
                    R.string.search_no_results
                } else if (isFiltered) {
                    R.string.empty_filter_title
                } else {
                    R.string.empty_shelf_title
                },
            )
            emptyBody.setText(
                if (isFiltered) R.string.empty_filter_body else R.string.empty_shelf_body,
            )
            emptyPanel.alpha = 0f
            emptyPanel.animate().alpha(1f).setDuration(220).start()
            return
        }

        emptyPanel.clearAnimation()
        emptyPanel.visibility = View.GONE
        booksContainer.visibility = View.VISIBLE

        if (!isGridView) {
            books.forEachIndexed { index, book ->
                val card = createBookCard(book)
                booksContainer.addView(card)
                animateBookCard(card, index)
            }
        } else {
            val rows = books.chunked(2)
            rows.forEachIndexed { rowIndex, pair ->
                val rowLayout = LinearLayout(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        topMargin = if (rowIndex == 0) 0 else dpToPx(10)
                    }
                    orientation = LinearLayout.HORIZONTAL
                }

                val card1 = createBookGridCard(pair[0])
                rowLayout.addView(card1)

                if (pair.size == 2) {
                    val spacer = View(this).apply {
                        layoutParams = LinearLayout.LayoutParams(dpToPx(10), LinearLayout.LayoutParams.MATCH_PARENT)
                    }
                    rowLayout.addView(spacer)
                    val card2 = createBookGridCard(pair[1])
                    rowLayout.addView(card2)
                } else {
                    val spacer = View(this).apply {
                        layoutParams = LinearLayout.LayoutParams(dpToPx(10), LinearLayout.LayoutParams.MATCH_PARENT)
                    }
                    rowLayout.addView(spacer)
                    val dummy = View(this).apply {
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                    }
                    rowLayout.addView(dummy)
                }

                booksContainer.addView(rowLayout)
                animateBookCard(rowLayout, rowIndex)
            }
        }
    }

    private fun renderMemoryCard() {
        val memoryResult = databaseHelper.getMemoryBook()
        if (memoryResult == null) {
            memoryPanel.visibility = View.GONE
            return
        }
        val (book, prompt) = memoryResult
        memoryPanel.visibility = View.VISIBLE
        memorySubPrompt.text = prompt
        val authorText = book.author?.let { " · $it" } ?: ""
        memoryBookTitle.text = "《${book.title}》$authorText"

        val quoteText = book.shortComment?.trim()?.takeIf { it.isNotEmpty() }
            ?: book.review?.trim()?.takeIf { it.isNotEmpty() }
            ?: getString(R.string.today_reflection_body)
        memoryBookQuote.text = quoteText
        memoryPanel.setOnClickListener {
            openBookDetail(memoryPanel, book.id)
        }
    }

    private fun renderMonthlyStats() {
        val stats = databaseHelper.getMonthlyFinishedStats(6)
        monthlyChartBarsContainer.removeAllViews()
        if (stats.isEmpty()) {
            monthlyChartBarsContainer.visibility = View.GONE
            monthlyStatEmptyText.visibility = View.VISIBLE
            return
        }

        monthlyStatEmptyText.visibility = View.GONE
        monthlyChartBarsContainer.visibility = View.VISIBLE

        val chronologicStats = stats.reversed()
        val maxCount = chronologicStats.maxOf { it.count }.coerceAtLeast(1)

        chronologicStats.forEach { stat ->
            val col = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
            }

            val countText = TextView(this).apply {
                text = stat.count.toString()
                textSize = 11f
                gravity = Gravity.CENTER
                setTextColor(ContextCompat.getColor(context, R.color.readtrace_accent))
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            col.addView(countText)

            val barHeightDp = (stat.count.toFloat() / maxCount * 55f + 8f).roundToInt()
            val bar = View(this).apply {
                setBackgroundResource(R.drawable.bg_stat_bar)
                val params = LinearLayout.LayoutParams(
                    dpToPx(18),
                    dpToPx(barHeightDp),
                ).apply {
                    topMargin = dpToPx(4)
                    bottomMargin = dpToPx(6)
                }
                layoutParams = params
            }
            col.addView(bar)

            val monthLabel = if (stat.month.length >= 7) {
                stat.month.substring(5, 7) + "月"
            } else {
                stat.month
            }
            val monthText = TextView(this).apply {
                text = monthLabel
                textSize = 10f
                gravity = Gravity.CENTER
                setTextColor(ContextCompat.getColor(context, R.color.readtrace_muted))
            }
            col.addView(monthText)

            monthlyChartBarsContainer.addView(col)
        }
    }

    private fun dpToPx(value: Int): Int =
        (value * resources.displayMetrics.density).roundToInt()

    private fun createBookCard(book: Book): View {
        val card = LayoutInflater.from(this)
            .inflate(R.layout.item_book_card, booksContainer, false)
        val coverImageView = card.findViewById<ImageView>(R.id.bookCardCoverImage)
        CoverImageHelper.loadCover(coverImageView, book.coverUrl)

        card.findViewById<TextView>(R.id.bookCardTitle).text = book.title
        card.findViewById<TextView>(R.id.bookCardAuthor).text =
            book.author ?: getString(R.string.unknown_author)

        val ratingLabel = book.rating?.let {
            getString(R.string.rating_format, RATING_FORMAT.format(it))
        } ?: getString(R.string.unrated)
        card.findViewById<TextView>(R.id.bookCardMeta).visibility = View.GONE
        card.findViewById<View>(R.id.bookCardSummaryRow).visibility = View.VISIBLE
        card.findViewById<TextView>(R.id.bookCardMediaBadge).text = book.mediaType.emoji
        card.findViewById<TextView>(R.id.bookCardStatusPill).text = book.status.getDisplayName(book.mediaType)
        card.findViewById<TextView>(R.id.bookCardRating).text = ratingLabel
        card.findViewById<TextView>(R.id.bookCardCategory).apply {
            val category = book.category?.trim()
            if (category.isNullOrEmpty()) {
                visibility = View.GONE
            } else {
                visibility = View.VISIBLE
                text = category
            }
        }

        card.findViewById<TextView>(R.id.bookCardTags).apply {
            if (book.tags.isEmpty()) {
                visibility = View.GONE
            } else {
                visibility = View.VISIBLE
                text = book.tags.joinToString(" · ")
            }
        }
        card.findViewById<TextView>(R.id.bookCardComment).apply {
            val comment = book.shortComment?.trim()
            if (comment.isNullOrEmpty()) {
                visibility = View.GONE
            } else {
                visibility = View.VISIBLE
                text = comment
            }
        }
        card.setOnClickListener {
            openBookDetail(card, book.id)
        }
        card.setOnLongClickListener {
            showBookHologramPeekDialog(book)
            true
        }
        com.example.readtrace.util.ViewAnimationHelper.attachSpringTouch(card, 0.97f)

        return card
    }

    private fun createBookGridCard(book: Book): View {
        val card = LayoutInflater.from(this).inflate(R.layout.item_book_grid_card, null, false)
        val coverImg = card.findViewById<ImageView>(R.id.bookGridCoverImage)
        CoverImageHelper.loadCover(coverImg, book.coverUrl)

        card.findViewById<TextView>(R.id.bookGridMediaBadge).text = book.mediaType.emoji
        card.findViewById<TextView>(R.id.bookGridStatusPill).text = book.status.getDisplayName(book.mediaType)
        card.findViewById<TextView>(R.id.bookGridTitle).text = book.title
        card.findViewById<TextView>(R.id.bookGridAuthor).text = book.author ?: getString(R.string.unknown_author)

        val ratingLabel = book.rating?.let {
            "★ ${RATING_FORMAT.format(it)}"
        } ?: getString(R.string.unrated)
        card.findViewById<TextView>(R.id.bookGridRating).text = ratingLabel

        card.findViewById<TextView>(R.id.bookGridCategory).apply {
            val category = book.category?.trim()
            if (category.isNullOrEmpty()) {
                visibility = View.GONE
            } else {
                visibility = View.VISIBLE
                text = category
            }
        }

        card.setOnClickListener {
            openBookDetail(card, book.id)
        }
        card.setOnLongClickListener {
            showBookHologramPeekDialog(book)
            true
        }
        com.example.readtrace.util.ViewAnimationHelper.attachSpringTouch(card, 0.96f)
        return card
    }

    private fun showBookHologramPeekDialog(book: Book) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_book_hologram_peek, null)
        val coverImg = view.findViewById<ImageView>(R.id.peekCoverImage)
        CoverImageHelper.loadCover(coverImg, book.coverUrl)

        view.findViewById<TextView>(R.id.peekMediaBadge).text = book.mediaType.emoji
        view.findViewById<TextView>(R.id.peekBookTitle).text = book.title
        view.findViewById<TextView>(R.id.peekBookAuthor).text = book.author ?: getString(R.string.unknown_author)
        view.findViewById<TextView>(R.id.peekStatusBadge).text = book.status.getDisplayName(book.mediaType)
        view.findViewById<TextView>(R.id.peekRating).text = book.rating?.let {
            getString(R.string.rating_format, RATING_FORMAT.format(it))
        } ?: getString(R.string.unrated)

        // 六维心智雷达
        val mindprint = databaseHelper.getMindprint(book.id)
        val radarView = view.findViewById<com.example.readtrace.widget.MindprintRadarView>(R.id.peekMindprintRadar)
        radarView.setMindprint(mindprint, animate = true)

        // 最近打卡记录
        val sessions = databaseHelper.getReadingSessions(book.id)
        val sessionContent = view.findViewById<TextView>(R.id.peekSessionContent)
        if (sessions.isEmpty()) {
            sessionContent.text = "暂无专注打卡记录，点击下方开启专注打卡。"
        } else {
            val latest = sessions.first()
            val pageStr = if (!latest.pagesRead.isNullOrBlank()) " · 读至 ${latest.pagesRead}" else ""
            val thoughtStr = if (!latest.thought.isNullOrBlank()) " · 「${latest.thought}」" else ""
            sessionContent.text = "专注 ${latest.durationMinutes} 分钟$pageStr$thoughtStr"
        }

        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .create()

        view.findViewById<View>(R.id.peekActionTimer).setOnClickListener {
            dialog.dismiss()
            startActivity(ReadingTimerActivity.createIntent(this, book.id, book.title))
        }
        view.findViewById<View>(R.id.peekActionPoster).setOnClickListener {
            dialog.dismiss()
            val notes = databaseHelper.getNotes(book.id)
            val quote = notes.firstOrNull { it.noteType == com.example.readtrace.model.NoteType.QUOTE }?.content ?: book.shortComment ?: "读书是一场穿越时空的灵魂相遇。"
            val chapter = notes.firstOrNull { it.noteType == com.example.readtrace.model.NoteType.QUOTE }?.chapter ?: "全息印记"
            startActivity(
                QuotePosterActivity.createIntent(
                    this,
                    book.id,
                    book.title,
                    book.author,
                    book.coverUrl,
                    quote,
                    chapter,
                ),
            )
        }
        view.findViewById<View>(R.id.peekActionDetail).setOnClickListener {
            dialog.dismiss()
            startActivity(BookDetailActivity.createIntent(this, book.id))
        }

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun updateShelfInsight(allBooks: List<Book>, visibleCount: Int) {
        statTotalValue.text = allBooks.size.toString()
        statReadingValue.text = allBooks.count { it.status == BookStatus.READING }.toString()
        statFinishedValue.text = allBooks.count { it.status == BookStatus.FINISHED }.toString()
        val ratedBooks = allBooks.mapNotNull { it.rating }
        statAverageValue.text =
            if (ratedBooks.isEmpty()) {
                getString(R.string.home_average_empty)
            } else {
                RATING_FORMAT.format(ratedBooks.average())
            }
        shelfCountText.text = selectedStatus?.let {
            getString(R.string.home_shelf_filtered_count_format, it.displayName, visibleCount)
        } ?: getString(R.string.home_shelf_count_format, visibleCount)
    }

    private fun exportShelfScrollImage() {
        val allBooks = databaseHelper.getBooks()
        val filteredBooks = allBooks.filter { book ->
            val matchesMedia = selectedMediaType == null || book.mediaType == selectedMediaType
            val matchesStatus = selectedStatus == null || book.status == selectedStatus
            val matchesKeyword = searchKeyword.isBlank() ||
                book.title.contains(searchKeyword, ignoreCase = true) ||
                (book.author?.contains(searchKeyword, ignoreCase = true) == true) ||
                (book.category?.contains(searchKeyword, ignoreCase = true) == true)
            val matchesTag = selectedTag == null || book.tags.contains(selectedTag)
            matchesMedia && matchesStatus && matchesKeyword && matchesTag
        }

        if (filteredBooks.isEmpty()) {
            Toast.makeText(this, "当前筛选条件下暂无作品可导出", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val width = 1080
            val headerHeight = 320
            val itemHeight = 180
            val footerHeight = 220
            val totalHeight = headerHeight + filteredBooks.size * itemHeight + footerHeight

            val bitmap = android.graphics.Bitmap.createBitmap(width, totalHeight, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)

            // 背景底色 (宣纸米色)
            canvas.drawColor(android.graphics.Color.parseColor("#F8F5EE"))

            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)

            // 边框装饰线
            paint.style = android.graphics.Paint.Style.STROKE
            paint.color = android.graphics.Color.parseColor("#DCD3C7")
            paint.strokeWidth = 3f
            canvas.drawRect(40f, 40f, (width - 40).toFloat(), (totalHeight - 40).toFloat(), paint)
            canvas.drawRect(48f, 48f, (width - 48).toFloat(), (totalHeight - 48).toFloat(), paint)

            // 头部标题
            paint.style = android.graphics.Paint.Style.FILL
            paint.color = android.graphics.Color.parseColor("#20241F")
            paint.textSize = 52f
            paint.isFakeBoldText = true
            paint.textAlign = android.graphics.Paint.Align.CENTER

            val filterTitle = when {
                selectedTag != null -> "「${selectedTag}」主题"
                selectedMediaType != null -> selectedMediaType!!.displayName
                selectedStatus != null -> selectedStatus!!.displayName
                else -> "个人典藏"
            }
            canvas.drawText("《阅痕 · $filterTitle 全息书单长卷》", width / 2f, 130f, paint)

            paint.textSize = 28f
            paint.isFakeBoldText = false
            paint.color = android.graphics.Color.parseColor("#71776D")
            val totalHours = filteredBooks.sumOf { databaseHelper.getTotalReadingMinutes(it.id) } / 60.0
            val hoursStr = if (totalHours > 0) " · 累计专注 ${String.format(java.util.Locale.getDefault(), "%.1f", totalHours)} 小时" else ""
            canvas.drawText("收录 ${filteredBooks.size} 部精神印记$hoursStr", width / 2f, 190f, paint)

            paint.color = android.graphics.Color.parseColor("#4E7A5A")
            paint.textSize = 24f
            paint.isFakeBoldText = true
            canvas.drawText("✦ 纸寿千年 · 心智留痕 ✦", width / 2f, 240f, paint)

            // 分割线
            paint.color = android.graphics.Color.parseColor("#DCD3C7")
            paint.strokeWidth = 2f
            canvas.drawLine(80f, 280f, (width - 80).toFloat(), 280f, paint)

            // 逐本绘制
            var currentY = headerHeight.toFloat()
            for ((index, book) in filteredBooks.withIndex()) {
                val itemTop = currentY
                val itemBottom = currentY + itemHeight

                paint.textAlign = android.graphics.Paint.Align.LEFT
                paint.textSize = 34f
                paint.color = android.graphics.Color.parseColor("#4E7A5A")
                paint.isFakeBoldText = true
                canvas.drawText(String.format(java.util.Locale.getDefault(), "%02d", index + 1), 80f, itemTop + 55f, paint)

                paint.textSize = 36f
                paint.color = android.graphics.Color.parseColor("#20241F")
                paint.isFakeBoldText = true
                val title = "${book.mediaType.emoji} 《${book.title}》"
                val author = book.author?.let { " · $it" } ?: ""
                val fullTitle = if ((title + author).length > 22) (title + author).take(22) + "..." else title + author
                canvas.drawText(fullTitle, 150f, itemTop + 55f, paint)

                paint.textSize = 26f
                paint.color = android.graphics.Color.parseColor("#C47D5C")
                paint.isFakeBoldText = true
                val ratingStr = book.rating?.let { "★ $it" } ?: ""
                val statusStr = "【${book.status.getDisplayName(book.mediaType)}】"
                canvas.drawText("$statusStr $ratingStr", 150f, itemTop + 98f, paint)

                val mp = databaseHelper.getMindprint(book.id)
                paint.textSize = 24f
                paint.color = android.graphics.Color.parseColor("#71776D")
                paint.isFakeBoldText = false
                val mpSummary = "🧠 思想 ${String.format(java.util.Locale.getDefault(), "%.1f", mp.depthScore)}  🖋️ 文笔 ${String.format(java.util.Locale.getDefault(), "%.1f", mp.artistryScore)}  ❤️ 情感 ${String.format(java.util.Locale.getDefault(), "%.1f", mp.emotionScore)}  🌿 治愈 ${String.format(java.util.Locale.getDefault(), "%.1f", mp.healingScore)}"
                canvas.drawText(mpSummary, 150f, itemTop + 138f, paint)

                if (index < filteredBooks.size - 1) {
                    paint.color = android.graphics.Color.parseColor("#EADFD5")
                    paint.strokeWidth = 1.5f
                    canvas.drawLine(100f, itemBottom, (width - 100).toFloat(), itemBottom, paint)
                }

                currentY += itemHeight
            }

            val footerCenterY = currentY + footerHeight / 2f
            paint.textAlign = android.graphics.Paint.Align.CENTER
            paint.textSize = 28f
            paint.color = android.graphics.Color.parseColor("#71776D")
            paint.isFakeBoldText = false
            canvas.drawText("阅痕 ReadTrace · 留存文字的永恒温度", width / 2f - 60f, footerCenterY - 15f, paint)

            paint.textSize = 22f
            paint.color = android.graphics.Color.parseColor("#9E9E9E")
            val dateStr = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy年MM月dd日"))
            canvas.drawText("长卷生成时间：$dateStr", width / 2f - 60f, footerCenterY + 25f, paint)

            val stampX = width - 200f
            val stampY = footerCenterY - 35f
            paint.style = android.graphics.Paint.Style.STROKE
            paint.color = android.graphics.Color.parseColor("#A84232")
            paint.strokeWidth = 3f
            canvas.drawRect(stampX, stampY, stampX + 90f, stampY + 90f, paint)

            paint.style = android.graphics.Paint.Style.FILL
            paint.color = android.graphics.Color.parseColor("#A84232")
            paint.textSize = 24f
            paint.isFakeBoldText = true
            paint.textAlign = android.graphics.Paint.Align.CENTER
            canvas.drawText("阅痕", stampX + 45f, stampY + 38f, paint)
            canvas.drawText("馆藏", stampX + 45f, stampY + 72f, paint)

            saveAndShareScrollBitmap(bitmap, filterTitle)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "生成书单长卷失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveAndShareScrollBitmap(bitmap: android.graphics.Bitmap, filterTitle: String) {
        val filename = "ReadTrace_Scroll_${filterTitle}_${System.currentTimeMillis()}.png"
        var fos: java.io.OutputStream? = null
        var success = false

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val resolver = contentResolver
            val contentValues = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/png")
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES + "/ReadTrace")
            }
            val imageUri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            if (imageUri != null) {
                fos = resolver.openOutputStream(imageUri)
                success = bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, fos!!)
            }
        } else {
            val imagesDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES).toString() + "/ReadTrace"
            val file = java.io.File(imagesDir)
            if (!file.exists()) file.mkdirs()
            val imageFile = java.io.File(file, filename)
            fos = java.io.FileOutputStream(imageFile)
            success = bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, fos)
        }
        fos?.close()

        if (success) {
            AlertDialog.Builder(this)
                .setTitle("🎉 书单长卷已生成")
                .setMessage("全息书单长卷已成功保存至系统相册！是否立即分享给书友？")
                .setNegativeButton("稍后再说", null)
                .setPositiveButton("🔗 立即分享") { _, _ ->
                    val cachePath = java.io.File(cacheDir, "images")
                    cachePath.mkdirs()
                    val file = java.io.File(cachePath, "share_scroll.png")
                    val stream = java.io.FileOutputStream(file)
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
                    stream.close()

                    val contentUri = androidx.core.content.FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "image/png"
                        putExtra(Intent.EXTRA_STREAM, contentUri)
                        putExtra(Intent.EXTRA_SUBJECT, "《阅痕 · $filterTitle 全息书单长卷》")
                        putExtra(Intent.EXTRA_TEXT, "这是我的《阅痕 · $filterTitle 全息书单长卷》，记录阅读的心智留痕与精神印记。")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(Intent.createChooser(shareIntent, "分享全息书单长卷"))
                }
                .show()
        } else {
            Toast.makeText(this, "保存长卷失败，请重试", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openBookDetail(card: View, bookId: Long) {
        card.isEnabled = false
        card.animate()
            .scaleX(0.985f)
            .scaleY(0.985f)
            .setDuration(90L)
            .withEndAction {
                card.scaleX = 1f
                card.scaleY = 1f
                card.isEnabled = true
                startActivity(BookDetailActivity.createIntent(this, bookId))
            }
            .start()
    }

    private fun animateBookCard(card: View, index: Int) {
        com.example.readtrace.util.ViewAnimationHelper.staggerFadeIn(card, index)
    }

    override fun onDestroy() {
        databaseHelper.close()
        super.onDestroy()
    }

    companion object {
        private val RATING_FORMAT = DecimalFormat("0.#")
    }
}
