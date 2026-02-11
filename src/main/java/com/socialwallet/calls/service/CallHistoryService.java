package com.socialwallet.calls.service;

import com.socialwallet.calls.CallException;
import com.socialwallet.calls.dto.CallHistoryItemResponse;
import com.socialwallet.calls.dto.CallSignalType;
import com.socialwallet.calls.model.CallHistory;
import com.socialwallet.calls.model.CallStatus;
import com.socialwallet.calls.repository.CallHistoryRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CallHistoryService {
  private final CallHistoryRepository callHistoryRepository;

  @Transactional
  public CallHistory createInvite(UUID callId,
                                  String roomName,
                                  boolean audioOnly,
                                  UUID initiatorUserId,
                                  UUID recipientUserId,
                                  Instant now) {
    CallHistory call = new CallHistory();
    call.setCallId(callId);
    call.setRoomName(roomName);
    call.setAudioOnly(audioOnly);
    call.setInitiatorUserId(initiatorUserId);
    call.setRecipientUserId(recipientUserId);
    call.setStatus(CallStatus.INVITED);
    call.setInitiatedAt(now);
    call.setLastSignalFromUserId(initiatorUserId);
    return callHistoryRepository.save(call);
  }

  @Transactional
  public CallHistory markInviteFailed(UUID callId, UUID failedBy, String reason, Instant now) {
    CallHistory call = callHistoryRepository.findByCallId(callId)
      .orElseThrow(() -> new CallException(HttpStatus.NOT_FOUND, "Call not found"));
    call.setStatus(CallStatus.FAILED);
    call.setFailureReason(trimReason(reason));
    call.setLastSignalFromUserId(failedBy);
    if (call.getEndedAt() == null) {
      call.setEndedAt(now);
    }
    if (call.getDurationSeconds() == null) {
      call.setDurationSeconds(0L);
    }
    return callHistoryRepository.save(call);
  }

  @Transactional
  public CallHistory applySignal(UUID callId, UUID senderUserId, CallSignalType action, Instant now) {
    CallHistory call = callHistoryRepository.findByCallId(callId)
      .orElseThrow(() -> new CallException(HttpStatus.NOT_FOUND, "Call not found"));
    validateParticipant(call, senderUserId);
    call.setLastSignalFromUserId(senderUserId);
    switch (action) {
      case ACCEPT -> {
        call.setStatus(CallStatus.ACCEPTED);
        if (call.getAcceptedAt() == null) {
          call.setAcceptedAt(now);
        }
      }
      case DECLINE -> endWithStatus(call, CallStatus.DECLINED, now);
      case BUSY -> endWithStatus(call, CallStatus.BUSY, now);
      case CANCEL -> endWithStatus(call, CallStatus.CANCELED, now);
      case END -> endWithStatus(call, CallStatus.ENDED, now);
      case INVITE -> call.setStatus(CallStatus.INVITED);
      default -> throw new CallException(HttpStatus.BAD_REQUEST, "Unsupported signal action");
    }
    return callHistoryRepository.save(call);
  }

  @Transactional(readOnly = true)
  public List<CallHistoryItemResponse> findForUser(UUID userId) {
    return callHistoryRepository
      .findTop100ByInitiatorUserIdOrRecipientUserIdOrderByInitiatedAtDesc(userId, userId)
      .stream()
      .map(this::toResponse)
      .toList();
  }

  @Transactional(readOnly = true)
  public CallHistory getRequired(UUID callId) {
    return callHistoryRepository.findByCallId(callId)
      .orElseThrow(() -> new CallException(HttpStatus.NOT_FOUND, "Call not found"));
  }

  private void endWithStatus(CallHistory call, CallStatus status, Instant now) {
    call.setStatus(status);
    if (call.getEndedAt() == null) {
      call.setEndedAt(now);
    }
    Instant start = call.getAcceptedAt() != null ? call.getAcceptedAt() : call.getInitiatedAt();
    long seconds = start == null ? 0L : Math.max(0L, Duration.between(start, call.getEndedAt()).getSeconds());
    call.setDurationSeconds(seconds);
  }

  private void validateParticipant(CallHistory call, UUID senderUserId) {
    if (!senderUserId.equals(call.getInitiatorUserId()) && !senderUserId.equals(call.getRecipientUserId())) {
      throw new CallException(HttpStatus.FORBIDDEN, "User is not part of this call");
    }
  }

  private String trimReason(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    if (trimmed.isEmpty()) {
      return null;
    }
    return trimmed.length() > 500 ? trimmed.substring(0, 500) : trimmed;
  }

  private CallHistoryItemResponse toResponse(CallHistory call) {
    CallHistoryItemResponse response = new CallHistoryItemResponse();
    response.setCallId(call.getCallId());
    response.setRoomName(call.getRoomName());
    response.setAudioOnly(call.isAudioOnly());
    response.setInitiatorUserId(call.getInitiatorUserId());
    response.setRecipientUserId(call.getRecipientUserId());
    response.setStatus(call.getStatus().name());
    response.setInitiatedAt(call.getInitiatedAt());
    response.setAcceptedAt(call.getAcceptedAt());
    response.setEndedAt(call.getEndedAt());
    response.setDurationSeconds(call.getDurationSeconds());
    response.setLastSignalFromUserId(call.getLastSignalFromUserId());
    return response;
  }
}
