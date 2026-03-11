package com.socialwallet.moderation.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.socialwallet.identity.model.AccountStatus;
import com.socialwallet.identity.model.UserAccount;
import com.socialwallet.identity.repository.UserAccountRepository;
import com.socialwallet.moderation.model.ModerationActionLogExecutionStatus;
import com.socialwallet.moderation.model.ModerationActionType;
import com.socialwallet.moderation.model.ModerationReport;
import com.socialwallet.moderation.model.ModerationTargetType;
import com.socialwallet.store.service.StoreService;
import com.socialwallet.stories.service.StoryService;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ModerationActionExecutor {
  private final StoryService storyService;
  private final StoreService storeService;
  private final UserAccountRepository userAccountRepository;

  public ExecutionResult execute(ModerationReport report, ModerationActionType actionType, UUID moderatorUserId) {
    if (actionType == null || actionType == ModerationActionType.NONE) {
      return ExecutionResult.skipped("No automated action requested");
    }

    try {
      return switch (actionType) {
        case WARNING -> ExecutionResult.skipped("Warning recorded (no backend mutation)");
        case CONTENT_HIDDEN -> hideContent(report, moderatorUserId);
        case CONTENT_REMOVED -> removeContent(report, moderatorUserId);
        case USER_TEMP_SUSPENDED -> setUserStatus(report, AccountStatus.SUSPENDED);
        case USER_BANNED -> setUserStatus(report, AccountStatus.BANNED);
        default -> ExecutionResult.failed("Unsupported moderation action");
      };
    } catch (Exception ex) {
      return ExecutionResult.failed("Action execution failed: " + safeMessage(ex));
    }
  }

  private ExecutionResult hideContent(ModerationReport report, UUID moderatorUserId) {
    ModerationTargetType targetType = report.getTargetType();
    return switch (targetType) {
      case STORY -> {
        UUID storyId = parseUuid(report.getTargetId(), "storyId");
        storyService.moderatorHideStory(storyId, moderatorUserId);
        yield ExecutionResult.success("Story hidden");
      }
      case STORE_PRODUCT -> {
        JsonNode result = storeService.moderateArchiveProduct(report.getTargetId(), moderatorUserId, report.getId(), report.getReasonCode(), "CONTENT_HIDDEN");
        yield ExecutionResult.success("Store product archived: " + extractProductId(result, report.getTargetId()));
      }
      default -> ExecutionResult.failed("CONTENT_HIDDEN not implemented for target " + targetType.name());
    };
  }

  private ExecutionResult removeContent(ModerationReport report, UUID moderatorUserId) {
    ModerationTargetType targetType = report.getTargetType();
    return switch (targetType) {
      case STORY -> {
        UUID storyId = parseUuid(report.getTargetId(), "storyId");
        storyService.moderatorDeleteStory(storyId, moderatorUserId);
        yield ExecutionResult.success("Story removed");
      }
      case STORE_PRODUCT -> {
        JsonNode result = storeService.moderateArchiveProduct(report.getTargetId(), moderatorUserId, report.getId(), report.getReasonCode(), "CONTENT_REMOVED");
        yield ExecutionResult.success("Store product archived (soft remove): " + extractProductId(result, report.getTargetId()));
      }
      default -> ExecutionResult.failed("CONTENT_REMOVED not implemented for target " + targetType.name());
    };
  }

  private ExecutionResult setUserStatus(ModerationReport report, AccountStatus targetStatus) {
    if (report.getTargetType() != ModerationTargetType.PROFILE) {
      return ExecutionResult.failed(targetStatus.name() + " requires targetType PROFILE");
    }
    UUID userId = parseUuid(report.getTargetId(), "profileUserId");
    UserAccount account = userAccountRepository.findById(userId).orElse(null);
    if (account == null) {
      return ExecutionResult.failed("Target user not found");
    }
    account.setStatus(targetStatus);
    userAccountRepository.save(account);
    return ExecutionResult.success("User status updated to " + targetStatus.name());
  }

  private UUID parseUuid(String value, String fieldName) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(fieldName + " is required");
    }
    return UUID.fromString(value.trim());
  }

  private String extractProductId(JsonNode result, String fallback) {
    if (result == null) {
      return fallback;
    }
    String id = result.path("product").path("id").asText(null);
    if (id != null && !id.isBlank()) {
      return id;
    }
    return fallback;
  }

  private String safeMessage(Exception ex) {
    String message = ex.getMessage();
    if (message == null || message.isBlank()) {
      return ex.getClass().getSimpleName().toUpperCase(Locale.ROOT);
    }
    return message;
  }

  public record ExecutionResult(ModerationActionLogExecutionStatus status, String details) {
    public static ExecutionResult success(String details) {
      return new ExecutionResult(ModerationActionLogExecutionStatus.SUCCESS, details);
    }

    public static ExecutionResult failed(String details) {
      return new ExecutionResult(ModerationActionLogExecutionStatus.FAILED, details);
    }

    public static ExecutionResult skipped(String details) {
      return new ExecutionResult(ModerationActionLogExecutionStatus.SKIPPED, details);
    }
  }
}
