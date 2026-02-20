# Disaster Recovery Plan (Kobo Backend)

## Scope
- Namespace applicatif: `kobo-dev` / `kobo-prod`
- Donnees critiques: PostgreSQL (`social_wallet`, `keycloak`)
- Identite: Keycloak (realm + users)
- Observabilite: Elastic stack isole

## Objectifs
- RPO cible: 2h (dev), 15min (prod cible)
- RTO cible: 8h (dev), 60min (prod cible)

## Backup strategy
- Backup PostgreSQL chiffre via CronJob `social-wallet-postgres-backup`
- Chiffrement AES-256 (`openssl`) avec `BACKUP_ENCRYPTION_KEY`
- Retention locale: `BACKUP_RETENTION_DAYS`
- Stockage local: PVC `social-wallet-postgres-backups`
- Offsite cible (phase prod): Object Storage S3 compatible chiffre

## Restore procedure (high level)
1. Restaurer cluster et manifests (GitHub + infra k8s).
2. Restaurer secrets critiques (`social-wallet-platform-secrets`, `social-wallet-backend-secrets`).
3. Recuperer dernier backup chiffre (`/backups/<timestamp>/social_wallet.sql.gz.enc`).
4. Dechiffrer et restaurer sur PostgreSQL.
5. Verifier API (`/api/public/ping`) + Keycloak discovery endpoint.

## Test de restauration
- Script: `scripts/vps/test-postgres-backup-restore.sh`
- Frequence recommandee:
  - dev: hebdomadaire
  - prod: mensuelle
- KPI test:
  - backup lisible
  - restauration SQL valide
  - verification du schema/table count

## Runbook incident
1. Geler les ecritures API si corruption suspectee.
2. Restaurer DB depuis dernier backup valide.
3. Verifier IAM (Keycloak) et tokens.
4. Rejouer/traiter les webhooks non confirms.
5. Publier postmortem (cause, impact, actions).
