package com.kuangru52.embysic

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.media3.common.Player
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.util.Log
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class LyricsFragment : Fragment() {
    private lateinit var rvLyrics: RecyclerView
    private val lyricsAdapter = LyricsAdapter { /* 点击处理 */ }
    private val handler = Handler(Looper.getMainLooper())
    private val updateProgressAction = object : Runnable {
        override fun run() {
            val player = (activity as? HomeActivity)?.mediaController
            if (player?.isPlaying == true) {
                val index = lyricsAdapter.updateActiveLine(player.currentPosition)
                if (index != -1) rvLyrics.smoothScrollToPosition(index)
            }
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_lyrics, container, false)
        rvLyrics = view.findViewById(R.id.rvLyrics)
        rvLyrics.layoutManager = LinearLayoutManager(context)
        rvLyrics.adapter = lyricsAdapter
        
        setupPlayerListener()
        return view
    }

    private fun setupPlayerListener() {
        val activity = activity as? HomeActivity ?: return
        val handler = Handler(Looper.getMainLooper())
        
        val checkController = object : Runnable {
            override fun run() {
                val player = activity.mediaController
                if (player != null) {
                    val itemId = player.currentMediaItem?.mediaId
                    if (itemId != null) {
                        preloadLyrics(itemId, player.currentMediaItem?.mediaMetadata?.title?.toString(), player.currentMediaItem?.mediaMetadata?.artist?.toString())
                    }
                    player.addListener(object : Player.Listener {
                        override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                            val id = mediaItem?.mediaId ?: return
                            preloadLyrics(id, mediaItem.mediaMetadata.title?.toString(), mediaItem.mediaMetadata.artist?.toString())
                        }
                    })
                    this@LyricsFragment.handler.post(updateProgressAction)
                } else {
                    handler.postDelayed(this, 100)
                }
            }
        }
        handler.post(checkController)
    }

    private fun preloadLyrics(itemId: String, title: String?, artist: String?) {
        val activity = activity as? HomeActivity ?: return
        val apiService = activity.let {
            val prefs = it.getSharedPreferences("embysic_prefs", android.content.Context.MODE_PRIVATE)
            val serverUrl = prefs.getString("server_url", "") ?: ""
            if (serverUrl.isNotEmpty()) {
                retrofit2.Retrofit.Builder()
                    .baseUrl(if (serverUrl.endsWith("/")) serverUrl else "$serverUrl/")
                    .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
                    .build()
                    .create(EmbyApiService::class.java)
            } else null
        } ?: return

        val prefs = activity.getSharedPreferences("embysic_prefs", android.content.Context.MODE_PRIVATE)
        val accessToken = prefs.getString("access_token", "") ?: ""
        val authHeader = "MediaBrowser Token=\"$accessToken\""

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = apiService.getLyrics(itemId, authHeader)
                val rawLines = response.Lines ?: response.Lyrics ?: response.LyricLines
                if (!rawLines.isNullOrEmpty()) {
                    val lyrics = rawLines.map { LrcLine(it.StartTicks?.let { t -> t / 10000 } ?: it.Start?.let { t -> t / 10000 } ?: 0L, it.Text) }
                        .filter { it.text.isNotBlank() }.sortedBy { it.timeMs }
                    lyricsAdapter.lines = lyrics
                }
            } catch (e: Exception) {
                Log.e("LyricsFragment", "Load error: ${e.message}")
            }
        }
    }

    override fun onDestroyView() {
        handler.removeCallbacks(updateProgressAction)
        super.onDestroyView()
    }
}
