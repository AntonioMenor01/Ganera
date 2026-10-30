# db/postgresql — migraciones y callbacks solo para PostgreSQL

Flyway lee `classpath:db/migration` y `classpath:db/{vendor}` (`spring.flyway.locations` en los dos
`application.yml`). Spring Boot resuelve `{vendor}` a `postgresql` contra PostgreSQL (Supabase, el
embebido de los tests, el `docker-compose` local) y a `h2` contra H2, así que lo que hay aquí **solo se
ejecuta en PostgreSQL**. `spring.flyway.fail-on-missing-locations: true` hace que la aplicación no
arranque si esta carpeta no llega al classpath (sin eso Flyway solo lo diría en DEBUG y el callback de
seguridad dejaría de ejecutarse en silencio).

Aquí va lo que H2 no entiende o no necesita: roles, permisos, RLS y, más adelante, el trigger de
auditoría.

## `afterMigrate__seguridad_esquema.sql` (migración a Supabase, T2)

Callback de Flyway: se ejecuta **al final de cada `migrate`**, haya o no migraciones pendientes, sobre
el esquema por defecto de Flyway (`${flyway:defaultSchema}`: `ganera` en el perfil `clever`, `public`
en los demás tests de PostgreSQL y en local). Es idempotente. Recorre **todas** las tablas del esquema,
también las de migraciones futuras:

- activa RLS (sin `FORCE`: el dueño, `ganera_propietario` — Flyway y la siembra —, no la cumple);
- quita todo a `PUBLIC`, `anon`, `authenticated`, `service_role` y `ganera_app`, y luego da a
  `ganera_app` exactamente lo suyo:
  - tabla normal: `SELECT, INSERT, UPDATE, DELETE` y la política `ganera_app_todo`
    (`FOR ALL TO ganera_app USING (true) WITH CHECK (true)`);
  - tabla «solo inserción y lectura»: `SELECT, INSERT` y las políticas `ganera_app_lectura`
    (`FOR SELECT USING (true)`) y `ganera_app_insercion` (`FOR INSERT WITH CHECK (true)`); nunca
    `UPDATE` ni `DELETE`;
  - `flyway_schema_history`: nada (RLS activada y sin política);
- secuencias: solo `USAGE` para `ganera_app` (las tablas usan `BIGSERIAL`);
- funciones, procedimientos y vistas: nada para `PUBLIC`, `anon`, `authenticated` y `service_role`;
- esquema: nada para esos cuatro; `ganera_app` solo `USAGE`, nunca `CREATE`.

Si falta alguno de los roles `ganera_app`, `anon`, `authenticated` o `service_role`, **falla** (y con él
la migración y el arranque) con un mensaje que manda a ejecutar
`backend/supabase/01-preparar-roles-y-esquema.sql`. Las políticas solo se crean si no están ya (mira
`pg_policies`); una política propia de Ganera con otra definición se borra y se vuelve a crear. Una
política ajena no se toca, pero la detecta la comprobación.

**Lista de excepciones** (tablas «solo inserción y lectura»): una constante del callback. Hoy solo
tiene `auditoria`, el nombre reservado de la futura tabla de auditoría. Si esa tarea la renombra, hay
que cambiarla **aquí y en `backend/supabase/02-comprobar-seguridad.sql` a la vez**.

La comprobación es `backend/supabase/02-comprobar-seguridad.sql` (0 filas = correcto); la usan los
tests (`SeguridadEsquemaPostgresTest`) y se ejecuta en el editor SQL de Supabase.

## Regla de numeración

- La versión es **global** entre `db/migration`, `db/postgresql` y `db/h2`: una migración versionada
  de esta carpeta toma el siguiente número libre (p. ej. `V27__...sql`) y deja un hueco en H2, que
  Flyway admite.
- **Nunca** dos ficheros con la misma versión en carpetas distintas: en PostgreSQL Flyway vería las dos
  y fallaría.
- Los callbacks (`afterMigrate__*.sql`, etc.) no llevan versión.
