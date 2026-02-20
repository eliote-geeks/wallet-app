# Elastic Stack (Isolated)

## Architecture
- Elasticsearch + Kibana dans une stack Docker dediee sur le VPS (`/opt/kobo/elastic-isolated`).
- Le cluster Kobo (k3s) n'heberge plus Kibana/Elasticsearch.
- Les agents `filebeat` et `metricbeat` tournent dans `observability` (k3s) et envoient vers `http://79.137.32.27:9201`.

## Services exposes
- Kibana: `http://79.137.32.27:5602`
- Elasticsearch: `9201/tcp` limite au CIDR pods `10.42.0.0/16`

## Secrets
- Fichier: `/opt/kobo/elastic-isolated/.env`
- Variables critiques: `ELASTIC_PASSWORD`, `KIBANA_SYSTEM_PASSWORD`, `KIBANA_ENCRYPTION_KEY`

## Verification rapide
```bash
curl -u elastic:$ELASTIC_PASSWORD http://localhost:9201/_cluster/health
curl http://localhost:5602/api/status
kubectl -n observability logs daemonset/filebeat --tail=50
kubectl -n observability logs daemonset/metricbeat --tail=50
```

## Alerting
- Script idempotent: `scripts/observability/bootstrap-kibana-rules.sh`
- Regles configurees:
  - `Kobo Kubernetes Warning Events`
  - `Kobo Backend Warning Events`
  - `Kobo Keycloak Warning Events`
