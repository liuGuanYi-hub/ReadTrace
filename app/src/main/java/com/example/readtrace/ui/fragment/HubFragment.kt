package com.example.readtrace.ui.fragment

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import com.example.readtrace.AddBookActivity
import com.example.readtrace.BackupActivity
import com.example.readtrace.BookDetailActivity
import com.example.readtrace.CulturalPassportActivity
import com.example.readtrace.DataMigrationActivity
import com.example.readtrace.Gallery3DActivity
import com.example.readtrace.MediaTimelineScrollActivity
import com.example.readtrace.MindprintTopologyActivity
import com.example.readtrace.R
import com.example.readtrace.VinylCassettePlayerActivity
import com.example.readtrace.data.BookDatabaseHelper
import com.example.readtrace.model.Book
import com.example.readtrace.model.BookStatus
import com.example.readtrace.model.MediaType
import com.example.readtrace.util.CoverImageHelper
import com.example.readtrace.util.GyroscopeParallaxHelper
import com.example.readtrace.util.HapticFeedbackEngine
import com.example.readtrace.util.ThemeHelper
import com.example.readtrace.util.ViewAnimationHelper
import java.text.DecimalFormat

/**
 * 🌅 精神晨报主页控制器 (Hub Gazette Fragment)
 * 极致一屏半极简架构：策展人晨报 + 在读灵感聚焦 + 单行 44dp 晶体工坊胶囊 + 精神微脉冲。
 */
class HubFragment : Fragment() {

    private lateinit var databaseHelper: BookDatabaseHelper

    // Header & 背景
    private lateinit var auroraBackgroundView: com.example.readtrace.widget.AuroraFluidBackgroundView
    private lateinit var homeTitle: TextView
    private lateinit var homeSubtitle: TextView
    private lateinit var themeToggleButton: TextView
    private lateinit var addBtn: View
    private lateinit var importPresetBtn: View
    private lateinit var backupBtn: View

    // 🌟 Hero 在读/灵感聚焦
    private lateinit var heroCuratorialCard: CardView
    private lateinit var heroSpecularOverlay: com.example.readtrace.widget.HolographicSpecularOverlayView
    private lateinit var heroCuratorialBadge: com.example.readtrace.widget.EditorialBadgeView
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

    // 💎 5 大 44dp 晶体工坊胶囊
    private lateinit var capsuleVinylPlayer: View
    private lateinit var capsuleExLibris: View
    private lateinit var capsuleMediaTimeline: View
    private lateinit var capsule3DGallery: View
    private lateinit var capsuleMindprintTopology: View

    // 📜 羊皮纸便签
    private lateinit var parchmentQuoteRibbon: View
    private lateinit var btnRefreshParchmentQuote: View
    private lateinit var parchmentQuoteText: com.example.readtrace.widget.DropCapTextView
    private lateinit var parchmentQuoteSource: TextView

    // 📊 5 维精神能量统计
    private lateinit var statTotalValue: TextView
    private lateinit var statReadingValue: TextView
    private lateinit var statFinishedValue: TextView
    private lateinit var statWishlistValue: TextView
    private lateinit var statAverageValue: TextView

    private lateinit var gyroscopeHelper: GyroscopeParallaxHelper

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
    }

    private fun initViews(view: View) {
        auroraBackgroundView = view.findViewById(R.id.auroraBackgroundView)
        homeTitle = view.findViewById(R.id.homeTitle)
        homeSubtitle = view.findViewById(R.id.homeSubtitle)
        themeToggleButton = view.findViewById(R.id.themeToggleButton)
        addBtn = view.findViewById(R.id.addBtn)
        importPresetBtn = view.findViewById(R.id.importPresetBtn)
        backupBtn = view.findViewById(R.id.backupBtn)

        // Hero
        heroCuratorialCard = view.findViewById(R.id.heroCuratorialCard)
        heroSpecularOverlay = view.findViewById(R.id.heroSpecularOverlay)
        heroCuratorialBadge = view.findViewById(R.id.heroCuratorialBadge)
        heroBadgeMedia = view.findViewById(R.id.heroBadgeMedia)
        heroBookCover = view.findViewById(R.id.heroBookCover)
        heroCoverPlaceholder = view.findViewById(R.id.heroCoverPlaceholder)
        heroBookTitle = view.findViewById(R.id.heroBookTitle)
        heroBookAuthor = view.findViewById(R.id.heroBookAuthor)
        heroBookRating = view.findViewById(R.id.heroBookRating)
        heroBookQuote = view.findViewById(R.id.heroBookQuote)
        heroBtnRead = view.findViewById(R.id.heroBtnRead)
        heroBtnDetail = view.findViewById(R.id.heroBtnDetail)

        // Capsules
        capsuleVinylPlayer = view.findViewById(R.id.capsuleVinylPlayer)
        capsuleExLibris = view.findViewById(R.id.capsuleExLibris)
        capsuleMediaTimeline = view.findViewById(R.id.capsuleMediaTimeline)
        capsule3DGallery = view.findViewById(R.id.capsule3DGallery)
        capsuleMindprintTopology = view.findViewById(R.id.capsuleMindprintTopology)

        // Parchment
        parchmentQuoteRibbon = view.findViewById(R.id.parchmentQuoteRibbon)
        btnRefreshParchmentQuote = view.findViewById(R.id.btnRefreshParchmentQuote)
        parchmentQuoteText = view.findViewById(R.id.parchmentQuoteText)
        parchmentQuoteSource = view.findViewById(R.id.parchmentQuoteSource)

        // Stats
        statTotalValue = view.findViewById(R.id.statTotalValue)
        statReadingValue = view.findViewById(R.id.statReadingValue)
        statFinishedValue = view.findViewById(R.id.statFinishedValue)
        statWishlistValue = view.findViewById(R.id.statWishlistValue)
        statAverageValue = view.findViewById(R.id.statAverageValue)

        // 弹簧阻尼微触觉反馈
        listOf(
            addBtn, importPresetBtn, backupBtn, themeToggleButton,
            heroBtnRead, heroBtnDetail,
            capsuleVinylPlayer, capsuleExLibris, capsuleMediaTimeline, capsule3DGallery, capsuleMindprintTopology,
            btnRefreshParchmentQuote,
        ).forEach {
            ViewAnimationHelper.attachSpringTouch(it)
        }
    }

    private fun setupListeners() {
        addBtn.setOnClickListener {
            // P11 极简心流：主页「+」直弹 3 秒极速速记半屏 Sheet（高级录入仍可在 Sheet 内进入）
            com.example.readtrace.ui.QuickLogBottomSheet.show(requireActivity())
        }

        // ⏳ P15 StandBy 禅意伴读钟
        view?.findViewById<View>(R.id.capsuleStandByClock)?.setOnClickListener {
            startActivity(Intent(requireContext(), com.example.readtrace.StandByZenDeskActivity::class.java))
        }

        importPresetBtn.setOnClickListener {
            startActivity(DataMigrationActivity.createIntent(requireContext()))
        }

        backupBtn.setOnClickListener {
            startActivity(Intent(requireContext(), BackupActivity::class.java))
        }

        themeToggleButton.setOnClickListener {
            ThemeHelper.toggleDarkMode(requireContext())
            updateThemeToggleIcon()
        }

        // Hero 操作
        heroBtnRead.setOnClickListener {
            currentHeroBook?.let { book ->
                startActivity(BookDetailActivity.createIntent(requireContext(), book.id))
            }
        }
        heroBtnDetail.setOnClickListener {
            currentHeroBook?.let { book ->
                startActivity(BookDetailActivity.createIntent(requireContext(), book.id))
            }
        }

        // 5 大先锋微胶囊跳转
        capsuleVinylPlayer.setOnClickListener {
            startActivity(Intent(requireContext(), VinylCassettePlayerActivity::class.java))
        }
        capsuleExLibris.setOnClickListener {
            startActivity(CulturalPassportActivity.createIntent(requireContext(), MediaType.BOOK))
        }
        capsuleMediaTimeline.setOnClickListener {
            startActivity(MediaTimelineScrollActivity.createIntent(requireContext()))
        }
        capsule3DGallery.setOnClickListener {
            startActivity(Gallery3DActivity.createIntent(requireContext()))
        }
        capsuleMindprintTopology.setOnClickListener {
            startActivity(Intent(requireContext(), MindprintTopologyActivity::class.java))
        }

        // 便签换一句
        btnRefreshParchmentQuote.setOnClickListener {
            HapticFeedbackEngine.lightClick(requireContext())
            refreshDailyQuote()
        }
    }

    private fun setupGyroscopeParallax() {
        gyroscopeHelper = GyroscopeParallaxHelper(requireContext())
        gyroscopeHelper.bind3DParallax(heroCuratorialCard, maxRotation = 6f, maxTranslation = 8f)
        gyroscopeHelper.bind3DParallax(heroBookCover, maxRotation = 10f, maxTranslation = 12f)
        gyroscopeHelper.bindHolographicSpecular(heroSpecularOverlay)
        gyroscopeHelper.bind3DParallax(parchmentQuoteRibbon, maxRotation = 4f, maxTranslation = 6f)
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

    /**
     * 🕯️ P12 那年今日 · 时光回溯：检索历史同日完读/开读记忆并唤醒羊皮纸便签
     */
    private fun refreshMemoryFlashback() {
        val ribbon = view?.findViewById<View>(R.id.memoryFlashbackRibbon) ?: return
        val text = view?.findViewById<com.example.readtrace.widget.DropCapTextView>(R.id.memoryFlashbackText) ?: return
        val memories = com.example.readtrace.util.MemoryFlashbackEngine
            .findFlashbacks(databaseHelper.getBooks())
        if (memories.isEmpty()) {
            ribbon.visibility = View.GONE
            return
        }
        text.setEditorialText(com.example.readtrace.util.MemoryFlashbackEngine.formatRibbonText(memories.first()))
        ribbon.visibility = View.VISIBLE
        ribbon.setOnClickListener {
            com.example.readtrace.util.HapticFeedbackEngine.pageTurnRustle(requireContext())
            startActivity(
                com.example.readtrace.BookDetailActivity.createIntent(
                    requireContext(),
                    memories.first().book.id,
                ),
            )
        }
    }

    override fun onPause() {
        auroraBackgroundView.stopAnimation()
        super.onPause()
    }

    private fun updateThemeToggleIcon() {
        themeToggleButton.text = if (ThemeHelper.isDarkMode(requireContext())) "🌙" else "☀️"
    }

    private fun refreshDashboard() {
        Thread {
            val allBooks = databaseHelper.getBooks()
            val readingBooks = allBooks.filter { it.status == BookStatus.READING }
            val finishedBooks = allBooks.filter { it.status == BookStatus.FINISHED }
            val wishlistBooks = allBooks.filter { it.status == BookStatus.WISHLIST }

            val total = allBooks.size
            val readingCount = readingBooks.size
            val finishedCount = finishedBooks.size
            val wishlistCount = wishlistBooks.size

            val ratedBooks = allBooks.filter { it.rating != null && it.rating > 0 }
            val avgRating = if (ratedBooks.isNotEmpty()) {
                ratedBooks.mapNotNull { it.rating }.average()
            } else 0.0

            // 优先选取正在阅读的作品作为 Hero，否则选取最新作品
            val heroCandidate = readingBooks.firstOrNull() ?: allBooks.firstOrNull()

            activity?.runOnUiThread {
                if (!isAdded) return@runOnUiThread

                // 绑定统计
                statTotalValue.text = total.toString()
                statReadingValue.text = readingCount.toString()
                statFinishedValue.text = finishedCount.toString()
                statWishlistValue.text = wishlistCount.toString()
                statAverageValue.text = if (avgRating > 0) DecimalFormat("0.0").format(avgRating) else "--"

                // 绑定 Hero 聚焦
                bindHeroCard(heroCandidate)
                refreshDailyQuote()
            }
        }.start()
    }

    private fun bindHeroCard(book: Book?) {
        currentHeroBook = book
        if (book == null) {
            heroCuratorialCard.visibility = View.GONE
            return
        }

        heroCuratorialCard.visibility = View.VISIBLE
        heroCuratorialBadge.setBadgeContent(
            if (book.status == BookStatus.READING) "✦ 正在品读" else "✦ 灵感精选",
            "FOCUS",
        )
        heroBadgeMedia.text = "${book.mediaType.emoji} ${book.mediaType.displayName}"
        heroBookTitle.text = book.title
        heroBookAuthor.text = book.author ?: "未知作者"
        heroBookRating.text = book.rating?.let { "★ ${DecimalFormat("0.0").format(it)} / 10" } ?: "尚未评分"

        CoverImageHelper.loadCover(heroBookCover, book.coverUrl)
        if (book.coverUrl.isNullOrBlank()) {
            heroCoverPlaceholder.visibility = View.VISIBLE
            heroCoverPlaceholder.text = book.title.take(1)
        } else {
            heroCoverPlaceholder.visibility = View.GONE
        }

        val quote = book.shortComment?.takeIf { it.isNotBlank() }
            ?: "一本好书，是人类精神的纯粹印记。"
        heroBookQuote.text = quote
    }

    private fun refreshDailyQuote() {
        val quotes = listOf(
            "生命中真正重要的不是你遭遇了什么，而是你记住了哪些事。" to "加西亚·马尔克斯《百年孤独》",
            "万物皆有裂痕，那是光照进来的地方。" to "莱昂纳德·科恩",
            "阅读不是为了逃避现实，而是为了更清醒地活在当下。" to "精神策展手记",
            "给岁月以文明，而不是给文明以岁月。" to "刘慈欣《三体》",
            "记忆会褪色，但阅过的痕迹会构筑灵魂的形状。" to "阅痕 ReadTrace",
        )
        val selected = quotes.random()
        parchmentQuoteText.text = selected.first
        parchmentQuoteSource.text = "—— ${selected.second}"
    }
}