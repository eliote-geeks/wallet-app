# HA Roadmap (Target: 10k users)

## Etat actuel
- 1 VPS, 1 noeud k3s (SPOF control-plane + workload)
- PostgreSQL single instance (SPOF)

## Cible HA minimale
- 3 noeuds Kubernetes (1 control-plane + 2 workers minimum, ideal 3 control-plane)
- Ingress redondant
- DB managée HA (recommended) ou PostgreSQL HA (Patroni)
- Backup offsite chiffre + restore tests periodiques

## Plan par phase
1. Phase 1 (immediate)
- Garder architecture actuelle mais backups chiffrés verifies
- Observabilite centralisee (Elastic isole)
- Runbooks incidents + DR

2. Phase 2 (pre-prod)
- Ajouter 2 VPS workers
- Migrer vers k3s HA (external DB etcd/PostgreSQL) ou Kubernetes managé
- Mettre StorageClasses resilientes

3. Phase 3 (prod)
- DB managée HA (OVH Managed DB ou equivalent)
- Replication inter-zone + PITR
- Offsite backup (Object Storage S3 compatible) chiffre AES256/GPG
- Tests DR trimestriels avec preuve

## RPO / RTO cible
- RPO: 15 min prod
- RTO: 60 min prod

## Prérequis securite
- Plus de `6443` public
- runner self-hosted/VPN/tunnel admin
- NetworkPolicies + PDB + HPA + limits partout
