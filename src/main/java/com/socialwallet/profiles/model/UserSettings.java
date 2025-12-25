package com.socialwallet.profiles.model;

import jakarta.persistence.*;
import lombok.Data;
import java.util.UUID;

@Entity
@Table(name = "user_settings")
@Data
public class UserSettings {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "user_id", unique = true, nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PrivacyLevel profilePhoto = PrivacyLevel.EVERYONE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PrivacyLevel about = PrivacyLevel.EVERYONE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PrivacyLevel lastSeenAndOnline = PrivacyLevel.MY_CONTACTS;

    @Column(nullable = false)
    private boolean readReceipts = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PrivacyLevel defaultStoryVisibility = PrivacyLevel.MY_CONTACTS;
}