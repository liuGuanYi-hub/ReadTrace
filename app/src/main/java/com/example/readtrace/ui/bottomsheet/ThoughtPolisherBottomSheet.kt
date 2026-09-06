package com.example.readtrace.ui.bottomsheet

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.example.readtrace.ExLibrisStudioActivity
import com.example.readtrace.QuotePosterActivity
import com.example.readtrace.R
import com.example.readtrace.model.MediaType
import com.example.readtrace.util.HapticFeedbackEngine
import com.example.readtrace.util.ThoughtPolisherEngine
import com.example.readtrace.util.ViewAnimationHelper
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog

/**
 * 🖋️ P25 文心雕龙：AI 读后感大师润色与金句提炼工坊弹窗 (ThoughtPolisherBottomSheet)
 */
object ThoughtPolisherBottomSheet {

    fun show(
        activity: Activity,
        bookTitle: String,
        author: String? = null,
        mediaType: MediaType = MediaType.BOOK,
        bookCoverUrl: String? = null,
        bookId: Long = 0L,
        currentDraft: String = "",
        onApplied: (polishedText: String, isAppend: Boolean) -> Unit,
    ) {
        val dialog = BottomSheetDialog(activity, R.style.Theme_ReadTrace_BottomSheetDialog)
        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_thought_polisher_bottom_sheet, null)
        dialog.setContentView(view)

        dialog.behavior.apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
        }

        val txtTitle = view.findViewById<TextView>(R.id.txtPolisherTitle)
        val txtSubtitle = view.findViewById<TextView>(R.id.txtPolisherSubtitle)
        val txtSourceTag = view.findViewById<TextView>(R.id.txtPolisherSourceTag)
        val btnClose = view.findViewById<View>(R.id.btnPolisherClose)

        val chipPhilosophical = view.findViewById<TextView>(R.id.chipStylePhilosophical)
        val chipCritical = view.findViewById<TextView>(R.id.chipStyleCritical)
        val chipIntimate = view.findViewById<TextView>(R.id.chipStyleIntimate)

        val rawContainer = view.findViewById<View>(R.id.rawThoughtContainer)
        val txtRawContent = view.findViewById<TextView>(R.id.txtRawThoughtContent)

        val txtCurrentStyleLabel = view.findViewById<TextView>(R.id.txtCurrentStyleLabel)
        val loadingSpinner = view.findViewById<ProgressBar>(R.id.polisherLoadingSpinner)
        val txtPolishedText = view.findViewById<TextView>(R.id.txtPolishedThoughtText)

        val txtGoldenQuote = view.findViewById<TextView>(R.id.txtGoldenQuote)
        val btnCopyQuote = view.findViewById<TextView>(R.id.btnCopyQuote)
        val btnMakePoster = view.findViewById<View>(R.id.btnMakeQuotePoster)
        val btnMakeExLibris = view.findViewById<View>(R.id.btnMakeExLibris)

        val btnApplyReplace = view.findViewById<Button>(R.id.btnPolisherApplyReplace)
        val btnApplyAppend = view.findViewById<Button>(R.id.btnPolisherApplyAppend)

        txtSubtitle.text = "《$bookTitle》· 大师文风重塑与灵魂金句提炼"

        val rawTrimmed = currentDraft.trim()
        if (rawTrimmed.isNotBlank()) {
            rawContainer.visibility = View.VISIBLE
            txtRawContent.text = rawTrimmed
        } else {
            rawContainer.visibility = View.GONE
        }

        var currentStyle = ThoughtPolisherEngine.PolishingStyle.PHILOSOPHICAL
        var currentResult: ThoughtPolisherEngine.PolishedThought? = null

        fun updateChipsUI() {
            val chips = listOf(
                chipPhilosophical to ThoughtPolisherEngine.PolishingStyle.PHILOSOPHICAL,
                chipCritical to ThoughtPolisherEngine.PolishingStyle.CRITICAL,
                chipIntimate to ThoughtPolisherEngine.PolishingStyle.INTIMATE,
            )
            for ((chip, style) in chips) {
                val isSelected = style == currentStyle
                chip.setBackgroundResource(
                    if (isSelected) R.drawable.bg_chip_picker_selected else R.drawable.bg_chip_picker_idle
                )
                chip.setTextColor(
                    activity.getColor(if (isSelected) R.color.chip_selected_text else R.color.chip_idle_text)
                )
            }
        }

        fun executePolish() {
            loadingSpinner.visibility = View.VISIBLE
            txtCurrentStyleLabel.text = "✦ ${currentStyle.displayName} 润色中..."
            btnApplyReplace.isEnabled = false
            btnApplyAppend.isEnabled = false

            ThoughtPolisherEngine.polish(
                context = activity,
                rawThought = rawTrimmed,
                bookTitle = bookTitle,
                author = author,
                mediaType = mediaType,
                style = currentStyle,
            ) { result ->
                activity.runOnUiThread {
                    if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                    currentResult = result
                    loadingSpinner.visibility = View.GONE
                    txtCurrentStyleLabel.text = "✦ ${result.style.displayName} 润色成果"
                    txtPolishedText.text = result.polishedText
                    txtGoldenQuote.text = result.goldenQuote
                    txtSourceTag.text = if (result.isFromOffline) "✦ 离线经典推导" else "✦ AI 实时深度润色"
                    btnApplyReplace.isEnabled = true
                    btnApplyAppend.isEnabled = true
                    HapticFeedbackEngine.lightClick(activity)
                }
            }
        }

        chipPhilosophical.setOnClickListener {
            if (currentStyle != ThoughtPolisherEngine.PolishingStyle.PHILOSOPHICAL) {
                currentStyle = ThoughtPolisherEngine.PolishingStyle.PHILOSOPHICAL
                updateChipsUI()
                executePolish()
            }
        }

        chipCritical.setOnClickListener {
            if (currentStyle != ThoughtPolisherEngine.PolishingStyle.CRITICAL) {
                currentStyle = ThoughtPolisherEngine.PolishingStyle.CRITICAL
                updateChipsUI()
                executePolish()
            }
        }

        chipIntimate.setOnClickListener {
            if (currentStyle != ThoughtPolisherEngine.PolishingStyle.INTIMATE) {
                currentStyle = ThoughtPolisherEngine.PolishingStyle.INTIMATE
                updateChipsUI()
                executePolish()
            }
        }

        btnCopyQuote.setOnClickListener {
            val quote = currentResult?.goldenQuote ?: txtGoldenQuote.text.toString()
            if (quote.isNotBlank()) {
                val cm = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("GoldenQuote", quote))
                HapticFeedbackEngine.lightClick(activity)
                Toast.makeText(activity, "✨ 已复制金句：$quote", Toast.LENGTH_SHORT).show()
            }
        }

        btnMakePoster.setOnClickListener {
            val quote = currentResult?.goldenQuote ?: txtGoldenQuote.text.toString()
            val posterIntent = QuotePosterActivity.createIntent(
                context = activity,
                bookId = bookId,
                bookTitle = bookTitle,
                bookAuthor = author,
                bookCover = bookCoverUrl,
                quoteContent = quote.ifBlank { "字句如灯，照见宿命浮沉" },
                quoteSource = author,
            )
            HapticFeedbackEngine.stampImpact(activity)
            activity.startActivity(posterIntent)
        }

        btnMakeExLibris.setOnClickListener {
            val exLibrisIntent = Intent(activity, ExLibrisStudioActivity::class.java).apply {
                putExtra("extra_book_id", bookId)
                putExtra("extra_custom_quote", currentResult?.goldenQuote ?: txtGoldenQuote.text.toString())
            }
            HapticFeedbackEngine.stampImpact(activity)
            activity.startActivity(exLibrisIntent)
        }

        btnApplyReplace.setOnClickListener {
            currentResult?.let { res ->
                HapticFeedbackEngine.stampImpact(activity)
                onApplied(res.polishedText, false)
                Toast.makeText(activity, "✓ 已采纳并替换读后感", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
        }

        btnApplyAppend.setOnClickListener {
            currentResult?.let { res ->
                HapticFeedbackEngine.stampImpact(activity)
                onApplied(res.polishedText, true)
                Toast.makeText(activity, "✓ 已将润色成果追加至文末", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
        }

        btnClose.setOnClickListener {
            HapticFeedbackEngine.lightClick(activity)
            dialog.dismiss()
        }

        // 挂载物理弹簧触摸反馈
        ViewAnimationHelper.attachSpringTouch(btnClose)
        ViewAnimationHelper.attachSpringTouch(btnApplyReplace)
        ViewAnimationHelper.attachSpringTouch(btnApplyAppend)
        ViewAnimationHelper.attachSpringTouch(btnMakePoster)
        ViewAnimationHelper.attachSpringTouch(btnMakeExLibris)

        updateChipsUI()
        executePolish()
        dialog.show()
    }
}
