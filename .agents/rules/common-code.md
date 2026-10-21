---
trigger: always_on
description: Where TVFreedom app code goes: shared logic and UI in commonMain, Android APIs only in androidMain behind interfaces or expect/actual.
---

# RULE: COMMON CODE FIRST

The app is Compose Multiplatform with one target (Android). No desktop or iOS target.

- **`composeApp/src/commonMain`**: rules, flow, storage, the ViewModel and every screen. Use okio
  (`Path`, `FileSystem`), kotlinx-datetime, kotlinx-serialization, `kotlin.time` and Compose
  Multiplatform APIs only.
- **`composeApp/src/androidMain`**: libadb, Termux, Gemini Nano, the foreground service, location,
  `Context`. Reach it through the `AppPlatform` interface (`AndroidPlatform`) or `expect`/`actual`
  (`ui/PlatformUi.kt`).
- **`androidCMP`**: only the Android app shell (`MainActivity`, manifest, resources, signing).
- Never import `android.*`, `java.*`, `javax.*` or `androidx.core`/`androidx.activity` in commonMain.
  `scripts/check-common.sh` checks this; `scripts/ci-local.sh` runs it.
