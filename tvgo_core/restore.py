"""The reverse ("bloat") side: per phase from a list, or everything back to a saved backup.

Per phase reuses tv.restore (pm enable / cmd package install-existing). Everything diffs the TV
against a backup made by tvgo before its first change. A backup taken after an earlier debloat
can only undo what came after it; the repo's lists/ phases cover the earlier ones.
"""
import glob
import os
import re
import time

import tv

from . import adbio, known


def _pkgs(path, with_paths=False):
    if not os.path.exists(path):
        return set()
    text = open(path, encoding="utf-8").read()
    if with_paths:
        return set(re.findall(r"^package:.+=([^=\s]+)\r?$", text, re.M))
    return {l[8:].strip() for l in text.splitlines() if l.startswith("package:")}


def backups(serial_or_model):
    """Backup dirs for this TV, newest first."""
    hits = [d for d in glob.glob(os.path.join(tv.ROOT, "backup", "*", ""))
            if serial_or_model in os.path.basename(os.path.dirname(d))]
    return sorted(hits, reverse=True)


def diff_to_backup(host, bdir):
    """What 'restore everything' would do: (to_enable, to_reinstall)."""
    then_all = _pkgs(os.path.join(bdir, "packages-all.txt"), with_paths=True)
    then_off = _pkgs(os.path.join(bdir, "packages-disabled.txt"))
    now_all = adbio.packages(host)
    now_off = adbio.packages(host, "-d")
    enable = sorted((now_off - then_off) & then_all)
    reinstall = sorted(then_all - now_all)
    return enable, reinstall


def restore_all(host, bdir):
    enable, reinstall = diff_to_backup(host, bdir)
    adbio.use(host)
    for pkg in reinstall:
        out = adbio.sh(host, f"cmd package install-existing {pkg}")
        if "installed for user" not in out:
            out += f" -> reinstall from Play Store: market://details?id={pkg}"
        tv.log(f"[{host}] reinstall {pkg}: {out}")
    for pkg in enable:
        tv.log(f"[{host}] enable {pkg}: {adbio.sh(host, f'pm enable {pkg}')}")
    print("waiting 30 s so the TV saves the package state before any reboot")
    time.sleep(30)


def phase_lists(model):
    """Restorable list files for this model: its repo phases and every tvgo run (newest first)."""
    d = known.model_dir(model)
    runs = sorted(glob.glob(os.path.join(d, "ai-*", "")), reverse=True)
    files = known.phase_files(d)
    for r in runs:
        files += [os.path.join(r, f) for f in ("applied.txt",) if os.path.exists(os.path.join(r, f))]
        files += known.phase_files(r)
    return files


def restore_list(host, path):
    adbio.use(host)
    tv.restore(path)
    print("waiting 30 s so the TV saves the package state before any reboot")
    time.sleep(30)
