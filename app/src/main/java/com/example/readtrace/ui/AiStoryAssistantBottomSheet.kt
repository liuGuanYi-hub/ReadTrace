package com.example.readtrace.ui

import android.app.Activity
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.example.readtrace.R
import com.example.readtrace.data.BookDatabaseHelper
import com.example.readtrace.data.UserPreferencesManager
import com.example.readtrace.model.Book
import com.example.readtrace.model.BookCharacter
import com.example.readtrace.model.BookOutline
import com.example.readtrace.model.Note
import com.example.readtrace.model.NoteType
import com.example.readtrace.util.AiAssistantEngine
import com.example.readtrace.util.HapticFeedbackEngine
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog

/**
 * 🤖 AI 角色与故事大纲智能分析底板 (AiStoryAssistantBottomSheet)
 */
object AiStoryAssistantBottomSheet {

    fun show(activity: Activity, book: Book, onBookUpdated: () -> Unit) {
        val dialog = BottomSheetDialog(activity)
        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_ai_story_assistant_bottom_sheet, null)
        dialog.setContentView(view)

        dialog.behavior.apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
        }

        val tvTitle = view.findViewById<TextView>(R.id.txtAiAssistantTitle)
        val tvSubtitle = view.findViewById<TextView>(R.id.txtAiAssistantSubtitle)
        val btnConfig = view.findViewById<View>(R.id.btnAiConfig)
        val loadingBox = view.findViewById<View>(R.id.aiLoadingBox)
        val contentScroll = view.findViewById<View>(R.id.aiContentScroll)
        val txtSourceTag = view.findViewById<TextView>(R.id.txtAiSourceTag)
        val txtPremise = view.findViewById<TextView>(R.id.txtAiPremise)
        val charsContainer = view.findViewById<LinearLayout>(R.id.aiCharactersContainer)
        val outlineContainer = view.findViewById<LinearLayout>(R.id.aiOutlineContainer)
        val btnFillDesc = view.findViewById<TextView>(R.id.btnAiFillDescription)
        val btnSaveNote = view.findViewById<TextView>(R.id.btnAiSaveAsNote)

        tvTitle.text = "🤖 AI 角色与故事大纲"
        tvSubtitle.text = "《${book.title}》· 智能梳理人物与脉络"

        var currentAnalysis: AiAssistantEngine.AiStoryAnalysis? = null

        fun runAnalysis() {
            loadingBox.visibility = View.VISIBLE
            contentScroll.visibility = View.GONE

            AiAssistantEngine.analyzeStory(
                context = activity,
                title = book.title,
                author = book.author,
                mediaType = book.mediaType,
                existingSummary = book.description,
            ) { analysis ->
                activity.runOnUiThread {
                    if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                    currentAnalysis = analysis
                    loadingBox.visibility = View.GONE
                    contentScroll.visibility = View.VISIBLE

                    txtSourceTag.text = if (analysis.isFromOffline) "✦ 离线经典知识库" else "✦ AI 实时深度生成"
                    txtPremise.text = analysis.premise

                    val density = activity.resources.displayMetrics.density
                    fun dp(v: Int) = (v * density).toInt()

                    // 渲染角色列表
                    charsContainer.removeAllViews()
                    analysis.characters.forEach { char ->
                        val charCard = LinearLayout(activity).apply {
                            orientation = LinearLayout.VERTICAL
                            setBackgroundResource(R.drawable.bg_dark_chip)
                            setPadding(dp(12), dp(10), dp(12), dp(10))
                            val lp = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                            ).apply { bottomMargin = dp(8) }
                            layoutParams = lp

                            val topRow = LinearLayout(activity).apply {
                                orientation = LinearLayout.HORIZONTAL
                                val nameView = TextView(activity).apply {
                                    text = char.name
                                    textSize = 14f
                                    setTextColor(activity.getColor(R.color.readtrace_ink))
                                    setTypeface(null, android.graphics.Typeface.BOLD)
                                    layoutParams = LinearLayout.LayoutParams(
                                        LinearLayout.LayoutParams.WRAP_CONTENT,
                                        LinearLayout.LayoutParams.WRAP_CONTENT,
                                    ).apply { weight = 1f }
                                }
                                val badgeView = TextView(activity).apply {
                                    text = char.identity
                                    textSize = 11f
                                    setTextColor(Color.WHITE)
                                    setBackgroundResource(R.drawable.bg_dark_chip_selected)
                                    setPadding(dp(8), dp(2), dp(8), dp(2))
                                }
                                addView(nameView)
                                addView(badgeView)
                            }
                            val descView = TextView(activity).apply {
                                text = char.description
                                textSize = 12f
                                setTextColor(activity.getColor(R.color.readtrace_muted))
                                setLineSpacing(0f, 1.2f)
                                setPadding(0, dp(6), 0, 0)
                            }
                            addView(topRow)
                            addView(descView)
                        }
                        charsContainer.addView(charCard)
                    }

                    // 渲染分幕大纲
                    outlineContainer.removeAllViews()
                    analysis.outline.forEach { item ->
                        val outlineCard = LinearLayout(activity).apply {
                            orientation = LinearLayout.VERTICAL
                            setBackgroundResource(R.drawable.bg_dark_chip)
                            setPadding(dp(12), dp(10), dp(12), dp(10))
                            val lp = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                            ).apply { bottomMargin = dp(8) }
                            layoutParams = lp

                            val topRow = LinearLayout(activity).apply {
                                orientation = LinearLayout.HORIZONTAL
                                val phaseView = TextView(activity).apply {
                                    text = item.phase
                                    textSize = 11f
                                    setTextColor(activity.getColor(R.color.readtrace_ink))
                                    setBackgroundResource(R.drawable.bg_status_chip)
                                    setPadding(dp(8), dp(2), dp(8), dp(2))
                                }
                                val titleView = TextView(activity).apply {
                                    text = item.title
                                    textSize = 13f
                                    setTextColor(activity.getColor(R.color.readtrace_ink))
                                    setTypeface(null, android.graphics.Typeface.BOLD)
                                    setPadding(dp(8), 0, 0, 0)
                                }
                                addView(phaseView)
                                addView(titleView)
                            }
                            val summaryView = TextView(activity).apply {
                                text = item.summary
                                textSize = 12f
                                setTextColor(activity.getColor(R.color.readtrace_muted))
                                setLineSpacing(0f, 1.2f)
                                setPadding(0, dp(6), 0, 0)
                            }
                            addView(topRow)
                            addView(summaryView)
                        }
                        outlineContainer.addView(outlineCard)
                    }
                }
            }
        }

        btnConfig.setOnClickListener {
            HapticFeedbackEngine.lightClick(activity)
            showConfigDialog(activity) {
                runAnalysis()
            }
        }

        btnFillDesc.setOnClickListener {
            val a = currentAnalysis ?: return@setOnClickListener
            HapticFeedbackEngine.stampImpact(activity)
            val fullText = buildFormattedText(a)
            val db = BookDatabaseHelper.getInstance(activity)
            val updated = book.copy(description = fullText)
            db.updateBook(updated)
            onBookUpdated()
            Toast.makeText(activity, "✨ 角色与大纲已成功填入作品简介", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        btnSaveNote.setOnClickListener {
            val a = currentAnalysis ?: return@setOnClickListener
            HapticFeedbackEngine.cartridgeSnap(activity)
            val fullText = buildFormattedText(a)
            val db = BookDatabaseHelper.getInstance(activity)

            // 1. 存为深度笔记
            val note = Note(
                bookId = book.id,
                content = fullText,
                noteType = NoteType.NOTE,
                chapter = "🤖 AI 角色与大纲",
            )
            db.insertNote(note)

            // 2. 自动同步填充至人物角色谱
            a.characters.forEach { c ->
                db.insertCharacter(
                    BookCharacter(
                        bookId = book.id,
                        name = c.name,
                        roleTitle = c.identity,
                        description = c.description,
                        avatarEmoji = "🎭",
                    )
                )
            }

            // 3. 自动同步填充至章节大纲
            a.outline.forEachIndexed { index, o ->
                db.insertOutline(
                    BookOutline(
                        bookId = book.id,
                        chapterOrder = index + 1,
                        title = o.title,
                        summary = o.summary,
                        keyTakeaways = o.phase,
                    )
                )
            }

            onBookUpdated()
            Toast.makeText(activity, "📝 角色谱与分幕大纲已全量入库沉淀", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        runAnalysis()
        dialog.show()
    }

    private fun buildFormattedText(a: AiAssistantEngine.AiStoryAnalysis): String {
        val sb = StringBuilder()
        sb.appendLine("【核心主旨与背景】")
        sb.appendLine(a.premise)
        sb.appendLine()
        sb.appendLine("【🎭 核心人物角色表】")
        a.characters.forEach {
            sb.appendLine("• ${it.name}（${it.identity}）：${it.description}")
        }
        sb.appendLine()
        sb.appendLine("【📖 故事分幕与大纲】")
        a.outline.forEach {
            sb.appendLine("• [${it.phase}] ${it.title}：${it.summary}")
        }
        return sb.toString().trim()
    }

    private fun showConfigDialog(activity: Activity, onSaved: () -> Unit) {
        val density = activity.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(10))
        }

        val tvKeyLabel = TextView(activity).apply {
            text = "API Key (支持 B.AI / DeepSeek / OpenAI / Kimi 等):"
            textSize = 13f
            setTextColor(activity.getColor(R.color.readtrace_ink))
        }
        val etKey = EditText(activity).apply {
            hint = "sk-..."
            setText(UserPreferencesManager.getAiApiKey(activity))
            textSize = 13f
            setBackgroundResource(R.drawable.bg_input_glass)
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }

        val tvUrlLabel = TextView(activity).apply {
            text = "Base URL (默认 B.AI 聚合网关，OpenAI 兼容):"
            textSize = 13f
            setTextColor(activity.getColor(R.color.readtrace_ink))
            setPadding(0, dp(12), 0, 0)
        }
        val etUrl = EditText(activity).apply {
            hint = UserPreferencesManager.DEFAULT_AI_BASE_URL
            setText(UserPreferencesManager.getAiBaseUrl(activity))
            textSize = 13f
            setBackgroundResource(R.drawable.bg_input_glass)
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }

        val tvModelLabel = TextView(activity).apply {
            text = "Model (如 glm-5.3-flash / deepseek-chat):"
            textSize = 13f
            setTextColor(activity.getColor(R.color.readtrace_ink))
            setPadding(0, dp(12), 0, 0)
        }
        val etModel = EditText(activity).apply {
            hint = UserPreferencesManager.DEFAULT_AI_MODEL
            setText(UserPreferencesManager.getAiModel(activity))
            textSize = 13f
            setBackgroundResource(R.drawable.bg_input_glass)
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }

        // Key 只落在本机私有 SharedPreferences，不写入任何代码与仓库，需向用户明示
        val tvTip = TextView(activity).apply {
            text = "密钥仅保存在本机，不随备份上传；推理型模型生成较慢，已改用流式请求以避免中途断连。"
            textSize = 11f
            setTextColor(activity.getColor(R.color.readtrace_muted))
            setLineSpacing(0f, 1.3f)
            setPadding(0, dp(12), 0, 0)
        }

        layout.addView(tvKeyLabel)
        layout.addView(etKey)
        layout.addView(tvUrlLabel)
        layout.addView(etUrl)
        layout.addView(tvModelLabel)
        layout.addView(etModel)
        layout.addView(tvTip)

        AlertDialog.Builder(activity)
            .setTitle("⚙️ AI 助手模型配置")
            .setView(layout)
            .setPositiveButton("保存") { _, _ ->
                UserPreferencesManager.setAiApiKey(activity, etKey.text.toString())
                UserPreferencesManager.setAiBaseUrl(activity, etUrl.text.toString())
                UserPreferencesManager.setAiModel(activity, etModel.text.toString())
                Toast.makeText(activity, "AI 配置已保存", Toast.LENGTH_SHORT).show()
                onSaved()
            }
            .setNegativeButton("取消", null)
            .show()
    }
}
