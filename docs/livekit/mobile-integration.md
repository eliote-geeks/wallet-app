## Integration mobile LiveKit + OpenIM (WhatsApp-like)

Objectif:
- OpenIM = messagerie + signalisation (invitation / acceptation / fin).
- LiveKit = media (audio/video).

### 1) Auth + tokens
1. Login via API backend (telephone):
   - `POST /api/auth/login`
2. Recuperer token OpenIM:
   - `POST /api/messaging/token`
3. Recuperer token LiveKit:
   - `POST /api/calls/token` avec `{ "roomName": "...", "audioOnly": true|false }`

### 2) Flux appel (signalisation OpenIM)
1. Caller demande un payload d invitation:
   - `POST /api/calls/invite`
   - Payload OpenIM renvoye dans `openimPayload`
2. Le client envoie `openimPayload` via un **custom message** OpenIM.
3. Callee recoit l invite (custom message), affiche l ecran d appel.
4. Callee accepte/refuse:
   - `POST /api/calls/respond` -> payload OpenIM a envoyer au caller.
5. Caller recoit la reponse:
   - `ACCEPT` -> les deux joignent la room LiveKit
   - `DECLINE/BUSY/CANCEL/END` -> fermer l UI.

### 3) LiveKit (audio/video)
- Audio-only: publier uniquement le micro.
- Video: publier micro + camera.
- Le token LiveKit est signe par le backend (HS256).

### 4) Exemple payload OpenIM
```json
{
  "type": "call_signal",
  "action": "INVITE",
  "callId": "uuid",
  "roomName": "call-123",
  "audioOnly": true,
  "timestamp": "2026-01-19T23:59:00Z",
  "fromUserId": "uuid",
  "fromOpenimUserId": 1000000009,
  "toUserId": "uuid",
  "toOpenimUserId": 1000000010
}
```

### 5) SDK LiveKit
- Android: `io.livekit:livekit-android`
- iOS: `LiveKit` (Swift)

### 6) Notes
- LiveKit gere les appels audio **et** video.
- OpenIM reste la source de verite pour la signalisation (invites, fin d appel).
