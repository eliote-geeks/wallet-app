package com.socialwallet.profiles.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.socialwallet.profiles.dto.ContactDto;
import com.socialwallet.profiles.dto.MyProfileDto;
import com.socialwallet.profiles.dto.ProfileDto;
import com.socialwallet.profiles.dto.UserSettingsDto;
import com.socialwallet.profiles.model.PrivacyLevel;
import com.socialwallet.profiles.model.Profile;
import com.socialwallet.profiles.model.UserSettings;
import com.socialwallet.profiles.repository.BlockRepository;
import com.socialwallet.profiles.repository.ContactRepository;
import com.socialwallet.profiles.repository.ProfileRepository;
import com.socialwallet.profiles.repository.UserSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Comprehensive integration tests for ProfileController.
 * 
 * Uses H2 in-memory database.
 * Simulates authenticated user with @WithMockUser (username = userId as string).
 * Principal.getName() is used in the controller to extract userId.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("ProfileController - Integration Tests")
class ProfileControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private UserSettingsRepository settingsRepository;

    @Autowired
    private BlockRepository blockRepository;

    @Autowired
    private ContactRepository contactRepository;

    private final UUID authenticatedUserId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID otherUserId = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @BeforeEach
    void setUp() {
        // Clean all relevant tables before each test
        blockRepository.deleteAll();
        contactRepository.deleteAll();
        profileRepository.deleteAll();
        settingsRepository.deleteAll();
    }

    // Helper to create and save a profile
    private Profile createAndSaveProfile(UUID userId, String name, String about, String photo) {
        Profile p = new Profile();
        p.setUserId(userId);
        p.setName(name);
        p.setAbout(about);
        p.setPhotoUrl(photo);
        return profileRepository.save(p);
    }

    // Helper to create and save settings
    private UserSettings createAndSaveSettings(UUID userId, PrivacyLevel photo, PrivacyLevel about) {
        UserSettings s = new UserSettings();
        s.setUserId(userId);
        s.setProfilePhoto(photo);
        s.setAbout(about);
        s.setLastSeenAndOnline(PrivacyLevel.MY_CONTACTS);
        s.setReadReceipts(true);
        s.setDefaultStoryVisibility(PrivacyLevel.MY_CONTACTS);
        return settingsRepository.save(s);
    }

    @Nested
    @DisplayName("Security Tests")
    class Security {

        @Test
        @DisplayName("All endpoints require authentication")
        void allEndpoints_requireAuthentication() throws Exception {
            mockMvc.perform(get("/api/profiles/me")).andExpect(status().isUnauthorized());
            mockMvc.perform(put("/api/profiles/me")).andExpect(status().isUnauthorized());
            mockMvc.perform(get("/api/profiles/me/settings")).andExpect(status().isUnauthorized());
            mockMvc.perform(get("/api/profiles/contacts")).andExpect(status().isUnauthorized());
            mockMvc.perform(post("/api/profiles/contacts/123e4567-e89b-12d3-a456-426614174000"))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(post("/api/profiles/block/123e4567-e89b-12d3-a456-426614174000"))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(get("/api/profiles/blocked")).andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111")  // Simulates authenticated user
    @DisplayName("Happy Path Tests with Authentication")
    class HappyPath {

        @Test
        @DisplayName("GET /me returns full profile for owner")
        void getMyProfile_returnsFullProfile() throws Exception {
            createAndSaveProfile(authenticatedUserId, "Me", "My status", "my-photo.jpg");

            mockMvc.perform(get("/api/profiles/me"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.userId").value(authenticatedUserId.toString()))
                    .andExpect(jsonPath("$.name").value("Me"))
                    .andExpect(jsonPath("$.about").value("My status"))
                    .andExpect(jsonPath("$.photoUrl").value("my-photo.jpg"));
        }

        @Test
        @DisplayName("GET /{targetId} applies visibility rules correctly")
        void getOtherProfile_appliesVisibility() throws Exception {
            createAndSaveProfile(otherUserId, "Other", "Hidden status", "hidden-photo.jpg");
            createAndSaveSettings(otherUserId, PrivacyLevel.MY_CONTACTS, PrivacyLevel.MY_CONTACTS);

            mockMvc.perform(get("/api/profiles/" + otherUserId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("Other"))
                    .andExpect(jsonPath("$.about").doesNotExist())
                    .andExpect(jsonPath("$.photoUrl").doesNotExist());
        }

        @Test
        @DisplayName("GET /me/settings returns default privacy settings when none exist")
        void getMySettings_returnsDefaultSettings() throws Exception {
            mockMvc.perform(get("/api/profiles/me/settings"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.profilePhoto").value("EVERYONE"))
                    .andExpect(jsonPath("$.about").value("EVERYONE"))
                    .andExpect(jsonPath("$.lastSeenAndOnline").value("MY_CONTACTS"))
                    .andExpect(jsonPath("$.readReceipts").value(true))
                    .andExpect(jsonPath("$.defaultStoryVisibility").value("MY_CONTACTS"));
        }

        @Test
        @DisplayName("PUT /me/settings updates privacy settings successfully")
        void updateMySettings_updatesSuccessfully() throws Exception {
            UserSettingsDto update = new UserSettingsDto();
            update.setProfilePhoto(PrivacyLevel.NOBODY);
            update.setReadReceipts(false);

            mockMvc.perform(put("/api/profiles/me/settings")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(update)))
                    .andExpect(status().isNoContent());

            mockMvc.perform(get("/api/profiles/me/settings"))
                    .andExpect(jsonPath("$.profilePhoto").value("NOBODY"))
                    .andExpect(jsonPath("$.readReceipts").value(false));
        }

        @Test
        @DisplayName("GET /contacts returns empty list initially")
        void getMyContacts_returnsEmptyListInitially() throws Exception {
            mockMvc.perform(get("/api/profiles/contacts"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$").isEmpty());
        }

        @Test
        @DisplayName("GET /blocked returns empty list initially")
        void getMyBlockedUsers_returnsEmptyListInitially() throws Exception {
            mockMvc.perform(get("/api/profiles/blocked"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$").isEmpty());
        }

        @Test
        @DisplayName("GET /blocked returns blocked user after blocking")
        void getMyBlockedUsers_returnsBlockedUser() throws Exception {
            mockMvc.perform(post("/api/profiles/block/" + otherUserId))
                    .andExpect(status().isNoContent());

            mockMvc.perform(get("/api/profiles/blocked"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$[0].contactId").value(otherUserId.toString()));
        }

        @Test
        @DisplayName("POST /contacts/{selfId} returns 400 Bad Request")
        void addContact_self_returnsBadRequest() throws Exception {
            mockMvc.perform(post("/api/profiles/contacts/" + authenticatedUserId))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("POST /block/{selfId} returns 400 Bad Request")
        void blockUser_self_returnsBadRequest() throws Exception {
            mockMvc.perform(post("/api/profiles/block/" + authenticatedUserId))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("GET /exists/{existingId} returns 200 OK")
        void checkUserExists_existing_returnsOk() throws Exception {
            // Create the profile first so it exists
            createAndSaveProfile(authenticatedUserId, "Test User", "Test about", "test.jpg");

            mockMvc.perform(get("/api/profiles/exists/" + authenticatedUserId))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("GET /exists/{nonExistingId} returns 404 Not Found")
        void checkUserExists_nonExisting_returnsNotFound() throws Exception {
            UUID nonExisting = UUID.fromString("00000000-0000-0000-0000-000000000999");
            mockMvc.perform(get("/api/profiles/exists/" + nonExisting))
                    .andExpect(status().isNotFound());
        }
    }
}