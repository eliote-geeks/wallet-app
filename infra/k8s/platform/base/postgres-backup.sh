#!/usr/bin/env bash
set -euo pipefail

TIMESTAMP="$(date -u +%Y%m%dT%H%M%SZ)"
TARGET_DIR="/backups/${TIMESTAMP}"
mkdir -p "${TARGET_DIR}"

for DB in "${POSTGRES_APP_DB}" "${POSTGRES_KEYCLOAK_DB}"; do
  RAW_FILE="${TARGET_DIR}/${DB}.sql.gz"
  ENC_FILE="${RAW_FILE}.enc"

  PGPASSWORD="${POSTGRES_SUPERUSER_PASSWORD}" \
    pg_dump -h social-wallet-postgres -U "${POSTGRES_USER}" -d "${DB}" | gzip -9 > "${RAW_FILE}"

  openssl enc -aes-256-cbc -pbkdf2 -salt \
    -in "${RAW_FILE}" \
    -out "${ENC_FILE}" \
    -pass env:BACKUP_ENCRYPTION_KEY

  sha256sum "${ENC_FILE}" > "${ENC_FILE}.sha256"
  rm -f "${RAW_FILE}"
done

find /backups -mindepth 1 -maxdepth 1 -type d -mtime +"${BACKUP_RETENTION_DAYS}" -exec rm -rf {} +
