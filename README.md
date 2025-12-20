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
- Keycloak: http://localhost:8081
- Postgres: localhost:5432

## Configuration Keycloak (dev)
1. Ouvre Keycloak (http://localhost:8081)
2. Login admin: `admin` / `admin`
3. Cree un realm `social-wallet`
4. Cree un client `social-wallet-api` (confidential ou bearer-only)
5. Utilise l issuer dans `application.yml`:

```
http://localhost:8081/realms/social-wallet
```

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
