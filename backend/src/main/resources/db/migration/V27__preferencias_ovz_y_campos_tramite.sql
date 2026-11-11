-- Preferencias fijas de la gestoría para rellenar OVZ (oficina DI/MER, transporte, teléfono de guía)
-- y campos del trámite que la ficha / la IA / el PATCH pueden rellenar (fecha del hecho, alta, destino, MER).
-- Plan: docs/superpowers/plans/2026-11-10-bloques-piloto-ovz.md

CREATE TABLE preferencia_ovz (
    id BIGSERIAL PRIMARY KEY,
    gestoria_id BIGINT NOT NULL,
    created_at TIMESTAMP,
    oficina_di VARCHAR(80),
    oficina_mer VARCHAR(80),
    emision_di VARCHAR(80),
    tipo_identificador VARCHAR(80),
    telefono_movimiento VARCHAR(30),
    medio_transporte VARCHAR(40),
    matricula VARCHAR(40),
    transportista_sirentra VARCHAR(40),
    tipo_responsable VARCHAR(80),
    CONSTRAINT uk_preferencia_ovz_gestoria UNIQUE (gestoria_id),
    CONSTRAINT fk_preferencia_ovz_gestoria FOREIGN KEY (gestoria_id) REFERENCES gestoria(id)
);

ALTER TABLE tramite ADD COLUMN fecha_hecho DATE;
ALTER TABLE tramite ADD COLUMN fecha_nacimiento DATE;
ALTER TABLE tramite ADD COLUMN sexo VARCHAR(40);
ALTER TABLE tramite ADD COLUMN crotal_madre_indicado VARCHAR(30);
ALTER TABLE tramite ADD COLUMN numero_mer VARCHAR(60);
ALTER TABLE tramite ADD COLUMN destino_rega VARCHAR(20);
ALTER TABLE tramite ADD COLUMN destino_texto VARCHAR(255);
ALTER TABLE tramite ADD COLUMN consignatario VARCHAR(255);
ALTER TABLE tramite ADD COLUMN aptitud_movimiento VARCHAR(80);
ALTER TABLE tramite ADD COLUMN fecha_identificacion DATE;
