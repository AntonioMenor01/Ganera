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
  differentiation yet — any Usuario can operate anywhere within their own Gestoría. There is no
  public self-signup: every Gestoría + its first Usuario is created by hand (see Current status).
- **Ganadero** — a Gestoría's client. **Never logs in.** Holds the OVZ.net credentials
  (`ovzUsuario` / `ovzPasswordCifrada`) — one login per Ganadero, shared across all their
  Explotaciones (not per-Explotación).
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
- Ganaderos and Contactos never authenticate. Only Usuarios have logins.
- A Gestoría in `TRIAL_EXPIRADO_SIN_PAGO` or `SUSPENDIDA` can never approve trámites (read-only) —
  gated by `SuscripcionService.puedeAprobarTramites(gestoriaId)`. Fail-closed: no `Suscripcion` row
  for a Gestoría also means no approval, not a free pass.
- `POST /auth/login` never reveals *why* it rejected a login (unknown email, wrong password, or
  inactive Usuario all return a plain 401) — don't add a more specific error message.

## Technical decisions already made

- **Multi-tenancy**: row-level via `gestoria_id` + Hibernate Filters (`GestoriaScopedEntity`,
  filter name `gestoriaFilter`), not schema-per-tenant. The filter is activated per-request in
  `TenantFilterActivationInterceptor` from the JWT's `gestoriaId` claim — it relies on
  `spring.jpa.open-in-view=true`. Public endpoints (webhooks) have no `Authentication`, so the
  filter is simply never enabled for them — that's intentional, not a gap.
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
  already had stored (`TRIAL` → `TRIAL_EXPIRADO_SIN_PAGO`, anything else → `IMPAGO_GRACIA`). A daily
  `@Scheduled` job (`SuscripcionSyncScheduler`, 3am) reconciles `explotacionesContratadas` against
  the live Explotación count for every `ACTIVA`/`IMPAGO_GRACIA` subscription and pushes quantity
  changes to Stripe (default proration).
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
- Public paths: `/webhooks/**`, `/auth/login`, and `/internal/**` (the last one only at the Spring
  Security layer — `/internal/onboarding/gestoria` still gates itself on the `X-Internal-Secret`
  header inside `OnboardingController`). Everything else requires a valid JWT.
- **`OnboardingController` (`POST /internal/onboarding/gestoria`) is a temporary bootstrap, not the
  final design.** There's no public self-signup yet, so every pilot Gestoría + its first Usuario is
  created by hitting this endpoint by hand, guarded by a shared secret (`ONBOARDING_SECRET`,
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
- `StripeWebhookService` is package-private (`facturacion` only) and split so the actual state-machine
  decisions (`procesarCheckoutCompletado`, `procesarPagoExitoso`, `procesarSuscripcionActualizada`,
  and the static `calcularEstadoTrasActualizacion`) are plain methods testable without faking Stripe
  SDK deserialization — only the `manejarXxx` methods touch `Event`/`Session`/`Invoice`/`Subscription`
  directly. `StripeConfig` sets the static `Stripe.apiKey` once at startup from `STRIPE_API_KEY`
  (blank is tolerated at startup, same reasoning as the Twilio fix above — it only fails when a
  Stripe call is actually made).

## Architecture notes (frontend)

Vite + React 19 + TypeScript + Tailwind v4 (`@tailwindcss/vite` plugin, no `tailwind.config.js`) +
shadcn/ui (initialized with the "Nova" preset: Radix, Lucide, Geist font). Path alias `@/*` →
`src/*`. Structure is by feature (`features/tramites`, `features/ganaderos`,
`features/explotaciones`, `features/facturacion`), plus `shared/api/httpClient.ts` (axios instance
that attaches the JWT from `localStorage` on every request) and `shared/layout/AppLayout.tsx` +
`router.tsx` (react-router-dom, default route redirects to `/tramites`).

## Commands

Backend (from `backend/`):
- `mvn compile` — compile
- `mvn spring-boot:run` — run locally (Spring Boot does **not** auto-load `.env` — export the
  variables from `.env.example` into the shell/IDE run config yourself, or run via
  `docker-compose` where Postgres is provided but the app itself still needs its own env vars set)
- `mvn test` — runs the test suite (`SuscripcionServiceTest`, `AuthServiceTest`,
  `StripeWebhookServiceTest`); a single test: `mvn test -Dtest=AuthServiceTest`
- Playwright browsers are already installed locally. If they need reinstalling elsewhere (no
  `exec-maven-plugin` is configured in `pom.xml`, so `mvn exec:java` won't work out of the box):
  `mvn dependency:build-classpath -Dmdep.outputFile=cp.txt` then
  `java -cp "target/classes;$(cat cp.txt)" com.microsoft.playwright.CLI install`.
- **No Docker/Postgres in this sandbox** — to actually boot the app for a manual smoke test without
  a real Postgres, run against an in-memory H2 (PostgreSQL compatibility mode), skip Flyway, and let
  Hibernate create the schema:
  `mvn spring-boot:run -Dspring-boot.run.useTestClasspath=true -Dspring-boot.run.arguments='--spring.datasource.url=jdbc:h2:mem:ganera;MODE=PostgreSQL;DB_CLOSE_DELAY=-1 --spring.datasource.driver-class-name=org.h2.Driver --spring.datasource.username=sa --spring.datasource.password= --spring.flyway.enabled=false --spring.jpa.hibernate.ddl-auto=update'`
  (`useTestClasspath=true` is required — `h2` is a test-scope dependency, not on the runtime
  classpath otherwise). This is how the Twilio footgun above was actually caught — `mvn compile`
  alone never would have. Still export `JWT_SECRET`/`ENCRYPTION_KEY`/`ONBOARDING_SECRET` first.
  **On Windows, killing this process needs `taskkill`/`Stop-Process` on the child `java.exe` PID** —
  stopping the wrapping shell/task does not kill the spawned JVM.

Frontend (from `frontend/`):
- `npm install`
- `npm run dev` — dev server
- `npm run build` — `tsc -b && vite build`
- `npm run lint` — oxlint
- `npm run preview`

Root:
- `docker-compose up -d` — Postgres only (the Spring Boot app runs outside Docker for now).

## Current status

Steps 1, 2, 2.5, and 2.7 are complete: infrastructure scaffold, the remaining data model
(`EstadoSuscripcion`'s final 6 states, `SuscripcionService.puedeAprobarTramites`,
`Gestoria.modoCartera` + `usuario_explotacion`), real Usuario authentication (`POST /auth/login`,
`GET /auth/me`) plus manual pilot onboarding (`POST /internal/onboarding/gestoria`, temporary — see
Architecture notes), and the full Stripe integration (`POST /facturacion/checkout`, the
`StripeWebhookService` state machine, and the nightly quantity-sync job). Backend and frontend
compile/build cleanly, backend tests pass (verified once by actually booting the app — see the H2
smoke-test command above — not just by compiling). No business logic is wired end-to-end yet for
trámites — Twilio's webhook validates signatures and persists raw data, but doesn't yet trigger AI
extraction or trámite creation; `TramiteExtractionService`'s system prompt and
`OvzAutomationService`'s Playwright logic are still unimplemented skeletons; `puedeAprobarTramites`
isn't called from anywhere yet; portfolio filtering by `modoCartera` isn't implemented; there is
still no public self-signup or admin panel, only the manual onboarding endpoint;
`STRIPE_PRICE_ID_EXPLOTACION` is still an empty placeholder until a real `Price` is created in the
Stripe dashboard. `ganera-prompts.md` at the repo root tracks the full sequence of prompts used to
build this out, in order — check it for the detailed history/rationale behind any given step.
