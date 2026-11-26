package com.aaideas.krossvord.update

import androidx.compose.runtime.Composable

/** The release (Google Play) build updates through the store: nothing to show. */
@Composable
fun UpdateBanner() = Unit

/** Store build: only the version; updates come from Google Play. */
@Composable
fun UpdateSettingsRow() {
    com.aaideas.krossvord.ui.SettingText("Versiya", com.aaideas.krossvord.BuildConfig.VERSION_NAME)
}
