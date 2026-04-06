CREATE TABLE IF NOT EXISTS store_products (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    name VARCHAR(120) NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    image_url TEXT NOT NULL DEFAULT '',
    price NUMERIC(36, 8) NOT NULL DEFAULT 0,
    stock_current INTEGER NOT NULL DEFAULT 0,
    stock_total INTEGER NOT NULL DEFAULT 0,
    visible BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_store_products_user_id
    ON store_products (user_id);

CREATE INDEX IF NOT EXISTS idx_store_products_user_id_sort_order
    ON store_products (user_id, sort_order ASC, id DESC);
