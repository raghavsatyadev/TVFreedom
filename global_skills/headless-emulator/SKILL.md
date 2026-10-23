---
name: headless-emulator
description: Run Android emulators with no window and drive them from a browser tab or adb, to save RAM and CPU. Use whenever a task needs an emulator (testing an app, a TV launcher, screenshots) - start it with emu up instead of the emulator window, look with emu shot or emu web, and stop it with emu down (snapshot saved, next start ~2 s). emu idle pauses and stops ones nobody uses.
---

# Headless emulators

`emu` (in `~/.headless-emu/`, Windows `%USERPROFILE%\.headless-emu\`; run it with bash) needs the SDK's
`emulator` and `adb` on PATH and Node for `emu web`.

```bash
emu up Pixel_10_Pro           # headless start, or resume one already running; prints the serial
emu ls                        # serial, AVD, running/paused, idle minutes
emu shot emulator-5554 a.png  # screenshot to look at (Read the PNG)
emu adb emulator-5554 shell input keyevent 20   # any adb command; resumes a paused one first
emu web emulator-5554 8790    # browser view: click = tap (TV: focus moves to the item, then Enter), drag = swipe, arrows/Enter = D-pad, Esc = back
emu down emulator-5554        # stop; quick boot saves the state
emu idle 5 30                 # (background) pause after 5 idle minutes, stop after 30
```

## Rules

- **Start with `emu up`, never the emulator window.** No window, no audio, no boot animation.
- **Go through `emu`** (`emu adb`, `emu shot`, `emu web`): each call marks the emulator as used, so `emu idle`
  does not pause it under you, and a paused one is resumed first. Plain `adb` on a paused emulator hangs.
- **To show the user the screen**, run `emu web <serial>` in the background and open
  `http://127.0.0.1:8790` in the built-in browser. While the page is open the emulator counts as used.
  One viewer serves one emulator: after that emulator stops, the page shows "No picture" with the reason. Then
  restart the viewer on the running serial (`emu ls`). On Windows, stopping the background shell can leave its
  `node` holding the port: find it with `netstat -ano | grep :8790` and end it before starting a new one.
- **Stop it when done** (`emu down`) unless the user still uses it. A paused emulator still holds its RAM;
  only a stopped one gives it back.
- Use one GPU mode per AVD. A snapshot saved in another mode does not load, and that start is a cold boot
  (~17 s against ~2 s). `emu` uses `swiftshader_indirect`; set `EMU_GPU=host` for every start if you want speed.

## Measured (Windows PC, Google TV API 34 AVD)

| State | CPU | RAM |
| --- | --- | --- |
| Running headless, idle | ~9% of one core | ~2.8 GB |
| Paused (`emu pause`) | ~0.6% of one core | ~2.8 GB (kept) |
| Stopped (`emu down`) | 0 | 0; `emu up` restores from snapshot in ~2 s |

`emu pause` is the emulator console's `avd stop` (`emu resume` is `avd start`).
