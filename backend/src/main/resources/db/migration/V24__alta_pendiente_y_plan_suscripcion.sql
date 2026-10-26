-- Enlace del pago de la landing (ganera-web) con la app, fase piloto / alta semi-manual
-- (sin crear Usuario ni enviar email todavia; eso es la fase automatica, ver CLAUDE.md > Stripe).
--
-- 1) suscripcion.plan / suscripcion.periodo: marca de "plan fijo". Las altas de la landing cobran un
--    precio fijo por plan (Base/Gestion/Plus, mensual/anual), NO por numero de explotaciones. Cuando
--    plan NO es null, SuscripcionSyncScheduler EXCLUYE esa suscripcion de la reconciliacion nocturna:
--    si no, el job empujaria quantity=nº de explotaciones a un precio fijo y multiplicaria el cobro.
ALTER TABLE suscripcion ADD COLUMN plan VARCHAR(20);
ALTER TABLE suscripcion ADD COLUMN periodo VARCHAR(20);

-- 2) alta_pendiente: una fila por checkout.session.completed de la landing que todavia no esta enlazado
--    a una Gestoria. NO es una tabla multi-tenant (existe ANTES de que exista la Gestoria), por eso no
--    tiene gestoria_id ni extiende GestoriaScopedEntity. El callback afterMigrate de RLS la cubre como
--    tabla normal (politica ganera_app_todo: SELECT/INSERT/UPDATE/DELETE para ganera_app), porque pasa
--    de PENDIENTE a ENLAZADA (no es "solo insercion").
--    Idempotencia por stripe_session_id UNIQUE (Stripe reintenta y reenvia). Sin cuerpo de mensajes ni
--    datos de tarjeta; los importes/medios de pago no se guardan. Fechas TIMESTAMP sin zona con el
--    Instant del Clock, como el resto del esquema (sin DEFAULT now()).
CREATE TABLE alta_pendiente (
    id BIGSERIAL PRIMARY KEY,
    stripe_session_id VARCHAR(255) NOT NULL,
    customer_id VARCHAR(255),
    subscription_id VARCHAR(255),
    email VARCHAR(320),
    nombre VARCHAR(255),
    nif VARCHAR(64),
    direccion_facturacion TEXT,
    plan VARCHAR(20),
    periodo VARCHAR(20),
    prueba BOOLEAN,
    estado VARCHAR(20) NOT NULL,
    creado_en TIMESTAMP NOT NULL,
    CONSTRAINT uk_alta_pendiente_session UNIQUE (stripe_session_id)
);

CREATE INDEX idx_alta_pendiente_subscription ON alta_pendiente (subscription_id);
