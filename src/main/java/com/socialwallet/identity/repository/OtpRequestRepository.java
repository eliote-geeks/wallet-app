package com.socialwallet.identity.repository;

import com.socialwallet.identity.model.OtpRequest;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OtpRequestRepository extends JpaRepository<OtpRequest, UUID> {
}
