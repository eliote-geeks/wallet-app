package com.socialwallet.profiles.repository;

import com.socialwallet.profiles.model.Block;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BlockRepository extends JpaRepository<Block, UUID> {
    Optional<Block> findByBlockerIdAndBlockedId(UUID blockerId, UUID blockedId);
    List<Block> findAllByBlockerId(UUID blockerId);
}