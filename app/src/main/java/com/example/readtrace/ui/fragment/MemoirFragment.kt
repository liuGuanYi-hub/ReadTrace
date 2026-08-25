package com.example.readtrace.ui.fragment

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.readtrace.AnimeTimelineScrollActivity
import com.example.readtrace.CoverGalleryActivity
import com.example.readtrace.CulturalPassportActivity
import com.example.readtrace.GameCartridgePosterActivity
import com.example.readtrace.MovieTicketPosterActivity
import com.example.readtrace.R
import com.example.readtrace.ResonancePosterActivity
import com.example.readtrace.data.BookDatabaseHelper
import com.example.readtrace.model.MediaType
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
        databaseHelper = BookDatabaseHelper(requireContext())

        val cardTimeWarpTunnel = view.findViewById<View>(R.id.cardTimeWarpTunnel)
        val cardPassport = view.findViewById<View>(R.id.cardPassport)
        val cardMovieTicket = view.findViewById<View>(R.id.cardMovieTicket)
        val cardGameCartridge = view.findViewById<View>(R.id.cardGameCartridge)
        val cardResonancePoster = view.findViewById<View>(R.id.cardResonancePoster)
        val cardAnimeTimeline = view.findViewById<View>(R.id.cardAnimeTimeline)
        val cardCoverGallery = view.findViewById<View>(R.id.cardCoverGallery)

        cardTimeWarpTunnel?.setOnClickListener {
            startActivity(Intent(requireContext(), com.example.readtrace.TimeWarpTunnelActivity::class.java))
        }

        cardPassport.setOnClickListener {
            startActivity(CulturalPassportActivity.createIntent(requireContext(), MediaType.ANIME))
        }

        cardMovieTicket.setOnClickListener {
            val firstMovie = databaseHelper.getBooks().firstOrNull { it.mediaType == MediaType.MOVIE }
            if (firstMovie != null) {
                startActivity(MovieTicketPosterActivity.createIntent(requireContext(), firstMovie.id))
            } else {
                Toast.makeText(requireContext(), "剧场暂无电影记录，请先添加或导入", Toast.LENGTH_SHORT).show()
            }
        }

        cardGameCartridge.setOnClickListener {
            val firstGame = databaseHelper.getBooks().firstOrNull { it.mediaType == MediaType.GAME }
            if (firstGame != null) {
                startActivity(GameCartridgePosterActivity.createIntent(requireContext(), firstGame.id))
            } else {
                Toast.makeText(requireContext(), "游戏库暂无作品，请先添加或导入", Toast.LENGTH_SHORT).show()
            }
        }

        cardResonancePoster.setOnClickListener {
            val allBooks = databaseHelper.getBooks()
            val bookA = allBooks.firstOrNull { it.mediaType == MediaType.BOOK }
            val animeB = allBooks.firstOrNull { it.mediaType == MediaType.ANIME }
            if (bookA != null && animeB != null) {
                startActivity(
                    ResonancePosterActivity.createIntent(
                        requireContext(),
                        bookA.id,
                        animeB.id,
                        94,
                        "跨媒介双生 · 精神共鸣",
                    ),
                )
            } else {
                Toast.makeText(requireContext(), "需要至少收录一部书籍和一部番剧以生成双生微卡", Toast.LENGTH_SHORT).show()
            }
        }

        cardAnimeTimeline.setOnClickListener {
            startActivity(Intent(requireContext(), AnimeTimelineScrollActivity::class.java))
        }

        cardCoverGallery.setOnClickListener {
            startActivity(Intent(requireContext(), CoverGalleryActivity::class.java))
        }

        listOfNotNull(
            cardTimeWarpTunnel, cardPassport, cardMovieTicket, cardGameCartridge,
            cardResonancePoster, cardAnimeTimeline, cardCoverGallery,
        ).forEach { ViewAnimationHelper.attachSpringTouch(it) }
    }

    override fun onDestroyView() {
        databaseHelper.close()
        super.onDestroyView()
    }
}
