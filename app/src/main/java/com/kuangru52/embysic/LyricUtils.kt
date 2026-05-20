package com.kuangru52.embysic

import kotlin.math.abs

object LyricUtils {
    fun cleanString(str: String): String {
        return str.lowercase()
            .replace(Regex("\\s*([(\\[].*?[)\\]])"), "") // 去除括号内容
            .replace(Regex("[^a-z0-9\\u4e00-\\u9fa5]"), "") // 仅保留数字、字母、中文
            .trim()
    }

    fun findBestMatch(
        songs: List<NeteaseSearchSong>,
        targetTitle: String,
        targetArtist: String?,
        targetDurationMs: Long
    ): NeteaseSearchSong? {
        if (songs.isEmpty()) return null

        val lowerTargetTitle = targetTitle.lowercase()
        val cleanTargetTitle = cleanString(targetTitle)
        val cleanTargetArtist = cleanString(targetArtist ?: "")

        val isTargetLive = lowerTargetTitle.contains("live")
        val isTargetRemix = lowerTargetTitle.contains("remix")

        return songs.minByOrNull { song ->
            var score = 0
            val lowerSongName = song.name.lowercase()
            val cleanSongName = cleanString(song.name)

            // 1. 标题匹配度 (0-100分)
            if (cleanSongName == cleanTargetTitle) {
                score += 0
            } else if (cleanSongName.contains(cleanTargetTitle) || cleanTargetTitle.contains(cleanSongName)) {
                score += 20
            } else {
                score += 100
            }

            // 2. 歌手匹配度
            val songArtists = song.artists?.map { cleanString(it.name) } ?: emptyList()
            if (songArtists.any { it == cleanTargetArtist || cleanTargetArtist.contains(it) || it.contains(cleanTargetArtist) }) {
                score += 0
            } else {
                score += 50
            }

            // 3. 时长偏移惩罚 (100ms 误差 = 1 分)
            val durationDiff = abs((song.duration ?: 0L) - targetDurationMs)
            score += (durationDiff / 100).toInt()

            // 4. 版本冲突拦截 (严防匹配到 Live 或 Remix)
            val isSongLive = lowerSongName.contains("live")
            if (isTargetLive != isSongLive) score += 150

            val isSongRemix = lowerSongName.contains("remix")
            if (isTargetRemix != isSongRemix) score += 150

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
