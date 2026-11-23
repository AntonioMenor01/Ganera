-- Raza del animal para el alta por nacimiento (plan 2026-11-23-alta-nacimiento-automatica-ovz, T1/D1).
-- Guarda el CÓDIGO del catálogo de OVZ (ListasOvz.RAZAS), nunca el texto: hay dos «DESCONOCIDA»
-- (901/955) y «PARDE/PARDA DE MONTAÑA» (1088/1322). Nullable y aditiva: los trámites que no son
-- alta no la usan. Obligatoria para APROBAR un ALTA_NACIMIENTO (validación en TramiteRevisionService).
-- Migración común H2 + PostgreSQL; siguiente número libre tras V40/V41 de Álvaro (hueco V35–V39 de Antonio).
ALTER TABLE tramite ADD COLUMN raza VARCHAR(10);
