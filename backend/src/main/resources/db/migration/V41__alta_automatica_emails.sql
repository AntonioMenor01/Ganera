-- Idempotencia de los emails del alta automatica (rama feat/alta-automatica-emails, Alvaro, rango V40+).
-- Dos columnas ADITIVAS y NULL en alta_pendiente: cuando se envio con exito cada email. Mientras esten en NULL,
-- el planificador reintenta el envio en la siguiente pasada; en cuanto constan, NO se reenvia (las re-entregas
-- de Stripe y los reintentos no duplican correos). Un fallo de email no toca estas columnas (quedan NULL) y no
-- revierte la cuenta ya creada.
--   - email_bienvenida_enviado_en: cuando se envio el email de bienvenida al cliente.
--   - email_equipo_enviado_en: cuando se envio el aviso al equipo (solo si ganera.notificaciones.equipo-email
--     esta configurado; si esta en blanco no se envia y queda NULL).
-- TIMESTAMP sin zona con el Instant del Clock, como el resto del esquema (sin DEFAULT now()).
ALTER TABLE alta_pendiente ADD COLUMN email_bienvenida_enviado_en TIMESTAMP;
ALTER TABLE alta_pendiente ADD COLUMN email_equipo_enviado_en TIMESTAMP;
