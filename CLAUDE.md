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
  (`POST /gestorias/registro`, real customers, goes straight to Stripe Checkout) or by hand through
  the internal onboarding endpoint (`POST /internal/onboarding/gestoria`, pilots/support) — see
  Technical decisions and Architecture notes.
- **Ganadero** — a Gestoría's client. **Never logs in.** Holds the OVZ.net credentials
  (`ovzUsuario` / `ovzPasswordCifrada`) — one login per Ganadero, shared across all their
  Explotaciones (not per-Explotación). `nif` (added in `V14`, globally unique like `codigoRega`
  and `crotal`) is the real-world business key used by the Excel importer (Prompt 3d) to upsert a
  Ganadero without duplicating on name variations.
- **Explotación** — a farm belonging to a Ganadero, identified by `codigoRega`.
- **Animal** — identified by a full crotal (`ES123456789012`); `crotalUltimosDigitos` is indexed
  separately because that's what Contactos actually type in WhatsApp.
- **Contacto** — whoever writes on WhatsApp (titular or trabajador of a Ganadero), identified by
  phone number. A trabajador is linked to exactly one Explotación; a titular can be linked to
  several (`ContactoExplotacion`).

## Operational flow

1. A Contacto sends a WhatsApp message via Twilio describing a trámite, mentioning animals by the
   last digits of their crotal.
2. **Twilio uses a single WhatsApp number shared by every Gestoría** — so `Contacto.telefono` is
   globally unique (not scoped per-gestoría), and `ContactoRepository.findByTelefono` is a
   deliberately tenant-unscoped lookup: it's the only way to resolve which Gestoría a message
   belongs to when it arrives. Don't "fix" this into a per-tenant lookup.
3. If the Contacto has more than one Explotación and the message doesn't disambiguate, the Tramite
   is created with `explotacion = null` and `estado = PENDIENTE_REVISION` — an employee assigns the
   Explotación by hand in the UI. There is no automatic WhatsApp round-trip asking the Ganadero to
   clarify (that's a future phase).
4. Claude Haiku 4.5 (via Spring AI, `TramiteExtractionService`) extracts `{tipoTramite,
   últimosDigitosCrotales}` as Structured Output from the raw message text.
5. Crotales are resolved by matching last digits within the resolved Explotación. No match, or more
   than one match, → `PENDIENTE_REVISION`. It is never auto-approved.
6. An employee reviews and clicks "Aprobar" → `APROBADO`.
7. Playwright executes the trámite against OVZ.net using the **Ganadero's** encrypted credentials.
   One immediate automatic retry on failure; if it fails again, `ERROR_OVZ` with the reason
   recorded — never silent. The same Playwright service is also used read-only to sync a newly
   onboarded Ganadero's initial inventory of explotaciones/animales.

## Non-negotiable rules

- **No trámite is ever executed against OVZ.net without an employee explicitly clicking
  "Aprobar."** No AI confidence threshold ever skips this step.
- OVZ.net credentials are encrypted at rest (AES-256-GCM, `EncryptedStringConverter`), key from
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
  finding, not a minor note.
- Ganaderos and Contactos never authenticate. Only Usuarios have logins.
- A Gestoría in `TRIAL_EXPIRADO_SIN_PAGO` or `SUSPENDIDA` can never approve trámites (read-only) —
  gated by `SuscripcionService.puedeAprobarTramites(gestoriaId)`. Fail-closed: no `Suscripcion` row
  for a Gestoría also means no approval, not a free pass.
- `POST /auth/login` never reveals *why* it rejected a login (unknown email, wrong password, or
  inactive Usuario all return a plain 401) — don't add a more specific error message.
- **The frontend never implies a trámite is executed against OVZ.net when "Aprobar" is clicked** —
  `OvzAutomationService.ejecutarTramite()` is unimplemented (Prompt 3c), so the button only changes
  `EstadoTramite` in DB, matching the backend exactly. Don't add wording, spinners, or toasts that
  suggest anything happens in OVZ.net until 3c is real.
- **No manual "alta de Ganadero" form in the frontend, no OVZ-credentials-onboarding screen.** The
  only way to create Ganaderos/Explotaciones/Animales right now is the Excel importer
  (Prompt 3d) — don't add a second, divergent creation path without an explicit decision to do so.
  The OVZ-credentials screen is deliberately deferred until a real Ganadero is ready to hand over
  credentials for that explicit purpose (see Prompt 3a/3b/3c blocker).

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
  and `POST /facturacion/checkout` were confirmed exempt by design (both resolve the Gestoría via
  `SuscripcionRepository.findByGestoriaId(gestoriaId)`/`ExplotacionRepository.countByGestoriaId(gestoriaId)`
  — real query parameters, immune to both bug classes above regardless of filter/interceptor
  state).
- **AI provider**: Claude Haiku 4.5 via Spring AI, pinned to the dated snapshot
  `claude-haiku-4-5-20251001` (not the floating alias) so it can't change under us silently.
  Abstracted behind `TramiteExtractionService` so the provider is swappable. Structured Outputs
  (`.entity(...)`) enforce the JSON shape — never relying on prompt instructions alone.
- **WhatsApp**: Twilio, one shared number across all Gestorías (see Operational flow above).
- **Automation**: Playwright (Java) for OVZ.net, both read-only sync and write-mode execution.
- **Billing**: Stripe, priced by number of contracted active Explotaciones, 15-day trial.
  `EstadoSuscripcion` has exactly 6 values: `TRIAL` (full access), `TRIAL_EXPIRADO_SIN_PAGO`
  (read-only, no approvals), `ACTIVA` (full access), `IMPAGO_GRACIA` (full access, but the
  frontend must surface a warning), `SUSPENDIDA` (read-only, no approvals, grace period over),
  `CANCELADA`. The read-only/no-approval gate is `SuscripcionService.puedeAprobarTramites` —
  not wired to any endpoint yet, that comes with the trámite-approval business logic.
- **Stripe integration** (`facturacion` package): a single `Price` (`STRIPE_PRICE_ID_EXPLOTACION`,
  placeholder until one is created in the Stripe dashboard) with quantity = active Explotaciones
  count, 15-day trial baked into the Checkout Session. `POST /facturacion/checkout`
  (`StripeCheckoutService`) sets `client_reference_id` = `gestoriaId` on the Checkout Session — this
  is how the `checkout.session.completed` webhook maps back to a Gestoría, not the Stripe customer
  ID. Pilot Gestorías (created via `/internal/onboarding/gestoria`) never touch this flow; a real
  paying Gestoría's Usuario hits checkout, and if no `Suscripcion` row exists yet for their Gestoría,
  `SuscripcionService.obtenerOCrearSuscripcion` creates one in `TRIAL` on the spot (idempotent via
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
  comparison) — there is no dedupe by `event.id`. Two distinct events landing in the same
  epoch-second can be applied in either order; revisit (e.g. an `event.id` seen-table) before
  scaling past pilot volume. A daily `@Scheduled` job (`SuscripcionSyncScheduler`, cron
  `0 0 3 * * *` `Europe/Madrid`) reconciles `explotacionesContratadas` against the live Explotación
  count for every `ACTIVA`/`IMPAGO_GRACIA` subscription and pushes quantity changes to Stripe
  (default proration) — deliberately nightly rather than on every Explotación create/delete, to
  avoid coupling the Explotación CRUD to Stripe calls. It runs outside any HTTP request, so the
  `gestoriaFilter` Hibernate filter is never active for its queries — that's an intentional
  cross-tenant job, not a multi-tenancy leak. A failed push for one subscription is caught and
  logged per-iteration so it never aborts the rest of the nightly batch.
  **Known limitation:** `SuscripcionSyncScheduler` only reconciles `ACTIVA`/`IMPAGO_GRACIA`
  subscriptions — a Gestoría still in `TRIAL` never gets its `explotacionesContratadas`
  auto-corrected against its real Explotación count during the trial window. This matters for the
  public self-registration flow (`POST /gestorias/registro`, see below): the quantity it sends to
  Stripe is an *estimate* derived from a client-count range, not a real Explotación count (there
  are no Explotaciones yet at registration time), and that estimate will not self-correct until
  the subscription actually reaches `ACTIVA`/`IMPAGO_GRACIA` — or until a future manual adjustment
  screen exists (deliberately out of scope for now). Don't assume `explotacionesContratadas` is
  accurate for a `TRIAL` subscription.
  **Known bugs, detected 2026-09-25 in a full-project review, PENDING (not fixed yet):**
  1. *`invoice.payment_failed` on an already-`TRIAL_EXPIRADO_SIN_PAGO` subscription.*
     `StripeWebhookService.procesarPagoFallido` only maps a stored `TRIAL` to
     `TRIAL_EXPIRADO_SIN_PAGO`; any other stored state goes to `IMPAGO_GRACIA`. The Prompt 2.7 plan
     (Decision 4) says `TRIAL` **or** `TRIAL_EXPIRADO_SIN_PAGO` → `TRIAL_EXPIRADO_SIN_PAGO`. Stripe
     retries failed charges, so the second failed retry after an unpaid trial flips the
     subscription to `IMPAGO_GRACIA` — which **re-enables** `puedeAprobarTramites`. No test covers
     that transition today. Fix test-first.
  2. *Abandoned trial never expires.* `SuscripcionService.obtenerOCrearSuscripcion` creates the
     `Suscripcion` row in `TRIAL` **before** the Checkout Session is completed (both from
     `POST /facturacion/checkout` and from `POST /gestorias/registro`). If the Gestoría abandons
     Stripe Checkout, no webhook ever arrives and nothing local expires the row, so it stays in
     `TRIAL` — with full approval rights — indefinitely. Needs a design decision (e.g. don't grant
     `TRIAL` until `checkout.session.completed`, or a local trial-expiry check) before fixing.
- **Public self-registration** (`registro` package): `POST /gestorias/registro`, public (no JWT,
  no shared secret), in parallel with `/internal/onboarding/gestoria` — the internal endpoint is
  unchanged and stays for support/manual cases; this is an additional entry point for real
  customers, not a replacement. Reuses the exact Gestoria→Usuario creation shape already proven by
  `OnboardingController` (`RegistroGestoriaService.crearGestoriaYUsuario`, `@Transactional`), but
  does not set a `Suscripcion` itself — it lets the immediate `StripeCheckoutService` call create it
  in `TRIAL` via the already-existing `obtenerOCrearSuscripcion`, exactly like the authenticated
  checkout flow already does. A Gestoría knows its number of clients (Ganaderos), not how many
  Explotaciones they add up to, so the form asks for a **client-count range**
  (`RangoClientes`: `UNO_A_DIEZ`, `ONCE_A_TREINTA`, `TREINTA_UNO_A_SETENTA_Y_CINCO`,
  `SETENTA_Y_SEIS_O_MAS`) instead of an Explotación count. Stripe still bills by Explotaciones
  (unchanged) — each range's **minimum** client count is multiplied by a starting ratio of 1.3
  Explotaciones/client (a normal Ganadero has 1 Explotación, some have more — an explicit
  **estimate**, not a real count) and rounded up (`RangoClientes.quantityExplotacionesEstimada()`),
  giving quantities 2 / 15 / 41 / 99. Using the range's minimum (not its midpoint) is deliberate: it
  never over-charges before the real inventory is known — see the `SuscripcionSyncScheduler`/`TRIAL`
  limitation just above, which is exactly why this estimate won't self-correct until the
  subscription leaves `TRIAL`. `StripeCheckoutService` gained
  `crearSesionCheckoutConCantidadEstimada(gestoriaId, cantidadEstimada)`, a sibling of the existing
  `crearSesionCheckout(gestoriaId)` that skips the real Explotación count and shares the same
  private helper and `configuracionCompleta()` guard. Validation
  (`RegistroGestoriaValidacion`, package-private, pure static methods — deliberately not
  `spring-boot-starter-validation`'s `@Valid`/`@Email`, which is on the classpath but unused
  anywhere in this codebase, to avoid introducing a first `@ControllerAdvice` just for this one
  endpoint) and the duplicate-email case (a real `UNIQUE(email)` violation, not a pre-check
  `findByEmail`) all collapse into **one identical `400` + generic message** — same principle as
  `AuthService.autenticar`'s uniform `401`, so a duplicate email can't be distinguished from a weak
  password or a malformed one (no enumeration oracle for which Gestorías are already customers).
  `RegistroGestoriaService.crearGestoriaYUsuario` deliberately does **not** catch
  `DataIntegrityViolationException` itself — it must propagate out of the `@Transactional` method
  so Spring rolls back the `Gestoria` insert together with the failed `Usuario` insert; catching it
  inside would leave an orphan `Gestoria` row. `RegistroGestoriaController`, outside that
  transaction, is the one that catches it to shape the `400` response — same pattern as
  `FacturacionController` catching `StripeException` one level above where it's thrown.
- **Excel inventory importer** (`explotacion` package, Prompt 3d): manual fallback for loading
  Explotaciones/Animales when the OVZ.net read sync (Prompt 3a, still blocked) isn't available.
  `POST /explotaciones/importar` (multipart `.xlsx`, Apache POI) parses two fixed-position sheets —
  "Explotaciones" (`codigo_rega, nombre, nif_ganadero, nombre_ganadero`) and "Animales"
  (`crotal, especie, codigo_rega_explotacion`) — reading by column *index*, not header name. A
  missing required sheet throws `IllegalArgumentException`, caught by `ExplotacionImportController`
  and returned as `400` with the message (not a generic `500`). `especie` is validated (must be
  blank or a bovine label — Ganera only handles cattle for now) but **never persisted**: `Animal`
  has no `especie` column, an unsupported value is just a row error. `crotalUltimosDigitos` is
  derived as the last 6 characters of the full crotal — an assumption, since no real WhatsApp-
  matching logic (Prompt 3b) exists yet to confirm the real digit count; revisit if 3b needs a
  different length.
  **Per-row transaction isolation (`ExplotacionImportFilaService`):** `ExplotacionImportService`
  upserts by `codigoRega`/`crotal`/`nif` (all real-world unique keys, see Domain model) and must
  never abort the whole file on one bad row — but a code review caught that a *real* unique-
  constraint violation on `saveAndFlush` (e.g. a `codigoRega` already owned by another Gestoría,
  invisible to the current tenant filter) left Hibernate's persistence context unusable for the
  rest of the sheet (`AssertionFailure: don't flush the Session after an exception occurs`) — rows
  *after* the failing one broke too, with a generic error unrelated to their own data, not just the
  bad row itself. Fixed by extracting all per-row DB work into `ExplotacionImportFilaService`, a
  **separate** `@Service` bean (self-invocation within the same class doesn't go through Spring's
  `@Transactional` proxy, so REQUIRES_NEW must live on another bean) whose
  `procesarExplotacion`/`procesarAnimal` each run in `@Transactional(propagation = REQUIRES_NEW)` —
  their own transaction, their own connection, so one row's constraint violation rolls back only
  that row and never touches the session used for the rest of the file.
  **Non-obvious side effect of REQUIRES_NEW:** it suspends the request's `open-in-view` entity
  manager — the one `TenantFilterActivationInterceptor` enabled `gestoriaFilter` on — and binds a
  brand-new one with **no filter active**, which would silently break multi-tenancy isolation for
  every row. `ExplotacionImportFilaService` re-enables `gestoriaFilter` itself at the top of each
  REQUIRES_NEW method using the `gestoriaId` passed in, precisely to close that gap. Any other code
  that reaches for `REQUIRES_NEW` on a bean touching `GestoriaScopedEntity` data must do the same or
  it will silently query across tenants.
  `GET /explotaciones` and `GET /tramites` (filterable by `estado`) are the first paginated
  endpoints in the codebase (`Page`/`Pageable`, Spring Data's default web support — no custom
  config needed); both rely solely on the already-active `gestoriaFilter` for tenant scoping, no
  manual `gestoria_id` checks. `POST /tramites/{id}/aprobar` gates on
  `SuscripcionService.puedeAprobarTramites` (`403` if false) — the first real caller of that method
  — and, together with `/rechazar`, just flips `EstadoTramite`; neither calls `OvzAutomationService`
  yet (that's Prompt 3c). A tramite id from another Gestoría or that doesn't exist returns `404`
  (`Optional`-based, not `.orElseThrow()` — this is an expected, common case, not a "should never
  happen" one).
- **Frontend, Prompt 4 (partial — everything not blocked by OVZ.net):** login, Explotaciones
  dashboard + Excel import, Trámites queue + review modal, Facturación status page. Two backend
  additions this required, both driven directly by the frontend's stated needs, not speculative:
  `GET /facturacion/suscripcion` (`FacturacionController`, `404` if the Gestoría never had a
  `Suscripcion` — fail-closed, a `GET` must never create one as a side effect, that's what
  `POST /facturacion/checkout` → `obtenerOCrearSuscripcion` is for) and `GET /tramites/{id}`
  (`TramiteDetalleResponse`, adds the raw WhatsApp message body via
  `MensajeCampoRepository.findFirstByTramiteIdOrderByCreatedAtDesc` plus the resolved Explotación's
  `codigoRega`/`nombre` — kept out of the paginated `GET /tramites` list to avoid N+1 there).
  **CORS** (`SecurityConfig.corsConfigurationSource`, origin from `ganera.frontend.origen` /
  `FRONTEND_ORIGEN` env var, default `http://localhost:5173`): found by *actually driving the app in
  a real browser* — every backend test passed and `curl` worked fine, but the browser blocks the
  request at preflight before it ever reaches a controller, JWT valid or not. `curl`/Postman never
  send an `Origin` header the way a browser does, so this class of bug is invisible to any
  request-level test; only a real browser catches it.
- **Backend build tool**: Maven (not Gradle).

## Architecture notes (backend)

Package layout under `backend/src/main/java/com/ganera/core/`: `gestoria`, `ganadero`,
`explotacion`, `contacto`, `tramite`, `facturacion`, `whatsapp`, `ovz`, `auth`, `onboarding`, and
`shared` (`shared/tenant`, `shared/security`, `shared/crypto`).

- `Tramite.estado` (`EstadoTramite`) has exactly 7 values:
  `PENDIENTE_EXTRACCION, PENDIENTE_REVISION, APROBADO, EN_PROCESO, EJECUTADO_OVZ, ERROR_OVZ,
  RECHAZADO`. There is no separate "ambiguous explotación" state — that case is
  `PENDIENTE_REVISION` with `explotacion = null`.
- `MensajeCampo` deliberately does **not** extend `GestoriaScopedEntity` — at webhook-receipt time
  the Gestoría isn't known yet. `gestoria_id`/`contacto`/`tramite` are nullable and populated once
  the Contacto is resolved by phone.
- `UsuarioExplotacion` (in the `gestoria` package, mirroring how `ContactoExplotacion` lives in
  `contacto`) is an empty, unused join table for the future portfolio feature — no repository yet,
  same as `ContactoExplotacion`.
- Security is stateless JWT (`jjwt`). `JwtService` puts `usuarioId`, `gestoriaId`, `email` in the
  token; `JwtAuthenticationFilter` reconstructs a `GaneraUserPrincipal` from it, resolvable in
  controllers via `@AuthenticationPrincipal GaneraUserPrincipal`. `SecurityConfig` has an explicit
  `authenticationEntryPoint` so a missing/invalid JWT on a protected endpoint returns `401`, not
  Spring Security's default `403`.
- Public paths: `/webhooks/**`, `/auth/login`, `/gestorias/registro` (public self-registration,
  no JWT and no shared secret), and `/internal/**` (the last one only at the Spring Security layer —
  `/internal/onboarding/gestoria` still gates itself on the `X-Internal-Secret` header inside
  `OnboardingController`). Everything else requires a valid JWT.
- **`OnboardingController` (`POST /internal/onboarding/gestoria`) is a temporary bootstrap, not the
  final design.** Real customers now sign up through `POST /gestorias/registro`; this endpoint
  stays only for pilot Gestorías (created directly in `ACTIVA`, no Stripe) and support/manual
  cases, hit by hand and guarded by a shared secret (`ONBOARDING_SECRET`,
  constant-time compare, fails closed if unset) instead of a JWT — at the time it's called, no admin
  Usuario exists yet to authenticate as. Replace it with a real admin panel (with its own access
  control) once one exists; don't build more features on top of the shared-secret pattern.
- **Lombok footgun**: this JDK/Maven combination breaks Lombok's annotation processing when
  `--release 21` is combined with implicit classpath-based processor discovery (silent
  "cannot find symbol" for `@Getter`/`@Setter`/`@Slf4j`-generated members). Fixed by declaring
  `annotationProcessorPaths` explicitly for Lombok in the `maven-compiler-plugin` config in
  `backend/pom.xml` — don't remove that block thinking it's redundant boilerplate.
- **Twilio footgun (fixed)**: `TwilioWebhookController` used to build `new RequestValidator(authToken)`
  in its constructor — Twilio's SDK throws `IllegalArgumentException: Empty key` on a blank token,
  which meant the *entire app* refused to start whenever `TWILIO_AUTH_TOKEN` was unset (true for
  every environment so far, since Twilio isn't wired to business logic yet). Fixed by constructing
  `RequestValidator` per-request instead of in the constructor. Don't move Stripe's or any other
  optional integration's client construction back into a constructor without checking it tolerates
  a blank credential — verified live by actually starting the app, not just by compiling (see
  `mvn spring-boot:run` note below).
- **commons-io footgun (fixed)**: adding Apache POI (`poi`/`poi-ooxml` 5.3.0, for the Prompt 3d Excel
  importer) caused a runtime `NoSuchMethodError` on `BoundedInputStream.builder()` — Twilio's SDK
  transitively pulls `commons-io:2.14.0`, which Maven's nearest-wins mediation picked over the
  newer version POI actually needs (`BoundedInputStream.Builder` only exists from `commons-io`
  `2.16+`), and this only surfaces at runtime when the importer actually reads a file, never at
  `mvn compile`. Fixed by pinning `commons-io` to `2.16.1` as an explicit direct dependency in
  `backend/pom.xml` (a direct dependency always wins mediation over anything transitive, regardless
  of version). If any future dependency bump touches Twilio, POI, or commons-io, re-check this pin
  doesn't drift back out of range.
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

## Architecture notes (frontend)

Vite + React 19 + TypeScript + Tailwind v4 (`@tailwindcss/vite` plugin, no `tailwind.config.js`) +
shadcn/ui, actually the **"base-nova"** style variant (`@base-ui/react` primitives, not Radix,
despite this file's earlier prose — `components.json` and every installed component in
`src/components/ui/` confirm base-ui; component *props* generally match the usual shadcn/Radix
shape — `value`/`onValueChange` on `Select`, `open`/`onOpenChange` on `Dialog` — but check the
actual `.d.ts` before assuming a prop exists, e.g. `Select.Value`'s label is **not** automatic: it
needs a `children` render-function (`{(value) => label}`), unlike Radix). Path alias `@/*` →
`src/*`. Structure is by feature (`features/auth`, `features/tramites`, `features/ganaderos`,
`features/explotaciones`, `features/facturacion`); `features/ganaderos` is still an empty
placeholder route (`Prompt 4` deliberately built no Ganadero screen — Excel import is the only
creation path, see Non-negotiable rules).

- **Auth is in-memory, not `localStorage`, on purpose** (session-only SPA — a page reload requires
  logging in again, no refresh token yet). `shared/api/authSession.ts` holds the token in a
  module-level variable (not React state) so `shared/api/httpClient.ts` — outside any React
  tree — can read it in its request interceptor; a response interceptor calls
  `notifyUnauthorized()` on any real `401`, which `shared/auth/AuthContext.tsx` (`AuthProvider`)
  registers a handler for to clear its own React state. `shared/auth/RequireAuth.tsx` is a
  pathless layout route (`<Outlet/>` or `<Navigate to="/login"/>`) wrapping the authenticated part
  of `router.tsx` — reading `token` from context, not the module variable directly, so React
  re-renders when it changes.
- **`AppLayout.tsx`** now has real navigation (Explotaciones/Trámites/Facturación — deliberately no
  Ganaderos link, see above) and fetches subscription status once
  (`features/facturacion/useSuscripcionEstado.ts`) via `<Outlet context={{ suscripcion }}>` so
  `FacturacionPage` reuses the same fetch instead of refetching — read it with
  `useOutletContext<AppLayoutContext>()`. `SuscripcionBanner.tsx` shows a persistent warning for
  `TRIAL_EXPIRADO_SIN_PAGO`/`SUSPENDIDA`/no-`Suscripcion`-at-all (all three block approving, all
  three get an "actualiza tu suscripción" banner) and a milder one for `IMPAGO_GRACIA` — it never
  hides the rest of the app (Explotaciones/Trámites stay browsable), only `POST /tramites/{id}/aprobar`
  itself is blocked (`403` from `puedeAprobarTramites`), matching the backend's existing read-only
  vs. full-access semantics from Prompt 2 — don't make the banner block navigation, that would be a
  stricter gate than what the backend actually enforces.
- **No frontend test tooling exists** (no Vitest, no `@testing-library/react`) — verification for
  this prompt was `tsc -b` (clean) + `oxlint` (clean, pre-existing warnings only) + driving the real
  built app in a headless Chromium against the real backend (login → Excel import round-trip with
  exact resumen match → trámites list + estado filter → facturación page → logout), not automated
  browser tests. If frontend logic grows more complex, revisit adding a test runner rather than
  relying on manual browser checks indefinitely.

## Commands

Backend (from `backend/`; use `./mvnw` not a bare `mvn` — see the PATH/JAVA_HOME footgun above):
- `./mvnw compile` — compile
- `./mvnw spring-boot:run` — run locally (Spring Boot does **not** auto-load `.env` — export the
  variables from `.env.example` into the shell/IDE run config yourself, or run via
  `docker-compose` where Postgres is provided but the app itself still needs its own env vars set)
- `./mvnw test` — runs the test suite (110 tests as of Prompt 4); a single test:
  `./mvnw test -Dtest=AuthServiceTest`
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
- `npm run build` — `tsc -b && vite build`
- `npm run lint` — oxlint
- `npm run preview`

Root:
- `docker-compose up -d` — Postgres only (the Spring Boot app runs outside Docker for now).
- **After cloning, recreate the `impeccable` skill junction.** The design skill is committed under
  `.agents/skills/impeccable/` (pinned in `skills-lock.json`), but Claude Code loads it from
  `.claude/skills/impeccable`. That path is a Windows directory junction, and it's in `.gitignore`:
  git for Windows traverses junctions as plain directories (`core.symlinks=false`), so committing
  it would store a second, drifting copy of the whole skill. Recreate it from the repo root in
  `cmd`: `mklink /J .claude\skills\impeccable .agents\skills\impeccable`, or reinstall the skill
  from `skills-lock.json`.

## Current status

Steps 1, 2, 2.5, and 2.7 are complete: infrastructure scaffold; the data model closing out Prompt 2
(`EstadoSuscripcion`'s final 6 states, `SuscripcionService.puedeAprobarTramites` fail-closed when no
`Suscripcion` row exists, `Gestoria.modoCartera` + `UsuarioExplotacion`) was rebuilt from scratch and
re-verified end-to-end on 2026-07-09 following
`docs/superpowers/plans/2026-07-09-prompt2-estados-suscripcion-cartera.md` (migrations `V10`-`V12`,
H2 smoke test confirming all 12 migrations apply cleanly with no bean-wiring errors); Prompt 2.5 —
real Usuario authentication (`POST /auth/login`, `GET /auth/me`) plus manual pilot onboarding
(`POST /internal/onboarding/gestoria`, temporary — see Architecture notes; it creates the Gestoría,
its first Usuario, and a `Suscripcion` directly in `ACTIVA`, no Stripe involved) — implemented and
verified on 2026-07-09 following
`docs/superpowers/plans/2026-07-09-prompt2.5-autenticacion.md`; and Prompt 2.7 — the full Stripe
integration (`POST /facturacion/checkout`, the `StripeWebhookService` state machine, and the nightly
`SuscripcionSyncScheduler` quantity-reconciliation job — see the Stripe bullet under Technical
decisions for the full design and its one known limitation) — implemented and verified across the
plan's four tasks on 2026-07-09 following `docs/superpowers/plans/2026-07-09-prompt2.7-stripe.md`: migration
`V13` adds `stripeSubscriptionId`/`explotacionesContratadas`/`stripeUltimoEventoEpoch` to
`Suscripcion`; full backend suite green at 79 tests; an H2 smoke test confirming the app boots
cleanly with all 13 Flyway migrations validating and every Stripe-related env var
(`STRIPE_API_KEY`/`STRIPE_WEBHOOK_SECRET`/`STRIPE_PRICE_ID_EXPLOTACION`) left blank —
`POST /facturacion/checkout` returns `503` with a real JWT (Stripe unconfigured) and `401` without
one, `POST /webhooks/stripe` returns `400` on an invalid signature, and `SuscripcionSyncScheduler`
wires up with no bean errors. Backend and frontend compile/build cleanly, backend tests pass
(verified once by actually booting the app — see the H2 smoke-test command above — not just by
compiling). **`STRIPE_PRICE_ID_EXPLOTACION` is still an empty placeholder** — creating the real
`Price` in the Stripe dashboard (test mode first) is Antonio's manual action, not something any
agent does, so a real end-to-end Checkout Session against Stripe test-mode is still pending that
step. No AI/WhatsApp business logic is wired end-to-end yet for trámites — Twilio's webhook
validates signatures and persists raw data, but doesn't yet trigger AI extraction or trámite
creation; `TramiteExtractionService`'s system prompt and `OvzAutomationService`'s Playwright logic
are still unimplemented skeletons; portfolio filtering by `modoCartera` isn't implemented; there is
still no admin panel (public self-registration was added later, see below).

Prompt 3d — Excel inventory importer + basic REST controllers — is also complete, implemented and
verified end-to-end on 2026-07-14 (no separate written plan; the prompt itself, refined through a
few upfront clarifying questions on schema gaps, served as the spec): migration `V14` adds
`Ganadero.nif` (globally unique); `POST /explotaciones/importar`, `GET /explotaciones`,
`GET /tramites`, `POST /tramites/{id}/aprobar`, `POST /tramites/{id}/rechazar` (see the Excel
importer bullet under Technical decisions for the full design). This is the **first real caller of
`SuscripcionService.puedeAprobarTramites`** (via `/aprobar`, `403` if it returns false) — though
`/aprobar` and `/rechazar` still only flip `EstadoTramite` in the DB, no `OvzAutomationService` call
yet (Prompt 3c). Full backend suite green at 91 tests (79 + 12 new); real end-to-end HTTP smoke test
(H2 + all 14 migrations, onboarding → login → import a real fixture `.xlsx` → list → re-import same
file and confirm no duplication → 401 without JWT → 404 on an unknown trámite id) — not just the
`@DataJpaTest`-level suite.

Prompt 4 — functional frontend, **only the part not blocked by OVZ.net** — is complete as of
2026-07-14: login (JWT in-memory, see Architecture notes), Explotaciones dashboard with the Excel
importer given real UI prominence, Trámites queue with estado filter + review modal (handles a null
WhatsApp message/unresolved Explotación without looking broken, since 3b hasn't shipped yet) +
Aprobar/Rechazar (a `403` on Aprobar shows the real "tu suscripción no permite aprobar trámites"
reason, never a generic error), and a Facturación status page. Deliberately **not** built (per
explicit scope): the OVZ-credentials-onboarding screen for a new Ganadero, and any manual
Ganadero/Explotación creation form — both would either need real OVZ.net access or open a second,
divergent data-entry path around the Excel importer. Two backend additions this required
(`GET /facturacion/suscripcion`, `GET /tramites/{id}`) — see the Prompt 4 bullet under Technical
decisions. Full backend suite green at 101 tests (93 + 7 for the two new endpoints + 1 critical
end-to-end regression test, see below). Frontend verified with `tsc -b` (clean) + `oxlint` (clean)
+ a real headless-Chromium session against the real backend (Playwright's npm package, installed
ad hoc for this — no project skill existed yet for driving this app; consider
`/run-skill-generator` if this becomes a recurring need) driving: login → Excel import (exact
resumen match against the backend) → Trámites list + estado filter (confirmed the actual
`?estado=APROBADO` query fired) → Facturación (ACTIVA, no blocking banner) → logout → redirect to
`/login`. **Found and fixed two frontend bugs only a live browser catches:** the CORS gap above,
and `Select`'s trigger showing the raw value (`"TODOS"`) instead of the label until given a
`children` render-function (see Architecture notes).

Before calling Prompt 4 closed, Antonio explicitly asked to also verify live (not just by backend
integration tests/code review) the three paths that hadn't been exercised yet: the review-modal
with a real WhatsApp message + resolved Explotación, the `403`-on-aprobar message, and the
no-Suscripción banner — plus the null-message/unresolved-Explotación case again for real. Since
none of these can be produced through the running app yet (no Tramite-creation UI, 3b doesn't
exist), the data was seeded directly by SQL against the smoke-test H2 instance (a file-backed
H2 with `AUTO_SERVER=TRUE` so a separate `org.h2.tools.RunScript` process could write to the same
live database — no app business logic involved in the seeding itself) and driven from a real
browser. All four looked correct — **but seeding a second real Gestoría with its own login for
the no-Suscripción check surfaced a real, previously-undetected critical bug**: `GET /tramites`
and `GET /explotaciones` returned rows from *every* Gestoría, not just the authenticated one. See
the "Critical footgun" note under the Multi-tenancy bullet above for the full root cause
(`TenantFilterActivationInterceptor` vs. Spring Boot's `OpenEntityManagerInViewInterceptor`
ordering) and the fix. This had been latent since the interceptor was first introduced — invisible
to every previous test and manual smoke test because none of them ever compared two Gestorías
with overlapping data over real HTTP in the same run. Fixed, verified with a new deliberate
end-to-end test (confirmed it fails without the fix, passes with it), full suite re-run green, and
all four scenarios re-verified live in the browser against the fixed backend.

**Before considering that fix sufficient, Antonio asked for a targeted audit of every endpoint
touching a `GestoriaScopedEntity`** — explicitly rejecting "it uses the same Repository, so it's
already covered" as a valid argument, since the bug above was at the interceptor/ordering level,
not the query level, so each endpoint needed its own real two-tenant HTTP test. That audit found
a **second, independent critical bug**: `gestoriaFilter` never applied to `findById(id)` at all
(a separate Hibernate behavior, unrelated to interceptor ordering — see the second footgun note
under Multi-tenancy above), so `GET /tramites/{id}`, `POST /tramites/{id}/aprobar`, and
`POST /tramites/{id}/rechazar` let any authenticated user view/approve/reject **any Gestoría's**
trámite by id. Fixed with an explicit `findByIdAndGestoriaId` query in all three, again writing
the failing test first. `TenantIsolationEndToEndTest` now has one real two-tenant case per
affected endpoint (7 total); full backend suite green at **110 tests** (101 + 6 more E2E cases +
3 unit-level cross-tenant checks in `TramiteControllerTest`).

**Public self-registration (`POST /gestorias/registro`) is complete**, implemented and verified on
2026-07-14: the `registro` package (see the Technical decisions bullet above for the full design —
`RangoClientes`, `RegistroGestoriaService`, `RegistroGestoriaController`, the new
`StripeCheckoutService.crearSesionCheckoutConCantidadEstimada` overload, and the `SecurityConfig`
`permitAll` for this route), plus a matching frontend `RegistroPage` (same brand styling as the rest
of Prompt 4/the branding pass) reachable from a new "¿No tienes cuenta? Regístrate" link on
`LoginPage`. Full backend suite green at **135 tests** (110 + 25 new: `RangoClientesTest`,
`RegistroGestoriaValidacionTest`, `RegistroGestoriaServiceTest`, `RegistroGestoriaControllerTest`, a
new `RegistroGestoriaEndToEndTest`, and one addition to `StripeCheckoutServiceTest`). Verified with
a real H2 smoke test over HTTP (`curl`, no `Authorization` header — confirms the route is genuinely
public, not just returning `401` before ever reaching the controller — followed by a real
`/auth/login` + `/auth/me` round-trip with the credentials just registered, proving Gestoria+Usuario
were actually persisted) and in a real browser (Playwright): the "Regístrate" link, the client-range
`Select` showing real labels (not raw enum values — same care already needed for `TramitesPage`'s
`Select`), a weak password and a duplicate email producing the exact same generic error message
(confirming the no-enumeration guard holds visually, not just in a unit test), and a genuinely new
registration correctly showing the "facturación no configurada" message. **A real end-to-end
redirect to Stripe's hosted Checkout page is still unverifiable in this sandbox**, for the same
pre-existing reason as the rest of the Checkout flow: `STRIPE_PRICE_ID_EXPLOTACION` is still an
empty placeholder (see Prompt 2.7 above) — creating the real `Price` in the Stripe dashboard remains
Antonio's manual action.

**Next pending step: Prompt 3a (sincronización inicial de OVZ.net, modo lectura) — blocked.** Per
`ganera-prompts.md`, Prompts 3a, 3b, and 3c (OVZ.net read sync, Twilio webhook + AI extraction, and
real Playwright write-mode automation) are all blocked on the same prerequisite: Antonio needs to
provide real OVZ.net credentials so the actual site structure and trámite catalog can be explored
live (the Playwright MCP is set up for exactly this — driving the real site with Antonio steering,
not guessing at its structure from assumptions). Until that happens, `TramiteExtractionService` and
`OvzAutomationService` stay as unimplemented skeletons (see above) — don't write real prompt/scraping
logic against assumptions about OVZ.net's structure. The OVZ-credentials onboarding screen and the
rest of Prompt 4's originally-scoped items are deferred alongside it.

`ganera-prompts.md` at the repo root tracks the full sequence of prompts used to
build this out, in order — check it for the detailed history/rationale behind any given step.
