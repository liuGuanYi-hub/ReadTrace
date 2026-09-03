package com.example.readtrace.util

import android.content.Context
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.readtrace.R
import com.example.readtrace.model.CuratorAccount

/**
 * 🎨 艺术头像选择卡片构建器 (AvatarChipBuilder)
 *
 * 档案定制页与认证页共用的头像选择流实现，替代原先逐行重复的 setupAvatarSelector()。
 * 卡片几何规格在此唯一维护：
 * - 宽 64dp、高固定 76dp，emoji 行固定 30dp 并关闭字体 padding，
 *   使不同 emoji 的字形度量差异（如 U+FE0F 变体选择符）不再导致卡片顶底边错位；
 * - 选中/未选中态统一使用同几何规格的 bg_avatar_chip_selected / bg_avatar_chip_normal，
 *   切换仅变色不改尺寸。
 */
object AvatarChipBuilder {

    private const val CARD_WIDTH_DP = 64
    private const val CARD_HEIGHT_DP = 76
    private const val EMOJI_ROW_HEIGHT_DP = 30
    private const val CARD_PADDING_DP = 8
    private const val CARD_SPACING_DP = 8
    private const val NAME_TOP_MARGIN_DP = 3

    fun setup(
        context: Context,
        container: LinearLayout,
        selectedKey: String,
        onSelected: (String) -> Unit,
    ) {
        container.removeAllViews()
        val density = context.resources.displayMetrics.density
        fun dp(v: Int): Int = (v * density).toInt()

        CuratorAccount.PRESET_AVATARS.forEachIndexed { index, avatar ->
            val isSelected = avatar.key == selectedKey
            val itemView = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                val pad = dp(CARD_PADDING_DP)
                setPadding(pad, pad, pad, pad)
                layoutParams = LinearLayout.LayoutParams(dp(CARD_WIDTH_DP), dp(CARD_HEIGHT_DP)).apply {
                    if (index > 0) marginStart = dp(CARD_SPACING_DP)
                }
                isClickable = true
                isFocusable = true
                setBackgroundResource(
                    if (isSelected) R.drawable.bg_avatar_chip_selected else R.drawable.bg_avatar_chip_normal
                )
                setOnClickListener { onSelected(avatar.key) }
            }

            val tvEmoji = TextView(context).apply {
                text = avatar.emoji
                textSize = 22f
                gravity = Gravity.CENTER
                includeFontPadding = false
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(EMOJI_ROW_HEIGHT_DP),
                )
            }
            val tvName = TextView(context).apply {
                text = avatar.name
                textSize = 10f
                setTextColor(
                    ContextCompat.getColor(
                        context,
                        if (isSelected) R.color.white else R.color.readtrace_muted,
                    )
                )
                gravity = Gravity.CENTER
                setPadding(0, dp(NAME_TOP_MARGIN_DP), 0, 0)
            }
            itemView.addView(tvEmoji)
            itemView.addView(tvName)
            container.addView(itemView)
        }
    }
}
