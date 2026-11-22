package com.quranwbw.android

import com.quranwbw.android.data.ArabicLetterParser
import org.junit.Assert.assertEquals
import org.junit.Test

class ArabicLetterParserTest {

    @Test
    fun parseBismiDecomposesLettersAndHarakat() {
        val units = ArabicLetterParser.parse("بِسْمِ")

        assertEquals(3, units.size)

        assertEquals("بِ", units[0].display)
        assertEquals("Ba", units[0].letterName)
        assertEquals(listOf("Kasra"), units[0].harakatNames)
        assertEquals("ba_kasra", units[0].audioKey)

        assertEquals("سْ", units[1].display)
        assertEquals("Sin", units[1].letterName)
        assertEquals(listOf("Sukun"), units[1].harakatNames)
        assertEquals("sin_sukun", units[1].audioKey)

        assertEquals("مِ", units[2].display)
        assertEquals("Mim", units[2].letterName)
        assertEquals(listOf("Kasra"), units[2].harakatNames)
        assertEquals("mim_kasra", units[2].audioKey)
    }

    @Test
    fun parseQulHandlesSukunAndDamma() {
        val units = ArabicLetterParser.parse("قُلْ")

        assertEquals(2, units.size)
        assertEquals("Qaf", units[0].letterName)
        assertEquals("qaf_damma", units[0].audioKey)
        assertEquals("Lam", units[1].letterName)
        assertEquals("lam_sukun", units[1].audioKey)
    }
}
