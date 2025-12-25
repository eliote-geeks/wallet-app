# Tests du Module Profiles & Contacts

**Version** : 3.0  
**Date** : 24 décembre 2025  

## Couverture des tests

Le module est couvert par :
- **Tests unitaires** (`ProfileServiceTest`) : logique métier isolée avec Mockito.
- **Tests d’intégration** (`ProfileControllerIntegrationTest`) : endpoints REST avec MockMvc et simulation d’authentification.

**Couverture** : ~95-100 % des cas critiques (visibilité, contacts mutuels, blocage, exceptions, idempotence).

## Tests unitaires (ProfileServiceTest)

Fichier : `src/test/java/com/socialwallet/profiles/service/ProfileServiceTest.java`

### Cas testés

#### Récupération de profil
- `getMyProfile` retourne le profil complet du propriétaire.
- `getProfileForViewer` cache les champs quand pas contact.
- `getProfileForViewer` montre tout quand contact mutuel.
- `getProfileForViewer` montre tout si visibilité = EVERYONE.

#### Gestion des contacts
- `addContact` crée des entrées mutuelles.
- `addContact` est idempotent.
- `addContact` interdit l’ajout de soi-même.
- `addContact` interdit l’ajout d’un utilisateur bloqué.
- `removeContact` supprime les deux côtés.
- `getMyContacts` retourne la liste des IDs de contacts.

#### Blocage
- `blockUser` crée le blocage et supprime les contacts mutuels.
- `blockUser` est idempotent.
- `blockUser` interdit le self-block.
- `unblockUser` supprime le blocage.

#### Paramètres de confidentialité
- `getMySettings` crée les valeurs par défaut si absentes.
- `updateMySettings` applique uniquement les changements fournis.

## Tests d’intégration (ProfileControllerIntegrationTest)

Fichier : `src/test/java/com/socialwallet/profiles/web/ProfileControllerIntegrationTest.java`

### Cas testés

#### Sécurité
- Tous les endpoints retournent 401 Unauthorized sans authentification.

#### Flux heureux (avec utilisateur authentifié simulé)
- GET /me retourne le profil complet.
- GET /{targetId} applique la visibilité (champs cachés si non contact).
- GET /me/settings retourne les paramètres.
- PUT /me/settings met à jour et persiste les paramètres.

## Lancement des tests

```bash
./mvnw test