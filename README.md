# DeliveryBackbone

Sistema backend para una app de delivery con tres problemas centrales resueltos:

1. **Inventario en tiempo real** con manejo correcto de concurrencia (cero sobreventa bajo carga).
2. **Monitoreo en vivo de motorizados** (GPS de alta frecuencia: Redis para estado vivo, Postgres para histórico).
3. **Estadísticas y dashboards** para decisiones de negocio.

> Arquitectura completa y decisiones de diseño: [ARQUITECTURA.md](ARQUITECTURA.md)

## Stack

| Capa | Tecnología |
|---|---|
| Backend | Spring Boot 3.5 (Java 21), monolito modular |
| Base de datos | PostgreSQL 16 (migraciones con Flyway) |
| Estado en vivo / pub-sub | Redis 7 |
| Frontend | React + Vite (Leaflet + Recharts) |
| Infraestructura | Docker Compose |
| Tests | JUnit 5 + Testcontainers |

## Quick start

```bash
# 1. Infraestructura (Postgres en localhost:5434, Redis en localhost:6379)
docker compose up -d

# 2. Backend (http://localhost:8080)
cd backend
./mvnw spring-boot:run        # Windows: mvnw.cmd spring-boot:run

# 3. Verificar
curl http://localhost:8080/actuator/health
```

## Tests

```bash
cd backend
./mvnw test    # requiere Docker (los tests de integración usan Testcontainers)
```

Incluye `CheckoutConcurrencyTest`: 50 compradores simultáneos compiten por 10 unidades —
exactamente 10 compras exitosas, 40 rechazos con 409, cero sobreventa.

## Prueba de carga en vivo

```bash
cd simulator
java LoadSimulator.java 200 1 4    # 200 compradores reales vía HTTP contra el producto 4
```

Ver [simulator/README.md](simulator/README.md).

## API (Fase 1)

```
CATÁLOGO    GET   /api/products?category=&search=   |  GET /api/products/{id}
ADMIN       POST  /api/products                     |  PATCH /api/products/{id}/restock
CHECKOUT    POST  /api/orders/checkout              |  POST  /api/orders/{id}/pay
PEDIDOS     GET   /api/orders/{id}                  |  GET   /api/orders/my?userId=
```

El checkout reserva stock con UPDATE condicional (atómico en Postgres); las reservas
sin pagar expiran a los 10 minutos y un job programado libera el stock.

## Estructura de módulos

```
com.estefano.deliverybackbone
├── inventory/      productos, stock, reservas, movimientos
├── orders/         pedidos, checkout, estados
├── tracking/       motorizados, posiciones, asignación
├── analytics/      agregaciones SQL, endpoints de dashboard
├── realtime/       WebSocket/STOMP, publishers
└── common/         excepciones, seguridad, config
```

## Roadmap

- [x] **Fase 0** — Esqueleto: Docker Compose + Flyway + smoke tests
- [x] **Fase 1** — Checkout con reserva de stock + LoadSimulator (prueba de concurrencia)
- [ ] **Fase 2** — Stock en vivo: WebSocket/STOMP + Redis pub/sub + catálogo React
- [ ] **Fase 3** — Mapa con motorizados en movimiento (Leaflet + CourierSimulator)
- [ ] **Fase 4** — Dashboards de analítica (Recharts)
- [ ] **Fase 5** — Deploy + documentación con GIFs
