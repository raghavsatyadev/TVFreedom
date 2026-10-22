package io.github.raghavsatyadev.tvfreedom.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap

/** The wallpaper colour scheme where the platform has one (Android 12+), else null. */
@Composable
expect fun dynamicScheme(dark: Boolean): ColorScheme?

/** An image decoded at no more than [maxWidth] px wide (a 4K screenshot would be 33 MB), or null. */
expect fun decodeImage(bytes: ByteArray, maxWidth: Int = 1280): ImageBitmap?

/** Opens the platform's photo picker; [onPicked] gets the chosen image's bytes. */
@Composable
expect fun rememberImagePicker(onPicked: (ByteArray) -> Unit): () -> Unit

/** A runtime permission: [granted] now, and [ask] shows the system prompt once. */
interface PermissionAsk {
    val granted: Boolean
    fun ask()
}

enum class Permission { LOCATION, LOCAL_NETWORK }

/** [onResult] gets the user's answer after [PermissionAsk.ask]. A permission the platform doesn't need is granted. */
@Composable
expect fun rememberPermission(p: Permission, onResult: (Boolean) -> Unit = {}): PermissionAsk

/** The phone's back button runs [onBack] while [enabled]. */
@Composable
expect fun SystemBack(enabled: Boolean, onBack: () -> Unit)

/** The steps that set up Claude in Termux (Android only). */
@Composable
expect fun TermuxSetup(vm: TvFreedomViewModel)
