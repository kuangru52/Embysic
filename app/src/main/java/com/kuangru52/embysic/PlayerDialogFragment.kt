package com.kuangru52.embysic

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.annotation.SuppressLint
import android.view.animation.AccelerateDecelerateInterpolator
import android.app.Dialog
import android.content.DialogInterface
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.graphics.Outline
import android.view.ViewOutlineProvider
import android.view.RoundedCorner
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.annotation.AttrRes
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.graphics.toColorInt
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import coil.ImageLoader
import coil.load
import coil.request.ImageRequest
import coil.request.SuccessResult
import jp.wasabeef.transformers.coil.BlurTransformation
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.Locale

@UnstableApi
class PlayerDialogFragment : BottomSheetDialogFragment() {

    private val neteaseApi = RetrofitClient.neteaseApi

    private lateinit var tvPlayModeHint: TextView
    private lateinit var ivCover: ImageView
    private lateinit var ivBlurBackground: ImageView
    private lateinit var tvTitle: TextView
    private lateinit var tvArtist: TextView
    private lateinit var btnPlayMode: ImageView
    private lateinit var btnPlayPause: ImageView
    private lateinit var seekBar: SeekBar
    private lateinit var tvCurrentTime: TextView
    private lateinit var tvTotalTime: TextView
    private lateinit var tvAudioQuality: TextView
    private lateinit var btnPrev: ImageView
    private lateinit var btnNext: ImageView
    private lateinit var btnMore: ImageView
    private lateinit var bottomTouchArea: View
    private lateinit var volumeDotView: VolumeDotView
    private lateinit var rlDiscContainer: View
    private lateinit var rlDisc: View
    private lateinit var cvAlbumArt: View
    private lateinit var ivNeedle: ImageView
    private var discRotation = 0f
    private val discRotationHandler = Handler(Looper.getMainLooper())
    private val discRotationRunnable = object : Runnable {
        override fun run() {
            val p = player ?: return
            if (p.isPlaying) {
                discRotation = (discRotation + 0.5f) % 360f
                rlDisc.rotation = discRotation
            }
            discRotationHandler.postDelayed(this, 30)
        }
    }
    private lateinit var rvLyrics: RecyclerView
    private lateinit var contentContainer: View
    private lateinit var flHintContainer: View
    private lateinit var pbDownload: ProgressBar
    private lateinit var llVolumeHint: View
    private lateinit var sbVolumeHint: SeekBar
    private val hideVolumeHintRunnable = Runnable { 
        llVolumeHint.animate()
            .alpha(0f)
            .setDuration(300)
            .withEndAction { 
                llVolumeHint.visibility = View.INVISIBLE // 使用 INVISIBLE 保持占位，避免布局跳变
                llVolumeHint.alpha = 1f
            }
            .start()
    }

    private lateinit var heartLayout: HeartLayout

    private val lyricsAdapter = LyricsAdapter { hideLyrics() }
    private var player: Player? = null
    fun setPlayer(player: Player) {
        this.player = player
    }

    private val handler = Handler(Looper.getMainLooper())
    private var isDragging = false
    private var isUserScrollingLyrics = false
    private val resumeAutoScrollRunnable = Runnable { 
        isUserScrollingLyrics = false
        player?.let { updateUIProgress(it) }
    }
    private var apiService: EmbyApiService? = null

    companion object {
        val lyricsCache = mutableMapOf<String, List<LrcLine>>()
    }

    private val downloadProgressListener = { itemId: String, progressPercent: Int ->
        val currentId = player?.currentMediaItem?.mediaId
        if (itemId == currentId) {
            handler.post {
                if (progressPercent == 1) {
                    pbDownload.isIndeterminate = true
                    pbDownload.visibility = View.VISIBLE
                } else if (progressPercent >= 100) {
                    pbDownload.isIndeterminate = false
                    pbDownload.progress = 100
                    handler.postDelayed({ pbDownload.visibility = View.GONE }, 2000)
                } else {
                    pbDownload.isIndeterminate = false
                    pbDownload.progress = progressPercent
                    pbDownload.visibility = View.VISIBLE
                }
            }
        }
    }

    private val updateProgressAction = object : Runnable {
        override fun run() {
            val p = player ?: return
            if (p.isPlaying) {
                updateUIProgress(p)
            }
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.Theme_PlayerBottomSheet)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.behavior.apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
            isHideable = true
            isDraggable = true
        }
        dialog.window?.let { window ->
            window.setWindowAnimations(R.style.PlayerDialogAnimation)
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            WindowCompat.setDecorFitsSystemWindows(window, false)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        return dialog
    }

    override fun onStart() {
        super.onStart()
        val dialog = dialog as? BottomSheetDialog
        dialog?.let {
            val bottomSheet = it.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.let { sheet ->
                sheet.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
                // 关键：将系统默认的 BottomSheet 背景设为透明
                sheet.setBackgroundColor(Color.TRANSPARENT)
                // 强制关闭 BottomSheet 的自动适配
                sheet.fitsSystemWindows = false
                
                val behavior = BottomSheetBehavior.from(sheet)
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true
            }

            // 核心修复：强制拦截并忽略父容器的 Insets 处理，防止它们自动把内容往下顶
            val container = it.findViewById<View>(com.google.android.material.R.id.container)
            val coordinator = it.findViewById<View>(com.google.android.material.R.id.coordinator)
            
            container?.fitsSystemWindows = false
            container?.let { c -> ViewCompat.setOnApplyWindowInsetsListener(c) { _, insets -> insets } }
            
            coordinator?.fitsSystemWindows = false
            coordinator?.let { co -> ViewCompat.setOnApplyWindowInsetsListener(co) { _, insets -> insets } }
            
            it.window?.let { window ->
                window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
                
                // 核心：强制沉浸式
                WindowCompat.setDecorFitsSystemWindows(window, false)
                window.statusBarColor = Color.TRANSPARENT
                window.navigationBarColor = Color.TRANSPARENT
                
                // 使用 NO_LIMITS 确保背景图覆盖状态栏
                window.setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }

                val controller = WindowInsetsControllerCompat(window, window.decorView)
                controller.isAppearanceLightStatusBars = false
                controller.isAppearanceLightNavigationBars = false
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_player_dialog, container, false)
        initViews(view)
        setupPlayer()
        initApiService()
        SongDownloader.addProgressListener(downloadProgressListener)
        
        val mainContent = view.findViewById<View>(R.id.mainContent)
        val statusBarSpacer = view.findViewById<View>(R.id.statusBarSpacer)
        
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            
            // 增强型状态栏高度获取
            val statusBarHeight = if (bars.top > 0) bars.top else {
                val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
                if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else (24 * resources.displayMetrics.density).toInt()
            }

            // 核心修复：使用 statusBarSpacer 物理占位，确保歌词等内容不进入状态栏区域
            // 同时 mainContent 的 paddingTop 设为 0，让背景可以延伸到状态栏（配合 DecorFitsSystemWindows(false)）
            statusBarSpacer?.layoutParams?.height = statusBarHeight
            statusBarSpacer?.requestLayout()

            mainContent.setPadding(
                mainContent.paddingLeft,
                0,
                mainContent.paddingRight,
                bars.bottom
            )

            // 处理自适应圆角
            val radius = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                val platformInsets = insets.toWindowInsets()
                val topLeft = platformInsets?.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)
                val topRight = platformInsets?.getRoundedCorner(RoundedCorner.POSITION_TOP_RIGHT)
                topLeft?.radius?.toFloat() ?: topRight?.radius?.toFloat() ?: (28 * resources.displayMetrics.density)
            } else {
                28 * resources.displayMetrics.density
            }

            v.outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, radius)
                }
            }
            v.clipToOutline = true

            insets
        }
        
        return view
    }

    private fun initApiService() {
        apiService = RetrofitClient.getEmbyApiService(requireContext())
    }

    private fun initViews(view: View) {
        ivCover = view.findViewById(R.id.ivAlbumArt)
        ivBlurBackground = view.findViewById(R.id.ivBlurBackground)
        tvTitle = view.findViewById(R.id.tvTitle)
        tvArtist = view.findViewById(R.id.tvArtist)
        
        // 开启跑马灯滚动效果
        tvTitle.isSelected = true
        tvArtist.isSelected = true
        btnPlayMode = view.findViewById(R.id.btnPlayMode)
        btnPlayPause = view.findViewById(R.id.btnPlayPause)
        seekBar = view.findViewById(R.id.seekBar)
        tvCurrentTime = view.findViewById(R.id.tvCurrentTime)
        tvTotalTime = view.findViewById(R.id.tvTotalTime)
        tvAudioQuality = view.findViewById(R.id.tvAudioQuality)
        tvPlayModeHint = view.findViewById(R.id.tvPlayModeHint)
        btnPrev = view.findViewById(R.id.btnPrev)
        btnNext = view.findViewById(R.id.btnNext)
        btnMore = view.findViewById(R.id.btnMore)
        bottomTouchArea = view.findViewById(R.id.bottomTouchArea)
        volumeDotView = view.findViewById(R.id.volumeDotView)
        rlDiscContainer = view.findViewById(R.id.rlDiscContainer)
        rlDisc = view.findViewById(R.id.rlDisc)
        cvAlbumArt = view.findViewById(R.id.cvAlbumArt)
        ivNeedle = view.findViewById(R.id.ivNeedle)
        rvLyrics = view.findViewById(R.id.rvLyrics)
        contentContainer = view.findViewById(R.id.contentContainer)
        flHintContainer = view.findViewById(R.id.flHintContainer)
        pbDownload = view.findViewById(R.id.pbDownload)
        sbVolumeHint = view.findViewById(R.id.sbVolumeHint)
        llVolumeHint = view.findViewById(R.id.llVolumeHint)
        heartLayout = view.findViewById(R.id.heartLayout)

        ivNeedle.rotation = -25f // 初始设置为暂停时的角度

        rvLyrics.layoutManager = LinearLayoutManager(context)
        rvLyrics.adapter = lyricsAdapter

        btnPlayPause.setOnClickListener {
            player?.let { if (it.isPlaying) it.pause() else it.play() }
        }
        btnPrev.setOnClickListener { 
            player?.seekToPrevious()
            player?.play()
        }
        btnNext.setOnClickListener { 
            player?.seekToNext()
            player?.play()
        }
        
        btnPlayMode.setOnClickListener { togglePlayMode() }
        btnMore.setOnClickListener { toggleFavorite() }
        rlDisc.setOnClickListener { showLyrics() }

        tvArtist.setOnClickListener {
            val currentItem = player?.currentMediaItem ?: return@setOnClickListener
            val extras = currentItem.mediaMetadata.extras ?: return@setOnClickListener
            val artistId = extras.getString("artist_id")
            val artistName = currentItem.mediaMetadata.artist?.toString() ?: "歌手"

            if (artistId != null) {
                dismiss() // 关闭播放界面
                val fragment = LibraryFragment().apply {
                    arguments = Bundle().apply {
                        putString("artist_id", artistId)
                        putString("artist_name", artistName)
                    }
                }
                (activity as? HomeActivity)?.replaceFragment(fragment, "artist_$artistId")
            }
        }

        var qualityClickCount = 0
        var lastClickTime = 0L
        tvAudioQuality.setOnClickListener {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastClickTime < 500) {
                qualityClickCount++
            } else {
                qualityClickCount = 1
            }
            lastClickTime = currentTime
            
            if (qualityClickCount >= 5) {
                qualityClickCount = 0
                MediaItemUtils.isForceDirectMode = !MediaItemUtils.isForceDirectMode
                
                // 立即重新载入当前歌曲以应用源码模式
                player?.let { p ->
                    val currentPos = p.currentPosition
                    val currentItem = p.currentMediaItem ?: return@let
                    val extras = currentItem.mediaMetadata.extras ?: return@let
                    val itemId = extras.getString("item_id") ?: return@let
                    
                    lifecycleScope.launch {
                        try {
                            val prefs = requireContext().getSharedPreferences("embysic_prefs", AppCompatActivity.MODE_PRIVATE)
                            val serverUrl = prefs.getString("server_url", "") ?: ""
                            val accessToken = prefs.getString("access_token", "") ?: ""
                            val userId = prefs.getString("user_id", "") ?: ""
                            val auth = "MediaBrowser Token=\"$accessToken\""
                            
                            val embyItem = apiService?.getItem(userId, itemId, auth) ?: return@launch
                            
                            val newItem = MediaItemUtils.buildMediaItem(
                                requireContext(),
                                embyItem, serverUrl, accessToken, userId,
                                forceDirect = MediaItemUtils.isForceDirectMode
                            )
                            
                            p.setMediaItem(newItem)
                            p.seekTo(currentPos)
                            p.prepare()
                            p.play()
                            
                            // 立即刷新 UI 显示源码信息
                            updateMetadata(newItem)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        }
        
        val llTitleContainer = view.findViewById<View>(R.id.llTitleContainer)
        llTitleContainer.setOnClickListener {
            val mediaId = player?.currentMediaItem?.mediaId ?: return@setOnClickListener
            (activity as? HomeActivity)?.let { activity ->
                dismiss()
                val fragment = LibraryFragment().apply {
                    arguments = Bundle().apply {
                        putString("target_item_id", mediaId)
                    }
                }
                activity.replaceFragment(fragment, "library_locate")
            }
        }
        
        var startX = 0f
        var startVolume = 0
        @SuppressLint("ClickableViewAccessibility")
        bottomTouchArea.setOnTouchListener { v, event ->
            val audioManager = context?.getSystemService(android.content.Context.AUDIO_SERVICE) as? android.media.AudioManager ?: return@setOnTouchListener false
            val maxVolume = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
            val currentVolume = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
            
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.x
                    startVolume = currentVolume
                    sbVolumeHint.max = maxVolume
                    sbVolumeHint.progress = currentVolume
                    handler.removeCallbacks(hideVolumeHintRunnable)
                    llVolumeHint.alpha = 1f
                    llVolumeHint.visibility = View.VISIBLE
                    volumeDotView.setTouchPosition(event.x, event.y, true)
                    volumeDotView.animate().alpha(1f).setDuration(200).start()
                    
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val width = v.width
                    val deltaX = event.x - startX
                    val sensitivityWidth = width / 3f
                    val volumeDelta = (deltaX / sensitivityWidth * maxVolume).toInt()
                    val targetVolume = (startVolume + volumeDelta).coerceIn(0, maxVolume)
                    
                    audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, targetVolume, 0)
                    sbVolumeHint.progress = targetVolume
                    volumeDotView.setTouchPosition(event.x, event.y, true)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handler.postDelayed(hideVolumeHintRunnable, 2000)
                    volumeDotView.setTouchPosition(event.x, event.y, false)
                    volumeDotView.animate().alpha(0f).setDuration(200).start()
                    
                    true
                }
                else -> false
            }
        }
        
        contentContainer.setOnClickListener {
            if (rvLyrics.isVisible) hideLyrics() else showLyrics()
        }

        rvLyrics.setOnClickListener { hideLyrics() }
        
        rvLyrics.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    isUserScrollingLyrics = true
                    handler.removeCallbacks(resumeAutoScrollRunnable)
                } else if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    handler.removeCallbacks(resumeAutoScrollRunnable)
                    handler.postDelayed(resumeAutoScrollRunnable, 3000)
                }
            }
        })

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) tvCurrentTime.text = formatTime(progress.toLong())
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) { isDragging = true }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val p = player ?: return
                val posMs = seekBar?.progress?.toLong() ?: 0L
                p.seekTo(posMs)
                p.play()
                handler.postDelayed({ isDragging = false; updateUIProgress(p) }, 300)
            }
        })
    }

    private val hidePlayModeHintRunnable = Runnable {
        tvPlayModeHint.animate().alpha(0f).setDuration(300).withEndAction {
            tvPlayModeHint.visibility = View.INVISIBLE
            tvPlayModeHint.alpha = 1f
        }.start()
    }

    private val updatePlayModeUIRunnable = Runnable {
        updatePlayModeIcon()
        showPlayModeHint()
    }

    private fun togglePlayMode() {
        val p = player ?: return
        val currentRepeatMode = p.repeatMode
        val isShuffle = p.shuffleModeEnabled

        // 目标模式确定
        val nextRepeat: Int
        val nextShuffle: Boolean
        val hint: String

        when {
            isShuffle -> {
                // 当前是随机 -> 切到单曲循环
                nextRepeat = Player.REPEAT_MODE_ONE
                nextShuffle = false
                hint = getString(R.string.repeat_one)
            }
            currentRepeatMode == Player.REPEAT_MODE_ONE -> {
                // 当前是单曲 -> 切到列表循环
                nextRepeat = Player.REPEAT_MODE_ALL
                nextShuffle = false
                hint = getString(R.string.repeat_all)
            }
            else -> {
                // 当前是列表循环或其他 -> 切到随机播放
                nextRepeat = Player.REPEAT_MODE_ALL
                nextShuffle = true
                hint = getString(R.string.shuffle)
            }
        }

        // 1. 设置状态 (必须先设置模式，因为 HomeActivity 需要根据此状态拉取数据)
        p.repeatMode = nextRepeat
        p.shuffleModeEnabled = nextShuffle
        
        // 2. 根据模式刷新播放列表
        // 关键：HomeActivity 内部现在执行的是增量无缝更新
        (activity as? HomeActivity)?.updatePlaylistByMode(nextShuffle)
        
        // 3. 立即更新 UI，避免 MediaController 异步延迟导致的图标闪烁或错误
        updatePlayModeUI(nextShuffle, nextRepeat, hint)
    }

    private fun showPlayModeHint(text: String? = null) {
        val p = player ?: return
        if (!isAdded) return
        
        val modeText = text ?: when {
            p.shuffleModeEnabled -> getString(R.string.shuffle)
            p.repeatMode == Player.REPEAT_MODE_ONE -> getString(R.string.repeat_one)
            else -> getString(R.string.repeat_all)
        }
        
        tvPlayModeHint.text = modeText
        tvPlayModeHint.visibility = View.VISIBLE
        tvPlayModeHint.alpha = 1f
        handler.removeCallbacks(hidePlayModeHintRunnable)
        handler.postDelayed(hidePlayModeHintRunnable, 2000)
    }

    private fun updatePlayModeIcon() {
        val p = player ?: return
        updatePlayModeUI(p.shuffleModeEnabled, p.repeatMode, null)
    }

    private fun updatePlayModeUI(shuffleEnabled: Boolean, repeatMode: Int, hint: String?) {
        if (!isAdded) return
        
        // 优先级：随机模式优先于循环模式图标
        val resId = when {
            shuffleEnabled -> R.drawable.ic_shuffle_vector
            repeatMode == Player.REPEAT_MODE_ONE -> R.drawable.ic_repeat_one_vector
            else -> R.drawable.ic_repeat_vector
        }
        btnPlayMode.setImageResource(resId)
        
        if (hint != null) {
            showPlayModeHint(hint)
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            isUserScrollingLyrics = false
            handler.removeCallbacks(resumeAutoScrollRunnable)
            updateMetadata(mediaItem)
        }
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            btnPlayPause.setImageResource(if (isPlaying) R.drawable.ic_pause_vector else R.drawable.ic_play_vector)
            updateNeedle(isPlaying)
            if (isPlaying && !rvLyrics.isVisible) {
                discRotationHandler.post(discRotationRunnable)
            } else {
                discRotationHandler.removeCallbacks(discRotationRunnable)
            }
        }
        override fun onRepeatModeChanged(repeatMode: Int) { 
            handler.removeCallbacks(updatePlayModeUIRunnable)
            handler.post(updatePlayModeUIRunnable)
        }
        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) { 
            handler.removeCallbacks(updatePlayModeUIRunnable)
            handler.post(updatePlayModeUIRunnable)
        }
        override fun onMediaMetadataChanged(mediaMetadata: androidx.media3.common.MediaMetadata) {
            updateFavoriteIcon(mediaMetadata.extras?.getBoolean("is_favorite", false) ?: false)
        }
    }

    private fun setupPlayer() {
        player = (activity as? HomeActivity)?.mediaController
        player?.let {
            it.addListener(playerListener)
            btnPlayPause.setImageResource(if (it.isPlaying) R.drawable.ic_pause_vector else R.drawable.ic_play_vector)
            updateNeedle(it.isPlaying)
            updatePlayModeIcon()

            updateMetadata(it.currentMediaItem)
            updateUIProgress(it)
            handler.post(updateProgressAction)
            if (it.isPlaying && !rvLyrics.isVisible) {
                discRotationHandler.post(discRotationRunnable)
            }
        }
    }

    private fun updateNeedle(isPlaying: Boolean) {
        ivNeedle.animate()
            .rotation(if (isPlaying) -8f else -35f) // 15f 是【播放/放下】角度，-25f 是【暂停/抬起】角度
            .setDuration(400) // 稍微慢一点更像机械臂
            .start()
    }

    private fun getBaseMediaId(mediaId: String?): String = mediaId?.substringBefore('_') ?: ""

    private fun updateMetadata(mediaItem: MediaItem?) {
        if (mediaItem == null || !isAdded) return
        
        val rawMediaId = mediaItem.mediaId
        val itemId = getBaseMediaId(rawMediaId)
        pbDownload.progress = SongDownloader.getProgress(rawMediaId)

        mediaItem.mediaMetadata.let { metadata ->
            tvTitle.text = (metadata.title ?: getString(R.string.unknown_song)).toString().replace("\n", " ")
            tvTitle.isSelected = true
            tvTitle.requestFocus()
            val artist = (metadata.artist ?: getString(R.string.unknown_artist)).toString().replace("\n", " ")
            val album = metadata.albumTitle?.toString()?.replace("\n", " ")
            tvArtist.text = if (album.isNullOrEmpty()) artist else "$artist - $album"
            tvArtist.isSelected = true
            tvArtist.requestFocus()
            
            val artworkUri = metadata.artworkUri
            val extras = metadata.extras
            val hasPrimary = extras?.getBoolean("has_primary_image") ?: false
            
            if (extras != null) {
                updateFavoriteIcon(extras.getBoolean("is_favorite", false))
                val isTranscoding = extras.getBoolean("is_transcoding", false)
                val codec = extras.getString("codec", "未知")
                val bitrate = extras.getInt("bitrate", 0) / 1000
                
                val qualityText = if (!isTranscoding) "⚠️ 源码播放 $codec ${bitrate}kbps" else "正在转码 $codec ${bitrate}kbps"
                tvAudioQuality.apply {
                    text = qualityText
                    setTextColor(if (!isTranscoding) Color.YELLOW else 0x99FFFFFF.toInt())
                    visibility = View.VISIBLE
                }
            } else {
                tvAudioQuality.visibility = View.GONE
            }

            preloadLyrics(itemId, metadata.title?.toString(), metadata.artist?.toString(), metadata.albumTitle?.toString())

            // 封面加载策略：
            // 1. 优先尝试本地缓存的文件封面（Tag 解析出的）
            // 2. 如果有原生封面（hasPrimary），尝试 Emby
            // 3. 否则尝试网易云缓存
            // 4. 都没有则尝试解析 Tag
            
            val context = context ?: return
            val neteasePrefs = context.getSharedPreferences("netease_covers", AppCompatActivity.MODE_PRIVATE)
            val cachedNeteaseUrl = neteasePrefs.getString(itemId, null)
            
            // 检查本地是否有 File 缓存 (MediaItemUtils.saveCoverToFile 存入的)
            val coverFile = java.io.File(context.cacheDir, "covers/${itemId}.jpg")
            val initialData: Any? = when {
                coverFile.exists() -> coverFile
                hasPrimary && artworkUri != null -> artworkUri
                cachedNeteaseUrl != null -> cachedNeteaseUrl
                else -> artworkUri
            }

        ivCover.load(initialData) {
            crossfade(true)
            placeholder(R.drawable.cd)
            error(R.drawable.cd)
            listener(
                onStart = {
                    // 转场动画：淡入并轻微缩放
                    ivCover.alpha = 0.5f
                    ivCover.scaleX = 0.95f
                    ivCover.scaleY = 0.95f
                },
                onSuccess = { _, _ ->
                    ivCover.animate()
                        .alpha(1f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(300)
                        .setInterpolator(AccelerateDecelerateInterpolator())
                        .start()
                    updateBlurBackground(initialData)
                },
                onError = { _, _ ->
                    if (initialData != artworkUri && artworkUri != null) {
                        ivCover.load(artworkUri) {
                            placeholder(R.drawable.cd)
                            listener(onSuccess = { _, _ -> updateBlurBackground(artworkUri) })
                        }
                    } else {
                        // 最后的尝试：从文件流解析 Tag
                        loadCoverFromTags(itemId, metadata.title?.toString(), metadata.artist?.toString(), metadata.albumTitle?.toString())
                    }
                }
            )
        }
        }
    }

    private fun loadCoverFromTags(itemId: String, title: String?, artist: String?, album: String?) {
        val serverUrl = context?.getSharedPreferences("embysic_prefs", AppCompatActivity.MODE_PRIVATE)?.getString("server_url", "") ?: ""
        val accessToken = context?.getSharedPreferences("embysic_prefs", AppCompatActivity.MODE_PRIVATE)?.getString("access_token", "") ?: ""
        val streamUrl = "${serverUrl.trimEnd('/')}/emby/Audio/$itemId/stream?static=true&api_key=$accessToken"
        
        lifecycleScope.launch(Dispatchers.IO) {
            val retriever = android.media.MediaMetadataRetriever()
            try {
                retriever.setDataSource(streamUrl, mapOf("X-Emby-Token" to accessToken))
                val picture = retriever.embeddedPicture
                if (picture != null) {
                    val bitmap = android.graphics.BitmapFactory.decodeByteArray(picture, 0, picture.size)
                    if (bitmap != null) {
                        MediaItemUtils.saveCoverToFile(requireContext(), itemId, bitmap)
                        withContext(Dispatchers.Main) {
                            if (getBaseMediaId(player?.currentMediaItem?.mediaId) == itemId) {
                                ivCover.setImageBitmap(bitmap)
                                updateBlurBackground(bitmap)
                            }
                        }
                    }
                } else {
                    // Tag 也没有，去搜网易云
                    searchNeteaseCover(itemId, title, artist, album)
                }
            } catch (e: Exception) {
                searchNeteaseCover(itemId, title, artist, album)
            } finally {
                try { retriever.release() } catch (e: Exception) {}
            }
        }
    }

    private fun updateUIProgress(p: Player) {
        if (isDragging || !isAdded) return
        if (rvLyrics.isVisible && !isUserScrollingLyrics) {
            val index = lyricsAdapter.updateActiveLine(p.currentPosition)
            if (index != -1) {
                val layoutManager = rvLyrics.layoutManager as? LinearLayoutManager
                val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                val offset = if (isLandscape) rvLyrics.height / 4 else rvLyrics.height / 3
                layoutManager?.scrollToPositionWithOffset(index, offset)
            }
        }
        val extras = p.currentMediaItem?.mediaMetadata?.extras
        val injectDuration = extras?.getLong("duration_ms") ?: 0L
        val duration = if (p.duration > 0) p.duration else injectDuration
        val current = p.currentPosition
        if (duration > 0) {
            seekBar.max = duration.toInt()
            seekBar.progress = current.toInt()
            seekBar.secondaryProgress = p.bufferedPosition.toInt()
            tvTotalTime.text = formatTime(duration)
        }
        tvCurrentTime.text = formatTime(current)
    }

    private fun updateFavoriteIcon(isFavorite: Boolean) {
        if (isFavorite) {
            btnMore.setImageResource(R.drawable.ic_heart)
            btnMore.setColorFilter(Color.RED, android.graphics.PorterDuff.Mode.SRC_IN)
        } else {
            btnMore.setImageResource(R.drawable.ic_heart_border)
            btnMore.setColorFilter("#66FFFFFF".toColorInt(), android.graphics.PorterDuff.Mode.SRC_IN)
        }
    }

    private fun toggleFavorite() {
        val mediaItem = player?.currentMediaItem ?: return
        val itemId = mediaItem.mediaMetadata.extras?.getString("item_id") ?: return
        val isFavorite = mediaItem.mediaMetadata.extras?.getBoolean("is_favorite", false) ?: false
        val context = context ?: return
        val service = RetrofitClient.getEmbyApiService(context) ?: return
        val prefs = context.getSharedPreferences("embysic_prefs", AppCompatActivity.MODE_PRIVATE)
        val userId = prefs.getString("user_id", "") ?: ""
        val accessToken = prefs.getString("access_token", "") ?: ""
        val authHeader = "MediaBrowser Token=\"$accessToken\""

        lifecycleScope.launch {
            try {
                // 提前捕获位置，因为 dismiss 可能导致 view 被销毁
                val loc = IntArray(2)
                btnMore.getLocationInWindow(loc)
                val centerX = loc[0] + btnMore.width / 2f
                val centerY = loc[1] + btnMore.height / 2f

                if (isFavorite) {
                    service.unmarkFavorite(userId, itemId, authHeader)
                    // 收藏碎裂动效
                    heartLayout.shatterHeart(centerX, centerY)
                } else {
                    service.markFavorite(userId, itemId, authHeader)
                    // 添加爱心浮现动效
                    repeat(5) {
                        handler.postDelayed({
                            heartLayout.addHeart(centerX, centerY)
                        }, it * 100L)
                    }
                }
                mediaItem.mediaMetadata.extras?.putBoolean("is_favorite", !isFavorite)
                updateFavoriteIcon(!isFavorite)
            } catch (e: Exception) {
                // 静默失败
            }
        }
    }

    private fun searchNeteaseCover(itemId: String, title: String?, artist: String?, album: String?) {
        lifecycleScope.launch(Dispatchers.IO) {
            val rawTitle = title ?: ""
            val query = if (album.isNullOrBlank() || album == "未知专辑") {
                if (artist.isNullOrBlank() || artist == "未知歌手") rawTitle else "$rawTitle $artist"
            } else {
                "$album $artist"
            }
            if (query.isEmpty()) return@launch
            try {
                // 1. 搜索歌曲，获取 songId
                val searchResponse = neteaseApi.searchSong(query, limit = 10)
                val songs = searchResponse.result?.songs ?: return@launch

                // 使用优化后的匹配逻辑
                val currentDuration = player?.duration ?: 0L
                val song = LyricUtils.findBestMatch(songs, rawTitle, artist, album, currentDuration)
                    ?: songs.firstOrNull() ?: return@launch
                
                // 2. 使用新版 v3 详情接口获取封面
                val detailResponse = neteaseApi.getSongDetail("[{\"id\":${song.id}}]")
                val picUrl = detailResponse.songs?.firstOrNull()?.al?.picUrl?.replace("http://", "https://")
                
                if (!picUrl.isNullOrEmpty()) {
                    withContext(Dispatchers.Main) {
                        if (getBaseMediaId(player?.currentMediaItem?.mediaId) == itemId) {
                            // 3. 将拉取到的封面保存到本地持久化缓存，供列表使用
                            saveNeteaseCoverToCache(itemId, picUrl)
                            
                            // 4. 更新当前播放器的 MediaItem，确保通知栏同步
                            updateMediaItemArtwork(picUrl)

                            // 加载界面封面
                            ivCover.load(picUrl) { 
                                crossfade(true)
                                placeholder(R.drawable.cd)
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
        val prefs = context.getSharedPreferences("netease_covers", AppCompatActivity.MODE_PRIVATE)
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
        
        // 注意：Media3 不支持直接在播放时替换单个 Item 的 Metadata 且保持进度
        // 但我们可以通过 replaceMediaItem 来实现
        val currentIndex = p.currentMediaItemIndex
        val currentPosition = p.currentPosition
        p.replaceMediaItem(currentIndex, newItem)
        p.seekTo(currentIndex, currentPosition)
    }

    private fun updateBlurBackground(data: Any?) {
        lifecycleScope.launch {
            val context = context ?: return@launch
            val request = ImageRequest.Builder(context)
                .data(data ?: R.drawable.bg_superman)
                .size(300)
                .transformations(BlurTransformation(context, 25, 4))
                .crossfade(true)
                .build()
            val result = ImageLoader(context).execute(request)
            if (result is SuccessResult && isAdded) {
                ivBlurBackground.setImageDrawable(result.drawable)
                // 更新状态栏图标颜色：始终保持浅色图标以适应深色播放页
                dialog?.window?.let { window ->
                    val controller = WindowInsetsControllerCompat(window, window.decorView)
                    controller.isAppearanceLightStatusBars = false
                    controller.isAppearanceLightNavigationBars = false
                }
            } else if (isAdded) {
                // 如果加载失败，回退到 superman
                ivBlurBackground.setImageResource(R.drawable.bg_superman)
            }
        }
    }

    private fun showLyrics() {
        isUserScrollingLyrics = false
        handler.removeCallbacks(resumeAutoScrollRunnable)

        // 隐藏不需要的组件以释放空间给歌词
        contentContainer.visibility = View.GONE
        flHintContainer.visibility = View.GONE

        rvLyrics.visibility = View.VISIBLE
        rvLyrics.alpha = 0f
        rvLyrics.animate()
            .alpha(1f)
            .setDuration(400)
            .start()

        // 停止唱片旋转以节省性能
        discRotationHandler.removeCallbacks(discRotationRunnable)

        val mediaItem = player?.currentMediaItem ?: return
        val itemId = mediaItem.mediaId
        
        // 1. 优先检查内存缓存
        if (lyricsCache.containsKey(itemId)) {
            lyricsAdapter.lines = lyricsCache[itemId]!!
            player?.let { updateUIProgress(it) }
            return
        }

        // 2. 检查内置标签 (从 MediaItem Extras 读取，如果已通过扫描注入)
        val embeddedLyrics = mediaItem.mediaMetadata.extras?.getString("lyrics")
        if (!embeddedLyrics.isNullOrBlank()) {
            val lines = LrcParser.parse(embeddedLyrics)
            if (lines.isNotEmpty()) {
                lyricsCache[itemId] = lines
                lyricsAdapter.lines = lines
                player?.let { updateUIProgress(it) }
                return
            }
        }

        // 3. 网络拉取 (Emby 内置/外置 -> 网易云)
        lyricsAdapter.lines = listOf(LrcLine(0, getString(R.string.loading_lyrics)))
        preloadLyrics(itemId, mediaItem.mediaMetadata.title?.toString(), mediaItem.mediaMetadata.artist?.toString(), mediaItem.mediaMetadata.albumTitle?.toString())
        
        // 自动滚动到当前进度
        player?.let { updateUIProgress(it) }
    }

    private fun preloadLyrics(itemId: String, title: String?, artist: String?, album: String?) {
        val service = apiService ?: return
        val context = context ?: return
        val prefs = context.getSharedPreferences("embysic_prefs", AppCompatActivity.MODE_PRIVATE)
        val accessToken = prefs.getString("access_token", "") ?: ""
        val authHeader = "MediaBrowser Token=\"$accessToken\""

        // 1. 检查内存缓存
        if (lyricsCache.containsKey(itemId)) {
            if (rvLyrics.isVisible && player?.currentMediaItem?.mediaId == itemId) {
                lyricsAdapter.lines = lyricsCache[itemId]!!
            }
            return
        }

        // 2. 尝试从磁盘缓存恢复 (实现秒开)
        val lyricPrefs = context.getSharedPreferences("lyrics_disk_cache", AppCompatActivity.MODE_PRIVATE)
        val cachedJson = lyricPrefs.getString(itemId, null)
        if (cachedJson != null) {
            try {
                val type = object : TypeToken<List<LrcLine>>() {}.type
                val diskLines: List<LrcLine> = Gson().fromJson(cachedJson, type)
                lyricsCache[itemId] = diskLines
                if (rvLyrics.isVisible && player?.currentMediaItem?.mediaId == itemId) {
                    lyricsAdapter.lines = diskLines
                    player?.let { updateUIProgress(it) }
                }
                return
            } catch (e: Exception) { e.printStackTrace() }
        }

        lifecycleScope.launch {
            try {
                val response = service.getLyrics(itemId, authHeader)
                val rawLines = response.Lines ?: response.Lyrics ?: response.LyricLines
                @Suppress("UNCHECKED_CAST")
                val actualLines = rawLines as? List<LyricLine>
                if (actualLines.isNullOrEmpty()) {
                    searchNeteaseLyrics(itemId, title, artist, album)
                } else {
                    val metadata = mutableListOf(LrcLine(-1, title ?: "未知曲名"), LrcLine(-1, artist ?: "未知歌手"))
                    val lyrics = actualLines.map { LrcLine(it.StartTicks?.let { t -> t / 10000 } ?: it.Start?.let { t -> t.toLong() / 10000 } ?: 0L, it.Text ?: "") }
                        .filter { it.text.isNotBlank() }.sortedBy { it.timeMs }
                    val finalLines = metadata + lyrics
                    lyricsCache[itemId] = finalLines
                    
                    // 使用 KTX 优化磁盘保存
                    context.getSharedPreferences("lyrics_disk_cache", AppCompatActivity.MODE_PRIVATE).edit {
                        putString(itemId, Gson().toJson(finalLines))
                    }

                    if (rvLyrics.isVisible && player?.currentMediaItem?.mediaId == itemId) {
                        lyricsAdapter.lines = finalLines
                        player?.let { updateUIProgress(it) }
                    }
                }
            } catch (_: Exception) { searchNeteaseLyrics(itemId, title, artist, null) }
        }
    }

    private fun searchNeteaseLyrics(itemId: String, title: String?, artist: String?, album: String?) {
        val cleanedTitle = title?.replace(Regex("\\s*[(\\[].*?[)\\]]"), "")?.trim() ?: ""
        val isArtistUnknown = artist.isNullOrBlank() || artist.contains("未知") || artist.contains("Unknown")
        val isAlbumUnknown = album.isNullOrBlank() || album.contains("未知") || album.contains("Unknown")
        
        val query = if (!isAlbumUnknown && !isArtistUnknown) {
            "$album $artist"
        } else if (!isArtistUnknown) {
            "$cleanedTitle $artist"
        } else {
            cleanedTitle
        }
        
        if (query.isEmpty()) { showNoLyrics(itemId, title, artist); return }

        lifecycleScope.launch {
            try {
                // 增加 limit 以便进行多结果择优匹配
                val searchResponse = neteaseApi.searchSong(query, limit = 20)
                val songs = searchResponse.result?.songs ?: throw Exception("Not found")
                
                // 智能匹配：寻找最接近的歌曲
                val currentDuration = player?.duration ?: 0L
                val bestMatch = LyricUtils.findBestMatch(songs, cleanedTitle, artist, album, currentDuration)
                    ?: throw Exception("No good match")

                val lyricResponse = neteaseApi.getLyric(bestMatch.id)
                val lrcText = lyricResponse.lrc?.lyric
                if (!lrcText.isNullOrBlank()) {
                    val metadata = mutableListOf(LrcLine(-1, title ?: getString(R.string.unknown_song)), LrcLine(-1, artist ?: getString(R.string.unknown_artist)), LrcLine(-1, getString(R.string.source_netease)))
                    val mainLyrics = LrcParser.parse(lrcText)
                    val tlyricText = lyricResponse.tlyric?.lyric
                    val finalLines = if (!tlyricText.isNullOrBlank()) metadata + LyricUtils.mergeLyrics(mainLyrics, LrcParser.parse(tlyricText)) else metadata + mainLyrics
                    lyricsCache[itemId] = finalLines
                    
                    context?.getSharedPreferences("lyrics_disk_cache", AppCompatActivity.MODE_PRIVATE)?.edit {
                        putString(itemId, Gson().toJson(finalLines))
                    }

                    if (rvLyrics.isVisible && player?.currentMediaItem?.mediaId == itemId) {
                        lyricsAdapter.lines = finalLines
                        player?.let { updateUIProgress(it) }
                    }
                } else { showNoLyrics(itemId, title, artist) }
            } catch (_: Exception) { 
                if (!isArtistUnknown && cleanedTitle.isNotEmpty()) searchNeteaseLyrics(itemId, cleanedTitle, null, null)
                else showNoLyrics(itemId, title, artist)
            }
        }
    }

    private fun showNoLyrics(itemId: String, title: String?, artist: String?) {
        val finalLines = listOf(LrcLine(-1, title ?: getString(R.string.unknown_song)), LrcLine(-1, artist ?: getString(R.string.unknown_artist)), LrcLine(0, getString(R.string.no_lyrics)))
        lyricsCache[itemId] = finalLines
        if (rvLyrics.isVisible && player?.currentMediaItem?.mediaId == itemId) {
            lyricsAdapter.lines = finalLines
        }
    }

    private fun hideLyrics() {
        rvLyrics.animate()
            .alpha(0f)
            .setDuration(400)
            .withEndAction {
                rvLyrics.visibility = View.GONE
                // 恢复组件显示
                contentContainer.visibility = View.VISIBLE
                flHintContainer.visibility = View.VISIBLE
                
                rlDiscContainer.visibility = View.VISIBLE
                rlDiscContainer.alpha = 0f
                rlDiscContainer.scaleX = 0.8f
                rlDiscContainer.scaleY = 0.8f
                rlDiscContainer.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(400)
                    .withEndAction {
                        if (player?.isPlaying == true) {
                            discRotationHandler.post(discRotationRunnable)
                        }
                    }
                    .start()
            }
            .start()
    }

    private fun formatTime(ms: Long): String {
        val sec = (ms / 1000) % 60
        val min = (ms / (1000 * 60)) % 60
        return String.format(Locale.getDefault(), "%02d:%02d", min, sec)
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
    }

    override fun onDestroyView() {
        player?.removeListener(playerListener)
        handler.removeCallbacksAndMessages(null)
        discRotationHandler.removeCallbacksAndMessages(null)
        SongDownloader.removeProgressListener(downloadProgressListener)
        super.onDestroyView()
    }
}
