package com.example.readtrace.ui

import android.app.Activity
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import com.example.readtrace.R
import com.example.readtrace.model.MediaType
import com.example.readtrace.util.DimensionalScoringEngine
import com.example.readtrace.util.HapticFeedbackEngine
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 📐 多维度微评分与 0.1 游标工坊底板 (DimensionalScoringBottomSheet)
 */
object DimensionalScoringBottomSheet {

    fun show(
        activity: Activity,
        workTitle: String,
        mediaType: MediaType,
        currentScore: Double?,
        onScoreConfirmed: (Double) -> Unit,
    ) {
        val dialog = BottomSheetDialog(activity)
        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_dimensional_scoring_bottom_sheet, null)
        dialog.setContentView(view)

        dialog.behavior.apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
        }

        val tvSubtitle = view.findViewById<TextView>(R.id.txtScoringSubtitle)
        val tvTotalScore = view.findViewById<TextView>(R.id.txtLiveTotalScore)
        val tvTierLabel = view.findViewById<TextView>(R.id.txtLiveTierLabel)
        val dimensionsContainer = view.findViewById<LinearLayout>(R.id.scoringDimensionsContainer)
        val seekFineTune = view.findViewById<SeekBar>(R.id.seekFineTune)
        val btnMinusTenth = view.findViewById<TextView>(R.id.btnMinusTenth)
        val btnPlusTenth = view.findViewById<TextView>(R.id.btnPlusTenth)
        val btnConfirm = view.findViewById<TextView>(R.id.btnConfirmScore)

        tvSubtitle.text = "《$workTitle》· 4 大维度加权计算 10.0 高精评分"

        val initial = (currentScore ?: 8.0).coerceIn(0.0, 10.0)
        val dimensions = DimensionalScoringEngine.getDimensionsForMediaType(mediaType, initial)
        var currentTotalScore = (initial * 10.0).roundToInt() / 10.0

        val density = activity.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        fun refreshScoreDisplay(score: Double, updateFineTuneBar: Boolean = true) {
            currentTotalScore = (score * 10.0).roundToInt() / 10.0
            tvTotalScore.text = String.format(Locale.getDefault(), "%.1f", currentTotalScore)
            tvTierLabel.text = DimensionalScoringEngine.getTierLabel(currentTotalScore)
            btnConfirm.text = "✓ 确认微评分 (${String.format(Locale.getDefault(), "%.1f", currentTotalScore)} 分)"

            if (updateFineTuneBar) {
                seekFineTune.progress = (currentTotalScore * 10).toInt().coerceIn(0, 100)
            }
        }

        // 渲染 4 个维度的打分条
        dimensionsContainer.removeAllViews()
        dimensions.forEach { dim ->
            val dimLayout = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dp(6), 0, dp(6))

                val headerRow = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    val nameTv = TextView(activity).apply {
                        text = "${dim.name} (${(dim.weight * 100).toInt()}%)"
                        textSize = 13f
                        setTextColor(activity.getColor(R.color.readtrace_ink))
                        setTypeface(null, android.graphics.Typeface.BOLD)
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    }
                    val valTv = TextView(activity).apply {
                        id = android.view.View.generateViewId()
                        text = "${String.format(Locale.getDefault(), "%.1f", dim.score)} 分"
                        textSize = 13f
                        setTextColor(activity.getColor(R.color.readtrace_accent))
                        setTypeface(null, android.graphics.Typeface.BOLD)
                    }
                    addView(nameTv)
                    addView(valTv)
                }

                val seekBar = SeekBar(activity).apply {
                    max = 100
                    progress = (dim.score * 10).toInt().coerceIn(0, 100)
                    setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(sb: SeekBar?, prog: Int, fromUser: Boolean) {
                            if (fromUser) {
                                dim.score = prog / 10.0
                                (headerRow.getChildAt(1) as? TextView)?.text = "${String.format(Locale.getDefault(), "%.1f", dim.score)} 分"
                                val newTotal = DimensionalScoringEngine.calculateWeightedScore(dimensions)
                                refreshScoreDisplay(newTotal, updateFineTuneBar = true)
                            }
                        }

                        override fun onStartTrackingTouch(sb: SeekBar?) {}
                        override fun onStopTrackingTouch(sb: SeekBar?) {
                            HapticFeedbackEngine.lightClick(activity)
                        }
                    })
                }

                addView(headerRow)
                addView(seekBar)
            }
            dimensionsContainer.addView(dimLayout)
        }

        // 全局游标滑杆监听
        seekFineTune.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, prog: Int, fromUser: Boolean) {
                if (fromUser) {
                    val score = prog / 10.0
                    refreshScoreDisplay(score, updateFineTuneBar = false)
                }
            }

            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {
                HapticFeedbackEngine.lightClick(activity)
            }
        })

        btnMinusTenth.setOnClickListener {
            HapticFeedbackEngine.needleDropCrackle(activity)
            val next = (currentTotalScore - 0.1).coerceIn(0.0, 10.0)
            refreshScoreDisplay(next, updateFineTuneBar = true)
        }

        btnPlusTenth.setOnClickListener {
            HapticFeedbackEngine.needleDropCrackle(activity)
            val next = (currentTotalScore + 0.1).coerceIn(0.0, 10.0)
            refreshScoreDisplay(next, updateFineTuneBar = true)
        }

        btnConfirm.setOnClickListener {
            HapticFeedbackEngine.stampImpact(activity)
            onScoreConfirmed(currentTotalScore)
            dialog.dismiss()
        }

        refreshScoreDisplay(initial, updateFineTuneBar = true)
        dialog.show()
    }
}
