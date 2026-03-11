package com.socialwallet.moderation.service;

import com.socialwallet.identity.repository.UserAccountRepository;
import com.socialwallet.moderation.ModerationException;
import com.socialwallet.moderation.dto.ModerationReportDecisionRequest;
import com.socialwallet.moderation.model.ModerationActionLogExecutionStatus;
import com.socialwallet.moderation.model.ModerationActionType;
import com.socialwallet.moderation.model.ModerationReport;
import com.socialwallet.moderation.model.ModerationReportStatus;
import com.socialwallet.moderation.model.ModerationTargetType;
import com.socialwallet.moderation.repository.ModerationReportRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ModerationServiceTest {
  @Mock
  private ModerationReportRepository moderationReportRepository;

  @Mock
  private UserAccountRepository userAccountRepository;

  @Mock
  private ModerationActionExecutor moderationActionExecutor;

  @Mock
  private ModerationActionLogService moderationActionLogService;

  private ModerationService moderationService;

  @BeforeEach
  void setUp() {
    moderationService = new ModerationService(
      moderationReportRepository,
      userAccountRepository,
      moderationActionExecutor,
      moderationActionLogService
    );
  }

  @Test
  void updateStatus_rejects_action_when_status_not_resolved() {
    UUID moderatorId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    UUID reportId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    ModerationReport report = sampleReport(reportId);
    when(userAccountRepository.existsById(moderatorId)).thenReturn(true);
    when(moderationReportRepository.findById(reportId)).thenReturn(Optional.of(report));

    ModerationReportDecisionRequest request = new ModerationReportDecisionRequest();
    request.setStatus(ModerationReportStatus.IN_REVIEW);
    request.setActionType(ModerationActionType.CONTENT_REMOVED);

    ModerationException ex = assertThrows(ModerationException.class,
      () -> moderationService.updateStatus(moderatorId, reportId, request));

    assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    verify(moderationActionLogService, never()).record(any(), any(), any(), any(), any(), any(), any());
    verify(moderationReportRepository, never()).save(any());
  }

  @Test
  void updateStatus_aborts_when_execution_failed() {
    UUID moderatorId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    UUID reportId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    ModerationReport report = sampleReport(reportId);
    when(userAccountRepository.existsById(moderatorId)).thenReturn(true);
    when(moderationReportRepository.findById(reportId)).thenReturn(Optional.of(report));
    when(moderationActionExecutor.execute(eq(report), eq(ModerationActionType.CONTENT_HIDDEN), eq(moderatorId)))
      .thenReturn(ModerationActionExecutor.ExecutionResult.failed("Chat target not supported"));

    ModerationReportDecisionRequest request = new ModerationReportDecisionRequest();
    request.setStatus(ModerationReportStatus.RESOLVED);
    request.setActionType(ModerationActionType.CONTENT_HIDDEN);

    ModerationException ex = assertThrows(ModerationException.class,
      () -> moderationService.updateStatus(moderatorId, reportId, request));

    assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatus());
    verify(moderationActionLogService).record(
      eq(reportId),
      eq(moderatorId),
      eq(ModerationTargetType.CHAT_MESSAGE),
      eq("msg_123"),
      eq(ModerationActionType.CONTENT_HIDDEN),
      eq(ModerationActionLogExecutionStatus.FAILED),
      eq("Chat target not supported")
    );
    verify(moderationReportRepository, never()).save(any());
  }

  @Test
  void updateStatus_saves_when_execution_successful() {
    UUID moderatorId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    UUID reportId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    ModerationReport report = sampleReport(reportId);
    when(userAccountRepository.existsById(moderatorId)).thenReturn(true);
    when(moderationReportRepository.findById(reportId)).thenReturn(Optional.of(report));
    when(moderationActionExecutor.execute(eq(report), eq(ModerationActionType.CONTENT_REMOVED), eq(moderatorId)))
      .thenReturn(ModerationActionExecutor.ExecutionResult.success("Story removed"));
    when(moderationReportRepository.save(any(ModerationReport.class))).thenAnswer(invocation -> invocation.getArgument(0));

    ModerationReportDecisionRequest request = new ModerationReportDecisionRequest();
    request.setStatus(ModerationReportStatus.RESOLVED);
    request.setActionType(ModerationActionType.CONTENT_REMOVED);
    request.setResolutionNote("validated");

    ModerationReport saved = moderationService.updateStatus(moderatorId, reportId, request);

    assertEquals(ModerationReportStatus.RESOLVED, saved.getStatus());
    assertEquals(ModerationActionType.CONTENT_REMOVED, saved.getActionType());
    assertEquals(moderatorId, saved.getAssignedModeratorUserId());
    verify(moderationActionLogService).record(
      eq(reportId),
      eq(moderatorId),
      eq(ModerationTargetType.CHAT_MESSAGE),
      eq("msg_123"),
      eq(ModerationActionType.CONTENT_REMOVED),
      eq(ModerationActionLogExecutionStatus.SUCCESS),
      eq("Story removed")
    );
    verify(moderationReportRepository).save(report);
  }

  private ModerationReport sampleReport(UUID reportId) {
    ModerationReport report = new ModerationReport();
    report.setId(reportId);
    report.setReporterUserId(UUID.fromString("22222222-2222-2222-2222-222222222222"));
    report.setTargetType(ModerationTargetType.CHAT_MESSAGE);
    report.setTargetId("msg_123");
    report.setReasonCode("SPAM");
    report.setStatus(ModerationReportStatus.OPEN);
    report.setActionType(ModerationActionType.NONE);
    return report;
  }
}
