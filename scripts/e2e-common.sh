#!/usr/bin/env bash
set -euo pipefail

API_BASE="${API_BASE:-http://localhost:8080}"
APP_PASSWORD="${APP_PASSWORD:-Passw0rd!}"
KEYCLOAK_CONTAINER="${KEYCLOAK_CONTAINER:-social-wallet-keycloak}"
COMMON_ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

require_tools() {
  local missing=0
  for cmd in curl jq docker; do
    if ! command -v "$cmd" >/dev/null 2>&1; then
      echo "[e2e] Missing required command: $cmd" >&2
      missing=1
    fi
  done
  if [ "$missing" -ne 0 ]; then
    exit 1
  fi
}

wait_health() {
  local url="${1:-$API_BASE/actuator/health}"
  local attempts="${2:-90}"
  local delay="${3:-2}"
  local i
  for i in $(seq 1 "$attempts"); do
    if curl -fsS "$url" >/dev/null 2>&1; then
      return 0
    fi
    sleep "$delay"
  done
  echo "[e2e] Service not healthy: $url" >&2
  return 1
}

login_token() {
  local phone="$1"
  curl -fsS -X POST "$API_BASE/api/auth/login" \
    -H "content-type: application/json" \
    -d "{\"identifier\":\"$phone\",\"password\":\"$APP_PASSWORD\"}" \
    | jq -r ".access_token // empty"
}

register_and_verify() {
  local phone="$1"
  local register_resp otp_id otp_code
  register_resp="$(curl -fsS -X POST "$API_BASE/api/auth/register" \
    -H "content-type: application/json" \
    -d "{\"phoneNumber\":\"$phone\",\"password\":\"$APP_PASSWORD\"}")"
  otp_id="$(echo "$register_resp" | jq -r ".otpId")"
  otp_code="$(echo "$register_resp" | jq -r ".debugCode")"

  if [ -z "$otp_id" ] || [ "$otp_id" = "null" ]; then
    echo "[e2e] Registration failed for $phone: missing otpId" >&2
    echo "$register_resp" >&2
    return 1
  fi
  if [ -z "$otp_code" ] || [ "$otp_code" = "null" ]; then
    echo "[e2e] Registration failed for $phone: missing debugCode (OTP_DEBUG must be true in dev)" >&2
    echo "$register_resp" >&2
    return 1
  fi

  curl -fsS -X POST "$API_BASE/api/auth/verify-otp" \
    -H "content-type: application/json" \
    -d "{\"otpId\":\"$otp_id\",\"code\":\"$otp_code\",\"password\":\"$APP_PASSWORD\"}" \
    >/dev/null
}

ensure_user_token() {
  local phone="$1"
  local token
  token="$(login_token "$phone" || true)"
  if [ -n "$token" ]; then
    echo "$token"
    return 0
  fi
  register_and_verify "$phone"
  token="$(login_token "$phone")"
  if [ -z "$token" ]; then
    echo "[e2e] Could not obtain token for $phone" >&2
    return 1
  fi
  echo "$token"
}

me_user_id() {
  local token="$1"
  curl -fsS -H "authorization: Bearer $token" "$API_BASE/api/auth/me" | jq -r ".userId"
}

ensure_realm_role() {
  local role="$1"
  local kc="/opt/keycloak/bin/kcadm.sh"
  docker exec "$KEYCLOAK_CONTAINER" sh -lc \
    "$kc config credentials --server http://localhost:8080 --realm master --user admin --password admin >/dev/null && \
     $kc get roles -r social-wallet --fields name --format csv --noquotes | grep -qx '$role' || \
     $kc create roles -r social-wallet -s name=$role >/dev/null"
}

assign_role() {
  local user_id="$1"
  local role="$2"
  local kc="/opt/keycloak/bin/kcadm.sh"
  ensure_realm_role "$role"
  docker exec "$KEYCLOAK_CONTAINER" sh -lc \
    "$kc config credentials --server http://localhost:8080 --realm master --user admin --password admin >/dev/null && \
     $kc add-roles -r social-wallet --uid $user_id --rolename $role >/dev/null 2>&1 || true"
}

get_medusa_admin_token() {
  local token
  if [ -n "${MEDUSA_ADMIN_TOKEN:-}" ]; then
    echo "$MEDUSA_ADMIN_TOKEN"
    return 0
  fi

  if docker ps --format '{{.Names}}' | grep -qx 'social-wallet-api'; then
    token="$(docker exec social-wallet-api sh -lc 'printf %s "$MEDUSA_ADMIN_TOKEN"' 2>/dev/null || true)"
    if [ -n "$token" ]; then
      echo "$token"
      return 0
    fi
  fi

  if [ -f "$COMMON_ROOT_DIR/.env" ]; then
    token="$(grep -E '^MEDUSA_ADMIN_TOKEN=' "$COMMON_ROOT_DIR/.env" | tail -n1 | cut -d= -f2-)"
    if [ -n "$token" ]; then
      echo "$token"
      return 0
    fi
  fi

  if [ -z "$token" ]; then
    echo "[e2e] MEDUSA_ADMIN_TOKEN missing (env, .env, and social-wallet-api container)." >&2
    return 1
  fi
  echo "$token"
}

get_medusa_webhook_secret() {
  local secret
  if [ -n "${MEDUSA_WEBHOOK_SECRET:-}" ]; then
    echo "$MEDUSA_WEBHOOK_SECRET"
    return 0
  fi
  if [ -f "$COMMON_ROOT_DIR/.env" ]; then
    secret="$(grep -E '^MEDUSA_WEBHOOK_SECRET=' "$COMMON_ROOT_DIR/.env" | tail -n1 | cut -d= -f2-)"
    if [ -n "$secret" ]; then
      echo "$secret"
      return 0
    fi
  fi
  return 1
}

medusa_signature_header() {
  local payload="$1"
  local secret digest
  secret="$(get_medusa_webhook_secret || true)"
  if [ -z "$secret" ]; then
    return 0
  fi
  if ! command -v openssl >/dev/null 2>&1; then
    echo "[e2e] openssl required to sign Medusa webhook payload" >&2
    return 1
  fi
  digest="$(printf '%s' "$payload" | openssl dgst -sha256 -hmac "$secret" | awk '{print $2}')"
  if [ -z "$digest" ]; then
    echo "[e2e] Unable to compute Medusa webhook signature" >&2
    return 1
  fi
  echo "sha256=$digest"
}
