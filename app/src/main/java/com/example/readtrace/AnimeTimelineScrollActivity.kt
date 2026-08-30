package com.example.readtrace

import android.os.Bundle
import com.example.readtrace.model.MediaType

/**
 * 追番编年画卷 Activity（向后兼容，默认定向至番剧 [MediaType.ANIME]）
 */
class AnimeTimelineScrollActivity : MediaTimelineScrollActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        if (!intent.hasExtra(EXTRA_MEDIA_TYPE)) {
            intent.putExtra(EXTRA_MEDIA_TYPE, MediaType.ANIME.databaseValue)
        }
        super.onCreate(savedInstanceState)
    }
}
