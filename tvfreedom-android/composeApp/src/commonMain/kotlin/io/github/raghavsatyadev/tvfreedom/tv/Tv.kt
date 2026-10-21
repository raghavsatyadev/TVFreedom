package io.github.raghavsatyadev.tvfreedom.tv

/** One TV over adb. The Android app talks to it with libadb ([io.github.raghavsatyadev.tvfreedom.tv.AdbTv]). */
interface Tv : Shell {
    val host: String
    val port: Int
    val address: String get() = "$host:$port"
    val connected: Boolean

    /** Connect; waits up to [timeoutS] for the user to accept "Allow debugging?" on the TV. */
    suspend fun connect(timeoutS: Long = 90): ConnectResult

    /**
     * Run [cmd] with the raw exec: service (no terminal, so binary output such as a PNG arrives untouched),
     * writing [input] to its stdin first. Returns every byte of stdout.
     */
    suspend fun exec(cmd: String, input: ByteArray? = null, timeoutMs: Long = 120_000): ByteArray

    suspend fun reboot()

    fun close()
}

sealed interface ConnectResult {
    data object Ok : ConnectResult
    data object PairingRequired : ConnectResult
    data class Failed(val why: String) : ConnectResult
}

/** A TV found on the network: [why] says how (mDNS service or open port 5555); [pairing] is a pairing port. */
data class Candidate(val address: String, val why: String, val pairing: Boolean = false)

/** Finds TVs and opens adb connections to them, with this phone's adb key. */
interface TvLink {
    fun tv(host: String, port: Int): Tv

    /** Pair with a TV's "Pair device with pairing code" screen; "paired" when it worked. */
    suspend fun pair(host: String, port: Int, code: String): String

    /** This phone's Wi-Fi address, or null when it isn't on Wi-Fi. */
    fun localIp(): String?

    /** mDNS and a port 5555 scan of the local network. */
    suspend fun find(): List<Candidate>

    /** mDNS only (quick): used to find a TV again after its wireless debugging port moved. */
    suspend fun mdns(): List<Candidate>

    companion object {
        /** "192.168.1.20:5555" or "192.168.1.20" (port 5555) to host and port, or null if it isn't an address. */
        fun parseAddress(text: String): Pair<String, Int>? {
            val t = text.trim()
            val host = t.substringBeforeLast(':', t).ifEmpty { return null }
            val port = if (':' in t) t.substringAfterLast(':').toIntOrNull() ?: return null else 5555
            if (port !in 1..65535 || !Regex("^[A-Za-z0-9.-]+$").matches(host)) return null
            return host to port
        }
    }
}
