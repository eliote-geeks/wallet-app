package com.socialwallet.media.service;

import com.socialwallet.media.config.ImgproxyProperties;
import com.socialwallet.media.config.S3Properties;
import com.socialwallet.media.provider.StorageProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UrlSigningServiceTest {

    @Mock
    private StorageProvider storageProvider;

    private UrlSigningService urlSigningService;

    @BeforeEach
    void setUp() {
        ImgproxyProperties imgproxyProps = new ImgproxyProperties();
        imgproxyProps.setBaseUrl("https://cdn.test.local");
        imgproxyProps.setKey("736563726574"); // "secret" in hex
        imgproxyProps.setSalt("73616c74");     // "salt" in hex
        imgproxyProps.setUrlExpirationSeconds(3600);

        S3Properties s3Props = new S3Properties();
        s3Props.setBucket("test-bucket");

        urlSigningService = new UrlSigningService(imgproxyProps, s3Props, storageProvider);
    }

    // ──────────────────────────────────────────────
    // IMGPROXY URL GENERATION
    // ──────────────────────────────────────────────

    @Nested
    @DisplayName("imgproxy URL generation")
    class ImgproxyUrlTests {

        @Test
        @DisplayName("Should build URL with resize (fit)")
        void buildUrl_withResize_fit() {
            String url = urlSigningService.buildImageDisplayUrl("avatar/user1/media1.jpg", 150, 150, false);

            assertThat(url)
                    .startsWith("https://cdn.test.local/")
                    .contains("/rs:fit:150:150/")
                    .contains("/plain/s3://test-bucket/avatar/user1/media1.jpg");
        }

        @Test
        @DisplayName("Should build URL with resize (crop)")
        void buildUrl_withResize_crop() {
            String url = urlSigningService.buildImageDisplayUrl("avatar/user1/media1.jpg", 200, 200, true);

            assertThat(url)
                    .contains("/rs:fill:200:200/")
                    .contains("/plain/s3://test-bucket/avatar/user1/media1.jpg");
        }

        @Test
        @DisplayName("Should build URL at original size")
        void buildUrl_originalSize() {
            String url = urlSigningService.buildImageDisplayUrl("story/user1/media1.jpg");

            assertThat(url)
                    .startsWith("https://cdn.test.local/")
                    .contains("/plain/s3://test-bucket/story/user1/media1.jpg")
                    .doesNotContain("/rs:");
        }

        @Test
        @DisplayName("Should include HMAC signature")
        void buildUrl_hasSignature() {
            String url = urlSigningService.buildImageDisplayUrl("test.jpg");

            // URL format: {base}/{signature}/{path}
            // The signature is between the base URL and the processing options
            String afterBase = url.replace("https://cdn.test.local/", "");
            String signature = afterBase.split("/")[0];

            assertThat(signature)
                    .isNotEmpty()
                    .isNotEqualTo("unsafe")
                    .doesNotContain("="); // base64url uses no padding
        }

        @Test
        @DisplayName("Same path should produce same signature (deterministic)")
        void buildUrl_deterministicSignature() {
            String url1 = urlSigningService.buildImageDisplayUrl("same/path.jpg", 100, 100, false);
            String url2 = urlSigningService.buildImageDisplayUrl("same/path.jpg", 100, 100, false);

            assertThat(url1).isEqualTo(url2);
        }

        @Test
        @DisplayName("Different paths should produce different signatures")
        void buildUrl_differentSignatures() {
            String url1 = urlSigningService.buildImageDisplayUrl("path/a.jpg", 100, 100, false);
            String url2 = urlSigningService.buildImageDisplayUrl("path/b.jpg", 100, 100, false);

            assertThat(url1).isNotEqualTo(url2);
        }
    }

    // ──────────────────────────────────────────────
    // IMGPROXY WITHOUT SIGNING (dev mode)
    // ──────────────────────────────────────────────

    @Nested
    @DisplayName("imgproxy without signing (dev mode)")
    class UnsafeMode {

        @Test
        @DisplayName("Should use 'unsafe' token when no key/salt configured")
        void buildUrl_noKeys_usesUnsafe() {
            ImgproxyProperties noKeyProps = new ImgproxyProperties();
            noKeyProps.setBaseUrl("https://cdn.dev.local");
            noKeyProps.setKey(null);
            noKeyProps.setSalt(null);

            S3Properties s3Props = new S3Properties();
            s3Props.setBucket("dev-bucket");

            UrlSigningService devService = new UrlSigningService(noKeyProps, s3Props, storageProvider);

            String url = devService.buildImageDisplayUrl("test.jpg");

            assertThat(url).startsWith("https://cdn.dev.local/unsafe/");
        }
    }

    // ──────────────────────────────────────────────
    // DOWNLOAD URL (presigned S3)
    // ──────────────────────────────────────────────

    @Nested
    @DisplayName("Download URL")
    class DownloadUrlTests {

        @Test
        @DisplayName("Should delegate to storage provider for presigned URL")
        void buildDownloadUrl_delegatesToProvider() {
            when(storageProvider.generatePresignedUrl(eq("docs/file.pdf"), any(Duration.class)))
                    .thenReturn("https://s3.test/presigned/docs/file.pdf?sig=abc");

            String url = urlSigningService.buildDownloadUrl("docs/file.pdf");

            assertThat(url).isEqualTo("https://s3.test/presigned/docs/file.pdf?sig=abc");
        }
    }
}
