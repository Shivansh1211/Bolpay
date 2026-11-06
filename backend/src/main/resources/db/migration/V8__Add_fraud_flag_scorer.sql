ALTER TABLE fraud_flags
    ADD COLUMN scorer VARCHAR(30) NOT NULL DEFAULT 'legacy';

ALTER TABLE fraud_flags
    ALTER COLUMN model_version TYPE VARCHAR(40);
