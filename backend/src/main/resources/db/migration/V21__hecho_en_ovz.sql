-- «Hecho en OVZ» (D1 de docs/superpowers/plans/2026-10-07-hecho-en-ovz.md): el gestor indica que EL ya
-- ha metido en OVZ un tramite APROBADO y Ganera guarda quien (el usuario autenticado) y cuando (Instant
-- del Clock, TIMESTAMP sin zona como el resto). Ganera no envia nada a OVZ. Se puede deshacer
-- (vuelve a APROBADO y las dos columnas a null).
-- El CHECK mantiene la coherencia en la propia BD: con estado HECHO_EN_OVZ las dos columnas llenas;
-- con cualquier otro estado, las dos null. estado ya es VARCHAR(30) (V8): no se ensancha.
-- Las filas existentes no estan en HECHO_EN_OVZ y quedan con null: cumplen el CHECK.
ALTER TABLE tramite ADD COLUMN hecho_en_ovz_por BIGINT;
ALTER TABLE tramite ADD COLUMN hecho_en_ovz_en TIMESTAMP;

ALTER TABLE tramite ADD CONSTRAINT fk_tramite_hecho_en_ovz_por
    FOREIGN KEY (hecho_en_ovz_por) REFERENCES usuario(id);

ALTER TABLE tramite ADD CONSTRAINT ck_tramite_hecho_en_ovz CHECK (
    (estado = 'HECHO_EN_OVZ' AND hecho_en_ovz_por IS NOT NULL AND hecho_en_ovz_en IS NOT NULL)
    OR (estado <> 'HECHO_EN_OVZ' AND hecho_en_ovz_por IS NULL AND hecho_en_ovz_en IS NULL)
);
