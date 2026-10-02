# Prompt A1 - Task 7b report (final review, smoke test, documentation)

Nothing was committed. I did not touch production code, tests, `frontend/`, `.agents/`, `.claude/skills/impeccable/`, `skills-lock.json`, `logo.jpg`, `PRODUCT.md` or `DESIGN.md`. I ran no git add, commit, stash, reset or checkout.

## 1. Greps

Search scope: `backend/src`. The first group of patterns was checked in `src/main`. `findByTelefono(` was checked across the whole backend.

| Pattern | Hits in `src/main` | Verdict |
|---|---|---|
| `findById(` | `AuthController.java:35` `usuarioRepository.findById(principal.usuarioId())`. The other hits are Javadoc comments in `GanaderoRepository`, `ExplotacionRepository`, `TramiteRepository` and `TramiteController`. | **Safe.** `/auth/me` gets its id from the signed JWT, not from a path or body. This predates A1 and is already documented as safe in CLAUDE.md. |
| `findByCodigoRega(` | 0 | The method no longer exists. It was removed in 7a, so going back to it is a compile error. |
| `findByNif(` | 0 | Removed, as above. |
| `findByCrotal(` | 0 | Removed, as above. |
| `findByTelefono(` | Only its declaration, `ContactoRepository.java:18`, plus a Javadoc mention in `ContactoService.java:21` saying it is NOT used there. | **Safe.** No caller in main, including `TwilioWebhookController`, which is not wired to business logic yet. It stays reserved for the 3b webhook (decision 11). |

`findByTelefono` in tests appears in two places only:
- `ContactoRepositoryTest:38` tests the method itself.
- `ExplotacionImportServiceTest:637` is `verify(contactoRepository, never()).findByTelefono(any())`, a guard that the importer never calls it.

`findById(` in tests: these are re-reads in `@DataJpaTest`/E2E assertions, not endpoints. That is fine.

**Related checks:**
- `getReferenceById`: every call site uses the JWT `gestoriaId`. `Gestoria` is not a scoped entity.
- `entityManager.find(`: 0.

**One pre-existing unscoped finder remains:** `AnimalRepository.findByExplotacionIdAndCrotalUltimosDigitos(Long, String)` has no `gestoriaId` and **no callers**. It is not new in A1. It is not in the task's grep list, and it is not reachable from any endpoint today. See open questions.

## 2. Test suite

`cd backend && JAVA_HOME="/c/Program Files/Java/jdk-23" ./mvnw clean test` gave **`Tests run: 415, Failures: 0, Errors: 0, Skipped: 0` — BUILD SUCCESS** (exit 0).

## 3. Smoke test: real HTTP, file-backed H2

**Setup**
- H2 file DB in my scratchpad: `jdbc:h2:file:<scratchpad>/smoke/ganera-smoke;MODE=PostgreSQL;AUTO_SERVER=TRUE;DB_CLOSE_DELAY=-1`.
- Started with `spring-boot:run -Dspring-boot.run.useTestClasspath=true`, Flyway enabled and `ddl-auto=none`.
- Exported `JWT_SECRET`, `ENCRYPTION_KEY` (random base64, 32 bytes) and `ONBOARDING_SECRET`. The Stripe variables were left blank.
- Startup log: `Successfully applied 17 migrations ... now at version v17`, then `Started GaneraApplication in 12.41 seconds`. The app PID was **2220**.

**Excel file.** Generated with openpyxl in the scratchpad, outside the repo:
- **Explotaciones:** `ES280790000001` and `ES280790000002`, both belonging to the Ganadero with NIF `12345678Z`.
- **Animales:**
  - `"ES 0100 0000 1234"` (with separators, to test normalization)
  - `ES010000005678`
  - `ES010000009999` (in the second explotación)
- **Contactos:**
  - `612 345 678`, TITULAR, on both explotaciones
  - `+34 699 000 111`, EMPLEADO
  - `12345`, which is invalid

**Results**

| # | Request | HTTP | Response excerpt |
|---|---|---|---|
| 1 | `POST /internal/onboarding/gestoria` | 200 | `{"gestoriaId":1,"usuarioId":1}` |
| 2 | `POST /auth/login` | 200 | `{"token":"eyJhbGciOiJIUzM4NCJ9...` |
| 3 | `POST /explotaciones/importar` | 200 | `explotaciones 2/2/0, animales 3/3/0, contactos {"filasProcesadas":4,"creadas":2,"actualizadas":1}, errores:[{"hoja":"Contactos","fila":5,"motivo":"Teléfono no válido"}]` |
| 4 | `GET /ganaderos` | 200 | `[{"id":1,"nombre":"Ganadero Smoke","nif":"12345678Z","numeroExplotaciones":2}]` (no `ovz*` fields) |
| 5 | `GET /ganaderos/1` | 200 | `explotaciones:[{codigoRega:"ES280790000001", contactos:[{"contactoId":2,"nombre":"Empleado Smoke","telefono":"+34699000111","rol":"EMPLEADO"},{"contactoId":1,...,"telefono":"+34612345678","rol":"TITULAR"}]},{codigoRega:"ES280790000002", contactos:[{contactoId:1,...,"rol":"TITULAR"}]}]` |
| 6 | `GET /ganaderos/999` | 404 | (no body) |
| 7 | `GET /contactos` | 200 | Both contactos, sorted by nombre, each with its explotaciones and rol; phones normalized to E.164 |
| 8 | `GET /contactos?sort=noExiste` | 400 | `{"motivo":"Campo de ordenación no permitido."}` |
| 9 | `GET /explotaciones/1/animales` | 200 | `{"crotal":"ES010000001234","crotalUltimosDigitos":"001234"}, {"crotal":"ES010000005678",...}`. The crotal with separators was stored normalized. |
| 10 | `GET /contactos` with no JWT | 401 | |
| 11 | SQL seed via `org.h2.tools.RunScript` (AUTO_SERVER) | n/a | A `tramite` row with `(gestoria 1, contacto 1, explotacion NULL, tipo NULL, PENDIENTE_REVISION)` and a `mensaje_campo` row. Read back as `1 PENDIENTE_REVISION null null 0`. |
| 12 | `GET /tramites/1` | 200 | `"mensajeOriginal":"Alta de la 1234 y la ES010000007777","crotales":[],"version":0` |
| 13 | `POST /tramites/1/aprobar`, no body | **400** | `{"motivo":"Falta la versión del trámite (campo version). Vuelve a cargarlo e inténtalo de nuevo."}` |
| 14 | `POST /tramites/1/aprobar` `{}` | 400 | Same motivo |
| 15 | `POST /tramites/1/aprobar` `{"version":0}` | **409** | `{"motivo":"Falta asignar la explotación. Falta el tipo de trámite."}` |
| 16 | `PATCH /tramites/1`, malformed JSON (truncated) | **400** | Boot's default body `{"status":400,"error":"Bad Request","path":"/tramites/1"}`. It is **not 401**, so decision 29 holds. |
| 17 | `PATCH /tramites/1` `{"version":0,"explotacionId":"abc"}` | 400 | Boot's default body |
| 18 | `POST /tramites/abc/aprobar` | 400 | Boot's default body |
| 19 | `PATCH /tramites/1` `{"explotacionId":1}`, no version | 400 | The "Falta la versión..." motivo |
| 20 | `PATCH /tramites/1` `{"version":0,"explotacionId":1,"tipoTramite":"ALTA","crotales":["1234","ES 0100 0000 7777"]}` | **200** | `"tipoTramite":"ALTA","explotacionCodigoRega":"ES280790000001","crotales":[{"crotalIndicado":"1234","crotal":"ES010000001234","animalId":1,"enInventario":true,"resolucion":"EN_INVENTARIO"},{"crotalIndicado":"ES010000007777","crotal":"ES010000007777","animalId":null,"enInventario":false,"resolucion":"NO_ENCONTRADO"}],"version":1` |
| 21 | `PATCH /tramites/1` `{"version":0,"tipoTramite":"BAJA"}` (stale) | **409** | `{"motivo":"El trámite ha cambiado desde que lo abriste. Vuelve a cargarlo y revísalo antes de continuar."}` |
| 22 | `POST /tramites/1/aprobar` `{"version":0}` (stale) | **409** | Same motivo |
| 23 | `POST /tramites/1/aprobar` `{"version":1}` | **200** | `"estado":"APROBADO","tipoTramite":"ALTA",...,"version":2` |
| 24 | `POST /tramites/1/aprobar` `{"version":2}` | 409 | `{"motivo":"Solo se puede aprobar un trámite pendiente de revisión."}` |
| 25 | `POST /tramites/1/rechazar` | 409 | `{"motivo":"Solo se puede rechazar un trámite pendiente de revisión."}` |
| 26 | `GET /tramites?estado=APROBADO` | 200 | The trámite, with its 2 crotales and `version:2` |
| 27 | `GET /tramites?sort=contacto.nombre` | 400 | `{"motivo":"Campo de ordenación no permitido."}` |
| 28 | `GET /explotaciones?sort=ganadero.nif` | 400 | Same motivo |

**Final DB state, read by SQL:**
- `tramite`: `1 APROBADO 1 ALTA 2`
- `tramite_crotal`:
  - `1 1234 ES010000001234 1 EN_INVENTARIO 1`
  - `1 ES010000007777 ES010000007777 null NO_ENCONTRADO 1`
- `contacto`: `1 1 +34612345678 TRUE` and `2 1 +34699000111 TRUE`

The `NO_ENCONTRADO` crotal was typed with separators and is complete, `ES` + 12 digits. It passed the provisional format rule, as expected.

**Cleanup**
- Before killing anything, `Get-CimInstance Win32_Process java.exe` showed three processes:
  - 14128: the VS Code Java language server, pre-existing and left alone
  - 7672: the Maven wrapper JVM
  - 2220: the Spring Boot app
- I ran `Stop-Process -Id 2220 -Force` and `Stop-Process -Id 7672 -Force`.
- A re-check showed **only PID 14128** (the VS Code language server). No smoke JVM is left alive.
- I deleted the scratchpad `smoke/` folder: the H2 `.mv.db` file, the Excel file, `gen.py`, `seed.sql`, `check.sql` and `token`. I also deleted the copied app log.
- The repo has no temp files from the smoke test.

Result: **no defects found.**

## 4. Documentation changes

### `CLAUDE.md` (in English, same style)

**Domain model**
- Contacto:
  - It is now a `GestoriaScopedEntity`, with `telefono` still globally `UNIQUE`.
  - The role lives on the relation (`ContactoExplotacion.rol`, `RolContacto` TITULAR/EMPLEADO). `TipoContacto` was dropped.
  - **Removed** the "trabajador -> exactly one Explotación" sentence. A Contacto can now be linked to any number of explotaciones (decision 4).
  - Added the `TelefonoNormalizador` rules.
  - Logical delete via `activo`: inactive contactos are left out of listings and the Ganadero detail, and they cannot be linked.
  - The 3b webhook must ignore inactive Contactos (decision 2).
- Animal: points to `tramite_crotal`.

**Operational flow**
- Step 2:
  - `findByTelefono` is exclusive to the 3b webhook; everything else uses `findByGestoriaIdAndTelefono` (decision 11).
  - A duplicate phone is detected from the constraint violation.
  - Decision 1: (a) the limitation (one Gestoría per phone, and the deduction oracle); (b) the future path (one number per Gestoría, the Gestoría identified by the destination number, `UNIQUE(gestoria_id, telefono)`).
  - The webhook ignores inactive contactos.
- Step 5: `tramite_crotal`, and 3b must deduplicate crotales that resolve to the same Animal (decision 23).
- Step 6: PATCH before approving.

**Non-negotiable rules**
- The `findById` rule is extended: the unscoped `findByCodigoRega`/`findByNif`/`findByCrotal` were removed, and `findByTelefono` is the only unscoped finder left.
- Subscription rule: "read-only" changed to "no approvals". The gate applies **only** to `aprobar`; PATCH, rechazar, import and contactos stay allowed (decision 30).
- Frontend "Aprobar" rule: added the **known breakage until A2** (the UI sends `aprobar` without a `version`, so it always gets 400).
- "No manual alta" rule: noted the explicit exception for Contactos (importer sheet or `/contactos`, backend only).

**Technical decisions**
- `EstadoSuscripcion` bullet: "read-only" changed to "no approvals". Corrected the stale "not wired to any endpoint yet"; the gate is wired only to `/aprobar`.
- Excel importer bullet:
  - Decision 22: `CrotalNormalizador` is applied to `Animal.crotal`, with the 20-character limit. Incomplete crotales are still accepted into inventory.
  - The "Contactos" sheet.
  - **M4 note:** pre-A1 crotales were not migrated, which can cause duplicates on re-import and blocks their approval.
  - Decision 17: inherent cross-tenant deduction from the global UNIQUE constraints, the neutral message, scoped finders only, unscoped finders removed. The 7a review M3 caveat is included.
  - **Decision 19:** the REQUIRES_NEW explanation is rewritten with the precise wording from the 7a review N2. Over HTTP it reuses the OSIV EntityManager with the filter already active. A new, unfiltered EntityManager only appears when **no EntityManager is bound to the thread** (a scheduler or other non-request thread, even with no outer transaction) **or there is an outer transaction** (a `@Transactional` caller, `@DataJpaTest`). That is why each method still re-enables the filter. I also noted that the class Javadoc still lists "a scheduler" under the outer-transaction case (N2 nit, code not touched).
  - `/explotaciones` and `/tramites` now use an explicit `gestoriaId` plus sort whitelists. The paragraph that said they relied only on the ambient filter is corrected.
- **New bullet "Prompt A1":**
  - The plan path, and migrations V15 to V17.
  - Error format: 409 `{motivo}`, 404 with no body, 400.
  - Every new endpoint: `/contactos` (full CRUD, reactivar, enlazar/desenlazar, decision 15), `/ganaderos`, `/ganaderos/{id}` (no OVZ credentials), `/explotaciones/{id}/animales`, `PATCH /tramites/{id}`.
  - The sort whitelists and defaults for all 5 listings.
  - `tramite_crotal` and `crotal_indicado`.
  - `CrotalNormalizador`: the complete/incomplete rules, `ResolucionCrotal`, and re-resolution on explotación change and on approve.
  - The PATCH check order, including M7: PATCH cannot clear the explotación or tipo, `crotales: []` clears the list, and the note about the collision after an explotación change.
  - The **full approval rule in the real order**: 403, then 400, then 404, then 409 estado (decision 12), then 409 version, then `ResolucionCrotalesCambiadaException` (**the only 409 that commits**, M-R3), then the accumulated 409s (decisions 25 and 23), then 200.
  - Decision 28 as a **provisional** rule, applied to `NO_ENCONTRADO` and to the Animal's crotal for `EN_INVENTARIO` (I1).
  - `PESSIMISTIC_WRITE` + refresh, including the M3 "first load" warning.
  - Decision 27 (`@Version`, exact +1, rechazar increments without requiring it, why not `PESSIMISTIC_FORCE_INCREMENT`), and estado checked before version.
  - The 7a review M2 note on the untranslated raw EntityManager calls.
  - **3c risks** (M2): Playwright outside the lock, `57014` gives 500, `55P03` gives 409.
  - `/error` is public, with the reason (decision 29).

**Architecture notes (backend)**
- `shared/web` added to the package layout.
- `ContactoExplotacion` is no longer "unused".
- `/error` added to **Public paths**.
- New note: **always run `./mvnw clean test`**, because of the VS Code/ECJ classes in `target/` (the 387-test episode).

**Architecture notes (frontend)**
- The banner's "read-only" became "no-approvals".
- New note: `TramiteReviewDialog` is out of date until A2. It sends no version, shows the buttons in every estado, shows a generic message on 409, and has no PATCH UI.

**Commands**
- `./mvnw clean test`, 415 tests.

**Current status**
- New A1 paragraph: complete, 415 tests, the smoke test, **not committed yet, pending Antonio's approval**, the UI approve breakage, and `TipoTramite` unchanged.
- "Next pending step" adds A2 as the next step that is not blocked.

### `ganera-prompts.md`
- Status header: dated 2026-09-28, A1 complete (not committed), 415 tests, approving from the UI broken until A2, next unblocked step is A2.
- **3b** section, "Pendientes heredados del Prompt A1":
  - decision 2 (ignore inactive contactos);
  - decision 11 (`findByTelefono` exclusive to the webhook, normalize `From` with `TelefonoNormalizador`);
  - decision 23 (deduplicate crotales that resolve to the same Animal);
  - decision 18: the `TipoTramite` enum gets replaced in prompt B by `ALTA_BOVINO`, `BAJA`, `SOLICITUD_MOVIMIENTO`, `CONFIRMACION_MOVIMIENTO`, `DECLARACION_CENSO`, `MOD_DECLARACION_CENSO`, `DEMORA_CROTALIZACION`. Occasional types (annulling guías, rejecting animals at origin) come in a later phase. The enum was **not** changed in A1.
- **3c** section: M2 (Playwright outside the row lock, and `statement_timeout` / `lock_timeout`).
- **New section "Prompt A1"**, in the same format as the others:
  - context, and what was delivered per task;
  - verification (415 tests and the smoke test);
  - the process finding (javac vs VS Code);
  - **pending items for A2:** send `version`; reload the detail and show `motivo` on any 409 (R1/M5); hide or disable Aprobar/Rechazar outside `PENDIENTE_REVISION`; a PATCH UI, noting M7 (PATCH cannot clear explotación/tipo, `crotales: []` clears);
  - other open notes: M4 legacy crotales, the provisional format rule, and the M3 neutral-message caveat.

### `.superpowers/sdd/progress.md`
New entry "2026-09-25 → 2026-09-28 — Prompt A1", in the style of the earlier ones:
- Step 0 and decisions 1 to 31.
- Each task with its test counts: 171, 206, 220, 239, 288, 358, 408 and 415.
- The review verdicts and the findings that became decisions.
- **The session cut during 7a**, and how it was resumed: `clean test` failed at testCompile, which showed that the "387 tests" figure came from ECJ classes.
- Task 7b: greps, suite, smoke test, docs.
- Not committed, plus the pre-demo warning about Aprobar in the UI.

## 5. Git status and diff --stat (after 7b)

`git diff --stat` (tracked files):
```
 .superpowers/sdd/progress.md                       |  74 ++++
 CLAUDE.md                                          | 339 ++++++++++++--
 .../java/com/ganera/core/contacto/Contacto.java    |  34 +-
 .../ganera/core/contacto/ContactoExplotacion.java  |  27 +-
 .../ganera/core/contacto/ContactoRepository.java   |  19 +
 .../com/ganera/core/contacto/TipoContacto.java     |   6 -
 .../ganera/core/explotacion/AnimalRepository.java  |  18 +-
 .../core/explotacion/ExplotacionController.java    |  55 ++-
 .../explotacion/ExplotacionImportFilaService.java  | 117 ++++-
 .../core/explotacion/ExplotacionImportService.java | 153 ++++++-
 .../core/explotacion/ExplotacionRepository.java    |  28 +-
 .../core/explotacion/ImportResumenResponse.java    |   2 +
 .../ganera/core/ganadero/GanaderoRepository.java   |  13 +-
 .../core/shared/security/SecurityConfig.java       |   7 +
 .../main/java/com/ganera/core/tramite/Tramite.java |  12 +
 .../com/ganera/core/tramite/TramiteController.java | 164 +++++--
 .../core/tramite/TramiteDetalleResponse.java       |  16 +-
 .../com/ganera/core/tramite/TramiteRepository.java |  22 +-
 .../com/ganera/core/tramite/TramiteResponse.java   |  12 +-
 .../core/contacto/ContactoRepositoryTest.java      | 120 ++++-
 .../explotacion/ExplotacionControllerTest.java     |  52 ++-
 .../explotacion/ExplotacionImportServiceTest.java  | 491 ++++++++++++++++++++-
 .../shared/tenant/TenantIsolationEndToEndTest.java |  37 +-
 .../ganera/core/tramite/TramiteControllerTest.java | 426 ++++++++++++++++--
 .../ganera/core/tramite/TramiteRepositoryTest.java |   3 +-
 backend/src/test/resources/application.yml         |   2 +-
 ganera-prompts.md                                  |  36 +-
 27 files changed, 2061 insertions(+), 224 deletions(-)
```

**Untracked:**
- 15 SDD reports: `a1-task1..7a` report and review files, plus this report.
- The A1 plan.
- 32 new Java files under `backend/src/main` (including the 2 in `shared/web/`) and 3 migrations.
- 16 new test files.
- Not part of A1: `capturaTramites.jpeg`, `logo.jpg`, `frontend/public/*` (5 files).

## 6. Proposed commit: paths to add

**Documentation**
```
CLAUDE.md
ganera-prompts.md
.superpowers/sdd/progress.md
docs/superpowers/plans/2026-09-25-promptA1-contactos-crotales-revision.md
```

**Backend: modified and deleted** (`git add` also stages the deletion of `TipoContacto.java`)
```
backend/src/main/java/com/ganera/core/contacto/Contacto.java
backend/src/main/java/com/ganera/core/contacto/ContactoExplotacion.java
backend/src/main/java/com/ganera/core/contacto/ContactoRepository.java
backend/src/main/java/com/ganera/core/contacto/TipoContacto.java
backend/src/main/java/com/ganera/core/explotacion/AnimalRepository.java
backend/src/main/java/com/ganera/core/explotacion/ExplotacionController.java
backend/src/main/java/com/ganera/core/explotacion/ExplotacionImportFilaService.java
backend/src/main/java/com/ganera/core/explotacion/ExplotacionImportService.java
backend/src/main/java/com/ganera/core/explotacion/ExplotacionRepository.java
backend/src/main/java/com/ganera/core/explotacion/ImportResumenResponse.java
backend/src/main/java/com/ganera/core/ganadero/GanaderoRepository.java
backend/src/main/java/com/ganera/core/shared/security/SecurityConfig.java
backend/src/main/java/com/ganera/core/tramite/Tramite.java
backend/src/main/java/com/ganera/core/tramite/TramiteController.java
backend/src/main/java/com/ganera/core/tramite/TramiteDetalleResponse.java
backend/src/main/java/com/ganera/core/tramite/TramiteRepository.java
backend/src/main/java/com/ganera/core/tramite/TramiteResponse.java
backend/src/test/java/com/ganera/core/contacto/ContactoRepositoryTest.java
backend/src/test/java/com/ganera/core/explotacion/ExplotacionControllerTest.java
backend/src/test/java/com/ganera/core/explotacion/ExplotacionImportServiceTest.java
backend/src/test/java/com/ganera/core/shared/tenant/TenantIsolationEndToEndTest.java
backend/src/test/java/com/ganera/core/tramite/TramiteControllerTest.java
backend/src/test/java/com/ganera/core/tramite/TramiteRepositoryTest.java
backend/src/test/resources/application.yml
```

**Backend: new main files**
```
backend/src/main/java/com/ganera/core/contacto/ContactoController.java
backend/src/main/java/com/ganera/core/contacto/ContactoExplotacionRepository.java
backend/src/main/java/com/ganera/core/contacto/ContactoExplotacionResponse.java
backend/src/main/java/com/ganera/core/contacto/ContactoInactivoException.java
backend/src/main/java/com/ganera/core/contacto/ContactoRequest.java
backend/src/main/java/com/ganera/core/contacto/ContactoResponse.java
backend/src/main/java/com/ganera/core/contacto/ContactoService.java
backend/src/main/java/com/ganera/core/contacto/EnlaceExplotacionRequest.java
backend/src/main/java/com/ganera/core/contacto/RecursoNoEncontradoException.java
backend/src/main/java/com/ganera/core/contacto/RolContacto.java
backend/src/main/java/com/ganera/core/contacto/TelefonoNormalizador.java
backend/src/main/java/com/ganera/core/explotacion/AnimalResponse.java
backend/src/main/java/com/ganera/core/explotacion/ConteoExplotacionesPorGanadero.java
backend/src/main/java/com/ganera/core/explotacion/FilaImportacionException.java
backend/src/main/java/com/ganera/core/ganadero/GanaderoConsultaService.java
backend/src/main/java/com/ganera/core/ganadero/GanaderoController.java
backend/src/main/java/com/ganera/core/ganadero/GanaderoDetalleResponse.java
backend/src/main/java/com/ganera/core/ganadero/GanaderoResumenResponse.java
backend/src/main/java/com/ganera/core/shared/web/MotivoErrorResponse.java
backend/src/main/java/com/ganera/core/shared/web/OrdenacionPermitida.java
backend/src/main/java/com/ganera/core/tramite/CrotalInvalidoException.java
backend/src/main/java/com/ganera/core/tramite/CrotalNormalizador.java
backend/src/main/java/com/ganera/core/tramite/ResolucionCrotal.java
backend/src/main/java/com/ganera/core/tramite/ResolucionCrotalesCambiadaException.java
backend/src/main/java/com/ganera/core/tramite/TramiteAprobarRequest.java
backend/src/main/java/com/ganera/core/tramite/TramiteConflictoException.java
backend/src/main/java/com/ganera/core/tramite/TramiteCrotal.java
backend/src/main/java/com/ganera/core/tramite/TramiteCrotalRepository.java
backend/src/main/java/com/ganera/core/tramite/TramiteCrotalResponse.java
backend/src/main/java/com/ganera/core/tramite/TramiteCrotalService.java
backend/src/main/java/com/ganera/core/tramite/TramitePatchRequest.java
backend/src/main/java/com/ganera/core/tramite/TramiteRevisionService.java
backend/src/main/resources/db/migration/V15__contacto_tenant_rol_y_activo.sql
backend/src/main/resources/db/migration/V16__create_tramite_crotal.sql
backend/src/main/resources/db/migration/V17__add_version_to_tramite.sql
```

**Backend: new tests**
```
backend/src/test/java/com/ganera/core/contacto/ContactoEndToEndTest.java
backend/src/test/java/com/ganera/core/contacto/ContactoExplotacionRepositoryTest.java
backend/src/test/java/com/ganera/core/contacto/ContactoServiceEnlazarTest.java
backend/src/test/java/com/ganera/core/contacto/ContactoServiceMismaGestoriaTest.java
backend/src/test/java/com/ganera/core/contacto/TelefonoNormalizadorTest.java
backend/src/test/java/com/ganera/core/explotacion/ExplotacionImportContactosEndToEndTest.java
backend/src/test/java/com/ganera/core/explotacion/ExplotacionImportMotivoContactoTest.java
backend/src/test/java/com/ganera/core/ganadero/GanaderoEndToEndTest.java
backend/src/test/java/com/ganera/core/shared/security/ErrorDispatchEndToEndTest.java
backend/src/test/java/com/ganera/core/tramite/CrotalNormalizadorTest.java
backend/src/test/java/com/ganera/core/tramite/SqlCapturadoInspector.java
backend/src/test/java/com/ganera/core/tramite/TramiteControllerErroresTest.java
backend/src/test/java/com/ganera/core/tramite/TramiteCrotalEndToEndTest.java
backend/src/test/java/com/ganera/core/tramite/TramiteCrotalServiceTest.java
backend/src/test/java/com/ganera/core/tramite/TramiteRevisionEndToEndTest.java
backend/src/test/java/com/ganera/core/tramite/TramiteRevisionServiceTest.java
```

**Excluded:**
- `frontend/public/*`: Antonio's files. `preview.png` is never uploaded.
- `.agents/`, `.claude/skills/impeccable/`, `skills-lock.json`.
- `logo.jpg`.
- `capturaTramites.jpeg`: untracked and not part of A1. See the question below.
- The `.superpowers/sdd/a1-*` reports: see below.

**Should the `.superpowers/sdd/a1-*` reports be committed?** No, if we follow precedent.
- `git ls-files .superpowers` lists only `progress.md`.
- `git log --all` shows no commit that ever added a `*report*`/`*review*`/`*brief*` under `.superpowers/sdd/`. Every earlier commit (Prompt 2.7, 3d, 4...) included only `progress.md`.
- The plans under `docs/superpowers/plans/` are always committed, so the A1 plan goes in.
- Recommendation: keep the reports out, for consistency, unless Antonio wants them archived.

## 7. Proposed commit message

```
feat: Prompt A1 - contactos, crotales en tramites y revision editable (solo backend)

Prepara 3b (WhatsApp + IA) sin depender de OVZ.net. Plan con 31
decisiones en
docs/superpowers/plans/2026-09-25-promptA1-contactos-crotales-revision.md.

- Contactos: Contacto pasa a GestoriaScopedEntity (V15) con telefono
  UNIQUE global (limitacion y camino futuro documentados); rol en la
  relacion (ContactoExplotacion.rol, RolContacto TITULAR/EMPLEADO; se
  borra TipoContacto); borrado logico activo; TelefonoNormalizador a
  E.164. CRUD /contactos con reactivar y enlazar/desenlazar explotaciones.
- Importador: hoja "Contactos" opcional (REQUIRES_NEW por fila, solo
  findByGestoriaIdAndTelefono); crotales normalizados con
  CrotalNormalizador; mensaje neutro para identificadores de otra
  Gestoria; se eliminan findByCodigoRega/findByNif/findByCrotal sin scope.
- Lectura: GET /ganaderos, GET /ganaderos/{id} (contactos activos con
  rol, sin credenciales OVZ), GET /explotaciones/{id}/animales.
- tramite_crotal (V16): crotal indicado + resolucion (EN_INVENTARIO,
  AMBIGUO, NO_ENCONTRADO, SIN_EXPLOTACION) dentro de la explotacion del
  tramite; crotales en GET /tramites con una consulta por pagina.
- PATCH /tramites/{id} (explotacion, tipo, lista completa de crotales) con
  bloqueo PESSIMISTIC_WRITE; aprobar/rechazar solo desde
  PENDIENTE_REVISION; regla de aprobacion ampliada (explotacion y tipo
  obligatorios, crotales ambiguos/sin explotacion/incompletos bloquean,
  formato provisional ES+12 u otro pais 2 letras+8-12, tambien sobre el
  crotal del Animal en inventario; dos crotales al mismo animal -> 409); re-resolucion al aprobar con 409 que confirma la
  nueva resolucion.
- Version optimista (V17, @Version): PATCH y aprobar exigen version
  (400 si falta, 409 si desfasada); toda escritura la incrementa en 1.
- Listas blancas de sort en todos los listados; /error publico (un
  cuerpo mal formado da 400, no 401); errores 409 {motivo} / 404 sin
  cuerpo / 400.
- CLAUDE.md, ganera-prompts.md y progress.md al dia.

Aviso: el frontend actual llama a aprobar sin version -> 400 (salvo el
403 de suscripcion, que va antes); aprobar desde la UI no funciona hasta
el prompt A2.

Suite backend: 415 tests en verde (./mvnw clean test). Smoke test HTTP
real con H2 en fichero.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
```

## 8. Open questions for Antonio

1. **`capturaTramites.jpeg`** (repo root, untracked): is it yours? It is not A1 work. Should it stay out of the commit, be deleted, or go into `.gitignore`?
2. **`frontend/public/*`** (favicon and logos, plus `preview.png`): I assume these are your branding work and they are excluded. Confirm whether the logos go in a separate commit. `preview.png` is never uploaded.
3. **`.superpowers/sdd/a1-*` reports:** the precedent is to not commit them (only `progress.md` is committed). Do you want to keep that?
4. **`AnimalRepository.findByExplotacionIdAndCrotalUltimosDigitos`**: this pre-existing finder has no `gestoriaId` and no callers. Should it be removed or scoped in a follow-up, before 3b is tempted to use it?
5. **Decision 28 is provisional.** Confirm the crotal lengths for countries other than ES.
6. **Pre-A1 data (M4):** if any real environment (your local Postgres, a pilot) already has animals imported before A1, a one-off normalizing `UPDATE` of `animal.crotal` is recommended.
7. **Small leftover nits, not blocking:**
   - The `ExplotacionImportFilaService` Javadoc still lists "a scheduler" under "outer transaction" (7a N2). `CLAUDE.md` now has the precise wording, but the code comment was not touched, per the brief.
   - The neutral importer message also covers length violations on NIF and name (7a M3).

## Fixes after review

The review verdict was **Changes required** (documentation only). Every fix below is documentation. I did not touch code, tests or git.

- **I1** (`CLAUDE.md`, non-negotiable `findById` rule): `findByTelefono` is now described as the only unscoped finder *meant to be used* (reserved for the 3b webhook). The rule now also says that `AnimalRepository.findByExplotacionIdAndCrotalUltimosDigitos` (pre-A1, no `gestoriaId`, no callers) still exists, must not be called (A1 uses `findByExplotacionIdAndGestoriaIdAndCrotalEndingWithOrderByIdAsc`), and must be removed or scoped before 3b; that decision is Antonio's. I added the same item to the 3b "Pendientes heredados del Prompt A1" in `ganera-prompts.md`. The code was not changed.
- **M1** (the "always 400" claim): I added the exception in four places. A Gestoría whose subscription is blocked, or that has no `Suscripcion`, still gets the `403` first, with its specific message.
  - `CLAUDE.md`: the non-negotiable frontend rule and the `TramiteReviewDialog` note.
  - `ganera-prompts.md`: the A2 pending item.
  - `progress.md`: the pre-demo warning.
  - The commit-message "Aviso".
- **M2** (`CLAUDE.md`, Contacto domain model): the text now reads "`409` via the API, a row error in the importer".
- **M3** (commit message): I used the full plan path and added that the provisional format rule also applies to the Animal's crotal for `EN_INVENTARIO`. The commit message in section 7 has been updated.
- **N1** (`CLAUDE.md`, Operational flow step 2): the text now says no authenticated code path uses `findByTelefono`, only the importer looks up with `findByGestoriaIdAndTelefono`, and the `/contactos` endpoints do no phone lookup at all.
- **N2** (`ganera-prompts.md`): I added short annotations and left the historical text as it was.
  - Prompt 0: "[Nota posterior: superado por A1, decisión 4 …]".
  - Prompt 4 narrative: "superado por A1, decisión 30: solo se bloquea aprobar; editar, rechazar e importar siguen permitidos".

**Commit path list:** unchanged. The same 79 paths, with the same exclusions.
