# Despliegue en producción: frontend en Vercel, backend en Clever, base de datos en Supabase

**Estado: PLAN. Esta sesión implementa SOLO el bloque A (frontend/Vercel) y el bloque C (runbook).**
Fecha: 2026-10-21. Rama: `feat/despliegue`.

✅ **La base de datos es Supabase (decidido el 19/10).** Backend en Clever, app en Vercel. La
comparativa de coste (sección 1) queda como justificación de esa decisión, no como elección abierta.

Objetivo: dejar el repo listo para desplegar la web app en una URL pública y escribir el runbook de
los pasos manuales, pensado para que lo siga Álvaro (que no escribió el backend). Esta rama es
**configuración, documentación y, como mucho, manejo de variables de entorno**: no se toca código de
funcionalidad, no se crean migraciones de Flyway, no se generan ni escriben secretos.

---

## Reparto de zonas (dado por Álvaro en el chat, 2026-10-21)
`CONTRIBUTING-paralelo.md` **no estaba en el disco** al empezar (solo `.DS_Store` y este plan salían
en `git status`). El reparto se toma de las instrucciones del chat:
- **`frontend/` y `docs/`: míos.** Adelante.
- **`application-clever.yml`, `CLAUDE.md`, `ganera-prompts.md`: compartidos con Antonio, ficheros
  calientes.** NO se editan en esta rama. Los cambios que harían falta van como **propuesta con el
  diff exacto** dentro del runbook (`docs/referencias/despliegue.md`), apartado "Cambios pendientes
  de acuerdo con Antonio".
- **Sin migraciones de Flyway** en esta rama. Si hiciera falta una, se para y se avisa.

## Qué se implementa en esta sesión y qué queda parado
- **Bloque A (frontend/Vercel): SÍ.** `frontend/` es mío.
- **Bloque C (runbook `docs/referencias/despliegue.md`): SÍ.** `docs/` es mío. Incluye los diffs
  propuestos de los tres ficheros calientes, sin aplicarlos.
- **Bloque B (backend en Clever): PARADO.** Cuesta dinero y toca la zona de Antonio.
- **Bloque D (actualizar `CLAUDE.md` / `ganera-prompts.md`): PARADO.** Ficheros calientes; los
  cambios van como diff propuesto en el runbook.

---

## 1. Comparativa de coste mensual de la base de datos (justificación de la decisión del 19/10)

En las tres opciones el **backend sigue en Clever** (instancia Java XS) y el **frontend en Vercel**
(plan Hobby, 0 €). La única variable es dónde vive PostgreSQL. **Precios a confirmar en la consola
de cada proveedor**; aquí van órdenes de magnitud (2026-10).

| | BD en **Clever** (add-on PostgreSQL) | BD en **Supabase Free** | BD en **Supabase Pro** |
|---|---|---|---|
| Coste/mes de la BD | ≈ 5–7 € (XXS Small Space) | **0 €** | ≈ 25 $/mes (~23 €) |
| ¿Se pausa por inactividad? | No | **Sí, a los ~7 días sin uso** (hay que despausar a mano) | No |
| Backups | Según plan del add-on | Limitados | Diarios |
| Trabajo ya hecho que se APROVECHA | **Todo**: `application-clever.yml` tal cual (inyección `POSTGRESQL_ADDON_*`), paso `ALTER DATABASE … timezone`, runbook de Clever, tests contra PostgreSQL embebido | Los tests contra PG embebido valen igual | Igual |
| Trabajo que se REHACE | Nada (es el diseño original) | La inyección `POSTGRESQL_ADDON_*` del perfil `clever` deja de usarse; hay que añadir `sslmode=require`, usar el **pooler en modo sesión** (no el 6543) y revisar el permiso de `ALTER DATABASE` | Igual que Free |
| Riesgo técnico | Bajo (ya probado) | Medio: SSL, IPv4/IPv6, modo del pooler (Flyway y los locks `PESSIMISTIC_WRITE` se rompen con el pooler de transacción) | Medio (igual) |

**Resumen para el socio:**
- **Más barato, menos trabajo y menos riesgo ahora mismo:** dejar la BD en **Clever** (~5–7 €/mes).
  El perfil `clever` ya está hecho y probado contra PostgreSQL real; **no se tira nada**.
- **Supabase Free (0 €)** solo compensa si se acepta que la demo **se pausa** tras una semana parada
  (malo para un enlace que abres cuando quieres) más el pequeño trabajo extra de conexión.
- **Supabase Pro (~23 €/mes)** cuesta parecido al conjunto de Clever pero añade un proveedor nuevo;
  tiene sentido solo si Supabase va a ser la **infraestructura definitiva** (su dashboard, auth,
  storage…) más allá de la demo, no solo "una BD".

**Decisión tomada el 19/10: la base de datos va en Supabase** (solo como base de datos), con el
backend en Clever y la app en Vercel. La comparativa de arriba queda como registro de los costes y
el trabajo de conexión que implica esa opción.

---

## 2. Respuestas a las preguntas (a)–(d)

### (a) Reparto de piezas
Frontend (Vite + React SPA) → **Vercel**. Backend (Spring Boot) → **Clever** (perfil `clever` ya
listo). Base de datos → **Supabase** (decidido el 19/10; ver sección 1).

### (b) Qué deja de valer del perfil `clever` SI la BD pasara a Supabase
Hoy el datasource de `application-clever.yml` se arma con variables que **inyecta el add-on de
Clever** (`POSTGRESQL_ADDON_HOST/PORT/DB/USER/PASSWORD`). Con Supabase nadie las inyecta. Deja de
valer, en concreto (NO se cambia nada ahora; va como diff propuesto en el runbook):
1. La **inyección automática** de credenciales: habría que definirlas a mano o pasar a leer una
   `DATABASE_URL` completa.
2. **`sslmode=require`:** Supabase exige SSL; hoy el `url` no lo lleva.
3. **Conexión correcta (gotcha gordo):** usar el **pooler de Supabase en modo SESIÓN**
   (`…pooler.supabase.com:5432`, usuario `postgres.<ref>`), **NO el de transacción (6543)** —
   Flyway, `open-in-view` y los `PESSIMISTIC_WRITE` se rompen con el pooler de transacción.
4. **`ALTER DATABASE … SET timezone TO 'UTC'`:** en Supabase puede no haber permiso; `TZ=UTC` en el
   entorno basta para la app (solo afectaba al `DEFAULT now()` de filas sembradas a mano).
5. **Versión de PostgreSQL:** tests con PG16; Supabase PG15/17. Migraciones SQL estándar + una Java
   (V18): deberían aplicar. A confirmar en los logs.
6. `logServerErrorDetail=false` sigue valiendo (es de pgjdbc). Y todo lo no-datasource del perfil
   (`hsts-siempre`, `exigir-https`, `origen`, driver explícito) **se mantiene igual**.

### (c) Secretas vs. públicas
**SECRETAS** (solo gestor de contraseñas / consola; nunca en repo, comando ni chat):
`JWT_SECRET`, `ENCRYPTION_KEY`, `ONBOARDING_SECRET`, la **contraseña de la BD** (Clever add-on o
Supabase), `ANTHROPIC_API_KEY`, `TWILIO_ACCOUNT_SID`, `TWILIO_AUTH_TOKEN`, `STRIPE_API_KEY`,
`STRIPE_WEBHOOK_SECRET`.
**PÚBLICA** (se hornea en el bundle del frontend → NUNCA secreta): **`VITE_API_BASE_URL`**, la única
`VITE_*`. Regla dura: jamás meter un secreto en una variable `VITE_*`.
**Config de servidor** (no secreta, vive en la consola de Clever, no en el bundle): `FRONTEND_ORIGEN`,
`TWILIO_WEBHOOK_URL`, `TWILIO_STATUS_CALLBACK_URL`, `TWILIO_WHATSAPP_FROM`,
`STRIPE_PRICE_ID_EXPLOTACION`, `SPRING_PROFILES_ACTIVE`, `TZ`, `APP_FOLDER`, `CC_*`.

### (d) URLs públicas que hay que fijar y dónde (carácter a carácter o fallan en silencio)
| URL | Dónde | Valor | Coincide con |
|---|---|---|---|
| Origen del frontend (CORS) | `FRONTEND_ORIGEN` en **Clever** | `https://<dominio-frontend>` **sin barra final**, https | El dominio real de Vercel |
| Base de la API | `VITE_API_BASE_URL` en **Vercel** | `https://<dominio-api>` sin barra final | El dominio real de Clever |
| Webhook de Twilio | `TWILIO_WEBHOOK_URL` en **Clever** | `https://<dominio-api>/webhooks/twilio/whatsapp` **sin query string** | La consola de Twilio |
| Callback de estado | `TWILIO_STATUS_CALLBACK_URL` en **Clever** | `https://<dominio-api>/webhooks/twilio/whatsapp/estado` | — (la fija la app) |
| Webhook de Stripe | consola de **Stripe**; secreto en `STRIPE_WEBHOOK_SECRET` | `https://<dominio-api>/webhooks/stripe` | El endpoint del dashboard de Stripe |

**Dependencia circular (el fallo clásico):** `FRONTEND_ORIGEN` necesita el dominio del frontend y
`VITE_API_BASE_URL` el del backend. Solución: **fijar los dominios antes de desplegar**
(`app.ganera.es` / `api.ganera.es` con `ganera.es`, que ya está en IONOS). Si se usan los dominios
autogenerados (`*.vercel.app` / `*.cleverapps.io`), desplegar primero y **luego** fijar
`FRONTEND_ORIGEN` y redeploy del backend.

---

## 3. Bloque A — frontend listo para Vercel (implementado en esta sesión)
- `frontend/vercel.json`: framework Vite, `outputDirectory: dist`, y **reescritura SPA** (todas las
  rutas no-fichero → `/index.html`) para que `/tramites`, `/ganaderos/:id`, etc. no den 404 al
  recargar. Es el fallo que más rompe un deploy de React Router (`createBrowserRouter`); se deja
  resuelto.
- `frontend/package.json`: `engines.node >= 22` (Vite 8 pide Node ≥ 22.12; coherente con Vercel y
  con `CC_NODE_VERSION: 22`).
- `frontend/src/shared/api/httpClient.ts`: en **producción** NO hay default a localhost; si falta
  `VITE_API_BASE_URL` el build de producción falla ruidoso y claro al cargar. En desarrollo y en los
  tests se mantiene el `http://localhost:8080` de siempre (guardado con `import.meta.env.PROD`, así
  no se toca ningún test). Es manejo de variable de entorno, no de funcionalidad.
- Verificación: `npm run build` limpio, `dist/` servido en estático local y una ruta interna
  recargada funcionando con el rewrite.

## 4. Bloque C — runbook `docs/referencias/despliegue.md` (implementado)
Pasos manuales numerados, en orden (BD → backend → frontend → actualizar `FRONTEND_ORIGEN` y
webhooks), con qué variable va en qué sitio, qué comprobar y cómo saber que fue bien. Sección "si
falla CORS". **`ENCRYPTION_KEY` en negrita: NO se regenera si ya hay credenciales OVZ guardadas.**
Apartado "Cambios pendientes de acuerdo con Antonio" con los diffs exactos de `application-clever.yml`,
`CLAUDE.md` y `ganera-prompts.md`, sin aplicarlos.

## 5. Restricciones (se respetan)
No crear cuentas, no contratar, no gastar, no generar ni escribir secretos. `ENCRYPTION_KEY`
intocable si hay credenciales OVZ. Registro público cerrado (`GANERA_REGISTRO_*` sin definir). No
tocar código de funcionalidad. No subir `.env` ni valores reales (`.gitignore` ya cubre `.env`,
`.clever.json`, `docs/referencias/*.pdf` — verificado).

## 6. Verificación final
Backend `./mvnw clean test` en verde (no lo toca esta rama, pero se comprueba que sigue verde);
frontend `npm test`, `npx tsc -b`, `npx oxlint` y `npm run build` limpios. Luego, 5 líneas en el
chat con lo que hay que hacer a mano y en qué orden.
