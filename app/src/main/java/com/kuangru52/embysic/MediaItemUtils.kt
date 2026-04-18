package com.kuangru52.embysic

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import java.util.UUID

object MediaItemUtils {
    private const val DEVICE_ID = "123456"

    // 整个 App 会话期间的源码播放开关，冷启动自动重置为 false
    var isForceDirectMode = false

    fun buildMediaItem(
        song: EmbyItem,
        serverUrl: String,
        accessToken: String,
        userId: String,
        startMs: Long = 0L,
        endMs: Long = 0L,
        forceDirect: Boolean = isForceDirectMode
    ): MediaItem {
        val baseUrl = if (serverUrl.endsWith("/")) serverUrl else "$serverUrl/"
        val mediaSourceId = song.MediaSources?.firstOrNull()?.Id ?: song.Id
        val sessionId = UUID.randomUUID().toString().replace("-", "")

        val streamUrl = if (forceDirect) {
            "${baseUrl}emby/Audio/${song.Id}/stream?api_key=$accessToken&Static=true&MediaSourceId=$mediaSourceId"
        } else {
            "${baseUrl}emby/Audio/${song.Id}/stream.aac?api_key=$accessToken" +
                    "&DeviceId=$DEVICE_ID" +
                    "&MaxStreamingBitrate=256000" +
                    "&AudioBitrate=256000" +
                    "&AudioCodec=aac" +
                    "&Static=true" +
                    "&MediaSourceId=$mediaSourceId"
        }

        val durationMs = if (endMs > startMs) (endMs - startMs) else ((song.RunTimeTicks ?: 0L) / 10000)
        val imageId = if (song.ImageTags?.containsKey("Primary") == true) song.Id else song.AlbumId ?: song.Id
        val artworkUrl = "${baseUrl}emby/Items/$imageId/Images/Primary?api_key=$accessToken"

        val extras = Bundle().apply {
            putString("item_id", song.Id)
            putString("media_source_id", mediaSourceId)
            putString("play_session_id", sessionId)
            putLong("duration_ms", durationMs)
            val source = song.MediaSources?.firstOrNull()
            val codec = if (forceDirect) (source?.Container ?: "Source") else "AAC"
            val bitrate = if (forceDirect) (source?.Bitrate ?: 0) else 256000
            putString("codec", codec.uppercase())
            putInt("bitrate", bitrate)
            putBoolean("is_transcoding", !forceDirect)
            putBoolean("is_favorite", song.UserData?.IsFavorite ?: false)
            putLong("real_start_ms", startMs)
        }

        return MediaItem.Builder()
            .setUri(Uri.parse(streamUrl))
            .setMediaId(if (startMs > 0) "${song.Id}_$startMs" else song.Id)
            .setMimeType(MimeTypes.AUDIO_AAC)
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(startMs)
                    .apply { if (endMs > startMs) setEndPositionMs(endMs) }
                    .build()
            )
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(song.Name)
                    .setArtist(song.Artists?.firstOrNull() ?: "未知艺术家")
                    .setArtworkUri(Uri.parse(artworkUrl))
                    .setExtras(extras)
                    .build()
            )
            .build()
    }
}
