## Import Keycloak (dev)

Prerequis:
- Keycloak tourne sur `http://localhost:8082`
- Admin: `admin` / `admin`

1) Se connecter avec kcadm:

```bash
docker exec social-wallet-keycloak /opt/keycloak/bin/kcadm.sh config credentials \
  --server http://localhost:8080 --realm master --user admin --password admin
```

2) Creer le realm si besoin:

```bash
docker exec social-wallet-keycloak /opt/keycloak/bin/kcadm.sh create realms \
  -s realm=social-wallet -s enabled=true
```

3) Importer la config:

```bash
# Realm (parametres + sessions)
docker exec -i social-wallet-keycloak /opt/keycloak/bin/kcadm.sh update realms/social-wallet \
  -f - < docs/keycloak/realm-social-wallet.json

# Profil utilisateur (champs requis optionnels)
docker exec -i social-wallet-keycloak /opt/keycloak/bin/kcadm.sh update users/profile -r social-wallet \
  -f - < docs/keycloak/user-profile-social-wallet.json

# Client API
docker exec -i social-wallet-keycloak /opt/keycloak/bin/kcadm.sh create clients -r social-wallet \
  -f - < docs/keycloak/client-social-wallet-api.json
```

Note:
- Si le client existe deja, remplace `create clients` par `update clients/<id>`.
  Recuperer l ID avec:

```bash
docker exec social-wallet-keycloak /opt/keycloak/bin/kcadm.sh get clients -r social-wallet \
  -q clientId=social-wallet-api --fields id
```
