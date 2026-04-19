package com.kuangru52.embysic

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.collect.ImmutableList
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaNotification
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File

@UnstableApi
class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private var apiService: EmbyApiService? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val handler = Handler(Looper.getMainLooper())
    private var lastReportedItemId: String? = null
    private var currentItemExtras: Bundle? = null
    private var lastKnownPositionMs: Long = 0

    private val updateLastPosRunnable = object : Runnable {
        override fun run() {
            mediaSession?.player?.let {
                if (it.isPlaying) {
                    lastKnownPositionMs = it.currentPosition
                }
            }
            handler.postDelayed(this, 1000)
        }
    }

    private val reportProgressRunnable = object : Runnable {
        override fun run() {
            val player = mediaSession?.player
            if (player != null && player.isPlaying) {
                reportProgress(player)
            }
            handler.postDelayed(this, 15000) // 每 15 秒汇报一次，Emby 建议频率
        }
    }

    // 移除旧的私有 cache 定义，使用全局 CacheManager
    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        initApiService()
        handler.post(updateLastPosRunnable)
        
        val accessToken = getSharedPreferences("embysic_prefs", MODE_PRIVATE).getString("access_token", "") ?: ""
        
        // 1. 构建支持缓存的数据源 (切换到 OkHttp 以获得更好的移动网络稳定性)
        val httpDataSourceFactory = androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(
            okhttp3.OkHttpClient.Builder()
                .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                .build()
        )
            .setUserAgent("Embysic/1.12")
            .setDefaultRequestProperties(mapOf("X-Emby-Token" to accessToken))

        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(CacheManager.getCache(this)) // 使用 1GB 全局缓存
            .setUpstreamDataSourceFactory(httpDataSourceFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        // 2. 自定义缓冲策略：让它更积极地预加载，应对不主动缓存的问题
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                50000,  // minBufferMs: 提高到 50s。只要不满 50s，播放器就会一直尝试下载
                120000, // maxBufferMs: 最大缓存 120s
                2500,   // bufferForPlaybackMs: 2.5s 即可起播
                5000    // bufferForPlaybackAfterRebufferMs: 5s
            )
            .setBackBuffer(30000, true) // 增加 30s 后退缓冲区
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        // 3. 初始化 ExoPlayer
        val exoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(AudioAttributes.DEFAULT, true)
            .setMediaSourceFactory(DefaultMediaSourceFactory(this).setDataSourceFactory(cacheDataSourceFactory))
            .setLoadControl(loadControl)
            .build()

        exoPlayer.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                // 1. 汇报旧歌停止：必须使用旧歌的 Extras（包含其对应的 PlaySessionId）
                if (lastReportedItemId != null) {
                    reportStop(lastReportedItemId, currentItemExtras, lastKnownPositionMs)
                }
                
                // 2. 更新当前歌曲上下文
                lastReportedItemId = mediaItem?.mediaId
                currentItemExtras = mediaItem?.mediaMetadata?.extras
                lastKnownPositionMs = 0
                isStartReported = false
                
                // 3. 汇报新歌开始
                reportStart()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    reportStart()
                    handler.removeCallbacks(reportProgressRunnable)
                    handler.postDelayed(reportProgressRunnable, 15000)
                } else {
                    handler.removeCallbacks(reportProgressRunnable)
                    // 暂停时记录最后进度并汇报
                    lastKnownPositionMs = exoPlayer.currentPosition
                    reportProgress(exoPlayer)
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                    // 记录最后有效进度并立即上报 Seek 给 Emby
                    lastKnownPositionMs = exoPlayer.currentPosition
                    reportProgress(exoPlayer, "Seek")
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    // 正常结束，汇报 0 表示“听完了”，reportStop 内部会转为 duration
                    reportStop(exoPlayer.currentMediaItem?.mediaId, currentItemExtras, 0L)
                    lastReportedItemId = null
                    currentItemExtras = null
                }
            }
        })

        // 3. 强制开启寻址权限（工业级兜底）
        val wrappedPlayer = object : ForwardingPlayer(exoPlayer) {
            override fun seekToNext() {
                super.seekToNext()
                play() // 强制播放
            }

            override fun seekToNextMediaItem() {
                super.seekToNextMediaItem()
                play() // 强制播放
            }

            override fun seekToPrevious() {
                super.seekToPrevious()
                play() // 强制播放
            }

            override fun seekToPreviousMediaItem() {
                super.seekToPreviousMediaItem()
                play() // 强制播放
            }

            override fun getDuration(): Long {
                val d = super.getDuration()
                // 如果流还没解析出时长，从 extras 拿元数据时长兜底，确保 UI 进度条能显示
                return if (d <= 0) currentItemExtras?.getLong("duration_ms") ?: 0L else d
            }

            override fun isCurrentMediaItemSeekable(): Boolean = true
            
            override fun getAvailableCommands(): Player.Commands {
                return super.getAvailableCommands().buildUpon()
                    .add(Player.COMMAND_PLAY_PAUSE)
                    .add(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
                    .add(Player.COMMAND_SEEK_TO_NEXT)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                    .add(Player.COMMAND_SET_SPEED_AND_PITCH)
                    .build()
            }
        }

        mediaSession = MediaSession.Builder(this, wrappedPlayer)
            .setSessionActivity(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, HomeActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                        putExtra("open_player", true)
                    },
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            .setCallback(object : MediaSession.Callback {
                override fun onPlaybackResumption(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo
                ): com.google.common.util.concurrent.ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
                    // 恢复播放上一次的项目和位置
                    val player = session.player
                    val mediaItem = player.currentMediaItem
                    val startPositionMs = player.currentPosition
                    
                    val result = if (mediaItem != null) {
                        MediaSession.MediaItemsWithStartPosition(
                            listOf(mediaItem),
                            player.currentMediaItemIndex,
                            startPositionMs
                        )
                    } else {
                        // 如果当前没有项目，返回空（或由 App 逻辑决定加载什么）
                        MediaSession.MediaItemsWithStartPosition(listOf(), 0, 0L)
                    }
                    
                    return com.google.common.util.concurrent.Futures.immediateFuture(result)
                }

                override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
                    // 彻底放开所有权限，包括 Seek (Command 5)
                    val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                        .build()
                    val playerCommands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
                        .add(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
                        .add(Player.COMMAND_SEEK_TO_NEXT)
                        .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                        .add(Player.COMMAND_GET_CURRENT_MEDIA_ITEM)
                        .build()
                    
                    return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                        .setAvailableSessionCommands(sessionCommands)
                        .setAvailablePlayerCommands(playerCommands)
                        .build()
                }
            }).build()

        // 使用包装器来实现自定义 Provider
        val defaultProvider = DefaultMediaNotificationProvider(this)
        setMediaNotificationProvider(object : MediaNotification.Provider {
            override fun createNotification(
                session: MediaSession,
                customLayout: ImmutableList<CommandButton>,
                actionFactory: MediaNotification.ActionFactory,
                onNotificationChangedCallback: MediaNotification.Provider.Callback
            ): MediaNotification {
                val mediaNotification = defaultProvider.createNotification(
                    session, customLayout, actionFactory, onNotificationChangedCallback
                )
                // 设置颜色为极低不透明度的白色，营造玻璃感
                mediaNotification.notification.color = 0x1EFFFFFF.toInt()
                // 禁用颜色化背景，以尝试获得更透明的系统默认效果
                mediaNotification.notification.extras.putBoolean("android.colorized", false)
                return mediaNotification
            }

            override fun handleCustomCommand(session: MediaSession, action: String, extras: Bundle): Boolean {
                return defaultProvider.handleCustomCommand(session, action, extras)
            }
        })
    }

    private fun initApiService() {
        val prefs = getSharedPreferences("embysic_prefs", MODE_PRIVATE)
        val url = prefs.getString("server_url", "") ?: ""
        if (url.isNotEmpty()) {
            apiService = Retrofit.Builder()
                .baseUrl(if (url.endsWith("/")) url else "$url/")
                .addConverterFactory(GsonConverterFactory.create())
                .build().create(EmbyApiService::class.java)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = mediaSession

    private fun getAuthHeader(): String {
        val prefs = getSharedPreferences("embysic_prefs", MODE_PRIVATE)
        val accessToken = prefs.getString("access_token", "") ?: ""
        val userId = prefs.getString("user_id", "") ?: ""
        // 完全对标 SPlayer 的 Header 格式
        return "MediaBrowser Client=\"Embysic\", Device=\"Android Phone\", DeviceId=\"123456\", Version=\"1.12\", Token=\"$accessToken\", UserId=\"$userId\""
    }

    private var isStartReported = false
    private var currentReportingItemId: String? = null

    private fun reportStart() {
        val player = mediaSession?.player ?: return
        val item = player.currentMediaItem ?: return
        val extras = item.mediaMetadata.extras ?: return
        val service = apiService ?: return
        
        // 【关键】主线程立即捕获快照
        val itemId = extras.getString("item_id") ?: item.mediaId
        val mediaSourceId = extras.getString("media_source_id") ?: itemId
        val sessionId = extras.getString("play_session_id")
        val positionTicks = player.currentPosition * 10000
        val auth = getAuthHeader()
        val prefs = getSharedPreferences("embysic_prefs", MODE_PRIVATE)
        val userId = prefs.getString("user_id", "") ?: ""

        if (isStartReported && currentReportingItemId == itemId) return

        serviceScope.launch {
            try {
                service.reportPlaybackStart(
                    auth,
                    PlaybackReportInfo(
                        ItemId = itemId!!,
                        UserId = userId,
                        MediaSourceId = mediaSourceId,
                        PlaySessionId = sessionId,
                        PositionTicks = positionTicks,
                        PlayMethod = "DirectStream",
                        CanSeek = true
                    )
                )
                isStartReported = true
                currentReportingItemId = itemId
                Log.d("PlaybackService", "Emby >> Start: $itemId (Session: $sessionId)")
            } catch (e: Exception) {
                Log.e("PlaybackService", "Emby Start Error: ${e.message}")
            }
        }
    }

    private fun reportProgress(player: Player, event: String = "TimeUpdate") {
        val item = player.currentMediaItem ?: return
        val extras = item.mediaMetadata.extras ?: return
        val service = apiService ?: return
        
        // 【关键】主线程立即捕获快照，防止异步导致的进度归零
        val itemId = extras.getString("item_id") ?: item.mediaId
        val mediaSourceId = extras.getString("media_source_id") ?: itemId
        val sessionId = extras.getString("play_session_id")
        val positionTicks = player.currentPosition * 10000
        val isPaused = !player.isPlaying
        val volume = (player.volume * 100).toInt()
        val auth = getAuthHeader()
        val prefs = getSharedPreferences("embysic_prefs", MODE_PRIVATE)
        val userId = prefs.getString("user_id", "") ?: ""

        serviceScope.launch {
            try {
                service.reportPlaybackProgress(
                    auth,
                    PlaybackProgressInfo(
                        ItemId = itemId!!,
                        UserId = userId,
                        PositionTicks = positionTicks,
                        MediaSourceId = mediaSourceId,
                        PlaySessionId = sessionId,
                        IsPaused = isPaused,
                        PlayMethod = "DirectStream",
                        EventName = event,
                        VolumeLevel = volume
                    )
                )
            } catch (e: Exception) {
                Log.e("PlaybackService", "Emby Progress Error: ${e.message}")
            }
        }
    }

    private fun reportStop(stoppedItemId: String?, extras: Bundle?, lastPos: Long) {
        val service = apiService ?: return
        val itemId = stoppedItemId ?: return
        
        val mediaSourceId = extras?.getString("media_source_id") ?: itemId
        val sessionId = extras?.getString("play_session_id")
        val auth = getAuthHeader()
        val prefs = getSharedPreferences("embysic_prefs", MODE_PRIVATE)
        val userId = prefs.getString("user_id", "") ?: ""
        
        // 【核心算法】如果进度接近终点，上报总时长，触发 Emby 的“已播放”标记
        val durationMs = extras?.getLong("duration_ms") ?: 0L
        val finalPositionTicks = if (lastPos > 1000) lastPos * 10000 else durationMs * 10000

        serviceScope.launch {
            try {
                service.reportPlaybackStopped(
                    auth,
                    PlaybackReportInfo(
                        ItemId = itemId,
                        UserId = userId,
                        MediaSourceId = mediaSourceId,
                        PlaySessionId = sessionId,
                        PositionTicks = finalPositionTicks,
                        PlayMethod = "DirectStream"
                    )
                )
                isStartReported = false
                currentReportingItemId = null
                Log.d("PlaybackService", "Emby >> Stopped: $itemId at $finalPositionTicks (Session: $sessionId)")
            } catch (e: Exception) {
                Log.e("PlaybackService", "Emby Stop Error: ${e.message}")
            }
        }
    }

    override fun onDestroy() {
        mediaSession?.player?.release()
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }
}
