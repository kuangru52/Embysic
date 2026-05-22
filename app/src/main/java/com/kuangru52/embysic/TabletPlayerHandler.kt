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
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
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
    }

    private fun updateMetadata(item: MediaItem?) {
        if (item == null) {
            tvTitle?.text = "未在播放"
            tvArtist?.text = ""
            ivAlbumArt?.setImageResource(R.drawable.disk)
            ivBlurBackground?.setImageDrawable(null)
            return
        }
        val metadata = item.mediaMetadata
        tvTitle?.apply {
            text = metadata.title ?: "Unknown"
            isSelected = true // 开启跑马灯
            requestFocus()
        }
        tvArtist?.apply {
            text = metadata.artist ?: "Unknown Artist"
            isSelected = true
            requestFocus()
        }
        
        val mediaId = item.mediaId
        val artworkUri = metadata.artworkUri

        // 封面加载策略：Emby原生(内置/服务器) > 本地网络缓存 > 实时网络搜索
        ivAlbumArt?.load(artworkUri) {
            crossfade(true)
            placeholder(R.drawable.disk)
            error(R.drawable.disk)
            listener(
                onSuccess = { _, _ ->
                    ivBlurBackground?.load(artworkUri) {
                        crossfade(true)
                        transformations(BlurTransformation(activity, 25, 3))
                    }
                },
                onError = { _, _ ->
                    // 原生封面失败，尝试读取缓存的网络封面
                    val prefs = activity.getSharedPreferences("netease_covers", Context.MODE_PRIVATE)
                    val cachedCover = prefs.getString(mediaId, null)
                    if (cachedCover != null) {
                        val cachedUri = android.net.Uri.parse(cachedCover)
                        ivAlbumArt?.load(cachedUri) {
                            crossfade(true)
                            placeholder(R.drawable.disk)
                            error(R.drawable.disk)
                        }
                        ivBlurBackground?.load(cachedUri) {
                            crossfade(true)
                            transformations(BlurTransformation(activity, 25, 3))
                        }
                    } else {
                        // 既没有原生也没有缓存，搜索网络
                        searchNeteaseCover(mediaId, metadata.title?.toString(), metadata.artist?.toString())
                    }
                }
            )
        }

        val extras = metadata.extras
        updateFavoriteIcon(extras?.getBoolean("is_favorite", false) ?: false)
        updateAudioQuality(item)
        
        // 加载歌词逻辑
        val cached = PlayerDialogFragment.lyricsCache[mediaId]
        if (cached != null) {
            lyricsAdapter?.lines = cached
        } else {
            // 检查内置标签
            val embeddedLyrics = extras?.getString("lyrics")
            if (!embeddedLyrics.isNullOrBlank()) {
                val lines = LrcParser.parse(embeddedLyrics)
                if (lines.isNotEmpty()) {
                    PlayerDialogFragment.lyricsCache[mediaId] = lines
                    lyricsAdapter?.lines = lines
                } else {
                    lyricsAdapter?.lines = emptyList()
                    preloadLyrics(mediaId, metadata.title?.toString(), metadata.artist?.toString())
                }
            } else {
                lyricsAdapter?.lines = emptyList()
                preloadLyrics(mediaId, metadata.title?.toString(), metadata.artist?.toString())
            }
        }
    }

    private fun searchNeteaseCover(mediaId: String, title: String?, artist: String?) {
        activity.lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val neteaseApi = retrofit2.Retrofit.Builder()
                .baseUrl("https://music.163.com/")
                .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
                .build()
                .create(NeteaseApiService::class.java)

            val cleanedTitle = title?.replace(Regex("\\s*[([\\[].*[\\])]]"), "")?.trim() ?: ""
            val query = if (artist.isNullOrBlank() || artist == "未知歌手") cleanedTitle else "$cleanedTitle $artist"
            if (query.isEmpty()) return@launch

            try {
                val searchResponse = neteaseApi.searchSong(query)
                val song = searchResponse.result?.songs?.firstOrNull() ?: return@launch
                val detailResponse = neteaseApi.getSongDetail("[{\"id\":${song.id}}]")
                val picUrl = detailResponse.songs?.firstOrNull()?.al?.picUrl?.replace("http://", "https://")
                
                if (!picUrl.isNullOrEmpty()) {
                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                        if (controller.currentMediaItem?.mediaId == mediaId) {
                            activity.getSharedPreferences("netease_covers", Context.MODE_PRIVATE)
                                .edit().putString(mediaId, picUrl).apply()
                            
                            ivAlbumArt?.load(picUrl) {
                                crossfade(true)
                            }
                            ivBlurBackground?.load(picUrl) {
                                crossfade(true)
                                transformations(BlurTransformation(activity, 25, 3))
                            }
                        }
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun updateFavoriteIcon(isFavorite: Boolean) {
        btnMore?.setImageResource(if (isFavorite) R.drawable.ic_heart else R.drawable.ic_heart_border)
        btnMore?.imageTintList = android.content.res.ColorStateList.valueOf(
            if (isFavorite) Color.RED else 0x66FFFFFF.toInt()
        )
    }

    private fun toggleFavorite() {
        val mediaItem = controller.currentMediaItem ?: return
        val itemId = mediaItem.mediaMetadata.extras?.getString("item_id") ?: return
        val isFavorite = mediaItem.mediaMetadata.extras?.getBoolean("is_favorite", false) ?: false
        
        val prefs = activity.getSharedPreferences("embysic_prefs", Context.MODE_PRIVATE)
        val userId = prefs.getString("user_id", "") ?: ""
        val accessToken = prefs.getString("access_token", "") ?: ""
        val authHeader = "MediaBrowser Token=\"$accessToken\""

        activity.lifecycleScope.launch {
            try {
                val loc = IntArray(2)
                btnMore?.getLocationInWindow(loc)
                val centerX = loc[0] + (btnMore?.width ?: 0) / 2f
                val centerY = loc[1] + (btnMore?.height ?: 0) / 2f

                if (isFavorite) {
                    EmbyApiService.getService(activity).unmarkFavorite(userId, itemId, authHeader)
                    heartLayout?.shatterHeart(centerX, centerY)
                } else {
                    EmbyApiService.getService(activity).markFavorite(userId, itemId, authHeader)
                    repeat(5) {
                        handler.postDelayed({
                            heartLayout?.addHeart(centerX, centerY)
                        }, it * 100L)
                    }
                }
                mediaItem.mediaMetadata.extras?.putBoolean("is_favorite", !isFavorite)
                updateFavoriteIcon(!isFavorite)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun updateAudioQuality(item: MediaItem?) {
        val extras = item?.mediaMetadata?.extras ?: return
        val codec = extras.getString("codec") ?: ""
        val bitrate = extras.getInt("bitrate", 0) / 1000
        val isTranscoding = extras.getBoolean("is_transcoding", false)

        if (!isTranscoding) {
            tvAudioQuality?.text = activity.getString(R.string.source_playback, codec, bitrate)
            tvAudioQuality?.setTextColor(Color.YELLOW)
        } else {
            tvAudioQuality?.text = activity.getString(R.string.transcoding, codec, bitrate)
            tvAudioQuality?.setTextColor(0xB3FFFFFF.toInt())
        }
        tvAudioQuality?.visibility = View.VISIBLE
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
                
                // 平板端立即重新载入当前歌曲
                val currentPos = controller.currentPosition
                val currentItem = controller.currentMediaItem ?: return@setOnClickListener
                val extras = currentItem.mediaMetadata.extras ?: return@setOnClickListener
                val itemId = extras.getString("item_id") ?: return@setOnClickListener
                
                activity.lifecycleScope.launch {
                    try {
                        val prefs = activity.getSharedPreferences("embysic_prefs", Context.MODE_PRIVATE)
                        val serverUrl = prefs.getString("server_url", "") ?: ""
                        val accessToken = prefs.getString("access_token", "") ?: ""
                        val userId = prefs.getString("user_id", "") ?: ""
                        
                        val embyItem = EmbyApiService.getService(activity).getItem(userId, itemId, "MediaBrowser Token=\"$accessToken\"")
                        
                        val newItem = MediaItemUtils.buildMediaItem(
                            embyItem, serverUrl, accessToken, userId,
                            forceDirect = MediaItemUtils.isForceDirectMode
                        )
                        
                        controller.setMediaItem(newItem)
                        controller.seekTo(currentPos)
                        controller.prepare()
                        controller.play()
                        
                        updateMetadata(newItem)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
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
                    val width = v.width
                    val deltaX = event.x - startX
                    val sensitivityWidth = width / 3f
                    val volumeDelta = (deltaX / sensitivityWidth * maxVolume).toInt()
                    val targetVolume = (startVolume + volumeDelta).coerceIn(0, maxVolume)
                    
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

    private fun preloadLyrics(mediaId: String, title: String?, artist: String?) {
        val lyricPrefs = activity.getSharedPreferences("lyrics_disk_cache", Context.MODE_PRIVATE)
        val cachedJson = lyricPrefs.getString(mediaId, null)
        if (cachedJson != null) {
            try {
                val type = object : com.google.gson.reflect.TypeToken<List<LrcLine>>() {}.type
                val diskLines: List<LrcLine> = com.google.gson.Gson().fromJson(cachedJson, type)
                PlayerDialogFragment.lyricsCache[mediaId] = diskLines
                if (controller.currentMediaItem?.mediaId == mediaId) {
                    lyricsAdapter?.lines = diskLines
                }
                return
            } catch (e: Exception) { e.printStackTrace() }
        }

        activity.lifecycleScope.launch {
            try {
                val prefs = activity.getSharedPreferences("embysic_prefs", android.content.Context.MODE_PRIVATE)
                val accessToken = prefs.getString("access_token", "") ?: ""
                val authHeader = "MediaBrowser Token=\"$accessToken\""
                
                val response = EmbyApiService.getService(activity).getLyrics(mediaId, authHeader)
                val rawLines = response.Lines ?: response.Lyrics ?: response.LyricLines
                @Suppress("UNCHECKED_CAST")
                val actualLines = rawLines as? List<LyricLine>
                
                if (!actualLines.isNullOrEmpty()) {
                    val metadata = mutableListOf(LrcLine(-1, title ?: "Unknown"), LrcLine(-1, artist ?: "Unknown"))
                    val lyrics = actualLines.map { LrcLine(it.StartTicks?.div(10000) ?: it.Start?.toLong()?.div(10000) ?: 0L, it.Text ?: "") }
                        .filter { it.text.isNotBlank() }.sortedBy { it.timeMs }
                    val finalLines = metadata + lyrics
                    
                    PlayerDialogFragment.lyricsCache[mediaId] = finalLines
                    activity.getSharedPreferences("lyrics_disk_cache", Context.MODE_PRIVATE).edit {
                        putString(mediaId, com.google.gson.Gson().toJson(finalLines))
                    }
                    if (controller.currentMediaItem?.mediaId == mediaId) {
                        lyricsAdapter?.lines = finalLines
                    }
                } else {
                    searchNeteaseLyrics(mediaId, title, artist)
                }
            } catch (e: Exception) { 
                Log.e("TabletPlayer", "Load lyrics failed: ${e.message}")
                searchNeteaseLyrics(mediaId, title, artist)
            }
        }
    }

    private fun searchNeteaseLyrics(mediaId: String, title: String?, artist: String?) {
        val cleanedTitle = title?.replace(Regex("\\s*([(\\[].*?[)\\]])"), "")?.trim() ?: ""
        val isUnknown = artist.isNullOrBlank() || artist.contains("未知") || artist.contains("Unknown")
        val query = if (isUnknown) cleanedTitle else "$cleanedTitle $artist"
        if (query.isEmpty()) return

        activity.lifecycleScope.launch {
            val neteaseApi = retrofit2.Retrofit.Builder()
                .baseUrl("https://music.163.com/")
                .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
                .build()
                .create(NeteaseApiService::class.java)

            try {
                // 增加搜索限制到 20 条，以便从中择优
                val searchResponse = neteaseApi.searchSong(query, limit = 20)
                val songs = searchResponse.result?.songs ?: throw Exception("Not found")

                // 智能匹配算法
                val currentDuration = controller.duration
                val bestMatch = LyricUtils.findBestMatch(songs, cleanedTitle, artist, currentDuration)
                    ?: throw Exception("No suitable match")

                val lrcResponse = neteaseApi.getLyric(bestMatch.id)
                val lyricText = lrcResponse.lrc?.lyric
                if (!lyricText.isNullOrEmpty()) {
                    val metadata = mutableListOf(LrcLine(-1, title ?: "Unknown"), LrcLine(-1, artist ?: "Unknown"), LrcLine(-1, "来源: 网易云音乐 (智能匹配)"))
                    val mainLyrics = LrcParser.parse(lyricText)
                    
                    val tlyricText = lrcResponse.tlyric?.lyric
                    val finalLines = if (!tlyricText.isNullOrBlank()) {
                        metadata + LyricUtils.mergeLyrics(mainLyrics, LrcParser.parse(tlyricText))
                    } else {
                        metadata + mainLyrics
                    }

                    PlayerDialogFragment.lyricsCache[mediaId] = finalLines
                    activity.getSharedPreferences("lyrics_disk_cache", Context.MODE_PRIVATE).edit {
                        putString(mediaId, com.google.gson.Gson().toJson(finalLines))
                    }
                    if (controller.currentMediaItem?.mediaId == mediaId) {
                        lyricsAdapter?.lines = finalLines
                    }
                }
            } catch (e: Exception) {
                // 如果带歌手搜不到，尝试只搜标题
                if (!isUnknown && cleanedTitle.isNotEmpty() && !query.equals(cleanedTitle)) {
                    searchNeteaseLyrics(mediaId, cleanedTitle, null)
                }
            }
        }
    }

    private fun updateUIProgress(player: Player) {
        val duration = player.duration
        val current = player.currentPosition
        if (duration > 0) {
            seekBar?.max = duration.toInt()
            seekBar?.progress = current.toInt()
            tvTotalTime?.text = formatTime(duration)
            tvCurrentTime?.text = formatTime(current)
            
            val isUserScrolling = (rvLyricsRight?.getTag(R.id.bottom_container) as? Boolean) ?: false
            lyricsAdapter?.updateActiveLine(current)?.let { index ->
                if (index != -1 && !isUserScrolling) {
                    (rvLyricsRight?.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(index, (rvLyricsRight?.height ?: 0) / 3)
                }
            }
        } else {
            // Fallback for some streams where duration isn't immediately available
            val extras = player.currentMediaItem?.mediaMetadata?.extras
            val injectDuration = extras?.getLong("duration_ms") ?: 0L
            if (injectDuration > 0) {
                seekBar?.max = injectDuration.toInt()
                seekBar?.progress = current.toInt()
                tvTotalTime?.text = formatTime(injectDuration)
                tvCurrentTime?.text = formatTime(current)
                
                val isUserScrolling = (rvLyricsRight?.getTag(R.id.bottom_container) as? Boolean) ?: false
                lyricsAdapter?.updateActiveLine(current)?.let { index ->
                    if (index != -1 && !isUserScrolling) {
                        (rvLyricsRight?.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(index, (rvLyricsRight?.height ?: 0) / 3)
                    }
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
        
        // 目标模式确定
        val nextShuffle: Boolean
        val nextRepeat: Int
        val hint: String

        when {
            isShuffle -> {
                nextShuffle = false
                nextRepeat = Player.REPEAT_MODE_ONE
                hint = activity.getString(R.string.repeat_one)
            }
            repeatMode == Player.REPEAT_MODE_ONE -> {
                nextShuffle = false
                nextRepeat = Player.REPEAT_MODE_ALL
                hint = activity.getString(R.string.repeat_all)
            }
            else -> {
                nextShuffle = true
                nextRepeat = Player.REPEAT_MODE_ALL
                hint = activity.getString(R.string.shuffle)
            }
        }

        // 1. 立即更新控制器状态
        controller.shuffleModeEnabled = nextShuffle
        controller.repeatMode = nextRepeat

        // 2. 立即更新 UI，不等待回调，解决状态同步延迟导致的图标错误
        updatePlayModeUI(nextShuffle, nextRepeat, hint)
    }

    private fun updatePlayModeIcon() {
        // 用于外部（如初始化或监听到变化）调用的通用刷新
        updatePlayModeUI(controller.shuffleModeEnabled, controller.repeatMode, null)
    }

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
        tvPlayModeHint?.animate()?.alpha(0f)?.setDuration(500)?.withEndAction {
            tvPlayModeHint?.isVisible = false
        }?.start()
    }

    private val hideVolumeHintRunnable = Runnable {
        llVolumeHint?.animate()?.alpha(0f)?.setDuration(500)?.withEndAction {
            llVolumeHint?.visibility = View.INVISIBLE
        }?.start()
    }

    private fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}