package com.estefano.deliverybackbone.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {

    /**
     * Defensa principal contra sobreventa: UPDATE condicional, atómico a nivel
     * de fila en Postgres. Devuelve 0 si no hay stock disponible suficiente.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE products
               SET stock_reserved = stock_reserved + :qty
             WHERE id = :productId
               AND active = TRUE
               AND (stock_total - stock_reserved) >= :qty
            """, nativeQuery = true)
    int tryReserve(@Param("productId") long productId, @Param("qty") int qty);

    /** Confirma una venta: descuenta del total y libera la reserva en una sola sentencia. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE products
               SET stock_total = stock_total - :qty,
                   stock_reserved = stock_reserved - :qty
             WHERE id = :productId
               AND stock_reserved >= :qty
            """, nativeQuery = true)
    int confirmSale(@Param("productId") long productId, @Param("qty") int qty);

    /** Libera stock reservado (reserva expirada o cancelada) sin tocar el total. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE products
               SET stock_reserved = stock_reserved - :qty
             WHERE id = :productId
               AND stock_reserved >= :qty
            """, nativeQuery = true)
    int releaseReserved(@Param("productId") long productId, @Param("qty") int qty);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE products SET stock_total = stock_total + :qty WHERE id = :productId",
            nativeQuery = true)
    int addStock(@Param("productId") long productId, @Param("qty") int qty);

    // Sin parámetros null: el patrón "(:p IS NULL OR ...)" rompe en Hibernate 6 + Postgres
    // (no infiere el tipo del null y lo manda como bytea → "function lower(bytea) does not exist").
    @Query("""
            SELECT p FROM Product p
             WHERE p.active = TRUE
               AND LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%'))
             ORDER BY p.name
            """)
    List<Product> searchByName(@Param("search") String search);

    @Query("""
            SELECT p FROM Product p
             WHERE p.active = TRUE
               AND p.categoryId = :categoryId
               AND LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%'))
             ORDER BY p.name
            """)
    List<Product> searchByCategoryAndName(@Param("categoryId") long categoryId,
                                          @Param("search") String search);
}
