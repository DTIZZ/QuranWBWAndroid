package com.quranwbw.android.data

import com.quranwbw.android.model.ArabicLetterUnit

/**
 * Decomposes an Arabic Unicode word into individual [ArabicLetterUnit]s by grouping
 * each base letter with its trailing combining diacritics (harakat, shadda, sukun, etc.).
 */
object ArabicLetterParser {

    private val letterNames = mapOf(
        '\u0621' to "Hamza",
        '\u0622' to "Alif Maddah",
        '\u0623' to "Alif Hamza Above",
        '\u0624' to "Waw Hamza",
        '\u0625' to "Alif Hamza Below",
        '\u0626' to "Ya Hamza",
        '\u0627' to "Alif",
        '\u0671' to "Alif Wasla",
        '\u0628' to "Ba",
        '\u0629' to "Ta Marbuta",
        '\u062A' to "Ta",
        '\u062B' to "Tha",
        '\u062C' to "Jim",
        '\u062D' to "Ha",
        '\u062E' to "Kha",
        '\u062F' to "Dal",
        '\u0630' to "Dhal",
        '\u0631' to "Ra",
        '\u0632' to "Zay",
        '\u0633' to "Sin",
        '\u0634' to "Shin",
        '\u0635' to "Sad",
        '\u0636' to "Dad",
        '\u0637' to "Taa",
        '\u0638' to "Dhaa",
        '\u0639' to "Ayn",
        '\u063A' to "Ghayn",
        '\u0641' to "Fa",
        '\u0642' to "Qaf",
        '\u0643' to "Kaf",
        '\u0644' to "Lam",
        '\u0645' to "Mim",
        '\u0646' to "Nun",
        '\u0647' to "Ha (soft)",
        '\u0648' to "Waw",
        '\u0649' to "Alif Maqsurah",
        '\u064A' to "Ya"
    )

    private val diacriticNames = mapOf(
        '\u064B' to "Fathatan",
        '\u064C' to "Dammatan",
        '\u064D' to "Kasratan",
        '\u064E' to "Fatha",
        '\u064F' to "Damma",
        '\u0650' to "Kasra",
        '\u0651' to "Shadda",
        '\u0652' to "Sukun",
        '\u0653' to "Maddah",
        '\u0654' to "Hamza Above",
        '\u0655' to "Hamza Below",
        '\u0670' to "Superscript Alif"
    )

    fun isDiacritic(ch: Char): Boolean {
        val code = ch.code
        return (code in 0x064B..0x065F) || code == 0x0670 || (code in 0x06D6..0x06ED)
    }

    fun parse(word: String): List<ArabicLetterUnit> {
        val units = mutableListOf<ArabicLetterUnit>()
        var i = 0
        while (i < word.length) {
            val base = word[i]
            if (isDiacritic(base) || base.isWhitespace()) {
                i++
                continue
            }
            val cluster = StringBuilder().append(base)
            val harakat = mutableListOf<String>()
            var j = i + 1
            while (j < word.length && isDiacritic(word[j])) {
                val mark = word[j]
                cluster.append(mark)
                diacriticNames[mark]?.let { harakat.add(it) }
                j++
            }

            val letterName = letterNames[base] ?: "Letter"
            val slugBase = letterName.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
            val primaryHarakat = harakat.firstOrNull { it != "Shadda" }
                ?.lowercase()
                ?.replace(Regex("[^a-z0-9]+"), "_")
                ?.trim('_')
            val audioKey = if (primaryHarakat != null) "${slugBase}_$primaryHarakat" else slugBase

            units.add(
                ArabicLetterUnit(
                    index = units.size,
                    display = cluster.toString(),
                    baseChar = base,
                    letterName = letterName,
                    harakatNames = harakat,
                    audioKey = audioKey
                )
            )
            i = j
        }
        return units
    }
}
