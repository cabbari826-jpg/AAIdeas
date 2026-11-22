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

/** When interstitials may appear. Never during solving, never for premium users. */
object AdPolicy {
    /** No interstitial until the player has finished this many levels in total. */
    const val FREE_LEVELS_BEFORE_FIRST_AD = 1
    /** Then at most one interstitial per this many finished levels. */
    const val LEVELS_BETWEEN_INTERSTITIALS = 2

    fun shouldShowInterstitial(isPremium: Boolean, totalCompleted: Int, levelsSinceLast: Int): Boolean =
        !isPremium && totalCompleted > FREE_LEVELS_BEFORE_FIRST_AD && levelsSinceLast >= LEVELS_BETWEEN_INTERSTITIALS
}
