package com.kuangru52.embysic

import kotlin.math.abs

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

/**
 * 核心匹配工具类
 * 注意：虽然名字叫 LyricUtils，但它承担了搜索结果（歌词、封面）与当前歌曲的【元数据匹配】核心逻辑。
 */
object LyricUtils {
    // 内存缓存歌词，供全局（手机/平板）共享
    val lyricsCache: MutableMap<String, List<LrcLine>> = mutableMapOf()

    /**
     * 统一同步逻辑：一次性抓取封面和歌词
     */
    suspend fun fetchNeteaseMetadata(
        context: Context,
        itemId: String,
        title: String?,
        artist: String?,
        album: String?,
        durationMs: Long,
        onCoverReady: (Any) -> Unit = {},
        onLyricsReady: (List<LrcLine>) -> Unit = {}
    ) {
        val cleanedTitle = title?.replace(Regex("\\s*[(\\[].*?[)\\]]"), "")?.trim() ?: ""
        val isArtistUnknown = artist.isNullOrBlank() || artist.contains("未知") || artist.contains("Unknown")
        val isAlbumUnknown = album.isNullOrBlank() || album.contains("未知") || album.contains("Unknown")
        
        val query = if (!isAlbumUnknown && !isArtistUnknown) {
            "$album $artist"
        } else if (!isArtistUnknown) {
            "$cleanedTitle $artist"
        } else {
            cleanedTitle
        }
        
        if (query.isEmpty()) return

        try {
            val neteaseApi = RetrofitClient.neteaseApi
            val searchResponse = neteaseApi.searchSong(query, limit = 15)
            val songs = searchResponse.result?.songs ?: return
            
            val bestMatch = findBestMatch(songs, cleanedTitle, artist, album, durationMs) ?: return

            // 1. 同步抓取封面
            val detailResponse = neteaseApi.getSongDetail("[{\"id\":${bestMatch.id}}]")
            val picUrl = detailResponse.songs?.firstOrNull()?.al?.picUrl?.replace("http://", "https://")
            
            if (!picUrl.isNullOrEmpty()) {
                // 下载并缓存封面到本地文件，确保手机/平板通用
                downloadAndCacheCover(context, itemId, picUrl, onCoverReady)
            }

            // 2. 同步抓取歌词
            val lyricResponse = neteaseApi.getLyric(bestMatch.id)
            val lrcText = lyricResponse.lrc?.lyric
            if (!lrcText.isNullOrBlank()) {
                val metadata = mutableListOf(
                    LrcLine(-1, title ?: "未知歌曲"),
                    LrcLine(-1, artist ?: "未知艺术家"),
                    LrcLine(-1, "来源: 网易云音乐 (智能匹配)")
                )
                val mainLyrics = LrcParser.parse(lrcText)
                val tlyricText = lyricResponse.tlyric?.lyric
                val finalLines = if (!tlyricText.isNullOrBlank()) {
                    metadata + mergeLyrics(mainLyrics, LrcParser.parse(tlyricText))
                } else {
                    metadata + mainLyrics
                }

                // 写入内存和磁盘缓存
                lyricsCache[itemId] = finalLines
                context.getSharedPreferences("lyrics_disk_cache", Context.MODE_PRIVATE).edit {
                    putString(itemId, Gson().toJson(finalLines))
                }
                
                withContext(Dispatchers.Main) {
                    onLyricsReady(finalLines)
                }
            }
        } catch (e: Exception) {
            Log.e("LyricUtils", "Failed to fetch Netease metadata: ${e.message}")
            // 如果是带歌手搜不到，尝试降级只搜标题
            if (!isArtistUnknown && cleanedTitle.isNotEmpty()) {
                fetchNeteaseMetadata(context, itemId, cleanedTitle, null, null, durationMs, onCoverReady, onLyricsReady)
            }
        }
    }

    private suspend fun downloadAndCacheCover(context: Context, itemId: String, url: String, onCoverReady: (Any) -> Unit) {
        val request = ImageRequest.Builder(context)
            .data(url)
            .allowHardware(false)
            .build()
        
        val result = context.imageLoader.execute(request)
        if (result is SuccessResult) {
            val bitmap = (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
            if (bitmap != null) {
                MediaItemUtils.saveCoverToFile(context, itemId, bitmap)
                withContext(Dispatchers.Main) {
                    onCoverReady(bitmap)
                }
            }
        }
    }
    fun cleanString(str: String): String {
        return str.lowercase()
            .replace(Regex("[^a-z0-9\\u4e00-\\u9fa5]"), "") // 仅保留数字、字母、中文
            .trim()
    }

    /**
     * 提取名称的主干部分
     * 例如 "Road to Revolution: Live at Milton Keynes" -> "roadtorevolution"
     */
    fun getMainName(str: String?): String {
        if (str.isNullOrBlank()) return ""
        val mainPart = str.split(Regex("[:：(\\[（]")).firstOrNull() ?: str
        return cleanString(mainPart)
    }

    fun findBestMatch(
        songs: List<NeteaseSearchSong>,
        targetTitle: String,
        targetArtist: String?,
        targetAlbum: String?,
        targetDurationMs: Long
    ): NeteaseSearchSong? {
        if (songs.isEmpty()) return null

        val cleanTargetArtist = cleanString(targetArtist ?: "")
        val cleanTargetAlbum = cleanString(targetAlbum ?: "")
        val mainTargetAlbum = getMainName(targetAlbum)
        val cleanTargetTitle = cleanString(targetTitle)

        val lowerTargetTitle = targetTitle.lowercase()
        val lowerTargetAlbum = targetAlbum?.lowercase() ?: ""
        val isTargetLive = lowerTargetTitle.contains("live") || lowerTargetAlbum.contains("live")

        return songs.minByOrNull { song ->
            var score = 0
            val lowerSongName = song.name.lowercase()
            val lowerSongAlbum = song.album?.name?.lowercase() ?: ""
            val cleanSongName = cleanString(song.name)
            val cleanSongAlbum = cleanString(song.album?.name ?: "")
            val mainSongAlbum = getMainName(song.album?.name)

            // 1. 歌手匹配 (权重最高)
            val songArtists = song.artists?.map { cleanString(it.name) } ?: emptyList()
            val artistMatched = songArtists.any { it == cleanTargetArtist || cleanTargetArtist.contains(it) || it.contains(cleanTargetArtist) }
            if (!artistMatched) score += 500 // 歌手不对，直接排除

            // 2. 专辑匹配 (这是匹配正确封面的关键)
            when {
                cleanSongAlbum == cleanTargetAlbum -> score += 0
                // Linkin Park 案例：主干一致 (Road to Revolution) 即可匹配，无视后缀差异
                mainSongAlbum == mainTargetAlbum && mainTargetAlbum.isNotEmpty() -> score += 10 
                cleanSongAlbum.contains(cleanTargetAlbum) || cleanTargetAlbum.contains(cleanSongAlbum) -> score += 30
                else -> score += 100
            }

            // 3. 歌曲标题匹配
            if (cleanSongName == cleanTargetTitle) {
                score += 0
            } else if (cleanSongName.contains(cleanTargetTitle) || cleanTargetTitle.contains(cleanSongName)) {
                score += 20
            } else {
                score += 100
            }

            // 4. 时长匹配 (封面匹配时，时长仅作为极弱参考)
            if (targetDurationMs > 0 && (song.duration ?: 0) > 0) {
                val diff = abs((song.duration ?: 0) - targetDurationMs)
                score += (diff / 2000).toInt() 
            }

            // 5. 版本检查 (Live/Remix)
            val isSongLive = lowerSongName.contains("live") || lowerSongAlbum.contains("live")
            if (isTargetLive != isSongLive) score += 50

            score
        }
    }

    fun mergeLyrics(main: List<LrcLine>, trans: List<LrcLine>): List<LrcLine> {
        val transMap = trans.filter { it.timeMs >= 0 }.associateBy { it.timeMs }
        return main.filter { it.timeMs >= 0 }.map { line ->
            val tLine = transMap[line.timeMs]
            if (tLine != null && tLine.text.isNotBlank() && tLine.text != line.text) {
                LrcLine(line.timeMs, "${line.text}\n${tLine.text}")
            } else {
                line
            }
        }
    }
}
