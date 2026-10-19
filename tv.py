#!/usr/bin/env python3
"""Back up, debloat and restore the Mi TV over adb. One adb call per package (chained pm stops early).

python tv.py backup <name>     snapshot packages, props, settings, home, IME, memory, screenshot
python tv.py apply <list>      run a list (lines: "disable <pkg>" or "uninstall <pkg>", # comments)
python tv.py restore <list>    undo a list: enable disabled packages, reinstall-existing uninstalled ones
python tv.py mem               free/available RAM in MB
python tv.py shot <file.png>   screenshot from the TV

TV_HOST picks the TV (default: the Mi TV at 192.168.1.20:5555; Mi Box 4: 192.168.1.21:5555).
"""
import datetime
import os
import re
import shlex
import subprocess
import sys
import time

HOST = os.environ.get("TV_HOST", "192.168.1.20:5555")
ADB = shlex.split(os.environ.get("ADB", "adb"))  # override for tests (fake adb)
ROOT = os.path.dirname(os.path.abspath(__file__))


def adb(*args, binary=False):
    r = subprocess.run([*ADB, "-s", HOST, *args], capture_output=True, timeout=120)
    return r.stdout if binary else (r.stdout + r.stderr).decode("utf-8", "replace").strip()


def ensure():
    """The TV drops adb in standby: reconnect, wake it, and stop if it is still unreachable."""
    for _ in range(3):
        subprocess.run([*ADB, "disconnect", HOST], capture_output=True)
        subprocess.run([*ADB, "connect", HOST], capture_output=True, timeout=30)
        if adb("get-state") == "device":
            sh("input keyevent KEYCODE_WAKEUP")
            time.sleep(3)
            return
        time.sleep(5)
    sys.exit(f"TV at {HOST} is not reachable over adb (is it on?)")


def sh(cmd):
    return adb("shell", cmd)


def mem():
    out = sh("cat /proc/meminfo")
    info = dict(re.findall(r"^(\w+):\s+(\d+) kB", out, re.M))
    if "MemTotal" not in info:
        return f"meminfo unavailable: {out[:200]}"
    mb = lambda k: int(info[k]) // 1024
    return f"total {mb('MemTotal')} MB, free {mb('MemFree')} MB, available {mb('MemAvailable')} MB"


def read_list(path):
    for line in open(path, encoding="utf-8"):
        line = line.split("#", 1)[0].split()
        if len(line) == 2 and line[0] in ("disable", "uninstall"):
            yield line[0], line[1]


def log(msg):
    os.makedirs(os.path.join(ROOT, "logs"), exist_ok=True)
    stamp = datetime.datetime.now().isoformat(timespec="seconds")
    with open(os.path.join(ROOT, "logs", "actions.log"), "a", encoding="utf-8") as f:
        f.write(f"{stamp} {msg}\n")
    print(msg)


def backup(name):
    d = os.path.join(ROOT, "backup", f"{datetime.date.today()}-{name}")
    os.makedirs(d, exist_ok=True)
    files = {
        "packages-all.txt": "pm list packages -f",
        "packages-disabled.txt": "pm list packages -d",
        "packages-enabled.txt": "pm list packages -e",
        "packages-user.txt": "pm list packages -3",
        "getprop.txt": "getprop",
        "settings-global.txt": "settings list global",
        "settings-secure.txt": "settings list secure",
        "settings-system.txt": "settings list system",
        "home.txt": "cmd package resolve-activity --brief -a android.intent.action.MAIN"
                    " -c android.intent.category.HOME",
        "meminfo.txt": "dumpsys meminfo",
    }
    for f, cmd in files.items():
        open(os.path.join(d, f), "w", encoding="utf-8").write(sh(cmd) + "\n")
    open(os.path.join(d, "mem.txt"), "w").write(mem() + "\n")
    shot(os.path.join(d, "screen.png"))
    print(f"backup -> {d}\n{mem()}")
    return d


def apply(path):
    for action, pkg in read_list(path):
        out = sh(f"pm disable-user --user 0 {pkg}") if action == "disable" else sh(f"pm uninstall --user 0 {pkg}")  # keeps the APK, restorable
        log(f"{action} {pkg}: {out}")
    # Android saves enabled/disabled state lazily; a reboot within seconds loses all of it.
    print("waiting 30 s so the TV saves the new package state before any reboot")
    time.sleep(30)


def restore(path):
    for action, pkg in reversed(list(read_list(path))):
        if action == "disable":
            log(f"enable {pkg}: {sh(f'pm enable {pkg}')}")
        else:
            out = sh(f"cmd package install-existing {pkg}")
            if "installed for user" not in out:  # fully removed (TV1 phase 2): reinstall from Play Store
                out += f" -> reinstall from Play Store: market://details?id={pkg}"
            log(f"reinstall {pkg}: {out}")


def shot(path):
    data = adb("exec-out", "screencap", "-p", binary=True)
    start = data.find(bytes.fromhex("89504e47"))  # vendor screencap prints debug text before the PNG
    open(path, "wb").write(data[start:] if start >= 0 else data)


if __name__ == "__main__":
    cmd, *rest = sys.argv[1:] or ["-h"]
    if cmd in ("backup", "apply", "restore", "mem", "shot"):
        ensure()
    {"backup": backup, "apply": apply, "restore": restore, "shot": shot,
     "mem": lambda: print(mem())}.get(cmd, lambda *a: print(__doc__))(*rest)
