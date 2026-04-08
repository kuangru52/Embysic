package com.kuangru52.embysic

import android.net.Uri
import android.util.TypedValue
import androidx.annotation.AttrRes
import android.util.Log
import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.content.res.Configuration
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.core.content.edit
import androidx.core.net.toUri
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
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

    private val neteaseApi: NeteaseApiService by lazy {
        val okHttpClient = okhttp3.OkHttpClient.Builder()
            .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .protocols(listOf(okhttp3.Protocol.HTTP_1_1))
            .addInterceptor { chain ->
                val request = chain.request()
                val newRequest = request.newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Referer", "https://music.163.com/")
                    .header("Cookie", "os=pc; appver=2.10.12; osver=Microsoft-Windows-10-Professional-build-19045-64bit; MUSIC_U=; __remember_me=true")
                    .build()
                chain.proceed(newRequest)
            }
            .build()
            
        Retrofit.Builder()
            .baseUrl("https://music.163.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(NeteaseApiService::class.java)
    }

    private lateinit var ivCover: ImageView
    private lateinit var ivBlurBackground: ImageView
    private lateinit var tvTitle: TextView
    private lateinit var tvArtist: TextView
    private lateinit var btnPlayPause: ImageButton
    private lateinit var seekBar: SeekBar
    private lateinit var tvCurrentTime: TextView
    private lateinit var tvTotalTime: TextView
    private lateinit var tvAudioQuality: TextView
    private lateinit var btnPrev: ImageButton
    private lateinit var btnNext: ImageButton
    private lateinit var btnMore: ImageButton
    private lateinit var rlDiscContainer: View
    private lateinit var rlDisc: View
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
    private lateinit var pbDownload: ProgressBar

    private val lyricsAdapter = LyricsAdapter { _ -> hideLyrics() }
    private var player: Player? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isDragging = false
    private var isUserScrollingLyrics = false
    private val resumeAutoScrollRunnable = Runnable { isUserScrollingLyrics = false }
    private var apiService: EmbyApiService? = null

    companion object {
        private val lyricsCache = mutableMapOf<String, List<LrcLine>>()
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

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.behavior.apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
            isHideable = true
        }
        dialog.window?.setWindowAnimations(R.style.PlayerDialogAnimation)
        return dialog
    }

    override fun onStart() {
        super.onStart()
        val dialog = dialog as? BottomSheetDialog
        dialog?.let {
            val bottomSheet = it.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.let { sheet ->
                sheet.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
                sheet.setBackgroundColor(Color.TRANSPARENT)
            }
            
            it.window?.let { window ->
                window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                WindowCompat.setDecorFitsSystemWindows(window, false)
                window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
                window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
                window.statusBarColor = Color.TRANSPARENT
                window.navigationBarColor = Color.TRANSPARENT
                
                val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
                val controller = WindowInsetsControllerCompat(window, window.decorView)
                controller.isAppearanceLightStatusBars = !isDark
                controller.isAppearanceLightNavigationBars = !isDark
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_player_full, container, false)
        initViews(view)
        setupPlayer()
        initApiService()
        SongDownloader.addProgressListener(downloadProgressListener)
        return view
    }

    private fun initApiService() {
        val prefs = requireContext().getSharedPreferences("embysic_prefs", AppCompatActivity.MODE_PRIVATE)
        val serverUrl = prefs.getString("server_url", "") ?: ""
        if (serverUrl.isNotEmpty()) {
            val retrofit = Retrofit.Builder()
                .baseUrl(if (serverUrl.endsWith("/")) serverUrl else "$serverUrl/")
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            apiService = retrofit.create(EmbyApiService::class.java)
        }
    }

    private fun initViews(view: View) {
        ivCover = view.findViewById(R.id.ivFullCover)
        ivBlurBackground = view.findViewById(R.id.ivBlurBackground)
        tvTitle = view.findViewById(R.id.tvFullTitle)
        tvArtist = view.findViewById(R.id.tvFullArtist)
        btnPlayPause = view.findViewById(R.id.btnPlayPauseFull)
        seekBar = view.findViewById(R.id.seekBar)
        tvCurrentTime = view.findViewById(R.id.tvCurrentTime)
        tvTotalTime = view.findViewById(R.id.tvTotalTime)
        tvAudioQuality = view.findViewById(R.id.tvAudioQuality)
        btnPrev = view.findViewById(R.id.btnPrev)
        btnNext = view.findViewById(R.id.btnNext)
        btnMore = view.findViewById(R.id.btnMore)
        rlDiscContainer = view.findViewById(R.id.rlDiscContainer)
        rlDisc = view.findViewById(R.id.rlDisc)
        ivNeedle = view.findViewById(R.id.ivNeedle)
        rvLyrics = view.findViewById(R.id.rvLyrics)
        pbDownload = view.findViewById(R.id.pbDownload)

        ivNeedle.rotation = -30f

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
        
        btnMore.setOnClickListener { toggleFavorite() }
        rlDisc.setOnClickListener { showLyrics() }
        
        val contentContainer = view.findViewById<View>(R.id.contentContainer)
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

    private fun setupPlayer() {
        player = (activity as? HomeActivity)?.mediaController
        player?.let {
            it.addListener(object : Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    updateMetadata(mediaItem)
                }
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    btnPlayPause.setImageResource(if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
                    updateNeedle(isPlaying)
                }
            })
            it.repeatMode = Player.REPEAT_MODE_ALL
            btnPlayPause.setImageResource(if (it.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
            updateNeedle(it.isPlaying)

            updateMetadata(it.currentMediaItem)
            updateUIProgress(it)
            handler.post(updateProgressAction)
            discRotationHandler.post(discRotationRunnable)
        }
    }

    private fun updateNeedle(isPlaying: Boolean) {
        ivNeedle.animate()
            .rotation(if (isPlaying) 0f else -30f)
            .setDuration(300)
            .start()
    }

    private fun updateMetadata(mediaItem: MediaItem?) {
        if (mediaItem == null || !isAdded) return
        
        val itemId = mediaItem.mediaId
        pbDownload.progress = SongDownloader.getProgress(itemId)

        mediaItem.mediaMetadata.let {
            tvTitle.text = it.title
            tvArtist.text = it.artist
            val artworkUri = it.artworkUri
            
            val extras = it.extras
            if (extras != null) {
                updateFavoriteIcon(extras.getBoolean("is_favorite", false))
                val isTranscoding = extras.getBoolean("is_transcoding", false)
                val codec = extras.getString("codec", "未知")
                val bitrate = extras.getInt("bitrate", 0) / 1000
                tvAudioQuality.text = if (isTranscoding) "正在转码: $codec ${bitrate}kbps" else "$codec ${bitrate}kbps"
                tvAudioQuality.visibility = View.VISIBLE
            } else {
                tvAudioQuality.visibility = View.GONE
            }

            preloadLyrics(itemId, it.title?.toString(), it.artist?.toString())

            ivCover.load(artworkUri) {
                crossfade(true)
                placeholder(android.R.drawable.ic_menu_gallery)
                listener(onError = { _, _ ->
                    searchNeteaseCover(itemId, it.title?.toString(), it.artist?.toString())
                })
            }
            
            updateBlurBackground(artworkUri)
        }
    }

    private fun updateUIProgress(p: Player) {
        if (isDragging) return
        if (rvLyrics.isVisible && !isUserScrollingLyrics) {
            val index = lyricsAdapter.updateActiveLine(p.currentPosition)
            if (index != -1) rvLyrics.smoothScrollToPosition(index)
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

    private fun getColorFromAttr(@AttrRes attr: Int): Int {
        val typedValue = TypedValue()
        requireContext().theme.resolveAttribute(attr, typedValue, true)
        return typedValue.data
    }

    private fun updateFavoriteIcon(isFavorite: Boolean) {
        if (isFavorite) {
            btnMore.setImageResource(R.drawable.ic_heart)
            btnMore.setColorFilter(Color.RED, android.graphics.PorterDuff.Mode.SRC_IN)
        } else {
            btnMore.setImageResource(R.drawable.ic_heart_border)
            val colorOnSurface = getColorFromAttr(com.google.android.material.R.attr.colorOnSurface)
            btnMore.setColorFilter(colorOnSurface, android.graphics.PorterDuff.Mode.SRC_IN)
        }
    }

    private fun toggleFavorite() {
        val mediaItem = player?.currentMediaItem ?: return
        val itemId = mediaItem.mediaMetadata.extras?.getString("item_id") ?: return
        val isFavorite = mediaItem.mediaMetadata.extras?.getBoolean("is_favorite", false) ?: false
        val service = apiService ?: return
        val context = context ?: return
        val prefs = context.getSharedPreferences("embysic_prefs", AppCompatActivity.MODE_PRIVATE)
        val userId = prefs.getString("user_id", "") ?: ""
        val accessToken = prefs.getString("access_token", "") ?: ""
        val authHeader = "MediaBrowser Client=\"Android\", Device=\"Android Phone\", DeviceId=\"123456\", Version=\"1.0.0\", Token=\"$accessToken\""

        lifecycleScope.launch {
            try {
                if (isFavorite) service.unmarkFavorite(userId, itemId, authHeader)
                else service.markFavorite(userId, itemId, authHeader)
                mediaItem.mediaMetadata.extras?.putBoolean("is_favorite", !isFavorite)
                updateFavoriteIcon(!isFavorite)
                Toast.makeText(context, if (isFavorite) "已取消收藏" else "已添加收藏", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "同步失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun searchNeteaseCover(itemId: String, title: String?, artist: String?) {
        lifecycleScope.launch(Dispatchers.IO) {
            val cleanedTitle = title?.replace(Regex("\\s*[([\\[].*[\\])]]"), "")?.trim() ?: ""
            val query = if (artist.isNullOrBlank() || artist == "未知歌手") cleanedTitle else "$cleanedTitle $artist"
            if (query.isEmpty()) return@launch
            try {
                // 1. 搜索歌曲，获取 songId
                val searchResponse = neteaseApi.searchSong(query)
                val song = searchResponse.result?.songs?.firstOrNull() ?: return@launch
                
                // 2. 使用新版 v3 详情接口获取封面
                // 这里的 ids 参数需要格式化为 [{"id":songId}]
                val detailResponse = neteaseApi.getSongDetail("[{\"id\":${song.id}}]")
                val picUrl = detailResponse.songs?.firstOrNull()?.al?.picUrl?.replace("http://", "https://")
                
                if (!picUrl.isNullOrEmpty()) {
                    withContext(Dispatchers.Main) {
                        if (player?.currentMediaItem?.mediaId == itemId) {
                            // 3. 将拉取到的封面保存到本地持久化缓存，供列表使用
                            saveNeteaseCoverToCache(itemId, picUrl)
                            
                            // 4. 更新当前播放器的 MediaItem，确保通知栏同步
                            updateMediaItemArtwork(picUrl)

                            // 加载界面封面
                            ivCover.load(picUrl) { 
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
                .data(data)
                .size(300)
                .transformations(BlurTransformation(context, 25, 4))
                .crossfade(true)
                .build()
            val result = ImageLoader(context).execute(request)
            if (result is SuccessResult && isAdded) {
                ivBlurBackground.setImageDrawable(result.drawable)
                // 更新状态栏图标颜色
                dialog?.window?.let { window ->
                    val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
                    WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = !isDark
                }
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
            preloadLyrics(itemId, mediaItem.mediaMetadata.title?.toString(), mediaItem.mediaMetadata.artist?.toString())
        }
    }

    private fun preloadLyrics(itemId: String, title: String?, artist: String?) {
        if (lyricsCache.containsKey(itemId)) return
        val service = apiService ?: return
        val context = context ?: return
        val prefs = context.getSharedPreferences("embysic_prefs", AppCompatActivity.MODE_PRIVATE)
        val accessToken = prefs.getString("access_token", "") ?: ""
        val authHeader = "MediaBrowser Token=\"$accessToken\""

        lifecycleScope.launch {
            try {
                val response = service.getLyrics(itemId, authHeader)
                val rawLines = response.Lines ?: response.Lyrics ?: response.LyricLines
                if (rawLines.isNullOrEmpty()) {
                    searchNeteaseLyrics(itemId, title, artist)
                } else {
                    val metadata = mutableListOf(LrcLine(-1, title ?: "未知曲名"), LrcLine(-1, artist ?: "未知歌手"))
                    val lyrics = rawLines.map { LrcLine(it.StartTicks?.let { t -> t / 10000 } ?: it.Start?.let { t -> t / 10000 } ?: 0L, it.Text) }
                        .filter { it.text.isNotBlank() }.sortedBy { it.timeMs }
                    val finalLines = metadata + lyrics
                    lyricsCache[itemId] = finalLines
                    if (rvLyrics.isVisible && player?.currentMediaItem?.mediaId == itemId) {
                        lyricsAdapter.lines = finalLines
                    }
                }
            } catch (_: Exception) { searchNeteaseLyrics(itemId, title, artist) }
        }
    }

    private fun searchNeteaseLyrics(itemId: String, title: String?, artist: String?) {
        val cleanedTitle = title?.replace(Regex("\\s*[([\\[].*[\\])]]"), "")?.trim() ?: ""
        val isUnknown = artist.isNullOrBlank() || artist.contains("未知") || artist.contains("Unknown")
        val query = if (isUnknown) cleanedTitle else "$cleanedTitle $artist"
        if (query.isEmpty()) { showNoLyrics(itemId, title, artist); return }

        lifecycleScope.launch {
            try {
                val searchResponse = neteaseApi.searchSong(query)
                val song = searchResponse.result?.songs?.firstOrNull() ?: throw Exception("Not found")
                val lyricResponse = neteaseApi.getLyric(song.id)
                val lrcText = lyricResponse.lrc?.lyric
                if (!lrcText.isNullOrBlank()) {
                    val metadata = mutableListOf(LrcLine(-1, title ?: "未知曲名"), LrcLine(-1, artist ?: "未知歌手"), LrcLine(-1, "来源: 网易云音乐"))
                    val mainLyrics = LrcParser.parse(lrcText)
                    val tlyricText = lyricResponse.tlyric?.lyric
                    val finalLines = if (!tlyricText.isNullOrBlank()) metadata + mergeLyrics(mainLyrics, LrcParser.parse(tlyricText)) else metadata + mainLyrics
                    lyricsCache[itemId] = finalLines
                    if (rvLyrics.isVisible && player?.currentMediaItem?.mediaId == itemId) {
                        lyricsAdapter.lines = finalLines
                    }
                } else { showNoLyrics(itemId, title, artist) }
            } catch (_: Exception) { 
                if (!isUnknown && cleanedTitle.isNotEmpty()) searchNeteaseLyrics(itemId, cleanedTitle, null)
                else showNoLyrics(itemId, title, artist)
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

    private fun hideLyrics() {
        rvLyrics.visibility = View.GONE
        rlDiscContainer.visibility = View.VISIBLE
    }

    private fun formatTime(ms: Long): String {
        val sec = (ms / 1000) % 60
        val min = (ms / (1000 * 60)) % 60
        return String.format(Locale.getDefault(), "%02d:%02d", min, sec)
    }

    override fun onDestroyView() {
        handler.removeCallbacks(updateProgressAction)
        discRotationHandler.removeCallbacks(discRotationRunnable)
        SongDownloader.removeProgressListener(downloadProgressListener)
        super.onDestroyView()
    }
}
