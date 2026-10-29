-- Auditoria base (docs/superpowers/plans/2026-10-21-auditoria-base.md, T1). Una fila por cada escritura de
-- negocio, lectura sensible o acceso a un recurso de otra gestoria. Tabla de SOLO INSERCION: la app (ganera_app)
-- solo tiene SELECT e INSERT (lista «solo insercion» del callback afterMigrate de db/postgresql); los triggers que
-- rechazan UPDATE, DELETE y TRUNCATE tambien al propietario llegan en V25, solo en PostgreSQL (en H2 queda el hueco).
-- antes / despues: JSON canonico con claves de un vocabulario cerrado (Clave) y solo ids, codigos, recuentos,
-- booleanos y fechas (DatosAuditoria lo impone por tipo). Nunca textos, telefonos, nombres, NIF, crotales, ids de
-- Stripe/Twilio ni nada cifrado. VARCHAR(4000) y no jsonb/TEXT: portable y acotado (DatosAuditoria garantiza que
-- cabe). ip y request_id: NULL fuera de una peticion HTTP (planificadores). creado_en: Instant del Clock truncado a
-- microsegundos, TIMESTAMP sin zona como el resto y sin DEFAULT now().
-- Sin CHECK sobre accion / entidad_tipo (cada accion nueva obligaria a migrar; los cierra el enum de Java). Las FK
-- van sin ON DELETE: una accion en cascada seria un UPDATE/DELETE sobre auditoria. Consecuencia: una gestoria o un
-- usuario con filas de auditoria no se pueden borrar (hoy no se borran).
CREATE TABLE auditoria (
    id BIGSERIAL PRIMARY KEY,
    gestoria_id BIGINT NOT NULL,
    actor_tipo VARCHAR(20) NOT NULL,
    usuario_id BIGINT,
    accion VARCHAR(60) NOT NULL,
    entidad_tipo VARCHAR(40) NOT NULL,
    entidad_id BIGINT,
    antes VARCHAR(4000),
    despues VARCHAR(4000),
    ip VARCHAR(45),
    request_id VARCHAR(36),
    creado_en TIMESTAMP NOT NULL,
    CONSTRAINT fk_auditoria_gestoria FOREIGN KEY (gestoria_id) REFERENCES gestoria(id),
    CONSTRAINT fk_auditoria_usuario FOREIGN KEY (usuario_id) REFERENCES usuario(id),
    CONSTRAINT ck_auditoria_actor_tipo CHECK (actor_tipo IN
        ('USUARIO', 'SISTEMA', 'WEBHOOK_TWILIO', 'WEBHOOK_STRIPE', 'PLANIFICADOR')),
    CONSTRAINT ck_auditoria_usuario CHECK ((actor_tipo = 'USUARIO') = (usuario_id IS NOT NULL))
);

CREATE INDEX idx_auditoria_gestoria_creado ON auditoria (gestoria_id, creado_en);
CREATE INDEX idx_auditoria_entidad ON auditoria (entidad_tipo, entidad_id);
