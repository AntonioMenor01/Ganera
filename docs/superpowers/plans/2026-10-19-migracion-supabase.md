# Migración de la base de datos a Supabase — PLAN APROBADO (2026-10-19)

## Decisiones de Antonio (2026-10-19) — prevalecen sobre el resto del plan

- **Push** de `28ea24d` a `origin/main` aprobado y hecho antes de empezar. Trabajo en la rama `supabase`.
- **S1** sí: callback `afterMigrate` en `db/{vendor}`, idempotente (mira `pg_policies` antes de `CREATE POLICY`).
  **Admite excepciones por tabla**: la futura tabla de auditoría no puede recibir `UPDATE` ni `DELETE` para
  `ganera_app` (el callback tiene una lista de tablas «solo inserción y lectura» que la auditoría rellenará; hoy vacía
  salvo para el test). Sin permisos de `ganera_app` sobre `flyway_schema_history` y sin `CREATE` en el esquema `ganera`.
  El test de guardia comprueba también esto último y **se mantiene aunque exista el callback**.
- **S2, S3** sí.
- **S4** sí, `\password` de `psql` 17. T0 incluye cómo instalar solo las herramientas de línea de comandos en Windows.
  Alternativa si no se puede: pegar el hash SCRAM (`ALTER ROLE … PASSWORD 'SCRAM-SHA-256$…'`), **nunca la contraseña en
  claro**.
- **S5** sí: certificados autofirmados en `src/test/resources`, con nombres claramente de test. La CA de Supabase en
  `src/main/resources`.
- **S6** sí, también `service_role`.
- **S7** sí: se vacía el esquema; **se conservan los roles**.
- **Riesgos del smoke:** si el pooler no acepta `rol.<ref>`, parar y avisar; **nunca conectar la app como `postgres`**.
  Si `ALTER DATABASE … SET timezone` no está permitido, `ALTER ROLE … SET timezone` en los dos roles y adaptar el test.
- T1–T4 con Superpowers (implementador + revisor) y TDD. T0 y T5 los hace Antonio, de uno en uno, con comandos sin
  valores. Al acabar T4, antes del smoke: cifras (backend ~1027, frontend 1084 en 50, build y lint) y lista de rutas.
  **Sin commit hasta que lo apruebe.**

Fecha: 2026-10-19. Parte de `main` = `28ea24d` (backend 1001: 1000 + 1 omitido; frontend 1084 en 50; lint con los 3
avisos conocidos). `origin/main` sigue en `b4b786c`: **el push de `28ea24d` lo hace Antonio**.
**Nada de código, rama ni commit hasta que Antonio apruebe este plan.**

Base: «Decisiones 19/10» de `.superpowers/sdd/progress.md`. Supabase sustituye **solo** a la base de datos; el backend
sigue en Clever (París) y el frontend irá a Vercel (`app.ganera.es`, tarea aparte). HSTS, CORS fail-closed, sin usuario
por defecto y PWA no cambian. La auditoría (trigger, `403` entre gestorías, IP) es la **tarea siguiente**: aquí solo
roles y permisos generales.

Datos del proyecto Supabase (ya creado por Antonio): región **eu-west-1 (Irlanda)**, **PostgreSQL 17.11**, zona horaria
UTC, plan **Free**, **Data API desactivada**.

---

## Comprobaciones hechas al preparar el plan

- **Flyway de Boot 3.4.1 (Flyway 10.20.1) admite PostgreSQL 17 sin avisos.** Leído en el bytecode de
  `PostgreSQLDatabase.ensureSupported`: mínimo 9.0, y `recommendFlywayUpgradeIfNecessaryForMajorVersion("17")`, que solo
  avisa con una versión mayor que 17. En T1 queda un test que lo vigila (sin `WARN` de `org.flywaydb` al migrar en 17).
- **Binarios embebidos:** `io.zonky.test.postgres:embedded-postgres-binaries-bom` tiene **17.11.0** en Maven Central
  (la misma versión menor que Supabase), con el binario de Windows (`HTTP 200`).
- **pgjdbc 42.7.4** (el de Boot 3.4.1): `org.postgresql.ssl.SingleCertValidatingFactory` acepta
  `sslfactoryarg=classpath:…`, y `MakeSSL` comprueba el nombre del host (`verifyPeerName`) **después** de crear el
  socket con la fábrica, así que `sslmode=verify-full` sigue verificando el host con una fábrica propia. Así la CA va
  en el classpath sin depender de una ruta de disco en Clever.
- **pgjdbc manda siempre `TimeZone` en el arranque de la sesión** (la de la JVM; en Clever `TZ=UTC`). La zona de la
  base solo decide en sesiones que no son JDBC: el editor SQL de Supabase, `psql` y el SQL a mano de la siembra
  (`DEFAULT now()`, ver `DefaultNowPostgresTest`). Por eso se fija en la base **y** se comprueba en el catálogo.
- Las tablas usan `BIGSERIAL`: `ganera_app` necesita `USAGE` en las secuencias, no solo permisos en las tablas.

## Decisiones que te pido (mi recomendación primero)

- **S1 — RLS y permisos con un callback `afterMigrate` de Flyway en `db/postgresql`** (recomendado), idempotente, que
  en cada migración recorre **todas** las tablas del esquema: activa RLS, crea la política si falta, revoca a `PUBLIC`,
  `anon` y `authenticated`, y da a `ganera_app` exactamente lo que necesita. Así una tabla de una migración futura queda
  protegida sin acordarse, y el test de guardia (T2) lo vigila igualmente. *Alternativa:* una migración versionada V24
  solo-Postgres para las tablas de hoy y, por cada tabla nueva, una migración gemela en `db/postgresql`; el test la
  pillaría si se olvida, pero obliga a duplicar cada migración que cree tablas.
- **S2 — Nombres de variables:** `SUPABASE_DB_HOST`, `SUPABASE_DB_PORT`, `SUPABASE_DB_NAME`, `SUPABASE_DB_USER`,
  `SUPABASE_DB_PASSWORD` (app) y `SUPABASE_FLYWAY_USER`, `SUPABASE_FLYWAY_PASSWORD` (propietario). Todas sin valor por
  defecto. **El puerto va en variable** porque los tests levantan el PostgreSQL embebido en un puerto libre; el valor
  en Clever es **5432** (modo sesión). El 6543 (modo transacción) **no vale**: rompe los *prepared statements* y el
  bloqueo consultivo de Flyway; lo dice `.env.example` y lo comprueba el smoke.
- **S3 — Nombres de roles:** `ganera_propietario` (dueño del esquema y de las tablas; solo Flyway) y `ganera_app`.
- **S4 — Contraseñas de los roles:** con `\password` de `psql` 17 desde tu terminal (el cliente cifra la contraseña
  con SCRAM antes de mandarla: no queda en claro en ningún log ni en el historial del editor). Hace falta instalar solo
  las «Command Line Tools» de PostgreSQL 17. *Alternativa:* la pantalla de roles del panel de Supabase, si deja poner
  contraseña. Nunca `ALTER ROLE … PASSWORD '…'` en el editor SQL.
- **S5 — Certificados de prueba para SSL:** una CA y un certificado de servidor **solo de test**, generados una vez y
  guardados en `src/test/resources/ssl-pruebas/` (válidos 100 años, `localhost` como único nombre, con un `LEEME.md`
  de cómo regenerarlos). No protegen nada real. *Alternativa:* generarlos al arrancar la suite con `keytool`, sin
  ficheros de clave en el repo, a cambio de más código de test y de escribir en `target/test-classes`.
- **S6 — `service_role` también en el `REVOKE`** (recomendado). No está en las decisiones del 19/10, pero en Supabase
  ese rol tiene `BYPASSRLS`; con la Data API apagada no se usa, y revocarle todo no cuesta nada.
- **S7 — Después del smoke real, vaciar el esquema** (`DROP SCHEMA ganera CASCADE` y volver a ejecutar el script 01) para
  que el despliegue en Clever empiece de cero con la siembra de la demo, que no se puede repetir.

---

## Diseño

### Perfil `clever` (`application-clever.yml`)

```yaml
spring:
  datasource:
    url: jdbc:postgresql://${SUPABASE_DB_HOST}:${SUPABASE_DB_PORT}/${SUPABASE_DB_NAME}?sslmode=verify-full&sslfactory=org.postgresql.ssl.SingleCertValidatingFactory&sslfactoryarg=classpath:supabase/ca-raiz.crt&currentSchema=ganera&logServerErrorDetail=false
    username: ${SUPABASE_DB_USER}        # ganera_app.<ref del proyecto> en el pooler
    password: ${SUPABASE_DB_PASSWORD}
    driver-class-name: org.postgresql.Driver
    hikari:
      maximum-pool-size: 5
  flyway:
    user: ${SUPABASE_FLYWAY_USER}        # ganera_propietario.<ref del proyecto>
    password: ${SUPABASE_FLYWAY_PASSWORD}
    default-schema: ganera
    schemas: ganera
    create-schemas: false                # el esquema lo crea el script 01; sin él, la app no arranca
```

- SSL, CA y esquema **fijos en el YAML**; las `POSTGRESQL_ADDON_*` desaparecen.
- Con `spring.flyway.user` Boot crea para Flyway una conexión aparte, sin pool, contra la misma URL; la app usa
  `ganera_app` con Hikari a 5 (el pooler del plan Free admite de sobra 5 + 1).
- La CA de Supabase va en `src/main/resources/supabase/ca-raiz.crt` (la descargas tú del panel: Database → SSL
  Configuration). En los tests, `src/test/resources/supabase/ca-raiz.crt` es la CA de prueba y tapa a la real en el
  classpath (como ya pasa con `application.yml`); un test lee la real **del disco** para que no se pierda.
- Variables nuevas: nunca `GANERA_*` (binding relajado) ni `DATABASE_*` en Clever.

### Roles, esquema y zona horaria — `backend/supabase/01-preparar-roles-y-esquema.sql`

Lo ejecutas tú en el editor SQL de Supabase (como `postgres`). Sin contraseñas, idempotente, en una transacción:

1. Crea, si no existen, `ganera_propietario` y `ganera_app`: `LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION
   NOBYPASSRLS`. Crea `anon` y `authenticated` (`NOLOGIN`) **solo si no existen** (en Supabase existen; esto es para el
   embebido y el Postgres local).
2. `GRANT ganera_propietario TO current_user WITH SET TRUE, INHERIT FALSE`: en PostgreSQL 16+ un rol con `CREATEROLE`
   no puede crear un esquema a nombre de otro ni hacer `SET ROLE` sin esto. Se usa para la siembra y para el `DROP`.
3. `CREATE SCHEMA IF NOT EXISTS ganera AUTHORIZATION ganera_propietario`; `REVOKE ALL ON SCHEMA ganera FROM PUBLIC,
   anon, authenticated, service_role` (si existe); `GRANT USAGE ON SCHEMA ganera TO ganera_app`.
4. `ALTER DATABASE <current_database()> SET timezone TO 'UTC'` (con `format('%I')` en un bloque `DO`).

`backend/supabase/02-comprobar-seguridad.sql`: una consulta que devuelve **una fila por problema** (tabla sin RLS, sin
la política, con permisos para `PUBLIC`/`anon`/`authenticated`/`service_role`, `ganera_app` con más permisos de los
debidos o con alguno en `flyway_schema_history`, esquema con `USAGE`/`CREATE` ajeno, base sin `TimeZone=UTC`). Lo
correcto es **0 filas**. La usa el test de guardia (leída del disco) y la ejecutas tú en el smoke: una sola fuente.

### Callback `db/postgresql/afterMigrate__seguridad_esquema.sql` (S1)

Sobre `${flyway:defaultSchema}` (en Clever, `ganera`; en el resto de tests en PostgreSQL, `public`), como propietario:

- Falla con un mensaje claro si no existen `ganera_app`, `anon` o `authenticated` (fail-closed: nunca deja tablas sin
  proteger en silencio).
- Por cada tabla (`relkind` `r`/`p`): `ENABLE ROW LEVEL SECURITY` (sin `FORCE`: el propietario, o sea Flyway y la
  siembra, no la cumple); `REVOKE ALL … FROM PUBLIC, anon, authenticated, service_role`; y salvo
  `flyway_schema_history`: `GRANT SELECT, INSERT, UPDATE, DELETE … TO ganera_app` y, si falta, `CREATE POLICY ganera_app_todo
  ON … FOR ALL TO ganera_app USING (true) WITH CHECK (true)`. En `flyway_schema_history`, `REVOKE ALL … FROM ganera_app`
  (RLS activada y sin política: nada para la app).
- Secuencias: `REVOKE ALL` a los mismos y `GRANT USAGE … TO ganera_app`. Funciones: `REVOKE EXECUTE` a `PUBLIC`, `anon`,
  `authenticated` (hoy no hay; las traerá la auditoría).

`SELECT … FOR UPDATE` (bloqueo de trámites) necesita `UPDATE`, que ya tiene. Las comprobaciones de claves ajenas no
pasan por RLS.

### Migraciones solo-Postgres: `db/{vendor}`

- `spring.flyway.locations: classpath:db/migration,classpath:db/{vendor}` en el `application.yml` principal **y** en el
  de test (el de test tapa al principal en el classpath). Boot pone `postgresql` o `h2`.
- Carpetas `src/main/resources/db/postgresql/` (el callback; la auditoría añadirá su trigger) y
  `src/main/resources/db/h2/` con un `LEEME.md` (Maven no copia carpetas vacías y Flyway avisaría de una ubicación que
  no existe).
- **Regla de numeración** (va a CLAUDE.md): la versión es global entre las dos carpetas; una migración solo-Postgres
  deja un hueco en H2, que Flyway admite. Nunca dos ficheros con la misma versión.
- Las migraciones Java (`db.migration.V18…`) siguen donde están.
- `docker-compose.yml`: `postgres:17-alpine` y el script 01 montado en `/docker-entrypoint-initdb.d/` (sin roles, el
  callback no deja arrancar contra el Postgres local). Un volumen antiguo hay que borrarlo; lo digo en CLAUDE.md.

### Reproducción en el PostgreSQL embebido

`PostgresEmbebido` (un servidor por JVM): binarios 17.11.0, `ssl=on` con el certificado de prueba (S5), y al arrancar
crea un rol `admin_supabase` (`CREATEROLE CREATEDB`, **sin** superusuario), que imita al `postgres` de Supabase. Cada
`nuevaBaseDeDatos()` crea la base con dueño `admin_supabase` y ejecuta el script 01 **como `admin_supabase`** (leído del
disco), así que un permiso que en Supabase faltaría también falta en el test. Los tests que ya existían siguen
conectando como superusuario al esquema `public`; el callback también los protege. El smoke real cubre lo que el
embebido no puede imitar: el pooler (usuarios `rol.<ref>`) y la CA real.

---

## Tareas (Superpowers: un implementador + un revisor independiente por tarea; TDD; cada cambio de seguridad con su
mutación en rojo, restaurada con `cp` + `cmp`; los subagentes nunca tocan el índice de git)

### T0 — Tú, antes de T3 (sin secretos)

Descarga la CA del proyecto (Database → SSL Configuration → «Download certificate») a una carpeta fuera del repo y dime
la ruta. Es pública; la copio a `src/main/resources/supabase/ca-raiz.crt`.

### T1 — PostgreSQL 17 en los tests y `db/{vendor}` (≈0,5 días)

- BOM de binarios `16.15.0` → `17.11.0` (y su comentario); `startsWith("PostgreSQL 16.")` → `"PostgreSQL 17."` en
  `FlywayPostgresTest` y `PerfilCleverPostgresTest`; Javadoc de `PostgresEmbebido` y de los tests que dicen «16».
- Ubicaciones `{vendor}` en los dos `application.yml`; carpetas `db/postgresql/` (vacía salvo un `LEEME.md` hasta T2) y
  `db/h2/`. El `Flyway` programático de los tests de PostgreSQL pasa a un ayudante `PostgresEmbebido.flyway(ds)` con
  las dos ubicaciones.
- **Tests nuevos (3):** `ConfiguracionFlywayTest` (lee los dos YAML del disco: las dos ubicaciones);
  `FlywayH2VendorTest` (contexto H2: V1–V23 aplicadas, ningún `WARN` de ubicación no encontrada); en
  `FlywayPostgresTest`, `migrarEnPostgres17NoDejaAvisosDeFlyway` (captura de log: ningún `WARN` de `org.flywaydb`).
- **Mutaciones:** BOM a 16 → rojo; quitar `{vendor}` de un YAML → rojo.

### T2 — Roles, esquema, RLS y zona horaria (≈1 día)

- Scripts `backend/supabase/01-…` y `02-…`, callback, `PostgresEmbebido` con `admin_supabase` y el script 01 por base,
  `docker-compose.yml`.
- **Tests nuevos (11):**
  - `PreparacionBaseDeDatosPostgresTest` (4): roles con los atributos exactos (sin superusuario, sin `BYPASSRLS`, sin
    `CREATEROLE`); esquema de `ganera_propietario` sin `USAGE` ni `CREATE` para `PUBLIC`/`anon`/`authenticated`; el
    script se ejecuta dos veces sin error y sin cambios; **la base queda en `TimeZone=UTC`** (en `pg_db_role_setting`; no
    se puede comprobar con `SHOW` desde JDBC, porque pgjdbc impone la zona de la JVM al conectar).
  - `SeguridadEsquemaPostgresTest` (7), con Flyway como `ganera_propietario` sobre `ganera`: el script 02 da 0 filas;
    cada tabla tiene RLS y la política exacta (`FOR ALL`, `TO ganera_app`, `USING true`, `WITH CHECK true`);
    `ganera_app` tiene solo `SELECT/INSERT/UPDATE/DELETE` en las tablas de negocio, `USAGE` en las secuencias, nada en
    `flyway_schema_history` y no puede crear tablas; una concesión «hostil» a `anon`/`authenticated`/`PUBLIC` hecha
    antes de migrar queda revocada; **una tabla futura** (ubicación solo de test con un `V9999__tabla_futura.sql`)
    sale con RLS y política; el script 02 **sí** detecta una tabla a la que se le quita la RLS a mano (la guardia
    funciona); sin el rol `ganera_app` la migración falla con el mensaje del callback.
- **Mutaciones:** quitar `ENABLE ROW LEVEL SECURITY` del callback, el `REVOKE` a `anon`, el `ALTER DATABASE` del script
  01, o el bucle (solo tablas de hoy) → cada una en rojo.

### T3 — Perfil `clever` contra Supabase (≈1 día; necesita T0)

- `application-clever.yml` como en el diseño; CA real en `main`, CA de prueba y certificados en `test` (S5).
- Los tres `*PerfilCleverPostgresTest` (`PerfilClever`, `CorsPerfilClever`, `HstsPerfilClever`) pasan las siete
  variables nuevas con SSL real contra el embebido, `localhost` como host, `ganera_app`/`ganera_propietario` como
  usuarios. `PerfilCleverPostgresTest` comprueba además: la URL exacta del pool; `pg_stat_ssl.ssl = true` en la
  conexión de la app; `current_user = ganera_app` y `current_schema() = ganera`; `installed_by = ganera_propietario` en
  las 23 filas; tablas de `ganera_propietario`; `maximumPoolSize = 5`; `logServerErrorDetail` desactivado.
- **Tests nuevos (12):**
  - `BaseDeDatosPerfilCleverPostgresTest` (10): sin cada una de las siete variables, la app no arranca
    (parametrizado, 7); con `127.0.0.1` como host (el certificado solo vale para `localhost`) no arranca por
    `verify-full` (1); sin el esquema `ganera` no arranca (1); **flujo real como `ganera_app`** con RLS activa:
    onboarding → login → importar el Excel de prueba → `GET /explotaciones?q=` → aprobar un trámite (pasa por
    `SELECT … FOR UPDATE`) → job de retención (borra) (1).
  - `ConfiguracionBaseDeDatosProduccionTest` (1, lee el YAML del disco): `sslmode=verify-full`, la fábrica y la CA,
    `currentSchema=ganera`, `logServerErrorDetail=false`, Hikari 5, usuario y contraseña de Flyway, ninguna
    `POSTGRESQL_ADDON_*` y ninguna variable con valor por defecto.
  - `CaSupabaseTest` (1, del disco): la CA de `main` es un certificado X.509 de CA, no caducado, con el sujeto de
    Supabase, y distinto de la CA de prueba.
- **Mutaciones:** `sslmode=require` → rojo (el de `127.0.0.1`); sin `currentSchema` → rojo; sin
  `logServerErrorDetail=false` → rojo; Hikari 10 → rojo; Flyway sin su usuario (todo como `ganera_app`) → rojo.

### T4 — Documentación y kit (≈0,5–1 día)

- `.env.example`: bloque comentado «Perfil clever (Supabase)» con las siete variables sin valor, el puerto 5432 y el
  formato `rol.<ref>` del usuario; y las dos de Twilio que faltan, `TWILIO_WHATSAPP_FROM` y
  `TWILIO_STATUS_CALLBACK_URL`, con su comentario.
- Kit `C:\Users\Antonio\Desktop\ganera-clever\`: `LEEME.md` con el editor SQL de Supabase en vez de Adminer (paso 1: el
  script 01 y las contraseñas con `\password`; la zona horaria ya va dentro); `generador/generar.py` emite al principio
  `SET ROLE ganera_propietario; SET search_path TO ganera;` y se regenera `seed-tramites.sql`; `prueba-supabase.sh`
  nuevo para el smoke de T5 (pide el `ONBOARDING_SECRET` oculto; solo imprime PASS/FAIL). **Ensayo** del kit contra el
  embebido 17 con el perfil `clever`, SSL y los dos roles: alta, los dos Excel, siembra como `ganera_propietario` y
  `smoke.sh` en modo ensayo.
- Plan del 14/10: §1 (la base en Supabase, Irlanda), §4 (variables nuevas en lugar de `POSTGRESQL_ADDON_*`), §7 (copias
  de Supabase y RGPD), §8 (pasos 3 y 8: Supabase y script 01 en lugar del add-on y Adminer), §9, D2–D4; nota de que el
  frontend va a Vercel (tarea aparte).
- `CLAUDE.md`: bullet nuevo «Supabase» (perfil, roles, callback, regla de numeración de `db/{vendor}`, SQL a mano con
  `SET ROLE` y `search_path`, nunca `GANERA_*`/`DATABASE_*` en Clever); «C mínimo» y «Clever deployment hardening» al
  día (PostgreSQL 17, sin add-on); cifras de tests; `docker-compose`. `progress.md` y `ganera-prompts.md` al día.
- **Para el abogado** (a `progress.md`): Supabase Inc. es estadounidense aunque los datos estén en Irlanda (AWS) → su
  DPA con cláusulas tipo; y las **copias en el plan Free** (compruébalo en el panel: creo que no hay copias descargables
  hasta Pro). Riesgo de demo: en Free el proyecto **se pausa tras 7 días sin actividad**.

### T5 — Smoke real contra Supabase (tú, ≈1–2 horas; datos inventados)

En tu terminal de Git Bash, en `backend/`, con la rama de T1–T4 (antes del commit):

1. **Editor SQL de Supabase:** pega `backend/supabase/01-preparar-roles-y-esquema.sql` entero → «Success». Si falla el
   `ALTER DATABASE`, para y dímelo.
2. **Contraseñas:** genera dos con `openssl rand -base64 32` y guárdalas en tu gestor. Conéctate con `psql` como
   `postgres.<ref>` al **pooler en modo sesión** (Connect → Session pooler; puerto 5432) con `sslmode=verify-full` y la
   CA descargada, y `\password ganera_propietario`, `\password ganera_app`.
3. **Variables, sin valores en los comandos:** `read -r` para `SUPABASE_DB_HOST` (el host del pooler),
   `SUPABASE_DB_USER` (`ganera_app.<ref>`) y `SUPABASE_FLYWAY_USER` (`ganera_propietario.<ref>`); `read -rs` para las dos
   contraseñas; y `export` de todas, más `SUPABASE_DB_PORT=5432`, `SUPABASE_DB_NAME=postgres`,
   `SPRING_PROFILES_ACTIVE=clever`, `TZ=UTC`, `FRONTEND_ORIGEN=https://app.ganera.es` y los secretos generados en el
   momento: `JWT_SECRET=$(openssl rand -base64 48)`, `ENCRYPTION_KEY=$(openssl rand -base64 32)`,
   `ONBOARDING_SECRET=$(openssl rand -hex 32)` (cópialo al portapapeles para el paso 5 con
   `printf %s "$ONBOARDING_SECRET" | clip`).
4. **Arranque:** `JAVA_HOME="…/jdk-23" ./mvnw spring-boot:run`. En el log: perfil `clever`; Flyway «Successfully applied
   23 migrations to schema "ganera"»; el callback `afterMigrate`; **ningún `WARN` de Flyway**; Hikari arrancado; sin
   «Using generated security password». Si falla el SSL o el usuario del pooler, para y pégame solo el error.
5. **Petición de la app**, en otra terminal: `bash prueba-supabase.sh` (pide el secreto oculto; da de alta una gestoría
   inventada, login, importa `inventario-norte.xlsx`, `GET /explotaciones?q=` y `GET /tramites`) → todo PASS.
6. **Editor SQL:** el script `02-comprobar-seguridad.sql` → **0 filas**; `SHOW timezone` → `UTC`;
   `select installed_by, count(*) from ganera.flyway_schema_history group by 1` → `ganera_propietario`, 23; y la prueba
   de RLS: `SET ROLE ganera_app; select count(*) from ganera.explotacion;` → 15, y `SET ROLE anon; select count(*) from
   ganera.explotacion;` → *permission denied*; `RESET ROLE`.
   **Corrección de T4 (2026-10-20), sustituye a lo anterior:** desde el editor SQL `postgres` no tiene `USAGE` en el
   esquema `ganera` (solo `SET` sobre `ganera_propietario`, sin heredar) ni puede hacer `SET ROLE ganera_app` (solo
   tiene `ADMIN` sobre ese rol; y el 02 marcaría como problema a cualquiera que pudiera). Así:
   - el 02 (solo lee catálogos) y `SHOW timezone` → `UTC`, tal cual;
   - `SET ROLE ganera_propietario; select installed_by, count(*) from ganera.flyway_schema_history group by 1; RESET
     ROLE;` → `ganera_propietario`, 23;
   - lo que ve la app, con `psql` conectado como `ganera_app.<ref>` al pooler en sesión con `verify-full`:
     `select count(*) from ganera.explotacion;` → 15;
   - `SET ROLE anon; select count(*) from ganera.explotacion;` → *permission denied for schema ganera* (el error corta
     el lote: el `RESET ROLE` de detrás no se llega a ejecutar, y da igual porque el editor abre sesión por consulta).
7. **Cierre:** para la app (`taskkill` del `java.exe`), cierra la terminal (se van las variables) y, con S7,
   `SET ROLE ganera_propietario; DROP SCHEMA ganera CASCADE; RESET ROLE;` y el script 01 otra vez.

Me pegas solo PASS/FAIL, las líneas del log que te pida y el número de filas; nunca un valor de variable.

---

## Cifras esperadas

| | Hoy | Tras T1 | Tras T2 | Tras T3 | Tras T4 |
|---|---|---|---|---|---|
| Backend (`./mvnw clean test`) | 1001 (1000 + 1 omitido) | 1004 | 1015 | **1027** (1026 + 1 omitido) | 1027 |
| Frontend (`npm test`) | 1084 en 50 | = | = | = | = |
| Lint | 3 avisos | = | = | = | = |

El número final puede moverse unos pocos tests si el revisor pide partir alguno; lo importante son las mutaciones en
rojo. El frontend no se toca.

## Riesgos (los comprueba el smoke y paro si salen)

- **Usuarios propios en el pooler:** Supavisor tiene que aceptar `ganera_app.<ref>` y `ganera_propietario.<ref>`. Si no,
  la alternativa es la conexión directa, que en Free es solo IPv6 (habría que ver si Clever sale por IPv6).
- **Certificado del pooler:** que lo firme la misma CA que se descarga del panel. Si `verify-full` falla contra el
  pooler, paramos y decidimos (no se baja a `require`).
- **`ALTER DATABASE` como `postgres` en Supabase:** si no lo deja, la alternativa es la zona por rol (`ALTER ROLE … SET
  timezone`) y el test cambia a comprobar los roles.

## Estimación y orden

T1 0,5 + T2 1 + T3 1 + T4 0,5–1 días, más T5 contigo (1–2 h): **3–3,5 días**. Orden T1 → T2 → T0 → T3 → T4 → T5; un
commit por tarea, aprobado por ti, en una rama nueva desde `main` cuando apruebes el plan.
