package io.github.raghavsatyadev.tvfreedom.store

import android.content.Context
import androidx.core.content.pm.PackageInfoCompat
import io.github.raghavsatyadev.tvfreedom.flow.DesignFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.jvm.javaio.toInputStream

/**
 * The TVFreedom home screen for the TV, from the newest GitHub release. Kept on the phone, and fetched again when
 * older than [FRESH_MS] or than [DesignFlow.LOOKS] (this TVFreedom's designs need it), so a run without internet still
 * has the last one.
 */
class LauncherDownload(private val ctx: Context) {
    private val file get() = File(ctx.filesDir, "launcher/launcher.apk")

    /** The APK and its versionCode. */
    suspend fun get(log: (String) -> Unit): Pair<ByteArray, Long> = withContext(Dispatchers.IO) {
        val have = file.takeIf { it.isFile }
        val stale = have == null || System.currentTimeMillis() - have.lastModified() > FRESH_MS || version(have) < DesignFlow.LOOKS
        if (stale) {
            try {
                fetch()
                log("downloaded the home screen ${version(file)} from GitHub")
            } catch (e: IOException) {
                if (have == null) error("TVFreedom could not download the home screen for the TV. Check the phone's internet")
                log("could not download the home screen (${e.message}); using the one from before")
            }
        }
        file.readBytes() to version(file)
    }

    private suspend fun fetch() {
        val part = File(file.parentFile, "launcher.part").apply { parentFile?.mkdirs() }
        http.prepareGet(URL_APK).execute { r ->
            if (r.status.value != 200) throw IOException("GitHub answered ${r.status.value}")
            r.bodyAsChannel().toInputStream().use { input -> part.outputStream().use { input.copyTo(it) } }
        }
        val pkg = ctx.packageManager.getPackageArchiveInfo(part.path, 0)?.packageName
        if (pkg != PACKAGE) {
            part.delete()
            throw IOException("the download is not the home screen")
        }
        if (!part.renameTo(file)) {
            file.delete()
            part.renameTo(file)
        }
    }

    private fun version(apk: File): Long =
        ctx.packageManager.getPackageArchiveInfo(apk.path, 0)?.let { PackageInfoCompat.getLongVersionCode(it) } ?: 0L

    companion object {
        /** The release asset name; publish the launcher's release APK under it. */
        const val ASSET = "TVFreedomHome.apk"
        private const val URL_APK = "https://github.com/raghavsatyadev/TVFreedom/releases/latest/download/$ASSET"
        private const val PACKAGE = "io.github.raghavsatyadev.tvfreedom.home"
        private const val FRESH_MS = 6 * 60 * 60 * 1000L
    }
}
