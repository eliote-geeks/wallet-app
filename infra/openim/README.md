# OpenIM (docker)

Source: https://github.com/openimsdk/openim-docker

## Quick start

```bash
cd /home/paul/social-wallet-backend/infra/openim
docker compose up -d
```

## Environment

Update `.env` before running in production:
- `MINIO_EXTERNAL_ADDRESS`
- `GRAFANA_URL`
- `OPENIM_SECRET`

Local defaults are set to `localhost` so the stack works out of the box.

## Useful ports (local)

- OpenIM gateway: `10001`
- OpenIM API: `10002`
- Chat API: `10008`
- Admin API: `10009`
- MinIO: `10005` (console `10004`)
- Grafana: `13000` (optional)
- Prometheus: `19090` (optional)

## Notes

- Data is stored under `infra/openim/components/` (ignored by git).
- When the backend runs in Docker, it should call OpenIM via `openim-server:10002`
  on a shared network (to be wired in the next step).
