# SLO Kobo (Backend + IAM)

## Objectifs SLO (dev/prod cible)
- API Backend availability (`/api/public/ping`):
  - dev: 99.0%
  - prod: 99.5%
- Keycloak token endpoint availability:
  - dev: 99.0%
  - prod: 99.5%
- MTTR incidents P1:
  - dev: < 60 min
  - prod: < 30 min

## SLI proposes
- Availability API: ratio `2xx/3xx` sur checks synthetiques 1 min.
- Availability Keycloak: checks 1 min sur `/.well-known/openid-configuration`.
- Erreurs runtime: nb events `kubernetes.event.type=Warning` par composant.

## Alertes actives
- `Kobo Kubernetes Warning Events`
- `Kobo Backend Warning Events`
- `Kobo Keycloak Warning Events`

## Error budget mensuel
- 99.5% => ~3h39 d'indisponibilite max/mois.
- Si budget consomme > 50% avant J15: freeze des changements non critiques.
