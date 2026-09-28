ALTER TABLE contacto ADD COLUMN gestoria_id BIGINT NOT NULL REFERENCES gestoria(id);
ALTER TABLE contacto ADD COLUMN activo BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE contacto DROP COLUMN tipo;
CREATE INDEX idx_contacto_gestoria_id ON contacto(gestoria_id);
-- telefono sigue UNIQUE global (V6) -- decision A.

ALTER TABLE contacto_explotacion ADD COLUMN gestoria_id BIGINT NOT NULL REFERENCES gestoria(id);
ALTER TABLE contacto_explotacion ADD COLUMN rol VARCHAR(20) NOT NULL;
ALTER TABLE contacto_explotacion ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT now();
CREATE INDEX idx_contacto_explotacion_gestoria_id ON contacto_explotacion(gestoria_id);
