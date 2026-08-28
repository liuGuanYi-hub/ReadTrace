package com.example.readtrace.community.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.readtrace.R
import com.example.readtrace.community.model.CommunityExhibition
import com.example.readtrace.community.repository.CommunityRepository
import com.example.readtrace.util.FloatingBack

class ExhibitionDetailActivity : AppCompatActivity() {

    private lateinit var detailAvatar: TextView
    private lateinit var detailAuthor: TextView
    private lateinit var detailDate: TextView
    private lateinit var detailTitle: TextView
    private lateinit var detailDesc: TextView
    private lateinit var detailLikeBtn: TextView
    private lateinit var booksListContainer: LinearLayout
    private lateinit var commentsContainer: LinearLayout
    private lateinit var commentInput: EditText
    private lateinit var commentSendBtn: TextView

    private var exhibition: CommunityExhibition? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_exhibition_detail)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.exhibitionDetailRoot)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val exhibitionId = intent.getStringExtra(EXTRA_EXHIBITION_ID).orEmpty()
        exhibition = CommunityRepository.getExhibitionById(exhibitionId)
        if (exhibition == null) {
            Toast.makeText(this, "展览不存在", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        bindViews()
        renderExhibitionInfo()
        renderBooksList()
        renderComments()
    }

    private fun bindViews() {
        detailAvatar = findViewById(R.id.detailAvatar)
        detailAuthor = findViewById(R.id.detailAuthor)
        detailDate = findViewById(R.id.detailDate)
        detailTitle = findViewById(R.id.detailTitle)
        detailDesc = findViewById(R.id.detailDesc)
        detailLikeBtn = findViewById(R.id.detailLikeBtn)
        booksListContainer = findViewById(R.id.detailBooksListContainer)
        commentsContainer = findViewById(R.id.detailCommentsContainer)
        commentInput = findViewById(R.id.commentInput)
        commentSendBtn = findViewById(R.id.commentSendBtn)

        FloatingBack.install(this)

        findViewById<View>(R.id.detail3DExploreBtn).setOnClickListener {
            startActivity(CommunityGalleryActivity.createIntent(this, exhibition!!.id))
        }

        detailLikeBtn.setOnClickListener {
            CommunityRepository.toggleLike(exhibition!!.id)
            detailLikeBtn.text = if (exhibition!!.isLiked) "❤️ ${exhibition!!.likeCount} 共鸣" else "🤍 ${exhibition!!.likeCount} 共鸣"
        }

        commentSendBtn.setOnClickListener {
            val content = commentInput.text.toString().trim()
            if (content.isEmpty()) {
                Toast.makeText(this, "请输入评论内容", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            CommunityRepository.addComment(exhibition!!.id, "漫游读者", content)
            commentInput.setText("")
            Toast.makeText(this, "共鸣已留下", Toast.LENGTH_SHORT).show()
            renderComments()
        }
    }

    private fun renderExhibitionInfo() {
        detailAvatar.text = exhibition!!.authorAvatar
        detailAuthor.text = exhibition!!.authorName
        detailDate.text = "策展于 ${exhibition!!.createdAt}"
        detailTitle.text = exhibition!!.title
        detailDesc.text = exhibition!!.themeDescription
        detailLikeBtn.text = if (exhibition!!.isLiked) "❤️ ${exhibition!!.likeCount} 共鸣" else "🤍 ${exhibition!!.likeCount} 共鸣"
    }

    private fun renderBooksList() {
        booksListContainer.removeAllViews()
        val inflater = LayoutInflater.from(this)

        exhibition!!.curatedBooks.forEach { book ->
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundResource(R.drawable.bg_glass_panel_soft)
                setPadding(32, 28, 32, 28)
                elevation = 4f
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = 20 }

            // 作品主行
            val headerRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
            }

            val titleView = TextView(this).apply {
                text = "${book.mediaType.emoji} ${book.title}"
                textSize = 16f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(getColor(R.color.readtrace_ink))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val ratingView = TextView(this).apply {
                text = "★ ${book.rating ?: "-"}"
                textSize = 14f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(getColor(R.color.readtrace_accent))
            }
            headerRow.addView(titleView)
            headerRow.addView(ratingView)
            item.addView(headerRow)

            // 作者与分类
            val authorView = TextView(this).apply {
                text = "${book.author ?: "未知创作者"} · ${book.category ?: "精选"}"
                textSize = 12f
                setTextColor(getColor(R.color.readtrace_muted))
                setPadding(0, 4, 0, 0)
            }
            item.addView(authorView)

            // 短评或随想
            val commentText = book.shortComment ?: book.review
            if (!commentText.isNullOrBlank()) {
                val commentView = TextView(this).apply {
                    text = "“$commentText”"
                    textSize = 13f
                    setTextColor(getColor(R.color.readtrace_ink))
                    setPadding(0, 12, 0, 0)
                    setLineSpacing(0f, 1.2f)
                }
                item.addView(commentView)
            }

            // 操作行：收入我的书架 / 3D 试读
            val actionRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 16, 0, 0)
            }

            val saveBtn = TextView(this).apply {
                text = "✦ 收入我的书架"
                textSize = 12f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(getColor(R.color.white))
                setBackgroundResource(R.drawable.bg_primary_button)
                gravity = android.view.Gravity.CENTER
                setPadding(28, 14, 28, 14)
                setOnClickListener {
                    val saved = CommunityRepository.saveBookToLocalShelf(this@ExhibitionDetailActivity, book)
                    if (saved) {
                        Toast.makeText(this@ExhibitionDetailActivity, "✦ 已将《${book.title}》收入你的书架！", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@ExhibitionDetailActivity, "已在书架中或转存失败", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            actionRow.addView(saveBtn)
            item.addView(actionRow)

            booksListContainer.addView(item, lp)
        }
    }

    private fun renderComments() {
        commentsContainer.removeAllViews()
        val comments = CommunityRepository.getComments(exhibition!!.id)
        if (comments.isEmpty()) {
            val empty = TextView(this).apply {
                text = "暂无留言，写下你的第一条共鸣吧！"
                textSize = 13f
                setTextColor(getColor(R.color.readtrace_muted))
                setPadding(10, 10, 10, 10)
            }
            commentsContainer.addView(empty)
            return
        }

        comments.forEach { comment ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundResource(R.drawable.bg_glass_panel_soft)
                setPadding(24, 20, 24, 20)
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = 12 }

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
            }

            val avatar = TextView(this).apply {
                text = comment.userAvatar
                textSize = 14f
            }
            val name = TextView(this).apply {
                text = comment.userName
                textSize = 12f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(getColor(R.color.readtrace_ink))
                setPadding(10, 0, 0, 0)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val date = TextView(this).apply {
                text = comment.createdAt
                textSize = 10f
                setTextColor(getColor(R.color.readtrace_muted))
            }
            row.addView(avatar)
            row.addView(name)
            row.addView(date)
            card.addView(row)

            val body = TextView(this).apply {
                text = comment.content
                textSize = 13f
                setTextColor(getColor(R.color.readtrace_ink))
                setPadding(0, 8, 0, 0)
            }
            card.addView(body)

            commentsContainer.addView(card, lp)
        }
    }

    companion object {
        private const val EXTRA_EXHIBITION_ID = "com.example.readtrace.extra.EXHIBITION_ID"

        fun createIntent(context: Context, exhibitionId: String): Intent =
            Intent(context, ExhibitionDetailActivity::class.java).apply {
                putExtra(EXTRA_EXHIBITION_ID, exhibitionId)
            }
    }
}
