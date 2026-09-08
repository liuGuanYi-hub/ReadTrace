package com.example.readtrace.ui

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.readtrace.R
import com.example.readtrace.util.CuratedShelf

/**
 * 精选策展画廊横滑适配器（P39 Phase 1 视觉核心）。
 */
class CuratedShelfAdapter(
    private val shelves: List<CuratedShelf>,
    private val onShelfClick: (CuratedShelf, Boolean) -> Unit, // shelf, isSelected
) : RecyclerView.Adapter<CuratedShelfAdapter.ShelfViewHolder>() {

    private var selectedShelfId: String? = null

    fun getSelectedShelf(): CuratedShelf? = shelves.find { it.id == selectedShelfId }

    fun setSelectedShelf(shelfId: String?) {
        val oldPos = shelves.indexOfFirst { it.id == selectedShelfId }
        val newPos = shelves.indexOfFirst { it.id == shelfId }
        selectedShelfId = shelfId
        if (oldPos != -1) notifyItemChanged(oldPos)
        if (newPos != -1) notifyItemChanged(newPos)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ShelfViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_curated_shelf_card, parent, false)
        return ShelfViewHolder(view)
    }

    override fun onBindViewHolder(holder: ShelfViewHolder, position: Int) {
        holder.bind(shelves[position])
    }

    override fun getItemCount(): Int = shelves.size

    inner class ShelfViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val container: View = itemView.findViewById(R.id.shelfCardContainer)
        private val badge: TextView = itemView.findViewById(R.id.shelfCardBadge)
        private val title: TextView = itemView.findViewById(R.id.shelfCardTitle)
        private val subtitle: TextView = itemView.findViewById(R.id.shelfCardSubtitle)
        private val activeDot: View = itemView.findViewById(R.id.shelfCardActiveDot)

        fun bind(shelf: CuratedShelf) {
            val isSelected = shelf.id == selectedShelfId

            badge.text = shelf.badge
            title.text = shelf.title
            subtitle.text = shelf.subtitle

            // 策展级动态渐变卡片与高质感光晕描边
            val density = itemView.context.resources.displayMetrics.density
            val gradient = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(shelf.gradientStartColor, shelf.gradientEndColor),
            ).apply {
                cornerRadius = density * 14f
                if (isSelected) {
                    setStroke((density * 2.0f).toInt(), shelf.accentColor)
                } else {
                    setStroke((density * 0.8f).toInt(), 0x33FFFFFF)
                }
            }
            container.background = gradient

            activeDot.visibility = if (isSelected) View.VISIBLE else View.GONE
            title.setTextColor(if (isSelected) 0xFFFFFFFF.toInt() else 0xFFE0E0E0.toInt())
            badge.setTextColor(shelf.accentColor)

            container.setOnClickListener {
                val toggledSelected = selectedShelfId != shelf.id
                val newId = if (toggledSelected) shelf.id else null
                setSelectedShelf(newId)
                onShelfClick(shelf, toggledSelected)
            }
        }
    }
}
