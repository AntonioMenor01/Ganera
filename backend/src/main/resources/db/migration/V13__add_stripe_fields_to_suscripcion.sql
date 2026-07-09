ALTER TABLE suscripcion ADD COLUMN stripe_customer_id VARCHAR(255);
ALTER TABLE suscripcion ADD COLUMN stripe_subscription_id VARCHAR(255);
ALTER TABLE suscripcion ADD COLUMN explotaciones_contratadas INTEGER;
-- Guard monotonico contra re-entrega tardia / desorden de webhooks de Stripe
-- (Decision de diseño 4): epoch-seconds del event.created mas reciente aplicado.
ALTER TABLE suscripcion ADD COLUMN stripe_ultimo_evento_epoch BIGINT;
ALTER TABLE suscripcion ADD CONSTRAINT uq_suscripcion_stripe_subscription_id UNIQUE (stripe_subscription_id);
