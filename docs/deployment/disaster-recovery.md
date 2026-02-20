# Disaster Recovery Plan (Kobo Backend)

## Scope
- Namespace applicatif: `kobo-dev` / `kobo-prod`
- Donnees critiques: PostgreSQL (`social_wallet`, `keycloak`)
- Identite: Keycloak (realm + users)
- Observabilite: Elastic Stack

## Objectifs
- RPO cible: 2h (prod), 6h (dev)
- RTO cible: 4h (prod), 8h (dev)

## Backup strategy
- PostgreSQL backups chiffres via `social-wallet-postgres-backup` (CronJob)
- Chiffrement: AES-256 (`openssl`, cle `BACKUP_ENCRYPTION_KEY`)
- Retention: `BACKUP_RETENTION_DAYS` (defaut 14)
- Backup path: PVC `social-wallet-postgres-backups` mounted at `/backups`

## Restore procedure (high level)
1. Restaurer le cluster (k3s) et les manifests via GitHub Actions/infra K8s.
2. Restaurer les secrets critiques (`social-wallet-platform-secrets`, `social-wallet-backend-secrets`, `elastic-credentials`).
3. Identifier le dernier backup chiffre: `/backups/<timestamp>/social_wallet.sql.gz.enc`.
4. Dechiffrer et restaurer dans PostgreSQL.
5. Verifier les tables et la sante API (`/api/public/ping`).

## Test de restauration
- Script: `scripts/vps/test-postgres-backup-restore.sh`
- Frequence recommandee: hebdomadaire en dev, mensuelle en prod.
- KPI test: backup lisible, restauration SQL valide, verification de schema.

## Runbook incident
1. P1 Data loss: geler les ecritures API.
2. Restaurer DB depuis dernier backup valide.
3. Verifier IAM (Keycloak) et tokens.
4. Rejouer/traiter manuellement les events webhook non confirmes.
5. Publier postmortem (cause, impact, actions correctives).
