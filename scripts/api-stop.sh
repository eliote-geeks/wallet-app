#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

PID_FILE=".runtime/api.pid"

if [ ! -f "$PID_FILE" ]; then
  echo "[api-stop] No pid file found ($PID_FILE)."
  exit 0
fi

PID="$(cat "$PID_FILE" || true)"
if [ -z "${PID:-}" ]; then
  echo "[api-stop] Empty pid file; removing."
  rm -f "$PID_FILE"
  exit 0
fi

if kill -0 "$PID" 2>/dev/null; then
  echo "[api-stop] Stopping API (pid=$PID)..."
  kill "$PID"
else
  echo "[api-stop] Process not running (pid=$PID). Cleaning up pid file."
fi

rm -f "$PID_FILE"
