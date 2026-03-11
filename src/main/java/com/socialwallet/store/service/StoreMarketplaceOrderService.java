package com.socialwallet.store.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.socialwallet.store.StoreException;
import com.socialwallet.store.StoreSettlementProperties;
import com.socialwallet.store.dto.StoreSellerOrderDetailDto;
import com.socialwallet.store.dto.StoreSellerOrderSummaryDto;
import com.socialwallet.store.model.StoreOrder;
import com.socialwallet.store.model.StoreOrderSeller;
import com.socialwallet.store.model.StoreOrderSellerStatus;
import com.socialwallet.store.model.StoreProductOwnership;
import com.socialwallet.store.repository.StoreOrderRepository;
import com.socialwallet.store.repository.StoreOrderSellerRepository;
import com.socialwallet.store.repository.StoreProductOwnershipRepository;
import com.socialwallet.wallet.WalletInternalUsers;
import com.socialwallet.wallet.application.WalletPaymentService;
import com.socialwallet.wallet.model.WalletTransaction;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class StoreMarketplaceOrderService {
  private final ObjectMapper objectMapper;
  private final StoreOrderRepository orderRepository;
  private final StoreOrderSellerRepository orderSellerRepository;
  private final StoreProductOwnershipRepository productOwnershipRepository;
  private final WalletPaymentService walletPaymentService;
  private final StoreSettlementProperties settlementProperties;
  private final MedusaClient medusaClient;

  private static final Set<String> SETTLE_EVENTS = Set.of(
    "payment.captured",
    "order.payment_captured",
    "order.completed"
  );

  private static final Set<String> CANCEL_EVENTS = Set.of(
    "payment.failed",
    "order.canceled",
    "order.cancelled",
    "order.refunded"
  );

  @Transactional
  public void handleMedusaWebhook(String eventName, String payload) {
    if (!StringUtils.hasText(eventName) || !StringUtils.hasText(payload)) {
      return;
    }

    String event = eventName.trim().toLowerCase(Locale.ROOT);
    // We only care about order-related events.
    if (!event.startsWith("order.") && !event.startsWith("payment.")) {
      return;
    }

    JsonNode root;
    try {
      root = objectMapper.readTree(payload);
    } catch (Exception ex) {
      log.debug("Unable to parse Medusa webhook payload for event={}", eventName, ex);
      return;
    }

    JsonNode orderNode = extractOrderNode(root);
    if (orderNode == null || orderNode.isMissingNode() || orderNode.isNull()) {
      return;
    }

    String medusaOrderId = textAt(orderNode, "id");
    if (!StringUtils.hasText(medusaOrderId)) {
      return;
    }

    JsonNode enrichedOrderNode = enrichOrderNodeForProcessing(orderNode, medusaOrderId);

    StoreOrder order = upsertOrder(enrichedOrderNode);
    Map<UUID, SellerAggregation> sellers = aggregateSellers(enrichedOrderNode);

    if (sellers.isEmpty()) {
      log.debug("No seller items found for orderId={} event={}", medusaOrderId, event);
    } else {
      upsertSellerOrders(order, sellers, event, enrichedOrderNode);
    }

    if (shouldSettle(event, enrichedOrderNode)) {
      settleOrder(order, event);
    }

    handleRefundOrCancel(order, event);
  }

  private JsonNode enrichOrderNodeForProcessing(JsonNode orderNode, String medusaOrderId) {
    JsonNode items = orderNode != null ? orderNode.path("items") : null;
    if (itemsHaveQuantities(items)) {
      return orderNode;
    }

    if (!StringUtils.hasText(medusaOrderId)) {
      return orderNode;
    }

    try {
      MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
      params.add("fields", "*items,*items.product,*items.product.metadata,*items.variant,*items.variant.product,*items.variant.product.metadata");
      JsonNode response = medusaClient.getAdmin("/admin/orders/" + medusaOrderId, params);
      JsonNode adminOrder = response != null ? response.path("order") : null;
      JsonNode adminItems = adminOrder != null ? adminOrder.path("items") : null;

      if (adminItems == null || !adminItems.isArray() || adminItems.isEmpty()) {
        return orderNode;
      }

      if (orderNode != null && orderNode.isObject()) {
        ObjectNode copy = ((ObjectNode) orderNode).deepCopy();
        copy.set("items", adminItems);
        return copy;
      }

      return adminOrder != null && !adminOrder.isMissingNode() && !adminOrder.isNull() ? adminOrder : orderNode;
    } catch (Exception ex) {
      log.warn("Unable to enrich Medusa order payload for orderId={}: {}", medusaOrderId, ex.getMessage());
      return orderNode;
    }
  }

  private boolean itemsHaveQuantities(JsonNode items) {
    if (items == null || !items.isArray() || items.isEmpty()) {
      return false;
    }
    for (JsonNode item : items) {
      if (item == null || item.isNull() || item.isMissingNode()) {
        return false;
      }
      JsonNode quantity = item.get("quantity");
      if (quantity == null || !quantity.isNumber()) {
        return false;
      }
    }
    return true;
  }

  @Transactional(readOnly = true)
  public Page<StoreOrderSeller> listSellerOrders(UUID sellerUserId,
                                                 StoreOrderSellerStatus status,
                                                 int page,
                                                 int size) {
    if (sellerUserId == null) {
      throw new StoreException(HttpStatus.BAD_REQUEST, "sellerUserId is required");
    }
    int safePage = Math.max(page, 0);
    int safeSize = Math.min(Math.max(size, 1), 100);
    PageRequest pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
    if (status == null) {
      return orderSellerRepository.findBySellerUserId(sellerUserId, pageable);
    }
    return orderSellerRepository.findBySellerUserIdAndStatus(sellerUserId, status, pageable);
  }
  @Transactional(readOnly = true)
  public Page<StoreSellerOrderSummaryDto> listSellerOrderSummaries(UUID sellerUserId,
                                                                   StoreOrderSellerStatus status,
                                                                   int page,
                                                                   int size) {
    Page<StoreOrderSeller> sellerOrders = listSellerOrders(sellerUserId, status, page, size);
    List<UUID> orderIds = sellerOrders.getContent().stream()
      .map(StoreOrderSeller::getOrderId)
      .distinct()
      .toList();

    Map<UUID, StoreOrder> ordersById = new HashMap<>();
    if (!orderIds.isEmpty()) {
      orderRepository.findAllById(orderIds)
        .forEach(order -> ordersById.put(order.getId(), order));
    }

    return sellerOrders.map(sellerOrder -> toSummaryDto(sellerOrder, ordersById.get(sellerOrder.getOrderId())));
  }

  @Transactional(readOnly = true)
  public StoreSellerOrderDetailDto getSellerOrderDetail(UUID sellerUserId, String medusaOrderId) {
    StoreOrder order = getOrderByMedusaId(medusaOrderId);
    StoreOrderSeller sellerOrder = orderSellerRepository.findByOrderIdAndSellerUserId(order.getId(), sellerUserId)
      .orElseThrow(() -> new StoreException(HttpStatus.NOT_FOUND, "Seller order not found"));

    StoreSellerOrderDetailDto dto = new StoreSellerOrderDetailDto();
    populateSummary(dto, sellerOrder, order);
    dto.setItems(parseItems(sellerOrder.getItems()));
    return dto;
  }


  @Transactional(readOnly = true)
  public StoreOrderSeller getSellerOrder(UUID sellerUserId, String medusaOrderId) {
    if (sellerUserId == null) {
      throw new StoreException(HttpStatus.BAD_REQUEST, "sellerUserId is required");
    }
    if (!StringUtils.hasText(medusaOrderId)) {
      throw new StoreException(HttpStatus.BAD_REQUEST, "orderId is required");
    }
    StoreOrder order = orderRepository.findByMedusaOrderId(medusaOrderId)
      .orElseThrow(() -> new StoreException(HttpStatus.NOT_FOUND, "Order not found"));

    return orderSellerRepository.findByOrderIdAndSellerUserId(order.getId(), sellerUserId)
      .orElseThrow(() -> new StoreException(HttpStatus.NOT_FOUND, "Seller order not found"));
  }

  @Transactional(readOnly = true)
  public StoreOrder getOrderByMedusaId(String medusaOrderId) {
    if (!StringUtils.hasText(medusaOrderId)) {
      throw new StoreException(HttpStatus.BAD_REQUEST, "orderId is required");
    }
    return orderRepository.findByMedusaOrderId(medusaOrderId)
      .orElseThrow(() -> new StoreException(HttpStatus.NOT_FOUND, "Order not found"));
  }

  private StoreOrder upsertOrder(JsonNode orderNode) {
    String medusaOrderId = textAt(orderNode, "id");
    StoreOrder existing = orderRepository.findByMedusaOrderId(medusaOrderId).orElse(null);
    StoreOrder order = existing != null ? existing : new StoreOrder();
    if (existing == null) {
      order.setMedusaOrderId(medusaOrderId);
    }

    String cartId = firstNonBlank(
      textAt(orderNode, "cart_id"),
      textAt(orderNode, "cart", "id"),
      textAt(orderNode, "metadata", "cart_id")
    );

    UUID buyerUserId = parseUuidOrNull(firstNonBlank(
      textAt(orderNode, "metadata", "kobo_user_id"),
      textAt(orderNode, "customer", "metadata", "kobo_user_id")
    ));

    order.setCartId(cartId);
    order.setBuyerUserId(buyerUserId);
    order.setCurrencyCode(textAt(orderNode, "currency_code"));
    order.setTotalAmount(longAt(orderNode, "total"));
    order.setOrderStatus(textAt(orderNode, "status"));
    order.setPaymentStatus(textAt(orderNode, "payment_status"));
    order.setFulfillmentStatus(textAt(orderNode, "fulfillment_status"));

    try {
      return orderRepository.save(order);
    } catch (DataIntegrityViolationException ex) {
      // Concurrent webhook + synthetic settlement can race on unique medusa_order_id.
      // In this case, reload and update existing row instead of failing the flow.
      StoreOrder concurrent = orderRepository.findByMedusaOrderId(medusaOrderId).orElseThrow(() ->
        new StoreException(HttpStatus.CONFLICT, "Order upsert conflict for medusa order " + medusaOrderId));
      concurrent.setCartId(order.getCartId());
      concurrent.setBuyerUserId(order.getBuyerUserId());
      concurrent.setCurrencyCode(order.getCurrencyCode());
      concurrent.setTotalAmount(order.getTotalAmount());
      concurrent.setOrderStatus(order.getOrderStatus());
      concurrent.setPaymentStatus(order.getPaymentStatus());
      concurrent.setFulfillmentStatus(order.getFulfillmentStatus());
      return orderRepository.save(concurrent);
    }
  }

  private void upsertSellerOrders(StoreOrder order,
                                 Map<UUID, SellerAggregation> sellers,
                                 String normalizedEvent,
                                 JsonNode orderNode) {
    for (Map.Entry<UUID, SellerAggregation> entry : sellers.entrySet()) {
      UUID sellerId = entry.getKey();
      SellerAggregation agg = entry.getValue();

      StoreOrderSeller sellerOrder = orderSellerRepository.findByOrderIdAndSellerUserId(order.getId(), sellerId)
        .orElse(null);
      if (sellerOrder == null) {
        sellerOrder = new StoreOrderSeller();
        sellerOrder.setOrderId(order.getId());
        sellerOrder.setSellerUserId(sellerId);
      }

      long gross = Math.max(agg.grossAmount, 0L);
      long fee = computePlatformFee(gross);
      long net = Math.max(gross - fee, 0L);

      String sellerCurrency = firstNonBlank(order.getCurrencyCode(), agg.currency, sellerOrder.getCurrencyCode());
      sellerOrder.setCurrencyCode(sellerCurrency);
      sellerOrder.setGrossAmount(gross);
      sellerOrder.setPlatformFeeAmount(fee);
      sellerOrder.setNetAmount(net);
      sellerOrder.setItems(agg.itemsNode);

      StoreOrderSellerStatus nextStatus = deriveStatus(sellerOrder, normalizedEvent, orderNode);
      sellerOrder.setStatus(nextStatus);

      orderSellerRepository.save(sellerOrder);
    }
  }

  private StoreOrderSellerStatus deriveStatus(StoreOrderSeller sellerOrder,
                                             String event,
                                             JsonNode orderNode) {
    StoreOrderSellerStatus current = sellerOrder.getStatus();
    if (current == null) {
      current = StoreOrderSellerStatus.PENDING;
    }

    if (CANCEL_EVENTS.contains(event)) {
      boolean settledAny = sellerOrder.getSettledWalletTxId() != null
        || sellerOrder.getPlatformFeeSettledWalletTxId() != null
        || current == StoreOrderSellerStatus.SETTLED;
      if (settledAny) {
        boolean reversedAny = sellerOrder.getReversalWalletTxId() != null
          || sellerOrder.getPlatformFeeReversalWalletTxId() != null
          || current == StoreOrderSellerStatus.REFUNDED;
        if (reversedAny) {
          return StoreOrderSellerStatus.REFUNDED;
        }
        return StoreOrderSellerStatus.REFUND_PENDING;
      }
      return StoreOrderSellerStatus.CANCELLED;
    }

    String paymentStatus = textAt(orderNode, "payment_status");
    if (StringUtils.hasText(paymentStatus)) {
      String p = paymentStatus.trim().toLowerCase(Locale.ROOT);
      if (p.contains("captured") || p.contains("paid")) {
        return current == StoreOrderSellerStatus.SETTLED ? StoreOrderSellerStatus.SETTLED : StoreOrderSellerStatus.PAID;
      }
    }

    if (SETTLE_EVENTS.contains(event)) {
      return current == StoreOrderSellerStatus.SETTLED ? StoreOrderSellerStatus.SETTLED : StoreOrderSellerStatus.PAID;
    }

    return current;
  }

  private void settleOrder(StoreOrder order, String normalizedEvent) {
    List<StoreOrderSeller> sellers = orderSellerRepository.findByOrderId(order.getId());
    if (sellers.isEmpty()) {
      return;
    }

    for (StoreOrderSeller sellerOrder : sellers) {
      boolean sellerSettled = sellerOrder.getSettledWalletTxId() != null;
      boolean platformFeeSettled = sellerOrder.getPlatformFeeSettledWalletTxId() != null;
      if (sellerOrder.getStatus() == StoreOrderSellerStatus.CANCELLED
        || sellerOrder.getStatus() == StoreOrderSellerStatus.REFUND_PENDING
        || sellerOrder.getStatus() == StoreOrderSellerStatus.REFUNDED) {
        continue;
      }

      long net = sellerOrder.getNetAmount() == null ? 0L : Math.max(sellerOrder.getNetAmount(), 0L);
      long fee = sellerOrder.getPlatformFeeAmount() == null ? 0L : Math.max(sellerOrder.getPlatformFeeAmount(), 0L);

      if (net <= 0 && fee <= 0) {
        continue;
      }

      if (!sellerSettled && net > 0) {
        String metadata = buildSettlementMetadata(order.getMedusaOrderId(), sellerOrder);
        WalletTransaction tx = walletPaymentService.settleToUser(
          sellerOrder.getSellerUserId(),
          sellerOrder.getCurrencyCode(),
          net,
          "ORDER_SELLER",
          order.getMedusaOrderId(),
          metadata
        );
        sellerOrder.setSettledWalletTxId(tx.getId());
        sellerOrder.setSettledAt(Instant.now());
        sellerSettled = true;
      }

      if (!platformFeeSettled && fee > 0) {
        String metadata = buildPlatformFeeSettlementMetadata(order.getMedusaOrderId(), sellerOrder, fee);
        WalletTransaction tx = walletPaymentService.settleToUser(
          WalletInternalUsers.PLATFORM_TREASURY_USER_ID,
          sellerOrder.getCurrencyCode(),
          fee,
          "ORDER_FEE",
          order.getMedusaOrderId(),
          metadata
        );
        sellerOrder.setPlatformFeeSettledWalletTxId(tx.getId());
        sellerOrder.setPlatformFeeSettledAt(Instant.now());
        platformFeeSettled = true;
      }

      if ((sellerSettled || net <= 0) && (platformFeeSettled || fee <= 0)) {
        sellerOrder.setStatus(StoreOrderSellerStatus.SETTLED);
      }
      orderSellerRepository.save(sellerOrder);

      log.info("Marketplace settlement: orderId={}, sellerId={}, net={}, fee={}, event={}",
        order.getMedusaOrderId(), sellerOrder.getSellerUserId(), net, fee, normalizedEvent);
    }
  }

  private void handleRefundOrCancel(StoreOrder order, String normalizedEvent) {
    if (order == null || !CANCEL_EVENTS.contains(normalizedEvent)) {
      return;
    }

    List<StoreOrderSeller> sellers = orderSellerRepository.findByOrderId(order.getId());
    if (sellers.isEmpty()) {
      return;
    }

    for (StoreOrderSeller sellerOrder : sellers) {
      boolean sellerSettled = sellerOrder.getSettledWalletTxId() != null;
      boolean feeSettled = sellerOrder.getPlatformFeeSettledWalletTxId() != null;
      boolean anySettled = sellerSettled || feeSettled || sellerOrder.getStatus() == StoreOrderSellerStatus.SETTLED;
      if (!anySettled) {
        if (sellerOrder.getStatus() != StoreOrderSellerStatus.CANCELLED) {
          sellerOrder.setStatus(StoreOrderSellerStatus.CANCELLED);
          orderSellerRepository.save(sellerOrder);
        }
        continue;
      }

      long net = sellerOrder.getNetAmount() == null ? 0L : Math.max(sellerOrder.getNetAmount(), 0L);
      long fee = sellerOrder.getPlatformFeeAmount() == null ? 0L : Math.max(sellerOrder.getPlatformFeeAmount(), 0L);

      sellerOrder.setStatus(StoreOrderSellerStatus.REFUND_PENDING);
      orderSellerRepository.save(sellerOrder);

      if (sellerSettled && sellerOrder.getReversalWalletTxId() == null && net > 0) {
        try {
          String metadata = buildRefundMetadata(order.getMedusaOrderId(), sellerOrder, normalizedEvent);
          WalletTransaction reversal = walletPaymentService.refundFromUser(
            sellerOrder.getSellerUserId(),
            sellerOrder.getCurrencyCode(),
            net,
            "ORDER_SELLER",
            order.getMedusaOrderId(),
            metadata
          );

          sellerOrder.setReversalWalletTxId(reversal.getId());
          sellerOrder.setReversedAt(Instant.now());
          sellerOrder.setReversalFailureReason(null);
        } catch (Exception ex) {
          String reason = ex.getMessage();
          if (reason != null && reason.length() > 500) {
            reason = reason.substring(0, 500);
          }
          sellerOrder.setReversalFailureReason(reason);
          log.warn("Marketplace refund reversal failed: orderId={}, sellerId={}, reason={}",
            order.getMedusaOrderId(), sellerOrder.getSellerUserId(), ex.getMessage());
        }
      }

      if (feeSettled && sellerOrder.getPlatformFeeReversalWalletTxId() == null && fee > 0) {
        try {
          String metadata = buildPlatformFeeRefundMetadata(order.getMedusaOrderId(), sellerOrder, normalizedEvent, fee);
          WalletTransaction reversal = walletPaymentService.refundFromUser(
            WalletInternalUsers.PLATFORM_TREASURY_USER_ID,
            sellerOrder.getCurrencyCode(),
            fee,
            "ORDER_FEE",
            order.getMedusaOrderId(),
            metadata
          );

          sellerOrder.setPlatformFeeReversalWalletTxId(reversal.getId());
          sellerOrder.setPlatformFeeReversedAt(Instant.now());
          sellerOrder.setPlatformFeeReversalFailureReason(null);
        } catch (Exception ex) {
          String reason = ex.getMessage();
          if (reason != null && reason.length() > 500) {
            reason = reason.substring(0, 500);
          }
          sellerOrder.setPlatformFeeReversalFailureReason(reason);
          log.warn("Marketplace platform fee reversal failed: orderId={}, sellerId={}, reason={}",
            order.getMedusaOrderId(), sellerOrder.getSellerUserId(), ex.getMessage());
        }
      }

      boolean sellerReversed = !sellerSettled || net <= 0 || sellerOrder.getReversalWalletTxId() != null;
      boolean feeReversed = !feeSettled || fee <= 0 || sellerOrder.getPlatformFeeReversalWalletTxId() != null;
      if (sellerReversed && feeReversed) {
        sellerOrder.setStatus(StoreOrderSellerStatus.REFUNDED);
      } else {
        sellerOrder.setStatus(StoreOrderSellerStatus.REFUND_PENDING);
      }
      orderSellerRepository.save(sellerOrder);

      log.info("Marketplace refund: orderId={}, sellerId={}, net={}, fee={}, status={}",
        order.getMedusaOrderId(), sellerOrder.getSellerUserId(), net, fee, sellerOrder.getStatus());
    }
  }

  private String buildRefundMetadata(String orderId, StoreOrderSeller sellerOrder, String event) {
    try {
      ObjectNode root = objectMapper.createObjectNode();
      root.put("orderId", orderId);
      root.put("sellerId", sellerOrder.getSellerUserId().toString());
      root.put("event", event);
      root.put("net", sellerOrder.getNetAmount());
      return objectMapper.writeValueAsString(root);
    } catch (Exception ex) {
      return "";
    }
  }

  private String buildPlatformFeeSettlementMetadata(String orderId, StoreOrderSeller sellerOrder, long fee) {
    try {
      ObjectNode root = objectMapper.createObjectNode();
      root.put("orderId", orderId);
      root.put("sellerId", sellerOrder.getSellerUserId().toString());
      root.put("fee", fee);
      return objectMapper.writeValueAsString(root);
    } catch (Exception ex) {
      return "";
    }
  }

  private String buildPlatformFeeRefundMetadata(String orderId,
                                                StoreOrderSeller sellerOrder,
                                                String event,
                                                long fee) {
    try {
      ObjectNode root = objectMapper.createObjectNode();
      root.put("orderId", orderId);
      root.put("sellerId", sellerOrder.getSellerUserId().toString());
      root.put("event", event);
      root.put("fee", fee);
      return objectMapper.writeValueAsString(root);
    } catch (Exception ex) {
      return "";
    }
  }


  private String buildSettlementMetadata(String orderId, StoreOrderSeller sellerOrder) {
    try {
      ObjectNode root = objectMapper.createObjectNode();
      root.put("orderId", orderId);
      root.put("sellerId", sellerOrder.getSellerUserId().toString());
      root.put("gross", sellerOrder.getGrossAmount());
      root.put("fee", sellerOrder.getPlatformFeeAmount());
      root.put("net", sellerOrder.getNetAmount());
      return objectMapper.writeValueAsString(root);
    } catch (Exception ex) {
      return "";
    }
  }

  private boolean shouldSettle(String normalizedEvent, JsonNode orderNode) {
    if (CANCEL_EVENTS.contains(normalizedEvent)) {
      return false;
    }
    if (SETTLE_EVENTS.contains(normalizedEvent)) {
      return true;
    }
    String paymentStatus = textAt(orderNode, "payment_status");
    if (!StringUtils.hasText(paymentStatus)) {
      return false;
    }
    String p = paymentStatus.trim().toLowerCase(Locale.ROOT);
    return p.contains("captured") || p.contains("paid");
  }

  private long computePlatformFee(long grossAmount) {
    int bps = settlementProperties != null ? settlementProperties.getPlatformFeeBps() : 0;
    int safeBps = Math.max(bps, 0);
    if (safeBps == 0 || grossAmount <= 0) {
      return 0L;
    }
    // bps: 100 = 1% => divide by 10_000
    return (grossAmount * safeBps) / 10_000L;
  }

  private Map<UUID, SellerAggregation> aggregateSellers(JsonNode orderNode) {
    Map<UUID, SellerAggregation> results = new HashMap<>();
    JsonNode items = orderNode.path("items");
    if (!items.isArray()) {
      return results;
    }

    String currency = textAt(orderNode, "currency_code");

    for (JsonNode item : items) {
      UUID sellerId = resolveSellerId(item);
      if (sellerId == null) {
        continue;
      }

      long itemTotal = resolveLineItemTotal(item);
      SellerAggregation agg = results.computeIfAbsent(sellerId, id -> new SellerAggregation(currency));
      agg.grossAmount += itemTotal;
      agg.items.add(toItemSnapshot(item, itemTotal));
    }

    for (Map.Entry<UUID, SellerAggregation> entry : results.entrySet()) {
      entry.getValue().itemsNode = serializeItems(entry.getValue().items);
    }

    return results;
  }

  private JsonNode serializeItems(List<ObjectNode> items) {
    ArrayNode array = objectMapper.createArrayNode();
    if (items != null) {
      for (ObjectNode node : items) {
        array.add(node);
      }
    }
    return array;
  }

  private ObjectNode toItemSnapshot(JsonNode item, long itemTotal) {
    ObjectNode snapshot = objectMapper.createObjectNode();
    snapshot.put("lineItemId", textAt(item, "id"));

    String productId = firstNonBlank(
      textAt(item, "product_id"),
      textAt(item, "product", "id"),
      textAt(item, "variant", "product_id"),
      textAt(item, "variant", "product", "id")
    );
    snapshot.put("productId", productId);

    snapshot.put("title", firstNonBlank(textAt(item, "title"), textAt(item, "product_title")));

    Long quantity = longAt(item, "quantity");
    snapshot.put("quantity", quantity != null ? quantity : 0L);

    Long unitPrice = longAt(item, "unit_price");
    if (unitPrice == null) {
      unitPrice = longAt(item, "original_unit_price");
    }
    snapshot.put("unitPrice", unitPrice != null ? unitPrice : 0L);

    snapshot.put("total", itemTotal);
    return snapshot;
  }

  private long resolveLineItemTotal(JsonNode item) {
    Long total = longAt(item, "total");
    if (total != null) {
      return total;
    }
    Long subtotal = longAt(item, "subtotal");
    if (subtotal != null) {
      return subtotal;
    }
    Long unitPrice = longAt(item, "unit_price");
    Long quantity = longAt(item, "quantity");
    if (unitPrice != null && quantity != null) {
      return unitPrice * quantity;
    }
    return 0L;
  }

  private UUID resolveSellerId(JsonNode item) {
    String seller = firstNonBlank(
      textAt(item, "metadata", "kobo_seller_id"),
      textAt(item, "product", "metadata", "kobo_seller_id"),
      textAt(item, "variant", "product", "metadata", "kobo_seller_id")
    );

    if (StringUtils.hasText(seller)) {
      return parseUuidOrNull(seller);
    }

    String productId = firstNonBlank(
      textAt(item, "product_id"),
      textAt(item, "product", "id"),
      textAt(item, "variant", "product_id"),
      textAt(item, "variant", "product", "id")
    );

    if (!StringUtils.hasText(productId)) {
      return null;
    }

    Optional<StoreProductOwnership> ownership = productOwnershipRepository.findByProductId(productId);
    return ownership.map(StoreProductOwnership::getSellerUserId).orElse(null);
  }

  private JsonNode extractOrderNode(JsonNode root) {
    if (root == null || root.isNull() || root.isMissingNode()) {
      return null;
    }
    JsonNode data = root.path("data");
    if (data != null && data.isObject()) {
      if (data.hasNonNull("order")) {
        return data.path("order");
      }
      if (data.hasNonNull("id") && data.has("items")) {
        return data;
      }
    }
    if (root.hasNonNull("order")) {
      return root.path("order");
    }
    return null;
  }

  private String textAt(JsonNode node, String... path) {
    JsonNode current = node;
    for (String segment : path) {
      if (current == null || current.isMissingNode() || current.isNull()) {
        return null;
      }
      current = current.path(segment);
    }
    if (current == null || current.isMissingNode() || current.isNull()) {
      return null;
    }
    String value = current.asText();
    return StringUtils.hasText(value) ? value : null;
  }

  private Long longAt(JsonNode node, String... path) {
    JsonNode current = node;
    for (String segment : path) {
      if (current == null || current.isMissingNode() || current.isNull()) {
        return null;
      }
      current = current.path(segment);
    }
    if (current == null || current.isMissingNode() || current.isNull() || !current.isNumber()) {
      return null;
    }
    return current.asLong();
  }

  private String firstNonBlank(String... values) {
    if (values == null) {
      return null;
    }
    for (String value : values) {
      if (StringUtils.hasText(value)) {
        return value;
      }
    }
    return null;
  }

  private UUID parseUuidOrNull(String value) {
    if (!StringUtils.hasText(value)) {
      return null;
    }
    try {
      return UUID.fromString(value);
    } catch (IllegalArgumentException ex) {
      return null;
    }
  }


  private StoreSellerOrderSummaryDto toSummaryDto(StoreOrderSeller sellerOrder, StoreOrder order) {
    StoreSellerOrderSummaryDto dto = new StoreSellerOrderSummaryDto();
    populateSummary(dto, sellerOrder, order);
    return dto;
  }

  private void populateSummary(StoreSellerOrderSummaryDto dto, StoreOrderSeller sellerOrder, StoreOrder order) {
    dto.setOrderId(order != null ? order.getMedusaOrderId() : null);
    dto.setSellerUserId(sellerOrder.getSellerUserId());
    dto.setBuyerUserId(order != null ? order.getBuyerUserId() : null);
    dto.setCartId(order != null ? order.getCartId() : null);
    dto.setCurrency(sellerOrder.getCurrencyCode());
    dto.setGrossAmount(sellerOrder.getGrossAmount());
    dto.setPlatformFeeAmount(sellerOrder.getPlatformFeeAmount());
    dto.setNetAmount(sellerOrder.getNetAmount());
    dto.setStatus(sellerOrder.getStatus() != null ? sellerOrder.getStatus().name() : null);
    dto.setOrderStatus(order != null ? order.getOrderStatus() : null);
    dto.setPaymentStatus(order != null ? order.getPaymentStatus() : null);
    dto.setFulfillmentStatus(order != null ? order.getFulfillmentStatus() : null);
    dto.setSettledWalletTxId(sellerOrder.getSettledWalletTxId());
    dto.setSettledAt(sellerOrder.getSettledAt());
    dto.setCreatedAt(sellerOrder.getCreatedAt());
  }

  private JsonNode parseItems(JsonNode items) {
    if (items == null || items.isNull() || items.isMissingNode()) {
      return objectMapper.createArrayNode();
    }
    return items;
  }

  private static class SellerAggregation {
    private final String currency;
    private long grossAmount = 0L;
    private final List<ObjectNode> items = new ArrayList<>();
    private JsonNode itemsNode;

    private SellerAggregation(String currency) {
      this.currency = currency;
    }
  }
}
