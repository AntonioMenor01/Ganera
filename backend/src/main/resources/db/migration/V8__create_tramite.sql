CREATE TABLE tramite (
    id BIGSERIAL PRIMARY KEY,
    gestoria_id BIGINT NOT NULL REFERENCES gestoria(id),
    contacto_id BIGINT NOT NULL REFERENCES contacto(id),
    explotacion_id BIGINT REFERENCES explotacion(id),
    tipo_tramite VARCHAR(20),
    estado VARCHAR(30) NOT NULL,
    motivo_error TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_tramite_gestoria_id ON tramite(gestoria_id);
CREATE INDEX idx_tramite_estado ON tramite(estado);
