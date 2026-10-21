"""The decision maker: any agent CLI or local model that reads a prompt on stdin and prints an answer.

The agent only classifies. It gets no tools and never talks to the TV; the guard and the human
confirmation sit between its answer and any adb write. Bad or missing answers mean "keep".
"""
import json
import os
import re
import subprocess

import tv

# Presets: a prompt goes to stdin. Each runs the agent with tools off / read-only where the CLI allows it.
PRESETS = {
    # Claude Code: print mode, no tools, and no MCP servers, plugins, hooks or skills from the user's setup
    "claude": 'claude -p --tools "" --setting-sources "" --strict-mcp-config --disable-slash-commands',
    "gemini": "gemini -p ''",                               # Gemini CLI: stdin is appended to -p
    "codex": "codex exec --sandbox read-only -",            # Codex: read-only sandbox, prompt from stdin
    "ollama": "ollama run qwen3:4b",                        # local model (Termux: pkg install ollama)
    "llama": "llama-cli -m model.gguf -no-cnv -f /dev/stdin",  # llama.cpp; set the model path
}
PHASES = {1: "telemetry, ads, tracking, promos",
          2: "apps the user does not use (streaming apps, games, demos)",
          3: "vendor extras (galleries, players, tips, cleaners, store demo)"}

PROMPT = """You classify packages on an Android TV ({brand} {model}, Android {android}) for debloating.
For each package decide "remove" or "keep". When unsure, keep.
Never remove anything needed for boot, the system UI, input or the keyboard, Wi-Fi/network, the remote
and Bluetooth, Cast, voice/Assistant, firmware updates (OTA), the home screen/launcher or setup,
HDMI/Live TV inputs, Google Play services or the Play Store. A code guard re-checks this anyway.
Brands differ (Sony, TCL, Hisense, Philips, Xiaomi, Nvidia, Chromecast, set-top boxes): vendor services
for the panel, picture/sound, tuner, CEC, remote pairing or the vendor's settings must stay. Typical bloat on
any brand: ad/ACR tracking (Alphonso, Samba TV), telemetry/analytics, promo rows and "free channels",
store demo mode, preinstalled games and streaming apps the user did not ask to keep.
The user always keeps: {keep}.
Phases for removals: {phases}.

Decisions made on the user's other TVs (pkg: action, phase, note), use them as examples:
{examples}

Packages to classify, one JSON object per line (apk = install path; user_app = installed by the user
or the vendor as a normal app; hint = what another TV's list did with it):
{items}

Answer with ONLY a JSON array, one object per package, nothing else:
[{{"pkg": "<name>", "verdict": "remove" or "keep", "phase": 1, 2 or 3, "reason": "<= 8 words"}}]
"""


def command(name=None):
    spec = name or os.environ.get("TVGO_AGENT", "claude")
    return PRESETS.get(spec, spec)


def build_prompt(info, items, examples, keep):
    ex = "\n".join(f"{p}: {a}, phase {ph}, {c or '-'} [{m}]"
                   for p, (a, ph, c, m) in sorted(examples.items()))
    lines = "\n".join(json.dumps(i, separators=(",", ":")) for i in items)
    phases = "; ".join(f"{k} = {v}" for k, v in PHASES.items())
    return PROMPT.format(brand=info.get("brand", ""), model=info.get("model", ""),
                         android=info.get("android", ""), keep=", ".join(sorted(keep)) or "-",
                         phases=phases, examples=ex or "-", items=lines)


def run(cmd, prompt, timeout=900):
    try:
        r = subprocess.run(tv.split_cmd(cmd), input=prompt.encode("utf-8"), capture_output=True,
                           timeout=timeout)
    except (OSError, subprocess.TimeoutExpired) as e:
        print(f"agent failed: {e}")
        return ""
    if r.returncode:
        print(f"agent exited {r.returncode}: {r.stderr.decode('utf-8', 'replace')[:300]}")
    return r.stdout.decode("utf-8", "replace")


def parse(text, asked):
    """{pkg: (verdict, phase, reason)} for packages in `asked`; anything malformed is dropped (=keep)."""
    found = []
    m = re.search(r"\[.*\]", text, re.S)
    if m:
        try:
            found = json.loads(m.group(0))
        except ValueError:
            found = []
    if not found:  # small models: salvage one object at a time
        for obj in re.findall(r"\{[^{}]*\}", text):
            try:
                found.append(json.loads(obj))
            except ValueError:
                pass
    out = {}
    for d in found if isinstance(found, list) else []:
        if not isinstance(d, dict) or d.get("pkg") not in asked:
            continue
        verdict = str(d.get("verdict", "keep")).lower()
        if verdict not in ("remove", "keep"):
            continue
        try:
            phase = int(d.get("phase", 3))
        except (TypeError, ValueError):
            phase = 3
        out[d["pkg"]] = (verdict, phase if phase in PHASES else 3, " ".join(str(d.get("reason", "")).split())[:80])
    return out


def classify(info, items, examples, keep, cmd=None, batch=None):
    """Ask the agent about `items` in batches. Returns {pkg: (verdict, phase, reason)}."""
    cmd = command(cmd)
    batch = batch or int(os.environ.get("TVGO_BATCH", "60"))
    answers = {}
    for i in range(0, len(items), batch):
        chunk = items[i:i + batch]
        print(f"agent: {len(chunk)} packages ({i + len(chunk)}/{len(items)}) via {os.path.basename(tv.split_cmd(cmd)[0])}")
        text = run(cmd, build_prompt(info, chunk, examples, keep))
        answers.update(parse(text, {it["pkg"] for it in chunk}))
    return answers
