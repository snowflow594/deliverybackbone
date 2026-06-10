package com.estefano.deliverybackbone.common;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI deliveryBackboneOpenApi() {
        return new OpenAPI().info(new Info()
                .title("DeliveryBackbone API")
                .description("""
                        Backend de delivery: inventario concurrente en tiempo real (checkout \
                        con reserva de stock atómica, cero sobreventa), tracking de couriers \
                        y analítica. Las reservas sin pagar expiran a los 10 minutos.""")
                .version("0.1.0"));
    }
}
