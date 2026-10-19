package com.gathin.tvgo.tv

import android.content.Context
import android.net.ConnectivityManager
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/** An adb endpoint seen on the Wi-Fi. Nothing has connected to it yet. */
data class Candidate(val address: String, val why: String, val pairing: Boolean = false)

/**
 * Find Android TVs on the Wi-Fi without touching them: adb's mDNS services plus a port 5555 scan.
 * It only lists addresses. The app connects (and the TV may ask "Allow debugging?") only when the user picks one.
 */
class Discovery(private val ctx: Context) {
    private val nsd = ctx.getSystemService(NsdManager::class.java)

    /** The phone's Wi-Fi IPv4 address, or null when there is no LAN (then only a typed address works). */
    fun localIp(): String? {
        val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return null
        val lp = cm.getLinkProperties(cm.activeNetwork) ?: return null
        return lp.linkAddresses.map { it.address }.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }?.hostAddress
    }

    /** Connect, TLS and pairing services from mDNS, browsing for [ms]. */
    suspend fun mdns(ms: Long = 4000): List<Candidate> {
        val wifi = ctx.applicationContext.getSystemService(WifiManager::class.java)
        val lock = wifi?.createMulticastLock("tvgo-mdns")?.apply { setReferenceCounted(false); acquire() }
        val found = ConcurrentHashMap<String, Candidate>()
        val listeners = SERVICES.keys.map { type -> browse(type, found) }
        try {
            delay(ms)
        } finally {
            listeners.forEach { runCatching { nsd.stopServiceDiscovery(it) } }
            lock?.release()
        }
        val me = localIp()
        return found.values.filter { it.address.substringBefore(':') != me }.sortedBy { it.address }
    }

    private fun browse(type: String, found: MutableMap<String, Candidate>): NsdManager.DiscoveryListener {
        val listener = object : NsdManager.DiscoveryListener {
            override fun onServiceFound(info: NsdServiceInfo) = resolve(info, type, found)
            override fun onDiscoveryStarted(t: String) = Unit
            override fun onDiscoveryStopped(t: String) = Unit
            override fun onServiceLost(info: NsdServiceInfo) = Unit
            override fun onStartDiscoveryFailed(t: String, code: Int) = Unit
            override fun onStopDiscoveryFailed(t: String, code: Int) = Unit
        }
        nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener)
        return listener
    }

    private fun resolve(info: NsdServiceInfo, type: String, found: MutableMap<String, Candidate>) {
        val kind = SERVICES.getValue(type)
        fun add(host: String?, port: Int) {
            if (host != null && port > 0) found["$host:$port"] = Candidate("$host:$port", "mDNS $kind ${info.serviceName}", kind == "pairing")
        }
        if (Build.VERSION.SDK_INT >= 34) {
            nsd.registerServiceInfoCallback(info, executor, object : NsdManager.ServiceInfoCallback {
                override fun onServiceInfoCallbackRegistrationFailed(code: Int) = Unit
                override fun onServiceUpdated(s: NsdServiceInfo) {
                    add(s.hostAddresses.firstOrNull { it is Inet4Address }?.hostAddress, s.port)
                    runCatching { nsd.unregisterServiceInfoCallback(this) }
                }
                override fun onServiceLost() = Unit
                override fun onServiceInfoCallbackUnregistered() = Unit
            })
        } else {
            @Suppress("DEPRECATION")
            nsd.resolveService(info, object : NsdManager.ResolveListener {
                override fun onResolveFailed(s: NsdServiceInfo, code: Int) = Unit
                override fun onServiceResolved(s: NsdServiceInfo) = add(s.host?.hostAddress, s.port)
            })
        }
    }

    /** ip:port of every host on the phone's /24 with [port] open (TVs with classic network debugging). */
    suspend fun scan(port: Int = 5555): List<Candidate> = withContext(Dispatchers.IO) {
        val me = localIp() ?: return@withContext emptyList()
        val prefix = me.substringBeforeLast('.')
        val gate = Semaphore(64)
        coroutineScope {
            (1..254).map { "$prefix.$it" }.filter { it != me }.map { ip ->
                async { gate.withPermit { if (open(ip, port)) Candidate("$ip:$port", "port $port open") else null } }
            }.awaitAll().filterNotNull()
        }
    }

    /** Both, one entry per IP (an adb endpoint wins over a bare open port), pairing endpoints listed too. */
    suspend fun find(): List<Candidate> {
        val mdns = mdns()
        val byIp = linkedMapOf<String, Candidate>()
        mdns.filter { !it.pairing }.forEach { byIp.putIfAbsent(it.address.substringBefore(':'), it) }
        scan().forEach { byIp.putIfAbsent(it.address.substringBefore(':'), it) }
        return byIp.values.toList() + mdns.filter { it.pairing }
    }

    private fun open(ip: String, port: Int) = runCatching {
        Socket().use { it.connect(InetSocketAddress(ip, port), 400) }
        true
    }.getOrDefault(false)

    companion object {
        val SERVICES = mapOf("_adb-tls-connect._tcp" to "tls", "_adb._tcp" to "connect", "_adb-tls-pairing._tcp" to "pairing")
        private val executor = Executors.newSingleThreadExecutor()
    }
}
