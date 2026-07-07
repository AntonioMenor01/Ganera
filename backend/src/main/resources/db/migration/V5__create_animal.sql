CREATE TABLE animal (
    id BIGSERIAL PRIMARY KEY,
    gestoria_id BIGINT NOT NULL REFERENCES gestoria(id),
    explotacion_id BIGINT NOT NULL REFERENCES explotacion(id),
    crotal VARCHAR(20) NOT NULL UNIQUE,
    crotal_ultimos_digitos VARCHAR(10) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_animal_gestoria_id ON animal(gestoria_id);
CREATE INDEX idx_animal_explotacion_crotal_digitos ON animal(explotacion_id, crotal_ultimos_digitos);
