-- DeliveryBackbone — esquema inicial (ver ARQUITECTURA.md §2)

-- ============ Catálogo e inventario ============

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
    -- Stock vive en la misma fila para simplificar el lock.
    -- Stock disponible (derivado, nunca almacenado): stock_total - stock_reserved
    stock_total     INT NOT NULL DEFAULT 0 CHECK (stock_total >= 0),
    stock_reserved  INT NOT NULL DEFAULT 0 CHECK (stock_reserved >= 0),
    version     BIGINT NOT NULL DEFAULT 0,   -- optimistic locking (@Version en JPA)
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT reserved_lte_total CHECK (stock_reserved <= stock_total)
);

-- ============ Usuarios y pedidos ============

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
    delivery_lat  DOUBLE PRECISION NOT NULL,
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

-- ============ Reservas y movimientos de stock ============

CREATE TABLE stock_reservations (
    id          BIGSERIAL PRIMARY KEY,
    product_id  BIGINT NOT NULL REFERENCES products(id),
    order_id    BIGINT NOT NULL REFERENCES orders(id),
    quantity    INT NOT NULL CHECK (quantity > 0),
    status      VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
                -- ACTIVE | CONFIRMED | EXPIRED | RELEASED
    expires_at  TIMESTAMPTZ NOT NULL,
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

-- ============ Motorizados y tracking ============

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

-- Histórico de posiciones: UNA muestra cada ~30 s por courier.
-- La posición "en vivo" (cada 2-3 s) vive solo en Redis.
CREATE TABLE courier_locations (
    id          BIGSERIAL PRIMARY KEY,
    courier_id  BIGINT NOT NULL REFERENCES couriers(id),
    lat         DOUBLE PRECISION NOT NULL,
    lng         DOUBLE PRECISION NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_locations_courier_time ON courier_locations (courier_id, recorded_at);
