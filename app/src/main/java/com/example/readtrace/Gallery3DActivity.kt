package com.example.readtrace

import android.content.Context
import android.content.Intent
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.readtrace.data.BookDatabaseHelper
import com.example.readtrace.gallery3d.Gallery3DRenderer
import com.example.readtrace.gallery3d.GalleryTouchHandler
import com.example.readtrace.model.Book
import com.example.readtrace.util.CoverImageHelper
import com.example.readtrace.util.FloatingBack

class Gallery3DActivity : AppCompatActivity() {

    private lateinit var databaseHelper: BookDatabaseHelper
    private lateinit var gallerySurfaceView: GLSurfaceView
    private lateinit var renderer: Gallery3DRenderer

    private lateinit var gallerySubtitle: TextView
    private lateinit var focusCard: View
    private lateinit var focusCoverImage: ImageView
    private lateinit var focusMediaBadge: TextView
    private lateinit var focusStatusPill: TextView
    private lateinit var focusRating: TextView
    private lateinit var focusTitle: TextView
    private lateinit var focusAuthor: TextView
    private lateinit var focusComment: TextView
    private lateinit var galleryEmptyPanel: View

    private lateinit var themeMidnight: TextView
    private lateinit var themeWarm: TextView
    private lateinit var themeZen: TextView

    private var featuredBooks: List<Book> = emptyList()
    private var currentFocusedBook: Book? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_gallery_3d)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.galleryRoot)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        databaseHelper = BookDatabaseHelper(this)
        bindViews()
        init3DGallery()
        configureThemes()

        FloatingBack.install(this)
    }

    private fun bindViews() {
        gallerySurfaceView = findViewById(R.id.gallerySurfaceView)
        gallerySubtitle = findViewById(R.id.gallerySubtitle)
        focusCard = findViewById(R.id.focusCard)
        focusCoverImage = findViewById(R.id.focusCoverImage)
        focusMediaBadge = findViewById(R.id.focusMediaBadge)
        focusStatusPill = findViewById(R.id.focusStatusPill)
        focusRating = findViewById(R.id.focusRating)
        focusTitle = findViewById(R.id.focusTitle)
        focusAuthor = findViewById(R.id.focusAuthor)
        focusComment = findViewById(R.id.focusComment)
        galleryEmptyPanel = findViewById(R.id.galleryEmptyPanel)

        themeMidnight = findViewById(R.id.themeMidnight)
        themeWarm = findViewById(R.id.themeWarm)
        themeZen = findViewById(R.id.themeZen)
    }

    private fun init3DGallery() {
        featuredBooks = databaseHelper.getGalleryFeaturedWorks(24)

        if (featuredBooks.isEmpty()) {
            gallerySurfaceView.visibility = View.GONE
            focusCard.visibility = View.GONE
            // 空书架时 renderer 未初始化，同步隐藏主题按钮避免点击触发 lateinit 未初始化崩溃
            themeMidnight.visibility = View.GONE
            themeWarm.visibility = View.GONE
            themeZen.visibility = View.GONE
            galleryEmptyPanel.visibility = View.VISIBLE
            return
        }

        galleryEmptyPanel.visibility = View.GONE
        gallerySurfaceView.visibility = View.VISIBLE
        focusCard.visibility = View.VISIBLE

        gallerySubtitle.text = getString(R.string.home_gallery_badge_format, featuredBooks.size)

        gallerySurfaceView.setEGLContextClientVersion(2)
        renderer = Gallery3DRenderer(this, featuredBooks)
        gallerySurfaceView.setRenderer(renderer)
        gallerySurfaceView.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY

        val touchHandler = GalleryTouchHandler(this, renderer) { focusedIndex ->
            if (focusedIndex in featuredBooks.indices) {
                runOnUiThread {
                    updateFocusCard(featuredBooks[focusedIndex])
                }
            }
        }
        gallerySurfaceView.setOnTouchListener(touchHandler)

        // 默认聚焦第一本书
        updateFocusCard(featuredBooks.first())

        focusCard.setOnClickListener {
            currentFocusedBook?.let { book ->
                startActivity(BookDetailActivity.createIntent(this, book.id))
            }
        }
        focusCard.setOnLongClickListener {
            currentFocusedBook?.let { book ->
                startActivity(BookDetailActivity.createIntent(this, book.id))
            }
            true
        }
    }

    private fun updateFocusCard(book: Book) {
        currentFocusedBook = book
        focusTitle.text = book.title
        focusAuthor.text = book.author ?: "未知创作者"
        focusMediaBadge.text = book.mediaType.emoji
        focusStatusPill.text = book.status.getDisplayName(book.mediaType)

        if (book.rating != null) {
            focusRating.visibility = View.VISIBLE
            focusRating.text = "★ ${String.format("%.1f", book.rating / 2.0)}"
        } else {
            focusRating.visibility = View.GONE
        }

        val commentText = book.shortComment?.trim()?.takeIf { it.isNotEmpty() }
            ?: book.review?.trim()?.takeIf { it.isNotEmpty() }
            ?: "时光漫溢，此作常驻心间"
        focusComment.text = "“$commentText”"

        CoverImageHelper.loadCover(focusCoverImage, book.coverUrl)
    }

    private fun configureThemes() {
        val themes = listOf(
            themeMidnight to Gallery3DRenderer.GalleryTheme.MIDNIGHT,
            themeWarm to Gallery3DRenderer.GalleryTheme.WARM_LIBRARY,
            themeZen to Gallery3DRenderer.GalleryTheme.ZEN_OASIS,
        )

        themes.forEach { (view, theme) ->
            view.setOnClickListener {
                // 守卫未初始化的 renderer，防止极端路径下 lateinit 访问崩溃
                if (::renderer.isInitialized) {
                    renderer.currentTheme = theme
                    updateThemeChips(theme)
                }
            }
        }
    }

    private fun updateThemeChips(selected: Gallery3DRenderer.GalleryTheme) {
        val themeViews = listOf(
            themeMidnight to Gallery3DRenderer.GalleryTheme.MIDNIGHT,
            themeWarm to Gallery3DRenderer.GalleryTheme.WARM_LIBRARY,
            themeZen to Gallery3DRenderer.GalleryTheme.ZEN_OASIS,
        )
        themeViews.forEach { (view, theme) ->
            val isCurrent = theme == selected
            view.setBackgroundResource(
                if (isCurrent) R.drawable.bg_status_chip_selected else R.drawable.bg_status_chip,
            )
            view.setTextColor(
                ContextCompat.getColor(this, if (isCurrent) R.color.white else R.color.readtrace_muted),
            )
        }
    }

    override fun onResume() {
        super.onResume()
        if (::gallerySurfaceView.isInitialized && gallerySurfaceView.visibility == View.VISIBLE) {
            gallerySurfaceView.onResume()
        }
    }

    override fun onPause() {
        super.onPause()
        if (::gallerySurfaceView.isInitialized && gallerySurfaceView.visibility == View.VISIBLE) {
            gallerySurfaceView.onPause()
        }
    }

    override fun onDestroy() {
        databaseHelper.close()
        super.onDestroy()
    }

    companion object {
        fun createIntent(context: Context): Intent =
            Intent(context, Gallery3DActivity::class.java)
    }
}
