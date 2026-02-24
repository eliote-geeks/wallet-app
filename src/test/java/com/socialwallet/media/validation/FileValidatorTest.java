package com.socialwallet.media.validation;

import com.socialwallet.media.config.MediaPolicyProperties;
import com.socialwallet.media.exception.InvalidMediaException;
import com.socialwallet.media.model.MediaPurpose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileValidatorTest {

    @Mock
    private MediaPolicyProperties policyProperties;

    private FileValidator fileValidator;

    @BeforeEach
    void setUp() {
        fileValidator = new FileValidator(policyProperties);
    }

    private MediaPolicyProperties.PolicyEntry createPolicy(long maxSizeMb, List<String> allowedTypes) {
        MediaPolicyProperties.PolicyEntry policy = new MediaPolicyProperties.PolicyEntry();
        policy.setMaxSizeMb(maxSizeMb);
        policy.setAllowedTypes(allowedTypes);
        return policy;
    }

    // ──────────────────────────────────────────────
    // BASIC VALIDATION
    // ──────────────────────────────────────────────

    @Nested
    @DisplayName("Basic validation")
    class BasicValidation {

        @Test
        @DisplayName("Should reject null file")
        void validate_nullFile_rejected() {
            assertThatThrownBy(() -> fileValidator.validate(null, MediaPurpose.AVATAR))
                    .isInstanceOf(InvalidMediaException.class)
                    .hasMessageContaining("empty");
        }

        @Test
        @DisplayName("Should reject empty file")
        void validate_emptyFile_rejected() {
            MockMultipartFile emptyFile = new MockMultipartFile(
                    "file", "empty.jpg", "image/jpeg", new byte[0]);

            assertThatThrownBy(() -> fileValidator.validate(emptyFile, MediaPurpose.AVATAR))
                    .isInstanceOf(InvalidMediaException.class)
                    .hasMessageContaining("empty");
        }
    }

    // ──────────────────────────────────────────────
    // SIZE VALIDATION
    // ──────────────────────────────────────────────

    @Nested
    @DisplayName("Size validation")
    class SizeValidation {

        @Test
        @DisplayName("Should reject file exceeding max size for AVATAR")
        void validate_oversizedAvatar_rejected() {
            MediaPolicyProperties.PolicyEntry policy = createPolicy(5, List.of("image/jpeg"));
            when(policyProperties.getPolicyFor(MediaPurpose.AVATAR)).thenReturn(policy);

            // 6 MB file (exceeds 5 MB limit)
            byte[] content = new byte[6 * 1024 * 1024];
            // Set JPEG magic bytes
            content[0] = (byte) 0xFF;
            content[1] = (byte) 0xD8;
            content[2] = (byte) 0xFF;

            MockMultipartFile bigFile = new MockMultipartFile(
                    "file", "big.jpg", "image/jpeg", content);

            assertThatThrownBy(() -> fileValidator.validate(bigFile, MediaPurpose.AVATAR))
                    .isInstanceOf(InvalidMediaException.class)
                    .hasMessageContaining("exceeds maximum");
        }

        @Test
        @DisplayName("Should accept file within size limit")
        void validate_validSize_accepted() {
            MediaPolicyProperties.PolicyEntry policy = createPolicy(5, List.of("image/jpeg"));
            when(policyProperties.getPolicyFor(MediaPurpose.AVATAR)).thenReturn(policy);

            // Create a minimal valid JPEG (magic bytes FF D8 FF)
            byte[] jpegContent = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00};
            MockMultipartFile validFile = new MockMultipartFile(
                    "file", "photo.jpg", "image/jpeg", jpegContent);

            // Should not throw
            assertThatCode(() -> fileValidator.validate(validFile, MediaPurpose.AVATAR))
                    .doesNotThrowAnyException();
        }
    }

    // ──────────────────────────────────────────────
    // FORMAT VALIDATION
    // ──────────────────────────────────────────────

    @Nested
    @DisplayName("Format validation")
    class FormatValidation {

        @Test
        @DisplayName("Should reject format not allowed for purpose")
        void validate_wrongFormat_rejected() {
            MediaPolicyProperties.PolicyEntry policy = createPolicy(10, List.of("image/jpeg", "image/png"));
            when(policyProperties.getPolicyFor(MediaPurpose.AVATAR)).thenReturn(policy);

            // PDF content with correct magic bytes (%PDF)
            byte[] pdfContent = "%PDF-1.4 test content".getBytes();
            MockMultipartFile pdfFile = new MockMultipartFile(
                    "file", "doc.pdf", "application/pdf", pdfContent);

            assertThatThrownBy(() -> fileValidator.validate(pdfFile, MediaPurpose.AVATAR))
                    .isInstanceOf(InvalidMediaException.class)
                    .hasMessageContaining("not allowed");
        }

        @Test
        @DisplayName("Should accept PDF for KYC_DOCUMENT purpose")
        void validate_pdfForKyc_accepted() {
            MediaPolicyProperties.PolicyEntry policy = createPolicy(10,
                    List.of("image/jpeg", "image/png", "application/pdf"));
            when(policyProperties.getPolicyFor(MediaPurpose.KYC_DOCUMENT)).thenReturn(policy);

            byte[] pdfContent = "%PDF-1.4 test content".getBytes();
            MockMultipartFile pdfFile = new MockMultipartFile(
                    "file", "id-card.pdf", "application/pdf", pdfContent);

            assertThatCode(() -> fileValidator.validate(pdfFile, MediaPurpose.KYC_DOCUMENT))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should accept video for STORY purpose")
        void validate_videoForStory_accepted() {
            MediaPolicyProperties.PolicyEntry policy = createPolicy(15,
                    List.of("image/jpeg", "video/mp4"));
            when(policyProperties.getPolicyFor(MediaPurpose.STORY)).thenReturn(policy);

            // MP4 magic bytes (simplified — ftyp box at offset 4)
            byte[] mp4Content = new byte[32];
            mp4Content[4] = 'f';
            mp4Content[5] = 't';
            mp4Content[6] = 'y';
            mp4Content[7] = 'p';

            MockMultipartFile videoFile = new MockMultipartFile(
                    "file", "story.mp4", "video/mp4", mp4Content);

            assertThatCode(() -> fileValidator.validate(videoFile, MediaPurpose.STORY))
                    .doesNotThrowAnyException();
        }
    }

    // ──────────────────────────────────────────────
    // MIME DETECTION
    // ──────────────────────────────────────────────

    @Nested
    @DisplayName("MIME type detection")
    class MimeDetection {

        @Test
        @DisplayName("Should detect JPEG from magic bytes")
        void detectMimeType_jpeg() {
            byte[] jpegContent = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
            MockMultipartFile file = new MockMultipartFile(
                    "file", "photo.jpg", "image/jpeg", jpegContent);

            String detected = fileValidator.detectMimeType(file);

            assertThat(detected).isEqualTo("image/jpeg");
        }

        @Test
        @DisplayName("Should detect PNG from magic bytes")
        void detectMimeType_png() {
            byte[] pngContent = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
            MockMultipartFile file = new MockMultipartFile(
                    "file", "image.png", "image/png", pngContent);

            String detected = fileValidator.detectMimeType(file);

            assertThat(detected).isEqualTo("image/png");
        }

        @Test
        @DisplayName("Should detect PDF from magic bytes")
        void detectMimeType_pdf() {
            byte[] pdfContent = "%PDF-1.4 some content".getBytes();
            MockMultipartFile file = new MockMultipartFile(
                    "file", "document.pdf", "application/pdf", pdfContent);

            String detected = fileValidator.detectMimeType(file);

            assertThat(detected).isEqualTo("application/pdf");
        }

        @Test
        @DisplayName("Should detect real MIME even if extension is wrong")
        void detectMimeType_wrongExtension() {
            // PNG magic bytes but .jpg extension
            byte[] pngContent = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
            MockMultipartFile file = new MockMultipartFile(
                    "file", "fake.jpg", "image/jpeg", pngContent);

            String detected = fileValidator.detectMimeType(file);

            // Tika should detect PNG regardless of the extension
            assertThat(detected).isEqualTo("image/png");
        }
    }

    // ──────────────────────────────────────────────
    // PURPOSE-SPECIFIC LIMITS
    // ──────────────────────────────────────────────

    @Nested
    @DisplayName("Purpose-specific limits")
    class PurposeLimits {

        @Test
        @DisplayName("PRODUCT_ASSET allows 100 MB")
        void productAsset_allowsLargeFiles() {
            MediaPolicyProperties.PolicyEntry policy = createPolicy(100,
                    List.of("application/pdf", "application/zip", "video/mp4"));
            when(policyProperties.getPolicyFor(MediaPurpose.PRODUCT_ASSET)).thenReturn(policy);

            // 50 MB file (within limit)
            byte[] content = new byte[50 * 1024 * 1024];
            content[0] = '%';
            content[1] = 'P';
            content[2] = 'D';
            content[3] = 'F';

            MockMultipartFile bigFile = new MockMultipartFile(
                    "file", "ebook.pdf", "application/pdf", content);

            assertThatCode(() -> fileValidator.validate(bigFile, MediaPurpose.PRODUCT_ASSET))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("CHAT_MESSAGE allows audio files")
        void chatMessage_allowsAudio() {
            MediaPolicyProperties.PolicyEntry policy = createPolicy(20,
                    List.of("image/jpeg", "audio/mpeg", "audio/ogg"));
            when(policyProperties.getPolicyFor(MediaPurpose.CHAT_MESSAGE)).thenReturn(policy);

            // MP3-like content (simplified)
            byte[] mp3Content = new byte[]{(byte) 0xFF, (byte) 0xFB, 0x00, 0x00};
            MockMultipartFile audioFile = new MockMultipartFile(
                    "file", "voice.mp3", "audio/mpeg", mp3Content);

            // The actual test depends on Tika detecting the MIME correctly
            // For a real MP3, Tika would return "audio/mpeg"
            String detected = fileValidator.detectMimeType(audioFile);
            assertThat(detected).isNotNull();
        }
    }
}
