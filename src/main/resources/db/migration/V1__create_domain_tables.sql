-- Tabelas de dominio do mechanical-hub-stock (data_model.md 3).
-- service_id e service_order_id referenciam o servico orders: sem FK.

CREATE TABLE materials (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name               VARCHAR(255) NOT NULL,
    description        TEXT,
    min_stock_quantity INTEGER      NOT NULL DEFAULT 0 CHECK (min_stock_quantity >= 0),
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at         TIMESTAMPTZ
);

CREATE TABLE stock (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    material_id UUID        NOT NULL REFERENCES materials (id) ON DELETE RESTRICT,
    status      VARCHAR(20) NOT NULL CHECK (status IN ('AVAILABLE', 'RESERVED')),
    quantity    INTEGER     NOT NULL DEFAULT 0 CHECK (quantity >= 0),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_stock_material_status UNIQUE (material_id, status)
);

CREATE TABLE service_materials (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    service_id  UUID    NOT NULL,
    material_id UUID    NOT NULL REFERENCES materials (id) ON DELETE RESTRICT,
    quantity    INTEGER NOT NULL CHECK (quantity > 0),
    CONSTRAINT uk_service_materials_service_material UNIQUE (service_id, material_id)
);

COMMENT ON COLUMN service_materials.service_id IS 'repairId (ref:orders), sem FK';

CREATE TABLE stock_movements (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    material_id      UUID        NOT NULL REFERENCES materials (id) ON DELETE RESTRICT,
    service_order_id UUID,
    movement_type    VARCHAR(20) NOT NULL CHECK (movement_type IN ('ENTRY', 'RESERVED', 'RETURN', 'EXCLUDED', 'OUT')),
    quantity         INTEGER     NOT NULL CHECK (quantity > 0),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON COLUMN stock_movements.service_order_id IS 'orderId (ref:orders), sem FK; NULL em ENTRY';

CREATE INDEX idx_stock_movements_material ON stock_movements (material_id, created_at);
CREATE INDEX idx_stock_movements_order ON stock_movements (service_order_id) WHERE service_order_id IS NOT NULL;

CREATE TABLE stock_pending_items (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    service_order_id    UUID        NOT NULL,
    saga_transaction_id UUID        NOT NULL,
    material_id         UUID        NOT NULL REFERENCES materials (id) ON DELETE RESTRICT,
    quantity            INTEGER     NOT NULL CHECK (quantity > 0),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON COLUMN stock_pending_items.created_at IS 'Prioridade FIFO (created_at, id)';

CREATE INDEX idx_stock_pending_items_material_fifo ON stock_pending_items (material_id, created_at, id);
CREATE INDEX idx_stock_pending_items_order ON stock_pending_items (service_order_id);
