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
- Les ports 5433 (Postgres) et 6381 (Redis) evitent les conflits locaux.
- Le seed configure une region XAF (Central Africa, CM) et des prix en XAF.
- L admin Medusa et l integration Keycloak seront ajustes plus tard.
- Pour arreter: `docker compose down`.
