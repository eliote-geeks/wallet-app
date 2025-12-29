package com.socialwallet.stories.repository;

import com.socialwallet.stories.model.StoryView;
import com.socialwallet.stories.model.StoryViewId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StoryViewRepository extends JpaRepository<StoryView, StoryViewId> {

    // Check if viewer already viewed this story
    Optional<StoryView> findByStoryIdAndViewerId(UUID storyId, UUID viewerId);

    // Get all viewers for a story
    List<StoryView> findByStoryIdOrderByViewedAtDesc(UUID storyId);

    // Count views for a story
    long countByStoryId(UUID storyId);

    // Delete all views for a story (cascade)
    void deleteByStoryId(UUID storyId);

    // Check if a viewer has seen any stories from list
    List<StoryView> findByStoryIdInAndViewerId(List<UUID> storyIds, UUID viewerId);
}