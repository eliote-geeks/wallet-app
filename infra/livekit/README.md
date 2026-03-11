## LiveKit (self-host)

Lancer LiveKit en local:

```bash
docker compose up -d
```

Ports exposes:
- `7880` (HTTP/WebSocket)
- `7881` (TCP)
- `7882/udp` (UDP media)

La cle et le secret doivent correspondre a `app.calls.livekit.api-key` /
`app.calls.livekit.api-secret` dans `application.yml`.
Le client mobile utilise `LIVEKIT_URL` (ex: `ws://localhost:7880`).
