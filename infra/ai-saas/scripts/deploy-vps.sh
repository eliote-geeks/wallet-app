#!/usr/bin/env bash
set -euo pipefail

# Usage:
#   HOST=79.137.32.27 DEPLOY_USER=ubuntu ./infra/ai-saas/scripts/deploy-vps.sh

HOST="${HOST:-79.137.32.27}"
DEPLOY_USER="${DEPLOY_USER:-${USER:-ubuntu}}"
IMAGE="ai-saas-portal:dev-local"
APP_URL="${APP_URL:-https://ai-portal-dev.${HOST}.nip.io}"
REMOTE_APP_DIR="/tmp/ai-saas-src"
REMOTE_K8S_DIR="/tmp/ai-saas-k8s"
REMOTE_OUT_FILE="/tmp/ai-saas-secrets.out"

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"

echo "[1/6] Upload sources + manifests to server..."
ssh "${DEPLOY_USER}@${HOST}" "rm -rf ${REMOTE_APP_DIR} ${REMOTE_K8S_DIR} && mkdir -p ${REMOTE_APP_DIR} ${REMOTE_K8S_DIR}"
scp -r "${REPO_ROOT}/infra/ai-saas"/* "${DEPLOY_USER}@${HOST}:${REMOTE_APP_DIR}/"
scp -r "${REPO_ROOT}/infra/k8s/ai-saas"/* "${DEPLOY_USER}@${HOST}:${REMOTE_K8S_DIR}/"

echo "[2/6] Build docker image on server..."
ssh "${DEPLOY_USER}@${HOST}" "set -euo pipefail
  cd ${REMOTE_APP_DIR}
  sudo docker build -t ${IMAGE} .
  sudo docker save -o /tmp/ai-saas-portal-dev-local.tar ${IMAGE}
"

echo "[3/6] Import image into k3s containerd..."
ssh "${DEPLOY_USER}@${HOST}" "sudo k3s ctr images import /tmp/ai-saas-portal-dev-local.tar"

echo "[4/6] Create runtime secrets..."
JWT_SECRET="$(openssl rand -hex 32)"
PAYMENT_WEBHOOK_SECRET="$(openssl rand -hex 24)"
ADMIN_API_SECRET="$(openssl rand -hex 24)"
DB_PASSWORD="ai_saas_change_me"

ssh "${DEPLOY_USER}@${HOST}" "set -euo pipefail
  LITELLM_MASTER_KEY=\$(sudo kubectl -n ai-dev get secret ai-secrets -o jsonpath='{.data.LITELLM_MASTER_KEY}' | base64 -d)
  sudo kubectl create namespace ai-saas-dev --dry-run=client -o yaml | sudo kubectl apply -f -
  sudo kubectl -n ai-saas-dev create secret generic ai-saas-secrets \\
    --from-literal=POSTGRES_DB=ai_saas \\
    --from-literal=POSTGRES_USER=ai_saas \\
    --from-literal=POSTGRES_PASSWORD='${DB_PASSWORD}' \\
    --from-literal=DATABASE_URL='postgresql+psycopg2://ai_saas:${DB_PASSWORD}@ai-saas-postgres:5432/ai_saas' \\
    --from-literal=JWT_SECRET='${JWT_SECRET}' \\
    --from-literal=PAYMENT_WEBHOOK_SECRET='${PAYMENT_WEBHOOK_SECRET}' \\
    --from-literal=ADMIN_API_SECRET='${ADMIN_API_SECRET}' \\
    --from-literal=LITELLM_MASTER_KEY=\"\${LITELLM_MASTER_KEY}\" \\
    --dry-run=client -o yaml | sudo kubectl apply -f -
  {
    echo WEBHOOK_SECRET=${PAYMENT_WEBHOOK_SECRET}
    echo ADMIN_API_SECRET=${ADMIN_API_SECRET}
  } > ${REMOTE_OUT_FILE}
"

echo "[5/6] Apply manifests + update image/env..."
ssh "${DEPLOY_USER}@${HOST}" "set -euo pipefail
  sudo kubectl apply -k ${REMOTE_K8S_DIR}/overlays/dev
  sudo kubectl -n ai-saas-dev set image deployment/ai-saas-portal ai-saas-portal=${IMAGE}
  sudo kubectl -n ai-saas-dev set env deployment/ai-saas-portal APP_URL='${APP_URL}'
  sudo kubectl -n ai-saas-dev rollout restart deployment/ai-saas-portal
"

echo "[6/6] Wait rollout + print status..."
ssh "${DEPLOY_USER}@${HOST}" "sudo kubectl -n ai-saas-dev rollout status statefulset/ai-saas-postgres --timeout=300s && sudo kubectl -n ai-saas-dev rollout status deployment/ai-saas-portal --timeout=300s && sudo kubectl -n ai-saas-dev get pods,svc,ingress && cat ${REMOTE_OUT_FILE}"

echo
echo "Portal URL: ${APP_URL}"
