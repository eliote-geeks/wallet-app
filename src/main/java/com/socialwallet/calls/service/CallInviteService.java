package com.socialwallet.calls.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.socialwallet.calls.CallException;
import com.socialwallet.calls.dto.CallInviteRequest;
import com.socialwallet.calls.dto.CallInviteResponse;
import com.socialwallet.calls.dto.CallSignalRequest;
import com.socialwallet.calls.dto.CallSignalResponse;
import com.socialwallet.calls.dto.CallSignalType;
import com.socialwallet.calls.model.CallHistory;
import com.socialwallet.chat.OpenImException;
import com.socialwallet.chat.service.OpenImClient;
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
  private static final int CUSTOM_MESSAGE_CONTENT_TYPE = 110;
  private static final int SINGLE_CHAT_SESSION_TYPE = 1;

  private final UserAccountRepository userAccountRepository;
  private final OpenImUserIdService openImUserIdService;
  private final OpenImClient openImClient;
  private final CallHistoryService callHistoryService;
  private final ObjectMapper objectMapper;

  public CallInviteResponse sendInvite(Jwt jwt, CallInviteRequest request) {
    UUID callerId = UUID.fromString(jwt.getSubject());
    UserAccount caller = ensureAccount(callerId);
    UserAccount recipient = ensureAccount(request.getRecipientUserId());

    String room = resolveRoom(request.getRoomName());
    UUID callId = UUID.randomUUID();
    Instant now = Instant.now();

    Map<String, Object> payload = buildPayload(CallSignalType.INVITE, callId, room, caller, recipient, request.isAudioOnly(), now);
    CallHistory callHistory = callHistoryService.createInvite(callId, room, request.isAudioOnly(), caller.getId(), recipient.getId(), now);
    try {
      sendSignal(caller, recipient, payload, request.isAudioOnly());
    } catch (OpenImException ex) {
      callHistoryService.markInviteFailed(callId, caller.getId(), ex.getMessage(), now);
      throw new CallException(HttpStatus.BAD_GATEWAY, "OpenIM invite dispatch failed: " + ex.getMessage());
    }

    return toInviteResponse(caller, recipient, payload, callHistory, true);
  }

  public CallSignalResponse sendResponse(Jwt jwt, CallSignalRequest request) {
    UUID senderId = UUID.fromString(jwt.getSubject());
    UserAccount sender = ensureAccount(senderId);
    UserAccount target = ensureAccount(request.getTargetUserId());
    Instant now = Instant.now();
    CallHistory call = callHistoryService.getRequired(request.getCallId());
    validateTarget(call, sender.getId(), target.getId());
    if (request.getRoomName() != null && !request.getRoomName().trim().equals(call.getRoomName())) {
      throw new CallException(HttpStatus.BAD_REQUEST, "Room name mismatch for call");
    }

    Map<String, Object> payload = buildPayload(
      request.getAction(),
      request.getCallId(),
      call.getRoomName(),
      sender,
      target,
      null,
      now
    );

    try {
      sendSignal(sender, target, payload, null);
    } catch (OpenImException ex) {
      throw new CallException(HttpStatus.BAD_GATEWAY, "OpenIM response dispatch failed: " + ex.getMessage());
    }

    CallHistory updated = callHistoryService.applySignal(request.getCallId(), sender.getId(), request.getAction(), now);
    return toSignalResponse(request, sender, target, payload, updated, true, call.getRoomName());
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

  private void sendSignal(UserAccount sender, UserAccount target, Map<String, Object> payload, Boolean audioOnly) {
    OpenImClient.SendMessageRequest request = new OpenImClient.SendMessageRequest();
    request.setSendId(String.valueOf(sender.getOpenimUserId()));
    request.setRecvId(String.valueOf(target.getOpenimUserId()));
    request.setGroupId("");
    request.setContent(buildCustomElem(payload));
    request.setContentType(CUSTOM_MESSAGE_CONTENT_TYPE);
    request.setSessionType(SINGLE_CHAT_SESSION_TYPE);
    request.setOnlineOnly(false);
    request.setNotOfflinePush(false);
    request.setOfflinePushInfo(buildPushInfo(audioOnly));
    openImClient.sendMessage(request);
  }

  private OpenImClient.OfflinePushInfo buildPushInfo(Boolean audioOnly) {
    OpenImClient.OfflinePushInfo pushInfo = new OpenImClient.OfflinePushInfo();
    pushInfo.setTitle("Kobo call");
    pushInfo.setDesc(Boolean.TRUE.equals(audioOnly) ? "Incoming voice call" : "Incoming video call");
    pushInfo.setEx("{\"type\":\"call_signal\"}");
    pushInfo.setIOSBadgeCount(true);
    pushInfo.setIOSPushSound("default");
    return pushInfo;
  }

  private String serializePayload(Map<String, Object> payload) {
    try {
      return objectMapper.writeValueAsString(payload);
    } catch (JsonProcessingException ex) {
      throw new CallException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to serialize call signal");
    }
  }

  private OpenImClient.CustomElem buildCustomElem(Map<String, Object> payload) {
    OpenImClient.CustomElem elem = new OpenImClient.CustomElem();
    elem.setData(serializePayload(payload));
    elem.setDescription(TYPE_CALL_SIGNAL);
    elem.setExtension("{}");
    return elem;
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

  private void validateTarget(CallHistory call, UUID senderId, UUID targetId) {
    boolean senderParticipant = senderId.equals(call.getInitiatorUserId()) || senderId.equals(call.getRecipientUserId());
    boolean targetParticipant = targetId.equals(call.getInitiatorUserId()) || targetId.equals(call.getRecipientUserId());
    if (!senderParticipant || !targetParticipant) {
      throw new CallException(HttpStatus.FORBIDDEN, "Invalid call participants");
    }
    if (senderId.equals(targetId)) {
      throw new CallException(HttpStatus.BAD_REQUEST, "Target user must be different from sender");
    }
  }

  private CallInviteResponse toInviteResponse(UserAccount caller,
                                              UserAccount recipient,
                                              Map<String, Object> payload,
                                              CallHistory callHistory,
                                              boolean sent) {
    CallInviteResponse response = new CallInviteResponse();
    response.setCallId(callHistory.getCallId());
    response.setRoomName(callHistory.getRoomName());
    response.setAudioOnly(callHistory.isAudioOnly());
    response.setCallerUserId(caller.getId());
    response.setCallerOpenimUserId(caller.getOpenimUserId());
    response.setRecipientUserId(recipient.getId());
    response.setRecipientOpenimUserId(recipient.getOpenimUserId());
    response.setStatus(callHistory.getStatus().name());
    response.setSent(sent);
    response.setCreatedAt(callHistory.getInitiatedAt());
    response.setOpenimPayload(payload);
    return response;
  }

  private CallSignalResponse toSignalResponse(CallSignalRequest request,
                                              UserAccount sender,
                                              UserAccount target,
                                              Map<String, Object> payload,
                                              CallHistory updated,
                                              boolean sent,
                                              String roomName) {
    CallSignalResponse response = new CallSignalResponse();
    response.setCallId(request.getCallId());
    response.setRoomName(roomName);
    response.setAction(request.getAction());
    response.setSenderUserId(sender.getId());
    response.setSenderOpenimUserId(sender.getOpenimUserId());
    response.setTargetUserId(target.getId());
    response.setTargetOpenimUserId(target.getOpenimUserId());
    response.setStatus(updated.getStatus().name());
    response.setDurationSeconds(updated.getDurationSeconds());
    response.setSent(sent);
    response.setCreatedAt(Instant.now());
    response.setOpenimPayload(payload);
    return response;
  }
}
