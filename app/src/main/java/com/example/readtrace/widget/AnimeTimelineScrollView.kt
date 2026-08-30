package com.example.readtrace.widget

import android.content.Context
import android.util.AttributeSet
import com.example.readtrace.model.Book
import com.example.readtrace.model.MediaType

/**
 * 追番编年画卷（向后兼容封装，直接继承自通用 [MediaTimelineScrollView]）
 */
class AnimeTimelineScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : MediaTimelineScrollView(context, attrs, defStyleAttr) {

    fun getAnimeList(): List<Book> = bookList

    override fun setAnimeData(list: List<Book>) {
        setTimelineData(list, MediaType.ANIME)
    }
}
