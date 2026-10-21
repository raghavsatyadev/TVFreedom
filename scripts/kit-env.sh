#!/usr/bin/env bash
# Sourced by the kit scripts. Reads agent-kit.env from the repo root (the one config file).
# Sets GRADLE_DIR (the Gradle root, relative to the repo), APP_MODULE, APP_VARIANT, APP_DIR (module dir from the repo root), APPLICATION_ID, BASE_BRANCH, KTFMT_VERSION, NDK_VERSION.
_kit_root="$(git rev-parse --show-toplevel 2>/dev/null || (cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd))"
GRADLE_DIR="."; APP_MODULE=":app"; APP_VARIANT="Debug"; APPLICATION_ID=""; BASE_BRANCH="development"; FORMAT_CHECK=1; KTFMT_VERSION=""; NDK_VERSION=""
if [ -f "$_kit_root/agent-kit.env" ]; then
  # shellcheck disable=SC1090
  . <(tr -d '\r' < "$_kit_root/agent-kit.env")
fi
APP_DIR="${APP_MODULE#:}"; APP_DIR="${GRADLE_DIR%/}/${APP_DIR//://}"; APP_DIR="${APP_DIR#./}"
export GRADLE_DIR APP_MODULE APP_VARIANT APP_DIR APPLICATION_ID BASE_BRANCH FORMAT_CHECK KTFMT_VERSION NDK_VERSION
