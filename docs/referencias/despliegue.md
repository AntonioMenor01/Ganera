# Runbook de despliegue de Ganera

Pasos manuales para poner la web app en una URL pública. Pensado para que lo siga **Álvaro**, que no
escribió el backend. Cada paso dice **qué variable va en qué sitio**, **qué comprobar después** y
**cómo saber que ha ido bien**.

> **Estado (2026-10-21):** el frontend va a **Vercel** y el backend a **Clever Cloud**. **Dónde vive
> la base de datos está SIN decidir** (Clever add-on vs. Supabase): ver la sección 1. El runbook
> antiguo "todo en Clever" de `ganera-prompts.md` sigue siendo válido para el backend; su parte de
> frontend (runtime Static de Clever) queda **superada por Vercel**.

> ⚠️ **NUNCA se hace `push` a `main`.** Esta rama (`feat/despliegue`) es configuración y
> documentación. Los **secretos los genera Antonio en su terminal** y los pega en la consola del
> proveedor; nunca en el repo, en un comando ni en el chat.

---

## 0. Antes de nada: regla intocable de `ENCRYPTION_KEY`

> ## 🔴 **`ENCRYPTION_KEY` NO SE REGENERA JAMÁS SI YA HAY CREDENCIALES DE OVZ GUARDADAS.**
> Las contraseñas de OVZ se guardan cifradas con esa clave (AES-256-GCM). **Si la cambias, TODAS las
> contraseñas guardadas quedan ilegibles para siempre** y el detalle de trámite / `/ganaderos` daría
> error 500 al intentar descifrarlas. Se genera **una sola vez** y se guarda en el gestor de
> contraseñas. En un entorno nuevo sin datos reales, se genera una y ya no se toca.

---

## 1. Decisión pendiente: dónde vive la base de datos

El backend va a Clever en las dos opciones. Lo único a decidir es PostgreSQL. Resumen (detalle y
comparativa completa en `docs/superpowers/plans/2026-10-21-despliegue-produccion.md`):

| | BD en **Clever** (add-on) | **Supabase Free** | **Supabase Pro** |
|---|---|---|---|
| Coste/mes BD | ≈ 5–7 € | 0 € | ≈ 25 $/mes |
| ¿Se pausa parada? | No | **Sí, ~7 días** | No |
| Trabajo extra | Ninguno (ya hecho) | SSL + pooler sesión + revisar `ALTER DATABASE` | Igual |
| Riesgo | Bajo | Medio | Medio |

**Recomendación para demo/piloto: Clever** (barato, ya probado, no se rehace nada). Supabase solo si
va a ser la infraestructura definitiva. **Lo decide Antonio.** El resto del runbook marca con
**[Clever]** / **[Supabase]** lo que cambia según la opción.

---

## 2. Qué es secreto y qué es público (no confundir nunca)

**SECRETO** — solo en el gestor de contraseñas y en la consola del proveedor; **nunca** en el repo,
un comando o el chat:
`JWT_SECRET`, `ENCRYPTION_KEY`, `ONBOARDING_SECRET`, la **contraseña de la base de datos**,
`ANTHROPIC_API_KEY`, `TWILIO_ACCOUNT_SID`, `TWILIO_AUTH_TOKEN`, `STRIPE_API_KEY`,
`STRIPE_WEBHOOK_SECRET`.

**PÚBLICO** — viaja en el JavaScript del frontend, así que **no puede ser secreto jamás**:
`VITE_API_BASE_URL` (la única variable `VITE_*`). **Regla dura: nunca metas un secreto en una
variable que empiece por `VITE_`.**

**Config de servidor** (no secreta, vive en la consola de Clever, no en el bundle):
`FRONTEND_ORIGEN`, `TWILIO_WEBHOOK_URL`, `TWILIO_STATUS_CALLBACK_URL`, `TWILIO_WHATSAPP_FROM`,
`STRIPE_PRICE_ID_EXPLOTACION`, `SPRING_PROFILES_ACTIVE`, `TZ`, `APP_FOLDER`, `CC_*`.

**Generar los secretos** (Antonio, en Git Bash; cada uno copia al portapapeles sin mostrarlo, pégalo
en la consola antes de generar el siguiente):
```bash
openssl rand -base64 48 | tr -d '\n' | clip   # JWT_SECRET
openssl rand -base64 32 | tr -d '\n' | clip   # ENCRYPTION_KEY (una sola vez, ver sección 0)
openssl rand -hex 32    | tr -d '\n' | clip   # ONBOARDING_SECRET (se borra tras sembrar)
```

---

## 3. Fijar los dominios ANTES de desplegar (evita el baile de CORS)

`FRONTEND_ORIGEN` necesita el dominio del frontend y `VITE_API_BASE_URL` el del backend: si los dejas
para el final, cada uno espera al otro. Decídelos ahora. `ganera.es` ya está en IONOS.
- Frontend (Vercel): `https://app.ganera.es`
- API (Clever): `https://api.ganera.es`

Si prefieres no tocar DNS todavía, usa los autogenerados (`https://<algo>.vercel.app` y
`https://<algo>.cleverapps.io`): entonces hay que **desplegar primero** para conocerlos y **luego**
fijar `FRONTEND_ORIGEN` (paso 7). Con dominios propios te ahorras esa segunda vuelta.

Cada URL debe coincidir **carácter a carácter** con lo que se configure en el proveedor (Twilio,
Stripe) o la validación de firma falla **en silencio**. Siempre **https** y **sin barra final**.

---

## 4. Pasos en orden: BD → backend → frontend → URLs

### Paso 4.1 — Base de datos

**[Clever]**
1. En la consola de Clever: add-on **PostgreSQL 16**, plan **XXS Small Space**, zona **Paris**
   (`ganera-demo-db`).
2. **Enlázalo** a la app de la API (paso 4.2): Clever inyecta solas las variables
   `POSTGRESQL_ADDON_HOST/PORT/DB/USER/PASSWORD`. No hay que copiarlas a mano.
3. En Adminer (desde el panel del add-on): `ALTER DATABASE <nombre-bd> SET timezone TO 'UTC';`,
   reconecta y comprueba que `SHOW timezone;` devuelve `UTC`.
   - **Cómo sé que fue bien:** `SHOW timezone;` = `UTC`.

**[Supabase]** (requiere el diff de `application-clever.yml` del apartado 8, pendiente de Antonio)
1. Proyecto nuevo en Supabase, región europea (p. ej. Frankfurt), PostgreSQL 15/16/17.
2. Coge la cadena de conexión del **pooler en modo SESIÓN**: host `…pooler.supabase.com`, **puerto
   5432**, usuario `postgres.<project-ref>`. **No uses el puerto 6543 (modo transacción):** rompe
   Flyway, `open-in-view` y los bloqueos de los trámites.
3. Define en Clever las variables de conexión (a mano, no hay add-on) con `sslmode=require`.
   - **Cómo sé que fue bien:** en el arranque del backend (paso 4.2) Flyway aplica las migraciones
     sin error de SSL ni de locks.

### Paso 4.2 — Backend (Clever) — **BLOQUE B, lo hace Antonio; aquí solo para referencia**
App Java + Maven, XS, 1 instancia, sin autoescalado, Paris, **enlazada a la BD**.

| Variable | Valor | Tipo |
|---|---|---|
| `APP_FOLDER` | `backend` | config |
| `CC_JAVA_VERSION` | `21` | config |
| `CC_MAVEN_BUILD_GOAL` | `package -DskipTests` | config |
| `CC_RUN_COMMAND` | `java -XX:MaxRAMPercentage=70 -jar target/ganera-core-0.1.0-SNAPSHOT.jar` | config |
| `SPRING_PROFILES_ACTIVE` | `clever` | config |
| `TZ` | `UTC` | config |
| `FRONTEND_ORIGEN` | `https://app.ganera.es` (el dominio del frontend, sin barra) | config |
| `JWT_SECRET`, `ENCRYPTION_KEY`, `ONBOARDING_SECRET` | los genera Antonio (sección 2) | **secreto** |
| `POSTGRESQL_ADDON_*` | **[Clever]** las pone Clever al enlazar | secreto (contraseña) |
| conexión Supabase | **[Supabase]** a mano, con `sslmode=require` | **secreto** |

**No definir:** `GANERA_REGISTRO_*` (abriría el registro público, que está cerrado por diseño), ni
`GANERA_SEGURIDAD_*` / `GANERA_FRONTEND_*` (el binding relajado de Spring pisaría el perfil `clever`).
`ANTHROPIC_API_KEY`, `TWILIO_*` y `STRIPE_*` pueden quedar **vacías** (ver sección 6).

Desplegar: `clever deploy --alias api`. **Comprobar en `clever logs --alias api`** que Flyway aplica
**V1–V23** (hoy el runbook viejo dice V1–V22; ya hay `V22 respuesta_whatsapp` y `V23
descarte_sin_tramite`) y que la app arranca sin errores de bean.
- **Cómo sé que fue bien:** `GET https://api.ganera.es/auth/login` sin cuerpo responde (401/400), no
  un 502/timeout. Un `GET` a una ruta protegida sin token responde **401**.

### Paso 4.3 — Frontend (Vercel) — **BLOQUE A**
1. En Vercel, proyecto nuevo desde el repo, **Root Directory = `frontend`**. Vercel detecta Vite y
   lee `frontend/vercel.json` (framework, `outputDirectory: dist`, rewrite SPA) y `engines.node` de
   `package.json` (Node ≥ 22.12).
2. Variable de entorno de Vercel (**pública**, Production):
   `VITE_API_BASE_URL = https://api.ganera.es` (el dominio de la API, **sin barra final**).
3. Deploy.
   - **Cómo sé que fue bien:**
     - Abre `https://app.ganera.es/` → carga el login.
     - **Recarga en una ruta interna:** entra, ve a `/tramites`, **pulsa F5**. Debe recargar la
       pantalla, **no un 404** (eso prueba el rewrite SPA).
     - Si ves **pantalla en blanco** y en la consola `Falta VITE_API_BASE_URL en el build de
       producción…`, es que esa variable no estaba al hacer el build: defínela y **vuelve a
       desplegar** (se hornea en build, no basta con añadirla después).

### Paso 4.4 — Cerrar las URLs públicas
- Si usaste **dominios propios**, `FRONTEND_ORIGEN` y `VITE_API_BASE_URL` ya son los finales:
  nada más que hacer aquí.
- Si usaste **autogenerados**, ahora que conoces los dominios reales: pon `FRONTEND_ORIGEN` (Clever)
  = dominio de Vercel y `VITE_API_BASE_URL` (Vercel) = dominio de Clever, y **redeploy** de ambos
  (el frontend hay que rebuildearlo para que el nuevo valor entre en el bundle).
- **(Opcional, solo si activas WhatsApp/Stripe)** en la consola del proveedor:
  - Twilio: webhook del número = `https://api.ganera.es/webhooks/twilio/whatsapp` y, en Clever,
    `TWILIO_WEBHOOK_URL` con **ese mismo texto exacto** (sin query string). Callback de estado =
    `https://api.ganera.es/webhooks/twilio/whatsapp/estado` en `TWILIO_STATUS_CALLBACK_URL`.
  - Stripe: endpoint de webhook = `https://api.ganera.es/webhooks/stripe`; copia su firma a
    `STRIPE_WEBHOOK_SECRET`.
  - **Cómo sé que fue bien:** la firma solo valida si coinciden carácter a carácter; si no, el
    webhook responde 403 y no pasa nada (fallo en silencio) — revísalas letra a letra.

---

## 5. Orden de arranque (resumen)
**BD → backend → frontend → cerrar `FRONTEND_ORIGEN` y webhooks.** El backend no arranca sin BD ni
sin un `FRONTEND_ORIGEN` válido (https, sin barra); el frontend necesita `VITE_API_BASE_URL` en build.

---

## 6. Qué funciona con las variables de IA, Twilio y Stripe vacías
La app **arranca igual**. Deja de funcionar solo lo que depende de cada integración:
- **IA (`ANTHROPIC_API_KEY` vacía):** la extracción de cada trámite falla con
  `ExtraccionFallidaException` y el trámite cae a `PENDIENTE_REVISION` (`FALLIDA`). Todo lo manual
  funciona.
- **Twilio vacío (`TWILIO_AUTH_TOKEN`/`TWILIO_WEBHOOK_URL`):** `POST /webhooks/twilio/whatsapp`
  responde **503** (fail-closed) y no guarda nada. Responder al ganadero por WhatsApp
  (`POST /tramites/{id}/respuestas-whatsapp`) responde **503**.
- **Stripe vacío:** no hay checkout; `POST /webhooks/stripe` responde 400 a firma inválida.
- **Sigue funcionando sin NADA de lo anterior:** login, importar Excel, listar/revisar/editar
  trámites, **aprobar trámites**, la ficha para OVZ, "Hecho en OVZ", descartar/deshacer. El registro
  público está **cerrado** (404) por diseño.

---

## 7. Si falla CORS (es lo que más falla)
Síntoma: en el navegador, la petición muere en el *preflight* (OPTIONS) antes de llegar a ningún
controlador; en la consola, "blocked by CORS policy". Un `curl` funcionaría igual (no manda
`Origin`), así que **esto solo se ve en un navegador real**. Causas y arreglo:
1. **`FRONTEND_ORIGEN` no coincide EXACTO** con el origen del navegador: tiene que ser el mismo
   esquema + host + puerto, **https**, **sin barra final** (`https://app.ganera.es`, no
   `https://app.ganera.es/` ni `http://…`). Con el perfil `clever`, `exigir-https: true` rechaza
   cualquier origen que no sea https. Corrige la variable en Clever y **reinicia** el backend.
2. **El frontend llama a otra URL** de la que crees: comprueba `VITE_API_BASE_URL` del build de
   Vercel (si la cambiaste, hay que **rebuildear**, no solo reiniciar).
3. **La app no arrancó** por `FRONTEND_ORIGEN` vacío o inválido: en el perfil `clever` es obligatoria
   y sin ella la app **no arranca** (lo valida `OrigenFrontend`). Mira `clever logs --alias api`.

---

## 8. Cambios pendientes de acuerdo con Antonio (ficheros calientes — NO aplicados)

Estos tres ficheros son compartidos. Aquí van los diffs exactos **como propuesta**; Antonio los
revisa y, cuando dé el visto bueno, se aplican.

### 8.1 `backend/src/main/resources/application-clever.yml`
**Solo si se elige [Supabase].** Si la BD se queda en **[Clever]**, este fichero **no se toca**.

Hoy el datasource recompone la URL con las `POSTGRESQL_ADDON_*` (que inyecta el add-on de Clever) y
**no lleva `sslmode`**. Supabase exige SSL y no inyecta esas variables. Opción recomendada: leer una
`DATABASE_URL` completa (como `application.yml`, pero sin default), donde ya va el host del pooler,
el puerto 5432 y `sslmode=require`.

```diff
 spring:
   datasource:
-    # logServerErrorDetail=false: que un error de restriccion no lleve "Failing row contains (...)"
-    # con datos de mensajes a los logs (nota de B1 para produccion).
-    url: jdbc:postgresql://${POSTGRESQL_ADDON_HOST}:${POSTGRESQL_ADDON_PORT}/${POSTGRESQL_ADDON_DB}?logServerErrorDetail=false
-    username: ${POSTGRESQL_ADDON_USER}
-    password: ${POSTGRESQL_ADDON_PASSWORD}
+    # Supabase: DATABASE_URL completa (pooler en modo SESION, puerto 5432, NO 6543) con
+    # sslmode=require y logServerErrorDetail=false. Sin default: si falta, la app no arranca.
+    # Ejemplo de valor (va en la consola de Clever, NO aqui):
+    #   jdbc:postgresql://aws-0-eu-central-1.pooler.supabase.com:5432/postgres?sslmode=require&logServerErrorDetail=false
+    url: ${DATABASE_URL}
+    username: ${DATABASE_USERNAME}
+    password: ${DATABASE_PASSWORD}
     # Explicito para que el perfil no dependa de que nadie mas defina el driver (en los tests,
     # application.yml de test fija org.h2.Driver y sin esto lo heredaria).
     driver-class-name: org.postgresql.Driver
```
Variables en Clever (**secretas**): `DATABASE_URL`, `DATABASE_USERNAME` (`postgres.<ref>`),
`DATABASE_PASSWORD`. Alternativa mínima (menos recomendada): mantener las `POSTGRESQL_ADDON_*` a mano
y solo añadir `&sslmode=require` al final de la `url` actual.

### 8.2 `ganera-prompts.md` — sección "Despliegue en Clever Cloud — pendiente"
```diff
-**App del frontend** (`ganera-demo`, Static, pico, París; nada secreto, va en el JavaScript público):
+**App del frontend** — SUPERADO (2026-10-21): el frontend pasa a **Vercel** (Root Directory
+`frontend/`, `VITE_API_BASE_URL` como variable pública, rewrite SPA en `frontend/vercel.json`). La
+tabla de abajo (runtime Static de Clever) queda solo como referencia histórica.
```
```diff
-9. `clever deploy --alias api`, revisar en `clever logs --alias api` que Flyway aplica V1–V22 y que arranca; luego
+9. `clever deploy --alias api`, revisar en `clever logs --alias api` que Flyway aplica V1–V23 y que arranca; luego
```
Y añadir, al principio de la sección, una nota: *"Infraestructura revisada el 2026-10-21: frontend en
Vercel, backend en Clever, base de datos POR DECIDIR (Clever add-on vs. Supabase). Ver
`docs/referencias/despliegue.md` y `docs/superpowers/plans/2026-10-21-despliegue-produccion.md`."*

### 8.3 `CLAUDE.md`
Añadir un apartado nuevo (o una nota bajo "C mínimo — demo deployed in the EU", línea ~734) que diga:
> **Despliegue en producción (2026-10-21, `feat/despliegue`).** El frontend se sirve desde **Vercel**
> (Root Directory `frontend/`, `VITE_API_BASE_URL` pública, `frontend/vercel.json` con rewrite SPA y
> `engines.node >= 22.12`; en producción `httpClient.ts` no cae a localhost y falla ruidoso si falta
> `VITE_API_BASE_URL`). El backend sigue en **Clever** con el perfil `clever`. **La base de datos
> está sin decidir** entre el add-on de Clever y **Supabase** (propuesta); comparativa de coste en el
> plan de esa fecha. Runbook: `docs/referencias/despliegue.md`. El runtime Static de Clever para el
> frontend queda superado por Vercel.

---

## 9. Verificación en el repo (antes de dar la rama por lista)
- Frontend: `npm test`, `npx tsc -b`, `npx oxlint` y `npm run build` limpios.
- Backend: `./mvnw clean test` en verde (esta rama no toca el backend; se comprueba que sigue igual).
- `.gitignore` cubre `.env`, `.clever.json` y `docs/referencias/*.pdf` (verificado). No se sube ningún
  valor real.

---

## 10. Nota para Antonio — test que falla en `main` (no tocado)

Al verificar el frontend en esta rama (`feat/despliegue`, macOS) aparece **1 test en rojo de 1084**,
**ajeno a este trabajo** y **ya presente en `main`**:

- **Fichero/línea:** `frontend/src/features/tramites/TramiteReviewDialog.test.tsx:1491`
- **Test:** `it("FALLIDA: aviso fijo en ámbar con icono decorativo, sin botón de cerrar ni región viva", …)`
- **Aserción que falla:** `expect(within(dialogo).getByRole("heading", { name: "Trámite #7" })).toHaveFocus()`
  — el encabezado existe pero no tiene el foco (parece acabar en el centinela del *focus-trap*); huele a
  diferencia de manejo de foco de jsdom entre entornos.
- **Prueba de que NO lo causa esta rama:** se revirtió `httpClient.ts` a su versión de `HEAD`
  (`git show HEAD:…`) y el fichero **siguió fallando igual** (`1 failed | 120 passed (121)`); al
  restaurar mi versión, idéntico resultado. El único cambio funcional de la rama (el guard de
  `VITE_API_BASE_URL`) solo actúa bajo `import.meta.env.PROD`, que es `false` en vitest.
- **Decisión:** se deja **como está**; no se arregla en esta rama (es zona/funcionalidad de Antonio).
  Resto de la suite: 1083 en verde; `tsc -b`, `oxlint` (3 warnings conocidos) y `npm run build` limpios.
