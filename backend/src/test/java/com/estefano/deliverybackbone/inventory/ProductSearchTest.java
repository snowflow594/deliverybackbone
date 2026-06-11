package com.estefano.deliverybackbone.inventory;

import com.estefano.deliverybackbone.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regresión del bug "function lower(bytea) does not exist": GET /api/products
 * sin filtros pasaba null a JPQL y Hibernate 6 lo bindeaba como bytea en Postgres.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ProductSearchTest {

    @Autowired
    private InventoryService inventory;

    @Test
    void searchWithoutFiltersReturnsActiveCatalog() {
        var all = inventory.search(null, null);

        assertThat(all).isNotEmpty();
        assertThat(all).allSatisfy(p -> assertThat(p.isActive()).isTrue());
    }

    @Test
    void searchByCategoryFilters() {
        var bebidas = inventory.search(2L, null);

        assertThat(bebidas).isNotEmpty();
        assertThat(bebidas).allSatisfy(p -> assertThat(p.getCategoryId()).isEqualTo(2L));
    }

    @Test
    void searchByNameIsCaseInsensitive() {
        var result = inventory.search(null, "inca kola");

        assertThat(result).anySatisfy(p -> assertThat(p.getName()).contains("Inca Kola"));
    }
}
