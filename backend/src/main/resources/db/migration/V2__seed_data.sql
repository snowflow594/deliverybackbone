-- Datos de demo para desarrollo local.
-- Contraseña de ambos usuarios: "password" (hash bcrypt de ejemplo de la doc de Spring Security)

INSERT INTO users (email, password_hash, full_name, role) VALUES
('admin@deliverybackbone.dev',   '$2a$10$GRLdNijSQMUvl/au9ofL.eDwmoohzzS7.rmNSJZ.0FxO/BTk76klW', 'Admin Demo',   'ADMIN'),
('cliente@deliverybackbone.dev', '$2a$10$GRLdNijSQMUvl/au9ofL.eDwmoohzzS7.rmNSJZ.0FxO/BTk76klW', 'Cliente Demo', 'CUSTOMER');

INSERT INTO categories (name) VALUES
('Abarrotes'), ('Bebidas'), ('Snacks'), ('Limpieza'), ('Cuidado personal');

INSERT INTO products (category_id, sku, name, price, stock_total) VALUES
(1, 'ABA-001', 'Arroz Costeño 5kg',            24.90, 100),
(1, 'ABA-002', 'Aceite Primor 1L',             12.50,  80),
(1, 'ABA-003', 'Azúcar Rubia Cartavio 1kg',     4.80, 120),
(2, 'BEB-001', 'Inca Kola 1.5L',                7.50, 150),
(2, 'BEB-002', 'Agua San Luis 2.5L',            4.20, 200),
(2, 'BEB-003', 'Cerveza Cusqueña six-pack',    32.90,  60),
(3, 'SNK-001', 'Papas Lays Clásicas 145g',      7.90,  90),
(3, 'SNK-002', 'Chocolate Sublime 30g',         2.50, 300),
(3, 'SNK-003', 'Galletas Casino fresa 6-pack',  4.50, 110),
(4, 'LIM-001', 'Detergente Bolívar 2kg',       21.90,  70),
(4, 'LIM-002', 'Lejía Clorox 1L',               5.90,  85),
(5, 'CUI-001', 'Shampoo H&S 375ml',            18.90,  45);

INSERT INTO couriers (full_name, phone, status) VALUES
('Jorge Ramírez',  '+51 999 111 222', 'OFFLINE'),
('Lucía Torres',   '+51 999 333 444', 'OFFLINE'),
('Miguel Castillo','+51 999 555 666', 'OFFLINE');
