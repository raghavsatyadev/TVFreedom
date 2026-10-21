package io.github.raghavsatyadev.tvfreedom

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.content.ContextCompat
import androidx.core.content.pm.PackageInfoCompat
import io.github.raghavsatyadev.tvfreedom.ai.ClaudeClassifier
import io.github.raghavsatyadev.tvfreedom.ai.NanoClassifier
import io.github.raghavsatyadev.tvfreedom.ai.TermuxClaude
import io.github.raghavsatyadev.tvfreedom.core.Classifier
import io.github.raghavsatyadev.tvfreedom.flow.RunService
import io.github.raghavsatyadev.tvfreedom.store.Assets
import io.github.raghavsatyadev.tvfreedom.store.Brand
import io.github.raghavsatyadev.tvfreedom.store.Images
import io.github.raghavsatyadev.tvfreedom.store.Net
import io.github.raghavsatyadev.tvfreedom.store.PhoneLocation
import io.github.raghavsatyadev.tvfreedom.store.Prefs
import io.github.raghavsatyadev.tvfreedom.store.Settings
import io.github.raghavsatyadev.tvfreedom.store.Storage
import io.github.raghavsatyadev.tvfreedom.tv.AndroidTvLink
import io.github.raghavsatyadev.tvfreedom.ui.AppPlatform
import io.github.raghavsatyadev.tvfreedom.ui.Place
import io.github.raghavsatyadev.tvfreedom.ui.Progress
import io.github.raghavsatyadev.tvfreedom.ui.TermuxStep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URL

/** The Android side of [AppPlatform]: files, prefs, adb, the foreground service, AI and location. */
class AndroidPlatform(private val ctx: Context, override val open: StateFlow<Int>) : AppPlatform {
    override val storage = Storage(FileSystem.SYSTEM, ctx.filesDir.toOkioPath(), ctx.cacheDir.toOkioPath(),
        AndroidAssets(ctx), SharedPrefs(ctx.getSharedPreferences("tvfreedom", Context.MODE_PRIVATE)))
    override val brand = Brand(FileSystem.SYSTEM, ctx.filesDir.toOkioPath(), AndroidImages, storage.settings)
    override val link by lazy { AndroidTvLink(ctx, storage.keysDir.toFile()) }
    override val net = Net { url ->
        withContext(Dispatchers.IO) {
            URL(url).openConnection().apply { connectTimeout = 20_000; readTimeout = 30_000 }.getInputStream().use { it.readBytes() }
        }
    }

    override fun startJob(p: Progress) = RunService.start(ctx, p)
    override fun updateJob(p: Progress) = RunService.update(ctx, p)
    override fun stopJob() = RunService.stop(ctx)

    override fun classifier(ai: Settings.Ai, settings: Settings, waitOpen: suspend () -> Unit, log: (String) -> Unit): Classifier? =
        when (ai) {
            Settings.Ai.TERMUX -> TermuxClaude(ctx, settings.termuxModel.alias, waitOpen)
            Settings.Ai.NANO -> NanoClassifier(waitOpen)
            Settings.Ai.CLAUDE -> settings.claudeKey.takeIf { it.isNotBlank() }?.let { ClaudeClassifier(it, settings.claudeModel) }
                ?: null.also { log("no Claude API key: only the guard and the lists decide") }
            Settings.Ai.NONE -> null
        }

    override suspend fun checkAi(ai: Settings.Ai, settings: Settings, log: (String) -> Unit): String = when (ai) {
        Settings.Ai.TERMUX -> TermuxClaude(ctx, settings.termuxModel.alias).check().let { if (it == TermuxClaude.READY) "Claude is ready" else it }
        Settings.Ai.NANO -> NanoClassifier().let { n -> try { n.prepare(log) } finally { n.close() } }
        Settings.Ai.CLAUDE -> if (settings.claudeKey.isBlank()) "add a Claude API key" else "key saved"
        Settings.Ai.NONE -> "no AI: only the guard and the lists decide"
    }

    override suspend fun termuxStep(log: (String) -> Unit): TermuxStep = TermuxClaude(ctx).probe(log)

    override fun locationGranted() =
        ContextCompat.checkSelfPermission(ctx, PhoneLocation.PERMISSION) == PackageManager.PERMISSION_GRANTED

    override suspend fun phonePlace(): Place? = PhoneLocation.find(ctx)

    override fun apkVersion(apk: ByteArray): Long {
        val file = File(ctx.cacheDir, "version-check.apk").apply { writeBytes(apk) }
        return try {
            ctx.packageManager.getPackageArchiveInfo(file.path, 0)?.let { PackageInfoCompat.getLongVersionCode(it) } ?: 0L
        } finally {
            file.delete()
        }
    }
}

private class SharedPrefs(private val p: SharedPreferences) : Prefs {
    override fun string(key: String, default: String) = p.getString(key, default) ?: default
    override fun putString(key: String, value: String) = p.edit().putString(key, value).apply()
    override fun bool(key: String, default: Boolean) = p.getBoolean(key, default)
    override fun putBool(key: String, value: Boolean) = p.edit().putBoolean(key, value).apply()
}

private class AndroidAssets(private val ctx: Context) : Assets {
    override fun read(path: String) = ctx.assets.open(path).use { it.readBytes() }
    override fun list(dir: String) = ctx.assets.list(dir).orEmpty().toList()
}

private object AndroidImages : Images {
    override fun shrinkToPng(bytes: ByteArray, maxSide: Int): ByteArray {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "that file is not an image this phone can read" }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: error("could not read the image")
        val scale = maxSide.toFloat() / maxOf(decoded.width, decoded.height)
        val bitmap = if (scale < 1f) Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt().coerceAtLeast(1),
            (decoded.height * scale).toInt().coerceAtLeast(1), true) else decoded
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    override fun samplePixels(bytes: ByteArray, maxSamples: Int): IntArray {
        val b = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return IntArray(0)
        val step = maxOf(1, maxOf(b.width, b.height) / maxSamples)
        return buildList {
            for (y in 0 until b.height step step) for (x in 0 until b.width step step) add(b.getPixel(x, y))
        }.toIntArray()
    }
}
