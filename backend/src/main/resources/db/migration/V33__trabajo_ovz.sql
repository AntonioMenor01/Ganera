-- Presentación automática de la baja por muerte en OVZ (escalón 2, T2;
-- docs/superpowers/plans/2026-11-17-presentacion-automatica-baja-ovz.md, §3/§4/D11). trabajo_ovz es la cola:
-- se encola al APROBAR un BAJA_MUERTE cuyo ganadero tiene credenciales y no usa certificado (D4/D8); el worker
-- (T4) lo reclama y lo presenta. Tenant-scoped (gestoria_id), como el trámite.
--
-- Un solo trabajo por trámite (clave de idempotencia): UNIQUE(tramite_id). Reencolar reutiliza la misma fila;
-- un doble encolado lo corta el constraint. clase_error (SEGURO/INCIERTO, D11) solo con estado = 'ERROR'
-- (CHECK). motivo_error es texto para el gestor; nunca va a auditoría. Fechas TIMESTAMP sin zona con el Instant
-- del Clock, como el resto del esquema. Migración común H2 + PostgreSQL.
CREATE TABLE trabajo_ovz (
    id BIGSERIAL PRIMARY KEY,
    gestoria_id BIGINT NOT NULL,
    tramite_id BIGINT NOT NULL,
    ganadero_id BIGINT NOT NULL,
    tipo VARCHAR(20) NOT NULL,
    estado VARCHAR(20) NOT NULL,
    clase_error VARCHAR(20),
    motivo_error VARCHAR(500),
    intentos INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    actualizado_en TIMESTAMP NOT NULL,
    CONSTRAINT fk_trabajo_ovz_gestoria FOREIGN KEY (gestoria_id) REFERENCES gestoria(id),
    CONSTRAINT fk_trabajo_ovz_tramite FOREIGN KEY (tramite_id) REFERENCES tramite(id),
    CONSTRAINT fk_trabajo_ovz_ganadero FOREIGN KEY (ganadero_id) REFERENCES ganadero(id),
    CONSTRAINT uk_trabajo_ovz_tramite UNIQUE (tramite_id),
    CONSTRAINT ck_trabajo_ovz_clase_error CHECK (clase_error IS NULL OR estado = 'ERROR')
);
