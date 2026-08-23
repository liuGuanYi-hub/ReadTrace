package com.example.readtrace.reader

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.readtrace.R

class ReaderPageAdapter(
    private var pages: List<ReaderPage>,
    private val bookTitle: String,
    private val authorName: String,
) : RecyclerView.Adapter<ReaderPageAdapter.PageViewHolder>() {

    enum class ReaderTheme(
        val bgColor: Int,
        val textColor: Int,
        val subTextColor: Int,
        val dividerColor: Int,
    ) {
        PARCHMENT(
            Color.parseColor("#F7EFE2"),
            Color.parseColor("#2D241E"),
            Color.parseColor("#8C7A6B"),
            Color.parseColor("#208C7A6B"),
        ),
        MINT(
            Color.parseColor("#EAF4EC"),
            Color.parseColor("#1B2E20"),
            Color.parseColor("#6A8B71"),
            Color.parseColor("#206A8B71"),
        ),
        NIGHT(
            Color.parseColor("#141923"),
            Color.parseColor("#E6EDF3"),
            Color.parseColor("#7D8590"),
            Color.parseColor("#25FFFFFF"),
        ),
    }

    var currentTheme: ReaderTheme = ReaderTheme.PARCHMENT

    fun updatePages(newPages: List<ReaderPage>) {
        pages = newPages
        notifyDataSetChanged()
    }

    fun setTheme(theme: ReaderTheme) {
        currentTheme = theme
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_reader_page, parent, false)
        return PageViewHolder(view)
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        val page = pages[position]
        holder.bind(page, bookTitle, authorName, currentTheme)
    }

    override fun getItemCount(): Int = pages.size

    class PageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val pageCard: View = itemView.findViewById(R.id.readerPageCard)
        private val bookTitleText: TextView = itemView.findViewById(R.id.pageBookTitle)
        private val chapterTitleText: TextView = itemView.findViewById(R.id.pageChapterTitle)
        private val contentText: TextView = itemView.findViewById(R.id.pageContentText)
        private val headerDivider: View = itemView.findViewById(R.id.pageHeaderDivider)
        private val footerDivider: View = itemView.findViewById(R.id.pageFooterDivider)
        private val footerBrand: TextView = itemView.findViewById(R.id.pageFooterBrand)
        private val footerNumber: TextView = itemView.findViewById(R.id.pageFooterNumber)

        fun bind(page: ReaderPage, bookTitle: String, author: String, theme: ReaderTheme) {
            pageCard.setBackgroundColor(theme.bgColor)

            val displayAuthor = if (author.isNotBlank()) " · $author" else ""
            bookTitleText.text = "$bookTitle$displayAuthor"
            chapterTitleText.text = page.chapterTitle
            contentText.text = page.content

            val percentage = (page.progressRatio * 100).toInt()
            footerNumber.text = "${page.pageIndex} / ${page.totalPages} 页 ($percentage%)"

            // 应用配色
            contentText.setTextColor(theme.textColor)
            bookTitleText.setTextColor(theme.subTextColor)
            chapterTitleText.setTextColor(theme.subTextColor)
            footerBrand.setTextColor(theme.subTextColor)
            footerNumber.setTextColor(theme.subTextColor)
            headerDivider.setBackgroundColor(theme.dividerColor)
            footerDivider.setBackgroundColor(theme.dividerColor)
        }
    }
}
