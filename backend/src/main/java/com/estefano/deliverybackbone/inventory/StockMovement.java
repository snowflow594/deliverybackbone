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

/** Auditoría: todo cambio de stock registra una fila aquí (regla del proyecto). */
@Entity
@Table(name = "stock_movements")
@Getter
@NoArgsConstructor
public class StockMovement {

    public enum Reason { RESTOCK, SALE, RESERVATION_EXPIRED, ADJUSTMENT }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id")
    private Long productId;

    /** Positivo = stock que vuelve a estar disponible / reposición; negativo = venta. */
    private int delta;

    @Enumerated(EnumType.STRING)
    private Reason reason;

    /** order_id o reservation_id según reason. */
    @Column(name = "reference_id")
    private Long referenceId;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    public StockMovement(Long productId, int delta, Reason reason, Long referenceId) {
        this.productId = productId;
        this.delta = delta;
        this.reason = reason;
        this.referenceId = referenceId;
    }
}
