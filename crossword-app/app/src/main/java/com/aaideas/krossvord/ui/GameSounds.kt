package com.aaideas.krossvord.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.aaideas.krossvord.R

/** Short chime and buzz for a found word and a solved level. Silent when the player turns them off. */
class GameSounds(context: Context) {
    /** Chime on/off and buzz on/off, set from the settings. */
    var sound = true
    var vibrate = true
    private val pool: SoundPool? = runCatching {
        SoundPool.Builder().setMaxStreams(2).setAudioAttributes(
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        ).build()
    }.getOrNull()
    private val word = pool?.load(context, R.raw.word, 1) ?: 0
    private val level = pool?.load(context, R.raw.level, 1) ?: 0
    private val vibrator: Vibrator? = runCatching {
        if (Build.VERSION.SDK_INT >= 31) (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        else @Suppress("DEPRECATION") (context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator)
    }.getOrNull()

    fun wordFound() = play(word, longArrayOf(0, 40))
    fun levelSolved() = play(level, longArrayOf(0, 60, 80, 60, 80, 120))

    private fun play(id: Int, pattern: LongArray) {
        if (sound) runCatching { pool?.play(id, 0.8f, 0.8f, 1, 0, 1f) }
        if (vibrate) runCatching {
            val v = vibrator ?: return@runCatching
            if (!v.hasVibrator()) return@runCatching
            if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createWaveform(pattern, -1))
            else @Suppress("DEPRECATION") v.vibrate(pattern, -1)
        }
    }

    fun release() { pool?.release() }
}
