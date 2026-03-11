# Deployment (Hostinger VPS) - Docker Compose

Objectif: deployer une version "core" stable (API + Postgres + Keycloak) sur un VPS Hostinger.

Note: pour 10k users, OpenIM (chat) et LiveKit (calls) doivent idealement etre sur des VPS separes.
Ce guide commence volontairement par la stack core.

## Prerequis
- Ubuntu 22.04/24.04 (ou equivalent)
- Un domaine (optionnel au debut)

## 1) Installer Docker

```bash
sudo apt-get update
sudo apt-get install -y ca-certificates curl git

# Docker Engine + compose plugin
curl -fsSL https://get.docker.com | sudo sh

# (Optionnel) utiliser docker sans sudo
sudo usermod -aG docker $USER
# Puis reconnecte-toi
```

Verifier:

```bash
docker --version
docker compose version
```

## 2) Ouvrir les ports (MVP)

MVP sans reverse-proxy:
- API: `8080`
- Keycloak: `8082`

Si tu utilises UFW:

```bash
sudo ufw allow 8080/tcp
sudo ufw allow 8082/tcp
sudo ufw enable
sudo ufw status
```

Recommandation prod:
- Mettre un reverse-proxy (Traefik/Nginx) + HTTPS.
- Ne pas exposer Keycloak publiquement (ou le proteger).

## 3) Recuperer le code

```bash
git clone git@github.com:eliote-geeks/wallet-app.git
cd wallet-app

git checkout dev
```

## 4) Configuration `.env`

```bash
cp .env.example .env
nano .env
```

A changer au minimum:
- `POSTGRES_PASSWORD`
- `KEYCLOAK_ADMIN_PASSWORD`

## 5) Lancer la stack core

```bash
docker compose up -d --build

docker ps
```

URLs:
- API: `http://<VPS_IP>:8080/swagger-ui/index.html`
- Keycloak: `http://<VPS_IP>:8082`

## 6) Initialiser Keycloak (realm + client)

Le projet contient des exports:
- `docs/keycloak/realm-social-wallet.json`
- `docs/keycloak/client-social-wallet-api.json`

Option simple (UI):
1. Connecte-toi a Keycloak (`admin` + `KEYCLOAK_ADMIN_PASSWORD`).
2. Cree/import le realm `social-wallet` depuis `docs/keycloak/realm-social-wallet.json`.
3. Verifie le client `social-wallet-api`.

## 7) Verification rapide

```bash
curl -s http://localhost:8080/actuator/health
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8080/swagger-ui/index.html
```

## 8) Chat / Calls / Store (optionnels)

Ces stacks sont dans `infra/`:
- OpenIM: `infra/openim` (tres gourmand: Kafka/Mongo/Redis/MinIO)
- LiveKit: `infra/livekit`
- Medusa: `infra/medusa`

Recommandation:
- Si tu as un seul VPS au debut, lance les services un par un et surveille la RAM/CPU.
- Pour la stabilite, deplace OpenIM sur un VPS dedie.
