#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")"
if ! command -v node >/dev/null 2>&1; then
  echo "Bitte zuerst Node.js 22 oder neuer von https://nodejs.org/ installieren."
  exit 1
fi
exec node start.mjs
