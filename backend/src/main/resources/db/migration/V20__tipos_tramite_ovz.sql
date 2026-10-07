-- Ficha OVZ, T1 (decision D1): cada tipo de tramite pasa a ser un formulario de OVZNET.
-- Plan: docs/superpowers/plans/2026-10-06-ficha-ovz.md; catalogo: docs/referencias/ovz-tramites-bovino.md.
-- La columna se ensancha porque CONFIRMACION_MOVIMIENTO tiene 23 caracteres. Todo MOVIMIENTO pasa a
-- SOLICITUD_MOVIMIENTO: no se puede saber si un movimiento antiguo era una entrada (los datos son de
-- demo; un pendiente se corrige con PATCH). Los null siguen siendo null.
-- SET DATA TYPE es SQL estandar y vale igual en PostgreSQL 16 y en H2 (modo PostgreSQL).
ALTER TABLE tramite ALTER COLUMN tipo_tramite SET DATA TYPE VARCHAR(30);

UPDATE tramite SET tipo_tramite = 'ALTA_NACIMIENTO' WHERE tipo_tramite = 'ALTA';
UPDATE tramite SET tipo_tramite = 'BAJA_MUERTE' WHERE tipo_tramite = 'BAJA';
UPDATE tramite SET tipo_tramite = 'SOLICITUD_MOVIMIENTO' WHERE tipo_tramite = 'MOVIMIENTO';
UPDATE tramite SET tipo_tramite = 'DECLARACION_CENSO' WHERE tipo_tramite = 'CENSO';
UPDATE tramite SET tipo_tramite = 'DEMORA_CROTALIZACION' WHERE tipo_tramite = 'DEMORA';
