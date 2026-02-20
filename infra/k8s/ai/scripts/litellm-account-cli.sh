#!/usr/bin/env bash
set -euo pipefail

if ! command -v jq >/dev/null 2>&1; then
  echo "jq is required" >&2
  exit 1
fi

if ! command -v curl >/dev/null 2>&1; then
  echo "curl is required" >&2
  exit 1
fi

LITELLM_URL="${LITELLM_URL:-http://ai-api-dev.kobo.79.137.32.27.nip.io}"
LITELLM_MASTER_KEY="${LITELLM_MASTER_KEY:-}"

usage() {
  cat <<'EOF'
LiteLLM account CLI

Required environment:
  LITELLM_MASTER_KEY=sk-...
Optional environment:
  LITELLM_URL=http://ai-api-dev.kobo.79.137.32.27.nip.io

Commands:
  new-team   --alias NAME [--models m1,m2] [--monthly-budget USD] [--rpm N] [--tpm N]
  new-user   --id USER_ID --email EMAIL [--role internal_user|internal_user_viewer]
  new-key    --user-id USER_ID [--team-id TEAM_ID] [--models m1,m2] [--monthly-budget USD] [--duration 30d] [--rpm N] [--tpm N]
  list-teams
  list-users
  list-keys
EOF
}

require_master_key() {
  if [[ -z "${LITELLM_MASTER_KEY}" ]]; then
    echo "Set LITELLM_MASTER_KEY before running this command." >&2
    exit 1
  fi
}

api_post() {
  local path="$1"
  local payload="$2"

  curl -fsS --retry 3 \
    -H "Authorization: Bearer ${LITELLM_MASTER_KEY}" \
    -H "Content-Type: application/json" \
    -X POST \
    -d "${payload}" \
    "${LITELLM_URL}${path}"
}

api_get() {
  local path="$1"
  curl -fsS --retry 3 \
    -H "Authorization: Bearer ${LITELLM_MASTER_KEY}" \
    "${LITELLM_URL}${path}"
}

comma_csv_to_json_array() {
  local csv="${1:-}"
  if [[ -z "${csv}" ]]; then
    echo "[]"
    return
  fi

  jq -cn --arg csv "${csv}" '
    ($csv | split(",") | map(gsub("^\\s+|\\s+$"; "")) | map(select(length > 0)))
  '
}

cmd="${1:-}"
shift || true

case "${cmd}" in
  new-team)
    require_master_key
    alias_name=""
    models_csv=""
    monthly_budget="0"
    rpm="0"
    tpm="0"
    while [[ $# -gt 0 ]]; do
      case "$1" in
        --alias) alias_name="$2"; shift 2 ;;
        --models) models_csv="$2"; shift 2 ;;
        --monthly-budget) monthly_budget="$2"; shift 2 ;;
        --rpm) rpm="$2"; shift 2 ;;
        --tpm) tpm="$2"; shift 2 ;;
        *) echo "Unknown option: $1" >&2; exit 1 ;;
      esac
    done

    if [[ -z "${alias_name}" ]]; then
      echo "--alias is required" >&2
      exit 1
    fi

    models_json="$(comma_csv_to_json_array "${models_csv}")"
    payload="$(jq -cn \
      --arg alias "${alias_name}" \
      --argjson models "${models_json}" \
      --argjson budget "${monthly_budget}" \
      --argjson rpm "${rpm}" \
      --argjson tpm "${tpm}" '
      {
        team_alias: $alias,
        models: $models,
        max_budget: $budget,
        budget_duration: "30d",
        rpm_limit: (if $rpm > 0 then $rpm else null end),
        tpm_limit: (if $tpm > 0 then $tpm else null end)
      }')"

    api_post "/team/new" "${payload}" | jq
    ;;

  new-user)
    require_master_key
    user_id=""
    email=""
    role="internal_user"
    while [[ $# -gt 0 ]]; do
      case "$1" in
        --id) user_id="$2"; shift 2 ;;
        --email) email="$2"; shift 2 ;;
        --role) role="$2"; shift 2 ;;
        *) echo "Unknown option: $1" >&2; exit 1 ;;
      esac
    done

    if [[ -z "${user_id}" || -z "${email}" ]]; then
      echo "--id and --email are required" >&2
      exit 1
    fi

    payload="$(jq -cn \
      --arg user_id "${user_id}" \
      --arg user_email "${email}" \
      --arg user_role "${role}" '
      {
        user_id: $user_id,
        user_email: $user_email,
        user_role: $user_role
      }')"

    api_post "/user/new" "${payload}" | jq
    ;;

  new-key)
    require_master_key
    user_id=""
    team_id=""
    models_csv=""
    monthly_budget="0"
    duration="30d"
    rpm="0"
    tpm="0"
    while [[ $# -gt 0 ]]; do
      case "$1" in
        --user-id) user_id="$2"; shift 2 ;;
        --team-id) team_id="$2"; shift 2 ;;
        --models) models_csv="$2"; shift 2 ;;
        --monthly-budget) monthly_budget="$2"; shift 2 ;;
        --duration) duration="$2"; shift 2 ;;
        --rpm) rpm="$2"; shift 2 ;;
        --tpm) tpm="$2"; shift 2 ;;
        *) echo "Unknown option: $1" >&2; exit 1 ;;
      esac
    done

    if [[ -z "${user_id}" ]]; then
      echo "--user-id is required" >&2
      exit 1
    fi

    models_json="$(comma_csv_to_json_array "${models_csv}")"
    payload="$(jq -cn \
      --arg user_id "${user_id}" \
      --arg team_id "${team_id}" \
      --argjson models "${models_json}" \
      --argjson budget "${monthly_budget}" \
      --arg duration "${duration}" \
      --argjson rpm "${rpm}" \
      --argjson tpm "${tpm}" '
      {
        user_id: $user_id,
        models: $models,
        max_budget: $budget,
        budget_duration: $duration,
        duration: $duration,
        rpm_limit: (if $rpm > 0 then $rpm else null end),
        tpm_limit: (if $tpm > 0 then $tpm else null end),
        team_id: (if ($team_id | length) > 0 then $team_id else null end)
      }')"

    api_post "/key/generate" "${payload}" | jq
    ;;

  list-users)
    require_master_key
    api_get "/user/list" | jq
    ;;

  list-teams)
    require_master_key
    api_get "/team/list" | jq
    ;;

  list-keys)
    require_master_key
    api_get "/key/list" | jq
    ;;

  ""|"-h"|"--help"|"help")
    usage
    ;;

  *)
    echo "Unknown command: ${cmd}" >&2
    usage
    exit 1
    ;;
esac
