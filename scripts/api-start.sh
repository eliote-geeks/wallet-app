#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

mkdir -p .runtime

PID_FILE=".runtime/api.pid"
LOG_FILE=".runtime/api.log"

if [ -f "$PID_FILE" ]; then
  PID="$(cat "$PID_FILE" || true)"
  if [ -n "${PID:-}" ] && kill -0 "$PID" 2>/dev/null; then
    echo "[api-start] API already running (pid=$PID)."
    exit 0
  fi
fi

export KEYCLOAK_ISSUER_URI="${KEYCLOAK_ISSUER_URI:-http://localhost:8082/realms/social-wallet}"
export KEYCLOAK_JWK_SET_URI="${KEYCLOAK_JWK_SET_URI:-http://localhost:8082/realms/social-wallet/protocol/openid-connect/certs}"

# Keep Maven/Spring output in a log file for debugging.
# Use spring-boot:run (fast for dev). For prod, we will build an image.
echo "[api-start] Starting API..."
nohup ./mvnw -DskipTests spring-boot:run >"$LOG_FILE" 2>&1 &

echo $! >"$PID_FILE"

echo "[api-start] Started (pid=$(cat "$PID_FILE"))."
echo "[api-start] Logs: $LOG_FILE"
