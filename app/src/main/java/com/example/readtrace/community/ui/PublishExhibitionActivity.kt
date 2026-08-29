package com.example.readtrace.community.ui

import android.os.Bundle
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.readtrace.R
import com.example.readtrace.community.repository.CommunityRepository
import com.example.readtrace.data.BookDatabaseHelper
import com.example.readtrace.model.Book
import com.example.readtrace.util.FloatingBack

class PublishExhibitionActivity : AppCompatActivity() {

    private lateinit var databaseHelper: BookDatabaseHelper
    private lateinit var authorInput: EditText
    private lateinit var titleInput: EditText
    private lateinit var descInput: EditText
    private lateinit var avatarSelect: TextView
    private lateinit var selectedCountView: TextView
    private lateinit var booksContainer: LinearLayout
    private lateinit var submitBtn: TextView

    private val allLocalBooks = mutableListOf<Book>()
    private val selectedBooks = mutableListOf<Book>()
    private var currentAvatar = "🦉"
    private val avatars = listOf("🦉", "🌌", "🕯️", "🌿", "🌊", "🌙", "🎨", "☕")
    private var avatarIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_publish_exhibition)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.publishRoot)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        databaseHelper = BookDatabaseHelper(this)
        bindViews()
        loadLocalBooks()
    }

    private fun bindViews() {
        authorInput = findViewById(R.id.publishAuthorInput)
        titleInput = findViewById(R.id.publishTitleInput)
        descInput = findViewById(R.id.publishDescInput)
        avatarSelect = findViewById(R.id.publishAvatarSelect)
        selectedCountView = findViewById(R.id.publishSelectedCount)
        booksContainer = findViewById(R.id.publishBooksContainer)
        submitBtn = findViewById(R.id.publishSubmitBtn)

        FloatingBack.install(this)

        avatarSelect.setOnClickListener {
            avatarIndex = (avatarIndex + 1) % avatars.size
            currentAvatar = avatars[avatarIndex]
            avatarSelect.text = currentAvatar
        }

        submitBtn.setOnClickListener {
            submitExhibition()
        }
    }

    private fun loadLocalBooks() {
        allLocalBooks.clear()
        allLocalBooks.addAll(databaseHelper.getBooks())

        booksContainer.removeAllViews()
        if (allLocalBooks.isEmpty()) {
            val empty = TextView(this).apply {
                text = "你的书架暂无书籍，先去添加或导入一些作品吧！"
                textSize = 13f
                setTextColor(getColor(R.color.readtrace_muted))
                setPadding(10, 20, 10, 20)
            }
            booksContainer.addView(empty)
            return
        }

        allLocalBooks.forEach { book ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(12, 16, 12, 16)
                setBackgroundResource(R.drawable.bg_glass_panel_soft)
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = 8 }

            val checkBox = CheckBox(this).apply {
                isChecked = false
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        if (!selectedBooks.contains(book)) selectedBooks.add(book)
                    } else {
                        selectedBooks.remove(book)
                    }
                    updateSelectedCount()
                }
            }

            val titleView = TextView(this).apply {
                text = "${book.mediaType.emoji} ${book.title}"
                textSize = 14f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(getColor(R.color.readtrace_ink))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val metaView = TextView(this).apply {
                text = "${book.category ?: "精选"} · ★${book.rating?.div(2.0) ?: "-"}"
                textSize = 12f
                setTextColor(getColor(R.color.readtrace_muted))
            }

            row.addView(checkBox)
            row.addView(titleView)
            row.addView(metaView)

            row.setOnClickListener {
                checkBox.isChecked = !checkBox.isChecked
            }

            booksContainer.addView(row, lp)
        }
    }

    private fun updateSelectedCount() {
        selectedCountView.text = "已选 ${selectedBooks.size} 本"
    }

    private fun submitExhibition() {
        val title = titleInput.text.toString().trim()
        if (title.isEmpty()) {
            titleInput.error = "请输入展厅主题名称"
            titleInput.requestFocus()
            return
        }

        if (selectedBooks.isEmpty()) {
            Toast.makeText(this, "请至少勾选 1 本参展作品", Toast.LENGTH_SHORT).show()
            return
        }

        val authorName = authorInput.text.toString().trim().ifBlank { "深居漫步者" }
        val desc = descInput.text.toString().trim().ifBlank { "在静谧时光中与诸君分享这些滋养心灵的佳作。" }
        val tags = selectedBooks.mapNotNull { it.category?.trim() }.filter { it.isNotEmpty() }.distinct()

        CommunityRepository.publishExhibition(
            authorName = authorName,
            authorAvatar = currentAvatar,
            title = title,
            description = desc,
            books = selectedBooks.toList(),
            tags = tags,
            featuredTheme = "星空漫想",
        )

        Toast.makeText(this, "🎉 展厅发布成功！已在阅痕广场展示", Toast.LENGTH_LONG).show()
        finish()
    }

    override fun onDestroy() {
        databaseHelper.close()
        super.onDestroy()
    }
}
