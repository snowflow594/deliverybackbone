package com.estefano.deliverybackbone.orders;

import com.estefano.deliverybackbone.TestcontainersConfiguration;
import com.estefano.deliverybackbone.common.InsufficientStockException;
import com.estefano.deliverybackbone.orders.OrderDtos.CheckoutItem;
import com.estefano.deliverybackbone.orders.OrderDtos.CheckoutRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prueba demostrable de concurrencia (Fase 1): N compradores simultáneos
 * compiten por stock limitado y NUNCA hay sobreventa.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class CheckoutConcurrencyTest {

    private static final int STOCK = 10;
    private static final int BUYERS = 50;

    @Autowired
    private CheckoutService checkoutService;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void concurrentCheckoutsNeverOversell() throws InterruptedException {
        long userId = jdbc.queryForObject(
                "SELECT id FROM users WHERE role = 'CUSTOMER' LIMIT 1", Long.class);
        long productId = jdbc.queryForObject("""
                INSERT INTO products (category_id, sku, name, price, stock_total)
                VALUES (1, 'CONC-001', 'Producto Disputado', 9.90, ?) RETURNING id
                """, Long.class, STOCK);

        var successes = new AtomicInteger();
        var rejections = new AtomicInteger();
        var unexpectedErrors = new AtomicInteger();
        var startGun = new CountDownLatch(1);
        var done = new CountDownLatch(BUYERS);

        ExecutorService pool = Executors.newFixedThreadPool(BUYERS);
        for (int i = 0; i < BUYERS; i++) {
            pool.submit(() -> {
                try {
                    startGun.await();
                    checkoutService.checkout(new CheckoutRequest(userId,
                            List.of(new CheckoutItem(productId, 1)),
                            -12.09, -77.04, "Calle Test 123", "Surco"));
                    successes.incrementAndGet();
                } catch (InsufficientStockException e) {
                    rejections.incrementAndGet();
                } catch (Exception e) {
                    unexpectedErrors.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }

        startGun.countDown();   // todos los compradores disparan a la vez
        assertThat(done.await(60, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();

        // Exactamente STOCK compras exitosas; el resto rechazadas con 409. Cero sobreventa.
        assertThat(unexpectedErrors.get()).isZero();
        assertThat(successes.get()).isEqualTo(STOCK);
        assertThat(rejections.get()).isEqualTo(BUYERS - STOCK);

        Integer reserved = jdbc.queryForObject(
                "SELECT stock_reserved FROM products WHERE id = ?", Integer.class, productId);
        Integer total = jdbc.queryForObject(
                "SELECT stock_total FROM products WHERE id = ?", Integer.class, productId);
        assertThat(reserved).isEqualTo(STOCK);
        assertThat(total).isEqualTo(STOCK);

        // Invariante de la BD: nunca reserved > total (CHECK constraint + lógica).
        Integer violations = jdbc.queryForObject(
                "SELECT COUNT(*) FROM products WHERE stock_reserved > stock_total", Integer.class);
        assertThat(violations).isZero();

        // Una orden PENDING_PAYMENT por compra exitosa, con su reserva ACTIVE.
        Integer pendingOrders = jdbc.queryForObject("""
                SELECT COUNT(*) FROM orders o
                 WHERE o.status = 'PENDING_PAYMENT'
                   AND EXISTS (SELECT 1 FROM order_items i
                                WHERE i.order_id = o.id AND i.product_id = ?)
                """, Integer.class, productId);
        assertThat(pendingOrders).isEqualTo(STOCK);

        Integer activeReservations = jdbc.queryForObject(
                "SELECT COUNT(*) FROM stock_reservations WHERE product_id = ? AND status = 'ACTIVE'",
                Integer.class, productId);
        assertThat(activeReservations).isEqualTo(STOCK);
    }
}
