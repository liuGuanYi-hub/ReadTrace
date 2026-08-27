package com.example.readtrace.community.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.readtrace.R
import com.example.readtrace.community.model.CommunityExhibition
import com.example.readtrace.community.repository.CommunityRepository
import com.example.readtrace.util.FloatingBack

class CommunityActivity : AppCompatActivity() {

    private lateinit var searchInput: EditText
    private lateinit var categoriesContainer: LinearLayout
    private lateinit var featuredContainer: LinearLayout
    private lateinit var exhibitionsContainer: LinearLayout
    private lateinit var emptyView: TextView
    private lateinit var featuredSection: LinearLayout

    private var selectedCategory: String = "全部"
    private var searchQuery: String = ""

    private val categories = listOf("全部", "哲学", "科幻", "悬疑", "治愈", "历史", "小说", "影视", "游戏")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_community)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.communityRoot)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        bindViews()
        setupListeners()
        renderCategoryTabs()
    }

    override fun onResume() {
        super.onResume()
        refreshData()
    }

    private fun bindViews() {
        searchInput = findViewById(R.id.communitySearchInput)
        categoriesContainer = findViewById(R.id.communityCategoriesContainer)
        featuredContainer = findViewById(R.id.featuredContainer)
        exhibitionsContainer = findViewById(R.id.exhibitionsContainer)
        emptyView = findViewById(R.id.communityEmptyView)
        featuredSection = findViewById(R.id.featuredSection)
    }

    private fun setupListeners() {
        FloatingBack.install(this)
        findViewById<View>(R.id.communityRefreshBtn).setOnClickListener { refreshData() }
        findViewById<View>(R.id.publishFab).setOnClickListener {
            startActivity(Intent(this, PublishExhibitionActivity::class.java))
        }

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                searchQuery = s?.toString()?.trim().orEmpty()
                renderExhibitionsList()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun renderCategoryTabs() {
        categoriesContainer.removeAllViews()
        categories.forEach { category ->
            val textView = TextView(this).apply {
                text = category
                textSize = 13f
                setPadding(32, 14, 32, 14)
                val isSelected = category == selectedCategory
                setBackgroundResource(if (isSelected) R.drawable.bg_status_chip_selected else R.drawable.bg_status_pill)
                setTextColor(getColor(if (isSelected) R.color.white else R.color.readtrace_ink))
                setOnClickListener {
                    selectedCategory = category
                    renderCategoryTabs()
                    renderExhibitionsList()
                }
            }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { marginEnd = 16 }
            categoriesContainer.addView(textView, params)
        }
    }

    private fun refreshData() {
        renderFeaturedExhibitions()
        renderExhibitionsList()
    }

    private fun renderFeaturedExhibitions() {
        featuredContainer.removeAllViews()
        val featured = CommunityRepository.getFeaturedExhibitions()
        if (featured.isEmpty() || searchQuery.isNotEmpty() || selectedCategory != "全部") {
            featuredSection.visibility = View.GONE
            return
        }
        featuredSection.visibility = View.VISIBLE

        val inflater = LayoutInflater.from(this)
        featured.forEach { exhibition ->
            val card = inflater.inflate(R.layout.item_community_featured, featuredContainer, false)
            card.findViewById<TextView>(R.id.featuredAvatar).text = exhibition.authorAvatar
            card.findViewById<TextView>(R.id.featuredAuthor).text = exhibition.authorName
            card.findViewById<TextView>(R.id.featuredLikes).text = "🔥 ${exhibition.likeCount} 共鸣"
            card.findViewById<TextView>(R.id.featuredTitle).text = exhibition.title
            card.findViewById<TextView>(R.id.featuredDesc).text = exhibition.themeDescription

            card.setOnClickListener {
                startActivity(CommunityGalleryActivity.createIntent(this, exhibition.id))
            }
            featuredContainer.addView(card)
        }
    }

    private fun renderExhibitionsList() {
        exhibitionsContainer.removeAllViews()
        val exhibitions = CommunityRepository.getExhibitions(selectedCategory, searchQuery)

        if (exhibitions.isEmpty()) {
            emptyView.visibility = View.VISIBLE
            return
        }
        emptyView.visibility = View.GONE

        val inflater = LayoutInflater.from(this)
        exhibitions.forEach { exhibition ->
            val item = inflater.inflate(R.layout.item_community_exhibition, exhibitionsContainer, false)

            item.findViewById<TextView>(R.id.exhibitionAuthorAvatar).text = exhibition.authorAvatar
            item.findViewById<TextView>(R.id.exhibitionAuthorName).text = exhibition.authorName
            item.findViewById<TextView>(R.id.exhibitionDate).text = exhibition.createdAt
            item.findViewById<TextView>(R.id.exhibitionTitle).text = exhibition.title
            item.findViewById<TextView>(R.id.exhibitionDesc).text = exhibition.themeDescription

            val tagsText = if (exhibition.tags.isNotEmpty()) {
                exhibition.tags.joinToString(" ") { "#$it" }
            } else ""
            item.findViewById<TextView>(R.id.exhibitionTags).text = tagsText

            val likeBtn = item.findViewById<TextView>(R.id.exhibitionLikeBtn)
            likeBtn.text = if (exhibition.isLiked) "❤️ ${exhibition.likeCount}" else "🤍 ${exhibition.likeCount}"
            likeBtn.setOnClickListener {
                CommunityRepository.toggleLike(exhibition.id)
                likeBtn.text = if (exhibition.isLiked) "❤️ ${exhibition.likeCount}" else "🤍 ${exhibition.likeCount}"
            }

            item.findViewById<TextView>(R.id.exhibitionCommentBtn).apply {
                text = "💬 ${exhibition.commentCount}"
                setOnClickListener {
                    startActivity(ExhibitionDetailActivity.createIntent(this@CommunityActivity, exhibition.id))
                }
            }

            // 展品微缩胶囊流
            val booksContainer = item.findViewById<LinearLayout>(R.id.exhibitionBooksContainer)
            booksContainer.removeAllViews()
            exhibition.curatedBooks.take(3).forEach { book ->
                val pill = TextView(this).apply {
                    text = "${book.mediaType.emoji} ${book.title} ★${book.rating ?: "-"}"
                    textSize = 11f
                    setPadding(20, 8, 20, 8)
                    setBackgroundResource(R.drawable.bg_status_pill)
                    setTextColor(getColor(R.color.readtrace_ink))
                }
                val p = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { marginEnd = 12 }
                booksContainer.addView(pill, p)
            }

            // 点击 3D 漫游
            item.findViewById<View>(R.id.exhibition3DBadge).setOnClickListener {
                startActivity(CommunityGalleryActivity.createIntent(this, exhibition.id))
            }

            // 点击卡片进入展览详情
            item.setOnClickListener {
                startActivity(ExhibitionDetailActivity.createIntent(this, exhibition.id))
            }

            exhibitionsContainer.addView(item)
        }
    }

    companion object {
        fun createIntent(context: Context): Intent = Intent(context, CommunityActivity::class.java)
    }
}
