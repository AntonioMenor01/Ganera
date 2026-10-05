-- Prompt B1 (D3): recepcion de WhatsApp y extraccion en segundo plano.
-- tramite: de donde viene (origen, null en las filas antiguas) y el estado de la extraccion por IA
-- (estado_extraccion PENDIENTE/COMPLETADA/FALLIDA/SIN_TEXTO, null en las antiguas), con lo que
-- necesita el planificador de reintentos (T2): intentos, proximo intento, la version que dejo el
-- primer fallo (para no pisar una correccion humana) y cuantos crotales se descartaron por invalidos.
ALTER TABLE tramite ADD COLUMN origen VARCHAR(20);
ALTER TABLE tramite ADD COLUMN estado_extraccion VARCHAR(20);
ALTER TABLE tramite ADD COLUMN intentos_extraccion INT NOT NULL DEFAULT 0;
ALTER TABLE tramite ADD COLUMN proximo_intento_extraccion TIMESTAMP;
ALTER TABLE tramite ADD COLUMN version_tras_fallo BIGINT;
ALTER TABLE tramite ADD COLUMN crotales_descartados INT NOT NULL DEFAULT 0;
CREATE INDEX idx_tramite_extraccion_pendiente ON tramite(estado_extraccion, proximo_intento_extraccion);

-- mensaje_campo: que paso con el mensaje (TRAMITE_CREADO/NUMERO_DESCONOCIDO/CONTACTO_INACTIVO, null
-- en las antiguas) y cuantos adjuntos traia (solo el numero, nunca sus URLs). Indice por fecha para
-- la purga de retencion (T3).
ALTER TABLE mensaje_campo ADD COLUMN resultado VARCHAR(30);
ALTER TABLE mensaje_campo ADD COLUMN num_media INT NOT NULL DEFAULT 0;
CREATE INDEX idx_mensaje_campo_created_at ON mensaje_campo(created_at);
