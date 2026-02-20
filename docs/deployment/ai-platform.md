# AI Platform (Open Source) - Dev

Cette stack déploie une IA self-hosted sur Kubernetes (k3s) avec domaines `nip.io`.

## Domaines

- UI: `http://ai-dev.kobo.79.137.32.27.nip.io`
- API Gateway: `http://ai-api-dev.kobo.79.137.32.27.nip.io`

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
MASTER_KEY="sk-dev-change-me-very-strong"
curl -sS http://ai-api-dev.kobo.79.137.32.27.nip.io/v1/models \
  -H "Authorization: Bearer ${MASTER_KEY}" | jq
```

## Premier accès UI

1. Ouvrir `http://ai-dev.kobo.79.137.32.27.nip.io`
2. Créer le premier compte admin dans OpenWebUI
3. Créer ensuite les comptes utilisateurs

## Notes

- Les secrets `ai-secrets` doivent être remplacés pour la prod.
- Le job `ollama-pull-qwen` télécharge le modèle `qwen2.5:7b-instruct-q4_K_M`.
- Sans GPU, les temps de réponse seront plus élevés qu'avec API cloud.
