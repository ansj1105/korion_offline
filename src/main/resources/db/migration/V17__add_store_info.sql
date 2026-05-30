CREATE TABLE IF NOT EXISTS store_info (
    user_id BIGINT PRIMARY KEY,
    store_name VARCHAR(120) NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    address TEXT NOT NULL DEFAULT '',
    contact_phone VARCHAR(40) NOT NULL DEFAULT '',
    business_hours_weekday VARCHAR(40) NOT NULL DEFAULT '',
    business_hours_weekend VARCHAR(40) NOT NULL DEFAULT '',
    business_hours_holiday VARCHAR(40) NOT NULL DEFAULT '',
    category VARCHAR(40) NOT NULL DEFAULT 'etc',
    logo_image_url TEXT NOT NULL DEFAULT '',
    background_image_url TEXT NOT NULL DEFAULT '',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_store_info_updated_at
    ON store_info (updated_at DESC);
