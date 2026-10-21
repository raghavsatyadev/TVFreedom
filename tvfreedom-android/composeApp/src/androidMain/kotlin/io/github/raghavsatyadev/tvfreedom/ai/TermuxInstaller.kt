package io.github.raghavsatyadev.tvfreedom.ai

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.coroutines.resume

/**
 * Gets Termux from F-Droid without the F-Droid app: asks F-Droid's API for the suggested version, downloads that
 * APK, checks it is com.termux signed with F-Droid's Termux key ([CERT_SHA256]), then installs it with
 * PackageInstaller. Android shows its own "Install?" screen; Play Protect may ask too.
 */
class TermuxInstaller(private val ctx: Context) {

    sealed interface Result {
        data object Installed : Result
        /** Android needs "Install unknown apps" for TVFreedom first: send the user to [unknownSources]. */
        data object NeedsPermission : Result
        data class Failed(val why: String) : Result
    }

    fun canInstall() = Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ctx.packageManager.canRequestPackageInstalls()

    /** The "Install unknown apps" switch for TVFreedom. */
    fun unknownSources(): Intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Download, check and install; [progress] gets 0..1 while downloading. */
    suspend fun install(progress: (Float) -> Unit): Result {
        if (!canInstall()) return Result.NeedsPermission
        val apk = runCatching { download(progress) }.getOrElse { return Result.Failed("Download failed: ${it.message ?: it.javaClass.simpleName}") }
        verify(apk)?.let { apk.delete(); return Result.Failed(it) }
        return try {
            session(apk)
        } finally {
            apk.delete()
        }
    }

    /**
     * A slow or broken connection does not start over: each try asks only for the bytes still missing, and the part
     * already downloaded stays in the cache for the next "Try again".
     */
    private suspend fun download(progress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val api = retrying { get(API).inputStream.use { it.readBytes().decodeToString() } }
        val vc = Regex("\"suggestedVersionCode\"\\s*:\\s*(\\d+)").find(api)?.groupValues?.get(1)
            ?: error("F-Droid did not say which Termux to get")
        val file = File(ctx.cacheDir, "termux_$vc.apk")
        ctx.cacheDir.listFiles { f -> f.name.startsWith("termux") && f != file }?.forEach { it.delete() }
        retrying {
            val conn = get("$REPO/com.termux_$vc.apk", from = file.length())
            if (conn.responseCode == 416) return@retrying // the cached part is already the whole file
            val resumed = conn.responseCode == 206
            var done = if (resumed) file.length() else 0L
            val total = conn.contentLengthLong.let { if (it > 0) done + it else 0L }
            conn.inputStream.use { input ->
                FileOutputStream(file, resumed).use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0) progress(done.toFloat() / total)
                    }
                }
            }
        }
        file
    }

    /** Runs [block] up to [TRIES] times, waiting a little longer after each failed try. */
    private suspend fun <T> retrying(block: () -> T): T {
        var last: Exception? = null
        repeat(TRIES) { n ->
            try {
                return block()
            } catch (e: IOException) {
                last = e
                delay(2_000L * (n + 1))
            }
        }
        throw last!!
    }

    /** [from] > 0 asks for the rest of a file (206); a server that ignores it answers 200 with the whole file. */
    private fun get(url: String, from: Long = 0): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 20_000
        readTimeout = 60_000
        if (from > 0) setRequestProperty("Range", "bytes=$from-")
        if (responseCode !in setOf(200, 206, 416)) error("$url answered $responseCode")
    }

    /** Null when [apk] is com.termux signed with the pinned key; otherwise why not. */
    @Suppress("DEPRECATION")
    private fun verify(apk: File): String? {
        val pm = ctx.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val info = pm.getPackageArchiveInfo(apk.path, flags) ?: return "The download is not an app"
        if (info.packageName != TERMUX) return "The download is ${info.packageName}, not Termux"
        val signers = if (Build.VERSION.SDK_INT >= 28) {
            val s = info.signingInfo ?: return "The download is not signed"
            if (s.hasMultipleSigners()) s.apkContentsSigners else s.signingCertificateHistory
        } else info.signatures
        val hashes = signers.orEmpty().map { sig ->
            MessageDigest.getInstance("SHA-256").digest(sig.toByteArray()).joinToString("") { "%02x".format(it) }
        }
        return if (CERT_SHA256 in hashes) null else "The download is not signed by F-Droid"
    }

    private suspend fun session(apk: File): Result {
        val installer = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(TERMUX)
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        withContext(Dispatchers.IO) {
            installer.openSession(id).use { s ->
                s.openWrite("termux.apk", 0, apk.length()).use { out -> apk.inputStream().use { it.copyTo(out) }; s.fsync(out) }
            }
        }
        return suspendCancellableCoroutine { cont ->
            val action = "${ctx.packageName}.TERMUX_INSTALL.$id"
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context, i: Intent) {
                    when (val status = i.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
                        // Android's "Install?" screen: show it and wait for the next status.
                        PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                            @Suppress("DEPRECATION") val confirm = i.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                            confirm?.let { ctx.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        }
                        else -> {
                            runCatching { ctx.unregisterReceiver(this) }
                            val r = if (status == PackageInstaller.STATUS_SUCCESS) Result.Installed
                            else Result.Failed(i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "Android did not install Termux")
                            if (cont.isActive) cont.resume(r)
                        }
                    }
                }
            }
            ContextCompat.registerReceiver(ctx, receiver, IntentFilter(action), ContextCompat.RECEIVER_NOT_EXPORTED)
            cont.invokeOnCancellation { runCatching { ctx.unregisterReceiver(receiver) } }
            // Mutable so PackageInstaller can add its status extras; explicit so only TVFreedom receives it.
            val reply = PendingIntent.getBroadcast(ctx, id, Intent(action).setPackage(ctx.packageName),
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            installer.openSession(id).use { it.commit(reply.intentSender) }
        }
    }

    companion object {
        private const val TERMUX = "com.termux"
        private const val API = "https://f-droid.org/api/v1/packages/com.termux"
        private const val REPO = "https://f-droid.org/repo"
        private const val TRIES = 5
        /** F-Droid's signing key for com.termux (apksigner verify --print-certs on com.termux_1002.apk). */
        const val CERT_SHA256 = "228fb2cfe90831c1499ec3ccaf61e96e8e1ce70766b9474672ce427334d41c42"
    }
}
