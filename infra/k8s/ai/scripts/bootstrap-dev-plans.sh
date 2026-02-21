#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CLI="${SCRIPT_DIR}/litellm-account-cli.sh"

if [[ ! -x "${CLI}" ]]; then
  chmod +x "${CLI}"
fi

: "${LITELLM_MASTER_KEY:?Set LITELLM_MASTER_KEY before running this script}"

MODEL="${MODEL:-qwen2.5-7b}"

create_if_missing() {
  local alias="$1"
  shift
  local existing_id
  existing_id="$("${CLI}" list-teams | jq -r --arg alias "${alias}" '.[] | select(.team_alias == $alias) | .team_id' | head -n 1 || true)"
  if [[ -n "${existing_id}" ]]; then
    echo "Plan already exists: ${alias} (${existing_id})"
    return
  fi
  echo "Creating plan: ${alias}"
  "${CLI}" new-team --alias "${alias}" "$@" | jq '{team_id: .team_id, team_alias: .team_alias}'
}

create_if_missing "oi-basic" \
  --models "${MODEL}" \
  --monthly-budget 3 \
  --rpm 20 \
  --tpm 40000

create_if_missing "oi-plus" \
  --models "${MODEL}" \
  --monthly-budget 10 \
  --rpm 40 \
  --tpm 80000

create_if_missing "oi-pro" \
  --models "${MODEL}" \
  --monthly-budget 30 \
  --rpm 80 \
  --tpm 160000
