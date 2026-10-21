-- Callback de Flyway afterMigrate (solo PostgreSQL; migracion a Supabase, T2). Flyway lo ejecuta al final de
-- CADA migrate, haya o no migraciones pendientes, sobre el esquema por defecto de Flyway (en el perfil clever,
-- "ganera"; en los demas tests de PostgreSQL, "public"). Es idempotente.
--
-- Recorre TODAS las tablas del esquema (tambien las de migraciones futuras, sin acordarse de nada):
--   - activa RLS (sin FORCE: el dueno, o sea Flyway y la siembra, no la cumple);
--   - quita todo a PUBLIC, anon, authenticated, service_role y ganera_app, y despues da a ganera_app
--     exactamente lo que necesita, con su politica (USING true / WITH CHECK true: hoy la separacion entre
--     gestorias la hace la aplicacion; RLS esta para que ningun otro rol lea nada):
--       tabla normal            SELECT, INSERT, UPDATE, DELETE + politica ganera_app_todo (FOR ALL)
--       tabla solo insercion    SELECT, INSERT + ganera_app_lectura (FOR SELECT) y ganera_app_insercion
--                               (FOR INSERT); nunca UPDATE ni DELETE (la futura tabla de auditoria)
--       flyway_schema_history   nada para ganera_app (RLS activada y sin politica)
--   - secuencias: solo USAGE para ganera_app (las tablas usan BIGSERIAL);
--   - funciones y procedimientos: sin EXECUTE para PUBLIC, anon, authenticated y service_role;
--   - vistas: nada para PUBLIC, anon, authenticated y service_role;
--   - esquema: sin nada para PUBLIC, anon, authenticated y service_role; ganera_app solo USAGE, nunca CREATE.
--
-- Falla (y con el, la migracion y el arranque) si falta alguno de los roles: nunca deja tablas sin proteger en
-- silencio. Los crea backend/supabase/01-preparar-roles-y-esquema.sql.
--
-- La lista de tablas «solo insercion y lectura» es la MISMA que en backend/supabase/02-comprobar-seguridad.sql.
-- Hoy solo tiene el nombre reservado de la futura tabla de auditoria; si esa tarea la renombra, se cambia aqui y
-- en 02 a la vez.
DO $seguridad$
DECLARE
    esquema constant text := '${flyway:defaultSchema}';
    solo_insercion constant text[] := ARRAY['auditoria'];
    ajenos constant text := 'PUBLIC, anon, authenticated, service_role';
    faltan text;
    t record;
    politica record;
BEGIN
    SELECT string_agg(r, ', ' ORDER BY r) INTO faltan
    FROM unnest(ARRAY['ganera_app', 'anon', 'authenticated', 'service_role']) AS r
    WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = r);
    IF faltan IS NOT NULL THEN
        RAISE EXCEPTION 'Faltan los roles % en la base de datos. Antes de migrar hay que ejecutar backend/supabase/01-preparar-roles-y-esquema.sql (crea los roles y el esquema).', faltan;
    END IF;

    -- Esquema
    EXECUTE format('REVOKE ALL ON SCHEMA %I FROM %s', esquema, ajenos);
    EXECUTE format('REVOKE CREATE ON SCHEMA %I FROM ganera_app', esquema);
    EXECUTE format('GRANT USAGE ON SCHEMA %I TO ganera_app', esquema);

    -- Tablas
    FOR t IN
        SELECT c.relname, c.relrowsecurity
        FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = esquema AND c.relkind IN ('r', 'p')
        ORDER BY c.relname
    LOOP
        -- Solo si falta: ALTER TABLE toma ACCESS EXCLUSIVE hasta el final de la transaccion del callback, y en cada
        -- despliegue la instancia vieja sigue sirviendo mientras arranca la nueva (bloquearia todas sus consultas, o
        -- un interbloqueo haria fallar el arranque). Los GRANT/REVOKE de abajo no toman ningun bloqueo de la tabla
        -- (solo reescriben su fila de pg_class), asi que se repiten siempre; CREATE/DROP POLICY si lo toman, pero
        -- solo se ejecutan cuando la politica falta o esta mal.
        IF NOT t.relrowsecurity THEN
            EXECUTE format('ALTER TABLE %I.%I ENABLE ROW LEVEL SECURITY', esquema, t.relname);
        END IF;
        EXECUTE format('REVOKE ALL ON TABLE %I.%I FROM %s, ganera_app CASCADE', esquema, t.relname, ajenos);

        IF t.relname = 'flyway_schema_history' THEN
            -- Nada para ganera_app. Se borra cualquier politica que hubiera.
            FOR politica IN
                SELECT policyname FROM pg_policies WHERE schemaname = esquema AND tablename = t.relname
            LOOP
                EXECUTE format('DROP POLICY %I ON %I.%I', politica.policyname, esquema, t.relname);
            END LOOP;
        ELSIF t.relname = ANY (solo_insercion) THEN
            EXECUTE format('GRANT SELECT, INSERT ON TABLE %I.%I TO ganera_app', esquema, t.relname);
            -- Fuera las politicas de ganera_app que no son las de esta clase, o que no son exactas.
            FOR politica IN
                SELECT policyname FROM pg_policies
                WHERE schemaname = esquema AND tablename = t.relname
                  AND (policyname = 'ganera_app_todo'
                       OR (policyname = 'ganera_app_lectura'
                           AND NOT (permissive = 'PERMISSIVE' AND cmd = 'SELECT'
                                    AND roles = ARRAY['ganera_app']::name[] AND qual IS NOT DISTINCT FROM 'true'
                                    AND with_check IS NULL))
                       OR (policyname = 'ganera_app_insercion'
                           AND NOT (permissive = 'PERMISSIVE' AND cmd = 'INSERT'
                                    AND roles = ARRAY['ganera_app']::name[] AND qual IS NULL
                                    AND with_check IS NOT DISTINCT FROM 'true')))
            LOOP
                EXECUTE format('DROP POLICY %I ON %I.%I', politica.policyname, esquema, t.relname);
            END LOOP;
            IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE schemaname = esquema AND tablename = t.relname
                                                       AND policyname = 'ganera_app_lectura') THEN
                EXECUTE format('CREATE POLICY ganera_app_lectura ON %I.%I FOR SELECT TO ganera_app USING (true)',
                               esquema, t.relname);
            END IF;
            IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE schemaname = esquema AND tablename = t.relname
                                                       AND policyname = 'ganera_app_insercion') THEN
                EXECUTE format('CREATE POLICY ganera_app_insercion ON %I.%I FOR INSERT TO ganera_app WITH CHECK (true)',
                               esquema, t.relname);
            END IF;
        ELSE
            EXECUTE format('GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE %I.%I TO ganera_app', esquema, t.relname);
            FOR politica IN
                SELECT policyname FROM pg_policies
                WHERE schemaname = esquema AND tablename = t.relname
                  AND (policyname IN ('ganera_app_lectura', 'ganera_app_insercion')
                       OR (policyname = 'ganera_app_todo'
                           AND NOT (permissive = 'PERMISSIVE' AND cmd = 'ALL'
                                    AND roles = ARRAY['ganera_app']::name[] AND qual IS NOT DISTINCT FROM 'true'
                                    AND with_check IS NOT DISTINCT FROM 'true')))
            LOOP
                EXECUTE format('DROP POLICY %I ON %I.%I', politica.policyname, esquema, t.relname);
            END LOOP;
            IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE schemaname = esquema AND tablename = t.relname
                                                       AND policyname = 'ganera_app_todo') THEN
                EXECUTE format('CREATE POLICY ganera_app_todo ON %I.%I FOR ALL TO ganera_app USING (true) WITH CHECK (true)',
                               esquema, t.relname);
            END IF;
        END IF;
    END LOOP;

    -- Secuencias
    FOR t IN
        SELECT c.relname
        FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = esquema AND c.relkind = 'S'
    LOOP
        EXECUTE format('REVOKE ALL ON SEQUENCE %I.%I FROM %s, ganera_app CASCADE', esquema, t.relname, ajenos);
        EXECUTE format('GRANT USAGE ON SEQUENCE %I.%I TO ganera_app', esquema, t.relname);
    END LOOP;

    -- Vistas (hoy no hay)
    FOR t IN
        SELECT c.relname
        FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = esquema AND c.relkind IN ('v', 'm', 'f')
    LOOP
        EXECUTE format('REVOKE ALL ON TABLE %I.%I FROM %s CASCADE', esquema, t.relname, ajenos);
    END LOOP;

    -- Funciones y procedimientos (hoy no hay; los traera la auditoria)
    FOR t IN
        SELECT p.oid::regprocedure::text AS relname
        FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace
        WHERE n.nspname = esquema
    LOOP
        EXECUTE format('REVOKE ALL ON ROUTINE %s FROM %s CASCADE', t.relname, ajenos);
    END LOOP;
END
$seguridad$;
