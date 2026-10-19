"""adb plumbing for the phone flow: connect (with the approval prompt), identify, read-only queries.

Writes to the TV go through tv.py (apply/restore), which this module points at the chosen host.
"""
import re
import subprocess
import time

import tv


def adb(*args, host=None, timeout=60):
    cmd = [*tv.ADB, *(["-s", host] if host else []), *args]
    try:
        r = subprocess.run(cmd, capture_output=True, timeout=timeout)
    except subprocess.TimeoutExpired:
        return ""
    return (r.stdout + r.stderr).decode("utf-8", "replace").replace("\r\n", "\n").strip()


def sh(host, cmd):
    return adb("shell", cmd, host=host)


def state(host):
    return adb("get-state", host=host)


def connect(host, wait_s=90, wake=True):
    """Connect; if the TV asks to allow debugging, tell the user and wait for them to accept."""
    told = False
    deadline = time.time() + wait_s
    while time.time() < deadline:
        adb("connect", host, timeout=20)
        st = state(host)
        if st == "device":
            if wake:
                sh(host, "input keyevent KEYCODE_WAKEUP")
            return True
        if "unauthorized" in st and not told:
            print(f"{host}: accept the 'Allow debugging?' prompt on the TV (tick 'Always allow').")
            told = True
        time.sleep(3)
    print(f"{host}: no adb connection ({st or 'no answer'}).")
    return False


def use(host):
    """Point tv.py (backup/apply/restore/shot) at this TV. Call right before using them."""
    tv.HOST = host


def pair(host_port, code):
    """Android 11+ wireless debugging: pair once with the 6-digit code shown on the TV."""
    return adb("pair", host_port, code, timeout=30)


def getprop(host, key):
    return sh(host, f"getprop {key}").strip()


def is_tv(host):
    out = sh(host, "pm has-feature android.software.leanback")
    if out.strip() == "true":
        return True
    return "tv" in getprop(host, "ro.build.characteristics")


def identify(host):
    return {
        "host": host,
        "model": getprop(host, "ro.product.model"),
        "device": getprop(host, "ro.product.device"),
        "brand": getprop(host, "ro.product.brand"),
        "android": getprop(host, "ro.build.version.release"),
        "sdk": getprop(host, "ro.build.version.sdk"),
        "serial": getprop(host, "ro.serialno"),
        "home": home_activity(host),
        "ime": sh(host, "settings get secure default_input_method").strip(),
    }


def home_activity(host):
    out = sh(host, "cmd package resolve-activity --brief -a android.intent.action.MAIN"
                   " -c android.intent.category.HOME")
    lines = [l.strip() for l in out.splitlines() if "/" in l]
    return lines[-1] if lines else ""


def home_packages(host):
    """Every package with a HOME activity (the guard never touches these)."""
    out = sh(host, "cmd package query-activities --brief -a android.intent.action.MAIN"
                   " -c android.intent.category.HOME")
    return {l.strip().split("/")[0] for l in out.splitlines() if "/" in l}


def packages(host, flag=""):
    """Package names for `pm list packages <flag>`; with -f returns {pkg: apk path}."""
    out = sh(host, f"pm list packages {flag}".strip())
    if flag == "-f":
        return dict((m[1], m[0]) for m in re.findall(r"^package:(.+)=([^=\s]+)\r?$", out, re.M))
    return {l[8:].strip() for l in out.splitlines() if l.startswith("package:")}


def uids(host):
    """{pkg: uid}. A uid under 10000 (system, phone, bluetooth, nfc ...) marks a core package on any brand."""
    out = sh(host, "pm list packages -U")
    return {m[0]: int(m[1]) for m in re.findall(r"^package:(\S+) uid:(\d+)", out, re.M)}


def focused_package(host):
    out = sh(host, "dumpsys window")
    m = re.search(r"mCurrentFocus=Window\{\S+ \S+ ([\w.]+)/", out) or \
        re.search(r"mFocusedApp=.*? ([\w.]+)/", out)
    return m.group(1) if m else ""
