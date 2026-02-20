#!/usr/bin/env bash
set -euo pipefail

KIBANA_URL="${KIBANA_URL:-http://localhost:5602}"
ELASTIC_USERNAME="${ELASTIC_USERNAME:-elastic}"
ELASTIC_PASSWORD="${ELASTIC_PASSWORD:-}"

if [[ -z "${ELASTIC_PASSWORD}" ]]; then
  echo "ELASTIC_PASSWORD is required"
  exit 1
fi
if ! command -v jq >/dev/null 2>&1; then
  echo "jq is required"
  exit 1
fi

kbn() {
  curl -sS -u "${ELASTIC_USERNAME}:${ELASTIC_PASSWORD}" \
    -H "kbn-xsrf: true" \
    -H "Content-Type: application/json" \
    "$@"
}

ensure_rule() {
  local name="$1"
  local payload_file="$2"
  local name_q="${name// /%20}"

  local existing_id
  existing_id="$(kbn "${KIBANA_URL}/api/alerting/rules/_find?per_page=100&search_fields=name&search=${name_q}" | jq -r --arg n "${name}" '.data[] | select(.name == $n) | .id' | head -n1)"

  if [[ -n "${existing_id}" ]]; then
    kbn -X DELETE "${KIBANA_URL}/api/alerting/rule/${existing_id}" >/dev/null
  fi

  kbn -X POST "${KIBANA_URL}/api/alerting/rule" --data-binary "@${payload_file}" >/dev/null
  echo "rule_applied=${name}"
}

TMP_DIR="$(mktemp -d)"
trap 'rm -rf "${TMP_DIR}"' EXIT

cat > "${TMP_DIR}/k8s-warning-events.json" <<'JSON'
{
  "name": "Kobo Kubernetes Warning Events",
  "consumer": "stackAlerts",
  "rule_type_id": ".es-query",
  "schedule": {"interval": "1m"},
  "params": {
    "searchType": "esQuery",
    "esQuery": "{\"query\":{\"bool\":{\"filter\":[{\"term\":{\"event.dataset\":\"kubernetes.event\"}},{\"term\":{\"kubernetes.event.type\":\"Warning\"}}]}}}",
    "timeField": "@timestamp",
    "index": ["metricbeat-*", ".ds-metricbeat-*"],
    "size": 0,
    "aggType": "count",
    "groupBy": "all",
    "termSize": 5,
    "thresholdComparator": ">",
    "threshold": [0],
    "timeWindowSize": 5,
    "timeWindowUnit": "m",
    "excludeHitsFromPreviousRun": true
  },
  "actions": [],
  "notify_when": "onActiveAlert",
  "tags": ["kobo", "ops", "slo"]
}
JSON

cat > "${TMP_DIR}/backend-warning-events.json" <<'JSON'
{
  "name": "Kobo Backend Warning Events",
  "consumer": "stackAlerts",
  "rule_type_id": ".es-query",
  "schedule": {"interval": "1m"},
  "params": {
    "searchType": "esQuery",
    "esQuery": "{\"query\":{\"bool\":{\"filter\":[{\"term\":{\"event.dataset\":\"kubernetes.event\"}},{\"term\":{\"kubernetes.event.type\":\"Warning\"}},{\"wildcard\":{\"kubernetes.event.involved_object.name\":\"social-wallet-backend*\"}}]}}}",
    "timeField": "@timestamp",
    "index": ["metricbeat-*", ".ds-metricbeat-*"],
    "size": 0,
    "aggType": "count",
    "groupBy": "all",
    "termSize": 5,
    "thresholdComparator": ">",
    "threshold": [0],
    "timeWindowSize": 5,
    "timeWindowUnit": "m",
    "excludeHitsFromPreviousRun": true
  },
  "actions": [],
  "notify_when": "onActiveAlert",
  "tags": ["kobo", "ops", "slo", "backend"]
}
JSON

cat > "${TMP_DIR}/keycloak-warning-events.json" <<'JSON'
{
  "name": "Kobo Keycloak Warning Events",
  "consumer": "stackAlerts",
  "rule_type_id": ".es-query",
  "schedule": {"interval": "1m"},
  "params": {
    "searchType": "esQuery",
    "esQuery": "{\"query\":{\"bool\":{\"filter\":[{\"term\":{\"event.dataset\":\"kubernetes.event\"}},{\"term\":{\"kubernetes.event.type\":\"Warning\"}},{\"wildcard\":{\"kubernetes.event.involved_object.name\":\"social-wallet-keycloak*\"}}]}}}",
    "timeField": "@timestamp",
    "index": ["metricbeat-*", ".ds-metricbeat-*"],
    "size": 0,
    "aggType": "count",
    "groupBy": "all",
    "termSize": 5,
    "thresholdComparator": ">",
    "threshold": [0],
    "timeWindowSize": 5,
    "timeWindowUnit": "m",
    "excludeHitsFromPreviousRun": true
  },
  "actions": [],
  "notify_when": "onActiveAlert",
  "tags": ["kobo", "ops", "slo", "keycloak"]
}
JSON

ensure_rule "Kobo Kubernetes Warning Events" "${TMP_DIR}/k8s-warning-events.json"
ensure_rule "Kobo Backend Warning Events" "${TMP_DIR}/backend-warning-events.json"
ensure_rule "Kobo Keycloak Warning Events" "${TMP_DIR}/keycloak-warning-events.json"

echo "kibana_rules_bootstrap=OK"
