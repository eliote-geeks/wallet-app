#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

echo "[status] Containers:"
docker ps --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}' | sed -n '1,30p'

echo
if command -v ss >/dev/null 2>&1; then
  echo "[status] Listening ports (8080/8082/5432):"
  ss -ltnp | grep -E ':(8080|8082|5432)\b' || true
fi

echo
if curl -fsS http://localhost:8080/actuator/health >/dev/null 2>&1; then
  echo "[status] API health: OK"
else
  echo "[status] API health: NOT OK (maybe not started yet)"
fi
