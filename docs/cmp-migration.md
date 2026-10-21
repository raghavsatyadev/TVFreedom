# TVFreedom: Compose Multiplatform layout

The phone app in `tvgo-android/` follows the module layout of SCUS (SimpleCricketUmpireScorer).
`tvgo-android/` stays the Gradle root, so `secret.properties`, `local.properties` and the paths to
`lists/`, `scripts/` and `launcher/` do not move.

| Module | What it holds |
| --- | --- |
| `composeApp` | KMP library. `commonMain`: rules (`core`), TV command parsing (`tv`), the run flow (`flow`), storage (`store`), the ViewModel and every screen (`ui`). `androidMain`: the libadb transport, mDNS discovery, the foreground `RunService`, Gemini Nano and Claude classifiers, Termux, phone location, image import and the Android side of the platform interfaces. `androidHostTest`: the JVM unit tests and goldens. |
| `androidCMP` | The Android app: `TvGoApp`, `MainActivity`, manifest, icons, release signing, and the tasks that bundle `lists/`, `scripts/termux-setup.sh` and the launcher APK into assets. |

No `desktopCMP`: the app needs an adb transport on the device it runs on, and the PC side is
`tvgo.py`. Add a desktop target only with a JVM adb transport.

## Rules kept from the old `app` module

- applicationId `com.gathin.tvgo`, release key alias `tv_debloater` from `secret.properties`.
- Class names `com.gathin.tvgo.TvGoApp`, `com.gathin.tvgo.ui.MainActivity`,
  `com.gathin.tvgo.flow.RunService` (launcher icon and service survive the update).
- `filesDir/tvgo/{keys,backups,runs,shots}` and the SharedPreferences name (no re-pairing, Restore
  keeps its history).
- `commonMain` imports no `android.*` or `java.*` (checked by `scripts/check-common.sh`).

## Steps

1. Module move: all code to `composeApp/androidMain`, app shell to `androidCMP`; build, test, install.
2. Move to `commonMain` in order core → tv → flow → store → ViewModel → UI, behind small
   platform interfaces (`ui/Platform.kt`). Files: okio. Dates: kotlinx-datetime.
3. Agent kit from SCUS: `AGENTS.md`, `.agents/`, `.claude/settings.json`, `global_skills/`,
   `.githooks/`, `agent-kit.env`, `scripts/` local CI.
