package io.github.raghavsatyadev.tvfreedom.home;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;

import java.util.Calendar;
import java.util.function.Consumer;

/** The row above the dock: greeting, and the Inputs, network, Bluetooth and Settings pills. */
final class TopBar {

    private final Activity act;
    private final TextView greeting;
    private final TextView btnWifi;
    private final Handler handler = new Handler(Looper.getMainLooper());
    /** Refreshes the network pill when the connection changes: at boot Wi-Fi often connects after Home is shown. */
    private final ConnectivityManager.NetworkCallback networkCallback = new ConnectivityManager.NetworkCallback() {
        @Override
        public void onAvailable(@NonNull Network network) {
            handler.post(TopBar.this::updateWifiLabel);
        }

        @Override
        public void onLost(@NonNull Network network) {
            handler.post(TopBar.this::updateWifiLabel);
        }
    };

    TopBar(Activity act, ViewGroup root, String liveTvPkg, Consumer<Intent> open) {
        this.act = act;
        greeting = act.findViewById(R.id.greeting);
        btnWifi = act.findViewById(R.id.btnWifi);
        View btnInputs = act.findViewById(R.id.btnInputs);
        View btnBluetooth = act.findViewById(R.id.btnBluetooth);
        View btnSettings = act.findViewById(R.id.btnSettings);
        btnInputs.setOnClickListener(v -> InputsPopup.show(act, v, root, liveTvPkg, open));
        btnWifi.setOnClickListener(v -> open.accept(new Intent(Settings.ACTION_WIFI_SETTINGS)));
        btnBluetooth.setOnClickListener(v -> open.accept(bluetoothSettings()));
        btnSettings.setOnClickListener(v -> open.accept(new Intent(Settings.ACTION_SETTINGS)));

        // Keep focus on the top bar at its ends and at the top: Android's focus finder would otherwise
        // jump to a tile far below when there is nothing in that direction.
        View.OnKeyListener pillKeys = (v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) return true;
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && v.getId() == R.id.btnInputs) return true;
            return keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && v.getId() == R.id.btnSettings;
        };
        btnInputs.setOnKeyListener(pillKeys);
        btnWifi.setOnKeyListener(pillKeys);
        btnBluetooth.setOnKeyListener(pillKeys);
        btnSettings.setOnKeyListener(pillKeys);

        ConnectivityManager cm = act.getSystemService(ConnectivityManager.class);
        if (cm != null) cm.registerDefaultNetworkCallback(networkCallback);
    }

    /** Bluetooth settings; a TV whose Settings has no such page gets its main Settings (remotes and accessories are there). */
    private Intent bluetoothSettings() {
        Intent bt = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
        return bt.resolveActivity(act.getPackageManager()) != null ? bt : new Intent(Settings.ACTION_SETTINGS);
    }

    /** Greeting and network label; called on resume and every minute. */
    void update() {
        updateGreeting();
        updateWifiLabel();
    }

    void release() {
        ConnectivityManager cm = act.getSystemService(ConnectivityManager.class);
        if (cm != null) cm.unregisterNetworkCallback(networkCallback);
    }

    private void updateGreeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        int res;
        if (hour < 5) res = R.string.good_night;
        else if (hour < 12) res = R.string.good_morning;
        else if (hour < 17) res = R.string.good_afternoon;
        else if (hour < 22) res = R.string.good_evening;
        else res = R.string.good_night;
        greeting.setText(res);
    }

    /** Shows the Wi-Fi network name on the button when we can read it, otherwise the connection type. */
    private void updateWifiLabel() {
        String text = act.getString(R.string.wifi);
        try {
            ConnectivityManager cm = (ConnectivityManager) act.getSystemService(Context.CONNECTIVITY_SERVICE);
            Network net = cm != null ? cm.getActiveNetwork() : null;
            NetworkCapabilities caps = net != null ? cm.getNetworkCapabilities(net) : null;
            if (caps == null) {
                text = act.getString(R.string.offline);
            } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                text = act.getString(R.string.ethernet);
            } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                WifiManager wm = (WifiManager) act.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                WifiInfo info = wm != null ? wm.getConnectionInfo() : null;
                String ssid = info != null ? info.getSSID() : null;
                if (ssid != null) {
                    ssid = ssid.replace("\"", "").trim();
                    if (!ssid.isEmpty() && !ssid.contains("unknown") && !ssid.equals("0x")) text = ssid;
                }
            }
        } catch (Exception ignored) {
        }
        btnWifi.setText(text);
    }
}
