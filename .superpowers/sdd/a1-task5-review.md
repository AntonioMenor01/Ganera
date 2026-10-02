# Prompt A1 - Task 5 review (Crotales en tramites) - independent reviewer

VERDICT: Approved (no Critical findings; one Important finding is a pre-existing importer gap outside
Task 5's file scope. It needs a decision from Antonio or a Task 7 follow-up, and does not block Task 6)

## Test run
`JAVA_HOME="/c/Program Files/Java/jdk-23" ./mvnw test` from `backend/`:
**Tests run: 288, Failures: 0, Errors: 0, Skipped: 0, BUILD SUCCESS.** This matches the report.
Per class: CrotalNormalizadorTest 23, TramiteCrotalServiceTest 18, TramiteCrotalEndToEndTest 4,
TramiteControllerTest 17, TenantIsolationEndToEndTest 7.

## What I verified (no finding)
1. **Spec / Decision 20.**
   - V16 matches the plan's SQL exactly.
   - Normalizer behavior:
     - Strips `isWhitespace || isSpaceChar`, which covers NBSP, U+2007 and U+202F.
     - Validates `[A-Za-z0-9]{1,30}` *before* uppercasing, so `ß` cannot become `SS`.
     - Uppercases with `Locale.ROOT`.
   - Classification:
     - Digits only, length ≤3: 400 "demasiado corto".
     - Digits only, length 4..12: INCOMPLETO.
     - Digits only, length ≥13: COMPLETO.
     - Every other valid form: COMPLETO. This includes the two-letter prefix, `A1234` and `12A34`.
   - Stored values:
     - `crotal` is the full `Animal.crotal` only when the crotal resolves to EN_INVENTARIO. Otherwise it equals `crotalIndicado`.
     - `sinEnlace` also resets `animal=null`.
   - `recalcularEnlaces` always re-resolves from `crotalIndicado`. The test walks E1→E2→E3→null→E1 and asserts the DB state after `flush/clear`.
2. **Isolation.**
   - Both Animal finders carry `explotacionId` AND `gestoriaId` as real query parameters. Before linking, `enlazar` checks explicitly that the Animal belongs to the Tramite's Explotación and to the gestoriaId.
   - `comprobarTramiteDeLaGestoria` rejects a Tramite, or its Explotación, that belongs to another Gestoría.
   - The listing's crotal batch is keyed by the page ids plus the explicit JWT `gestoriaId`. Even if the ambient filter on the list query failed again (the old interceptor bug), B would at worst see A's Tramite rows, never A's crotal rows. `laCargaDeCrotalesDelListadoUsaElGestoriaIdDelUsuarioNoElFiltroAmbiente` covers this, since `@DataJpaTest` has no ambient filter.
   - The detail endpoint uses `findByIdAndGestoriaId` plus the gestoria-scoped crotal query.
   - Tests use two Gestorías with overlapping suffixes (`...1234` in both). The implementer's mutation evidence is plausible and consistent with the tests.
   - No bare `findById`.
3. **Suffix matching.**
   - Spring Data derived `EndingWith` binds a LIKE parameter with escaping (`EscapeCharacter.DEFAULT`). The normalizer already restricts values to `[A-Z0-9]`, so `%`/`_` injection is impossible either way.
   - A 4-digit suffix also matches `...01234`. That is intended.
   - The `(explotacion_id, crotal_ultimos_digitos)` index serves the `explotacion_id` prefix, so the scan is bounded per explotación. Acceptable at this volume.
4. **reemplazarCrotales.**
   - Every crotal is normalized before any DB write. The test asserts "nothing changed" inside the same transaction, which is valid proof because nothing had been written.
   - Existing rows are deleted and flushed before the IDENTITY inserts, which is correct for `UNIQUE(tramite_id, crotal_indicado)`.
   - `crotalIndicado updatable=false` is compatible with the design: re-resolution only updates `crotal`, `animal` and `resolucion`, and a replace does delete plus insert.
   - The implementer's rollback-only warning is **accurate**. A RuntimeException leaving an inner `@Transactional` that participates in an outer transaction marks that outer transaction rollback-only (`globalRollbackOnParticipationFailure=true`). Catching the exception inside Task 6's transaction and then committing would throw `UnexpectedRollbackException`.
5. **Defensive checks.** They throw `RecursoNoEncontradoException`, which maps to 404, and they are tested. I found no path that bypasses them, because the service is the only writer of `TramiteCrotal`.
6. **N+1.** The E2E test uses 5 Tramites with 2 crotales each, including EN_INVENTARIO rows with an `animal` proxy. It asserts exactly 2 prepared statements, so `getAnimal().getId()` and `getTramite().getId()` do not initialize proxies. The mutation raised the count from 2 to 7. The proof is meaningful.
7. **Frontend compatibility.** The changes only add fields. `crotales` is never null (`List.of()` fallback in both `from(...)` methods and in `getOrDefault`). All existing fields and their order are unchanged. aprobar/rechazar responses now also carry `crotales`, which is also additive.
8. **Scope.** There is no PATCH. aprobar/rechazar logic and ordering are unchanged (the diff only touches the response). `TipoTramite` is untouched. `AnimalRepository` only gains finders.

## Critical
None.

## Important
**I1. `Animal.crotal` is stored un-normalized by the importer, so exact and long-suffix matching silently fail on real data.**
- Where: `ExplotacionImportService.java:146` → `ExplotacionImportFilaService.java:107,120` (existing code, not part of Task 5).
- Cause: the importer stores the cell as `formatter.formatCellValue(cell).trim()`. It does not uppercase it, remove inner spaces or NBSP, or check the charset. Task 5 compares an uppercase, space-free `crotalIndicado` by equality or suffix.
- Scenario: the Excel has `es 0100 0000 1234` or `ES0100 0000 1234`. The gestoría types `ES010000001234` or `010000001234`, and the result is NO_ENCONTRADO. A 4-digit suffix still works only by luck, if the last group is unbroken.
- A second consequence: the importer's own `findByCrotal` upsert treats `es…` and `ES…` as different Animals, which can create duplicates.
- Suggested fix: in the importer, run crotales through the same normalization as `CrotalNormalizador` (strip whitespace, uppercase, validate the charset, and make an invalid value a row error). Then decide whether existing rows need a one-off migration. The importer is outside Task 5's file list, so raise it with Antonio as a Task 7 item or a separate follow-up. It is not a Task 5 blocker.

## Minor
**M1. Two different indicated crotales can resolve to the same Animal.**
- Where: `TramiteCrotalService.java:69-76`.
- Scenario: `["1234", "ES010000001234"]` produces two rows with the same `animal_id` and the same `crotal`. For review this is harmless. Once 3c exists, a trámite could send the same animal to OVZ twice.
- Suggestion: decide in Task 6 or 3c whether to collapse rows by resolved animal or to flag the duplicate. No schema change is needed now.

**M2. A concurrent replace on the same Tramite surfaces as a 500.**
- Where: `TramiteCrotalService.java:63-75`.
- Scenario: two simultaneous PATCHes with crotales. In Postgres READ COMMITTED, the second `deleteAll` blocks, then deletes 0 rows. Hibernate then throws a stale-row-count exception or the insert hits `UNIQUE(tramite_id, crotal_indicado)`. Either way the caller gets an unmapped `DataIntegrityViolationException` or `OptimisticLock` exception, which becomes a 500. No data is corrupted.
- Suggestion: in Task 6, load the Tramite with `PESSIMISTIC_WRITE` (or a `@Version`) in the PATCH transaction, or map these exceptions to 409.

**M3. The lazy-load cost noted in the report applies to Task 6 as well.**
- Where: `TramiteCrotalService.java:162-166`.
- `comprobarTramiteDeLaGestoria` calls `explotacion.getGestoria()`, which initializes a lazy Explotación proxy. Outside a session, for example a detached Tramite whose explotación was never loaded, this would throw `LazyInitializationException`. It is fine under OSIV and in the current tests.
- Task 6 ordering note: when a PATCH changes both `explotacionId` and `crotales`, it should set the Explotación **before** calling `reemplazarCrotales`, which resolves against the current explotación. Calling `recalcularEnlaces` in addition is then redundant.

**M4. Other E2E classes do not clean `tramite_crotal` defensively.**
- Where: `TenantIsolationEndToEndTest` `@AfterEach`.
- It deletes `tramite` without first deleting `tramite_crotal`. Today only `TramiteCrotalEndToEndTest` seeds crotal rows, and it cleans them first, so the suite has no order dependence now.
- Task 6's `TramiteRevisionEndToEndTest`, and ideally `TenantIsolationEndToEndTest` too, should call `tramiteCrotalRepository.deleteAll()` before `tramiteRepository.deleteAll()`, so that one failed class cannot cascade FK errors into others.

**M5. Naming and optional-optimization deviations are documented and acceptable.**
- The plan's `findByExplotacionIdAndCrotalInAndGestoriaId` was implemented as the exact and `EndingWith` finders instead.
- The 6-digit `crotal_ultimos_digitos` fast path was not implemented. It was optional in the plan.
- Both deviations should be mentioned in Task 7's docs, alongside the "hyphens → 400" behavior: `ES-0100-…` is rejected because the spec says `[A-Z0-9]`. Consider whether the normalizer should also strip `-` for pasted values; this is a product decision.

**M6. No FK cascade or deletion path for `tramite_crotal`.**
- `tramite_crotal.animal_id` and `tramite_id` have no `ON DELETE` rule. Nothing deletes Tramites or Animales today.
- If the importer or a future feature ever deletes Animales, those deletes will fail on referenced crotal rows. This is a note for future work only.
