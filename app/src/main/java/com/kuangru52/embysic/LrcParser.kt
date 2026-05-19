package com.kuangru52.embysic

data class LrcLine(val timeMs: Long, val text: String)

object LrcParser {
    fun parse(lrcText: String?): List<LrcLine> {
        if (lrcText.isNullOrBlank()) return emptyList()
        
        val lines = mutableListOf<LrcLine>()
        // 匹配 [mm:ss.SSS] 或 [mm:ss:SSS] 或 [mm:ss] 格式
        val timeRegex = Regex("\\[(\\d+):(\\d{1,2})(?:[.:](\\d{1,3}))?\\]")
        // 匹配元数据标签 [ti:Title] [ar:Artist] [al:Album]
        val tagRegex = Regex("\\[(ti|ar|al|by|offset):(.*)\\]", RegexOption.IGNORE_CASE)
        
        lrcText.lines().forEach { rawLine ->
            val trimLine = rawLine.trim()
            if (trimLine.isEmpty()) return@forEach

            // 处理元数据
            val tagMatch = tagRegex.find(trimLine)
            if (tagMatch != null) {
                val key = tagMatch.groupValues[1].lowercase()
                val value = tagMatch.groupValues[2].trim()
                if (value.isNotEmpty()) {
                    val prefix = when(key) {
                        "ti" -> "曲名: "
                        "ar" -> "歌手: "
                        "al" -> "专辑: "
                        else -> null
                    }
                    if (prefix != null) {
                        lines.add(LrcLine(-1, prefix + value))
                    }
                }
                return@forEach
            }

            // 处理歌词行，支持一行多个时间标签
            val timeMatches = timeRegex.findAll(trimLine).toList()
            if (timeMatches.isNotEmpty()) {
                val text = trimLine.replace(timeRegex, "").trim()
                // 优化：过滤掉空行
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
                        val timeMs = min * 60000 + sec * 1000 + ms
                        lines.add(LrcLine(timeMs, text))
                    }
                }
            } else if (!trimLine.startsWith("[")) {
                // 处理没有标签的纯文本
                if (trimLine.isNotBlank()) {
                    lines.add(LrcLine(0, trimLine))
                }
            }
        }
        
        val metadata = lines.filter { it.timeMs == -1L }
        val lyrics = lines.filter { it.timeMs >= 0L && it.text.isNotBlank() }.sortedBy { it.timeMs }
        
        return if (metadata.isEmpty() && lyrics.isEmpty()) emptyList() else metadata + lyrics
    }
}
