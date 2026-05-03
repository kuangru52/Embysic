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

class LyricFragment : Fragment() {
    private lateinit var rvLyrics: RecyclerView
    private val lyricsAdapter = LyricsAdapter { /* 不在分栏模式下收起 */ }
    private val handler = Handler(Looper.getMainLooper())
    private val updateProgressAction = object : Runnable {
        override fun run() {
            val player = (activity as? HomeActivity)?.mediaController
            if (player != null && player.isPlaying) {
                val index = lyricsAdapter.updateActiveLine(player.currentPosition)
                if (index != -1) rvLyrics.smoothScrollToPosition(index)
            }
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        rvLyrics = RecyclerView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            layoutManager = LinearLayoutManager(context)
            setPadding(0, 100, 0, 100)
            clipToPadding = false
        }
        rvLyrics.adapter = lyricsAdapter
        
        setupPlayerListener()
        return rvLyrics
    }

    private fun setupPlayerListener() {
        val activity = activity as? HomeActivity ?: return
        val checkController = object : Runnable {
            override fun run() {
                val player = activity.mediaController
                if (player != null) {
                    player.addListener(object : Player.Listener {
                        override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                            // 实际开发中这里应调用类似 PlayerDialogFragment 的歌词加载逻辑
                        }
                    })
                    handler.post(updateProgressAction)
                } else {
                    handler.postDelayed(this, 100)
                }
            }
        }
        handler.post(checkController)
    }

    override fun onDestroyView() {
        handler.removeCallbacks(updateProgressAction)
        super.onDestroyView()
    }
}
