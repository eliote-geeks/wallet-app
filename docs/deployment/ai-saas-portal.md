# AI SaaS Portal - Commercial MVP

Ce module est **separe de Kobo**. Il sert a vendre des comptes IA type ChatGPT/Claude.

## Fonctionnalites implementees

1. Auth + UI SaaS
- Signup / login / logout
- Pricing page
- Dashboard utilisateur

2. Paiement + webhook (mock pour dev)
- Creation checkout
- Simulateur de paiement `SUCCESS` / `FAILED`
- Endpoint webhook securise par `X-Webhook-Secret`

3. Activation automatique LiteLLM
- Creation user LiteLLM
- Generation cle API utilisateur
- Quotas par plan (budget, RPM, TPM, modele)

## Emplacement du code

- Backend + front web: `infra/ai-saas`
- Kubernetes manifests: `infra/k8s/ai-saas`
- Script de deploiement VPS: `infra/ai-saas/scripts/deploy-vps.sh`

Note secrets:
- Exemple: `infra/k8s/ai-saas/base/secrets.example.yaml`
- Secret reel a creer hors Git: `ai-saas-secrets`

## URL dev

- Portail SaaS: `http://ai-portal-dev.kobo.79.137.32.27.nip.io`
- LiteLLM API: `http://ai-api-dev.kobo.79.137.32.27.nip.io`
- OpenWebUI: `http://ai-dev.kobo.79.137.32.27.nip.io`

## Flux utilisateur

1. User cree son compte sur le portail.
2. Il choisit un plan (Basic/Plus/Pro).
3. Paiement (mock en dev).
4. Webhook valide le paiement.
5. Le backend provisionne LiteLLM automatiquement.
6. L'utilisateur recupere sa cle API dans son dashboard.

## Passage en production

1. Remplacer payment provider `mock` par provider reel (CinetPay/Flutterwave/Stripe)
2. Mettre un domaine reel + TLS
3. Basculer `COOKIE_SECURE=true`
4. Rotation des secrets
5. Ajouter jobs de renouvellement/suspension automatique des abonnes
