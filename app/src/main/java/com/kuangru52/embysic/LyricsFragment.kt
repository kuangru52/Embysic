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

import androidx.media3.common.util.UnstableApi
import androidx.annotation.OptIn

@UnstableApi
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

    @OptIn(UnstableApi::class)
    private fun preloadLyrics(itemId: String, title: String?, artist: String?) {
        val activity = activity as? HomeActivity ?: return
        
        // 统一从 PlayerDialogFragment 的缓存获取
        // 因为 PlayerDialogFragment 已经在播放时通过 preloadLyrics -> searchNeteaseLyrics 填充了缓存
        val playerDialog = activity.supportFragmentManager.findFragmentByTag("player") as? PlayerDialogFragment
        val cached = playerDialog?.getLyricsFromCache(itemId)
        if (cached != null) {
            lyricsAdapter.lines = cached
        } else {
            // 如果缓存中没有，显示加载中或空
            lyricsAdapter.lines = listOf(LrcLine(-1, "正在同步歌词..."))
        }
    }

    override fun onDestroyView() {
        handler.removeCallbacks(updateProgressAction)
        super.onDestroyView()
    }
}
