#!/usr/bin/env bash
# Wrapper: use Python importer (reliable on Windows Git Bash paths)
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
exec python "$ROOT/scripts/import-nacos-config.py" --also-original "$@"
