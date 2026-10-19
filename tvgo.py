#!/usr/bin/env python3
"""Press Go: debloat (or restore) an Android TV from the phone. An AI agent proposes, you confirm.

python tvgo.py setup             first-time checklist: phone, agent, TV settings, pairing (runs by itself once)
python tvgo.py go [ip:port]        discover -> backup -> plan -> confirm -> apply -> reboot -> verify
python tvgo.py plan [ip:port]      same up to the plan; changes nothing on the TV
python tvgo.py restore [ip:port]   undo: a tvgo run, a repo phase list, or everything back to a backup
python tvgo.py discover            list TVs on the Wi-Fi (mDNS + port 5555 scan)
python tvgo.py pair <ip:port> <code>   Android 11+ wireless debugging pairing (code shown on the TV)
python tvgo.py verify [ip:port]    reboot and check the TV reaches its home screen

Options: --agent claude|gemini|codex|ollama|llama|"<any command reading stdin>" (default $TVGO_AGENT
or claude), --subnet 192.168.1.0/24, --uninstall-user-apps (default disables everything; uninstall
of a user app may need the Play Store to undo). See docs/phone-go.md.
"""
import argparse
import os
import sys

import tv
from tvgo_core import adbio, discover, known, onboard, plan, restore, verify


def is_terminal():
    if not sys.stdin.isatty():
        return False
    if os.name == "nt":  # Windows calls NUL a tty; only a real console has a console mode
        import ctypes
        import msvcrt
        mode = ctypes.c_uint32()
        return bool(ctypes.windll.kernel32.GetConsoleMode(msvcrt.get_osfhandle(0), ctypes.byref(mode)))
    return True


def ask(prompt):
    """Human-only input: refuses when stdin isn't a terminal, so no script or agent can pipe a yes."""
    if not is_terminal():
        sys.exit("This step needs you at a terminal (stdin is not a TTY). Nothing was changed.")
    try:
        return input(prompt).strip()
    except EOFError:
        return ""


def pick_tv(args):
    info = _pick_tv(args)
    adbio.use(info["host"])  # tv.py must act on the chosen TV, not the last one discovery touched
    return info


def _saved_tv():
    """The TV from setup, if it still answers at the same address with the same serial."""
    cfg = onboard.load()
    host = cfg.get("tv")
    if host and adbio.connect(host, wait_s=10, wake=False) and adbio.getprop(host, "ro.serialno") == cfg.get("serial"):
        adbio.sh(host, "input keyevent KEYCODE_WAKEUP")
        return adbio.identify(host)
    return None


def _pick_tv(args):
    if not args.host and not args.subnet:
        saved = _saved_tv()
        if saved:
            return saved
    if args.host:
        if not adbio.connect(args.host):
            sys.exit("Could not connect. For wireless debugging: python tvgo.py pair <ip:port> <code> first.")
        return adbio.identify(args.host)
    cands, pairing = discover.find(args.subnet)
    if pairing:
        print("Waiting to pair (TV shows a code): " + ", ".join(pairing)
              + "\n  python tvgo.py pair <ip:port> <code>")
    tvs = discover.identify_tvs(cands)
    if not tvs:
        sys.exit("No TV found. Turn on adb/network debugging on the TV, or pass its ip:port.")
    if len(tvs) == 1:
        return tvs[0]
    for i, t in enumerate(tvs, 1):
        print(f"{i}. {t['brand']} {t['model']} at {t['host']}")
    choice = ask("Which TV? ")
    if not choice.isdigit() or not 1 <= int(choice) <= len(tvs):
        sys.exit("No TV chosen.")
    return tvs[int(choice) - 1]


def make_plan(args):
    if not onboard.load().get("ready"):
        print("Setup isn't finished yet: running it now.\n")
        if not onboard.run(ask, args.agent, args.subnet, args.host):
            sys.exit("Setup isn't finished. Nothing was changed.")
    info = pick_tv(args)
    print(f"\n{info['brand']} {info['model']} ({info['device']}), Android {info['android']},"
          f" home {info['home'] or '?'}")
    bdir = tv.backup(f"tvgo-{known.slug(info['model'])}-{info['serial'] or 'noserial'}")
    p = plan.build(info, plan.facts(info["host"]), args.agent, args.uninstall_user_apps)
    pdir = plan.save(p)
    plan.show(p)
    print(f"\nplan saved: {os.path.relpath(pdir, tv.ROOT)}  backup: {os.path.relpath(bdir, tv.ROOT)}")
    return info, p, pdir


def cmd_plan(args):
    make_plan(args)


def cmd_go(args):
    info, p, pdir = make_plan(args)
    if not p["remove"]:
        print("Nothing to remove.")
        return
    phases = sorted({r["phase"] for r in p["remove"]})
    pick = ask(f"\nApply which phases? ({' '.join(map(str, phases))} / all / none) ").lower()
    chosen = phases if pick == "all" else [int(x) for x in pick.replace(",", " ").split()
                                          if x.isdigit() and int(x) in phases]
    rows = [r for r in p["remove"] if r["phase"] in chosen]
    if not rows:
        print("Nothing applied.")
        return
    print(f"\nThis will change {len(rows)} packages on {info['model']} at {info['host']}"
          f" (disable-user / uninstall --user 0 only), wait 30 s, reboot and check the home screen.")
    if ask("Type GO to apply: ") != "GO":
        print("Cancelled. Nothing was changed.")
        return
    applied = os.path.join(pdir, "applied.txt")
    with open(applied, "w", encoding="utf-8") as fh:
        fh.write(f"# Applied by tvgo.py to {info['model']} {info['serial']}: phases {chosen}\n")
        fh.writelines(f"{r['action']} {r['pkg']}  # {r['reason']}\n" for r in rows)
    adbio.use(info["host"])
    tv.apply(applied)  # one adb call per package, logged, then the 30 s save wait
    ok, host = verify.reboot_and_check(info["host"], info["serial"], pdir)
    known.mark_verified(pdir, ok)
    if ok:
        print(f"Done. Undo any time: python tvgo.py restore {host}")
        return
    if host and ask("Home screen check failed. Type RESTORE to undo this run: ") == "RESTORE":
        restore.restore_list(host, applied)
        verify.reboot_and_check(host, info["serial"], pdir)


def cmd_restore(args):
    info = pick_tv(args)
    host = info["host"]
    options = [("list", f) for f in restore.phase_lists(info["model"])]
    options += [("backup", b) for b in restore.backups(info["serial"] or known.slug(info["model"]))]
    if not options:
        sys.exit("No lists or backups for this TV in the repo.")
    print(f"\nRestore on {info['model']} at {host}:")
    for i, (kind, path) in enumerate(options, 1):
        label = "everything back to backup" if kind == "backup" else "list"
        print(f"{i}. {label}: {os.path.relpath(path, tv.ROOT)}")
    choice = ask("Which? ")
    if not choice.isdigit() or not 1 <= int(choice) <= len(options):
        sys.exit("Nothing chosen.")
    kind, path = options[int(choice) - 1]
    if kind == "backup":
        enable, reinstall = restore.diff_to_backup(host, path)
        print(f"enable {len(enable)}: {' '.join(enable) or '-'}\n"
              f"reinstall {len(reinstall)}: {' '.join(reinstall) or '-'}\n"
              "(only undoes changes made after this backup)")
        if not enable and not reinstall:
            return
    else:
        print(open(path, encoding="utf-8").read())
    if ask("Type RESTORE to apply: ") != "RESTORE":
        print("Cancelled. Nothing was changed.")
        return
    restore.restore_all(host, path, info["model"]) if kind == "backup" else restore.restore_list(host, path)
    if ask("Reboot and check the home screen? (y/N) ").lower() == "y":
        verify.reboot_and_check(host, info["serial"])


def cmd_setup(args):
    if not onboard.run(ask, args.agent, args.subnet, args.host):
        sys.exit(1)


def cmd_discover(args):
    cands, pairing = discover.find(args.subnet)
    for addr, why in cands:
        print(f"{addr}  {why}")
    for addr in pairing:
        print(f"{addr}  pairing (python tvgo.py pair {addr} <code>)")
    if not cands and not pairing:
        print("nothing found")


def cmd_pair(args):
    if not args.code:
        sys.exit("usage: python tvgo.py pair <ip:port> <code>")
    print(adbio.pair(args.host, args.code))


def cmd_verify(args):
    info = pick_tv(args)
    if ask(f"Reboot {info['model']} now? (y/N) ").lower() == "y":
        verify.reboot_and_check(info["host"], info["serial"])


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("command", choices=["setup", "go", "plan", "restore", "discover", "pair", "verify"])
    ap.add_argument("host", nargs="?", help="ip:port of the TV (skips discovery)")
    ap.add_argument("code", nargs="?", help="pairing code (pair only)")
    ap.add_argument("--agent", help="agent preset or command (default $TVGO_AGENT or claude)")
    ap.add_argument("--subnet", help="scan this subnet instead of the phone's /24")
    ap.add_argument("--uninstall-user-apps", action="store_true",
                    help="uninstall --user 0 user apps the plan removes instead of disabling them")
    args = ap.parse_args()
    args.agent = args.agent or os.environ.get("TVGO_AGENT") or onboard.load().get("agent")
    globals()[f"cmd_{args.command}"](args)


if __name__ == "__main__":
    main()
