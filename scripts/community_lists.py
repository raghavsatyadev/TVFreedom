"""Build lists/community.lst: the Clean Your TV community lists, one block per TV model.

Sources (both MIT, see THIRD-PARTY-NOTICES):
  - Gathin23/cleanyourtv examples/<folder>/debloat.txt at a pinned commit (the package lists)
  - udhaykumarbala/cleanyourtv-app profile-meta.json (match strings, keep lists, a category per group)

Only folders with curated metadata are written. A group title the metadata does not name fails the build.

Run: python scripts/community_lists.py [--meta path/to/profile-meta.json]
"""

import argparse
import json
import re
import sys
import urllib.request
from pathlib import Path

UPSTREAM = "Gathin23/cleanyourtv"
APP = "udhaykumarbala/cleanyourtv-app"
UPSTREAM_COMMIT = "9071ec8d2f41826778f6b4c7f5f42dadc22f5b22"
APP_COMMIT = "476bd4bf2a2635fac14aa41057b9f682e9134cb3"
META = "core/spec/src/main/resources/tv/cleanyour/core/spec/profile-meta.json"
OUT = Path(__file__).resolve().parent.parent / "lists" / "community.lst"

PACKAGE_LINE = re.compile(r"^\s*([A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z0-9_]+)+)\s*(?:#\s?(.*))?$")
DECORATION = re.compile(r"^[-=*\s]+|[-=*\s]+$")
# Never part of a clean-up, whatever a list says: home screens and the remote's pairing setup.
NEVER = {
    "com.google.android.tvlauncher", "com.google.android.leanbacklauncher", "com.google.android.apps.tv.launcherx",
    "com.amazon.tv.launcher", "com.google.android.tungsten.setupwraith",
}
CATEGORIES = {"ads", "tracking", "preloads", "brand", "demo", "other", "streaming", "voice", "casting",
              "screensaver", "accessibility", "updates", "home"}


def fetch(repo, sha, path):
    url = f"https://raw.githubusercontent.com/{repo}/{sha}/{path}"
    with urllib.request.urlopen(url, timeout=60) as r:
        return r.read().decode("utf-8")


def comment(line):
    return line.lstrip().removeprefix("#").strip()


def title(block):
    for line in block:
        t = DECORATION.sub("", comment(line))
        if t:
            return t
    return ""


def scan(text):
    """Groups of (title, [(pkg, note)]), the same way the app's DebloatTxt.scan reads them."""
    groups, block, in_pkgs = [], [], False
    for raw in text.split("\n"):
        line = raw.rstrip("\r")
        if not line.strip():
            block, in_pkgs = [], False
            continue
        if line.lstrip().startswith("#"):
            if in_pkgs and line[0] in " \t":
                pkg, note = groups[-1][1][-1]
                more = comment(line)
                if more:
                    groups[-1][1][-1] = (pkg, f"{note} {more}".strip())
                continue
            if in_pkgs:
                in_pkgs, block = False, []
            block.append(line)
            continue
        m = PACKAGE_LINE.match(line)
        if not m:
            raise ValueError(f"neither a package nor a comment: {line!r}")
        if not in_pkgs:
            if block or not groups:
                groups.append((title(block), []))
            block, in_pkgs = [], True
        groups[-1][1].append((m.group(1), (m.group(2) or "").strip()))
    return groups


def one_line(s, n=70):
    return " ".join(s.replace("#", " ").replace("|", " ").split())[:n]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--meta", help="a local profile-meta.json instead of the pinned one")
    args = ap.parse_args()
    meta = json.loads(Path(args.meta).read_text("utf-8") if args.meta else fetch(APP, APP_COMMIT, META))
    sha = UPSTREAM_COMMIT
    if not sha.startswith(meta["upstreamCommit"]):
        raise SystemExit(f"profile-meta.json was made for {meta['upstreamCommit']}, not {sha}")
    problems, out = [], []
    for folder, m in sorted(meta["profiles"].items()):
        groups = scan(fetch(UPSTREAM, sha, f"examples/{folder}/debloat.txt"))
        titles = {t for t, _ in groups}
        problems += [f"{folder}: group not in the metadata: {t}" for t in titles - set(m["groups"])]
        problems += [f"{folder}: metadata group not upstream: {t}" for t in set(m["groups"]) - titles]
        mt = m["match"]
        keep = sorted(set(m.get("neverDisable", [])) | set(m.get("stockLaunchers", [])) | NEVER)
        out.append("@ " + "|".join([folder, mt.get("brand") or "", ",".join(mt.get("models", [])),
                                     ",".join(mt.get("devices", [])), str(mt.get("sdkMin") or ""),
                                     str(mt.get("sdkMax") or ""), mt.get("fingerprintPrefix") or ""]))
        out.append("! " + " ".join(keep))
        seen = set()
        for t, entries in groups:
            g = m["groups"].get(t)
            for pkg, note in entries:
                if pkg in seen or pkg in NEVER:
                    continue
                seen.add(pkg)
                c = m.get("packages", {}).get(pkg) or g
                if c is None or c["category"] not in CATEGORIES:
                    problems.append(f"{folder}: no category for {pkg}")
                    continue
                row = f"{c['category']}{'?' if c.get('ask') else ''} {pkg}"
                note = one_line(note)
                out.append(f"{row:<56} # {note}" if note else row)
    if problems:
        print("\n".join(problems), file=sys.stderr)
        raise SystemExit(f"{len(problems)} problem(s); nothing written")
    head = [
        "# Clean Your TV community lists, one block per TV model. Generated by scripts/community_lists.py; do not edit.",
        f"# Lists: github.com/{UPSTREAM} @ {sha} (MIT). Metadata: github.com/{APP} @ {APP_COMMIT} (MIT).",
        "# @ id|brand|models|devices|sdkMin|sdkMax|fingerprintPrefix   ! never touch   <category>[?] pkg  (? = ask first)",
    ]
    OUT.write_text("\n".join(head + out) + "\n", "utf-8", newline="\n")
    print(f"{OUT.name}: {len(meta['profiles'])} models, {sum(1 for l in out if not l.startswith(('@', '!')))} rows")


if __name__ == "__main__":
    main()
