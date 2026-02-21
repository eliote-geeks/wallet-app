# AI SaaS Portal (separe de l'app principale)

Service SaaS pour vendre des comptes IA:
- Auth (signup/login)
- Pricing
- Checkout
- Activation automatique LiteLLM (creation user + cle API + quotas)

## Variables d'environnement

- `APP_NAME` (default: `Father Paul Assistant`)
- `APP_URL` (URL publique du portail)
- `DATABASE_URL` (PostgreSQL SQLAlchemy URL)
- `JWT_SECRET`
- `JWT_EXP_MINUTES` (default `43200` = 30 jours)
- `COOKIE_SECURE` (`true` si HTTPS)
- `PAYMENT_MODE` (`mock` ou `manual`)
- `PAYMENT_WEBHOOK_SECRET`
- `ADMIN_API_SECRET` (obligatoire pour valider/rejeter paiements manuels)
- `MANUAL_PAYMENT_INSTRUCTIONS`
- `CHAT_UI_URL` (lien de l'interface chat, ex OpenWebUI)
- `CONTACT_EMAIL` (default: `pauleliote97@gmail.com`)
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
export PAYMENT_MODE="manual"
export PAYMENT_WEBHOOK_SECRET="change-me"
export ADMIN_API_SECRET="change-admin-secret"
export MANUAL_PAYMENT_INSTRUCTIONS="Paye via Mobile Money puis partage la reference"
export LITELLM_URL="http://localhost:4000"
export LITELLM_MASTER_KEY="sk-..."
uvicorn app.main:app --host 0.0.0.0 --port 8080
```

## Sans API de paiement: comment ca marche

1. L'utilisateur lance checkout (status `PENDING`).
2. Il paie hors plateforme (Mobile Money, virement, etc.).
3. L'admin valide le paiement via endpoint admin.
4. Le backend active l'abonnement et genere la cle API LiteLLM.

Endpoints admin manuels:

- `GET /api/admin/payments/pending`
- `POST /api/admin/payments/{payment_id}/approve`
- `POST /api/admin/payments/{payment_id}/reject`

Header requis:

- `X-Admin-Secret: <ADMIN_API_SECRET>`

Exemple approbation:

```bash
curl -X POST "http://localhost:8080/api/admin/payments/<payment_id>/approve" \
  -H "Content-Type: application/json" \
  -H "X-Admin-Secret: change-admin-secret" \
  -d '{"provider_ref":"momo-12345","note":"Paiement recu"}'
```

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
- `GET /api/admin/payments/pending`
- `POST /api/admin/payments/{payment_id}/approve`
- `POST /api/admin/payments/{payment_id}/reject`
