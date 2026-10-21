#!/usr/bin/env bash
# ==============================================================================
# Android Agent Kit - Interactive Developer Environment Doctor & Setup (macOS / Linux)
# ==============================================================================
# Verifies and configures:
#   1. Git submodules & git hooks
#   2. JDK 21 & JAVA_HOME
#   3. Android SDK (NDK only if agent-kit.env sets one), ANDROID_HOME & platform-tools
#   4. Connected Android devices/emulators via adb
#   5. Python 3.10+ and uv
#   6. GitHub CLI login and repo access
#   7. ARTEMIS clone, API key and MCP registration
#   8. ARTEMIS model: Gemini (local Qwen fallback) or local Qwen only, sized to your GPU
#      Non-interactive choice: ARTEMIS_MODEL=gemini|qwen bash scripts/setup-env.sh
#      Decision model: DECISION_MODEL=nimble|tev1|laya|jev|none (default: asks, recommending by hardware)
#   9. ktfmt (fetched by scripts/ci-local.sh)
#  10. Links .agents/skills/ into .claude/skills/ so Claude Code finds the skills
# ==============================================================================

set -uo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT" || exit 1
. "$REPO_ROOT/scripts/kit-env.sh"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m' # No Color

issues_found=0

header() {
  echo ""
  echo -e "${CYAN}==================================================================${NC}"
  echo -e "${BOLD}  $1${NC}"
  echo -e "${CYAN}==================================================================${NC}"
}

pass() { echo -e "  ${GREEN}[OK]${NC} $1"; }
warn() { echo -e "  ${YELLOW}[WARN]${NC} $1"; }
fail() { echo -e "  ${RED}[FAIL]${NC} $1"; }
info() { echo -e "  [INFO] $1"; }

prompt_fix() {
  echo ""
  read -r -p "  --> $1 (y/N): " response
  case "$response" in
    [yY][eE][sS]|[yY]) return 0 ;;
    *) return 1 ;;
  esac
}

OS_TYPE="$(uname -s)"
case "$OS_TYPE" in
  Darwin*)
    SHELL_PROFILE="$HOME/.zshrc"
    DEFAULT_ANDROID_HOME="$HOME/Library/Android/sdk"
    ;;
  Linux*)
    SHELL_PROFILE="$HOME/.bashrc"
    DEFAULT_ANDROID_HOME="$HOME/Android/Sdk"
    ;;
  *)
    SHELL_PROFILE="$HOME/.bashrc"
    DEFAULT_ANDROID_HOME="$HOME/Android/Sdk"
    ;;
esac

header "Android Agent Kit - Developer Setup Doctor ($OS_TYPE)"
info "Repository: $REPO_ROOT"
info "Target Shell Profile: $SHELL_PROFILE"

# -------------------------------------------------------------
# 1. Git & Submodules
# -------------------------------------------------------------
header "1. Git Configuration & Submodules"
if command -v git &>/dev/null; then
  pass "Git is installed: $(command -v git)"

  if [[ -f "$REPO_ROOT/.gitmodules" ]] && git submodule status | grep -q '^-'; then
    warn "git submodules are not initialized."
    if prompt_fix "Initialize git submodules now (git submodule update --init --recursive)?"; then
      git submodule update --init --recursive
    else
      info "Run manually: git submodule update --init --recursive"
      issues_found=$((issues_found + 1))
    fi
  fi

  CURRENT_HOOKS="$(git config core.hooksPath || true)"
  if [[ "$CURRENT_HOOKS" != ".githooks" ]]; then
    warn "Git hooks path is not set to .githooks (current: '$CURRENT_HOOKS')"
    if prompt_fix "Set git core.hooksPath to .githooks now?"; then
      git config core.hooksPath .githooks
      pass "Git hooks path set to .githooks"
    else
      info "Run manually: git config core.hooksPath .githooks"
    fi
  else
    pass "Git hooks configured (.githooks)."
  fi
else
  fail "Git is not installed."
  issues_found=$((issues_found + 1))
fi

# -------------------------------------------------------------
# 2. Java / JDK 21
# -------------------------------------------------------------
header "2. Java Development Kit (JDK 21)"
if command -v java &>/dev/null; then
  JAVA_VER="$(java -version 2>&1 | head -n 1)"
  pass "Java found: $JAVA_VER"
else
  fail "java command not found in PATH."
  issues_found=$((issues_found + 1))
fi

if [[ -n "${JAVA_HOME:-}" && -d "$JAVA_HOME" ]]; then
  pass "JAVA_HOME is set: $JAVA_HOME"
else
  warn "JAVA_HOME is not set or directory does not exist."
  info "Add JAVA_HOME to $SHELL_PROFILE:"
  echo -e "${YELLOW}    export JAVA_HOME=/path/to/jdk-21${NC}"
  echo -e "${YELLOW}    export PATH=\"\$JAVA_HOME/bin:\$PATH\"${NC}"
  issues_found=$((issues_found + 1))
fi

# -------------------------------------------------------------
# 3. Android SDK (and the NDK from agent-kit.env, if set)
# -------------------------------------------------------------
header "3. Android SDK"
DETECTED_SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [[ -z "$DETECTED_SDK" && -f "$REPO_ROOT/$GRADLE_DIR/local.properties" ]]; then
  # Android Studio writes sdk.dir here, escaped (C\:\\Path on Windows).
  SDK_LINE="$(grep -E '^sdk\.dir=' "$REPO_ROOT/$GRADLE_DIR/local.properties" | head -1 | tr -d '\r')"
  DETECTED_SDK="$(sed -e 's/^sdk\.dir=//' -e 's/\\:/:/g' -e 's/\\\\/\//g' <<<"$SDK_LINE")"
  command -v cygpath &>/dev/null && [[ -n "$DETECTED_SDK" ]] && DETECTED_SDK="$(cygpath -u "$DETECTED_SDK")"
fi
DETECTED_SDK="${DETECTED_SDK:-$DEFAULT_ANDROID_HOME}"

if [[ -d "$DETECTED_SDK" ]]; then
  pass "Android SDK found at: $DETECTED_SDK"

  if command -v adb &>/dev/null; then
    pass "adb found in PATH: $(command -v adb)"
  elif [[ -f "$DETECTED_SDK/platform-tools/adb" ]]; then
    warn "adb found at $DETECTED_SDK/platform-tools/adb but not in PATH."
    info "Add to $SHELL_PROFILE:"
    echo -e "${YELLOW}    export PATH=\"\$ANDROID_HOME/platform-tools:\$PATH\"${NC}"
  else
    fail "adb not found in platform-tools."
    issues_found=$((issues_found + 1))
  fi

  REQUIRED_NDK="${NDK_VERSION:-}"
  if [[ -z "$REQUIRED_NDK" ]]; then
    info "No NDK needed (NDK_VERSION is blank in agent-kit.env)."
  elif [[ -d "$DETECTED_SDK/ndk/$REQUIRED_NDK" ]]; then
    pass "Required Android NDK ($REQUIRED_NDK) found."
  else
    warn "Android NDK $REQUIRED_NDK not found at $DETECTED_SDK/ndk/$REQUIRED_NDK"
    info "Install via SDK Manager or CLI:"
    echo -e "${YELLOW}    \"\$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager\" \"ndk;$REQUIRED_NDK\"${NC}"
  fi
else
  fail "Android SDK not found."
  info "Install Android Studio and export ANDROID_HOME in $SHELL_PROFILE:"
  echo -e "${YELLOW}    export ANDROID_HOME=\"$DEFAULT_ANDROID_HOME\"${NC}"
  echo -e "${YELLOW}    export PATH=\"\$ANDROID_HOME/platform-tools:\$ANDROID_HOME/cmdline-tools/latest/bin:\$PATH\"${NC}"
  issues_found=$((issues_found + 1))
fi

# -------------------------------------------------------------
# 4. Connected Android Devices & Emulators
# -------------------------------------------------------------
header "4. Connected Android Devices"
if command -v adb &>/dev/null; then
  DEVICES="$(adb devices -l | grep -E '^\S+\s+(device|unauthorized|offline)' || true)"
  if [[ -n "$DEVICES" ]]; then
    pass "Connected device(s) found:"
    echo "$DEVICES" | while read -r line; do
      echo -e "    ${GREEN}$line${NC}"
    done
  else
    warn "No Android devices or emulators connected. Connect phone via USB/Wi-Fi or start an AVD."
  fi
fi

# -------------------------------------------------------------
# 5. Python 3.10+ & uv
# -------------------------------------------------------------
header "5. Python 3.10+ and uv Package Manager"
if command -v python3 &>/dev/null; then
  PY_VER="$(python3 --version)"
  pass "Python is installed: $PY_VER"
else
  warn "python3 not found in PATH."
fi

if command -v uv &>/dev/null; then
  pass "uv package manager installed: $(command -v uv)"
else
  warn "uv package manager not found."
  if prompt_fix "Install uv now (curl -LsSf https://astral.sh/uv/install.sh | sh)?"; then
    curl -LsSf https://astral.sh/uv/install.sh | sh
    pass "uv installed. You may need to source $SHELL_PROFILE."
  else
    info "Install manually: curl -LsSf https://astral.sh/uv/install.sh | sh"
  fi
fi

# -------------------------------------------------------------
# 6. GitHub CLI (issue -> PR workflow)
# -------------------------------------------------------------
header "6. GitHub CLI"
if ! command -v gh &>/dev/null; then
  fail "gh is not installed. The bug -> PR workflow uses it to read issues and open PRs."
  info "Install: https://cli.github.com (brew install gh / sudo apt install gh), then: gh auth login"
  issues_found=$((issues_found + 1))
else
  pass "gh is installed: $(command -v gh)"
  # 'gh auth status' also fails on a stale inactive account; test the active one instead.
  if ! GH_USER="$(gh api user -q .login 2>/dev/null)" || [[ -z "$GH_USER" ]]; then
    fail "gh is not logged in. Run: gh auth login"
    issues_found=$((issues_found + 1))
  else
    PERM="$(gh repo view --json viewerPermission -q .viewerPermission 2>/dev/null || true)"
    case "$PERM" in
      ADMIN|MAINTAIN|WRITE) pass "gh is logged in as $GH_USER with $PERM access to this repo." ;;
      *) warn "gh is logged in but has '$PERM' access here - you cannot push fix branches."
         info "Ask a maintainer for write access to the repository." ;;
    esac
  fi
fi

. "$REPO_ROOT/scripts/setup-env-models.sh"

# -------------------------------------------------------------
# 9. Kotlin Formatting
# -------------------------------------------------------------
header "9. Kotlin Formatting (ktfmt)"
pass "Nothing to install: 'bash scripts/ci-local.sh --fix' downloads the ktfmt version CI uses."
if command -v ktfmt &>/dev/null; then
  warn "A ktfmt is on your PATH - don't use it for this repo; its version may disagree with CI."
fi

# -------------------------------------------------------------
# 10. Agent Skills for Claude Code
# -------------------------------------------------------------
# Skills live in .agents/skills/ (Gemini CLI reads it). Claude Code only reads .claude/skills/,
# so symlink each skill there.
header "10. Agent Skills for Claude Code"
mkdir -p .claude/skills
linked=0
for skill in .agents/skills/*/; do
  name="$(basename "$skill")"
  [ -e ".claude/skills/$name" ] && continue
  ln -s "../../.agents/skills/$name" ".claude/skills/$name"
  linked=$((linked + 1))
done
pass "Claude Code sees every skill in .agents/skills/ ($linked new link(s) in .claude/skills/)."

# -------------------------------------------------------------
# Environment Variables Guide
# -------------------------------------------------------------
header "Environment Variables Configuration for $SHELL_PROFILE"
echo -e "${BOLD}Add the following lines to your $SHELL_PROFILE:${NC}"
echo ""
echo -e "${YELLOW}# Java 21${NC}"
echo -e "${YELLOW}export JAVA_HOME=\"/path/to/jdk-21\"${NC}"
echo -e "${YELLOW}export PATH=\"\$JAVA_HOME/bin:\$PATH\"${NC}"
echo ""
echo -e "${YELLOW}# Android SDK & Tools${NC}"
echo -e "${YELLOW}export ANDROID_HOME=\"$DEFAULT_ANDROID_HOME\"${NC}"
echo -e "${YELLOW}export PATH=\"\$ANDROID_HOME/platform-tools:\$ANDROID_HOME/cmdline-tools/latest/bin:\$PATH\"${NC}"
echo ""
echo -e "After updating, reload with: ${BOLD}source $SHELL_PROFILE${NC}"
echo ""

header "Doctor Summary"
if [[ $issues_found -eq 0 ]]; then
  echo -e "${GREEN}${BOLD}ALL ESSENTIAL PREREQUISITES MET! Ready to build.${NC}"
  echo -e "Commands to get started:"
  echo -e "  Build:   ${CYAN}cd $GRADLE_DIR && ./gradlew :${APP_MODULE#:}:assemble${APP_VARIANT}${NC}"
  echo -e "  Test:    ${CYAN}cd $GRADLE_DIR && ./gradlew :${APP_MODULE#:}:test${APP_VARIANT}UnitTest${NC}"
  echo -e "  Format:  ${CYAN}bash scripts/ci-local.sh --fix${NC}"
else
  echo -e "${YELLOW}${BOLD}Found $issues_found item(s) that need attention. See above logs.${NC}"
fi
echo ""
