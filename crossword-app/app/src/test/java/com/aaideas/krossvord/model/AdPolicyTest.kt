package com.aaideas.krossvord.model

import com.aaideas.krossvord.monetization.AdPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdPolicyTest {
    @Test fun noAdsForPremium() = assertFalse(AdPolicy.shouldShowInterstitial(true, 10, 10))
    @Test fun noAdAfterFirstLevel() = assertFalse(AdPolicy.shouldShowInterstitial(false, 1, 1))
    @Test fun adEverySecondLevelLater() {
        assertTrue(AdPolicy.shouldShowInterstitial(false, 2, 2))
        assertFalse(AdPolicy.shouldShowInterstitial(false, 3, 1))
    }
}
