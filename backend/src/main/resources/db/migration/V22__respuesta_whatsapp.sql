-- B2 reducido (docs/superpowers/plans/2026-10-11-promptB2-reducido-responder-whatsapp.md, D7/D8/D14):
-- respuestas que un Usuario envia por WhatsApp al Contacto que escribio un tramite. Ganera guarda que se
-- envio, quien, cuando y que dijo Twilio (estado, SID y codigo de error; nunca su mensaje en ingles).
-- Enviar no toca el tramite (ni estado ni version).
-- telefono_destino: copia del telefono en el momento del envio (si el contacto cambia de numero, el
-- historial dice a donde fue); NULL tras la retencion de 12 meses, que tambien vacia cuerpo y marca
-- texto_vaciado (asi una segunda pasada no toca nada).
-- clave_idempotencia: UUID del cliente; UNIQUE por gestoria corta la carrera de dos peticiones iguales.
-- twilio_message_sid: UNIQUE global (varios NULL permitidos: FALLIDO/SIN_CONFIRMAR/ENVIANDO no tienen SID).
-- Fechas TIMESTAMP sin zona con el Instant del Clock, como el resto del esquema (sin DEFAULT now()).
CREATE TABLE respuesta_whatsapp (
    id BIGSERIAL PRIMARY KEY,
    gestoria_id BIGINT NOT NULL,
    tramite_id BIGINT NOT NULL,
    contacto_id BIGINT NOT NULL,
    telefono_destino VARCHAR(30),
    cuerpo TEXT NOT NULL,
    enviado_por BIGINT NOT NULL,
    creado_en TIMESTAMP NOT NULL,
    actualizado_en TIMESTAMP NOT NULL,
    estado VARCHAR(20) NOT NULL,
    twilio_message_sid VARCHAR(64),
    codigo_error_twilio INTEGER,
    clave_idempotencia VARCHAR(36) NOT NULL,
    texto_vaciado BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_respuesta_whatsapp_gestoria FOREIGN KEY (gestoria_id) REFERENCES gestoria(id),
    CONSTRAINT fk_respuesta_whatsapp_tramite FOREIGN KEY (tramite_id) REFERENCES tramite(id),
    CONSTRAINT fk_respuesta_whatsapp_contacto FOREIGN KEY (contacto_id) REFERENCES contacto(id),
    CONSTRAINT fk_respuesta_whatsapp_enviado_por FOREIGN KEY (enviado_por) REFERENCES usuario(id),
    CONSTRAINT uk_respuesta_whatsapp_clave UNIQUE (gestoria_id, clave_idempotencia),
    CONSTRAINT uk_respuesta_whatsapp_sid UNIQUE (twilio_message_sid)
);

CREATE INDEX idx_respuesta_whatsapp_tramite ON respuesta_whatsapp (tramite_id);
