@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package io.github.raghavsatyadev.tvfreedom.ui

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

// Render with: android studio render-compose-preview <this file> WorkingPreview

@Preview
@Composable
fun WorkingPreview() {
    TvFreedomTheme { Working(headline = "Removing bloatware", detail = "12 of 40 apps") }
}

@Preview
@Composable
fun NoticePreview() {
    TvFreedomTheme { Notice("Turn on USB debugging on the TV, then try again.") }
}
