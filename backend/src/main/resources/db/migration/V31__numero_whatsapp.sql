-- WhatsApp: un número por gestoría, fase 1 (docs/superpowers/plans/2026-11-16-whatsapp-numero-por-gestoria.md).
-- numero_whatsapp registra SOLO números DEDICADO: el número de WhatsApp propio de una gestoría, al que
-- escriben sus clientes. La gestoría de un mensaje DEDICADO se resuelve por el número de DESTINO (To),
-- no por el teléfono del contacto. El modo COMPARTIDO (número único de hoy) NO es una fila: se identifica
-- por configuración (To == ganera.twilio.whatsapp-from). Por eso gestoria_id es NOT NULL (todos DEDICADO)
-- y la auditoría del alta siempre tiene gestoría. numero va normalizado a E.164 (TelefonoNormalizador).
-- Tabla de config, consultada por el webhook sin tenant (findByNumero, globalmente UNIQUE por numero).
-- Fechas TIMESTAMP sin zona con el Instant del Clock, como el resto del esquema. Migración común H2 + PostgreSQL.
CREATE TABLE numero_whatsapp (
    id BIGSERIAL PRIMARY KEY,
    numero VARCHAR(30) NOT NULL,
    gestoria_id BIGINT NOT NULL,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_numero_whatsapp_gestoria FOREIGN KEY (gestoria_id) REFERENCES gestoria(id),
    CONSTRAINT uk_numero_whatsapp_numero UNIQUE (numero)
);

-- El número de DESTINO (To) por el que llegó el mensaje, normalizado; NULL en mensajes anteriores a V31 y
-- cuando Twilio no envía To. Nunca sale en un DTO de respuesta; en logs va enmascarado.
ALTER TABLE mensaje_campo ADD COLUMN telefono_destino VARCHAR(30);

-- El número DEDICADO por el que entró el trámite. NULL = COMPARTIDO/legacy (se responde por el
-- ganera.twilio.whatsapp-from configurado). Invariante: si no es NULL, el numero_whatsapp referido
-- pertenece a la MISMA gestoría que el trámite (lo garantiza la bifurcación de MensajeEntranteService).
ALTER TABLE tramite ADD COLUMN numero_whatsapp_id BIGINT;
ALTER TABLE tramite ADD CONSTRAINT fk_tramite_numero_whatsapp
    FOREIGN KEY (numero_whatsapp_id) REFERENCES numero_whatsapp(id);
