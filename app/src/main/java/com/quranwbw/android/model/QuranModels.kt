package com.quranwbw.android.model

data class Surah(
    val number: Int,
    val nameArabic: String,
    val nameEnglish: String,
    val meaning: String,
    val ayahs: List<Ayah>
)

data class Ayah(
    val number: Int,
    val audioUrl: String,
    val words: List<QuranWord>
)

data class QuranWord(
    val index: Int,
    val arabic: String,
    val transliteration: String,
    val translation: String,
    val audioUrl: String,
    val startMs: Long,
    val endMs: Long
)

/**
 * A single base Arabic letter combined with its attached diacritics (harakat/shadda/madd).
 */
data class ArabicLetterUnit(
    val index: Int,
    val display: String,
    val baseChar: Char,
    val letterName: String,
    val harakatNames: List<String>,
    val audioKey: String
)
