package com.kuangru52.embysic

import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.Settings
import androidx.annotation.OptIn
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object MediaItemUtils {
    /**
     * 获取设备唯一 ID，用于 Emby 会话识别
     */
    @Suppress("HardwareIds")
    fun getDeviceId(context: Context): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "123456"
    }

    /**
     * 获取设备市场名称 (如 "Xiaomi Pad 6S Pro 12.4") 而不是认证型号 (如 "24018RPACC")
     */
    @Suppress("PrivateApi", "DiscouragedPrivateApi")
    fun getDeviceName(context: Context): String {
        var name = ""
        
        try {
            name = Settings.Global.getString(context.contentResolver, "device_name") ?: ""
        } catch (_: Exception) {}

        if (name.isBlank()) {
            val props = arrayOf(
                "ro.product.odm.marketname",
                "ro.product.marketname",
                "ro.config.marketing_name",
                "ro.product.model.name",
                "ro.product.nickname",
            )
            try {
                val systemProperties = Class.forName("android.os.SystemProperties")
                val getMethod = systemProperties.getMethod("get", String::class.java, String::class.java)
                for (prop in props) {
                    val value = getMethod.invoke(null, prop, "") as String
                    if (value.isNotBlank()) {
                        name = value
                        break
                    }
                }
            } catch (_: Exception) {}
        }

        if (name.isBlank()) {
            try {
                val file = File("/vendor/odm/etc/build.prop")
                if (file.exists() && file.canRead()) {
                    file.useLines { lines ->
                        for (line in lines) {
                            if (line.startsWith("ro.product.odm.marketname=")) {
                                val value = line.substringAfter("=").trim()
                                if (value.isNotBlank()) {
                                    name = value
                                    break
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        if (name.isBlank()) {
            name = android.os.Build.MODEL
        }
        return name
    }

    // 整个 App 会话期间的源码播放开关，冷启动自动重置为 false
    var isForceDirectMode = false

    @Suppress("UNUSED_PARAMETER")
    @OptIn(UnstableApi::class)
    fun buildMediaItem(
        context: Context,
        song: EmbyItem,
        serverUrl: String,
        accessToken: String,
        userId: String,
        startMs: Long = 0L,
        endMs: Long = 0L,
        forceDirect: Boolean = isForceDirectMode,
        overrideSessionId: String? = null,
        existingMetadata: MediaMetadata? = null,
    ): MediaItem {
        val baseUrl = if (serverUrl.endsWith("/")) serverUrl else "$serverUrl/"
        val mediaSourceId = song.MediaSources?.firstOrNull()?.Id ?: song.Id
        val sessionId = overrideSessionId ?: UUID.randomUUID().toString().replace("-", "")
        val deviceId = getDeviceId(context)

        val finalUrl = if (forceDirect) {
            "${baseUrl}emby/Audio/${song.Id}/stream?api_key=$accessToken&Static=true&MediaSourceId=$mediaSourceId&PlaySessionId=$sessionId" +
                    if (startMs > 0) "&StartPositionTicks=${startMs * 10000}" else ""
        } else {
            "${baseUrl}emby/Audio/${song.Id}/stream?api_key=$accessToken" +
                    "&DeviceId=$deviceId" +
                    "&MediaSourceId=$mediaSourceId" +
                    "&PlaySessionId=$sessionId" +
                    "&MaxStreamingBitrate=256000" +
                    "&AudioBitrate=256000" +
                    "&AudioCodec=aac" +
                    "&Container=aac" +
                    "&Static=false" +
                    "&EnableDirectPlay=false" +
                    "&EnableDirectStream=false" +
                    if (startMs > 0) "&StartPositionTicks=${startMs * 10000}" else ""
        }

        val streamUrl = if (song.IsFolder) "" else finalUrl

        val durationMs = if (endMs > startMs) (endMs - startMs) else ((song.RunTimeTicks ?: 0L) / 10000)
        val hasPrimary = song.ImageTags?.containsKey("Primary") == true
        val imageId = when {
            hasPrimary -> song.Id
            !song.AlbumId.isNullOrEmpty() -> song.AlbumId
            !song.ParentId.isNullOrEmpty() -> song.ParentId
            else -> song.Id
        }
        val artworkUrl = "${serverUrl.trimEnd('/')}/emby/Items/$imageId/Images/Primary?MaxWidth=600&api_key=$accessToken"

        val source = song.MediaSources?.firstOrNull()
        val mimeType: String? = if (forceDirect) {
            when (source?.Container?.lowercase()) {
                "flac" -> MimeTypes.AUDIO_FLAC
                "mp3" -> MimeTypes.AUDIO_MPEG
                "wav" -> MimeTypes.AUDIO_WAV
                "ogg" -> MimeTypes.AUDIO_OGG
                "m4a", "aac" -> MimeTypes.AUDIO_AAC
                "opus" -> MimeTypes.AUDIO_OPUS
                else -> null
            }
        } else {
            null // 转码模式下由 ExoPlayer 自动嗅探格式，避免 ID3/ADTS 报 MimeType 错误
        }

        val extras = Bundle().apply {
            putString("item_id", song.Id)
            putString("parent_id", song.ParentId ?: song.AlbumId)
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
            
            val artistId = song.ArtistItems?.firstOrNull()?.Id ?: song.AlbumArtists?.firstOrNull()?.Id
            putString("artist_id", artistId)
            putString("image_url", artworkUrl)
        }

        val metadataBuilder = existingMetadata?.buildUpon() ?: MediaMetadata.Builder()
        val metadata = metadataBuilder
            .setTitle(song.Name)
            .setDisplayTitle(song.Name)
            .setArtist(song.Artists?.joinToString(", ") ?: "未知艺术家")
            .setAlbumTitle(song.Album)
            .setAlbumArtist(song.AlbumArtists?.firstOrNull()?.Name ?: song.Artists?.firstOrNull())
            .setArtworkUri(artworkUrl.toUri())
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .setIsPlayable(true)
            .setExtras(extras)
            .build()

        val builder = MediaItem.Builder()
            .setUri(streamUrl.toUri())
            .setMediaId(if (startMs > 0) "${song.Id}_$startMs" else song.Id)
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(startMs)
                    .apply { if (endMs > startMs) setEndPositionMs(endMs) }
                    .build(),
            )
            .setMediaMetadata(metadata)

        mimeType?.let { builder.setMimeType(it) }

        return builder.build()
    }

    fun saveCoverToFile(context: Context, itemId: String, bitmap: Bitmap): String? {
        val dir = File(context.cacheDir, "covers")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "$itemId.jpg")
        return try {
            FileOutputStream(file).use {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)
            }
            val path = file.absolutePath
            context.getSharedPreferences("netease_covers", Context.MODE_PRIVATE).edit {
                putString(itemId, path)
            }
            path
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun bitmapToByteArray(bitmap: Bitmap): ByteArray {
        val stream = java.io.ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
        return stream.toByteArray()
    }

    @OptIn(UnstableApi::class)
    fun seekToPosition(player: androidx.media3.common.Player, targetMs: Long) {
        val extras = player.currentMediaItem?.mediaMetadata?.extras
        val injectDuration = extras?.getLong("duration_ms") ?: 0L
        val dur = if (player.duration > 0) player.duration else injectDuration
        val safeTargetMs = if (dur > 0) targetMs.coerceIn(0L, dur) else targetMs.coerceAtLeast(0L)

        player.seekTo(safeTargetMs)
    }
}
