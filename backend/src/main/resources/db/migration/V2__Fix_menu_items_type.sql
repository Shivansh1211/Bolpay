-- Change menu_items from JSONB to TEXT to match Java String type
ALTER TABLE vendors ALTER COLUMN menu_items TYPE TEXT USING menu_items::TEXT;
