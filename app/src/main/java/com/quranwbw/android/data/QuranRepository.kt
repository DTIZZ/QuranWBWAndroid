package com.quranwbw.android.data

import android.content.Context
import com.quranwbw.android.model.Ayah
import com.quranwbw.android.model.QuranWord
import com.quranwbw.android.model.Surah
import org.json.JSONObject

class QuranRepository(private val context: Context) {

    fun loadSurahs(): List<Surah> {
        val assetName = if (context.assets.list("")?.contains("quran_full.json") == true) {
            "quran_full.json"
        } else {
            "quran_sample.json"
        }
        val jsonText = context.assets.open(assetName)
            .bufferedReader()
            .use { it.readText() }

        val root = JSONObject(jsonText)
        val surahsArray = root.getJSONArray("surahs")
        return buildList {
            for (s in 0 until surahsArray.length()) {
                val sObj = surahsArray.getJSONObject(s)
                val ayahsArray = sObj.getJSONArray("ayahs")
                val ayahs = buildList {
                    for (a in 0 until ayahsArray.length()) {
                        val aObj = ayahsArray.getJSONObject(a)
                        val wordsArray = aObj.getJSONArray("words")
                        val words = buildList {
                            for (w in 0 until wordsArray.length()) {
                                val wObj = wordsArray.getJSONObject(w)
                                add(
                                    QuranWord(
                                        index = wObj.getInt("index"),
                                        arabic = wObj.getString("arabic"),
                                        transliteration = wObj.getString("transliteration"),
                                        translation = wObj.getString("translation"),
                                        audioUrl = wObj.getString("audioUrl"),
                                        startMs = wObj.getLong("startMs"),
                                        endMs = wObj.getLong("endMs")
                                    )
                                )
                            }
                        }
                        add(
                            Ayah(
                                number = aObj.getInt("number"),
                                audioUrl = aObj.getString("audioUrl"),
                                words = words
                            )
                        )
                    }
                }
                add(
                    Surah(
                        number = sObj.getInt("number"),
                        nameArabic = sObj.getString("nameArabic"),
                        nameEnglish = sObj.getString("nameEnglish"),
                        meaning = sObj.getString("meaning"),
                        ayahs = ayahs
                    )
                )
            }
        }
    }
}
