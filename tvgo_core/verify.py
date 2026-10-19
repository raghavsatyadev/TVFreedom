"""Reboot (only after the 30 s save wait, which tv.apply already did) and check the TV reaches its home screen."""
import os
import time

import tv

from . import adbio, discover


def wait_boot(host, serial, timeout=240):
    """Wait for adb + sys.boot_completed. Returns the (possibly new) host, or '' on timeout."""
    deadline = time.time() + timeout
    time.sleep(20)
    while time.time() < deadline:
        if adbio.connect(host, wait_s=15) and adbio.getprop(host, "sys.boot_completed") == "1":
            return host
        if ":5555" not in host:  # wireless debugging: the port may have changed
            moved = discover.relocate(serial)
            if moved:
                host = moved
                continue
        time.sleep(10)
    return ""


def home_ok(host, shot_dir=None):
    """Press Home and check the focused window belongs to the HOME package."""
    adbio.sh(host, "input keyevent KEYCODE_WAKEUP")
    time.sleep(2)
    adbio.sh(host, "input keyevent KEYCODE_HOME")
    time.sleep(6)
    home = adbio.home_activity(host).split("/")[0]
    focus = adbio.focused_package(host)
    if shot_dir:
        adbio.use(host)
        tv.shot(os.path.join(shot_dir, "after-reboot.png"))
    print(f"home package {home or '?'}, focused {focus or '?'}")
    return bool(home) and focus == home


def reboot_and_check(host, serial, shot_dir=None):
    print("rebooting the TV")
    adbio.adb("reboot", host=host, timeout=20)
    host = wait_boot(host, serial)
    if not host:
        print("TV did not come back over adb within 4 min. Check it on screen.")
        return False, ""
    ok = home_ok(host, shot_dir)
    print("home screen OK" if ok else "home screen NOT reached")
    return ok, host
