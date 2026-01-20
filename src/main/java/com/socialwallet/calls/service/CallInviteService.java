package com.socialwallet.calls.service;

import com.socialwallet.calls.CallException;
import com.socialwallet.calls.dto.CallInviteRequest;
import com.socialwallet.calls.dto.CallInviteResponse;
import com.socialwallet.calls.dto.CallSignalRequest;
import com.socialwallet.calls.dto.CallSignalResponse;
import com.socialwallet.calls.dto.CallSignalType;
import com.socialwallet.chat.service.OpenImUserIdService;
import com.socialwallet.identity.model.UserAccount;
import com.socialwallet.identity.repository.UserAccountRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CallInviteService {
  private static final String TYPE_CALL_SIGNAL = "call_signal";

  private final UserAccountRepository userAccountRepository;
  private final OpenImUserIdService openImUserIdService;

  public CallInviteResponse buildInvite(Jwt jwt, CallInviteRequest request) {
    UUID callerId = UUID.fromString(jwt.getSubject());
    UserAccount caller = ensureAccount(callerId);
    UserAccount recipient = ensureAccount(request.getRecipientUserId());

    String room = resolveRoom(request.getRoomName());
    UUID callId = UUID.randomUUID();
    Instant now = Instant.now();

    CallInviteResponse response = new CallInviteResponse();
    response.setCallId(callId);
    response.setRoomName(room);
    response.setAudioOnly(request.isAudioOnly());
    response.setCallerUserId(caller.getId());
    response.setCallerOpenimUserId(caller.getOpenimUserId());
    response.setRecipientUserId(recipient.getId());
    response.setRecipientOpenimUserId(recipient.getOpenimUserId());
    response.setCreatedAt(now);
    response.setOpenimPayload(buildPayload(CallSignalType.INVITE, callId, room, caller, recipient, request.isAudioOnly(), now));
    return response;
  }

  public CallSignalResponse buildResponse(Jwt jwt, CallSignalRequest request) {
    UUID senderId = UUID.fromString(jwt.getSubject());
    UserAccount sender = ensureAccount(senderId);
    UserAccount target = ensureAccount(request.getTargetUserId());
    Instant now = Instant.now();

    CallSignalResponse response = new CallSignalResponse();
    response.setCallId(request.getCallId());
    response.setRoomName(request.getRoomName());
    response.setAction(request.getAction());
    response.setSenderUserId(sender.getId());
    response.setSenderOpenimUserId(sender.getOpenimUserId());
    response.setTargetUserId(target.getId());
    response.setTargetOpenimUserId(target.getOpenimUserId());
    response.setCreatedAt(now);
    response.setOpenimPayload(buildPayload(request.getAction(), request.getCallId(), request.getRoomName(), sender, target, null, now));
    return response;
  }

  private UserAccount ensureAccount(UUID userId) {
    UserAccount account = userAccountRepository.findById(userId)
      .orElseThrow(() -> new CallException(HttpStatus.NOT_FOUND, "User not found"));
    return openImUserIdService.assignIfMissing(account);
  }

  private String resolveRoom(String roomName) {
    if (roomName == null || roomName.isBlank()) {
      return "call-" + UUID.randomUUID();
    }
    return roomName.trim();
  }

  private Map<String, Object> buildPayload(CallSignalType action,
                                           UUID callId,
                                           String roomName,
                                           UserAccount sender,
                                           UserAccount target,
                                           Boolean audioOnly,
                                           Instant now) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("type", TYPE_CALL_SIGNAL);
    payload.put("action", action.name());
    payload.put("callId", callId.toString());
    payload.put("roomName", roomName);
    if (audioOnly != null) {
      payload.put("audioOnly", audioOnly);
    }
    payload.put("timestamp", now.toString());
    payload.put("fromUserId", sender.getId().toString());
    payload.put("fromOpenimUserId", sender.getOpenimUserId());
    payload.put("toUserId", target.getId().toString());
    payload.put("toOpenimUserId", target.getOpenimUserId());
    return payload;
  }
}
