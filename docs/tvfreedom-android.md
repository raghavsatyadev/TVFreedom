# TVFreedom (Android app): design note

The app is called TVFreedom (`io.github.raghavsatyadev.tvfreedom`).

A phone app that does what `tvfreedom.py` does, without Termux: find the TV, back it up, plan, confirm, apply,
reboot and check, restore. Same rules: the guard always wins, then the known lists, then the AI.
Phases 1-3 plan the same on the PC and in the app: `tvfreedom-android/tools/make_golden.py` writes what `tvfreedom.py`
decides and the app's PlannerTest must match it exactly. Phases 4-6 (home screen, screensaver, branding,
extras) are in the app only; both leave the TV's own launcher and screensaver to the app's home-screen setup.
Only `pm disable-user --user 0` and `pm uninstall --user 0` touch the TV.

Project: `tvfreedom-android/`, Compose Multiplatform with an Android target only: `composeApp` (shared code in
`commonMain`, Android parts in `androidMain`) and `androidCMP` (the app shell). See [cmp-migration.md](cmp-migration.md).

## Build targets

| Item | Choice | Why |
|---|---|---|
| minSdk | 26 | ML Kit GenAI needs 26. The phone (Samsung phone) is Android 17. |
| compile/targetSdk | 37 | Matches the installed SDK and the user's other apps. |
| AGP / Kotlin / Gradle | 9.3.1 / 2.4.0 (built-in Kotlin) / 9.8.0 | Already cached on this PC. |
| UI | Compose Multiplatform, Material 3, one Activity | No navigation library: a small screen enum in the ViewModel. |

## adb client: libadb-android, not dadb

| | libadb-android 3.1.1 (JitPack) | dadb 2.0.0 |
|---|---|---|
| Classic `ip:5555` with RSA auth ("Allow debugging?") | yes | yes |
| Android 11+ pairing code (SPAKE2) | yes | no |
| TLS connect (`_adb-tls-connect`) | yes (Conscrypt) | no |

So: `com.github.MuntashirAkon:libadb-android` plus `org.conscrypt:conscrypt-android` (TLS 1.3 to a remote
adbd) and `org.bouncycastle:bcpkix-jdk15to18` (self-signed client certificate). One RSA key and
certificate is made on first run and kept in the app's private files. Each TV gets its own
`AbsAdbConnectionManager`. Shell commands go through `openStream("shell:<cmd>")` and are read to the end.

## Discovery

- `NsdManager` browses `_adb-tls-connect._tcp`, `_adb._tcp` and `_adb-tls-pairing._tcp`, holding a Wi-Fi
  `MulticastLock` while it runs.
- A /24 scan of port 5555 (64 sockets at a time, 400 ms timeout) for TVs with classic network debugging.
- A typed `ip:port` always works.
- Identify: connect, `pm has-feature android.software.leanback` (or `ro.build.characteristics` has `tv`),
  then model, brand, device, Android version, serial, home activity and keyboard via `getprop`/`settings`.

## AI

| Option | Library | Notes |
|---|---|---|
| Gemini Nano, on the phone (default) | `com.google.mlkit:genai-prompt:1.0.0-beta4` | Free and offline. `checkStatus()`, `download()` if DOWNLOADABLE. Small context, so 12 packages per prompt and only the hints for those packages. |
| Claude (optional) | `com.anthropic:anthropic-java` | The user pastes an API key in Settings. Model `claude-opus-5-5`, effort low; one call per 60 packages. |
| None | none | Plan only from the guard and the lists; everything else is kept. |

The AI only classifies. It never sees a tool or the TV. Bad, missing or unknown answers mean keep.
Reasons are squashed to one line and stripped of `#` before they reach a list file.

## Plan order (same as tvfreedom.py)

1. Packages already disabled are skipped.
2. Guard: user keep list, HOME packages, the current keyboard, system uid (< 10000), `/apex/` and
   `/system/framework/` paths, then the name rules (core, network, input/remote, TV inputs, Cast, voice,
   OTA, launcher/setup, vendor HAL). A guard hit is kept even if a list removes it (shown as a conflict).
3. This model's own lists (bundled `lists/` assets plus earlier verified runs on this phone).
4. AI for the rest, with other models' lists as hints.

Actions are `disable` unless the user turns on "uninstall user apps" and the package is a user app.

## Flow and storage

All files sit in the app's private storage (`files/`), in the same layout as the repo:

- `backups/<date>-<model>-<serial>/packages-{all,disabled,user,system}.txt` before the first change.
- `runs/<model>/ai-<date>/phaseN.txt`, `plan.json`, `applied.txt`, `verified`.

Go: discover, identify, backup, plan, confirmation screen with phases (typed `GO`), apply one package at a
time with a log, wait 30 s, reboot, wait for `sys.boot_completed` (re-finding a wireless-debugging port
by serial through mDNS if it moved), press Home and check the focused window is the HOME package.

Restore: a run's `applied.txt`, any phase list for the model (`pm enable`, `cmd package install-existing`),
or everything back to a backup (diff of package lists). A typed `RESTORE` confirms.

## Not in v1

- A foreground service: the screen stays on during a run instead, so keep the app open.
- Learning from runs on other phones (lists are per phone, plus the bundled repo lists).
