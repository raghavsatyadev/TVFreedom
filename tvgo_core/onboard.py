"""First-run onboarding: check the phone (host), the agent and the TV, and walk through what's still missing.

`python tvgo.py setup` runs it; `go` and `plan` run it on their own when there is no saved setup yet.
Each check prints OK or the exact thing to do. Settings live in ~/.tvgo.json (agent, last TV).
"""
import json
import os
import platform
import shutil
import sys

import tv

from . import adbio, agent, discover

CONFIG = os.path.join(os.path.expanduser("~"), ".tvgo.json")

INSTALL = {  # how to get each agent CLI on the phone (Termux) or a computer
    "claude": "npm i -g @anthropic-ai/claude-code, then run `claude` once to log in",
    "gemini": "npm i -g @google/gemini-cli, then run `gemini` once to log in",
    "codex": "npm i -g @openai/codex, then run `codex` once to log in",
    "ollama": "Termux: pkg install ollama; then `ollama serve &` and `ollama pull qwen3:4b`",
    "llama": "Termux: pkg install llama-cpp, download a .gguf model and set its path with --agent",
}

TV_STEPS = """On the TV (once). Menu names differ a little between brands; the usual paths are:

1. Turn on Developer options
   Google TV / most Android TVs: Settings > System > About > select "Android TV OS build" 7 times.
   Older Android TV, Sony, Nvidia Shield, Philips: Settings > Device Preferences > About > "Build" 7 times.
   Fire TV: Settings > My Fire TV > About > select the device name 7 times.
2. Turn on debugging over the network (Settings > System or Device Preferences > Developer options)
   Android 11 and newer: "Wireless debugging" ON, then "Pair device with pairing code".
     The TV shows an ip:port and a 6-digit code; this setup asks you for both.
   Android 9/10, Nvidia Shield, many Xiaomi/TCL TVs: "USB debugging" ON and "Network debugging"
     (or "ADB debugging over network") ON. The TV then listens on port 5555.
   Fire TV: "ADB debugging" ON.
3. Keep the phone and the TV on the same Wi-Fi. The TV's IP is under Settings > Network & Internet.
4. When the TV asks "Allow debugging?", tick "Always allow" and press OK.
"""


def load():
    try:
        with open(CONFIG, encoding="utf-8") as fh:
            return json.load(fh)
    except (OSError, ValueError):
        return {}


def save(cfg):
    with open(CONFIG, "w", encoding="utf-8") as fh:
        json.dump(cfg, fh, indent=1)


def where():
    """termux, linxr, or the OS name: decides which install hints to print."""
    if "com.termux" in os.environ.get("PREFIX", ""):
        return "termux"
    if discover.local_ip().startswith("10.0.2."):
        return "linxr"  # QEMU user-mode NAT (LinXR's Alpine VM)
    return platform.system().lower()


def _line(ok, text, fix=""):
    print(f"  [{'OK' if ok else '!!'}] {text}" + ("" if ok or not fix else f"\n       fix: {fix}"))
    return ok


def check_host():
    """Python, adb and the Wi-Fi. Returns True when the phone side can reach a TV."""
    host = where()
    print(f"Phone / host ({host}):")
    ok = _line(sys.version_info >= (3, 8), f"Python {platform.python_version()}",
               "Termux: pkg install python" if host == "termux" else "install Python 3.8+")
    adb_bin = shutil.which(tv.ADB[0]) or (tv.ADB[0] if os.path.exists(tv.ADB[0]) else "")
    hint = {"termux": "pkg install android-tools", "linxr": "apk add android-tools",
            "darwin": "brew install android-platform-tools"}.get(host, "install Android platform-tools")
    if _line(bool(adb_bin), f"adb {adb_bin or 'not found'}", hint):
        version = adbio.adb("version", timeout=15).splitlines()
        _line(bool(version), version[0] if version else "adb did not answer", hint)
    else:
        ok = False
    ip = discover.local_ip()
    ok &= _line(bool(ip), f"Wi-Fi address {ip or 'none'}", "join the same Wi-Fi as the TV")
    if host == "linxr":
        print("       note: LinXR's VM can't see mDNS. Give the TV's ip:port, or --subnet 192.168.1.0/24.")
    if host == "termux":
        print("       tip: run `termux-wake-lock` so a long run isn't paused when the screen turns off.")
    return ok


def check_agent(spec, test=True):
    """Is the agent CLI installed, and (test=True) does it answer? Returns True when usable."""
    cmd = agent.command(spec)
    name = spec if spec in agent.PRESETS else os.path.basename(tv.split_cmd(cmd)[0])
    print(f"Agent ({name}: {cmd}):")
    exe = shutil.which(tv.split_cmd(cmd)[0])
    if not _line(bool(exe), f"{tv.split_cmd(cmd)[0]} {'found' if exe else 'not on PATH'}",
                 INSTALL.get(name, "install it, or pick another with --agent")):
        return False
    if not test:
        return True
    print("       asking it one test question (can take a minute)...")
    out = agent.run(cmd, 'Reply with only the JSON array ["ok"] and nothing else.', timeout=300)
    return _line("ok" in out.lower(), "agent answers" if "ok" in out.lower() else
                 f"agent gave no answer: {out.strip()[:120] or 'empty'}",
                 "run it once by hand to see why (not logged in, usage limit, no model), or pick another agent")


def connect_tv(ask, subnet=None):
    """Guide the user until one TV is connected. Returns its identify() dict, or None."""
    print("\n" + TV_STEPS)
    while True:
        print("Looking for TVs on the Wi-Fi...")
        cands, pairing = discover.find(subnet)
        tvs = discover.identify_tvs(cands)
        if tvs:
            return tvs[0] if len(tvs) == 1 else _choose(ask, tvs)
        for addr in pairing:
            print(f"A TV is waiting to pair at {addr}.")
        typed = ask("No TV connected yet. Type the ip:port shown on the TV (Enter to search again,"
                    " q to stop): ").strip()
        if typed.lower() == "q":
            return None
        if not typed:
            continue
        if ":" not in typed:
            typed += ":5555"
        code = ask("Pairing code shown on the TV (Enter if there is none, port 5555 TVs): ").strip()
        if code:
            print(adbio.pair(typed, code))
            port = ask("Now the ip:port on the Wireless debugging screen itself (not the pairing one): ").strip()
            typed = port if ":" in port else typed
        if adbio.connect(typed) and adbio.is_tv(typed):
            return adbio.identify(typed)
        print("Not connected. Check the steps above and try again.")


def _choose(ask, tvs):
    for i, t in enumerate(tvs, 1):
        print(f"{i}. {t['brand']} {t['model']} at {t['host']}")
    choice = ask("Which TV? ")
    return tvs[int(choice) - 1] if choice.isdigit() and 1 <= int(choice) <= len(tvs) else None


def run(ask, spec=None, subnet=None, host=None):
    """The whole onboarding. Saves the agent and the TV to ~/.tvgo.json. Returns True when ready."""
    cfg = load()
    print("tvgo setup: three checks, then you're ready to press Go.\n")
    ready = check_host()
    spec = spec or os.environ.get("TVGO_AGENT") or cfg.get("agent") or ""
    if not spec:
        print("\nWhich AI decides what to remove? " + ", ".join(agent.PRESETS)
              + ", or any command that reads a prompt on stdin.")
        spec = ask("Agent [claude]: ").strip() or "claude"
    print()
    ready &= check_agent(spec)
    print()
    info = None
    if host:
        info = adbio.identify(host) if adbio.connect(host) else None
    else:
        info = connect_tv(ask, subnet)
    if info:
        print(f"TV: {info['brand']} {info['model']}, Android {info['android']} at {info['host']}")
        cfg.update(tv=info["host"], serial=info["serial"])
    ready &= bool(info)
    cfg["agent"] = spec
    save(cfg)
    print(f"\nSaved to {CONFIG}." + (" Ready: python tvgo.py go" if ready else
                                     " Not ready yet: fix the [!!] lines and run python tvgo.py setup again."))
    return ready
