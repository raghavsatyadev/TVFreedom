# Press Go from the phone: design (2026-10-19)

Goal: debloat or restore any Android TV (any brand or model) from the Samsung phone. An AI agent of your
choice proposes the plan, you confirm it, and the script applies it over wireless adb.

## Host: Termux, with LinXR as a fallback

**LinXR** ([AI2TH/Linxr](https://github.com/AI2TH/Linxr), `com.ai2th.linxr`, not written by us) runs Alpine
Linux in a QEMU VM on the phone, with SSH on port 2222, Docker and python3. Two things in its source make it
a weak primary host:

- **Networking is SLIRP user-mode NAT** (`-netdev user,…,hostfwd=tcp::2222-:22` in `VmManager.kt`). Outbound
  `adb connect` and `adb pair` to the TV work. mDNS multicast doesn't reach the VM, and the VM sees
  `10.0.2.x`, so the /24 scan needs `--subnet 192.168.1.0/24`. Every TV has to be found by scan or typed in.
- **CPU is emulated** (`-accel tcg`): there's no KVM on the phone, so Python and adb run slowly, and a
  local model is far too slow. A cloud agent CLI (Claude Code, Codex, Gemini) inside it still works.

**Termux** (F-Droid) runs natively on the phone's Wi-Fi IP: `pkg install python android-tools git`. It also
runs agent CLIs (`npm i -g @anthropic-ai/claude-code`, or Codex/Gemini) and local models (`pkg install ollama`
or `llama-cpp`). It's the recommended host. None of this has been run on the Samsung yet,
including Claude Code under Termux. LinXR works with `--subnet` plus a typed `ip:port`.

A small Android app was skipped for v1. It would need its own adb client (pairing TLS and the adb protocol)
and its own model runtime, and it would duplicate `tv.py`. It's worth doing later only if a one-tap home
screen widget matters.

## How the phone talks adb to the TV

- **Older TVs** (Android 9/10, e.g. the Mi Box 4) use *Network debugging* on port 5555. The first
  `adb connect` shows "Allow debugging?" on the TV. TVFreedom sees `unauthorized`, tells you to accept it, and
  waits up to 90 s.
- **Android 11+** uses *Wireless debugging*. On the TV, choose "Pair with code". Then run
  `python tvfreedom.py pair <ip:pairport> <code>` once. The connect port is random (`_adb-tls-connect._tcp`) and
  can change after a reboot, so verify finds the TV again by serial number.
- **Discovery** (`tvfreedom_core/discover.py`) runs `adb mdns services` (`_adb._tcp`, `_adb-tls-connect`,
  `_adb-tls-pairing`), plus a threaded port-5555 scan of the phone's /24, plus any `ip:port` you type.
  Candidates are kept only if `pm has-feature android.software.leanback` is true (or
  `ro.build.characteristics` contains `tv`), so phones and tablets are skipped. The phone's own IP is
  excluded. **Unverified:** whether mDNS answers inside Termux on the Samsung (Android can drop multicast
  without a Wi-Fi multicast lock). The scan and a typed `ip:port` cover that case.

## Agent interface

The script drives the flow; the agent only classifies. Any command that reads a prompt on stdin and prints
an answer works. Pick it with `--agent` or `$TVFREEDOM_AGENT`:

| preset | command | notes |
|---|---|---|
| `claude` (default) | `claude -p --tools ""` | print mode, all tools off |
| `codex` | `codex exec --sandbox read-only -` | read-only sandbox |
| `gemini` | `gemini -p ''` | flags not checked on the phone yet |
| `ollama` | `ollama run qwen3:4b` | on-phone model; use `TVFREEDOM_BATCH=25` for small models |
| `llama` | `llama-cli -m model.gguf -no-cnv -f /dev/stdin` | set your model path |
| anything else | the literal command | e.g. `--agent "my-agent --json"` |

The prompt (`tvfreedom_core/agent.py`) holds the TV's brand, model and Android version, the keep rules and your
keep list. It also holds every decision from this repo's lists across all models as examples, and the
packages to decide as JSON lines (name, install path, user app or not, what another TV's list did with it).
The answer is a JSON array of `{pkg, verdict, phase, reason}`. The parser is strict: unknown packages, bad
verdicts or broken JSON are dropped, and **anything unanswered is kept**. Packages go out in batches of
`$TVFREEDOM_BATCH` (60).

## Making it work on any brand or model

Decisions are made in layers, and each layer can only keep more:

1. **Guard** (`tvfreedom_core/guard.py`, code that always wins). It needs no brand knowledge:
   - any package on a system uid (< 10000: `android.uid.system`, phone, Bluetooth, NFC), from
     `pm list packages -U`
   - every package with a HOME activity (`query-activities … HOME`)
   - the current keyboard
   - APKs in `/apex` or `/system/framework`
   - name patterns for boot/core, network, input/remote/Bluetooth, TV inputs/HDMI/CEC/tuner, Cast, voice,
     OTA, and launcher/setup

   On top of that, known vendor namespaces are kept: MediaTek/Amlogic/Realtek/MStar SoCs, Sony
   `com.sony.dtv.*`, TCL, Hisense, Philips/TP Vision `org.droidtv.*`, Nvidia Shield. Packages in
   `lists/keep.txt` (your apps, the custom launcher, calendar sync for ClockDream) are kept too.
2. **This model's known lists.** `lists/` for the Mi TV, `lists/<model-slug>/` for others (`lists/mibox4/`).
   This includes `ai-*/applied.txt` from earlier runs that passed the home-screen check (a run you restore
   is dropped again), so a model gets smarter each time.
3. **The agent**, for whatever is left. It sees every other model's decisions as examples.

Launchers are never touched in v1. That needs a working replacement home first, and has the
setupwraith/HOME-priority gotchas from the README. OTA is kept by default. When an earlier list disabled an
OTA package, the plan shows it under "kept by the safety guard".

## Flow (`python tvfreedom.py go`)

1. **Discover**, then pick a TV (asked only if there is more than one).
2. **Connect**, with the approval prompt handled. **Identify**: brand, model, device, Android, serial,
   home activity, keyboard.
3. **Back up** with `tv.backup` to `backup/<date>-tvgo-<model>-<serial>/`: packages (`-f`, `-d`, `-e`,
   `-3`), getprop, all settings, home, meminfo and a screenshot.
4. **Plan**: guard, then known lists, then agent. Saved as
   `lists/<model>/ai-<date-time>/phaseN.txt` (the normal list format) plus `plan.json` with every reason.
   Phases: 1 = telemetry/ads/tracking, 2 = unused apps, 3 = vendor extras.
5. **Confirm**: pick the phases, then type `GO`. Prompts read only from a real terminal (`isatty`, and on
   Windows a console check), so no script or agent can pipe a yes. There's no `--yes` flag.
6. **Apply**: the chosen rows go to `applied.txt`, then `tv.apply` runs it. Each package is one adb call,
   logged: `pm disable-user --user 0` by default, or `pm uninstall --user 0` for user apps with
   `--uninstall-user-apps` (undoing that may need the Play Store). Then it waits **30 s** so the TV saves
   the package state.
7. **Verify**: reboot, wait for `sys.boot_completed` (finding the TV again if its port moved), press Home,
   and check the focused window belongs to the HOME package. A screenshot is saved in the run folder. If
   the check fails, it offers `RESTORE` of this run.

`python tvfreedom.py plan` stops after step 4 and changes no packages. It wakes the chosen TV. During discovery
it also `adb connect`s every candidate (which can show an approval prompt there) and disconnects any that
turn out not to be TVs.

## Restore ("bloat")

`python tvfreedom.py restore` lists every option for this TV:

- **one list**: a repo phase (`lists/…/phaseN.txt`), a run's `applied.txt` or one of its phases. It uses
  `tv.restore`: `pm enable`, or `cmd package install-existing` and then the Play Store link.
- **everything back to a backup**: compares the TV now against a TVFreedom backup. It enables what was enabled
  then and is disabled now, and runs `install-existing` on what was installed then and is missing now. It
  shows the diff first.

Then you type `RESTORE`, it waits 30 s, and optionally reboots and verifies. Both TVs were debloated before
TVFreedom existed, so their first TVFreedom backup is already debloated. To undo those earlier phases, restore
`lists/phaseN.txt` (Mi TV) or `lists/mibox4/phaseN.txt`.

## Phone setup (Termux)

Install Termux from F-Droid or its GitHub releases (the Play Store build is outdated). Then, once:

```bash
pkg install git gh && gh auth login          # the repo is private
gh repo clone <owner>/TV_Debloater && cd TV_Debloater
sh scripts/termux-setup.sh                   # or: sh scripts/termux-setup.sh ollama (on-phone model)
claude                                       # once, to log in; then /exit
python tvfreedom.py setup
```

`scripts/termux-setup.sh` does the following, and is safe to run again:

1. Switches to Termux's main Cloudflare mirror. The auto-picked mirror ran at about 36 KB/s on the Samsung.
2. Runs `pkg upgrade` first. Freshly installed adb and node fail with "CANNOT LINK EXECUTABLE" on the
   base libraries that ship with the app.
3. Installs python, android-tools, git and nodejs.
4. Installs Claude Code. Termux's Node reports the platform as `android`, so `npm i -g` gets no native
   binary. The script fetches the `linux-arm64` build instead. A `claude` wrapper in `$PREFIX/bin` runs it
   with glibc-runner's loader. The wrapper calls the loader directly rather than `grun`, because `grun`
   passes `$@` unquoted, which drops the empty argument in `--tools ""`.

Tested on the Samsung phone (Android 17) on 2026-10-19:
- Termux 0.118.3, Python 3.14, adb 1.0.41, Node 26.
- Claude Code 2.1.303 starts.
- The 12 offline tests pass on the phone.

`python tvfreedom.py setup` is the onboarding. It also runs by itself the first time you run `go` or `plan`.

1. **Phone:** checks Python, adb and the Wi-Fi address. For each missing piece it prints the install
   command for Termux, LinXR, macOS or other hosts.
2. **Agent:** asks which agent to use, checks it's on PATH and sends it one test question. A missing
   login or a usage limit shows up here, not halfway through a plan.
3. **TV:** prints the settings to turn on, per brand family (Google TV, older Android TV, Sony, Shield,
   Philips, Xiaomi/TCL, Fire TV), then looks for the TV. If none is found, it asks for the ip:port and
   the pairing code shown on the TV and pairs.

It saves the agent and the TV (address and serial) to `~/.tvfreedom.json` (it still reads an old `~/.tvgo.json`). `go`, `plan` and `restore` then
use that TV directly when it still answers with the same serial. Otherwise they run discovery.

## Tests

`python -m unittest tests.test_offline` runs everything offline. It uses a fake adb (the Mi TV's
pre-debloat snapshot in `data/`) and a careless fake agent that says "remove" to everything. It covers:
discover, the guard and strict parsing, plan, apply, reboot and verify, restore-all and restore-list,
learning only verified runs, and the review fixes. No TV needed.

**Emulator run (2026-10-19), real adb and Claude:** used a Google TV Android 14 image
(`system-images;android-34;google-tv;x86`, AVD `tvgo_GoogleTV_34`).

1. `setup` passed all checks.
2. `go` planned 5 disables: Play Games, YouTube Music, print spooler, the two Google screensavers.
   The guard kept launcherx and tvlauncher even though earlier lists had disabled them.
3. It applied the 5, rebooted, and the home screen came back OK.
4. Restoring phase 2 alone brought YouTube Music back.
5. Restoring everything back to the backup re-enabled the other 4. After a reboot the home screen was OK
   and the emulator matched its starting state.

Not tested on the emulator: discovery (the emulator sits on localhost) and the pairing screen.
