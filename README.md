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

## Redemarage rapide (dev)

Pour eviter un `docker compose up --build` (lent), utilise les scripts:

```bash
./scripts/dev-up.sh
./scripts/api-start.sh
./scripts/status.sh
```

Arret:

```bash
./scripts/api-stop.sh
./scripts/dev-down.sh
```

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
- `OPENIM_REST_URL` (default `http://localhost:10002`)
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

## RBAC (dev)
- Roles applicatifs: `USER`, `SELLER`, `MODERATOR`, `ADMIN`.
- Les roles Keycloak dans le JWT sont normalises en backend pour eviter les ecarts de casse (`user` == `USER`).
- Endpoints admin: `/api/admin/**` -> role `ADMIN` obligatoire.
- Endpoints metier proteges (`/api/profiles/**`, `/api/stories/**`, `/api/messaging/**`, `/api/calls/**`, `/api/wallet/**`, `/api/payments/mobile-money/**`, `/api/store/**`, `/api/private/**`, `/api/auth/me`) -> un role parmi `USER|SELLER|MODERATOR|ADMIN`.
- Endpoints publics: `/api/public/**`, `/api/webhooks/**`, endpoints d auth publics (`/api/auth/register`, `/api/auth/verify-otp`, `/api/auth/login`, `/api/auth/refresh`, `/api/auth/logout`).

### Workflow attribution SELLER/MODERATOR (admin)
- `GET /api/admin/roles/users/{userId}` -> roles Keycloak de l utilisateur
- `GET /api/admin/roles/audit` -> historique des changements de roles (filtres: `actorUserId`, `targetUserId`, `role`, `action`, pagination)
- `POST /api/admin/roles/users/{userId}/seller` -> attribue `SELLER` (+ garantit `USER`)
- `DELETE /api/admin/roles/users/{userId}/seller` -> retire `SELLER`
- `POST /api/admin/roles/users/{userId}/moderator` -> attribue `MODERATOR` (+ garantit `USER`)
- `DELETE /api/admin/roles/users/{userId}/moderator` -> retire `MODERATOR`
- `POST /api/admin/roles/users/{userId}/assign` payload `{ "role": "ADMIN" }` -> attribution generique
- `POST /api/admin/roles/users/{userId}/revoke` payload `{ "role": "ADMIN" }` -> retrait generique
- Garde-fou: impossible de retirer `USER`; impossible de retirer son propre role `ADMIN`.
- Audit DB des changements de roles: table `role_audit_log` (`who`, `when`, `target`, `role`, `action`).

## LiveKit (appels audio/video)
- Lancer LiveKit: `docker compose -f infra/livekit/docker-compose.yml up -d`
- Endpoint token: `POST /api/calls/token` (JWT requis) payload: `{ "roomName": "call-123", "audioOnly": true }`
- Response: `{ "roomName", "identity", "token", "livekitUrl" }`
- Signalisation OpenIM:
  - `POST /api/calls/invite` -> envoi reel du message d invite via OpenIM + persistance historique
  - `POST /api/calls/respond` -> envoi reel via OpenIM + mise a jour historique (accept/decline/busy/cancel/end)
  - `GET /api/calls/history` -> historique des appels (statut, duree, initiateur, destinataire)

## Moderation (MVP)
- `POST /api/moderation/reports` (USER|SELLER|MODERATOR|ADMIN) -> creer un signalement:
  payload exemple: `{ "targetType":"CHAT_MESSAGE", "targetId":"msg_123", "reasonCode":"SPAM", "description":"Spam links" }`
- `GET /api/moderation/reports/my` -> liste mes 100 derniers signalements.
- `GET /api/moderation/reports/queue` (MODERATOR|ADMIN) -> file de moderation filtrable
  (`status`, `targetType`, `reporterUserId`, `targetId`, pagination).
- `PATCH /api/moderation/reports/{reportId}/status` (MODERATOR|ADMIN) ->
  decision de moderation (statuts autorises: `IN_REVIEW`, `RESOLVED`, `REJECTED`) avec
  `actionType` (`NONE`, `WARNING`, `CONTENT_REMOVED`, `CONTENT_HIDDEN`, `USER_TEMP_SUSPENDED`, `USER_BANNED`).
- `GET /api/moderation/reports/{reportId}/actions` (MODERATOR|ADMIN) -> historique d execution des actions auto.
- Actions auto actuellement connectees:
  - `STORY` -> `CONTENT_HIDDEN` (expire immediate) / `CONTENT_REMOVED` (suppression)
  - `STORE_PRODUCT` -> archivage soft dans Medusa (`status=draft` + metadata moderation)
  - `PROFILE` -> `USER_TEMP_SUSPENDED` / `USER_BANNED` (status compte backend)
- Si l execution auto echoue, le statut du report n est pas valide en `RESOLVED` (erreur API + trace audit).

## Wallet (dev)
- `GET /api/wallet/balance` (JWT requis) -> liste des soldes par devise
- `GET /api/wallet/balance/{currency}` (JWT requis)
- `POST /api/wallet/topup` (JWT requis) payload: `{ "amount": 15000, "currency": "XAF" }`
- `POST /api/wallet/withdraw` (JWT requis) payload: `{ "amount": 5000, "currency": "XAF", "destination": "momo" }`
- `POST /api/wallet/transfer` (JWT requis) payload: `{ "recipientId": "<uuid>", "amount": 2500, "currency": "XAF" }`
- `GET /api/wallet/transactions` (JWT requis)
- Checkout wallet: `POST /api/store/carts/{cartId}/complete` payload: `{ "payment_method": "wallet" }`
- Le wallet supporte plusieurs devises (un compte par devise) ; la devise doit matcher celle du cart Medusa.

## Store vendeur (dev)
- Endpoints proteges `SELLER_OR_ADMIN`:
- `POST /api/store/seller/products` -> creation produit via Medusa admin API (metadata `kobo_seller_id` auto-injectee)
- `GET /api/store/seller/products` -> liste des produits vendeur (filtre ownership strict pour SELLER)
- `POST /api/store/seller/products/{productId}` -> mise a jour produit (bloquee si vendeur different pour un utilisateur non-admin)
- `PATCH /api/store/seller/products/{productId}/pricing-stock` -> mise a jour prix/stock (payload `variants`)
- `POST /api/store/seller/products/{productId}/archive` -> archivage produit (status `draft` + metadata archive)
- Ownership strict: un SELLER ne peut lire/modifier/archiver que ses propres produits.
- Prerequis: `MEDUSA_ADMIN_TOKEN` configure.

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
