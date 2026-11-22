package com.quranwbw.android.audio

import com.quranwbw.android.model.QuranWord

/**
 * Timestamp-based word highlighting helper inspired by QuranWBW's `word-highlighter`.
 */
object WordHighlightSync {

    /**
     * Returns the index of the word active at [positionMs], or null if no word matches.
     */
    fun findActiveWordIndex(positionMs: Long, words: List<QuranWord>): Int? {
        if (positionMs < 0L || words.isEmpty()) return null
        return words.firstOrNull { word ->
            positionMs >= word.startMs && positionMs < word.endMs
        }?.index
    }
}
