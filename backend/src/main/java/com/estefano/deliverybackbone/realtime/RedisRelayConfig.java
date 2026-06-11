package com.estefano.deliverybackbone.realtime;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.nio.charset.StandardCharsets;

/**
 * Suscriptor Redis → STOMP: todo lo publicado en channel:inventory se
 * reenvía a /topic/inventory para los navegadores conectados.
 */
@Configuration
public class RedisRelayConfig {

    @Bean
    RedisMessageListenerContainer inventoryRelayContainer(RedisConnectionFactory connectionFactory,
                                                          SimpMessagingTemplate messaging) {
        var container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(
                (message, pattern) -> messaging.convertAndSend("/topic/inventory",
                        new String(message.getBody(), StandardCharsets.UTF_8)),
                new ChannelTopic(RedisChannels.INVENTORY));
        return container;
    }
}
