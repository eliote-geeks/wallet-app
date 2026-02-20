#!/usr/bin/env bash
set -euo pipefail

function db_exists() {
  psql -U "$POSTGRES_USER" -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname='${1}'" | grep -q 1
}

function role_exists() {
  psql -U "$POSTGRES_USER" -d postgres -tAc "SELECT 1 FROM pg_roles WHERE rolname='${1}'" | grep -q 1
}

if ! db_exists "${POSTGRES_APP_DB}"; then
  psql -U "$POSTGRES_USER" -d postgres -c "CREATE DATABASE ${POSTGRES_APP_DB};"
fi

if ! db_exists "${POSTGRES_KEYCLOAK_DB}"; then
  psql -U "$POSTGRES_USER" -d postgres -c "CREATE DATABASE ${POSTGRES_KEYCLOAK_DB};"
fi

if ! role_exists "${POSTGRES_APP_USER}"; then
  psql -U "$POSTGRES_USER" -d postgres -c "CREATE ROLE ${POSTGRES_APP_USER} LOGIN PASSWORD '${POSTGRES_APP_PASSWORD}';"
else
  psql -U "$POSTGRES_USER" -d postgres -c "ALTER ROLE ${POSTGRES_APP_USER} WITH PASSWORD '${POSTGRES_APP_PASSWORD}';"
fi

if ! role_exists "${POSTGRES_KEYCLOAK_USER}"; then
  psql -U "$POSTGRES_USER" -d postgres -c "CREATE ROLE ${POSTGRES_KEYCLOAK_USER} LOGIN PASSWORD '${POSTGRES_KEYCLOAK_PASSWORD}';"
else
  psql -U "$POSTGRES_USER" -d postgres -c "ALTER ROLE ${POSTGRES_KEYCLOAK_USER} WITH PASSWORD '${POSTGRES_KEYCLOAK_PASSWORD}';"
fi

psql -U "$POSTGRES_USER" -d postgres -c "GRANT ALL PRIVILEGES ON DATABASE ${POSTGRES_APP_DB} TO ${POSTGRES_APP_USER};"
psql -U "$POSTGRES_USER" -d postgres -c "GRANT ALL PRIVILEGES ON DATABASE ${POSTGRES_KEYCLOAK_DB} TO ${POSTGRES_KEYCLOAK_USER};"
