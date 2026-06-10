# Simulator

Herramientas CLI de simulación (ver `ARQUITECTURA.md` §4). Sin dependencias: archivos únicos ejecutables con Java 21+.

## LoadSimulator — prueba de concurrencia en vivo

Lanza N compradores virtuales que hacen checkout del **mismo producto a la vez** y verifica el invariante de cero sobreventa contra el backend real.

```bash
# 1. Backend corriendo (docker compose up -d && cd backend && mvnw spring-boot:run)
# 2. Desde esta carpeta:
java LoadSimulator.java                    # 50 compradores, qty 1, producto 1
java LoadSimulator.java 200 1 4            # 200 compradores compiten por el producto 4
java LoadSimulator.java 100 2 1 2 http://localhost:8080   # todos los parámetros
```

Parámetros (posicionales, todos opcionales): `compradores` `qtyPorCompra` `productId` `userId` `baseUrl`.

Salida esperada: exactamente `stock disponible` compras con 201, el resto rechazadas con 409, e

```
INVARIANTE OK: 100 == 100 (reservado) + 0 (disponible) — CERO SOBREVENTA
```

> Las compras quedan en `PENDING_PAYMENT`; si no se pagan, el job de expiración
> libera el stock reservado a los 10 minutos (configurable en `application.yml`).

## CourierSimulator (Fase 3)

Pendiente: motorizados virtuales recorriendo rutas de Lima reportando GPS cada 2-3 s.
