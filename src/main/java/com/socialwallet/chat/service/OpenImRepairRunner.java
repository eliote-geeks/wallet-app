package com.socialwallet.chat.service;

import com.socialwallet.chat.OpenImException;
import com.socialwallet.identity.model.UserAccount;
import com.socialwallet.identity.repository.UserAccountRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.openim", name = "repair-on-startup", havingValue = "true")
@RequiredArgsConstructor
public class OpenImRepairRunner implements ApplicationRunner {
  private final UserAccountRepository userAccountRepository;
  private final OpenImService openImService;

  @Override
  public void run(ApplicationArguments args) {
    if (!openImService.isEnabled()) {
      log.info("OpenIM repair skipped (integration disabled)");
      return;
    }
    List<UserAccount> accounts = userAccountRepository.findAll();
    int repaired = 0;
    int failed = 0;
    for (UserAccount account : accounts) {
      if (account.getOpenimUserId() == null) {
        continue;
      }
      try {
        openImService.repairUser(account);
        repaired++;
      } catch (OpenImException ex) {
        failed++;
        log.warn("OpenIM repair failed for user {}: {}", account.getId(), ex.getMessage());
      }
    }
    log.info("OpenIM repair finished: repaired={}, failed={}, total={}", repaired, failed, accounts.size());
  }
}
