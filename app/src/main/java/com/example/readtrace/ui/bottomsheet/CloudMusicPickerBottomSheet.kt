package com.example.readtrace.ui.bottomsheet

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.readtrace.R
import com.example.readtrace.util.HapticFeedbackEngine
import com.example.readtrace.util.ViewAnimationHelper
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * ☁️ 网易云音乐选择底板
 *
 * 替换系统 AlertDialog.setItems，统一用于：
 * - 我的歌单列表
 * - 歌单内曲目列表
 *
 * 设计：羊皮纸暖色圆角底板 + 唱片图标 + 淡金分隔，贴合黑胶唱机页氛围。
 */
class CloudMusicPickerBottomSheet : BottomSheetDialogFragment() {

    data class PickerItem(
        val id: Long = -1,
        val title: String,
        val subtitle: String? = null,
        val emoji: String = "🎵",
    )

    private var pickerTitle: String = ""
    private var pickerCountHint: String = ""
    private var items: List<PickerItem> = emptyList()
    private var onItemSelectedListener: ((Int) -> Unit)? = null

    override fun getTheme(): Int = R.style.Theme_ReadTrace_BottomSheetDialog

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? = inflater.inflate(R.layout.dialog_cloud_music_picker_bottom_sheet, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val txtTitle = view.findViewById<TextView>(R.id.txtCloudMusicPickerTitle)
        val txtCount = view.findViewById<TextView>(R.id.txtCloudMusicPickerCount)
        val btnClose = view.findViewById<TextView>(R.id.btnCloudMusicPickerClose)
        val layoutEmpty = view.findViewById<View>(R.id.layoutCloudMusicPickerEmpty)
        val rvList = view.findViewById<RecyclerView>(R.id.rvCloudMusicPicker)

        txtTitle.text = pickerTitle
        txtCount.text = if (pickerCountHint.isNotBlank()) pickerCountHint else "共 ${items.size} 项"

        btnClose.setOnClickListener { dismiss() }
        ViewAnimationHelper.attachSpringTouch(btnClose)

        val adapter = CloudMusicAdapter(items) { position ->
            HapticFeedbackEngine.lightClick(requireContext())
            onItemSelectedListener?.invoke(position)
            dismiss()
        }
        rvList.layoutManager = LinearLayoutManager(requireContext())
        rvList.adapter = adapter

        if (items.isEmpty()) {
            layoutEmpty.visibility = View.VISIBLE
            rvList.visibility = View.GONE
        } else {
            layoutEmpty.visibility = View.GONE
            rvList.visibility = View.VISIBLE
        }

        listOf(
            view.findViewById<View>(R.id.cloudMusicPickerRoot),
        ).forEachIndexed { index, block ->
            ViewAnimationHelper.staggerFadeIn(block, index, baseDelay = 40L, duration = 320L)
        }
    }

    companion object {
        private const val TAG = "CloudMusicPickerBottomSheet"

        fun show(
            fragmentManager: FragmentManager,
            title: String,
            items: List<PickerItem>,
            countHint: String = "",
            onSelected: (Int) -> Unit,
        ): CloudMusicPickerBottomSheet {
            return CloudMusicPickerBottomSheet().apply {
                this.pickerTitle = title
                this.items = items
                this.pickerCountHint = countHint
                this.onItemSelectedListener = onSelected
            }.also { it.show(fragmentManager, TAG) }
        }
    }

    private class CloudMusicAdapter(
        private val items: List<PickerItem>,
        private val onItemClicked: (Int) -> Unit,
    ) : RecyclerView.Adapter<CloudMusicAdapter.ItemViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_cloud_music_picker, parent, false)
            return ItemViewHolder(view)
        }

        override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
            holder.bind(items[position])
            ViewAnimationHelper.staggerFadeIn(holder.itemView, position, baseDelay = 20L, duration = 260L)
        }

        override fun getItemCount(): Int = items.size

        inner class ItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val icon = itemView.findViewById<TextView>(R.id.itemCloudMusicIcon)
            private val title = itemView.findViewById<TextView>(R.id.itemCloudMusicTitle)
            private val subtitle = itemView.findViewById<TextView>(R.id.itemCloudMusicSubtitle)

            init {
                ViewAnimationHelper.attachSpringTouch(itemView)
                itemView.setOnClickListener {
                    val pos = adapterPosition
                    if (pos in items.indices) onItemClicked(pos)
                }
            }

            fun bind(item: PickerItem) {
                icon.text = item.emoji
                title.text = item.title
                subtitle.text = item.subtitle ?: ""
                subtitle.visibility = if (item.subtitle.isNullOrBlank()) View.GONE else View.VISIBLE
            }
        }
    }
}
