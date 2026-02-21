# AI SaaS Portal - Commercial MVP

Ce module est **separe de Kobo**. Il sert a vendre des comptes IA type ChatGPT/Claude.

## Fonctionnalites implementees

1. Auth + UI SaaS
- Signup / login / logout
- Landing page style produit (moins basique)
- Dashboard utilisateur

2. Paiement sans API possible
- Mode `manual`: checkout en `PENDING`
- Validation/rejet par admin via API (secret)
- Mode `mock` conserve pour test rapide

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

Note:
- Le portail SaaS n'est pas le chat lui-meme.
- Le chat est accessible via OpenWebUI (menu `Chat IA` dans le portail).

## Flux utilisateur (sans API paiement)

1. User cree son compte sur le portail.
2. Il choisit un plan (Basic/Plus/Pro).
3. Paiement manuel hors plateforme (Mobile Money / virement).
4. Admin valide via `POST /api/admin/payments/{payment_id}/approve`.
5. Le backend provisionne LiteLLM automatiquement.
6. L'utilisateur recupere sa cle API dans son dashboard.

Header admin requis:
- `X-Admin-Secret: <ADMIN_API_SECRET>`

## Passage en production

1. Mettre un domaine reel + TLS
2. Basculer `COOKIE_SECURE=true`
3. Rotation des secrets
4. Brancher provider reel (CinetPay/Flutterwave/Stripe) en gardant le meme flux d'activation
5. Ajouter jobs de renouvellement/suspension automatique des abonnes
