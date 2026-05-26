package com.kuangru52.embysic

import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import jp.wasabeef.transformers.coil.BlurTransformation
import androidx.core.content.edit
import kotlinx.coroutines.withContext
import android.net.Uri

import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.util.Locale

@androidx.media3.common.util.UnstableApi
class TabletPlayerHandler(
    private val activity: AppCompatActivity,
    private val controller: MediaController
) {
    private var tvTitle: TextView? = null
    private var tvArtist: TextView? = null
    private var ivAlbumArt: ImageView? = null
    private var ivBlurBackground: ImageView? = null
    private var ivNeedle: ImageView? = null
    private var rlDisc: View? = null
    private var btnPlayPause: ImageView? = null
    private var btnPrev: ImageView? = null
    private var btnNext: ImageView? = null
    private var btnPlayMode: ImageView? = null
    private var btnMore: ImageView? = null
    private var seekBar: SeekBar? = null
    private var tvCurrentTime: TextView? = null
    private var tvTotalTime: TextView? = null
    private var rvLyrics: RecyclerView? = null
    private var rvLyricsRight: RecyclerView? = null
    private var tvPlayModeHint: TextView? = null
    private var tvAudioQuality: TextView? = null
    private var pbDownload: android.widget.ProgressBar? = null
    private var volumeDotView: VolumeDotView? = null
    private var bottomTouchArea: View? = null
    private var sbVolumeHint: SeekBar? = null
    private var llVolumeHint: View? = null
    private var heartLayout: HeartLayout? = null
    private var vBackgroundScrim: View? = null

    private var lyricsAdapter: LyricsAdapter? = null
    private val handler = Handler(Looper.getMainLooper())
    private var discRotation = 0f
    
    private val discRotationRunnable = object : Runnable {
        override fun run() {
            if (controller.isPlaying) {
                discRotation = (discRotation + 0.5f) % 360f
                rlDisc?.rotation = discRotation
                handler.postDelayed(this, 30)
            }
        }
    }

    private val updateProgressAction = object : Runnable {
        override fun run() {
            val player = controller
            updateUIProgress(player)
            handler.postDelayed(this, 500)
        }
    }

    fun init() {
        val root = activity.findViewById<View>(R.id.main_root) ?: return
        
        tvTitle = root.findViewById(R.id.tvTitle)
        tvArtist = root.findViewById(R.id.tvArtist)
        ivAlbumArt = root.findViewById(R.id.ivAlbumArt)
        ivBlurBackground = root.findViewById(R.id.ivBlurBackground)
        ivNeedle = root.findViewById(R.id.ivNeedle)
        rlDisc = root.findViewById(R.id.rlDisc)
        btnPlayPause = root.findViewById(R.id.btnPlayPause)
        btnPrev = root.findViewById(R.id.btnPrev)
        btnNext = root.findViewById(R.id.btnNext)
        btnPlayMode = root.findViewById(R.id.btnPlayMode)
        btnMore = root.findViewById(R.id.btnMore)
        seekBar = root.findViewById(R.id.seekBar)
        tvCurrentTime = root.findViewById(R.id.tvCurrentTime)
        tvTotalTime = root.findViewById(R.id.tvTotalTime)
        rvLyrics = root.findViewById(R.id.rvLyrics)
        rvLyricsRight = root.findViewById(R.id.rvLyricsRight)
        tvPlayModeHint = root.findViewById(R.id.tvPlayModeHint)
        tvAudioQuality = root.findViewById(R.id.tvAudioQuality)
        pbDownload = root.findViewById(R.id.pbDownload)
        volumeDotView = root.findViewById(R.id.volumeDotView)
        bottomTouchArea = root.findViewById(R.id.bottomTouchArea)
        sbVolumeHint = root.findViewById(R.id.sbVolumeHint)
        llVolumeHint = root.findViewById(R.id.llVolumeHint)
        heartLayout = root.findViewById(R.id.heartLayout)
        vBackgroundScrim = root.findViewById(R.id.vBackgroundScrim)

        ivNeedle?.rotation = -35f

        setupLyrics()
        setupListeners()
        
        controller.addListener(playerListener)
        updateMetadata(controller.currentMediaItem)
        updatePlayModeIcon()
        handler.post(updateProgressAction)
        if (controller.isPlaying) handler.post(discRotationRunnable)
    }

    private fun setupLyrics() {
        lyricsAdapter = LyricsAdapter {
            // 平板模式下点击歌词暂无特定动作
        }
        val layoutManager = LinearLayoutManager(activity)
        rvLyricsRight?.layoutManager = layoutManager
        rvLyricsRight?.adapter = lyricsAdapter
        
        rvLyricsRight?.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            private val resumeRunnable = Runnable { rvLyricsRight?.setTag(R.id.bottom_container, false) }

            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    rvLyricsRight?.setTag(R.id.bottom_container, true)
                    handler.removeCallbacks(resumeRunnable)
                } else if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    handler.postDelayed(resumeRunnable, 3000)
                }
            }
        })
    }

    private fun setupListeners() {
        btnPlayPause?.setOnClickListener {
            if (controller.isPlaying) controller.pause() else controller.play()
        }
        btnPrev?.setOnClickListener { 
            controller.seekToPrevious() 
            controller.play()
        }
        btnNext?.setOnClickListener { 
            controller.seekToNext() 
            controller.play()
        }
        btnPlayMode?.setOnClickListener { togglePlayMode() }
        btnMore?.setOnClickListener { toggleFavorite() }

        tvArtist?.setOnClickListener {
            val currentItem = controller.currentMediaItem ?: return@setOnClickListener
            val extras = currentItem.mediaMetadata.extras ?: return@setOnClickListener
            val artistId = extras.getString("artist_id")
            val artistName = currentItem.mediaMetadata.artist?.toString() ?: "歌手"

            if (artistId != null) {
                (activity as? HomeActivity)?.let { activity ->
                    val fragment = LibraryFragment().apply {
                        arguments = android.os.Bundle().apply {
                            putString("artist_id", artistId)
                            putString("artist_name", artistName)
                        }
                    }
                    activity.replaceFragment(fragment, "artist_$artistId")
                }
            }
        }
        
        val root = activity.findViewById<View>(R.id.main_root)
        val llTitleContainer = root?.findViewById<View>(R.id.llTitleContainer)
        llTitleContainer?.setOnClickListener {
            val mediaId = controller.currentMediaItem?.mediaId ?: return@setOnClickListener
            (activity as? HomeActivity)?.let { activity ->
                val fragment = LibraryFragment().apply {
                    arguments = android.os.Bundle().apply {
                        putString("target_item_id", mediaId)
                    }
                }
                activity.replaceFragment(fragment, "library_locate")
            }
        }

        rlDisc?.setOnClickListener { 
            // 平板模式下，可以在这里触发一些视觉反馈
        }

        setupVolumeTouch()
        setupQualityClick()
        
        seekBar?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                if (fromUser) tvCurrentTime?.text = formatTime(p.toLong())
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {
                s?.let { 
                    controller.seekTo(it.progress.toLong())
                    controller.play()
                }
            }
        })
    }

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            updateMetadata(mediaItem)
        }
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            btnPlayPause?.setImageResource(if (isPlaying) R.drawable.ic_pause_vector else R.drawable.ic_play_vector)
            updateNeedle(isPlaying)
            if (isPlaying) handler.post(discRotationRunnable) else handler.removeCallbacks(discRotationRunnable)
        }
        override fun onRepeatModeChanged(repeatMode: Int) { updatePlayModeIcon() }
        override fun onShuffleModeEnabledChanged(enabled: Boolean) { updatePlayModeIcon() }
        override fun onMediaMetadataChanged(mediaMetadata: androidx.media3.common.MediaMetadata) {
            updateFavoriteIcon(mediaMetadata.extras?.getBoolean("is_favorite", false) ?: false)
        }
    }

    private fun updateMetadata(item: MediaItem?) {
        val isDark = (activity as? HomeActivity)?.isDarkForce() ?: true
        (activity as? HomeActivity)?.updateBackground(item)
        vBackgroundScrim?.setBackgroundColor(if (isDark) 0x33000000 else 0x1A000000)
        
        if (item == null) {
            tvTitle?.text = activity.getString(R.string.not_playing)
            tvArtist?.text = ""
            tvAudioQuality?.visibility = View.GONE
            ivAlbumArt?.setImageResource(R.drawable.disk)
            ivBlurBackground?.setImageDrawable(null)
            return
        }
        val metadata = item.mediaMetadata
        
        val textColorPrimary = if (isDark) Color.WHITE else Color.BLACK
        val textColorSecondary = if (isDark) 0xB3FFFFFF.toInt() else 0xE6000000.toInt()
        val iconTint = if (isDark) Color.WHITE else Color.BLACK
        val iconTintSecondary = if (isDark) 0xB3FFFFFF.toInt() else 0x99000000.toInt()

        tvTitle?.apply {
            text = metadata.title ?: "Unknown"
            setTextColor(textColorPrimary)
            setShadowLayer(if (isDark) 10f else 0f, 0f, 4f, if (isDark) 0xCC000000.toInt() else Color.TRANSPARENT)
            isSelected = true
            requestFocus()
        }
        tvArtist?.apply {
            val artist = (metadata.artist ?: "Unknown Artist").toString().replace("\n", " ")
            val album = metadata.albumTitle?.toString()?.replace("\n", " ")
            text = if (album.isNullOrEmpty()) artist else "$artist - $album"
            setTextColor(if (isDark) 0xE6FFFFFF.toInt() else 0xE6000000.toInt())
            setShadowLayer(if (isDark) 8f else 0f, 0f, 2f, if (isDark) 0x99000000.toInt() else Color.TRANSPARENT)
            isSelected = true
            requestFocus()
        }
        
        tvCurrentTime?.setTextColor(textColorSecondary)
        tvTotalTime?.setTextColor(textColorSecondary)
        btnPrev?.imageTintList = android.content.res.ColorStateList.valueOf(iconTint)
        btnPlayPause?.imageTintList = android.content.res.ColorStateList.valueOf(iconTint)
        btnNext?.imageTintList = android.content.res.ColorStateList.valueOf(iconTint)
        btnPlayMode?.imageTintList = android.content.res.ColorStateList.valueOf(iconTintSecondary)
        seekBar?.progressTintList = android.content.res.ColorStateList.valueOf(textColorPrimary)
        seekBar?.thumbTintList = android.content.res.ColorStateList.valueOf(textColorPrimary)
        
        val mediaId = item.mediaId
        val artworkUri = metadata.artworkUri

        // 封面加载策略
        val coverFile = java.io.File(activity.cacheDir, "covers/${mediaId}.jpg")
        if (coverFile.exists()) {
            ivAlbumArt?.load(coverFile) {
                crossfade(600)
                placeholder(R.drawable.disk)
                listener(onSuccess = { _, _ -> updateBlurBackground(coverFile) })
            }
        } else {
            ivAlbumArt?.load(artworkUri) {
                crossfade(600)
                placeholder(R.drawable.disk)
                error(R.drawable.disk)
                listener(
                    onSuccess = { _, _ -> updateBlurBackground(artworkUri) },
                    onError = { _, _ -> syncNeteaseData(item) }
                )
            }
        }

        val extras = metadata.extras
        updateFavoriteIcon(extras?.getBoolean("is_favorite", false) ?: false)
        updateAudioQuality(item)
        
        // 加载歌词
        val cached = LyricUtils.lyricsCache[mediaId] ?: run {
            val lyricPrefs = activity.getSharedPreferences("lyrics_disk_cache", Context.MODE_PRIVATE)
            val cachedJson = lyricPrefs.getString(mediaId, null)
            if (cachedJson != null) {
                try {
                    val type = object : com.google.gson.reflect.TypeToken<List<LrcLine>>() {}.type
                    val diskLines: List<LrcLine> = com.google.gson.Gson().fromJson(cachedJson, type)
                    LyricUtils.lyricsCache[mediaId] = diskLines
                    diskLines
                } catch (e: Exception) { null }
            } else null
        }

        if (cached != null) {
            lyricsAdapter?.lines = cached
        } else {
            val embeddedLyrics = extras?.getString("lyrics")
            if (!embeddedLyrics.isNullOrBlank()) {
                val lines = LrcParser.parse(embeddedLyrics)
                if (lines.isNotEmpty()) {
                    LyricUtils.lyricsCache[mediaId] = lines
                    lyricsAdapter?.lines = lines
                } else {
                    lyricsAdapter?.lines = emptyList()
                    syncNeteaseData(item)
                }
            } else {
                lyricsAdapter?.lines = emptyList()
                syncNeteaseData(item)
            }
        }
    }

    private fun syncNeteaseData(item: MediaItem) {
        val mediaId = item.mediaId
        val metadata = item.mediaMetadata
        val durationMs = controller.duration.let { if (it > 0) it else metadata.extras?.getLong("duration_ms") ?: 0L }
        
        activity.lifecycleScope.launch {
            LyricUtils.fetchNeteaseMetadata(
                context = activity,
                itemId = mediaId,
                title = metadata.title?.toString(),
                artist = metadata.artist?.toString(),
                album = metadata.albumTitle?.toString(),
                durationMs = durationMs,
                onCoverReady = { source ->
                    if (controller.currentMediaItem?.mediaId == mediaId) {
                        ivAlbumArt?.load(source) {
                            crossfade(600)
                            placeholder(R.drawable.disk)
                        }
                        updateBlurBackground(source)
                    }
                },
                onLyricsReady = { lines ->
                    if (controller.currentMediaItem?.mediaId == mediaId) {
                        lyricsAdapter?.lines = lines
                    }
                }
            )
        }
    }

    private fun updateBlurBackground(source: Any?) {
        if (source == null) return
        (activity as? HomeActivity)?.findViewById<ImageView>(R.id.ivBlurBackground)?.load(source) {
            crossfade(600)
            transformations(BlurTransformation(activity, 25, 3))
        }
    }

    private fun updateAudioQuality(item: MediaItem) {
        val extras = item.mediaMetadata.extras ?: return
        val isDark = (activity as? HomeActivity)?.isDarkForce() ?: true
        val codec = extras.getString("codec", "未知")
        val bitrate = extras.getInt("bitrate", 0) / 1000
        val isTranscoding = extras.getBoolean("is_transcoding", false)
        val qualityText = if (!isTranscoding) "⚠️ 源码播放 $codec ${bitrate}kbps" else "正在转码 $codec ${bitrate}kbps"
        tvAudioQuality?.apply {
            text = qualityText
            setTextColor(if (!isTranscoding) (if (isDark) Color.YELLOW else 0xFFF57C00.toInt()) else (if (isDark) 0x99FFFFFF.toInt() else 0x99000000.toInt()))
            visibility = View.VISIBLE
        }
    }

    private fun setupQualityClick() {
        var qualityClickCount = 0
        var lastClickTime = 0L
        tvAudioQuality?.setOnClickListener {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastClickTime < 500) qualityClickCount++ else qualityClickCount = 1
            lastClickTime = currentTime
            if (qualityClickCount >= 5) {
                qualityClickCount = 0
                MediaItemUtils.isForceDirectMode = !MediaItemUtils.isForceDirectMode
                val currentPos = controller.currentPosition
                val currentItem = controller.currentMediaItem ?: return@setOnClickListener
                val itemId = currentItem.mediaMetadata.extras?.getString("item_id") ?: return@setOnClickListener
                val service = RetrofitClient.getEmbyApiService(activity) ?: return@setOnClickListener
                activity.lifecycleScope.launch {
                    try {
                        val prefs = activity.getSharedPreferences("embysic_prefs", Context.MODE_PRIVATE)
                        val serverUrl = prefs.getString("server_url", "") ?: ""
                        val accessToken = prefs.getString("access_token", "") ?: ""
                        val userId = prefs.getString("user_id", "") ?: ""
                        val embyItem = service.getItem(userId, itemId, "MediaBrowser Token=\"$accessToken\"")
                        val newItem = MediaItemUtils.buildMediaItem(activity, embyItem, serverUrl, accessToken, userId, forceDirect = MediaItemUtils.isForceDirectMode)
                        controller.setMediaItem(newItem)
                        controller.seekTo(currentPos)
                        controller.prepare()
                        controller.play()
                        updateMetadata(newItem)
                    } catch (e: Exception) { e.printStackTrace() }
                }
            }
        }
    }

    @android.annotation.SuppressLint("ClickableViewAccessibility")
    private fun setupVolumeTouch() {
        var startX = 0f
        var startVolume = 0
        bottomTouchArea?.setOnTouchListener { v, event ->
            val audioManager = activity.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager ?: return@setOnTouchListener false
            val maxVolume = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
            val currentVolume = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    startX = event.x
                    startVolume = currentVolume
                    sbVolumeHint?.max = maxVolume
                    sbVolumeHint?.progress = currentVolume
                    handler.removeCallbacks(hideVolumeHintRunnable)
                    llVolumeHint?.alpha = 1f
                    llVolumeHint?.visibility = View.VISIBLE
                    volumeDotView?.setTouchPosition(event.x, event.y, true)
                    volumeDotView?.animate()?.alpha(1f)?.setDuration(200)?.start()
                    true
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.x - startX
                    val sensitivityWidth = v.width / 3f
                    val targetVolume = (startVolume + (deltaX / sensitivityWidth * maxVolume).toInt()).coerceIn(0, maxVolume)
                    audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, targetVolume, 0)
                    sbVolumeHint?.progress = targetVolume
                    volumeDotView?.setTouchPosition(event.x, event.y, true)
                    true
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    handler.postDelayed(hideVolumeHintRunnable, 2000)
                    volumeDotView?.setTouchPosition(event.x, event.y, false)
                    volumeDotView?.animate()?.alpha(0f)?.setDuration(200)?.start()
                    true
                }
                else -> false
            }
        }
    }

    private fun updateUIProgress(player: Player) {
        val duration = player.duration.let { if (it > 0) it else player.currentMediaItem?.mediaMetadata?.extras?.getLong("duration_ms") ?: 0L }
        val current = player.currentPosition
        if (duration > 0) {
            seekBar?.max = duration.toInt()
            seekBar?.progress = current.toInt()
            tvTotalTime?.text = formatTime(duration)
            tvCurrentTime?.text = formatTime(current)
            val isUserScrolling = (rvLyricsRight?.getTag(R.id.bottom_container) as? Boolean) ?: false
            lyricsAdapter?.updateActiveLine(current)?.let { index ->
                if (index != -1 && !isUserScrolling) {
                    (rvLyricsRight?.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(index, (rvLyricsRight?.height ?: 0) / 4)
                }
            }
        }
    }

    private fun updateNeedle(playing: Boolean) {
        ivNeedle?.animate()?.rotation(if (playing) -8f else -35f)?.setDuration(400)?.start()
    }

    private fun togglePlayMode() {
        val isShuffle = controller.shuffleModeEnabled
        val repeatMode = controller.repeatMode
        val nextShuffle: Boolean
        val nextRepeat: Int
        val hint: String
        when {
            isShuffle -> { nextShuffle = false; nextRepeat = Player.REPEAT_MODE_ONE; hint = activity.getString(R.string.repeat_one) }
            repeatMode == Player.REPEAT_MODE_ONE -> { nextShuffle = false; nextRepeat = Player.REPEAT_MODE_ALL; hint = activity.getString(R.string.repeat_all) }
            else -> { nextShuffle = true; nextRepeat = Player.REPEAT_MODE_ALL; hint = activity.getString(R.string.shuffle) }
        }
        controller.shuffleModeEnabled = nextShuffle
        controller.repeatMode = nextRepeat
        if (isShuffle != nextShuffle) (activity as? HomeActivity)?.updatePlaylistByMode(nextShuffle)
        updatePlayModeUI(nextShuffle, nextRepeat, hint)
    }

    private fun updatePlayModeIcon() { updatePlayModeUI(controller.shuffleModeEnabled, controller.repeatMode, null) }

    private fun updatePlayModeUI(shuffleEnabled: Boolean, repeatMode: Int, hint: String?) {
        val resId = when {
            shuffleEnabled -> R.drawable.ic_shuffle_vector
            repeatMode == Player.REPEAT_MODE_ONE -> R.drawable.ic_repeat_one_vector
            else -> R.drawable.ic_repeat_vector
        }
        btnPlayMode?.setImageResource(resId)
        val text = hint ?: when {
            shuffleEnabled -> activity.getString(R.string.shuffle)
            repeatMode == Player.REPEAT_MODE_ONE -> activity.getString(R.string.repeat_one)
            else -> activity.getString(R.string.repeat_all)
        }
        showPlayModeHint(text)
    }

    private fun showPlayModeHint(text: String) {
        tvPlayModeHint?.text = text
        tvPlayModeHint?.isVisible = true
        tvPlayModeHint?.alpha = 1f
        handler.removeCallbacks(hidePlayModeHintRunnable)
        handler.postDelayed(hidePlayModeHintRunnable, 2000)
    }

    private val hidePlayModeHintRunnable = Runnable {
        tvPlayModeHint?.animate()?.alpha(0f)?.setDuration(500)?.withEndAction { tvPlayModeHint?.isVisible = false }?.start()
    }

    private val hideVolumeHintRunnable = Runnable {
        llVolumeHint?.animate()?.alpha(0f)?.setDuration(500)?.withEndAction { llVolumeHint?.visibility = View.INVISIBLE }?.start()
    }

    fun onConfigurationChanged() { updateMetadata(controller.currentMediaItem) }

    private fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }

    private fun updateFavoriteIcon(isFavorite: Boolean) {
        btnMore?.setImageResource(if (isFavorite) R.drawable.ic_heart else R.drawable.ic_heart_border)
        btnMore?.imageTintList = android.content.res.ColorStateList.valueOf(if (isFavorite) Color.RED else (if ((activity as? HomeActivity)?.isDarkForce() == true) Color.WHITE else Color.BLACK))
    }

    private fun toggleFavorite() {
        val currentItem = controller.currentMediaItem ?: return
        val itemId = currentItem.mediaId
        val extras = currentItem.mediaMetadata.extras ?: return
        val isFavorite = extras.getBoolean("is_favorite", false)
        val service = RetrofitClient.getEmbyApiService(activity) ?: return
        
        activity.lifecycleScope.launch {
            try {
                val prefs = activity.getSharedPreferences("embysic_prefs", Context.MODE_PRIVATE)
                val userId = prefs.getString("user_id", "") ?: ""
                val accessToken = prefs.getString("access_token", "") ?: ""
                val authHeader = "MediaBrowser Token=\"$accessToken\""
                
                // Calculate position for animation before potential UI changes
                val loc = IntArray(2)
                btnMore?.getLocationInWindow(loc)
                val centerX = loc[0] + (btnMore?.width ?: 0) / 2f
                val centerY = loc[1] + (btnMore?.height ?: 0) / 2f

                if (isFavorite) {
                    service.unmarkFavorite(userId, itemId, authHeader)
                    heartLayout?.shatterHeart(centerX, centerY)
                } else {
                    service.markFavorite(userId, itemId, authHeader)
                    repeat(3) {
                        handler.postDelayed({
                            heartLayout?.addHeart(centerX, centerY)
                        }, it * 100L)
                    }
                }
                
                extras.putBoolean("is_favorite", !isFavorite)
                updateFavoriteIcon(!isFavorite)
            } catch (e: Exception) { e.printStackTrace() }
        }
    }
}
