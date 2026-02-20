#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CLI="${SCRIPT_DIR}/litellm-account-cli.sh"

if [[ ! -x "${CLI}" ]]; then
  chmod +x "${CLI}"
fi

: "${LITELLM_MASTER_KEY:?Set LITELLM_MASTER_KEY before running this script}"

MODEL="${MODEL:-qwen2.5-7b}"

echo "Creating plan: kobo-basic"
"${CLI}" new-team \
  --alias "kobo-basic" \
  --models "${MODEL}" \
  --monthly-budget 3 \
  --rpm 20 \
  --tpm 40000 | jq '{team_id: .team_id, team_alias: .team_alias}'

echo "Creating plan: kobo-plus"
"${CLI}" new-team \
  --alias "kobo-plus" \
  --models "${MODEL}" \
  --monthly-budget 10 \
  --rpm 40 \
  --tpm 80000 | jq '{team_id: .team_id, team_alias: .team_alias}'

echo "Creating plan: kobo-pro"
"${CLI}" new-team \
  --alias "kobo-pro" \
  --models "${MODEL}" \
  --monthly-budget 30 \
  --rpm 80 \
  --tpm 160000 | jq '{team_id: .team_id, team_alias: .team_alias}'
