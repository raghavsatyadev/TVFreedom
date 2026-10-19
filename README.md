# TV_Debloater

Debloat the living-room Android TV over adb (inspired by the "hacked my TV with Claude Code" bookmark).

## Devices (2026-10-18 LAN scan)

- 192.168.1.20:5555 (mDNS `adb-<serial>`, MAC <mac>): **the TV**. Xiaomi Mi TV
  `MiTV-MOOR0` (device `venom`), Android TV 11 (SDK 30), 1080p, ~1.8 GB RAM (54 MB free at idle), 11 GB /data.
  Authorized for this PC on 2026-10-18.
- 192.168.1.22 (mDNS `adb-<serial>`): Wireless debugging (TLS, port changes); needs pairing.

```bash
adb connect 192.168.1.20:5555
```

## Package snapshot (2026-10-18)

`data/packages-*.txt`: 142 packages total, 130 system, 12 user, none disabled. Take a new snapshot
before any change so it can be undone (`pm install-existing <pkg>` restores a `pm uninstall --user 0`).

## Done (2026-10-18)

Tool: `python tv.py backup|apply|restore|mem|shot` (see its docstring). Backups in `backup/`,
action log in `logs/actions.log`.

- **Phase 1** (`lists/phase1.txt`): 13 packages disabled (Xiaomi telemetry, auto-installs, promo
  services, firmware updates). Gotcha: Android saves the disabled state lazily; rebooting within
  seconds of `pm disable-user` silently undid all of it. `tv.py apply` now waits 30 s.
- **Phase 2** (`lists/phase2.txt`): 8 unused streaming apps uninstalled, YouTube Music disabled,
  Plex installed from Play Store.
- Verified after reboot: home loads; Netflix, Prime Video, JioHotstar, Xiaomi TV+ (plays live),
  YouTube, Jellyfin and Plex open; Chromecast (`mediashell`), Mi cast and voice (`katniss`) running.
- RAM available after a fresh boot: ~883 MB before, ~900–960 MB after (varies run to run).

Undo a phase: `python tv.py restore lists/phase1.txt` (uninstalled apps: reinstall from Play Store).
Firmware update: `pm enable com.xiaomi.mitv.updateservice` and `pm enable com.mitv.upgrader`.
Other gotchas: the TV drops adb in standby (wake with `input keyevent KEYCODE_WAKEUP`); the vendor
`screencap` prints debug text before the PNG (`tv.py shot` strips it).
- **Phase 3** (`lists/phase3.txt`): PatchWall (`com.mitv.tvhome.atv`), Mi Home tab provider, scene
  modes, Xiaomi gallery, media explorer and video player disabled (20 disabled in total). Verified
  after reboot: Xiaomi TV+ still plays live without PatchWall; all kept apps and Live TV open;
  Chromecast, Mi cast and voice running. The "Mi Home" tab and Google TV's sponsored banner
  ("Presented by ...") still show: both come from the Google TV launcher, replaced in phase 4.
- RAM available after a fresh boot after phase 3: ~880–900 MB (baseline ~883 MB). Readings swing
  ±60 MB between boots, so phases 1–3 gave no clear RAM gain yet; the launcher swap is the big one.
- `tv.py` now reconnects and wakes the TV before every command (it drops adb in standby).
- **Phase 4** (`lists/phase4.txt`, `launcher/`): new home screen `com.gathin.tvhome`, based on
  Gathin23/tv-vibehack's launcher (MIT, original kept in `vendor/`). Changes: favourites grid with
  full-size tiles (Netflix, Prime Video, JioHotstar, Xiaomi TV+ / YouTube, Jellyfin, Plex in a 4 + 3
  grid, padded with empty slots so other apps start on a new row; Down from TV+ goes to Plex), Live TV
  `com.mitv.livetv` in the Inputs menu. Build: `cd launcher && gradlew.bat assembleDebug` (JDK 21 works,
  `local.properties` = `sdk.dir=<path to Android SDK>`), then `adb install -r
  app/build/outputs/apk/debug/app-debug.apk` and
  `adb shell pm grant com.gathin.tvhome android.permission.ACCESS_FINE_LOCATION` (Wi-Fi name).
  Disabled: Google TV launcher `launcherx` and the Google TV setup app `setupwraith`.
  Gotcha: with `launcherx` off, Home went to setupwraith's invisible `RecoveryActivity` (HOME
  priority 1 beats third-party launchers at 0; `set-home-activity` and the HOME role didn't help, and
  the shell may not disable a single component of it), so the whole setup app is disabled. Re-enable
  `setupwraith` (and `launcherx`) before any re-setup; a factory reset re-enables everything anyway.
  Verified after reboot: boots to the new home; all 7 favourites open and Home returns to the new
  home from each; Inputs lists HDMI 1–3, AV, Live TV; Chromecast, Mi cast and the Assistant service
  run, and an injected voice key starts an Assistant session (no audio from a fake key, so the real
  remote mic still needs a manual check). RAM available after boot ~940–960 MB (baseline ~883 MB).

## Done (2026-10-19): phase 2b

Uninstalled VLC (org.videolan.vlc) and MX Player (com.mxtech.videoplayer.television) with `lists/phase2b.txt`. Home screen checked afterwards (`logs/home-2026-10-19.png`). No app now plays USB media files; undo with `python tv.py restore lists/phase2b.txt` or re-enable com.mitv.videoplayer.


## Done (2026-10-19): phase 5

- Info screensaver `com.gathin.tvhome/.ClockDream`: clock, date, Your City weather (Open-Meteo), next 3 events from "Holidays in Your Country". Selected with `settings put secure screensaver_components com.gathin.tvhome/.ClockDream`.
- Google screensaver `com.google.android.apps.tv.dreamx` disabled (`lists/phase5.txt`); verified after reboot (`logs/p5-after-reboot.png`). Available RAM after boot: 914 MB.
- Start the screensaver on demand: `adb shell service call dreams 1` (IDreamManager.dream). `tv.py shot` sends WAKEUP first, which ends the dream; capture with `adb exec-out screencap -p` instead.
- Fixes: all-day holidays that ended yesterday (UTC vs IST) are skipped; the clock block is pulled back on screen when weather/events make it taller.
- Favourites: Play Store added as the 8th tile (two full rows).


## Second device (2026-10-19): Xiaomi Mi Box 4

- 192.168.1.21:5555 (mDNS `adb-<serial>`): Xiaomi Mi Box 4 `MIBOX4` (device `oneday`), Android TV 9
  (SDK 28), 1080p, 2 GB RAM, stock Android TV home (`com.google.android.tvlauncher`). 90 packages (4 user),
  far leaner than the Mi TV: no Xiaomi TV+, Live TV, HDMI inputs, VLC/MX, Mi Music or TV Manager.
- Every command needs `TV_HOST=192.168.1.21:5555` (the default is the Mi TV). Backup in `backup/MIBOX4/`.
- Lists in `lists/mibox4/` (phases 1–5, same meaning as above). Changes from the Mi TV: Alphonso ad-tracking
  EULA and the bug report sender are off too; the silent app installer
  `android.autoinstalls.config.xioami.mibox3` stays on (user's choice); Play Movies was disabled from the
  factory; the screensaver replaced is `com.google.android.backdrop`.
- The same launcher APK works: missing favourites are skipped, Inputs shows "no inputs" (no HDMI on a box).
  Install: `adb install -r`, then `pm grant` ACCESS_FINE_LOCATION and READ_CALENDAR. Disabling `tvlauncher`
  was enough here; setupwraith's HOME activity doesn't resolve on Android 9, so it stays enabled.
- Start the screensaver on demand on Android 9: `am start -n com.android.systemui/.Somnambulator`
  (`service call dreams 1` needs WRITE_DREAM_STATE, which the shell lacks there).
- Verified after reboot: boots to the new home; Netflix, Prime Video, JioHotstar, YouTube, Jellyfin, Plex and
  Play Store open and Home returns from each; voice key starts an Assistant session; Chromecast, Mi cast and
  the remote service run; ClockDream shows clock, date and weather (no calendar events: the holidays
  calendar isn't synced on this box). RAM available after boot: 899 MB before, ~1110 MB after.
- `tv.py` now uninstalls with `pm uninstall --user 0` (APK kept) and `restore` tries `install-existing` first.
  `logs/actions.log` lines now include the host.

## Done (2026-10-19): favourites from the remote, phone apps listed (both TVs)

- Long press OK (or the Menu key) on a tile: Move left / Move right (favourites), Remove from favourites or
  Add to favourites, App info. Saved on the TV (`shared_prefs/home.xml`); `config.xml` `dock_apps` is only
  the starting order. Verified on the Mi Box (move, remove, re-add, focus follows the app).
- Apps you install that have only a phone launcher entry (sideloaded APKs) now get an icon tile and open
  normally. System apps' phone screens stay hidden. Neither TV has such an app yet, so this path is untested
  on a device.
- Code: `Favourites.java` (saved list), `TileMenu.java`, `GlassMenu.java` (shared glass dropdown), Inputs
  menu moved to `InputsPopup.java` (MainActivity was over 500 lines).
