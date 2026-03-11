package com.socialwallet.store.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.socialwallet.store.StoreException;
import com.socialwallet.store.dto.StoreBuyerOrderSummaryDto;
import com.socialwallet.store.model.StoreOrder;
import com.socialwallet.store.repository.StoreOrderRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class StoreBuyerOrderService {
  private final StoreOrderRepository orderRepository;
  private final MedusaClient medusaClient;

  @Transactional(readOnly = true)
  public Page<StoreBuyerOrderSummaryDto> listBuyerOrders(UUID buyerUserId, int page, int size) {
    if (buyerUserId == null) {
      throw new StoreException(HttpStatus.BAD_REQUEST, "buyerUserId is required");
    }
    int safePage = Math.max(page, 0);
    int safeSize = Math.min(Math.max(size, 1), 100);
    PageRequest pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
    return orderRepository.findByBuyerUserId(buyerUserId, pageable).map(this::toSummaryDto);
  }

  @Transactional(readOnly = true)
  public JsonNode getBuyerOrder(UUID buyerUserId, String orderId) {
    if (buyerUserId == null) {
      throw new StoreException(HttpStatus.BAD_REQUEST, "buyerUserId is required");
    }
    if (!StringUtils.hasText(orderId)) {
      throw new StoreException(HttpStatus.BAD_REQUEST, "orderId is required");
    }

    StoreOrder order = orderRepository.findByMedusaOrderId(orderId)
      .orElseThrow(() -> new StoreException(HttpStatus.NOT_FOUND, "Order not found"));

    // Avoid leaking order ids.
    if (order.getBuyerUserId() == null || !buyerUserId.equals(order.getBuyerUserId())) {
      throw new StoreException(HttpStatus.NOT_FOUND, "Order not found");
    }

    return medusaClient.getAdmin("/admin/orders/" + orderId);
  }

  private StoreBuyerOrderSummaryDto toSummaryDto(StoreOrder order) {
    StoreBuyerOrderSummaryDto dto = new StoreBuyerOrderSummaryDto();
    dto.setOrderId(order.getMedusaOrderId());
    dto.setCartId(order.getCartId());
    dto.setCurrencyCode(order.getCurrencyCode());
    dto.setTotalAmount(order.getTotalAmount());
    dto.setOrderStatus(order.getOrderStatus());
    dto.setPaymentStatus(order.getPaymentStatus());
    dto.setFulfillmentStatus(order.getFulfillmentStatus());
    dto.setCreatedAt(order.getCreatedAt());
    dto.setUpdatedAt(order.getUpdatedAt());
    return dto;
  }
}

