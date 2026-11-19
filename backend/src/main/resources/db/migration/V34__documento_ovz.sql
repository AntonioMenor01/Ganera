-- Justificante de la baja presentada en OVZ (escalon 2, T3; plan 2026-11-17, §9 y D6 revisada 2026-11-18). El
-- binario se guarda EN POSTGRES (no en Supabase Storage): metadatos en documento_ovz y el PDF en
-- documento_ovz_contenido (1:1, misma PK). Tenant-scoped (gestoria_id), como el tramite y el trabajo. El contenido
-- se separa en su propia tabla para no arrastrar el BYTEA en cada consulta de metadatos (listado de la ficha).
--
-- tipo: por ahora solo 'BAJA_ANIMAL' (el tipo del justificante en OVZ). tamano en bytes (max 10 MB, cabe en INTEGER).
-- sha256 en hexadecimal (64 chars). content_type siempre 'application/pdf' (solo se aceptan PDF, cabecera %PDF).
-- created_at TIMESTAMP sin zona con el Instant del Clock, como el resto del esquema. Migracion comun H2 + PostgreSQL.
CREATE TABLE documento_ovz (
    id BIGSERIAL PRIMARY KEY,
    gestoria_id BIGINT NOT NULL,
    ganadero_id BIGINT NOT NULL,
    tramite_id BIGINT NOT NULL,
    explotacion_id BIGINT NOT NULL,
    tipo VARCHAR(30) NOT NULL,
    nombre VARCHAR(255) NOT NULL,
    tamano INTEGER NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    sha256 VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_documento_ovz_gestoria FOREIGN KEY (gestoria_id) REFERENCES gestoria(id),
    CONSTRAINT fk_documento_ovz_ganadero FOREIGN KEY (ganadero_id) REFERENCES ganadero(id),
    CONSTRAINT fk_documento_ovz_tramite FOREIGN KEY (tramite_id) REFERENCES tramite(id),
    CONSTRAINT fk_documento_ovz_explotacion FOREIGN KEY (explotacion_id) REFERENCES explotacion(id),
    CONSTRAINT ck_documento_ovz_tamano CHECK (tamano > 0 AND tamano <= 10485760)
);

-- El PDF, 1:1 con documento_ovz (misma PK). Se separa de los metadatos para que el listado de la ficha no cargue
-- el binario. ON DELETE CASCADE: si algun dia se borra un documento, su contenido se va con el.
CREATE TABLE documento_ovz_contenido (
    documento_ovz_id BIGINT PRIMARY KEY,
    contenido BYTEA NOT NULL,
    CONSTRAINT fk_documento_ovz_contenido FOREIGN KEY (documento_ovz_id)
        REFERENCES documento_ovz(id) ON DELETE CASCADE
);
