---
trigger: always_on
description: Verify the code compiles (and the tests pass) before calling any task done.
---

# RULE: ANDROID & KMP BUILD VERIFICATION

Verify the code compiles before marking any task complete. The Gradle root is `GRADLE_DIR` from
`agent-kit.env` (`tvfreedom-android/`), so run Gradle from there.

| You edited | Run |
| --- | --- |
| `*.gradle.kts` / `libs.versions.toml` | `scripts/gradle-agent.sh help` |
| Kotlin sources | `bash scripts/ci-local.sh` (compile + commonMain purity + 500-line limit) |
| Shared logic (`composeApp/src/commonMain`) | also `scripts/gradle-agent.sh :composeApp:testAndroidHostTest` |
| PC tool (`tvfreedom.py`, `tvfreedom_core/`) | `python -m unittest tests.test_offline` |

On Windows PowerShell use `.\gradlew.bat` inside `tvfreedom-android\`.

If the build fails: read the error → fix that issue → retry. Do NOT guess imports.
