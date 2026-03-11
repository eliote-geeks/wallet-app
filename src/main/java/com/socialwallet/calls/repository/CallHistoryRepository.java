package com.socialwallet.calls.repository;

import com.socialwallet.calls.model.CallHistory;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CallHistoryRepository extends JpaRepository<CallHistory, UUID> {
  Optional<CallHistory> findByCallId(UUID callId);

  List<CallHistory> findTop100ByInitiatorUserIdOrRecipientUserIdOrderByInitiatedAtDesc(UUID initiatorUserId, UUID recipientUserId);
}
