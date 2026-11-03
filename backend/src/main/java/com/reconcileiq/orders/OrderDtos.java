package com.reconcileiq.orders;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class OrderDtos {

    private OrderDtos() {
    }

    public record CreateOrderRequest(
            BigDecimal amount,
            String currency) {
    }

    public record MerchantOrderResponse(
            String merchantOrderId,
            BigDecimal expectedAmount,
            String currency,
            String status,
            String paypalOrderId,
            String approvalUrl,
            LocalDateTime createdAt) {

        public static MerchantOrderResponse from(MerchantOrder order) {
            return new MerchantOrderResponse(
                    order.getMerchantOrderId(),
                    order.getExpectedAmount(),
                    order.getCurrency(),
                    order.getStatus(),
                    order.getPaypalOrderId(),
                    order.getApprovalUrl(),
                    order.getCreatedAt()
            );
        }
    }
}