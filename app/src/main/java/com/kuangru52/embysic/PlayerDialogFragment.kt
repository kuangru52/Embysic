package com.kuangru52.embysic

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.DialogInterface
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Outline
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.RoundedCorner
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.graphics.toColorInt
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import coil.ImageLoader
import coil.load
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import jp.wasabeef.transformers.coil.BlurTransformation
import kotlinx.coroutines.launch
import java.util.Locale

@UnstableApi
class PlayerDialogFragment : BottomSheetDialogFragment() {

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
    private lateinit var mainContent: View
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

    private val lyricsAdapter = LyricsAdapter(
        onItemClick = { hideLyrics() },
        onSeekTo = { timeMs ->
            player?.let { p ->
                p.seekTo(timeMs)
                p.play()
                // 添加轻微触感反馈，提升操作感
                view?.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
            }
        }
    )
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
            val statusBarHeight = bars.top

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
        mainContent = view.findViewById(R.id.mainContent)
        contentContainer = view.findViewById(R.id.contentContainer)
        flHintContainer = view.findViewById(R.id.flHintContainer)
        pbDownload = view.findViewById(R.id.pbDownload)
        sbVolumeHint = view.findViewById(R.id.sbVolumeHint)
        llVolumeHint = view.findViewById(R.id.llVolumeHint)
        heartLayout = view.findViewById(R.id.heartLayout)

        ivNeedle.rotation = -35f // 初始设置为暂停时的角度

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

        // 处理歌词界面空白区域点击回到唱片页
        rvLyrics.addOnItemTouchListener(object : RecyclerView.OnItemTouchListener {
            private val gestureDetector = android.view.GestureDetector(context, object : android.view.GestureDetector.SimpleOnGestureListener() {
                override fun onSingleTapUp(e: android.view.MotionEvent): Boolean {
                    val child = rvLyrics.findChildViewUnder(e.x, e.y)
                    if (child == null) {
                        hideLyrics()
                        return true
                    }
                    return false
                }
            })
            override fun onInterceptTouchEvent(rv: RecyclerView, e: android.view.MotionEvent): Boolean {
                gestureDetector.onTouchEvent(e)
                return false
            }
            override fun onTouchEvent(rv: RecyclerView, e: android.view.MotionEvent) {}
            override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {}
        })
        
        // 点击主容器背景（非子控件区域）也触发返回
        mainContent.setOnClickListener {
            if (rvLyrics.isVisible) hideLyrics()
        }
        
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
            // 关键修复：如果是由于列表元数据更新（如服务抓取到封面）导致的 Transition，
            // 且歌曲 ID 没变，则跳过重置逻辑，防止 Logo 闪烁。
            val newId = getBaseMediaId(mediaItem?.mediaId)
            val currentId = getBaseMediaId(player?.currentMediaItem?.mediaId)
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED && newId == currentId) {
                // 如果是元数据更新，我们只更新那些可能变化的东西（如收藏状态），但不重置封面
                mediaItem?.mediaMetadata?.let { updateFavoriteIcon(it.extras?.getBoolean("is_favorite") ?: false) }
                return
            }

            isUserScrollingLyrics = false
            handler.removeCallbacks(resumeAutoScrollRunnable)
            updateMetadata(mediaItem)
        }
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            btnPlayPause.setImageResource(if (isPlaying) R.drawable.ic_pause_vector else R.drawable.ic_play_vector)
            updateNeedle(isPlaying)
            if (isPlaying && !rvLyrics.isVisible) {
                // 延迟开启旋转，等唱针落下，视觉上更真实
                discRotationHandler.removeCallbacks(discRotationRunnable)
                discRotationHandler.postDelayed(discRotationRunnable, 400)
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
            // 关键：如果元数据更新，再次检查并刷新封面，覆盖初次进入 metadata 为空的缺口
            player?.let { p ->
                if (p.currentMediaItem?.mediaMetadata == mediaMetadata) {
                    updateMetadata(p.currentMediaItem)
                }
            }
        }
    }

    private fun setupPlayer() {
        player = (activity as? HomeActivity)?.mediaController
        player?.let {
            it.addListener(playerListener)
            btnPlayPause.setImageResource(if (it.isPlaying) R.drawable.ic_pause_vector else R.drawable.ic_play_vector)
            updateNeedle(it.isPlaying)
            updatePlayModeIcon()

            // 关键修复：增加延迟，确保 MediaController 完全挂载并获取到 metadata
            handler.postDelayed({
                if (isAdded) {
                    updateMetadata(it.currentMediaItem)
                }
            }, 100)

            updateUIProgress(it)
            handler.post(updateProgressAction)
            if (it.isPlaying && !rvLyrics.isVisible) {
                discRotationHandler.post(discRotationRunnable)
            }
        }
    }

    private fun updateNeedle(isPlaying: Boolean) {
        ivNeedle.animate()
            .rotation(if (isPlaying) -8f else -35f)
            .setDuration(400)
            .setInterpolator(android.view.animation.AccelerateDecelerateInterpolator())
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
            
            val extras = metadata.extras
            
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

            // 封面加载策略优化：
            // 1. 优先使用 MediaItem 自带的 artworkUri (Emby 封面，与列表一致)
            // 2. 其次使用本地缓存封面 (covers/${itemId}.jpg)
            // 3. 最后才考虑从网络或 Tag 中提取
            val artworkUri = metadata.artworkUri
            val safeContext = context ?: return@let
            val coverFile = java.io.File(safeContext.cacheDir, "covers/${itemId}.jpg")

            when {
                artworkUri != null -> {
                    ivCover.load(artworkUri) {
                        crossfade(true)
                        // 如果当前已经有图片了，加载新图片时不要显示占位图，防止闪烁
                        if (ivCover.drawable == null) {
                            placeholder(R.drawable.logo)
                        }
                        error(R.drawable.logo)
                        listener(onSuccess = { _, _ -> updateBlurBackground(artworkUri) })
                    }
                }
                coverFile.exists() -> {
                    ivCover.load(coverFile) {
                        crossfade(true)
                        if (ivCover.drawable == null) {
                            placeholder(R.drawable.logo)
                        }
                        listener(onSuccess = { _, _ -> updateBlurBackground(coverFile) })
                    }
                }
                else -> {
                    // 尝试使用 Emby API 构造 URL，确保与列表一致
                    val prefs = safeContext.getSharedPreferences("embysic_prefs", android.content.Context.MODE_PRIVATE)
                    val serverUrl = prefs.getString("server_url", "")?.trimEnd('/') ?: ""
                    val accessToken = prefs.getString("access_token", "") ?: ""
                    val imageUrl = "$serverUrl/emby/Items/$itemId/Images/Primary?MaxWidth=800"
                    
                    ivCover.load(imageUrl) {
                        crossfade(true)
                        addHeader("Authorization", "MediaBrowser Token=\"$accessToken\"")
                        placeholder(R.drawable.logo)
                        error(R.drawable.logo)
                        listener(onSuccess = { _, _ -> updateBlurBackground(imageUrl) })
                    }
                }
            }

            // 同步入口：仅强制同步歌词，如果已有封面则跳过网络封面下载
            syncNeteaseData(itemId, metadata.title?.toString(), metadata.artist?.toString(), metadata.albumTitle?.toString(), skipCover = artworkUri != null)
        }
    }

    private fun syncNeteaseData(itemId: String, title: String?, artist: String?, album: String?, skipCover: Boolean = false) {
        val p = player ?: return
        val durationMs = if (p.duration > 0) p.duration else (p.currentMediaItem?.mediaMetadata?.extras?.getLong("duration_ms") ?: 0L)
        
        lifecycleScope.launch {
            LyricUtils.fetchNeteaseMetadata(
                context = requireContext(),
                itemId = itemId,
                title = title,
                artist = artist,
                album = album,
                durationMs = durationMs,
                skipCover = skipCover,
                onCoverReady = { data ->
                    if (!skipCover && getBaseMediaId(player?.currentMediaItem?.mediaId) == itemId) {
                        ivCover.load(data) {
                            crossfade(true)
                            // 关键：如果已经有图了（比如 Tag 提取的），加载网络图时不要闪 Logo
                            if (ivCover.drawable == null) {
                                placeholder(R.drawable.logo)
                            }
                            listener(onSuccess = { _, _ -> updateBlurBackground(data) })
                        }
                    }
                },
                onLyricsReady = { lines ->
                    if (getBaseMediaId(player?.currentMediaItem?.mediaId) == itemId) {
                        if (rvLyrics.isVisible) {
                            lyricsAdapter.lines = lines
                            player?.let { updateUIProgress(it) }
                        }
                    }
                }
            )
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
            } catch (_: Exception) {
                // 静默失败
            }
        }
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
        val rawId = mediaItem.mediaId
        val itemId = getBaseMediaId(rawId)
        
        // 1. 优先检查内存缓存
        if (LyricUtils.lyricsCache.containsKey(itemId)) {
            lyricsAdapter.lines = LyricUtils.lyricsCache[itemId]!!
            player?.let { updateUIProgress(it) }
            return
        }

        // 2. 检查内置标签 (从 MediaItem Extras 读取，如果已通过扫描注入)
        val embeddedLyrics = mediaItem.mediaMetadata.extras?.getString("lyrics")
        if (!embeddedLyrics.isNullOrBlank()) {
            val lines = LrcParser.parse(embeddedLyrics)
            if (lines.isNotEmpty()) {
                LyricUtils.lyricsCache[itemId] = lines
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
        if (LyricUtils.lyricsCache.containsKey(itemId)) {
            if (rvLyrics.isVisible && getBaseMediaId(player?.currentMediaItem?.mediaId) == itemId) {
                lyricsAdapter.lines = LyricUtils.lyricsCache[itemId]!!
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
                LyricUtils.lyricsCache[itemId] = diskLines
                if (rvLyrics.isVisible && getBaseMediaId(player?.currentMediaItem?.mediaId) == itemId) {
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
                    syncNeteaseData(itemId, title, artist, album)
                } else {
                    val metadata = mutableListOf(LrcLine(-1, title ?: "未知曲名"), LrcLine(-1, artist ?: "未知歌手"))
                    val lyrics = actualLines.map { LrcLine(it.StartTicks?.let { t -> t / 10000 } ?: it.Start?.let { t -> t.toLong() / 10000 } ?: 0L, it.Text ?: "") }
                        .filter { it.text.isNotBlank() }.sortedBy { it.timeMs }
                    val finalLines = metadata + lyrics
                    LyricUtils.lyricsCache[itemId] = finalLines
                    
                    context.getSharedPreferences("lyrics_disk_cache", AppCompatActivity.MODE_PRIVATE).edit {
                        putString(itemId, Gson().toJson(finalLines))
                    }

                    if (rvLyrics.isVisible && getBaseMediaId(player?.currentMediaItem?.mediaId) == itemId) {
                        lyricsAdapter.lines = finalLines
                        player?.let { updateUIProgress(it) }
                    }
                }
            } catch (_: Exception) { syncNeteaseData(itemId, title, artist, album) }
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
