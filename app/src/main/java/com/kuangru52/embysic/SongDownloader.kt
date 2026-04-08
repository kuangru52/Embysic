package com.kuangru52.embysic

import android.content.Context
import android.os.Environment
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors

object SongDownloader {
    private val executor = Executors.newFixedThreadPool(3)
    private val client = OkHttpClient()

    private val progressMap = mutableMapOf<String, Int>()
    private val listeners = mutableListOf<(String, Int) -> Unit>()

    fun addProgressListener(listener: (String, Int) -> Unit) {
        listeners.add(listener)
    }

    fun removeProgressListener(listener: (String, Int) -> Unit) {
        listeners.remove(listener)
    }

    fun getProgress(itemId: String): Int = progressMap[itemId] ?: 0

    /**
     * 将歌曲下载到应用的私有下载目录 (转码为 AAC)
     */
    fun downloadSong(context: Context, itemId: String, baseUrl: String, token: String, userId: String, onComplete: (File?) -> Unit = {}) {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return
        if (!dir.exists()) dir.mkdirs()
        
        val outputFile = File(dir, "$itemId.aac")
        if (outputFile.exists() && outputFile.length() > 0) {
            Log.d("SongDownloader", "File already exists: $itemId")
            progressMap[itemId] = 100
            onComplete(outputFile)
            return
        }

        executor.execute {
            try {
                // 设置初始状态为 1% 表示开始下载
                progressMap[itemId] = 1
                listeners.forEach { it(itemId, 1) }

                // 使用 Emby 的 universal 音频流接口进行转码并下载
                // 重点：设置 Container=aac 和 AudioCodec=aac 强制服务器转码
                val url = StringBuilder("${if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"}emby/Audio/$itemId/universal")
                    .append("?api_key=$token")
                    .append("&UserId=$userId")
                    .append("&DeviceId=123456")
                    .append("&MaxStreamingBitrate=320000")
                    .append("&AudioBitrate=320000")
                    .append("&Container=aac")
                    .append("&AudioCodec=aac")
                    .append("&TranscodingContainer=aac")
                    .append("&TranscodingProtocol=http")
                    .toString()

                Log.d("SongDownloader", "Starting transcoded download: $itemId from $url")
                
                val request = Request.Builder()
                    .url(url)
                    .addHeader("X-Emby-Token", token)
                    .build()
                
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.e("SongDownloader", "Download failed: ${response.code} ${response.message}")
                        progressMap.remove(itemId)
                        listeners.forEach { it(itemId, 0) }
                        onComplete(null)
                        return@execute
                    }
                    val body = response.body ?: return@execute
                    val totalBytes = body.contentLength()
                    
                    val tempFile = File(dir, "$itemId.tmp")
                    var downloadedBytes = 0L
                    var lastReportedProgress = 0
                    
                    FileOutputStream(tempFile).use { output ->
                        body.byteStream().use { input ->
                            val buffer = ByteArray(64 * 1024)
                            var bytesRead: Int
                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                output.write(buffer, 0, bytesRead)
                                downloadedBytes += bytesRead
                                
                                if (totalBytes > 0) {
                                    val progress = ((downloadedBytes * 100) / totalBytes).toInt()
                                    if (progress > lastReportedProgress) {
                                        lastReportedProgress = progress
                                        progressMap[itemId] = progress
                                        listeners.forEach { it(itemId, progress) }
                                    }
                                }
                            }
                        }
                    }
                    if (tempFile.renameTo(outputFile)) {
                        Log.d("SongDownloader", "Successfully saved transcoded file: ${outputFile.absolutePath}")
                        progressMap[itemId] = 100
                        listeners.forEach { it(itemId, 100) }
                        onComplete(outputFile)
                    } else {
                        progressMap.remove(itemId)
                        listeners.forEach { it(itemId, 0) }
                        onComplete(null)
                    }
                }
            } catch (e: Exception) {
                Log.e("SongDownloader", "Download error: ${e.message}")
                progressMap.remove(itemId)
                listeners.forEach { it(itemId, 0) }
                onComplete(null)
            }
        }
    }

    /**
     * 获取本地文件路径
     */
    fun getLocalFileUri(context: Context, itemId: String): android.net.Uri? {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return null
        val file = File(dir, "$itemId.aac")
        return if (file.exists() && file.length() > 0) android.net.Uri.fromFile(file) else null
    }
}
