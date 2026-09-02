package com.example.readtrace

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.readtrace.data.BookDatabaseHelper
import com.example.readtrace.model.Book
import com.example.readtrace.util.HapticFeedbackEngine
import com.example.readtrace.widget.DioramaBoxView
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * 🔮 2.5D visionOS 空间深度视差展厅 (SpatialParallaxGalleryActivity)
 *
 * P12 批次E：以「实体标本盒」隐喻重构展厅体验——
 * 每部藏品是一个 4 层景深的 DioramaBox（背景毛玻璃 / 极客元数据 /
 * 3D 浮雕封面 / 高光透镜层），陀螺仪 + 触控双通道视差，
 * 左右滑动在精选藏品间漫游，彻底摆脱生硬 3D 眩晕感。
 * 经典 3D 展厅（Gallery3DActivity）保留为可选模式。
 */
class SpatialParallaxGalleryActivity : AppCompatActivity() {

    private lateinit var stage: FrameLayout
    private lateinit var counterText: TextView
    private var works: List<Book> = emptyList()
    private var index = 0

    private var downX = 0f
    private var downTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        works = BookDatabaseHelper.getInstance(this).getGalleryFeaturedWorks(24)
        if (works.isEmpty()) {
            Toast.makeText(this, "书库还没有藏品，先去收录几部作品吧", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        stage = FrameLayout(this).apply { setBackgroundColor(Color.parseColor("#05070B")) }
        setContentView(stage)

        counterText = TextView(this).apply {
            textSize = 12f
            setTextColor(Color.parseColor("#99FFFFFF"))
            gravity = Gravity.CENTER
            setPadding(0, 48, 0, 0)
        }
        stage.addView(
            counterText,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP,
            ),
        )

        val exit = TextView(this).apply {
            text = "✕"
            textSize = 16f
            setTextColor(Color.WHITE)
            setPadding(40, 40, 40, 40)
            setOnClickListener { finish() }
        }
        stage.addView(
            exit,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.END,
            ),
        )

        showAt(index)
        stage.startAnimation(AnimationUtils.loadAnimation(this, R.anim.home_enter))
    }

    /** 渲染第 index 个「标本盒」 */
    private fun showAt(index: Int) {
        val book = works[index]
        counterText.text = "🔮 空间标本盒 · ${index + 1} / ${works.size} · 滑动漫游 · 轻触进入"

        // Layer 0：环境弥散落影底座
        val glow = View(this).apply {
            setBackgroundColor(Color.argb(60, 77, 238, 234))
            elevation = 4f
        }

        // Layer 1：极客等宽元数据标签
        val meta = TextView(this).apply {
            val created = runCatching {
                SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(SimpleDateFormat("yyyy-MM-dd'T'", Locale.getDefault()).parse(book.createdAt)!!)
            }.getOrDefault("")
            textSize = 10f
            typeface = android.graphics.Typeface.MONOSPACE
            setTextColor(Color.parseColor("#8FE6E4"))
            text = "[ARCHIVE_ID: #RT-${"%04d".format(book.id.toInt())} // ${book.mediaType.emoji} // ⭐${book.rating ?: "-"} // $created]"
            gravity = Gravity.CENTER
        }

        // Layer 2：核心主体（浮雕封面 + 标题 + 金句）
        val subject = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(60, 60, 60, 60)
        }
        val cover = android.widget.ImageView(this)
        cover.layoutParams = LinearLayout.LayoutParams(340, 480).apply { gravity = Gravity.CENTER_HORIZONTAL }
        cover.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
        com.example.readtrace.util.CoverImageHelper.loadCover(cover, book.coverUrl)
        subject.addView(cover)
        subject.addView(
            TextView(this).apply {
                text = "《${book.title}》"
                textSize = 19f
                setTextColor(Color.WHITE)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(0, 28, 0, 4)
            },
        )
        subject.addView(
            TextView(this).apply {
                text = book.shortComment ?: book.author ?: "—"
                textSize = 12.5f
                setTextColor(Color.parseColor("#CCFFFFFF"))
                gravity = Gravity.CENTER
                setPadding(0, 4, 0, 0)
            },
        )

        // 标本盒组装（子 View 顺序即图层序，景深系数由 DioramaBoxView 内部分配）
        val box = DioramaBoxView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.CENTER,
            )
        }
        box.addView(glow, FrameLayout.LayoutParams(620, 760, Gravity.CENTER))
        box.addView(meta, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL or Gravity.TOP).apply { topMargin = 220 })
        box.addView(subject, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER))

        stage.addView(
            box,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )

        // 陀螺仪微倾角驱动全盒视差
        val gyroscopeHelper = com.example.readtrace.util.GyroscopeParallaxHelper(this)
        gyroscopeHelper.bindLifecycle(lifecycle)

        box.setOnClickListener {
            HapticFeedbackEngine.lightClick(this)
            startActivity(BookDetailActivity.createIntent(this, book.id))
        }
    }

    override fun dispatchTouchEvent(event: android.view.MotionEvent): Boolean {
        when (event.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downTime = System.currentTimeMillis()
            }
            android.view.MotionEvent.ACTION_UP -> {
                val dx = event.x - downX
                if (Math.abs(dx) > 120f && System.currentTimeMillis() - downTime < 600L) {
                    val nextIndex = if (dx < 0) (index + 1).coerceAtMost(works.size - 1) else (index - 1).coerceAtLeast(0)
                    if (nextIndex != index) {
                        index = nextIndex
                        // 移除旧标本盒（counterText/exit 保留）
                        while (stage.childCount > 2) stage.removeViewAt(2)
                        HapticFeedbackEngine.pageTurnRustle(this)
                        showAt(index)
                        return true
                    }
                }
            }
        }
        return super.dispatchTouchEvent(event)
    }
}
