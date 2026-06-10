package com.kuangru52.embysic

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.*
import androidx.media3.session.DefaultMediaNotificationProvider
import coil.ImageLoader
import coil.request.ImageRequest
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.*
import java.io.File

@UnstableApi
class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private lateinit var exoPlayer: ExoPlayer
    private lateinit var wrappedPlayer: Player
    private lateinit var cacheDataSourceFactory: CacheDataSource.Factory
    private var embyApi: EmbyApiService? = null
    private lateinit var neteaseApi: NeteaseApiService
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    private val neteasePrefs by lazy { getSharedPreferences("netease_covers", MODE_PRIVATE) }
    private val imageLoader by lazy { ImageLoader(this) }
    private var currentArtworkBitmap: Bitmap? = null
    
    private var lastReportedItemId: String? = null
    private var currentItemExtras: Bundle? = null
    private var lastKnownPositionMs: Long = 0
    private var isStartReported = false
    private var currentReportingItemId: String? = null

    override fun onCreate() {
        super.onCreate()
        
        val cache = CacheManager.getCache(this)
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
        cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(httpDataSourceFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        embyApi = RetrofitClient.getEmbyApiService(this)
        neteaseApi = RetrofitClient.neteaseApi

        exoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(AudioAttributes.DEFAULT, true)
            .setMediaSourceFactory(DefaultMediaSourceFactory(this).setDataSourceFactory(cacheDataSourceFactory))
            .build()

        wrappedPlayer = object : ForwardingPlayer(exoPlayer) {
            override fun getAvailableCommands(): Player.Commands = super.getAvailableCommands().buildUpon()
                .add(COMMAND_PLAY_PAUSE)
                .add(COMMAND_SEEK_TO_NEXT)
                .add(COMMAND_SEEK_TO_PREVIOUS)
                .add(COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
                .add(COMMAND_SEEK_BACK)
                .add(COMMAND_SEEK_FORWARD)
                .build()
        }

        exoPlayer.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val newId = mediaItem?.mediaId
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED && newId == lastReportedItemId) return

                if (lastReportedItemId != null) {
                    reportStop(lastReportedItemId!!, currentItemExtras, lastKnownPositionMs, false)
                }

                if (mediaItem == null) {
                    lastReportedItemId = null
                    currentItemExtras = null
                    lastKnownPositionMs = 0
                    return
                }

                currentArtworkBitmap = null
                preloadArtwork(mediaItem)

                lastReportedItemId = mediaItem.mediaId
                currentItemExtras = mediaItem.mediaMetadata.extras
                lastKnownPositionMs = 0
                isStartReported = false
                currentReportingItemId = null
                
                reportStart()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    val item = exoPlayer.currentMediaItem
                    if (item != null) {
                        reportStop(item.mediaId, item.mediaMetadata.extras, exoPlayer.duration, true)
                    }
                }
            }

            override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
                lastKnownPositionMs = newPosition.positionMs
            }
        })

        val notificationProvider = DefaultMediaNotificationProvider.Builder(this).build()
        notificationProvider.setSmallIcon(R.drawable.logo)
        setMediaNotificationProvider(notificationProvider)

        mediaSession = MediaSession.Builder(this, wrappedPlayer)
            .setCallback(CustomCallback())
            .setSessionActivity(
                android.app.PendingIntent.getActivity(
                    this,
                    0,
                    android.content.Intent(this, HomeActivity::class.java).apply {
                        putExtra("show_player", true)
                        flags = android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
                    },
                    android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            .build()
            
        startProgressReporting()
    }

    private fun preloadArtwork(item: MediaItem) {
        val baseId = getBaseMediaId(item.mediaId)
        val cachedPath = neteasePrefs.getString(baseId, null)
        
        if (cachedPath != null && File(cachedPath).exists()) {
            loadBitmap(Uri.fromFile(File(cachedPath)), baseId)
            return
        }

        val embyUri = item.mediaMetadata.artworkUri
        val hasPrimary = item.mediaMetadata.extras?.getBoolean("has_primary_image") ?: false
        if (hasPrimary && embyUri != null) {
            loadBitmap(embyUri, baseId)
            return
        }

        serviceScope.launch(Dispatchers.IO) {
            val title = item.mediaMetadata.title?.toString() ?: return@launch
            val artist = item.mediaMetadata.artist?.toString() ?: ""
            val album = item.mediaMetadata.albumTitle?.toString() ?: ""

            try {
                val query = if (album.isNotEmpty()) "$title $album" else "$title $artist"
                val res = neteaseApi.searchSong(query)
                val best = res.result?.songs?.let { LyricUtils.findBestMatch(it, title, artist, album, 0L) }
                val url = best?.album?.picUrl ?: best?.id?.let {
                    neteaseApi.getSongDetail("[{\"id\":$it}]").songs?.firstOrNull()?.al?.picUrl
                }
                if (url != null) {
                    downloadAndCache(url, baseId)
                    return@launch
                }
            } catch (_: Exception) {}

            if (album.isNotEmpty()) {
                // 已移除 Discogs 调用，不再尝试从 Discogs 获取封面
            }
        }
    }

    private fun loadBitmap(uri: Uri, baseId: String) {
        val request = ImageRequest.Builder(this)
            .data(uri)
            .allowHardware(false)
            .size(512)
            .target(onSuccess = { result ->
                currentArtworkBitmap = (result as BitmapDrawable).bitmap
                updateMetadataWithArtwork(baseId, uri)
            })
            .build()
        imageLoader.enqueue(request)
    }

    private suspend fun downloadAndCache(url: String, baseId: String) {
        val request = ImageRequest.Builder(this).data(url).allowHardware(false).build()
        val result = imageLoader.execute(request)
        if (result is coil.request.SuccessResult) {
            val bitmap = (result.drawable as BitmapDrawable).bitmap
            val path = MediaItemUtils.saveCoverToFile(this, baseId, bitmap)
            if (path != null) {
                withContext(Dispatchers.Main) {
                    currentArtworkBitmap = bitmap
                    updateMetadataWithArtwork(baseId, Uri.fromFile(File(path)))
                }
            }
        }
    }

    private fun updateMetadataWithArtwork(baseId: String, artworkUri: Uri) {
        val currentItem = exoPlayer.currentMediaItem ?: return
        if (getBaseMediaId(currentItem.mediaId) != baseId) return
        
        val metadataBuilder = currentItem.mediaMetadata.buildUpon()
            .setArtworkUri(artworkUri)
        
        currentArtworkBitmap?.let {
            val byteArray = MediaItemUtils.bitmapToByteArray(it)
            metadataBuilder.setArtworkData(byteArray, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
        }

        val newMetadata = metadataBuilder.build()
        val newItem = currentItem.buildUpon()
            .setMediaMetadata(newMetadata)
            .build()

        val currentIndex = exoPlayer.currentMediaItemIndex
        exoPlayer.replaceMediaItem(currentIndex, newItem)
    }

    private fun getBaseMediaId(id: String): String = id.split("_").first()

    private fun getAuthHeader(): String? {
        val prefs = getSharedPreferences("embysic_prefs", MODE_PRIVATE)
        val accessToken = prefs.getString("access_token", null)
        return if (accessToken != null) "MediaBrowser Token=\"$accessToken\"" else null
    }

    private fun getUserId(): String? {
        val prefs = getSharedPreferences("embysic_prefs", MODE_PRIVATE)
        return prefs.getString("user_id", null)
    }

    private fun reportStart() {
        val mediaId = exoPlayer.currentMediaItem?.mediaId ?: return
        val extras = exoPlayer.currentMediaItem?.mediaMetadata?.extras ?: return
        val itemId = getBaseMediaId(mediaId)
        val playSessionId = extras.getString("play_session_id")
        val mediaSourceId = extras.getString("media_source_id") ?: itemId
        
        val auth = getAuthHeader() ?: return
        val userId = getUserId() ?: return
        
        if (isStartReported && currentReportingItemId == itemId) return

        serviceScope.launch {
            try {
                embyApi?.reportPlaybackStart(
                    auth,
                    PlaybackReportInfo(ItemId = itemId, UserId = userId, PlaySessionId = playSessionId, MediaSourceId = mediaSourceId)
                )
                isStartReported = true
                currentReportingItemId = itemId
            } catch (_: Exception) { }
        }
    }

    private fun reportStop(mediaId: String, extras: Bundle?, positionMs: Long, isEnded: Boolean) {
        val itemId = getBaseMediaId(mediaId)
        val playSessionId = extras?.getString("play_session_id")
        val mediaSourceId = extras?.getString("media_source_id") ?: itemId
        val auth = getAuthHeader() ?: return
        val userId = getUserId() ?: return
        
        serviceScope.launch {
            try {
                if (isEnded) embyApi?.markPlayed(userId, itemId, auth)
                embyApi?.reportPlaybackStop(
                    auth,
                    PlaybackReportInfo(
                        ItemId = itemId, UserId = userId, PlaySessionId = playSessionId,
                        MediaSourceId = mediaSourceId, PositionTicks = positionMs * 10000
                    )
                )
            } catch (_: Exception) {}
        }
    }

    private fun startProgressReporting() {
        serviceScope.launch {
            while (isActive) {
                if (exoPlayer.isPlaying) {
                    val item = exoPlayer.currentMediaItem
                    val extras = item?.mediaMetadata?.extras
                    if (item != null) {
                        val itemId = getBaseMediaId(item.mediaId)
                        val playSessionId = extras?.getString("play_session_id")
                        val mediaSourceId = extras?.getString("media_source_id") ?: itemId
                        val auth = getAuthHeader()
                        val userId = getUserId()
                        
                        if (auth != null && userId != null) {
                            try {
                                embyApi?.reportPlaybackProgress(
                                    auth,
                                    PlaybackProgressInfo(
                                        ItemId = itemId, UserId = userId, PlaySessionId = playSessionId,
                                        MediaSourceId = mediaSourceId, PositionTicks = exoPlayer.currentPosition * 10000,
                                        IsPaused = !exoPlayer.playWhenReady
                                    )
                                )
                                lastKnownPositionMs = exoPlayer.currentPosition
                            } catch (_: Exception) {}
                        }
                    }
                }
                delay(10000)
            }
        }
    }

    private class CustomCallback : MediaSession.Callback {
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            val playerCommands = session.player.availableCommands.buildUpon()
                .add(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
                .add(Player.COMMAND_SEEK_BACK)
                .add(Player.COMMAND_SEEK_FORWARD)
                .build()
            return MediaSession.ConnectionResult.accept(SessionCommands.EMPTY, playerCommands)
        }

        override fun onAddMediaItems(mediaSession: MediaSession, controller: MediaSession.ControllerInfo, mediaItems: List<MediaItem>): ListenableFuture<List<MediaItem>> {
            val future = SettableFuture.create<List<MediaItem>>()
            future.set(mediaItems)
            return future
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        if (lastReportedItemId != null) {
            reportStop(lastReportedItemId!!, currentItemExtras, exoPlayer.currentPosition, false)
        }
        serviceScope.cancel()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
