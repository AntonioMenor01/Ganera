-- Mensajes sin tramite y «Descartar» (docs/superpowers/plans/2026-10-12-sin-tramite.md, D4, A1 y A6).
-- motivo_rechazo: CODIGO del motivo de un rechazo (nunca texto; el texto lo pone el frontend). Hoy solo
-- 'SIN_TRAMITE' (descartado como mensaje sin tramite); NULL en un rechazo normal y en cualquier otro estado.
-- El CHECK cierra la lista de codigos y exige estado RECHAZADO (deshacer el descarte vuelve a
-- PENDIENTE_REVISION vaciando el motivo). Las filas existentes quedan con NULL: cumplen el CHECK.
-- tramite_evento (A6): una fila por cada descartar / deshacer, con el usuario autenticado (JWT) y la hora
-- (Instant del Clock, TIMESTAMP sin zona como el resto, sin DEFAULT now()). Sin texto ni datos personales
-- nuevos: solo referencias a un Usuario y a un Tramite existentes. La FK no comprueba que el usuario sea de
-- la gestoria del tramite (lo hace el servicio); sin ON DELETE (hoy no se borran usuarios ni tramites).
ALTER TABLE tramite ADD COLUMN motivo_rechazo VARCHAR(30);

ALTER TABLE tramite ADD CONSTRAINT ck_tramite_motivo_rechazo CHECK (
    motivo_rechazo IS NULL OR (estado = 'RECHAZADO' AND motivo_rechazo IN ('SIN_TRAMITE'))
);

CREATE TABLE tramite_evento (
    id BIGSERIAL PRIMARY KEY,
    gestoria_id BIGINT NOT NULL,
    tramite_id BIGINT NOT NULL,
    tipo VARCHAR(30) NOT NULL,
    usuario_id BIGINT NOT NULL,
    creado_en TIMESTAMP NOT NULL,
    CONSTRAINT fk_tramite_evento_gestoria FOREIGN KEY (gestoria_id) REFERENCES gestoria(id),
    CONSTRAINT fk_tramite_evento_tramite FOREIGN KEY (tramite_id) REFERENCES tramite(id),
    CONSTRAINT fk_tramite_evento_usuario FOREIGN KEY (usuario_id) REFERENCES usuario(id),
    CONSTRAINT ck_tramite_evento_tipo CHECK (tipo IN ('DESCARTADO', 'DESCARTE_DESHECHO'))
);

CREATE INDEX idx_tramite_evento_tramite ON tramite_evento (tramite_id);
