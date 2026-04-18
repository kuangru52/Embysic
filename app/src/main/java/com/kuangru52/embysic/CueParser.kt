package com.kuangru52.embysic

import java.util.regex.Pattern

data class CueSheet(
    val albumTitle: String? = null,
    val albumArtist: String? = null,
    val fileName: String? = null,
    val tracks: List<CueTrack> = emptyList()
)

data class CueTrack(
    val number: Int,
    val title: String,
    val artist: String?,
    val startTimeMs: Long,
    var durationMs: Long = 0 // 持续时间需要通过下一首的起始时间计算
)

object CueParser {
    fun parse(content: String): CueSheet {
        val lines = content.lines().map { it.trim() }
        var albumTitle: String? = null
        var albumArtist: String? = null
        var fileName: String? = null
        val tracks = mutableListOf<CueTrack>()

        var currentTrackNumber: Int = -1
        var currentTrackTitle: String = ""
        var currentTrackArtist: String? = null
        var isParsingTrack = false

        lines.forEach { line ->
            val upperLine = line.uppercase()
            when {
                upperLine.startsWith("TITLE") && !isParsingTrack -> {
                    albumTitle = line.removeSurroundingQuotes("TITLE")
                }
                upperLine.startsWith("PERFORMER") && !isParsingTrack -> {
                    albumArtist = line.removeSurroundingQuotes("PERFORMER")
                }
                upperLine.startsWith("FILE") -> {
                    // 提取文件名: FILE "name.flac" WAVE
                    val firstQuote = line.indexOf("\"")
                    val lastQuote = line.lastIndexOf("\"")
                    if (firstQuote != -1 && lastQuote != -1 && firstQuote != lastQuote) {
                        fileName = line.substring(firstQuote + 1, lastQuote)
                    } else {
                        // 如果没有引号，尝试按空格切分
                        val parts = line.split(" ")
                        if (parts.size >= 2) fileName = parts[1]
                    }
                }
                upperLine.startsWith("TRACK") -> {
                    isParsingTrack = true
                    val parts = line.split(Regex("\\s+"))
                    if (parts.size >= 2) {
                        currentTrackNumber = parts[1].toIntOrNull() ?: -1
                    }
                    currentTrackArtist = albumArtist
                }
                upperLine.startsWith("TITLE") && isParsingTrack -> {
                    currentTrackTitle = line.removeSurroundingQuotes("TITLE")
                }
                upperLine.startsWith("PERFORMER") && isParsingTrack -> {
                    currentTrackArtist = line.removeSurroundingQuotes("PERFORMER")
                }
                upperLine.startsWith("INDEX 01") -> {
                    val timeStr = line.substringAfter("INDEX 01").trim()
                    val timeMs = parseTimeToMs(timeStr)
                    if (currentTrackNumber != -1) {
                        tracks.add(CueTrack(currentTrackNumber, currentTrackTitle, currentTrackArtist, timeMs))
                    }
                }
            }
        }

        // 计算每一首的持续时间
        for (i in 0 until tracks.size - 1) {
            tracks[i].durationMs = tracks[i + 1].startTimeMs - tracks[i].startTimeMs
        }
        // 注意：最后一首的持续时间需要配合整轨文件的总长度才能计算，暂时设为 0

        return CueSheet(albumTitle, albumArtist, fileName, tracks)
    }

    private fun String.removeSurroundingQuotes(prefix: String): String {
        val upper = this.uppercase()
        val pUpper = prefix.uppercase()
        if (upper.startsWith(pUpper)) {
            val content = this.substring(prefix.length).trim()
            return if (content.startsWith("\"") && content.endsWith("\"")) {
                content.removeSurrounding("\"")
            } else {
                content
            }
        }
        return this
    }

    private fun parseTimeToMs(time: String): Long {
        // 格式通常为 00:00:00 (分:秒:帧)
        val parts = time.split(":")
        if (parts.size < 2) return 0L
        
        val minutes: Long
        val seconds: Long
        val frames: Long
        
        if (parts.size == 3) {
            minutes = parts[0].toLongOrNull() ?: 0L
            seconds = parts[1].toLongOrNull() ?: 0L
            frames = parts[2].toLongOrNull() ?: 0L
        } else {
            // 处理 00:00 格式
            minutes = parts[0].toLongOrNull() ?: 0L
            seconds = parts[1].toLongOrNull() ?: 0L
            frames = 0L
        }
        
        // 1分=60秒，1秒=1000毫秒，1帧=1/75秒 ≈ 13.33毫秒
        return (minutes * 60 * 1000) + (seconds * 1000) + (frames * 1000 / 75)
    }
}
