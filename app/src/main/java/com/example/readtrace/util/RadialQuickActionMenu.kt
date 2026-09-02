package com.example.readtrace.util

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import com.example.readtrace.R
import com.example.readtrace.model.Book

/**
 * 🎛️ 长按径向快捷操作环 (RadialQuickActionMenu)
 *
 * P14 极客单手盲操：书架长按作品封面，手指周围展开一圈发光微胶囊，
 * 划过对应胶囊松手即触发，0.2 秒完成操作，告别层层点击。
 */
object RadialQuickActionMenu {

    /** 径向动作定义 */
    data class Action(val emoji: String, val label: String, val run: () -> Unit)

    /**
     * 在锚点视图中心上方弹出径向快捷环（锚点通常为作品封面卡片）
     */
    fun show(activity: Activity, book: Book, actions: List<Action>) {
        if (actions.isEmpty()) return

        val dialog = Dialog(activity)
        val root = FrameLayout(activity).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            // 点击环外空白处收起；胶囊自身消费点击
            setOnClickListener { dialog.dismiss() }
        }
        dialog.setContentView(root)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setGravity(Gravity.CENTER)
        }
        dialog.setCancelable(true)

        val density = activity.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()
        val radius = dp(118)

        // 中心徽章：作品标识
        val badge = TextView(activity).apply {
            text = "${book.mediaType.emoji}\n《${book.title.take(8)}》"
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setBackgroundResource(R.drawable.bg_quick_log_sheet)
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        root.addView(
            badge,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER),
        )

        // 径向微胶囊：以中心徽章为原点顺时针环绕展开
        actions.forEachIndexed { index, action ->
            val angle = Math.PI * 2 * index / actions.size - Math.PI / 2
            val targetTx = (Math.cos(angle) * radius).toFloat()
            val targetTy = (Math.sin(angle) * radius).toFloat()

            val lp = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            )
            val pill = TextView(activity).apply {
                text = "${action.emoji} ${action.label}"
                textSize = 12f
                setTextColor(Color.WHITE)
                setBackgroundResource(R.drawable.bg_dark_chip_selected)
                setPadding(dp(16), dp(9), dp(16), dp(9))
                // 入场动画：从中心径向弹出
                translationX = 0f
                translationY = 0f
                scaleX = 0.6f
                scaleY = 0.6f
                alpha = 0f
                animate()
                    .translationX(targetTx)
                    .translationY(targetTy)
                    .scaleX(1f)
                    .scaleY(1f)
                    .alpha(1f)
                    .setDuration(220L)
                    .setInterpolator(android.view.animation.OvershootInterpolator(1.2f))
                    .start()

                setOnTouchListener { v, event ->
                    when (event.action) {
                        android.view.MotionEvent.ACTION_DOWN -> {
                            HapticFeedbackEngine.needleDropCrackle(activity)
                            v.animate().scaleX(1.18f).scaleY(1.18f).setDuration(100L).start()
                        }
                        android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                            v.animate().scaleX(1f).scaleY(1f).setDuration(100L).start()
                        }
                    }
                    false
                }

                setOnClickListener {
                    HapticFeedbackEngine.cartridgeSnap(activity)
                    dialog.dismiss()
                    action.run()
                }
            }
            root.addView(pill, lp)
        }

        dialog.show()
    }

    /**
     * 为藏品快速构建高频 5 大动作集 (切态 / 速记 / 概念共鸣 / 票根工坊 / 移入回收站)
     */
    fun buildDefaultActions(
        activity: Activity,
        book: Book,
        onUpdate: () -> Unit,
    ): List<Action> {
        val nextStatus = when (book.status) {
            com.example.readtrace.model.BookStatus.WISHLIST -> com.example.readtrace.model.BookStatus.READING
            com.example.readtrace.model.BookStatus.READING -> com.example.readtrace.model.BookStatus.FINISHED
            else -> com.example.readtrace.model.BookStatus.WISHLIST
        }
        return listOf(
            Action("⚡", "切为${nextStatus.getDisplayName(book.mediaType)}") {
                val db = com.example.readtrace.data.BookDatabaseHelper.getInstance(activity)
                db.updateBook(book.copy(status = nextStatus))
                onUpdate()
            },
            Action("📝", "速写笔记") {
                val intent = android.content.Intent(activity, com.example.readtrace.BookDetailActivity::class.java).apply {
                    putExtra("book_id", book.id)
                }
                activity.startActivity(intent)
            },
            Action("🌌", "概念脉络") {
                val intent = android.content.Intent(activity, com.example.readtrace.BookDetailActivity::class.java).apply {
                    putExtra("book_id", book.id)
                }
                activity.startActivity(intent)
            },
            Action("🎟️", "纪念票根") {
                val intent = android.content.Intent(activity, com.example.readtrace.MovieTicketPosterActivity::class.java).apply {
                    putExtra("book_id", book.id)
                }
                activity.startActivity(intent)
            },
            Action("🗑️", "移入回收") {
                val db = com.example.readtrace.data.BookDatabaseHelper.getInstance(activity)
                db.archiveBook(book.id)
                onUpdate()
            },
        )
    }
}
