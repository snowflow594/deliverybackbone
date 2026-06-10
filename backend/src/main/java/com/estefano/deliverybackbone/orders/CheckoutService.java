package com.estefano.deliverybackbone.orders;

import com.estefano.deliverybackbone.common.ConflictException;
import com.estefano.deliverybackbone.common.NotFoundException;
import com.estefano.deliverybackbone.inventory.InventoryService;
import com.estefano.deliverybackbone.orders.OrderDtos.CheckoutRequest;
import com.estefano.deliverybackbone.orders.OrderDtos.OrderResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Flujo de checkout con reserva de stock (ver ARQUITECTURA.md §3.1).
 * Orden de operaciones dentro de la transacción:
 *   1. validar productos  2. reservar stock (UPDATE condicional, por item)
 *   3. crear orden PENDING_PAYMENT  4. crear items (precio congelado) + reservas con TTL.
 * Si cualquier item no tiene stock, TODO se revierte (409 Conflict).
 */
@Service
public class CheckoutService {

    private final OrderRepository orders;
    private final OrderItemRepository orderItems;
    private final InventoryService inventory;

    public CheckoutService(OrderRepository orders, OrderItemRepository orderItems,
                           InventoryService inventory) {
        this.orders = orders;
        this.orderItems = orderItems;
        this.inventory = inventory;
    }

    @Transactional
    public OrderResponse checkout(CheckoutRequest request) {
        if (!orders.userExists(request.userId())) {
            throw new NotFoundException("Usuario %d no existe".formatted(request.userId()));
        }

        // 1. Validar productos y congelar precios antes de reservar.
        var products = request.items().stream()
                .map(item -> inventory.getProduct(item.productId()))
                .toList();

        // 2. Reservar stock por item; si alguno falla, rollback de toda la transacción.
        for (var item : request.items()) {
            inventory.reserve(item.productId(), item.quantity());
        }

        // 3. Crear la orden con el total calculado de los precios congelados.
        var total = BigDecimal.ZERO;
        for (int i = 0; i < request.items().size(); i++) {
            total = total.add(products.get(i).getPrice()
                    .multiply(BigDecimal.valueOf(request.items().get(i).quantity())));
        }
        var order = orders.save(new Order(request.userId(), total, request.deliveryLat(),
                request.deliveryLng(), request.deliveryAddress(), request.district()));

        // 4. Items con precio congelado + reservas con TTL.
        var savedItems = new ArrayList<OrderItem>();
        for (int i = 0; i < request.items().size(); i++) {
            var item = request.items().get(i);
            savedItems.add(orderItems.save(new OrderItem(order.getId(), item.productId(),
                    item.quantity(), products.get(i).getPrice())));
            inventory.createReservation(order.getId(), item.productId(), item.quantity());
        }

        return OrderResponse.from(order, savedItems);
    }

    /** Pago simulado: PENDING_PAYMENT → PAID y confirmación de reservas (venta efectiva). */
    @Transactional
    public OrderResponse pay(long orderId) {
        if (!orders.existsById(orderId)) {
            throw new NotFoundException("Orden %d no existe".formatted(orderId));
        }
        if (orders.markPaid(orderId) == 0) {
            throw new ConflictException("La orden %d no está pendiente de pago".formatted(orderId));
        }
        inventory.confirmReservationsForOrder(orderId);
        return getOrder(orderId);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(long orderId) {
        var order = orders.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Orden %d no existe".formatted(orderId)));
        return OrderResponse.from(order, orderItems.findByOrderId(orderId));
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getOrdersByUser(long userId) {
        return orders.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(o -> OrderResponse.from(o, orderItems.findByOrderId(o.getId())))
                .toList();
    }

    /** Invocado por el job programado: expira reservas vencidas y marca sus órdenes. */
    @Transactional
    public int expireOverdueOrders() {
        var affectedOrders = inventory.expireOverdueReservations();
        int expired = 0;
        for (var orderId : affectedOrders) {
            expired += orders.markExpired(orderId);
        }
        return expired;
    }
}
