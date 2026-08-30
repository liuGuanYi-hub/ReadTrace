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
import android.widget.ProgressBar
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
import com.example.readtrace.ResonancePosterActivity
import com.example.readtrace.TrashActivity
import com.example.readtrace.VinylCassettePlayerActivity
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

    // Header
    private lateinit var auroraBackgroundView: com.example.readtrace.widget.AuroraFluidBackgroundView
    private lateinit var homeTitle: TextView
    private lateinit var homeSubtitle: TextView
    private lateinit var themeToggleButton: TextView
    private lateinit var addBtn: TextView
    private lateinit var importPresetBtn: TextView
    private lateinit var backupBtn: TextView
    private lateinit var trashBtn: TextView

    // 🌟 Hero 策展主位
    private lateinit var heroCuratorialCard: View
    private lateinit var heroSpecularOverlay: com.example.readtrace.widget.HolographicSpecularOverlayView
    private lateinit var heroBadgeMedia: TextView
    private lateinit var heroBookCover: ImageView
    private lateinit var heroCoverPlaceholder: TextView
    private lateinit var heroBookTitle: TextView
    private lateinit var heroBookAuthor: TextView
    private lateinit var heroBookRating: TextView
    private lateinit var heroBookQuote: com.example.readtrace.widget.DropCapTextView
    private lateinit var heroBtnRead: TextView
    private lateinit var heroBtnDetail: TextView
    private var currentHeroBook: Book? = null

    private lateinit var gyroscopeHelper: com.example.readtrace.util.GyroscopeParallaxHelper



    // 📜 羊皮纸便签横幅
    private lateinit var parchmentQuoteRibbon: View
    private lateinit var btnRefreshParchmentQuote: TextView
    private lateinit var parchmentQuoteText: com.example.readtrace.widget.DropCapTextView
    private lateinit var parchmentQuoteSource: TextView
    private var currentParchmentQuoteText: String? = null
    private lateinit var heroCuratorialBadge: com.example.readtrace.widget.EditorialBadgeView

    // 统计胶囊
    private lateinit var statTotalValue: TextView
    private lateinit var statReadingValue: TextView
    private lateinit var statFinishedValue: TextView
    private lateinit var statWishlistValue: TextView
    private lateinit var statAverageValue: TextView

    // 四大媒介 Bento 展馆
    private lateinit var hubCardBook: View
    private lateinit var hubBookCountBadge: TextView
    private lateinit var hubBookSubtitle: TextView
    private lateinit var hubBookCoversPreview: LinearLayout
    private lateinit var btnEnterBookHub: View

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

    private lateinit var hubCardMusic: View
    private lateinit var hubMusicCountBadge: TextView
    private lateinit var hubMusicSubtitle: TextView
    private lateinit var hubMusicCoversPreview: LinearLayout
    private lateinit var btnEnterMusicHub: View
    private lateinit var btnQuickMusicVinyl: View
    private lateinit var btnQuickMusicResonance: View

    // 时光深处的回响
    private lateinit var memoryPanel: View
    private lateinit var memoryTitle: TextView
    private lateinit var memoryMediaBadge: TextView
    private lateinit var memoryBookCover: ImageView
    private lateinit var memoryCoverPlaceholder: TextView
    private lateinit var memoryBookTitle: TextView
    private lateinit var memoryBookMeta: TextView
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
        databaseHelper = BookDatabaseHelper.getInstance(requireContext())

        initViews(view)
        setupListeners()
        setupGyroscopeParallax()

        // 卡片滚动进场：进入视口渐入上浮
        com.example.readtrace.util.ScrollReveal.attach(
            view.findViewById(R.id.hubScroll),
            listOf(
                view.findViewById(R.id.heroCuratorialContainer),
                view.findViewById(R.id.parchmentQuoteRibbon),
                view.findViewById(R.id.hubCardBook),
                view.findViewById(R.id.hubCardAnime),
                view.findViewById(R.id.hubCardMovie),
                view.findViewById(R.id.hubCardGame),
                view.findViewById(R.id.hubCardMusic),
                view.findViewById(R.id.memoryPanel),
            ),
        )
    }

    private fun setupGyroscopeParallax() {
        gyroscopeHelper = com.example.readtrace.util.GyroscopeParallaxHelper(requireContext())
        gyroscopeHelper.bind3DParallax(heroCuratorialCard, maxRotation = 8f, maxTranslation = 12f)
        gyroscopeHelper.bind3DParallax(heroBookCover, maxRotation = 14f, maxTranslation = 16f)
        gyroscopeHelper.bindHolographicSpecular(heroSpecularOverlay)
        gyroscopeHelper.bind3DParallax(parchmentQuoteRibbon, maxRotation = 5f, maxTranslation = 8f)
        gyroscopeHelper.bind3DParallax(memoryBookCover, maxRotation = 10f, maxTranslation = 12f)
        gyroscopeHelper.bindLifecycle(viewLifecycleOwner.lifecycle)
    }

    override fun onResume() {
        super.onResume()
        auroraBackgroundView.startAnimation()
        homeSubtitle.text = com.example.readtrace.util.CircadianLightingEngine.getCircadianSummary()
        updateThemeToggleIcon()
        refreshDashboard()
    }

    override fun onPause() {
        auroraBackgroundView.stopAnimation()
        super.onPause()
    }

    private fun initViews(view: View) {
        auroraBackgroundView = view.findViewById(R.id.auroraBackgroundView)
        homeTitle = view.findViewById(R.id.homeTitle)
        homeSubtitle = view.findViewById(R.id.homeSubtitle)
        themeToggleButton = view.findViewById(R.id.themeToggleButton)
        addBtn = view.findViewById(R.id.addButton)
        importPresetBtn = view.findViewById(R.id.importPresetButton)
        backupBtn = view.findViewById(R.id.backupButton)
        trashBtn = view.findViewById(R.id.trashButton)

        // 🌟 Hero
        heroCuratorialCard = view.findViewById(R.id.heroCuratorialCard)
        heroSpecularOverlay = view.findViewById(R.id.heroSpecularOverlay)
        heroCuratorialBadge = view.findViewById(R.id.heroCuratorialBadge)
        heroCuratorialBadge.setBadgeContent("NO. 01", "CURATED")
        heroBadgeMedia = view.findViewById(R.id.heroBadgeMedia)
        heroBookCover = view.findViewById(R.id.heroBookCover)
        heroCoverPlaceholder = view.findViewById(R.id.heroCoverPlaceholder)
        heroBookTitle = view.findViewById(R.id.heroBookTitle)
        heroBookAuthor = view.findViewById(R.id.heroBookAuthor)
        heroBookRating = view.findViewById(R.id.heroBookRating)
        heroBookQuote = view.findViewById(R.id.heroBookQuote)
        heroBtnRead = view.findViewById(R.id.heroBtnRead)
        heroBtnDetail = view.findViewById(R.id.heroBtnDetail)

        // 📊 Bento 副卡


        // 📜 羊皮纸便签
        parchmentQuoteRibbon = view.findViewById(R.id.parchmentQuoteRibbon)
        btnRefreshParchmentQuote = view.findViewById(R.id.btnRefreshParchmentQuote)
        parchmentQuoteText = view.findViewById(R.id.parchmentQuoteText)
        parchmentQuoteSource = view.findViewById(R.id.parchmentQuoteSource)

        // 统计胶囊
        statTotalValue = view.findViewById(R.id.statTotalValue)
        statReadingValue = view.findViewById(R.id.statReadingValue)
        statFinishedValue = view.findViewById(R.id.statFinishedValue)
        statWishlistValue = view.findViewById(R.id.statWishlistValue)
        statAverageValue = view.findViewById(R.id.statAverageValue)

        // 四大媒介
        hubCardBook = view.findViewById(R.id.hubCardBook)
        hubBookCountBadge = view.findViewById(R.id.hubBookCountBadge)
        hubBookSubtitle = view.findViewById(R.id.hubBookSubtitle)
        hubBookCoversPreview = view.findViewById(R.id.hubBookCoversPreview)
        btnEnterBookHub = view.findViewById(R.id.btnEnterBookHub)

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

        hubCardMusic = view.findViewById(R.id.hubCardMusic)
        hubMusicCountBadge = view.findViewById(R.id.hubMusicCountBadge)
        hubMusicSubtitle = view.findViewById(R.id.hubMusicSubtitle)
        hubMusicCoversPreview = view.findViewById(R.id.hubMusicCoversPreview)
        btnEnterMusicHub = view.findViewById(R.id.btnEnterMusicHub)
        btnQuickMusicVinyl = view.findViewById(R.id.btnQuickMusicVinyl)
        btnQuickMusicResonance = view.findViewById(R.id.btnQuickMusicResonance)


        // 记忆面板
        memoryPanel = view.findViewById(R.id.memoryPanel)
        memoryTitle = view.findViewById(R.id.memoryTitle)
        memoryMediaBadge = view.findViewById(R.id.memoryMediaBadge)
        memoryBookCover = view.findViewById(R.id.memoryBookCover)
        memoryCoverPlaceholder = view.findViewById(R.id.memoryCoverPlaceholder)
        memoryBookTitle = view.findViewById(R.id.memoryBookTitle)
        memoryBookMeta = view.findViewById(R.id.memoryBookMeta)
        memoryBookQuote = view.findViewById(R.id.memoryBookQuote)
    }

    private fun setupListeners() {
        themeToggleButton.setOnClickListener {
            val ctx = requireContext()
            ThemeHelper.toggleDarkMode(ctx)
            updateThemeToggleIcon()
            com.example.readtrace.util.ConfettiBurstHelper.burstCenter(requireActivity())
            com.example.readtrace.util.HapticFeedbackEngine.stampImpact(ctx)
            activity?.recreate()
        }

        addBtn.setOnLongClickListener {
            showQuickRecordDialog()
            true
        }

        addBtn.setOnClickListener { startActivity(Intent(requireContext(), AddBookActivity::class.java)) }
        importPresetBtn.setOnClickListener { showImportCsvDialog() }
        trashBtn.setOnClickListener { startActivity(TrashActivity.createIntent(requireContext())) }
        backupBtn.setOnClickListener { startActivity(Intent(requireContext(), BackupActivity::class.java)) }

        // 🌟 Hero 策展位交互
        heroBtnRead.setOnClickListener {
            val book = currentHeroBook
            if (book != null) {
                when (book.mediaType) {
                    MediaType.BOOK -> startActivity(BookDetailActivity.createIntent(requireContext(), book.id))
                    MediaType.ANIME -> startActivity(CulturalPassportActivity.createIntent(requireContext(), MediaType.ANIME))
                    MediaType.MOVIE -> startActivity(MovieTicketPosterActivity.createIntent(requireContext(), book.id))
                    MediaType.GAME -> startActivity(GameCartridgePosterActivity.createIntent(requireContext(), book.id))
                    MediaType.MUSIC -> startActivity(Intent(requireContext(), ResonancePosterActivity::class.java))
                }
            } else {
                Toast.makeText(requireContext(), "请先添加或导入藏品", Toast.LENGTH_SHORT).show()
            }
        }
        val openHeroDetail = {
            val book = currentHeroBook
            val act = activity
            if (book != null) {
                val intent = BookDetailActivity.createIntent(requireContext(), book.id)
                if (act != null) {
                    val options = com.example.readtrace.util.TransitionHelper.createTransitionOptions(act, heroBookCover)
                    startActivity(intent, options.toBundle())
                } else {
                    startActivity(intent)
                }
            }
        }
        heroBtnDetail.setOnClickListener { openHeroDetail() }
        heroCuratorialCard.setOnClickListener { openHeroDetail() }

        // 📊 Bento 副卡交互


        // 📜 羊皮纸便签轮播交互
        btnRefreshParchmentQuote.setOnClickListener {
            renderParchmentQuote(excludeQuote = currentParchmentQuoteText)
            ViewAnimationHelper.playCardBounce(parchmentQuoteRibbon)
        }
        parchmentQuoteRibbon.setOnClickListener {
            renderParchmentQuote(excludeQuote = currentParchmentQuoteText)
            ViewAnimationHelper.playCardBounce(parchmentQuoteRibbon)
        }

        // 四大媒介交互
        val openBookHub = { startActivity(MediaHubActivity.createIntent(requireContext(), MediaType.BOOK)) }
        hubCardBook.setOnClickListener { openBookHub() }
        btnEnterBookHub.setOnClickListener { openBookHub() }

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
            val firstMovie = databaseHelper.getCachedBooks().firstOrNull { it.mediaType == MediaType.MOVIE }
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
            val firstGame = databaseHelper.getCachedBooks().firstOrNull { it.mediaType == MediaType.GAME }
            if (firstGame != null) {
                startActivity(GameCartridgePosterActivity.createIntent(requireContext(), firstGame.id))
            } else {
                Toast.makeText(requireContext(), "游艺厅暂无游戏记录", Toast.LENGTH_SHORT).show()
            }
        }
        btnQuickGamePassport.setOnClickListener {
            startActivity(CulturalPassportActivity.createIntent(requireContext(), MediaType.GAME))
        }

        val openMusicHub = { startActivity(MediaHubActivity.createIntent(requireContext(), MediaType.MUSIC)) }
        hubCardMusic.setOnClickListener { openMusicHub() }
        btnEnterMusicHub.setOnClickListener { openMusicHub() }
        btnQuickMusicVinyl.setOnClickListener {
            val firstMusic = databaseHelper.getCachedBooks().firstOrNull { it.mediaType == MediaType.MUSIC }
            if (firstMusic != null) {
                startActivity(VinylCassettePlayerActivity.createIntent(requireContext(), firstMusic.id))
            } else {
                Toast.makeText(requireContext(), "音乐馆暂无曲目，请先添加或导入", Toast.LENGTH_SHORT).show()
            }
        }
        btnQuickMusicResonance.setOnClickListener {
            startActivity(Intent(requireContext(), ResonancePosterActivity::class.java))
        }
    }

    private fun updateThemeToggleIcon() {
        val ctx = context ?: return
        val isDark = ThemeHelper.isDarkMode(ctx)
        themeToggleButton.text = if (isDark) "🌙" else "☀️"
        if (::auroraBackgroundView.isInitialized) {
            auroraBackgroundView.updateThemePalette(isDark)
        }
    }

    /** 极速记录：只填书名（可选媒介），其余全自动默认，即填即入库 */
    private fun showQuickRecordDialog() {
        val ctx = requireContext()
        val density = resources.displayMetrics.density
        fun px(v: Int) = (v * density).toInt()

        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(22), px(18), px(22), px(20))
            setBackgroundResource(R.drawable.bg_elegant_dialog)
        }
        container.addView(TextView(ctx).apply {
            text = "⚡ 极速记录"
            textSize = 16.5f
            setTextColor(requireContext().getColor(R.color.readtrace_ink))
        })
        container.addView(TextView(ctx).apply {
            text = "只填名字，默认在读 · 4 星 · 今天开始"
            textSize = 11.5f
            setTextColor(requireContext().getColor(R.color.readtrace_muted))
            setPadding(0, px(3), 0, px(10))
        })

        val nameInput = android.widget.EditText(ctx).apply {
            hint = "作品名称 *"
            setTextSize(14f)
            setTextColor(requireContext().getColor(R.color.readtrace_ink))
            setHintTextColor(requireContext().getColor(R.color.readtrace_muted))
            setBackgroundResource(R.drawable.bg_form_input)
            setPadding(px(13), px(10), px(13), px(10))
            setSingleLine()
        }
        container.addView(nameInput)

        var selectedMedia = com.example.readtrace.model.MediaType.BOOK
        val chipRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, px(12), 0, 0)
        }
        val mediaChips = listOf(
            "📚" to com.example.readtrace.model.MediaType.BOOK,
            "🌸" to com.example.readtrace.model.MediaType.ANIME,
            "🎬" to com.example.readtrace.model.MediaType.MOVIE,
            "🎮" to com.example.readtrace.model.MediaType.GAME,
            "💿" to com.example.readtrace.model.MediaType.MUSIC,
        )
        val chipViews = mutableListOf<TextView>()
        mediaChips.forEach { (emoji, media) ->
            val chip = TextView(ctx).apply {
                text = emoji
                textSize = 17f
                gravity = android.view.Gravity.CENTER
                setBackgroundResource(
                    if (media == selectedMedia) R.drawable.bg_chip_picker_selected else R.drawable.bg_chip_picker_idle,
                )
                layoutParams = LinearLayout.LayoutParams(px(40), px(36)).apply { marginEnd = px(8) }
                setOnClickListener {
                    selectedMedia = media
                    mediaChips.forEachIndexed { i, (_, m) ->
                        chipViews[i].setBackgroundResource(
                            if (m == selectedMedia) R.drawable.bg_chip_picker_selected else R.drawable.bg_chip_picker_idle,
                        )
                    }
                }
            }
            chipViews.add(chip)
            chipRow.addView(chip)
        }
        container.addView(chipRow)

        val btnSave = TextView(ctx).apply {
            text = "收 入 藏"
            gravity = android.view.Gravity.CENTER
            textSize = 14f
            isAllCaps = false
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setBackgroundResource(R.drawable.bg_chip_picker_selected)
            setTextColor(requireContext().getColor(R.color.chip_selected_text))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, px(44),
            ).apply { topMargin = px(16) }
        }
        container.addView(btnSave)

        val dialog = android.app.Dialog(ctx).apply {
            requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
            setContentView(container)
            window?.apply {
                setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
                setLayout((resources.displayMetrics.widthPixels * 0.9f).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT)
                setGravity(android.view.Gravity.CENTER)
            }
        }
        container.alpha = 0f
        container.translationY = px(28).toFloat()
        container.animate().alpha(1f).translationY(0f).setDuration(280L).start()

        btnSave.setOnClickListener {
            val title = nameInput.text.toString().trim()
            if (title.isEmpty()) {
                nameInput.animate().translationX(-px(6).toFloat()).setDuration(50)
                    .withEndAction { nameInput.animate().translationX(px(6).toFloat()).setDuration(50).withEndAction { nameInput.animate().translationX(0f).setDuration(50).start() }.start() }
                    .start()
                return@setOnClickListener
            }
            val today = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
            val book = com.example.readtrace.model.Book(
                title = title,
                status = com.example.readtrace.model.BookStatus.READING,
                mediaType = selectedMedia,
                rating = 8.0,
                startDate = today,
            )
            val newId = databaseHelper.insertBook(book)
            val seed = 8.0
            databaseHelper.saveMindprint(
                com.example.readtrace.model.BookMindprint(
                    bookId = newId,
                    depthScore = seed, artistryScore = seed, emotionScore = seed,
                    logicScore = seed, difficultyScore = 5.0, healingScore = seed,
                ),
            )
            dialog.dismiss()
            refreshDashboard()
            com.example.readtrace.util.HapticFeedbackEngine.lightClick(ctx)
            Toast.makeText(ctx, "⚡ 已收入藏《$title》，可稍后补充详情", Toast.LENGTH_SHORT).show()
        }
        dialog.show()
        nameInput.requestFocus()
    }

    private fun refreshDashboard() {
        val allBooks = databaseHelper.getCachedBooks()
        val total = allBooks.size
        val reading = allBooks.count { it.status == BookStatus.READING }
        val finished = allBooks.count { it.status == BookStatus.FINISHED }
        val rated = allBooks.mapNotNull { it.rating }

        val wishlist = total - reading - finished
        animateStatCountUp(statTotalValue, total, 0)
        animateStatCountUp(statReadingValue, reading, 1)
        animateStatCountUp(statFinishedValue, finished, 2)
        animateStatCountUp(statWishlistValue, wishlist, 3)
        statAverageValue.text = if (rated.isEmpty()) "均分 ★ -" else "均分 ★ ${RATING_FORMAT.format(rated.average() / 2.0)}"


        val phase = com.example.readtrace.util.CircadianLightingEngine.getCurrentPhase()
        if (::heroCuratorialBadge.isInitialized) {
            heroCuratorialBadge.setBadgeContent("NO. 01 · ${phase.displayName}", "CURATED")
        }
        if (::auroraBackgroundView.isInitialized) {
            val ctx = context
            val isDark = if (ctx != null) ThemeHelper.isDarkMode(ctx) else true
            auroraBackgroundView.setCircadianPhase(phase, darkMode = isDark)
        }

        renderHeroCuratorialCard(allBooks)
        renderParchmentQuote()

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
        renderHubCard(
            mediaType = MediaType.MUSIC,
            allWorks = allBooks.filter { it.mediaType == MediaType.MUSIC },
            badgeView = hubMusicCountBadge,
            subtitleView = hubMusicSubtitle,
            coversContainer = hubMusicCoversPreview,
            unit = "首曲目",
        )

        renderMemoryCard()
    }

    private val lastStatValues = intArrayOf(-1, -1, -1, -1)

    /** 总览数字滚动动画：仅在数值变化时从 0 滚动到目标值 */
    private fun animateStatCountUp(view: TextView, target: Int, slot: Int) {
        if (lastStatValues.getOrNull(slot) == target) {
            view.text = target.toString()
            return
        }
        lastStatValues[slot] = target
        android.animation.ValueAnimator.ofInt(0, target).apply {
            duration = 550L
            interpolator = android.view.animation.DecelerateInterpolator()
            addUpdateListener { view.text = (it.animatedValue as Int).toString() }
            start()
        }
    }

    private fun renderHeroCuratorialCard(allBooks: List<Book>) {
        val featuredBook = allBooks.firstOrNull { it.status == BookStatus.READING }
            ?: allBooks.maxByOrNull { it.rating ?: 0.0 }
            ?: allBooks.firstOrNull()

        currentHeroBook = featuredBook
        if (featuredBook != null) {
            heroCuratorialCard.visibility = View.VISIBLE
            heroBadgeMedia.text = "${featuredBook.mediaType.emoji} ${featuredBook.category ?: featuredBook.mediaType.displayName}"
            (heroBookTitle as? com.example.readtrace.widget.ScrambleTextView)?.setScrambleText("《${featuredBook.title}》")
                ?: run { heroBookTitle.text = "《${featuredBook.title}》" }
            heroBookAuthor.text = featuredBook.author?.ifBlank { "未知作者" } ?: "未知作者"
            heroBtnRead.text = when (featuredBook.mediaType) {
                MediaType.BOOK -> "📖 3D 沉浸翻阅"
                MediaType.ANIME -> "🌸 追番入境签证"
                MediaType.MOVIE -> "🎟️ 透光电影票根"
                MediaType.GAME -> "🕹️ 全息白金卡带"
                MediaType.MUSIC -> "🎴 共鸣双生微卡"
            }

            val rating = featuredBook.rating
            heroBookRating.text = if (rating != null && rating > 0) "★ ${RATING_FORMAT.format(rating / 2.0)} · 精神典藏" else "✦ 重点策展推荐"

            val quote = featuredBook.shortComment?.takeIf { it.isNotBlank() }
                ?: featuredBook.review?.takeIf { it.isNotBlank() }
                ?: databaseHelper.getNotes(featuredBook.id).firstOrNull()?.content
                ?: "“你在你的玫瑰花身上耗费的时间，使你的玫瑰花变得如此重要。”"
            val formattedQuote = if (quote.startsWith("“")) quote else "“$quote”"
            heroBookQuote.setEditorialText(formattedQuote)

            if (!featuredBook.coverUrl.isNullOrBlank()) {
                heroBookCover.visibility = View.VISIBLE
                heroCoverPlaceholder.visibility = View.GONE
                CoverImageHelper.loadCover(heroBookCover, featuredBook.coverUrl)
            } else {
                heroBookCover.visibility = View.GONE
                heroCoverPlaceholder.visibility = View.VISIBLE
                heroCoverPlaceholder.text = "${featuredBook.mediaType.emoji}\n${featuredBook.title.take(4)}"
            }
        } else {
            heroCuratorialCard.visibility = View.GONE
        }
    }


    private fun renderParchmentQuote(excludeQuote: String? = null) {
        val (book, quote) = databaseHelper.getRandomOrNextQuote(excludeQuote)
        currentParchmentQuoteText = quote
        val formattedQuote = if (quote.startsWith("“")) quote else "“$quote”"
        parchmentQuoteText.setEditorialText(formattedQuote)
        val authorPart = book?.author?.let { " · $it" } ?: ""
        val titlePart = book?.title?.let { "《$it》" } ?: "《阅痕 ReadTrace》"
        parchmentQuoteSource.text = "—— $titlePart$authorPart"
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
                val act = activity
                val intent = BookDetailActivity.createIntent(requireContext(), book.id)
                if (act != null) {
                    val options = com.example.readtrace.util.TransitionHelper.createTransitionOptions(act, cardView)
                    startActivity(intent, options.toBundle())
                } else {
                    startActivity(intent)
                }
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
        val memoryReason = memoryPair.second
        memoryPanel.visibility = View.VISIBLE

        val isYearsAgo = memoryReason.contains("年前")
        memoryTitle.text = if (isYearsAgo) "🕰️ 那年今日 · 时光印记" else "🕰️ 时光深处的印记"
        memoryMediaBadge.text = "${memoryBook.mediaType.emoji} ${memoryBook.mediaType.displayName}"

        memoryBookTitle.text = "《${memoryBook.title}》"

        val authorStr = memoryBook.author?.takeIf { it.isNotBlank() } ?: "经典创作者"
        val ratingVal = memoryBook.rating
        val ratingStr = if (ratingVal != null && ratingVal > 0) " · ★ ${RATING_FORMAT.format(ratingVal / 2.0)}" else ""
        val categoryStr = memoryBook.category?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""
        memoryBookMeta.text = "$authorStr$categoryStr$ratingStr"

        val quote = if (memoryReason.isNotBlank() && isYearsAgo) {
            "⏳ $memoryReason"
        } else {
            val raw = memoryBook.shortComment?.takeIf { it.isNotBlank() }
                ?: memoryBook.review?.takeIf { it.isNotBlank() }
                ?: memoryReason.ifBlank { "曾在心底泛起涟漪的经典作品。" }
            if (raw.startsWith("“")) raw else "“$raw”"
        }
        memoryBookQuote.text = quote

        if (!memoryBook.coverUrl.isNullOrBlank()) {
            memoryBookCover.visibility = View.VISIBLE
            memoryCoverPlaceholder.visibility = View.GONE
            CoverImageHelper.loadCover(memoryBookCover, memoryBook.coverUrl)
        } else {
            memoryBookCover.visibility = View.GONE
            memoryCoverPlaceholder.visibility = View.VISIBLE
            memoryCoverPlaceholder.text = "${memoryBook.mediaType.emoji}\n${memoryBook.title.take(4)}"
        }

        ViewAnimationHelper.attachSpringTouch(memoryPanel)
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
