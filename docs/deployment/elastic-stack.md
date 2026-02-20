# Elastic Stack (Kubernetes)

## Composants deployes
- Elasticsearch (single-node)
- Kibana (ingress TLS)
- Filebeat (logs containers)
- Metricbeat (metrics Kubernetes)
- APM Server (endpoint traces)

## Namespace
- `observability`

## Overlay dev
- `infra/k8s/observability/overlays/dev`
- Kibana URL: `https://kibana-dev.kobo.79.137.32.27.nip.io`

## Secrets requis
- `ELASTIC_PASSWORD`
- `KIBANA_ENCRYPTION_KEY`
- `APM_SECRET_TOKEN`

## Verification rapide
```bash
kubectl -n observability get pods
kubectl -n observability get ingress kibana
curl -k https://kibana-dev.kobo.79.137.32.27.nip.io
```

## Alerting
- Utiliser Kibana Rules & Connectors (Index threshold, Error rate, APM latency).
- Créer au minimum:
  - erreur API > seuil
  - saturation CPU/memoire pods critiques
  - indisponibilite endpoint `/api/public/ping`
