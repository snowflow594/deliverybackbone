package com.estefano.deliverybackbone;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class DeliveryBackboneApplicationTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void contextLoads() {
    }

    @Test
    void flywayMigrationsCreateSchemaAndSeedData() {
        Integer products = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM products WHERE active = TRUE", Integer.class);
        assertThat(products).isEqualTo(12);

        Integer couriers = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM couriers", Integer.class);
        assertThat(couriers).isEqualTo(3);
    }

    @Test
    void productStockInvariantIsEnforcedByDatabase() {
        // La BD debe rechazar stock reservado mayor al total (CHECK reserved_lte_total)
        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class,
                () -> jdbcTemplate.update(
                        "UPDATE products SET stock_reserved = stock_total + 1 WHERE id = 1"));
    }
}
