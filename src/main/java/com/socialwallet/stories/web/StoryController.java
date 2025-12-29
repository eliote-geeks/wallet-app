package com.socialwallet.stories.web;

import com.socialwallet.stories.dto.*;
import com.socialwallet.stories.service.StoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

/**
 * REST controller for Stories (WhatsApp Status model).
 * All stories expire after 24 hours.
 */
@RestController
@RequestMapping("/api/stories")
@RequiredArgsConstructor
public class StoryController {

    private final StoryService storyService;

    @PostMapping
    @Operation(summary = "Create a new story", description = "Creates a story that expires in 24 hours. Type can be IMAGE, VIDEO, or TEXT.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Story created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request (missing required fields)"),
        @ApiResponse(responseCode = "401", description = "Unauthenticated")
    })
    public ResponseEntity<StoryDto> createStory(Principal principal, @Valid @RequestBody PostStoryRequest request) {
        UUID authorId = UUID.fromString(principal.getName());
        StoryDto created = storyService.createStory(authorId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/me")
    @Operation(summary = "Get my active stories", description = "Returns all my stories that have not expired yet, with view counts.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Stories retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthenticated")
    })
    public ResponseEntity<List<StoryDto>> getMyStories(Principal principal) {
        UUID authorId = UUID.fromString(principal.getName());
        return ResponseEntity.ok(storyService.getMyActiveStories(authorId));
    }

    @GetMapping("/contacts")
    @Operation(summary = "Get stories from my contacts", description = "Returns active stories from all mutual contacts, grouped by author and sorted by unviewed first.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Stories retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthenticated")
    })
    public ResponseEntity<List<StoryPreviewDto>> getContactsStories(Principal principal) {
        UUID viewerId = UUID.fromString(principal.getName());
        return ResponseEntity.ok(storyService.getContactsStories(viewerId));
    }

    @GetMapping("/{storyId}")
    @Operation(summary = "Get story details", description = "Returns details of a specific story if authorized to view it.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Story retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthenticated"),
        @ApiResponse(responseCode = "403", description = "Not authorized to view this story"),
        @ApiResponse(responseCode = "404", description = "Story not found or expired")
    })
    public ResponseEntity<StoryDto> getStory(Principal principal, @PathVariable UUID storyId) {
        UUID viewerId = UUID.fromString(principal.getName());
        return ResponseEntity.ok(storyService.getStory(viewerId, storyId));
    }

    @PostMapping("/{storyId}/view")
    @Operation(summary = "Mark story as viewed", description = "Records a view for this story. Idempotent (multiple calls don't create duplicate views).")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "View recorded successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthenticated"),
        @ApiResponse(responseCode = "403", description = "Not authorized to view this story"),
        @ApiResponse(responseCode = "404", description = "Story not found or expired")
    })
    public ResponseEntity<Void> markStoryAsViewed(Principal principal, @PathVariable UUID storyId) {
        UUID viewerId = UUID.fromString(principal.getName());
        storyService.markAsViewed(viewerId, storyId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{storyId}")
    @Operation(summary = "Delete my story", description = "Deletes one of your own stories before it expires.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Story deleted successfully"),
        @ApiResponse(responseCode = "400", description = "Cannot delete someone else's story"),
        @ApiResponse(responseCode = "401", description = "Unauthenticated"),
        @ApiResponse(responseCode = "404", description = "Story not found")
    })
    public ResponseEntity<Void> deleteStory(Principal principal, @PathVariable UUID storyId) {
        UUID authorId = UUID.fromString(principal.getName());
        storyService.deleteStory(authorId, storyId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{storyId}/viewers")
    @Operation(summary = "Get story viewers", description = "Returns the list of users who viewed your story (only accessible by story author).")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Viewers retrieved successfully"),
        @ApiResponse(responseCode = "400", description = "Can only view viewers of your own stories"),
        @ApiResponse(responseCode = "401", description = "Unauthenticated"),
        @ApiResponse(responseCode = "404", description = "Story not found")
    })
    public ResponseEntity<List<StoryViewDto>> getStoryViewers(Principal principal, @PathVariable UUID storyId) {
        UUID authorId = UUID.fromString(principal.getName());
        return ResponseEntity.ok(storyService.getStoryViewers(authorId, storyId));
    }
}