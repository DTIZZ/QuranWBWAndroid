package com.quranwbw.android

import com.quranwbw.android.audio.WordHighlightSync
import com.quranwbw.android.model.QuranWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WordHighlightSyncTest {

    private val sampleWords = listOf(
        QuranWord(0, "بِسْمِ", "Bismi", "In (the) name", "", 0L, 680L),
        QuranWord(1, "ٱللَّهِ", "Allahi", "(of) Allah", "", 680L, 1450L),
        QuranWord(2, "ٱلرَّحْمَٰنِ", "Ar-Rahmani", "the Most Gracious", "", 1450L, 2550L)
    )

    @Test
    fun findActiveWordIndexMatchesTimestampRange() {
        assertEquals(0, WordHighlightSync.findActiveWordIndex(0L, sampleWords))
        assertEquals(0, WordHighlightSync.findActiveWordIndex(500L, sampleWords))
        assertEquals(1, WordHighlightSync.findActiveWordIndex(680L, sampleWords))
        assertEquals(2, WordHighlightSync.findActiveWordIndex(2000L, sampleWords))
        assertNull(WordHighlightSync.findActiveWordIndex(3000L, sampleWords))
        assertNull(WordHighlightSync.findActiveWordIndex(-10L, sampleWords))
    }
}
