#!/usr/bin/env bash
# Extra setup for the headless-emulator skill; global_skills/install.sh runs it after it copies SKILL.md.
#   ~/.headless-emu/emu and emu-web.mjs (the folder also keeps the idle stamps and emulator logs). Re-run to update.
set -eu
here="$(cd "$(dirname "$0")" && pwd)"
mkdir -p "$HOME/.headless-emu"
cp "$here/emu" "$here/emu-web.mjs" "$HOME/.headless-emu/"
chmod +x "$HOME/.headless-emu/emu"
for t in emulator adb node; do command -v "$t" >/dev/null 2>&1 || echo "headless-emulator: $t is not on PATH" >&2; done
echo "headless-emulator: emu in ~/.headless-emu"
