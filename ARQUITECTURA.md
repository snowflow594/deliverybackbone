# DeliveryBackbone — Sistema de respaldo para app de compras por delivery

Documento de arquitectura y modelo de datos. Pensado para usarse como base de contexto en Claude Code (puede referenciarse desde `CLAUDE.md`).

---

## 1. Visión general

Sistema backend (con frontend de demostración) para una app de delivery que resuelve tres problemas centrales:

1. **Inventario en tiempo real** con manejo correcto de concurrencia (múltiples compradores simultáneos).
2. **Monitoreo en vivo de motorizados** (posición GPS de alta frecuencia).
3. **Estadísticas y dashboards** para la toma de decisiones del negocio.

### Stack

| Capa | Tecnología | Justificación |
|---|---|---|
| Backend | Spring Boot 3 (Java 21) | Transacciones robustas, WebSocket/STOMP integrado |
| Base de datos | PostgreSQL 16 | ACID, locks a nivel de fila, agregaciones para analítica |
| Estado en vivo / caché | Redis 7 | Posiciones GPS, pub/sub, TTL para reservas |
| Frontend | React + Vite | SPA de demo: catálogo, mapa, dashboards |
| Mapa | Leaflet + OpenStreetMap | Gratuito, sin API key |
| Gráficos | Recharts | Dashboards rápidos en React |
| Infraestructura | Docker Compose | Levantar todo con un comando |
| Simulación | Scripts Java/Node (módulo `simulator`) | Carga concurrente + motorizados virtuales |

### Decisión clave: monolito modular

NO usar microservicios. Un monolito Spring Boot organizado por módulos de dominio (`inventory`, `orders`, `tracking`, `analytics`) es más realista para el alcance, más fácil de demostrar, y la separación por paquetes permite explicar en entrevista cómo se extraería un módulo a servicio independiente si escalara.

```
com.estefano.deliverybackbone
├── inventory/      (productos, stock, reservas, movimientos)
├── orders/         (pedidos, checkout, estados)
├── tracking/       (motorizados, posiciones, asignación)
├── analytics/      (queries de agregación, endpoints de dashboard)
├── realtime/       (configuración WebSocket/STOMP, publishers)
└── common/         (excepciones, seguridad, config)
```

---

## 2. Modelo de datos (PostgreSQL)

### Diagrama entidad-relación (resumen)

```
users ──< orders ──< order_items >── products >── categories
                │                        │
                │                   stock_movements
                │                        │
                ├──< stock_reservations ─┘
                │
                └──< deliveries >── couriers ──< courier_locations
```

### 2.1 Catálogo e inventario

```sql
CREATE TABLE categories (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(80) NOT NULL UNIQUE
);

CREATE TABLE products (
    id          BIGSERIAL PRIMARY KEY,
    category_id BIGINT NOT NULL REFERENCES categories(id),
    sku         VARCHAR(40) NOT NULL UNIQUE,
    name        VARCHAR(150) NOT NULL,
    price       NUMERIC(10,2) NOT NULL CHECK (price >= 0),
    active      BOOLEAN NOT NULL DEFAULT TRUE,
    -- Stock vive en la misma fila para simplificar el lock:
    stock_total     INT NOT NULL DEFAULT 0 CHECK (stock_total >= 0),
    stock_reserved  INT NOT NULL DEFAULT 0 CHECK (stock_reserved >= 0),
    version     BIGINT NOT NULL DEFAULT 0,   -- optimistic locking (@Version en JPA)
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT reserved_lte_total CHECK (stock_reserved <= stock_total)
);
-- Stock disponible (derivado, nunca almacenado): stock_total - stock_reserved
```

**Concepto central:** el stock disponible nunca se guarda como columna; siempre se calcula como `stock_total - stock_reserved`. Esto elimina una clase entera de bugs de inconsistencia.

```sql
CREATE TABLE stock_reservations (
    id          BIGSERIAL PRIMARY KEY,
    product_id  BIGINT NOT NULL REFERENCES products(id),
    order_id    BIGINT NOT NULL REFERENCES orders(id),
    quantity    INT NOT NULL CHECK (quantity > 0),
    status      VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
                -- ACTIVE | CONFIRMED | EXPIRED | RELEASED
    expires_at  TIMESTAMPTZ NOT NULL,        -- p. ej. now() + 10 min
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_reservations_expiry ON stock_reservations (status, expires_at);

CREATE TABLE stock_movements (              -- auditoría: todo cambio de stock queda registrado
    id          BIGSERIAL PRIMARY KEY,
    product_id  BIGINT NOT NULL REFERENCES products(id),
    delta       INT NOT NULL,               -- +50 reposición, -2 venta
    reason      VARCHAR(30) NOT NULL,       -- RESTOCK | SALE | RESERVATION_EXPIRED | ADJUSTMENT
    reference_id BIGINT,                    -- order_id o reservation_id según reason
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_movements_product_time ON stock_movements (product_id, created_at);
```

### 2.2 Usuarios y pedidos

```sql
CREATE TABLE users (
    id          BIGSERIAL PRIMARY KEY,
    email       VARCHAR(120) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    full_name   VARCHAR(120) NOT NULL,
    role        VARCHAR(20) NOT NULL DEFAULT 'CUSTOMER',  -- CUSTOMER | ADMIN
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE orders (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users(id),
    status      VARCHAR(25) NOT NULL DEFAULT 'PENDING_PAYMENT',
                -- PENDING_PAYMENT | PAID | PREPARING | IN_TRANSIT | DELIVERED | CANCELLED | EXPIRED
    total       NUMERIC(10,2) NOT NULL,
    delivery_lat  DOUBLE PRECISION NOT NULL,   -- destino de entrega
    delivery_lng  DOUBLE PRECISION NOT NULL,
    delivery_address VARCHAR(250) NOT NULL,
    district    VARCHAR(60),                   -- para mapa de calor por distrito
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    paid_at     TIMESTAMPTZ,
    delivered_at TIMESTAMPTZ
);
CREATE INDEX idx_orders_status ON orders (status);
CREATE INDEX idx_orders_created ON orders (created_at);

CREATE TABLE order_items (
    id          BIGSERIAL PRIMARY KEY,
    order_id    BIGINT NOT NULL REFERENCES orders(id),
    product_id  BIGINT NOT NULL REFERENCES products(id),
    quantity    INT NOT NULL CHECK (quantity > 0),
    unit_price  NUMERIC(10,2) NOT NULL          -- precio congelado al momento de la compra
);
```

### 2.3 Motorizados y tracking

```sql
CREATE TABLE couriers (
    id          BIGSERIAL PRIMARY KEY,
    full_name   VARCHAR(120) NOT NULL,
    phone       VARCHAR(20),
    status      VARCHAR(20) NOT NULL DEFAULT 'OFFLINE',
                -- OFFLINE | AVAILABLE | ASSIGNED | DELIVERING
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE deliveries (
    id          BIGSERIAL PRIMARY KEY,
    order_id    BIGINT NOT NULL UNIQUE REFERENCES orders(id),
    courier_id  BIGINT NOT NULL REFERENCES couriers(id),
    status      VARCHAR(20) NOT NULL DEFAULT 'ASSIGNED',
                -- ASSIGNED | PICKED_UP | DELIVERED | FAILED
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    picked_up_at TIMESTAMPTZ,
    delivered_at TIMESTAMPTZ
);

-- Histórico de posiciones: se persiste UNA muestra cada ~30 s por courier.
-- La posición "en vivo" (cada 2-3 s) vive solo en Redis.
CREATE TABLE courier_locations (
    id          BIGSERIAL PRIMARY KEY,
    courier_id  BIGINT NOT NULL REFERENCES couriers(id),
    lat         DOUBLE PRECISION NOT NULL,
    lng         DOUBLE PRECISION NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_locations_courier_time ON courier_locations (courier_id, recorded_at);
```

### 2.4 Estructuras en Redis

| Clave | Tipo | Contenido | TTL |
|---|---|---|---|
| `courier:pos:{courierId}` | Hash | `lat`, `lng`, `status`, `updatedAt` | 60 s (si no reporta, desaparece del mapa) |
| `couriers:active` | Set | IDs de motorizados activos | — |
| `channel:inventory` | Pub/Sub | Eventos de cambio de stock (JSON) | — |
| `channel:couriers` | Pub/Sub | Eventos de posición (JSON) | — |

---

## 3. Arquitectura del sistema

```
                                   ┌──────────────────────────────┐
  ┌────────────┐   REST (HTTP)     │     Spring Boot (monolito)   │
  │  React SPA │◄─────────────────►│                              │
  │ (catálogo, │   WebSocket/STOMP │  inventory ─ orders          │      ┌────────────┐
  │ mapa,      │◄──────────────────│  tracking  ─ analytics       │◄────►│ PostgreSQL │
  │ dashboards)│  /topic/inventory │  realtime (publisher)        │ JPA  └────────────┘
  └────────────┘  /topic/couriers  │                              │
                                   └──────┬───────────────────────┘
  ┌────────────────┐  REST: POST          │ Redis client
  │ Courier        │  /api/couriers/      ▼ (pub/sub + estado vivo)
  │ Simulator      │  {id}/location   ┌────────┐
  ├────────────────┤ ────────────────►│ Redis  │
  │ Load Simulator │  REST: checkout  └────────┘
  │ (N compradores)│  concurrente
  └────────────────┘
```

### 3.1 Flujo de checkout con reserva de stock (el corazón del proyecto)

```
1. POST /api/orders/checkout  {items: [{productId, qty}, ...]}
2. Transacción:
   a. Por cada item: UPDATE products
        SET stock_reserved = stock_reserved + :qty
        WHERE id = :id AND (stock_total - stock_reserved) >= :qty
      → si affected rows = 0 ⇒ SIN STOCK ⇒ rollback ⇒ 409 Conflict
   b. INSERT order (PENDING_PAYMENT) + order_items + stock_reservations (expira en 10 min)
3. Publicar evento a Redis channel:inventory → STOMP /topic/inventory
4. POST /api/orders/{id}/pay (pago simulado):
   - reservación → CONFIRMED, stock_total -= qty, stock_reserved -= qty
   - INSERT stock_movement (SALE)
5. Job programado (@Scheduled, cada 30 s): expira reservas vencidas,
   libera stock_reserved, marca orden EXPIRED, publica evento.
```

**Estrategia de concurrencia (para explicar en entrevista):**
- Defensa principal: `UPDATE ... WHERE` condicional (atómico a nivel de fila en Postgres). No requiere `SELECT FOR UPDATE` y no bloquea lecturas.
- Defensa secundaria: `@Version` (optimistic locking de JPA) para ediciones de producto desde el panel admin.
- Defensa de integridad: `CHECK constraints` en la tabla — aunque el código falle, la BD nunca acepta stock negativo.

### 3.2 Flujo de tracking

```
1. Simulador envía POST /api/couriers/{id}/location cada 2-3 s
2. Backend: escribe en Redis (courier:pos:{id}), publica a channel:couriers
3. RealtimePublisher (suscrito a Redis) reenvía a STOMP /topic/couriers
4. Frontend (mapa Leaflet) mueve los marcadores
5. Cada ~30 s: una muestra se persiste en courier_locations (histórico)
```

### 3.3 Endpoints principales

```
AUTH        POST /api/auth/register | POST /api/auth/login (JWT)
CATÁLOGO    GET  /api/products?category=&search=
CHECKOUT    POST /api/orders/checkout | POST /api/orders/{id}/pay
PEDIDOS     GET  /api/orders/my | GET /api/orders/{id}
ADMIN       POST /api/products | PATCH /api/products/{id}/restock
TRACKING    POST /api/couriers/{id}/location | GET /api/couriers/active
DELIVERY    POST /api/deliveries/assign | PATCH /api/deliveries/{id}/status
ANALYTICS   GET  /api/analytics/sales?from=&to=&groupBy=hour|day
            GET  /api/analytics/top-products?limit=10
            GET  /api/analytics/delivery-times
            GET  /api/analytics/orders-by-district
WEBSOCKET   /ws (SockJS) → /topic/inventory, /topic/couriers, /topic/orders/{id}
```

### 3.4 Módulo de analítica

Queries de agregación directamente en SQL (vistas o `@Query` nativas), por ejemplo:

```sql
-- Ventas por hora del día (para detectar horas pico)
SELECT date_trunc('hour', paid_at) AS hora, COUNT(*) AS pedidos, SUM(total) AS ingresos
FROM orders WHERE status NOT IN ('CANCELLED','EXPIRED') AND paid_at BETWEEN :from AND :to
GROUP BY 1 ORDER BY 1;

-- Tiempo promedio de entrega por distrito
SELECT o.district, AVG(EXTRACT(EPOCH FROM (d.delivered_at - d.assigned_at))/60) AS min_promedio
FROM deliveries d JOIN orders o ON o.id = d.order_id
WHERE d.status = 'DELIVERED' GROUP BY o.district;
```

---

## 4. Módulo de simulación (diferenciador del proyecto)

Proyecto aparte (`/simulator`) con dos herramientas CLI:

1. **LoadSimulator**: lanza N hilos compradores que hacen checkout del mismo producto con stock limitado. Al final imprime: compras exitosas, rechazos por stock, y verifica el invariante `stock vendido + stock restante = stock inicial` (cero sobreventa). Esto es la **prueba demostrable de concurrencia**.
2. **CourierSimulator**: crea M motorizados virtuales que recorren rutas reales de Lima (polilíneas predefinidas entre distritos: Surco, Miraflores, San Borja, etc.) reportando posición cada 2-3 s.

---

## 5. Fases de construcción (roadmap para Claude Code)

| Fase | Entregable demostrable | Contenido |
|---|---|---|
| 0 | `docker compose up` funciona | Esqueleto Spring Boot + Postgres + Redis + migraciones Flyway |
| 1 | Tests de concurrencia en verde | Módulos inventory + orders, checkout con reservas, LoadSimulator, tests de integración (Testcontainers) |
| 2 | Stock cambia en vivo en 2 pestañas | WebSocket/STOMP + Redis pub/sub + frontend catálogo básico |
| 3 | Mapa con motorizados moviéndose | Módulo tracking + CourierSimulator + Leaflet |
| 4 | Dashboard con 4 gráficos | Módulo analytics + Recharts (ventas, top productos, tiempos, distritos) |
| 5 | (Opcional) Deploy + README con GIFs | Render/Railway free tier, documentación con capturas |

### Sugerencias para el CLAUDE.md del repo

```markdown
# DeliveryBackbone
Backend de delivery con inventario concurrente en tiempo real, tracking de couriers y analítica.
- Arquitectura completa: ver ARQUITECTURA.md (fuente de verdad — no desviar sin actualizar)
- Stack: Spring Boot 3 / Java 21, PostgreSQL 16, Redis 7, React+Vite, Docker Compose
- Reglas: stock disponible SIEMPRE derivado (total - reserved); todo cambio de stock
  registra stock_movement; migraciones solo vía Flyway; tests con Testcontainers.
- Comandos: `docker compose up -d` | `./mvnw test` | `npm run dev` (en /frontend)
```

---

## 6. Qué destacar en el CV / entrevista

- Resolución de **condiciones de carrera** en inventario con UPDATE condicional + optimistic locking + constraints (defensa en 3 capas).
- Arquitectura **event-driven interna** con Redis pub/sub desacoplando dominio de tiempo real.
- Manejo de **datos de alta frecuencia** (GPS) separando estado vivo (Redis) de histórico (Postgres).
- **Simulador de carga** que demuestra cero sobreventa bajo concurrencia real.
- SQL analítico con agregaciones y dashboards orientados al negocio.
