#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck disable=SC1091
. "$ROOT_DIR/scripts/e2e-common.sh"

BUYER_PHONE="${BUYER_PHONE:-+237690000060}"
TOPUP_AMOUNT="${TOPUP_AMOUNT:-5000}"
PROVIDER="${PROVIDER:-mtn}"
CURRENCY="${CURRENCY:-XAF}"

require_tools
wait_health "$API_BASE/actuator/health"

echo "[e2e-mobilemoney] Using buyer phone: $BUYER_PHONE"
BUYER_TOKEN="$(ensure_user_token "$BUYER_PHONE")"

before_balance="$(curl -fsS -H "authorization: Bearer $BUYER_TOKEN" \
  "$API_BASE/api/wallet/balance/$CURRENCY" | jq -r ".available // 0")"

topup_resp="$(curl -fsS -X POST "$API_BASE/api/payments/mobile-money/topups" \
  -H "authorization: Bearer $BUYER_TOKEN" \
  -H "content-type: application/json" \
  -d "{\"amount\":$TOPUP_AMOUNT,\"currency\":\"$CURRENCY\",\"phoneNumber\":\"$BUYER_PHONE\",\"provider\":\"$PROVIDER\"}")"

tx_id="$(echo "$topup_resp" | jq -r ".transactionId")"
status="$(echo "$topup_resp" | jq -r ".status")"

if [ -z "$tx_id" ] || [ "$tx_id" = "null" ]; then
  echo "[e2e-mobilemoney] Missing transactionId" >&2
  echo "$topup_resp" >&2
  exit 1
fi

curl -fsS -X POST "$API_BASE/api/webhooks/mobile-money" \
  -H "content-type: application/json" \
  -d "{\"transactionId\":\"$tx_id\",\"status\":\"SUCCESS\",\"type\":\"TOPUP\"}" \
  >/dev/null

after_balance="$(curl -fsS -H "authorization: Bearer $BUYER_TOKEN" \
  "$API_BASE/api/wallet/balance/$CURRENCY" | jq -r ".available // 0")"

expected_balance=$((before_balance + TOPUP_AMOUNT))
if [ "$after_balance" -ne "$expected_balance" ]; then
  echo "[e2e-mobilemoney] Balance mismatch: before=$before_balance amount=$TOPUP_AMOUNT expected=$expected_balance got=$after_balance" >&2
  exit 1
fi

echo "[e2e-mobilemoney] PASS"
echo "  transactionId: $tx_id"
echo "  providerStatus: $status"
echo "  balanceBefore: $before_balance $CURRENCY"
echo "  balanceAfter : $after_balance $CURRENCY"

