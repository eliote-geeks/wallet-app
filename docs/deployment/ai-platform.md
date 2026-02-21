# AI Platform (Open Source) - Dev

Cette stack déploie une IA self-hosted sur Kubernetes (k3s) avec domaines `nip.io`.

## Domaines

- UI: `http://ai-dev.79.137.32.27.nip.io`
- API Gateway: `http://ai-api-dev.79.137.32.27.nip.io`

Note: les certificats Let's Encrypt pour `nip.io` sont bloqués par rate limit global. Utiliser un domaine dédié pour activer TLS fiable en production.

## Composants

- `ollama`: serveur de modèles LLM locaux
- `litellm`: gateway API unique + clé maître + routing modèle
- `openwebui`: interface web avec comptes utilisateurs

## Déploiement

```bash
kubectl apply -k infra/k8s/ai/overlays/dev
kubectl -n ai-dev get pods
```

## Vérification API

```bash
MASTER_KEY="$(kubectl -n ai-dev get secret ai-secrets -o jsonpath='{.data.LITELLM_MASTER_KEY}' | base64 -d)"
curl -sS http://ai-api-dev.79.137.32.27.nip.io/v1/models \
  -H "Authorization: Bearer ${MASTER_KEY}" | jq
```

## Premier accès UI

1. Ouvrir `http://ai-dev.79.137.32.27.nip.io`
2. Créer le premier compte admin dans OpenWebUI
3. Créer ensuite les comptes utilisateurs

## Comptes API (vente de comptes)

Scripts inclus:
- `infra/k8s/ai/scripts/litellm-account-cli.sh`
- `infra/k8s/ai/scripts/bootstrap-dev-plans.sh`

Prérequis:
```bash
sudo apt-get update && sudo apt-get install -y jq curl
export LITELLM_URL="http://ai-api-dev.79.137.32.27.nip.io"
export LITELLM_MASTER_KEY="$(kubectl -n ai-dev get secret ai-secrets -o jsonpath='{.data.LITELLM_MASTER_KEY}' | base64 -d)"
chmod +x infra/k8s/ai/scripts/*.sh
```

1. Créer des plans (teams) avec quota:
```bash
infra/k8s/ai/scripts/bootstrap-dev-plans.sh
infra/k8s/ai/scripts/litellm-account-cli.sh list-teams
```

Le script de bootstrap est idempotent: il n'ajoute pas de doublons si un plan existe déjà.

2. Créer un utilisateur API:
```bash
infra/k8s/ai/scripts/litellm-account-cli.sh new-user \
  --id user_demo_001 \
  --email demo1@oi.local
```

3. Générer une clé API liée au user:
```bash
infra/k8s/ai/scripts/litellm-account-cli.sh new-key \
  --user-id user_demo_001 \
  --models qwen2.5-7b \
  --monthly-budget 3 \
  --duration 30d \
  --rpm 20 \
  --tpm 40000
```

4. Tester la clé utilisateur:
```bash
USER_KEY="sk-..."
curl -sS "${LITELLM_URL}/v1/chat/completions" \
  -H "Authorization: Bearer ${USER_KEY}" \
  -H "Content-Type: application/json" \
  -d '{"model":"qwen2.5-7b","messages":[{"role":"user","content":"Bonjour"}]}' | jq
```

Note: la clé utilisateur est affichée une seule fois lors de la génération.

## Notes

- Les secrets `ai-secrets` doivent être remplacés pour la prod.
- Le job `ollama-pull-qwen` télécharge le modèle `qwen2.5:7b-instruct-q4_K_M`.
- Sans GPU, les temps de réponse seront plus élevés qu'avec API cloud.
