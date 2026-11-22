package com.quranwbw.android.audio

import android.content.Context
import android.net.Uri
import android.speech.tts.TextToSpeech
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.quranwbw.android.model.ArabicLetterUnit
import java.util.Locale

/**
 * Handles word-level, ayah-level, and letter-level audio playback.
 *
 * - Words & Ayahs: streamed/cached via Media3 ExoPlayer using QuranWBW-style MP3 URLs.
 * - Letters: looks up a bundled `res/raw/<audioKey>` clip first; if not yet bundled,
 *   falls back to Android's Arabic TextToSpeech so letter taps produce immediate audio.
 */
class QuranAudioPlayer(private val context: Context) {

    private val exoPlayer: ExoPlayer = ExoPlayer.Builder(context).build()
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale("ar"))
                ttsReady = result != TextToSpeech.LANG_MISSING_DATA &&
                    result != TextToSpeech.LANG_NOT_SUPPORTED
            }
        }
    }

    fun playUrl(url: String, onEnded: () -> Unit = {}) {
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    exoPlayer.removeListener(this)
                    onEnded()
                }
            }
        }
        exoPlayer.addListener(listener)
        exoPlayer.setMediaItem(MediaItem.fromUri(url))
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    fun playLetter(letter: ArabicLetterUnit) {
        val rawResId = context.resources.getIdentifier(
            letter.audioKey,
            "raw",
            context.packageName
        )
        if (rawResId != 0) {
            val rawUri = Uri.parse("android.resource://${context.packageName}/$rawResId")
            exoPlayer.stop()
            exoPlayer.setMediaItem(MediaItem.fromUri(rawUri))
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
            return
        }

        if (ttsReady) {
            tts?.speak(letter.display, TextToSpeech.QUEUE_FLUSH, null, "letter_${letter.index}")
        }
    }

    fun currentPositionMs(): Long = exoPlayer.currentPosition

    fun isPlaying(): Boolean = exoPlayer.isPlaying

    fun stop() {
        exoPlayer.stop()
    }

    fun release() {
        exoPlayer.release()
        tts?.stop()
        tts?.shutdown()
    }
}
