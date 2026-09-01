package com.example.readtrace

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.example.readtrace.data.BookDatabaseHelper
import com.example.readtrace.model.Book
import com.example.readtrace.model.MediaType
import com.example.readtrace.util.CoverImageHelper
import com.example.readtrace.util.ViewAnimationHelper
import com.example.readtrace.util.FloatingBack
import kotlin.random.Random

class CoverGalleryActivity : AppCompatActivity() {

    private lateinit var databaseHelper: BookDatabaseHelper
    private lateinit var rvCoverGallery: RecyclerView
    private lateinit var tvGalleryCounter: TextView
    private lateinit var btnGalleryOpenDetail: TextView
    private lateinit var galleryAmbientGlow: View
    private lateinit var galleryRoot: View
    private lateinit var tabGalleryAll: TextView
    private lateinit var tabGalleryBook: TextView
    private lateinit var tabGalleryAnime: TextView
    private lateinit var tabGalleryMovie: TextView
    private lateinit var tabGalleryGame: TextView
    private lateinit var tabGalleryMusic: TextView
    private lateinit var tvGalleryTitle: TextView

    private var allBooks: List<Book> = emptyList()
    private var displayedBooks: List<Book> = emptyList()
    private var selectedMediaType: MediaType? = null
    private var currentPosition: Int = 0

    private val snapHelper = PagerSnapHelper()
    private lateinit var adapter: CoverGalleryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContentView(R.layout.activity_cover_gallery)

        databaseHelper = BookDatabaseHelper.getInstance(this)

        initViews()
        applySystemBarInsets()
        setupRecyclerView()
        setupTabs()
        loadData()
    }

    private fun initViews() {
        galleryRoot = findViewById(R.id.galleryRoot)
        galleryAmbientGlow = findViewById(R.id.galleryAmbientGlow)
        tvGalleryCounter = findViewById(R.id.tvGalleryCounter)
        btnGalleryOpenDetail = findViewById(R.id.btnGalleryOpenDetail)
        rvCoverGallery = findViewById(R.id.rvCoverGallery)
        tabGalleryAll = findViewById(R.id.tabGalleryAll)
        tabGalleryBook = findViewById(R.id.tabGalleryBook)
        tabGalleryAnime = findViewById(R.id.tabGalleryAnime)
        tabGalleryMovie = findViewById(R.id.tabGalleryMovie)
        tabGalleryGame = findViewById(R.id.tabGalleryGame)
        tabGalleryMusic = findViewById(R.id.tabGalleryMusic)
        tvGalleryTitle = findViewById(R.id.tvGalleryTitle)

        FloatingBack.install(this)

        val btnRandom = findViewById<TextView>(R.id.btnGalleryRandom)
        btnRandom.setOnClickListener {
            if (displayedBooks.isNotEmpty()) {
                val next = Random.nextInt(displayedBooks.size)
                rvCoverGallery.smoothScrollToPosition(next)
            }
        }
        ViewAnimationHelper.attachSpringTouch(btnRandom)

        btnGalleryOpenDetail.setOnClickListener {
            val book = displayedBooks.getOrNull(currentPosition) ?: return@setOnClickListener
            val intent = Intent(this, BookDetailActivity::class.java).apply {
                putExtra(BookDetailActivity.EXTRA_BOOK_ID, book.id)
            }
            startActivity(intent)
        }
        ViewAnimationHelper.attachSpringTouch(btnGalleryOpenDetail)
    }

    /**
     * Android 15+/targetSdk 35+ 默认强制 edge-to-edge，状态栏会覆盖在内容上方。
     * 监听系统栏与刘海 inset，动态给 root view 补 paddingTop / paddingBottom，
     * 让标题、tab 行、底部按钮都能避开系统栏。
     */
    private fun applySystemBarInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(galleryRoot) { v, insets ->
            val sb = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            v.updatePadding(top = sb.top, bottom = sb.bottom)
            WindowInsetsCompat.CONSUMED
        }
    }

    private fun setupRecyclerView() {
        val layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        rvCoverGallery.layoutManager = layoutManager
        snapHelper.attachToRecyclerView(rvCoverGallery)

        adapter = CoverGalleryAdapter(
            onItemClick = { book ->
                val intent = Intent(this, BookDetailActivity::class.java).apply {
                    putExtra(BookDetailActivity.EXTRA_BOOK_ID, book.id)
                }
                startActivity(intent)
            }
        )
        rvCoverGallery.adapter = adapter

        rvCoverGallery.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    val centerView = snapHelper.findSnapView(layoutManager) ?: return
                    val pos = layoutManager.getPosition(centerView)
                    if (pos != RecyclerView.NO_POSITION && pos != currentPosition) {
                        currentPosition = pos
                        updateCounter()
                        updateAmbientGlow(displayedBooks.getOrNull(pos))
                    }
                }
            }
        })
    }

    private fun setupTabs() {
        val tabs = listOf(
            tabGalleryAll to null,
            tabGalleryBook to MediaType.BOOK,
            tabGalleryAnime to MediaType.ANIME,
            tabGalleryMovie to MediaType.MOVIE,
            tabGalleryGame to MediaType.GAME,
            tabGalleryMusic to MediaType.MUSIC,
        )

        tabs.forEach { (tab, mediaType) ->
            tab.setOnClickListener {
                selectedMediaType = mediaType
                tabs.forEach { (t, m) ->
                    val isSelected = m == selectedMediaType
                    t.setBackgroundResource(if (isSelected) R.drawable.bg_status_chip_selected else R.drawable.bg_pill_button)
                    t.setTextColor(if (isSelected) Color.WHITE else Color.parseColor("#E8E2D9"))
                }
                filterBooks()
            }
            ViewAnimationHelper.attachSpringTouch(tab)
        }
    }

    private fun loadData() {
        allBooks = databaseHelper.getBooks()
        filterBooks()
    }

    private fun filterBooks() {
        displayedBooks = if (selectedMediaType == null) {
            allBooks
        } else {
            allBooks.filter { it.mediaType == selectedMediaType }
        }
        adapter.submitList(displayedBooks)
        currentPosition = 0
        if (displayedBooks.isNotEmpty()) {
            rvCoverGallery.scrollToPosition(0)
            updateCounter()
            updateAmbientGlow(displayedBooks.firstOrNull())
        } else {
            tvGalleryCounter.text = "0 / 0"
        }
    }

    private fun updateCounter() {
        if (displayedBooks.isEmpty()) {
            tvGalleryCounter.text = "0 / 0"
            return
        }
        tvGalleryCounter.text = "${currentPosition + 1} / ${displayedBooks.size}"
    }

    private fun updateAmbientGlow(book: Book?) {
        if (book == null) return
        val colors = when (book.mediaType) {
            MediaType.ANIME -> intArrayOf(Color.parseColor("#3A1A28"), Color.parseColor("#12100E"))
            MediaType.BOOK -> intArrayOf(Color.parseColor("#2C2216"), Color.parseColor("#12100E"))
            MediaType.MOVIE -> intArrayOf(Color.parseColor("#14233A"), Color.parseColor("#12100E"))
            MediaType.GAME -> intArrayOf(Color.parseColor("#1A2B20"), Color.parseColor("#12100E"))
            MediaType.MUSIC -> intArrayOf(Color.parseColor("#2D1B36"), Color.parseColor("#12100E"))
        }

        val gradient = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, colors)
        galleryAmbientGlow.background = gradient
    }

    class CoverGalleryAdapter(
        private val onItemClick: (Book) -> Unit
    ) : RecyclerView.Adapter<CoverGalleryAdapter.ViewHolder>() {

        private var items: List<Book> = emptyList()

        fun submitList(newItems: List<Book>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_cover_gallery_card, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(items[position], onItemClick)
        }

        override fun getItemCount(): Int = items.size

        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val galleryCoverImage: ImageView = itemView.findViewById(R.id.galleryCoverImage)
            private val galleryCoverPlaceholder: View = itemView.findViewById(R.id.galleryCoverPlaceholder)
            private val galleryPlaceholderEmoji: TextView = itemView.findViewById(R.id.galleryPlaceholderEmoji)
            private val galleryPlaceholderTitle: TextView = itemView.findViewById(R.id.galleryPlaceholderTitle)
            private val galleryMediaBadge: TextView = itemView.findViewById(R.id.galleryMediaBadge)
            private val galleryBookTitle: TextView = itemView.findViewById(R.id.galleryBookTitle)
            private val galleryBookMeta: TextView = itemView.findViewById(R.id.galleryBookMeta)
            private val galleryBookQuote: TextView = itemView.findViewById(R.id.galleryBookQuote)
            private val galleryCardContainer: FrameLayout = itemView.findViewById(R.id.galleryCardContainer)

            fun bind(book: Book, onItemClick: (Book) -> Unit) {
                galleryMediaBadge.text = "${book.mediaType.emoji} ${book.mediaType.displayName}"
                galleryBookTitle.text = book.title

                val author = book.author.takeUnless { it.isNullOrBlank() } ?: "未知作者"
                val category = book.category.takeUnless { it.isNullOrBlank() } ?: "作品"
                val ratingStr = book.rating?.let { "★ ${String.format(java.util.Locale.CHINA, "%.1f", it / 2.0)}" } ?: "未评分"
                galleryBookMeta.text = "$author · $category · $ratingStr"

                val quote = book.shortComment.takeUnless { it.isNullOrBlank() }
                    ?: book.review.takeUnless { it.isNullOrBlank() }
                    ?: "静默于书架上的精神印记"
                galleryBookQuote.text = "“$quote”"

                if (!book.coverUrl.isNullOrBlank()) {
                    galleryCoverImage.visibility = View.VISIBLE
                    galleryCoverPlaceholder.visibility = View.GONE
                    CoverImageHelper.loadCover(galleryCoverImage, book.coverUrl)
                } else {
                    galleryCoverImage.visibility = View.GONE
                    galleryCoverPlaceholder.visibility = View.VISIBLE
                    galleryPlaceholderEmoji.text = book.mediaType.emoji
                    galleryPlaceholderTitle.text = book.title
                }

                itemView.setOnClickListener { onItemClick(book) }
                ViewAnimationHelper.attachSpringTouch(galleryCardContainer)
            }
        }
    }
}
