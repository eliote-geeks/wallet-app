package com.socialwallet.stories.repository;

import com.socialwallet.stories.model.Story;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StoryRepository extends JpaRepository<Story, UUID> {

    // Find all active stories by author
    List<Story> findByAuthorIdAndExpiresAtAfterOrderByCreatedAtDesc(UUID authorId, LocalDateTime now);

    // Find a specific active story
    Optional<Story> findByIdAndExpiresAtAfter(UUID storyId, LocalDateTime now);

    // Find all active stories from a list of authors (contacts)
    @Query("SELECT s FROM Story s WHERE s.authorId IN :authorIds AND s.expiresAt > :now ORDER BY s.createdAt DESC")
    List<Story> findActiveStoriesByAuthors(@Param("authorIds") List<UUID> authorIds, @Param("now") LocalDateTime now);

    // Delete expired stories
    void deleteByExpiresAtBefore(LocalDateTime now);

    // Count active stories by author
    long countByAuthorIdAndExpiresAtAfter(UUID authorId, LocalDateTime now);
}