package com.example.readtrace.ui.fragment

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.readtrace.BookDetailActivity
import com.example.readtrace.MindprintConstellationActivity
import com.example.readtrace.R
import com.example.readtrace.data.BookDatabaseHelper
import com.example.readtrace.model.Book
import com.example.readtrace.model.BookMindprint
import com.example.readtrace.model.MediaType
import com.example.readtrace.util.CoverImageHelper
import com.example.readtrace.util.ViewAnimationHelper
import com.example.readtrace.widget.ConstellationFilter
import com.example.readtrace.widget.MindprintConstellationView

class ConstellationFragment : Fragment() {

    private lateinit var databaseHelper: BookDatabaseHelper
    private lateinit var constellationCanvas: MindprintConstellationView
    private lateinit var constellationSubtitle: TextView
    private lateinit var btnConstellationFullscreen: View

    private lateinit var filterAll: TextView
    private lateinit var filterBook: TextView
    private lateinit var filterAnime: TextView
    private lateinit var filterMovie: TextView
    private lateinit var filterGame: TextView
    private lateinit var filterResonance: TextView

    private lateinit var constellationNodeCard: View
    private lateinit var nodeCoverImage: ImageView
    private lateinit var nodeTitle: TextView
    private lateinit var nodeMeta: TextView
    private lateinit var btnNodeDetail: TextView

    private var currentFilter: ConstellationFilter = ConstellationFilter.ALL
    private var selectedBook: Book? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.fragment_constellation, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        databaseHelper = BookDatabaseHelper.getInstance(requireContext())

        initViews(view)
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        loadConstellationData()
    }

    private fun initViews(view: View) {
        constellationCanvas = view.findViewById(R.id.constellationCanvas)
        constellationSubtitle = view.findViewById(R.id.constellationSubtitle)
        btnConstellationFullscreen = view.findViewById(R.id.btnConstellationFullscreen)

        filterAll = view.findViewById(R.id.filterAll)
        filterBook = view.findViewById(R.id.filterBook)
        filterAnime = view.findViewById(R.id.filterAnime)
        filterMovie = view.findViewById(R.id.filterMovie)
        filterGame = view.findViewById(R.id.filterGame)
        filterResonance = view.findViewById(R.id.filterResonance)

        constellationNodeCard = view.findViewById(R.id.constellationNodeCard)
        nodeCoverImage = view.findViewById(R.id.nodeCoverImage)
        nodeTitle = view.findViewById(R.id.nodeTitle)
        nodeMeta = view.findViewById(R.id.nodeMeta)
        btnNodeDetail = view.findViewById(R.id.btnNodeDetail)

        constellationCanvas.onStarClickListener = { book, mindprint ->
            showNodeCard(book, mindprint)
        }
    }

    private fun setupListeners() {
        btnConstellationFullscreen.setOnClickListener {
            startActivity(MindprintConstellationActivity.createIntent(requireContext()))
        }

        filterAll.setOnClickListener { selectFilter(ConstellationFilter.ALL) }
        filterBook.setOnClickListener { selectFilter(ConstellationFilter.ByMedia(MediaType.BOOK)) }
        filterAnime.setOnClickListener { selectFilter(ConstellationFilter.ByMedia(MediaType.ANIME)) }
        filterMovie.setOnClickListener { selectFilter(ConstellationFilter.ByMedia(MediaType.MOVIE)) }
        filterGame.setOnClickListener { selectFilter(ConstellationFilter.ByMedia(MediaType.GAME)) }
        filterResonance.setOnClickListener { selectFilter(ConstellationFilter.CrossMediaResonance) }

        btnNodeDetail.setOnClickListener {
            selectedBook?.let { book ->
                startActivity(BookDetailActivity.createIntent(requireContext(), book.id))
            }
        }

        listOfNotNull<View>(
            btnConstellationFullscreen, btnNodeDetail,
            filterAll, filterBook, filterAnime, filterMovie, filterGame, filterResonance,
        ).forEach { ViewAnimationHelper.attachSpringTouch(it) }
    }

    private fun selectFilter(filter: ConstellationFilter) {
        currentFilter = filter
        updateFilterChips()
        constellationCanvas.setFilter(filter)
    }

    private fun updateFilterChips() {
        val ctx = context ?: return
        val chips = listOf(
            filterAll to (currentFilter == ConstellationFilter.ALL),
            filterBook to (currentFilter is ConstellationFilter.ByMedia && (currentFilter as ConstellationFilter.ByMedia).mediaType == MediaType.BOOK),
            filterAnime to (currentFilter is ConstellationFilter.ByMedia && (currentFilter as ConstellationFilter.ByMedia).mediaType == MediaType.ANIME),
            filterMovie to (currentFilter is ConstellationFilter.ByMedia && (currentFilter as ConstellationFilter.ByMedia).mediaType == MediaType.MOVIE),
            filterGame to (currentFilter is ConstellationFilter.ByMedia && (currentFilter as ConstellationFilter.ByMedia).mediaType == MediaType.GAME),
            filterResonance to (currentFilter == ConstellationFilter.CrossMediaResonance),
        )
        chips.forEach { (chip, isSelected) ->
            // 未选中态用日夜自适应胶囊与墨色文字，避免明亮模式下对比度过低
            chip.setBackgroundResource(if (isSelected) R.drawable.bg_status_chip_selected else R.drawable.bg_status_chip)
            chip.setTextColor(
                if (isSelected) Color.WHITE else ContextCompat.getColor(ctx, R.color.readtrace_ink),
            )
        }
    }

    private fun loadConstellationData() {
        val books = databaseHelper.getCachedBooks()
        val mindprints = databaseHelper.getAllMindprints()
        constellationSubtitle.text = "共聚联 ${books.size} 颗精神星辰 · 极光脉冲交织"
        constellationCanvas.setBooksData(books, mindprints)
    }

    private fun showNodeCard(book: Book, mindprint: BookMindprint) {
        selectedBook = book
        nodeTitle.text = "${book.mediaType.emoji} 《${book.title}》"
        nodeMeta.text = "${book.author ?: "未知作者"} · 思想 ${mindprint.depthScore} · 情感 ${mindprint.emotionScore}"
        CoverImageHelper.loadCover(nodeCoverImage, book.coverUrl)

        constellationNodeCard.visibility = View.VISIBLE
        constellationNodeCard.alpha = 0f
        constellationNodeCard.animate().alpha(1f).setDuration(180).start()
    }

    override fun onDestroyView() {
        super.onDestroyView()
    }
}
