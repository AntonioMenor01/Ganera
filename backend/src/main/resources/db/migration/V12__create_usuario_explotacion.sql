CREATE TABLE usuario_explotacion (
    id BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT NOT NULL REFERENCES usuario(id),
    explotacion_id BIGINT NOT NULL REFERENCES explotacion(id),
    UNIQUE (usuario_id, explotacion_id)
);
