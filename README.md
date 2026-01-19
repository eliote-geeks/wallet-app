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
- `store`: commerce (service Medusa)
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

## Store (Medusa)
Service Medusa dans `infra/medusa`.

```bash
cd infra/medusa
cp .env.example .env
docker compose up -d
```

## Avancements
- Messagerie: stack OpenIM ajoutee dans `infra/openim` (compose + config + .env.example).
- Services exposes (dev): 10001 (IM WS), 10002 (OpenIM API), 10008 (Chat API), 10009 (Admin API), 11001 (OpenIM Web), 10005 (MinIO).
- Front OpenIM: http://localhost:11001 (double-clic sur le titre pour configurer IMWsUrl/IMApiUrl/ChatUrl).
- Integration Spring Boot <-> OpenIM: provisionnement auto + endpoint tokens `/api/messaging/token`.
- Store: Medusa ajoute dans `infra/medusa` (compose + .env.example + server starter).
- Wallet: comptes + holds pour le checkout via wallet (tables `wallet_accounts`, `wallet_holds`).
- Seed Medusa configure en XAF (region Central Africa, pays CM).

## OpenIM (backend)
Variables utiles (dev):
- `OPENIM_CHAT_URL` (default `http://localhost:10008`)
- `OPENIM_ADMIN_URL` (default `http://localhost:10009`)
- `OPENIM_ADMIN_ACCOUNT` / `OPENIM_ADMIN_PASSWORD_HASH` (defaut `chatAdmin` / md5)
- `OPENIM_PASSWORD_SALT` (sel pour generer le mot de passe OpenIM des users)
- `OPENIM_REPAIR_ON_STARTUP` (repare les comptes OpenIM existants au demarrage)

### Reparer les anciens comptes OpenIM
Si des comptes ont ete crees avant le mapping `openim_user_id` -> `phoneNumber`,
OpenIM refusera le login (areaCode/phone manquants). Pour corriger:

```bash
OPENIM_REPAIR_ON_STARTUP=true \
OPENIM_PASSWORD_SALT=dev-openim-salt \
./mvnw -DskipTests spring-boot:run
```

Le job met a jour `phoneNumber`/`areaCode` dans OpenIM en utilisant le `openim_user_id`.
Desactive ensuite en supprimant `OPENIM_REPAIR_ON_STARTUP`.

Alternative (admin API):
- `POST /api/admin/openim/repair` (role `ADMIN` requis)
- Optionnel: `?userId=<uuid>` pour cibler un seul compte

Note: l API tente aussi une auto-reparation lors du login OpenIM si
`areaCode`/`phoneNumber` sont manquants.

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

## Auth (dev)
- Inscription/login par numero de telephone (format E.164 recommande, ex: `+237...`).
- Email optionnel.

## LiveKit (appels audio/video)
- Lancer LiveKit: `docker compose -f infra/livekit/docker-compose.yml up -d`
- Endpoint token: `POST /api/calls/token` (JWT requis) payload: `{ "roomName": "call-123", "audioOnly": true }`
- Response: `{ "roomName", "identity", "token", "livekitUrl" }`

## Wallet (dev)
- `GET /api/wallet/balance` (JWT requis) -> liste des soldes par devise
- `GET /api/wallet/balance/{currency}` (JWT requis)
- `POST /api/wallet/topup` (JWT requis) payload: `{ "amount": 15000, "currency": "XAF" }`
- `POST /api/wallet/withdraw` (JWT requis) payload: `{ "amount": 5000, "currency": "XAF", "destination": "momo" }`
- `POST /api/wallet/transfer` (JWT requis) payload: `{ "recipientId": "<uuid>", "amount": 2500, "currency": "XAF" }`
- `GET /api/wallet/transactions` (JWT requis)
- Checkout wallet: `POST /api/store/carts/{cartId}/complete` payload: `{ "payment_method": "wallet" }`
- Le wallet supporte plusieurs devises (un compte par devise) ; la devise doit matcher celle du cart Medusa.

## Admin wallet
- `GET /api/admin/wallet/ledger/diagnostics` (ADMIN) -> compare ledger vs cache
- `POST /api/admin/wallet/ledger/recalculate` (ADMIN) -> met a jour les colonnes cachees `available_amount`/`reserved_amount`

## Mobile Money (placeholder)
- `POST /api/payments/mobile-money/topups` (JWT requis) payload: `{ "amount": 15000, "currency": "XAF", "phoneNumber": "+237...", "provider": "mtn" }`
- `POST /api/payments/mobile-money/withdrawals` (JWT requis) payload: `{ "amount": 5000, "currency": "XAF", "phoneNumber": "+237...", "provider": "mtn" }`
- `POST /api/webhooks/mobile-money` (public) payload: `{ "transactionId": "<uuid>", "type": "TOPUP|WITHDRAW", "status": "SUCCESS|FAILED" }`

## i18n (FR par defaut)
- Langues supportees: `fr`, `en`
- Selection via header `Accept-Language` (ex: `fr`, `en`)
- Override possible via query param `?lang=fr` ou `?lang=en`

## Notes
- `application.yml` utilise des variables d environnement (DB_URL, DB_USER, DB_PASSWORD, KEYCLOAK_ISSUER_URI).
- `Flyway` est active, le schema sera versionne dans `src/main/resources/db/migration`.
codex resume 019b3bab-3824-7f41-8a3f-7f44cfe54824
