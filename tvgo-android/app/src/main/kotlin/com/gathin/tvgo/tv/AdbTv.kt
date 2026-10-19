package com.gathin.tvgo.tv

import android.util.Log
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import io.github.muntashirakon.adb.AdbPairingRequiredException
import io.github.muntashirakon.adb.AdbStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date
import java.util.concurrent.TimeUnit

/** The phone's adb identity: one RSA key and self-signed certificate, made once, kept in private storage. */
class AdbKeys(dir: File) {
    private val keyFile = File(dir, "adbkey")
    private val certFile = File(dir, "adbkey.crt")
    val privateKey: PrivateKey
    val certificate: Certificate

    init {
        if (!keyFile.exists() || !certFile.exists()) generate()
        privateKey = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(keyFile.readBytes()))
        certificate = certFile.inputStream().use { CertificateFactory.getInstance("X.509").generateCertificate(it) }
    }

    private fun generate() {
        val kp = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val name = X500Name("CN=tvgo")
        val now = System.currentTimeMillis()
        val holder = JcaX509v3CertificateBuilder(name, BigInteger.valueOf(now), Date(now - 86_400_000L),
            Date(now + 30L * 365 * 86_400_000L), name, kp.public)
            .build(JcaContentSignerBuilder("SHA256withRSA").build(kp.private))
        val cert = JcaX509CertificateConverter().getCertificate(holder)
        keyFile.parentFile?.mkdirs()
        keyFile.writeBytes(kp.private.encoded)
        certFile.writeBytes(cert.encoded)
    }
}

private class Manager(private val keys: AdbKeys, timeoutS: Long) : AbsAdbConnectionManager() {
    init {
        setTimeout(timeoutS, TimeUnit.SECONDS)
    }

    override fun getPrivateKey(): PrivateKey = keys.privateKey
    override fun getCertificate(): Certificate = keys.certificate
    override fun getDeviceName(): String = "tvgo"
}

sealed interface ConnectResult {
    data object Ok : ConnectResult
    data object PairingRequired : ConnectResult
    data class Failed(val why: String) : ConnectResult
}

/**
 * One TV over adb (classic ip:5555 with the "Allow debugging?" prompt, or TLS wireless debugging after pairing).
 * A fresh connection manager is made for every connect, so a reboot never leaves a stale transport behind.
 */
class AdbTv(val host: String, val port: Int, private val keys: AdbKeys) : Shell {
    val address get() = "$host:$port"
    private var mgr: Manager? = null

    /** Connect; waits up to [timeoutS] for the user to accept "Allow debugging?" on the TV. */
    suspend fun connect(timeoutS: Long = 90): ConnectResult = withContext(Dispatchers.IO) {
        close()
        val m = Manager(keys, timeoutS)
        try {
            Log.d(TAG, "connect $address")
            val ok = m.connect(host, port)
            Log.d(TAG, "connect $address -> $ok")
            if (ok) {
                mgr = m
                ConnectResult.Ok
            } else {
                m.close()
                ConnectResult.Failed("the TV did not answer")
            }
        } catch (e: AdbPairingRequiredException) {
            m.close()
            ConnectResult.PairingRequired
        } catch (e: Exception) {
            runCatching { m.close() }
            ConnectResult.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    val connected get() = mgr?.isConnected == true

    override suspend fun sh(cmd: String): String = withContext(Dispatchers.IO) {
        val m = mgr ?: throw IllegalStateException("not connected to $address")
        Log.d(TAG, "sh $cmd")
        withTimeout(120_000) {
            m.openStream("shell:$cmd").use { s -> readAll(s).toString(Charsets.UTF_8).replace("\r\n", "\n").trim() }
        }
    }

    /**
     * Read a stream to its end, working around two libadb-android 3.1.1 races:
     * - When the TV's close arrives while output is still queued, libadb only sets a private "pending close"
     *   flag. Once the queue is drained, read() waits forever. So pending close plus nothing left counts as the end.
     * - read() throws "Stream closed." instead of returning -1 if the close lands between its checks.
     */
    private fun readAll(s: AdbStream): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        while (true) {
            val n = try {
                if (drained(s)) -1 else s.read(buf, 0, buf.size)
            } catch (e: IOException) {
                if (s.isClosed) -1 else throw e
            }
            if (n < 0) break
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    /** The TV has closed [s] and every byte it sent has been read (nothing more can arrive after a close). */
    private fun drained(s: AdbStream): Boolean {
        val pending = pendingClose?.let { runCatching { it.getBoolean(s) }.getOrDefault(false) } ?: false
        return s.isClosed || (pending && s.available() == 0)
    }

    /** Reboot the TV and drop this connection (the daemon goes away with it). */
    suspend fun reboot() = withContext(Dispatchers.IO) {
        runCatching { mgr?.openStream("reboot:")?.close() }
        close()
    }

    fun close() {
        runCatching { mgr?.close() }
        mgr = null
    }

    companion object {
        private const val TAG = "tvgo.adb"

        /** libadb's private AdbStream.mPendingClose (see [readAll]); null if a library update renamed it. */
        private val pendingClose = runCatching {
            AdbStream::class.java.getDeclaredField("mPendingClose").apply { isAccessible = true }
        }.getOrNull()

        /** Android 11+ wireless debugging: pair once with the 6-digit code the TV shows. */
        suspend fun pair(host: String, port: Int, code: String, keys: AdbKeys): String = withContext(Dispatchers.IO) {
            val m = Manager(keys, 30)
            try {
                if (m.pair(host, port, code.trim())) "paired" else "pairing failed"
            } catch (e: Exception) {
                "pairing failed: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                runCatching { m.close() }
            }
        }

        /** "192.168.1.20:5555" or "192.168.1.20" (port 5555) to host and port, or null. */
        fun parseAddress(text: String): Pair<String, Int>? {
            val t = text.trim()
            val host = t.substringBeforeLast(':', t).ifEmpty { return null }
            val port = if (':' in t) t.substringAfterLast(':').toIntOrNull() ?: return null else 5555
            if (port !in 1..65535 || !Regex("^[A-Za-z0-9.\\-]+$").matches(host)) return null
            return host to port
        }
    }
}
