package com.quranwbw.android.speedread

import com.quranwbw.android.model.Ayah
import kotlin.math.roundToLong

enum class SpeedReadTheme(val label: String) {
    WARM_SEPIA("Warm Sepia"),
    AMBER_NIGHT("Amber Night"),
    FOREST_SAGE("Forest Sage"),
    SOFT_PAPER("Soft Paper"),
    SLATE_DUSK("Slate Dusk"),
}

enum class FocusedReadMode(val label: String) {
    ARABIC_WBW("Arabic + English"),
    ENGLISH("English Words"),
}

data class SpeedReadToken(
    val word: String,
    /** Index of the Optimal Recognition Point character within [word]. */
    val orpIndex: Int,
    /** Multiplier applied to the base per-word delay (e.g. >1.0 for punctuation or long words). */
    val delayMultiplier: Float,
)

data class WordByWordToken(
    val arabicWord: String,
    val englishGloss: String,
    val transliteration: String = "",
    val delayMultiplier: Float = 1.0f,
)

/**
 * Splits ayah text and word-by-word entries into tokens for RSVP speed reading,
 * computing the Optimal Recognition Point (ORP) index and natural punctuation pauses.
 */
object SpeedReadTokenizer {

    const val MIN_SPEED_WPM = 60
    const val MAX_SPEED_WPM = 800
    const val DEFAULT_SPEED_WPM = 250

    private val WHITESPACE_REGEX = Regex("""\s+""")

    fun tokenize(text: String): List<SpeedReadToken> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return emptyList()
        return trimmed.split(WHITESPACE_REGEX)
            .filter { it.isNotEmpty() }
            .map { word ->
                SpeedReadToken(
                    word = word,
                    orpIndex = computeOrpIndex(word),
                    delayMultiplier = computeDelayMultiplier(word),
                )
            }
    }

    fun tokenizeAyahEnglish(ayah: Ayah): List<SpeedReadToken> {
        val combined = ayah.words
            .map { it.translation.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(" ")
        return tokenize(combined)
    }

    fun tokenizeAyahWbw(ayah: Ayah): List<WordByWordToken> {
        return ayah.words
            .filter { it.arabic.isNotBlank() }
            .map { word ->
                val ar = word.arabic.trim()
                val en = word.translation.trim()
                WordByWordToken(
                    arabicWord = ar,
                    englishGloss = en,
                    transliteration = word.transliteration.trim(),
                    delayMultiplier = computeWbwDelayMultiplier(en),
                )
            }
    }

    /**
     * Computes how many milliseconds a token should stay on screen at [wpm].
     */
    fun delayMillisFor(token: SpeedReadToken, wpm: Int): Long {
        val clampedWpm = wpm.coerceIn(MIN_SPEED_WPM, MAX_SPEED_WPM)
        val baseMs = 60_000.0 / clampedWpm.toDouble()
        return (baseMs * token.delayMultiplier).roundToLong().coerceAtLeast(40L)
    }

    /**
     * Computes how many milliseconds an Arabic+English word-by-word token should stay on screen at [wpm].
     */
    fun delayMillisForWbw(token: WordByWordToken, wpm: Int): Long {
        val clampedWpm = wpm.coerceIn(MIN_SPEED_WPM, MAX_SPEED_WPM)
        val baseMs = 60_000.0 / clampedWpm.toDouble()
        return (baseMs * token.delayMultiplier).roundToLong().coerceAtLeast(60L)
    }

    /**
     * Optimal Recognition Point (slightly left of the word's center, ignoring leading punctuation).
     */
    fun computeOrpIndex(word: String): Int {
        if (word.isEmpty()) return 0
        val firstAlpha = word.indexOfFirst { it.isLetterOrDigit() }.takeIf { it >= 0 } ?: 0
        val lastAlpha = word.indexOfLast { it.isLetterOrDigit() }.takeIf { it >= 0 } ?: (word.length - 1)
        val coreLen = (lastAlpha - firstAlpha + 1).coerceAtLeast(1)
        val offsetInCore = when {
            coreLen <= 1 -> 0
            coreLen in 2..5 -> 1
            coreLen in 6..9 -> 2
            coreLen in 10..13 -> 3
            else -> 4
        }
        return (firstAlpha + offsetInCore).coerceIn(0, word.length - 1)
    }

    private fun computeDelayMultiplier(word: String): Float {
        val lastChar = word.lastOrNull { it != '"' && it != '\'' && it != ')' && it != ']' }
        val punctFactor = when (lastChar) {
            '.', '!', '?' -> 1.75f
            ';', ':' -> 1.45f
            ',', '—', '-' -> 1.30f
            else -> 1.0f
        }
        val lengthFactor = if (word.count { it.isLetter() } >= 10) 1.20f else 1.0f
        return punctFactor * lengthFactor
    }

    private fun computeWbwDelayMultiplier(en: String): Float {
        val enWordCount = en.split(WHITESPACE_REGEX).count { it.isNotEmpty() }
        val glossFactor = when {
            enWordCount >= 4 -> 1.35f
            enWordCount == 3 -> 1.20f
            else -> 1.05f
        }
        val punctFactor = computeDelayMultiplier(en)
        return (glossFactor * punctFactor).coerceIn(1.0f, 2.2f)
    }

    /**
     * Exact estimated duration in milliseconds across [ayahTokens] at [wpm],
     * starting from ([fromAyahIndex], [fromWordIndex]) through the end of the surah.
     */
    fun estimateEnglishDurationMs(
        ayahTokens: List<List<SpeedReadToken>>,
        wpm: Int,
        fromAyahIndex: Int = 0,
        fromWordIndex: Int = 0,
    ): Long {
        if (ayahTokens.isEmpty() || fromAyahIndex >= ayahTokens.size) return 0L
        val startAyah = fromAyahIndex.coerceAtLeast(0)
        var total = 0L
        for (aIdx in startAyah until ayahTokens.size) {
            val tokens = ayahTokens[aIdx]
            val startWord = if (aIdx == startAyah) fromWordIndex.coerceAtLeast(0) else 0
            for (wIdx in startWord until tokens.size) {
                total += delayMillisFor(tokens[wIdx], wpm)
            }
        }
        return total
    }

    /**
     * Exact estimated duration in milliseconds across [ayahTokens] in Arabic+English WBW mode at [wpm],
     * starting from ([fromAyahIndex], [fromWordIndex]) through the end of the surah.
     */
    fun estimateWbwDurationMs(
        ayahTokens: List<List<WordByWordToken>>,
        wpm: Int,
        fromAyahIndex: Int = 0,
        fromWordIndex: Int = 0,
    ): Long {
        if (ayahTokens.isEmpty() || fromAyahIndex >= ayahTokens.size) return 0L
        val startAyah = fromAyahIndex.coerceAtLeast(0)
        var total = 0L
        for (aIdx in startAyah until ayahTokens.size) {
            val tokens = ayahTokens[aIdx]
            val startWord = if (aIdx == startAyah) fromWordIndex.coerceAtLeast(0) else 0
            for (wIdx in startWord until tokens.size) {
                total += delayMillisForWbw(tokens[wIdx], wpm)
            }
        }
        return total
    }

    /**
     * Formats an estimated reading duration concisely (e.g., "42s", "4m 18s", "1h 12m").
     */
    fun formatEstimatedDuration(millis: Long): String {
        val totalSeconds = (millis.coerceAtLeast(0L) + 500L) / 1000L
        if (totalSeconds < 60L) return "${totalSeconds}s"
        val totalMinutes = totalSeconds / 60L
        val remSeconds = totalSeconds % 60L
        if (totalMinutes < 60L) {
            return if (remSeconds == 0L) "${totalMinutes}m" else "${totalMinutes}m ${remSeconds}s"
        }
        val hours = totalMinutes / 60L
        val remMinutes = totalMinutes % 60L
        return if (remMinutes == 0L) "${hours}h" else "${hours}h ${remMinutes}m"
    }
}
