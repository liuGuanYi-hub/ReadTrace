package com.example.readtrace

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.readtrace.data.BookDatabaseHelper
import com.example.readtrace.model.Book
import com.example.readtrace.model.BookMindprint
import com.example.readtrace.util.CoverImageHelper
import com.example.readtrace.util.ViewAnimationHelper
import com.example.readtrace.widget.MindprintConstellationView
import java.util.Locale

class MindprintConstellationActivity : AppCompatActivity() {

    private lateinit var databaseHelper: BookDatabaseHelper
    private lateinit var constellationCanvas: MindprintConstellationView
    private lateinit var constellationDetailCard: View
    private var currentSelectedBook: Book? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_mindprint_constellation)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.constellationRoot)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        databaseHelper = BookDatabaseHelper(this)
        constellationCanvas = findViewById(R.id.constellationCanvas)
        constellationDetailCard = findViewById(R.id.constellationDetailCard)

        val backBtn = findViewById<TextView>(R.id.constellationBackBtn)
        backBtn.setOnClickListener { finish() }
        ViewAnimationHelper.attachSpringTouch(backBtn)

        val closeCardBtn = findViewById<TextView>(R.id.starCardCloseBtn)
        closeCardBtn.setOnClickListener {
            constellationDetailCard.visibility = View.GONE
        }
        ViewAnimationHelper.attachSpringTouch(closeCardBtn)

        val goDetailBtn = findViewById<TextView>(R.id.starGoDetailBtn)
        goDetailBtn.setOnClickListener {
            currentSelectedBook?.let {
                startActivity(BookDetailActivity.createIntent(this, it.id))
            }
        }
        ViewAnimationHelper.attachSpringTouch(goDetailBtn)

        constellationCanvas.onStarClickListener = { book, mindprint ->
            com.example.readtrace.util.HapticFeedbackEngine.celestialResonancePulse(this)
            com.example.readtrace.util.SpatialAudioEngine.playCelestialTone()
            showStarDetailCard(book, mindprint)
        }

        configureFilterChips()
        loadConstellationData()
    }

    private fun configureFilterChips() {
        val filterContainer = findViewById<android.widget.LinearLayout>(R.id.constellationFilterContainer) ?: return
        filterContainer.removeAllViews()

        data class FilterOption(val label: String, val filter: com.example.readtrace.widget.ConstellationFilter)

        val options = listOf(
            FilterOption("✦ 全星系", com.example.readtrace.widget.ConstellationFilter.ALL),
            FilterOption("🌌 跨媒介共鸣", com.example.readtrace.widget.ConstellationFilter.CrossMediaResonance),
            FilterOption("📖 纸墨书籍", com.example.readtrace.widget.ConstellationFilter.ByMedia(com.example.readtrace.model.MediaType.BOOK)),
            FilterOption("🌸 动漫番剧", com.example.readtrace.widget.ConstellationFilter.ByMedia(com.example.readtrace.model.MediaType.ANIME)),
            FilterOption("🎬 光影影视", com.example.readtrace.widget.ConstellationFilter.ByMedia(com.example.readtrace.model.MediaType.MOVIE)),
            FilterOption("🎮 互动游戏", com.example.readtrace.widget.ConstellationFilter.ByMedia(com.example.readtrace.model.MediaType.GAME)),
            FilterOption("🎙️ 沉浸播客", com.example.readtrace.widget.ConstellationFilter.ByMedia(com.example.readtrace.model.MediaType.PODCAST)),
            FilterOption("🇨🇳 华语经典", com.example.readtrace.widget.ConstellationFilter.ByRegion("华语")),
            FilterOption("🇯🇵 日本文学", com.example.readtrace.widget.ConstellationFilter.ByRegion("日本")),
            FilterOption("🌎 拉美文学", com.example.readtrace.widget.ConstellationFilter.ByRegion("拉美")),
            FilterOption("🇷🇺 俄苏文学", com.example.readtrace.widget.ConstellationFilter.ByRegion("俄")),
            FilterOption("🏛️ 欧美名著", com.example.readtrace.widget.ConstellationFilter.ByRegion("欧美")),
        )

        val chipViews = mutableListOf<TextView>()

        options.forEachIndexed { index, opt ->
            val chip = TextView(this).apply {
                text = opt.label
                textSize = 12.5f
                setPadding(dpToPx(12), dpToPx(6), dpToPx(12), dpToPx(6))
                val lp = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    marginEnd = dpToPx(8)
                }
                layoutParams = lp
                setBackgroundResource(if (index == 0) R.drawable.bg_status_chip_selected else R.drawable.bg_status_chip)
                setTextColor(
                    androidx.core.content.ContextCompat.getColor(
                        context,
                        if (index == 0) R.color.white else R.color.readtrace_ink,
                    ),
                )
            }

            chip.setOnClickListener {
                chipViews.forEachIndexed { i, v ->
                    val isSelected = (v == chip)
                    v.setBackgroundResource(if (isSelected) R.drawable.bg_status_chip_selected else R.drawable.bg_status_chip)
                    v.setTextColor(
                        androidx.core.content.ContextCompat.getColor(
                            this@MindprintConstellationActivity,
                            if (isSelected) R.color.white else R.color.readtrace_ink,
                        ),
                    )
                }
                constellationCanvas.setFilter(opt.filter)
            }
            com.example.readtrace.util.ViewAnimationHelper.attachSpringTouch(chip)
            chipViews.add(chip)
            filterContainer.addView(chip)
        }
    }

    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density + 0.5f).toInt()

    private fun loadConstellationData() {
        val books = databaseHelper.getBooks()
        val mindprints = databaseHelper.getAllMindprints()
        constellationCanvas.setBooksData(books, mindprints)
        findViewById<TextView>(R.id.constellationSubTitle).text =
            "共汇聚 ${books.size} 颗心智星辰 · 拖拽漫游星空"
    }

    private fun showStarDetailCard(book: Book, mindprint: BookMindprint) {
        currentSelectedBook = book
        constellationDetailCard.visibility = View.VISIBLE
        constellationDetailCard.startAnimation(AnimationUtils.loadAnimation(this, R.anim.card_enter_delayed))

        val coverImg = findViewById<ImageView>(R.id.starCoverImg)
        CoverImageHelper.loadCover(coverImg, book.coverUrl)

        findViewById<TextView>(R.id.starBookTitle).text = "${book.mediaType.emoji} 《${book.title}》"
        findViewById<TextView>(R.id.starBookAuthor).text = "${book.author ?: getString(R.string.unknown_author)} · ${book.category ?: "典藏"}"

        val mpSummary = String.format(
            Locale.getDefault(),
            "🧠 思想 %.1f · 🖋️ 文笔 %.1f · ❤️ 情感 %.1f",
            mindprint.depthScore,
            mindprint.artistryScore,
            mindprint.emotionScore,
        )
        findViewById<TextView>(R.id.starMindprintSummary).text = mpSummary

        val crossMediaContainer = findViewById<View>(R.id.starCrossMediaContainer)
        val crossMediaText = findViewById<TextView>(R.id.starCrossMediaText)
        val btnJumpResonanceStar = findViewById<View>(R.id.btnJumpResonanceStar)
        val btnExportTwinPoster = findViewById<View>(R.id.btnExportTwinPoster)

        val resonancePair = constellationCanvas.getCrossMediaResonancePeer(book.id)
        if (resonancePair != null) {
            val peer = resonancePair.first
            val edge = resonancePair.second
            crossMediaContainer.visibility = View.VISIBLE
            crossMediaText.text = "✨ 跨媒介共鸣：${peer.book.mediaType.emoji}《${peer.book.title}》${edge.similarity}%\n(${edge.resonanceTrait})"
            btnJumpResonanceStar.setOnClickListener {
                constellationCanvas.focusOnBook(peer.book.id)
                showStarDetailCard(peer.book, peer.mindprint)
            }
            com.example.readtrace.util.ViewAnimationHelper.attachSpringTouch(btnJumpResonanceStar)

            btnExportTwinPoster.setOnClickListener {
                val intent = ResonancePosterActivity.createIntent(
                    context = this,
                    bookAId = book.id,
                    bookBId = peer.book.id,
                    similarity = edge.similarity,
                    resonanceTrait = edge.resonanceTrait,
                )
                startActivity(intent)
            }
            com.example.readtrace.util.ViewAnimationHelper.attachSpringTouch(btnExportTwinPoster)
        } else {
            crossMediaContainer.visibility = View.GONE
        }
    }

    override fun onDestroy() {
        databaseHelper.close()
        super.onDestroy()
    }

    companion object {
        fun createIntent(context: Context): Intent {
            return Intent(context, MindprintConstellationActivity::class.java)
        }
    }
}
