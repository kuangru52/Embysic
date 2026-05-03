package com.kuangru52.embysic

import android.net.Uri
import android.os.Bundle
import android.content.Context
import android.graphics.Bitmap
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object MediaItemUtils {
    private const val DEVICE_ID = "123456"

    // 整个 App 会话期间的源码播放开关，冷启动自动重置为 false
    var isForceDirectMode = false

    @OptIn(UnstableApi::class)
    fun buildMediaItem(
        song: EmbyItem,
        serverUrl: String,
        accessToken: String,
        userId: String,
        startMs: Long = 0L,
        endMs: Long = 0L,
        forceDirect: Boolean = isForceDirectMode,
        overrideSessionId: String? = null
    ): MediaItem {
        val baseUrl = if (serverUrl.endsWith("/")) serverUrl else "$serverUrl/"
        val mediaSourceId = song.MediaSources?.firstOrNull()?.Id ?: song.Id
        val sessionId = overrideSessionId ?: UUID.randomUUID().toString().replace("-", "")

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
        val hasPrimary = song.ImageTags?.containsKey("Primary") == true
        val imageId = if (hasPrimary) song.Id else (song.AlbumId ?: song.Id)
        val artworkUrl = "${serverUrl.trimEnd('/')}/emby/Items/$imageId/Images/Primary?MaxWidth=500&api_key=$accessToken"

        val source = song.MediaSources?.firstOrNull()
        val mimeType = if (forceDirect) {
            when (source?.Container?.lowercase()) {
                "flac" -> MimeTypes.AUDIO_FLAC
                "mp3" -> MimeTypes.AUDIO_MPEG
                "wav" -> MimeTypes.AUDIO_WAV
                "ogg" -> MimeTypes.AUDIO_OGG
                "m4a", "aac" -> MimeTypes.AUDIO_AAC
                "opus" -> MimeTypes.AUDIO_OPUS
                else -> MimeTypes.AUDIO_UNKNOWN
            }
        } else {
            MimeTypes.AUDIO_AAC
        }

        val extras = Bundle().apply {
            putString("item_id", song.Id)
            putString("album_id", song.AlbumId)
            putString("path", song.Path)
            putString("media_source_id", mediaSourceId)
            putString("play_session_id", sessionId)
            putLong("duration_ms", durationMs)
            val codec = if (forceDirect) (source?.Container ?: "Source") else "AAC"
            val bitrate = if (forceDirect) (source?.Bitrate ?: 0) else 256000
            putString("codec", codec.uppercase())
            putInt("bitrate", bitrate)
            putBoolean("is_transcoding", !forceDirect)
            putBoolean("is_favorite", song.UserData?.IsFavorite ?: false)
            putBoolean("has_primary_image", song.ImageTags?.containsKey("Primary") == true)
            putBoolean("has_lyrics", song.HasLyrics == true)
            putLong("real_start_ms", startMs)
            putString("parent_id", song.ParentId ?: song.AlbumId)
            
            // 添加艺术家 ID，优先取第一个
            val artistId = song.ArtistItems?.firstOrNull()?.Id ?: song.AlbumArtists?.firstOrNull()?.Id
            putString("artist_id", artistId)
        }

        return MediaItem.Builder()
            .setUri(Uri.parse(streamUrl))
            .setMediaId(if (startMs > 0) "${song.Id}_$startMs" else song.Id)
            .setMimeType(mimeType)
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(startMs)
                    .apply { if (endMs > startMs) setEndPositionMs(endMs) }
                    .build()
            )
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(song.Name)
                    .setArtist(song.Artists?.joinToString(", ") ?: "未知艺术家")
                    .setAlbumTitle(song.Album)
                    .setArtworkUri(Uri.parse(artworkUrl))
                    .setExtras(extras)
                    .build()
            )
            .build()
    }

    fun saveCoverToFile(context: Context, itemId: String, bitmap: Bitmap): String? {
        val dir = File(context.cacheDir, "covers")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "${itemId}.jpg")
        return try {
            FileOutputStream(file).use {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)
            }
            val path = file.absolutePath
            context.getSharedPreferences("netease_covers", Context.MODE_PRIVATE)
                .edit().putString(itemId, path).apply()
            path
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
