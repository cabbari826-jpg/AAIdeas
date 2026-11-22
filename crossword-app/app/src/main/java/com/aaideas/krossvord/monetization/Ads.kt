package com.aaideas.krossvord.monetization

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Ads sit behind this interface so game logic never depends on an ad SDK.
 * AdMob (+ UMP consent) will implement it later; the prototype shows a placeholder dialog.
 */
interface AdService {
    /** Rewarded ad, always started by the player. [onReward] runs only if the ad was watched. */
    fun showRewarded(reason: String, onReward: () -> Unit)

    /** Interstitial, only at natural breaks (after a level). [onClosed] always runs. */
    fun showInterstitial(onClosed: () -> Unit)
}

enum class AdKind { REWARDED, INTERSTITIAL }

data class AdRequest(val kind: AdKind, val reason: String, val onFinished: (watched: Boolean) -> Unit)

/** Placeholder: the UI observes [pending] and draws a fake ad. */
class PlaceholderAdService : AdService {
    var pending by mutableStateOf<AdRequest?>(null)
        private set

    override fun showRewarded(reason: String, onReward: () -> Unit) {
        pending = AdRequest(AdKind.REWARDED, reason) { watched -> if (watched) onReward() }
    }

    override fun showInterstitial(onClosed: () -> Unit) {
        pending = AdRequest(AdKind.INTERSTITIAL, "") { onClosed() }
    }

    fun finish(watched: Boolean) {
        val r = pending ?: return
        pending = null
        r.onFinished(watched)
    }
}

/**
 * Owner's rule (2026-11-22): no ads between words or levels. Ads appear only when the player
 * asks for something (out of lives, a letter, an easier clue). Interstitials are kept in the
 * code path but never shown.
 */
object AdPolicy {
    @Suppress("UNUSED_PARAMETER")
    fun shouldShowInterstitial(isPremium: Boolean, totalCompleted: Int, levelsSinceLast: Int): Boolean = false
}
