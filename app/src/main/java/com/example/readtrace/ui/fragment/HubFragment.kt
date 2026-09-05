package com.example.readtrace.ui.fragment

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.doOnLayout
import androidx.fragment.app.Fragment
import com.example.readtrace.BackupActivity
import com.example.readtrace.BookDetailActivity
import com.example.readtrace.CulturalPassportActivity
import com.example.readtrace.GameCartridgePosterActivity
import com.example.readtrace.MediaTimelineScrollActivity
import com.example.readtrace.MindprintTopologyActivity
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
import com.example.readtrace.util.HapticFeedbackEngine
import com.example.readtrace.util.ThemeHelper
import com.example.readtrace.util.ViewAnimationHelper
import java.text.DecimalFormat

class HubFragment : Fragment() {

    private lateinit var databaseHelper: BookDatabaseHelper

    // Header
    private lateinit var auroraBackgroundView: com.example.readtrace.widget.AuroraFluidBackgroundView
    private lateinit var homeTitle: TextView
    private lateinit var headerPanel: View
    private lateinit var firstScreenStage: View
    private lateinit var hubScroll: android.widget.ScrollView
    private lateinit var arcCountBook: TextView
    private lateinit var arcCountAnime: TextView
    private lateinit var arcCountMovie: TextView
    private lateinit var arcCountGame: TextView
    private lateinit var arcCountMusic: TextView
    private lateinit var favShowcaseSection: View
    private lateinit var favStripContainer: android.widget.LinearLayout
    private lateinit var btnFavSeeAll: View
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

    // 💎 晶体工坊微胶囊（工坊功能入口）
    private lateinit var capsulePersonalizedDiscovery: View
    private lateinit var capsuleStandByClock: View
    private lateinit var capsuleVinylPlayer: View
    private lateinit var capsuleExLibris: View
    private lateinit var capsuleMediaTimeline: View
    private lateinit var capsuleMindprintTopology: View

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

    // 富内容 JSON 本地导入入口：与 CSV 导入并列，用于换机/回补角色谱与大纲
    private val selectRichJsonLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            importRichContentFromUri(uri)
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
                view.findViewById(R.id.memoryFlashbackRibbon),
                view.findViewById(R.id.insightPanel),
                view.findViewById(R.id.memoryPanel),
            ),
        )

        // 🗂️ P35 第一页「清爽记录台」：记录面板呈正方形（宽=高）并在首屏内垂直水平居中；
        // 跑马灯/策展主位等自然下沉到第二页及以后。
        // 用 doOnLayout 代替 post：Fragment 视图 post 时可能尚未完成首次 layout，
        // 高度读到 0 会让舞台塌陷，第二页内容全部涌上首屏
        hubScroll = view.findViewById(R.id.hubScroll)
        firstScreenStage.doOnLayout {
            // 舞台高度 = ScrollView 内容区全高（只扣顶部 padding）：
            // 舞台底边贴住屏幕底，滚动位置 0 时第二页内容（跑马灯/Hero 等）完全在屏幕外
            firstScreenStage.minimumHeight = hubScroll.height - hubScroll.paddingTop
            headerPanel.minimumHeight = headerPanel.width
        }

        // 🗂️ 记录台进场：轻微缩放 + 淡入（杂志封面翻开感）
        headerPanel.alpha = 0f
        headerPanel.scaleX = 0.96f
        headerPanel.scaleY = 0.96f
        headerPanel.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setStartDelay(80L)
            .setDuration(420L)
            .setInterpolator(android.view.animation.DecelerateInterpolator(1.6f))
            .start()
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
        refreshMemoryFlashback()
    }

    override fun onPause() {
        auroraBackgroundView.stopAnimation()
        super.onPause()
    }

    /**
     * 🕯️ P12 那年今日 · 时光回溯：检索历史同日完读/开读记忆并唤醒羊皮纸便签
     */
    private fun refreshMemoryFlashback() {
        val ribbon = view?.findViewById<View>(R.id.memoryFlashbackRibbon) ?: return
        val text = view?.findViewById<com.example.readtrace.widget.DropCapTextView>(R.id.memoryFlashbackText) ?: return
        val memories = com.example.readtrace.util.MemoryFlashbackEngine
            .findFlashbacks(databaseHelper.getCachedBooks())
        if (memories.isEmpty()) {
            ribbon.visibility = View.GONE
            return
        }
        text.setEditorialText(com.example.readtrace.util.MemoryFlashbackEngine.formatRibbonText(memories.first()))
        ribbon.visibility = View.VISIBLE
        ribbon.setOnClickListener {
            com.example.readtrace.util.HapticFeedbackEngine.pageTurnRustle(requireContext())
            startActivity(
                BookDetailActivity.createIntent(requireContext(), memories.first().book.id),
            )
        }
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

        // 🗂️ P35 第一页「清爽记录台」舞台：headerPanel 的居中容器
        firstScreenStage = view.findViewById(R.id.firstScreenStage)
        headerPanel = view.findViewById(R.id.headerPanel)
        arcCountBook = view.findViewById(R.id.arcCountBook)
        arcCountAnime = view.findViewById(R.id.arcCountAnime)
        arcCountMovie = view.findViewById(R.id.arcCountMovie)
        arcCountGame = view.findViewById(R.id.arcCountGame)
        arcCountMusic = view.findViewById(R.id.arcCountMusic)
        favShowcaseSection = view.findViewById(R.id.favShowcaseSection)
        favStripContainer = view.findViewById(R.id.favStripContainer)
        btnFavSeeAll = view.findViewById(R.id.btnFavSeeAll)

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
        heroBtnRead = view.findViewById(R.id.heroBtnRead)
        heroBtnDetail = view.findViewById(R.id.heroBtnDetail)

        // 💎 晶体工坊微胶囊
        capsulePersonalizedDiscovery = view.findViewById(R.id.capsulePersonalizedDiscovery)
        capsuleStandByClock = view.findViewById(R.id.capsuleStandByClock)
        capsuleVinylPlayer = view.findViewById(R.id.capsuleVinylPlayer)
        capsuleExLibris = view.findViewById(R.id.capsuleExLibris)
        capsuleMediaTimeline = view.findViewById(R.id.capsuleMediaTimeline)
        capsuleMindprintTopology = view.findViewById(R.id.capsuleMindprintTopology)

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

        // P11 极简心流：主页「+」直弹 3 秒极速速记半屏 Sheet（高级录入仍可在 Sheet 内进入）
        addBtn.setOnClickListener {
            // P35 调整：不再弹「⚡ 极速速记」半屏 Sheet，直接进入完整添加页
            startActivity(Intent(requireContext(), com.example.readtrace.AddBookActivity::class.java))
        }
        importPresetBtn.setOnClickListener { showImportCsvDialog() }
        trashBtn.setOnClickListener { startActivity(TrashActivity.createIntent(requireContext())) }
        backupBtn.setOnClickListener { startActivity(Intent(requireContext(), BackupActivity::class.java)) }

        // 💖 P28→P35：第二页「我的最爱」区块「全部」入口
        btnFavSeeAll.setOnClickListener {
            startActivity(com.example.readtrace.CuratorFavoritesActivity.createIntent(requireContext()))
        }
        ViewAnimationHelper.attachSpringTouch(btnFavSeeAll)

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
        // 详情页进出统一使用系统默认左右平滑切换（已移除共享元素转场）
        val openHeroDetail = {
            currentHeroBook?.let { book ->
                startActivity(BookDetailActivity.createIntent(requireContext(), book.id))
            }
        }
        heroBtnDetail.setOnClickListener { openHeroDetail() }
        heroCuratorialCard.setOnClickListener { openHeroDetail() }

        // 📜 羊皮纸便签轮播交互
        btnRefreshParchmentQuote.setOnClickListener {
            renderParchmentQuote(excludeQuote = currentParchmentQuoteText)
            ViewAnimationHelper.playCardBounce(parchmentQuoteRibbon)
        }
        parchmentQuoteRibbon.setOnClickListener {
            renderParchmentQuote(excludeQuote = currentParchmentQuoteText)
            ViewAnimationHelper.playCardBounce(parchmentQuoteRibbon)
        }

        // 💎 晶体工坊微胶囊交互
        capsulePersonalizedDiscovery.setOnClickListener {
            HapticFeedbackEngine.lightClick(requireContext())
            com.example.readtrace.ui.PersonalizedDiscoveryBottomSheet.show(requireActivity()) {
                refreshDashboard()
            }
        }
        capsuleStandByClock.setOnClickListener {
            startActivity(Intent(requireContext(), com.example.readtrace.StandByZenDeskActivity::class.java))
        }
        capsuleVinylPlayer.setOnClickListener {
            startActivity(Intent(requireContext(), VinylCassettePlayerActivity::class.java))
        }
        capsuleExLibris.setOnClickListener {
            startActivity(CulturalPassportActivity.createIntent(requireContext(), MediaType.BOOK))
        }
        capsuleMediaTimeline.setOnClickListener {
            startActivity(MediaTimelineScrollActivity.createIntent(requireContext()))
        }
        capsuleMindprintTopology.setOnClickListener {
            startActivity(Intent(requireContext(), MindprintTopologyActivity::class.java))
        }

        // 弹簧阻尼微触觉反馈
        listOf(
            addBtn, importPresetBtn, backupBtn, trashBtn, themeToggleButton,
            heroBtnRead, heroBtnDetail,
            capsulePersonalizedDiscovery, capsuleStandByClock, capsuleVinylPlayer,
            capsuleExLibris, capsuleMediaTimeline, capsuleMindprintTopology,
            btnRefreshParchmentQuote,
        ).forEach {
            ViewAnimationHelper.attachSpringTouch(it)
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
        statAverageValue.text = if (rated.isEmpty()) "均分 ★ -" else "均分 ★ ${RATING_FORMAT.format(rated.average())}"

        // 🗂️ P35 记录台五媒介藏品计数（正方形腹地的精神领土一览）
        if (::arcCountBook.isInitialized) {
            val byMedia = allBooks.groupBy { it.mediaType }
            arcCountBook.text = "${byMedia[MediaType.BOOK]?.size ?: 0}"
            arcCountAnime.text = "${byMedia[MediaType.ANIME]?.size ?: 0}"
            arcCountMovie.text = "${byMedia[MediaType.MOVIE]?.size ?: 0}"
            arcCountGame.text = "${byMedia[MediaType.GAME]?.size ?: 0}"
            arcCountMusic.text = "${byMedia[MediaType.MUSIC]?.size ?: 0}"
        }


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

        renderMemoryCard()
        renderFavoriteStrip()
    }

    /** 💖 P28→P35：主页第二页「我的最爱 · 心选展厅」横滑带（无收藏时整块隐藏） */
    private fun renderFavoriteStrip() {
        if (!::favStripContainer.isInitialized) return
        val items = MediaType.values().flatMap { databaseHelper.getFavoritesByMediaType(it) }
            .sortedWith(compareBy({ it.mediaType.ordinal }, { it.rankOrder }, { it.id }))
        if (items.isEmpty()) {
            favShowcaseSection.visibility = View.GONE
            return
        }
        favShowcaseSection.visibility = View.VISIBLE
        favStripContainer.removeAllViews()
        val inflater = layoutInflater
        items.forEachIndexed { index, item ->
            val card = inflater.inflate(R.layout.item_hub_favorite_card, favStripContainer, false)
            val cover = card.findViewById<ImageView>(R.id.favCardCover)
            val placeholder = card.findViewById<TextView>(R.id.favCardPlaceholder)
            val rank = card.findViewById<TextView>(R.id.favCardRank)
            val title = card.findViewById<TextView>(R.id.favCardTitle)
            if (item.book.coverUrl.isNullOrBlank()) {
                // 无封面：占位卡显示媒介 emoji + 标题前四字（与 Hero 占位同款降级）
                placeholder.text = "${item.book.mediaType.emoji}\n${item.book.title.take(4)}"
                com.example.readtrace.util.CoverImageHelper.loadCover(cover, null, placeholder)
            } else {
                com.example.readtrace.util.CoverImageHelper.loadCover(cover, item.book.coverUrl)
            }
            title.text = item.book.title
            rank.text = "NO.${index + 1}"
            card.setOnClickListener {
                startActivity(BookDetailActivity.createIntent(requireContext(), item.book.id))
            }
            ViewAnimationHelper.attachSpringTouch(card)
            favStripContainer.addView(card)
        }
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
            heroBookRating.text = if (rating != null && rating > 0) "★ ${RATING_FORMAT.format(rating)} · 精神典藏" else "✦ 重点策展推荐"

            // P35：长随想（heroBookQuote）已整段移除——首屏只保留封面/标题/评分/操作，长文本留在详情页
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
        val options = listOf(
            "📚  导入预设名著经典 (54 本)",
            "🌸  导入预设追番史 (71 部)",
            "🎬  导入预设经典电影 (11 部)",
            "🎮  导入预设游戏神作 (69 款)",
            "🌟  一键全量合入 (205 部神作)",
            "📂  选择本地 CSV 文件...",
            "🎨  选择本地富内容 JSON 文件...",
        )
        val actions: List<() -> Unit> = listOf(
            { importAssetCsv("preset_books.csv", MediaType.BOOK, "名著书单") },
            { importAssetCsv("preset_anime.csv", MediaType.ANIME, "追番清单") },
            { importAssetCsv("preset_movies.csv", MediaType.MOVIE, "电影清单") },
            { importAssetCsv("preset_games.csv", MediaType.GAME, "游戏清单") },
            { importAllPresetCsvs() },
            { selectCsvLauncher.launch(arrayOf("text/*", "text/comma-separated-values", "application/csv")) },
            { selectRichJsonLauncher.launch(arrayOf("application/json", "text/*", "application/octet-stream")) },
        )

        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        // 与 ElegantFormDialog 同一设计语言：深色玻璃圆角容器 + hairline 描边
        val container = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(dp(22), dp(18), dp(22), dp(20))
            setBackgroundResource(R.drawable.bg_elegant_dialog)
        }
        val titleView = TextView(requireContext()).apply {
            text = "📥 批量导入精神清单"
            textSize = 16.5f
            setTextColor(requireContext().getColor(R.color.readtrace_ink))
            letterSpacing = 0.02f
        }
        container.addView(titleView)
        container.addView(TextView(requireContext()).apply {
            text = "选一份预设书单，或从本地 CSV 带回你的记录"
            textSize = 11.5f
            setTextColor(requireContext().getColor(R.color.readtrace_muted))
            setPadding(0, dp(4), 0, dp(6))
        })

        var importDialog: android.app.Dialog? = null
        options.forEachIndexed { index, label ->
            val row = TextView(requireContext()).apply {
                text = label
                textSize = 14f
                setTextColor(requireContext().getColor(R.color.readtrace_ink))
                setBackgroundResource(R.drawable.bg_form_input)
                setPadding(dp(14), dp(12), dp(14), dp(12))
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = if (index == 0) 0 else dp(8) }
                alpha = 0f
                translationY = dp(10).toFloat()
                animate().alpha(1f).translationY(0f)
                    .setStartDelay(80L + index * 45L)
                    .setDuration(280L)
                    .start()
            }
            ViewAnimationHelper.attachSpringTouch(row)
            row.setOnClickListener {
                importDialog?.dismiss()
                actions[index].invoke()
            }
            container.addView(row)
        }

        val btnCancel = TextView(requireContext()).apply {
            text = "取 消"
            gravity = android.view.Gravity.CENTER
            textSize = 14f
            isAllCaps = false
            setTextColor(requireContext().getColor(R.color.chip_idle_text))
            setBackgroundResource(R.drawable.bg_chip_picker_idle)
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                dp(44),
            ).apply { topMargin = dp(14) }
        }
        ViewAnimationHelper.attachSpringTouch(btnCancel)
        btnCancel.setOnClickListener { importDialog?.dismiss() }
        container.addView(btnCancel)

        importDialog = android.app.Dialog(requireContext()).apply {
            requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
            setContentView(container)
            window?.apply {
                setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
                setLayout(
                    (resources.displayMetrics.widthPixels * 0.88f).toInt(),
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                )
                setGravity(android.view.Gravity.CENTER)
            }
        }
        // 进场：容器上浮渐入
        container.translationY = dp(24).toFloat()
        container.animate().translationY(0f).setDuration(300L).start()
        importDialog.show()
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

    /**
     * 从本地文件导入富内容 JSON（角色谱/语录/章节大纲），按标题匹配藏库作品；
     * 缺失的作品按文件名推断媒介自动建最小骨架，实现「导入一次即作品+富内容」。
     * 文件可达数百 KB 且含批量事务写入，放后台线程避免主线程卡顿。
     */
    private fun importRichContentFromUri(uri: Uri) {
        Thread {
            val result = runCatching {
                val fileName = requireContext().contentResolver.query(uri, null, null, null, null)?.use { c ->
                    val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
                } ?: uri.lastPathSegment
                requireContext().contentResolver.openInputStream(uri)
                    ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                    ?.let { text -> databaseHelper.importRichContentJson(text, fileName) }
                    ?: -1
            }.getOrElse { -1 }
            requireActivity().runOnUiThread {
                when {
                    result > 0 -> {
                        Toast.makeText(
                            requireContext(),
                            "成功导入 $result 部作品及其富内容（角色谱/语录/大纲）！",
                            Toast.LENGTH_SHORT,
                        ).show()
                        refreshDashboard()
                    }
                    result == 0 -> Toast.makeText(
                        requireContext(),
                        "JSON 中没有有效作品条目，请确认选择的是 rich_content_*.json 富内容文件",
                        Toast.LENGTH_LONG,
                    ).show()
                    else -> Toast.makeText(
                        requireContext(),
                        "导入失败：请确认选择的是 rich_content_*.json 富内容文件",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }.start()
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

    override fun onDestroyView() {
        // 注意：BookDatabaseHelper 为全局单例，此处不可 close()，否则其他页面会拿到已关闭的连接
        super.onDestroyView()
    }

    companion object {
        private val RATING_FORMAT = DecimalFormat("0.#")
    }
}
