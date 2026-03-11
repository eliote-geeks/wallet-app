#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKEND_ENV="$ROOT_DIR/.env"
BACKEND_ENV_EXAMPLE="$ROOT_DIR/.env.example"
MEDUSA_ENV="$ROOT_DIR/infra/medusa/.env"
MEDUSA_ENV_EXAMPLE="$ROOT_DIR/infra/medusa/.env.example"

ensure_env_file() {
  local env_file="$1"
  local example_file="$2"
  if [ -f "$env_file" ]; then
    return
  fi
  if [ -f "$example_file" ]; then
    cp "$example_file" "$env_file"
  else
    touch "$env_file"
  fi
}

upsert_env_var() {
  local file="$1"
  local key="$2"
  local value="$3"
  local tmp
  tmp="$(mktemp)"
  awk -v k="$key" -v v="$value" '
    BEGIN { replaced = 0 }
    $0 ~ "^"k"=" {
      if (!replaced) {
        print k "=" v
        replaced = 1
      }
      next
    }
    { print }
    END {
      if (!replaced) {
        print k "=" v
      }
    }
  ' "$file" >"$tmp"
  mv "$tmp" "$file"
}

generate_secret() {
  if command -v openssl >/dev/null 2>&1; then
    openssl rand -hex 32
  else
    head -c 32 /dev/urandom | xxd -p -c 256
  fi
}

SECRET="${1:-${MEDUSA_WEBHOOK_SECRET:-}}"
if [ -z "$SECRET" ]; then
  SECRET="$(generate_secret)"
fi

ensure_env_file "$BACKEND_ENV" "$BACKEND_ENV_EXAMPLE"
ensure_env_file "$MEDUSA_ENV" "$MEDUSA_ENV_EXAMPLE"

upsert_env_var "$BACKEND_ENV" "MEDUSA_WEBHOOK_SECRET" "$SECRET"
upsert_env_var "$MEDUSA_ENV" "KOBO_BACKEND_WEBHOOK_SECRET" "$SECRET"

echo "[webhook-secret] Secret configured in:"
echo "  - $BACKEND_ENV (MEDUSA_WEBHOOK_SECRET)"
echo "  - $MEDUSA_ENV (KOBO_BACKEND_WEBHOOK_SECRET)"

if [ "${NO_RESTART:-false}" = "true" ]; then
  echo "[webhook-secret] NO_RESTART=true, skipping container restart."
  exit 0
fi

if ! command -v docker >/dev/null 2>&1 || ! docker info >/dev/null 2>&1; then
  echo "[webhook-secret] Docker daemon unavailable. Restart skipped."
  echo "[webhook-secret] Run manually:"
  echo "  docker compose up -d app"
  echo "  (cd infra/medusa && docker compose up -d medusa)"
  exit 0
fi

echo "[webhook-secret] Restarting API and Medusa..."
if ! (cd "$ROOT_DIR" && docker compose up -d app >/dev/null 2>&1); then
  echo "[webhook-secret] Warning: unable to restart app container (maybe API runs locally on port 8080)."
fi
if ! (cd "$ROOT_DIR/infra/medusa" && docker compose up -d medusa >/dev/null 2>&1); then
  echo "[webhook-secret] Warning: unable to restart Medusa container."
fi
echo "[webhook-secret] Restart complete."
