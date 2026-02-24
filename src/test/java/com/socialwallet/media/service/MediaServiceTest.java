package com.socialwallet.media.service;

import com.socialwallet.media.config.MediaPolicyProperties;
import com.socialwallet.media.dto.MediaDto;
import com.socialwallet.media.dto.MediaUrlDto;
import com.socialwallet.media.exception.*;
import com.socialwallet.media.model.MediaAsset;
import com.socialwallet.media.model.MediaPurpose;
import com.socialwallet.media.model.MediaStatus;
import com.socialwallet.media.provider.StorageProvider;
import com.socialwallet.media.repository.MediaAssetRepository;
import com.socialwallet.media.validation.FileValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MediaServiceTest {

    @Mock private MediaAssetRepository mediaRepository;
    @Mock private StorageProvider storageProvider;
    @Mock private FileValidator fileValidator;
    @Mock private UrlSigningService urlSigningService;
    @Mock private MediaPolicyProperties policyProperties;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private MediaService mediaService;

    private UUID userId;
    private UUID mediaId;
    private MultipartFile mockFile;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        mediaId = UUID.randomUUID();
        mockFile = mock(MultipartFile.class);
    }

    // ──────────────────────────────────────────────
    // UPLOAD TESTS
    // ──────────────────────────────────────────────

    @Nested
    @DisplayName("Upload")
    class UploadTests {

        @Test
        @DisplayName("Should upload file successfully")
        void upload_success() throws IOException {
            // Given
            when(mockFile.isEmpty()).thenReturn(false);
            when(mockFile.getSize()).thenReturn(1024L);
            when(mockFile.getOriginalFilename()).thenReturn("photo.jpg");
            when(mockFile.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[1024]));

            when(fileValidator.detectMimeType(mockFile)).thenReturn("image/jpeg");
            when(mediaRepository.countRecentUploads(eq(userId), any())).thenReturn(0L);

            MediaPolicyProperties.PolicyEntry policy = new MediaPolicyProperties.PolicyEntry();
            policy.setMaxSizeMb(5);
            policy.setAllowedTypes(List.of("image/jpeg"));
            when(policyProperties.getPolicyFor(MediaPurpose.AVATAR)).thenReturn(policy);
            when(policyProperties.getMaxUploadsPerHour()).thenReturn(30);

            when(mediaRepository.save(any(MediaAsset.class))).thenAnswer(inv -> {
                MediaAsset a = inv.getArgument(0);
                if (a.getCreatedAt() == null) a.setCreatedAt(Instant.now());
                return a;
            });

            // When
            MediaDto result = mediaService.upload(userId, mockFile, MediaPurpose.AVATAR, null);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getUploaderId()).isEqualTo(userId);
            assertThat(result.getPurpose()).isEqualTo(MediaPurpose.AVATAR);
            assertThat(result.getMimeType()).isEqualTo("image/jpeg");
            assertThat(result.getStatus()).isEqualTo(MediaStatus.UPLOADED);

            verify(storageProvider).upload(any(), anyString(), eq("image/jpeg"), eq(1024L));
            verify(eventPublisher).publishEvent(any());
        }

        @Test
        @DisplayName("Should return existing media on duplicate idempotency key")
        void upload_idempotent_returnExisting() {
            // Given
            String idempotencyKey = "dup-key-123";
            MediaAsset existing = MediaAsset.builder()
                    .id(mediaId)
                    .uploaderId(userId)
                    .purpose(MediaPurpose.STORY)
                    .mimeType("image/png")
                    .sizeBytes(2048L)
                    .storagePath("story/user/media.png")
                    .status(MediaStatus.UPLOADED)
                    .createdAt(Instant.now())
                    .build();

            when(mediaRepository.findByIdempotencyKeyAndUploaderId(idempotencyKey, userId))
                    .thenReturn(Optional.of(existing));

            // When
            MediaDto result = mediaService.upload(userId, mockFile, MediaPurpose.STORY, idempotencyKey);

            // Then
            assertThat(result.getMediaId()).isEqualTo(mediaId);
            verify(storageProvider, never()).upload(any(), any(), any(), anyLong());
        }

        @Test
        @DisplayName("Should reject upload when rate limit exceeded")
        void upload_rateLimitExceeded() {
            // Given
            when(mediaRepository.findByIdempotencyKeyAndUploaderId(any(), any())).thenReturn(Optional.empty());
            when(mediaRepository.countRecentUploads(eq(userId), any())).thenReturn(30L);
            when(policyProperties.getMaxUploadsPerHour()).thenReturn(30);

            // When/Then
            assertThatThrownBy(() -> mediaService.upload(userId, mockFile, MediaPurpose.STORY, "key"))
                    .isInstanceOf(RateLimitExceededException.class);
        }

        @Test
        @DisplayName("Should set status FAILED when S3 upload fails")
        void upload_s3Failure() throws IOException {
            // Given
            when(mockFile.isEmpty()).thenReturn(false);
            when(mockFile.getSize()).thenReturn(1024L);
            when(mockFile.getOriginalFilename()).thenReturn("photo.jpg");
            when(mockFile.getInputStream()).thenThrow(new IOException("Stream error"));

            when(fileValidator.detectMimeType(mockFile)).thenReturn("image/jpeg");
            when(mediaRepository.countRecentUploads(eq(userId), any())).thenReturn(0L);
            when(policyProperties.getMaxUploadsPerHour()).thenReturn(30);

            MediaPolicyProperties.PolicyEntry policy = new MediaPolicyProperties.PolicyEntry();
            policy.setMaxSizeMb(5);
            when(policyProperties.getPolicyFor(MediaPurpose.STORY)).thenReturn(policy);

            when(mediaRepository.save(any(MediaAsset.class))).thenAnswer(inv -> inv.getArgument(0));

            // When/Then
            assertThatThrownBy(() -> mediaService.upload(userId, mockFile, MediaPurpose.STORY, null))
                    .isInstanceOf(MediaUploadException.class);

            ArgumentCaptor<MediaAsset> captor = ArgumentCaptor.forClass(MediaAsset.class);
            verify(mediaRepository, atLeastOnce()).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(MediaStatus.FAILED);
        }

        @Test
        @DisplayName("Should deactivate previous avatars when uploading new one")
        void upload_avatar_deactivatesPrevious() throws IOException {
            // Given
            when(mockFile.isEmpty()).thenReturn(false);
            when(mockFile.getSize()).thenReturn(1024L);
            when(mockFile.getOriginalFilename()).thenReturn("avatar.jpg");
            when(mockFile.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[1024]));

            when(fileValidator.detectMimeType(mockFile)).thenReturn("image/jpeg");
            when(mediaRepository.countRecentUploads(eq(userId), any())).thenReturn(0L);
            when(policyProperties.getMaxUploadsPerHour()).thenReturn(30);

            MediaPolicyProperties.PolicyEntry policy = new MediaPolicyProperties.PolicyEntry();
            policy.setMaxSizeMb(5);
            policy.setAllowedTypes(List.of("image/jpeg"));
            when(policyProperties.getPolicyFor(MediaPurpose.AVATAR)).thenReturn(policy);

            UUID oldAvatarId = UUID.randomUUID();
            MediaAsset oldAvatar = MediaAsset.builder()
                    .id(oldAvatarId)
                    .uploaderId(userId)
                    .status(MediaStatus.UPLOADED)
                    .build();

            when(mediaRepository.findByUploaderIdAndPurposeAndStatus(userId, MediaPurpose.AVATAR, MediaStatus.UPLOADED))
                    .thenReturn(List.of(oldAvatar));

            when(mediaRepository.save(any(MediaAsset.class))).thenAnswer(inv -> {
                MediaAsset a = inv.getArgument(0);
                if (a.getCreatedAt() == null) a.setCreatedAt(Instant.now());
                return a;
            });

            // When
            mediaService.upload(userId, mockFile, MediaPurpose.AVATAR, null);

            // Then — old avatar should be set to DELETED
            assertThat(oldAvatar.getStatus()).isEqualTo(MediaStatus.DELETED);
        }
    }

    // ──────────────────────────────────────────────
    // METADATA TESTS
    // ──────────────────────────────────────────────

    @Nested
    @DisplayName("Metadata")
    class MetadataTests {

        @Test
        @DisplayName("Should return metadata for active media")
        void getMetadata_success() {
            MediaAsset asset = buildTestAsset(MediaStatus.UPLOADED);
            when(mediaRepository.findById(mediaId)).thenReturn(Optional.of(asset));

            MediaDto result = mediaService.getMetadata(mediaId);

            assertThat(result.getMediaId()).isEqualTo(mediaId);
            assertThat(result.getMimeType()).isEqualTo("image/jpeg");
        }

        @Test
        @DisplayName("Should throw when media not found")
        void getMetadata_notFound() {
            when(mediaRepository.findById(mediaId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> mediaService.getMetadata(mediaId))
                    .isInstanceOf(MediaNotFoundException.class);
        }

        @Test
        @DisplayName("Should throw when media is deleted")
        void getMetadata_deleted() {
            MediaAsset asset = buildTestAsset(MediaStatus.DELETED);
            when(mediaRepository.findById(mediaId)).thenReturn(Optional.of(asset));

            assertThatThrownBy(() -> mediaService.getMetadata(mediaId))
                    .isInstanceOf(MediaNotFoundException.class);
        }
    }

    // ──────────────────────────────────────────────
    // DISPLAY URL TESTS
    // ──────────────────────────────────────────────

    @Nested
    @DisplayName("Display URL")
    class DisplayUrlTests {

        @Test
        @DisplayName("Should return imgproxy URL for image with resize")
        void displayUrl_imageWithResize() {
            MediaAsset asset = buildTestAsset(MediaStatus.UPLOADED);
            when(mediaRepository.findById(mediaId)).thenReturn(Optional.of(asset));
            when(urlSigningService.buildImageDisplayUrl(asset.getStoragePath(), 150, 150, false))
                    .thenReturn("https://cdn.test/signed/rs:fit:150:150/plain/s3://bucket/path.jpg");

            MediaUrlDto result = mediaService.getDisplayUrl(mediaId, 150, 150, false);

            assertThat(result.getUrl()).contains("rs:fit:150:150");
        }

        @Test
        @DisplayName("Should return imgproxy URL for image at original size")
        void displayUrl_imageOriginalSize() {
            MediaAsset asset = buildTestAsset(MediaStatus.UPLOADED);
            when(mediaRepository.findById(mediaId)).thenReturn(Optional.of(asset));
            when(urlSigningService.buildImageDisplayUrl(asset.getStoragePath()))
                    .thenReturn("https://cdn.test/signed/plain/s3://bucket/path.jpg");

            MediaUrlDto result = mediaService.getDisplayUrl(mediaId, null, null, false);

            assertThat(result.getUrl()).contains("plain");
        }

        @Test
        @DisplayName("Should return presigned URL for non-image files")
        void displayUrl_nonImage() {
            MediaAsset asset = buildTestAsset(MediaStatus.UPLOADED);
            asset.setMimeType("application/pdf");
            when(mediaRepository.findById(mediaId)).thenReturn(Optional.of(asset));
            when(urlSigningService.buildDownloadUrl(asset.getStoragePath()))
                    .thenReturn("https://s3.test/presigned/path.pdf");

            MediaUrlDto result = mediaService.getDisplayUrl(mediaId, null, null, false);

            assertThat(result.getUrl()).contains("presigned");
        }
    }

    // ──────────────────────────────────────────────
    // DOWNLOAD URL TESTS
    // ──────────────────────────────────────────────

    @Nested
    @DisplayName("Download URL")
    class DownloadUrlTests {

        @Test
        @DisplayName("Should return download URL for uploader")
        void downloadUrl_uploaderCanAccess() {
            MediaAsset asset = buildTestAsset(MediaStatus.UPLOADED);
            when(mediaRepository.findById(mediaId)).thenReturn(Optional.of(asset));
            when(urlSigningService.buildDownloadUrl(asset.getStoragePath()))
                    .thenReturn("https://s3.test/download/path.jpg");

            MediaUrlDto result = mediaService.getDownloadUrl(userId, mediaId);

            assertThat(result.getUrl()).isNotBlank();
        }

        @Test
        @DisplayName("Should deny download URL for non-uploader")
        void downloadUrl_notUploader_denied() {
            MediaAsset asset = buildTestAsset(MediaStatus.UPLOADED);
            when(mediaRepository.findById(mediaId)).thenReturn(Optional.of(asset));

            UUID otherUser = UUID.randomUUID();
            assertThatThrownBy(() -> mediaService.getDownloadUrl(otherUser, mediaId))
                    .isInstanceOf(MediaAccessDeniedException.class);
        }
    }

    // ──────────────────────────────────────────────
    // DELETE TESTS
    // ──────────────────────────────────────────────

    @Nested
    @DisplayName("Delete")
    class DeleteTests {

        @Test
        @DisplayName("Should soft-delete media for uploader")
        void delete_uploaderCanDelete() {
            MediaAsset asset = buildTestAsset(MediaStatus.UPLOADED);
            when(mediaRepository.findById(mediaId)).thenReturn(Optional.of(asset));
            when(mediaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            mediaService.delete(userId, mediaId);

            assertThat(asset.getStatus()).isEqualTo(MediaStatus.DELETED);
            verify(eventPublisher).publishEvent(any());
        }

        @Test
        @DisplayName("Should deny deletion for non-uploader")
        void delete_notUploader_denied() {
            MediaAsset asset = buildTestAsset(MediaStatus.UPLOADED);
            when(mediaRepository.findById(mediaId)).thenReturn(Optional.of(asset));

            UUID otherUser = UUID.randomUUID();
            assertThatThrownBy(() -> mediaService.delete(otherUser, mediaId))
                    .isInstanceOf(MediaAccessDeniedException.class);
        }

        @Test
        @DisplayName("Should be idempotent when already deleted")
        void delete_alreadyDeleted_idempotent() {
            MediaAsset asset = buildTestAsset(MediaStatus.DELETED);
            when(mediaRepository.findById(mediaId)).thenReturn(Optional.of(asset));

            // Should not throw
            mediaService.delete(userId, mediaId);

            verify(eventPublisher, never()).publishEvent(any());
        }
    }

    // ──────────────────────────────────────────────
    // INTERNAL METHODS TESTS
    // ──────────────────────────────────────────────

    @Nested
    @DisplayName("Internal methods")
    class InternalTests {

        @Test
        @DisplayName("exists() returns true for UPLOADED media")
        void exists_uploaded_true() {
            MediaAsset asset = buildTestAsset(MediaStatus.UPLOADED);
            when(mediaRepository.findById(mediaId)).thenReturn(Optional.of(asset));

            assertThat(mediaService.exists(mediaId)).isTrue();
        }

        @Test
        @DisplayName("exists() returns false for DELETED media")
        void exists_deleted_false() {
            MediaAsset asset = buildTestAsset(MediaStatus.DELETED);
            when(mediaRepository.findById(mediaId)).thenReturn(Optional.of(asset));

            assertThat(mediaService.exists(mediaId)).isFalse();
        }

        @Test
        @DisplayName("exists() returns false for unknown ID")
        void exists_unknown_false() {
            when(mediaRepository.findById(any())).thenReturn(Optional.empty());

            assertThat(mediaService.exists(UUID.randomUUID())).isFalse();
        }

        @Test
        @DisplayName("getDisplayUrl(mediaId, w, h) returns imgproxy URL")
        void internalDisplayUrl_withResize() {
            MediaAsset asset = buildTestAsset(MediaStatus.UPLOADED);
            when(mediaRepository.findById(mediaId)).thenReturn(Optional.of(asset));
            when(urlSigningService.buildImageDisplayUrl(asset.getStoragePath(), 800, 600, false))
                    .thenReturn("https://cdn.test/signed/url");

            String url = mediaService.getDisplayUrl(mediaId, 800, 600);

            assertThat(url).isEqualTo("https://cdn.test/signed/url");
        }

        @Test
        @DisplayName("getDisplayUrl(mediaId) returns imgproxy URL at original size")
        void internalDisplayUrl_originalSize() {
            MediaAsset asset = buildTestAsset(MediaStatus.UPLOADED);
            when(mediaRepository.findById(mediaId)).thenReturn(Optional.of(asset));
            when(urlSigningService.buildImageDisplayUrl(asset.getStoragePath()))
                    .thenReturn("https://cdn.test/signed/original");

            String url = mediaService.getDisplayUrl(mediaId);

            assertThat(url).isEqualTo("https://cdn.test/signed/original");
        }
    }

    // ──────────────────────────────────────────────
    // HELPERS
    // ──────────────────────────────────────────────

    private MediaAsset buildTestAsset(MediaStatus status) {
        return MediaAsset.builder()
                .id(mediaId)
                .uploaderId(userId)
                .purpose(MediaPurpose.STORY)
                .originalFilename("photo.jpg")
                .mimeType("image/jpeg")
                .sizeBytes(2048L)
                .storagePath("story/" + userId + "/" + mediaId + ".jpg")
                .status(status)
                .createdAt(Instant.now())
                .build();
    }
}
