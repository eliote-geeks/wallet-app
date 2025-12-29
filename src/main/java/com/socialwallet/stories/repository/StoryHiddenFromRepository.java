package com.socialwallet.stories.repository;

import com.socialwallet.stories.model.StoryHiddenFrom;
import com.socialwallet.stories.model.StoryHiddenFromId;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface StoryHiddenFromRepository extends JpaRepository<StoryHiddenFrom, StoryHiddenFromId> {
    List<StoryHiddenFrom> findByStoryId(UUID storyId);
    void deleteByStoryId(UUID storyId);
}