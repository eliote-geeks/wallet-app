#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

echo "[dev-up] Starting core services (db, keycloak)..."
docker compose up -d db keycloak

echo
echo "Core services:" 
docker ps --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}' | (head -n 1; grep -E 'social-wallet-(db|keycloak)' || true)

echo
cat <<INFO
URLs:
- Keycloak: http://localhost:8082
- Postgres: localhost:5432 (db: social_wallet)
INFO
