CREATE TABLE mensaje_campo (
    id BIGSERIAL PRIMARY KEY,
    message_sid VARCHAR(100) NOT NULL UNIQUE,
    telefono_origen VARCHAR(30) NOT NULL,
    cuerpo TEXT NOT NULL,
    gestoria_id BIGINT REFERENCES gestoria(id),
    contacto_id BIGINT REFERENCES contacto(id),
    tramite_id BIGINT REFERENCES tramite(id),
    created_at TIMESTAMP NOT NULL DEFAULT now()
);
