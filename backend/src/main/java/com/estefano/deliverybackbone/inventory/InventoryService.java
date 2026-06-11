package com.estefano.deliverybackbone.inventory;

import com.estefano.deliverybackbone.common.ConflictException;
import com.estefano.deliverybackbone.common.InsufficientStockException;
import com.estefano.deliverybackbone.common.NotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class InventoryService {

    private final ProductRepository products;
    private final StockReservationRepository reservations;
    private final StockMovementRepository movements;
    private final CategoryRepository categories;
    private final ApplicationEventPublisher events;
    private final long reservationTtlMinutes;

    public InventoryService(ProductRepository products,
                            StockReservationRepository reservations,
                            StockMovementRepository movements,
                            CategoryRepository categories,
                            ApplicationEventPublisher events,
                            @Value("${app.reservation-ttl-minutes:10}") long reservationTtlMinutes) {
        this.products = products;
        this.reservations = reservations;
        this.movements = movements;
        this.categories = categories;
        this.events = events;
        this.reservationTtlMinutes = reservationTtlMinutes;
    }

    public Product getProduct(long productId) {
        return products.findById(productId)
                .orElseThrow(() -> new NotFoundException("Producto %d no existe".formatted(productId)));
    }

    public List<Product> search(Long categoryId, String search) {
        // Normalizar aquí evita pasar null a JPQL (ver nota en ProductRepository).
        String term = search == null ? "" : search.trim();
        return categoryId == null
                ? products.searchByName(term)
                : products.searchByCategoryAndName(categoryId, term);
    }

    /**
     * Reserva stock con UPDATE condicional (atómico). Debe ejecutarse dentro de
     * la transacción del checkout: si otro item falla, esta reserva se revierte.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void reserve(long productId, int quantity) {
        if (products.tryReserve(productId, quantity) == 0) {
            throw new InsufficientStockException(productId, quantity);
        }
        events.publishEvent(new StockChangedEvent(productId, "RESERVED"));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public StockReservation createReservation(long orderId, long productId, int quantity) {
        var expiresAt = OffsetDateTime.now().plusMinutes(reservationTtlMinutes);
        return reservations.save(new StockReservation(productId, orderId, quantity, expiresAt));
    }

    /** Confirma todas las reservas de una orden: total -= qty, reserved -= qty, movimiento SALE. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void confirmReservationsForOrder(long orderId) {
        var orderReservations = reservations.findByOrderId(orderId);
        if (orderReservations.isEmpty()) {
            throw new ConflictException("La orden %d no tiene reservas de stock".formatted(orderId));
        }
        for (var reservation : orderReservations) {
            if (reservations.markConfirmed(reservation.getId()) == 0) {
                throw new ConflictException(
                        "La reserva %d ya no está activa o expiró".formatted(reservation.getId()));
            }
            if (products.confirmSale(reservation.getProductId(), reservation.getQuantity()) == 0) {
                throw new ConflictException(
                        "Inconsistencia de stock en el producto %d".formatted(reservation.getProductId()));
            }
            movements.save(new StockMovement(reservation.getProductId(), -reservation.getQuantity(),
                    StockMovement.Reason.SALE, orderId));
            events.publishEvent(new StockChangedEvent(reservation.getProductId(), "SOLD"));
        }
    }

    /**
     * Expira reservas vencidas y libera su stock reservado. La transición
     * condicional a EXPIRED evita la carrera contra un pago concurrente.
     * Devuelve los IDs de las órdenes afectadas para que el módulo orders las marque.
     */
    @Transactional
    public Set<Long> expireOverdueReservations() {
        Set<Long> affectedOrders = new HashSet<>();
        for (var reservation : reservations.findOverdue(OffsetDateTime.now())) {
            if (reservations.markExpired(reservation.getId()) == 1) {
                products.releaseReserved(reservation.getProductId(), reservation.getQuantity());
                movements.save(new StockMovement(reservation.getProductId(), reservation.getQuantity(),
                        StockMovement.Reason.RESERVATION_EXPIRED, reservation.getId()));
                events.publishEvent(new StockChangedEvent(reservation.getProductId(), "RELEASED"));
                affectedOrders.add(reservation.getOrderId());
            }
        }
        return affectedOrders;
    }

    @Transactional
    public Product createProduct(long categoryId, String sku, String name, BigDecimal price, int initialStock) {
        if (!categories.existsById(categoryId)) {
            throw new NotFoundException("Categoría %d no existe".formatted(categoryId));
        }
        var product = products.save(new Product(categoryId, sku, name, price, initialStock));
        if (initialStock > 0) {
            movements.save(new StockMovement(product.getId(), initialStock,
                    StockMovement.Reason.RESTOCK, null));
        }
        return product;
    }

    @Transactional
    public Product restock(long productId, int quantity) {
        if (products.addStock(productId, quantity) == 0) {
            throw new NotFoundException("Producto %d no existe".formatted(productId));
        }
        movements.save(new StockMovement(productId, quantity, StockMovement.Reason.RESTOCK, null));
        events.publishEvent(new StockChangedEvent(productId, "RESTOCKED"));
        return getProduct(productId);
    }
}
