# Profiles & Contacts Module

**Version** : 3.2  
**Date** : December 25, 2025  
**Package** : `com.socialwallet.profiles`  

## Module Purpose

This module manages user profiles, mutual contacts, blocking, and privacy settings in a private messaging application with integrated wallet and store features (WhatsApp-like model).

**No public social network features** — everything is based on private contacts and granular privacy controls.

## Privacy Principles

- **Default settings**:
  - Profile photo and "About" visible to **Everyone** (to facilitate discovery).
  - Last seen / online status visible to **My contacts**.
  - Read receipts enabled.
  - Stories (future feature) visible to **My contacts**.

- Users can restrict each setting individually:  
  **Everyone** → **My contacts** → **Nobody**.

- Automatic creation of profile and privacy settings on first successful Keycloak login.

## Module Structure
com.socialwallet.profiles/
├── dto/                  → DTOs for API input/output
├── model/                → JPA entities and enums
├── repository/           → Spring Data JPA repositories
├── service/              → Business logic
├── listener/             → ProfileCreationListener (auto-creation on first login)
├── web/                  → REST controllers + exception handler
text## Main Entities

- **Profile** : userId, name, about, photoUrl
- **Contact** : mutual relationship (double entry in database)
- **Block** : unidirectional block
- **UserSettings** : privacy configuration per user

## REST API (/api/profiles)

All endpoints require JWT authentication (Bearer token).

| Method | Endpoint                  | Description                                      | Response                |
|--------|---------------------------|--------------------------------------------------|-------------------------|
| GET    | /me                       | Full profile of the authenticated user           | MyProfileDto            |
| PUT    | /me                       | Update own profile                               | 204 No Content          |
| GET    | /{targetId}               | Profile of another user (visibility rules applied)| ProfileDto              |
| GET    | /me/settings              | Retrieve privacy settings                        | UserSettingsDto         |
| PUT    | /me/settings              | Update privacy settings                          | 204 No Content          |
| POST   | /contacts/{contactId}     | Add a mutual contact                             | 204 No Content          |
| DELETE | /contacts/{contactId}     | Remove a mutual contact                          | 204 No Content          |
| POST   | /block/{blockedId}        | Block a user                                     | 204 No Content          |
| DELETE | /block/{blockedId}        | Unblock a user                                   | 204 No Content          |
| GET    | /contacts                 | List of mutual contacts                          | List<ContactDto>        |
| GET    | /blocked                  | List of blocked users                            | List<ContactDto>        |

## Security & Business Rules

- User ID extracted via `Principal.getName()` (compatible with production JWT and test `@WithMockUser`).
- All contact/block operations are **idempotent**.
- Self-add contact and self-block are forbidden → returns **400 Bad Request** with clear message.
- Blocking a user automatically removes any existing mutual contact relationship.
- Automatic profile creation on first Keycloak authentication (via `ProfileCreationListener`).

## Future Improvements

- Pagination for `/contacts` and `/blocked` endpoints (scalability for millions of users).
- Contact search by phone number (integration with Identity module).
- Redis caching for frequently accessed contact and blocked lists.

This module is **production-ready**, fully tested, secure, and designed for high scalability.