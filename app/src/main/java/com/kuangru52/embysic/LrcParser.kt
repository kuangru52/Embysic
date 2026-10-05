package com.kuangru52.embysic

data class LrcLine(val timeMs: Long, val text: String)

object LrcParser {
    fun parse(lrcText: String?): List<LrcLine> {
        if (lrcText.isNullOrBlank()) return emptyList()

        val lines = mutableListOf<LrcLine>()
        val timeRegex = Regex("\\[(\\d+):(\\d{1,2})(?:[.:](\\d{1,3}))?]")
        val tagRegex = Regex("\\[(ti|ar|al|by|offset):(.*)]", RegexOption.IGNORE_CASE)

        lrcText.lines().forEach { rawLine ->
            val trimLine = rawLine.trim()
            if (trimLine.isEmpty()) return@forEach

            val tagMatch = tagRegex.find(trimLine)
            if (tagMatch != null) {
                val key = tagMatch.groupValues[1].lowercase()
                val value = tagMatch.groupValues[2].trim()
                if (value.isNotEmpty()) {
                    when (key) {
                        "ti" -> lines.add(LrcLine(-1, "曲名: $value"))
                        "ar" -> lines.add(LrcLine(-1, "歌手: $value"))
                        "al" -> lines.add(LrcLine(-1, "专辑: $value"))
                    }
                }
                return@forEach
            }

            val timeMatches = timeRegex.findAll(trimLine).toList()
            if (timeMatches.isNotEmpty()) {
                val text = trimLine.replace(timeRegex, "").trim()
                if (text.isNotBlank()) {
                    timeMatches.forEach { match ->
                        val min = match.groupValues[1].toLong()
                        val sec = match.groupValues[2].toLong()
                        val msStr = match.groupValues.getOrNull(3)

                        val ms = when (msStr?.length) {
                            null -> 0L
                            1 -> msStr.toLong() * 100
                            2 -> msStr.toLong() * 10
                            3 -> msStr.toLong()
                            else -> msStr.take(3).toLong()
                        }
                        val timeMs = (min * 60000) + (sec * 1000) + ms
                        lines.add(LrcLine(timeMs, text))
                    }
                }
            } else if (!trimLine.startsWith("[")) {
                if (trimLine.isNotBlank()) {
                    lines.add(LrcLine(0, trimLine))
                }
            }
        }

        val metadata = lines.asSequence().filter { it.timeMs == -1L }.toList()
        val lyrics = lines.asSequence().filter { (it.timeMs >= 0L) && it.text.isNotBlank() }.sortedBy { it.timeMs }.toList()

        return if (metadata.isEmpty() && lyrics.isEmpty()) emptyList() else metadata + lyrics
    }
}
