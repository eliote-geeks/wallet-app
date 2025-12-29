package com.socialwallet.stories.repository;

import com.socialwallet.stories.model.StorySharedWith;
import com.socialwallet.stories.model.StorySharedWithId;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface StorySharedWithRepository extends JpaRepository<StorySharedWith, StorySharedWithId> {
    List<StorySharedWith> findByStoryId(UUID storyId);
    void deleteByStoryId(UUID storyId);
}