package com.kuangru52.embysic

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.fragment.app.Fragment
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import coil.load

class DiscFragment : Fragment() {
    private lateinit var rlDisc: View
    private lateinit var ivCover: ImageView
    private lateinit var ivNeedle: ImageView
    private var discRotation = 0f
    private val handler = Handler(Looper.getMainLooper())
    private val rotationRunnable = object : Runnable {
        override fun run() {
            val player = (activity as? HomeActivity)?.mediaController
            if (player?.isPlaying == true) {
                discRotation = (discRotation + 0.5f) % 360f
                rlDisc.rotation = discRotation
            }
            handler.postDelayed(this, 30)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_disc, container, false)
        rlDisc = view.findViewById(R.id.rlDisc)
        ivCover = view.findViewById(R.id.ivFullCover)
        ivNeedle = view.findViewById(R.id.ivNeedle)
        
        ivNeedle.rotation = -25f
        
        setupPlayerListener()
        return view
    }

    private fun setupPlayerListener() {
        val activity = activity as? HomeActivity ?: return
        
        // 关键：持续检查直到 mediaController 初始化完成
        val handler = Handler(Looper.getMainLooper())
        val checkController = object : Runnable {
            override fun run() {
                val player = activity.mediaController
                if (player != null) {
                    updateUI(player)
                    player.addListener(object : Player.Listener {
                        override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                            updateUI(player)
                        }
                        override fun onIsPlayingChanged(isPlaying: Boolean) {
                            updateNeedle(isPlaying)
                            if (isPlaying) {
                                this@DiscFragment.handler.post(rotationRunnable)
                            } else {
                                this@DiscFragment.handler.removeCallbacks(rotationRunnable)
                            }
                        }
                    })
                    this@DiscFragment.handler.post(rotationRunnable)
                } else {
                    handler.postDelayed(this, 100)
                }
            }
        }
        handler.post(checkController)
    }

    private fun updateUI(player: Player) {
        val item = player.currentMediaItem ?: return
        val itemId = item.mediaId
        
        // 优先检查本地缓存封面，提高加载速度
        val prefs = context?.getSharedPreferences("netease_covers", android.content.Context.MODE_PRIVATE)
        val cachedCover = prefs?.getString(itemId, null)
        val artworkUri = if (cachedCover != null) android.net.Uri.parse(cachedCover) else item.mediaMetadata.artworkUri

        ivCover.load(artworkUri) {
            crossfade(true)
            placeholder(R.drawable.logo)
            error(R.drawable.logo)
        }
        
        // 更新旋转状态
        val isPlaying = player.isPlaying
        updateNeedle(isPlaying)
        if (isPlaying) {
            handler.post(rotationRunnable)
        } else {
            handler.removeCallbacks(rotationRunnable)
        }
    }

    private fun updateNeedle(isPlaying: Boolean) {
        ivNeedle.animate()
            .rotation(if (isPlaying) 15f else -25f) // 15f 是【播放/放下】角度，-25f 是【暂停/抬起】角度
            .setDuration(400)
            .start()
    }

    override fun onDestroyView() {
        handler.removeCallbacks(rotationRunnable)
        super.onDestroyView()
    }
}
