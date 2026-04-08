package com.kuangru52.embysic

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import java.util.UUID

object MediaItemUtils {
    private const val DEVICE_ID = "123456"

    fun buildMediaItem(
        song: EmbyItem,
        serverUrl: String,
        accessToken: String,
        userId: String
    ): MediaItem {
        val baseUrl = if (serverUrl.endsWith("/")) serverUrl else "$serverUrl/"
        val mediaSourceId = song.MediaSources?.firstOrNull()?.Id ?: song.Id
        val sessionId = UUID.randomUUID().toString().replace("-", "")

        // 优雅方案：使用 /stream 接口伪装成静态文件
        // 关键点：Static=true 配合 .aac 后缀，诱导 Emby 返回 Content-Length，从而激活 Media3 的原生 Seek
        val streamUrl = "${baseUrl}emby/Audio/${song.Id}/stream.aac?api_key=$accessToken" +
                "&DeviceId=$DEVICE_ID" +
                "&MaxStreamingBitrate=256000" +
                "&AudioBitrate=256000" +
                "&AudioCodec=aac" +
                "&Static=true" +
                "&MediaSourceId=$mediaSourceId"

        val durationMs = (song.RunTimeTicks ?: 0L) / 10000
        val imageId = if (song.ImageTags?.containsKey("Primary") == true) song.Id else song.AlbumId ?: song.Id
        val artworkUrl = "${baseUrl}emby/Items/$imageId/Images/Primary?api_key=$accessToken"

        val extras = Bundle().apply {
            putString("item_id", song.Id)
            putString("media_source_id", mediaSourceId)
            putString("play_session_id", sessionId)
            putLong("duration_ms", durationMs)
            putString("codec", "AAC")
            putInt("bitrate", 256000)
            putBoolean("is_transcoding", true)
            putBoolean("is_favorite", song.UserData?.IsFavorite ?: false)
        }

        return MediaItem.Builder()
            .setUri(Uri.parse(streamUrl))
            .setMediaId(song.Id)
            .setMimeType(MimeTypes.AUDIO_AAC)
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
