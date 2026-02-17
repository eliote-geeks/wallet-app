package com.socialwallet.store.service;

import com.socialwallet.store.StoreException;
import com.socialwallet.store.dto.StoreSellerApplicationRequest;
import com.socialwallet.store.dto.StoreSellerApplicationResponse;
import com.socialwallet.store.dto.StoreSellerProfileResponse;
import com.socialwallet.store.model.StoreSeller;
import com.socialwallet.store.model.StoreSellerApplication;
import com.socialwallet.store.model.StoreSellerApplicationStatus;
import com.socialwallet.store.model.StoreSellerStatus;
import com.socialwallet.store.repository.StoreSellerApplicationRepository;
import com.socialwallet.store.repository.StoreSellerRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class StoreSellerService {
  private final StoreSellerRepository sellerRepository;
  private final StoreSellerApplicationRepository applicationRepository;

  @Transactional
  public StoreSellerApplicationResponse apply(UUID userId, StoreSellerApplicationRequest request) {
    if (userId == null) {
      throw new StoreException(HttpStatus.UNAUTHORIZED, "Authentication required");
    }
    if (request == null || !StringUtils.hasText(request.getShopName())) {
      throw new StoreException(HttpStatus.BAD_REQUEST, "shopName is required");
    }
    if (sellerRepository.findByUserId(userId).isPresent()) {
      throw new StoreException(HttpStatus.CONFLICT, "Seller profile already exists");
    }

    applicationRepository.findFirstByUserIdAndStatusOrderByCreatedAtDesc(userId, StoreSellerApplicationStatus.PENDING)
      .ifPresent(existing -> {
        throw new StoreException(HttpStatus.CONFLICT, "A pending seller application already exists");
      });

    StoreSellerApplication app = new StoreSellerApplication();
    app.setUserId(userId);
    app.setShopName(request.getShopName().trim());
    app.setDescription(request.getDescription());
    app.setStatus(StoreSellerApplicationStatus.PENDING);
    StoreSellerApplication saved = applicationRepository.save(app);
    return toResponse(saved);
  }

  @Transactional(readOnly = true)
  public StoreSellerApplicationResponse getMyLatestApplication(UUID userId) {
    if (userId == null) {
      throw new StoreException(HttpStatus.UNAUTHORIZED, "Authentication required");
    }
    StoreSellerApplication app = applicationRepository.findFirstByUserIdOrderByCreatedAtDesc(userId)
      .orElseThrow(() -> new StoreException(HttpStatus.NOT_FOUND, "Seller application not found"));
    return toResponse(app);
  }

  @Transactional(readOnly = true)
  public StoreSellerProfileResponse getMySellerProfile(UUID userId) {
    if (userId == null) {
      throw new StoreException(HttpStatus.UNAUTHORIZED, "Authentication required");
    }
    StoreSeller seller = sellerRepository.findByUserId(userId)
      .orElseThrow(() -> new StoreException(HttpStatus.NOT_FOUND, "Seller profile not found"));
    return toSellerProfileResponse(seller);
  }

  @Transactional
  public StoreSeller ensureSellerProfile(UUID userId, String shopName, String description) {
    return sellerRepository.findByUserId(userId).orElseGet(() -> {
      StoreSeller seller = new StoreSeller();
      seller.setUserId(userId);
      seller.setShopName(shopName);
      seller.setDescription(description);
      seller.setStatus(StoreSellerStatus.ACTIVE);
      return sellerRepository.save(seller);
    });
  }

  @Transactional
  public StoreSellerApplication approveApplication(UUID applicationId, UUID decidedByUserId) {
    StoreSellerApplication app = applicationRepository.findById(applicationId)
      .orElseThrow(() -> new StoreException(HttpStatus.NOT_FOUND, "Seller application not found"));
    if (app.getStatus() != StoreSellerApplicationStatus.PENDING) {
      throw new StoreException(HttpStatus.CONFLICT, "Seller application is not pending");
    }
    app.setStatus(StoreSellerApplicationStatus.APPROVED);
    app.setRejectionReason(null);
    app.setDecidedAt(Instant.now());
    app.setDecidedByUserId(decidedByUserId);
    applicationRepository.save(app);

    ensureSellerProfile(app.getUserId(), app.getShopName(), app.getDescription());
    return app;
  }

  @Transactional
  public StoreSellerApplication rejectApplication(UUID applicationId, UUID decidedByUserId, String reason) {
    StoreSellerApplication app = applicationRepository.findById(applicationId)
      .orElseThrow(() -> new StoreException(HttpStatus.NOT_FOUND, "Seller application not found"));
    if (app.getStatus() != StoreSellerApplicationStatus.PENDING) {
      throw new StoreException(HttpStatus.CONFLICT, "Seller application is not pending");
    }
    app.setStatus(StoreSellerApplicationStatus.REJECTED);
    app.setRejectionReason(StringUtils.hasText(reason) ? reason.trim() : "Rejected");
    app.setDecidedAt(Instant.now());
    app.setDecidedByUserId(decidedByUserId);
    return applicationRepository.save(app);
  }

  private StoreSellerApplicationResponse toResponse(StoreSellerApplication app) {
    StoreSellerApplicationResponse dto = new StoreSellerApplicationResponse();
    dto.setId(app.getId());
    dto.setUserId(app.getUserId());
    dto.setShopName(app.getShopName());
    dto.setDescription(app.getDescription());
    dto.setStatus(app.getStatus());
    dto.setRejectionReason(app.getRejectionReason());
    dto.setDecidedAt(app.getDecidedAt());
    dto.setDecidedByUserId(app.getDecidedByUserId());
    dto.setCreatedAt(app.getCreatedAt());
    return dto;
  }

  private StoreSellerProfileResponse toSellerProfileResponse(StoreSeller seller) {
    StoreSellerProfileResponse dto = new StoreSellerProfileResponse();
    dto.setId(seller.getId());
    dto.setUserId(seller.getUserId());
    dto.setShopName(seller.getShopName());
    dto.setDescription(seller.getDescription());
    dto.setStatus(seller.getStatus());
    dto.setCreatedAt(seller.getCreatedAt());
    return dto;
  }
}
