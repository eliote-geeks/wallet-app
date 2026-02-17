#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck disable=SC1091
. "$ROOT_DIR/scripts/e2e-common.sh"

SELLER_PHONE="${SELLER_PHONE:-+237690000020}"
BUYER_PHONE="${BUYER_PHONE:-+237690000060}"
CURRENCY="${CURRENCY:-XAF}"
ITEM_AMOUNT="${ITEM_AMOUNT:-15010}"
TOPUP_AMOUNT="${TOPUP_AMOUNT:-60000}"
SETTLEMENT_WAIT_ATTEMPTS="${SETTLEMENT_WAIT_ATTEMPTS:-20}"
REFUND_WAIT_ATTEMPTS="${REFUND_WAIT_ATTEMPTS:-20}"

require_tools
wait_health "$API_BASE/actuator/health"

echo "[e2e-store] Ensure seller/buyer users and tokens"
SELLER_TOKEN="$(ensure_user_token "$SELLER_PHONE")"
BUYER_TOKEN="$(ensure_user_token "$BUYER_PHONE")"
SELLER_USER_ID="$(me_user_id "$SELLER_TOKEN")"

assign_role "$SELLER_USER_ID" "SELLER"

# Refresh seller token after role assignment.
SELLER_TOKEN="$(login_token "$SELLER_PHONE")"

MEDUSA_ADMIN_TOKEN="$(get_medusa_admin_token)"

shipping_profile_id="$(curl -fsS -u "$MEDUSA_ADMIN_TOKEN:" \
  "http://localhost:9000/admin/shipping-profiles?type=default" | jq -r ".shipping_profiles[0].id")"
sales_channel_id="$(curl -fsS -u "$MEDUSA_ADMIN_TOKEN:" \
  "http://localhost:9000/admin/sales-channels" | jq -r ".sales_channels[0].id")"

sku="KOBO-E2E-$(date +%s)"
title="Kobo E2E Product ${sku}"
handle="kobo-e2e-${sku,,}"
product_payload="$(jq -n \
  --arg ship "$shipping_profile_id" \
  --arg sc "$sales_channel_id" \
  --arg sku "$sku" \
  --arg title "$title" \
  --arg handle "$handle" \
  --argjson amount "$ITEM_AMOUNT" \
  --arg currency_lc "${CURRENCY,,}" \
  '{
    title: $title,
    handle: $handle,
    status: "published",
    shipping_profile_id: $ship,
    sales_channels: [{id: $sc}],
    options: [{title: "Default", values: ["Default"]}],
    variants: [{
      title: "Default",
      sku: $sku,
      options: {Default: "Default"},
      prices: [{amount: $amount, currency_code: $currency_lc}]
    }]
  }')"

product_resp_file="$(mktemp)"
product_http_code="$(curl -sS -o "$product_resp_file" -w "%{http_code}" -X POST "$API_BASE/api/store/seller/products" \
  -H "authorization: Bearer $SELLER_TOKEN" \
  -H "content-type: application/json" \
  -d "$product_payload")"
if [[ "$product_http_code" -lt 200 || "$product_http_code" -ge 300 ]]; then
  echo "[e2e-store] Seller product creation failed (HTTP $product_http_code)" >&2
  cat "$product_resp_file" >&2
  rm -f "$product_resp_file"
  exit 1
fi
product_resp="$(cat "$product_resp_file")"
rm -f "$product_resp_file"

product_id="$(echo "$product_resp" | jq -r ".product.id")"
variant_id="$(echo "$product_resp" | jq -r ".product.variants[0].id")"

if [ -z "$product_id" ] || [ "$product_id" = "null" ] || [ -z "$variant_id" ] || [ "$variant_id" = "null" ]; then
  echo "[e2e-store] Could not create product/variant" >&2
  echo "$product_resp" >&2
  exit 1
fi

curl -fsS -X PATCH "$API_BASE/api/store/seller/products/$product_id/pricing-stock" \
  -H "authorization: Bearer $SELLER_TOKEN" \
  -H "content-type: application/json" \
  -d "{\"variants\":[{\"id\":\"$variant_id\",\"prices\":[{\"amount\":$ITEM_AMOUNT,\"currency_code\":\"${CURRENCY,,}\"}],\"inventory_quantity\":100}]}" \
  >/dev/null

# Topup buyer (manual wallet endpoint) to guarantee enough funds.
curl -fsS -X POST "$API_BASE/api/wallet/topup" \
  -H "authorization: Bearer $BUYER_TOKEN" \
  -H "content-type: application/json" \
  -d "{\"amount\":$TOPUP_AMOUNT,\"currency\":\"$CURRENCY\"}" \
  >/dev/null

buyer_before="$(curl -fsS -H "authorization: Bearer $BUYER_TOKEN" \
  "$API_BASE/api/wallet/balance/$CURRENCY" | jq -r ".available // 0")"
seller_before="$(curl -fsS -H "authorization: Bearer $SELLER_TOKEN" \
  "$API_BASE/api/wallet/balance/$CURRENCY" | jq -r ".available // 0")"

cart_id="$(curl -fsS -X POST "$API_BASE/api/store/carts" \
  -H "authorization: Bearer $BUYER_TOKEN" \
  -H "content-type: application/json" \
  -d "{}" | jq -r ".cart.id")"

curl -fsS -X POST "$API_BASE/api/store/carts/$cart_id/line-items" \
  -H "authorization: Bearer $BUYER_TOKEN" \
  -H "content-type: application/json" \
  -d "{\"variant_id\":\"$variant_id\",\"quantity\":1}" \
  >/dev/null

shipping_option_id="$(curl -fsS -H "authorization: Bearer $BUYER_TOKEN" \
  "$API_BASE/api/store/carts/$cart_id/shipping-options" | jq -r ".shipping_options[0].id")"

curl -fsS -X POST "$API_BASE/api/store/carts/$cart_id/shipping-methods" \
  -H "authorization: Bearer $BUYER_TOKEN" \
  -H "content-type: application/json" \
  -d "{\"option_id\":\"$shipping_option_id\"}" \
  >/dev/null

order_resp="$(curl -fsS -X POST "$API_BASE/api/store/carts/$cart_id/complete" \
  -H "authorization: Bearer $BUYER_TOKEN" \
  -H "content-type: application/json" \
  -d "{\"payment_method\":\"wallet\"}")"
order_id="$(echo "$order_resp" | jq -r ".order.id")"

if [ -z "$order_id" ] || [ "$order_id" = "null" ]; then
  echo "[e2e-store] Checkout failed: missing order id" >&2
  echo "$order_resp" >&2
  exit 1
fi

status_after_checkout=""
for _ in $(seq 1 "$SETTLEMENT_WAIT_ATTEMPTS"); do
  status_after_checkout="$(curl -fsS -H "authorization: Bearer $SELLER_TOKEN" \
    "$API_BASE/api/store/seller/orders/$order_id/status" | jq -r ".status // empty")"
  if [ "$status_after_checkout" = "SETTLED" ]; then
    break
  fi
  sleep 2
done

buyer_after_checkout="$(curl -fsS -H "authorization: Bearer $BUYER_TOKEN" \
  "$API_BASE/api/wallet/balance/$CURRENCY" | jq -r ".available // 0")"
seller_after_checkout="$(curl -fsS -H "authorization: Bearer $SELLER_TOKEN" \
  "$API_BASE/api/wallet/balance/$CURRENCY" | jq -r ".available // 0")"

if [ "$status_after_checkout" != "SETTLED" ]; then
  echo "[e2e-store] Seller order not settled: $status_after_checkout" >&2
  exit 1
fi
if [ "$seller_after_checkout" -le "$seller_before" ]; then
  echo "[e2e-store] Seller balance did not increase after settlement" >&2
  exit 1
fi

# Simulate refund webhook to verify reversal.
admin_order="$(curl -fsS -u "$MEDUSA_ADMIN_TOKEN:" \
  "http://localhost:9000/admin/orders/$order_id?fields=*items,*items.product,*items.product.metadata,*items.variant,*items.variant.product,*items.variant.product.metadata,payment_status,currency_code,metadata,cart_id")"
payload="$(jq -n --argjson order "$(echo "$admin_order" | jq ".order")" '{event:"order.refunded",data:{order:$order}}')"
signature="$(medusa_signature_header "$payload" || true)"
curl_args=(
  -fsS
  -X POST "$API_BASE/api/webhooks/medusa"
  -H "content-type: application/json"
  -H "x-medusa-event: order.refunded"
)
if [ -n "$signature" ]; then
  curl_args+=(-H "x-medusa-signature: $signature")
fi
curl "${curl_args[@]}" -d "$payload" >/dev/null

status_after_refund=""
for _ in $(seq 1 "$REFUND_WAIT_ATTEMPTS"); do
  status_after_refund="$(curl -fsS -H "authorization: Bearer $SELLER_TOKEN" \
    "$API_BASE/api/store/seller/orders/$order_id/status" | jq -r ".status // empty")"
  if [ "$status_after_refund" = "REFUNDED" ]; then
    break
  fi
  sleep 2
done
seller_after_refund="$(curl -fsS -H "authorization: Bearer $SELLER_TOKEN" \
  "$API_BASE/api/wallet/balance/$CURRENCY" | jq -r ".available // 0")"

if [ "$status_after_refund" != "REFUNDED" ]; then
  echo "[e2e-store] Seller order not refunded: $status_after_refund" >&2
  exit 1
fi
if [ "$seller_after_refund" -ge "$seller_after_checkout" ]; then
  echo "[e2e-store] Seller balance did not decrease after refund reversal" >&2
  exit 1
fi

echo "[e2e-store] PASS"
echo "  productId: $product_id"
echo "  orderId  : $order_id"
echo "  buyer before/after checkout: $buyer_before -> $buyer_after_checkout $CURRENCY"
echo "  seller before/after checkout/refund: $seller_before -> $seller_after_checkout -> $seller_after_refund $CURRENCY"
