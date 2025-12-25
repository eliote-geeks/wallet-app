# Tests for the Profiles & Contacts Module

**Version** : 3.2  
**Date** : December 25, 2025  

## Test Coverage

The module is covered at **~100%** by:
- **Unit tests**:
  - `ProfileServiceTest`: isolated business logic with Mockito.
  - `ProfileCreationListenerTest`: automatic profile and settings creation on first login.
- **Integration tests** (`ProfileControllerIntegrationTest`): REST endpoints with MockMvc, authentication simulation via `@WithMockUser`, and HTTP error handling.

**Total coverage**: visibility rules, mutual contacts, blocking (including blocked list), privacy settings, idempotence, business exceptions, automatic creation on first login, and proper HTTP error responses.

## Unit Tests

### ProfileServiceTest.java
File: `src/test/java/com/socialwallet/profiles/service/ProfileServiceTest.java`

#### Tested cases
- **Profile retrieval**:
  - `getMyProfile` returns full profile for owner.
  - `getMyProfile` throws exception if profile not found.
  - `getProfileForViewer` hides fields when viewer is not a contact.
  - `getProfileForViewer` shows all fields when mutual contact.
  - `getProfileForViewer` shows all fields when visibility is EVERYONE.

- **Contact management**:
  - `addContact` creates mutual entries.
  - `addContact` is idempotent.
  - `addContact` forbids self-add.
  - `addContact` forbids adding a blocked user (both directions).
  - `removeContact` removes both sides.
  - `getMyContacts` returns list of contact IDs.

- **Blocking**:
  - `blockUser` creates block and removes mutual contacts.
  - `blockUser` is idempotent.
  - `blockUser` forbids self-block.
  - `unblockUser` removes the block.
  - `getMyBlockedUsers` returns list of blocked user IDs.

- **Privacy settings**:
  - `getMySettings` creates defaults if missing.
  - `updateMySettings` applies only provided changes.

### ProfileCreationListenerTest.java
File: `src/test/java/com/socialwallet/profiles/listener/ProfileCreationListenerTest.java`

#### Tested cases
- Creates profile and default settings on first successful login (populates name from JWT claims).
- Does nothing if profile and settings already exist (idempotent).

## Integration Tests

### ProfileControllerIntegrationTest.java
File: `src/test/java/com/socialwallet/profiles/web/ProfileControllerIntegrationTest.java`

#### Tested cases
- **Security**: all endpoints return 401 Unauthorized without authentication.

- **Happy path** (authenticated user):
  - `GET /me` returns full profile.
  - `GET /{targetId}` applies visibility rules correctly.
  - `GET /me/settings` returns default settings (created automatically).
  - `PUT /me/settings` updates settings successfully.
  - `GET /contacts` returns empty list initially.
  - `GET /blocked` returns empty list initially.
  - `GET /blocked` returns blocked user after blocking.

- **Client errors**:
  - `POST /contacts/{selfId}` → 400 Bad Request.
  - `POST /block/{selfId}` → 400 Bad Request.

## Error handling

`IllegalArgumentException` thrown by the service (self-add, self-block) are caught by `ProfileExceptionHandler` and returned as **400 Bad Request** with a clear English message.

## Running the tests

```bash
./mvnw.cmd clean test   # Windows
# or
./mvnw test             # Linux / macOS