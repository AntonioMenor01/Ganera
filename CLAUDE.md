# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What Ganera is

Ganera automates bovine paperwork ("trámites": alta, baja, censo, movimiento, demora) for Spanish
cattle-ranching **gestorías** (agricultural agencies). It replaces manual entry into OVZ.net (the
official government system) with a WhatsApp-driven flow, AI extraction, and a mandatory human
approval step before anything is written to OVZ.net.

## Domain model

- **Gestoría** — the tenant. Subscribes and pays via Stripe by number of active Explotaciones,
  with a 15-day trial. `modoCartera` (boolean, default `false`) will optionally restrict each
  Usuario to a portfolio of Explotaciones via `usuario_explotacion` — the data model exists but
  filtering isn't implemented yet (see Current status).
- **Usuario** — an employee of a Gestoría. Has a login (JWT, via `POST /auth/login`). No role
  differentiation yet — any Usuario can operate anywhere within their own Gestoría. A Gestoría +
  its first Usuario are created either through the public self-registration
  (`POST /gestorias/registro`, real customers, goes straight to Stripe Checkout — **closed by default
  since C mínimo**, see that bullet) or by hand through
  the internal onboarding endpoint (`POST /internal/onboarding/gestoria`, pilots/support) — see
  Technical decisions and Architecture notes.
- **Ganadero** — a Gestoría's client. **Never logs in.** Has `ovzUsuario` / `ovzPasswordCifrada`
  columns (from `V3`). Whose OVZ account the assisted mode uses — the gestoría’s or each ganadero’s —
  is open until the OVZ mapping session; these columns may move or go. `nif` (added in `V14`, globally
  unique like `codigoRega` and `crotal`) is the real-world business key used by the Excel importer
  (Prompt 3d) to upsert a Ganadero without duplicating on name variations.
- **Explotación** — a farm belonging to a Ganadero, identified by `codigoRega`.
- **Animal** — identified by a full crotal (`ES123456789012`); `crotalUltimosDigitos` is indexed
  separately because that's what Contactos actually type in WhatsApp. The crotales a Tramite
  mentions live in `tramite_crotal` (Prompt A1, see the "Prompt A1" bullet under Technical
  decisions).
- **Contacto** — whoever writes on WhatsApp (titular or employee of a Ganadero), identified by
  phone number. Since Prompt A1 (`V15`) it extends `GestoriaScopedEntity` (`gestoria_id` NOT NULL),
  but `telefono` stays **globally** `UNIQUE` (see Operational flow step 2). The role lives on the
  **relation**, not on the Contacto: `ContactoExplotacion.rol` (`RolContacto`: `TITULAR`,
  `EMPLEADO`; the old `TipoContacto`/`contacto.tipo` was dropped), so the same person can be
  `TITULAR` of one Explotación and `EMPLEADO` of another. Any Contacto (either role) can be linked
  to any number of Explotaciones — there is no "an employee belongs to exactly one Explotación"
  limit. Phones are normalized to E.164 by `TelefonoNormalizador` (pure, no Spring — reusable by
  the 3b webhook): spaces/dots/dashes and a `whatsapp:` prefix are stripped, a bare 9-digit Spanish
  number (starting 6/7/8/9) or `34…`/`0034…` becomes `+34…`, other `+` numbers need 8–15 digits
  and no leading `0`, and `+` + 9 digits starting 6–9 (a Spanish mobile missing its `34`) is
  rejected. **Logical delete:** `Contacto.activo` (`DELETE /contactos/{id}` sets it to `false`,
  `POST /contactos/{id}/reactivar` sets it back). This keeps the history of trámites and mensajes.
  Inactive Contactos never show in listings (unless `?incluirInactivos=true`), never appear in a
  Ganadero's detail, and can't be linked to Explotaciones (`409` via the API, a row error in the
  importer). **The Twilio webhook (Prompt B1) ignores messages from an inactive Contacto:** no
  Tramite is created, the message is kept as `CONTACTO_INACTIVO` and gets an empty TwiML answer.

## Operational flow

1. A Contacto sends a WhatsApp message via Twilio describing a trámite, mentioning animals by the
   last digits of their crotal.
2. **Twilio uses a single WhatsApp number shared by every Gestoría** — so `Contacto.telefono` is
   globally unique (not scoped per-gestoría), and `ContactoRepository.findByTelefono` is a
   deliberately tenant-unscoped lookup: it's the only way to resolve which Gestoría a message
   belongs to when it arrives. Don't "fix" this into a per-tenant lookup.
   **`findByTelefono` is reserved exclusively for the 3b webhook** (no `Authentication`, so no
   tenant yet) — no authenticated code path ever uses it: the importer looks the phone up with
   `ContactoRepository.findByGestoriaIdAndTelefono` (decision 11 of Prompt A1; the method's Javadoc
   says so too), and the `/contactos` endpoints do no phone lookup at all. Creating a Contacto with a phone that already exists anywhere is
   detected by the global `UNIQUE` violation itself (never by a pre-check query) and rejected with
   one generic message. **Known limitation (A1 decision 1):** a phone can't belong to Contactos in
   two Gestorías, and that rejection lets a Gestoría deduce the number exists in another one.
   **Future path:** one WhatsApp number per Gestoría, identifying the Gestoría by the message's
   *destination* number — which would allow `UNIQUE(gestoria_id, telefono)` instead.
   Since Prompt B1 the webhook is wired; see the "Prompt B1" bullet under Technical decisions.
   **WhatsApp por gestoría — fase 1 (2026-11-16, rama `feat/whatsapp-por-gestoria`, migración `V31`;
   plan `docs/superpowers/plans/2026-11-16-whatsapp-numero-por-gestoria.md`).** Dos modalidades, ahora
   resueltas **por el número de DESTINO (`To`)**, no solo por el remitente: **DEDICADO** — un número
   propio por gestoría (tabla `numero_whatsapp`, solo DEDICADO, `gestoria_id NOT NULL`; `To` en la tabla
   → la gestoría es su dueña y el contacto se busca **dentro de ella** con `findByGestoriaIdAndTelefono`,
   así un contacto de otra gestoría que escribe ahí es **desconocido**, sin fuga); **COMPARTIDO** — el
   número único de hoy, **identificado por configuración** (`To == ganera.twilio.whatsapp-from`), se
   resuelve por el teléfono del remitente (`findByTelefono`) como siempre; `To` que no es ni uno ni otro
   → desconocido, como hoy, con un `log.warn` sin el número. El `To` normalizado se guarda en
   `mensaje_campo.telefono_destino` (nunca en un DTO de respuesta) y el número DEDICADO en
   `tramite.numero_whatsapp_id` (FK; **invariante:** si no es null, es de la misma gestoría que el
   trámite). **`NumeroWhatsappRepository.findByNumero` es la nueva consulta sin tenant** (reservada al
   webhook, junto a `findByTelefono` y las dos por SID; lo vigila `ConsultasSinTenantTest` por bytecode).
   Se responde **desde el número por el que entró** (`tramite.numeroWhatsapp`, o el `whatsapp-from`
   para COMPARTIDO/legacy). Los números DEDICADO se dan de alta por `POST /internal/whatsapp/numeros`
   (`X-Internal-Secret`, como el onboarding; `NUMERO_WHATSAPP_REGISTRADO`, actor SISTEMA, nunca el
   teléfono en texto; `To == whatsapp-from` → 400, duplicado → 409). El `UNIQUE` global de
   `contacto.telefono` **se mantiene** (el paso a `UNIQUE(gestoria_id, telefono)` queda aplazado:
   no se puede mientras exista un número COMPARTIDO). Sin cambios en la firma de Twilio (una cuenta,
   un token). Pendiente de fase 2: identidad propia por gestoría (WABA/subcuenta separada, multi-token)
   y el `UNIQUE(gestoria_id, telefono)`. Meta/Twilio: registrar un número existente en Twilio **borra**
   su cuenta de WhatsApp (no hay coexistencia); pasos de consola y requisitos en el anexo del plan.
3. If the Contacto has more than one Explotación and the message doesn't disambiguate, the Tramite
   is created with `explotacion = null` and `estado = PENDIENTE_REVISION` — an employee assigns the
   Explotación by hand in the UI. There is no automatic WhatsApp round-trip asking the Ganadero to
   clarify (that's a future phase).
4. Claude Haiku 4.5 (official `anthropic-java` SDK, `TramiteExtractionService`) extracts
   `{tipoTramite, crotales}` as native structured output from the raw message text only, in the
   background, after the message and the Tramite are already saved (Prompt B1).
5. Crotales are resolved by matching last digits within the resolved Explotación. No match, or more
   than one match, → `PENDIENTE_REVISION`. It is never auto-approved. (Since Prompt A1 each
   mentioned crotal is stored in `tramite_crotal` with its resolution — see the "Prompt A1"
   bullet under Technical decisions. The B1 extraction keeps only the first of several crotales
   that resolve to the same Animal.)
6. An employee reviews the Tramite, corrects it if needed (`PATCH /tramites/{id}`: Explotación,
   tipo, crotales), and clicks "Aprobar" → `APROBADO`, subject to the approval rule in the
   "Prompt A1" bullet. "Aprobar" executes nothing in OVZ. Today (escalón 1) they paste the approved
   data into OVZ by hand (ficha OVZ) and click "Marcar como hecho en OVZ" → `HECHO_EN_OVZ`, recording
   who and when (reversible; see the "Hecho en OVZ" bullet under Technical decisions). In escalón 1
   Ganera sends nothing; escalón 2 is step 7.
7. **OVZ is always assisted, never autonomous** (partners' decision of 2026-10-06,
   `docs/referencias/decisiones-ovz-y-piloto.md`; scope clarified by Antonio on 2026-10-12). Escalón 1
   (done) is the ficha to copy by hand. For escalón 2, **the human gate is the gestor's "Aprobar" in
   Ganera** — nothing reaches OVZ.net without an approved trámite, and no AI confidence ever skips that
   approval. There is no escalón 3 (no batch sending, no automatic retry). A read-only Playwright sync of a
   newly onboarded Ganadero's inventory is still possible (Prompt 3a). Two shapes of escalón 2:
   - For most trámite types (modo asistido, Álvaro's design, pending the OVZ mapping session): Ganera fills
     in the OVZ form and the gestor reviews and sends it — **one trámite at a time, after the gestor's
     "Enviar"; Ganera downloads the justificante**. The "Enviar a OVZ" button and the send states belong to
     escalón 2; they don't appear in design phase 1.
   - **For the baja por muerte — presentación automática desde el servidor (IMPLEMENTED, T0–T6, branch
     `feat/ovz-presentacion-automatica`; plan
     `docs/superpowers/plans/2026-11-17-presentacion-automatica-baja-ovz.md`, decisiones D1–D11; spike
     `docs/spikes/2026-11-17-spike-ovz-presentacion-baja.md`).** After the gestor **approves** a
     `BAJA_MUERTE` in Ganera (that approval **is** the human review — nothing reaches OVZ without it), a job
     is enqueued (`trabajo_ovz`, never inside the approval transaction or the row lock) and an **OVZ worker**
     (separate Docker instance, official Playwright Java image with Chromium, profile `clever,worker`) logs
     into OVZ with the ganadero's encrypted credentials (M2, decrypted only in the worker, never logged),
     navigates by menus, **verifies** the crotal (Código RIIA) and the explotación (REGA) **before** acting,
     and only then presses "Aceptar" itself; it then downloads the justificante (Documentos › "Baja Animal",
     verified by crotal inside the PDF) and stores it in the ganadero's ficha (Postgres, D6). **The worker
     defaults to ENSAYO mode** (`GANERA_OVZ_WORKER_MODO`): it walks everything up to **before** "Aceptar" and
     exits without presenting (`ENSAYO_OK`); REAL mode (the real "Aceptar") only with
     `GANERA_OVZ_WORKER_MODO=REAL`. **Anti-duplicado estricto: una presentación nunca se reintenta** ante una
     respuesta incierta (se consulta Documentos; si no se resuelve → `ERROR_OVZ` incierto con aviso al
     gestor, que solo se reencola con confirmación explícita auditada, D11). Cuentas con **certificado
     digital: no soportadas** en automático (aviso y camino manual, D8). **Fail-closed por doble interruptor
     (T6):** se encola solo si `ganera.ovz.presentacion-automatica.activa` (maestro, apagado por defecto)
     **y** la gestoría está en `ganera.ovz.presentacion-automatica.gestorias` (lista vacía por defecto); si
     no hay credenciales guardadas, cae al camino manual (`HECHO_EN_OVZ`, D4). El enfoque previo de
     **extensión de Chrome** (plan `2026-11-17-modo-asistido-baja-muerte.md`, rama `feat/modo-asistido-baja`)
     **queda aparcado, sin borrar**.

## Non-negotiable rules

- **The human click is the gestor's "Aprobar" in Ganera, and nothing reaches OVZ.net without it** (D10,
  closed 2026-11-17). Ganera presents to OVZ.net **only trámites already approved**, **one at a time**,
  with **prior verification** (crotal + explotación) and **never a retry on an uncertain response**. No AI
  confidence threshold ever skips the approval. Two readings, both subject to this rule:
  - *Modo asistido (most types):* Ganera fills in the OVZ form and the gestor sends it with an explicit
    "Enviar"; only an approved trámite can reach that form.
  - *Baja por muerte (presentación automática, implemented):* the approval **is** the human gate; the
    `BAJA_MUERTE` worker then presses OVZ's real "Aceptar" by itself **only on an approved trámite**, after
    verifying crotal (Código RIIA) and explotación (REGA), and **the worker starts in ENSAYO mode by
    default** (never presses "Aceptar" until `GANERA_OVZ_WORKER_MODO=REAL`). A presentation is **never
    retried** on uncertainty. See Operational flow step 7.
- OVZ.net credentials are encrypted at rest (AES-256-GCM, `CifradorAesGcm`; decrypted only on demand, see the
  "Auditoría" bullet), key from
  `ENCRYPTION_KEY` env var only — never hardcoded, never a silent insecure default (the app fails
  to start if it's unset).
- Twilio webhook idempotency: dedupe by `MessageSid` (`mensaje_campo.message_sid` UNIQUE). A
  retried delivery must never create a duplicate Tramite.
- Multi-tenancy isolation (`gestoria_id` + Hibernate Filters) must never be bypassed for entities
  extending `GestoriaScopedEntity`.
- **NEVER call `findById(id)` directly on a repository for an entity extending
  `GestoriaScopedEntity` from a controller or service reachable by an authenticated endpoint.**
  Always use an explicit finder with `gestoriaId` (e.g. `findByIdAndGestoriaId`), even when
  `gestoriaFilter` is active — a primary-key load does not go through Hibernate's
  result-set/query filter (confirmed in Prompt 4, see `progress.md`). Any reviewer (human or
  subagent) must treat a bare `findById` on a `GestoriaScopedEntity` in new code as a blocking
  finding, not a minor note. The same goes for lookups by a business key: since Prompt A1 the
  unscoped `findByCodigoRega`/`findByNif`/`findByCrotal` no longer exist in the repositories (going
  back to one is a compile error), and the unscoped finders left are
  `ContactoRepository.findByTelefono` and, since `V31`, `NumeroWhatsappRepository.findByNumero`
  (resolución de la gestoría por el número de DESTINO), both reserved for the 3b webhook and both
  vigilados por `ConsultasSinTenantTest` (ningún otro bean puede llamarlos) — see Operational flow step 2.
  The pre-A1 `AnimalRepository.findByExplotacionIdAndCrotalUltimosDigitos` (no `gestoriaId`, no
  callers) was removed right after A1; crotales are resolved by last digits with
  `findByExplotacionIdAndGestoriaIdAndCrotalEndingWithOrderByIdAsc` — use that in 3b too.
- Ganaderos and Contactos never authenticate. Only Usuarios have logins.
- A Gestoría in `TRIAL_EXPIRADO_SIN_PAGO` or `SUSPENDIDA` can never approve trámites (no
  approvals) — gated by `SuscripcionService.puedeAprobarTramites(gestoriaId)`. Fail-closed: no
  `Suscripcion` row for a Gestoría also means no approval, not a free pass. The gate is **only**
  on approving (`POST /tramites/{id}/aprobar`, `403`): editing a trámite (`PATCH`), rejecting it,
  the Excel import and the Contactos endpoints stay allowed (A1 decision 30 — deliberate, not an
  oversight).
- `POST /auth/login` never reveals *why* it rejected a login (unknown email, wrong password, or
  inactive Usuario all return a plain 401) — don't add a more specific error message.
- **The frontend never implies a trámite is executed against OVZ.net when "Aprobar" is clicked** —
  the button only changes `EstadoTramite` in DB, matching the backend exactly, and that stays true in
  escalón 2 (modo asistido): there only the gestor's "Enviar" sends. Don't add wording, spinners, or
  toasts that suggest anything happens in OVZ.net. The "Enviar a OVZ" button and the send states belong
  to escalón 2 and don't appear in design phase 1 (`OvzAutomationService` is still an unimplemented
  skeleton). The OVZ references allowed after approving
  are the manual hand-off of the ficha OVZ escalón 1 ("Abrir ficha para OVZ", which opens the data to
  copy by hand; Ganera sends nothing) and, since "Hecho en OVZ", the state badge, the "Marcar como hecho
  en OVZ" button with its hint and the "Hecho en OVZ por … el …" band — always with the person as the
  subject. `TramiteReviewDialog.test.tsx` and `FichaOvzPage.test.tsx` check the exact list of mentions
  in every state and that nothing says sent/executed/registered/processed/presented/completed. (Since Prompt A2 the review dialog sends
  the trámite's `version` with `PATCH` and `aprobar`, so approving from the UI works again — the
  breakage A1 introduced is closed.)
- **Manual "alta de Ganadero" is allowed (nombre + NIF only); no OVZ-credentials-onboarding screen.**
  Since the frontend-MVP merge, a Ganadero can be created by hand from the UI — `POST /ganaderos`
  (`GanaderoAltaService`, `@Transactional`, gestoriaId **always** from the JWT) + the frontend
  `AltaGanaderoDialog`. The model is deliberately **minimal (nombre + NIF)**: the NIF is validated
  (`NifValidador`) and normalized, duplicates collapse into a `409` (own gestoría: clear motivo;
  another's, via the global `UNIQUE`, a neutral "no disponible" that never reveals the other
  gestoría — same no-enumeration principle as the importer). The alta writes its audit row
  (`GANADERO_CREADO`, actor USUARIO from the JWT, `ORIGEN: MANUAL`, MANDATORY, inside the alta
  transaction; the `409`/rollback leaves **no** row).
  **Teléfono en la ficha (opción B, primer campo; 2026-11-10, rama `feat/ganadero-telefono`):**
  `Ganadero.telefono` (nullable, `VARCHAR(30)`, migración `V29`, **sin `UNIQUE` ni índice**, normalizado a E.164
  por `contacto.TelefonoNormalizador`). **Nunca** identifica al remitente de un WhatsApp (eso es
  `Contacto.telefono`) y **no** sale en `GET /ganaderos`, `GET /buscar`, el detalle de trámite ni la auditoría —
  solo en el detalle del Ganadero (D9). Se informa opcional en `POST /ganaderos` (`{nombre, nif, telefono?}`:
  teléfono inválido no vacío → `400` «El teléfono no es válido…» **tras** el NIF; el `201` no lo trae;
  `GANADERO_CREADO` sin cambios, D5) y se edita con **`PUT /ganaderos/{id}/telefono` `{telefono}`**
  (`GanaderoEdicionService`, carga con `findByIdAndGestoriaId`, gana la última escritura, sin `version`): el cuerpo
  se lee como `JsonNode` para distinguir la clave ausente de `null`; **borrado explícito (A6)** — `{"telefono":
  null}`/`""`/solo espacios → `200` y borra; **sin cuerpo, `{}` o sin la clave `telefono` → `400 {motivo}` «Indica
  el teléfono (o déjalo vacío para quitarlo).», sin cambios ni fila**; inválido o valor no textual → `400` «El
  teléfono no es válido…»; ajeno/inexistente → `404` sin cuerpo (y, si existe en otra gestoría, fila
  `ACCESO_OTRA_GESTORIA` con `OperacionAuditada.EDITAR_GANADERO` en la gestoría del actor, fuera de la transacción
  del servicio, como el detalle). El PUT deja **exactamente una** fila `GANADERO_MODIFICADO`
  (`despues = {"campos_cambiados":["TELEFONO"],"origen":"MANUAL"}` vía el enum `CampoGanadero`; lista vacía si el
  valor no cambia) y **cero `LECTURA_GANADERO`** (reutiliza `GanaderoConsultaService.detalle` sin auditar la
  lectura, A1). El valor del teléfono **nunca** va a auditoría (`DatosAuditoria` no admite `String` libre), a logs
  ni a listados; `CredencialOvzCifradaTipoTest.SETTERS_STRING` pasa a `setNombre, setNif, setOvzUsuario,
  setTelefono` (con test de centinela). Frontend (mock v2 de Álvaro, **añadido sin rediseñar**): campo «Teléfono
  (opcional)» en `AltaGanaderoDialog` y edición en línea en `GanaderoDetallePage`
  (`cambiarTelefonoGanadero(id, telefono|null)`; «Quitar teléfono» envía `null` explícito; una petición a la vez;
  tras el `200` repinta con la respuesta del PUT **sin** volver a pedir el detalle; `404` → «Ganadero no
  encontrado»; foco al botón). La ayuda bajo el campo es literal (D7): «Para que Ganera reconozca sus WhatsApp,
  añade este número como contacto de una explotación». **Pendiente (opción B / contactos):** el teléfono del
  titular puede acabar **duplicado** entre la ficha y el contacto cuando llegue `contacto_titular_id`; **decidir
  antes de los empleados** si se sincroniza o la ficha lee del contacto.
  Don't widen the model further (beyond this first `telefono` field, more "opción B" fields are Antonio's later
  call) and don't add a *second* divergent creation path for Explotaciones/Animales
  — those still come only from the Excel importer (Prompt 3d). (Contactos are the other exception, by
  an explicit A1 decision: importer's optional "Contactos" sheet **or** the `/contactos` CRUD
  endpoints.) The rest of the Ganaderos screens stay read-only except the ficha's editable phone (the list and
  the animales panel stay read-only).
  The OVZ-credentials screen is the screen to store OVZ credentials (encrypted), deferred to escalón
  2; whether they belong to the gestoría or to each ganadero is decided after the OVZ mapping.

## Technical decisions already made

- **Multi-tenancy**: row-level via `gestoria_id` + Hibernate Filters (`GestoriaScopedEntity`,
  filter name `gestoriaFilter`), not schema-per-tenant. The filter is activated per-request in
  `TenantFilterActivationInterceptor` from the JWT's `gestoriaId` claim — it relies on
  `spring.jpa.open-in-view=true`. Public endpoints (webhooks) have no `Authentication`, so the
  filter is simply never enabled for them — that's intentional, not a gap.
  **Critical footgun, found and fixed 2026-07-14 (Prompt 4 final verification):**
  `WebMvcTenantConfig` registered `TenantFilterActivationInterceptor` with no explicit order.
  Spring Boot also registers its own `OpenEntityManagerInViewInterceptor` (from
  `open-in-view=true`) as an MVC `HandlerInterceptor`, and the relative order between two
  different `WebMvcConfigurer` beans' interceptors is **not guaranteed** without an explicit
  `.order(...)`. When ours happened to run before OSIV had bound the request's real
  `EntityManager` to the thread, the injected `@PersistenceContext EntityManager` in the
  interceptor silently fell back to a temporary, non-transactional `EntityManager` — Spring's
  shared-EntityManager proxy creates one on the spot when no context is bound yet, calls
  `enableFilter(...)` on it, and immediately discards it. The result: `gestoriaFilter` was
  **never actually applied** to any real HTTP request — `GET /explotaciones` and `GET /tramites`
  silently returned rows from *every* Gestoría, not just the authenticated one. Every existing
  test passed anyway, because `TenantFilterActivationInterceptorTest` calls
  `interceptor.preHandle(...)` directly (bypassing MVC dispatch and its interceptor ordering
  entirely) and every manual smoke test up to this point only ever exercised **one** Gestoría at
  a time — with only one tenant's data in the DB, an unfiltered query and a correctly filtered
  one return identical results, so the bug was invisible until two real Gestorías with
  overlapping data were compared over real HTTP. Fixed by giving the interceptor an explicit
  `.order(Ordered.LOWEST_PRECEDENCE)` in `WebMvcTenantConfig`, guaranteeing it runs after OSIV
  regardless of `@Configuration` bean ordering. Covered going forward by
  `TenantIsolationEndToEndTest` (`shared/tenant`), a deliberate `@SpringBootTest(webEnvironment =
  RANDOM_PORT)` + `TestRestTemplate` test — the *only* kind of test that exercises real
  interceptor registration/ordering; verified this test actually fails without the fix (reverted
  the `.order(...)` call, confirmed the test caught the leak, then restored it) before trusting
  it as a regression guard. **Any future `HandlerInterceptor` that touches
  `GestoriaScopedEntity` data via the shared `EntityManager` must set an explicit order relative
  to OSIV, or add its own end-to-end two-tenant test — don't trust a direct
  `interceptor.preHandle()` unit test alone.**
  **Second, independent critical footgun, found the same day by a targeted audit Antonio asked
  for after the fix above** (don't assume one fix means the whole risk class is closed — audit
  every endpoint individually): `gestoriaFilter` **only applies to Hibernate queries that
  generate a result-set fetch** (`findAll`, `findByEstado`, any derived query) — it does **not**
  apply to `EntityManager.find()` / Spring Data's plain `findById(id)`, a separate,
  long-standing Hibernate behavior (a primary-key load is resolved directly, bypassing filter
  application). `GET /tramites/{id}`, `POST /tramites/{id}/aprobar`, and
  `POST /tramites/{id}/rechazar` all called `tramiteRepository.findById(id)` with `id` taken
  straight from the path variable — **any authenticated user from any Gestoría could view,
  approve, or reject another Gestoría's trámite by guessing/enumerating its numeric id**, even
  with the interceptor-ordering fix above already in place. Confirmed by writing the failing
  test first (as instructed): all three returned `200` with the other tenant's data instead of
  `404`. Fixed by adding `TramiteRepository.findByIdAndGestoriaId(Long id, Long gestoriaId)` — an
  explicit derived query with `gestoriaId` as a real query parameter, not reliant on the ambient
  filter at all — and using it in all three endpoints instead of bare `findById(id)`. **Rule
  going forward: any lookup of a `GestoriaScopedEntity` by an id that came from a path
  variable/request body (as opposed to the caller's own JWT-derived id, e.g. `/auth/me`'s
  `findById(principal.usuarioId())`, which is safe because the id is never attacker-controlled)
  must use an explicit `findByIdAndGestoriaId`-style query — never bare `findById(id)`.** A full
  audit of every endpoint touching a `GestoriaScopedEntity` (`GET /explotaciones`,
  `GET /tramites`, `GET /tramites/{id}`, `POST /tramites/{id}/aprobar`,
  `POST /tramites/{id}/rechazar`, `GET /auth/me`, `POST /explotaciones/importar`) now has its own
  two-tenant `@SpringBootTest` case in `TenantIsolationEndToEndTest`; `GET /facturacion/suscripcion`
  was confirmed exempt by design (it resolves the Gestoría via
  `SuscripcionRepository.findByGestoriaId(gestoriaId)` — a real query parameter, immune to both bug
  classes above regardless of filter/interceptor state). (`POST /facturacion/checkout`, exempt for
  the same reason, was removed on 2026-10-04 — see "Billing moved out of the app".)
- **AI provider**: Claude Haiku 4.5 via the official SDK `com.anthropic:anthropic-java` (2.68.0;
  Spring AI was removed in Prompt B1 because its 1.0.3 Anthropic client never sends
  `output_config`, so `.entity(...)` was only prompt instructions), pinned to the dated snapshot
  `claude-haiku-4-5-20251001` (not the floating alias) so it can't change under us silently.
  Abstracted behind `TramiteExtractionService` so the provider is swappable. Native structured output
  (`output_config.format`, JSON Schema with `additionalProperties: false`) enforces the JSON shape,
  and the server validates it again. See the "Prompt B1" bullet for the rest.
  **Proveedor activo (2026-11-17, rama `fix/ia-por-defecto-anthropic`):** `ProveedorIaConfig` elige el
  proveedor con `AI_PROVIDER` (`anthropic` | `mistral`); **por defecto `anthropic`** (`application.yml`
  → `${AI_PROVIDER:anthropic}`; el `@Value` y `ProveedorIa.POR_DEFECTO` coinciden, y un valor vacío/no
  reconocido también cae en Anthropic). **El piloto corre con Haiku**, y en Clever `AI_PROVIDER=anthropic`
  (confirmado por Antonio). Mistral (`MistralTramiteExtractionService`, hoy `mistral-small-latest` vía
  `MISTRAL_MODEL`) queda **seleccionable a mano** con `AI_PROVIDER=mistral`, pero **pendiente de rehacer
  con un modelo fijo (snapshot con fecha, no un alias flotante) y de evaluar** antes de usarlo en
  producción — el default se puso en Mistral unos días (commits `15cedba`/`90bbb20`, 2026-10-23) y se
  revirtió aquí. `ProveedorIaPorDefectoProduccionTest` fija que sin `AI_PROVIDER` se elige Anthropic.
- **WhatsApp**: Twilio, one shared number across all Gestorías (see Operational flow above).
- **Automation**: Playwright (Java) for OVZ.net: read-only sync and, in the future, escalón 2 (modo
  asistido): Ganera fills in the OVZ form for the gestor to review — one trámite at a time, after the
  gestor’s “Enviar”; Ganera downloads the justificante (never a submission by Ganera itself; see
  Operational flow step 7).
- **Billing**: Stripe, priced by number of contracted active Explotaciones, 15-day trial.
  `EstadoSuscripcion` has exactly 6 values: `TRIAL` (full access), `TRIAL_EXPIRADO_SIN_PAGO`
  (no approvals), `ACTIVA` (full access), `IMPAGO_GRACIA` (full access, but the
  frontend must surface a warning), `SUSPENDIDA` (no approvals, grace period over),
  `CANCELADA`. The no-approval gate is `SuscripcionService.puedeAprobarTramites`, wired only to
  `POST /tramites/{id}/aprobar` (`403`) — everything else, including editing/rejecting trámites
  and importing, stays available (A1 decision 30).
- **Stripe integration** (`facturacion` package): a single `Price` (`STRIPE_PRICE_ID_EXPLOTACION`,
  placeholder until one is created in the Stripe dashboard) with quantity = active Explotaciones
  count, 15-day trial baked into the Checkout Session. `StripeCheckoutService` sets
  `client_reference_id` = `gestoriaId` on the Checkout Session — this is how the
  `checkout.session.completed` webhook maps back to a Gestoría, not the Stripe customer ID. The
  **only** caller is the public self-registration (`POST /gestorias/registro`,
  `crearSesionCheckoutConCantidadEstimada`). Pilot Gestorías (created
  via `/internal/onboarding/gestoria`) never touch this flow; when the checkout runs and no
  `Suscripcion` row exists yet for the Gestoría, `SuscripcionService.obtenerOCrearSuscripcion`
  creates one in `TRIAL` on the spot (idempotent via
  `saveAndFlush` + catching the `UNIQUE(gestoria_id)` violation, for the double-click case).
  `StripeWebhookService` dispatches the rest of the state machine — the one non-obvious mapping:
  on `customer.subscription.updated` with `status=past_due`, whether that means
  `TRIAL_EXPIRADO_SIN_PAGO` (trial just ended, first charge failed) or `IMPAGO_GRACIA` (an
  already-active subscription's renewal failed) is decided by `previous_attributes.status` — if it
  was `trialing`, the trial expired unpaid; otherwise it's a normal payment-grace case. If Stripe
  doesn't send `previous_attributes` on that event, it falls back to whatever `EstadoSuscripcion` we
  already had stored (`TRIAL` → `TRIAL_EXPIRADO_SIN_PAGO`, anything else → `IMPAGO_GRACIA`).
  **Known limitation, accepted for pilot volume:** webhook re-delivery/out-of-order protection is
  only a monotonic guard on `event.created` (`stripe_ultimo_evento_epoch`, strictly-less-than
  comparison) — there is no dedupe by `event.id`. A daily `@Scheduled` job (`SuscripcionSyncScheduler`, cron
  `0 0 3 * * *` `Europe/Madrid`) reconciles `explotacionesContratadas` against the live Explotación
  count for every `ACTIVA`/`IMPAGO_GRACIA` subscription and pushes quantity changes to Stripe
  (default proration). It runs outside any HTTP request, so the
  `gestoriaFilter` Hibernate filter is never active for its queries — that's an intentional
  cross-tenant job, not a multi-tenancy leak. A failed push for one subscription is caught and
  logged per-iteration so it never aborts the rest of the nightly batch.
  **Known limitation:** `SuscripcionSyncScheduler` only reconciles `ACTIVA`/`IMPAGO_GRACIA`
  subscriptions — a Gestoría still in `TRIAL` never gets its `explotacionesContratadas`
  auto-corrected against its real Explotación count during the trial window. Don't assume `explotacionesContratadas` is
  accurate for a `TRIAL` subscription.
  **Known bugs:**
  1. *`invoice.payment_failed` on an already-`TRIAL_EXPIRADO_SIN_PAGO` subscription.* **FIXED:**
     `StripeWebhookService.procesarPagoFallido`
     maps `TRIAL` **or** `TRIAL_EXPIRADO_SIN_PAGO` → `TRIAL_EXPIRADO_SIN_PAGO`;
     any other stored state still → `IMPAGO_GRACIA`.
  2. *Abandoned trial never expires.* (Still PENDING.) `SuscripcionService.obtenerOCrearSuscripcion` creates the
     `Suscripcion` row in `TRIAL` **before** the Checkout Session is completed.
     If the Gestoría abandons
     Stripe Checkout, no webhook ever arrives and nothing local expires the row, so it stays in
     `TRIAL` — with full approval rights — indefinitely. Needs a design decision (e.g. don't grant
     `TRIAL` until `checkout.session.completed`, or a local trial-expiry check) before fixing. — historia en [docs/historia/decisiones-tecnicas-historico.md](docs/historia/decisiones-tecnicas-historico.md).
- **Landing payment linking (`alta_pendiente` + onboarding link, 2026-10-26):** cobro desde la landing con precio fijo por plan; `alta_pendiente` (V24) + enlace en el onboarding; API-version mismatch resuelto por JSON crudo. **Guard anti-doble-cargo:** cuando `Suscripcion.plan != null` (plan fijo de la landing), `SuscripcionSyncScheduler.calcularAjustes` **excluye** esa suscripción de la reconciliación nocturna de cantidad (si no, empujaría cantidad = nº de Explotaciones a un precio fijo y multiplicaría el cargo); `plan == null` mantiene el comportamiento por-Explotación. Historico completo en [docs/historia/decisiones-tecnicas-historico.md](docs/historia/decisiones-tecnicas-historico.md).
- **Alta automática de cuentas desde la landing — detrás de un flag, APAGADA por defecto** (2026-11-23,
  rama `feat/alta-automatica`, Álvaro; `backend/**` tocado de forma ADITIVA, avisado en la PR). Es la
  **fase automática** que anunciaba `V24`: cuando llega un pago de la landing (fila `alta_pendiente` en
  `PENDIENTE`) se crea sola la cuenta, **sin tocar `OnboardingController` ni `StripeWebhookService`**.
  - **`ganera.alta.automatica.activa` (default `false`, env `GANERA_ALTA_AUTOMATICA_ACTIVA`).** Apagado,
    el planificador `AltaAutomaticaScheduler` **no hace nada** y el alta sigue siendo manual
    (`POST /internal/onboarding/gestoria`): **fusionar esta rama no cambia el comportamiento actual**.
    Encendido, cada `ganera.alta.automatica.intervalo` (default `PT30S`) procesa las `alta_pendiente`
    `PENDIENTE`. Sin `stripe.api-key` no hace nada (las altas llevan una Subscription que hay que enlazar).
  - **`AltaAutomaticaService`** (paquete nuevo `alta`) crea `Gestoria` + `Usuario` + `Suscripcion`
    **reutilizando** `GestoriaRepository`/`UsuarioRepository`/`SuscripcionRepository` y
    `EnlaceStripeService.enlazar` (enlaza la Subscription por su `subscription_id` y recalcula el estado
    desde Stripe), duplicando la secuencia mínima del onboarding en vez de refactorizarlo. Cada alta en su
    propia transacción (`TransactionTemplate`): un fallo de una fila no aborta el lote. Escribe las mismas
    filas de auditoría que el onboarding (`GESTORIA_CREADA`/`USUARIO_CREADO`/`SUSCRIPCION_CREADA`, actor
    `PLANIFICADOR`), por eso la guardia DML lo acepta y está en el inventario (`InventarioAuditoriaTest`).
  - **Contraseña temporal** aleatoria (`SecureRandom`), guardada **solo hasheada** (`PasswordEncoder`); se
    devuelve en claro **solo en memoria** (`CuentaCreada`) para el futuro email de activación (Prompt 3) y
    **nunca** se escribe en logs ni auditoría. De momento **no se envían emails**: al crear una cuenta solo
    se registra un log con ids (el email es el Prompt 3; la promesa de "te hemos enviado un correo" sigue sin
    cumplirse hasta entonces, igual que en el alta manual).
  - **Idempotencia** (las re-entregas de Stripe no duplican cuentas): no crea nada si la alta ya está
    `ENLAZADA`, si ya existe una `Suscripcion` con ese `subscription_id`, o si ya existe un `Usuario` con ese
    email; en esos casos marca la alta `ENLAZADA` sin crear. **Migración `V40`** (rango de Álvaro) añade a
    `alta_pendiente` dos columnas ADITIVAS y NULL (`gestoria_id` BIGINT sin FK, `procesada_en` TIMESTAMP),
    solo para trazabilidad; no cambia el enum de estado (reutiliza `PENDIENTE` → `ENLAZADA`).
- **Public self-registration** (`registro` package) — **closed by default since C mínimo**:
  `ganera.registro.abierto` is `false` in `application.yml` and only
  `RegistroGestoriaEndToEndTest` opens it; while closed, `RegistroCerradoFilter` answers `404` with no
  body before Spring Security, without reading the body or creating anything. The frontend
  `RegistroPage`, its API and the "Regístrate" link were deleted (`/registro` → `/login`); sign-up
  moves to the landing in Prompt C. The rest of this bullet describes the endpoint when open:
  `POST /gestorias/registro`, public (no JWT,
  no shared secret), in parallel with `/internal/onboarding/gestoria` — the internal endpoint is
  unchanged and stays for support/manual cases. Reuses the exact Gestoria→Usuario creation shape proven by
  `OnboardingController` (`RegistroGestoriaService.crearGestoriaYUsuario`, `@Transactional`), but
  does not set a `Suscripcion` itself — it lets the immediate `StripeCheckoutService` call create it
  in `TRIAL` via `obtenerOCrearSuscripcion`. The form asks for a **client-count range**
  (`RangoClientes`: `UNO_A_DIEZ`, `ONCE_A_TREINTA`, `TREINTA_UNO_A_SETENTA_Y_CINCO`,
  `SETENTA_Y_SEIS_O_MAS`) instead of an Explotación count. Stripe still bills by Explotaciones
  (unchanged) — each range's **minimum** client count is multiplied by a starting ratio of 1.3
  Explotaciones/client and rounded up (`RangoClientes.quantityExplotacionesEstimada()`),
  giving quantities 2 / 15 / 41 / 99. Using the range's minimum (not its midpoint) is deliberate: it
  never over-charges before the real inventory is known. `StripeCheckoutService` gained
  `crearSesionCheckoutConCantidadEstimada(gestoriaId, cantidadEstimada)`, which skips the real
  Explotación count and uses the private helper and `configuracionCompleta()` guard. Validation
  (`RegistroGestoriaValidacion`, package-private, pure static methods — deliberately not
  `spring-boot-starter-validation`'s `@Valid`/`@Email`) and the duplicate-email case (a real `UNIQUE(email)` violation, not a pre-check
  `findByEmail`) all collapse into **one identical `400` + generic message** (as `{motivo}`, via
  `shared/web/MotivoErrorResponse`) — so a duplicate email can't be distinguished from a weak
  password or a malformed one (no enumeration oracle for which Gestorías are already customers).
  `RegistroGestoriaService.crearGestoriaYUsuario` deliberately does **not** catch
  `DataIntegrityViolationException` itself — it must propagate out of the `@Transactional` method
  so Spring rolls back the `Gestoria` insert together with the failed `Usuario` insert; catching it
  inside would leave an orphan `Gestoria` row. `RegistroGestoriaController`, outside that
  transaction, is the one that catches it to shape the `400` response (and lets the checkout's
  `StripeException` propagate as a generic `500`). — historia en [docs/historia/decisiones-tecnicas-historico.md](docs/historia/decisiones-tecnicas-historico.md).
- **Excel inventory importer** (`explotacion` package, Prompt 3d): manual fallback for loading
  Explotaciones/Animales when the OVZ.net read sync isn't available.
  `POST /explotaciones/importar` (multipart `.xlsx`, Apache POI) parses two fixed-position sheets —
  "Explotaciones" (`codigo_rega, nombre, nif_ganadero, nombre_ganadero`) and "Animales"
  (`crotal, especie, codigo_rega_explotacion`) — reading by column *index*, not header name. A
  missing required sheet or a file that isn't a valid `.xlsx` throws
  `FicheroImportacionInvalidoException`, the **only** source of the
  importer's `400`, returned by `ExplotacionImportController` as `{motivo}` (no longer plain text).
  The file type is detected by **content** when opening the workbook (`abrirLibro`, which wraps
  only `new XSSFWorkbook(...)`), not by extension or `Content-Type`: an old `.xls` (OLE2) gets
  "El fichero es un Excel antiguo (.xls)…guárdalo como .xlsx…"; anything else (text, random bytes,
  empty, a non-xlsx ZIP, a truncated xlsx) gets "El fichero no es un Excel .xlsx válido." POI's
  English message never reaches the client. Any other failure while processing rows is still a
  `500` (never disguised as "invalid file"). `especie` is validated (must be
  blank or a bovine label — Ganera only handles cattle for now) but **never persisted**: `Animal`
  has no `especie` column, an unsupported value is just a row error. Since Prompt A1 (decision 22)
  the crotal is normalized with `CrotalNormalizador` **before** it is
  stored, so `"es-0100 0000.1234"` is saved as `ES010000001234`; an invalid crotal (including 3
  digits or fewer) or one longer than 20 characters after normalization (`animal.crotal
  VARCHAR(20)`) is a row error. The importer still accepts an *incomplete* crotal (4–12 bare
  digits) into inventory — it is the approval rule that blocks sending one to OVZ.net.
  `crotalUltimosDigitos` is derived as the last 6 characters of the (normalized) crotal.
  An optional third sheet, **"Contactos"** (Prompt A1: `telefono, nombre, codigo_explotacion
  (= codigo_rega), rol`), is processed after the other two if present; `ImportResumenResponse`
  gained a `contactos` summary (0/0/0 without the sheet). Per row: phone normalized, name
  non-blank, rol `TITULAR`/`EMPLEADO` (case-insensitive), Explotación by
  `findByCodigoRegaAndGestoriaId`, Contacto by `findByGestoriaIdAndTelefono` — own and active →
  link added/role updated; own and inactive → row error; absent → inserted, and if the insert hits
  the global `UNIQUE(telefono)` (the phone belongs to another Gestoría) → a generic row error that
  never mentions another Gestoría. **Never `findByTelefono` here.**
  **Known limitation (A1 review M4): existing `Animal.crotal` values were not migrated** — a legacy
  crotal with separators or lowercase re-imports as a *second* Animal and its last digits become
  `AMBIGUO` forever; if a real environment ever holds pre-A1 imported data, run a one-off normalizing
  `UPDATE` on `animal.crotal` (checking for collisions first).
  **Cross-tenant existence (A1 decision 17):** `codigo_rega`, `ganadero.nif`, `animal.crotal` and
  `contacto.telefono` are all *globally* unique, so a Gestoría can still deduce that a value exists
  in another Gestoría (its own value updates, a foreign one fails) — an inherent limitation. The
  importer answers a neutral "No se ha podido guardar la fila: alguno de sus
  identificadores (código REGA, NIF o crotal) no está disponible.", and `procesarExplotacion`/
  `procesarAnimal` only use finders with an explicit `gestoriaId`
  (`findByCodigoRegaAndGestoriaId`, `findByNifAndGestoriaId`, `findByCrotalAndGestoriaId`). The
  unscoped `findByCodigoRega`/`findByNif`/`findByCrotal` were **removed** from the repositories.
  **Per-row transaction isolation (`ExplotacionImportFilaService`):** `ExplotacionImportService`
  upserts by `codigoRega`/`crotal`/`nif` and must
  never abort the whole file on one bad row. All per-row DB work lives in `ExplotacionImportFilaService`, a
  **separate** `@Service` bean (self-invocation within the same class doesn't go through Spring's
  `@Transactional` proxy, so REQUIRES_NEW must live on another bean) whose
  `procesarExplotacion`/`procesarAnimal`/`procesarContacto` each run in
  `@Transactional(propagation = REQUIRES_NEW)` — their own transaction, so one row's constraint
  violation rolls back only that row and never touches the rest of the file.
  **Which `EntityManager` a REQUIRES_NEW row uses (A1 decision 19).** Over HTTP, open-in-view binds the request's
  `EntityManager` to the thread *without* a transaction, and `ExplotacionImportService` is not
  `@Transactional`, so REQUIRES_NEW has nothing to suspend: it starts its transaction on that same
  OSIV `EntityManager`, where `TenantFilterActivationInterceptor` has already enabled
  `gestoriaFilter`. A brand-new `EntityManager` with **no filter active** is only opened when there
  is **no `EntityManager` bound to the thread** (a `@Scheduled` job or any other non-request thread)
  **or there is an outer transaction** for REQUIRES_NEW to
  suspend (a `@Transactional` caller, `@DataJpaTest`). That's why each REQUIRES_NEW method still
  re-enables `gestoriaFilter` with the `gestoriaId` passed in — and every query there
  also takes that `gestoriaId` as an explicit parameter, so it doesn't depend on the filter at all.
  Any other code that reaches for `REQUIRES_NEW` on a bean touching `GestoriaScopedEntity` data
  must do the same.
  `GET /explotaciones` and `GET /tramites` (filterable by `estado`) are the first paginated
  endpoints in the codebase (`Page`/`Pageable`). Since Prompt A1 (decision 21) both query with the JWT's `gestoriaId` as an
  explicit parameter (`findByGestoriaId…`), not only the ambient `gestoriaFilter`, and validate
  `?sort=` against a whitelist. `POST /tramites/{id}/aprobar` gates on
  `SuscripcionService.puedeAprobarTramites` (`403` if false).
  Neither `/aprobar` nor `/rechazar` calls `OvzAutomationService`, and approving never will: sending
  is a separate step of escalón 2 (modo asistido), on the gestor's own "Enviar"; since A1
  both go through `TramiteRevisionService`. A tramite id from another
  Gestoría or that doesn't exist returns `404`. — historia en [docs/historia/decisiones-tecnicas-historico.md](docs/historia/decisiones-tecnicas-historico.md).
- **Frontend Prompt 4 (parcial):** login, dashboard de Explotaciones + import Excel, cola de Tramites + modal, `GET /facturacion/suscripcion` y `GET /tramites/{id}`, CORS. Historico en [docs/historia/decisiones-tecnicas-historico.md](docs/historia/decisiones-tecnicas-historico.md).
- **Prompt A1 — Contactos, crotales in trámites, editable review** (backend only).
  Migrations `V15` (Contacto/ContactoExplotacion become tenant-scoped, `rol`, `activo`, drop
  `contacto.tipo`), `V16` (`tramite_crotal`), `V17` (`tramite.version`).
  **Error format (all new endpoints, decision 8):** `409` + `{ "motivo": "..." }` for state
  conflicts; `404` with **no body** for any resource of another Gestoría *or* nonexistent (never
  reveals existence); `400` for invalid data, with `{ "motivo" }` when the controller decides it
  (Spring's own 400s — malformed JSON, wrong type, non-numeric path id — use Boot's default error
  body). Shared DTO: `shared/web/MotivoErrorResponse`. Every lookup takes the JWT's `gestoriaId`
  as an explicit query parameter; every new endpoint has two-Gestoría `@SpringBootTest` E2E tests.
  **New endpoints:**
  - `GET /contactos?incluirInactivos=false` (paginated; each Contacto with its Explotaciones + rol,
    batch-loaded), `POST /contactos` `{telefono, nombre}` (`201`; `400` invalid phone/blank name;
    `409` generic "No se puede usar ese teléfono para un contacto." if the phone exists anywhere —
    detected by the `UNIQUE` violation propagating out of `ContactoService`'s `@Transactional` and
    caught in the controller, same pattern as `RegistroGestoriaController`), `PUT /contactos/{id}`,
    `DELETE /contactos/{id}` (`204`, logical delete, idempotent), `POST /contactos/{id}/reactivar`,
    `POST /contactos/{id}/explotaciones` `{explotacionId, rol}` (link, or update the role if the
    link exists; `404` if the Contacto **or** the Explotación isn't the caller's; `409` if the
    Contacto is inactive; the service also checks explicitly that Contacto, Explotación and caller
    share the same Gestoría — decision 15, applied in the importer too),
    `DELETE /contactos/{id}/explotaciones/{explotacionId}` (`204`; `404` if the link isn't in the
    caller's Gestoría).
  - `GET /ganaderos` (paginated `{id, nombre, nif, numeroExplotaciones}`) and `GET /ganaderos/{id}`
    (`{id, nombre, nif, explotaciones: [{id, codigoRega, nombre, contactos: [{contactoId, nombre,
    telefono, rol}]}]}`, active Contactos only, one query for explotaciones + one batch query for
    contactos). **No Ganadero response ever exposes `ovzUsuario`/`ovzPasswordCifrada`.**
  - `GET /explotaciones/{id}/animales` (paginated; `404` if the Explotación isn't the caller's).
  - `PATCH /tramites/{id}` (see below).
  **`sort` whitelists on every listing (decision 21 + 7a review M1):** `?sort=` outside the list →
  `400 {"motivo": "Campo de ordenación no permitido."}` (`shared/web/OrdenacionPermitida`).
  `/explotaciones`: `codigoRega, nombre, id` (default `codigoRega, id`); `/tramites`:
  `id, estado, createdAt` (default `createdAt desc, id desc`); `/ganaderos`: `nombre, nif, id`
  (default `nombre, id`); `/explotaciones/{id}/animales`: `crotal, id` (default `crotal`);
  `/contactos`: `nombre, telefono, id` (default `nombre, id`). A client-chosen non-unique sort
  (`?sort=estado`) has no `id` tie-breaker.
  **`tramite_crotal` (`TramiteCrotal`, `GestoriaScopedEntity`):** one row per crotal a Tramite
  mentions — `crotal_indicado` (what was written, normalized; never lost, `UNIQUE(tramite_id,
  crotal_indicado)`), `crotal` (the full crotal if it resolved to exactly one Animal, otherwise =
  `crotal_indicado`), `animal_id`, `resolucion` (`ResolucionCrotal`). `crotal_indicado` is kept
  separately so that changing the Explotación re-resolves from what was *written*, not from the
  crotal completed against the previous Explotación. Exposed as `crotales: [{crotalIndicado,
  crotal, completo, animalId, enInventario, resolucion}]` in `GET /tramites` (loaded in **one**
  query for the whole page), `GET /tramites/{id}` and the PATCH/aprobar/rechazar responses.
  **`completo`** describes what was WRITTEN (`crotalIndicado`), not what it
  resolved to: `1234` resolved `EN_INVENTARIO` is `completo: false`. Computed when the response is
  built (`CrotalNormalizador` on `crotalIndicado`, no column/migration); a stored value that fails
  normalization gives `false` plus a `warn` log, never an exception.
  **`CrotalNormalizador`** (pure, no Spring — also used by the importer, decision 22): strips all
  whitespace (including non-breaking spaces pasted from Excel) and `-`, `.`, `/` (decision 24),
  uppercases; must then be `[A-Z0-9]{1,30}` → else `400`. Digits only and ≤ 3 → `400` ("indica al
  menos los últimos 4 dígitos"). Digits only, 4–12 → **INCOMPLETO** (a suffix; a Spanish crotal
  without `ES` is exactly 12 digits). Two leading letters, 13+ digits, or any other valid shape →
  **COMPLETO**. **Resolution** (`TramiteCrotalService`), always within the Tramite's Explotación and
  the caller's Gestoría (an Animal of another Explotación/Gestoría is never linked nor used to
  complete): COMPLETO by exact match, INCOMPLETO by suffix (`crotal` ends with the digits) →
  `EN_INVENTARIO` (exactly one Animal: full crotal stored, `animal_id` linked), `AMBIGUO` (several),
  `NO_ENCONTRADO` (none), `SIN_EXPLOTACION` (the Tramite has no Explotación yet). Re-resolved when
  the Explotación changes and again on every approve. Duplicate `crotal_indicado` values collapse.
  **`PATCH /tramites/{id}`** `{version, explotacionId?, tipoTramite?, crotales?}` (null = don't
  change; `crotales` is the **complete** list and replaces the previous one — `[]` removes them
  all; PATCH **cannot clear** the Explotación or the tipo). The Explotación is set before the
  crotales are resolved; changing it alone re-resolves the stored ones. Returns
  `TramiteDetalleResponse`. Order of checks: `400` no `version` (in the controller, before any DB
  access — same answer for an own, foreign or nonexistent Tramite) → `400` invalid `tipoTramite`
  (parsed in the controller) → `404` → `409` not `PENDIENTE_REVISION` → `409` stale `version` →
  `404` Explotación of another Gestoría / `400` invalid crotal → `409` two crotales resolve to the
  same Animal (decision 23) → `200`, `version + 1`. If stored crotales collide after an Explotación
  change, a PATCH without `crotales` gets that `409` and the user must send both fields together.
  **Approval rule (`POST /tramites/{id}/aprobar` `{version}`, `TramiteRevisionService.aprobar`),
  in the order actually checked:** `403` subscription (`puedeAprobarTramites`; with a fixed `{motivo}` —
  `TramiteController.MOTIVO_SUSCRIPCION_NO_PERMITE_APROBAR` = "Tu suscripción no permite aprobar
  trámites ahora mismo (prueba terminada o suscripción suspendida). Ponte en contacto con Ganera
  para regularizarla.",
  the same text the frontend shows — that says nothing about the trámite) → `400` no body /
  `{}` / `version: null` (body is `@RequestBody(required = false)` precisely so the `403` still
  comes first) → `404` → `409` not in `PENDIENTE_REVISION` (**approve and reject only from
  `PENDIENTE_REVISION`**, decision 12 — an approved trámite is what escalón 2 (modo asistido) will
  offer to send, so it must not be re-approved or reopened silently) → `409`
  stale `version` → re-resolve every crotal against the current inventory: if **any** resolution
  changed (another Animal, now ambiguous, gone…) → `ResolucionCrotalesCambiadaException`, a `409`
  that **commits** the new resolution and increments the version (`noRollbackFor`, only on
  `aprobar`) so the detail screen shows the fresh state — **the only `409` in the codebase that
  persists data**; never approve something other than what the reviewer saw → otherwise one `409`
  listing **every** failing reason, with full rollback: Explotación missing, tipo missing, a
  crotal `AMBIGUO` or `SIN_EXPLOTACION`, a `NO_ENCONTRADO` crotal that is INCOMPLETO (OVZ.net needs
  the full crotal; a complete `NO_ENCONTRADO` is allowed — an animal entering that isn't in
  inventory yet), the format rule below, two crotales resolving to the same Animal → `200
  APROBADO`, `version + 1`. Crotales themselves are not mandatory.
  **Format rule — PROVISIONAL (decision 28, other countries' lengths still to be confirmed):** to
  approve, a crotal must be `ES` + exactly 12 digits, or another country's 2 letters + 8–12 digits.
  It applies to a `NO_ENCONTRADO` complete crotal (the written one) **and** to an `EN_INVENTARIO`
  one — checked on the **Animal's** stored crotal (Antonio's decision after the 7a review, I1): an
  inventory imported without the `ES` prefix (`010000001234`) would otherwise resolve fine by last
  digits and send an incomplete crotal to OVZ.net. Its `409` motivo tells the user to re-import the
  inventory with the full crotal — there is no endpoint to edit an Animal, so re-importing is the
  only fix. The resolution classification itself doesn't change. `rechazar` has no data
  requirements.
  **Concurrency:** every PATCH/aprobar/rechazar loads the Tramite with
  `findConBloqueoByIdAndGestoriaId` (`PESSIMISTIC_WRITE`, `SELECT … FOR UPDATE`, explicit
  `gestoriaId`) and then `refresh`es it — if anything had already loaded that Tramite into the same
  OSIV `EntityManager`, Hibernate would return the cached, possibly stale instance even though the
  query locks; keep the lock read the first load of that Tramite in the request, or
  keep the refresh. Validation exceptions propagate out of the service's `@Transactional` and are
  mapped in `TramiteController` (never caught inside and committed). `ConcurrencyFailureException`
  (lock not acquired, optimistic conflict) and `DataIntegrityViolationException` → `409` "se estaba
  modificando a la vez", never `500`. On PostgreSQL, if a `statement_timeout` is ever configured,
  SQLState `57014` becomes Spring's `QueryTimeoutException` (not a `ConcurrencyFailureException`) →
  `500`; a `lock_timeout` (`55P03`) does map to `CannotAcquireLock` → `409`. **Optimistic version (decision 27):** `@Version Long version`
  on `Tramite`, returned in `TramiteResponse`/`TramiteDetalleResponse`. PATCH, aprobar **and
  rechazar** **require** the `version` the screen showed (`400` if
  missing, `409` if stale, nothing changes). Rechazar's order: `400` no body / `{}` / `version: null`
  (in the controller, before any DB access — same answer for own, foreign or nonexistent) → `404` →
  `409` not `PENDIENTE_REVISION` → `409` stale `version` → `200 RECHAZADO`, `version + 1`
  (`TramiteRechazarRequest`, its own record so a future rejection reason doesn't touch aprobar). **Every accepted write leaves the version at
  exactly read + 1**, including a crotal-only PATCH, a no-op PATCH and the committed re-resolution
  on aprobar (those only touch `tramite_crotal`, so the increment is forced with an explicit JPQL
  `UPDATE … SET version = version + 1` after a flush — not `PESSIMISTIC_FORCE_INCREMENT`, which
  Hibernate 6.6 silently skipped in `@DataJpaTest`). Estado is checked before version on purpose (a
  Tramite no longer pending gets its specific motivo, true with any version).
  **Risks to respect in escalón 2 (modo asistido):** never open the OVZ.net
  (Playwright) session inside the transaction that holds this row lock (other editors/approvers would
  block for the whole OVZ session, each holding a pooled connection).
  **`/error` is public (decision 29):** Spring's error dispatch (malformed JSON, wrong type,
  non-numeric id) runs unauthenticated — `JwtAuthenticationFilter` is a `OncePerRequestFilter`
  and doesn't re-run on the error dispatch — so without `permitAll("/error")` every such `400`
  became a `401`, and the frontend treats a `401` as a logout. `/error` only renders the already
  decided status (Boot defaults: no message, no stack trace) and makes no other route public;
  `/error/x` and every protected route still return `401` without a JWT. — historia en [docs/historia/decisiones-tecnicas-historico.md](docs/historia/decisiones-tecnicas-historico.md).
- **Mini-prompt backend tras A2:** `GET /explotaciones/{id}`, `?q=`, `{motivo}` en todos los errores, `version` en rechazar, `explotacionCodigoRega`/`Nombre` sin N+1, `completo` por crotal. Sin migraciones. Historico en [docs/historia/decisiones-tecnicas-historico.md](docs/historia/decisiones-tecnicas-historico.md).
- **Explotación search without accents, word by word** (the backend task blocking the pilot).
  Same `GET /explotaciones?q=` contract, frontend untouched.
  - **Normalized columns, one per entity:** `explotacion.busqueda` (`codigoRega` + `" "` + `nombre`,
    `NormalizadorBusqueda.textoExplotacion`) and `ganadero.nombre_busqueda`. Each depends only on
    its own row, so it's recalculated in the entity's **explicit setters** (`Explotacion.setCodigoRega/
    setNombre`, `Ganadero.setNombre`; the column has no public setter) — never `@PrePersist/@PreUpdate`.
    No denormalized Ganadero name in `explotacion` (renaming a Ganadero would leave it stale).
    `VARCHAR` without length (NFKD can lengthen text), `NOT NULL`. **Any SQL that inserts into
    `ganadero`/`explotacion` by hand (smoke seeding) must fill these columns**, or it fails.
  - **`shared/texto/NormalizadorBusqueda`** (pure, no Spring), in order: remove `\p{Sk}` (loose
    accents such as `´ ¨ ¸ ^`) **before** NFKD (`Mart´in` → `martin`, not `mart in`); NFKD and strip
    `\p{M}` (`á→a`, `ü→u`, **`ñ→n`**, **`ç→c`**, `º→o`); lowercase (`Locale.ROOT`); delete everything
    that isn't a letter, digit or whitespace **without leaving a gap** (`Martín-Pérez` → `martinperez`,
    `S.L.` → `sl`; `%`, `_`, `!` disappear, so they never reach the `LIKE` — the `escape '!'` stays
    as a defence); collapse Unicode whitespace. Deliberately **not folded**: `ß`, `ł`, `ø`, `æ`, `œ`,
    `đ`. `palabras(q)`: split `q` on whitespace, normalize each piece, drop empty ones and repeats
    (`martin - perez` → `[martin, perez]`). All pieces empty (`q=%%`) → **empty page**; only a
    blank `q` is the full listing. **More than 8 distinct words → `400 {motivo}` "La búsqueda admite
    como máximo 8 palabras."** (checked after the 100 characters).
  - **Query:** `ExplotacionBusquedaSpecification.porPalabras(gestoriaId, palabras)` — explicit
    `gestoria.id` predicate plus, per word, `(busqueda like %p% or ganadero.nombreBusqueda like
    %p%)`, ANDed; no `lower()` (columns are already lowercase); **no `fetch` in the Specification**
    (it breaks the count). The Ganadero comes from `@EntityGraph(attributePaths = "ganadero")` on
    `ExplotacionRepository.findAll(Specification, Pageable)` (data query only; `ganadero` is joined
    once). `buscarPorTexto` was removed. **Every `Specification` on `Explotacion` must carry the
    JWT's `gestoria.id`**: the inherited `JpaSpecificationExecutor` methods don't filter by Gestoría
    by themselves.
  - **Backfill: Flyway Java migration `db.migration.V18__busqueda_normalizada`** (JDBC only, no
    entities): add the columns, fill them in batches with the same `NormalizadorBusqueda`, `SET NOT
    NULL`. **If the normalization rule ever changes, add a new migration that recalculates both
    columns** (V18 won't run again).
  - **No index:** `like '%p%'` doesn't use a B-tree and the query is bounded by `gestoria_id`. If it
    ever gets slow, a PostgreSQL `pg_trgm` GIN index on the normalized columns (no `unaccent`
    needed) — noted for Prompt C. — historia en [docs/historia/decisiones-tecnicas-historico.md](docs/historia/decisiones-tecnicas-historico.md).
- **Billing moved out of the app (2026-10-04):** se quito `POST /facturacion/checkout` y la `FacturacionPage`; se mantienen webhooks Stripe, `Suscripcion`/estados, `puedeAprobarTramites` y el 403. Historico en [docs/historia/decisiones-tecnicas-historico.md](docs/historia/decisiones-tecnicas-historico.md).
- **Prompt B1 — WhatsApp + AI, the minimum that works.**
  - **Webhook** `POST /webhooks/twilio/whatsapp` (`TwilioWebhookController`): the signature is
    validated with **every** form parameter and the configured public URL
    (`ganera.twilio.webhook-url` / `TWILIO_WEBHOOK_URL`, no query string), never
    `getRequestURL()` (behind a tunnel or proxy it is `http://localhost…`). **Fail-closed:** blank
    token or URL → `503`; missing or bad signature → `403`; then `400` for missing
    `MessageSid`/`From`. Nothing is stored in any of those cases. `RequestValidator` is still built per request (Twilio footgun).
  - **Reception** (`MensajeEntranteService`, one short transaction, message saved first):
    `From` normalized with `TelefonoNormalizador` (one that won't normalize is kept truncated to 30
    and treated as unknown); `findByTelefono` (its only caller); unknown number →
    `NUMERO_DESCONOCIDO`, no Gestoría; inactive Contacto → `CONTACTO_INACTIVO`, no Tramite; active
    → Tramite in **the Contacto's** Gestoría, `origen = WHATSAPP`, the Explotación only if the
    Contacto has exactly one link in its Gestoría (D2), `PENDIENTE_EXTRACCION` + extraction
    `PENDIENTE` (or straight to `PENDIENTE_REVISION` + `SIN_TEXTO` without text). Answer: TwiML with
    "Recibido, tu gestoría lo revisará." only when a Tramite was created; `<Response/>` for
    unknown, inactive and duplicates. **Idempotency:** cheap `existsByMessageSid` + the
    `UNIQUE(message_sid)` violation propagating out of the transaction, confirmed with
    `existsByMessageSid` in the controller. **A message is never lost:** if creating the Tramite
    fails, `MensajeRescateService` (`REQUIRES_NEW`) stores it as `ERROR_RECEPCION` (no Gestoría) and
    answers `<Response/>`; only if that also fails → `500`, with the `MessageSid` in the log
    (Twilio does not retry a 5xx by default). Stored: `MessageSid`, normalized phone, text, `NumMedia`, result, links; **not**
    `ProfileName`, `WaId`, `AccountSid`, media URLs. Logs: never the text; phones masked
    (`EnmascaradorTelefono`).
  - **Extraction in the background** (`ExtraccionTramiteScheduler` every 5 s +
    `ExtraccionTramiteService`): a DB queue (`estado_extraccion`, `proximo_intento_extraccion`,
    compared with an `Instant` from the `Clock` bean in `shared/tiempo/RelojConfig`, never SQL
    `now()`). The AI is called **outside any transaction and without the row lock**; the result is
    applied under `findConBloqueoByIdAndGestoriaId` + `refresh`, re-checking that it still applies.
    The AI only gets the message text, never the inventory; `ExtraccionValidador` normalizes with
    `CrotalNormalizador`, discards and counts invalid ones (`crotales_descartados`), caps at 50, and
    the Tramite keeps only the first crotal per Animal. `NO_IDENTIFICADO` → tipo `null`. **On the
    first failure** the Tramite goes to `PENDIENTE_REVISION` with extraction `FALLIDA`; retries at
    1, 5 and 30 min (4 attempts, `ganera.extraccion.reintentos`). A successful retry is applied only
    if nobody touched the Tramite (`version == version_tras_fallo`); otherwise it is discarded. Retry
    bookkeeping uses JPQL updates that **don't** bump `version` (so nobody with the dialog open gets
    a false `409`). A non-AI error also counts as a failed attempt (otherwise it would block the head
    of the queue). Cross-tenant job like `SuscripcionSyncScheduler`, but every per-Tramite query
    carries its `gestoriaId`. No row claiming: one instance only. `spring.task.scheduling.pool.size:
    3` so a slow AI doesn't delay the Stripe and retention jobs. Disabled in tests
    (`ganera.extraccion.planificador.activo: false`).
  - **AI client** (`AnthropicTramiteExtractionService`): created lazily (the app starts without
    `ANTHROPIC_API_KEY`), `logLevel(OFF)` (`ANTHROPIC_LOG=debug` would dump the message text),
    `max_tokens` 1024, no tools, no thinking. Any `stop_reason` other than `end_turn`, an HTTP or
    network error or invalid JSON → `ExtraccionFallidaException`, which never chains the SDK cause
    (it carries the model's answer). The system prompt describes the types as Antonio decided: sale
    and slaughterhouse are `SOLICITUD_MOVIMIENTO`; `BAJA_MUERTE` is death or slaughter on the farm
    (OVZ type names since the ficha OVZ, `V20`).
  - **Responses:** `TramiteResponse`/`TramiteDetalleResponse` gained `origen` (`"WHATSAPP"`/null),
    `estadoExtraccion` (`PENDIENTE`/`COMPLETADA`/`FALLIDA`/`SIN_TEXTO`/null; `SIN_TRAMITE` since that task) and
    `crotalesDescartados`. The AI's technical failure reason is never exposed.
  - **Retention** (`RetencionMensajesScheduler`, 03:30 Madrid): deletes messages **without** a
    Tramite after `P30D` and empties the text of those **with** one after `P12M` (not an empty
    `SIN_TEXTO` text). Both periods are configurable and **pending confirmation by Ganera's lawyer**,
    together with the Anthropic and Twilio DPAs (Prompt C notes).
  - **Migration `V19`** (SQL): the `tramite` and `mensaje_campo` columns above; `mensaje_campo.cuerpo`
    lost its `@Lob` (`TEXT`). — historia en [docs/historia/decisiones-tecnicas-historico.md](docs/historia/decisiones-tecnicas-historico.md).
- **C minimo (demo en la UE, 2026-10-06):** Clever Paris, perfil `clever`, registro cerrado por defecto, tests contra PostgreSQL real, datos de demo fuera del repo. Historico en [docs/historia/decisiones-tecnicas-historico.md](docs/historia/decisiones-tecnicas-historico.md).
- **Ficha lista para OVZ — escalón 1 de OVZ.** Ganera
  shows an approved trámite's data in the order of the OVZ form, with Copiar on each field, so the gestor
  pastes it into OVZ by hand. Ganera still writes nothing to OVZ (until escalón 2 (modo asistido), see
  Operational flow step 7).
  - **`TipoTramite` = the OVZ forms** (`V20`, SQL): `ALTA_NACIMIENTO`, `BAJA_MUERTE`,
    `SOLICITUD_MOVIMIENTO`, `CONFIRMACION_MOVIMIENTO`, `DECLARACION_CENSO`, `DEMORA_CROTALIZACION`
    (supersedes A1 decision 18's list). `tipo_tramite` widened to `VARCHAR(30)`; every old `MOVIMIENTO`
    became `SOLICITUD_MOVIMIENTO` (an old one can't be told apart from an entrada). An old value in `PATCH` is a `400`. The AI's `TipoExtraido`/prompt and the
    frontend `TIPOS_TRAMITE` changed in the same commit; backend and frontend must deploy together.
  - **`TramiteDetalleResponse` gained `ganaderoNombre`/`ganaderoNif`** (the account to log into OVZ with;
    null without explotación). Never `ovzUsuario`/`ovzPasswordCifrada`:
    `CredencialesOvzDetalleEndToEndTest` (invented password and key) checks neither the plain password
    nor the ciphertext appears in GET/PATCH bodies or logs. **RESOLVED (M2,
    `feat/ovz-descifrado-bajo-demanda`):** the field no
    longer has `@Convert` (`EncryptedStringConverter` was deleted): the ciphertext stays at rest in the entity,
    those endpoints answer `200`, and the password is decrypted only on demand by `DescifradoCredencialOvz`
    (see the "Auditoría" bullet). Same column format, no migration.
  - **Frontend:** `features/tramites/fichaOvz.ts` is the single source of each OVZ form's fields, order,
    hints and deadlines (pure, tested), plus `modoFicha` (D4: only `APROBADO` copies; pending is a preview
    without copy buttons). `/tramites/:id/ovz` (`FichaOvzPage`) lives inside `RequireAuth` but outside
    `AppLayout` (no nav bar). The review dialog opens it with `abrirFichaOvz` (`window.open` with a fixed
    window name and **no `noopener`**, so the new window inherits the `sessionStorage` token; the page sets
    `window.opener = null`). A tab opened without opener (middle click, pasted URL) goes to login and back.
    `shared/ui/BotonCopiar` (Clipboard API, read-only `Input`
    fallback, polite live region, always reads "Copiar" for WCAG 2.5.3). See `DESIGN.md` → "Ficha para OVZ". — historia en [docs/historia/decisiones-tecnicas-historico.md](docs/historia/decisiones-tecnicas-historico.md).
- **Hecho en OVZ — D5 of the ficha OVZ.** `APROBADO` only means "data reviewed"; the gestor now records that **they**
  entered it in OVZ by hand. Ganera sends nothing.
  - **State `HECHO_EN_OVZ`** (after `APROBADO`) + `tramite.hecho_en_ovz_por` (FK to `usuario`) and
    `tramite.hecho_en_ovz_en` (`TIMESTAMP`, `Instant` from the `Clock`, truncated to microseconds so the
    response matches what is stored). Migration `V21` (SQL) with the named `CHECK ck_tramite_hecho_en_ovz`:
    both columns set iff `estado = 'HECHO_EN_OVZ'`. `EJECUTADO_OVZ` was not reused (its label implies
    Ganera did it); `EN_PROCESO`/`EJECUTADO_OVZ`/`ERROR_OVZ` stay in the enum, reserved, but the queue
    filter no longer offers them. **Any hand-written SQL that inserts a `HECHO_EN_OVZ` row must fill both
    columns** (demo seed).
  - **Endpoints** `POST /tramites/{id}/marcar-hecho-ovz` and `POST /tramites/{id}/desmarcar-hecho-ovz`,
    body `{version}` (`TramiteHechoEnOvzRequest`) → `TramiteResponse`. Order: `400` without `version` (in
    the controller, before any DB access) → `404` with no body → `409` wrong state (mark only from
    `APROBADO`, with a distinct motivo if already marked; unmark only from `HECHO_EN_OVZ`) → `409` stale
    `version` → `200`, `version + 1`. Unmarking goes back to `APROBADO`, clears both columns and logs
    `tramiteId`/`usuarioId` at `INFO`. **Who marks always comes from the JWT** (`GaneraUserPrincipal`,
    loaded with `UsuarioRepository.findByIdAndGestoriaId`), never from the body. **No subscription gate**
    (A1 decision 30: only approving is gated). `PATCH` on a `HECHO_EN_OVZ` trámite → `409` "Este trámite
    está marcado como hecho en OVZ. Quita la marca para editarlo."
  - **Responses:** `TramiteResponse`/`TramiteDetalleResponse` gained `hechoEnOvzEn` (ISO-8601 UTC with `Z`)
    and `hechoEnOvzPorNombre` (`Usuario.nombre`, never email or id), both `null` when not marked. The list
    loads the usuario with `@EntityGraph(attributePaths = {"explotacion", "hechoEnOvzPor"})`, guarded by a
    statement-counting test.
  - **Frontend:** badges "Aprobado · falta OVZ" (`warning`) and "Hecho en OVZ" (`success`); the queue shows
    only the badge. `modoFicha("HECHO_EN_OVZ")` = `hecho`: the ficha still copies, with the band "Hecho en
    OVZ por {nombre} el dd/mm/aaaa, hh:mm" (`features/tramites/hechoEnOvz.ts`, always **Europe/Madrid**,
    independent of the system zone). Marking is in the ficha (end of the form, no confirmation) and in
    the review dialog footer; "Quitar la marca" (inline confirmation, "En OVZ no cambia nada") in both. A
    `409` reloads and shows the `motivo`; the ficha also reloads after a network/5xx error. See `DESIGN.md`.
  - **Known limitations:** the FK does not enforce
    that the Usuario belongs to the Tramite's Gestoría (only the service does; hand-seeded data could show
    a foreign name or a `500`). The FK has no `ON DELETE`: deleting a Usuario who marked something would fail. An inactive Usuario with a still-valid JWT can mark, like
    every other endpoint. — historia en [docs/historia/decisiones-tecnicas-historico.md](docs/historia/decisiones-tecnicas-historico.md).
- **B2 reducido — the gestoría replies to the Contacto by WhatsApp.** Always manual: the gestor sees and edits the text, nothing is ever sent by itself.
  - **Entity `RespuestaWhatsapp`** (`whatsapp` package, `GestoriaScopedEntity`, migration `V22`, table
    `respuesta_whatsapp`): tramite, contacto, `telefono_destino` (copy at send time, nullable), `cuerpo`,
    `enviado_por` (from the JWT), `creado_en`/`actualizado_en`, `estado` (`ENVIANDO`, `ENVIADO` = accepted by
    Twilio, `ENTREGADO`, `LEIDO`, `FALLIDO`, `SIN_CONFIRMAR`), `twilio_message_sid` (`UNIQUE`), `codigo_error_twilio`,
    `clave_idempotencia` (`UNIQUE(gestoria_id, clave_idempotencia)`). An `ENVIANDO` row older than 2 minutes is
    exposed as `SIN_CONFIRMAR` (not rewritten). Never stores Twilio's English error text or its response body.
  - **Endpoint** `POST /tramites/{id}/respuestas-whatsapp` `{texto, claveIdempotencia}` (extra JSON fields ignored;
    the recipient is **always** `tramite.contacto`, loaded with the JWT's `gestoriaId`, and must be of the same
    Gestoría). Order: `400 {motivo}` in the controller before any DB access (missing/non-UUID key, blank text,
    over 1600 UTF-16 units, contains `___`) → `503 {motivo}` if Twilio isn't configured (nothing created) → `404`
    no body (another Gestoría's, nonexistent, or a contact of another Gestoría) → same key again → `200` with the
    stored row, **never resent** → `409 {motivo}` (key reused with another text/trámite; origen not `WHATSAPP`;
    estado not in `PENDIENTE_REVISION`/`APROBADO`/`HECHO_EN_OVZ`/`RECHAZADO`; contacto inactive; 24 h window
    closed) → send → `201`. A Twilio failure is **not** an HTTP error: `201` with `FALLIDO` (+ code and a Spanish
    motivo decided by Ganera: 63016 window, 63015 sandbox not joined, 21610 blocked) or `SIN_CONFIRMAR`. No
    subscription gate (D11). Never changes the trámite's estado or `version`.
  - **Idempotency and transactions (D7):** short transaction inserting the row in `ENVIANDO` (the `UNIQUE` settles
    a race; the loser re-reads and returns the stored row) → Twilio call **outside any transaction and without
    locking the trámite** → short transaction with SID/state. Ganera **never retries** a send.
  - **Twilio adapter** (`EnvioWhatsapp` port, `TwilioEnvioWhatsapp`): lazy client, 10 s timeouts, configured only
    with `TWILIO_ACCOUNT_SID`, `TWILIO_AUTH_TOKEN` and the new `TWILIO_WHATSAPP_FROM` (`ganera.twilio.whatsapp-from`,
    normalized to `whatsapp:+…`); the app starts with all of them blank. **Footgun:** twilio-java 10.6.4's
    `HttpClient.reliableRequest` retries any 5xx up to 3 times, which would duplicate a message —
    `HttpClientSinReintentos` (a `NetworkHttpClient` whose `shouldRetry` returns `false`) prevents it, and a 5xx (or
    no status) is classified `SIN_CONFIRMAR`, never `FALLIDO`. Classification is a pure function
    (`ClasificadorEnvioWhatsapp`, skill `test-sin-mocks-externos`); every test uses `EnvioWhatsappFalso`.
  - **24 h window:** last `MensajeCampo` of that Contacto **in that Gestoría** + 24 h, with the `Clock`. Templates
    for outside the window need an own verified number (Prompt C).
  - **Detail:** `TramiteDetalleResponse.whatsapp` (GET and PATCH; `null` if not from WhatsApp or the contact is of
    another Gestoría): `{destinatarioNombre, telefonoEnmascarado, contactoActivo, ventanaAbiertaHasta, firmaGestoria,
    motivoNoDisponible, respuestas[]}` — one query for the replies with their usuario. `TramiteResponse` unchanged.
  - **Retention (D8):** the nightly job also empties the reply text **and** sets `telefono_destino` to null after
    `ganera.retencion.texto-con-tramite` (P12M), idempotent, own transaction. Logs carry only ids, SID and code.
  - **Frontend** (review dialog only, `SeccionWhatsapp` + `useRespuestaWhatsapp` + pure `respuestaWhatsapp.ts`):
    proposed text per state, always editable, signed "— {gestoría}"; a `___` gap is mandatory in
    `PENDIENTE_REVISION`/`RECHAZADO`; **only the `HECHO_EN_OVZ` template mentions OVZ ("ya está hecho en OVZ")**.
    The honesty word list is unchanged and applies to the whole dialog, so no visible text says "enviado": states
    read "Enviando…", "Aceptado por WhatsApp", "Entregado", "Leído", "No ha llegado", "Sin confirmar". The key is
    generated on opening the panel (`claveIdempotencia()`, with a `getRandomValues` fallback for non-secure
    contexts), kept on network/5xx retries, renewed after success or a `409`. Closing the dialog with an edited
    reply asks inline ("Tienes una respuesta sin enviar."). See `DESIGN.md` → "Responder por WhatsApp".
  - **Status callback** (its own commit, backend only). If `TWILIO_STATUS_CALLBACK_URL` (`ganera.twilio.status-callback-url`) is a valid http(s) URL with a
    host and no user, query or fragment (**use `https` in production**), every send asks Twilio to report there;
    otherwise sends go without it and replies stay `ENVIADO`. Endpoint `POST /webhooks/twilio/whatsapp/estado`, no
    JWT, no response body ever. Order: body over 8 KB → `413` (`LimiteCuerpoCallbackEstadoFilter`, only for this
    route, runs **before** Spring Security and before anything reads the body; chunked bodies are read with a cap; the
    filter itself parses the form, always as UTF-8, and query-string parameters enter the signature like in Tomcat)
    → no `TWILIO_AUTH_TOKEN` or no valid callback URL → `503` → missing/invalid `X-Twilio-Signature` (all parameters,
    the **configured** URL, `RequestValidator` per request) → `403` → no `MessageSid`/`MessageStatus` → `400` →
    `200`. Only `MessageSid`, `MessageStatus` and `ErrorCode` are read. Pure transition (`CallbackEstadoWhatsapp`):
    `queued`/`accepted`/`sending`/`sent` → `ENVIADO`, `delivered` → `ENTREGADO`, `read` → `LEIDO`,
    `failed`/`undelivered` → `FALLIDO`; order `ENVIANDO`/`SIN_CONFIRMAR` < `ENVIADO` < `ENTREGADO` < `LEIDO`;
    `FALLIDO` beats only the first three, and a later `delivered`/`read` corrects a `FALLIDO` **and clears the
    error code**; `ErrorCode` stored only if 1–6 ASCII digits (else ignored, the state still applies). Applied by
    **one conditional JPQL `UPDATE … WHERE twilio_message_sid = :sid AND estado IN (:anteriores)`** — repeated,
    out-of-order or concurrent callbacks never go back (checked on embedded PostgreSQL). **The lookup by SID is the
    second deliberately tenant-unscoped query** (the request carries no tenant; the SID is globally `UNIQUE`), next
    to `ContactoRepository.findByTelefono`; there is no finder by SID. Logs: only SID, states and code. — historia en [docs/historia/decisiones-tecnicas-historico.md](docs/historia/decisiones-tecnicas-historico.md).
- **SIN_TRAMITE — messages that ask for nothing, and «Descartar».** Since B1 every
  message of an active Contacto creates a Tramite, so a «vale, gracias» reached the queue.
  - **AI:** `TipoExtraido.SIN_TRAMITE` (distinct from `NO_IDENTIFICADO`: «asks for something, unclear what»); prompt
    with an asymmetric rule («ante la duda, NO_IDENTIFICADO»; a message that mentions an animal, crotal, calf, death,
    sale, move, purchase or the census, or asks the gestoría to note/do something, is never SIN_TRAMITE). It is **not**
    a `TipoTramite`: it becomes `EstadoExtraccion.SIN_TRAMITE` (no migration: `estado_extraccion` has no `CHECK`), and
    the Tramite stays `PENDIENTE_REVISION` with `tipo = null` — **nothing is ever discarded by itself**. Server safety
    net in `ExtraccionValidador`: SIN_TRAMITE with **any** raw crotal (even an invalid one) or with a tipo → treated as
    a normal result (`COMPLETADA`).
  - **Evaluation against the real Haiku:** `ExtraccionSinTramiteEvalTest`, skipped unless `GANERA_EVAL_IA=true` **and**
    `ANTHROPIC_API_KEY` are set (never in CI or a normal `./mvnw clean test`). **Only Antonio runs it**, in his own terminal.
  - **`descartable`** (`DescarteTramite`, the single rule): `PENDIENTE_REVISION` ∧ `SIN_TRAMITE` ∧ no tipo ∧ no
    crotales — computed by the backend and exposed in `TramiteResponse`/`TramiteDetalleResponse` (no new query); the
    frontend never recomputes it. Also `motivoRechazo` (`"SIN_TRAMITE"`/null).
  - **Endpoints** `POST /tramites/{id}/descartar` and `POST /tramites/{id}/deshacer-descarte`, body `{version}` →
    `TramiteDetalleResponse`. Order: `400` without version (controller, before any DB access) → `404` no body → `409`
    state (descartar only from `PENDIENTE_REVISION`; deshacer **only** a discarded one — a normal `RECHAZADO` stays
    irreversible) → `409` stale version → (descartar) `409` not descartable → `200`, version + 1. User from the JWT
    (`findByIdAndGestoriaId`). No subscription gate. Nothing external is ever called.
  - **Migration `V23__descarte_sin_tramite.sql`:** `tramite.motivo_rechazo` (a **code**, never text; the text the user
    sees comes from the frontend) with `ck_tramite_motivo_rechazo` (`IS NULL OR (estado = 'RECHAZADO' AND motivo_rechazo
    IN ('SIN_TRAMITE'))`), and the history table **`tramite_evento`** (`GestoriaScopedEntity`: `tipo`
    `DESCARTADO`/`DESCARTE_DESHECHO` with a `CHECK`, `usuario_id` FK, `creado_en` from the `Clock` truncated to
    micros). One row per action, no new personal data (a user id and a time). Exposed as `historialDescarte: [{tipo,
    usuarioNombre, en}]` in the detail only (one query). **Any test that migrates "to the latest" must expect V1–V23.**
  - **WhatsApp:** a discarded Tramite can't be answered — `ReglasRespuestaWhatsapp.motivoNoDisponible` (origen → estado
    → **descartado** → contacto → ventana) and `POST /tramites/{id}/respuestas-whatsapp` `409`; after «Deshacer», yes.
  - **Frontend:** queue line «Parece un mensaje sin trámite» (neutral, only with `descartable`, **no button in the
    row**); in the dialog the notice, «Descartar» with **one click, no confirmation** (disabled with unsaved changes:
    «Para descartar el mensaje, quita antes los cambios con “Descartar cambios”.»), then the badge «Descartado»
    (`outline`), the band «Descartado como mensaje sin trámite por {nombre} el dd/mm/aaaa, hh:mm.» (Europe/Madrid,
    `descarte.ts`), «Deshacer» with no expiry, and the history lines. The ficha OVZ of a discarded one says «Descartado
    como mensaje sin trámite» / «Un mensaje descartado no se pasa a OVZ.».
  - **Shared contract:** `frontend/src/test/contrato/descarte-sin-tramite.json` holds **real** backend responses;
    `TramiteDescarteEndToEndTest.lasRespuestasRealesCumplenElContratoQueUsaElFrontend` checks the real responses
    against it (exact fields, JSON types, nulls, list sizes) and `contratoDescarte.test.tsx` drives the UI with it. If
    the contract changes on purpose, regenerate it from `backend/target/contrato-real/`. — historia en [docs/historia/decisiones-tecnicas-historico.md](docs/historia/decisiones-tecnicas-historico.md).
- **Clever deployment hardening** (`app.ganera.es` static frontend + `api.ganera.es` backend). Since the database is **Supabase**
  (Ireland), "a new base" means emptying the `ganera` schema and rerunning script 01 (S7). See the "Supabase" bullet.
  - **No default Spring user:** `GaneraApplication` excludes `UserDetailsServiceAutoConfiguration` (it created a `user`
    and logged «Using generated security password»); `SinUsuarioPorDefectoTest` starts its own context to avoid a cached
    one.
  - **HSTS only in production:** `ganera.seguridad.hsts-siempre` (false by default, `true` only in
    `application-clever.yml`, no placeholder) makes `SecurityConfig` send `Strict-Transport-Security: max-age=31536000 ;
    includeSubDomains` on **every** response, because Clever terminates TLS and reaches the app over HTTP. No
    `forward-headers-strategy`: nothing reads the request's scheme/host/IP (Twilio validates against configured URLs).
    The two filters before Spring Security (`RegistroCerradoFilter`, `LimiteCuerpoCallbackEstadoFilter`) don't add it.
  - **CORS fail-closed:** `OrigenFrontend.validar` (pure) accepts only an exact `scheme://host[:port]` (no wildcard,
    path, trailing slash, query, userinfo, default port or bare `:`); with `ganera.frontend.exigir-https` (true only in
    `clever`) only `https`. In `clever`, `ganera.frontend.origen: ${FRONTEND_ORIGEN}` has **no default**: without it, or
    with an invalid value, the app does not start. Never define `GANERA_SEGURIDAD_*`/`GANERA_FRONTEND_*` on Clever
    (relaxed binding would override the profile). `ConfiguracionSeguridadProduccionTest` reads the real YAMLs from disk
    (the test `application.yml` shadows the main one on the test classpath).
  - **Installable web app (PWA), install only:** `public/manifest.webmanifest` (`start_url` `/`, `standalone`, background
    `#f3f2f2`, theme `#eae9e9`), icons 192/512 + 512 maskable + 180 `apple-touch-icon` rendered from
    `ganera-logo-rojo.svg`; **no service worker**, no offline mode or data cache (`pwa.test.ts`). In an installed window the session (`sessionStorage`) ends when the window
    closes.
  - **Deployment kit** outside the repo in `C:\Users\Antonio\Desktop\ganera-clever\`: `alta-gestorias.sh`, two invented Excel files, `seed-tramites.sql` (8 trámites per
    gestoría with OVZ types, one `HECHO_EN_OVZ`, one discardable `SIN_TRAMITE`), `smoke.sh` (41 checks against Clever).
    `seed-tramites.sql` starts with `SET ROLE ganera_propietario; SET search_path TO ganera;`. — historia en [docs/historia/decisiones-tecnicas-historico.md](docs/historia/decisiones-tecnicas-historico.md).
- **Supabase — the production database.** Supabase replaces **only** the database (project in **eu-west-1,
  Ireland**, **PostgreSQL 17.11**, Free plan, UTC, **Data API disabled**); the backend stays on Clever (Paris).
  - **`clever` profile:** `jdbc:postgresql://${SUPABASE_DB_HOST}:${SUPABASE_DB_PORT}/${SUPABASE_DB_NAME}` with
    **fixed** `sslmode=verify-full`, `sslfactory=org.postgresql.ssl.SingleCertValidatingFactory`,
    `sslfactoryarg=classpath:supabase/ca-raiz.crt`, `currentSchema=ganera` and `logServerErrorDetail=false`; Hikari
    `maximum-pool-size: 5`. The app connects as `ganera_app` (`SUPABASE_DB_USER`/`_PASSWORD`); Flyway migrates as
    `ganera_propietario` (`SUPABASE_FLYWAY_USER`/`_PASSWORD`, Boot gives it its own unpooled connection to the same URL),
    `default-schema`/`schemas: ganera`, `create-schemas: false` (script 01 creates it; without it the app doesn't start).
    Through the **pooler in session mode, port 5432**; users are `rol.<ref>` (`ganera_app.<ref>`,
    `ganera_propietario.<ref>`), database `postgres`. **Port 6543 (transaction mode) doesn't work**: it breaks pgjdbc's
    prepared statements and Flyway's advisory lock. Never connect the app as `postgres`.
  - **The CA:** pgjdbc checks the host name after building the socket with that factory, so `verify-full` still
    verifies it. The real CA goes in `src/main/resources/supabase/ca-raiz.crt` (*Supabase Root 2021 CA*, valid until April 2031); `src/test/resources/supabase/ca-raiz.crt` is a test-only CA that shadows it on the test classpath, with the
    test server certificates in `src/test/resources/ssl-pruebas/` (`localhost` only, 100 years, `LEEME.md`).
    `CaSupabaseTest` reads the main one **from disk** (an X.509 CA, valid, Supabase's subject, not the test one).
  - **Seven variables, no defaults:** `SUPABASE_DB_HOST/PORT/NAME/USER/PASSWORD`, `SUPABASE_FLYWAY_USER/PASSWORD` (in
    `.env.example`, commented). `ConexionBaseDeDatosEstricta` (`EnvironmentPostProcessor` in `shared/basedatos`, registered
    in `META-INF/spring.factories`, runs before any bean in **every** startup, not only `clever`) resolves
    `spring.datasource.url/username/password` and `spring.flyway.url/user/password` strictly and fails, naming the
    property and the variable but never a value, if a variable is missing **or defined but blank**. **Never define `GANERA_*`, `DATABASE_*`, `SPRING_DATASOURCE_*` or `SPRING_FLYWAY_*` on Clever** (relaxed
    binding would override the profile).
  - **Roles and schema — `backend/supabase/01-preparar-roles-y-esquema.sql`** (Antonio pastes it whole in Supabase's SQL
    editor as `postgres`; one transaction, idempotent, no passwords): `ganera_propietario` (owns the schema and every
    table; only Flyway and hand-written SQL) and `ganera_app` (`LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION
    NOBYPASSRLS`, fixed if they already existed); `anon`/`authenticated`/`service_role` only if missing (local and
    tests); `GRANT ganera_propietario TO CURRENT_USER WITH SET TRUE, INHERIT FALSE` (PG16+: needed to create the schema
    for it and to `SET ROLE` for seeding/dropping); schema `ganera` owned by `ganera_propietario`, nothing for
    `PUBLIC`/Supabase roles, `USAGE` only for `ganera_app`; `ALTER DATABASE … SET timezone TO 'UTC'`. Passwords
    afterwards with `psql` 17 `\password` (SCRAM is computed client-side; alternative: paste a SCRAM hash, **never** a
    plain password in the editor). **`02-comprobar-seguridad.sql`**: one read-only query, one row per problem (RLS,
    exact policies, privileges, role memberships and attributes, the
    database's `TimeZone=UTC`); **0 rows = correct**. The tests read both from disk: one source.
  - **Callback `db/postgresql/afterMigrate__seguridad_esquema.sql`** (S1): runs after **every** `migrate` on
    `${flyway:defaultSchema}`, idempotent, over **all** tables (future ones too): `ENABLE ROW LEVEL SECURITY` only if
    missing (no `FORCE`: the owner doesn't obey it), `REVOKE ALL … CASCADE` from `PUBLIC, anon, authenticated, service_role` and `ganera_app`, then for `ganera_app`
    `SELECT, INSERT, UPDATE, DELETE` + policy `ganera_app_todo` (`FOR ALL TO ganera_app USING (true) WITH CHECK
    (true)`, created only if missing in `pg_policies`; tenant separation is still the app's job, RLS keeps every other
    role out). The **"insert-only" list** (`solo_insercion`, today only the reserved name `auditoria`;
    the **same** list in 02 — rename both together) gets `SELECT, INSERT` and two policies, never `UPDATE`/
    `DELETE`. `flyway_schema_history`: nothing for `ganera_app`. Sequences: `USAGE` (`BIGSERIAL`). Functions: no
    `EXECUTE` for the outsiders. Fails with a clear message if a role is missing (fail-closed).
  - **`db/{vendor}`:** `spring.flyway.locations: classpath:db/migration,classpath:db/{vendor}` in **both**
    `application.yml` (the test one shadows the main one); Boot resolves `postgresql` or `h2`. **Numbering rule:** the
    version is global across folders — a Postgres-only migration leaves a gap in H2, which Flyway accepts; never two
    files with the same version. `db/h2/` only holds a `LEEME.md` (Maven doesn't copy empty folders).
    **Migration counts as of V30 (crotal-madre):** on PostgreSQL Flyway applies **30** migrations (V1–V30; V26 lives only
    in `db/postgresql`, so it's included on Postgres); on H2 it applies **29** (the same set minus V26, leaving a gap
    at 26: V1–V25, V27, V28, V29, V30). The embedded-Postgres and H2 migration-count tests assert exactly these numbers.
    `spring.flyway.fail-on-missing-locations: true` in both YAMLs: Flyway 10 only logs a missing location at `DEBUG`,
    so a `db/postgresql` missing from the jar would silently skip the security callback.
  - **Hand-written SQL** (seed, fixes) runs as the owner: start with `SET ROLE ganera_propietario; SET search_path TO
    ganera;` (and `SET TIME ZONE 'UTC'`, see C mínimo). `postgres` **cannot** `SET ROLE ganera_app` (on purpose: 02
    flags anyone who can) — to see what the app sees, connect with `psql` as `ganera_app.<ref>`.
  - **Tests:** `PostgresEmbebido` (PostgreSQL 17.11) runs with `ssl=on` and the test certificate, creates
    `admin_supabase` (`CREATEROLE CREATEDB`, **not** superuser, like Supabase's `postgres`) and `anon`/`authenticated`/
    `service_role`/`authenticator` (`LOGIN NOINHERIT`, member of the three), and every `nuevaBaseDeDatos()` is owned by
    `admin_supabase` and gets script 01 run **as `admin_supabase`**. — historia en [docs/historia/decisiones-tecnicas-historico.md](docs/historia/decisiones-tecnicas-historico.md).
- **Auditoría — tabla `auditoria` inmutable + inventario cerrado** (2026-10-21 → 2026-10-30, rama
  `feat/auditoria`; plan `docs/superpowers/plans/2026-10-21-auditoria-base.md`, decisiones D1–D10). Registra quién
  hizo qué y cuándo, sin datos personales en el contenido. **Tabla `auditoria`** (`shared/auditoria`, `V25` común
  H2/PostgreSQL): `gestoria_id` **NOT NULL** + FK a `gestoria`/`usuario` (sin `ON DELETE`), `actor_tipo`
  (`USUARIO`/`SISTEMA`/`WEBHOOK_TWILIO`/`WEBHOOK_STRIPE`/`PLANIFICADOR`), `accion`/`entidad_tipo` (sin `CHECK`: los
  cierran los enums `AccionAuditoria`/`EntidadAuditada` y el inventario), `antes`/`despues` (`VARCHAR(4000)`,
  `DatosAuditoria` con vocabulario `Clave` cerrado — **ningún `String` libre**, un test por reflexión lo vigila),
  `ip`/`request_id` (NULL en planificadores), `creado_en` (`Instant` del `Clock`, micros). Entidad `Auditoria`
  `@Immutable`; `AuditoriaRepository extends Repository<…>` — **solo `save` y lecturas con `gestoriaId` explícito**,
  sin `delete*`. **Inmutabilidad en PostgreSQL** (`V26`, `db/postgresql`): dos triggers `BEFORE` —
  `auditoria_sin_cambios` (UPDATE/TRUNCATE por sentencia) y `auditoria_sin_borrado` (DELETE por fila, por si un futuro
  proceso de retención quisiera borrar fila a fila); el callback de seguridad **falla el arranque** si la tabla existe
  sin sus dos triggers exactos (D2), y RLS deja la tabla «solo inserción» para `ganera_app` (SELECT+INSERT, nunca
  UPDATE/DELETE). **`AuditoriaService`**: `registrar(...)` (`MANDATORY`, exige la transacción de la escritura) y
  `registrarAparte(...)` (`REQUIRES_NEW`, para lecturas sensibles, accesos a otra gestoría y login, que deben quedar
  aunque la operación termine en 404/401/rollback). La gestoría la pasa siempre quien llama desde la entidad tocada;
  con `Actor.usuario` se comprueba que el usuario es de esa gestoría. **IP y `request_id`**: `ContextoPeticionFilter`
  (`HIGHEST_PRECEDENCE`) pone un UUID por petición en el MDC y en la cabecera `X-Request-Id`; `IpCliente` es puro y la
  IP **nunca** decide nada de seguridad. Propiedad `ganera.auditoria.ip-desde-x-forwarded-for`: `false` por defecto
  (local/tests: `getRemoteAddr()`), `true` **solo** en `application-clever.yml`, **sin placeholder** — y **nunca
  definas `GANERA_AUDITORIA_*` en Clever** (relaxed binding abriría el `X-Forwarded-For`). **Regla no-negociable:**
  toda escritura nueva deja su fila de auditoría **y** su entrada en el **inventario cerrado**
  (`InventarioAuditoriaTest`, T6): enumera del contexto todas las rutas de escritura, los `@Scheduled` y los
  `@Modifying`, y los compara con una lista declarada (cada una *auditada→su test* o *exenta→su razón*); una ruta,
  planificador o `@Modifying` nuevo sin declarar pone la suite en rojo. La **guardia DML** (`InspectorDml`,
  `StatementInspector` solo de tests registrado en el `application.yml` de test, + `GuardiaDml`/`GuardiaDmlExtension`
  con `@SinGuardiaDml` para las excepciones) exige, por escenario, que toda escritura de negocio no exenta lleve una
  fila `insert into auditoria`; el acumulador es estático y **asume ejecución de tests en serie** (no actives
  paralelismo de JUnit sin rehacerlo). **Accesos a otra gestoría (D3):** `existsByIdAndGestoriaIdNot` (en los repos de
  `Tramite`/`Contacto`/`Explotacion`/`Ganadero`) + `AccesoAjeno`; cuando un recurso existe en otra gestoría se deja
  una fila `ACCESO_OTRA_GESTORIA` en la gestoría **del actor** (nunca el id de la ajena) y la respuesta **no cambia**
  (sigue `404` sin cuerpo). **Lecturas sensibles (D4):** `GET /tramites/{id}`, `GET /ganaderos/{id}`, `GET /contactos`
  y **`GET /explotaciones/{id}`** dejan `LECTURA_*` (los listados sin texto ni teléfonos, no).
  **`GET /explotaciones/{id}` → `LECTURA_EXPLOTACION`** (hueco cerrado el 2026-11-11, rama
  `fix/auditoria-lecturas-contactos`): la ficha enriquecida de #23–#28 devolvía los teléfonos de los contactos sin
  dejar fila; ahora deja **una** `LECTURA_EXPLOTACION` (`EntidadAuditada.EXPLOTACION`, `antes/despues` null, actor del
  JWT) en el 200, y el 404 ajeno sigue dejando `ACCESO_OTRA_GESTORIA`/`VER_EXPLOTACION`. Misma fórmula que
  `GET /ganaderos/{id}` y **sin migración** (`AccionAuditoria` se cierra por enum; la columna `accion` no tiene
  `CHECK`). La auditoría es un `registrarAparte` (REQUIRES_NEW) aparte de la carga del detalle, así que **las consultas
  del detalle no crecen** (G4, `ExplotacionEndToEndTest.detalleNoCreceEnConsultasConMasContactos`, 1 vs 3 contactos).
  **Guardia `InventarioLecturasTelefonoTest`:** por reflexión, ningún DTO de respuesta de producción (clase o
  envolvente `*Response`/`*Resultado`) puede tener un campo `String` cuyo nombre contenga `telefono`/`movil`/`phone`
  (sin distinguir mayúsculas) sin estar **declarado** auditado (ruta + test de lectura) o exento con su motivo
  (`telefonoEnmascarado`: va enmascarado; `telefonoMovimiento`: configuración de la gestoría). Un campo nuevo sin
  declarar pone el test en rojo. **Limitación (asumida):** vigila **tipos**, no **rutas** — un endpoint GET nuevo que
  reutilice un DTO ya declarado no la dispara (para eso valen D4 y `LecturasSensiblesAuditadasEndToEndTest`), y no ve
  un campo de teléfono con otro nombre. **Consultas sin tenant** (globalmente `UNIQUE`, el
  request no trae gestoría): `ContactoRepository.findByTelefono` (webhook 3b) y las **dos por SID** de
  `RespuestaWhatsappRepository` — `actualizarEstadoPorSid` (B2) y `bloquearPorSid` (`SELECT … FOR UPDATE` que D7 añadió
  para leer el estado anterior exacto y la gestoría de la fila del callback de estado de Twilio). **Exclusiones
  documentadas (escriben sin auditar):** los mensajes sin gestoría (`mensaje_campo` de número desconocido /
  `ERROR_RECEPCION`, D6) y **`AltaPendiente`** (pre-tenant: el checkout de la landing sin `client_reference_id` crea la
  fila antes de que exista la Gestoría; su transición a una Gestoría real sí se audita en el onboarding). La exclusión
  de `alta_pendiente` queda **vigilada** por la guardia (está en `TABLAS_NEGOCIO` ∧ `TABLAS_EXENTAS`; quitarla pone
  rojo el checkout). **`authenticator`** (rol LOGIN de la Data API de Supabase) lo **revoca el callback** de seguridad
  (incluido en `ajenos` si existe; cierra el pendiente de la migración a Supabase) y lo vigila el 02. **PR #7
  (landing/Stripe):** los cambios de estado de suscripción dejan `SUSCRIPCION_ESTADO_CAMBIADO` (actor `WEBHOOK_STRIPE`,
  gestoría de la suscripción); `EnlaceStripeService.enlazar` no tiene acción propia — su resultado va en la fila
  `SUSCRIPCION_CREADA` del onboarding, que desde esta tarea usa el **estado realmente persistido**
  (`suscripcion.getEstado()`), no el literal `ACTIVA` (el camino con `stripeSubscriptionId` puede quedar en otro
  estado según Stripe). **Descifrado de la contraseña OVZ bajo demanda (M2, 2026-11-09):**
  `ganadero/DescifradoCredencialOvz.descifrarPasswordParaEnvioOvz(Ganadero, GaneraUserPrincipal)` es el **único**
  sitio que descifra (`shared/crypto/CifradorAesGcm`); pensado solo para la vía de envío a OVZ del escalón 2, **hoy
  sin llamador de producción**. Orden: (1) la gestoría del Ganadero debe ser la del principal del JWT (única fuente),
  si no `CredencialOvzDeOtraGestoriaException`, sin fila y sin descifrar; (2) fila `CREDENCIAL_OVZ_DESCIFRADA`
  (`GANADERO`, `entidadId` = ganadero, actor USUARIO) con **`registrarAparte` ANTES de descifrar** — queda aunque el
  descifrado o el envío fallen, y si la fila falla no se devuelve el texto en claro; (3) descifrar: un ciphertext
  corrupto o ausente da `DescifradoCredencialOvzException` con mensaje genérico (nunca el valor, sin encadenar la
  causa). **Nunca hay secreto en `antes`/`despues`** (van `null`; `DatosAuditoria` no admite `String`). Devuelve
  `CredencialOvzEnClaro` (`toString()` redactado). Escritores: `Ganadero.setOvzPasswordCifrada` solo acepta
  `CredencialOvzCifrada` (interfaz sellada, implementación package-private que solo crea `CifradorAesGcm.cifrar`):
  pasar un `String` no compila (`CredencialOvzCifradaTipoTest` vigila la estructura); el campo lleva `@JsonIgnore`.
  Es una lectura: no toca el inventario ni la guardia DML. **Pendiente del escalón 2:** mapear
  `CredencialOvzDeOtraGestoriaException` a `404` sin cuerpo y `DescifradoCredencialOvzException` a un error
  controlado (hoy son `RuntimeException` sin mapear), y declarar el endpoint de envío en el inventario cerrado
  (`InventarioAuditoriaTest`). Dos pendientes más para entonces: (a) `descifrarPasswordParaEnvioOvz` es
  `@Transactional`, así que si se llama dentro de otra transacción y se captura su excepción, esa transacción queda
  marcada rollback-only (`UnexpectedRollbackException` al confirmar) — llamarlo fuera de la transacción del envío o
  no capturar dentro de ella; (b) `CifradorAesGcm.descifrar` es público: que `DescifradoCredencialOvz` sea el único
  que descifra es cierto hoy, pero **no está vigilado** por ningún test (cualquier bean podría inyectar el cifrador
  y descifrar sin fila de auditoría).
- **Email infrastructure (SMTP), off by default — reusable piece, not wired to any flow yet**
  (2026-11-23, branch `feat/email-servicio`, Álvaro; `backend/**` touched additively, warned in the
  PR). `shared/email`: `EmailService` (interface — `enviarHtml`/`enviarTexto`/`configurado`) and
  `SmtpEmailService` (the `@Service`, on `JavaMailSender`). **Fail-closed, and merging it changes
  nothing until it's turned on:** `ganera.email.activo` defaults to `false`, so with the config blank
  (host or `from` empty) nothing is sent — a WARN is logged and the call returns `false`, **never
  throws** (a caller's flow can't break because of email); an SMTP send failure is caught the same
  way (WARN with the exception class only — never recipient, subject or body). Config under
  `ganera.email.*` (`activo`, `host`, `puerto`=587, `usuario`, `password`, `from`) from `EMAIL_*`
  env vars, all blank by default (documented commented in `.env.example`; real values only in
  Clever, never in the repo). `EmailConfig` builds the `JavaMailSender` from `ganera.email.*` (not
  `spring.mail.*`, to keep all config under `ganera.*`) and is `@ConditionalOnMissingBean`, so Boot's
  own mail autoconfiguration — which backs off anyway with no `spring.mail.host` — would win if ever
  configured. `spring-boot-starter-mail` added to `pom.xml` (additive; with no host,
  `MailSenderAutoConfiguration` abstains and the app starts exactly as before). Covered by
  `SmtpEmailServiceTest` (off/unconfigured never touches `JavaMailSender`; active+configured builds
  and sends the `MimeMessage` with the right recipient/subject/from and HTML vs text; a send error
  returns `false` without throwing). **Still pending:** wiring it to a flow — e.g. the activation
  email of the landing-payment "automatic phase" (see the Stripe landing-payment bullet's "Still
  pending — automatic phase").
- **Backend build tool**: Maven (not Gradle).

## Architecture notes (backend)

Package layout under `backend/src/main/java/com/ganera/core/`: `gestoria`, `ganadero`,
`explotacion`, `contacto`, `tramite`, `facturacion`, `whatsapp`, `ovz`, `auth`, `onboarding`, and
`shared` (`shared/tenant`, `shared/security`, `shared/crypto`, `shared/web` — `MotivoErrorResponse`,
`OrdenacionPermitida` —, `shared/texto` — `NormalizadorBusqueda` —, `shared/tiempo` — `RelojConfig`, the `Clock`
bean — and `shared/basedatos` — `ConexionBaseDeDatosEstricta`, see the "Supabase" bullet). Java Flyway migrations live in
`backend/src/main/java/db/migration/` (V18 is the first one).

- `Tramite.estado` (`EstadoTramite`) has exactly 8 values:
  `PENDIENTE_EXTRACCION, PENDIENTE_REVISION, APROBADO, HECHO_EN_OVZ, EN_PROCESO, EJECUTADO_OVZ,
  ERROR_OVZ, RECHAZADO` (`HECHO_EN_OVZ` since "Hecho en OVZ"; the three OVZ-automation states are
  reserved and nothing assigns them). There is no separate "ambiguous explotación" state — that case is
  `PENDIENTE_REVISION` with `explotacion = null`.
- `MensajeCampo` deliberately does **not** extend `GestoriaScopedEntity` — at webhook-receipt time
  the Gestoría isn't known yet. `gestoria_id`/`contacto`/`tramite` are nullable and populated once
  the Contacto is resolved by phone.
- `UsuarioExplotacion` (in the `gestoria` package, mirroring how `ContactoExplotacion` lives in
  `contacto`) is an empty, unused join table for the future portfolio feature — no repository yet.
  (`ContactoExplotacion` stopped being unused in Prompt A1: it has `ContactoExplotacionRepository`
  and carries the Contacto's `rol`.)
- Security is stateless JWT (`jjwt`). `JwtService` puts `usuarioId`, `gestoriaId`, `email` in the
  token; `JwtAuthenticationFilter` reconstructs a `GaneraUserPrincipal` from it, resolvable in
  controllers via `@AuthenticationPrincipal GaneraUserPrincipal`. `SecurityConfig` has an explicit
  `authenticationEntryPoint` so a missing/invalid JWT on a protected endpoint returns `401`, not
  Spring Security's default `403`.
- Public paths: exactly `POST /webhooks/twilio/whatsapp`, `POST /webhooks/twilio/whatsapp/estado` and
  `POST /webhooks/stripe` (since the B2 status callback, no more `/webhooks/**` wildcard: anything else under
  `/webhooks/` without a JWT is `401`; `WebhooksPublicosEndToEndTest`), `/auth/login`, `/gestorias/registro` (public self-registration,
  no JWT and no shared secret; `404` while `ganera.registro.abierto` is `false`, the default), `/internal/**` (only at the Spring Security layer —
  `/internal/onboarding/gestoria` still gates itself on the `X-Internal-Secret` header inside
  `OnboardingController`), and `/error` (Prompt A1, decision 29: Spring's error dispatch runs
  without the JWT filter, so without this a malformed-body `400` turned into a `401` that logs the
  frontend out — see the "Prompt A1" bullet). Everything else requires a valid JWT.
- **`OnboardingController` (`POST /internal/onboarding/gestoria`) is a temporary bootstrap, not the
  final design.** Real customers were meant to sign up through `POST /gestorias/registro` (closed by
  default since C mínimo; sign-up moves to the landing in Prompt C); this endpoint
  stays only for pilot Gestorías (created directly in `ACTIVA`, no Stripe) and support/manual
  cases, hit by hand and guarded by a shared secret (`ONBOARDING_SECRET`,
  constant-time compare, fails closed if unset) instead of a JWT — at the time it's called, no admin
  Usuario exists yet to authenticate as. Replace it with a real admin panel (with its own access
  control) once one exists; don't build more features on top of the shared-secret pattern.
- **Lombok footgun:** nunca quites el `annotationProcessorPaths` explicito de Lombok en el `maven-compiler-plugin` de `backend/pom.xml` — con `--release 21` + descubrimiento implicito, `@Getter`/`@Setter`/`@Slf4j` fallan en silencio. Ver [footguns](docs/historia/footguns.md).
- **Twilio footgun:** construye `RequestValidator` por peticion, nunca en el constructor (un `TWILIO_AUTH_TOKEN` vacio impediria arrancar la app); misma cautela con cualquier integracion opcional. Ver [footguns](docs/historia/footguns.md).
- **commons-io footgun:** manten `commons-io` fijado a `2.16.1` como dependencia directa en `backend/pom.xml` (POI necesita 2.16+, Twilio arrastra 2.14); re-chequea en cualquier bump de Twilio/POI/commons-io. Ver [footguns](docs/historia/footguns.md).
- `StripeWebhookController` verifies the `Stripe-Signature` header (400 on an invalid signature)
  and dispatches every verified event to `StripeWebhookService.procesarEvento` — no other business
  logic lives in the controller. `StripeWebhookService` is package-private to `facturacion` and
  split per skill `test-sin-mocks-externos`: the state-machine decisions
  (`procesarCheckoutCompletado`, `procesarPagoExitoso`, `procesarPagoFallido`,
  `procesarSuscripcionActualizada`, and the static `calcularEstadoTrasActualizacion`) are plain
  methods testable without faking Stripe SDK deserialization; only the `manejarXxx` methods touch
  `Event`/`Session`/`Invoice`/`Subscription` directly, and those are validated only by the H2 smoke
  test, not a unit test. `StripeConfig` fixes the static `Stripe.apiKey` at startup and tolerates a
  blank `STRIPE_API_KEY` (same reasoning as the Twilio fix above — it only fails when a Stripe call
  is actually made).
- **`mvn` is not on the system PATH on the current dev machine.** Use `backend/mvnw` (the Maven
  Wrapper, added specifically so builds don't depend on ad hoc machine state) instead of a bare
  `mvn` — e.g. `cd backend && ./mvnw test`. The wrapper still needs a JDK on `JAVA_HOME`; the
  machine's global `JAVA_HOME` points at a JDK 17 install used by an unrelated project, and JDK 17
  cannot compile this project's `--release 21` target. Override `JAVA_HOME` **per command only**
  (never change the global env var): `JAVA_HOME="/path/to/a/jdk21+" ./mvnw test` — a newer JDK (e.g.
  23) compiles fine targeting an older `--release`. If `mvnw` itself is missing/broken, a cached
  Apache Maven distribution may already exist under `~/.m2/wrapper/dists/` from a prior wrapper run
  and can be invoked directly as a fallback.
- **Always validate with `./mvnw clean test`, never a bare `./mvnw test`.** VS Code's Java
  extension compiles classes into `backend/target/` with its own compiler (ECJ), and Maven reuses
  them: in Prompt A1 (Task 7a) a suite that "ran" 387 tests with failures was actually code that
  did not compile at all with `javac` (a clean build failed at `testCompile`), and a non-clean run
  later picked up ECJ's "Unresolved compilation problem" stubs. `clean` is the only trustworthy
  signal.
- **`TestRestTemplate`/`RestTemplate` with `String.class` gets XML**: Twilio's SDK brings `jackson-dataformat-xml`, so
  the default converters send `Accept: application/xml` first and the server answers XML. Send `Accept:
  application/json` explicitly in the `HttpEntity` headers (`RestTemplateBuilder.defaultHeader` doesn't work: the
  `Accept` callback overrides it). The frontend (axios) already asks for JSON.

## Architecture notes (frontend)

Vite + React 19 + TypeScript + Tailwind v4 (`@tailwindcss/vite` plugin, no `tailwind.config.js`) +
shadcn/ui, actually the **"base-nova"** style variant (`@base-ui/react` primitives, not Radix,
despite this file's earlier prose — `components.json` and every installed component in
`src/components/ui/` confirm base-ui; component *props* generally match the usual shadcn/Radix
shape — `value`/`onValueChange` on `Select`, `open`/`onOpenChange` on `Dialog` — but check the
actual `.d.ts` before assuming a prop exists, e.g. `Select.Value`'s label is **not** automatic: it
needs a `children` render-function (`{(value) => label}`), unlike Radix). Path alias `@/*` →
`src/*`. Structure is by feature (`features/auth`, `features/tramites`, `features/ganaderos`,
`features/explotaciones`, `features/facturacion`) plus `shared/` (`api`, `auth`, `layout`,
`brand` — `LogoGanera`, the CSS-mask brand mark — and `ui` — `CLASE_ENLACE`, the one text-link
recipe). Visual rules live in `DESIGN.md`; design work goes through the Impeccable skill only.
**Brand (2026-10-05):** the Ganera landing's red/grey palette and **Archivo** (self-hosted with
`@fontsource-variable/archivo`, never a Google/CDN request; Geist is gone). Tokens in `index.css`:
`--primary` = action red 600, `--primary-hover`/`--enlace`/`--ring` = red 700, `--marca` = Rojo Ganera
(the mark only, `text-marca`), `--destructive`/`--danger-foreground` = red 800. Rule: **solid red acts,
dark tinted red warns**; focus is a solid `ring-ring` (never `/50`), the primary hover is
`bg-primary-hover` (never `/80`), links use `CLASE_ENLACE` (`text-enlace`), except link columns inside
tables, which use `CLASE_ENLACE_TABLA` (Tinta at rest, red 700 on hover/focus), every destructive error
`Alert` starts with a `CircleAlertIcon`. Weights: 800 headlines, 600 interface (`font-semibold`), 500
only for data emphasis in tables. Radii unchanged, pending a decision with Antonio's partner. The
CSS-free logo variant is `public/ganera-logo-rojo.svg`.
Prompt A2 (plan `docs/superpowers/plans/2026-09-28-promptA2-frontend-revision.md`) is the
reference for every frontend decision below.

- **Session in `sessionStorage` (Prompt A2, decision 1):** only the token, under `ganera.token`. It
  survives a reload and is gone when the tab closes; it does **not** protect against XSS (neither
  would `localStorage` — the robust option, an httpOnly cookie, needs backend changes).
  `shared/api/authSession.ts` keeps the token in a module variable (mirrored to `sessionStorage`,
  every access in `try`) so `shared/api/httpClient.ts` — outside any React tree — can read it.
  On boot with a stored token, `AuthContext` is `comprobando` (routes show a loading state, never
  the login) and calls `GET /auth/me`: `200` restores the user; `401` clears the token and sends
  to `/login` with "Tu sesión ha caducado…"; a network error/5xx keeps the token and offers
  "Reintentar" (a network blip never logs out). `shared/auth/RequireAuth.tsx` is a pathless layout
  route wrapping the authenticated part of `router.tsx`.
- **One HTTP error model (decision 2):** `httpClient`'s response interceptor turns every failure
  into an `ErrorApi { tipo, status?, motivo? }` (`shared/api/errores.ts`), with `tipo` ∈
  `no-autorizado` (401: logout + notice, **except on `/auth/login`**, whose 401 is just the
  login's uniform error), `prohibido`, `validacion`, `no-encontrado`, `conflicto`, `servidor`,
  `red`, `desconocido`. `motivo` comes from `{motivo}` (every backend error body uses it now) or, as
  a defence nobody uses today, from a plain-text body. `mensajeDeError(error, contexto)` gives the text to
  show (the backend `motivo` verbatim when there is one). **No screen uses `axios.isAxiosError`
  directly**, and no load is silent: every list/detail has loading, error-with-"Reintentar" and
  empty states.
- **Explotación search in the review dialog (frontend task before the pilot, D3a–D3f):**
  `CampoExplotacion` + `features/tramites/useBuscarExplotaciones.ts` query `GET /explotaciones?q=`
  only while the popup is open, 300 ms after the last keystroke, aborting the previous request and
  ignoring stale responses (key = opening|attempt|q); first page of 20, "Hay N coincidencias;
  escribe para acotar." from `totalElements`; if the input text is the chosen explotación's label it
  counts as an empty query (`consultaDeBusqueda`); a `400` (over 100 chars) shows the `motivo`;
  errors show "Reintentar" inside the popup. The old complete list (`todasLasExplotaciones.ts`,
  `useTodasLasExplotaciones.ts`) and the client-side filter were **deleted**. The regression this
  caused (no accent-insensitive or word-by-word search) was closed in the backend on 2026-10-04 (see
  "Explotación search without accents" under Technical decisions); a retouched label of a long
  explotación name can now exceed 8 words and get that `400` `motivo`.
- **`AppLayout.tsx`** has the navigation Trámites/Ganaderos/Explotaciones (one
  `nav aria-label="Principal"`; below `md` two rows with the links in a full-bleed strip that
  scrolls inside itself, the active link brought into view by setting the strip's `scrollLeft` —
  `shared/layout/tiraNavegacion.ts`, plus a `ResizeObserver`, and the same for a link that receives
  keyboard focus — never `scrollIntoView`; from `md` one
  row with the email truncated; see `DESIGN.md` → Navigation) and fetches
  subscription status once (`features/facturacion/useSuscripcionEstado.ts`, passed to the banner by
  props; the `<Outlet context>`/`AppLayoutContext` that let `FacturacionPage` reuse it was removed
  with that page on 2026-10-04 — the `features/facturacion` folder keeps its name). A catch-all
  `*` route inside the authenticated layout redirects any unknown path (including the old
  `/facturacion`) to `/tramites`. `SuscripcionBanner.tsx` shows a persistent warning, titled "No
  puedes aprobar trámites ahora mismo", for `TRIAL_EXPIRADO_SIN_PAGO`/`SUSPENDIDA`/no-`Suscripcion`-at-all
  (all three block approving) and a milder "Aviso de pago" for `IMPAGO_GRACIA`; every description ends
  asking to contact Ganera, with **no button, link or `mailto:`** (only the load-error state keeps
  "Reintentar") — it never
  hides the rest of the app (Explotaciones/Trámites stay browsable), only `POST /tramites/{id}/aprobar`
  itself is blocked (`403` from `puedeAprobarTramites`), matching the backend's existing
  no-approvals vs. full-access semantics from Prompt 2 (editing, rejecting and importing stay
  allowed) — don't make the banner block navigation, that would be a stricter gate than what the
  backend actually enforces.
- **Trámites queue (`TramitesPage`)** opens filtered by "Pendiente de revisión" ("Todos" stays in
  the filter). Each row's first cell is a real button ("Revisar trámite #N"); the Explotación
  column shows `explotacionCodigoRega` from the `GET /tramites` row itself (name in `title` +
  sr-only; "Sin asignar" without one; never raw ids; no request to `/explotaciones`); the
  crotales column shows two plus a "+N más" disclosure. Labels and badge variants for estado, tipo,
  crotal resolution (`presentacionCrotal`: `NO_ENCONTRADO` + `completo: false` → amber "No está en
  el inventario · incompleto", read from the backend, never computed) and contact role come from
  **one source**, `features/tramites/etiquetas.ts`
  (`TIPOS_TRAMITE`, the six OVZ types since the ficha OVZ, is the single constant with the types). Since Prompt B1 the first cell
  adds a WhatsApp marker (outside the button, announced once), and while the trámite is pending a
  12px line under the state shows the extraction (`avisosExtraccion()` in `etiquetas.ts`:
  "Extracción en curso" neutral, "No se ha podido extraer" / "Mensaje sin texto" / "N descartados"
  amber, never red); the review dialog shows the same facts as fixed notices with no `role` (see
  `DESIGN.md`). No automatic refresh.
- **Review dialog (`TramiteReviewDialog` + `useRevisionTramite`):** logic lives in the hook
  (`revisionTramite.ts` has the pure diff/state rules), layout in the dialog. Editable **only** in
  `PENDIENTE_REVISION`; any other estado is read-only with no action buttons. "Guardar" sends
  `PATCH` with the `version` shown plus only the changed fields (`crotales` as the full list if it
  changed); Aprobar sends `{version}`; Rechazar sends `{version}` too (required since the backend
  mini-prompt after A2) after an
  inline confirmation. With unsaved changes Aprobar/Rechazar are disabled ("Guarda antes de
  aprobar") — the backend approves what is saved, not what is on screen. **Any `409`** (PATCH,
  aprobar, rechazar) reloads the detail, discards the form and shows the backend's `motivo`
  verbatim, never retrying by itself; a `400` keeps the edits and shows the `motivo`; a `404`
  says the trámite no longer exists or isn't yours; a `403` on aprobar shows the backend's `motivo` (the fixed subscription text is only the fallback
  when no body arrives), with no link (the Facturación page no longer exists).
  Badges show **what is saved**: an
  edited field/row says "Sin guardar" until the backend answers. One request at a time.
- **Ganaderos (`features/ganaderos`):** `/ganaderos` (paginated, sortable by nombre and NIF only)
  and `/ganaderos/:id` (stacked sections per explotación with contactos — `tel:` links, role
  badge — and a collapsed "Ver animales"); a `404` (another Gestoría's or nonexistent) shows
  "Ganadero no encontrado", never a broken page. `AnimalesDeExplotacion` (paginated
  `GET /explotaciones/{id}/animales`) is the same disclosure panel in Explotaciones and in the
  Ganadero detail — there is no `/explotaciones/:id` route (the backend has had
  `GET /explotaciones/{id}` since the mini-prompt after A2; no screen uses it yet).
- **Frontend tests (Prompt A2):** Vitest + jsdom + Testing Library (`react`, `user-event`,
  `jest-dom`) + **MSW**, which mocks the API at the HTTP level so the real axios client and its
  interceptors are exercised. Config in `vitest.config.ts` (merges `vite.config.ts`, so `@/`
  works); setup in `src/test/` (`setup.ts`, `server.ts`, `handlers.ts`) — any request without a
  handler fails the test (`onUnhandledRequest: "error"`), and session state is cleared after each
  test. `npm test` ran **475 tests in 36 files** at the end of A2 (476 after the mini-prompt's
  Rechazar change; **506 in 36 files** after the frontend task before the pilot; **508 in 36** after billing left the app;
  **513 in 37** after the brand colours and typography; **558 in 37** after Prompt B1; **557 in 36** after C mínimo removed the registration screen; **720 in 40** after the ficha OVZ; **811 in 41** after "Hecho en OVZ"; **912 in 43** after B2 reducido; **976 in 45** after SIN_TRAMITE; **1084 in 50** after the Clever deployment tasks; **1144 in 59** after Álvaro's
mock-v2 merge (PRs #13–#29) + the ganadero-telefono task; **1172 in 61** after merging PR #32 (gestionar personas titular/empleado desde las fichas) and the crotal-madre task). Layout can't be checked in
  jsdom: for visual changes, also drive the real app in a browser (Playwright from npm installed
  **outside the repo**, as in Prompts 4 and A2).

## Working rules for subagents (implementers and reviewers)

- **Subagents never run commands that touch the git index or restore files:** no `git add`,
  `git rm`, `git checkout`, `git restore`, `git reset`, `git stash` (nor `git commit`/`git push`).
  If they need one — to undo a change, recover a file, or stage something — they **stop and tell
  the main session**, which decides. Temporary changes to prove a test catches a regression are
  undone by copying the file back from a backup made beforehand (`cp`, then `cmp`), never through
  git. Antonio set this on 2026-10-04, after an implementer ran `git rm --cached` by mistake and a
  reviewer wiped a task's changes in a file with `git checkout` (both recovered). Staging and
  commits stay with the main session, path by path, only with Antonio's approval.

## Commands

Backend (from `backend/`; use `./mvnw` not a bare `mvn` — see the PATH/JAVA_HOME footgun above):
- `./mvnw compile` — compile
- `./mvnw spring-boot:run` — run locally (Spring Boot does **not** auto-load `.env` — export the
  variables from `.env.example` into the shell/IDE run config yourself, or run via
  `docker-compose` where Postgres is provided but the app itself still needs its own env vars set)
- `./mvnw clean test` — runs the test suite (1001 tests after the Clever deployment tasks — 1000 run plus the skipped Haiku evaluation —; **1075** after the Supabase migration — 1074 run plus the skipped evaluation —, including the embedded-PostgreSQL ones; **1338** (1337 run plus the skipped evaluation) after M2, the on-demand OVZ decryption; **1347** (1346 run plus the skipped evaluation) after the #23–#28 merge (manual trámite, enriched explotación fichas, OVZ preferences, alerts, demo seeder) and the backend stabilization that fixed the test suite; **1366** (1365 run plus the skipped evaluation) after the ganadero-telefono task (`PUT /ganaderos/{id}/telefono`, migration V29); **1370** (1369 run plus the skipped evaluation) after closing the contacto/ganadero read-audit gap (`GET /explotaciones/{id}` → `LECTURA_EXPLOTACION`, guard `InventarioLecturasTelefonoTest`; no migration); **1388** (1387 run plus the skipped evaluation) after the crotal-madre task (the trámite's mother crotal resolved against the explotación inventory via the shared `resolver`, migration V30; #32 added no backend tests; includes the E2E `laMadreNoSeResuelveContraOtraExplotacionDeLaMismaGestoria` that goes red if the `explotacionId` filter leaks); **1408** (1407 run plus the skipped evaluation) after the WhatsApp-por-gestoría fase 1 task (migration V31; resolución por número de DESTINO, tabla `numero_whatsapp`, `POST /internal/whatsapp/numeros`, guardia `ConsultasSinTenantTest`); also green with JDK 21 — the real `JAVA_HOME` is `~/.jdks/temurin-21/jdk-21.0.12.1+1` (the JDK sits one level below `temurin-21`; in Git Bash pass it Windows-style, `JAVA_HOME='C:\Users\Antonio\.jdks\temurin-21\jdk-21.0.12.1+1' ./mvnw clean test`, a `/c/...` path makes the wrapper fail with "JAVA_HOME is not defined correctly")); **always with `clean`**
  (see the VS Code/ECJ note under Architecture notes). A single test:
  `./mvnw clean test -Dtest=AuthServiceTest`
  **Flakiness del PostgreSQL embebido:** tras muchas ejecuciones seguidas en la misma máquina/sesión, el
  PostgreSQL embebido compartido puede dar **errores en bloque** (cargas de contexto que fallan en cascada,
  «Script Vxx failed», 500 espurios) que **cambian de orden entre ejecuciones** — no son regresiones; la
  referencia válida es una **ejecución limpia** (`./mvnw clean test`) con los procesos previos ya terminados.
- Playwright browsers are already installed locally. If they need reinstalling elsewhere (no
  `exec-maven-plugin` is configured in `pom.xml`, so `mvn exec:java` won't work out of the box):
  `mvn dependency:build-classpath -Dmdep.outputFile=cp.txt` then
  `java -cp "target/classes;$(cat cp.txt)" com.microsoft.playwright.CLI install`.
- **No Docker/Postgres in this sandbox** — to actually boot the app for a manual smoke test without
  a real Postgres, run against an in-memory H2 (PostgreSQL compatibility mode), skip Flyway, and let
  Hibernate create the schema:
  `./mvnw spring-boot:run -Dspring-boot.run.useTestClasspath=true -Dspring-boot.run.arguments='--spring.datasource.url=jdbc:h2:mem:ganera;MODE=PostgreSQL;DB_CLOSE_DELAY=-1 --spring.datasource.driver-class-name=org.h2.Driver --spring.datasource.username=sa --spring.datasource.password= --spring.flyway.enabled=true --spring.jpa.hibernate.ddl-auto=none'`
  (`useTestClasspath=true` is required — `h2` is a test-scope dependency, not on the runtime
  classpath otherwise; `flyway.enabled=true` + `ddl-auto=none` runs the real migrations against H2
  instead of letting Hibernate generate the schema, which is what every Prompt-closure smoke test
  since Paso 1 actually does). This is how the Twilio footgun above was actually caught — `mvn
  compile` alone never would have. Still export `JWT_SECRET`/`ENCRYPTION_KEY`/`ONBOARDING_SECRET`
  first (and `STRIPE_API_KEY`/`STRIPE_WEBHOOK_SECRET`/`STRIPE_PRICE_ID_EXPLOTACION` can stay blank).
  **On Windows, killing this process needs `taskkill`/`Stop-Process` on the child `java.exe` PID** —
  stopping the wrapping shell/task does not kill the spawned JVM.

Frontend (from `frontend/`):
- `npm install`
- `npm run dev` — dev server on `:5173`. **Needs the backend running on `:8080`** (see above) —
  `shared/api/httpClient.ts` defaults `VITE_API_BASE_URL` to `http://localhost:8080`, and the
  backend's `SecurityConfig` CORS bean must allow `:5173` (`ganera.frontend.origen`/`FRONTEND_ORIGEN`
  env var, defaults to `http://localhost:5173` already) or every request 500s at the browser's
  preflight before reaching any controller.
- `npm test` — Vitest, single run (`npm run test:watch` for watch mode)
- `npm run build` — `tsc -b && vite build` (it warns that the main chunk is over 500 kB — 622 kB
  after A2; code-splitting is noted for later, not a failure)
- `npm run lint` — oxlint (3 known `only-export-components` warnings: `button.tsx`, `badge.tsx`,
  `AuthContext.tsx`)
- `npm run preview`

Root:
- `docker-compose up -d` — Postgres only (the Spring Boot app runs outside Docker for now). `postgres:17-alpine` with
  `backend/supabase/01-…sql` in `docker-entrypoint-initdb.d` (runs only on a new volume: remove an old one with
  `docker-compose down -v`); see the "Supabase" bullet.
- **After cloning, recreate the `impeccable` skill junction.** The design skill is committed under
  `.agents/skills/impeccable/` (pinned in `skills-lock.json`), but Claude Code loads it from
  `.claude/skills/impeccable`. That path is a Windows directory junction, and it's in `.gitignore`:
  git for Windows traverses junctions as plain directories (`core.symlinks=false`), so committing
  it would store a second, drifting copy of the whole skill. Recreate it from the repo root in
  `cmd`: `mklink /J .claude\skills\impeccable .agents\skills\impeccable`, or reinstall the skill
  from `skills-lock.json`.

La historia prompt-a-prompt (pasos 1–2.7, 3d, 4, A1, A2, mini-prompts, busqueda, marca, B1, C minimo, ficha OVZ, Hecho en OVZ, B2, SIN_TRAMITE, Supabase, M2, telefono, crotal madre) esta en [docs/historia/historial-por-prompt.md](docs/historia/historial-por-prompt.md). Estado actual, abajo.

**Still blocked: Prompt 3a and escalón 2 (modo asistido)** — the OVZ.net read sync, and the mode where
Ganera fills in the OVZ form for the gestor to review — one trámite at a time, after the gestor’s
“Enviar”; Ganera downloads the justificante (never without that click, never autonomous). They need real
OVZ.net credentials so the actual site structure and trámite catalog can be explored live (the
Playwright MCP is set up for exactly this — driving the real site with Antonio steering, not
guessing at its structure from assumptions). Until then `OvzAutomationService` stays an
unimplemented skeleton — don't write scraping logic against assumptions about OVZ.net's structure.
The screen to store OVZ credentials (encrypted), deferred to escalón 2 — whether they belong to the
gestoría or to each ganadero is decided after the OVZ mapping — and the rest of Prompt 4's
originally-scoped items are deferred alongside it.
(3b's webhook + AI part was done without OVZ.net as Prompt B1.)

`ganera-prompts.md` at the repo root tracks the full sequence of prompts used to
build this out, in order — check it for the detailed history/rationale behind any given step.
