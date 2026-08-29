package com.example.readtrace.ui.bottomsheet

import android.graphics.Color
import android.os.Bundle
import android.view.animation.OvershootInterpolator
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.readtrace.R
import com.example.readtrace.model.Book
import com.example.readtrace.model.MediaType
import com.example.readtrace.util.CoverImageHelper
import com.example.readtrace.util.HapticFeedbackEngine
import com.example.readtrace.util.ViewAnimationHelper
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * 🎨 先锋艺术作品选择底板 (Curatorial Work Picker Bottom Sheet)
 * 
 * 核心特性：
 * 1. 深邃暗夜黑曜毛玻璃质感与圆润圆角容器；
 * 2. 实时动态搜索与媒介分类快捷胶囊；
 * 3. 高清封面渲染、分类/评分徽章与高亮选中态；
 * 4. 物理弹簧触控与清脆马达振动反馈。
 */
class WorkPickerBottomSheet : BottomSheetDialogFragment() {

    private var pickerTitle: String = "✨ 选择作品"
    private var allWorks: List<Book> = emptyList()
    private var selectedWorkId: Long? = null
    private var onWorkSelectedListener: ((Book) -> Unit)? = null

    private var currentFilterMediaType: MediaType? = null
    private var currentSearchQuery: String = ""

    private lateinit var adapter: WorkPickerAdapter
    private lateinit var txtCount: TextView
    private lateinit var layoutEmpty: View
    private lateinit var rvList: RecyclerView

    override fun getTheme(): Int = R.style.Theme_ReadTrace_BottomSheetDialog

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.dialog_work_picker_bottom_sheet, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val txtTitle = view.findViewById<TextView>(R.id.txtWorkPickerTitle)
        txtCount = view.findViewById(R.id.txtWorkPickerCount)
        val btnClose = view.findViewById<TextView>(R.id.btnWorkPickerClose)
        val etSearch = view.findViewById<EditText>(R.id.etWorkPickerSearch)
        val btnClearSearch = view.findViewById<TextView>(R.id.btnWorkPickerClearSearch)
        val scrollMediaTabs = view.findViewById<View>(R.id.scrollWorkPickerMediaTabs)
        layoutEmpty = view.findViewById(R.id.layoutWorkPickerEmpty)
        rvList = view.findViewById(R.id.rvWorkPickerList)

        txtTitle.text = pickerTitle
        btnClose.setOnClickListener { dismiss() }
        ViewAnimationHelper.attachSpringTouch(btnClose)

        // 判断是否有多种媒介类型
        val distinctMedia = allWorks.map { it.mediaType }.distinct()
        if (distinctMedia.size <= 1) {
            scrollMediaTabs.visibility = View.GONE
        } else {
            scrollMediaTabs.visibility = View.VISIBLE
            setupMediaFilterChips(view)
        }

        // 初始化列表
        adapter = WorkPickerAdapter(
            selectedId = selectedWorkId,
            onItemClicked = { book ->
                HapticFeedbackEngine.cartridgeSnap(requireContext())
                onWorkSelectedListener?.invoke(book)
                dismiss()
            },
        )
        rvList.layoutManager = LinearLayoutManager(requireContext())
        rvList.adapter = adapter

        // 搜索监听
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentSearchQuery = s?.toString()?.trim().orEmpty()
                btnClearSearch.visibility = if (currentSearchQuery.isNotEmpty()) View.VISIBLE else View.GONE
                applyFilter()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnClearSearch.setOnClickListener {
            etSearch.setText("")
        }

        applyFilter()

        // 内容区块依次渐入上浮（handle → header → search → chips → list）
        listOf(
            view.findViewById<View>(R.id.pickerHeaderBlock),
            view.findViewById<View>(R.id.pickerSearchBlock),
            view.findViewById<View>(R.id.scrollWorkPickerMediaTabs),
            view.findViewById<View>(R.id.pickerListBlock),
        ).forEachIndexed { index, block ->
            ViewAnimationHelper.staggerFadeIn(block, index, baseDelay = 60L, duration = 360L)
        }
    }

    private fun setupMediaFilterChips(root: View) {
        val chips = listOf(
            root.findViewById<TextView>(R.id.chipMediaAll) to null,
            root.findViewById<TextView>(R.id.chipMediaBook) to MediaType.BOOK,
            root.findViewById<TextView>(R.id.chipMediaAnime) to MediaType.ANIME,
            root.findViewById<TextView>(R.id.chipMediaMovie) to MediaType.MOVIE,
            root.findViewById<TextView>(R.id.chipMediaGame) to MediaType.GAME,
            root.findViewById<TextView>(R.id.chipMediaMusic) to MediaType.MUSIC,
        )

        chips.forEach { (chip, media) ->
            chip.setOnClickListener {
                currentFilterMediaType = media
                chips.forEach { (c, m) ->
                    val isSelected = m == currentFilterMediaType
                    c.setBackgroundResource(if (isSelected) R.drawable.bg_chip_picker_selected else R.drawable.bg_chip_picker_idle)
                    c.setTextColor(ContextCompat.getColor(requireContext(), if (isSelected) R.color.chip_selected_text else R.color.chip_idle_text))
                    c.paint.isFakeBoldText = isSelected
                }
                HapticFeedbackEngine.lightClick(requireContext())
                applyFilter()
            }
            ViewAnimationHelper.attachSpringTouch(chip)
        }
    }

    private fun applyFilter() {
        val filtered = allWorks.filter { book ->
            val matchesMedia = currentFilterMediaType == null || book.mediaType == currentFilterMediaType
            val matchesQuery = if (currentSearchQuery.isEmpty()) true else {
                book.title.contains(currentSearchQuery, ignoreCase = true) ||
                    (book.author?.contains(currentSearchQuery, ignoreCase = true) == true) ||
                    (book.category?.contains(currentSearchQuery, ignoreCase = true) == true) ||
                    book.tags.any { it.contains(currentSearchQuery, ignoreCase = true) }
            }
            matchesMedia && matchesQuery
        }

        txtCount.text = "书库中匹配 ${filtered.size} 款作品 · 点击即刻切换"
        adapter.submitList(filtered)

        if (filtered.isEmpty()) {
            layoutEmpty.visibility = View.VISIBLE
            rvList.visibility = View.GONE
        } else {
            layoutEmpty.visibility = View.GONE
            rvList.visibility = View.VISIBLE
        }
    }

    companion object {
        private const val TAG = "WorkPickerBottomSheet"

        fun show(
            fragmentManager: FragmentManager,
            title: String,
            works: List<Book>,
            selectedWorkId: Long? = null,
            onSelected: (Book) -> Unit,
        ): WorkPickerBottomSheet {
            val sheet = WorkPickerBottomSheet().apply {
                this.pickerTitle = title
                this.allWorks = works
                this.selectedWorkId = selectedWorkId
                this.onWorkSelectedListener = onSelected
            }
            sheet.show(fragmentManager, TAG)
            return sheet
        }
    }

    /**
     * 选片列表适配器
     */
    private class WorkPickerAdapter(
        private val selectedId: Long?,
        private val onItemClicked: (Book) -> Unit,
    ) : RecyclerView.Adapter<WorkPickerAdapter.WorkViewHolder>() {

        private val items = mutableListOf<Book>()

        fun submitList(newItems: List<Book>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WorkViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_work_picker_card, parent, false)
            return WorkViewHolder(view)
        }

        override fun onBindViewHolder(holder: WorkViewHolder, position: Int) {
            val book = items[position]
            holder.bind(book, book.id == selectedId)
            ViewAnimationHelper.staggerFadeIn(holder.itemView, position, baseDelay = 25L, duration = 300L)
        }

        override fun getItemCount(): Int = items.size

        inner class WorkViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val container = itemView.findViewById<View>(R.id.itemWorkContainer)
            private val imgCover = itemView.findViewById<ImageView>(R.id.itemWorkCover)
            private val layoutPlaceholder = itemView.findViewById<FrameLayout>(R.id.itemWorkPlaceholder)
            private val txtPlaceholderEmoji = itemView.findViewById<TextView>(R.id.itemWorkPlaceholderEmoji)
            private val txtMediaBadge = itemView.findViewById<TextView>(R.id.itemWorkMediaBadge)
            private val txtTitle = itemView.findViewById<TextView>(R.id.itemWorkTitle)
            private val txtAuthor = itemView.findViewById<TextView>(R.id.itemWorkAuthor)
            private val txtCategory = itemView.findViewById<TextView>(R.id.itemWorkCategory)
            private val txtScore = itemView.findViewById<TextView>(R.id.itemWorkScore)
            private val txtStatus = itemView.findViewById<TextView>(R.id.itemWorkStatus)
            private val badgeSelected = itemView.findViewById<View>(R.id.itemWorkSelectedBadge)
            private val ringUnselected = itemView.findViewById<View>(R.id.itemWorkUnselectedRing)

            init {
                ViewAnimationHelper.attachSpringTouch(itemView)
                itemView.setOnClickListener {
                    val pos = adapterPosition
                    if (pos in items.indices) {
                        onItemClicked(items[pos])
                    }
                }
            }

            fun bind(book: Book, isSelected: Boolean) {
                txtTitle.text = "《${book.title}》"
                txtAuthor.text = "创作者: ${book.author ?: "未知创作者"}"
                txtMediaBadge.text = book.mediaType.emoji
                txtPlaceholderEmoji.text = book.mediaType.emoji

                // 封面加载
                CoverImageHelper.loadCover(imgCover, book.coverUrl, layoutPlaceholder)

                // 标签与状态
                txtCategory.text = book.category ?: "典藏"
                txtScore.text = "⭐ ${String.format(java.util.Locale.US, "%.1f", (book.rating ?: 0.0) / 2.0)}"
                txtStatus.text = book.status.displayName

                // 选中态高光切换
                if (isSelected) {
                    container.setBackgroundResource(R.drawable.bg_work_picker_item_selected)
                    if (badgeSelected.visibility != View.VISIBLE) {
                        badgeSelected.visibility = View.VISIBLE
                        badgeSelected.scaleX = 0.2f
                        badgeSelected.scaleY = 0.2f
                        badgeSelected.animate()
                            .scaleX(1f).scaleY(1f)
                            .setDuration(260L)
                            .setInterpolator(OvershootInterpolator(2.2f))
                            .start()
                    }
                    ringUnselected.visibility = View.GONE
                } else {
                    container.setBackgroundResource(R.drawable.bg_work_picker_item)
                    badgeSelected.visibility = View.GONE
                    badgeSelected.scaleX = 1f
                    badgeSelected.scaleY = 1f
                    ringUnselected.visibility = View.VISIBLE
                }
            }
        }
    }
}
