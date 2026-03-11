package com.socialwallet.chat.service;

import com.socialwallet.chat.dto.MessagingTokenResponse;
import com.socialwallet.identity.model.AccountStatus;
import com.socialwallet.identity.model.UserAccount;
import com.socialwallet.identity.repository.UserAccountRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MessagingService {
  private final UserAccountRepository userAccountRepository;
  private final OpenImService openImService;
  private final OpenImUserIdService openImUserIdService;

  public MessagingTokenResponse issueTokens(Jwt jwt) {
    UUID userId = UUID.fromString(jwt.getSubject());
    UserAccount account = userAccountRepository.findById(userId)
      .orElseGet(() -> createFromJwt(userId, jwt));
    account = openImUserIdService.assignIfMissing(account);
    return openImService.issueTokens(account);
  }

  private UserAccount createFromJwt(UUID userId, Jwt jwt) {
    UserAccount account = new UserAccount();
    account.setId(userId);
    account.setEmail(jwt.getClaimAsString("email"));
    account.setPhoneNumber(jwt.getClaimAsString("phone_number"));
    account.setOpenimUserId(openImUserIdService.nextId());
    account.setStatus(AccountStatus.ACTIVE);
    account.setVerifiedAt(Instant.now());
    return userAccountRepository.save(account);
  }
}
