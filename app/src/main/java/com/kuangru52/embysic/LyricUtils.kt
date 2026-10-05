package com.kuangru52.embysic

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * 核心匹配工具类
 * 注意：虽然名字叫 LyricUtils，但它承担了搜索结果（歌词、封面）与当前歌曲的【元数据匹配】核心逻辑。
 */
object LyricUtils {
    // 内存缓存歌词，供全局（手机/平板）共享
    val lyricsCache: MutableMap<String, List<LrcLine>> = mutableMapOf()

    suspend fun fetchNeteaseMetadata(
        context: Context,
        itemId: String,
        title: String?,
        artist: String?,
        album: String?,
        durationMs: Long,
        skipCover: Boolean = false,
        onCoverReady: (Any) -> Unit = {},
        onLyricsReady: (List<LrcLine>) -> Unit = {},
    ) {
        val cleanedTitle = title?.replace(Regex("\\s*[(\\[].*?[)\\]]"), "")?.trim() ?: ""
        val isArtistUnknown = artist.isNullOrBlank() || artist.contains("未知") || artist.contains("Unknown")
        val isAlbumUnknown = album.isNullOrBlank() || album.contains("未知") || album.contains("Unknown")
        
        val query = when {
            !isArtistUnknown && !isAlbumUnknown -> "$cleanedTitle $artist $album"
            !isArtistUnknown -> "$cleanedTitle $artist"
            !isAlbumUnknown -> "$cleanedTitle $album"
            else -> cleanedTitle
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
            
            if (!picUrl.isNullOrEmpty() && !skipCover) {
                downloadAndCacheCover(context, itemId, picUrl, onCoverReady)
            }

            // 2. 同步抓取歌词
            val lyricResponse = neteaseApi.getLyric(bestMatch.id)
            val lrcText = lyricResponse.lrc?.lyric
            if (!lrcText.isNullOrBlank()) {
                val mainLyrics = LrcParser.parse(lrcText)
                val tlyricText = lyricResponse.tlyric?.lyric
                val lyrics = if (!tlyricText.isNullOrBlank()) {
                    mergeLyrics(mainLyrics, LrcParser.parse(tlyricText))
                } else {
                    mainLyrics
                }

                val finalLines = listOf(LrcLine(-1, "来源: 网易云音乐")) + lyrics

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
            if (!isArtistUnknown && cleanedTitle.isNotEmpty()) {
                fetchNeteaseMetadata(context, itemId, cleanedTitle, null, null, durationMs, skipCover, onCoverReady, onLyricsReady)
            }
        }
    }

    private suspend fun downloadAndCacheCover(context: Context, itemId: String, url: String, onCoverReady: (Any) -> Unit) {
        val request = ImageRequest.Builder(context)
            .data(url)
            .allowHardware(enable = false)
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
            .replace(Regex("[^a-z0-9\\u4e00-\\u9fa5]"), "")
            .trim()
    }

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
        targetDurationMs: Long,
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

            val songArtists = song.artists?.map { cleanString(it.name) } ?: emptyList()
            val artistMatched = songArtists.any { (it == cleanTargetArtist) || cleanTargetArtist.contains(it) || it.contains(cleanTargetArtist) }
            if (!artistMatched) score += 1000

            score += when {
                cleanSongName == cleanTargetTitle -> 0
                cleanSongName.contains(cleanTargetTitle) || cleanTargetTitle.contains(cleanSongName) -> 20
                else -> 500
            }

            score += when {
                cleanSongAlbum == cleanTargetAlbum -> 0
                (mainSongAlbum == mainTargetAlbum) && mainTargetAlbum.isNotEmpty() -> 10
                cleanSongAlbum.contains(cleanTargetAlbum) || cleanTargetAlbum.contains(cleanSongAlbum) -> 30
                else -> 200
            }

            if ((targetDurationMs > 0) && ((song.duration ?: 0) > 0)) {
                val diff = abs((song.duration ?: 0) - targetDurationMs)
                score += (diff / 2000).toInt()
            }

            val isSongLive = lowerSongName.contains("live") || lowerSongAlbum.contains("live")
            if (isTargetLive != isSongLive) score += 50

            score
        }
    }

    fun mergeLyrics(main: List<LrcLine>, trans: List<LrcLine>): List<LrcLine> {
        val transMap = trans.asSequence().filter { it.timeMs >= 0 }.associateBy { it.timeMs }
        return main.asSequence().filter { it.timeMs >= 0 }.map { line ->
            val tLine = transMap[line.timeMs]
            if ((tLine != null) && tLine.text.isNotBlank() && (tLine.text != line.text)) {
                LrcLine(line.timeMs, "${line.text}\n${tLine.text}")
            } else {
                line
            }
        }.toList()
    }
}
