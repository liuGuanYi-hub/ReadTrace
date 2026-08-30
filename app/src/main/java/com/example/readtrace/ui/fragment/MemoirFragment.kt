package com.example.readtrace.ui.fragment

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import com.example.readtrace.AnimeTimelineScrollActivity
import com.example.readtrace.MediaTimelineScrollActivity
import com.example.readtrace.CoverGalleryActivity
import com.example.readtrace.CulturalPassportActivity
import com.example.readtrace.ExLibrisStudioActivity
import com.example.readtrace.GameCartridgePosterActivity
import com.example.readtrace.MovieTicketPosterActivity
import com.example.readtrace.R
import com.example.readtrace.ResonancePosterActivity
import com.example.readtrace.VinylCassettePlayerActivity
import com.example.readtrace.data.BookDatabaseHelper
import com.example.readtrace.model.Book
import com.example.readtrace.model.MediaType
import com.example.readtrace.util.CoverImageHelper
import com.example.readtrace.util.ViewAnimationHelper

class MemoirFragment : Fragment() {

    private lateinit var databaseHelper: BookDatabaseHelper

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.fragment_memoir, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        databaseHelper = BookDatabaseHelper.getInstance(requireContext())

        val cardPassport = view.findViewById<View>(R.id.cardPassport)
        val cardExLibris = view.findViewById<View>(R.id.cardExLibris)
        val cardMovieTicket = view.findViewById<View>(R.id.cardMovieTicket)
        val cardVinylPlayer = view.findViewById<View>(R.id.cardVinylPlayer)
        val cardGameCartridge = view.findViewById<View>(R.id.cardGameCartridge)
        val cardResonancePoster = view.findViewById<View>(R.id.cardResonancePoster)
        val cardAnimeTimeline = view.findViewById<View>(R.id.cardAnimeTimeline)
        val cardCoverGallery = view.findViewById<View>(R.id.cardCoverGallery)

        cardPassport?.setOnClickListener {
            startActivity(CulturalPassportActivity.createIntent(requireContext(), MediaType.ANIME))
        }

        cardExLibris?.setOnClickListener {
            val firstBook = databaseHelper.getCachedBooks().firstOrNull { it.mediaType == MediaType.BOOK }
            if (firstBook != null) {
                startActivity(ExLibrisStudioActivity.createIntent(requireContext(), firstBook.id))
            } else {
                Toast.makeText(requireContext(), "藏书库暂无书籍记录，请先添加", Toast.LENGTH_SHORT).show()
            }
        }

        cardMovieTicket?.setOnClickListener {
            val firstMovie = databaseHelper.getCachedBooks().firstOrNull { it.mediaType == MediaType.MOVIE }
            if (firstMovie != null) {
                startActivity(MovieTicketPosterActivity.createIntent(requireContext(), firstMovie.id))
            } else {
                Toast.makeText(requireContext(), "剧场暂无电影记录，请先添加或导入", Toast.LENGTH_SHORT).show()
            }
        }

        cardVinylPlayer?.setOnClickListener {
            val firstMusic = databaseHelper.getCachedBooks().firstOrNull { it.mediaType == MediaType.MUSIC }
            if (firstMusic != null) {
                startActivity(VinylCassettePlayerActivity.createIntent(requireContext(), firstMusic.id))
            } else {
                Toast.makeText(requireContext(), "音乐馆暂无唱片，请先添加或导入", Toast.LENGTH_SHORT).show()
            }
        }

        cardGameCartridge?.setOnClickListener {
            val firstGame = databaseHelper.getCachedBooks().firstOrNull { it.mediaType == MediaType.GAME }
            if (firstGame != null) {
                startActivity(GameCartridgePosterActivity.createIntent(requireContext(), firstGame.id))
            } else {
                Toast.makeText(requireContext(), "游戏库暂无作品，请先添加或导入", Toast.LENGTH_SHORT).show()
            }
        }

        cardResonancePoster?.setOnClickListener {
            val allBooks = databaseHelper.getCachedBooks()
            val bookA = allBooks.firstOrNull { it.mediaType == MediaType.BOOK }
            val animeB = allBooks.firstOrNull { it.mediaType == MediaType.ANIME }
            if (bookA != null && animeB != null) {
                startActivity(
                    ResonancePosterActivity.createIntent(
                        requireContext(),
                        bookA.id,
                        animeB.id,
                        96,
                        "跨媒介双生 · 思想与文笔共鸣",
                    ),
                )
            } else {
                Toast.makeText(requireContext(), "需要至少收录一部书籍和一部番剧以生成双生微卡", Toast.LENGTH_SHORT).show()
            }
        }

        cardAnimeTimeline?.setOnClickListener {
            startActivity(MediaTimelineScrollActivity.createIntent(requireContext(), null))
        }

        cardCoverGallery?.setOnClickListener {
            startActivity(Intent(requireContext(), CoverGalleryActivity::class.java))
        }

        listOfNotNull<View>(
            cardPassport, cardExLibris, cardMovieTicket,
            cardVinylPlayer, cardGameCartridge, cardResonancePoster,
            cardAnimeTimeline, cardCoverGallery,
        ).forEach { ViewAnimationHelper.attachSpringTouch(it) }

        updateWorkshopBadges(view)
    }

    override fun onResume() {
        super.onResume()
        view?.let { updateWorkshopBadges(it) }
    }

    private fun updateWorkshopBadges(root: View) {
        val allBooks = databaseHelper.getCachedBooks()
        val animeList = allBooks.filter { it.mediaType == MediaType.ANIME }
        val gameList = allBooks.filter { it.mediaType == MediaType.GAME }

        root.findViewById<TextView>(R.id.passportSubtitle)?.text = "深蓝烫金首页 · ${animeList.size} 部番剧入境签证 · ${gameList.size} 款游戏白金戳印"

        // 动态计算全景编年长卷信息（支持全媒介任意历史与未来年份）
        val yearRegex = Regex("""\b(19\d{2}|20\d{2}|21\d{2})\b""")
        val allYears = allBooks.mapNotNull { book ->
            book.tags.mapNotNull { yearRegex.find(it)?.groupValues?.get(1)?.toIntOrNull() }.firstOrNull()
                ?: yearRegex.find(book.startDate.orEmpty())?.groupValues?.get(1)?.toIntOrNull()
                ?: yearRegex.find(book.finishDate.orEmpty())?.groupValues?.get(1)?.toIntOrNull()
                ?: yearRegex.find(book.category.orEmpty())?.groupValues?.get(1)?.toIntOrNull()
                ?: yearRegex.find(book.title)?.groupValues?.get(1)?.toIntOrNull()
        }
        val minYear = allYears.minOrNull()
        val maxYear = allYears.maxOrNull()

        val (titleStr, subtitleStr, spanBadgeStr) = if (minYear != null && maxYear != null) {
            val span = maxYear - minYear + 1
            val yearRange = if (minYear == maxYear) "$minYear" else "$minYear-$maxYear"
            Triple(
                "📜 全景编年画卷 ($yearRange)",
                "纵览 $span 年时光史 · 书籍/影视/游戏/番剧 · 1080P 超清全景长图导出",
                "⏳ $yearRange · ${span}年",
            )
        } else {
            Triple(
                "📜 全景编年画卷",
                "纵览全景精神史 · 书籍/影视/游戏/番剧 · 1080P 超清全景长图导出",
                "⏳ 跨越编年时光",
            )
        }

        root.findViewById<TextView>(R.id.animeTimelineTitle)?.text = titleStr
        root.findViewById<TextView>(R.id.animeTimelineSubtitle)?.text = subtitleStr
        root.findViewById<TextView>(R.id.animeTimelineCountBadge)?.text = "✨ ${allBooks.size} 部典藏"
        root.findViewById<TextView>(R.id.animeTimelineSpanBadge)?.text = spanBadgeStr
    }

    override fun onDestroyView() {
        databaseHelper.close()
        super.onDestroyView()
    }
}

