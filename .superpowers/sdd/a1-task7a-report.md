# Prompt A1 - Task 7a report (closing code changes: decisions 17, 19, 21, 22, 27, 28, 29, 31)

This picks up a session that was cut off partway. It is not a fresh start. The inherited work was audited first, then completed.

## Starting point: `clean test` as inherited

`JAVA_HOME="/c/Program Files/Java/jdk-23" ./mvnw clean test` gave **BUILD FAILURE at testCompile**, so no tests ran. There were 8 javac errors in 2 files:
- `ExplotacionControllerTest`: `listar(PageRequest)` no longer matches `listar(GaneraUserPrincipal, Pageable)`.
- `TramiteControllerTest` (4 sites): `ResponseEntity<?>` cannot be converted to `Page<TramiteResponse>`.

The orchestrator's "387 tests, 13 failures + 6 errors" figure came from classes that VS Code had compiled into `target/`. A clean javac build does not compile at all. This matters later too: `mvn test` without `clean` picked up ECJ classes ("Unresolved compilation problem"). Every run I relied on uses `clean`.

## Files

### Inherited, not reverted
- `shared/security/SecurityConfig.java`: `/error` permitAll (decision 29).
- `test/.../ErrorDispatchEndToEndTest.java`: decision 29.
- `test/resources/application.yml`: `LOCK_TIMEOUT=10000` (decision 31).
- `ExplotacionController`, `TramiteController#listar`, `ExplotacionRepository#findByGestoriaId`, `TramiteRepository#findByGestoriaId[AndEstado]`: decision 21, plus its E2E cases in `GanaderoEndToEndTest` and `TramiteRevisionEndToEndTest`.
- Decision 17/22/28 tests in `ExplotacionImportServiceTest` and `TramiteRevisionServiceTest`. I strengthened these; see the audit below.

### Mine

Main code:

| File | What changed |
|---|---|
| `explotacion/ExplotacionImportFilaService.java` | Decisions 17 and 22: explicit-`gestoriaId` finders. Decision 19: corrected the REQUIRES_NEW Javadoc. |
| `explotacion/ExplotacionImportService.java` | Decision 17: neutral motivo. Decision 22: normalize the crotal and enforce the 20-character limit. |
| `explotacion/AnimalRepository.java` | **Removed** `findByCrotal(String)`. |
| `explotacion/ExplotacionRepository.java` | **Removed** `findByCodigoRega(String)`. |
| `ganadero/GanaderoRepository.java` | **Removed** `findByNif(String)`. |
| `tramite/TramiteRevisionService.java` | Decision 28: approval format rule. Decision 27: version check and exact +1 increment on every write. |
| `tramite/TramiteController.java` | Decision 27: 400 when the version is missing. `aprobar` takes an optional body. |
| `tramite/Tramite.java` | Decision 27: `@Version Long version`. |
| `tramite/TramiteResponse.java`, `tramite/TramiteDetalleResponse.java` | New `version` field. |
| `tramite/TramitePatchRequest.java` | New `version` field as the first component. |
| `tramite/TramiteAprobarRequest.java` (new) | `record(Long version)`. |
| `db/migration/V17__add_version_to_tramite.sql` (new) | `tramite.version BIGINT NOT NULL DEFAULT 0`. |

Tests:
- `ExplotacionControllerTest`, `TramiteControllerTest`: adapted to the new `listar`, plus new tests.
- `ExplotacionImportServiceTest`: strengthened, and switched to scoped finders.
- `TramiteRevisionServiceTest`, `TramiteRevisionEndToEndTest`, `TramiteControllerErroresTest`, `TenantIsolationEndToEndTest`: adapted to `version`, plus new tests.

## Audit of the inherited tests (step 2)

**Decision 17 (`identificadoresDeOtraGestoria...`).** The test was correct but only covered the message. A version using bare finders would have passed it too, because the filter is re-enabled in REQUIRES_NEW.

Fixes:
1. **Removed the three unscoped finders from the repositories** (`findByNif`, `findByCodigoRega`, `findByCrotal`). They had no callers in `src/main` other than the importer. Going back to a bare lookup is now a compile error, which is stronger than a spy-based check. Five test lines that used them now use the `...AndGestoriaId` versions.
2. The assertion now also checks `doesNotContain("duplicad")`, in addition to `"gestori"`.
3. The foreign Ganadero (same NIF) must keep its name and must not gain any Explotación from this Gestoría.
4. There must be exactly 2 animals in total.
5. Added a row with the **foreign crotal written with separators** (`es-5100 0000.0099`). After normalization it collides the same way and must return the same neutral error. This covers how decisions 17 and 22 interact.

**Decision 22.** The inherited tests were good:
- The normalized value is stored, and a re-import does not duplicate it.
- `crotalUltimosDigitos` is derived from the normalized value (`001234`).
- The normalizer's motivos, plus the 20-character limit of `animal.crotal`.
- An invalid row does not block the ones after it.
- A trámite resolves against an imported crotal.

The only change was the crotal-with-separators row above.

**Decision 28.** The limits were well covered:
- Valid: `ES`+12, `FR`+8, `DE`+12, `IT`+10.
- Invalid: `ES`+11, `ES`+13, `FR`+7, `DE`+13, `ES1234`, 3 letters, 13 bare digits, `A1234`, `12A34`.

I added `FR12345A78` (a letter among the digits) and `ES12345678901A`. Without them, a regex like `[A-Z]{2}.{8,12}` would have passed.

**Decision 21.** The tests were adequate. `TramiteControllerTest.laCargaDeCrotalesDelListado...` had become **vacuous**: since B's listing now uses `findByGestoriaId`, it no longer contains A's trámite, so the `forEach` asserted nothing. I rewrote it:
- B's listing contains exactly B's trámite.
- `crotalesPorTramite([idA], gestoriaB)` is empty.
- With gestoriaA, the same call returns `4444`.

**Decision 29.** The tests were adequate:
- A wrongly typed body, non-JSON, and a non-numeric id all return 400.
- Without a JWT, everything still returns 401, including `/error/x` and `/errores`.
- `/error` leaks no data.

I made no changes.

**Decision 31.** Covered by `laBdDeTestTieneUnLockTimeoutGeneroso`.

## Step 3: controller tests adapted to the new `listar`
- `ExplotacionControllerTest`:
  - The first test no longer enables `gestoriaFilter`. It now checks that A and B each see only their own Explotaciones, which proves the explicit parameter works without the ambient filter. That is stronger than before.
  - New test: a disallowed `sort` returns 400 with a motivo.
- `TramiteControllerTest`: a `pagina(ResponseEntity)` helper asserts 200 and casts. New test: a disallowed `sort` returns 400 with a motivo.

## Behaviour by decision

**Decision 17: neutral motivo and explicit finders.**
- `procesarExplotacion` uses `findByNifAndGestoriaId` and `findByCodigoRegaAndGestoriaId`.
- `procesarAnimal` uses `findByCodigoRegaAndGestoriaId` and `findByCrotalAndGestoriaId`.
- A `DataIntegrityViolationException` on the Explotaciones or Animales sheets now gives: "No se ha podido guardar la fila: alguno de sus identificadores (código REGA, NIF o crotal) no está disponible."

**Decision 19: Javadoc of `ExplotacionImportFilaService`.**
- Over HTTP there is no outer transaction, so REQUIRES_NEW reuses the OSIV EntityManager, which already has the filter enabled.
- REQUIRES_NEW only opens a new EntityManager without the filter when there is an outer transaction: `@DataJpaTest`, a `@Transactional` caller, or a scheduler.
- `CLAUDE.md` is left for Task 7b.

**Decision 22: crotal normalization in the importer.**
- `ExplotacionImportService` normalizes the crotal with `CrotalNormalizador` before calling `procesarAnimal`. `crotalUltimosDigitos` is therefore derived from the normalized value.
- An invalid crotal becomes a row error carrying the `CrotalInvalidoException` motivo.
- A normalized value longer than 20 characters (`animal.crotal VARCHAR(20)`) gives "Crotal no válido: como máximo 20 caracteres".
- Existing data is not migrated.

**Decision 28: format for approving a NO_ENCONTRADO crotal.**
- The regexes are `ES[0-9]{12}` if the crotal starts with ES, otherwise `[A-Z]{2}[0-9]{8,12}`. They are applied to the normalized value.
- If the crotal does not match: 409 "El crotal X no está en el inventario y no tiene un formato completo válido."
- An incomplete crotal keeps its own motivo.
- The resolution classification (decision 20) does not change.

**Decision 27: optimistic version.**
- V17 plus `@Version`. The field starts as `null` on a new instance. With `0L`, Spring Data would call `merge` instead of `persist`.
- `version` is returned in `TramiteResponse` (list, aprobar, rechazar) and in `TramiteDetalleResponse` (detail, PATCH).
- PATCH and aprobar require `version`. Rechazar does not require it, but increments it.
- **Every accepted write leaves the version at exactly `versionRead + 1`** (`incrementarVersion`):
  1. First `flush()`. If the Tramite was dirty, Hibernate has already incremented the version.
  2. If not (only `tramite_crotal` changed, a PATCH with no changes, or the re-resolution on aprobar), it runs `update Tramite t set t.version = t.version + 1 where t.id=:id and t.gestoria.id=:gestoriaId and t.version=:version`, followed by `refresh`.
  3. If 0 rows are updated, it throws `ObjectOptimisticLockingFailureException`, which becomes 409 MOTIVO_CONCURRENCIA. That cannot happen while the row lock is held.
- **Why not `entityManager.lock(PESSIMISTIC_FORCE_INCREMENT)`:** this was my first implementation. It passed over HTTP, but in `@DataJpaTest` Hibernate 6.6 **silently skipped the UPDATE**. The SQL log showed no `update tramite` on the second PATCH, because the persistence context entry already had a lock of the same level. The explicit UPDATE does not depend on that internal state.
- **The re-resolution path (`noRollbackFor = ResolucionCrotalesCambiadaException`) persists the increment.** The E2E test reads `version` over JDBC after the 409 and sees `vista+1`.
- **Motivos:**
  - `MOTIVO_FALTA_VERSION` (controller, 400): "Falta la versión del trámite (campo version). Vuelve a cargarlo e inténtalo de nuevo."
  - `MOTIVO_VERSION_DESFASADA` (service, 409): "El trámite ha cambiado desde que lo abriste. Vuelve a cargarlo y revísalo antes de continuar."

## Order of checks

**PATCH `/tramites/{id}`:**
1. 400 if `version` is missing. This is in the controller, before any DB access, so it is identical for an own, foreign or non-existent trámite.
2. 400 if the tipo is invalid.
3. 404 from the gestoria-scoped lock-load.
4. 409 if the trámite is not `PENDIENTE_REVISION`.
5. **409 if the version differs**.
6. 404 for a foreign explotación, or 400 for an invalid crotal.
7. 409 for the same animal twice.
8. 200, version +1.

**POST `/tramites/{id}/aprobar`:**
1. **403 subscription**.
2. 400 if there is no body, the body is `{}`, or `version` is null. The body is `@RequestBody(required = false)` so that the 403 still comes first and the missing body gets our 400 with a motivo.
3. 404.
4. 409 estado.
5. **409 version**.
6. 409 resolution changed: committed, version +1.
7. 409 for the approval rules (full rollback).
8. 200, version +1.

**Decision: estado before version.**
- A trámite that is no longer pending returns its specific motivo ("Solo se puede aprobar…"), which is true whatever the version.
- The version is not needed to protect any write in that case: estado 409 writes nothing.
- This also keeps the concurrency test's guarantee intact. With both screens on the same version, the one that loses the lock sees APROBADO and gets "Solo se puede aprobar…".

A foreign trámite returns 404 whatever version is sent, including the correct one (tested).

## TDD evidence (red for the right reason before implementing)
- **Decisions 17, 22, 28 with the strengthened tests:**
  - `ExplotacionImportServiceTest` had 4 failures: unexpected rows, un-normalized crotal, different motivos.
  - `TramiteRevisionServiceTest` had 11 failures in the decision 28 cases: "Expecting code to raise a throwable". It approved crotales it should have rejected.
  - Both went green after implementing.
- **Decision 27:** I first added a compiling skeleton (V17, `@Version`, DTOs, signatures with `version` still ignored). Then I ran the new and adapted tests: **12 failures**, all for the right reason:
  - 200 where 400 or 409 was expected (missing or stale version).
  - `expected: 1L but was: 0L` (a crotal-only PATCH, or the re-resolution, did not increment).
  - The response `version` not updated.

  All existing tests, including the concurrency and TenantIsolation tests, stayed green with the skeleton.
- After implementing with `lock(PESSIMISTIC_FORCE_INCREMENT)`, 2 service tests stayed red. This is the Hibernate problem described above. They went green with the explicit UPDATE.

## Mutations tried (each restored afterwards; the file was checked after restoring)
1. **Forced increment disabled** (`incrementarVersion` returns early):
   - E2E `aprobarTrasUnCambioDeInventarioSoloApruebaConLaVersionNueva` fails ("la re-resolucion confirmada incrementa la version").
   - E2E `unPatchCorrectoIncrementaLaVersionAunqueSoloCambienLosCrotales` fails.
   - 2 service tests fail.
2. **`exigirVersion` removed from aprobar:**
   - 2 E2E tests fail (the second attempt with the old version, and the stale-version test).
   - 2 service tests fail.
   - 1 controller test fails.
3. **Version checked before estado in aprobar:** `dosAprobacionesSimultaneasDanExactamenteUn200YUn409` fails (motivo) and `elOrdenEs404LuegoEstadoLuegoVersion` fails.
4. **Decision 17 back to a bare finder:** this no longer compiles, because the methods were removed from the repositories.

## Tests added or adapted for decision 27 (existing tests not weakened)

- **Service:** `version(tramite)` reads the DB after a flush, and every call sends it. There are 6 new tests:
  - exact +1 for each kind of PATCH (tipo only, crotales only, explotación and crotales, no changes);
  - PATCH and aprobar with a stale, future or null version give 409, and nothing changes;
  - aprobar and rechazar each increment the version;
  - the re-resolution increments the version, and the old version no longer works;
  - the order is 404, then estado, then version.
- **Controller:**
  - aprobar and PATCH without a version: 400 with the same motivo for own, foreign and non-existent trámites.
  - 403 before 400.
  - 409 for a stale version.
- **E2E (TestRestTemplate, state read over JDBC):**
  - The mandatory test: the first aprobar after an inventory change returns 409, saves the new resolution and gives `version = vista+1`. The second attempt with `vista` returns 409 MOTIVO_VERSION_DESFASADA, with `foto` unchanged and nothing approved. GET returns `vista+1`. Using that version returns 200 APROBADO and `version = vista+2`.
  - Without a version: PATCH (field absent or null) and aprobar (no body, `{}`, `version:null`) all return 400 with the same motivo for own, foreign and non-existent (`999999`) trámites, with `foto` unchanged.
  - 403 before the missing-version 400.
  - PATCH and aprobar with a stale version (`v`) or a future one (`v+5`) return 409 with `foto` unchanged.
  - A crotal-only PATCH and a tipo-only PATCH are each exactly +1, in the response and in the DB.
  - Rechazar without a body returns 200 and +1.
  - A trámite of another Gestoría returns 404 with no body for the current version, `+1` and `+100`, with `foto` unchanged.
- **Adapted:**
  - `foto()` now includes `version`, so every existing "unchanged" check also covers it.
  - The `patch(...)` helper adds the current DB version (like a freshly loaded screen) unless the body already sets one. `aprobar(tramite, token)` does the same.
  - The approve-vs-approve concurrency test sends the same version from both screens.
  - The lock SQL test gained a crotal-only PATCH case: still exactly one `for update`. The forced increment is a plain UPDATE.
  - `TenantIsolationEndToEndTest`: B sends the **correct** version of A's trámite. The result must still be 404, with the estado and version unchanged.
  - `TramiteControllerErroresTest`: new signatures.
  - `ErrorDispatchEndToEndTest` was already sending `{"version":0}`.

## Final result
`JAVA_HOME="/c/Program Files/Java/jdk-23" ./mvnw clean test` gives **Tests run: 408, Failures: 0, Errors: 0, Skipped: 0, BUILD SUCCESS**.

The orchestrator's reference was 387 (a figure that did not compile with javac). The 21 new tests are:

| Test class | New tests |
|---|---|
| `ExplotacionControllerTest` | +1 |
| `TramiteControllerTest` | +5 (1 sort, 4 version) |
| `TramiteRevisionServiceTest` | +8 (2 decision 28 cases, 6 decision 27) |
| `TramiteRevisionEndToEndTest` | +7 |

## Control greps (`backend/src/main`)
- `findById(`: the only call is `AuthController` `/auth/me` with `principal.usuarioId()`, which comes from the JWT and is not attacker-controlled. It predates this task and is documented as safe in CLAUDE.md. The other hits are comments.
- `findByCodigoRega(`, `findByNif(`, `findByCrotal(`: **zero**. The methods no longer exist.
- `findByTelefono(`: only its declaration in `ContactoRepository`, with its Javadoc saying it is exclusive to the 3b webhook. There are no callers in main. In tests it is used only in `ContactoRepositoryTest` and in the `verify(..., never())` of the importer.
- New comments are ASCII (checked with grep `-P "[^\x00-\x7F]"` on comment lines of the touched files). The user-facing motivos keep their accents, like the existing ones.

## Deviations and risks
1. **The current frontend calls `POST /tramites/{id}/aprobar` without a body.** From now on it gets **400 `{motivo: "Falta la versión del trámite..."}`** until prompt A2 sends `version`, taken from `TramiteDetalleResponse.version`. The frontend has no PATCH either. A2 must also reload the detail and show the `motivo` on any 409 (decision 27, note for Task 7b).
2. **I removed `findByNif`/`findByCodigoRega`/`findByCrotal` from the repositories.** They were not in the brief. The only main-code caller was the importer, and removing them turns "never bare finders" into a compile-time guarantee. Five test lines were adapted.
3. **Decision 19 (Javadoc):** I corrected it in `ExplotacionImportFilaService` because the plan puts it in Task 7a. `CLAUDE.md` is left for 7b.
4. **Every accepted PATCH increments the version, even one with no changes** (all fields null). This keeps the rule simple and is documented in the code and tests. The side effect is that other open screens have to reload.
5. **The importer accepts an INCOMPLETE crotal (4 to 12 bare digits) as `Animal.crotal`**, as it did before, now normalized. Decision 22 only asks for "the same normalization". Requiring complete crotales in inventory would be a new rule for Antonio to decide.
6. **The neutral decision 17 motivo also covers any other constraint violation** on those sheets. In practice there is no other one: lengths are validated first, or the violation is the UNIQUE constraint. The wording ("alguno de sus identificadores ... no está disponible") could mislead if another constraint is ever added.
7. **The decision 28 rule is provisional** (other countries' lengths are unconfirmed). It must be documented in `CLAUDE.md` (Task 7b).
8. **`incrementarVersion` uses a JPQL bulk UPDATE and then `refresh`.** It is safe because it runs after `flush` and the row is locked. It must never be called with unflushed changes on the Tramite; the `flush()` at its start guarantees that.
9. **Maven without `clean` can pick up classes compiled by VS Code/ECJ** in `target/`. Always use `clean test` to validate.
10. **Not touched:** `frontend/`, `.agents/`, `.claude/skills/impeccable/`, `skills-lock.json`, `logo.jpg`, `PRODUCT.md`, `DESIGN.md`, `TipoTramite`, Stripe/Twilio/IA/`ovz`, `CLAUDE.md`, `ganera-prompts.md`, `progress.md`. No git add, commit, stash, reset or checkout.

## Fixes after review (I1, M1)

Antonio decided both fixes. Each was done test-first (TDD).

### I1: option (a), the decision 28 format also applies to EN_INVENTARIO crotales

**What changed**
- In `TramiteRevisionService.motivoCrotalNoAprobable`, the `EN_INVENTARIO` case now calls `tieneFormatoCompletoAprobable(fila.getCrotal())`.
  - It checks the **resolved** crotal (the Animal's), not the indicated one.
  - It is the same check already used for `NO_ENCONTRADO`, so there is a single source of truth.
- If the crotal does not match, the motivo is:
  "El crotal 1234 está en el inventario como 010000001234, que no es un crotal completo válido. Corrige el animal o vuelve a importar el inventario."
- This motivo accumulates with the other decision 25 motivos, in row order. It is a plain `TramiteConflictoException` with a full rollback, not `ResolucionCrotalesCambiadaException`.
- The resolution classification is unchanged: the row is still `EN_INVENTARIO` and still linked to the Animal.

**Tests**

`TramiteRevisionServiceTest` gained 3 tests:
- `aprobarUnCrotalEnInventarioConFormatoIncompleto...`: an Animal with crotal `010000001234`, with "1234" indicated. The result is 409 with that exact motivo. The exception is not `ResolucionCrotalesCambiada`. The estado stays `PENDIENTE_REVISION`, and the row is still `EN_INVENTARIO|010000001234|animalId`.
- `elCrotalDeInventarioIncompletoSeAcumulaConLosDemasMotivos`: the motivo is "Falta el tipo…", then the new motivo, then "4444 … incompleto".
- `aprobarUnCrotalEnInventarioConFormatoCompletoSePermite`: `ES010000001234` gives 200 APROBADO.

`TramiteRevisionEndToEndTest` gained 1 test:
- `aprobarUnCrotalDeInventarioConFormatoIncompletoDevuelve409YNoCambiaNada`, which runs over real HTTP.
- The Animal is seeded with `010000001234`, as an Excel file without the prefix would leave it.
- A PATCH resolves it as EN_INVENTARIO. Aprobar then returns 409 with the motivo.
- `foto()` read over JDBC (estado, explotación, tipo, **version**, crotales) is identical before and after.

**Existing tests**
- I reviewed every animal fixture: `setCrotal`/`nuevoAnimal` in `src/test`. All of them already use `ES` + 12 digits.
- No existing test was approving with an invalid-format inventory crotal, so I changed no test data.

**TDD evidence**
- Before implementing, 3 tests were red for the right reason:
  - the service test: "Expecting code to raise a throwable" (it approved);
  - the accumulation test: the message was missing the new motivo;
  - the E2E test: `expected 409 but was 200`.
- `...CompletoSePermite` was already green, as expected.

**Mutation**
- I made the check use the indicated crotal (`getCrotalIndicado()`) instead of the resolved one: 5 tests in `TramiteRevisionServiceTest` fail, including `...CompletoSePermite`, `aprobarUnTramiteCompletoLoDejaAprobado` and `aprobarConUnCrotalQueAhoraResuelveAOtroAnimal...`.
- Restored afterwards.

### M1: sort whitelist for `GET /contactos`

**What changed**
- `ContactoController.listar` now:
  - validates `CAMPOS_ORDENACION = {"nombre", "telefono", "id"}`;
  - returns 400 `{motivo: OrdenacionPermitida.MOTIVO}` when the sort is not allowed;
  - defaults to `@PageableDefault(sort = {"nombre", "id"})`;
  - returns `ResponseEntity<?>`.
- **Why these fields:**
  - All three are exposed by `ContactoResponse` and belong to the Contacto itself.
  - The whitelist excludes `activo` (already filtered by `incluirInactivos`), relations (`explotaciones`, `gestoria`) and internal fields (`createdAt`).
  - Default order: alphabetical by name, which is how a contact is looked for on screen, with `id` as the tie-break so the order is stable.

**Tests (`ContactoEndToEndTest`, +3)**
- `ordenarContactosPorUnCampoNoPermitidoDevuelve400ConMotivo`: `noExiste`, `explotaciones.x`, `gestoria.id`, `gestoria.nombre`, `createdAt`, and `nombre,desc&sort=gestoria.id` all return 400 with "Campo de ordenación no permitido.".
- `ordenarContactosPorCamposPermitidosFunciona`: `telefono,desc`, `id` and `nombre,desc` all return 200 in the correct order.
- `ordenPorDefectoDeContactosEsNombreYLuegoId`: two contacts both named "Ana" come out by id. Paging with `size=2` works across pages.

**TDD evidence**
- The invalid-sort test was red: `noExiste` returned **500** (`PropertyReferenceException`).
- The default-order test was red: the order was by insertion.
- The allowed-sort test was already green: Spring accepted those fields.

**Mutation**
- Without the `OrdenacionPermitida` check, the invalid-sort test goes back to 500, which is the observed red state.
- Without `@PageableDefault`, the default-order test fails, which is also the observed red state.

### Temporary files
- I deleted `svc.bak` from my session scratchpad (outside the repo). It was the mutation backup the reviewer mentioned.
- The mutation backup for this round was deleted after restoring.
- There are no `*.bak` files in the repo.
- The scratchpad's `rev/` folder is not mine (probably the reviewer's) and is outside the repo, so I left it alone.

### Final result
`JAVA_HOME="/c/Program Files/Java/jdk-23" ./mvnw clean test` gives **Tests run: 415, Failures: 0, Errors: 0, Skipped: 0, BUILD SUCCESS**.

That is 408 + 7:

| Test class | New tests |
|---|---|
| `TramiteRevisionServiceTest` | +3 |
| `TramiteRevisionEndToEndTest` | +1 |
| `ContactoEndToEndTest` | +3 |

New comments are ASCII. No git operations, and nothing touched outside backend/ except this report.

**For 7b:** `CLAUDE.md` needs to document the provisional decision 28 rule, which now covers both `EN_INVENTARIO` (using the Animal's crotal) and `NO_ENCONTRADO`, and the `/contactos` sort whitelist.
