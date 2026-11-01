ALTER TABLE payment_intent
    ADD COLUMN request_fingerprint VARCHAR(128);

UPDATE payment_intent
SET request_fingerprint =
        'v1|' || currency || '|' || trim_scale(amount)::text;

ALTER TABLE payment_intent
    ALTER COLUMN request_fingerprint SET NOT NULL;