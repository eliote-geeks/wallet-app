package com.socialwallet.chat.service;

import com.socialwallet.identity.model.UserAccount;
import com.socialwallet.identity.repository.UserAccountRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OpenImUserIdService {
  private final UserAccountRepository userAccountRepository;

  @PersistenceContext
  private EntityManager entityManager;

  public long nextId() {
    Number nextVal = (Number) entityManager
      .createNativeQuery("select nextval('openim_user_id_seq')")
      .getSingleResult();
    return nextVal.longValue();
  }

  @Transactional
  public UserAccount assignIfMissing(UserAccount account) {
    if (account.getOpenimUserId() != null) {
      return account;
    }
    account.setOpenimUserId(nextId());
    return userAccountRepository.save(account);
  }
}
