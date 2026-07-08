CREATE TABLE suscripcion (
    id BIGSERIAL PRIMARY KEY,
    gestoria_id BIGINT NOT NULL UNIQUE REFERENCES gestoria(id),
    estado VARCHAR(30) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);
