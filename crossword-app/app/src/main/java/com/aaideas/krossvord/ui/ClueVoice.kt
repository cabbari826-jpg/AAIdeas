package com.aaideas.krossvord.ui

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/**
 * Reads a clue aloud with the phone's own text-to-speech, only if it has an Azerbaijani voice;
 * otherwise [available] stays false and the button is hidden. Started on first use, not at
 * app start, so weak phones don't pay for it unless the player opens a clue.
 */
class ClueVoice(private val context: Context) {
    var available by mutableStateOf(false)
        private set
    private var tts: TextToSpeech? = null
    private val az = Locale("az", "AZ")

    fun warmUp() {
        if (tts != null) return
        tts = runCatching {
            TextToSpeech(context.applicationContext) { status ->
                val t = tts ?: return@TextToSpeech
                if (status != TextToSpeech.SUCCESS) return@TextToSpeech
                val ok = runCatching { t.isLanguageAvailable(az) >= TextToSpeech.LANG_AVAILABLE }.getOrDefault(false)
                if (ok) { t.language = az; t.setSpeechRate(0.85f) }
                available = ok
            }
        }.getOrNull()
    }

    fun speak(text: String) {
        if (available) tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "clue")
    }

    fun shutdown() { tts?.shutdown(); tts = null; available = false }
}
