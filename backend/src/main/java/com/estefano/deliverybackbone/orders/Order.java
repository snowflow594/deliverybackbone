package com.estefano.deliverybackbone.orders;

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
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
public class Order {

    public enum Status { PENDING_PAYMENT, PAID, PREPARING, IN_TRANSIT, DELIVERED, CANCELLED, EXPIRED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Enumerated(EnumType.STRING)
    private Status status = Status.PENDING_PAYMENT;

    private BigDecimal total;

    @Column(name = "delivery_lat")
    private double deliveryLat;

    @Column(name = "delivery_lng")
    private double deliveryLng;

    @Column(name = "delivery_address")
    private String deliveryAddress;

    private String district;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "paid_at")
    private OffsetDateTime paidAt;

    @Column(name = "delivered_at")
    private OffsetDateTime deliveredAt;

    public Order(Long userId, BigDecimal total, double deliveryLat, double deliveryLng,
                 String deliveryAddress, String district) {
        this.userId = userId;
        this.total = total;
        this.deliveryLat = deliveryLat;
        this.deliveryLng = deliveryLng;
        this.deliveryAddress = deliveryAddress;
        this.district = district;
    }
}
