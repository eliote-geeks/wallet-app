package com.socialwallet.admin.web;

import com.socialwallet.store.dto.StoreWebhookEventDetailDto;
import com.socialwallet.store.dto.StoreWebhookEventSummaryDto;
import com.socialwallet.store.model.StoreWebhookEvent;
import com.socialwallet.store.model.StoreWebhookEventStatus;
import com.socialwallet.store.service.MedusaWebhookService;
import com.socialwallet.store.service.StoreWebhookEventService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/webhooks/medusa")
@RequiredArgsConstructor
public class StoreWebhookAdminController {
  private final StoreWebhookEventService eventService;
  private final MedusaWebhookService webhookService;

  @GetMapping
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<Page<StoreWebhookEventSummaryDto>> list(
      @RequestParam(name = "status", required = false) StoreWebhookEventStatus status,
      Pageable pageable) {
    Page<StoreWebhookEventSummaryDto> page = eventService
      .list(status, pageable)
      .map(this::toSummary);
    return ResponseEntity.ok(page);
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<StoreWebhookEventDetailDto> detail(@PathVariable UUID id) {
    StoreWebhookEvent event = eventService.get(id);
    return ResponseEntity.ok(toDetail(event));
  }

  @PostMapping("/{id}/retry")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<StoreWebhookEventDetailDto> retry(@PathVariable UUID id) {
    StoreWebhookEvent event = eventService.get(id);
    StoreWebhookEvent processed = webhookService.retry(event);
    return ResponseEntity.ok(toDetail(processed));
  }

  private StoreWebhookEventSummaryDto toSummary(StoreWebhookEvent event) {
    StoreWebhookEventSummaryDto dto = new StoreWebhookEventSummaryDto();
    dto.setId(event.getId());
    dto.setProvider(event.getProvider());
    dto.setEventName(event.getEventName());
    dto.setStatus(event.getStatus());
    dto.setAttempts(event.getAttempts());
    dto.setLastError(event.getLastError());
    dto.setCreatedAt(event.getCreatedAt());
    dto.setProcessedAt(event.getProcessedAt());
    dto.setNextRetryAt(event.getNextRetryAt());
    return dto;
  }

  private StoreWebhookEventDetailDto toDetail(StoreWebhookEvent event) {
    StoreWebhookEventDetailDto dto = new StoreWebhookEventDetailDto();
    dto.setId(event.getId());
    dto.setProvider(event.getProvider());
    dto.setEventName(event.getEventName());
    dto.setStatus(event.getStatus());
    dto.setAttempts(event.getAttempts());
    dto.setLastError(event.getLastError());
    dto.setCreatedAt(event.getCreatedAt());
    dto.setProcessedAt(event.getProcessedAt());
    dto.setNextRetryAt(event.getNextRetryAt());
    dto.setPayload(event.getPayload());
    dto.setHeaders(event.getHeaders());
    dto.setSignature(event.getSignature());
    return dto;
  }
}
