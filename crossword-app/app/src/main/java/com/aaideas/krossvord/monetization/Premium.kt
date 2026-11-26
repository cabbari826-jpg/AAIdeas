package com.aaideas.krossvord.monetization

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Premium = one-time purchase: no interstitial ads, hints without rewarded ads.
 * The prototype stores a local flag. Google Play Billing replaces [purchase] / [restore] later,
 * and the flag must then come from the verified purchase, not from this toggle.
 */
interface PremiumManager {
    val isPremium: StateFlow<Boolean>
    fun purchase()
    fun restore()
    fun resetForTesting()
}

class LocalPremiumManager(context: Context) : PremiumManager {
    private val prefs = context.getSharedPreferences("premium", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(prefs.getBoolean(KEY, false))
    override val isPremium: StateFlow<Boolean> = state

    override fun purchase() = set(true)
    override fun restore() { state.value = prefs.getBoolean(KEY, false) }
    override fun resetForTesting() = set(false)

    private fun set(v: Boolean) {
        prefs.edit().putBoolean(KEY, v).apply()
        state.value = v
    }

    private companion object { const val KEY = "is_premium" }
}
