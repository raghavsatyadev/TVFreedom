# TVFreedom: Compose Multiplatform layout

The phone app in `tvfreedom-android/` follows the module layout of SCUS (SimpleCricketUmpireScorer).
`tvfreedom-android/` stays the Gradle root, so `secret.properties`, `local.properties` and the paths to
`lists/`, `scripts/` and `launcher/` do not move.

| Module | What it holds |
| --- | --- |
| `composeApp` | KMP library. `commonMain`: rules (`core`), TV command parsing (`tv`), the run flow (`flow`), storage (`store`), the ViewModel and every screen (`ui`). `androidMain`: the libadb transport, mDNS discovery, the foreground `RunService`, Gemini Nano and Claude classifiers, Termux, phone location, image import and the Android side of the platform interfaces. `androidHostTest`: the JVM unit tests and goldens. |
| `androidCMP` | The Android app: `TvFreedomApp`, `MainActivity`, manifest, icons, release signing, and the tasks that bundle `lists/`, `scripts/termux-setup.sh` and the launcher APK into assets. |

No `desktopCMP`: the app needs an adb transport on the device it runs on, and the PC side is
`tvfreedom.py`. Add a desktop target only with a JVM adb transport.

## Rules kept from the old `app` module (before the rename to TVFreedom)

- applicationId `io.github.raghavsatyadev.tvfreedom`, release key alias `tv_debloater` from `secret.properties`.
- Class names `io.github.raghavsatyadev.tvfreedom.TvFreedomApp`, `io.github.raghavsatyadev.tvfreedom.ui.MainActivity`,
  `io.github.raghavsatyadev.tvfreedom.flow.RunService` (launcher icon and service survive the update).
- `filesDir/tvfreedom/{keys,backups,runs,shots}` and the SharedPreferences name `tvfreedom`. The rename to
  `io.github.raghavsatyadev.tvfreedom` made a new app, so these start empty. Backup folders use the
  `-tvfreedom-` tag, the same as the PC tool's.
- `commonMain` imports no `android.*` or `java.*` (checked by `scripts/check-common.sh`).

## Steps (all done)

1. Module move: all code to `composeApp/androidMain`, app shell to `androidCMP`; build, test, install.
2. Move to `commonMain` in order core → tv → flow → store → ViewModel → UI, behind small
   platform interfaces (`ui/Platform.kt`). Files: okio. Dates: kotlinx-datetime.
3. Agent kit from SCUS: `AGENTS.md`, `.agents/`, `.claude/settings.json`, `global_skills/`,
   `.githooks/`, `agent-kit.env`, `scripts/` local CI. `GRADLE_DIR=tvfreedom-android` in `agent-kit.env`
   points the kit at this Gradle root; ktfmt is off (`FORMAT_CHECK=0`); `scripts/ci-local.sh` also runs
   `check-common.sh` and the 500-line limit.

## Build

```bash
cd tvfreedom-android
./gradlew :androidCMP:assembleRelease        # signed with the key in secret.properties
./gradlew :composeApp:testAndroidHostTest    # unit tests and goldens
```
