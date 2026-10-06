# C mínimo — demo desplegada en la UE para el socio (plan, pendiente de aprobación)

Fecha: 2026-10-06. Base: `main` = `4c97746` (B1 cerrado). **No es la infraestructura definitiva del C**: es un
entorno de demo, con PostgreSQL gestionado y **solo datos inventados**, para que el socio de Antonio pueda entrar
desde su casa.

Reglas que valen para todo el plan:
- Antonio crea las cuentas, paga y pone **todos** los secretos en la consola del proveedor. Claude nunca ve una
  credencial (ni del proveedor, ni de la BD, ni de Anthropic/Twilio, ni las contraseñas de los usuarios de la demo).
- Superpowers: un implementador + un revisor independiente por tarea con código, TDD donde haya código.
- **Regla de git de `CLAUDE.md` en todos los briefs:** los subagentes no ejecutan `git add`, `git rm`,
  `git checkout`, `git restore`, `git reset`, `git stash`, `git commit` ni `git push`. Si lo necesitan, paran y
  avisan a la sesión principal. Las mutaciones temporales para probar un test se deshacen con copia de seguridad
  (`cp` + `cmp`), nunca con git.
- Sin commits sin la aprobación de Antonio; stage ruta a ruta. Desplegar exige un commit (ver §4), así que hay un
  punto de aprobación antes del primer despliegue.

---

## 1. Proveedor — recomendación: Clever Cloud (zona `par`, París)

Precios comprobados el 2026-10-06 en la tabla pública de precios de Clever Cloud
(`api.clever-cloud.com/v4/billing/price-system?zone_id=par`, la que usa su estimador; € por hora × 730 h, sin IVA,
facturación por segundo) y en `render.com/pricing`.

| Pieza | Clever Cloud | €/mes aprox. |
|---|---|---|
| Backend Java, instancia **XS** (1 vCPU, 1 GB) | runtime Java/Maven nativo, Java 21 por defecto | 16,22 |
| PostgreSQL **XXS Small Space** (512 MB RAM, 1 GB de datos, 45 conexiones, copia diaria con 7 de retención) | v14–v18; se usará **v16** | 5,32 |
| Frontend, runtime **Static** en **pico** | servidor estático (SWS o Caddy) | 4,56 |
| **Total** | | **≈ 26 € + IVA** |

- La instancia **nano** (582 MB) es demasiado justa para Spring Boot + Hibernate + POI; no se recomienda.
- Alternativa más barata para la BD: el plan **DEV** gratuito (PostgreSQL 15, 256 MB, 5 conexiones, sin copias ni
  SLA). Con datos inventados las copias dan igual, pero obliga a bajar el pool de Hikari a ≤ 4 y usa otra versión
  mayor que la que tendremos después. Recomiendo XXS Small (5 € más).
- Al registrarse, Clever da créditos gratuitos sin tarjeta. Si la app se para cuando no hay demo, la instancia
  Java deja de facturar (la BD sigue).

**Render (Frankfurt), en una línea:** Standard 2 GB 25 $ (Starter de 512 MB, a 7 $, se queda corto) + Postgres
Basic‑256mb 6 $ + 0,30 $/GB, estático gratis ≈ **31 $/mes**; pero es empresa de EE. UU. (CLOUD Act), Java solo con
Dockerfile, y el Postgres gratuito caduca a los 30 días. Clever es francesa, tiene Java nativo y sale algo más barata.

## 2. Frontend — en el mismo Clever Cloud (runtime Static), no en Vercel

- Vercel Hobby prohíbe el uso comercial y es de EE. UU.; para una demo de 4,56 €/mes no compensa.
- Dos aplicaciones del mismo repo, cada una con `APP_FOLDER`: `ganera-demo-api` (`backend/`) y `ganera-demo`
  (`frontend/`), con dominios `*.cleverapps.io` y HTTPS incluido.
- **URL del backend:** `VITE_API_BASE_URL=https://<api>.cleverapps.io` como variable de entorno de la app del
  frontend; Vite la incrusta **al compilar** (`CC_BUILD_COMMAND="npm ci && npm run build"`, `CC_WEBROOT=/dist`).
  No es un secreto.
- **SPA:** las rutas de `createBrowserRouter` (`/tramites`, `/ganaderos/3`…) necesitan *fallback* a `index.html`.
  Con SWS, la variable `SERVER_FALLBACK_PAGE=./dist/index.html` (a confirmar en el despliegue; si no, un
  `Caddyfile` mínimo con `try_files`).
- **CORS:** sin cambios de código. `FRONTEND_ORIGEN=https://<front>.cleverapps.io` en la app del backend (origen
  exacto, sin barra final). Se comprueba en el smoke con un navegador real (curl no envía `Origin`).
- Alternativa descartada: servir el frontend desde Spring Boot (un dominio, sin CORS, 4,56 € menos), porque obliga a
  tocar el build de Maven y la seguridad para una demo.

## 3. Secretos — todos como variables de Clever, nunca en el repo ni en el chat

Antonio los genera en su máquina y los pega en la consola (Environment variables). Valores **nuevos**, no los de
local:

| Variable | Cómo generarla |
|---|---|
| `JWT_SECRET` | `openssl rand -base64 48` |
| `ENCRYPTION_KEY` | `openssl rand -base64 32` (debe decodificar a 32 bytes, la app lo comprueba al arrancar) |
| `ONBOARDING_SECRET` | `openssl rand -hex 32`; **se borra tras sembrar** (ver §6) |
| BD | **No se copia a mano:** al enlazar el add-on, Clever inyecta `POSTGRESQL_ADDON_HOST/PORT/DB/USER/PASSWORD`; un perfil `clever` los mapea (T2) |
| `ANTHROPIC_API_KEY` | **Clave aparte, sí** (ver abajo); solo si se activa WhatsApp (§8) |
| `TWILIO_AUTH_TOKEN`, `TWILIO_ACCOUNT_SID`, `TWILIO_WEBHOOK_URL` | solo si se activa WhatsApp (§8); sin ellos el webhook responde `503` |
| `STRIPE_*` | vacías (el webhook de Stripe rechaza todo con firma inválida) |

**Anthropic:** sí conviene una clave aparte, en un *workspace* propio (p. ej. `ganera-demo`) de la consola de
Anthropic con su **límite de gasto mensual** (5–10 $ sobra: Haiku con unos pocos mensajes son céntimos). Así se
puede revocar sin tocar la de desarrollo y un abuso queda acotado.

Otras variables de Clever (no secretas): `APP_FOLDER`, `CC_JAVA_VERSION=21`,
`CC_MAVEN_BUILD_GOAL="package -DskipTests"` (las suites se pasan en local antes de desplegar),
`CC_RUN_COMMAND="java -XX:MaxRAMPercentage=70 -jar target/ganera-core-0.1.0-SNAPSHOT.jar"`,
`SPRING_PROFILES_ACTIVE=clever`, `FRONTEND_ORIGEN`, `TZ=UTC` (ver §7).

**Una sola instancia del backend, sin autoescalado** (mín. = máx. = 1): la cola de extracción no reparte filas entre
instancias (B1). Durante un redespliegue Clever arranca la nueva antes de parar la vieja; durante unos segundos puede
haber dos planificadores. El efecto es acotado (la aplicación del resultado va bajo bloqueo y revalida), pero conviene
no redesplegar con mensajes entrando.

## 4. Despliegue — `clever` CLI con `git push` al remoto de Clever

Clever despliega **lo commiteado** (empuja `HEAD` a su git; no hace falta subir nada a GitHub). Por eso: tareas →
suites en verde → **commit aprobado por Antonio** → despliegue.

Pasos de Antonio (yo nunca toco la consola ni el token del CLI):
1. Crear la cuenta y la organización en Clever Cloud; método de pago.
2. Crear el add-on **PostgreSQL v16, XXS Small Space, zona París**.
3. Crear la app **Java + Maven** `ganera-demo-api` (XS, 1 instancia, sin autoescalado, París) y **enlazarle** el
   add-on. Crear la app **Static** `ganera-demo` (pico, París).
4. Meter las variables de §3 en cada app (las de `CC_*`, `APP_FOLDER`, etc. se las dejo en una lista para pegar). **No** definir ninguna `GANERA_REGISTRO_*` (abriría el registro).
5. Instalar `clever-tools` (`npm i -g clever-tools`), `clever login` en **su** terminal, y desde la raíz del repo:
   `clever link <id-api> --alias api` y `clever link <id-front> --alias front` (crea `.clever.json`, que **no** se
   commitea: lo añado a `.gitignore` en T2).
6. `clever deploy --alias api` y, cuando arranque, `clever deploy --alias front`. `clever logs --alias api` para ver
   Flyway aplicar `V1`–`V19`.

Antonio puede lanzar los comandos de 5–6 él mismo con `!` en esta sesión; yo leo la salida (logs sin secretos).

## 5. Datos de demo — dos gestorías inventadas

1. **Gestorías y usuarios:** un script (`scratchpad`, no se versiona) que Antonio ejecuta con `!`: pide con
   `read -s` el `ONBOARDING_SECRET` y las contraseñas, y llama dos veces a `POST /internal/onboarding/gestoria`
   (crea Gestoría + primer Usuario + Suscripción `ACTIVA`, así ambas pueden aprobar). Ejemplo: "Gestoría Demo Norte"
   / `socio-norte@demo.ganera.es` y "Gestoría Demo Sur" / `socio-sur@demo.ganera.es`. **Solo hay un Usuario por
   gestoría** (no existe alta de más usuarios), así que el socio usa estas dos cuentas.
2. **Inventario:** dos Excel con datos inventados (unos 10 ganaderos, 15 explotaciones, 150 animales y contactos con
   teléfonos de prueba `+34600000xxx`), con REGA, NIF y crotales distintos entre gestorías (son únicos globales),
   salvo **los mismos últimos dígitos** en ambas a propósito, para que se vea el aislamiento. Se importan desde la UI.
3. **Trámites para la cola:** no hay forma de crearlos por la app sin WhatsApp. Recomiendo un `seed-tramites.sql`
   (lo escribo yo; inserta 5–6 trámites por gestoría, localizando explotaciones y contactos por REGA/teléfono:
   pendiente con crotales resueltos, sin explotación, con aviso `FALLIDA`, uno aprobado, uno rechazado, con sus
   `mensaje_campo` inventados). Lo ejecuta **Antonio** con `psql` usando la URI del add-on de su consola
   (`psql "$URI" -f seed-tramites.sql`), sin pasármela. Sin `psql`: se instala el cliente de PostgreSQL, o se
   sustituye por WhatsApp (§8).
4. Al acabar, **borrar `ONBOARDING_SECRET`** de las variables de Clever y reiniciar: el endpoint queda cerrado
   (`401`, fail-closed).

## 6. Exposición — qué queda abierto y qué se cierra

| Ruta | Estado hoy | En la demo |
|---|---|---|
| `POST /gestorias/registro` | **Público.** Crea Gestoría + Usuario **antes** de llamar a Stripe; con Stripe vacío responde `503`, pero la cuenta queda creada y puede iniciar sesión, importar Excel, etc. | **Cerrado (T2, D4):** `ganera.registro.abierto`, `false` por defecto (fail-closed): un filtro por delante de Spring Security responde `404` vacío sin leer el cuerpo ni crear nada. **Oculto (T3):** sin enlace "Regístrate"; `/registro` → `/login`. En Clever **no** se define ninguna variable `GANERA_REGISTRO_*` (el binding relajado de Spring la aplicaría). |
| `POST /internal/onboarding/gestoria` | Secreto compartido, comparación en tiempo constante, fail-closed si está vacío | Abierto solo mientras se siembra; luego se borra el secreto (§5.4). |
| `POST /webhooks/twilio/whatsapp` | Fail-closed: `503` sin token/URL, `403` con firma mala | Cerrado (`503`) salvo que se active §8. |
| `POST /webhooks/stripe` | Firma obligatoria | Con el secreto vacío rechaza todo. Sin cambios. |
| `POST /auth/login` | Sin límite de intentos | Aceptable para la demo con contraseñas largas; anotado para el C de verdad. |
| `/error` | Público (decisión 29), sin traza | Sin cambios. |
| Resto | JWT obligatorio | Sin cambios. No hay Actuator ni Swagger. |

Además, en el perfil `clever`: `logServerErrorDetail=false` en la URL JDBC (nota de B1 para producción), y un
`robots.txt` + `<meta name="robots" content="noindex">` en el frontend de la demo (opcional, T3).

## 7. PostgreSQL real — primera vez de Flyway contra Postgres

Revisadas `V1`–`V19` a mano: SQL estándar de PostgreSQL (`BIGSERIAL`, `TEXT`, `DEFAULT now()`, `ALTER TABLE … ADD
COLUMN`). `flyway-database-postgresql` ya está en el `pom.xml` (sin él, Flyway 10 no arranca contra Postgres). `V18`
(Java, JDBC) también es compatible: en Postgres corre entera en una transacción, y el `SELECT` se lee completo en
memoria (aceptable con estos volúmenes). Pero **nada de esto se ha ejecutado nunca contra Postgres**, así que se prueba
antes de desplegar (T1).

**Hipótesis sobre `TIMESTAMP` (la nota n4 de B1 T3), a confirmar con un test que falle primero:** las columnas son
`TIMESTAMP` sin zona y las entidades usan `Instant`. Hibernate 6.6 envía un `Instant` a PostgreSQL como `timestamptz`
en UTC; al guardarlo en una columna sin zona, Postgres lo convierte a la hora de la **zona de la sesión**, que el driver
fija con la zona de la JVM. Al leer, el driver interpreta un `timestamp` sin zona como UTC. Si la JVM no está en UTC
(p. ej. `Europe/Madrid`), cada `Instant` leído volvería desplazado 1–2 h: `createdAt` en pantalla, el
`proximoIntentoExtraccion` cargado en la entidad. La comparación `proximoIntentoExtraccion <= :ahora` sería coherente
(ambos lados pasan por la zona de la sesión), salvo en la hora repetida del cambio de horario de octubre. En H2 no se ve.

Dos arreglos posibles, a decidir con el resultado de T1:
- **(a)** fijar UTC: `TZ=UTC` en Clever y `spring.jpa.properties.hibernate.jdbc.time_zone: UTC`. Sin migración, pero
  depende de la configuración del entorno.
- **(b) recomendado si el test lo confirma:** `V20` que pasa a `TIMESTAMPTZ` las columnas mapeadas a `Instant`
  (`created_at` de todas las tablas, `proximo_intento_extraccion`). Es el tipo correcto en Postgres para `Instant` y
  no depende de la zona. Se mantiene `TZ=UTC` como cinturón.

## 8. WhatsApp en la demo — sí, pero como último paso y opcional

Recomiendo apuntar el **sandbox de Twilio** a la demo **después** del smoke base, porque es lo que mejor enseña el
producto y la infraestructura ya está (firma con `TWILIO_WEBHOOK_URL`, fail-closed, IA en segundo plano). Coste: el
sandbox es gratis; Haiku, céntimos, con el límite de gasto de la clave aparte.

- El socio envía `join <código>` al número del sandbox desde su móvil. Su teléfono se da de alta como Contacto de la
  "Gestoría Demo Sur" (dato personal, con su consentimiento); el de Antonio, en la Norte. Un número desconocido no
  recibe respuesta y queda como `NUMERO_DESCONOCIDO`.
- El sandbox apunta a **una** URL: mientras esté en la demo, el desarrollo local no lo usa.
- Para apagarlo: vaciar la URL del sandbox en Twilio y borrar `TWILIO_AUTH_TOKEN` en Clever.
- Si se deja para más adelante, la cola se llena con el SQL de §5.3.

---

## Tareas

**T1 — Pruebas contra PostgreSQL real (backend, TDD).** Sin Docker aquí: PostgreSQL 16 embebido con
`io.zonky.test:embedded-postgres` (binarios desde Maven Central, funciona en Windows), solo en `test`. Tests:
- Flyway aplica `V1`–`V19` en Postgres limpio, y `V18` rellena filas existentes (mismo patrón que su test en H2:
  `target 17` → datos → migrar, con un lote de más de 500).
- Ida y vuelta de un `Instant` en `tramite.proximo_intento_extraccion` y `created_at` con la JVM en `Europe/Madrid`
  (y en UTC); `findPendientesDeExtraccion` con `:ahora` justo antes y después; las dos consultas de retención en el
  límite.
- Si falla como dice la hipótesis de §7: **parar y llevarle a Antonio la decisión (a)/(b)** antes de arreglar.
- Si `embedded-postgres` no funciona en esta máquina: parar y avisar (alternativa: PostgreSQL instalado en local por
  Antonio).

**T2 — Perfil `clever` y cierre del registro (backend, TDD).**
- `application-clever.yml`: datasource desde `POSTGRESQL_ADDON_*` (solo *placeholders*, ningún valor),
  `logServerErrorDetail=false` (hecho: además `driver-class-name` de PostgreSQL). El registro va cerrado en `application.yml`, no en el perfil.
- [Sustituido por D4] `ganera.registro.abierto=false` (por defecto) → `POST /gestorias/registro` responde `404` sin crear Gestoría, Usuario ni
  Suscripción (E2E: el recuento de filas no cambia). Con `true`, todo igual que hoy.
- `.clever.json` en `.gitignore`.

**T3 — Frontend de la demo (TDD).** [Hecho según D4, sin variable: enlace quitado siempre, `/registro` → `/login`, `RegistroPage` y su API borradas, `noindex`.] Plan original: `VITE_REGISTRO_ACTIVO=false` oculta el enlace "Regístrate" y la ruta `/registro`
redirige a `/login`; sin la variable, igual que hoy. Opcional: `noindex`. `fallback` de SPA si hace falta un fichero.

**T4 — Datos de demo (sin código de la app).** Los dos Excel, `seed-tramites.sql` (rellenando también
`busqueda`/`nombre_busqueda` si insertara en `ganadero`/`explotacion`, que no debería) y el script de onboarding con
`read -s`. Probados antes en el Postgres embebido de T1 o en H2.

**Cierre (sesión principal):**
1. `./mvnw clean test` y `npm test` en verde; `npm run build` y `npm run lint`.
2. Commit con aprobación de Antonio (stage ruta a ruta) → despliegue (§4) → siembra (§5).
3. **Smoke contra el entorno desplegado**, con un script que **ejecuta Antonio** con `!` (lee las contraseñas con
   `read -s`; yo solo veo PASS/FAIL), y después en un navegador real a 1440 y 375 px (el navegador lo conduce
   Antonio o, si me deja una sesión ya iniciada, yo):
   - login A y B; `401` con contraseña mala;
   - cola de A con sus trámites; PATCH + aprobar con `version` → `APROBADO`;
   - aislamiento: B no ve los trámites ni explotaciones de A en los listados; `GET /tramites/{id de A}`,
     `/ganaderos/{id de A}` y aprobar el de A con el token de B → `404`, y el de A sigue igual;
   - CORS: el frontend desplegado habla con la API (preflight en navegador real);
   - cerrado: registro `404`, onboarding `401` (tras borrar el secreto), webhook de Twilio `503`;
   - en los logs: Flyway `V1`–`V19` (y `V20` si la hay) aplicadas, sin errores.
4. Opcional §8: WhatsApp con el sandbox y un mensaje del socio que acaba en la cola de su gestoría.
5. `CLAUDE.md`, `ganera-prompts.md` y `progress.md` al día (URL de la demo **no** en el repo si Antonio prefiere).

## Decisiones cerradas (Antonio, 2026-10-06)

- **D1** Clever Cloud, París. **D2** PostgreSQL XXS Small Space (v16). **D3** frontend en Clever Static.
- **D4, con matiz:** propiedad `ganera.registro.abierto`, **cerrada por defecto** (fail-closed); solo se abre en los
  tests que la necesiten. Cerrada → `POST /gestorias/registro` responde `404` sin crear nada. El frontend oculta el
  enlace según esa configuración o, si es más simple, siempre. Backend y frontend en el **mismo commit** para que
  `main` no quede roto. (Sustituye a `ganera.registro.activo` con `true` por defecto de T2/T3.)
- **D5** trámites por SQL. Antonio **no tiene `psql`**: el SQL se ejecuta en **Adminer**, el cliente web que enlaza
  Clever desde el panel del add-on PostgreSQL (`dbms-adminer.clever-cloud.com`), pegando el script en "Comando SQL".
  Nada que instalar.
- **D6** el smoke lo ejecuta Antonio; Claude solo ve PASS/FAIL.
- **D7** WhatsApp como último paso, opcional.
- **D8 (tras T1, 2026-10-06):** T1 refutó la hipótesis de §7 (con Hibernate 6.6 + pgjdbc 42.7.4 un `Instant`
  hace ida y vuelta sin desfase en `TIMESTAMP` sin zona, también en `Europe/Madrid`, y la cola y la retención cortan
  bien). **Sin `V20` ni código nuevo.** Lo único sensible a la zona es `DEFAULT now()` en filas insertadas a mano:
  el SQL de siembra empieza con `SET TIME ZONE 'UTC'` y pone `created_at` explícito; `TZ=UTC` en Clever; el test
  desactivado de `now()` pasa a documentar el comportamiento y a pasar. **Precaución de Antonio:** en el despliegue,
  `ALTER DATABASE <nombre> SET timezone TO 'UTC'` en la base de Clever (desde Adminer), y el smoke comprueba con
  `SHOW timezone` (en Adminer, tras reconectar) que devuelve `UTC`.
- Al cierre: explicar cómo parar y arrancar las apps de Clever entre demos.

## Decisiones para Antonio (planteadas)

- **D1** Proveedor: Clever Cloud, París (recomendado) o Render Frankfurt.
- **D2** BD: XXS Small Space 5,32 € (recomendado) o DEV gratis.
- **D3** Frontend en Clever Static (recomendado) o Vercel.
- **D4** Registro: cerrado en el backend **y** oculto en el frontend (recomendado), o solo cerrado en el backend.
- **D5** Trámites de demo: SQL ejecutado por Antonio con `psql` (recomendado) o solo WhatsApp.
- **D6** Smoke: lo ejecuta Antonio con `!` y yo veo solo el resultado (recomendado), o un fichero local de
  contraseñas que mis scripts leen sin mostrarlas.
- **D7** WhatsApp: al final, opcional (recomendado), o más adelante.
- **D8** (llega con T1) `TIMESTAMP`: (a) UTC por configuración o (b) `V20` a `TIMESTAMPTZ`.
