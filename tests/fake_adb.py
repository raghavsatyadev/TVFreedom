"""A fake `adb` for offline tests: one Mi TV built from data/packages-*.txt (the pre-debloat snapshot).

State lives in $FAKE_ADB_DIR/state.json; every write (pm disable/uninstall/enable, install-existing,
reboot) is appended to $FAKE_ADB_DIR/writes.log so tests can assert exactly what reached the "TV".
"""
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DIR = os.environ["FAKE_ADB_DIR"]
STATE = os.path.join(DIR, "state.json")
HOST = "192.168.1.20:5555"
SYSTEM_UIDS = {"android": 1000, "com.android.systemui": 10050, "com.android.settings": 1000,
               "mitv.service": 1000, "com.xiaomi.mitv.remotecontroller.service": 1000}


def load():
    if os.path.exists(STATE):
        return json.load(open(STATE))
    text = open(os.path.join(ROOT, "data", "packages-all.txt"), encoding="utf-8").read()
    paths = dict((m[1], m[0]) for m in re.findall(r"^package:(.+)=([^=\s]+)\r?$", text, re.M))
    user = [l.strip() for l in open(os.path.join(ROOT, "data", "packages-user.txt")) if l.strip()]
    return {"paths": paths, "user": user, "disabled": [], "removed": [], "auth": True}


def save(st):
    json.dump(st, open(STATE, "w"))


def write(line):
    with open(os.path.join(DIR, "writes.log"), "a") as f:
        f.write(line + "\n")


def listing(st, flag):
    present = [p for p in sorted(st["paths"]) if p not in st["removed"]]
    if flag == "-d":
        present = [p for p in present if p in st["disabled"]]
    elif flag == "-e":
        present = [p for p in present if p not in st["disabled"]]
    elif flag == "-3":
        present = [p for p in present if p in st["user"]]
    if flag == "-f":
        return "\n".join(f"package:{st['paths'][p]}={p}" for p in present)
    if flag == "-U":
        return "\n".join(f"package:{p} uid:{SYSTEM_UIDS.get(p, 10100)}" for p in present)
    return "\n".join(f"package:{p}" for p in present)


def shell(st, cmd):
    a = cmd.split()
    if cmd.startswith("pm list packages"):
        return listing(st, a[3] if len(a) > 3 else "")
    if cmd.startswith("pm has-feature android.software.leanback"):
        return "true"
    if cmd.startswith("getprop"):
        props = {"ro.product.model": "MiTV-MOOR0", "ro.product.device": "venom", "ro.product.brand": "Xiaomi",
                 "ro.build.version.release": "11", "ro.build.version.sdk": "30", "ro.serialno": "FAKE123",
                 "sys.boot_completed": "1", "ro.build.characteristics": "tv"}
        return props.get(a[1], "") if len(a) > 1 else "\n".join(f"[{k}]: [{v}]" for k, v in props.items())
    if "category.HOME" in cmd:
        home = "com.google.android.apps.tv.launcherx/.home.HomeActivity"
        return f"priority=0 preferredOrder=0\n{home}" if "resolve" in cmd else \
            f"{home}\ncom.google.android.tungsten.setupwraith/.RecoveryActivity"
    if cmd == "dumpsys window":
        return "  mCurrentFocus=Window{abc u0 com.google.android.apps.tv.launcherx/.home.HomeActivity}"
    if cmd.startswith("settings get secure default_input_method"):
        return "com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME"
    if a[:2] == ["pm", "disable-user"]:
        st["disabled"].append(a[-1]); write(cmd)
        return f"Package {a[-1]} new state: disabled-user"
    if a[:2] == ["pm", "uninstall"]:
        st["removed"].append(a[-1]); write(cmd)
        return "Success"
    if a[:2] == ["pm", "enable"]:
        st["disabled"] = [p for p in st["disabled"] if p != a[-1]]; write(cmd)
        return f"Package {a[-1]} new state: enabled"
    if cmd.startswith("cmd package install-existing"):
        st["removed"] = [p for p in st["removed"] if p != a[-1]]; write(cmd)
        return f"Package {a[-1]} installed for user: 0"
    if cmd == "cat /proc/meminfo":
        return "MemTotal: 1843200 kB\nMemFree: 55296 kB\nMemAvailable: 905216 kB"
    return ""


def main(argv):
    st = load()
    if argv[:1] == ["-s"]:
        argv = argv[2:]
    cmd = argv[0] if argv else ""
    out = ""
    if cmd == "connect":
        out = f"connected to {argv[1]}"
    elif cmd == "get-state":
        out = "device" if st["auth"] else "error: device unauthorized."
    elif cmd == "mdns":
        out = f"List of discovered mdns services\nadb-FAKE123\t_adb._tcp.\t{HOST}"
    elif cmd == "reboot":
        write("reboot")
    elif cmd == "shell":
        out = shell(st, " ".join(argv[1:]))
    elif cmd == "exec-out":
        sys.stdout.buffer.write(b"debug\x89PNG\r\n\x1a\nfake")
    save(st)
    print(out)


if __name__ == "__main__":
    main(sys.argv[1:])
