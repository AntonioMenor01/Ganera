CREATE TABLE ganadero (
    id BIGSERIAL PRIMARY KEY,
    gestoria_id BIGINT NOT NULL REFERENCES gestoria(id),
    nombre VARCHAR(255) NOT NULL,
    ovz_usuario VARCHAR(255),
    ovz_password_cifrada VARCHAR(512),
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_ganadero_gestoria_id ON ganadero(gestoria_id);
