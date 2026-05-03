package com.kuangru52.embysic

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.RectF
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.core.content.edit
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import coil.ImageLoader
import coil.load
import coil.request.ImageRequest
import coil.request.SuccessResult
import jp.wasabeef.transformers.coil.BlurTransformation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class PlayerDialogFragment : DialogFragment() {

    private var player: Player? = null
    private var apiService: EmbyApiService? = null
    private val neteaseApi = RetrofitClient.neteaseApi
    
    private lateinit var ivBlurBackground: ImageView
    private lateinit var ivAlbumArt: ImageView
    private lateinit var ivNeedle: ImageView
    private lateinit var tvTitle: TextView
    private lateinit var tvArtist: TextView
    private lateinit var tvCurrentTime: TextView
    private lateinit var tvTotalTime: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var btnPlayPause: ImageButton
    private lateinit var btnPrev: ImageButton
    private lateinit var btnNext: ImageButton
    private lateinit var btnPlayMode: ImageButton
    private lateinit var btnMore: ImageButton
    private lateinit var tvAudioQuality: TextView
    private lateinit var tvPlayModeHint: TextView
    private lateinit var rlDisc: View
    private lateinit var rlDiscContainer: View
    private lateinit var rvLyrics: RecyclerView
    
    private val lyricsAdapter = LyricsAdapter { timeMs ->
        player?.seekTo(timeMs)
    }
    private val lyricsCache = ConcurrentHashMap<String, List<LrcLine>>()
    
    private val handler = Handler(Looper.getMainLooper())
    private var discAnimator: android.animation.ObjectAnimator? = null
    private var lastActiveLineIndex = -1
    
    private var clickCount = 0
    private var lastClickTime = 0L
    private var isSwitchingMode = false
    
    private var pendingPlayMode: String? = null
    private var playModeJob: Job? = null

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (isSwitchingMode) return
            Log.d("PlayerDialog", "onMediaItemTransition: ${mediaItem?.mediaMetadata?.title}")
            
            // 切歌时针尖动画：抬起 -> 切换 -> 放下
            ivNeedle.animate()
                .rotation(-30f)
                .setDuration(200)
                .withEndAction {
                    updateUI()
                    // 如果播放器状态不是 IDLE 且有项目，则放下磁针
                    if (player?.playbackState != Player.STATE_IDLE && (player?.mediaItemCount ?: 0) > 0) {
                        updateNeedle(player?.isPlaying == true)
                    }
                }
                .start()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isSwitchingMode) return
            Log.d("PlayerDialog", "onIsPlayingChanged: $isPlaying")
            updatePlayPauseButton(isPlaying)
            updateAnimationState(isPlaying, player?.playbackState ?: Player.STATE_IDLE)
            updateNeedle(isPlaying)
        }

        override fun onPlaybackStateChanged(state: Int) {
            if (isSwitchingMode) return
            Log.d("PlayerDialog", "onPlaybackStateChanged: $state")
            player?.let { 
                updatePlayPauseButton(it.isPlaying)
                updateAnimationState(it.isPlaying, state)
            }
            if (state == Player.STATE_READY) {
                val duration = player?.duration ?: 0L
                tvTotalTime.text = formatTime(duration)
                seekBar.max = duration.toInt()
                updateUI() // 状态就绪后再次尝试更新UI
            }
        }

        override fun onMediaMetadataChanged(mediaMetadata: androidx.media3.common.MediaMetadata) {
            Log.d("PlayerDialog", "onMediaMetadataChanged: ${mediaMetadata.title}")
            updateUI()
        }

        override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
            updateUI()
        }
    }

    private val updateProgressAction = object : Runnable {
        override fun run() {
            player?.let { p ->
                if (p.playbackState != Player.STATE_IDLE && p.playbackState != Player.STATE_ENDED) {
                    val currentPos = p.currentPosition
                    seekBar.progress = currentPos.toInt()
                    tvCurrentTime.text = formatTime(currentPos)
                    
                    if (rvLyrics.isVisible) {
                        val activeIndex = lyricsAdapter.updateActiveLine(currentPos)
                        if (activeIndex != -1 && activeIndex != lastActiveLineIndex) {
                            lastActiveLineIndex = activeIndex
                            rvLyrics.smoothScrollToPosition(activeIndex)
                        }
                    }

                    // v1.45: 优化播放模式切换卡顿。如果存在待处理模式，在歌曲结束前 2 秒自动触发静默切换
                    if (pendingPlayMode != null) {
                        val duration = p.duration
                        if (duration > 0) {
                            if (duration - currentPos < 2000) {
                                Log.d("PlayerDialog", "Auto-applying pending mode: $pendingPlayMode")
                                checkAndApplyPendingPlayMode()
                            }
                        }
                    }
                }
            }
            handler.postDelayed(this, 500)
        }
    }

    private fun startRotation() {
        if (discAnimator == null) {
            discAnimator = android.animation.ObjectAnimator.ofFloat(rlDisc, "rotation", 0f, 360f).apply {
                duration = 20000
                repeatCount = android.animation.ValueAnimator.INFINITE
                interpolator = android.view.animation.LinearInterpolator()
            }
        }
        if (discAnimator?.isStarted == false) {
            discAnimator?.start()
        } else if (discAnimator?.isPaused == true) {
            discAnimator?.resume()
        }
    }

    private fun stopRotation() {
        discAnimator?.pause()
    }

    private val hideHintRunnable = Runnable {
        tvPlayModeHint.visibility = View.GONE
    }

    private val downloadProgressListener = { songId: String, progress: Int ->
        if (isAdded && player?.currentMediaItem?.mediaId == songId) {
            view?.post {
                if (progress < 100) {
                    tvAudioQuality.text = "正在下载 $progress%"
                } else {
                    updateAudioQualityText()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.FullScreenDialog)
        apiService = RetrofitClient.getEmbyApiService(requireContext())
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_player_full, container, false)
        initViews(view)
        setupRecyclerView()
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d("PlayerDialog", "onViewCreated")
        (activity as? HomeActivity)?.mediaController?.let { 
            Log.d("PlayerDialog", "onViewCreated: Found MediaController")
            setPlayer(it) 
        }
        syncStateWithPlayer()
    }

    private fun syncStateWithPlayer() {
        val p = player ?: run {
            Log.w("PlayerDialog", "syncStateWithPlayer: player is null")
            return
        }
        if (view == null) return
        
        Log.d("PlayerDialog", "syncStateWithPlayer: isPlaying=${p.isPlaying}, mediaItem=${p.currentMediaItem?.mediaMetadata?.title}")
        
        updateUI()
        updatePlayModeIcon()
        updatePlayPauseButton(p.isPlaying)
        updateNeedle(p.isPlaying)
        updateAnimationState(p.isPlaying, p.playbackState)
        
        val duration = p.duration
        if (duration > 0) {
            tvTotalTime.text = formatTime(duration)
            seekBar.max = duration.toInt()
        }
        
        handler.removeCallbacks(updateProgressAction)
        handler.post(updateProgressAction)
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundDrawable(null)
            
            // 关键：启用边缘到边缘布局
            WindowCompat.setDecorFitsSystemWindows(this, false)
            
            // 设置沉浸式状态栏和导航栏图标颜色
            WindowInsetsControllerCompat(this, decorView).apply {
                // 播放页通常背景较深，强制使用白色图标（LightStatusBars = false）
                isAppearanceLightStatusBars = false 
                isAppearanceLightNavigationBars = false
            }

            // v1.45: 将当前播放页的位置同步给 HomeActivity 的 LiquidGlass 渲染
            // 播放页是全屏覆盖，但在 R 角处我们需要背景容器配合渲染
            val width = resources.displayMetrics.widthPixels.toFloat()
            val height = resources.displayMetrics.heightPixels.toFloat()
            val rect = RectF(0f, 0f, width, height)
            (activity as? HomeActivity)?.applyDialogEffect(
                rect = rect,
                radius = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 38f, resources.displayMetrics),
                refractionAmount = -60f,
                refractionHeight = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 20f, resources.displayMetrics),
                blurRadius = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4f, resources.displayMetrics)
            )
        }
        Log.d("PlayerDialog", "onStart")
        // 再次兜底检查，确保控制器连接
        if (player == null) {
            (activity as? HomeActivity)?.mediaController?.let { 
                Log.d("PlayerDialog", "onStart: Found MediaController in HomeActivity")
                setPlayer(it) 
            }
        }
        syncStateWithPlayer()
    }

    override fun onDestroyView() {
        (activity as? HomeActivity)?.clearDialogEffect()
        handler.removeCallbacks(updateProgressAction)
        discAnimator?.cancel()
        SongDownloader.removeProgressListener(downloadProgressListener)
        super.onDestroyView()
    }

    private fun initViews(v: View) {
        ivBlurBackground = v.findViewById(R.id.ivBlurBackground)
        ivAlbumArt = v.findViewById(R.id.ivFullCover)
        ivNeedle = v.findViewById(R.id.ivNeedle)
        tvTitle = v.findViewById(R.id.tvFullTitle)
        tvArtist = v.findViewById(R.id.tvFullArtist)
        tvCurrentTime = v.findViewById(R.id.tvCurrentTime)
        tvTotalTime = v.findViewById(R.id.tvTotalTime)
        seekBar = v.findViewById(R.id.seekBar)
        btnPlayPause = v.findViewById(R.id.btnPlayPauseFull)
        btnPrev = v.findViewById(R.id.btnPrev)
        btnNext = v.findViewById(R.id.btnNext)
        btnPlayMode = v.findViewById(R.id.btnPlayMode)
        btnMore = v.findViewById(R.id.btnMore)
        tvAudioQuality = v.findViewById(R.id.tvAudioQuality)
        tvPlayModeHint = v.findViewById(R.id.tvPlayModeHint)
        rlDisc = v.findViewById(R.id.rlDisc)
        rlDiscContainer = v.findViewById(R.id.rlDiscContainer)
        rvLyrics = v.findViewById(R.id.rvLyrics)

        v.findViewById<View>(R.id.contentContainer).setOnClickListener {
            if (rvLyrics.isVisible) hideLyrics() else showLyrics()
        }

        tvTitle.setOnClickListener {
            val itemId = player?.currentMediaItem?.mediaId ?: return@setOnClickListener
            dismiss()
            (activity as? HomeActivity)?.replaceFragment(LibraryFragment().apply {
                arguments = Bundle().apply { putString("target_item_id", itemId) }
            }, "Library")
        }

        tvArtist.setOnClickListener {
            val metadata = player?.currentMediaItem?.mediaMetadata ?: return@setOnClickListener
            val artist = metadata.artist?.toString()
            val extras = metadata.extras
            val artistId = extras?.getString("artist_id")
            
            dismiss()
            if (artistId != null) {
                (activity as? HomeActivity)?.replaceFragment(LibraryFragment().apply {
                    arguments = Bundle().apply {
                        putString("artist_id", artistId)
                        putString("artist_name", artist)
                    }
                }, "Library")
            } else {
                val itemId = player?.currentMediaItem?.mediaId ?: return@setOnClickListener
                (activity as? HomeActivity)?.replaceFragment(LibraryFragment().apply {
                    arguments = Bundle().apply { putString("target_item_id", itemId) }
                }, "Library")
            }
        }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) tvCurrentTime.text = formatTime(progress.toLong())
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                handler.removeCallbacks(updateProgressAction)
            }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                player?.seekTo(seekBar?.progress?.toLong() ?: 0L)
                handler.post(updateProgressAction)
            }
        })

        btnPlayPause.setOnClickListener {
            Log.d("PlayerDialog", "PlayPause clicked. Player: $player, isPlaying: ${player?.isPlaying}")
            player?.let { if (it.isPlaying) it.pause() else it.play() }
        }

        btnPrev.setOnClickListener {
            Log.d("PlayerDialog", "Prev clicked. Player available: ${player != null}")
            player?.let { p ->
                if (pendingPlayMode != null) {
                    checkAndApplyPendingPlayMode(skipDirection = -1)
                } else {
                    // 使用 seekToPrevious() 它会自动处理逻辑，如果不可用则手动兜底
                    if (p.hasPreviousMediaItem() || p.currentPosition > 5000) {
                        p.seekToPrevious()
                    } else if (p.mediaItemCount > 0) {
                        // 循环到最后一首
                        p.seekTo(p.mediaItemCount - 1, 0)
                    }
                    p.play()
                }
            }
        }

        btnNext.setOnClickListener {
            Log.d("PlayerDialog", "Next clicked. Player available: ${player != null}")
            player?.let { p ->
                if (pendingPlayMode != null) {
                    checkAndApplyPendingPlayMode(skipDirection = 1)
                } else {
                    if (p.hasNextMediaItem()) {
                        p.seekToNextMediaItem()
                    } else if (p.mediaItemCount > 0) {
                        // 循环到第一首
                        p.seekTo(0, 0)
                    }
                    p.play()
                }
            }
        }

        btnPlayMode.setOnClickListener { togglePlayMode() }
        btnMore.setOnClickListener { toggleFavorite() }
        rlDisc.setOnClickListener { showLyrics() }

        ivNeedle.rotation = -30f
        
        tvAudioQuality.setOnClickListener {
            if (isSwitchingMode) return@setOnClickListener
            val now = System.currentTimeMillis()
            if (now - lastClickTime < 500) {
                clickCount++
            } else {
                clickCount = 1
            }
            lastClickTime = now

            if (clickCount >= 5) {
                clickCount = 0
                val p = player ?: return@setOnClickListener
                val currentItem = p.currentMediaItem ?: return@setOnClickListener
                
                // 标记正在切换，防止 Listener 干扰
                isSwitchingMode = true
                
                val itemId = currentItem.mediaId
                val currentPos = p.currentPosition
                val wasPlaying = p.isPlaying
                
                MediaItemUtils.isForceDirectMode = !MediaItemUtils.isForceDirectMode
                val modeStr = if (MediaItemUtils.isForceDirectMode) "源码输出" else "转码播放"
                android.widget.Toast.makeText(requireContext(), "已切换至 $modeStr", android.widget.Toast.LENGTH_SHORT).show()
                
                lifecycleScope.launch {
                    try {
                        val prefs = requireContext().getSharedPreferences("embysic_prefs", android.content.Context.MODE_PRIVATE)
                        val serverUrl = prefs.getString("server_url", "") ?: ""
                        val accessToken = prefs.getString("access_token", "") ?: ""
                        val userId = prefs.getString("user_id", "") ?: ""
                        val auth = "MediaBrowser Client=\"Android\", Device=\"Android Phone\", DeviceId=\"123456\", Version=\"1.45\", Token=\"$accessToken\""
                        
                        // 获取最新的歌曲信息（包含可能的源码地址）
                        val song = apiService?.getItem(userId, itemId, auth)
                        if (song != null) {
                            val newMediaItem = MediaItemUtils.buildMediaItem(song, serverUrl, accessToken, userId)
                            
                            val ph = player // 再次检查
                            if (ph != null) {
                                // 使用单条原子命令：替换项目并定位
                                ph.setMediaItem(newMediaItem, currentPos)
                                ph.prepare()
                                if (wasPlaying) ph.play()
                                
                                // 立即更新一次 UI，让标签变化
                                updateUI()
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("PlayerDialog", "Failed to switch mode", e)
                    } finally {
                        // 稍微延迟释放开关，等待 Media3 内部消息循环处理掉中间状态
                        handler.postDelayed({
                            isSwitchingMode = false
                            // 确保 UI 最终状态正确
                            updateUI()
                        }, 500)
                    }
                }
            }
        }
        
        SongDownloader.addProgressListener(downloadProgressListener)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupRecyclerView() {
        rvLyrics.layoutManager = object : LinearLayoutManager(context) {
            override fun smoothScrollToPosition(recyclerView: RecyclerView, state: RecyclerView.State?, position: Int) {
                val scroller = object : LinearSmoothScroller(context) {
                    override fun getVerticalSnapPreference(): Int = SNAP_TO_START
                    
                    override fun calculateDtToFit(viewStart: Int, viewEnd: Int, boxStart: Int, boxEnd: Int, snapPreference: Int): Int {
                        // 使目标 item 居中显示
                        return (boxStart + (boxEnd - boxStart) / 2) - (viewStart + (viewEnd - viewStart) / 2)
                    }

                    override fun calculateSpeedPerPixel(displayMetrics: android.util.DisplayMetrics): Float {
                        return 150f / displayMetrics.densityDpi // 控制滚动速度
                    }
                }
                scroller.targetPosition = position
                startSmoothScroll(scroller)
            }
        }
        rvLyrics.adapter = lyricsAdapter
        
        // 使用点击监听器，但通过点击空白处隐藏歌词
        rvLyrics.setOnTouchListener { v, event ->
            if (event.action == android.view.MotionEvent.ACTION_UP) {
                val child = rvLyrics.findChildViewUnder(event.x, event.y)
                if (child == null) {
                    hideLyrics()
                    v.performClick()
                    return@setOnTouchListener true
                }
            }
            false
        }
    }

    fun setPlayer(p: Player) {
        if (this.player == p) {
            Log.d("PlayerDialog", "setPlayer: already set")
            return
        }
        this.player?.removeListener(playerListener)
        this.player = p
        p.addListener(playerListener)
        
        Log.d("PlayerDialog", "setPlayer called. isPlaying: ${p.isPlaying}, mediaItem: ${p.currentMediaItem?.mediaMetadata?.title}")
        syncStateWithPlayer()
    }

    private fun updateUI() {
        val p = player ?: return
        val mediaItem = p.currentMediaItem
        if (mediaItem == null) {
            Log.w("PlayerDialog", "updateUI: currentMediaItem is null")
            return
        }
        
        val metadata = mediaItem.mediaMetadata
        tvTitle.text = metadata.title ?: "未知曲名"
        tvArtist.text = metadata.artist ?: "未知歌手"
        
        Log.d("PlayerDialog", "Updating UI for: ${metadata.title}")

        val artworkUri = metadata.artworkUri
        ivAlbumArt.load(artworkUri) {
            crossfade(true)
            placeholder(R.drawable.cd)
            error(R.drawable.cd)
            listener(onError = { _, _ ->
                Log.d("PlayerDialog", "Artwork load failed, searching Netease")
                searchNeteaseCover(mediaItem.mediaId, metadata.title?.toString(), metadata.artist?.toString())
            })
        }
        
        if (artworkUri != null) {
            updateBlurBackground(artworkUri)
        } else {
            searchNeteaseCover(mediaItem.mediaId, metadata.title?.toString(), metadata.artist?.toString())
        }
        
        updateAudioQualityText()
        
        val extras = metadata.extras
        val isFav = extras?.getBoolean("is_favorite", false) ?: false
        updateFavoriteButton(isFav)
        
        // 预加载歌词
        preloadLyrics(
            mediaItem.mediaId, 
            metadata.title?.toString(), 
            metadata.artist?.toString()
        )
    }

    private fun updateAudioQualityText() {
        val mediaItem = player?.currentMediaItem ?: return
        val itemId = mediaItem.mediaId
        val extras = mediaItem.mediaMetadata.extras
        val isTranscoding = extras?.getBoolean("is_transcoding", false) ?: false
        val codec = extras?.getString("codec") ?: ""
        val bitrate = extras?.getInt("bitrate", 0) ?: 0

        if (SongDownloader.isDownloaded(itemId)) {
            tvAudioQuality.text = "已下载"
            tvAudioQuality.setTextColor("#B3FFFFFF".toColorInt())
        } else {
            if (!isTranscoding) {
                // 源码播放模式：添加黄色警告图标和提示文字
                tvAudioQuality.text = "⚠️ 源码播放: $codec ${bitrate / 1000}kbps"
                tvAudioQuality.setTextColor("#FFD700".toColorInt()) // 黄色/金色
            } else {
                tvAudioQuality.text = if (bitrate > 0) "正在转码: $codec ${bitrate / 1000}kbps" else ""
                tvAudioQuality.setTextColor("#B3FFFFFF".toColorInt())
            }
        }
    }

    private fun updatePlayPauseButton(isPlaying: Boolean) {
        btnPlayPause.setImageResource(if (isPlaying) R.drawable.ic_pause_vector else R.drawable.ic_play_vector)
    }

    private fun updateAnimationState(isPlaying: Boolean, playbackState: Int) {
        if (isPlaying && playbackState == Player.STATE_READY) {
            startRotation()
        } else {
            stopRotation()
        }
    }

    private fun updateNeedle(isPlaying: Boolean) {
        ivNeedle.animate()
            .rotation(if (isPlaying) 0f else -30f)
            .setDuration(300)
            .setInterpolator(android.view.animation.AccelerateDecelerateInterpolator())
            .start()
    }

    private fun searchNeteaseCover(itemId: String, title: String?, artist: String?) {
        lifecycleScope.launch(Dispatchers.IO) {
            val cleanedTitle = title?.replace(Regex("\\s*[(\\[].*?[)\\]]"), "")?.trim() ?: ""
            val query = if (artist.isNullOrBlank() || artist == "未知歌手") cleanedTitle else "$cleanedTitle $artist"
            if (query.isEmpty()) return@launch
            try {
                val searchResponse = neteaseApi.searchSong(query)
                val song = searchResponse.result?.songs?.firstOrNull() ?: return@launch
                
                val detailResponse = neteaseApi.getSongDetail("[{\"id\":${song.id}}]")
                val picUrl = detailResponse.songs?.firstOrNull()?.al?.picUrl?.replace("http://", "https://")
                
                if (!picUrl.isNullOrEmpty()) {
                    withContext(Dispatchers.Main) {
                        if (player?.currentMediaItem?.mediaId == itemId) {
                            saveNeteaseCoverToCache(itemId, picUrl)
                            updateMediaItemArtwork(picUrl)
                            ivAlbumArt.load(picUrl) { 
                                crossfade(true)
                                listener(onSuccess = { _, _ ->
                                    updateBlurBackground(picUrl)
                                })
                            }
                        }
                    }
                }
            } catch (e: Exception) { Log.e("Cover", "Netease search failed: ${e.message}") }
        }
    }

    private fun saveNeteaseCoverToCache(itemId: String, url: String) {
        val context = context ?: return
        val prefs = context.getSharedPreferences("netease_covers", android.content.Context.MODE_PRIVATE)
        prefs.edit { putString(itemId, url) }
    }

    private fun updateMediaItemArtwork(url: String) {
        val p = player ?: return
        val currentItem = p.currentMediaItem ?: return
        val newMetadata = currentItem.mediaMetadata.buildUpon()
            .setArtworkUri(url.toUri())
            .build()
        val newItem = currentItem.buildUpon()
            .setMediaMetadata(newMetadata)
            .build()
        
        val currentIndex = p.currentMediaItemIndex
        val currentPosition = p.currentPosition
        p.replaceMediaItem(currentIndex, newItem)
        p.seekTo(currentIndex, currentPosition)
    }

    private fun toggleFavorite() {
        val mediaItem = player?.currentMediaItem ?: return
        val itemId = mediaItem.mediaId
        val isFav = mediaItem.mediaMetadata.extras?.getBoolean("is_favorite", false) ?: false
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val prefs = context?.getSharedPreferences("embysic_prefs", android.content.Context.MODE_PRIVATE)
                val userId = prefs?.getString("user_id", "") ?: ""
                val accessToken = prefs?.getString("access_token", "") ?: ""
                val auth = "MediaBrowser Client=\"Android\", Device=\"Android Phone\", DeviceId=\"123456\", Version=\"1.45\", Token=\"$accessToken\""
                
                if (isFav) {
                    apiService?.unmarkFavorite(userId, itemId, auth)
                } else {
                    apiService?.markFavorite(userId, itemId, auth)
                }
                
                withContext(Dispatchers.Main) {
                    mediaItem.mediaMetadata.extras?.putBoolean("is_favorite", !isFav)
                    updateFavoriteButton(!isFav)
                }
            } catch (e: Exception) {
                Log.e("PlayerDialog", "Toggle favorite error: ${e.message}")
            }
        }
    }

    private fun updateFavoriteButton(isFav: Boolean) {
        btnMore.setImageResource(if (isFav) R.drawable.ic_heart else R.drawable.ic_heart_border)
        btnMore.setColorFilter(if (isFav) "#FF4444".toColorInt() else Color.WHITE)
    }

    private fun updatePlayModeIcon() {
        val p = player ?: return
        // 优先展示意图模式
        val mode = when {
            pendingPlayMode == "shuffle" -> "shuffle"
            pendingPlayMode == "folder" -> "folder"
            p.repeatMode == Player.REPEAT_MODE_ONE -> "repeat_one"
            p.shuffleModeEnabled -> "shuffle"
            else -> "folder"
        }
        
        when (mode) {
            "shuffle" -> btnPlayMode.setImageResource(R.drawable.ic_shuffle_vector)
            "repeat_one" -> btnPlayMode.setImageResource(R.drawable.ic_repeat_one_vector)
            else -> btnPlayMode.setImageResource(R.drawable.ic_repeat_vector)
        }
    }

    private fun togglePlayMode() {
        val p = player ?: return
        
        val currentMode = when {
            pendingPlayMode == "shuffle" -> "shuffle"
            pendingPlayMode == "folder" -> "folder"
            p.repeatMode == Player.REPEAT_MODE_ONE -> "repeat_one"
            p.shuffleModeEnabled -> "shuffle"
            else -> "folder"
        }

        val nextMode = when (currentMode) {
            "shuffle" -> "folder"
            "folder" -> "repeat_one"
            "repeat_one" -> "shuffle"
            else -> "folder"
        }

        playModeJob?.cancel()
        val hint: String
        
        // 保存播放模式到持久化存储
        context?.getSharedPreferences("embysic_prefs", android.content.Context.MODE_PRIVATE)?.edit {
            putString("play_mode", nextMode)
        }

        when (nextMode) {
            "folder" -> {
                pendingPlayMode = "folder"
                p.shuffleModeEnabled = false
                p.repeatMode = Player.REPEAT_MODE_ALL
                hint = "列表循环 (当前文件夹)"
                // 不再立即调用 switchToFolderRepeat，改为由进度监听器或手动切歌触发
            }
            "repeat_one" -> {
                pendingPlayMode = null
                p.shuffleModeEnabled = false
                p.repeatMode = Player.REPEAT_MODE_ONE
                hint = "单曲循环"
            }
            "shuffle" -> {
                pendingPlayMode = "shuffle"
                p.shuffleModeEnabled = true
                p.repeatMode = Player.REPEAT_MODE_ALL
                hint = "随机播放 (全库)"
            }
            else -> {
                pendingPlayMode = null
                hint = "列表循环"
            }
        }

        updatePlayModeIcon()
        showPlayModeHint(hint)
    }

    private fun checkAndApplyPendingPlayMode(skipDirection: Int = 0) {
        val mode = pendingPlayMode ?: return
        pendingPlayMode = null 
        
        Log.d("PlayerDialog", "Applying mode: $mode, skip: $skipDirection")
        
        when (mode) {
            "shuffle" -> playModeJob = switchToAllMusicShuffle(skipDirection)
            "folder" -> playModeJob = switchToFolderRepeat(skipDirection)
        }
    }

    private fun switchToAllMusicShuffle(skipDirection: Int = 0): Job? {
        val p = player ?: return null
        val currentMediaItem = p.currentMediaItem ?: return null
        val currentId = currentMediaItem.mediaId
        val currentSessionId = currentMediaItem.mediaMetadata.extras?.getString("play_session_id")
        val realItemId = if (currentId.contains("_")) currentId.substringBefore("_") else currentId
        val context = context ?: return null
        
        return lifecycleScope.launch(Dispatchers.IO) {
            try {
                val prefs = context.getSharedPreferences("embysic_prefs", android.content.Context.MODE_PRIVATE)
                val userId = prefs.getString("user_id", "") ?: ""
                val accessToken = prefs.getString("access_token", "") ?: ""
                val serverUrl = prefs.getString("server_url", "") ?: ""
                val authHeader = "MediaBrowser Client=\"Android\", Device=\"Android Phone\", DeviceId=\"123456\", Version=\"1.45\", Token=\"$accessToken\""
                
                if (userId.isEmpty() || serverUrl.isEmpty()) return@launch

                val response = apiService?.getItems(
                    userId = userId, 
                    includeItemTypes = "Audio", 
                    recursive = true, 
                    auth = authHeader,
                    sortBy = "SortName"
                )
                val allSongs = response?.Items ?: return@launch
                
                if (allSongs.isNotEmpty()) {
                    val mediaItems = allSongs.map { song ->
                        MediaItemUtils.buildMediaItem(
                            song = song,
                            serverUrl = serverUrl,
                            accessToken = accessToken,
                            userId = userId,
                            overrideSessionId = if (song.Id == realItemId) currentSessionId else null
                        )
                    }
                    
                    withContext(Dispatchers.Main) {
                        val currentPlayer = player ?: return@withContext
                        
                        // 记录当前播放位置，如果只是切换模式而不切歌，需要保持
                        val currentPos = currentPlayer.currentPosition
                        val wasPlaying = currentPlayer.isPlaying
                        
                        isSwitchingMode = true
                        
                        // 如果有 skipDirection，说明是点击下一曲/上一曲触发的，需要计算索引
                        var targetIndex = allSongs.indexOfFirst { it.Id == realItemId }
                        if (targetIndex == -1) targetIndex = 0
                        
                        currentPlayer.setMediaItems(mediaItems, targetIndex, currentPos)
                        currentPlayer.shuffleModeEnabled = true
                        
                        if (skipDirection > 0) currentPlayer.seekToNextMediaItem()
                        else if (skipDirection < 0) currentPlayer.seekToPreviousMediaItem()
                        
                        // 仅在非播放状态下调用 prepare，减少播放中的卡顿
                        if (currentPlayer.playbackState == Player.STATE_IDLE) {
                            currentPlayer.prepare()
                        }
                        if (wasPlaying || skipDirection != 0) currentPlayer.play()
                        
                        isSwitchingMode = false
                        updateUI()
                    }
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    Log.e("PlayerDialog", "Shuffle All error: ${e.message}")
                }
            }
        }
    }

    private fun switchToFolderRepeat(skipDirection: Int = 0): Job? {
        val p = player ?: return null
        val currentMediaItem = p.currentMediaItem ?: return null
        val currentId = currentMediaItem.mediaId
        val currentSessionId = currentMediaItem.mediaMetadata.extras?.getString("play_session_id")
        val realItemId = if (currentId.contains("_")) currentId.substringBefore("_") else currentId
        
        var parentId = currentMediaItem.mediaMetadata.extras?.getString("parent_id")
        val context = context ?: return null
        
        return lifecycleScope.launch(Dispatchers.IO) {
            try {
                val prefs = context.getSharedPreferences("embysic_prefs", android.content.Context.MODE_PRIVATE)
                val userId = prefs.getString("user_id", "") ?: ""
                val accessToken = prefs.getString("access_token", "") ?: ""
                val serverUrl = prefs.getString("server_url", "") ?: ""
                val authHeader = "MediaBrowser Client=\"Android\", Device=\"Android Phone\", DeviceId=\"123456\", Version=\"1.45\", Token=\"$accessToken\""
                
                if (parentId.isNullOrEmpty()) {
                    val item = apiService?.getItem(userId, realItemId, authHeader)
                    parentId = item?.ParentId
                }

                if (parentId.isNullOrEmpty()) {
                    withContext(Dispatchers.Main) {
                        p.shuffleModeEnabled = false
                        showPlayModeHint("无法识别目录，仅循环当前队列")
                    }
                    return@launch
                }
                
                val response = apiService?.getItems(
                    userId = userId,
                    parentId = parentId!!,
                    includeItemTypes = "Audio",
                    recursive = false,
                    auth = authHeader,
                    sortBy = "ParentIndexNumber,IndexNumber,SortName"
                )
                val folderSongs = response?.Items ?: return@launch
                
                if (folderSongs.isNotEmpty()) {
                    val mediaItems = folderSongs.map { song ->
                        MediaItemUtils.buildMediaItem(
                            song = song,
                            serverUrl = serverUrl,
                            accessToken = accessToken,
                            userId = userId,
                            overrideSessionId = if (song.Id == realItemId) currentSessionId else null
                        )
                    }
                    
                    withContext(Dispatchers.Main) {
                        val currentPlayer = player ?: return@withContext
                        val currentPos = currentPlayer.currentPosition
                        
                        // 找到当前歌曲在文件夹列表中的索引
                        val currentIndex = folderSongs.indexOfFirst { it.Id == realItemId }
                        
                        currentPlayer.shuffleModeEnabled = false
                        currentPlayer.repeatMode = Player.REPEAT_MODE_ALL
                        
                        isSwitchingMode = true // 防止切歌动画触发
                        currentPlayer.setMediaItems(mediaItems, if (currentIndex != -1) currentIndex else 0, currentPos)
                        
                        if (skipDirection > 0) currentPlayer.seekToNextMediaItem()
                        else if (skipDirection < 0) currentPlayer.seekToPreviousMediaItem()
                        
                        // 仅在非播放状态下调用 prepare
                        if (currentPlayer.playbackState == Player.STATE_IDLE) {
                            currentPlayer.prepare()
                        }
                        currentPlayer.play()
                        isSwitchingMode = false
                        updateUI()
                    }
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    Log.e("PlayerDialog", "Folder Repeat error: ${e.message}")
                }
            }
        }
    }

    private fun showPlayModeHint(text: String) {
        tvPlayModeHint.text = text
        tvPlayModeHint.visibility = View.VISIBLE
        handler.removeCallbacks(hideHintRunnable)
        handler.postDelayed(hideHintRunnable, 1000)
    }

    private fun updateBlurBackground(data: Any?) {
        lifecycleScope.launch {
            val context = context ?: return@launch
            val request = ImageRequest.Builder(context)
                .data(data)
                .size(300)
                .transformations(BlurTransformation(context, 25, 4))
                .crossfade(true)
                .build()
            val result = ImageLoader(context).execute(request)
            if (result is SuccessResult && isAdded) {
                ivBlurBackground.setImageDrawable(result.drawable)
                // 移除此处对状态栏颜色的干扰，保持 onStart 中的强制白色设置
            }
        }
    }

    private fun showLyrics() {
        rlDiscContainer.visibility = View.GONE
        rvLyrics.visibility = View.VISIBLE
        val mediaItem = player?.currentMediaItem ?: return
        val itemId = mediaItem.mediaId
        if (lyricsCache.containsKey(itemId)) {
            lyricsAdapter.lines = lyricsCache[itemId]!!
        } else {
            lyricsAdapter.lines = listOf(LrcLine(0, "正在加载歌词..."))
            val metadata = mediaItem.mediaMetadata
            preloadLyrics(itemId, metadata.title?.toString(), metadata.artist?.toString())
        }
    }

    private fun hideLyrics() {
        rvLyrics.visibility = View.GONE
        rlDiscContainer.visibility = View.VISIBLE
    }

    @OptIn(UnstableApi::class)
    fun getLyricsFromCache(itemId: String): List<LrcLine>? {
        return lyricsCache[itemId]
    }

    @OptIn(UnstableApi::class)
    private fun preloadLyrics(itemId: String, title: String?, artist: String?) {
        if (lyricsCache.containsKey(itemId)) return
        Log.d("Lyrics", "Fetching Netease lyrics for $itemId")
        searchNeteaseLyrics(itemId, title, artist)
    }

    private fun searchNeteaseLyrics(itemId: String, title: String?, artist: String?) {
        val cleanedTitle = title?.replace(Regex("(?i)\\s*[(\\[](?!.*feat).*?[)\\]]"), "")?.trim() ?: ""
        val isUnknown = artist.isNullOrBlank() || artist.contains("未知") || artist.contains("Unknown")
        val query = if (isUnknown) cleanedTitle else "$cleanedTitle $artist"
        if (query.isEmpty()) { showNoLyrics(itemId, title, artist); return }

        val currentDuration = player?.duration ?: 0L

        lifecycleScope.launch {
            try {
                val searchResponse = neteaseApi.searchSong(query, limit = 10)
                val songs = searchResponse.result?.songs
                
                if (songs.isNullOrEmpty()) {
                    if (!isUnknown) searchNeteaseLyrics(itemId, cleanedTitle, null)
                    else showNoLyrics(itemId, title, artist)
                    return@launch
                }

                val bestMatch = songs.maxByOrNull { song ->
                    var score = 0
                    if (song.name.equals(cleanedTitle, ignoreCase = true)) score += 15
                    else if (song.name.contains(cleanedTitle, ignoreCase = true)) score += 8
                    
                    val songArtists = song.artists?.map { it.name } ?: emptyList()
                    if (!isUnknown) {
                        if (songArtists.any { it.equals(artist, ignoreCase = true) }) score += 15
                        else if (songArtists.any { it.contains(artist, ignoreCase = true) }) score += 7
                    }
                    
                    if (currentDuration > 0 && (song.duration ?: 0) > 0) {
                        val diff = kotlin.math.abs(currentDuration - (song.duration ?: 0))
                        when {
                            diff < 2000 -> score += 20
                            diff < 5000 -> score += 10
                            diff > 30000 -> score -= 15
                        }
                    }
                    if (!cleanedTitle.contains("Live", true) && song.name.contains("Live", true)) score -= 10
                    score
                } ?: songs.first()

                val lyricResponse = neteaseApi.getLyric(bestMatch.id)
                val lrcText = lyricResponse.lrc?.lyric
                if (!lrcText.isNullOrBlank()) {
                    val metadata = mutableListOf(
                        LrcLine(-1, bestMatch.name),
                        LrcLine(-1, bestMatch.artists?.joinToString("/") { it.name } ?: (artist ?: "未知歌手")),
                        LrcLine(-1, "来源: 网易云音乐 (智能匹配)")
                    )
                    val mainLyrics = LrcParser.parse(lrcText)
                    val tlyricText = lyricResponse.tlyric?.lyric
                    val finalLines = if (!tlyricText.isNullOrBlank()) metadata + mergeLyrics(mainLyrics, LrcParser.parse(tlyricText)) else metadata + mainLyrics
                    lyricsCache[itemId] = finalLines
                    if (rvLyrics.isVisible && player?.currentMediaItem?.mediaId == itemId) {
                        lyricsAdapter.lines = finalLines
                    }
                } else { showNoLyrics(itemId, title, artist) }
            } catch (_: Exception) { 
                showNoLyrics(itemId, title, artist)
            }
        }
    }

    private fun showNoLyrics(itemId: String, title: String?, artist: String?) {
        val finalLines = listOf(LrcLine(-1, title ?: "未知曲名"), LrcLine(-1, artist ?: "未知歌手"), LrcLine(0, "未找到在线歌词"))
        lyricsCache[itemId] = finalLines
        if (rvLyrics.isVisible && player?.currentMediaItem?.mediaId == itemId) {
            lyricsAdapter.lines = finalLines
        }
    }

    private fun mergeLyrics(main: List<LrcLine>, trans: List<LrcLine>): List<LrcLine> {
        val transMap = trans.filter { it.timeMs >= 0 }.associateBy { it.timeMs }
        return main.filter { it.timeMs >= 0 }.map { line ->
            val tLine = transMap[line.timeMs]
            if (tLine != null && tLine.text.isNotBlank() && tLine.text != line.text) LrcLine(line.timeMs, "${line.text}\n${tLine.text}") else line
        }
    }

    private fun formatTime(ms: Long): String {
        val sec = (ms / 1000) % 60
        val min = (ms / (1000 * 60)) % 60
        return String.format(Locale.getDefault(), "%02d:%02d", min, sec)
    }

}
