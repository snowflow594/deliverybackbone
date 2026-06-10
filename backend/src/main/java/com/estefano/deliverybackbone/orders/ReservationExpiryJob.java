package com.estefano.deliverybackbone.orders;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Libera stock de reservas vencidas y expira sus órdenes (ARQUITECTURA.md §3.1 paso 5). */
@Component
@Slf4j
public class ReservationExpiryJob {

    private final CheckoutService checkoutService;

    public ReservationExpiryJob(CheckoutService checkoutService) {
        this.checkoutService = checkoutService;
    }

    @Scheduled(fixedDelayString = "${app.reservation-sweep-ms:30000}")
    public void expireOverdueReservations() {
        int expired = checkoutService.expireOverdueOrders();
        if (expired > 0) {
            log.info("Reservas vencidas procesadas: {} órdenes expiradas", expired);
        }
    }
}
