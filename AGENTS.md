# AGENTS.md

Shared instructions for every coding agent in this repo — Claude, Gemini, Codex, Cursor.
`CLAUDE.md` and `GEMINI.md` just point here.

**TVFreedom** debloats Android TVs over adb from a phone: it backs up the TV, removes bloat in
phases (1-3 debloat, 4 launcher and branding, 5 screensaver, 6 extras), checks the TV after a
reboot and can undo every change. Users are not technical: every screen says one short sentence
and offers one button.

| Part | Where |
| --- | --- |
| Phone app (Compose Multiplatform, Android only) | `tvgo-android/` — modules `composeApp`, `androidCMP` |
| TV launcher APK (bundled into the app) | `launcher/` |
| PC tool (phases 1-3, reads TV state) | `tvgo.py`, `tvgo_core/`, `tv.py`; tests in `tests/` |
| Package lists and known-bloat data | `lists/`, `data/` |
| Docs | `docs/`, `README.md` |

App module `:androidCMP` · applicationId `com.gathin.tvgo` · base branch `main` · Gradle root
`tvgo-android/`. Values also live in `agent-kit.env`.

## How to work

- Do only what the task asks: no unrequested features, tests, files, docs or refactors.
- Ask only when blocked, or before an action that needs approval (a change on a real TV, a
  download the task did not name).
- Before "done", run the check for the change (android-build.md); if none can run, say why.
- When done and checked, commit and push (branch-pr-policy.md), then stop. Report in five lines
  or fewer, in ASD-STE100 Simplified Technical English.
- Keep every source file at or under 500 lines; split by responsibility before it grows past that.
- Decision model: global skill `local-model`. Global skills live in `global_skills/` (no agent reads
  it directly); install them with `bash global_skills/install.sh`. Turn on the local decision model
  first (`bash ~/.local-model/lm-on`), then use `jgl`, `lm-ask`, `lm-rank` and `rg` as the skill
  says. Rule checks for `lm-diffcheck`: [.agents/diff-checks.txt](.agents/diff-checks.txt).
- Plan state: `.claude/PLAN.md` (git-ignored, kept by plan-keeper).

## Rules — `.agents/rules/`

| Rule | Read it when |
| --- | --- |
| [android-build.md](.agents/rules/android-build.md) | before calling any task done — the code must compile |
| [common-code.md](.agents/rules/common-code.md) | *always* — what goes in commonMain, androidMain and androidCMP |
| [branch-pr-policy.md](.agents/rules/branch-pr-policy.md) | *always* — commit and push finished work to `main` |
| [format.md](.agents/rules/format.md) | touching any Kotlin — ktfmt is off; keep the file's style |
| [artemis-mobile-testing.md](.agents/rules/artemis-mobile-testing.md) | *on demand* — using ARTEMIS MCP tools |
| [parallel-agents.md](.agents/rules/parallel-agents.md) | the user asks for agents in parallel worktrees |

## Skills — `.agents/skills/`

One folder per skill; each `SKILL.md` `description` says when to read it. Claude Code sees them
through the `.claude/skills/` links that `scripts/setup-env.*` create.

## Commands

New machine: run the setup doctor first — `scripts/setup-env.ps1` (Windows) or
`scripts/setup-env.sh` (macOS/Linux).

```bash
bash scripts/ci-local.sh                 # compile + commonMain purity + 500-line limit — before pushing
scripts/gradle-agent.sh <tasks>          # agents: Gradle (in tvgo-android/) with errors only; log in tmp/
cd tvgo-android && ./gradlew :androidCMP:assembleRelease      # signed APK (needs secret.properties)
cd tvgo-android && ./gradlew :composeApp:testAndroidHostTest  # the app's unit tests
python -m unittest tests.test_offline    # PC tool tests (fake adb, no TV needed)
adb -s <phone> install -r tvgo-android/androidCMP/build/outputs/apk/release/androidCMP-release.apk
```

Never use `installDebug`: install with `adb -s <phone> install -r`. The hooks in `.githooks/`
(pre-push runs `ci-local.sh`) work once `git config core.hooksPath .githooks` is set in the main
checkout; the owner sets that, agents do not.

## Secrets

`tvgo-android/secret.properties` holds the release keystore path, alias and passwords;
`local.properties` holds the SDK path. Never read, print or commit them; copy them with `cp` when a
worktree needs them. Never put a token in a remote URL or `.git/config`.

## Devices

Ask before any command that changes a real TV (the user's own TVs, not test devices). Reading
state (`pm list`, `dumpsys`, `screencap`) is fine. The phone may be locked: never enter its PIN.

## Housekeeping

`tmp/` (agent scratch), `memory/` (local data such as device serials), `.cache/` and every `.env`
are git-ignored. Never clean `tmp/` unless the user asks — see
[workspace-cleanup](.agents/skills/workspace-cleanup/SKILL.md).
