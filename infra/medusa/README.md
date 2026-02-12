# Medusa (store)

Ce dossier embarque Medusa comme service de boutique (store) pour Kobo.
Le code serveur Medusa est dans `infra/medusa/server` (starter officiel).

## Demarrage rapide

```bash
cd infra/medusa
cp .env.example .env

cd server
npm install

cd ..
docker compose up -d medusa-db medusa-redis

docker compose run --rm medusa yarn medusa db:migrate

docker compose run --rm medusa yarn seed

docker compose up -d medusa
```

## URLs utiles
- API Medusa: http://localhost:9000

## Notes
- Les ports Postgres/Redis exposes sont configurables (defaults: 5434 et 6381) pour eviter les conflits locaux.
- Le seed configure une region XAF (Central Africa, CM) et des prix en XAF.
- L admin Medusa et l integration Keycloak seront ajustes plus tard.
- Pour arreter: `docker compose down`.


## Cles API (backend Spring Boot)

Le seed cree 2 cles:
- une cle *publishable* (pour les endpoints store)
- une cle *secret* (pour les endpoints admin)

Apres `yarn seed`, Medusa affiche dans la sortie:
- `KOBO_MEDUSA_PUBLISHABLE_KEY=...`
- `KOBO_MEDUSA_ADMIN_TOKEN=...`

Cote backend Java, configure:
- `MEDUSA_PUBLISHABLE_KEY` = valeur de `KOBO_MEDUSA_PUBLISHABLE_KEY`
- `MEDUSA_ADMIN_TOKEN` = valeur de `KOBO_MEDUSA_ADMIN_TOKEN`

## Webhooks Medusa -> Backend

Le serveur Medusa inclut un subscriber qui forward les events `order.*` / `payment.*` vers
`KOBO_BACKEND_WEBHOOK_URL` (ex: `http://host.docker.internal:8080/api/webhooks/medusa`).

Variables (dans `infra/medusa/.env`):
- `KOBO_BACKEND_WEBHOOK_URL`
- `KOBO_BACKEND_WEBHOOK_SECRET` (optionnel)

Si `KOBO_BACKEND_WEBHOOK_SECRET` est defini, Medusa signe le payload avec HMAC SHA256
(`x-medusa-signature`). Cote backend, mets la meme valeur dans `MEDUSA_WEBHOOK_SECRET`.
