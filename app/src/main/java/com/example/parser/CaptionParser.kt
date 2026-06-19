package com.example.parser

import java.util.regex.Pattern

object CaptionParser {
    
    // Convert Myanmar digits (၀-၉) to standard Arabic (0-9)
    fun convertMyanmarToArabicDigits(input: String): String {
        val mmDigits = mapOf(
            '၀' to '0', '၁' to '1', '၂' to '2', '၃' to '3', '၄' to '4',
            '၅' to '5', '၆' to '6', '၇' to '7', '၈' to '8', '၉' to '9'
        )
        return input.map { mmDigits[it] ?: it }.joinToString("")
    }

    // Episode patterns matching Myanmar "အပိုင်း(၂၅)" and standard "Episode 12" / "EP12" / "Ep.12"
    private val EPISODE_REGEXES = listOf(
        Regex("""အပိုင်း\s*[\(\[]?\s*([၀-၉]+)\s*[\)\]]?""", RegexOption.IGNORE_CASE),
        Regex("""(?:episode|eps?)\s*[\.\-:#]?\s*(\d+)""", RegexOption.IGNORE_CASE)
    )

    // Quality pattern: only these five, case-insensitive
    private val QUALITY_REGEX = Regex("""\b(1080p|720p|480p|2k|4k)\b""", RegexOption.IGNORE_CASE)

    fun extractEpisode(caption: String): Int? {
        for (regex in EPISODE_REGEXES) {
            val match = regex.find(caption)
            if (match != null) {
                val valueRaw = match.groupValues[1]
                val normalized = convertMyanmarToArabicDigits(valueRaw)
                return normalized.toIntOrNull()
            }
        }
        return null
    }

    fun extractQuality(caption: String): String? {
        val match = QUALITY_REGEX.find(caption)
        return match?.groupValues?.get(1)?.lowercase()
    }

    fun extractTitle(caption: String): String? {
        val firstLine = caption.split("\n").firstOrNull()?.trim() ?: return null
        
        // Find indexes of all episode/quality matches in the first line
        var cutIndex = firstLine.length
        
        // Check Episode patterns location
        for (regex in EPISODE_REGEXES) {
            val match = regex.find(firstLine)
            if (match != null) {
                if (match.range.first < cutIndex) {
                    cutIndex = match.range.first
                }
            }
        }
        
        // Check Quality pattern location
        val qualMatch = QUALITY_REGEX.find(firstLine)
        if (qualMatch != null) {
            if (qualMatch.range.first < cutIndex) {
                cutIndex = qualMatch.range.first
            }
        }
        
        // Slice first line up to first match of episode/quality marker
        var rawParsedTitle = firstLine.substring(0, cutIndex)
        
        // Strip emojis and leading/trailing punctuation characters
        rawParsedTitle = stripEmojisAndPunctuation(rawParsedTitle)
        
        return if (rawParsedTitle.isNotEmpty()) rawParsedTitle else null
    }

    private fun stripEmojisAndPunctuation(input: String): String {
        // Strip out emojis range
        var output = input.replace(Regex("""[\uD83C-\uDBFF\uDC00-\uDFFF\u2600-\u27BF]"""), "")
        
        // Trim trailing/leading standard separation punctuation keys
        val stripPattern = """^[\s\-\|•\:>\*#\(\)\[\]]+|[\s\-\|•\:>\*#\(\)\[\]]+$"""
        output = output.replace(Regex(stripPattern), "").trim()
        
        // Final cleaning check on edges
        output = output.trim { it in listOf('-', '|', '•', ':', '>', '*', '#', '[', ']', '(', ')') }.trim()
        return output
    }

    fun parseCaption(caption: String): ParsedCaption? {
        val title = extractTitle(caption) ?: return null
        val episode = extractEpisode(caption) ?: return null
        val quality = extractQuality(caption) ?: return null
        
        return ParsedCaption(title, episode, quality)
    }
}

data class ParsedCaption(
    val title: String,
    val episode: Int,
    val quality: String
)
