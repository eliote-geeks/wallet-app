#!/usr/bin/env bash
set -euo pipefail

NAMESPACE="${1:-kobo-dev}"
if [[ -z "${BACKUP_ENCRYPTION_KEY:-}" ]]; then
  echo "BACKUP_ENCRYPTION_KEY must be set"
  exit 1
fi

POD="$(kubectl -n "${NAMESPACE}" get pod -l app.kubernetes.io/name=social-wallet-postgres -o jsonpath='{.items[0].metadata.name}')"
LATEST_DIR="$(kubectl -n "${NAMESPACE}" exec "$POD" -- sh -lc 'ls -1 /backups 2>/dev/null | sort | tail -n1')"

if [[ -z "${LATEST_DIR}" ]]; then
  echo "No backup directory found in /backups"
  exit 1
fi

RESTORE_DB="restore_validation_$(date +%s)"

kubectl -n "${NAMESPACE}" exec "$POD" -- sh -lc "psql -U postgres -d postgres -c 'CREATE DATABASE ${RESTORE_DB};'"

kubectl -n "${NAMESPACE}" exec "$POD" -- sh -lc \
  "openssl enc -d -aes-256-cbc -pbkdf2 -in /backups/${LATEST_DIR}/social_wallet.sql.gz.enc -out /tmp/social_wallet.sql.gz -pass pass:'${BACKUP_ENCRYPTION_KEY}'"

kubectl -n "${NAMESPACE}" exec "$POD" -- sh -lc \
  "gunzip -c /tmp/social_wallet.sql.gz | psql -U postgres -d ${RESTORE_DB}"

TABLE_COUNT="$(kubectl -n "${NAMESPACE}" exec "$POD" -- sh -lc "psql -U postgres -d ${RESTORE_DB} -tAc \"SELECT count(*) FROM information_schema.tables WHERE table_schema='public'\"")"

echo "Restore validation DB: ${RESTORE_DB}, public tables: ${TABLE_COUNT}"

kubectl -n "${NAMESPACE}" exec "$POD" -- sh -lc "psql -U postgres -d postgres -c 'DROP DATABASE ${RESTORE_DB};'"
