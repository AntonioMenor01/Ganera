# Despliegue de Ganera en Clever Cloud (demo y piloto) — PLAN APROBADO (2026-10-14)

Fecha: 2026-10-14. Parte de `main` = `origin/main` = `b4b786c` (backend 968: 967 + 1 omitido; frontend 1075 en 49).
**Nada de código ni commit hasta que Antonio apruebe el plan, y nunca antes de que acabe la demo del 2026-10-15**
(la demo se hace desde el worktree `C:\Users\Antonio\Desktop\ganera-demo\app`, en `b4b786c`).

Base: el plan de C mínimo (`2026-10-06-promptC-minimo-demo-desplegada.md`, D1–D8). Su código ya está en `main`
(perfil `clever`, registro cerrado, tests en PostgreSQL 16 embebido), pero **no llegó a desplegarse**. Su kit
(`C:\Users\Antonio\Desktop\Archivos-Ganera\ganera-demo\`: `alta-gestorias.sh`, `seed-tramites.sql`, `smoke.sh`,
`generador/generar.py`) **ha caducado**: siembra tipos anteriores a V20 (`BAJA`, `ALTA`, `MOVIMIENTO`, que hoy darían
`500` al leerlos), no tiene `HECHO_EN_OVZ` ni `SIN_TRAMITE`, y su smoke espera el webhook de Twilio en `503`.

> **Nota (migración a Supabase, 2026-10-19):** la base de datos ya **no** es el add-on PostgreSQL de Clever: es un proyecto de **Supabase**
> (región eu-west-1, Irlanda; PostgreSQL 17.11; plan Free; Data API desactivada). El backend sigue en Clever (París). El
> frontend irá a **Vercel** (`app.ganera.es`; tarea aparte, sin plan todavía). Plan y decisiones:
> `docs/superpowers/plans/2026-10-19-migracion-supabase.md`. Lo de este plan que cambia va marcado con esta etiqueta;
> el resto sigue igual (HSTS, CORS fail-closed, sin usuario por defecto, PWA).

---

## Decisiones de Antonio (2026-10-14) — prevalecen sobre el resto del plan

- **D1** Dos subdominios: `app.ganera.es` (frontend estático) y `api.ganera.es` (backend), con CORS limitado a
  `https://app.ganera.es`.
- **D2** PostgreSQL **XXS Small Space** en París (5,32 €/mes). No XXS Medium.
  **(migración a Supabase, 2026-10-19):** sustituida: la base es Supabase, Irlanda, PostgreSQL 17.11, plan Free (0 €). Sin add-on en Clever.
- **D3** **Esta base es para la demo. Antes del primer dato real: crear una base nueva y empezar desde cero** (add-on
  PostgreSQL nuevo, enlazarlo a `ganera-api` en lugar del de la demo, redesplegar —Flyway aplica V1–V23—, `ALTER
  DATABASE … SET timezone TO 'UTC'`, alta de las gestorías reales con un `ONBOARDING_SECRET` temporal que se borra
  después; y borrar el add-on de la demo).
  **(migración a Supabase, 2026-10-19):** ahora en Supabase (S7): `SET ROLE ganera_propietario; DROP SCHEMA ganera CASCADE; RESET ROLE;` en el
  editor SQL y el script `backend/supabase/01-preparar-roles-y-esquema.sql` otra vez (se conservan los roles y sus
  contraseñas); redesplegar `ganera-api` (Flyway aplica V1–V23 y el callback); la zona horaria ya la pone el 01.
- **D4** Copias de seguridad: las de Clever, **7 días** por ahora. **La retención se decide con el abogado antes de los
  datos reales.**
  **(migración a Supabase, 2026-10-19):** las copias son las de Supabase. En el plan Free probablemente **no** hay copias descargables (llegan con
  Pro): Antonio lo comprueba en el panel. Va a la lista del abogado (`progress.md`) junto con la retención.
- **D5** Para el abogado: los datos que salen de la UE (Twilio, Meta y Anthropic), anotados en `progress.md`.
- **D6** Tareas (Superpowers + TDD, revisión independiente por tarea; cada cambio de seguridad con su mutación en rojo):
  **T1** quitar el `UserDetailsService` por defecto; **T2** cabeceras del proxy (HTTPS y HSTS) solo en producción;
  **T3** CORS; **T4** PWA (manifiesto e iconos); **T5** siembra y `smoke.sh` nuevos en una carpeta nueva
  (`C:\Users\Antonio\Desktop\ganera-clever\`). Sin commit ni push sin aprobación. No se toca
  `C:\Users\Antonio\Desktop\ganera-demo\` (ni su worktree `app`, fijado en `b4b786c`).

La numeración de tareas de §3 queda sustituida por la de D6 (las comprobaciones de «nada de H2» y `.gitignore` van
dentro de T2 y T5).

## 1. Arquitectura en Clever (zona **París**, `par`)

Precios leídos hoy en la tabla pública de Clever (`api.clever-cloud.com/v4/billing/price-system?zone_id=par`,
€/hora × 730 h, **sin IVA**). Son los mismos que el 2026-10-06. Se cobra por segundo.

| Pieza | Plan | €/mes |
|---|---|---|
| Backend Java 21 + Maven (`backend/`) | **XS** (1 vCPU, 1 GB), 1 instancia, sin autoescalado | 16,22 |
| Frontend estático (`frontend/`, `dist/`) | runtime **Static**, **pico** | 4,56 |
| PostgreSQL **16** gestionado | **XXS Small Space** (512 MB RAM, 1 GB de datos, copia diaria con 7 días de retención) | 5,32 |
| **Total demo** | | **≈ 26,10 € + IVA ≈ 31,60 €** |
| Piloto con datos reales (recomendado) | BD **XXS Medium Space** (más disco, mismo plan de copias; tamaño exacto a confirmar en el panel) | +1,57 → ≈ 27,70 € + IVA |

- **Recomiendo dos aplicaciones (frontend estático y backend Java)**, como en C mínimo. Servir el frontend desde
  Spring Boot ahorra 4,56 €/mes, un CNAME y la configuración de CORS, pero obliga a compilar Node dentro de Maven,
  a abrir rutas públicas y el *fallback* de SPA en Spring Security, y a redesplegar el backend por cada cambio visual.
  No compensa por 4,56 €.
- **Una sola instancia del backend** (mínimo = máximo = 1): la cola de extracción no reparte filas entre instancias (B1).
- **Plan DEV de PostgreSQL (gratis): no.** No tiene copias de seguridad ni SLA y solo admite 5 conexiones.
- **Un solo entorno para la demo y el piloto.** Las dos gestorías de demo («Gestoría Demo Norte/Sur», datos inventados)
  conviven con las reales, aisladas por `gestoria_id`. **Decisión tuya:** antes de meter datos reales, borrar la base
  y empezar limpia (lo recomiendo: hoy no hay forma de borrar gestorías por la app, así que la demo se quedaría para
  siempre), o mantenerla.
- Clever es una empresa francesa con centros de datos en la UE; la zona `par` está en París (ver §7, «Dónde están los
  datos»).
- **(migración a Supabase, 2026-10-19):** la fila de PostgreSQL desaparece de Clever: la base es un proyecto de **Supabase** en **eu-west-1
  (Irlanda)**, **PostgreSQL 17.11**, plan Free (0 €; en Free el proyecto **se pausa tras 7 días sin actividad**). La app
  conecta por el *pooler* en modo sesión (puerto 5432) con SSL `verify-full`, como `ganera_app` (sin ser dueña de nada,
  con RLS), y Flyway migra como `ganera_propietario`. Total demo en Clever: backend + frontend ≈ 20,78 € + IVA (el
  frontend se irá a Vercel, tarea aparte).

## 2. Dominios y DNS

**Recomendación: `app.ganera.es` (frontend) y `api.ganera.es` (backend).** Un solo dominio solo sería más sencillo
en el DNS (un registro en vez de dos); en el código es más trabajo (ver §1).

Registros que crea **tu socio**, en el panel DNS de donde esté delegado `ganera.es` (registrador o proveedor de DNS),
sin tocar la raíz, `www` ni el correo (MX/SPF/DKIM):

| Nombre | Tipo | Valor | TTL |
|---|---|---|---|
| `app` | CNAME | `domain.par.clever-cloud.com.` | 300 al principio; 3600 cuando funcione |
| `api` | CNAME | `domain.par.clever-cloud.com.` | igual |

- Si el dominio tiene un registro **CAA**, añadir `0 issue "letsencrypt.org"`; si no lo tiene, no hace falta nada.
  Clever pide el certificado a Let's Encrypt **por sí solo** en cuanto el CNAME resuelve, sin registro TXT.
- En Clever: añadir `app.ganera.es` a la app del frontend y `api.ganera.es` a la del backend («Domain names»). Las URL
  `*.cleverapps.io` siguen funcionando (para el primer smoke y por si algo falla).
- Comprobación: `nslookup app.ganera.es` devuelve las IP de Clever, y `https://app.ganera.es` carga con candado válido.

## 3. Antes de subir (código, después de la demo)

Cada tarea con TDD, implementador y revisor independiente, y `./mvnw clean test` + `npm test` en verde.

**T1 — Quitar el `UserDetailsService` por defecto** (pendiente técnico de `progress.md`). Excluir
`UserDetailsServiceAutoConfiguration` en `@SpringBootApplication(exclude = …)`. Test: `@SpringBootTest` con
`OutputCaptureExtension` que comprueba que el arranque no imprime «Using generated security password» y que el
contexto no tiene ningún bean `UserDetailsService`. Regresión: quitar el `exclude` hace fallar el test (comprobado con
copia de seguridad del fichero, `cp` + `cmp`). Los E2E de login, de rutas públicas y de webhooks siguen en verde.

**T2 — HTTPS detrás del proxy de Clever (backend, perfil `clever`).** Clever termina TLS en su proxy y pasa la
petición a la app por HTTP con `X-Forwarded-Proto`. Sin `server.forward-headers-strategy: native`, Spring cree que la
petición es HTTP y **no envía HSTS**. Añadirlo **solo en `application-clever.yml`**, con un test sobre el perfil
`clever` (PostgreSQL embebido, como los de C mínimo): con `X-Forwarded-Proto: https`, la respuesta lleva
`Strict-Transport-Security`; sin ella, no. Las firmas de Twilio no cambian porque se validan contra la URL
configurada, nunca contra la de la petición (comprobado por los tests de los dos webhooks).
**Cambiado al implementar (2026-10-14):** nada en la app lee el esquema, el host ni la IP de la petición, así que
`forward-headers-strategy` no tendría efecto observable (configuración muerta), y con `native` Tomcat solo se fía de
`X-Forwarded-Proto` si la IP del proxy es privada (las de Clever no constan). En su lugar,
`ganera.seguridad.hsts-siempre: true` **solo en `application-clever.yml`** (sin placeholder): `SecurityConfig` manda
`Strict-Transport-Security: max-age=31536000 ; includeSubDomains` en **todas** las respuestas. Por HTTP el navegador
la ignora (RFC 6797) y Clever redirige con «Force HTTPS». Tests: `HstsPerfilCleverPostgresTest` (con y sin
`X-Forwarded-Proto`) y `HstsSoloEnProduccionTest` (fuera de `clever`, nunca).

**T3 — Web instalable (PWA), sin modo sin conexión** (ver §6).

**T4 — Kit de despliegue al día** (fuera del repo, en una carpeta nueva `C:\Users\Antonio\Desktop\ganera-clever\`,
copiando `Archivos-Ganera\ganera-demo` como punto de partida; no se toca `ganera-demo\`, que es el kit del portátil):
- `generar.py`: tipos OVZ (`BAJA_MUERTE`, `SOLICITUD_MOVIMIENTO`, `ALTA_NACIMIENTO` con crotal completo…); añadir un
  `HECHO_EN_OVZ` (rellenando `hecho_en_ovz_por` y `hecho_en_ovz_en`, o salta `ck_tramite_hecho_en_ovz`) y un
  `SIN_TRAMITE` descartable (`«Vale, muchas gracias»`), como en `sembrar-tramites.sql` del portátil. Mantener lo que ya
  servía: dos gestorías, últimos dígitos y «Los Castaños» repetidos a propósito, el trámite `[smoke]`, una
  transacción, comprobaciones con división por cero, `SET TIME ZONE 'UTC'` y `created_at` explícito.
- `alta-gestorias.sh`: sirve tal cual (el cuerpo del onboarding no ha cambiado); solo revisar los textos.
- `smoke.sh`: al día con el entorno real (ver §9).
- Ensayo completo contra **PostgreSQL 16 embebido** con Flyway V1–V23 (como en C mínimo): alta, los dos Excel, la
  siembra desde una sesión que no está en UTC, y `smoke.sh` en modo ensayo, todo PASS.

**T5 — Nada de H2 ni de configuración de tests en producción.** Ya comprobado hoy en el código; queda verificarlo
**sobre el jar** que construye Clever:
- `h2` y `embedded-postgres` tienen ámbito `test` en `pom.xml`, y `src/test/resources/application.yml` no entra en el
  jar. Comprobación: `jar tf target/ganera-core-*.jar` sin `h2`, `zonky` ni `application.yml` de test.
- El perfil `clever` fija el driver de PostgreSQL y lee las variables `POSTGRESQL_ADDON_*`; `ddl-auto: none` y Flyway
  activo vienen de `application.yml`. **(migración a Supabase, 2026-10-19):** lee las siete `SUPABASE_*` (§4), con SSL, CA y esquema fijos en
  `application-clever.yml`.
- Frontend: `msw` es dependencia de desarrollo y `dist/` no contiene `mockServiceWorker.js`. Comprobación:
  `npm run build` y luego listar `dist/`.
- En el primer log de arranque en Clever: `Flyway … V23`, el perfil `clever` activo, ninguna mención a `H2`, y que no
  aparece «Using generated security password» (prueba de T1 en real).

**T6 — `.gitignore`**: confirmar que `.clever.json` está ignorado (lo añadió C mínimo).

## 4. Variables de entorno (solo los nombres; los valores los pones tú en el panel de Clever, nunca en el chat)

Genera los secretos en tu terminal (Git Bash trae `openssl`). Valores **nuevos**, nunca los de local ni los de la demo
del portátil.

**App del backend (`ganera-api`)**

| Variable | ¿Secreta? | Valor o cómo generarla |
|---|---|---|
| `APP_FOLDER` | no | `backend` |
| `CC_JAVA_VERSION` | no | `21` |
| `CC_MAVEN_BUILD_GOAL` | no | `package -DskipTests` (las suites pasan en local antes de desplegar) |
| `CC_RUN_COMMAND` | no | `java -XX:MaxRAMPercentage=70 -jar target/ganera-core-0.1.0-SNAPSHOT.jar` |
| `SPRING_PROFILES_ACTIVE` | no | `clever` |
| `TZ` | no | `UTC` |
| `FRONTEND_ORIGEN` | no, pero **obligatoria** (sin ella, o si no es un origen exacto `https`, la app no arranca) | `https://app.ganera.es` (exacto, sin barra final; mientras no haya DNS, la URL `https://…cleverapps.io` del frontend) |
| `JWT_SECRET` | **sí** | `openssl rand -base64 48` |
| `ENCRYPTION_KEY` | **sí** | `openssl rand -base64 32` (32 bytes; la app no arranca si no). **Guárdala también en tu gestor de contraseñas:** si se pierde, lo cifrado no se recupera. |
| `ONBOARDING_SECRET` | **sí** | `openssl rand -hex 32`. **Se borra** después de dar de alta las gestorías (§8, paso 9). |
| `ANTHROPIC_API_KEY` | **sí** | clave nueva en un *workspace* `ganera-prod` de la consola de Anthropic, con **límite de gasto mensual** (10 $) |
| `TWILIO_ACCOUNT_SID` | **sí** (no da acceso solo, pero no se publica) | consola de Twilio |
| `TWILIO_AUTH_TOKEN` | **sí** | consola de Twilio |
| `TWILIO_WHATSAPP_FROM` | no | número del sandbox, `whatsapp:+14155238886` |
| `TWILIO_WEBHOOK_URL` | no | `https://api.ganera.es/webhooks/twilio/whatsapp` |
| `TWILIO_STATUS_CALLBACK_URL` | no | `https://api.ganera.es/webhooks/twilio/whatsapp/estado` |
| ~~`POSTGRESQL_ADDON_HOST/PORT/DB/USER/PASSWORD`~~ | sí | ~~**No se escriben:** Clever las inyecta al enlazar el add-on a la app~~ **(migración a Supabase, 2026-10-19):** ya no existen; las sustituyen las siete siguientes |
| `SUPABASE_DB_HOST` | no | host del **pooler en modo sesión** (Supabase → Connect → Session pooler) |
| `SUPABASE_DB_PORT` | no | `5432` (modo sesión; el `6543`, modo transacción, **no vale**: rompe los *prepared statements* y el bloqueo consultivo de Flyway) |
| `SUPABASE_DB_NAME` | no | `postgres` |
| `SUPABASE_DB_USER` | no | `ganera_app.<ref del proyecto>` |
| `SUPABASE_DB_PASSWORD` | **sí** | la de `ganera_app`, puesta con `\password` de `psql` 17 (kit, `LEEME.md`) |
| `SUPABASE_FLYWAY_USER` | no | `ganera_propietario.<ref del proyecto>` |
| `SUPABASE_FLYWAY_PASSWORD` | **sí** | la de `ganera_propietario`, igual |
| `STRIPE_API_KEY`, `STRIPE_WEBHOOK_SECRET`, `STRIPE_PRICE_ID_EXPLOTACION` | — | **No definir** (el cobro va en la landing; sin ellas, el webhook de Stripe rechaza todo y el job nocturno no hace nada) |

**No definir nunca** ninguna `GANERA_REGISTRO_*` (abriría el registro público), `GANERA_SEGURIDAD_*` ni `GANERA_FRONTEND_*` (pisarían HSTS y CORS del perfil `clever`), ni `DATABASE_URL`/`DATABASE_*` (las
sustituye el perfil `clever`), ni `ANTHROPIC_LOG`. **(migración a Supabase, 2026-10-19):** tampoco `SPRING_DATASOURCE_*` ni `SPRING_FLYWAY_*`
(el binding relajado pisaría el perfil). Las siete `SUPABASE_*` son obligatorias y sin valor por defecto: si falta una o
está **definida pero vacía**, la app no arranca (`ConexionBaseDeDatosEstricta`).

**App del frontend (`ganera-app`)**

| Variable | ¿Secreta? | Valor |
|---|---|---|
| `APP_FOLDER` | no | `frontend` |
| `CC_BUILD_COMMAND` | no | `npm ci && npm run build` |
| `CC_WEBROOT` | no | `/dist` |
| `VITE_API_BASE_URL` | no (Vite la mete en el JS público) | `https://api.ganera.es`; **se lee al compilar**: si cambia, hay que redesplegar el frontend |
| `SERVER_FALLBACK_PAGE` | no | `./dist/index.html` (para que `/tramites/12` recargue bien; si el runtime no la admite, un `Caddyfile` con `try_files`; se decide en el primer despliegue) |

## 5. Twilio

- **URL de mensajes entrantes:** `https://api.ganera.es/webhooks/twilio/whatsapp` (POST).
- **URL del callback de estado:** `https://api.ganera.es/webhooks/twilio/whatsapp/estado`. La pide Ganera en cada envío
  (`TWILIO_STATUS_CALLBACK_URL`); **no hace falta ponerla en la consola**.
- **En la consola** (Messaging → Try it out → Send a WhatsApp message → *Sandbox settings*): en **«When a message comes
  in»**, la URL de mensajes entrantes, método **POST**; guardar. «Status callback URL» del sandbox, vacía. Tiene que ser
  **la misma cadena exacta** que `TWILIO_WEBHOOK_URL` (con `https`, sin barra final), o todas las firmas darán `403`.
- Hasta tener DNS se puede probar con la URL `cleverapps.io` en los dos sitios a la vez (consola y variable).
- **Límite del sandbox para el piloto:** cada móvil tiene que enviar «join …» al número del sandbox y volver a hacerlo
  si pasan 72 h sin escribir. Para el piloto real hace falta un número propio de WhatsApp Business (verificación de
  Meta, plantillas fuera de la ventana de 24 h): tarea del bloque C, fuera de este plan.

## 6. Web instalable (PWA), solo instalable

Lo mínimo para que Chrome y Edge muestren «Instalar Ganera» en la barra de direcciones y en el menú:
- `frontend/public/manifest.webmanifest`: `name` «Ganera», `short_name` «Ganera», `lang` `es`, `start_url` `/`,
  `scope` `/`, `display` `standalone`, `background_color` y `theme_color` con los tokens de marca de `DESIGN.md`, e
  iconos **192 × 192** y **512 × 512** (`purpose: any`) más un **512 maskable** con margen de seguridad (el logo dentro
  del 80 % central, fondo liso).
- Iconos sacados de `public/ganera-logo-rojo.svg` (vector), no del PNG de 512 reescalado: `icon-192.png`, `icon-512.png`,
  `icon-512-maskable.png` y `apple-touch-icon.png` (180, para iOS «Añadir a pantalla de inicio»). Se generan una vez
  con un script fuera del repo y se commitean los PNG.
- `index.html`: `<link rel="manifest">`, `<meta name="theme-color">` y `<link rel="apple-touch-icon">`. Se queda
  `noindex`.
- **Sin service worker.** Chrome y Edge actuales ya no lo exigen para instalar. Si en la prueba real no sale
  «Instalar», se añade uno mínimo que **no cachea nada** (solo deja pasar las peticiones), con su test.
- Tests: uno de Vitest que lee el manifiesto (JSON válido, campos obligatorios, que cada icono existe con su tamaño
  real) y que `index.html` lo enlaza. En real, en Chrome y Edge sobre `https://app.ganera.es`: DevTools → Application →
  Manifest sin errores de instalabilidad, aparece «Instalar Ganera», y la app instalada abre la cola.
- **Dos cosas que comprobar en la app instalada:** (1) la sesión está en `sessionStorage`, así que al cerrar la ventana
  de la app se cierra la sesión (igual que al cerrar la pestaña hoy; se queda así); (2) «Abrir ficha para OVZ» usa
  `window.open` y la ficha tiene que heredar la sesión también desde la ventana instalada.

## 7. Seguridad y RGPD del despliegue

- **HTTPS obligatorio:** activar «Force HTTPS» en **las dos** apps (Clever redirige HTTP → HTTPS). HSTS en la API
  con T2. En el frontend estático, HSTS si el runtime admite cabeceras propias (se mira en el despliegue; si no, la
  redirección forzada basta para el piloto).
- **CORS:** hoy ya admite **un solo origen**, exacto (`FRONTEND_ORIGEN`), con los métodos y cabeceras justos. En
  producción: `https://app.ganera.es` y nada más. El smoke comprueba que un origen ajeno recibe `403` sin
  `Access-Control-Allow-Origin`.
- **Copias de PostgreSQL:** las de Clever, **diarias, con 7 días de retención**, incluidas en el plan XXS (se
  descargan desde el panel del add-on). Con datos reales recomiendo además: (a) una **prueba de restauración** antes
  del piloto, en un add-on temporal que se borra después; (b) pedir a soporte de Clever **30 días** de retención si
  el abogado lo ve necesario (es configurable por soporte). No hay recuperación a un instante concreto (PITR) salvo
  pidiéndolo.
  **(migración a Supabase, 2026-10-19):** las copias son las de **Supabase**. En el plan Free probablemente no hay copias descargables ni PITR
  (llegan con Pro): Antonio lo comprueba en el panel antes de los datos reales, y va a la lista del abogado. Riesgo de
  demo: en Free el proyecto **se pausa tras 7 días sin actividad**.
- **Logs sin teléfonos ni textos:** ya es así en el código (teléfonos enmascarados con `EnmascaradorTelefono`, nunca el
  texto del mensaje, cliente de Anthropic con `logLevel(OFF)`, `logServerErrorDetail=false` en la URL de PostgreSQL,
  sin log de SQL). Ojo con dos cosas fuera de nuestro código: **los logs de acceso del proxy de Clever** guardan la
  ruta con su *query string* (por ejemplo `?q=` de la búsqueda de explotaciones, que puede llevar el nombre de un
  ganadero) y la IP de quien entra; y en Clever no se debe activar nunca `ANTHROPIC_LOG=debug`. En el primer
  despliegue reviso el log de arranque y el de un mensaje del sandbox.
- **Dónde están los datos:** la base de datos, la app y sus copias en Clever Cloud, zona París (UE, empresa francesa).
  **Fuera de la UE:** el texto de cada mensaje de WhatsApp pasa por **Twilio** y por **Meta** (EE. UU.) y se envía a
  **Anthropic** (EE. UU.) para extraer el trámite. Eso necesita los DPA de Twilio y Anthropic (cláusulas contractuales
  tipo) y está ya en la lista del abogado, junto con los plazos de retención. Hay que firmar también el DPA de Clever
  (se acepta en la consola).
  **(migración a Supabase, 2026-10-19):** la base de datos y sus copias están en **Supabase**, región **eu-west-1 (Irlanda, sobre AWS)**. Los datos
  se quedan en la UE, pero **Supabase Inc. es una empresa estadounidense**: hace falta su DPA con cláusulas
  contractuales tipo (lista del abogado en `progress.md`). La Data API de Supabase está desactivada; además, RLS en
  todas las tablas y ningún permiso para `anon`/`authenticated`/`service_role` (lo comprueba el script 02).
- **Superficie pública:** solo los tres webhooks (cada uno valida su firma), `/auth/login`, `/error` y
  `/internal/onboarding/gestoria` (cerrado con `401` en cuanto se borra `ONBOARDING_SECRET`). El registro público,
  cerrado (`404`).
- **El `ONBOARDING_SECRET` solo existe mientras das de alta las gestorías.** Para dar de alta una gestoría real más
  adelante, se vuelve a crear, se usa y se borra.

## 8. Pasos uno a uno

**Tu socio (dominio):**
1. Comprueba dónde está el DNS de `ganera.es` y si tiene registro CAA.
2. Cuando tú le avises (paso 7), crea los dos CNAME de §2 con TTL 300.
3. Te avisa al guardarlos; tú compruebas que resuelven.

**Tú (Clever), en este orden:**
1. Crear la cuenta en `console.clever-cloud.com` con el correo de Ganera (no el personal) y activar la verificación en
   dos pasos. Crear una **organización** «Ganera» (que facture la empresa, no la cuenta personal) y añadir a tu socio
   como miembro. Aceptar el DPA.
2. **Pago:** en la organización, Billing → tarjeta de la empresa y datos fiscales (NIF de la sociedad para que la
   factura lleve IVA correcto). Los créditos gratuitos de bienvenida cubren las primeras pruebas.
3. **Base de datos:** Create → add-on → PostgreSQL, versión **16**, plan XXS Small (o Medium para el piloto), zona
   **Paris**. Nombre `ganera-db`.
   **(migración a Supabase, 2026-10-19):** sustituido: sin add-on. El proyecto de Supabase ya existe (Irlanda, PostgreSQL 17.11). En su editor SQL,
   el script `backend/supabase/01-preparar-roles-y-esquema.sql` entero; después, las contraseñas de `ganera_propietario`
   y `ganera_app` con `\password` de `psql` 17 (kit, `LEEME.md`). En el paso 4, nada que enlazar.
4. **Backend:** Create → application → Java + Maven, desde el *git* de Clever (sin GitHub), XS, 1 instancia, sin
   autoescalado, zona Paris, nombre `ganera-api`. Enlazarle `ganera-db` (Service dependencies). Activar «Force HTTPS».
5. **Frontend:** Create → application → Static, pico, zona Paris, nombre `ganera-app`. «Force HTTPS».
6. **Variables** de §4 en cada app (Environment variables → modo experto, pegar los nombres no secretos que te dejo en
   un `.txt`, y escribir tú los secretos).
7. **Desplegar** desde tu terminal, en la raíz del repo: `npm i -g clever-tools`, `clever login`, `clever link <id-api>
   --alias api`, `clever link <id-app> --alias app`, `clever deploy --alias api` y, cuando arranque,
   `clever deploy --alias app`. Primero con las URL `cleverapps.io` en `FRONTEND_ORIGEN` y `VITE_API_BASE_URL`.
   **Avisa a tu socio** para el DNS.
8. **Zona horaria de la BD:** en el panel del add-on (Adminer o `psql`): `ALTER DATABASE <base> SET timezone TO 'UTC';`,
   reconectar y `SHOW timezone;` → `UTC`.
   **(migración a Supabase, 2026-10-19):** ya no hace falta: la pone el script 01 (paso 3). La siembra del paso 9 se pega en el **editor SQL de
   Supabase** (empieza con `SET ROLE ganera_propietario; SET search_path TO ganera;`), y al acabar el script
   `backend/supabase/02-comprobar-seguridad.sql` → 0 filas.
9. **Alta y datos:** `bash alta-gestorias.sh` (una vez), importar los dos Excel desde la app, ejecutar `seed-tramites.sql`;
   después **borrar `ONBOARDING_SECRET`** y reiniciar `ganera-api`.
10. **Dominios:** con los CNAME resolviendo, añadir `api.ganera.es` y `app.ganera.es` en «Domain names» de cada app,
    esperar al certificado, cambiar `FRONTEND_ORIGEN`, `VITE_API_BASE_URL` y las dos `TWILIO_*_URL` a los dominios
    definitivos y redesplegar las dos apps.
11. **Twilio:** cambiar la URL del sandbox (§5).
12. **Smoke** (§9) y pegar la salida (solo PASS/FAIL).

Yo nunca toco la consola de Clever ni la de Twilio, ni veo un secreto. Tú lanzas los comandos y yo leo la salida.

## 9. Tests y smoke contra el entorno real

**En local, antes de desplegar:** `./mvnw clean test` y `npm test`, `npm run build` y `npm run lint` en verde; ensayo del
kit en PostgreSQL embebido (T4).

**`smoke.sh` contra el entorno real** (lo ejecutas tú; solo imprime PASS/FAIL, nunca tokens). Lo que ya tenía, y además:
- Login de las dos gestorías, `401` con contraseña mala.
- Aislamiento: listados, `GET`/`aprobar` de un trámite ajeno → `404`, búsquedas `castanos`/`000107`.
- Aprobar el `[smoke]`; marcar y desmarcar «Hecho en OVZ» con su `version`; descartar y deshacer el `SIN_TRAMITE`.
- **HTTPS:** `http://api…` y `http://app…` redirigen a `https`; la API envía `Strict-Transport-Security`.
- **CORS:** preflight desde `https://app.ganera.es` → `200` con el origen; desde un origen ajeno → `403` sin cabecera.
- **Frontend:** `/` y `/tramites/1` → `200` con `index.html` (*fallback* de SPA); `/manifest.webmanifest` → `200`
  y JSON válido; los iconos → `200`.
- **Cerrado:** registro → `404`; onboarding con secreto inventado → `401`; `POST /webhooks/otro` → `401`.
- **Webhooks con Twilio ya configurado:** entrante sin firma → **`403`** (antes `503`); callback de estado sin firma →
  `403`; Stripe sin firma → `400`.

**A mano, una vez:**
- Log de arranque: Flyway hasta V23, perfil `clever`, sin «Using generated security password», sin H2.
- Navegador real (Chrome y Edge, 1440 y 375 px): login, cola, revisar y aprobar, ficha OVZ en ventana nueva con la
  sesión, «Instalar Ganera» y la app instalada.
- **Sandbox de Twilio desde tu móvil:** mensaje → acuse en el móvil y trámite en la cola de la gestoría del contacto,
  extraído por Haiku; responder desde el modal → «Entregado»/«Leído»; en el log, sin teléfono ni texto.
- La variable `ONBOARDING_SECRET` ya no está en el panel.
- Una copia de seguridad aparece en el panel del add-on al día siguiente. **(migración a Supabase, 2026-10-19):** en el panel de Supabase, si el
  plan Free la ofrece (ver §7).
- **(migración a Supabase, 2026-10-19):** el script `backend/supabase/02-comprobar-seguridad.sql` en el editor SQL → **0 filas**; en el log de
  arranque, «Successfully applied 23 migrations to schema "ganera"», el callback `afterMigrate` y ningún `WARN` de
  Flyway. Antes del despliegue, el smoke contra Supabase desde el portátil (plan del 19/10, T5).

## 10. Estimación

| Bloque | Días |
|---|---|
| T1 `UserDetailsService` + T2 HSTS/forward headers + T5/T6 comprobaciones | 0,5 |
| T3 PWA (iconos, manifiesto, tests, prueba en Chrome y Edge) | 0,5–1 |
| T4 kit al día (generador, siembra, smoke) + ensayo en PostgreSQL embebido | 1 |
| Despliegue contigo (cuenta, apps, variables, DNS, Twilio) + smoke real + navegador + sandbox | 0,5–1 |
| **Total** | **2,5–3,5 días de trabajo** |

Más la espera del DNS y del certificado (normalmente minutos y, como mucho, unas horas), que depende de tu socio.
Orden: T1 → T2 → T3 → T4 (un commit por tarea, aprobado por ti) → despliegue.

## Decisiones que te pido

Cerradas: ver «Decisiones de Antonio (2026-10-14)» al principio.
