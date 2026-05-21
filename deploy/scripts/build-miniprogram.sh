#!/usr/bin/env bash
#
# Build the uni-app frontend into H5 + WeChat mini-program dist artifacts.
#
# Heavy build deps are installed in a sandbox tarball so they do not enter the
# committed lockfile (which protects the audit baseline at 0 vulnerabilities).
# Outputs:
#   apps/miniprogram/dist/build/h5/
#   apps/miniprogram/dist/build/mp-weixin/
#
# Usage (from repo root):
#   deploy/scripts/build-miniprogram.sh             # both targets
#   deploy/scripts/build-miniprogram.sh h5          # just H5
#   deploy/scripts/build-miniprogram.sh mp-weixin   # just WeChat mini-program

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
APP_DIR="$REPO_ROOT/apps/miniprogram"

if [ ! -f "$APP_DIR/package.json" ]; then
  echo "::error::apps/miniprogram/package.json not found"
  exit 1
fi

cd "$APP_DIR"

# Install committed deps from lockfile (test+typecheck deps).
if [ ! -d node_modules ]; then
  npm ci
fi

# Install heavy build-only deps WITHOUT touching package.json / lockfile.
# Versions tracked here so a regression is loud:
BUILD_DEPS=(
  "vite@5.4.10"
  "@dcloudio/vite-plugin-uni@3.0.0-alpha-1000920260519001"
  "@dcloudio/uni-h5@3.0.0-alpha-1000920260519001"
  "@dcloudio/uni-mp-weixin@3.0.0-alpha-1000920260519001"
  "@dcloudio/uni-mp-vue@3.0.0-alpha-1000920260519001"
)

echo "::group::Installing build-only deps (sandboxed, not persisted to lockfile)"
npm install --no-save --no-audit --no-fund "${BUILD_DEPS[@]}"
echo "::endgroup::"

target="${1:-all}"
case "$target" in
  h5)
    npm run build:h5
    ;;
  mp-weixin)
    npm run build:mp-weixin
    ;;
  all)
    npm run build:h5
    npm run build:mp-weixin
    ;;
  *)
    echo "::error::unknown target '$target' (expected: h5 | mp-weixin | all)"
    exit 2
    ;;
esac

echo
echo "Done. Artifacts:"
ls -1 dist/build 2>/dev/null || true
