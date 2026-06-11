package com.estefano.deliverybackbone.inventory;

/**
 * Evento interno de dominio: el stock de un producto cambió.
 * El módulo realtime lo escucha DESPUÉS del commit y lo propaga
 * a Redis pub/sub → STOMP (ver ARQUITECTURA.md §3.1 paso 3).
 */
public record StockChangedEvent(long productId, String reason) {
}
