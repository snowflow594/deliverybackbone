# DeliveryBackbone

Backend de delivery con inventario concurrente en tiempo real, tracking de couriers y analítica.

- Arquitectura completa: ver `ARQUITECTURA.md` (fuente de verdad — no desviar sin actualizar).
- Stack: Spring Boot 3.5 / Java 21, PostgreSQL 16, Redis 7, React+Vite, Docker Compose.

## Reglas

- Stock disponible SIEMPRE derivado (`stock_total - stock_reserved`), nunca almacenado.
- Todo cambio de stock registra una fila en `stock_movements`.
- Migraciones solo vía Flyway (`backend/src/main/resources/db/migration`). Nunca `ddl-auto: update`.
- Tests de integración con Testcontainers.
- Monolito modular: paquetes `inventory`, `orders`, `tracking`, `analytics`, `realtime`, `common`. Sin dependencias circulares entre módulos.

## Comandos

- Infraestructura: `docker compose up -d` (Postgres en 5434, Redis en 6379)
- Backend: `cd backend && ./mvnw spring-boot:run` (Windows: `mvnw.cmd`)
- Tests: `cd backend && ./mvnw test` (requiere Docker corriendo, usa Testcontainers)
- Frontend (desde Fase 2): `cd frontend && npm run dev`

## Estado

- [x] Fase 0: esqueleto + Docker Compose + migraciones Flyway
- [x] Fase 1: inventory + orders, checkout con reservas, LoadSimulator, tests de concurrencia
- [ ] Fase 2: WebSocket/STOMP + Redis pub/sub + catálogo React
- [ ] Fase 3: tracking + CourierSimulator + mapa Leaflet
- [ ] Fase 4: analytics + dashboards Recharts
- [ ] Fase 5: deploy + README con GIFs
