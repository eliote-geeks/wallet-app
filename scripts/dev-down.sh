#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

MODE="${1:-stop}"

case "$MODE" in
  stop)
    echo "[dev-down] Stopping core services (db, keycloak)..."
    docker compose stop db keycloak
    ;;
  down)
    echo "[dev-down] Bringing down core services (db, keycloak)..."
    docker compose down
    ;;
  *)
    echo "Usage: $0 [stop|down]" >&2
    exit 2
    ;;
esac
