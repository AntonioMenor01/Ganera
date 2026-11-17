# Decisiones tecnicas — historico

Relatos de prompts ya cerrados, movidos desde `CLAUDE.md` para adelgazarlo. Son historia, no reglas vivas; las reglas vigentes siguen en `CLAUDE.md`.


## Landing payment linking (alta_pendiente + onboarding link)

- **Landing payment linking — `alta_pendiente` + onboarding link (2026-10-26, `feat/stripe-enlace-alta`;
  phase 1 of the "billing from the landing" flow, pilot / semi-manual).** Ganera now charges from the
  landing (`ganera-web`), which creates Checkout Sessions with a **fixed price per plan** (Base/Gestión/Plus,
  monthly/annual), **not per Explotación**, carrying `metadata {plan, periodo, prueba, source:"ganera-web"}`
  and **no `client_reference_id`**. Contract in `ganera-web/docs/INTEGRACION-APP.md`. This task wires the
  payment to the app **safely**, without yet creating the Usuario or sending any email (that is the automatic
  phase, below).
  - **API-version mismatch (critical).** The live webhook is created with API `2026-09-30.endive`; the SDK
    (stripe-java 28.2.0) is `2024-12-18.acacia`. With that mismatch
    `evento.getDataObjectDeserializer().getObject()` comes back **empty** and the SDK types
    (Session/Invoice/Subscription) can't be deserialized from the event, so `manejarXxx` was silently
    dropping events. The SDK was **not** bumped. Instead all five handled events
    (`checkout.session.completed`, `invoice.payment_succeeded`, `invoice.payment_failed`,
    `customer.subscription.updated/deleted`) extract their fields from the **raw JSON**
    (`getDataObjectDeserializer().getRawJson()` + `getData().getPreviousAttributes()`) with pure static
    extractors (`extraerCheckout`, `extraerSubscriptionIdDeInvoice`, `extraerSuscripcion` — tested with
    endive fixtures). Outgoing calls (`Session.listLineItems`, `Subscription.retrieve`) keep using the SDK
    (Stripe answers in the SDK's version). The invoice's subscription id supports both the old top-level
    `subscription` and the new `parent.subscription_details.subscription`. **If an essential field can't be
    extracted → ERROR log (event id + type) + `EventoStripeNoProcesableException` → 5xx so Stripe retries;
    never a silent 200** (`StripeWebhookController` catches it).
  - **`alta_pendiente` table (migration `V24`, non-tenant — no `gestoria_id`, exists before the Gestoría):**
    on `checkout.session.completed` without `client_reference_id`, if `source=="ganera-web"` **or** a session
    price is in the configurable map (Payment Links), a row is saved (PENDIENTE) with session/customer/
    subscription ids, email, name, NIF (`customer_details.tax_ids`), billing address, plan, periodo, prueba.
    Idempotent by `stripe_session_id` UNIQUE. **No message bodies or card data; logs carry ids only.** Each
    new row logs **WARN with ids only** for Antonio/Adolfo to see in Clever. The RLS `afterMigrate` callback
    covers it as a normal table (`ganera_app_todo`). Price map: single env var **`STRIPE_PRICE_PLAN_MAP`**
    (`ganera.stripe.precio-plan-map`), format `priceId=plan:periodo,...`, **empty default, nothing
    hardcoded**; `source=ganera-web` is recognised without it, the map is only for Payment Links.
  - **`POST /internal/onboarding/gestoria` accepts an optional `stripeSubscriptionId`:** if present it links
    the new Gestoría's `Suscripcion` (`stripeCustomerId`, `stripeSubscriptionId`, `plan`/`periodo` from the
    `alta_pendiente` row or the Subscription metadata) and **recomputes the estado by retrieving the
    Subscription from Stripe** (`EnlaceStripeService`: `trialing→TRIAL`, `active→ACTIVA`, `past_due→IMPAGO_GRACIA`,
    `unpaid→SUSPENDIDA`, `canceled→CANCELADA`), then marks the `alta_pendiente` ENLAZADA. **Stripe not
    configured → 503** (checked before creating anything); a Stripe call failure → 502 **and rolls back** the
    whole `@Transactional` (Gestoría + Usuario + Suscripción). Recomputing the estado from Stripe at link time
    means a `invoice.payment_succeeded` that arrived **before** the link isn't lost (order-independent).
  - **Fixed-plan marker (double-charge guard):** `Suscripcion.plan`/`periodo` columns (migration `V24`).
    When `plan != null` (a landing/fixed-price subscription), `SuscripcionSyncScheduler.calcularAjustes`
    **excludes it** from the nightly quantity reconciliation — otherwise the job would push
    quantity = number of Explotaciones to a fixed price and multiply the charge. `plan == null` keeps the
    legacy per-Explotación behaviour. Covered by
    `SuscripcionSyncSchedulerTest.excluyeLasSuscripcionesDePlanFijoAunqueLaCantidadDifiera`.
  - **Still pending — automatic phase (not built on purpose for the pilot):** create the Usuario and send an
    **activation email with Ganera's own password-reset token (JWT-based, NOT Supabase Auth)** on
    `checkout.session.completed`, so the customer who paid gets the account the landing's `/gracias` page
    promises. Today that email is **not** sent and the Usuario is **not** created automatically — Antonio
    links each payment by hand via `/internal/onboarding/gestoria`, so a paying customer sees the "we've
    emailed you" promise but gets nothing until then (accepted gap for the pilot). The `INTEGRACION-APP.md`
    contract still says Supabase Auth `inviteUserByEmail`; that is **obsolete** (the app uses its own JWT),
    and the landing's copy of the doc will be corrected by Antonio.

## Frontend, Prompt 4 (partial)

- **Frontend, Prompt 4 (partial — everything not blocked by OVZ.net):** login, Explotaciones
  dashboard + Excel import, Trámites queue + review modal, Facturación status page (removed on
  2026-10-04, see "Billing moved out of the app"). Two backend additions this required, both driven
  directly by the frontend's stated needs, not speculative: `GET /facturacion/suscripcion`
  (`FacturacionController`, still used by the banner; `404` if the Gestoría never had a
  `Suscripcion` — fail-closed, a `GET` must never create one as a side effect; only the
  registration's checkout → `obtenerOCrearSuscripcion` does) and `GET /tramites/{id}`
  (`TramiteDetalleResponse`, adds the raw WhatsApp message body via
  `MensajeCampoRepository.findFirstByTramiteIdAndGestoriaIdOrderByCreatedAtDesc` — with the
  `gestoriaId` since "Hecho en OVZ"; the unscoped finder was removed — plus the resolved Explotación's
  `codigoRega`/`nombre` — kept out of the paginated `GET /tramites` list to avoid N+1 there).
  **CORS** (`SecurityConfig.corsConfigurationSource`, origin from `ganera.frontend.origen` /
  `FRONTEND_ORIGEN` env var, default `http://localhost:5173`): found by *actually driving the app in
  a real browser* — every backend test passed and `curl` worked fine, but the browser blocks the
  request at preflight before it ever reaches a controller, JWT valid or not. `curl`/Postman never
  send an `Origin` header the way a browser does, so this class of bug is invisible to any
  request-level test; only a real browser catches it.

## Mini-prompt of backend after A2

- **Mini-prompt of backend after A2** (plan with the closed decisions in
  `docs/superpowers/plans/2026-10-02-mini-prompt-backend-tras-a2.md`; the API gaps the A2 frontend
  worked around). Besides the changes noted in the importer, self-registration and "Prompt A1"
  bullets (`{motivo}` everywhere, `version` on rechazar, `403` with `motivo`, `completo`):
  - `GET /explotaciones/{id}` → `ExplotacionResponse` (same DTO as the list), with
    `findByIdAndGestoriaId`; `404` with no body for another Gestoría's or a nonexistent one.
    Side effect: `GET /explotaciones/importar` is now a `400` (non-numeric id) instead of `405`.
  - `GET /explotaciones?q=` — over the Explotación's `codigoRega` and `nombre` **and the
    Ganadero's `nombre`**; `q` trimmed; absent/blank = the usual listing; more than 100 characters
    (UTF-16 units) → `400 {motivo}`, checked **after** the `sort` whitelist. (Its first version
    matched `q` as one string, case-insensitive but not accent-insensitive; replaced on 2026-10-04 by
    the accent-insensitive, word-by-word search — see the "Explotación search without accents"
    bullet below.)
  - `TramiteResponse` (list, aprobar, rechazar) gained `explotacionCodigoRega` and
    `explotacionNombre` (null without Explotación), loaded in the page query with
    `@EntityGraph(attributePaths = "explotacion")` on `TramiteRepository.findByGestoriaId`/
    `findByGestoriaIdAndEstado` — a test counts SQL statements to guard against N+1 (it first
    asserts the page query really contains `join explotacion`, so a SQL-format change can't make
    it pass silently). Two-Gestoría coverage of these lives in `TramiteRevisionEndToEndTest` and
    the new `ExplotacionEndToEndTest`, not in `TenantIsolationEndToEndTest`.
  - **Pre-existing N+1 fixed:** `GET /explotaciones` **without** `q` used to load the Ganadero
    lazily for every distinct Ganadero of the page (`ExplotacionResponse` reads its name), which
    hurt with the frontend's 500-row pages. `ExplotacionRepository.findByGestoriaId` now has
    `@EntityGraph(attributePaths = "ganadero")`; `ExplotacionEndToEndTest` asserts zero separate
    entity fetches and exactly page + count statements (Hibernate statistics). The `q` path already
    used `join fetch`.
  - The frontend was touched **only** so Rechazar keeps working (`rechazarTramite(id, version)` in
    `features/tramites/api.ts`, fed with `detalle?.version` from `useRevisionTramite`). It does
    **not** consume the rest then; the frontend task before the pilot (2026-10-04) consumes `?q=`,
    `explotacionCodigoRega`/`explotacionNombre`, `completo` and the `403` `motivo` (see the
    frontend architecture notes). `GET /explotaciones/{id}` still has no caller.

## Billing moved out of the app

- **Billing moved out of the app** (2026-10-04; plan
  `docs/superpowers/plans/2026-10-04-quitar-pago-de-la-app.md`). Ganera charges from its **landing,
  with Stripe**, not from the app. **Removed:** `POST /facturacion/checkout`, `CheckoutResponse`,
  `StripeCheckoutService.crearSesionCheckout(gestoriaId)`, the frontend `FacturacionPage`, its route
  and nav link, `crearSesionCheckout`, the `checkout` error context and the 403's "Ir a
  Facturación" link. `POST /facturacion/checkout` now answers Boot's `404` (`NoResourceFoundException`,
  no handler) with a JWT and `401` without one, and creates nothing (`CheckoutEliminadoEndToEndTest`).
  **Kept:** Stripe webhooks and their state machine, `Suscripcion`/`EstadoSuscripcion`,
  `puedeAprobarTramites` and the approval `403`, `GET /facturacion/suscripcion` (same path and body,
  read by the banner), `SuscripcionSyncScheduler`, `StripeConfig`/`stripe-java`/`STRIPE_*`, the pilot
  onboarding, and the public registration with its Stripe Checkout redirect (untouched until
  Prompt C replaces it with the sign-up from the landing). Texts that sent users to Facturación now
  say to contact Ganera, **with no link or `mailto:`** — the 403 `motivo`
  (`TramiteController.MOTIVO_SUSCRIPCION_NO_PERMITE_APROBAR`, identical to the frontend fallback):
  "Tu suscripción no permite aprobar trámites ahora mismo (prueba terminada o suscripción
  suspendida). Ponte en contacto con Ganera para regularizarla." The two known Stripe bugs and the
  scheduler's review for per-ganadero plans are noted for Prompt C in `ganera-prompts.md`.

## C minimo — demo deployed in the EU

- **C mínimo — demo deployed in the EU** (2026-10-06; plan
  `docs/superpowers/plans/2026-10-06-promptC-minimo-demo-desplegada.md`, decisions D1–D8). Not the
  final Prompt C infrastructure: a demo with **invented data only** for Antonio's partner.
  - **Clever Cloud, Paris zone**: Java/Maven app XS (`APP_FOLDER=backend`, one instance, no
    autoscaling — the extraction queue doesn't claim rows), PostgreSQL 16 add-on XXS Small Space
    (replaced by Supabase on 2026-10-19, see the "Supabase" bullet; never deployed),
    frontend on the Static runtime (pico, `VITE_API_BASE_URL` baked in at build time). Every secret
    lives only in Clever's environment variables; Claude never sees them. `.clever.json` is ignored.
  - **`application-clever.yml`** (`SPRING_PROFILES_ACTIVE=clever`): datasource from the add-on's
    `POSTGRESQL_ADDON_HOST/PORT/DB/USER/PASSWORD` (history: since the Supabase migration the profile reads the seven
    `SUPABASE_*` variables instead, see the "Supabase" bullet), `?logServerErrorDetail=false`, explicit
    PostgreSQL driver. Never define a `GANERA_REGISTRO_*` variable there (relaxed binding would open
    the registration).
  - **Tests against real PostgreSQL** (`com.ganera.core.postgres`, `io.zonky.test:embedded-postgres`
    2.2.2 + binaries BOM **17.11.0** since the Supabase migration — Supabase's exact version; 16.15.0 until then —,
    test scope only, no Docker; `PostgresEmbebido` shares one server per
    JVM, UTF8, a fresh database per test): Flyway V1–V23 + `ddl-auto=validate`, V18 on existing rows,
    the `clever` profile end to end, and `InstantTimestampPostgresTest`. **Finding (D8):** with
    Hibernate 6.6 + pgjdbc 42.7.4 an `Instant` round-trips through a `TIMESTAMP` (without time zone)
    column with no shift even with the JVM in `Europe/Madrid`, and the extraction queue and
    retention cut exactly (also in October's repeated hour); that test is the regression guard for
    pgjdbc/Hibernate upgrades. Only `DEFAULT now()` on rows inserted by hand depends on the session
    zone (`DefaultNowPostgresTest`): hand-written SQL starts with `SET TIME ZONE 'UTC'` and sets
    `created_at` explicitly; `TZ=UTC` on Clever and `ALTER DATABASE … SET timezone TO 'UTC'` (since the Supabase
    migration, done by `backend/supabase/01-preparar-roles-y-esquema.sql`).
  - Demo data and scripts (two invented Gestorías, Excel, seed SQL for Adminer, onboarding and smoke
    scripts that Antonio runs in his own terminal) live **outside the repo**, in
    `C:\Users\Antonio\Desktop\ganera-demo\` (superseded by the `ganera-clever` kit, see "Clever deployment
    hardening").


## Stripe integration

- **Stripe integration** (`facturacion` package): a single `Price` (`STRIPE_PRICE_ID_EXPLOTACION`,
  placeholder until one is created in the Stripe dashboard) with quantity = active Explotaciones
  count, 15-day trial baked into the Checkout Session. `StripeCheckoutService` sets
  `client_reference_id` = `gestoriaId` on the Checkout Session — this is how the
  `checkout.session.completed` webhook maps back to a Gestoría, not the Stripe customer ID. Since
  2026-10-04 the **only** caller is the public self-registration (`POST /gestorias/registro`,
  `crearSesionCheckoutConCantidadEstimada`): the app's own `POST /facturacion/checkout` was removed
  (billing moves to Ganera's landing, see "Billing moved out of the app"). Pilot Gestorías (created
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
  **Known bugs, detected 2026-09-25 in a full-project review:**
  1. *`invoice.payment_failed` on an already-`TRIAL_EXPIRADO_SIN_PAGO` subscription.* **FIXED
     (2026-10-26, `feat/stripe-enlace-alta`, test-first.)** `StripeWebhookService.procesarPagoFallido`
     now maps `TRIAL` **or** `TRIAL_EXPIRADO_SIN_PAGO` → `TRIAL_EXPIRADO_SIN_PAGO` (Prompt 2.7 plan,
     Decision 4); any other stored state still → `IMPAGO_GRACIA`. Before, the second failed retry after
     an unpaid trial flipped the subscription to `IMPAGO_GRACIA`, which **re-enabled**
     `puedeAprobarTramites`. Covered by
     `StripeWebhookServiceTest.procesarPagoFallidoDesdeTrialExpiradoSinPagoSeQuedaIgualYNoPasaAImpagoGracia`.
  2. *Abandoned trial never expires.* (Still PENDING.) `SuscripcionService.obtenerOCrearSuscripcion` creates the
     `Suscripcion` row in `TRIAL` **before** the Checkout Session is completed (from
     `POST /gestorias/registro`; until 2026-10-04 also from the app's `POST /facturacion/checkout`).
     If the Gestoría abandons
     Stripe Checkout, no webhook ever arrives and nothing local expires the row, so it stays in
     `TRIAL` — with full approval rights — indefinitely. Needs a design decision (e.g. don't grant
     `TRIAL` until `checkout.session.completed`, or a local trial-expiry check) before fixing.

## Public self-registration

- **Public self-registration** (`registro` package) — **closed by default since C mínimo
  (2026-10-06)**: `ganera.registro.abierto` is `false` in `application.yml` and only
  `RegistroGestoriaEndToEndTest` opens it; while closed, `RegistroCerradoFilter` answers `404` with no
  body before Spring Security, without reading the body or creating anything. The frontend
  `RegistroPage`, its API and the "Regístrate" link were deleted (`/registro` → `/login`); sign-up
  moves to the landing in Prompt C. The rest of this bullet describes the endpoint when open:
  `POST /gestorias/registro`, public (no JWT,
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
  `crearSesionCheckoutConCantidadEstimada(gestoriaId, cantidadEstimada)`, which skips the real
  Explotación count and uses the private helper and `configuracionCompleta()` guard (its sibling
  `crearSesionCheckout(gestoriaId)`, used only by the app's checkout, was removed on 2026-10-04). Validation
  (`RegistroGestoriaValidacion`, package-private, pure static methods — deliberately not
  `spring-boot-starter-validation`'s `@Valid`/`@Email`, which is on the classpath but unused
  anywhere in this codebase, to avoid introducing a first `@ControllerAdvice` just for this one
  endpoint) and the duplicate-email case (a real `UNIQUE(email)` violation, not a pre-check
  `findByEmail`) all collapse into **one identical `400` + generic message** (as `{motivo}`, via
  `shared/web/MotivoErrorResponse`, since the mini-prompt after A2 — it used to be `{mensaje}`;
  `RegistroErrorResponse` was deleted; a test checks the bodies are byte-identical) — same principle as
  `AuthService.autenticar`'s uniform `401`, so a duplicate email can't be distinguished from a weak
  password or a malformed one (no enumeration oracle for which Gestorías are already customers).
  `RegistroGestoriaService.crearGestoriaYUsuario` deliberately does **not** catch
  `DataIntegrityViolationException` itself — it must propagate out of the `@Transactional` method
  so Spring rolls back the `Gestoria` insert together with the failed `Usuario` insert; catching it
  inside would leave an orphan `Gestoria` row. `RegistroGestoriaController`, outside that
  transaction, is the one that catches it to shape the `400` response (and lets the checkout's
  `StripeException` propagate as a generic `500`).

## Excel inventory importer

- **Excel inventory importer** (`explotacion` package, Prompt 3d): manual fallback for loading
  Explotaciones/Animales when the OVZ.net read sync (Prompt 3a, still blocked) isn't available.
  `POST /explotaciones/importar` (multipart `.xlsx`, Apache POI) parses two fixed-position sheets —
  "Explotaciones" (`codigo_rega, nombre, nif_ganadero, nombre_ganadero`) and "Animales"
  (`crotal, especie, codigo_rega_explotacion`) — reading by column *index*, not header name. A
  missing required sheet or a file that isn't a valid `.xlsx` throws
  `FicheroImportacionInvalidoException` (mini-prompt after A2), the **only** source of the
  importer's `400`, returned by `ExplotacionImportController` as `{motivo}` (no longer plain text).
  The file type is detected by **content** when opening the workbook (`abrirLibro`, which wraps
  only `new XSSFWorkbook(...)`), not by extension or `Content-Type`: an old `.xls` (OLE2) gets
  "El fichero es un Excel antiguo (.xls)…guárdalo como .xlsx…"; anything else (text, random bytes,
  empty, a non-xlsx ZIP, a truncated xlsx) gets "El fichero no es un Excel .xlsx válido." POI's
  English message never reaches the client. Any other failure while processing rows is still a
  `500` (never disguised as "invalid file"). Known minor: an `IOException` inside
  `new XSSFWorkbook(...)` can't be told apart from a server-side read failure of the upload, so
  the latter would also be that `400`. `especie` is validated (must be
  blank or a bovine label — Ganera only handles cattle for now) but **never persisted**: `Animal`
  has no `especie` column, an unsupported value is just a row error. Since Prompt A1 (decision 22)
  the crotal is normalized with `CrotalNormalizador` (see the "Prompt A1" bullet) **before** it is
  stored, so `"es-0100 0000.1234"` is saved as `ES010000001234`; an invalid crotal (including 3
  digits or fewer) or one longer than 20 characters after normalization (`animal.crotal
  VARCHAR(20)`) is a row error. The importer still accepts an *incomplete* crotal (4–12 bare
  digits) into inventory — it is the approval rule that blocks sending one to OVZ.net.
  `crotalUltimosDigitos` is derived as the last 6 characters of the (normalized) crotal — an
  assumption kept from 3d; A1's trámite resolution doesn't depend on it (it matches by suffix).
  An optional third sheet, **"Contactos"** (Prompt A1: `telefono, nombre, codigo_explotacion
  (= codigo_rega), rol`), is processed after the other two if present; `ImportResumenResponse`
  gained a `contactos` summary (0/0/0 without the sheet). Per row: phone normalized, name
  non-blank, rol `TITULAR`/`EMPLEADO` (case-insensitive), Explotación by
  `findByCodigoRegaAndGestoriaId`, Contacto by `findByGestoriaIdAndTelefono` — own and active →
  link added/role updated; own and inactive → row error; absent → inserted, and if the insert hits
  the global `UNIQUE(telefono)` (the phone belongs to another Gestoría) → a generic row error that
  never mentions another Gestoría. **Never `findByTelefono` here.**
  **Known limitation (A1 review M4): existing `Animal.crotal` values were not migrated.** A crotal
  imported before A1 with separators or lowercase (`ES 0100 0000 1234`) is not equal to its
  normalized form, so re-importing the same animal now creates a *second* Animal (no UNIQUE
  collision, the strings differ), and its last digits become `AMBIGUO` forever (there is no UI to
  delete animals). Such a legacy crotal is also blocked at approval by the format rule. If a real
  environment ever holds pre-A1 imported data, run a one-off normalizing `UPDATE` on
  `animal.crotal` (checking for collisions first).
  **Cross-tenant existence (A1 decision 17):** `codigo_rega`, `ganadero.nif`, `animal.crotal` and
  `contacto.telefono` are all *globally* unique, so a Gestoría can still deduce that a value exists
  in another Gestoría (its own value updates, a foreign one fails) — an inherent limitation, same
  as decision 1 for phones. What A1 closed: the importer used to answer "posible duplicado entre
  gestorias"; it now answers a neutral "No se ha podido guardar la fila: alguno de sus
  identificadores (código REGA, NIF o crotal) no está disponible.", and `procesarExplotacion`/
  `procesarAnimal` only use finders with an explicit `gestoriaId`
  (`findByCodigoRegaAndGestoriaId`, `findByNifAndGestoriaId`, `findByCrotalAndGestoriaId`). The
  unscoped `findByCodigoRega`/`findByNif`/`findByCrotal` were **removed** from the repositories.
  Caveat (A1 review M3): that neutral message also covers any other constraint violation on those
  sheets — e.g. an over-long NIF or name, which aren't length-validated before the DB yet — and is
  misleading there.
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
  `procesarExplotacion`/`procesarAnimal`/`procesarContacto` each run in
  `@Transactional(propagation = REQUIRES_NEW)` — their own transaction, so one row's constraint
  violation rolls back only that row and never touches the rest of the file.
  **Which `EntityManager` a REQUIRES_NEW row uses (corrected in Prompt A1, decision 19 — the
  original 3d explanation here was wrong for HTTP).** Over HTTP, open-in-view binds the request's
  `EntityManager` to the thread *without* a transaction, and `ExplotacionImportService` is not
  `@Transactional`, so REQUIRES_NEW has nothing to suspend: it starts its transaction on that same
  OSIV `EntityManager`, where `TenantFilterActivationInterceptor` has already enabled
  `gestoriaFilter`. A brand-new `EntityManager` with **no filter active** is only opened when there
  is **no `EntityManager` bound to the thread** (a `@Scheduled` job or any other non-request thread
  — no outer transaction needed for this) **or there is an outer transaction** for REQUIRES_NEW to
  suspend (a `@Transactional` caller, `@DataJpaTest`). That's why each REQUIRES_NEW method still
  re-enables `gestoriaFilter` with the `gestoriaId` passed in — and, since A1, every query there
  also takes that `gestoriaId` as an explicit parameter, so it doesn't depend on the filter at all.
  Any other code that reaches for `REQUIRES_NEW` on a bean touching `GestoriaScopedEntity` data
  must do the same. (Nit left from the A1 7a review, N2: the Javadoc of `ExplotacionImportFilaService`
  still lists "a scheduler" under "only when there IS an outer transaction"; the precise condition
  is the one stated here.)
  `GET /explotaciones` and `GET /tramites` (filterable by `estado`) are the first paginated
  endpoints in the codebase (`Page`/`Pageable`, Spring Data's default web support — no custom
  config needed). Since Prompt A1 (decision 21) both query with the JWT's `gestoriaId` as an
  explicit parameter (`findByGestoriaId…`), not only the ambient `gestoriaFilter`, and validate
  `?sort=` against a whitelist (see the "Prompt A1" bullet). `POST /tramites/{id}/aprobar` gates on
  `SuscripcionService.puedeAprobarTramites` (`403` if false) — the first real caller of that method.
  Neither `/aprobar` nor `/rechazar` calls `OvzAutomationService`, and approving never will: sending
  is a separate step of escalón 2 (modo asistido), on the gestor's own "Enviar"; since A1
  both go through `TramiteRevisionService` (see the "Prompt A1" bullet). A tramite id from another
  Gestoría or that doesn't exist returns `404` (an expected, common case, not a "should never
  happen" one).

## Prompt A1 — Contactos, crotales in trámites, editable review

- **Prompt A1 — Contactos, crotales in trámites, editable review** (backend only; plan with all 31
  decisions in `docs/superpowers/plans/2026-09-25-promptA1-contactos-crotales-revision.md`).
  Prepares 3b without depending on OVZ.net: which Explotación(es) a phone belongs to, which
  crotales a Tramite mentions, and letting the Gestoría correct a Tramite before approving it.
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
  `400 {"motivo": "Campo de ordenación no permitido."}` (`shared/web/OrdenacionPermitida`) — before,
  an unknown property was a `500` and a nested one (`ganadero.ovzUsuario`, `gestoria.id`) was
  accepted. `/explotaciones`: `codigoRega, nombre, id` (default `codigoRega, id`); `/tramites`:
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
  **`completo` (mini-prompt after A2) describes what was WRITTEN (`crotalIndicado`), not what it
  resolved to**: `1234` resolved `EN_INVENTARIO` is `completo: false`. Computed when the response is
  built (`CrotalNormalizador` on `crotalIndicado`, no column/migration); a stored value that fails
  normalization gives `false` plus a `warn` log, never an exception. It lets the frontend paint an
  incomplete `NO_ENCONTRADO` amber without classifying crotales itself (A2 decision 21).
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
  in the order actually checked:** `403` subscription (`puedeAprobarTramites`; since the
  mini-prompt after A2 with a fixed `{motivo}` — `TramiteController.MOTIVO_SUSCRIPCION_NO_PERMITE_APROBAR`,
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
  query locks (review M3); keep the lock read the first load of that Tramite in the request, or
  keep the refresh. Validation exceptions propagate out of the service's `@Transactional` and are
  mapped in `TramiteController` (never caught inside and committed). `ConcurrencyFailureException`
  (lock not acquired, optimistic conflict) and `DataIntegrityViolationException` → `409` "se estaba
  modificando a la vez", never `500`. **Optimistic version (decision 27):** `@Version Long version`
  on `Tramite`, returned in `TramiteResponse`/`TramiteDetalleResponse`. PATCH, aprobar **and,
  since the mini-prompt after A2, rechazar** **require** the `version` the screen showed (`400` if
  missing, `409` if stale, nothing changes). Rechazar's order: `400` no body / `{}` / `version: null`
  (in the controller, before any DB access — same answer for own, foreign or nonexistent) → `404` →
  `409` not `PENDIENTE_REVISION` → `409` stale `version` → `200 RECHAZADO`, `version + 1`
  (`TramiteRechazarRequest`, its own record so a future rejection reason doesn't touch aprobar). **Every accepted write leaves the version at
  exactly read + 1**, including a crotal-only PATCH, a no-op PATCH and the committed re-resolution
  on aprobar (those only touch `tramite_crotal`, so the increment is forced with an explicit JPQL
  `UPDATE … SET version = version + 1` after a flush — not `PESSIMISTIC_FORCE_INCREMENT`, which
  Hibernate 6.6 silently skipped in `@DataJpaTest`). Estado is checked before version on purpose (a
  Tramite no longer pending gets its specific motivo, true with any version). Caveat (7a review M2):
  those raw `EntityManager` calls bypass Spring's exception translation, so a failure there would
  be a `500`, not a `409` — unreachable while the row lock is held.
  **Risks to respect in escalón 2 (modo asistido) (Task 6 review M2):** never open the OVZ.net
  (Playwright) session inside the transaction that holds this row lock (other editors/approvers would
  block for the whole OVZ session, each holding a pooled connection). In escalón 2 the gestor's
  "Enviar" starts one submission at a time, outside that lock.
  On PostgreSQL, if a `statement_timeout` is ever configured, SQLState `57014` becomes Spring's
  `QueryTimeoutException` (not a `ConcurrencyFailureException`) → `500`; a `lock_timeout`
  (`55P03`) does map to `CannotAcquireLock` → `409`. (The lock-timeout `500` seen on H2 is an
  H2+Hikari artifact; tests use `LOCK_TIMEOUT=10000` in the H2 URL, decision 31.)
  **`/error` is public (decision 29):** Spring's error dispatch (malformed JSON, wrong type,
  non-numeric id) runs unauthenticated — `JwtAuthenticationFilter` is a `OncePerRequestFilter`
  and doesn't re-run on the error dispatch — so without `permitAll("/error")` every such `400`
  became a `401`, and the frontend treats a `401` as a logout. `/error` only renders the already
  decided status (Boot defaults: no message, no stack trace) and makes no other route public;
  `/error/x` and every protected route still return `401` without a JWT.

## Explotación search without accents, word by word

- **Explotación search without accents, word by word** (the backend task blocking the pilot,
  2026-10-04; plan `docs/superpowers/plans/2026-10-04-busqueda-explotaciones-sin-tildes.md`, with
  Antonio's adjustments A1–A3 at the top). Same `GET /explotaciones?q=` contract, frontend untouched.
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
    by themselves. The predicate is guarded by a `@DataJpaTest` without `gestoriaFilter`
    (`ExplotacionControllerTest`) — the two-Gestoría E2E tests can't catch its removal, because the
    ambient filter masks it.
  - **Backfill: Flyway Java migration `db.migration.V18__busqueda_normalizada`** (JDBC only, no
    entities): add the columns, fill them in batches with the same `NormalizadorBusqueda`, `SET NOT
    NULL`. **If the normalization rule ever changes, add a new migration that recalculates both
    columns** (V18 won't run again). It reads the whole table into memory on PostgreSQL (fine at
    pilot volume; use `setFetchSize` if the pattern is reused on big tables). Tested with a
    programmatic Flyway on its own H2 (`target 17` → data → migrate, including 1203 rows across
    batch cuts).
  - **No index:** `like '%p%'` doesn't use a B-tree and the query is bounded by `gestoria_id`. If it
    ever gets slow, a PostgreSQL `pg_trgm` GIN index on the normalized columns (no `unaccent`
    needed) — noted for Prompt C.

## Prompt B1 — WhatsApp + AI, the minimum that works

- **Prompt B1 — WhatsApp + AI, the minimum that works** (2026-10-05; plan
  `docs/superpowers/plans/2026-10-05-promptB1-whatsapp-ia.md`, with Antonio's decisions D1–D8 and
  additions A1–A5). B2 (unknown-number replies, several trámites per message, attachments, AI usage
  metering, the real OVZ trámite types, birth registration and the event date) is separate.
  - **Webhook** `POST /webhooks/twilio/whatsapp` (`TwilioWebhookController`): the signature is
    validated with **every** form parameter and the configured public URL
    (`ganera.twilio.webhook-url` / `TWILIO_WEBHOOK_URL`, no query string), never
    `getRequestURL()` (behind a tunnel or proxy it is `http://localhost…`). **Fail-closed:** blank
    token or URL → `503`; missing or bad signature → `403`; then `400` for missing
    `MessageSid`/`From`. Nothing is stored in any of those cases. At startup it logs only the
    host it validates against. `RequestValidator` is still built per request (Twilio footgun).
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
    (Twilio does not retry a 5xx by default). Nobody sees `ERROR_RECEPCION` yet; an alert is noted
    for Prompt C. Stored: `MessageSid`, normalized phone, text, `NumMedia`, result, links; **not**
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
    (OVZ type names since the ficha OVZ, `V20`). **Jackson:** the
    SDK declares 2.19.4 and Boot pins 2.18.2; it works today (the SDK checks it when building the
    client), but a future SDK could require pinning `jackson-bom.version` and would fail on the first
    call, not at startup.
  - **Responses:** `TramiteResponse`/`TramiteDetalleResponse` gained `origen` (`"WHATSAPP"`/null),
    `estadoExtraccion` (`PENDIENTE`/`COMPLETADA`/`FALLIDA`/`SIN_TEXTO`/null; `SIN_TRAMITE` since that task) and
    `crotalesDescartados`. The AI's technical failure reason is never exposed.
  - **Retention** (`RetencionMensajesScheduler`, 03:30 Madrid): deletes messages **without** a
    Tramite after `P30D` and empties the text of those **with** one after `P12M` (not an empty
    `SIN_TEXTO` text). Both periods are configurable and **pending confirmation by Ganera's lawyer**,
    together with the Anthropic and Twilio DPAs (Prompt C notes).
  - **Migration `V19`** (SQL): the `tramite` and `mensaje_campo` columns above; `mensaje_campo.cuerpo`
    lost its `@Lob` (`TEXT`).
  - **For production (Prompt C):** add `logServerErrorDetail=false` to the PostgreSQL URL, so a
    constraint violation can't log `Failing row contains (…)` with message data. (Done in the
    `clever` profile, see the next bullet.)

## Ficha lista para OVZ — escalón 1 de OVZ

- **Ficha lista para OVZ — escalón 1 de OVZ** (2026-10-07; analysis of the OVZNET manual in
  `docs/referencias/ovz-tramites-bovino.md`, the PDF itself is git-ignored; plan
  `docs/superpowers/plans/2026-10-06-ficha-ovz.md`, decisions D1–D8 and the approved T5 shape). Ganera
  shows an approved trámite's data in the order of the OVZ form, with Copiar on each field, so the gestor
  pastes it into OVZ by hand. Ganera still writes nothing to OVZ (until escalón 2 (modo asistido), see
  Operational flow step 7).
  - **`TipoTramite` = the OVZ forms** (`V20`, SQL): `ALTA_NACIMIENTO`, `BAJA_MUERTE`,
    `SOLICITUD_MOVIMIENTO`, `CONFIRMACION_MOVIMIENTO`, `DECLARACION_CENSO`, `DEMORA_CROTALIZACION`
    (supersedes A1 decision 18's list). `tipo_tramite` widened to `VARCHAR(30)`; every old `MOVIMIENTO`
    became `SOLICITUD_MOVIMIENTO` (an old one can't be told apart from an entrada). Migration tested on H2
    and embedded PostgreSQL. An old value in `PATCH` is a `400`. The AI's `TipoExtraido`/prompt and the
    frontend `TIPOS_TRAMITE` changed in the same commit; backend and frontend must deploy together.
  - **`TramiteDetalleResponse` gained `ganaderoNombre`/`ganaderoNif`** (the account to log into OVZ with;
    null without explotación). Never `ovzUsuario`/`ovzPasswordCifrada`:
    `CredencialesOvzDetalleEndToEndTest` (invented password and key) checks neither the plain password
    nor the ciphertext appears in GET/PATCH bodies or logs. **RESOLVED (M2, 2026-11-09,
    `feat/ovz-descifrado-bajo-demanda`):** loading the Ganadero used to decrypt its OVZ password, so a corrupt
    ciphertext or a changed `ENCRYPTION_KEY` gave `500` on the trámite detail and on `/ganaderos`. The field no
    longer has `@Convert` (`EncryptedStringConverter` was deleted): the ciphertext stays at rest in the entity,
    those endpoints answer `200`, and the password is decrypted only on demand by `DescifradoCredencialOvz`
    (see the "Auditoría" bullet). Same column format, no migration.
  - **Frontend:** `features/tramites/fichaOvz.ts` is the single source of each OVZ form's fields, order,
    hints and deadlines (pure, tested), plus `modoFicha` (D4: only `APROBADO` copies; pending is a preview
    without copy buttons). `/tramites/:id/ovz` (`FichaOvzPage`) lives inside `RequireAuth` but outside
    `AppLayout` (no nav bar). The review dialog opens it with `abrirFichaOvz` (`window.open` with a fixed
    window name and **no `noopener`**, so the new window inherits the `sessionStorage` token; the page sets
    `window.opener = null`). A tab opened without opener (middle click, pasted URL) goes to login and back.
    Verified in real Chromium and Firefox. `shared/ui/BotonCopiar` (Clipboard API, read-only `Input`
    fallback, polite live region, always reads "Copiar" for WCAG 2.5.3). See `DESIGN.md` → "Ficha para OVZ".
  - **Next, before the pilot:** the "sesión de mapeo de OVZ" in `ganera-prompts.md` (OVZ value lists and
    gestoría preferences) before B2. (D5, marking a trámite as done in OVZ, became "Hecho en OVZ", below.)

## Hecho en OVZ — D5 of the ficha OVZ

- **Hecho en OVZ — D5 of the ficha OVZ** (2026-10-07; plan `docs/superpowers/plans/2026-10-07-hecho-en-ovz.md`,
  decisions closed with Antonio). `APROBADO` only means "data reviewed"; the gestor now records that **they**
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
  - **Known limitations:** a ficha window that kept an older session (someone else logged in later in the
    main window) marks with the previous Usuario — same Gestoría, nothing leaks. The FK does not enforce
    that the Usuario belongs to the Tramite's Gestoría (only the service does; hand-seeded data could show
    a foreign name or a `500`). A `CHECK` violation would surface as the generic concurrency `409`
    (unreachable today). The FK has no `ON DELETE`: deleting a Usuario who marked something would fail
    (there is no way to delete Usuarios today). An inactive Usuario with a still-valid JWT can mark, like
    every other endpoint.

## B2 reducido — the gestoría replies to the Contacto by WhatsApp

- **B2 reducido — the gestoría replies to the Contacto by WhatsApp** (2026-10-11; plan
  `docs/superpowers/plans/2026-10-11-promptB2-reducido-responder-whatsapp.md`, "Decisiones de Antonio" at the top
  prevails). Always manual: the gestor sees and edits the text, nothing is ever sent by itself. The Twilio
  status callback (delivered/read/failed later) came in its own commit, right after (see the next sub-bullet
  "Status callback").
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
  - **Known limitations:** the window is per Contacto, not per phone number (changing a contact's phone could give
    a 63016 `FALLIDO`); a DB failure right after Twilio accepted gives `500` and leaves `ENVIANDO` (shown as
    `SIN_CONFIRMAR` after 2 minutes; the same key never resends) — and since the Twilio SID was never stored, the
    status callback cannot reconcile that row: it stays `SIN_CONFIRMAR`; a farmer's reply ("vale, gracias") still creates a
    trámite (since SIN_TRAMITE it can be discarded with one click; see that bullet).
  - **Status callback** (2026-10-11, its own commit; plan `docs/superpowers/plans/2026-10-11-b2-callback-estado-twilio.md`,
    backend only). If `TWILIO_STATUS_CALLBACK_URL` (`ganera.twilio.status-callback-url`) is a valid http(s) URL with a
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
    to `ContactoRepository.findByTelefono`; there is no finder by SID. Logs: only SID, states and code.
    **Known limitations:** a callback that arrives before the send stored the SID (Twilio may report before step 3
    commits) gets `200` with no change and an `INFO` log with only the SID — **if it happens in the pilot, add a
    status query to Twilio by SID**; with one `UPDATE` and no read, the log can't tell an unknown SID from a row
    already in that or a later state, nor give the exact previous state (it logs the set of possible ones);
    `SIN_CONFIRMAR`/`ENVIANDO` rows with a SID don't exist with today's code (those transitions are tested on
    seeded rows); a repeated form parameter is signed with its first value (same as the inbound webhook).

## SIN_TRAMITE — messages that ask for nothing, and «Descartar»

- **SIN_TRAMITE — messages that ask for nothing, and «Descartar»** (2026-10-12; plan
  `docs/superpowers/plans/2026-10-12-sin-tramite.md`, Antonio's decisions A1–A6 at the top prevail). Since B1 every
  message of an active Contacto creates a Tramite, so a «vale, gracias» reached the queue.
  - **AI:** `TipoExtraido.SIN_TRAMITE` (distinct from `NO_IDENTIFICADO`: «asks for something, unclear what»); prompt
    with an asymmetric rule («ante la duda, NO_IDENTIFICADO»; a message that mentions an animal, crotal, calf, death,
    sale, move, purchase or the census, or asks the gestoría to note/do something, is never SIN_TRAMITE). It is **not**
    a `TipoTramite`: it becomes `EstadoExtraccion.SIN_TRAMITE` (no migration: `estado_extraccion` has no `CHECK`), and
    the Tramite stays `PENDIENTE_REVISION` with `tipo = null` — **nothing is ever discarded by itself**. Server safety
    net in `ExtraccionValidador`: SIN_TRAMITE with **any** raw crotal (even an invalid one) or with a tipo → treated as
    a normal result (`COMPLETADA`).
  - **Evaluation against the real Haiku:** `ExtraccionSinTramiteEvalTest`, skipped unless `GANERA_EVAL_IA=true` **and**
    `ANTHROPIC_API_KEY` are set (never in CI or a normal `./mvnw clean test`); 13 invented messages × 3 passes, report in
    `backend/target/eval-sin-tramite.md`; fails on any false SIN_TRAMITE **or any FALLO** (an all-FALLO run used to look
    green). **Only Antonio runs it**, in his own terminal.
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
    the contract changes on purpose, regenerate it from `backend/target/contrato-real/`.
  - **Known:** the B1 acknowledgement («Recibido, tu gestoría lo revisará.») still goes out for a «vale, gracias» (the
    AI decides after answering Twilio); the text of a discarded message is kept 12 months like any other — question for
    Ganera's lawyer (`progress.md`); a reply being sent while someone discards the Tramite isn't locked against it (B2
    design; tiny window).

## Clever deployment hardening

- **Clever deployment hardening** (2026-10-14; plan `docs/superpowers/plans/2026-10-14-despliegue-clever.md`, decisions
  D1–D6 at the top: `app.ganera.es` static frontend + `api.ganera.es` backend, PostgreSQL XXS Small in Paris, the first
  base is for the demo only — a new one before the first real data). Since 2026-10-19 the database is **Supabase**
  (Ireland), not the Clever add-on, and "a new base" means emptying the `ganera` schema and rerunning script 01 (S7);
  the frontend will move to Vercel (separate task). See the "Supabase" bullet; the plan of 14/10 marks each change.
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
    `ganera-logo-rojo.svg`; **no service worker**, no offline mode or data cache (`pwa.test.ts`). Real Chromium 151 and
    Edge 155 report no installability errors. In an installed window the session (`sessionStorage`) ends when the window
    closes.
  - **Deployment kit** outside the repo in `C:\Users\Antonio\Desktop\ganera-clever\` (replaces
    `Archivos-Ganera\ganera-demo`): `alta-gestorias.sh`, two invented Excel files, `seed-tramites.sql` (8 trámites per
    gestoría with OVZ types, one `HECHO_EN_OVZ`, one discardable `SIN_TRAMITE`), `smoke.sh` (41 checks against Clever;
    recovers the state a demo leaves). Rehearsed against embedded PostgreSQL 16 with the `clever` profile. Updated for
    Supabase on 2026-10-20: `LEEME.md` uses Supabase's SQL editor instead of Adminer (script 01, passwords with `psql`
    17 `\password`), `seed-tramites.sql` starts with `SET ROLE ganera_propietario; SET search_path TO ganera;`, and
    `prueba-supabase.sh` (step 5 of the Supabase smoke) is new; rehearsed against embedded PostgreSQL 17 imitating
    Supabase (see the "Supabase" bullet).

## Supabase — the production database

- **Supabase — the production database** (2026-10-19/20; plan `docs/superpowers/plans/2026-10-19-migracion-supabase.md`,
  Antonio's decisions S1–S7 at the top prevail). Supabase replaces **only** the database (project in **eu-west-1,
  Ireland**, **PostgreSQL 17.11**, Free plan, UTC, **Data API disabled**); the backend stays on Clever (Paris) and the
  frontend will go to Vercel (separate task). HSTS, CORS fail-closed, no default user and the PWA don't change. The audit
  trail (trigger, cross-Gestoría `403`, IP) is the next task.
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
    verifies it. The real CA goes in `src/main/resources/supabase/ca-raiz.crt` (Antonio downloaded it on 2026-10-20, T0:
    *Supabase Root 2021 CA*, valid until April 2031); `src/test/resources/supabase/ca-raiz.crt` is a test-only CA that shadows it on the test classpath, with the
    test server certificates in `src/test/resources/ssl-pruebas/` (`localhost` only, 100 years, `LEEME.md`).
    `CaSupabaseTest` reads the main one **from disk** (an X.509 CA, valid, Supabase's subject, not the test one) — it
    would fail if the file were missing or were the test CA.
  - **Seven variables, no defaults:** `SUPABASE_DB_HOST/PORT/NAME/USER/PASSWORD`, `SUPABASE_FLYWAY_USER/PASSWORD` (in
    `.env.example`, commented). `ConexionBaseDeDatosEstricta` (`EnvironmentPostProcessor` in `shared/basedatos`, registered
    in `META-INF/spring.factories`, runs before any bean in **every** startup, not only `clever`) resolves
    `spring.datasource.url/username/password` and `spring.flyway.url/user/password` strictly and fails, naming the
    property and the variable but never a value, if a variable is missing **or defined but blank**. Why: Boot's binder
    leaves an unresolved `${…}` as text, and an empty host turns into `localhost` in pgjdbc — with `trust` auth (the
    embedded test server) a missing password would even connect. Side effect in local: `DATABASE_PASSWORD=` (defined,
    empty) also stops startup, even though `application.yml` has `${DATABASE_PASSWORD:ganera}`; `${X:}` with `X` unset
    is fine. **Never define `GANERA_*`, `DATABASE_*`, `SPRING_DATASOURCE_*` or `SPRING_FLYWAY_*` on Clever** (relaxed
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
    exact policies, privileges of `PUBLIC`/`anon`/`authenticated`/`service_role`/`authenticator` on schema, tables,
    columns, views, sequences, functions, `ganera_app`'s exact privileges, role memberships and attributes, the
    database's `TimeZone=UTC`); **0 rows = correct**. The tests read both from disk: one source.
  - **Callback `db/postgresql/afterMigrate__seguridad_esquema.sql`** (S1): runs after **every** `migrate` on
    `${flyway:defaultSchema}`, idempotent, over **all** tables (future ones too): `ENABLE ROW LEVEL SECURITY` only if
    missing (no `FORCE`: the owner doesn't obey it; re-enabling took an `ACCESS EXCLUSIVE` lock on every start),
    `REVOKE ALL … CASCADE` from `PUBLIC, anon, authenticated, service_role` and `ganera_app`, then for `ganera_app`
    `SELECT, INSERT, UPDATE, DELETE` + policy `ganera_app_todo` (`FOR ALL TO ganera_app USING (true) WITH CHECK
    (true)`, created only if missing in `pg_policies`; tenant separation is still the app's job, RLS keeps every other
    role out). The **"insert-only" list** (`solo_insercion`, today only the reserved name `auditoria` for the coming
    audit table; the **same** list in 02 — rename both together) gets `SELECT, INSERT` and two policies, never `UPDATE`/
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
    `admin_supabase` and gets script 01 run **as `admin_supabase`**. `SeguridadEsquemaPostgresTest`,
    `PreparacionBaseDeDatosPostgresTest`, `BaseDeDatosPerfilCleverPostgresTest` (each variable missing or blank,
    `127.0.0.1` vs the `localhost` certificate, no schema, and a real flow as `ganera_app` with RLS),
    `ConfiguracionBaseDeDatosProduccionTest` and `ConfiguracionFlywayTest` (read the YAMLs from disk),
    `FlywayH2VendorTest`. Older PostgreSQL tests still connect as superuser to `public`; the callback protects them too.
  - **`docker-compose`:** `postgres:17-alpine` with script 01 mounted in `/docker-entrypoint-initdb.d/` (without those
    roles the callback won't let Flyway migrate). It only runs when the volume is created: with an old volume,
    `docker-compose down -v` (or run the script by hand). The image's user `ganera` is a **superuser** and, because the schema is
    also called `ganera`, `search_path = "$user", public` makes the local app migrate into `ganera` as that superuser —
    it works and the callback protects it, but it doesn't reproduce Supabase's role split (T2 review M6). Not tested
    here (no Docker).
  - **Known limitations (from the reviews):** the callback doesn't revoke anything from `authenticator` (it may not
    exist outside Supabase; by default it gets nothing in `ganera`, and 02 detects a hand-made grant, but migrating
    doesn't undo it — Antonio's decision: fixed in the audit task, with a conditional `REVOKE`); since 2026-10-20 02 also
    follows **role chains** (T3 second review N1): an outsider role that can become some role R (itself, or through a
    `pg_has_role(…, 'SET')` chain) which inherits a `ganera_*` role (`'USAGE'`), can `SET ROLE` to it or has ADMIN on it
    (direct or inherited) shows as «llega por una cadena de roles» — not repeated when a direct clause already flags
    it; a direct membership without options doesn't hide the chain. So a grant to `anon`, `authenticated` or
    `service_role` also shows a row for `authenticator`, which can become any of them (and since `pg_has_role` is true
    for a superuser, being able to become one shows too); the CA validation is only pinned by the URL text (swapping the factory for
    `NonValidatingFactory` keeps the `127.0.0.1` test green; the exact-URL tests catch it — M2); Spring's chained cause
    for a missing variable carries the property's **unresolved** raw value (a secret would only show if a variable's
    value itself contained `${…}` — M4). The real pooler (`rol.<ref>` users), the real CA and `ALTER DATABASE` as
    Supabase's `postgres` can only be checked by the real smoke (T5).
