package com.socialwallet.admin.web;

import com.socialwallet.admin.dto.OpenImRepairResponse;
import com.socialwallet.chat.OpenImException;
import com.socialwallet.chat.service.OpenImService;
import com.socialwallet.chat.service.OpenImUserIdService;
import com.socialwallet.identity.model.UserAccount;
import com.socialwallet.identity.repository.UserAccountRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Slf4j
@RestController
@RequestMapping("/api/admin/openim")
@RequiredArgsConstructor
public class OpenImAdminController {
  private final UserAccountRepository userAccountRepository;
  private final OpenImService openImService;
  private final OpenImUserIdService openImUserIdService;

  @PostMapping("/repair")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<OpenImRepairResponse> repair(@RequestParam(name = "userId", required = false) UUID userId) {
    List<UserAccount> accounts;
    if (userId == null) {
      accounts = userAccountRepository.findAll();
    } else {
      UserAccount account = userAccountRepository.findById(userId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
      accounts = List.of(account);
    }

    int repaired = 0;
    int failed = 0;
    for (UserAccount account : accounts) {
      try {
        UserAccount target = openImUserIdService.assignIfMissing(account);
        openImService.repairUser(target);
        repaired++;
      } catch (OpenImException ex) {
        failed++;
        log.warn("OpenIM repair failed for user {}: {}", account.getId(), ex.getMessage());
      }
    }

    OpenImRepairResponse response = new OpenImRepairResponse();
    response.setTotal(accounts.size());
    response.setRepaired(repaired);
    response.setFailed(failed);
    return ResponseEntity.ok(response);
  }
}
