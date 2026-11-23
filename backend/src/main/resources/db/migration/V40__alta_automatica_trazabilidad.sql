-- Alta automatica de cuentas desde los pagos de la landing (rama feat/alta-automatica, Alvaro, rango V40+).
-- Fase automatica pendiente que anunciaba V24: cuando llega un pago de la landing (fila alta_pendiente en
-- PENDIENTE) se crea sola la cuenta (Gestoria + Usuario + Suscripcion) y se enlaza la Subscription de Stripe.
-- TODO detras del flag ganera.alta.automatica.activa (default false): sin encenderlo, nada de esto se ejecuta
-- y el alta sigue siendo manual (POST /internal/onboarding/gestoria).
--
-- Dos columnas ADITIVAS y NULL en alta_pendiente, solo para trazabilidad e idempotencia del alta automatica;
-- no cambian el enum de estado (se reutiliza PENDIENTE -> ENLAZADA, igual que el enlace manual). Las filas que
-- ya escribe el webhook de la landing no las rellenan: quedan NULL hasta que el alta automatica procesa la fila.
--   - gestoria_id: id de la Gestoria creada a partir de esta alta. BIGINT simple (sin FK): alta_pendiente es
--     pre-tenant (existe antes que la Gestoria, no es GestoriaScopedEntity), es solo traza.
--   - procesada_en: cuando el alta automatica proceso la fila (Instant del Clock, TIMESTAMP sin zona como el
--     resto del esquema; sin DEFAULT now()).
ALTER TABLE alta_pendiente ADD COLUMN gestoria_id BIGINT;
ALTER TABLE alta_pendiente ADD COLUMN procesada_en TIMESTAMP;
