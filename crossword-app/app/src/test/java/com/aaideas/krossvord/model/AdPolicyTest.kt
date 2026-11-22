package com.aaideas.krossvord.model

import com.aaideas.krossvord.monetization.AdPolicy
import org.junit.Assert.assertFalse
import org.junit.Test

/** No ads between levels, ever: only rewarded ads the player asks for. */
class AdPolicyTest {
    @Test fun neverBetweenLevels() {
        for (premium in listOf(true, false)) for (done in 0..20) for (since in 0..5)
            assertFalse(AdPolicy.shouldShowInterstitial(premium, done, since))
    }
}
