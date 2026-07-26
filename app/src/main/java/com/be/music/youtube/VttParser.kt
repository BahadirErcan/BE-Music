package com.be.music.youtube

/**
 * Represents a single line of lyrics with an optional start time in milliseconds.
 */
data class LyricLine(
    val timeMs: Long?, // Null if not synchronized
    val text: String
)

/**
 * Helper to parse WebVTT (.vtt) file contents into a list of LyricLines.
 */
object VttParser {

    /**
     * Parses the VTT content string. Strips any formatting/XML tags.
     */
    fun parse(content: String): List<LyricLine> {
        val lines = content.lineSequence().map { it.trim() }.toList()
        val result = mutableListOf<LyricLine>()
        var i = 0

        while (i < lines.size) {
            val line = lines[i]
            if (line.contains("-->")) {
                val timeParts = line.split("-->")
                val startStr = timeParts[0].trim()
                val startMs = try {
                    parseVttTime(startStr)
                } catch (e: Exception) {
                    null
                }

                i++
                val textBuilder = StringBuilder()
                // Collect lines until the next block or an empty line
                while (i < lines.size && lines[i].isNotEmpty() && !lines[i].contains("-->")) {
                    // Clean HTML/VTT style tags (e.g. <c.color> or <00:00:01.000>)
                    val cleanedLine = lines[i].replace("<[^>]*>".toRegex(), "").trim()
                    if (cleanedLine.isNotEmpty()) {
                        if (textBuilder.isNotEmpty()) textBuilder.append("\n")
                        textBuilder.append(cleanedLine)
                    }
                    i++
                }
                val text = textBuilder.toString().trim()
                if (text.isNotEmpty() && startMs != null) {
                    result.add(LyricLine(startMs, text))
                }
            } else {
                i++
            }
        }

        // Fallback: If no timed lines were parsed, treat all non-header lines as unsynced lyrics
        if (result.isEmpty()) {
            val plainLines = lines.filter { 
                it.isNotEmpty() && 
                !it.startsWith("WEBVTT") && 
                !it.startsWith("NOTE") &&
                !it.startsWith("STYLE")
            }
            return plainLines.map { 
                LyricLine(null, it.replace("<[^>]*>".toRegex(), "").trim()) 
            }.filter { it.text.isNotEmpty() }
        }

        return result
    }

    private fun parseVttTime(timeStr: String): Long {
        val cleaned = timeStr.trim().replace(',', '.')
        val parts = cleaned.split(":")
        if (parts.size == 2) {
            // MM:SS.mmm
            val minutes = parts[0].toLong()
            val secondsParts = parts[1].split(".")
            val seconds = secondsParts[0].toLong()
            val ms = if (secondsParts.size > 1) secondsParts[1].padEnd(3, '0').take(3).toLong() else 0L
            return (minutes * 60 + seconds) * 1000 + ms
        } else if (parts.size == 3) {
            // HH:MM:SS.mmm
            val hours = parts[0].toLong()
            val minutes = parts[1].toLong()
            val secondsParts = parts[2].split(".")
            val seconds = secondsParts[0].toLong()
            val ms = if (secondsParts.size > 1) secondsParts[1].padEnd(3, '0').take(3).toLong() else 0L
            return ((hours * 60 + minutes) * 60 + seconds) * 1000 + ms
        }
        throw IllegalArgumentException("Invalid WebVTT time format: $timeStr")
    }
}
