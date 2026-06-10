package com.estefano.deliverybackbone.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;

public interface StockReservationRepository extends JpaRepository<StockReservation, Long> {

    List<StockReservation> findByOrderId(long orderId);

    @Query("SELECT r FROM StockReservation r WHERE r.status = 'ACTIVE' AND r.expiresAt < :now")
    List<StockReservation> findOverdue(@Param("now") OffsetDateTime now);

    /** Transición condicional: solo confirma reservas ACTIVE aún vigentes. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE stock_reservations
               SET status = 'CONFIRMED'
             WHERE id = :id AND status = 'ACTIVE' AND expires_at > now()
            """, nativeQuery = true)
    int markConfirmed(@Param("id") long id);

    /** Transición condicional: evita la carrera entre el job de expiración y el pago. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE stock_reservations
               SET status = 'EXPIRED'
             WHERE id = :id AND status = 'ACTIVE'
            """, nativeQuery = true)
    int markExpired(@Param("id") long id);
}
