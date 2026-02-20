# Elastic Isolated Stack

Cette stack déploie Elasticsearch + Kibana hors du cluster Kobo (isolation runtime).

## 1) Préparer les secrets

```bash
cd infra/elastic-isolated
cp .env.example .env
# puis remplacer les valeurs par des secrets forts
```

Contraintes:
- `KIBANA_ENCRYPTION_KEY`: au moins 32 caractères.
- mots de passe: 32+ caractères recommandés.

## 2) Démarrer la stack

```bash
docker compose --env-file .env up -d
```

## 3) Vérifier

```bash
docker compose ps
curl -u elastic:$ELASTIC_PASSWORD http://localhost:9201/_cluster/health
curl http://localhost:5602/api/status
```

## 4) Relier les agents K8s (filebeat/metricbeat)

Le cluster Kobo envoie les logs/metrics via la clé `ELASTICSEARCH_HOSTS` du secret
`observability/elastic-credentials` (valeur attendue: `http://79.137.32.27:9201`).

## 5) Firewall recommandé

- Autoriser `9201/tcp` uniquement depuis le réseau pods k3s (`10.42.0.0/16`).
- Autoriser `5602/tcp` pour ton accès admin (ou via tunnel SSH).
