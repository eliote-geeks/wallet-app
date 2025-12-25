package com.socialwallet.profiles.repository;

import com.socialwallet.profiles.model.Contact;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContactRepository extends JpaRepository<Contact, UUID> {
    Optional<Contact> findByUserIdAndContactId(UUID userId, UUID contactId);
    List<Contact> findAllByUserId(UUID userId);
    void deleteByUserIdAndContactId(UUID userId, UUID contactId);
}