package com.kuangru52.embysic

import kotlin.math.abs

/**
 * 核心匹配工具类
 * 注意：虽然名字叫 LyricUtils，但它承担了搜索结果（歌词、封面）与当前歌曲的【元数据匹配】核心逻辑。
 */
object LyricUtils {
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
