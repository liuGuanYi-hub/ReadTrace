package com.example.readtrace.ui

import android.app.Activity
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.example.readtrace.R
import com.example.readtrace.data.BookDatabaseHelper
import com.example.readtrace.model.Book
import com.example.readtrace.model.BookStatus
import com.example.readtrace.model.MediaType
import com.example.readtrace.util.HapticFeedbackEngine
import com.example.readtrace.util.PersonalizedRecommendationEngine
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog

/**
 * ✨ 精神品味探索与个性化推荐底板 (PersonalizedDiscoveryBottomSheet)
 */
object PersonalizedDiscoveryBottomSheet {

    fun show(activity: Activity, onWorkAdded: () -> Unit = {}) {
        val dialog = BottomSheetDialog(activity)
        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_personalized_discovery_bottom_sheet, null)
        dialog.setContentView(view)

        dialog.behavior.apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
        }

        val tvSubtitle = view.findViewById<TextView>(R.id.txtDiscoverySubtitle)
        val btnAiAction = view.findViewById<TextView>(R.id.btnDiscoveryAction)
        val chipsRow = view.findViewById<LinearLayout>(R.id.discoveryTasteChipsRow)
        val loadingBox = view.findViewById<View>(R.id.discoveryLoadingBox)
        val contentScroll = view.findViewById<View>(R.id.discoveryContentScroll)
        val cardsContainer = view.findViewById<LinearLayout>(R.id.discoveryCardsContainer)

        val density = activity.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val taste = PersonalizedRecommendationEngine.analyzeUserTaste(activity)
        tvSubtitle.text = taste.summary

        // 渲染品味画像胶囊
        chipsRow.removeAllViews()
        val mediaChip = TextView(activity).apply {
            text = "✨ 主导媒介：${taste.dominantMediaType.displayName}"
            textSize = 11.5f
            setTextColor(Color.WHITE)
            setBackgroundResource(R.drawable.bg_dark_chip_selected)
            setPadding(dp(10), dp(4), dp(10), dp(4))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { marginEnd = dp(6) }
            layoutParams = lp
        }
        chipsRow.addView(mediaChip)

        taste.topTags.forEach { (tag, count) ->
            val tagChip = TextView(activity).apply {
                text = "#$tag ($count)"
                textSize = 11.5f
                setTextColor(activity.getColor(R.color.readtrace_ink))
                setBackgroundResource(R.drawable.bg_dark_chip)
                setPadding(dp(10), dp(4), dp(10), dp(4))
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { marginEnd = dp(6) }
                layoutParams = lp
            }
            chipsRow.addView(tagChip)
        }

        fun renderCards(works: List<PersonalizedRecommendationEngine.RecommendedWork>) {
            cardsContainer.removeAllViews()
            if (works.isEmpty()) {
                val emptyTv = TextView(activity).apply {
                    text = "暂无更多未收录的推荐作品，去书库探索更多吧。"
                    textSize = 13f
                    setTextColor(activity.getColor(R.color.readtrace_muted))
                    setPadding(0, dp(24), 0, dp(24))
                }
                cardsContainer.addView(emptyTv)
                return
            }

            works.forEach { work ->
                val card = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    setBackgroundResource(R.drawable.bg_dark_chip)
                    setPadding(dp(14), dp(12), dp(14), dp(12))
                    val lp = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ).apply { bottomMargin = dp(10) }
                    layoutParams = lp

                    // 顶行：媒介/标题/作者 + 评分
                    val topRow = LinearLayout(activity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        val titleCol = LinearLayout(activity).apply {
                            orientation = LinearLayout.VERTICAL
                            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                            val tView = TextView(activity).apply {
                                text = "${work.mediaType.emoji} 《${work.title}》"
                                textSize = 15f
                                setTextColor(activity.getColor(R.color.readtrace_ink))
                                setTypeface(null, android.graphics.Typeface.BOLD)
                            }
                            val aView = TextView(activity).apply {
                                text = work.author
                                textSize = 12f
                                setTextColor(activity.getColor(R.color.readtrace_muted))
                                setPadding(0, dp(2), 0, 0)
                            }
                            addView(tView)
                            addView(aView)
                        }
                        val ratingView = TextView(activity).apply {
                            text = "★ ${work.rating}"
                            textSize = 12.5f
                            setTextColor(Color.parseColor("#F4A261"))
                            setTypeface(null, android.graphics.Typeface.BOLD)
                            setBackgroundResource(R.drawable.bg_status_chip)
                            setPadding(dp(8), dp(3), dp(8), dp(3))
                        }
                        addView(titleCol)
                        addView(ratingView)
                    }

                    // 中间：标签流
                    val tagsRow = LinearLayout(activity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        setPadding(0, dp(8), 0, 0)
                        work.tags.take(4).forEach { tag ->
                            val tv = TextView(activity).apply {
                                text = "#$tag"
                                textSize = 11f
                                setTextColor(activity.getColor(R.color.readtrace_muted))
                                setBackgroundResource(R.drawable.bg_status_chip)
                                setPadding(dp(6), dp(2), dp(6), dp(2))
                                val tlp = LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.WRAP_CONTENT,
                                    LinearLayout.LayoutParams.WRAP_CONTENT,
                                ).apply { marginEnd = dp(4) }
                                layoutParams = tlp
                            }
                            addView(tv)
                        }
                    }

                    // 推荐理由
                    val reasonView = TextView(activity).apply {
                        text = work.matchReason
                        textSize = 12.5f
                        setTextColor(activity.getColor(R.color.readtrace_ink))
                        setLineSpacing(0f, 1.2f)
                        setPadding(0, dp(8), 0, dp(8))
                    }

                    // 底部一键收录按钮
                    val btnAdd = TextView(activity).apply {
                        val verb = when (work.mediaType) {
                            MediaType.BOOK -> "读"
                            MediaType.ANIME, MediaType.MOVIE -> "看"
                            MediaType.GAME -> "玩"
                            MediaType.MUSIC -> "听"
                        }
                        text = "+ 想$verb"
                        textSize = 12.5f
                        setTextColor(Color.WHITE)
                        setTypeface(null, android.graphics.Typeface.BOLD)
                        setBackgroundResource(R.drawable.bg_dark_chip_selected)
                        gravity = android.view.Gravity.CENTER
                        setPadding(dp(12), dp(8), dp(12), dp(8))
                        val blp = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                        )
                        layoutParams = blp

                        setOnClickListener {
                            HapticFeedbackEngine.stampImpact(activity)
                            val db = BookDatabaseHelper.getInstance(activity)
                            val newBook = Book(
                                title = work.title,
                                author = work.author,
                                mediaType = work.mediaType,
                                status = BookStatus.WISHLIST,
                                tags = work.tags,
                                description = work.matchReason,
                            )
                            db.insertBook(newBook)
                            onWorkAdded()
                            text = "✓ 已加入愿望单"
                            setBackgroundResource(R.drawable.bg_status_chip)
                            setTextColor(activity.getColor(R.color.readtrace_muted))
                            isEnabled = false
                            Toast.makeText(activity, "✨ 已收录《${work.title}》至愿望单", Toast.LENGTH_SHORT).show()
                        }
                    }

                    addView(topRow)
                    addView(tagsRow)
                    addView(reasonView)
                    addView(btnAdd)
                }
                cardsContainer.addView(card)
            }
        }

        // 默认先加载本地离线精选推荐
        val localWorks = PersonalizedRecommendationEngine.getCuratedRecommendations(activity, taste)
        renderCards(localWorks)

        // 点击 AI 深度探索按钮
        btnAiAction.setOnClickListener {
            HapticFeedbackEngine.lightClick(activity)
            loadingBox.visibility = View.VISIBLE
            contentScroll.visibility = View.GONE

            PersonalizedRecommendationEngine.fetchAiRecommendations(activity, taste) { aiWorks ->
                activity.runOnUiThread {
                    if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                    loadingBox.visibility = View.GONE
                    contentScroll.visibility = View.VISIBLE
                    renderCards(aiWorks)
                    Toast.makeText(activity, "✦ AI 深度品味探索已就绪", Toast.LENGTH_SHORT).show()
                }
            }
        }

        dialog.show()
    }
}
