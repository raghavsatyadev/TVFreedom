package io.github.raghavsatyadev.tvfreedom.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

actual fun decodeImage(bytes: ByteArray, maxWidth: Int): ImageBitmap? {
    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
    if (o.outWidth <= 0) return null
    o.inSampleSize = generateSequence(1) { it * 2 }.first { o.outWidth / it <= maxWidth }
    o.inJustDecodeBounds = false
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)?.asImageBitmap()
}

@Composable
actual fun SystemBack(enabled: Boolean, onBack: () -> Unit) = BackHandler(enabled, onBack)

@Composable
actual fun rememberImagePicker(onPicked: (ByteArray) -> Unit): () -> Unit {
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { u -> ctx.contentResolver.openInputStream(u)?.use { it.readBytes() } }?.let(onPicked)
    }
    return { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
}

@Composable
actual fun rememberPermission(p: Permission, onResult: (Boolean) -> Unit): PermissionAsk {
    val ctx = LocalContext.current
    val name = when (p) {
        Permission.LOCATION -> Manifest.permission.ACCESS_COARSE_LOCATION
        Permission.LOCAL_NETWORK -> "android.permission.ACCESS_LOCAL_NETWORK"
    }
    val needed = p != Permission.LOCAL_NETWORK || Build.VERSION.SDK_INT >= 37
    fun check() = !needed || ContextCompat.checkSelfPermission(ctx, name) == PackageManager.PERMISSION_GRANTED
    var answered by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { answered = it; onResult(it) }
    return object : PermissionAsk {
        override val granted get() = answered || check()
        override fun ask() = launcher.launch(name)
    }
}
