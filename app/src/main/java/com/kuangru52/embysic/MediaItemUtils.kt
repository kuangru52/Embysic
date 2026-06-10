package com.kuangru52.embysic

import android.net.Uri
import android.os.Bundle
import android.content.Context
import android.graphics.Bitmap
import android.provider.Settings
import androidx.annotation.OptIn
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
    fun getDeviceId(context: Context): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "123456"
    }

    /**
     * 获取设备市场名称 (如 "Xiaomi Pad 6S Pro 12.4") 而不是认证型号 (如 "24018RPACC")
     */
    fun getDeviceName(context: Context): String {
        var name = ""
        
        // 1. 优先从系统设置获取用户定义的设备名称 (通常就是商品名)
        try {
            name = Settings.Global.getString(context.contentResolver, "device_name") ?: ""
        } catch (_: Exception) {}

        if (name.isBlank()) {
            // 2. 尝试通过反射获取 OEM 特有的市场名称属性
            val props = arrayOf(
                "ro.product.odm.marketname",
                "ro.product.marketname",
                "ro.config.marketing_name",
                "ro.product.model.name",
                "ro.product.nickname"
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

        // 2.5 如果仍为空，或者为系统默认名称，尝试读取 /vendor/odm/etc/build.prop
        if (name.isBlank()) {
            try {
                val file = java.io.File("/vendor/odm/etc/build.prop")
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

        // 3. 最后兜底使用 Build.MODEL
        if (name.isBlank()) {
            name = android.os.Build.MODEL
        }
        return name
    }

    // 整个 App 会话期间的源码播放开关，冷启动自动重置为 false
    var isForceDirectMode = false

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
        existingMetadata: MediaMetadata? = null
    ): MediaItem {
        val baseUrl = if (serverUrl.endsWith("/")) serverUrl else "$serverUrl/"
        val mediaSourceId = song.MediaSources?.firstOrNull()?.Id ?: song.Id
        val sessionId = overrideSessionId ?: UUID.randomUUID().toString().replace("-", "")
        val deviceId = getDeviceId(context)

        val streamUrl = if (forceDirect) {
            "${baseUrl}emby/Audio/${song.Id}/stream?api_key=$accessToken&Static=true&MediaSourceId=$mediaSourceId&PlaySessionId=$sessionId"
        } else {
            "${baseUrl}emby/Audio/${song.Id}/stream.aac?api_key=$accessToken" +
                    "&DeviceId=$deviceId" +
                    "&MaxStreamingBitrate=256000" +
                    "&AudioBitrate=256000" +
                    "&AudioCodec=aac" +
                    "&Static=true" +
                    "&MediaSourceId=$mediaSourceId" +
                    "&PlaySessionId=$sessionId"
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
            // 存入 image_url 供 TabletPlayerHandler 直接使用
            putString("image_url", artworkUrl)
        }

        val metadataBuilder = existingMetadata?.buildUpon() ?: MediaMetadata.Builder()
        val metadata = metadataBuilder
            .setTitle(song.Name)
            .setDisplayTitle(song.Name)
            .setArtist(song.Artists?.joinToString(", ") ?: "未知艺术家")
            .setAlbumTitle(song.Album)
            .setAlbumArtist(song.AlbumArtists?.firstOrNull()?.Name ?: song.Artists?.firstOrNull())
            .setArtworkUri(Uri.parse(artworkUrl))
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .setIsPlayable(true)
            .setExtras(extras)
            .build()

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
            .setMediaMetadata(metadata)
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

    fun bitmapToByteArray(bitmap: Bitmap): ByteArray {
        val stream = java.io.ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
        return stream.toByteArray()
    }
}
