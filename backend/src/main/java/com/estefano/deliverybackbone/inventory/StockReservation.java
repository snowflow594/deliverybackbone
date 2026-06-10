package com.estefano.deliverybackbone.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Entity
@Table(name = "stock_reservations")
@Getter
@NoArgsConstructor
public class StockReservation {

    public enum Status { ACTIVE, CONFIRMED, EXPIRED, RELEASED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "order_id")
    private Long orderId;

    private int quantity;

    @Enumerated(EnumType.STRING)
    private Status status = Status.ACTIVE;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    public StockReservation(Long productId, Long orderId, int quantity, OffsetDateTime expiresAt) {
        this.productId = productId;
        this.orderId = orderId;
        this.quantity = quantity;
        this.expiresAt = expiresAt;
    }
}
