package com.estefano.deliverybackbone.orders;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByUserIdOrderByCreatedAtDesc(long userId);

    /** El módulo de usuarios/auth llega en fases posteriores; por ahora solo validamos existencia. */
    @Query(value = "SELECT EXISTS(SELECT 1 FROM users WHERE id = :userId)", nativeQuery = true)
    boolean userExists(@Param("userId") long userId);

    /** Transición condicional PENDING_PAYMENT → PAID (idempotencia del pago). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE orders SET status = 'PAID', paid_at = now()
             WHERE id = :id AND status = 'PENDING_PAYMENT'
            """, nativeQuery = true)
    int markPaid(@Param("id") long id);

    /** Transición condicional PENDING_PAYMENT → EXPIRED (job de expiración). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE orders SET status = 'EXPIRED'
             WHERE id = :id AND status = 'PENDING_PAYMENT'
            """, nativeQuery = true)
    int markExpired(@Param("id") long id);
}
