# Social Wallet Backend (monolithe modulaire)

Ce projet est un squelette Spring Boot monolithique avec une architecture modulaire. L'objectif est de coder vite, sans perdre les bonnes pratiques, et pouvoir extraire des microservices plus tard si besoin.

## Stack
- Java 21
- Spring Boot 3.x
- Maven
- PostgreSQL + Flyway
- Spring Security + OAuth2 Resource Server
- Keycloak (auth externe)
- Docker / docker-compose

## Modules (packages)
- `identity`: auth, roles, sessions, devices
- `profiles`: profils + social graph
- `media`: stockage et traitement media
- `social`: posts, comments, feed
- `stories`: stories 24h
- `chat`: conversations, messages
- `wallet`: ledger, balances
- `payments`: providers, webhooks
- `notifications`: push/email/SMS
- `moderation`: moderation + trust
- `admin`: backoffice
- `shared`: cross-cutting

## Demarrage rapide (Docker)

```bash
docker compose up --build
```

Services exposes:
- API: http://localhost:8080
- Keycloak: http://localhost:8082
- Postgres: localhost:5432

## Messagerie (OpenIM)
Stack OpenIM (open-source) dans `infra/openim`.

```bash
cd infra/openim
docker compose up -d
```

## Avancements
- Messagerie: stack OpenIM ajoutee dans `infra/openim` (compose + config + .env.example).
- Services exposes (dev): 10001 (IM WS), 10002 (OpenIM API), 10008 (Chat API), 10009 (Admin API), 11001 (OpenIM Web), 10005 (MinIO).
- Front OpenIM: http://localhost:11001 (double-clic sur le titre pour configurer IMWsUrl/IMApiUrl/ChatUrl).
- Integration Spring Boot <-> OpenIM: provisionnement auto + endpoint tokens `/api/messaging/token`.

## OpenIM (backend)
Variables utiles (dev):
- `OPENIM_CHAT_URL` (default `http://localhost:10008`)
- `OPENIM_ADMIN_URL` (default `http://localhost:10009`)
- `OPENIM_ADMIN_ACCOUNT` / `OPENIM_ADMIN_PASSWORD_HASH` (defaut `chatAdmin` / md5)
- `OPENIM_PASSWORD_SALT` (sel pour generer le mot de passe OpenIM des users)

## Configuration Keycloak (dev)
1. Ouvre Keycloak (http://localhost:8082)
2. Login admin: `admin` / `admin`
3. Cree un realm `social-wallet`
4. Cree un client `social-wallet-api` (confidential ou bearer-only)
5. Utilise l issuer dans `application.yml`:

```
http://localhost:8082/realms/social-wallet
```

## JWT issuers (dev)
Pour eviter les erreurs d issuer en local (host vs docker), l API accepte 2 issuers:
- `http://localhost:8082/realms/social-wallet`
- `http://keycloak:8080/realms/social-wallet`

Le JWK set est lu sur:
`http://keycloak:8080/realms/social-wallet/protocol/openid-connect/certs`

## Endpoints de test
- Public: `GET /api/public/ping`
- Prive: `GET /api/private/me` (necessite JWT)

## i18n (FR par defaut)
- Langues supportees: `fr`, `en`
- Selection via header `Accept-Language` (ex: `fr`, `en`)
- Override possible via query param `?lang=fr` ou `?lang=en`

## Notes
- `application.yml` utilise des variables d environnement (DB_URL, DB_USER, DB_PASSWORD, KEYCLOAK_ISSUER_URI).
- `Flyway` est active, le schema sera versionne dans `src/main/resources/db/migration`.
codex resume 019b3bab-3824-7f41-8a3f-7f44cfe54824
