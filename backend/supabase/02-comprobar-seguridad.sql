-- =====================================================================================================
-- Ganera — 02: comprobar la seguridad del esquema "ganera" (migracion a Supabase, T2)
--
-- UNA consulta de solo lectura. Devuelve una fila por problema (objeto, problema). Lo correcto es 0 FILAS.
-- La ejecuta Antonio en el editor SQL de Supabase despues de migrar, y la leen del disco los tests de
-- PostgreSQL (SeguridadEsquemaPostgresTest): una sola fuente.
--
-- Comprueba, en el esquema ganera:
--   - cada tabla con RLS activada;
--   - cada tabla con exactamente las politicas esperadas para ganera_app (y ninguna otra);
--   - ningun privilegio de PUBLIC, anon, authenticated, service_role o authenticator en el esquema, tablas,
--     vistas, secuencias o funciones;
--   - ganera_app con los privilegios exactos: USAGE (sin CREATE) en el esquema; SELECT, INSERT, UPDATE,
--     DELETE en las tablas normales; SELECT, INSERT en las de «solo insercion y lectura»; nada en
--     flyway_schema_history; USAGE en las secuencias; y que no sea dueno de nada;
--   - ningun privilegio por columna para esos roles, ni para ganera_app en flyway_schema_history;
--   - inmutabilidad de auditoria (si la tabla existe): sus dos triggers de V26 estan, activos (tgenabled = O),
--     BEFORE, con su granularidad (auditoria_sin_cambios por sentencia con UPDATE y TRUNCATE; auditoria_sin_borrado
--     por fila con DELETE), sin WHEN ni columnas, llamando cada uno a su funcion del esquema, y ningun trigger mas; y
--     las dos funciones son de ganera_propietario;
--   - que ningun rol vigilado (anon, authenticated, service_role, authenticator, ganera_app, ganera_propietario)
--     pueda hacer SET sobre el parametro session_replication_role (con replica se saltaria esos triggers);
--   - nadie hereda ni puede hacer SET ROLE a ganera_app; ganera_app no es miembro de ningun rol; nadie hereda
--     ganera_propietario (solo SET, y nunca un rol de Supabase); ni los roles de Supabase ni los de Ganera
--     tienen ADMIN sobre ellos; ningun rol de Supabase llega a ellos por una cadena de roles (pg_has_role);
--     ninguno de los dos tiene SUPERUSER, BYPASSRLS, CREATEROLE, CREATEDB ni REPLICATION;
--   - la base de datos con TimeZone=UTC.
--
-- Tablas «solo insercion y lectura» para ganera_app: la MISMA lista que el callback
-- src/main/resources/db/postgresql/afterMigrate__seguridad_esquema.sql. Hoy solo el nombre reservado de la
-- futura tabla de auditoria; si esa tarea la renombra, se cambia aqui y alli a la vez.
-- =====================================================================================================
WITH
solo_insercion(tabla) AS (
    VALUES ('auditoria')
),
-- Los dos triggers de V26 sobre auditoria: nombre, si es por fila (DELETE) o por sentencia (UPDATE+TRUNCATE), la
-- mascara de bits de eventos esperada (pg_trigger.tgtype & 60: DELETE 8, UPDATE 16, TRUNCATE 32) y su texto.
triggers_auditoria(nombre, es_fila, eventos_ok, eventos_texto) AS (
    VALUES ('auditoria_sin_cambios', false, 48, 'UPDATE,TRUNCATE'),
           ('auditoria_sin_borrado', true, 8, 'DELETE')
),
esquema AS (
    SELECT n.oid, n.nspname, n.nspowner, n.nspacl FROM pg_namespace n WHERE n.nspname = 'ganera'
),
app AS (
    SELECT to_regrole('ganera_app')::oid AS oid
),
ajenos(oid, nombre) AS (
    SELECT 0::oid, 'PUBLIC'
    -- authenticator: en Supabase el rol con LOGIN de la Data API (NOINHERIT, puede ser los otros tres).
    UNION ALL SELECT r.oid, r.rolname FROM pg_roles r
    WHERE r.rolname IN ('anon', 'authenticated', 'service_role', 'authenticator')
),
tablas AS (
    SELECT c.oid, c.relname, c.relowner, c.relacl, c.relrowsecurity,
           CASE
               WHEN c.relname = 'flyway_schema_history' THEN 'flyway'
               WHEN c.relname IN (SELECT tabla FROM solo_insercion) THEN 'solo_insercion'
               ELSE 'normal'
           END AS clase
    FROM pg_class c JOIN esquema e ON c.relnamespace = e.oid
    WHERE c.relkind IN ('r', 'p')
),
privilegios_app_tablas AS (
    SELECT t.relname, t.clase,
           coalesce((SELECT array_agg(DISTINCT a.privilege_type ORDER BY a.privilege_type)
                     FROM aclexplode(coalesce(t.relacl, acldefault('r', t.relowner))) a, app
                     WHERE a.grantee = app.oid), '{}') AS tiene
    FROM tablas t
),
politicas_esperadas(clase, nombre, cmd, qual, with_check) AS (
    VALUES ('normal', 'ganera_app_todo', 'ALL', 'true', 'true'),
           ('solo_insercion', 'ganera_app_lectura', 'SELECT', 'true', NULL),
           ('solo_insercion', 'ganera_app_insercion', 'INSERT', NULL, 'true')
),
problemas(objeto, problema) AS (
    -- Esquema
    SELECT 'ganera', 'no existe el esquema'
    WHERE NOT EXISTS (SELECT 1 FROM esquema)
    UNION ALL
    SELECT 'ganera_app', 'no existe el rol'
    WHERE (SELECT oid FROM app) IS NULL
    UNION ALL
    SELECT 'esquema ' || e.nspname, 'privilegio ' || a.privilege_type || ' para ' || aj.nombre
    FROM esquema e
    CROSS JOIN LATERAL aclexplode(coalesce(e.nspacl, acldefault('n', e.nspowner))) a
    JOIN ajenos aj ON aj.oid = a.grantee
    UNION ALL
    SELECT 'esquema ' || e.nspname, 'ganera_app tiene ' || a.privilege_type || ' (solo debe tener USAGE)'
    FROM esquema e CROSS JOIN app
    CROSS JOIN LATERAL aclexplode(coalesce(e.nspacl, acldefault('n', e.nspowner))) a
    WHERE a.grantee = app.oid AND a.privilege_type <> 'USAGE'
    UNION ALL
    SELECT 'esquema ' || e.nspname, 'ganera_app no tiene USAGE'
    FROM esquema e, app
    WHERE app.oid IS NOT NULL AND NOT EXISTS (
        SELECT 1 FROM aclexplode(coalesce(e.nspacl, acldefault('n', e.nspowner))) a
        WHERE a.grantee = app.oid AND a.privilege_type = 'USAGE')
    UNION ALL
    SELECT 'esquema ' || e.nspname, 'es de ganera_app'
    FROM esquema e, app WHERE e.nspowner = app.oid
    -- Tablas: RLS
    UNION ALL
    SELECT 'tabla ' || t.relname, 'sin RLS'
    FROM tablas t WHERE NOT t.relrowsecurity
    -- Tablas: politicas que faltan o que sobran
    UNION ALL
    SELECT 'tabla ' || t.relname, 'falta la politica ' || pe.nombre
    FROM tablas t JOIN politicas_esperadas pe ON pe.clase = t.clase
    WHERE NOT EXISTS (
        SELECT 1 FROM pg_policies p
        WHERE p.schemaname = 'ganera' AND p.tablename = t.relname AND p.policyname = pe.nombre
          AND p.permissive = 'PERMISSIVE' AND p.cmd = pe.cmd AND p.roles = ARRAY['ganera_app']::name[]
          AND p.qual IS NOT DISTINCT FROM pe.qual AND p.with_check IS NOT DISTINCT FROM pe.with_check)
    UNION ALL
    SELECT 'tabla ' || t.relname, 'politica no esperada ' || p.policyname
    FROM tablas t JOIN pg_policies p ON p.schemaname = 'ganera' AND p.tablename = t.relname
    WHERE NOT EXISTS (
        SELECT 1 FROM politicas_esperadas pe
        WHERE pe.clase = t.clase AND pe.nombre = p.policyname AND p.permissive = 'PERMISSIVE' AND p.cmd = pe.cmd
          AND p.roles = ARRAY['ganera_app']::name[]
          AND p.qual IS NOT DISTINCT FROM pe.qual AND p.with_check IS NOT DISTINCT FROM pe.with_check)
    -- Tablas: privilegios ajenos y de ganera_app
    UNION ALL
    SELECT 'tabla ' || t.relname, 'privilegio ' || a.privilege_type || ' para ' || aj.nombre
    FROM tablas t
    CROSS JOIN LATERAL aclexplode(coalesce(t.relacl, acldefault('r', t.relowner))) a
    JOIN ajenos aj ON aj.oid = a.grantee
    UNION ALL
    SELECT 'tabla ' || p.relname,
           'ganera_app tiene {' || array_to_string(p.tiene, ',') || '} y debe tener {' ||
           array_to_string(CASE p.clase
                               WHEN 'normal' THEN ARRAY['DELETE', 'INSERT', 'SELECT', 'UPDATE']
                               WHEN 'solo_insercion' THEN ARRAY['INSERT', 'SELECT']
                               ELSE ARRAY[]::text[] END, ',') || '}'
    FROM privilegios_app_tablas p, app
    WHERE app.oid IS NOT NULL
      AND p.tiene IS DISTINCT FROM (CASE p.clase
                                        WHEN 'normal' THEN ARRAY['DELETE', 'INSERT', 'SELECT', 'UPDATE']
                                        WHEN 'solo_insercion' THEN ARRAY['INSERT', 'SELECT']
                                        ELSE ARRAY[]::text[] END)
    -- Vistas: ningun privilegio ajeno
    UNION ALL
    SELECT 'vista ' || c.relname, 'privilegio ' || a.privilege_type || ' para ' || aj.nombre
    FROM pg_class c JOIN esquema e ON c.relnamespace = e.oid
    CROSS JOIN LATERAL aclexplode(coalesce(c.relacl, acldefault('r', c.relowner))) a
    JOIN ajenos aj ON aj.oid = a.grantee
    WHERE c.relkind IN ('v', 'm', 'f')
    -- Secuencias
    UNION ALL
    SELECT 'secuencia ' || c.relname, 'privilegio ' || a.privilege_type || ' para ' || aj.nombre
    FROM pg_class c JOIN esquema e ON c.relnamespace = e.oid
    CROSS JOIN LATERAL aclexplode(coalesce(c.relacl, acldefault('s', c.relowner))) a
    JOIN ajenos aj ON aj.oid = a.grantee
    WHERE c.relkind = 'S'
    UNION ALL
    SELECT 'secuencia ' || c.relname, 'ganera_app no tiene exactamente USAGE'
    FROM pg_class c JOIN esquema e ON c.relnamespace = e.oid, app
    WHERE c.relkind = 'S' AND app.oid IS NOT NULL
      AND coalesce((SELECT array_agg(DISTINCT a.privilege_type ORDER BY a.privilege_type)
                    FROM aclexplode(coalesce(c.relacl, acldefault('s', c.relowner))) a
                    WHERE a.grantee = app.oid), '{}') IS DISTINCT FROM ARRAY['USAGE']
    -- Funciones y procedimientos (sin ACL, PUBLIC tiene EXECUTE por defecto)
    UNION ALL
    SELECT 'funcion ' || p.oid::regprocedure::text, 'privilegio ' || a.privilege_type || ' para ' || aj.nombre
    FROM pg_proc p JOIN esquema e ON p.pronamespace = e.oid
    CROSS JOIN LATERAL aclexplode(coalesce(p.proacl, acldefault('f', p.proowner))) a
    JOIN ajenos aj ON aj.oid = a.grantee
    -- ganera_app no es dueno de nada en el esquema
    UNION ALL
    SELECT 'objeto ' || c.relname, 'es de ganera_app'
    FROM pg_class c JOIN esquema e ON c.relnamespace = e.oid, app
    WHERE c.relowner = app.oid
    UNION ALL
    SELECT 'funcion ' || p.oid::regprocedure::text, 'es de ganera_app'
    FROM pg_proc p JOIN esquema e ON p.pronamespace = e.oid, app
    WHERE p.proowner = app.oid
    UNION ALL
    SELECT 'tipo ' || ty.typname, 'es de ganera_app'
    FROM pg_type ty JOIN esquema e ON ty.typnamespace = e.oid, app
    WHERE ty.typowner = app.oid
    -- Columnas (attacl): nada para los ajenos; nada para ganera_app en flyway_schema_history
    UNION ALL
    SELECT 'columna ' || t.relname || '.' || at.attname, 'privilegio ' || a.privilege_type || ' para ' || aj.nombre
    FROM tablas t JOIN pg_attribute at ON at.attrelid = t.oid AND at.attacl IS NOT NULL
    CROSS JOIN LATERAL aclexplode(at.attacl) a
    JOIN ajenos aj ON aj.oid = a.grantee
    UNION ALL
    SELECT 'columna ' || t.relname || '.' || at.attname, 'ganera_app tiene ' || a.privilege_type
    FROM tablas t JOIN pg_attribute at ON at.attrelid = t.oid AND at.attacl IS NOT NULL
    CROSS JOIN LATERAL aclexplode(at.attacl) a
    CROSS JOIN app
    WHERE t.clase = 'flyway' AND a.grantee = app.oid
    -- Roles: membresias. Nadie hereda ni puede hacer SET ROLE a ganera_app (la membresia automatica de PostgreSQL
    -- 16+ de quien crea el rol, con ADMIN y sin INHERIT ni SET, no da privilegios); ganera_app no es miembro de
    -- nada; a ganera_propietario solo se le puede hacer SET ROLE, nunca heredarlo.
    UNION ALL
    SELECT 'rol ganera_app', 'tiene como miembro a ' || m.member::regrole::text
           || ' (hereda sus permisos o puede hacer SET ROLE)'
    FROM pg_auth_members m, app
    WHERE m.roleid = app.oid AND (m.inherit_option OR m.set_option)
    UNION ALL
    SELECT 'rol ganera_app', 'es miembro de ' || m.roleid::regrole::text
    FROM pg_auth_members m, app
    WHERE m.member = app.oid
    UNION ALL
    SELECT 'rol ganera_propietario', m.member::regrole::text || ' hereda sus permisos (INHERIT; solo debe tener SET)'
    FROM pg_auth_members m
    WHERE m.roleid = to_regrole('ganera_propietario') AND m.inherit_option
    -- Ni siquiera SET para un rol de Supabase: con SET ROLE seria dueno de todas las tablas (la RLS no le aplica).
    -- Quien ejecuta el 01 (postgres en Supabase) si lo tiene, y no esta en esta lista. ganera_app ya sale arriba
    -- («es miembro de»).
    UNION ALL
    SELECT 'rol ganera_propietario', aj.nombre || ' puede hacer SET ROLE (seria dueno de todas las tablas)'
    FROM pg_auth_members m JOIN ajenos aj ON aj.oid = m.member
    WHERE m.roleid = to_regrole('ganera_propietario') AND m.set_option
    -- Roles: ADMIN sobre los roles de Ganera. Con ADMIN un rol puede concederse luego INHERIT o SET a si mismo. Se
    -- marca si lo tiene un rol de Supabase o uno de Ganera; no se marca a quien creo el rol (la membresia
    -- automatica de PostgreSQL 16+, con ADMIN: en Supabase es postgres, en los tests admin_supabase), porque su
    -- nombre cambia segun el entorno y ya es quien administra los roles.
    UNION ALL
    SELECT 'rol ' || m.roleid::regrole::text, m.member::regrole::text
           || ' tiene ADMIN (podria darse INHERIT o SET a si mismo)'
    FROM pg_auth_members m
    WHERE m.roleid IN (to_regrole('ganera_app'), to_regrole('ganera_propietario')) AND m.admin_option
      AND m.member IN (SELECT oid FROM ajenos UNION ALL SELECT to_regrole('ganera_app')
                       UNION ALL SELECT to_regrole('ganera_propietario'))
    -- Roles: cadenas. Un rol de Supabase que llega a un rol de Ganera a traves de otros roles (anon -> X ->
    -- ganera_propietario) no sale en las clausulas de arriba, que solo miran membresias directas. Llega si puede
    -- hacerse algun rol R (el mismo, o R por una cadena de SET: pg_has_role SET) que hereda el de Ganera
    -- (pg_has_role USAGE, cadena de INHERIT), puede hacer SET ROLE a el (SET) o tiene ADMIN sobre el, directo o
    -- heredado (con ADMIN se lo concede a si mismo). No se repite lo que ya marcan las clausulas directas (una
    -- membresia directa con INHERIT, SET o ADMIN); una directa sin ninguna opcion no tapa la cadena. PUBLIC no puede
    -- ser miembro de un rol. Ojo: pg_has_role es true para un superusuario, asi que poder hacerse superusuario
    -- tambien sale (y es un problema).
    UNION ALL
    SELECT 'rol ' || g.rolname, aj.nombre || ' llega por una cadena de roles (hereda sus permisos, puede hacer SET ROLE o tiene ADMIN)'
    FROM ajenos aj
    CROSS JOIN pg_roles g
    WHERE aj.oid <> 0
      AND g.rolname IN ('ganera_app', 'ganera_propietario')
      AND NOT EXISTS (SELECT 1 FROM pg_auth_members d
                      WHERE d.roleid = g.oid AND d.member = aj.oid
                        AND (d.inherit_option OR d.set_option OR d.admin_option))
      AND EXISTS (SELECT 1 FROM pg_roles r
                  WHERE (r.oid = aj.oid OR pg_has_role(aj.oid, r.oid, 'SET'))
                    AND r.oid <> g.oid
                    AND (pg_has_role(r.oid, g.oid, 'USAGE') OR pg_has_role(r.oid, g.oid, 'SET')
                         OR EXISTS (SELECT 1 FROM pg_auth_members a
                                    WHERE a.roleid = g.oid AND a.admin_option
                                      AND (a.member = r.oid OR pg_has_role(r.oid, a.member, 'USAGE')))))
    -- Roles: atributos de los dos roles de Ganera
    UNION ALL
    SELECT 'rol ' || r.rolname, 'tiene ' || x.atributo
    FROM pg_roles r
    CROSS JOIN LATERAL (VALUES ('SUPERUSER', r.rolsuper), ('BYPASSRLS', r.rolbypassrls),
                               ('CREATEROLE', r.rolcreaterole), ('CREATEDB', r.rolcreatedb),
                               ('REPLICATION', r.rolreplication)) AS x(atributo, activo)
    WHERE r.rolname IN ('ganera_propietario', 'ganera_app') AND x.activo
    -- Auditoria (V25/V26): la tabla debe existir para que haya algo que proteger (solo si el esquema existe).
    UNION ALL
    SELECT 'tabla auditoria', 'no existe (la crea V25)'
    FROM esquema e
    WHERE NOT EXISTS (SELECT 1 FROM tablas t WHERE t.relname = 'auditoria')
    -- Auditoria: cada trigger esperado que falta, esta desactivado o no es exacto (el primer motivo que aplica).
    UNION ALL
    SELECT objeto, problema FROM (
        SELECT 'trigger auditoria.' || e.nombre AS objeto,
               CASE
                   WHEN tg.oid IS NULL THEN 'falta'
                   WHEN tg.tgenabled <> 'O' THEN 'desactivado (tgenabled = ' || tg.tgenabled::text || ', debe ser O)'
                   WHEN (tg.tgtype & 2) = 0 THEN 'no es BEFORE'
                   WHEN e.es_fila AND (tg.tgtype & 1) = 0 THEN 'no es FOR EACH ROW'
                   WHEN NOT e.es_fila AND (tg.tgtype & 1) = 1 THEN 'no es FOR EACH STATEMENT'
                   WHEN (tg.tgtype & 60) <> e.eventos_ok THEN 'eventos ' || array_to_string(ARRAY[
                           CASE WHEN (tg.tgtype & 4) <> 0 THEN 'INSERT' END,
                           CASE WHEN (tg.tgtype & 8) <> 0 THEN 'DELETE' END,
                           CASE WHEN (tg.tgtype & 16) <> 0 THEN 'UPDATE' END,
                           CASE WHEN (tg.tgtype & 32) <> 0 THEN 'TRUNCATE' END], ',')
                       || ' (deben ser ' || e.eventos_texto || ')'
                   WHEN tg.tgattr::text <> '' THEN 'solo para algunas columnas'
                   WHEN tg.tgqual IS NOT NULL THEN 'tiene condicion WHEN'
                   WHEN tg.tgfoid IS DISTINCT FROM to_regprocedure('ganera.' || e.nombre || '()')
                       THEN 'no llama a ganera.' || e.nombre || '()'
                   ELSE NULL
               END AS problema
        FROM triggers_auditoria e
        JOIN tablas ta ON ta.relname = 'auditoria'
        LEFT JOIN pg_trigger tg ON tg.tgrelid = ta.oid AND NOT tg.tgisinternal AND tg.tgname = e.nombre
    ) z WHERE problema IS NOT NULL
    -- Auditoria: cualquier trigger que no sea uno de los dos esperados.
    UNION ALL
    SELECT 'trigger auditoria.' || tg.tgname, 'no esperado'
    FROM tablas ta JOIN pg_trigger tg ON tg.tgrelid = ta.oid AND NOT tg.tgisinternal
    WHERE ta.relname = 'auditoria'
      AND tg.tgname NOT IN ('auditoria_sin_cambios', 'auditoria_sin_borrado')
    -- Auditoria: las funciones de los triggers deben ser de ganera_propietario (no de ganera_app ni de otro).
    UNION ALL
    SELECT 'funcion ' || p.oid::regprocedure::text,
           'es de ' || pg_get_userbyid(p.proowner) || ' (debe ser de ganera_propietario)'
    FROM pg_proc p JOIN esquema e ON p.pronamespace = e.oid
    WHERE p.proname IN ('auditoria_sin_cambios', 'auditoria_sin_borrado')
      AND p.proowner <> to_regrole('ganera_propietario')
    -- Auditoria: nadie vigilado puede hacer SET sobre session_replication_role (con replica saltaria los triggers).
    UNION ALL
    SELECT 'parametro session_replication_role',
           r.rolname || ' puede cambiarlo (SET; con replica se saltaria los triggers de auditoria)'
    FROM (VALUES ('anon'), ('authenticated'), ('service_role'), ('authenticator'),
                 ('ganera_app'), ('ganera_propietario')) AS v(nombre)
    JOIN pg_roles r ON r.rolname = v.nombre
    -- Un superusuario puede cambiar cualquier parametro: ya lo marca la clausula de atributos (no se duplica aqui).
    WHERE NOT r.rolsuper AND has_parameter_privilege(r.oid, 'session_replication_role', 'SET')
    -- Zona horaria de la base de datos
    UNION ALL
    SELECT 'base de datos ' || current_database(), 'sin TimeZone=UTC'
    WHERE NOT EXISTS (
        SELECT 1 FROM pg_db_role_setting s JOIN pg_database d ON d.oid = s.setdatabase
        WHERE d.datname = current_database() AND s.setrole = 0 AND 'TimeZone=UTC' = ANY (s.setconfig))
)
SELECT objeto, problema FROM problemas ORDER BY objeto, problema;
