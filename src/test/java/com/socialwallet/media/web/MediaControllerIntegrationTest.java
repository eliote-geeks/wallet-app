package com.socialwallet.media.web;

import com.socialwallet.media.model.MediaAsset;
import com.socialwallet.media.model.MediaPurpose;
import com.socialwallet.media.model.MediaStatus;
import com.socialwallet.media.repository.MediaAssetRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("MediaController - Integration Tests")
class MediaControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MediaAssetRepository mediaRepository;

    private static final String USER_ID = "11111111-1111-1111-1111-111111111111";
    private static final String OTHER_USER_ID = "22222222-2222-2222-2222-222222222222";

    @BeforeEach
    void setUp() {
        mediaRepository.deleteAll();
    }

    // ──────────────────────────────────────────────
    // UPLOAD TESTS
    // ──────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/media - Upload")
    class UploadTests {

        @Test
        @WithMockUser(username = USER_ID)
        @DisplayName("Should upload a valid JPEG image")
        void upload_validJpeg_success() throws Exception {
            byte[] jpegContent = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10};
            MockMultipartFile file = new MockMultipartFile(
                    "file", "photo.jpg", "image/jpeg", jpegContent);

            mockMvc.perform(multipart("/api/media")
                            .file(file)
                            .param("purpose", "AVATAR"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.mediaId").isNotEmpty())
                    .andExpect(jsonPath("$.purpose").value("AVATAR"))
                    .andExpect(jsonPath("$.mimeType").value("image/jpeg"))
                    .andExpect(jsonPath("$.status").value("UPLOADED"));

            assertThat(mediaRepository.count()).isEqualTo(1);
        }

        @Test
        @WithMockUser(username = USER_ID)
        @DisplayName("Should upload a PNG image for story")
        void upload_pngForStory_success() throws Exception {
            byte[] pngContent = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
            MockMultipartFile file = new MockMultipartFile(
                    "file", "story.png", "image/png", pngContent);

            mockMvc.perform(multipart("/api/media")
                            .file(file)
                            .param("purpose", "STORY"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.purpose").value("STORY"));
        }

        @Test
        @WithMockUser(username = USER_ID)
        @DisplayName("Should return existing media on duplicate idempotency key")
        void upload_idempotent() throws Exception {
            byte[] jpegContent = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
            MockMultipartFile file = new MockMultipartFile(
                    "file", "photo.jpg", "image/jpeg", jpegContent);
            String idempotencyKey = UUID.randomUUID().toString();

            // First upload
            mockMvc.perform(multipart("/api/media")
                            .file(file)
                            .param("purpose", "AVATAR")
                            .param("idempotencyKey", idempotencyKey))
                    .andExpect(status().isCreated());

            // Second upload with same key — should return existing
            mockMvc.perform(multipart("/api/media")
                            .file(file)
                            .param("purpose", "AVATAR")
                            .param("idempotencyKey", idempotencyKey))
                    .andExpect(status().isCreated());

            // Only one record in DB
            assertThat(mediaRepository.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("Should return 401 when unauthenticated")
        void upload_unauthenticated_401() throws Exception {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "photo.jpg", "image/jpeg", new byte[]{1, 2, 3});

            mockMvc.perform(multipart("/api/media")
                            .file(file)
                            .param("purpose", "AVATAR"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @WithMockUser(username = USER_ID)
        @DisplayName("Should reject empty file")
        void upload_emptyFile_400() throws Exception {
            MockMultipartFile emptyFile = new MockMultipartFile(
                    "file", "empty.jpg", "image/jpeg", new byte[0]);

            mockMvc.perform(multipart("/api/media")
                            .file(emptyFile)
                            .param("purpose", "AVATAR"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(containsString("empty")));
        }
    }

    // ──────────────────────────────────────────────
    // METADATA TESTS
    // ──────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/media/{mediaId} - Metadata")
    class MetadataTests {

        @Test
        @WithMockUser(username = USER_ID)
        @DisplayName("Should return metadata for existing media")
        void getMetadata_success() throws Exception {
            MediaAsset asset = createTestAsset(USER_ID, MediaPurpose.STORY, MediaStatus.UPLOADED);

            mockMvc.perform(get("/api/media/{mediaId}", asset.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.mediaId").value(asset.getId().toString()))
                    .andExpect(jsonPath("$.purpose").value("STORY"))
                    .andExpect(jsonPath("$.mimeType").value("image/jpeg"));
        }

        @Test
        @WithMockUser(username = USER_ID)
        @DisplayName("Should return 404 for non-existent media")
        void getMetadata_notFound() throws Exception {
            mockMvc.perform(get("/api/media/{mediaId}", UUID.randomUUID()))
                    .andExpect(status().isNotFound());
        }

        @Test
        @WithMockUser(username = USER_ID)
        @DisplayName("Should return 404 for deleted media")
        void getMetadata_deleted_404() throws Exception {
            MediaAsset asset = createTestAsset(USER_ID, MediaPurpose.STORY, MediaStatus.DELETED);

            mockMvc.perform(get("/api/media/{mediaId}", asset.getId()))
                    .andExpect(status().isNotFound());
        }
    }

    // ──────────────────────────────────────────────
    // DISPLAY URL TESTS
    // ──────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/media/{mediaId}/display - Display URL")
    class DisplayUrlTests {

        @Test
        @WithMockUser(username = USER_ID)
        @DisplayName("Should return display URL without resize")
        void displayUrl_noResize() throws Exception {
            MediaAsset asset = createTestAsset(USER_ID, MediaPurpose.AVATAR, MediaStatus.UPLOADED);

            mockMvc.perform(get("/api/media/{mediaId}/display", asset.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.mediaId").value(asset.getId().toString()))
                    .andExpect(jsonPath("$.url").isNotEmpty())
                    .andExpect(jsonPath("$.expiresAt").isNotEmpty());
        }

        @Test
        @WithMockUser(username = USER_ID)
        @DisplayName("Should return display URL with resize parameters")
        void displayUrl_withResize() throws Exception {
            MediaAsset asset = createTestAsset(USER_ID, MediaPurpose.AVATAR, MediaStatus.UPLOADED);

            mockMvc.perform(get("/api/media/{mediaId}/display", asset.getId())
                            .param("width", "150")
                            .param("height", "150")
                            .param("crop", "true"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.url").isNotEmpty());
        }
    }

    // ──────────────────────────────────────────────
    // DOWNLOAD URL TESTS
    // ──────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/media/{mediaId}/download - Download URL")
    class DownloadUrlTests {

        @Test
        @WithMockUser(username = USER_ID)
        @DisplayName("Should return download URL for uploader")
        void downloadUrl_uploader_success() throws Exception {
            MediaAsset asset = createTestAsset(USER_ID, MediaPurpose.CHAT_MESSAGE, MediaStatus.UPLOADED);

            mockMvc.perform(get("/api/media/{mediaId}/download", asset.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.url").isNotEmpty());
        }

        @Test
        @WithMockUser(username = OTHER_USER_ID)
        @DisplayName("Should return 403 for non-uploader")
        void downloadUrl_notUploader_403() throws Exception {
            MediaAsset asset = createTestAsset(USER_ID, MediaPurpose.CHAT_MESSAGE, MediaStatus.UPLOADED);

            mockMvc.perform(get("/api/media/{mediaId}/download", asset.getId()))
                    .andExpect(status().isForbidden());
        }
    }

    // ──────────────────────────────────────────────
    // DELETE TESTS
    // ──────────────────────────────────────────────

    @Nested
    @DisplayName("DELETE /api/media/{mediaId} - Delete")
    class DeleteTests {

        @Test
        @WithMockUser(username = USER_ID)
        @DisplayName("Should soft-delete media for uploader")
        void delete_uploader_success() throws Exception {
            MediaAsset asset = createTestAsset(USER_ID, MediaPurpose.STORY, MediaStatus.UPLOADED);

            mockMvc.perform(delete("/api/media/{mediaId}", asset.getId()))
                    .andExpect(status().isNoContent());

            MediaAsset updated = mediaRepository.findById(asset.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(MediaStatus.DELETED);
        }

        @Test
        @WithMockUser(username = OTHER_USER_ID)
        @DisplayName("Should return 403 when non-uploader tries to delete")
        void delete_notUploader_403() throws Exception {
            MediaAsset asset = createTestAsset(USER_ID, MediaPurpose.STORY, MediaStatus.UPLOADED);

            mockMvc.perform(delete("/api/media/{mediaId}", asset.getId()))
                    .andExpect(status().isForbidden());

            MediaAsset unchanged = mediaRepository.findById(asset.getId()).orElseThrow();
            assertThat(unchanged.getStatus()).isEqualTo(MediaStatus.UPLOADED);
        }

        @Test
        @WithMockUser(username = USER_ID)
        @DisplayName("Should be idempotent - deleting already deleted media returns 204")
        void delete_alreadyDeleted_idempotent() throws Exception {
            MediaAsset asset = createTestAsset(USER_ID, MediaPurpose.STORY, MediaStatus.DELETED);

            mockMvc.perform(delete("/api/media/{mediaId}", asset.getId()))
                    .andExpect(status().isNoContent());
        }

        @Test
        @WithMockUser(username = USER_ID)
        @DisplayName("Should return 404 when media does not exist")
        void delete_notFound_404() throws Exception {
            mockMvc.perform(delete("/api/media/{mediaId}", UUID.randomUUID()))
                    .andExpect(status().isNotFound());
        }
    }

    // ──────────────────────────────────────────────
    // HELPERS
    // ──────────────────────────────────────────────

    private MediaAsset createTestAsset(String uploaderId, MediaPurpose purpose, MediaStatus status) {
        MediaAsset asset = MediaAsset.builder()
                .uploaderId(UUID.fromString(uploaderId))
                .purpose(purpose)
                .originalFilename("test-file.jpg")
                .mimeType("image/jpeg")
                .sizeBytes(2048L)
                .storagePath(purpose.name().toLowerCase() + "/" + uploaderId + "/" + UUID.randomUUID() + ".jpg")
                .status(status)
                .createdAt(Instant.now())
                .build();
        return mediaRepository.save(asset);
    }
}