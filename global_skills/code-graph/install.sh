#!/usr/bin/env bash
# Extra setup for the code-graph skill; global_skills/install.sh runs it after it copies SKILL.md.
#   graphify (PyPI graphifyy) as a uv tool, and ~/.code-graph/gq. Re-run to update.
set -eu
here="$(cd "$(dirname "$0")" && pwd)"
if command -v uv >/dev/null 2>&1; then
  uv tool install --upgrade graphifyy >/dev/null 2>&1 || uv tool install graphifyy
elif command -v pipx >/dev/null 2>&1; then
  pipx install graphifyy || pipx upgrade graphifyy
else
  echo "code-graph: install uv (winget install astral-sh.uv, or https://docs.astral.sh/uv) and re-run" >&2; exit 1
fi
mkdir -p "$HOME/.code-graph"
cp "$here/gq" "$HOME/.code-graph/"
chmod +x "$HOME/.code-graph/gq"
echo "code-graph: $(graphify --version 2>/dev/null || echo 'graphify not on PATH: run uv tool update-shell'), gq in ~/.code-graph"
