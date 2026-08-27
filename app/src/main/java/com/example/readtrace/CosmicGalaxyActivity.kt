package com.example.readtrace

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
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
            cosmicGravityGraphView.setGalaxyData(allBooks)
        }
        findViewById<Button>(R.id.btnFilterPodcast).setOnClickListener {
            HapticFeedbackEngine.lightClick(this)
            val filtered = allBooks.filter { it.mediaType == MediaType.PODCAST }
            cosmicGravityGraphView.setGalaxyData(if (filtered.isNotEmpty()) filtered else allBooks)
        }
        findViewById<Button>(R.id.btnFilterAnime).setOnClickListener {
            HapticFeedbackEngine.lightClick(this)
            val filtered = allBooks.filter { it.mediaType == MediaType.ANIME }
            cosmicGravityGraphView.setGalaxyData(if (filtered.isNotEmpty()) filtered else allBooks)
        }
        findViewById<Button>(R.id.btnFilterLiterature).setOnClickListener {
            HapticFeedbackEngine.lightClick(this)
            val filtered = allBooks.filter { it.mediaType == MediaType.BOOK }
            cosmicGravityGraphView.setGalaxyData(if (filtered.isNotEmpty()) filtered else allBooks)
        }
    }

    private fun loadGalaxyData() {
        allBooks = databaseHelper.getBooks()
        cosmicGravityGraphView.setGalaxyData(allBooks)
    }

    override fun onResume() {
        super.onResume()
        cosmicAuroraBackground.startAnimation()
    }

    override fun onPause() {
        cosmicAuroraBackground.stopAnimation()
        super.onPause()
    }
}
