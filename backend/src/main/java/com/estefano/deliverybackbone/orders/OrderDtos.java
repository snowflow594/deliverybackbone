package com.estefano.deliverybackbone.orders;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public final class OrderDtos {

    private OrderDtos() {
    }

    public record CheckoutItem(@NotNull Long productId, @Positive int quantity) {
    }

    /** userId viaja en el body hasta que exista el módulo de auth (JWT, fase posterior). */
    public record CheckoutRequest(
            @NotNull Long userId,
            @NotEmpty @Valid List<CheckoutItem> items,
            double deliveryLat,
            double deliveryLng,
            @NotBlank String deliveryAddress,
            String district) {
    }

    public record OrderItemResponse(Long productId, int quantity, BigDecimal unitPrice) {

        static OrderItemResponse from(OrderItem item) {
            return new OrderItemResponse(item.getProductId(), item.getQuantity(), item.getUnitPrice());
        }
    }

    public record OrderResponse(
            Long id,
            Long userId,
            Order.Status status,
            BigDecimal total,
            String deliveryAddress,
            String district,
            OffsetDateTime createdAt,
            OffsetDateTime paidAt,
            List<OrderItemResponse> items) {

        static OrderResponse from(Order order, List<OrderItem> items) {
            return new OrderResponse(order.getId(), order.getUserId(), order.getStatus(),
                    order.getTotal(), order.getDeliveryAddress(), order.getDistrict(),
                    order.getCreatedAt(), order.getPaidAt(),
                    items.stream().map(OrderItemResponse::from).toList());
        }
    }
}
