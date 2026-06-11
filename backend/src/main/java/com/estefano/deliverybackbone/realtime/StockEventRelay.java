package com.estefano.deliverybackbone.realtime;

import com.estefano.deliverybackbone.inventory.ProductRepository;
import com.estefano.deliverybackbone.inventory.StockChangedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Publica cambios de stock a Redis pub/sub DESPUÉS del commit: si la
 * transacción del checkout hace rollback, no se emite nada.
 * Redis desacopla el dominio del tiempo real y permite escalar a varias
 * instancias del backend (todas reciben el evento y lo reenvían a sus clientes).
 */
@Component
@Slf4j
public class StockEventRelay {

    private final ProductRepository products;
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    public StockEventRelay(ProductRepository products, StringRedisTemplate redis, ObjectMapper mapper) {
        this.products = products;
        this.redis = redis;
        this.mapper = mapper;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStockChanged(StockChangedEvent event) {
        products.findById(event.productId()).ifPresent(product -> {
            try {
                String payload = mapper.writeValueAsString(Map.of(
                        "productId", product.getId(),
                        "stockAvailable", product.getStockAvailable(),
                        "reason", event.reason(),
                        "at", OffsetDateTime.now().toString()));
                redis.convertAndSend(RedisChannels.INVENTORY, payload);
            } catch (Exception e) {
                // El tiempo real es best-effort: nunca debe romper el flujo de negocio.
                log.warn("No se pudo publicar evento de stock del producto {}: {}",
                        event.productId(), e.getMessage());
            }
        });
    }
}
