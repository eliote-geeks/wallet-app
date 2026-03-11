package com.socialwallet.moderation.web;

import com.socialwallet.config.SecurityConfig;
import com.socialwallet.moderation.dto.ModerationReportCreateRequest;
import com.socialwallet.moderation.dto.ModerationReportDecisionRequest;
import com.socialwallet.moderation.model.ModerationActionType;
import com.socialwallet.moderation.model.ModerationActionLog;
import com.socialwallet.moderation.model.ModerationActionLogExecutionStatus;
import com.socialwallet.moderation.model.ModerationReport;
import com.socialwallet.moderation.model.ModerationReportStatus;
import com.socialwallet.moderation.model.ModerationTargetType;
import com.socialwallet.moderation.service.ModerationService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ModerationController.class)
@Import({SecurityConfig.class, ModerationExceptionHandler.class})
class ModerationControllerRbacIntegrationTest {
  private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID MODERATOR_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final UUID ADMIN_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
  private static final UUID REPORT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

  @Autowired
  private MockMvc mockMvc;

  @MockBean
  private ModerationService moderationService;

  @MockBean
  private JwtDecoder jwtDecoder;

  @Test
  void createReport_allowed_forPlatformUser() throws Exception {
    ModerationReport row = sampleReport();
    when(moderationService.createReport(eq(USER_ID), any(ModerationReportCreateRequest.class))).thenReturn(row);

    mockMvc.perform(post("/api/moderation/reports")
        .with(userJwt())
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
          {
            "targetType":"CHAT_MESSAGE",
            "targetId":"msg_123",
            "reasonCode":"SPAM",
            "description":"Spam links"
          }
          """))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.id").value(REPORT_ID.toString()))
      .andExpect(jsonPath("$.status").value("OPEN"));

    verify(moderationService).createReport(eq(USER_ID), any(ModerationReportCreateRequest.class));
  }

  @Test
  void queue_forbidden_forSimpleUser() throws Exception {
    mockMvc.perform(get("/api/moderation/reports/queue")
        .with(userJwt()))
      .andExpect(status().isForbidden());

    verify(moderationService, never()).listQueue(isNull(), isNull(), isNull(), isNull(), any());
  }

  @Test
  void queue_allowed_forModerator() throws Exception {
    when(moderationService.listQueue(isNull(), isNull(), isNull(), isNull(), any()))
      .thenReturn(new PageImpl<>(List.of(sampleReport())));

    mockMvc.perform(get("/api/moderation/reports/queue")
        .with(moderatorJwt()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.content[0].id").value(REPORT_ID.toString()))
      .andExpect(jsonPath("$.content[0].targetType").value("CHAT_MESSAGE"));

    verify(moderationService).listQueue(isNull(), isNull(), isNull(), isNull(), any());
  }

  @Test
  void updateStatus_allowed_forAdmin() throws Exception {
    ModerationReport updated = sampleReport();
    updated.setStatus(ModerationReportStatus.RESOLVED);
    updated.setActionType(ModerationActionType.CONTENT_REMOVED);
    updated.setAssignedModeratorUserId(ADMIN_ID);
    updated.setResolvedAt(Instant.parse("2026-02-11T18:00:00Z"));

    when(moderationService.updateStatus(eq(ADMIN_ID), eq(REPORT_ID), any(ModerationReportDecisionRequest.class)))
      .thenReturn(updated);

    mockMvc.perform(patch("/api/moderation/reports/{reportId}/status", REPORT_ID)
        .with(adminJwt())
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
          {
            "status":"RESOLVED",
            "actionType":"CONTENT_REMOVED",
            "resolutionNote":"Content deleted after verification"
          }
          """))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.status").value("RESOLVED"))
      .andExpect(jsonPath("$.actionType").value("CONTENT_REMOVED"))
      .andExpect(jsonPath("$.assignedModeratorUserId").value(ADMIN_ID.toString()));

    verify(moderationService).updateStatus(eq(ADMIN_ID), eq(REPORT_ID), any(ModerationReportDecisionRequest.class));
  }

  @Test
  void listActions_forbidden_forSimpleUser() throws Exception {
    mockMvc.perform(get("/api/moderation/reports/{reportId}/actions", REPORT_ID)
        .with(userJwt()))
      .andExpect(status().isForbidden());

    verify(moderationService, never()).listActionLogs(eq(REPORT_ID));
  }

  @Test
  void listActions_allowed_forModerator() throws Exception {
    ModerationActionLog logRow = new ModerationActionLog();
    logRow.setId(UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"));
    logRow.setReportId(REPORT_ID);
    logRow.setModeratorUserId(MODERATOR_ID);
    logRow.setTargetType(ModerationTargetType.CHAT_MESSAGE);
    logRow.setTargetId("msg_123");
    logRow.setActionType(ModerationActionType.CONTENT_HIDDEN);
    logRow.setExecutionStatus(ModerationActionLogExecutionStatus.FAILED);
    logRow.setDetails("CONTENT_HIDDEN not implemented for target CHAT_MESSAGE");
    logRow.setCreatedAt(Instant.parse("2026-02-11T18:10:00Z"));

    when(moderationService.listActionLogs(REPORT_ID)).thenReturn(List.of(logRow));

    mockMvc.perform(get("/api/moderation/reports/{reportId}/actions", REPORT_ID)
        .with(moderatorJwt()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$[0].reportId").value(REPORT_ID.toString()))
      .andExpect(jsonPath("$[0].executionStatus").value("FAILED"));

    verify(moderationService).listActionLogs(REPORT_ID);
  }

  private ModerationReport sampleReport() {
    ModerationReport row = new ModerationReport();
    row.setId(REPORT_ID);
    row.setReporterUserId(USER_ID);
    row.setTargetType(ModerationTargetType.CHAT_MESSAGE);
    row.setTargetId("msg_123");
    row.setReasonCode("SPAM");
    row.setDescription("Spam links");
    row.setStatus(ModerationReportStatus.OPEN);
    row.setActionType(ModerationActionType.NONE);
    row.setCreatedAt(Instant.parse("2026-02-11T17:50:00Z"));
    row.setUpdatedAt(Instant.parse("2026-02-11T17:50:00Z"));
    return row;
  }

  private JwtRequestPostProcessor userJwt() {
    return jwt()
      .jwt(token -> token
        .subject(USER_ID.toString())
        .claim("realm_access", Map.of("roles", List.of("USER"))))
      .authorities(new SimpleGrantedAuthority("ROLE_USER"));
  }

  private JwtRequestPostProcessor moderatorJwt() {
    return jwt()
      .jwt(token -> token
        .subject(MODERATOR_ID.toString())
        .claim("realm_access", Map.of("roles", List.of("MODERATOR"))))
      .authorities(new SimpleGrantedAuthority("ROLE_MODERATOR"));
  }

  private JwtRequestPostProcessor adminJwt() {
    return jwt()
      .jwt(token -> token
        .subject(ADMIN_ID.toString())
        .claim("realm_access", Map.of("roles", List.of("ADMIN"))))
      .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
  }
}
