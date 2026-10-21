-- =====================================================================================================
-- Ganera — 01: preparar roles, esquema y zona horaria de la base de datos (migracion a Supabase, T2)
--
-- Que hace:
--   1. Crea, si no existen, los roles de Ganera (y si ya existian, les quita los atributos indebidos):
--        ganera_propietario  dueno del esquema "ganera" y de sus tablas; solo lo usa Flyway (y la siembra).
--        ganera_app          el usuario de la aplicacion; solo lee y escribe filas (los permisos de cada
--                            tabla los da el callback de Flyway afterMigrate__seguridad_esquema.sql).
--      Los dos: LOGIN, sin superusuario, sin crear bases ni roles, sin replicacion, sin BYPASSRLS.
--   2. Crea, si no existen, los roles de Supabase anon, authenticated y service_role (NOLOGIN). En
--      Supabase ya existen y no se tocan; esto es para el PostgreSQL local (docker-compose) y los tests.
--   3. Da al usuario que ejecuta el script (en Supabase, postgres) la opcion de hacer SET ROLE
--      ganera_propietario, sin heredar sus permisos: desde PostgreSQL 16 hace falta para crear el esquema a
--      su nombre, y sirve para la siembra (SET ROLE ganera_propietario) y para vaciar el esquema.
--   4. Crea el esquema "ganera" de ganera_propietario; quita todo a PUBLIC, anon, authenticated y
--      service_role; a ganera_app solo le da USAGE (nunca CREATE).
--   5. Fija la zona horaria de la base de datos en UTC (para el editor SQL, psql y el SQL a mano; JDBC
--      manda la suya al conectar).
--
-- Como se ejecuta: ENTERO, de una vez (editor SQL de Supabase, como postgres; o psql -f). Va en una
-- transaccion: o se aplica todo o nada. Es idempotente: se puede ejecutar otra vez sin error y sin cambios.
--
-- Contrasenas: este script NO pone ninguna. Despues, desde psql 17 conectado como postgres:
--     \password ganera_propietario
--     \password ganera_app
-- (psql cifra la contrasena con SCRAM antes de mandarla: nunca queda en claro en un log ni en el historial).
--
-- Comprobacion: backend/supabase/02-comprobar-seguridad.sql (despues de migrar) debe devolver 0 filas.
-- =====================================================================================================

BEGIN;

-- 1 y 2. Roles
DO $roles$
DECLARE
    r record;
    cambios text;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'ganera_propietario') THEN
        CREATE ROLE ganera_propietario LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'ganera_app') THEN
        CREATE ROLE ganera_app LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;
    END IF;
    -- Si ya existian con otros atributos (creados a mano), se corrigen. Solo se cambia lo que esta mal: quitar
    -- SUPERUSER, REPLICATION o BYPASSRLS exige tenerlo, y entonces el script falla (y no deja nada) en vez de seguir.
    FOR r IN
        SELECT rolname, rolsuper, rolcreatedb, rolcreaterole, rolreplication, rolbypassrls, rolcanlogin
        FROM pg_roles WHERE rolname IN ('ganera_propietario', 'ganera_app')
    LOOP
        cambios := concat_ws(' ',
            CASE WHEN r.rolsuper THEN 'NOSUPERUSER' END,
            CASE WHEN r.rolcreatedb THEN 'NOCREATEDB' END,
            CASE WHEN r.rolcreaterole THEN 'NOCREATEROLE' END,
            CASE WHEN r.rolreplication THEN 'NOREPLICATION' END,
            CASE WHEN r.rolbypassrls THEN 'NOBYPASSRLS' END,
            CASE WHEN NOT r.rolcanlogin THEN 'LOGIN' END);
        IF cambios <> '' THEN
            EXECUTE format('ALTER ROLE %I %s', r.rolname, cambios);
        END IF;
    END LOOP;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
        CREATE ROLE anon NOLOGIN;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN
        CREATE ROLE authenticated NOLOGIN;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'service_role') THEN
        CREATE ROLE service_role NOLOGIN;
    END IF;
END
$roles$;

-- 3. Poder hacer SET ROLE ganera_propietario sin heredar sus permisos (PostgreSQL 16+).
GRANT ganera_propietario TO CURRENT_USER WITH SET TRUE, INHERIT FALSE;

-- 4. Esquema. Se crea a nombre de ganera_propietario; los permisos los da su dueno: con INHERIT FALSE el
--    usuario que ejecuta no tiene ningun privilegio sobre el esquema, y un GRANT o REVOKE suyo fallaria con
--    ERROR 42501 (permiso denegado).
CREATE SCHEMA IF NOT EXISTS ganera AUTHORIZATION ganera_propietario;

SET LOCAL ROLE ganera_propietario;
REVOKE ALL ON SCHEMA ganera FROM PUBLIC, anon, authenticated, service_role;
REVOKE CREATE ON SCHEMA ganera FROM ganera_app;
GRANT USAGE ON SCHEMA ganera TO ganera_app;
RESET ROLE;

-- 5. Zona horaria de la base de datos (la ejecuta su dueno).
DO $zona$
BEGIN
    EXECUTE format('ALTER DATABASE %I SET timezone TO %L', current_database(), 'UTC');
END
$zona$;

COMMIT;
