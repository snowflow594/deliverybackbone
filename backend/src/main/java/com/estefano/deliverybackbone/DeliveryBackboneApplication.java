package com.estefano.deliverybackbone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DeliveryBackboneApplication {

    public static void main(String[] args) {
        SpringApplication.run(DeliveryBackboneApplication.class, args);
    }
}
