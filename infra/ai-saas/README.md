# AI SaaS Portal (separe de Kobo)

Service SaaS minimal pour vendre des comptes IA:
- Auth (signup/login)
- Pricing
- Checkout + webhook (provider `mock` pour dev)
- Activation automatique LiteLLM (creation user + cle API + quotas)

## Variables d'environnement

- `APP_NAME` (default: `Kobo AI`)
- `APP_URL` (URL publique du portail)
- `DATABASE_URL` (PostgreSQL SQLAlchemy URL)
- `JWT_SECRET`
- `JWT_EXP_MINUTES` (default `43200` = 30 jours)
- `COOKIE_SECURE` (`true` si HTTPS)
- `PAYMENT_WEBHOOK_SECRET`
- `LITELLM_URL` (ex: `http://litellm.ai-dev.svc.cluster.local:4000`)
- `LITELLM_MASTER_KEY`
- `DEFAULT_MODEL` (ex: `qwen2.5-7b`)

## Lancer localement

```bash
cd infra/ai-saas
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
export DATABASE_URL="postgresql+psycopg2://ai_saas:ai_saas@localhost:5432/ai_saas"
export JWT_SECRET="change-me"
export PAYMENT_WEBHOOK_SECRET="change-me"
export LITELLM_URL="http://localhost:4000"
export LITELLM_MASTER_KEY="sk-..."
uvicorn app.main:app --host 0.0.0.0 --port 8080
```

## Flux MVP

1. User signup/login.
2. User choisit un plan.
3. Checkout mock (simuler SUCCESS).
4. Webhook traite le paiement et active l'abonnement.
5. LiteLLM genere une cle API utilisateur (visible dans dashboard).

## Kubernetes secrets

Le fichier `infra/k8s/ai-saas/base/secrets.example.yaml` est un exemple uniquement.
Ne pas l'appliquer en production. Creer `ai-saas-secrets` via `kubectl create secret ...`.

## API utile

- `GET /api/plans`
- `POST /api/auth/signup`
- `POST /api/auth/login`
- `POST /api/checkout/create`
- `POST /api/payments/webhook/mock`
- `GET /api/subscription/status`
