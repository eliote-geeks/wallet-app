# Kobo Backend Deployment (K3s + GitHub Actions)

Ce guide deploie `social-wallet-backend` sur Kubernetes (k3s) avec CI/CD GitHub Actions.

## 1. Strategie branches

- `feature/*` : developpement
- `dev` : environnement de recette (`kobo-dev`)
- `main` : production (`kobo-prod`)

## 2. Workflows actifs

- `.github/workflows/backend-ci.yml`
  - Trigger: push (`feature/*`, `dev`, `main`) + PR (`dev`, `main`) + manuel
  - Actions: tests Maven + build JAR
- `.github/workflows/backend-cd.yml`
  - Trigger: push `dev` ou `main`, ou manuel (`dev|prod`)
  - Actions: build image Docker, push GHCR, deploy K8s

## 3. Pre-requis cluster

Installer k3s + ingress nginx dans le VPS.

```bash
curl -sfL https://get.k3s.io | sh -
sudo kubectl get nodes
```

Exporter un kubeconfig lisible par GitHub Actions:

```bash
sudo cat /etc/rancher/k3s/k3s.yaml
```

Remplacer `127.0.0.1` par l IP publique du VPS dans ce kubeconfig avant de le stocker dans GitHub.

## 4. Secrets GitHub a configurer

Configurer les secrets dans chaque environnement GitHub:

- Environnement `dev` (branche `dev`)
- Environnement `production` (branche `main`)

Secrets requis:

- `KUBE_CONFIG`
- `DB_URL`
- `DB_USER`
- `DB_PASSWORD`
- `KEYCLOAK_ISSUER_URI`
- `KEYCLOAK_JWK_SET_URI`
- `MEDUSA_BASE_URL`
- `MEDUSA_PUBLISHABLE_KEY`
- `MEDUSA_ADMIN_TOKEN`
- `MEDUSA_WEBHOOK_SECRET`
- `STORE_PLATFORM_FEE_BPS`

## 5. Deployment manifests

- Base: `infra/k8s/backend/base`
- Overlay dev: `infra/k8s/backend/overlays/dev`
- Overlay prod: `infra/k8s/backend/overlays/prod`

Changer les hosts ingress:

- `api-dev.kobo.your-domain.tld`
- `api.kobo.your-domain.tld`

## 6. Verification via GitHub CLI

Lister workflows:

```bash
gh workflow list --repo eliote-geeks/wallet-app
```

Lancer CI manuellement:

```bash
gh workflow run backend-ci.yml --repo eliote-geeks/wallet-app --ref dev
```

Voir runs:

```bash
gh run list --repo eliote-geeks/wallet-app --limit 10
gh run watch --repo eliote-geeks/wallet-app <run-id>
```

Lancer deployment manuel:

```bash
gh workflow run backend-cd.yml --repo eliote-geeks/wallet-app --ref dev -f target_env=dev
```

## 7. Check post-deploy

```bash
kubectl -n kobo-dev get deploy,svc,ingress,pods
kubectl -n kobo-dev rollout status deploy/social-wallet-backend
kubectl -n kobo-dev logs deploy/social-wallet-backend --tail=150
```

