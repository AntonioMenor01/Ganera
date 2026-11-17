# Historial por prompt (antiguo «Current status»)

Historia prompt-a-prompt de la construccion, movida desde `CLAUDE.md`. Es registro historico; el estado actual y las reglas vigentes estan en `CLAUDE.md`.


## Current status — historial por prompt

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
step. (At that point no AI/WhatsApp business logic was wired; Prompt B1 wired it on 2026-10-05,
see below.) `OvzAutomationService`'s Playwright logic is still an unimplemented skeleton;
portfolio filtering by `modoCartera` isn't implemented; there is still no admin panel (public
self-registration was added later, see below).

Prompt 3d — Excel inventory importer + basic REST controllers — is also complete, implemented and
verified end-to-end on 2026-07-14 (no separate written plan; the prompt itself, refined through a
few upfront clarifying questions on schema gaps, served as the spec): migration `V14` adds
`Ganadero.nif` (globally unique); `POST /explotaciones/importar`, `GET /explotaciones`,
`GET /tramites`, `POST /tramites/{id}/aprobar`, `POST /tramites/{id}/rechazar` (see the Excel
importer bullet under Technical decisions for the full design). This is the **first real caller of
`SuscripcionService.puedeAprobarTramites`** (via `/aprobar`, `403` if it returns false) — though
`/aprobar` and `/rechazar` only flip `EstadoTramite` in the DB, with no `OvzAutomationService` call
(approving never sends: that's the gestor's "Enviar" in escalón 2 (modo asistido)). Full backend suite
green at 91 tests (79 + 12 new); real end-to-end HTTP smoke test
(H2 + all 14 migrations, onboarding → login → import a real fixture `.xlsx` → list → re-import same
file and confirm no duplication → 401 without JWT → 404 on an unknown trámite id) — not just the
`@DataJpaTest`-level suite.

Prompt 4 — functional frontend, **only the part not blocked by OVZ.net** — is complete as of
2026-07-14: login (JWT in-memory, see Architecture notes), Explotaciones dashboard with the Excel
importer given real UI prominence, Trámites queue with estado filter + review modal (handles a null
WhatsApp message/unresolved Explotación without looking broken, since 3b hasn't shipped yet) +
Aprobar/Rechazar (a `403` on Aprobar shows the real "tu suscripción no permite aprobar trámites"
reason, never a generic error), and a Facturación status page. Deliberately **not** built (per
explicit scope): the screen to store OVZ credentials (encrypted), deferred to escalón 2; whether they
belong to the gestoría or to each ganadero is decided after the OVZ mapping — and any manual
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

**Prompt A1 — Contactos, crotales in trámites and editable review (backend only) — is complete**
(2026-09-25 → 2026-09-28), following
`docs/superpowers/plans/2026-09-25-promptA1-contactos-crotales-revision.md` (31 closed decisions)
with the Superpowers flow: one implementer subagent + one independent reviewer subagent per task,
briefs/reports/reviews in `.superpowers/sdd/a1-*`. See the "Prompt A1" bullet under Technical
decisions for the full design: Contacto tenant-scoped with role on the relation and logical
delete, `/contactos` CRUD, `/ganaderos`, `/explotaciones/{id}/animales`, the importer's optional
"Contactos" sheet, `tramite_crotal` + `CrotalNormalizador`, `PATCH /tramites/{id}`, the full
approval rule (provisional crotal format, decision 28), optimistic `version`, `sort` whitelists
on every listing, public `/error`. Migrations `V15`–`V17`. Full backend suite green at **415
tests** (`./mvnw clean test`), plus a real HTTP smoke test on a file-backed H2 (onboarding → login
→ import with a Contactos sheet → `GET /ganaderos/{id}` with contactos and rol → `GET /contactos` +
`?sort=noExiste` `400` → a Tramite seeded by SQL → `aprobar` without version `400` / with it `409`
"Falta asignar la explotación. Falta el tipo de trámite." → `PATCH` `200` (`version` 0 → 1, one
crotal `EN_INVENTARIO` by its last digits, one full crotal `NO_ENCONTRADO`) → stale-version PATCH
and `aprobar` `409` → `aprobar` with the new version `200 APROBADO` (`version` 2) → malformed body
`400`, not `401`). Committed as `2538583` (plus `880d3ca`, which removed the last unscoped
Animal finder). `TipoTramite` was deliberately left unchanged then (replaced by the OVZ types in the ficha OVZ, `V20`; see
`ganera-prompts.md`).

**Prompt A2 — frontend for A1 (Ganaderos, animales, editable review) — is complete**
(2026-09-28 → 2026-10-02), following `docs/superpowers/plans/2026-09-28-promptA2-frontend-revision.md`
(31 decisions) with the Superpowers flow (reports in `.superpowers/sdd/a2-*`, not committed) and
the Impeccable skill for every design pass, run from the main session (critique of the queue,
craft of Ganaderos and the review dialog, audit + polish at the end; direction contracts in
`.impeccable/surfaces/`). **Frontend only — nothing in `backend/` changed.** Delivered: Vitest/MSW
test tooling, the single HTTP error model, the session in `sessionStorage`, the brand mark
(`LogoGanera`, `index.html` in Spanish with `favicon-64.png`), labels/badges from `etiquetas.ts`,
the queue (filtered by pending, keyboard-accessible rows, REGA column, crotales), Ganaderos list +
detail, animales per explotación, and the editable review dialog with `version` (see the frontend
architecture notes). `DESIGN.md` was brought up to date with the new patterns. Verified with
`npm test` 475/475, `npm run build` and `npm run lint` clean (known warnings only), `./mvnw clean
test` 415/415, and a real-browser smoke (Playwright from npm outside the repo, Vite dev server,
backend on a file-backed H2) with two Gestorías: login + reload keeps the session → Excel import
from the UI with a Contactos sheet (re-import doesn't duplicate) → Ganaderos → detail with
contactos and `tel:` links → animales paginated → queue filtered by pending → PATCH with `version`
(crotales resolved `EN_INVENTARIO`) → aprobar with `version` `200` → aprobar without tipo `409` with
its `motivo` → a stale-version `409` reloads the fresh data and shows the `motivo` → rechazar with
inline confirmation → read-only `APROBADO` → Facturación; Gestoría B (subscription `SUSPENDIDA`,
375 px): banner, only its own trámite in the queue, aprobar `403` with the subscription text and no
logout, `/ganaderos/{A's id}` → "Ganadero no encontrado", Salir clears the token. Known leftovers
(none blocking): the nav bar overflows at 375 px (fixed in the frontend task before the pilot), the 622 kB
chunk, the import summary card says "1 filas"/"1 actualizadas" (the announced sentence already
uses real plurals), and the minors m1–m4 of the Task 10 review.

**Mini-prompt of backend after A2 — complete** (2026-10-02), following
`docs/superpowers/plans/2026-10-02-mini-prompt-backend-tras-a2.md` (decisions closed with Antonio
before starting) with the Superpowers flow (T1–T4, one implementer + one independent reviewer each,
all **Approved** with minors only; reports in `.superpowers/sdd/mp-*`, not committed). See the
"Mini-prompt of backend after A2" bullet under Technical decisions: `GET /explotaciones/{id}`,
`?q=`, `403` with `motivo`, `version` required on rechazar (plus the one-line frontend change so
Rechazar keeps working — the only frontend change), `explotacionCodigoRega`/`explotacionNombre`
in the trámites list without N+1, the importer's and the registration's `400` as `{motivo}`, and
`completo` in each crotal. No migrations. Verified with `./mvnw clean test` **472/472** (471 + the N+1 fix's test), `npm test`
**476/476**, `npm run build` and `npm run lint` clean (known warnings only), and a real `curl`
smoke against two Gestorías on a file-backed H2 (log in `.superpowers/sdd/mp-t5-smoke.md`; run
before the N+1 fix, which is covered by its own test). Smoke
tip: Git Bash's `curl -d '…'` garbles non-ASCII characters on Windows — send JSON bodies with
`--data-binary @file`.

**Frontend task before the pilot — complete** (2026-10-03 → 2026-10-04), following
`docs/superpowers/plans/2026-10-03-tarea-frontend-antes-piloto.md` (decisions closed with Antonio)
with the Superpowers flow (T1–T5, one implementer + one independent reviewer each, all **Approved**
with minors; reports in `.superpowers/sdd/fp-*`, not committed) and Impeccable for the nav bar
(shape approved by Antonio before the craft; brief in
`.impeccable/surfaces/frontend-src-shared-layout-applayout-tsx.md`). **Frontend only.** Delivered:
mobile nav bar (two rows below `md`, scrolling link strip, no page-level horizontal scroll), the
importer card's singulars, the review combobox on `GET /explotaciones?q=` (complete list deleted),
the queue's REGA from `explotacionCodigoRega`, the amber incomplete `NO_ENCONTRADO` from
`completo`, the `403` `motivo` (already worked; now pinned by tests) and `errores.ts` comments.
Verified with `npm test` **506/506**, `npm run build` and `npm run lint` clean (known warnings
only), `./mvnw clean test` **472/472**, and a real-browser smoke with two Gestorías on a
file-backed H2 at 1440/768/640/375 px (log in `.superpowers/sdd/fp-t6-smoke.md`): no page-level
horizontal scroll anywhere, active link visible and `scrollY` stable at 375 px, no request to
`/explotaciones` from the queue, combobox search/400/500/D3d, approve from 375 px, Gestoría B's
`403` with the backend's `motivo`. The smoke's N1 (keyboard focus landing on a nav link clipped by
the scrolled strip) was fixed before the commit and checked at 375 px in a real browser.
Non-blocking leftovers: "Reintentar" inside the combobox isn't reachable by Tab, and the combobox
list empties while searching (height jump).

**Backend task blocking the pilot — explotación search without accents, word by word — complete**
(2026-10-04), following `docs/superpowers/plans/2026-10-04-busqueda-explotaciones-sin-tildes.md`
with the Superpowers flow (T1–T3, one implementer + one independent reviewer each, all **Approved
with minors**, minors fixed; reports in `.superpowers/sdd/bt-*`, not committed). **Backend only.**
See the "Explotación search without accents" bullet under Technical decisions. Migration `V18`
(Java). Verified with `./mvnw clean test` **533/533**, `npm test` **506/506** (frontend untouched),
and a `curl` smoke with two Gestorías on a file-backed H2 in two phases — `main`'s backend imported
the data at V17, then the new backend applied V18 to that same database and the searches ran
against the backfilled rows (log in `.superpowers/sdd/bt-t4-smoke.md`). The only frontend change,
by Antonio's explicit exception, is the JSDoc of `frontend/src/features/explotaciones/api.ts` (a
comment, no code); `npm test` 506/506 and `npm run lint` (known warnings only) after it.

**Billing moved out of the app — complete** (2026-10-04), following
`docs/superpowers/plans/2026-10-04-quitar-pago-de-la-app.md` (inventory "se quita / se queda" and
decisions closed with Antonio) with the Superpowers flow (T1 backend, T2 frontend, one implementer +
one independent reviewer each, both **Approved with minors**, minors fixed; reports in
`.superpowers/sdd/qp-*`, not committed). **Backend and frontend in the same commit** (a contract
changes). See "Billing moved out of the app" under Technical decisions. Verified with
`./mvnw clean test` **534/534**, `npm test` **508/508**, `npm run build` and `npm run lint` (known
warnings only), and a real-browser smoke with two Gestorías (A `ACTIVA`, B `SUSPENDIDA` by SQL) at
1440 and 375 px (log in `.superpowers/sdd/qp-t3-smoke.md`). **New order of work** (in
`ganera-prompts.md`): brand colours and typography → Prompt B (with the birth registration: mother's
crotal, sex and date of birth) → Prompt C.

**Brand colours and typography — complete** (2026-10-05), following
`docs/superpowers/plans/2026-10-04-colores-y-tipografia.md` (Impeccable shape approved by Antonio on
2026-10-04, craft from the main session; Superpowers flow T1 tokens + font, T2 classes, T3 logo
assets, each with an implementer and an independent reviewer: T1 and T2 **Approved with minors**,
minors fixed; T3 **Approved**; then T5, Antonio's table-link decision (`CLASE_ENLACE_TABLA`), **Approved with
minors**, minors fixed; reports in `.superpowers/sdd/ct-*`, not committed). **Frontend,
`DESIGN.md`, `PRODUCT.md` (by Antonio's explicit exception) and the Impeccable surface briefs.**
Verified with `npm test` **513/513**, `npm run build` and `npm run lint` (known warnings only),
`./mvnw clean test` **534/534**, `impeccable detect` with no findings, and a real-browser smoke at
1440 and 375 px with two Gestorías (log in `.superpowers/sdd/ct-t4-smoke.md`; screenshots outside the
repo in `C:\Users\Antonio\Desktop\capturas-ganera-marca\`). Next in the order of work: Prompt B.

**Prompt B1 — WhatsApp + AI, the minimum that works — complete** (2026-10-05), following
`docs/superpowers/plans/2026-10-05-promptB1-whatsapp-ia.md` with the Superpowers flow (T0 the three
defects first — signature, fail-closed, native structured output — then T1 reception, T2 background
extraction, T3 responses and retention, T4 the minimal frontend; one implementer + one independent
reviewer each, all **Approved with minors**, minors fixed; reports in `.superpowers/sdd/b1-*`, not
committed) and Impeccable for T4 (shape approved by Antonio; real-browser pass from the main
session). **Backend and the minimal frontend in the same commit** (Antonio's explicit exception).
See the "Prompt B1" bullet under Technical decisions. Migration `V19`. Verified with
`./mvnw clean test` **676/676**, `npm test` **558/558** (37 files), `npm run build` and
`npm run lint` (known warnings only), and a **real smoke with the Twilio WhatsApp sandbox from
Antonio's phone** (Cloudflare quick tunnel, file-backed H2, invented data, two Gestorías; log in
`.superpowers/sdd/b1-t5-smoke.md`): valid message → acknowledgement on the phone + Tramite only in
A, extracted by the real Haiku (BAJA, crotal resolved to A's animal and not to B's with the same last
digits); duplicate `MessageSid` → `<Response/>`, nothing new; AI failure (invalid key in that
process only) → acknowledgement + `FALLIDA` with the notice, then the 5-minute retry with the real
key completed it; unknown number → no reply, stored as `NUMERO_DESCONOCIDO`. The smoke also caught
a misconfigured `TWILIO_AUTH_TOKEN` (an Anthropic key in it): the webhook failed closed with `403`,
as designed. Tunnel closed and checked (Cloudflare 530/1033), H2 and every smoke file deleted. Next:
B2 (see `ganera-prompts.md`).

**C mínimo — demo deployed in the EU — code done, deployment pending** (2026-10-06), following
`docs/superpowers/plans/2026-10-06-promptC-minimo-demo-desplegada.md` (D1–D8 closed with Antonio)
with the Superpowers flow (T1 tests on real PostgreSQL, T2 `clever` profile + registration closed by
default, T3 registration screen removed, T4 demo data and scripts outside the repo; one implementer +
one independent reviewer each, all **Approved with minors**, minors fixed; reports in
`.superpowers/sdd/c0-*`, not committed). **Backend and frontend in the same commit.** See the "C mínimo"
bullet under Technical decisions. No migrations. Verified with `./mvnw clean test` **690/690**,
`npm test` **557/557** (36 files), `npm run build` and `npm run lint` (known warnings only), and a
dress rehearsal against embedded PostgreSQL 16 (onboarding, both Excel imports, the seed SQL from a
non-UTC session, `smoke.sh` all PASS). Still to do: deploy on Clever (Antonio's account, console and
secrets), seed, and the smoke against the deployed environment; then optionally the Twilio sandbox.

**Ficha lista para OVZ (escalón 1 de OVZ) — complete, committed as `db0d234`** (2026-10-06 → 2026-10-07),
following `docs/superpowers/plans/2026-10-06-ficha-ovz.md` with the Superpowers flow (T1 OVZ types +
`V20`, T2 titular in the detail + T2b OVZ-password leak test, T3 the pure ficha catalog, T4 the copy
button, one implementer + one independent reviewer each, all **Approved with minors**, minors fixed; T5
the page, crafted from the main session with Impeccable after Antonio approved the shape, and reviewed
independently; reports in `.superpowers/sdd/fo-*`, not committed). See the "Ficha lista para OVZ" bullet
under Technical decisions. Migration `V20`. Verified with `./mvnw clean test` **706/706**, `npm test`
**720/720** (40 files), `npm run build` and `npm run lint` (known warnings only), and a real-browser smoke
(H2 file DB, two invented gestorías seeded through the real API, Playwright outside the repo) with
36/36 PASS: session inherited by the new window in Chromium and Firefox, real copy and Ctrl+V paste, every
type and state, cross-gestoría `404`, no page-level horizontal scroll at 375/420/1440 px, login-and-back
for a tab without opener. The OVZ-password test was mutation-checked before the commit: exposing the
plain password in `TramiteDetalleResponse` fails it at the body check, and exposing the stored
ciphertext (a raw read-only mapping of the column) fails it too; both restored with `cp` + `cmp`.

**Hecho en OVZ (D5 of the ficha OVZ) — complete, committed as `747b2ad`** (2026-10-07), following
`docs/superpowers/plans/2026-10-07-hecho-en-ovz.md` with the Superpowers flow (T1 backend, T2 frontend, one
implementer + one independent reviewer each: T1 **Approved with minors**, T2 **Changes requested** → fixed →
**Approved with minors**, minors fixed; T3 smoke delegated; reports in `.superpowers/sdd/ho-*`, not
committed). No Impeccable pass by Antonio's decision: existing visual patterns, reviewed on the smoke
screenshots. See the "Hecho en OVZ" bullet under Technical decisions. Migration `V21`. Verified with
`./mvnw clean test` **742/742**, `npm test` **811/811** (41 files), `npm run build` and `npm run lint`
(known warnings only), and a real-browser smoke (H2 file DB, two invented gestorías, A `ACTIVA` with three
usuarios, B `SUSPENDIDA`; Chromium and Firefox) with 13/13 scenarios PASS; screenshots outside the repo in
`C:\Users\Antonio\Desktop\capturas-ganera-hecho-ovz\`.

**B2 reducido — replying to the Contacto by WhatsApp — complete, committed as `f816f6e`** (2026-10-11), following
`docs/superpowers/plans/2026-10-11-promptB2-reducido-responder-whatsapp.md` with the Superpowers flow (T1 backend:
**Changes requested** (the Twilio SDK's 5xx retries) → fixed → **Approved with minors**; T2 frontend: **Approved
with minors**; the correction rounds after a usage-limit cut were done from the main session with TDD and
reviewed independently, **Approved with minors**, minors fixed; reports in `.superpowers/sdd/b2-*`, not committed).
See the "B2 reducido" bullet under Technical decisions. Migration `V22`. Verified with `./mvnw clean test`
**796/796**, `npm test` **912/912** (43 files), `npm run build` and `npm run lint` (known warnings only), and a
real-browser smoke with two Gestorías and Twilio faked by a bean compiled outside the repo; screenshots outside the
repo in `C:\Users\Antonio\Desktop\capturas-ganera-b2\`.

**B2 reducido — Twilio status callback — complete, committed as `5eeb39e`** (2026-10-11), following
`docs/superpowers/plans/2026-10-11-b2-callback-estado-twilio.md` (backend only; one implementer + one independent
reviewer, **Approved with minors**, minors accepted as notes; reports `.superpowers/sdd/b2-t4-*`, not committed). See
the "Status callback" sub-bullet under "B2 reducido". No migration. Verified with `./mvnw clean test` **906/906**
(frontend untouched, `npm test` **912/912**). **B2 reducido is closed** after Antonio's real sandbox test on
2026-10-12 (his terminal, his own phone, Cloudflare Quick Tunnel): reply accepted, then `ENTREGADO` and `LEIDO` callbacks
with the same SID and no error code, no phone or text in the log (details and notes in `progress.md`).

**SIN_TRAMITE — complete** (2026-10-12), following
`docs/superpowers/plans/2026-10-12-sin-tramite.md` with the Superpowers flow (T1 AI and extraction, T2 discard/undo +
`V23` + history, T3 frontend; one implementer + one independent reviewer each, all **Approved with minors**, minors
fixed by the main session with TDD and mutation checks; Antonio's four T3 adjustments and the shared contract test done
by the main session; reports `.superpowers/sdd/st-*`, not committed). See the "SIN_TRAMITE" bullet under Technical
decisions. Migration `V23`. Verified with `./mvnw clean test` **968** (967 run, 1 skipped: the evaluation), `npm test`
**976 in 45**, `npm run build` and `npm run lint` (known warnings only). The prompt was measured against the real Haiku
by the main session before Antonio's rule (three runs; the first found one false SIN_TRAMITE, «ok, apunta el censo»,
the prompt was adjusted, the next two had 0); from now on only Antonio runs it. **Antonio's own run** (his terminal):
0 false SIN_TRAMITE, chat 18/18, 0 failures. **His real smoke with the Twilio sandbox**, steps 1–9 PASS (queue notice,
«Descartar» with its hint, one-click discard with nothing sent to the phone, `404` from the other Gestoría on discard
and undo, the ficha OVZ of a discarded one, «Deshacer» with history, «gracias, se ha muerto la 4521» as a death with
`ES990000004521`). Instructions live outside the repo, in
`C:\Users\Antonio\Desktop\ganera-prueba-sin-tramite\INSTRUCCIONES.md` (lesson about PowerShell JSON bodies in
`progress.md`).

**Supabase migration — complete, real smoke (T5) passed, not committed yet** (2026-10-19 → 2026-10-21),
following `docs/superpowers/plans/2026-10-19-migracion-supabase.md` with the Superpowers flow (T1 PostgreSQL 17 in the
tests and `db/{vendor}`, **Approved with minors**; T2 roles, schema, RLS and time zone, **Changes requested** → fixed →
**Approved with minors**; T3 the `clever` profile against Supabase, **Changes requested** → fixed → **Approved with
minors**; T4 docs and kit; reports `.superpowers/sdd/su-*`, not committed). **Backend, `.env.example`, `docker-compose`
and docs; frontend untouched.** See the "Supabase" bullet under Technical decisions. No new versioned migration (the
callback lives in `db/postgresql`). Verified with `./mvnw clean test` **1075** (1074 pass, 1 skipped: the evaluation; JDK 21
and 23), after the role-chain check of 02 (`pg_has_role`, reviewed), `npm test` **1084 in 50** (unchanged), and a rehearsal of the kit
against embedded PostgreSQL 17 imitating Supabase (SSL `verify-full`, SCRAM for the two roles, the `clever` profile):
`prueba-supabase.sh` 5/5 PASS, `alta-gestorias.sh`, both Excel files, the seed from a non-UTC session, `smoke.sh` 39 and
38 PASS, 02 with 0 rows. **Real smoke (T5, Antonio's terminal, 2026-10-21): all PASS** — the pooler accepts
`rol.<ref>` and the panel's CA validates it with `verify-full`; the local backend (`clever`, JDK 21) migrated 23
versions into `ganera` with the callback and no Flyway WARN; `prueba-supabase.sh` 5/5; 02 with 0 rows on real
Supabase; `UTC`; `anon` denied; `ganera_app` sees 15 explotaciones and not `flyway_schema_history`; S7 done (schema
emptied, roles kept). Lesson: never paste a password blind into a console prompt — read it from the clipboard and
set it as a SCRAM hash in the SQL editor (kit `rotar-contrasenas.ps1`, `smoke-supabase-local.ps1`; details in
`progress.md`). **No commit until Antonio approves it.**

**M2 — OVZ password decrypted on demand and audited — complete** (2026-11-09 → 2026-11-10, branch
`feat/ovz-descifrado-bajo-demanda`). **Backend only, no migration.** See the "Descifrado de la contraseña OVZ bajo
demanda" part of the "Auditoría" bullet under Technical decisions (and the pending items for escalón 2 listed there).
`EncryptedStringConverter` was deleted; `CifradorAesGcm` keeps the same column format; `Ganadero` holds the
ciphertext at rest and its only writer takes a `CredencialOvzCifrada`; `DescifradoCredencialOvz` is the only place
that decrypts (no production caller yet) and leaves `CREDENCIAL_OVZ_DESCIFRADA` before decrypting. Independent
reviewer on the last round: **Approved with minors**, minors fixed (`CredencialOvzCifradaTipoTest` now also pins
`Ganadero`'s only constructor and its exact list of `String` setters — **it must go red when "opción B" adds a field
such as the phone; extend the list only after reviewing it**). Verified with `./mvnw clean test` **1338** (0 failures, 0 errors, 1 skipped: the evaluation; JDK 21),
`npm test` **1117 in 54** (frontend untouched), mutation checks (restored with `cp` + `cmp`) and a local smoke: two
corrupted ciphertexts → `200` on `/tramites/{id}`, `/ganaderos` and `/ganaderos/{id}`.

**Teléfono en la ficha del ganadero (opción B, primer campo) — complete, not merged yet** (2026-11-10, rama
`feat/ganadero-telefono`; plan `docs/superpowers/plans/2026-11-10-ganadero-telefono.md`, decisiones D1–D10 y ajustes
A1–A10). **Backend + frontend en el mismo commit** (cambia un contrato). Base: la rama arranca sobre `main` =
`27ff412` (merge del mock v2 del frontend de Álvaro, PRs #13–#22, solo `frontend/`). Ver la regla «Manual alta de
Ganadero» (el `telefono`, el `PUT /ganaderos/{id}/telefono`, el borrado explícito, `GANADERO_MODIFICADO` con
`campos_cambiados`, `EDITAR_GANADERO`, `V29`, `SETTERS_STRING` y el pendiente de `contacto_titular_id`). Flujo
Superpowers: T1 backend (implementador + revisor independiente, **Approved**), T2 frontend (añadir sin rediseñar el
mock v2 de Álvaro; implementador + revisor independiente, **Approved**), T3 smoke + capturas desde la sesión
principal. Mutaciones A3 reproducidas por la sesión principal (cp/cmp): `findByIdAndGestoriaId`→`findById` → rojo en
`GanaderoTelefonoEndToEndTest` (`expected null but was "+34612345678"`); quitar el `registrar` → rojo (GuardiaDml
«sin fila de auditoría»); teléfono en `despues` → no compila (`con(Clave, String)` no existe); y el rojo del test de
tipo (`CredencialOvzCifradaTipoTest`) con `setTelefono` antes de ampliar la lista. Verificado tras integrar `main`
(#30 + #29, `origin/main` = `de782a9`) con `./mvnw clean test` **1366** (1365 pasan, 1 skip: la evaluación; JDK 21),
`npm test` **1144 en 59**, `npm run build` y `npm run lint`
(solo los 3 avisos conocidos), y un smoke local con H2 (dos gestorías inventadas, datos por la API real): 21/21 HTTP
PASS (alta con/sin teléfono, PUT poner/cambiar/borrar, `400` sin clave e inválido, `404` ajeno sin cuerpo), auditoría
por SQL (5 `GANADERO_MODIFICADO`, 2 `LECTURA_GANADERO` — los PUT no añaden ninguna —, 1 `ACCESO_OTRA_GESTORIA`
`EDITAR_GANADERO` en la gestoría del actor, 0 filas con el teléfono en `antes`/`despues`), el número ausente del log y
de `GET /ganaderos`/`GET /buscar`, y 10 capturas a 1440 y 375 px fuera del repo
(`C:\Users\Antonio\Desktop\capturas-ganera-telefono\`, D6, sin Impeccable). La migración se renumeró de `V27` a `V29`
al integrar `main` (el `V27` de main es el de preferencias OVZ de la #24); en PostgreSQL se aplican 29 migraciones y
en H2 28 (hueco en V26). **Pendiente tras el merge:** el smoke en
producción (ganadero 11 de la gestoría de pruebas, `+34600000000`, A5/A8).

**Crotal de la madre resuelto contra el inventario — complete, no merged yet** (2026-11-11 → 2026-11-16, rama
`feat/crotal-madre`; plan `docs/superpowers/plans/2026-11-11-crotal-madre.md`). **Backend + frontend** (cambia el
contrato de `TramiteDetalleResponse`), aún sin commitear. Para el registro de nacimiento (`ALTA_NACIMIENTO`), el
crotal de la madre (`crotal_madre_indicado`, V27) se **resuelve** contra el inventario de la explotación del trámite
con **exactamente el mismo mecanismo** que los demás crotales: el refactor extrae un método compartido
`TramiteCrotalService.resolver(CrotalNormalizado, Explotacion, gestoriaId)` (devuelve un record
`ResultadoResolucionCrotal`) que usan tanto la lista de crotales como `resolverMadre(Tramite, gestoriaId)`; las dos
búsquedas de `Animal` llevan `explotacionId` **y** `gestoriaId` explícitos (más la red de `enlazado`, que lanza
`RecursoNoEncontradoException` si el animal recuperado no es de la explotación/gestoría). **Migración `V30`**
(`crotal_madre VARCHAR(30)` = crotal completado si `EN_INVENTARIO`, si no lo indicado; `crotal_madre_resolucion
VARCHAR(20)` = enum `ResolucionCrotal`), común H2+PostgreSQL, **sin FK al animal** (la madre es un campo de referencia
del formulario, no entra en `tramite_crotal`). Se re-resuelve **desde `crotalMadreIndicado`** (lo escrito, no lo
completado) en alta, extracción IA y al cambiar la explotación en el PATCH; **aprobar NO re-resuelve** la instantánea
de la madre (solo `recalcularEnlaces` de la lista). Un crotal de madre inválido no rompe (el PATCH valida el formato;
`resolverMadre` además captura `CrotalInvalidoException` → ambas columnas null). Borrado explícito por `""`/espacios en
PATCH (null = no cambiar, como el resto de campos OVZ). `TramiteDetalleResponse` expone `crotalMadre`/
`crotalMadreResolucion`; `TramiteResponse` (lista/aprobar) **no** los lleva. Frontend: `fichaOvz.ts` `campoCrotalMadre`
copia solo si `EN_INVENTARIO`, con pistas por resolución; `types.ts` añade los dos campos. **Nota de cobertura (del
revisor):** el aislamiento efectivo lo da `explotacionId` (en los tests de "otra gestoría" el animal ajeno vive además
en otra explotación, así que por el modelo de datos no se puede construir un test donde `gestoriaId` sea el único
filtro aislante); `gestoriaId` es **defensa en profundidad** —queda demostrado como load-bearing por los tests de
resolución positiva, no por un test de aislamiento dedicado—. Flujo Superpowers: implementador + revisor independiente
**Approved** (las 3 mutaciones dirigidas — quitar `gestoriaId` o `explotacionId` del `resolver` compartido rompe madre
**y** crotales normales; inyectar `resolverMadre` en `aprobar` lo cata `aprobarNoReResuelveLaInstantaneaDeLaMadre` —
salieron rojas y se restauraron con cp/cmp). La sesión principal añadió además un E2E de aislamiento
`explotacionId` (`laMadreNoSeResuelveContraOtraExplotacionDeLaMismaGestoria`, misma gestoría/otra explotación, sufijo
`31234` solo en A2): el cruce de gestorías ya lo aísla `gestoriaId`, así que ese caso es el que se pone **rojo** si se
cae el filtro `explotacionId` (verificado con TDD: mutación que ignora la explotación → rojo; restaurado con cp/cmp →
verde). Integrado `main` (`origin/main` = `6931f01`, PR #32 solo frontend) por
**fast-forward** sin solape. Verificado con `./mvnw clean test` **1388** (1387 pasan, 1 skip: la evaluación; JDK 21),
`npm test` **1172 en 61**, `npm run build` y `npm run lint` (solo los 3 avisos conocidos), y un smoke local con H2
(onboarding → login → import → trámite sembrado → PATCH fija la madre por sufijo `789012` → detalle `crotalMadre =
ES123456789012`, `EN_INVENTARIO` → aprobar → la madre no cambia en BD ni en el detalle; Flyway aplica V1–V30, 29 en
H2). **Recuentos de migraciones:** PostgreSQL **30** (V1–V30), H2 **29** (hueco en V26). **No commit hasta que Antonio
lo apruebe.**

