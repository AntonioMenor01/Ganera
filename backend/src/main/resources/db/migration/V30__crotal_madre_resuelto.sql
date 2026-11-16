-- Instantánea de la resolución del crotal de la madre contra el inventario de la explotación del
-- trámite (plan 2026-11-11-crotal-madre). crotal_madre guarda el crotal completado (el del
-- inventario si EN_INVENTARIO; si no, lo indicado) y crotal_madre_resolucion el enum ResolucionCrotal.
-- Sin FK al animal: la madre es un campo de referencia del formulario, no entra en la lista de
-- animales del trámite. crotal_madre_indicado (V27) se conserva: de él se re-resuelve al cambiar la
-- explotación. Migración común H2 + PostgreSQL.

ALTER TABLE tramite ADD COLUMN crotal_madre VARCHAR(30);
ALTER TABLE tramite ADD COLUMN crotal_madre_resolucion VARCHAR(20);
