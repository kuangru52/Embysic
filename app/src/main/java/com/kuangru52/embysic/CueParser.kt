package com.kuangru52.embysic

data class CueSheet(
    val albumTitle: String? = null,
    val albumArtist: String? = null,
    val fileName: String? = null,
    val tracks: List<CueTrack> = emptyList(),
)

data class CueTrack(
    val number: Int,
    val title: String,
    val artist: String?,
    val startTimeMs: Long,
    var durationMs: Long = 0,
)

@Suppress("unused")
object CueParser {
    @Suppress("ASSIGNED_VALUE_IS_NEVER_READ", "UNUSED_VALUE")
    fun parse(content: String): CueSheet {
        val lines = content.lines().map { it.trim() }
        var albumTitle: String? = null
        var albumArtist: String? = null
        var fileName: String? = null
        val tracks = mutableListOf<CueTrack>()

        var currentTrackNumber = -1
        var currentTrackTitle = ""
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
                    val firstQuote = line.indexOf("\"")
                    val lastQuote = line.lastIndexOf("\"")
                    if (((firstQuote != -1) && (lastQuote != -1)) && (firstQuote != lastQuote)) {
                        fileName = line.substring(firstQuote + 1, lastQuote)
                    } else {
                        val parts = line.split(" ")
                        if (parts.size >= 2) {
                            fileName = parts[1]
                        }
                    }
                }
                upperLine.startsWith("TRACK") -> {
                    isParsingTrack = true
                    val parts = line.split("\\s+".toRegex())
                    if (parts.size >= 2) {
                        currentTrackNumber = parts[1].toIntOrNull() ?: (tracks.size + 1)
                    }
                    currentTrackTitle = ""
                    currentTrackArtist = null
                }
                upperLine.startsWith("TITLE") && isParsingTrack -> {
                    currentTrackTitle = line.removeSurroundingQuotes("TITLE")
                }
                upperLine.startsWith("PERFORMER") && isParsingTrack -> {
                    currentTrackArtist = line.removeSurroundingQuotes("PERFORMER")
                }
                upperLine.startsWith("INDEX 01") -> {
                    val timeStr = line.replace("INDEX 01", "").trim()
                    val timeMs = parseCueTime(timeStr)
                    tracks.add(
                        CueTrack(
                            number = currentTrackNumber,
                            title = currentTrackTitle,
                            artist = currentTrackArtist ?: albumArtist,
                            startTimeMs = timeMs,
                        ),
                    )
                }
            }
        }

        // 计算每轨的 duration
        for (i in 0 until (tracks.size - 1)) {
            tracks[i].durationMs = tracks[i + 1].startTimeMs - tracks[i].startTimeMs
        }

        return CueSheet(
            albumTitle = albumTitle,
            albumArtist = albumArtist,
            fileName = fileName,
            tracks = tracks,
        )
    }

    private fun parseCueTime(timeStr: String): Long {
        val parts = timeStr.split(":")
        if (parts.size == 3) {
            val min = parts[0].toLongOrNull() ?: 0L
            val sec = parts[1].toLongOrNull() ?: 0L
            val frame = parts[2].toLongOrNull() ?: 0L
            return ((min * 60L) * 1000L) + (sec * 1000L) + ((frame * 1000L) / 75L)
        }
        return 0L
    }

    private fun String.removeSurroundingQuotes(keyword: String): String {
        var str = this.replaceFirst(keyword, "", ignoreCase = true).trim()
        if (((str.startsWith("\"")) && (str.endsWith("\""))) && (str.length >= 2)) {
            str = str.substring(1, str.length - 1)
        }
        return str
    }
}
