package com.example.readtrace

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.readtrace.data.BookDatabaseHelper
import com.example.readtrace.model.Book
import com.example.readtrace.model.MediaType
import com.example.readtrace.util.HapticFeedbackEngine
import com.example.readtrace.widget.AuroraFluidBackgroundView
import com.example.readtrace.widget.CosmicGravityGraphView
import com.example.readtrace.widget.EditorialBadgeView
import com.example.readtrace.util.FloatingBack

/**
 * 🪐 跨媒介认知引力星系展厅 Activity (Cosmic Galaxy Activity)
 * 对标 Cosmos.so 与 Siteinspire 宇宙星轨图谱。
 */
class CosmicGalaxyActivity : AppCompatActivity() {

    private lateinit var databaseHelper: BookDatabaseHelper
    private lateinit var cosmicGravityGraphView: CosmicGravityGraphView
    private lateinit var cosmicAuroraBackground: AuroraFluidBackgroundView
    private lateinit var galaxyBadge: EditorialBadgeView
    private lateinit var tvGalaxySubtitle: TextView
    private lateinit var tvGalaxyEmpty: TextView

    private var allBooks: List<Book> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cosmic_galaxy)

        databaseHelper = BookDatabaseHelper(this)
        initViews()
        loadGalaxyData()
    }

    private fun initViews() {
        cosmicGravityGraphView = findViewById(R.id.cosmicGravityGraphView)
        cosmicAuroraBackground = findViewById(R.id.cosmicAuroraBackground)
        galaxyBadge = findViewById(R.id.galaxyBadge)
        tvGalaxySubtitle = findViewById(R.id.tvGalaxySubtitle)
        tvGalaxyEmpty = findViewById(R.id.tvGalaxyEmpty)

        galaxyBadge.setBadgeContent("COSMOS", "#4DEEEA")

        FloatingBack.install(this)

        // 节点点击跳转详情
        cosmicGravityGraphView.onNodeClickListener = { book ->
            val intent = BookDetailActivity.createIntent(this, book.id)
            startActivity(intent)
        }

        // 媒介分类过滤
        findViewById<Button>(R.id.btnFilterAll).setOnClickListener {
            HapticFeedbackEngine.lightClick(this)
            showGalaxyWorks(selectGalaxyWorks(allBooks))
        }
        findViewById<Button>(R.id.btnFilterMusic).setOnClickListener {
            HapticFeedbackEngine.lightClick(this)
            showMediaGalaxy(MediaType.MUSIC)
        }
        findViewById<Button>(R.id.btnFilterAnime).setOnClickListener {
            HapticFeedbackEngine.lightClick(this)
            showMediaGalaxy(MediaType.ANIME)
        }
        findViewById<Button>(R.id.btnFilterLiterature).setOnClickListener {
            HapticFeedbackEngine.lightClick(this)
            showMediaGalaxy(MediaType.BOOK)
        }
        findViewById<Button>(R.id.btnFilterMovie).setOnClickListener {
            HapticFeedbackEngine.lightClick(this)
            showMediaGalaxy(MediaType.MOVIE)
        }
        findViewById<Button>(R.id.btnFilterGame).setOnClickListener {
            HapticFeedbackEngine.lightClick(this)
            showMediaGalaxy(MediaType.GAME)
        }
    }

    private fun loadGalaxyData() {
        allBooks = databaseHelper.getBooks()
        showGalaxyWorks(selectGalaxyWorks(allBooks))
        val shownCount = minOf(allBooks.size, MAX_GALAXY_NODES)
        tvGalaxySubtitle.text = "共收录 ${allBooks.size} 部 · 呈现星等最高的 $shownCount 部 · 拖拽/点击天体"
    }

    /**
     * 星系只呈现"最亮"的天体：按评分（星等）降序、其次最近更新排序取前 N 部。
     */
    private fun selectGalaxyWorks(books: List<Book>): List<Book> =
        books.sortedWith(
            compareByDescending<Book> { it.rating ?: 0.0 }.thenByDescending { it.updatedAt }
        ).take(MAX_GALAXY_NODES)

    private fun showMediaGalaxy(type: MediaType) {
        showGalaxyWorks(
            selectGalaxyWorks(allBooks.filter { it.mediaType == type }),
            "🌌 这个星域还没有${type.displayName}天体\n去记录一部${type.displayName}作品吧",
        )
    }

    private fun showGalaxyWorks(works: List<Book>, emptyHint: String? = null) {
        if (works.isEmpty()) {
            tvGalaxyEmpty.text = emptyHint ?: "🌌 星系还是一片虚无\n先去书房记录第一部作品吧"
            tvGalaxyEmpty.visibility = View.VISIBLE
        } else {
            tvGalaxyEmpty.visibility = View.GONE
        }
        cosmicGravityGraphView.setGalaxyData(works)
    }

    override fun onResume() {
        super.onResume()
        cosmicAuroraBackground.startAnimation()
    }

    override fun onPause() {
        cosmicAuroraBackground.stopAnimation()
        super.onPause()
    }

    private companion object {
        /** 星系单次呈现的天体数量上限 */
        const val MAX_GALAXY_NODES = 28
    }
}
