-- Auditoria base (docs/superpowers/plans/2026-10-21-auditoria-base.md, T2). Inmutabilidad de la tabla auditoria en
-- PostgreSQL (solo aqui; en H2 queda el hueco de V25, que Flyway admite: regla de numeracion global de db/{vendor}).
--
-- Dos capas protegen la tabla:
--   1. Permisos: ganera_app (el usuario de la aplicacion) solo tiene SELECT e INSERT (lista «solo insercion» del
--      callback afterMigrate__seguridad_esquema.sql) -> un UPDATE/DELETE/TRUNCATE suyo es «permission denied».
--   2. Triggers: el PROPIETARIO (ganera_propietario, Flyway, SQL a mano) si tiene el privilegio, asi que ademas lo
--      para un trigger. Son los dos de abajo.
--
-- POR QUE DOS TRIGGERS, y no uno:
--   - auditoria_sin_cambios: BEFORE UPDATE OR TRUNCATE, POR SENTENCIA. UPDATE y TRUNCATE quedan prohibidos para
--     siempre (TRUNCATE solo admite triggers por sentencia; un UPDATE … WHERE false tambien dispara un trigger por
--     sentencia). Un TRUNCATE gestoria CASCADE tambien se para: el trigger de TRUNCATE se dispara en las tablas que
--     caen en cascada.
--   - auditoria_sin_borrado: BEFORE DELETE, POR FILA. El borrado va aparte y por fila a proposito, para que el dia
--     que el abogado fije un plazo de conservacion una migracion nueva solo tenga que SUSTITUIR auditoria_sin_borrado()
--     por una version que deje pasar la fila si, por ejemplo, current_user = 'ganera_propietario', la sesion la marca
--     como proceso de retencion (p. ej. SET LOCAL ganera.retencion_auditoria = 'on') y OLD.creado_en es anterior al
--     plazo; todo lo demas seguiria siendo excepcion. Ni la tabla, ni el otro trigger, ni los permisos de ganera_app
--     cambiarian. NO SE IMPLEMENTA AHORA (lo decide el abogado); el diseno solo lo deja posible. Un DELETE de 0 filas
--     no dispara un trigger por fila (no borra nada: aceptable).
--
-- LIMITES QUE QUEDAN (documentados, no se cierran aqui): el propietario puede DROP TRIGGER, ALTER TABLE … DISABLE
-- TRIGGER o DROP TABLE (S7, DROP SCHEMA ganera CASCADE, lo necesita para vaciar la base antes de los datos reales).
-- El callback afterMigrate comprueba al arrancar que la tabla tiene sus dos triggers activos y exactos (D2): si no,
-- la migracion y el arranque fallan, y backend/supabase/02-comprobar-seguridad.sql lo marca. session_replication_role
-- = replica saltaria los triggers, pero exige superusuario (el postgres de Supabase no lo es); el 02 marca ademas un
-- GRANT SET ON PARAMETER session_replication_role a cualquier rol de Ganera o de Supabase.
--
-- SET search_path = pg_catalog en las dos funciones: no dependen de ningun objeto del esquema, y asi un search_path
-- hostil no puede cambiar lo que hacen. Los nombres van sin esquema (como V24): la migracion se ejecuta en el esquema
-- por defecto de Flyway (ganera en el perfil clever, public en los demas tests de PostgreSQL).
CREATE FUNCTION auditoria_sin_cambios() RETURNS trigger LANGUAGE plpgsql SET search_path = pg_catalog AS $$
BEGIN
    RAISE EXCEPTION 'La tabla auditoria solo admite INSERT (% rechazado).', TG_OP;
END $$;

CREATE TRIGGER auditoria_sin_cambios BEFORE UPDATE OR TRUNCATE ON auditoria
    FOR EACH STATEMENT EXECUTE FUNCTION auditoria_sin_cambios();

CREATE FUNCTION auditoria_sin_borrado() RETURNS trigger LANGUAGE plpgsql SET search_path = pg_catalog AS $$
BEGIN
    RAISE EXCEPTION 'La tabla auditoria solo admite INSERT (DELETE rechazado).';
END $$;

CREATE TRIGGER auditoria_sin_borrado BEFORE DELETE ON auditoria
    FOR EACH ROW EXECUTE FUNCTION auditoria_sin_borrado();
