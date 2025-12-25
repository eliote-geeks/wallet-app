# Module Profiles & Contacts

**Version** : 3.0 (Sécurisée)  
**Date** : 24 décembre 2025  
**Package** : `com.socialwallet.profiles`  

## Objectif du module

Ce module gère les profils utilisateurs, les contacts mutuels, le blocage et les paramètres de confidentialité dans une application de messagerie privée avec portefeuille et magasin (modèle inspiré de WhatsApp).

**Aucun aspect réseau social public** : pas de follow/unfollow public, pas de feed. Tout est basé sur des contacts privés et une confidentialité granulaire.

## Principes de confidentialité (Visibilité)

- **Par défaut, tout est ouvert à la découverte** :
  - Photo de profil et statut "À propos" visibles par **Tout le monde**.
  - Dernière connexion / statut en ligne visibles par **Mes contacts**.
  - Reçus lus activés par défaut.
  - Stories (futur) visibles par **Mes contacts** par défaut.

- **L'utilisateur peut restreindre chaque paramètre individuellement** :
  - Tout le monde → Mes contacts → Personne

## Structure du module

com.socialwallet.profiles/
├── dto/                  → DTO pour les entrées/sorties API
├── model/                → Entités JPA et enums
├── repository/           → Repositories Spring Data JPA
├── service/              → Logique métier
└── web/                  → Contrôleurs REST


## Entités principales (model/)

- **Profile** : Profil utilisateur (name, about, photoUrl)
- **Contact** : Relation contact mutuelle (double entrée)
- **Block** : Blocage unidirectionnel
- **UserSettings** : Paramètres de confidentialité (photo, about, lastSeen, readReceipts, stories)

## API REST (/api/profiles)

Tous les endpoints nécessitent une authentification JWT.

| Méthode | Endpoint                  | Description                                      | Retour          |
|---------|---------------------------|--------------------------------------------------|-----------------|
| GET     | /me                       | Récupère le profil complet du propriétaire       | MyProfileDto    |
| PUT     | /me                       | Met à jour le profil du propriétaire             | 204 No Content  |
| GET     | /{targetId}               | Récupère le profil d’un autre utilisateur (visibilité appliquée) | ProfileDto |
| GET     | /me/settings              | Récupère les paramètres de confidentialité       | UserSettingsDto |
| PUT     | /me/settings              | Met à jour les paramètres de confidentialité     | 204 No Content  |
| POST    | /contacts/{contactId}     | Ajoute un contact (mutuel)                       | 204 No Content  |
| DELETE  | /contacts/{contactId}     | Supprime un contact (mutuel)                     | 204 No Content  |
| POST    | /block/{blockedId}        | Bloque un utilisateur                            | 204 No Content  |
| DELETE  | /block/{blockedId}        | Débloque un utilisateur                          | 204 No Content  |
| GET     | /contacts                 | Liste les contacts de l'utilisateur              | List<ContactDto>|

## Sécurité

- Aucune entité JPA exposée directement dans l’API (utilisation systématique de DTO).
- Visibilité appliquée côté service.
- Validation des règles (self-add/block interdit, blocage empêche ajout contact).
- Idempotence sur toutes les opérations de relation.

## Évolutions futures

- Création automatique du profil à la première connexion (listener Keycloak).
- Intégration avec module Chat (vérification contacts pour messages).
- Tips wallet uniquement entre contacts.
- Stories avec visibilité personnalisée par publication.

Ce module est prêt pour la production, entièrement testé et sécurisé.