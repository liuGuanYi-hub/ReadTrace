package com.example.readtrace.ui.dialog

import android.app.Activity
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.readtrace.R
import com.example.readtrace.data.BookDatabaseHelper
import com.example.readtrace.model.Book
import com.example.readtrace.model.MediaType
import com.example.readtrace.util.CoverImageHelper
import com.example.readtrace.util.HapticFeedbackEngine
import com.google.android.material.bottomsheet.BottomSheetDialog

/**
 * 📚 作品选择弹窗 (WorkPickerDialog)
 * 允许用户从已有书库中多选作品，快速导入到「我的最爱」中。
 */
object WorkPickerDialog {

    fun show(
        activity: Activity,
        mediaType: MediaType,
        alreadyFavoriteBookIds: Set<Long>,
        onWorksSelected: (List<Book>) -> Unit,
    ) {
        val dialog = BottomSheetDialog(activity, R.style.Theme_ReadTrace_BottomSheetDialog)
        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_work_picker, null)
        dialog.setContentView(view)

        val db = BookDatabaseHelper.getInstance(activity)
        val allBooks: List<Book> = db.getBooks().filter { it.mediaType == mediaType && !it.isDeleted }
        val selectableBooks: List<Book> = allBooks.filter { !alreadyFavoriteBookIds.contains(it.id) }

        val titleView = view.findViewById<TextView>(R.id.pickerDialogTitle)
        val subtitleView = view.findViewById<TextView>(R.id.pickerDialogSubtitle)
        val searchInput = view.findViewById<EditText>(R.id.pickerSearchInput)
        val btnClearSearch = view.findViewById<View>(R.id.btnPickerClearSearch)
        val recyclerView = view.findViewById<RecyclerView>(R.id.pickerRecyclerView)
        val emptyView = view.findViewById<TextView>(R.id.pickerEmptyView)
        val btnConfirm = view.findViewById<Button>(R.id.btnConfirmAdd)
        val btnClose = view.findViewById<View>(R.id.btnPickerClose)

        com.example.readtrace.util.ViewAnimationHelper.attachSpringTouch(btnClose)
        com.example.readtrace.util.ViewAnimationHelper.attachSpringTouch(btnConfirm)

        titleView.text = "添加${mediaType.displayName}到最爱"
        subtitleView.text = "已有 ${selectableBooks.size} 部可选${mediaType.displayName}作品"

        val selectedBookIds = mutableSetOf<Long>()
        var displayList = selectableBooks

        lateinit var adapter: WorkPickerAdapter

        fun updateConfirmButtonText() {
            btnConfirm.text = if (selectedBookIds.isEmpty()) {
                "＋ 添加作品 (已选 0 部)"
            } else {
                "＋ 添加作品 (已选 ${selectedBookIds.size} 部)"
            }
            btnConfirm.isEnabled = selectedBookIds.isNotEmpty()
        }

        adapter = WorkPickerAdapter(
            items = displayList,
            selectedIds = selectedBookIds,
            onItemClicked = { book ->
                HapticFeedbackEngine.lightClick(activity)
                if (selectedBookIds.contains(book.id)) {
                    selectedBookIds.remove(book.id)
                } else {
                    selectedBookIds.add(book.id)
                }
                adapter.notifyDataSetChanged()
                updateConfirmButtonText()
            }
        )

        recyclerView.layoutManager = LinearLayoutManager(activity)
        recyclerView.adapter = adapter

        if (selectableBooks.isEmpty()) {
            emptyView.visibility = View.VISIBLE
            emptyView.text = if (allBooks.isEmpty()) {
                "当前分类下暂无任何已录入的作品\n请先去书架录入"
            } else {
                "该分类下的所有作品均已加入最爱"
            }
            recyclerView.visibility = View.GONE
        } else {
            emptyView.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        }

        updateConfirmButtonText()

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim().orEmpty().lowercase()
                btnClearSearch?.visibility = if (query.isNotBlank()) View.VISIBLE else View.GONE
                displayList = if (query.isBlank()) {
                    selectableBooks
                } else {
                    selectableBooks.filter {
                        it.title.lowercase().contains(query) ||
                            (it.author?.lowercase()?.contains(query) == true) ||
                            (it.category?.lowercase()?.contains(query) == true)
                    }
                }
                adapter.updateItems(displayList)
                if (displayList.isEmpty()) {
                    emptyView.visibility = View.VISIBLE
                    emptyView.text = "未找到与「$query」相关的作品"
                    recyclerView.visibility = View.GONE
                } else {
                    emptyView.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnClearSearch?.setOnClickListener {
            searchInput.setText("")
        }

        btnConfirm.setOnClickListener {
            val selected = selectableBooks.filter { selectedBookIds.contains(it.id) }
            if (selected.isNotEmpty()) {
                HapticFeedbackEngine.stampImpact(activity)
                onWorksSelected(selected)
                dialog.dismiss()
            }
        }

        btnClose.setOnClickListener {
            HapticFeedbackEngine.lightClick(activity)
            dialog.dismiss()
        }

        dialog.show()
    }

    private class WorkPickerAdapter(
        private var items: List<Book>,
        private val selectedIds: Set<Long>,
        private val onItemClicked: (Book) -> Unit,
    ) : RecyclerView.Adapter<WorkPickerAdapter.ViewHolder>() {

        fun updateItems(newItems: List<Book>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_work_picker, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val book = items[position]
            val isChecked = selectedIds.contains(book.id)

            holder.checkBox.isChecked = isChecked
            holder.title.text = book.title
            val authorCategory = buildString {
                if (!book.author.isNullOrBlank()) append(book.author)
                if (!book.category.isNullOrBlank()) {
                    if (isNotEmpty()) append(" · ")
                    append(book.category)
                }
            }
            holder.author.text = authorCategory.ifBlank { "未知创作者" }

            val ratingVal = book.rating
            if (ratingVal != null && ratingVal > 0) {
                holder.rating.visibility = View.VISIBLE
                holder.rating.text = String.format("★ %.1f", ratingVal)
            } else {
                holder.rating.visibility = View.GONE
            }

            // 音乐作品没有阅读状态语义：不显示「已读」状态签
            if (book.mediaType == MediaType.MUSIC) {
                holder.status.visibility = View.GONE
            } else {
                holder.status.text = book.status.displayName
                holder.status.visibility = View.VISIBLE
            }
            CoverImageHelper.loadCover(holder.cover, book.coverUrl)

            holder.itemView.setOnClickListener {
                onItemClicked(book)
            }
        }

        override fun getItemCount(): Int = items.size

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val checkBox: CheckBox = view.findViewById(R.id.pickerCheckBox)
            val cover: ImageView = view.findViewById(R.id.pickerBookCover)
            val title: TextView = view.findViewById(R.id.pickerBookTitle)
            val author: TextView = view.findViewById(R.id.pickerBookAuthor)
            val rating: TextView = view.findViewById(R.id.pickerBookRating)
            val status: TextView = view.findViewById(R.id.pickerBookStatus)
        }
    }
}
