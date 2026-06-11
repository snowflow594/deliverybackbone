package com.estefano.deliverybackbone.realtime;

import com.estefano.deliverybackbone.TestcontainersConfiguration;
import com.estefano.deliverybackbone.orders.CheckoutService;
import com.estefano.deliverybackbone.orders.OrderDtos.CheckoutItem;
import com.estefano.deliverybackbone.orders.OrderDtos.CheckoutRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fase 2: el checkout publica el cambio de stock a Redis pub/sub y el
 * relay lo reenvía a STOMP /topic/inventory (lo que ve el navegador).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class RealtimeStockEventsTest {

    @LocalServerPort
    private int port;

    @Autowired
    private CheckoutService checkoutService;

    @Autowired
    private RedisConnectionFactory redisConnectionFactory;

    @Autowired
    private JdbcTemplate jdbc;

    private long newProduct(String sku, int stock) {
        return jdbc.queryForObject("""
                INSERT INTO products (category_id, sku, name, price, stock_total)
                VALUES (1, ?, 'Producto Realtime', 5.00, ?) RETURNING id
                """, Long.class, sku, stock);
    }

    private void doCheckout(long productId) {
        long userId = jdbc.queryForObject(
                "SELECT id FROM users WHERE role = 'CUSTOMER' LIMIT 1", Long.class);
        checkoutService.checkout(new CheckoutRequest(userId,
                List.of(new CheckoutItem(productId, 2)),
                -12.09, -77.04, "Calle Test 123", "Surco"));
    }

    @Test
    void checkoutPublishesStockEventToRedisChannel() throws Exception {
        long productId = newProduct("RT-001", 30);

        var received = new LinkedBlockingQueue<String>();
        var container = new RedisMessageListenerContainer();
        container.setConnectionFactory(redisConnectionFactory);
        container.addMessageListener(
                (message, pattern) -> received.add(new String(message.getBody(), StandardCharsets.UTF_8)),
                new ChannelTopic(RedisChannels.INVENTORY));
        container.afterPropertiesSet();
        container.start();
        try {
            doCheckout(productId);

            String payload = received.poll(10, TimeUnit.SECONDS);
            assertThat(payload).isNotNull();
            assertThat(payload).contains("\"productId\":" + productId);
            assertThat(payload).contains("\"stockAvailable\":28");
            assertThat(payload).contains("\"reason\":\"RESERVED\"");
        } finally {
            container.stop();
        }
    }

    @Test
    void stompTopicReceivesStockEventAfterCheckout() throws Exception {
        long productId = newProduct("RT-002", 30);

        var stomp = new WebSocketStompClient(new StandardWebSocketClient());
        stomp.setMessageConverter(new StringMessageConverter());
        var session = stomp.connectAsync("ws://localhost:" + port + "/ws",
                new StompSessionHandlerAdapter() {
                }).get(10, TimeUnit.SECONDS);

        BlockingQueue<String> received = new LinkedBlockingQueue<>();
        session.subscribe("/topic/inventory", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return String.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                received.add((String) payload);
            }
        });
        try {
            doCheckout(productId);

            String payload = received.poll(10, TimeUnit.SECONDS);
            assertThat(payload).isNotNull();
            assertThat(payload).contains("\"productId\":" + productId);
            assertThat(payload).contains("\"stockAvailable\":28");
        } finally {
            session.disconnect();
        }
    }
}
