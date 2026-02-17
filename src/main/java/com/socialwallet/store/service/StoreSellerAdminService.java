package com.socialwallet.store.service;

import com.socialwallet.admin.service.RoleAdminService;
import com.socialwallet.store.StoreException;
import com.socialwallet.store.model.StoreSellerApplication;
import com.socialwallet.store.model.StoreSellerApplicationStatus;
import com.socialwallet.store.repository.StoreSellerApplicationRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class StoreSellerAdminService {
  private final StoreSellerApplicationRepository applicationRepository;
  private final StoreSellerService sellerService;
  private final RoleAdminService roleAdminService;

  @Transactional(readOnly = true)
  public Page<StoreSellerApplication> listApplications(StoreSellerApplicationStatus status, Pageable pageable) {
    if (status == null) {
      return applicationRepository.findAll(pageable);
    }
    return applicationRepository.findByStatus(status, pageable);
  }

  @Transactional(readOnly = true)
  public StoreSellerApplication getApplication(UUID id) {
    return applicationRepository.findById(id)
      .orElseThrow(() -> new StoreException(HttpStatus.NOT_FOUND, "Seller application not found"));
  }

  @Transactional
  public StoreSellerApplication approve(UUID applicationId, UUID decidedByUserId) {
    StoreSellerApplication app = sellerService.approveApplication(applicationId, decidedByUserId);

    // Assign SELLER role in Keycloak (and guarantee USER) for the target user.
    roleAdminService.assignSeller(decidedByUserId, app.getUserId());
    return app;
  }

  @Transactional
  public StoreSellerApplication reject(UUID applicationId, UUID decidedByUserId, String reason) {
    String safeReason = StringUtils.hasText(reason) ? reason.trim() : "Rejected";
    if (safeReason.length() > 500) {
      safeReason = safeReason.substring(0, 500);
    }
    return sellerService.rejectApplication(applicationId, decidedByUserId, safeReason);
  }
}
