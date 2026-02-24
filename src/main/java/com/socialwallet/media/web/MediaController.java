package com.socialwallet.media.web;

import com.socialwallet.media.dto.MediaDto;
import com.socialwallet.media.dto.MediaUrlDto;
import com.socialwallet.media.model.MediaPurpose;
import com.socialwallet.media.service.MediaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.util.UUID;

/**
 * REST controller for media upload, retrieval, and deletion.
 * Access control for viewing media is delegated to the calling modules
 * (Profiles, Stories, Chat, Store). This controller only enforces
 * uploader-based ownership for download and delete operations.
 */
@RestController
@RequestMapping("/api/media")
@RequiredArgsConstructor
@Tag(name = "Media", description = "Upload, retrieve, and manage media files")
public class MediaController {

    private final MediaService mediaService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Upload a media file",
            description = "Uploads a file to storage. Validates size and format against the purpose-specific policy. "
                    + "If idempotencyKey is provided and a media with the same key already exists for this user, "
                    + "the existing media is returned without re-uploading."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Media uploaded successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid file (wrong format, too large, empty)"),
            @ApiResponse(responseCode = "401", description = "Unauthenticated"),
            @ApiResponse(responseCode = "429", description = "Upload rate limit exceeded")
    })
    public ResponseEntity<MediaDto> upload(
            Principal principal,
            @Parameter(description = "File to upload") @RequestParam("file") MultipartFile file,
            @Parameter(description = "Purpose of the media (determines size/format limits)") @RequestParam("purpose") MediaPurpose purpose,
            @Parameter(description = "Idempotency key to prevent duplicate uploads") @RequestParam(value = "idempotencyKey", required = false) String idempotencyKey
    ) {
        UUID uploaderId = UUID.fromString(principal.getName());
        MediaDto result = mediaService.upload(uploaderId, file, purpose, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @GetMapping("/{mediaId}")
    @Operation(summary = "Get media metadata", description = "Returns metadata for a media asset (no file content).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Metadata retrieved"),
            @ApiResponse(responseCode = "401", description = "Unauthenticated"),
            @ApiResponse(responseCode = "404", description = "Media not found or no longer available")
    })
    public ResponseEntity<MediaDto> getMetadata(@PathVariable UUID mediaId) {
        return ResponseEntity.ok(mediaService.getMetadata(mediaId));
    }

    @GetMapping("/{mediaId}/display")
    @Operation(
            summary = "Get signed display URL",
            description = "Returns a signed URL for displaying the media. For images, supports optional resize parameters. "
                    + "For non-images, returns a presigned S3 download URL."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Display URL generated"),
            @ApiResponse(responseCode = "401", description = "Unauthenticated"),
            @ApiResponse(responseCode = "404", description = "Media not found or no longer available")
    })
    public ResponseEntity<MediaUrlDto> getDisplayUrl(
            @PathVariable UUID mediaId,
            @Parameter(description = "Target width in pixels (0 = auto)") @RequestParam(value = "width", required = false) Integer width,
            @Parameter(description = "Target height in pixels (0 = auto)") @RequestParam(value = "height", required = false) Integer height,
            @Parameter(description = "If true, crop to exact dimensions") @RequestParam(value = "crop", defaultValue = "false") boolean crop
    ) {
        return ResponseEntity.ok(mediaService.getDisplayUrl(mediaId, width, height, crop));
    }

    @GetMapping("/{mediaId}/download")
    @Operation(
            summary = "Get signed download URL",
            description = "Returns a presigned S3 URL for direct file download. Only the uploader can request this via the API. "
                    + "Other modules use internal service methods for controlled access."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Download URL generated"),
            @ApiResponse(responseCode = "401", description = "Unauthenticated"),
            @ApiResponse(responseCode = "403", description = "Not the uploader of this media"),
            @ApiResponse(responseCode = "404", description = "Media not found or no longer available")
    })
    public ResponseEntity<MediaUrlDto> getDownloadUrl(Principal principal, @PathVariable UUID mediaId) {
        UUID requesterId = UUID.fromString(principal.getName());
        return ResponseEntity.ok(mediaService.getDownloadUrl(requesterId, mediaId));
    }

    @DeleteMapping("/{mediaId}")
    @Operation(
            summary = "Delete a media file",
            description = "Soft-deletes a media asset. Only the uploader can delete their own media. "
                    + "The file is physically removed from storage after a grace period. Idempotent."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Media deleted (or already deleted)"),
            @ApiResponse(responseCode = "401", description = "Unauthenticated"),
            @ApiResponse(responseCode = "403", description = "Not the uploader of this media"),
            @ApiResponse(responseCode = "404", description = "Media not found")
    })
    public ResponseEntity<Void> delete(Principal principal, @PathVariable UUID mediaId) {
        UUID requesterId = UUID.fromString(principal.getName());
        mediaService.delete(requesterId, mediaId);
        return ResponseEntity.noContent().build();
    }
}
