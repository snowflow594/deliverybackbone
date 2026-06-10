package com.estefano.deliverybackbone.orders;

import com.estefano.deliverybackbone.TestcontainersConfiguration;
import com.estefano.deliverybackbone.common.ConflictException;
import com.estefano.deliverybackbone.common.InsufficientStockException;
import com.estefano.deliverybackbone.orders.OrderDtos.CheckoutItem;
import com.estefano.deliverybackbone.orders.OrderDtos.CheckoutRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class CheckoutFlowTest {

    @Autowired
    private CheckoutService checkoutService;

    @Autowired
    private JdbcTemplate jdbc;

    private long userId;

    @BeforeEach
    void setUp() {
        userId = jdbc.queryForObject(
                "SELECT id FROM users WHERE role = 'CUSTOMER' LIMIT 1", Long.class);
    }

    /** Cada test usa un producto propio para no interferir con los demás. */
    private long newProduct(String sku, BigDecimal price, int stock) {
        return jdbc.queryForObject("""
                INSERT INTO products (category_id, sku, name, price, stock_total)
                VALUES (1, ?, 'Producto Test', ?, ?) RETURNING id
                """, Long.class, sku, price, stock);
    }

    private CheckoutRequest requestFor(long productId, int quantity) {
        return new CheckoutRequest(userId, List.of(new CheckoutItem(productId, quantity)),
                -12.0931, -77.0465, "Av. Larco 123, Miraflores", "Miraflores");
    }

    @Test
    void checkoutReservesStockAndFreezesPrice() {
        long productId = newProduct("TST-001", new BigDecimal("10.50"), 20);

        var order = checkoutService.checkout(requestFor(productId, 3));

        assertThat(order.status()).isEqualTo(Order.Status.PENDING_PAYMENT);
        assertThat(order.total()).isEqualByComparingTo("31.50");
        assertThat(order.items()).singleElement()
                .satisfies(item -> assertThat(item.unitPrice()).isEqualByComparingTo("10.50"));

        // Reservado, pero el total no cambia hasta el pago.
        Integer reserved = jdbc.queryForObject(
                "SELECT stock_reserved FROM products WHERE id = ?", Integer.class, productId);
        Integer total = jdbc.queryForObject(
                "SELECT stock_total FROM products WHERE id = ?", Integer.class, productId);
        assertThat(reserved).isEqualTo(3);
        assertThat(total).isEqualTo(20);

        Integer activeReservations = jdbc.queryForObject(
                "SELECT COUNT(*) FROM stock_reservations WHERE order_id = ? AND status = 'ACTIVE'",
                Integer.class, order.id());
        assertThat(activeReservations).isEqualTo(1);
    }

    @Test
    void checkoutFailsAndRollsBackWhenStockIsInsufficient() {
        long withStock = newProduct("TST-002", new BigDecimal("5.00"), 10);
        long without = newProduct("TST-003", new BigDecimal("5.00"), 2);

        var request = new CheckoutRequest(userId,
                List.of(new CheckoutItem(withStock, 5), new CheckoutItem(without, 3)),
                -12.0931, -77.0465, "Av. Larco 123, Miraflores", "Miraflores");

        assertThatThrownBy(() -> checkoutService.checkout(request))
                .isInstanceOf(InsufficientStockException.class);

        // La reserva del primer item también se revirtió (rollback completo).
        Integer reserved = jdbc.queryForObject(
                "SELECT stock_reserved FROM products WHERE id = ?", Integer.class, withStock);
        assertThat(reserved).isZero();
    }

    @Test
    void payConfirmsSaleDecrementsStockAndRecordsMovement() {
        long productId = newProduct("TST-004", new BigDecimal("8.00"), 15);
        var order = checkoutService.checkout(requestFor(productId, 4));

        var paid = checkoutService.pay(order.id());

        assertThat(paid.status()).isEqualTo(Order.Status.PAID);
        assertThat(paid.paidAt()).isNotNull();

        Integer total = jdbc.queryForObject(
                "SELECT stock_total FROM products WHERE id = ?", Integer.class, productId);
        Integer reserved = jdbc.queryForObject(
                "SELECT stock_reserved FROM products WHERE id = ?", Integer.class, productId);
        assertThat(total).isEqualTo(11);
        assertThat(reserved).isZero();

        Integer saleMovements = jdbc.queryForObject("""
                SELECT COUNT(*) FROM stock_movements
                 WHERE product_id = ? AND reason = 'SALE' AND delta = -4
                """, Integer.class, productId);
        assertThat(saleMovements).isEqualTo(1);
    }

    @Test
    void payingTwiceIsRejected() {
        long productId = newProduct("TST-005", new BigDecimal("8.00"), 15);
        var order = checkoutService.checkout(requestFor(productId, 1));
        checkoutService.pay(order.id());

        assertThatThrownBy(() -> checkoutService.pay(order.id()))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void expiryJobReleasesStockExpiresOrderAndBlocksLatePayment() {
        long productId = newProduct("TST-006", new BigDecimal("8.00"), 15);
        var order = checkoutService.checkout(requestFor(productId, 5));

        // Simular el paso del tiempo: la reserva ya venció.
        jdbc.update("UPDATE stock_reservations SET expires_at = now() - INTERVAL '1 minute' WHERE order_id = ?",
                order.id());

        int expired = checkoutService.expireOverdueOrders();
        assertThat(expired).isEqualTo(1);

        Integer reserved = jdbc.queryForObject(
                "SELECT stock_reserved FROM products WHERE id = ?", Integer.class, productId);
        assertThat(reserved).isZero();

        String orderStatus = jdbc.queryForObject(
                "SELECT status FROM orders WHERE id = ?", String.class, order.id());
        assertThat(orderStatus).isEqualTo("EXPIRED");

        String reservationStatus = jdbc.queryForObject(
                "SELECT status FROM stock_reservations WHERE order_id = ?", String.class, order.id());
        assertThat(reservationStatus).isEqualTo("EXPIRED");

        Integer expiredMovements = jdbc.queryForObject("""
                SELECT COUNT(*) FROM stock_movements
                 WHERE product_id = ? AND reason = 'RESERVATION_EXPIRED' AND delta = 5
                """, Integer.class, productId);
        assertThat(expiredMovements).isEqualTo(1);

        // Pagar una orden expirada debe fallar.
        assertThatThrownBy(() -> checkoutService.pay(order.id()))
                .isInstanceOf(ConflictException.class);
    }
}
