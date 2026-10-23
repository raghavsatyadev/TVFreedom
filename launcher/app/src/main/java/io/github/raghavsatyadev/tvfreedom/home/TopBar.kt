package io.github.raghavsatyadev.tvfreedom.home

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import java.util.Calendar

/** The row above the dock: greeting, and the Inputs, network, Bluetooth and Settings pills. */
class TopBar(private val act: Activity, root: ViewGroup, liveTvPkg: String, open: (Intent) -> Unit) {

    private val greeting: TextView = act.findViewById(R.id.greeting)
    private val btnWifi: TextView = act.findViewById(R.id.btnWifi)
    private val handler = Handler(Looper.getMainLooper())

    /** Refreshes the network pill when the connection changes: at boot Wi-Fi often connects after Home is shown. */
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            handler.post { updateWifiLabel() }
        }

        override fun onLost(network: Network) {
            handler.post { updateWifiLabel() }
        }
    }

    init {
        val btnInputs = act.findViewById<View>(R.id.btnInputs)
        val btnBluetooth = act.findViewById<View>(R.id.btnBluetooth)
        val btnSettings = act.findViewById<View>(R.id.btnSettings)
        btnInputs.setOnClickListener { v -> InputsPopup.show(act, v, root, liveTvPkg, open) }
        btnWifi.setOnClickListener { open(Intent(Settings.ACTION_WIFI_SETTINGS)) }
        btnBluetooth.setOnClickListener { open(bluetoothSettings()) }
        btnSettings.setOnClickListener { open(Intent(Settings.ACTION_SETTINGS)) }

        // Keep focus on the top bar at its ends and at the top: Android's focus finder would otherwise
        // jump to a tile far below when there is nothing in that direction.
        val pillKeys = View.OnKeyListener { v, keyCode, event ->
            when {
                event.action != KeyEvent.ACTION_DOWN -> false
                keyCode == KeyEvent.KEYCODE_DPAD_UP -> true
                keyCode == KeyEvent.KEYCODE_DPAD_LEFT && v.id == R.id.btnInputs -> true
                else -> keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && v.id == R.id.btnSettings
            }
        }
        for (pill in arrayOf(btnInputs, btnWifi, btnBluetooth, btnSettings)) pill.setOnKeyListener(pillKeys)

        act.getSystemService(ConnectivityManager::class.java)?.registerDefaultNetworkCallback(networkCallback)
    }

    /** Bluetooth settings; a TV whose Settings has no such page gets its main Settings (remotes and accessories are there). */
    private fun bluetoothSettings(): Intent {
        val bt = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
        return if (bt.resolveActivity(act.packageManager) != null) bt else Intent(Settings.ACTION_SETTINGS)
    }

    /** Greeting and network label; called on resume and every minute. */
    fun update() {
        updateGreeting()
        updateWifiLabel()
    }

    fun release() {
        act.getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(networkCallback)
    }

    private fun updateGreeting() {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        greeting.setText(
            when {
                hour < 5 -> R.string.good_night
                hour < 12 -> R.string.good_morning
                hour < 17 -> R.string.good_afternoon
                hour < 22 -> R.string.good_evening
                else -> R.string.good_night
            },
        )
    }

    /** Shows the Wi-Fi network name on the button when we can read it, otherwise the connection type. */
    @Suppress("DEPRECATION") // WifiManager.connectionInfo: the launcher targets API 29, where it still gives the SSID.
    private fun updateWifiLabel() {
        var text = act.getString(R.string.wifi)
        try {
            val cm = act.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager?
            val caps = cm?.activeNetwork?.let { cm.getNetworkCapabilities(it) }
            if (caps == null) {
                text = act.getString(R.string.offline)
            } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                text = act.getString(R.string.ethernet)
            } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                val wm = act.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager?
                val ssid = wm?.connectionInfo?.ssid?.replace("\"", "")?.trim()
                if (ssid != null && ssid.isNotEmpty() && !ssid.contains("unknown") && ssid != "0x") text = ssid
            }
        } catch (ignored: Exception) {
        }
        btnWifi.text = text
    }
}
