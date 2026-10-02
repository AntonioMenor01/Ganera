# Prompt A1 - Task 6 report (PATCH /tramites/{id} + approval rule)

## Files

New (main, `backend/src/main/java/com/ganera/core/tramite/`):
- `TramiteRevisionService.java`: `@Transactional` `actualizar`, `aprobar`, `rechazar`, plus the motivo constants.
- `TramitePatchRequest.java`: `record(Long explotacionId, String tipoTramite, List<String> crotales)`. A null field means "don't change".
- `TramiteConflictoException.java`: its message is the motivo, returned as 409.

Modified (main):
- `CrotalNormalizador.java` (Step 0, Decision 24): strips `-`, `.` and `/` like whitespace. A value that is empty after stripping (`-`, `--`) is invalid.
- `TramiteRepository.java`: new `@Lock(PESSIMISTIC_WRITE) @Query("select t from Tramite t where t.id = :id and t.gestoria.id = :gestoriaId") findConBloqueoByIdAndGestoriaId`.
- `TramiteController.java`:
  - New `PATCH /tramites/{id}`.
  - `aprobar` and `rechazar` now delegate to the service. Their return type is `ResponseEntity<?>` and the success body is still `TramiteResponse`, including crotales.
  - A new constructor argument, `TramiteRevisionService`.
  - Exceptions are mapped in the controller, outside the transaction.
  - `tipoTramite` is parsed here: case-insensitive, stripped, invalid or blank gives 400 with a motivo.
  - Two new constants: `MOTIVO_TIPO_INVALIDO` and `MOTIVO_CONCURRENCIA`.

New (tests, `backend/src/test/java/com/ganera/core/tramite/`):
- `TramiteRevisionServiceTest` (25, `@DataJpaTest`): every rule and every exact motivo.
- `TramiteRevisionEndToEndTest` (22, RANDOM_PORT + TestRestTemplate, two Gestorías with overlapping suffixes). It reads "unchanged" state straight from the DB with `JdbcTemplate`, so it sees the real rollback and not a Hibernate cache.
- `TramiteControllerErroresTest` (1, plain JUnit). A test double of the service throws each concurrency exception, and the test checks that PATCH, aprobar and rechazar all return 409 with a motivo.
- `SqlCapturadoInspector` (test-only Hibernate `StatementInspector`, enabled by a property only in the revision E2E).

Modified (tests): see "Existing tests changed" below.

## Behaviour
- **PATCH**, in order:
  1. Lock-load scoped by gestoriaId; absent gives 404 with no body.
  2. Not `PENDIENTE_REVISION` gives 409 "Solo se puede editar un trámite pendiente de revisión."
  3. `explotacionId` is looked up with `findByIdAndGestoriaId`; absent or another Gestoría's gives 404. It is set **before** crotales are touched (review M3).
  4. `tipoTramite` is set if present.
  5. If `crotales` is present, `reemplazarCrotales` runs; otherwise, if the explotación changed, `recalcularEnlaces` runs.
  6. If two rows resolve to the same Animal: 409 "Los crotales 1234 y ES… corresponden al mismo animal (ES…)." (Decision 23).
  7. 200 with `TramiteDetalleResponse`.
- **aprobar**, in order:
  1. 403 from `puedeAprobarTramites`, checked in the controller as before.
  2. 404 (lock-load).
  3. 409 if not `PENDIENTE_REVISION` ("Solo se puede aprobar …").
  4. `recalcularEnlaces` always runs, so a stored resolution is never trusted.
  5. Every problem is collected into **one** 409 motivo, joined with spaces:
     - "Falta asignar la explotación."
     - "Falta el tipo de trámite."
     - One sentence per failing crotal:
       - "El crotal X es ambiguo (varios animales coinciden)."
       - "El crotal X no se puede comprobar porque el trámite no tiene explotación."
       - "El crotal X no está en el inventario y está incompleto."
     - The duplicate-animal sentence.

   `NO_ENCONTRADO` with a complete crotal is allowed (Decision 25). Zero crotales is fine. Only when there are no problems does the trámite become `APROBADO`. Accumulating the problems, instead of stopping at the first one, lets a SIN_EXPLOTACION crotal be named alongside "Falta asignar la explotación".
- **rechazar**: 404, then 409 if not `PENDIENTE_REVISION` ("Solo se puede rechazar …"), then `RECHAZADO`. It has no data requirements.
- Nothing calls `OvzAutomationService`, and `TipoTramite` is unchanged.

## Rollback choice for approval 409s
**The whole approve transaction rolls back on any 409.** The re-resolution that `recalcularEnlaces` wrote is discarded as well. E2E `aprobarReResuelveLosCrotalesYUn409NoGuardaNadaDeLaReResolucion` proves it:
- A new animal makes "1234" ambiguous, so approval returns 409.
- The DB row is still `1234|ES980000011234|EN_INVENTARIO` and the estado is still `PENDIENTE_REVISION`.

A consequence: the review screen can still show the old resolution until the next PATCH or GET. The 409 motivo tells the user what changed.

## Exceptions (Decision 26 / review M2)
- `RecursoNoEncontradoException`, `CrotalInvalidoException` and `TramiteConflictoException` propagate **out** of the `@Transactional` service, so the transaction rolls back. `TramiteController.traducirErrores` maps them to 404, 400 with motivo, and 409 with motivo. Nothing is caught inside the transaction.
- `ConcurrencyFailureException` (pessimistic, cannot-acquire-lock, optimistic) and `DataIntegrityViolationException` map to 409 `MOTIVO_CONCURRENCIA`. `TramiteControllerErroresTest` covers four exception types on all three endpoints.

## Lock verification
- **SQL check:** E2E `patchAprobarYRechazarCarganElTramiteConBloqueoDeFila` captures SQL through the StatementInspector. It asserts exactly one `... from tramite ... for update` for each of PATCH, aprobar and rechazar. The actual H2 SQL, seen in the log, is `select t1_0.id,... from tramite t1_0 where t1_0.gestoria_id = ? and t1_0.id=? and t1_0.gestoria_id=? for update`. The first `gestoria_id` is the ambient filter; the second is the explicit parameter.
- **Deterministic concurrency test:** E2E `patchConcurrenteEsperaAlBloqueoYVeElEstadoConfirmadoPorLaOtraTransaccion`.
  1. A raw JDBC transaction runs `SELECT … FOR UPDATE` on the row.
  2. The PATCH is sent from another thread.
  3. The test polls `INFORMATION_SCHEMA.SESSIONS.BLOCKER_ID` until H2 shows the PATCH waiting.
  4. Only then does the holder set `estado='APROBADO'` and commit.
  5. The PATCH then sees APROBADO and returns 409 "Solo se puede editar…", with `tipo_tramite` untouched.
- **Mutation, `@Lock` removed:**
  - The SQL test fails (expected 1, was 0).
  - The concurrency test fails (expected 409, was 200): without the lock, the PATCH overwrites a concurrent approval.
  - The annotation was restored afterwards.

## TDD evidence
- **Step 0:** the 3 new normalizer tests plus 5 new invalid parameters (`-`, `--`, `.`, `/`, `" - / . "`) were written first. The run was red with 1 failure and 2 errors, then green after the change.
  - Moved out of the invalid list: `"ES-12"` and `"ES.1234"`, which are now valid (asserted `ES-12` → `ES12` COMPLETO).
  - `TramiteCrotalServiceTest.reemplazarConUnCrotalInvalidoNoCambiaNada` used `"ES-12"` as its invalid sample; it now uses `"ES_12"`, so it still proves "invalid, so nothing changes".
- **Service and controller tests** were written before `TramiteRevisionService` and the new controller existed:
  - The service test did not compile (types missing).
  - `TramiteControllerTest` ran red, 26/26 errors ("constructor TramiteController(..., TramiteRevisionService) is undefined").
  - Both went green after implementation.
- **E2E:** the first run had 2 real failures:
  - My snapshot helper used case-sensitive keys while H2 returns upper-case column names. Fixed with lower-cased keys plus a `containsOnlyKeys` guard, so "unchanged" snapshots are now meaningful.
  - The lock-timeout variant (below) failed.
- **Mutation, unscoped explotación lookup** (`findById(explotacionId)` in place of `findByIdAndGestoriaId`):
  - `TramiteRevisionServiceTest.asignarUnaExplotacionDeOtraGestoriaEsNoEncontrado` and `TramiteControllerTest.patchConExplotacionDeOtraGestoriaDevuelve404SinCuerpo` fail.
  - The mandatory E2E case still returns 404 with nothing persisted, because Task 5's defensive check in `TramiteCrotalService.comprobarTramiteDeLaGestoria` also fires, followed by a full rollback. That is two independent layers.
  - The lookup was restored afterwards.

## Full suite
`JAVA_HOME="/c/Program Files/Java/jdk-23" ./mvnw test` → **Tests run: 352, Failures: 0, Errors: 0, Skipped: 0, BUILD SUCCESS**.

The 64 new tests over the previous 288 break down as:

| Test class | New tests |
|---|---|
| CrotalNormalizadorTest | +7 |
| TramiteControllerTest | +9 |
| TramiteRevisionServiceTest | +25 |
| TramiteRevisionEndToEndTest | +22 |
| TramiteControllerErroresTest | +1 |

## Existing tests changed and why
- `TramiteControllerTest`:
  - The constructor now takes `TramiteRevisionService`, and `@Import` includes it.
  - `ResponseEntity<TramiteResponse>` became `ResponseEntity<?>`, with casts.
  - `aprobarCambiaEstadoCuandoLaSuscripcionLoPermite` now seeds an explotación and a tipo, because its intent is a successful approval.
  - `aprobarUnTramiteDeOtraGestoriaDevuelve404YNoLoCambia` also seeds an explotación and a tipo. The foreign trámite would be approvable, so the 404 can only come from isolation.
  - 9 new tests: PATCH 200/400 tipo/400 crotal/404 foreign explotación/404 foreign trámite/409 estado; approve 409 motivo; approve and reject from APROBADO give 409; 403 wins over 409.
- `TenantIsolationEndToEndTest`:
  - `@AfterEach` deletes `tramite_crotal` before `tramite` (review M4).
  - `aprobarTramiteDeOtraGestoriaDevuelve404YNoCambiaSuEstado` now seeds explotación and tipo, so a leak would be a 200 APROBADO rather than a 409 that could mask it. The 404 plus "state unchanged" assertions are unchanged.
- `CrotalNormalizadorTest` and `TramiteCrotalServiceTest`: see Step 0 above.

## Deviations / concerns
1. **H2 lock timeout cannot be tested end to end.**
   - My first concurrency test held the lock past H2's 2 s lock timeout. H2 throws `JdbcSQLTimeoutException`, and HikariCP evicts any connection that raises a `SQLTimeoutException`. The rollback then fails with `JpaSystemException: Unable to rollback … Connection is closed`, which escapes as 500 (seen as 401, because `/error` is protected).
   - This is an H2+Hikari artifact. In PostgreSQL, Hibernate only supports NOWAIT/SKIP LOCKED for lock timeouts, so the default `FOR UPDATE` waits and the requests serialize. That is exactly what the rewritten test proves.
   - I set no lock timeout hint.
   - If a Postgres `lock_timeout`/`statement_timeout` is ever configured, re-check this path: Postgres `57014` may also be delivered as an `SQLTimeoutException` and Hikari may evict the connection.
2. **`tipoTramite` is validated in the controller, before the 404/409 checks.** An invalid tipo on another Gestoría's trámite returns 400, not 404. This leaks nothing about existence, but the ordering differs from the crotal validation, which happens after the 404/409 checks.
3. **The PATCH cannot clear the explotación or the tipo**, because null means "don't change". An empty `crotales: []` does remove all crotales.
4. **Frontend (not changed):** `TramiteReviewDialog.tsx` handles 403 specially. Any other error on aprobar/rechazar, including the new 409 `{motivo}` (missing explotación/tipo, crotal problems, wrong state), shows the generic "No se ha podido aprobar/rechazar el trámite. Inténtalo de nuevo." The frontend should show `motivo` on 409, and there is no PATCH UI yet. Flagged for a later frontend prompt.
5. **Wording of motivos:**
   - All motivos end with a period.
   - The ambiguous and incomplete crotal motivos use `crotalIndicado`.
   - The duplicate motivo names the indicated crotales and the animal's full crotal.
6. **Grep:** there is no `findById(`, `findByCodigoRega(`, `findByNif(`, `findByTelefono(` or `findByCrotal(` in new or modified tramite code; the only hits are pre-existing Javadoc comments. Every new query takes an explicit gestoriaId. New comments are ASCII. The pre-existing non-ASCII "ß" comment in `CrotalNormalizador` (Task 5) was left untouched.
7. **Not touched:** Stripe, Twilio, IA, importer, contactos, ganaderos, `TipoTramite`, frontend, `.agents/`, skills, `skills-lock.json`, `logo.jpg`, `PRODUCT.md`, `DESIGN.md`. Nothing staged or committed.

## Fixes after review (I1, M3, M6)

Only I1, M3 and M6 were changed, as instructed. I2, M1, M4 and M5 were not touched.

### I1: aprobar never approves a resolution the reviewer did not see
- **New `ResolucionCrotalesCambiadaException extends TramiteConflictoException`.** The controller already maps its superclass to 409 `{motivo}`, and it still propagates out of the service.
- **How `aprobar` works now:**
  1. Before `recalcularEnlaces`, it snapshots `(crotal, animalId, resolucion)` for every row.
  2. After re-resolving, it compares each row with its snapshot.
  3. If any row changed, it throws the new exception with this motivo:
     `"La resolución de los crotales ha cambiado desde la revisión (inventario actualizado): 1234 antes ES980000011234, ahora ES980000021234. Revisa el trámite antes de aprobarlo."`
     Several changes are joined with "; ". The "ahora" part is the full crotal, or "ambiguo" / "no está en el inventario" / "sin explotación".
- **Only this exception keeps the re-resolution.** `aprobar` is now `@Transactional(noRollbackFor = ResolucionCrotalesCambiadaException.class)`, so the new resolution is committed and `GET /tramites/{id}` shows it. The estado stays `PENDIENTE_REVISION`, because it is only set after all checks pass. The rows are flushed explicitly before the throw, so a write failure surfaces at that point and not during the commit.
- **Everything else is unchanged.** All other 409s (missing explotación or tipo, Decision 25 crotal problems, duplicate animal, wrong estado) still roll back fully.
- **When nothing changed,** the normal rules apply. A second `aprobar` after the specific 409 follows the normal rules.
- **Changed tests:**
  - Replaced service test `aprobarVuelveAResolverLosCrotalesContraElInventarioActual` with `aprobarConUnCrotalQueAhoraEsAmbiguoEsConflictoDeResolucionCambiada`. It expects the new motivo, and the row is AMBIGUO afterwards.
  - Replaced E2E `aprobarReResuelveLosCrotalesYUn409NoGuardaNadaDeLaReResolucion`, whose rollback expectation is superseded by this fix, with `aprobarConUnCrotalQueAhoraEsAmbiguoGuardaLaNuevaResolucionYDevuelve409`:
    1. The first `aprobar` returns 409 with the new motivo. The DB row is now `1234|1234|AMBIGUO` and GET shows AMBIGUO.
    2. The second `aprobar` returns the normal 409 "El crotal 1234 es ambiguo…" with a full rollback.
- **New tests:**
  - **E2E scenario A** (`aprobarNoApruebaEnSilencioUnAnimalDistintoDelRevisado`):
    1. PATCH `"1234"`, which resolves to X.
    2. Move X to A2 via JDBC and add Y (`...21234`) to A1.
    3. `aprobar` returns 409 with the new motivo. The estado is unchanged and GET shows Y.
    4. The second `aprobar` returns 200 APROBADO.
  - **E2E scenario B** (`aprobarNoApruebaEnSilencioUnCrotalQueHaSalidoDelInventario`):
    1. PATCH the complete `"ES980000011234"`, which resolves EN_INVENTARIO.
    2. Move the animal out via JDBC.
    3. `aprobar` returns 409 with "... ahora no está en el inventario". GET shows NO_ENCONTRADO.
    4. The second `aprobar` returns 200, because Decision 25 allows a complete crotal that is NO_ENCONTRADO.
  - **Service tests** for scenario A (including the second attempt returning APROBADO) and scenario B.
- **TDD:**
  - The 3 new or changed service tests failed and the 3 E2E tests failed before the fix.
  - Mutation: removing `noRollbackFor` makes all 3 E2E I1 tests fail (GET shows the stale resolution). Restored afterwards.

### M3: refresh after the lock
- `cargarConBloqueo` (used by `actualizar`, `aprobar` and `rechazar`) now calls `entityManager.refresh(tramite)` after the locked finder. The `EntityManager` is injected with `@PersistenceContext`, so the constructor is unchanged.
- **New test** `aprobarUsaElEstadoActualDeLaBdAunqueElTramiteYaEstuvieraCargado` (`@DataJpaTest`):
  1. The trámite is managed in the persistence context.
  2. A JDBC update sets it to APROBADO in the DB, so the cached instance is stale.
  3. `aprobar`, `rechazar` and `actualizar` each return their "solo pendiente" 409.
- **TDD:** the test failed before the fix. Mutation: removing the refresh makes it fail again. Restored afterwards.
- **SQL for one approve** (show-sql):
  1. `select … from tramite t1_0 where t1_0.gestoria_id = ? and t1_0.id=? and t1_0.gestoria_id=? for update` (the single `FOR UPDATE`, gestoria-scoped).
  2. `select … from tramite t1_0 where t1_0.id=?`, the plain refresh by PK. It re-reads a row that was already resolved through the gestoria-scoped locked query, so it is not an attacker-controlled bare `findById`.
  3. `update tramite …`.
- The existing SQL test still asserts exactly one `for update` per endpoint.

### M6: approve-vs-approve concurrency
- **New E2E** `dosAprobacionesSimultaneasDanExactamenteUn200YUn409`:
  1. A raw JDBC transaction locks the row.
  2. Two HTTP approvals are sent concurrently.
  3. The test polls `INFORMATION_SCHEMA.SESSIONS.BLOCKER_ID` until at least 2 sessions are blocked.
  4. The lock is released (rollback).
  5. The result is exactly `{200, 409}` in either order. The 409 motivo is "Solo se puede aprobar un trámite pendiente de revisión." and the DB estado is APROBADO.
- It passed on first run: the existing lock already covers this case, and the test documents the guarantee.

### Full suite after fixes
`JAVA_HOME="/c/Program Files/Java/jdk-23" ./mvnw test` → **Tests run: 358, Failures: 0, Errors: 0, Skipped: 0, BUILD SUCCESS**.

That is 352 + 6:

| Test class | Before | After | Net |
|---|---|---|---|
| TramiteRevisionServiceTest | 25 | 28 | +4 new, −1 replaced |
| TramiteRevisionEndToEndTest | 22 | 25 | +4 new, −1 replaced |

### Other
- **Files touched:** `TramiteRevisionService.java`, new `ResolucionCrotalesCambiadaException.java`, `TramiteRevisionServiceTest.java`, `TramiteRevisionEndToEndTest.java` (gained a `get` helper).
- **Grep:** no bare `findById(`, `findByCodigoRega(`, `findByNif(`, `findByTelefono(` or `findByCrotal(` in the changed code. New comments are ASCII.
- **UX note for the frontend** (not changed): a user who clicks Aprobar after an inventory change now gets a 409 telling them to review. The dialog should re-fetch the detail on that 409 so the fresh resolution appears. Today it shows the generic error, see concern 4 above.
- Nothing staged or committed.
