package com.quranwbw.android

import com.quranwbw.android.model.Ayah
import com.quranwbw.android.model.QuranWord
import com.quranwbw.android.speedread.SpeedReadTokenizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SpeedReadTokenizerTest {

    @Test
    fun tokenizesVerseAndAppliesPunctuationPauseMultipliers() {
        val tokens = SpeedReadTokenizer.tokenize("In the name of Allah, the Beneficent, the Merciful.")
        assertEquals(9, tokens.size)
        assertEquals("In", tokens[0].word)
        assertEquals("Allah,", tokens[4].word)
        assertEquals("Merciful.", tokens[8].word)

        val plainDelay = SpeedReadTokenizer.delayMillisFor(tokens[0], 300)
        val commaDelay = SpeedReadTokenizer.delayMillisFor(tokens[4], 300)
        val periodDelay = SpeedReadTokenizer.delayMillisFor(tokens[8], 300)
        assertTrue(commaDelay > plainDelay)
        assertTrue(periodDelay > commaDelay)
    }

    @Test
    fun computesValidOrpIndexWithinBounds() {
        for (w in listOf("I", "be", "(alone)", "Beneficent,", " righteousness.")) {
            val tokens = SpeedReadTokenizer.tokenize(w)
            for (t in tokens) {
                assertTrue("ORP ${t.orpIndex} out of bounds for '${t.word}'", t.orpIndex in t.word.indices)
            }
        }
    }

    @Test
    fun tokenizesAyahWordsForWbwAndEnglishModes() {
        val ayah = Ayah(
            number = 1,
            audioUrl = "",
            words = listOf(
                QuranWord(0, "بِسْمِ", "Bismi", "In (the) name", "", 0, 680),
                QuranWord(1, "ٱللَّهِ", "Allahi", "(of) Allah", "", 680, 1450),
                QuranWord(2, "ٱلرَّحْمَٰنِ", "Ar-Rahmani", "the Most Gracious", "", 1450, 2550),
                QuranWord(3, "ٱلرَّحِيمِ", "Ar-Raheem", "the Most Merciful.", "", 2550, 4200),
            )
        )

        val wbwTokens = SpeedReadTokenizer.tokenizeAyahWbw(ayah)
        assertEquals(4, wbwTokens.size)
        assertEquals("بِسْمِ", wbwTokens[0].arabicWord)
        assertEquals("In (the) name", wbwTokens[0].englishGloss)
        assertEquals("Bismi", wbwTokens[0].transliteration)

        val enTokens = SpeedReadTokenizer.tokenizeAyahEnglish(ayah)
        assertTrue(enTokens.isNotEmpty())
        assertEquals("In", enTokens.first().word)
        assertEquals("Merciful.", enTokens.last().word)
    }

    @Test
    fun estimatesRemainingAndTotalDurationAccuratelyAtWpm() {
        val ayah1 = SpeedReadTokenizer.tokenize("One two three four.")
        val ayah2 = SpeedReadTokenizer.tokenize("Five six.")
        val surahTokens = listOf(ayah1, ayah2)

        val totalAt200 = SpeedReadTokenizer.estimateEnglishDurationMs(surahTokens, wpm = 200)
        val totalAt400 = SpeedReadTokenizer.estimateEnglishDurationMs(surahTokens, wpm = 400)
        assertTrue(abs(totalAt400 * 2 - totalAt200) <= 10)

        val remainingFromSecondWord = SpeedReadTokenizer.estimateEnglishDurationMs(
            surahTokens,
            wpm = 200,
            fromAyahIndex = 0,
            fromWordIndex = 1,
        )
        assertTrue(remainingFromSecondWord < totalAt200)

        assertEquals("45s", SpeedReadTokenizer.formatEstimatedDuration(45_000L))
        assertEquals("3m 15s", SpeedReadTokenizer.formatEstimatedDuration(195_000L))
        assertEquals("1h 12m", SpeedReadTokenizer.formatEstimatedDuration(4_320_000L))
    }
}
