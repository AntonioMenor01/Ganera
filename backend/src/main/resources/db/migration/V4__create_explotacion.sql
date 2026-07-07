CREATE TABLE explotacion (
    id BIGSERIAL PRIMARY KEY,
    gestoria_id BIGINT NOT NULL REFERENCES gestoria(id),
    ganadero_id BIGINT NOT NULL REFERENCES ganadero(id),
    codigo_rega VARCHAR(50) NOT NULL UNIQUE,
    nombre VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_explotacion_gestoria_id ON explotacion(gestoria_id);
CREATE INDEX idx_explotacion_ganadero_id ON explotacion(ganadero_id);
