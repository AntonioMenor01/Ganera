CREATE TABLE contacto_explotacion (
    id BIGSERIAL PRIMARY KEY,
    contacto_id BIGINT NOT NULL REFERENCES contacto(id),
    explotacion_id BIGINT NOT NULL REFERENCES explotacion(id),
    UNIQUE (contacto_id, explotacion_id)
);
