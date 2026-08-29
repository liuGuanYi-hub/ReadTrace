package com.example.readtrace.community.ui

import android.content.Context
import android.content.Intent
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.readtrace.R
import com.example.readtrace.community.model.CommunityExhibition
import com.example.readtrace.community.repository.CommunityRepository
import com.example.readtrace.gallery3d.Gallery3DRenderer
import com.example.readtrace.gallery3d.GalleryTouchHandler
import com.example.readtrace.model.Book
import com.example.readtrace.util.FloatingBack

class CommunityGalleryActivity : AppCompatActivity() {

    private lateinit var gallerySurfaceView: GLSurfaceView
    private lateinit var galleryTitle: TextView
    private lateinit var galleryAuthor: TextView
    private lateinit var focusTitle: TextView
    private lateinit var focusAuthor: TextView
    private lateinit var focusMediaBadge: TextView
    private lateinit var focusRating: TextView
    private lateinit var focusComment: TextView
    private lateinit var saveLocalBtn: TextView
    private lateinit var read3DBtn: TextView
    private lateinit var focusCard: View

    private var exhibition: CommunityExhibition? = null
    private var currentFocusedBook: Book? = null
    private var currentThemeIndex = 0
    private val themes = listOf("星空漫想", "暖木书房", "禅意绿洲")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_community_gallery)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.communityGalleryRoot)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val exhibitionId = intent.getStringExtra(EXTRA_EXHIBITION_ID).orEmpty()
        exhibition = CommunityRepository.getExhibitionById(exhibitionId)
        if (exhibition == null || exhibition!!.curatedBooks.isEmpty()) {
            Toast.makeText(this, "展厅数据不存在", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        bindViews()
        setupGallery3D()
    }

    private fun bindViews() {
        gallerySurfaceView = findViewById(R.id.communityGallerySurfaceView)
        galleryTitle = findViewById(R.id.communityGalleryTitle)
        galleryAuthor = findViewById(R.id.communityGalleryAuthor)
        focusTitle = findViewById(R.id.communityFocusTitle)
        focusAuthor = findViewById(R.id.communityFocusAuthor)
        focusMediaBadge = findViewById(R.id.communityFocusMediaBadge)
        focusRating = findViewById(R.id.communityFocusRating)
        focusComment = findViewById(R.id.communityFocusComment)
        saveLocalBtn = findViewById(R.id.communitySaveLocalBtn)
        read3DBtn = findViewById(R.id.communityRead3DBtn)
        focusCard = findViewById(R.id.communityFocusCard)

        galleryTitle.text = exhibition!!.title
        galleryAuthor.text = "策展人：${exhibition!!.authorName} ${exhibition!!.authorAvatar}"

        FloatingBack.install(this)

        val themeBtn = findViewById<TextView>(R.id.communityGalleryThemeBtn)
        themeBtn.setOnClickListener {
            currentThemeIndex = (currentThemeIndex + 1) % themes.size
            val newTheme = themes[currentThemeIndex]
            themeBtn.text = when (newTheme) {
                "星空漫想" -> "🌌 星空"
                "暖木书房" -> "🕯️ 暖木"
                else -> "🌿 禅意"
            }
        }
    }

    private fun setupGallery3D() {
        val books = exhibition!!.curatedBooks
        gallerySurfaceView.setEGLContextClientVersion(2)
        val renderer = Gallery3DRenderer(this, books)
        gallerySurfaceView.setRenderer(renderer)
        gallerySurfaceView.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY

        val touchHandler = GalleryTouchHandler(this, renderer) { focusedIndex ->
            if (focusedIndex in books.indices) {
                runOnUiThread {
                    updateFocusCard(books[focusedIndex])
                }
            }
        }
        gallerySurfaceView.setOnTouchListener(touchHandler)

        updateFocusCard(books.first())

        // 一键转存到本地书架
        saveLocalBtn.setOnClickListener {
            currentFocusedBook?.let { book ->
                val saved = CommunityRepository.saveBookToLocalShelf(this, book)
                if (saved) {
                    Toast.makeText(this, "✦ 已将《${book.title}》收入你的本地书架！", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "已在你的书架中或转存失败", Toast.LENGTH_SHORT).show()
                }
            }
        }

        read3DBtn.visibility = View.GONE
    }

    private fun updateFocusCard(book: Book) {
        currentFocusedBook = book
        focusTitle.text = book.title
        focusAuthor.text = "${book.author ?: "未知作者"} · ${book.category ?: "精选"}"
        focusMediaBadge.text = book.mediaType.emoji

        if (book.rating != null) {
            focusRating.visibility = View.VISIBLE
            focusRating.text = "★ ${String.format("%.1f", book.rating / 2.0)}"
        } else {
            focusRating.visibility = View.GONE
        }

        val commentText = book.shortComment?.trim()?.takeIf { it.isNotEmpty() }
            ?: book.review?.trim()?.takeIf { it.isNotEmpty() }
            ?: "此作常驻心间，愿与诸君共鸣"
        focusComment.text = "“$commentText”"
    }

    override fun onResume() {
        super.onResume()
        gallerySurfaceView.onResume()
    }

    override fun onPause() {
        super.onPause()
        gallerySurfaceView.onPause()
    }

    companion object {
        private const val EXTRA_EXHIBITION_ID = "com.example.readtrace.extra.EXHIBITION_ID"

        fun createIntent(context: Context, exhibitionId: String): Intent =
            Intent(context, CommunityGalleryActivity::class.java).apply {
                putExtra(EXTRA_EXHIBITION_ID, exhibitionId)
            }
    }
}
