#!/data/data/com.termux/files/usr/bin/sh
# One-time phone setup for TVFreedom in Termux (F-Droid or GitHub build). Safe to run again.
#   sh scripts/termux-setup.sh            tools + Claude Code
#   sh scripts/termux-setup.sh ollama     tools + a small on-phone model instead
#   sh termux-setup.sh app [component]    only Claude Code, for the TVFreedom phone app (it runs this for you);
#                                         opens [component] (the app's screen) again after the login
set -e

say() { printf '\n== %s\n' "$*"; }

say "Package mirror: Termux's main Cloudflare mirror (some auto-picked mirrors crawl at a few KB/s)"
ln -sf "$PREFIX/etc/termux/mirrors/default" "$PREFIX/etc/termux/chosen_mirrors"
echo "deb https://packages-cf.termux.dev/apt/termux-main stable main" > "$PREFIX/etc/apt/sources.list"

say "Upgrading Termux first (new adb/node won't start on the older base libraries)"
yes | pkg upgrade -o Dpkg::Options::=--force-confnew

if [ "$1" = "app" ]; then
  say "Installing nodejs"
  yes | pkg install nodejs
else
  say "Installing python, adb, git, nodejs"
  yes | pkg install python android-tools git nodejs
fi

if [ "$1" = "ollama" ]; then
  say "Installing Ollama and qwen3:4b (about 2.5 GB, needs ~4 GB free RAM while it runs)"
  yes | pkg install ollama
  (ollama serve > "$HOME/ollama.log" 2>&1 &) ; sleep 3
  ollama pull qwen3:4b
  echo "Agent: python tvfreedom.py setup --agent ollama (start the server first: ollama serve &)"
  exit 0
fi

say "Installing Claude Code (Linux arm64 build, run through glibc-runner)"
yes | pkg install glibc-repo
yes | pkg install glibc-runner
VER=$(npm view @anthropic-ai/claude-code version)
DIR="$HOME/.local/share/claude-code"
mkdir -p "$DIR" && cd "$DIR"
npm pack -s "@anthropic-ai/claude-code-linux-arm64@$VER" > /dev/null
tar xzf "anthropic-ai-claude-code-linux-arm64-$VER.tgz" && rm -f ./*.tgz
# Call glibc's loader directly: grun passes $@ unquoted, which drops empty args like --tools "".
cat > "$PREFIX/bin/claude" <<EOF
#!$PREFIX/bin/sh
unset LD_PRELOAD
exec "$PREFIX/glibc/lib/ld-linux-aarch64.so.1" "$DIR/package/claude" "\$@"
EOF
chmod 755 "$PREFIX/bin/claude"
claude --version

say "Letting the TVFreedom app run claude here (Termux RUN_COMMAND)"
mkdir -p "$HOME/.termux"
grep -qs '^allow-external-apps *= *true' "$HOME/.termux/termux.properties" ||
  echo "allow-external-apps=true" >> "$HOME/.termux/termux.properties"
termux-reload-settings 2>/dev/null || true

if [ "$1" = "app" ]; then
  say "Claude Code is installed. Log in below (skip if it opens straight away), then type /exit."
  claude || true
  if [ -n "$2" ]; then am start -n "$2" > /dev/null 2>&1 || echo "Now go back to TVFreedom."; fi
  exit 0
fi

say "Done. Next:"
echo "  1. claude            (once, to log in; then /exit)"
echo "  2. python tvfreedom.py setup"
