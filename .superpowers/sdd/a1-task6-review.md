# Prompt A1 - Task 6 review (PATCH /tramites/{id} + approval rule) - independent reviewer

VERDICT: Changes required. Only I1 blocks: it is a small change to `aprobar`. I2 is a product decision for Antonio. Everything else is Minor.

## Test run
`JAVA_HOME="/c/Program Files/Java/jdk-23" ./mvnw test` from `backend/`:
**Tests run: 352, Failures: 0, Errors: 0, Skipped: 0. BUILD SUCCESS.** This matches the report.

| Test class | Tests |
|---|---|
| TramiteRevisionServiceTest | 25 |
| TramiteRevisionEndToEndTest | 22 |
| TramiteControllerTest | 26 |
| TramiteControllerErroresTest | 1 |
| CrotalNormalizadorTest | 30 |
| TramiteCrotalServiceTest | 18 |
| TramiteCrotalEndToEndTest | 4 |
| TenantIsolationEndToEndTest | 7 |

## Verified, no finding
1. **PATCH** (`TramiteRevisionService.actualizar`).
   - A trámite of another Gestoría gets 404: the lock query takes `gestoriaId` as a real parameter.
   - A trámite outside `PENDIENTE_REVISION` gets 409.
   - An explotación of another Gestoría gets 404 via `findByIdAndGestoriaId`. The lookup throws before any write. E2E `foto()` reads the trámite row and its crotal rows through JDBC and shows them unchanged.
   - The explotación is set before `reemplazarCrotales` (Task 5 review M3).
   - A crotal replace re-resolves the crotales. Changing only the explotación triggers `recalcularEnlaces`.
   - Decision 23 (duplicate animal) returns 409. The rollback of the delete+flush+inserts is proven by E2E: the crotal rows still read `5678|5678|AMBIGUO`.
   - An invalid crotal returns 400 and leaves the rows unchanged. An invalid tipo returns 400 with a motivo.
   - Parsing tipo in the controller, instead of relying on Jackson's enum 400, is a good deviation. It avoids the /error to 401 problem (see M1).
2. **Approval order.**
   - The order is: 403 subscription (controller), then 404, then 409 estado, then re-resolve, then one accumulated 409.
   - The Decision 25 matrix is correct: AMBIGUO, SIN_EXPLOTACION, and NO_ENCONTRADO+INCOMPLETO block; NO_ENCONTRADO+COMPLETO and zero crotales pass.
   - Each failing crotal is named with its reason.
   - Every 409 leaves the estado unchanged, and E2E confirms it by reading the DB.
   - Approval never depends on the ambient filter alone: the lock query, the crotal rows and every Animal lookup take an explicit `gestoriaId`.
3. **Rechazar** returns 404 or a 409 for a non-pending trámite. It has no other requirement.
4. **Exceptions.**
   - `RecursoNoEncontrado`, `CrotalInvalido` and `TramiteConflicto` all propagate out of the `@Transactional` proxy. Nothing is caught inside, so there is no `UnexpectedRollbackException` path.
   - On rollback, Spring clears the pre-bound OSIV EntityManager, so no dirty state can flush later in the request.
   - Commit-time and flush-time exceptions are raised inside the supplier and translated there: repository proxies and `JpaTransactionManager` both translate them.
   - The following all map to 409 `MOTIVO_CONCURRENCIA`: `PessimisticLockingFailure`, `CannotAcquireLock` (Postgres `55P03` / `LockTimeoutException`, and `40P01` deadlock), `ObjectOptimisticLockingFailure`, and `DataIntegrityViolation`.
   - I found no other `DataIntegrityViolation` source that is not concurrency-related: `Animal.crotal` is VARCHAR(20), which is at most 30, and nothing deletes explotaciones or animals.
5. **Lock.**
   - Over HTTP, OSIV binds an EntityManager but no transaction. The service's `@Transactional` begins a real JDBC transaction on that EntityManager, so `FOR UPDATE` holds until commit or rollback. The captured SQL confirms `... from tramite ... for update` exactly once for each endpoint.
   - The concurrency test is sound. The lock holder releases only after H2 reports a blocked session. The PATCH then sees the committed `APROBADO` and returns 409 without touching `tipo_tramite`. The mutation evidence (without `@Lock` the result is 200, an overwrite) is credible.
   - On PostgreSQL READ COMMITTED, a waiting `SELECT ... FOR UPDATE` re-reads the latest committed row version, so the same serialization holds. This is what prevents a double approval, and so a double submission to OVZ, once 3c exists.
6. **Modified tests** are strengthened, not weakened.
   - The cross-tenant approve tests in `TenantIsolationEndToEndTest` and `TramiteControllerTest` now seed an *approvable* trámite. A leak would show up as a 200, not be hidden behind a 409.
   - The 404 and "unchanged" assertions are intact.
   - The `tramite_crotal` cleanup fixes Task 5 review M4.
   - The `CrotalNormalizadorTest` changes are consistent with Decision 24. `-`, `.` and `/` are stripped, so `ES-12` becomes `ES12` (COMPLETO), and a value that is only separators is invalid.
   - `TramiteCrotalServiceTest` still proves "an invalid value changes nothing", now with `ES_12`.
7. **Invariants.**
   - Nothing outside the `ovz` package references `OvzAutomationService`, except two comments.
   - `TipoTramite` is unchanged (empty diff).
   - Task 6 code has no bare `findById`, `findByCodigoRega`, `findByNif`, `findByTelefono` or `findByCrotal`. `findFirstByTramiteIdOrderByCreatedAtDesc` targets `MensajeCampo`, which is not tenant-scoped, and only after the trámite has been resolved through the gestoría scope.
8. **Scope.** `frontend/` is untouched, and nothing is staged or committed.

## Critical
None.

## Important

**I1. Approval can silently approve a different animal, or a different resolution, than the one the reviewer saw. A 409 also leaves the review screen showing a stale resolution.**
- Where: `TramiteRevisionService.java:114-134`.
- Background: re-resolving on approve is a good idea. But the successful path accepts whatever the re-resolution produced without surfacing any change. The importer really can move animals between explotaciones: `ExplotacionImportFilaService.procesarAnimal` finds by `crotal` and then calls `animal.setExplotacion(nueva)`.
- Scenario A:
  1. A trámite on explotación A1 has `"1234"`, which resolves EN_INVENTARIO to X (`ES980000011234`). The reviewer sees this crotal on screen.
  2. A re-import moves X to A2 and adds Y (`ES980000021234`) to A1.
  3. On "Aprobar", `"1234"` re-resolves to Y (single candidate), EN_INVENTARIO, and the result is **200 APROBADO for Y**, an animal the reviewer never saw.
  4. In 3c, Y is what gets sent to OVZ.
- Scenario B: the reviewer saw a complete crotal as EN_INVENTARIO. The animal has since left the explotación. Re-resolution gives NO_ENCONTRADO+COMPLETO, and the trámite is approved anyway. For a BAJA, the reviewer believed the animal was in inventory.
- Rollback side effect:
  - When the re-resolution *does* cause a 409 (for example, a crotal becomes AMBIGUO), the whole transaction rolls back and `tramite_crotal` keeps the old EN_INVENTARIO row.
  - `GET /tramites/{id}` then still shows `1234 -> ES980000011234, enInventario=true`, while the 409 says "es ambiguo".
  - The only way to refresh is a PATCH that resends the list. The current UI has no PATCH, so the user sees a contradiction.
- Suggested fix:
  - In `aprobar`, snapshot `(crotal, animalId, resolucion)` for each row before `recalcularEnlaces`.
  - If any row changed, throw a dedicated 409 subtype, for example `ResolucionCrotalesCambiadaException` with the motivo "La resolución de los crotales ha cambiado desde la última revisión (1234: ahora ES…/ambiguo…). Revísalo antes de aprobar."
  - Declare `@Transactional(noRollbackFor = ResolucionCrotalesCambiadaException.class)` on `aprobar` only. The re-resolution is then persisted and the detail screen shows the fresh state. The estado is untouched because it is only set after all checks.
  - The ordinary Decision 25 409s can keep the full rollback. Once a change is persisted, the stored resolution is already current for them.
  - Add E2E tests for scenario A, which must return 409 with the new row persisted, and for scenario B.

**I2 (decision for Antonio, not an implementation defect). The approval rule's notion of "complete" is much looser than "OVZ needs the full crotal".**
- Where: `TramiteRevisionService.java:180-182`, which reuses the Decision 20 classifier in `CrotalNormalizador.java:512-518`.
- Anything that starts with two letters, or any other non-digits-only form, is COMPLETO: `ES12`, `ES1234`, `A1234`, `12A34`.
- Scenario: a gestoría user types `ES1234`, thinking of the "last digits with the prefix". No animal matches, so the result is NO_ENCONTRADO+COMPLETO, and **approval returns 200**. In 3c this sends an impossible crotal to OVZ.
- This matches the literal wording of Decision 25 ("incompleto (solo dígitos)") but not its rationale.
- Suggestion: for NO_ENCONTRADO to be approvable, require a plausible full crotal, such as `^[A-Z]{2}[0-9]{12}$` (Spanish bovine) or an explicit whitelist of formats. Everything else would get the motivo "no está en el inventario y no parece un crotal completo". The classifier used for *resolution* can stay as it is.

## Minor

**M1. A malformed PATCH body returns 401, not 400, and the frontend treats a 401 as a logout. This is pre-existing and systemic, but newly reachable.**
- Where: `SecurityConfig` does not permit `/error`, and `JwtAuthenticationFilter` sets the SecurityContext directly, so the ERROR dispatch runs unauthenticated. The implementer saw the same thing for a 500 becoming a 401.
- Examples that would return 401: `{"crotales":"1234"}`, `{"explotacionId":"abc"}`, a missing body, or a non-numeric `{id}`.
- `httpClient`'s 401 interceptor would call `notifyUnauthorized()` and log the user out.
- Suggestion (Task 7 or a follow-up): `permitAll("/error")`, or a small handler for `HttpMessageNotReadableException` and `MethodArgumentTypeMismatchException` that returns 400 `{motivo}`.

**M2. Lock timeout classification.** The implementer's analysis is correct: the 500 is an **H2+Hikari artifact**. H2 throws `JdbcSQLTimeoutException`, which is an `SQLTimeoutException`, so Hikari evicts the connection and the rollback then fails.
- On PostgreSQL with no `lock_timeout`, the `FOR UPDATE` waits without limit. The wait is bounded in practice only by the holder's transaction, which today is short (no I/O).
- Two forward risks:
  - If 3c ever runs Playwright inside this locked transaction, other editors and approvers of that trámite block for the whole OVZ session, each one holding a pooled connection. Keep the OVZ call outside the lock, for example by approving (commit) and then executing asynchronously.
  - If a `statement_timeout` is ever configured, `57014` translates to Spring `QueryTimeoutException`. That is not a `ConcurrencyFailureException`, so it would be a 500. A `lock_timeout` (`55P03`) arrives as a `PSQLException`, not an `SQLTimeoutException`, so Hikari does not evict the connection and it maps to `CannotAcquireLock`, which gives 409.
- Document both in Task 7.

**M3. First-level-cache fragility of the lock read.**
- Where: `TramiteRepository.java` (`findConBloqueoByIdAndGestoriaId`).
- If any future code loads the Tramite into the same OSIV EntityManager *before* the service call (an interceptor, or a controller pre-check with `findByIdAndGestoriaId`), Hibernate still issues `FOR UPDATE` but returns the cached, possibly stale instance without refreshing it. The estado check could then pass on stale data.
- Nothing does this today. Add a comment on the finder or the service ("must be the first load of this Tramite in the request"), or `refresh` after locking.

**M4. PATCH and rechazar are not subscription-gated.**
- `CLAUDE.md` calls `TRIAL_EXPIRADO_SIN_PAGO` and `SUSPENDIDA` "read-only", but only `aprobar` is gated. This is consistent with the existing rechazar and Excel import, and with the plan.
- PATCH is a new write, however, so confirm with Antonio and word `CLAUDE.md` accordingly in Task 7: "no approvals", not "read-only".

**M5. Frontend implications (out of scope, but note them in Task 7 docs and ganera-prompts).**
- `TramiteReviewDialog.tsx` shows Aprobar and Rechazar for **every** estado.
- Every new 409 falls through to "No se ha podido aprobar/rechazar el trámite. Inténtalo de nuevo.", including: missing explotación or tipo, crotal problems, wrong estado and concurrency. "Try again" is misleading when the request can never succeed.
- The UI has no PATCH, so a trámite with `explotacion=null` cannot be made approvable from the UI.
- The frontend should render `motivo` on a 409, and hide or disable the buttons outside `PENDIENTE_REVISION`.

**M6. Test robustness notes.** None of these makes a current test unsound.
- `sesionesEsperandoUnBloqueo` counts *any* blocked H2 session, not specifically the PATCH's. It is fine with serial Surefire, but it could pass spuriously if test parallelism is ever enabled.
- There is no approve-vs-approve concurrency E2E. That is the case that matters most for 3c (double submission to OVZ). The same lock covers it, but a second test with the holder approving and the waiter calling `/aprobar` would document the intent.
- `TramiteControllerErroresTest` proves the mapping only for exceptions that are already Spring-translated. That is acceptable, because the repository and transaction-manager translation is standard.

**M7. Documented behaviour to carry into the Task 7 docs.**
- PATCH cannot clear the explotación or the tipo (`null` means "don't change"). `crotales: []` does clear the crotales.
- An invalid tipo gets 400 before the 404/409 checks. This does not leak existence.
- If stored crotal rows collide after an explotación change, a PATCH without `crotales` returns 409 and the user must send both fields together.

---

# Re-review (fixes for I1, M3, M6)

VERDICT: Approved. The fixes are correct. One residual Important item, R1, must be tracked before 3c or the frontend prompt; it is not an A1 backend blocker.

## Test run
`JAVA_HOME="/c/Program Files/Java/jdk-23" ./mvnw test` from `backend/`: **Tests run: 358, Failures: 0, Errors: 0, Skipped: 0. BUILD SUCCESS.**
- TramiteRevisionServiceTest: 28
- TramiteRevisionEndToEndTest: 25
- The total is 352 + 6, which matches the report.

## I1: verified
- **Snapshot.** `ResolucionVista` copies `(crotal, animalId, resolucion)` by value before `recalcularEnlaces` changes the same managed instances. The copy is therefore not aliased, and the comparison really is old against new.
- **Rows added or removed.** This cannot happen inside the transaction. `recalcularEnlaces` only updates existing rows, and every other writer of `tramite_crotal` (PATCH) takes the same row lock on the trámite first. The `antes == null` case is still handled defensively.
- **Explotación null.** Every row resolves to SIN_EXPLOTACION both before and after, so there is no change and the normal 409 "Falta asignar la explotación" applies with a full rollback. PATCH cannot clear the explotación, so a stored EN_INVENTARIO row with a null explotación cannot occur.
- **Crotal field.** For non-EN_INVENTARIO rows, `crotal` equals `crotalIndicado`, which is stable, so comparing it adds no false positives.
- **Scope of `noRollbackFor`.**
  - It is only on `aprobar`, and only for `ResolucionCrotalesCambiadaException`.
  - The exception is thrown in the outer method after the inner `@Transactional` calls (`recalcularEnlaces`, repository calls) have returned normally, so nothing has marked the transaction rollback-only and the commit succeeds.
  - Every other exception in the method still rolls back fully: `TramiteConflictoException` (state, Decision 25, duplicate animal), `RecursoNoEncontrado`, `CrotalInvalido`, and the concurrency exceptions. `noRollbackFor` matches by type and subclass, and the superclass `TramiteConflictoException` is not covered.
  - The two throws are mutually exclusive, because the method throws at the first. A resolution-changed 409 therefore never also carries Decision 25 motivos. That is fine: the retry evaluates them.
- **Flush failure.**
  - `tramiteCrotalRepository.flush()` runs inside the repository's participating transaction. A `DataIntegrityViolation` or `ConcurrencyFailure` there marks the transaction rollback-only and propagates as itself, not as the `noRollbackFor` type. The result is a full rollback and 409 `MOTIVO_CONCURRENCIA`.
  - Because the flush is forced first, a commit-time failure after the resolution-changed throw is very unlikely: only the already-flushed rows are pending.
  - If the commit itself did fail, Spring would throw the commit exception in place of the application exception. That would be a `TransactionSystemException` (500) or a translated `DataAccessException` (409). This is theoretical only.
- **Persisted and visible.** The E2E tests read `GET /tramites/{id}` and JDBC after the 409 and see the new row. The estado stays `PENDIENTE_REVISION`, because `setEstado` comes after every check. The mutation evidence (without `noRollbackFor` the old resolution comes back) is consistent with the code.
- **No silent 200 within a single request.** Any difference between the stored and re-resolved state throws before the approval block.

## M3: verified
- `entityManager.refresh(tramite)` runs on the instance returned by `findConBloqueoByIdAndGestoriaId`. That instance's id was just matched with `gestoriaId` as a real query parameter, in the same transaction, under our own row lock. `gestoria_id` is `updatable=false`, and the lock blocks every other writer.
- The follow-up `select … where id=?` therefore re-reads the same row, which is owned by the caller's Gestoría. It cannot reach another Gestoría's row, because the id is never taken unverified from the path.
- The refresh happens before any modification, so no pending change is discarded. The SQL test still asserts exactly one `for update`, and the new `@DataJpaTest` covering a stale cached instance with a JDBC update is a real regression guard.

## M6: verified
- The raw JDBC holder locks the row. Both approvals are released only after H2 reports at least 2 blocked sessions, and the test asserts that neither has completed before then. The result is `{200, 409}` in any order, with the correct motivo and estado APROBADO. The design is deterministic.
- One robustness note, M-R2 below, concerns timing against H2's lock timeout.

## Findings

### Critical
None.

### Important
**R1. The "approve only what the reviewer saw" guarantee holds per request, not per reviewer.** It is closed only once the client proves which version it saw.
- Where: `TramiteRevisionService.aprobar` together with `frontend/src/features/tramites/TramiteReviewDialog.tsx`.
- The server compares against the *stored* resolution, and the first resolution-changed 409 commits the new one. The next approve therefore passes, whoever clicks it and whatever their screen shows.
- Scenario 1 (current frontend):
  1. The dialog shows X.
  2. Aprobar returns 409 (resolution changed). The dialog shows the generic "No se ha podido aprobar el trámite. **Inténtalo de nuevo.**" and does not re-fetch.
  3. The user clicks Aprobar again and gets **200 for Y, which they never saw**. The E2E `aprobarNoApruebaEnSilencioUnAnimalDistintoDelRevisado` asserts exactly this second 200.
- Scenario 2 (two users):
  1. User 1's approve gets the resolution-changed 409 and commits Y.
  2. User 2, whose dialog was opened earlier and still shows X, clicks Aprobar and gets 200 for Y.
- Suggested fix, before 3c, together with the frontend 409 work (earlier M5):
  - `aprobar` accepts what the client displayed: a `@Version` of the trámite and crotales, or the list of `(crotalIndicado, animalId)` pairs, or a hash of it.
  - The server returns 409 if the stored or re-resolved state differs.
  - The frontend re-fetches the detail on any 409 and shows `motivo`.
- This is not a regression, since before the fix the first click already approved Y silently. It does belong on the 3c blocker list in `ganera-prompts.md` / `CLAUDE.md` (Task 7).

### Minor
**M-R1.** The resolution-changed 409 returns early, so any remaining Decision 25 problems only show up on the next attempt. The reviewer needs two round trips. Appending the Decision 25 motivos to the same message would give better UX; it is optional.

**M-R2. A timing dependency in the M6 test.**
- The first approval starts waiting on the lock before the second request has been authenticated and dispatched.
- If the gap to reaching 2 blocked sessions exceeds H2's lock timeout (the implementer observed about 2 s), the first request times out. H2 and Hikari then turn that into a 500 (surfacing as 401), and the test fails spuriously.
- This is unlikely locally but possible on a loaded CI runner.
- Mitigation: set an explicit, generous `LOCK_TIMEOUT` (e.g. `;LOCK_TIMEOUT=10000`) on the test H2 URL, or only for this class through a property override.
- Like the older test, it counts any blocked session in H2, so it assumes tests run serially.

**M-R3. Class Javadoc wording.** The class Javadoc still says errors "SALEN de la transaccion, que se revierte entera", with the new exception noted on the next line. That is accurate. Also mention the exception in the `aprobar` line of CLAUDE.md in Task 7, because it is the one place where a 409 commits data.
