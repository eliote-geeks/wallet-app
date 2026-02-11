package com.socialwallet.profiles.web;

import com.socialwallet.profiles.dto.*;
import com.socialwallet.profiles.service.ProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import static com.socialwallet.config.RbacExpressions.PLATFORM_USER;

/**
 * REST controller for Profiles & Contacts.
 * Uses Principal to support both real JWT and @WithMockUser in tests.
 */
@RestController
@RequestMapping("/api/profiles")
@RequiredArgsConstructor
@PreAuthorize(PLATFORM_USER)
public class ProfileController {

    private final ProfileService profileService;

    @GetMapping("/me")
    @Operation(summary = "Get my own profile", description = "Returns the full profile of the authenticated user.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Profile retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthenticated"),
        @ApiResponse(responseCode = "404", description = "Profile not found (first login not completed)")
    })
    public ResponseEntity<MyProfileDto> getMyProfile(Principal principal) {
        UUID userId = UUID.fromString(principal.getName());
        return ResponseEntity.ok(profileService.getMyProfile(userId));
    }

    @PutMapping("/me")
    @Operation(summary = "Update my profile", description = "Updates the name, about, and photoUrl of the profile.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Profile updated successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid data"),
        @ApiResponse(responseCode = "401", description = "Unauthenticated")
    })
    public ResponseEntity<Void> updateMyProfile(Principal principal, @Valid @RequestBody MyProfileDto updated) {
        UUID userId = UUID.fromString(principal.getName());
        profileService.updateMyProfile(userId, updated);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{targetId}")
    @Operation(summary = "Get another user's profile", description = "Returns the profile with visibility rules applied.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Profile retrieved (fields hidden according to privacy)"),
        @ApiResponse(responseCode = "401", description = "Unauthenticated"),
        @ApiResponse(responseCode = "404", description = "User not found")
    })
    public ResponseEntity<ProfileDto> getProfile(Principal principal, @PathVariable UUID targetId) {
        UUID viewerId = UUID.fromString(principal.getName());
        return ResponseEntity.ok(profileService.getProfileForViewer(viewerId, targetId));
    }

    @GetMapping("/me/settings")
    @Operation(summary = "Get privacy settings", description = "Returns the user's privacy settings (defaults created automatically if missing).")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Settings retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthenticated")
    })
    public ResponseEntity<UserSettingsDto> getMySettings(Principal principal) {
        UUID userId = UUID.fromString(principal.getName());
        return ResponseEntity.ok(profileService.getMySettings(userId));
    }

    @PutMapping("/me/settings")
    @Operation(summary = "Update privacy settings", description = "Updates only the provided fields.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Settings updated successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid data"),
        @ApiResponse(responseCode = "401", description = "Unauthenticated")
    })
    public ResponseEntity<Void> updateMySettings(Principal principal, @Valid @RequestBody UserSettingsDto updated) {
        UUID userId = UUID.fromString(principal.getName());
        profileService.updateMySettings(userId, updated);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/contacts/{contactId}")
    @Operation(summary = "Add a contact", description = "Creates a mutual contact relationship. Idempotent.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Contact added successfully (or already existing)"),
        @ApiResponse(responseCode = "400", description = "Cannot add self or a blocked user"),
        @ApiResponse(responseCode = "401", description = "Unauthenticated")
    })
    public ResponseEntity<Void> addContact(Principal principal, @PathVariable UUID contactId) {
        UUID userId = UUID.fromString(principal.getName());
        profileService.addContact(userId, contactId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/contacts/{contactId}")
    @Operation(summary = "Remove a contact", description = "Deletes the mutual contact relationship.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Contact removed successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthenticated")
    })
    public ResponseEntity<Void> removeContact(Principal principal, @PathVariable UUID contactId) {
        UUID userId = UUID.fromString(principal.getName());
        profileService.removeContact(userId, contactId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/block/{blockedId}")
    @Operation(summary = "Block a user", description = "Blocks a user (removes contact if existing). Idempotent.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "User blocked successfully (or already blocked)"),
        @ApiResponse(responseCode = "400", description = "Cannot block self"),
        @ApiResponse(responseCode = "401", description = "Unauthenticated")
    })
    public ResponseEntity<Void> blockUser(Principal principal, @PathVariable UUID blockedId) {
        UUID userId = UUID.fromString(principal.getName());
        profileService.blockUser(userId, blockedId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/block/{blockedId}")
    @Operation(summary = "Unblock a user", description = "Removes the block.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "User unblocked successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthenticated")
    })
    public ResponseEntity<Void> unblockUser(Principal principal, @PathVariable UUID blockedId) {
        UUID userId = UUID.fromString(principal.getName());
        profileService.unblockUser(userId, blockedId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/contacts")
    @Operation(summary = "List my contacts", description = "Returns the list of mutual contacts.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "List retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthenticated")
    })
    public ResponseEntity<List<ContactDto>> getMyContacts(Principal principal) {
        UUID userId = UUID.fromString(principal.getName());
        return ResponseEntity.ok(profileService.getMyContacts(userId));
    }

    @GetMapping("/blocked")
    @Operation(summary = "List blocked users", description = "Returns the list of users blocked by the authenticated user.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "List retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthenticated")
    })
    public ResponseEntity<List<ContactDto>> getMyBlockedUsers(Principal principal) {
        UUID userId = UUID.fromString(principal.getName());
        return ResponseEntity.ok(profileService.getMyBlockedUsers(userId));
    }

    @GetMapping("/exists/{targetId}")
    @Operation(summary = "Check if a user exists", description = "Returns 200 if the user exists, 404 otherwise. Useful for the frontend to know if someone is already registered.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "The user exists"),
        @ApiResponse(responseCode = "404", description = "The user does not exist"),
        @ApiResponse(responseCode = "401", description = "Unauthenticated")
    })
    public ResponseEntity<Void> checkUserExists(@PathVariable UUID targetId) {
        boolean exists = profileService.existsByUserId(targetId);
        return exists ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
    }
}
