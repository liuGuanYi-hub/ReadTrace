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
import com.example.readtrace.CoverGalleryActivity
import com.example.readtrace.CulturalPassportActivity
import com.example.readtrace.ExLibrisStudioActivity
import com.example.readtrace.GameCartridgePosterActivity
import com.example.readtrace.MovieTicketPosterActivity
import com.example.readtrace.R
import com.example.readtrace.ResonancePosterActivity
import com.example.readtrace.TimeWarpTunnelActivity
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

        val cardTimeWarpTunnel = view.findViewById<View>(R.id.cardTimeWarpTunnel)
        val cardPassport = view.findViewById<View>(R.id.cardPassport)
        val cardExLibris = view.findViewById<View>(R.id.cardExLibris)
        val cardMovieTicket = view.findViewById<View>(R.id.cardMovieTicket)
        val cardVinylPlayer = view.findViewById<View>(R.id.cardVinylPlayer)
        val cardGameCartridge = view.findViewById<View>(R.id.cardGameCartridge)
        val cardResonancePoster = view.findViewById<View>(R.id.cardResonancePoster)
        val cardAnimeTimeline = view.findViewById<View>(R.id.cardAnimeTimeline)
        val cardCoverGallery = view.findViewById<View>(R.id.cardCoverGallery)

        cardTimeWarpTunnel?.setOnClickListener {
            startActivity(Intent(requireContext(), TimeWarpTunnelActivity::class.java))
        }

        cardPassport?.setOnClickListener {
            startActivity(CulturalPassportActivity.createIntent(requireContext(), MediaType.ANIME))
        }

        cardExLibris?.setOnClickListener {
            val firstBook = databaseHelper.getBooks().firstOrNull { it.mediaType == MediaType.BOOK }
            if (firstBook != null) {
                startActivity(ExLibrisStudioActivity.createIntent(requireContext(), firstBook.id))
            } else {
                Toast.makeText(requireContext(), "藏书库暂无书籍记录，请先添加", Toast.LENGTH_SHORT).show()
            }
        }

        cardMovieTicket?.setOnClickListener {
            val firstMovie = databaseHelper.getBooks().firstOrNull { it.mediaType == MediaType.MOVIE }
            if (firstMovie != null) {
                startActivity(MovieTicketPosterActivity.createIntent(requireContext(), firstMovie.id))
            } else {
                Toast.makeText(requireContext(), "剧场暂无电影记录，请先添加或导入", Toast.LENGTH_SHORT).show()
            }
        }

        cardVinylPlayer?.setOnClickListener {
            val firstMusic = databaseHelper.getBooks().firstOrNull { it.mediaType == MediaType.MUSIC }
            if (firstMusic != null) {
                startActivity(VinylCassettePlayerActivity.createIntent(requireContext(), firstMusic.id))
            } else {
                Toast.makeText(requireContext(), "音乐馆暂无唱片，请先添加或导入", Toast.LENGTH_SHORT).show()
            }
        }

        cardGameCartridge?.setOnClickListener {
            val firstGame = databaseHelper.getBooks().firstOrNull { it.mediaType == MediaType.GAME }
            if (firstGame != null) {
                startActivity(GameCartridgePosterActivity.createIntent(requireContext(), firstGame.id))
            } else {
                Toast.makeText(requireContext(), "游戏库暂无作品，请先添加或导入", Toast.LENGTH_SHORT).show()
            }
        }

        cardResonancePoster?.setOnClickListener {
            val allBooks = databaseHelper.getBooks()
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
            startActivity(Intent(requireContext(), AnimeTimelineScrollActivity::class.java))
        }

        cardCoverGallery?.setOnClickListener {
            startActivity(Intent(requireContext(), CoverGalleryActivity::class.java))
        }

        listOfNotNull<View>(
            cardTimeWarpTunnel, cardPassport, cardExLibris, cardMovieTicket,
            cardVinylPlayer, cardGameCartridge, cardResonancePoster,
            cardAnimeTimeline, cardCoverGallery,
        ).forEach { ViewAnimationHelper.attachSpringTouch(it) }

        loadWorkshopPreviews(view)
    }

    override fun onResume() {
        super.onResume()
        view?.let { loadWorkshopPreviews(it) }
    }

    private fun loadWorkshopPreviews(root: View) {
        val allBooks = databaseHelper.getBooks()

        // 1. 时空穿梭隧道
        val tunnelBadge = root.findViewById<TextView>(R.id.tunnelCapsuleBadge)
        tunnelBadge?.text = "⏱️ ${allBooks.size} 颗时空胶囊"
        val tunnelContainer = root.findViewById<LinearLayout>(R.id.tunnelCoversPreview)
        if (tunnelContainer != null) {
            populateCoverPreview(tunnelContainer, allBooks.take(4))
        }

        // 2. 精神巡礼护照
        val animeList = allBooks.filter { it.mediaType == MediaType.ANIME }
        val gameList = allBooks.filter { it.mediaType == MediaType.GAME }
        val bookList = allBooks.filter { it.mediaType == MediaType.BOOK }
        val movieList = allBooks.filter { it.mediaType == MediaType.MOVIE }
        val musicList = allBooks.filter { it.mediaType == MediaType.MUSIC }

        val passportSub = root.findViewById<TextView>(R.id.passportSubtitle)
        passportSub?.text = "深蓝烫金首页 · ${animeList.size} 部番剧入境签证 · ${gameList.size} 款游戏白金戳印"
        val passportContainer = root.findViewById<LinearLayout>(R.id.passportCoversPreview)
        if (passportContainer != null) {
            val passportSamples = (animeList.take(2) + gameList.take(2)).ifEmpty { allBooks.take(4) }
            populateCoverPreview(passportContainer, passportSamples)
        }

        // 3. 藏书票工坊
        val exLibrisContainer = root.findViewById<LinearLayout>(R.id.exLibrisCoversPreview)
        if (exLibrisContainer != null) {
            populateCoverPreview(exLibrisContainer, bookList.take(4))
        }

        // 4. 电影票根
        val movieContainer = root.findViewById<LinearLayout>(R.id.movieCoversPreview)
        if (movieContainer != null) {
            populateCoverPreview(movieContainer, movieList.take(4))
        }

        // 5. 黑胶唱片机
        val vinylContainer = root.findViewById<LinearLayout>(R.id.vinylCoversPreview)
        if (vinylContainer != null) {
            populateCoverPreview(vinylContainer, musicList.take(4))
        }

        // 6. 游戏全息卡带
        val gameContainer = root.findViewById<LinearLayout>(R.id.gameCoversPreview)
        if (gameContainer != null) {
            populateCoverPreview(gameContainer, gameList.take(4))
        }

        // 7. 双生微卡
        val resonanceContainer = root.findViewById<LinearLayout>(R.id.resonanceCoversPreview)
        if (resonanceContainer != null) {
            val twinSamples = listOfNotNull(bookList.firstOrNull(), animeList.firstOrNull(), movieList.firstOrNull(), gameList.firstOrNull()).take(4)
            populateCoverPreview(resonanceContainer, twinSamples)
        }

        // 8. 追番编年画卷
        val animeTimelineContainer = root.findViewById<LinearLayout>(R.id.animeTimelineCoversPreview)
        if (animeTimelineContainer != null) {
            populateCoverPreview(animeTimelineContainer, animeList.take(4))
        }

        // 9. 封面画廊
        val galleryContainer = root.findViewById<LinearLayout>(R.id.galleryCoversPreview)
        if (galleryContainer != null) {
            populateCoverPreview(galleryContainer, allBooks.shuffled().take(4))
        }
    }

    private fun populateCoverPreview(container: LinearLayout, items: List<Book>) {
        container.removeAllViews()
        if (items.isEmpty()) {
            container.visibility = View.GONE
            return
        }
        container.visibility = View.VISIBLE
        val ctx = context ?: return

        items.forEach { book ->
            val cardView = CardView(ctx).apply {
                radius = dpToPx(6).toFloat()
                cardElevation = dpToPx(2).toFloat()
                val params = LinearLayout.LayoutParams(dpToPx(48), dpToPx(72)).apply {
                    marginEnd = dpToPx(8)
                }
                layoutParams = params
            }

            val iv = ImageView(ctx).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            }
            CoverImageHelper.loadCover(iv, book.coverUrl)
            cardView.addView(iv)
            container.addView(cardView)
        }
    }

    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density + 0.5f).toInt()

    override fun onDestroyView() {
        databaseHelper.close()
        super.onDestroyView()
    }
}

