# Prompt A1 — Task 3 review (independent reviewer)

VERDICT: Approved

Full suite (`JAVA_HOME="/c/Program Files/Java/jdk-23" ./mvnw test`, from `backend/`): **217 tests, 0 failures, 0 errors, 0 skipped**, exit 0. The Task 3 classes: `ExplotacionImportServiceTest` 14/14, `ExplotacionImportContactosEndToEndTest` 2/2 and `ExplotacionImportControllerTest` 1/1. The count matches the implementer's report.

No Critical findings, and no Important findings inside Task 3. There is one pre-existing Important item for follow-up, plus several Minor items.

## Checklist results

1. **Decision 11: holds, no cross-tenant path found.**
   - `procesarContacto` (`ExplotacionImportFilaService.java:139-188`) uses two scoped finders: `findByCodigoRegaAndGestoriaId` and `findByGestoriaIdAndTelefono`.
   - `findByTelefono(` has no caller anywhere in `src/main`. Only its declaration exists (`ContactoRepository.java:18`).
   - Branches:
     - Existing and inactive: `IllegalStateException` before any write. The row rolls back, so there is no link and no reactivation.
     - Existing and active: the nombre is overwritten, but only on a contacto of the caller's own Gestoría, because the lookup is scoped.
     - Absent: INSERT. A phone owned by another Gestoría hits the global UNIQUE on `saveAndFlush`. The `DataIntegrityViolationException` propagates, the REQUIRES_NEW transaction rolls back, and the row gets the generic `MOTIVO_TELEFONO_EN_USO`.
   - I tried to construct A-contacto → B-explotación and could not:
     - the contacto is never loaded from A;
     - the explotación is never loaded from another Gestoría;
     - the link's gestoria comes from `gestoriaId`;
     - D15 re-checks all three ids.
   - Nor can B see or modify A's contacto. B never loads it, so the nombre overwrite cannot reach it.
2. **D15: correct.**
   - `comprobarMismaGestoria` runs after the contacto flush and before the link lookup and write (`:169`). A failure is wrapped as "La explotacion 'X' no existe", which does not reveal existence.
   - The link's gestoria is `getReferenceById(gestoriaId)`.
3. **REQUIRES_NEW correctness: holds.**
   - The filter is re-enabled first (`:142`).
   - Every failure path throws out of the proxied method, so the whole row rolls back, including the nombre update flushed at `:164`, a newly inserted contacto, and a partial link.
   - Tests confirm this: the inactive-contacto test keeps its original name; the foreign-phone test leaves the foreign contacto intact.
   - "Later rows keep processing after a constraint violation" is proven both at service level and over real HTTP. The E2E has an invalid row followed by a valid row. See point 4 for *why* this works over HTTP.
4. **The implementer's claim about OSIV is correct.** CLAUDE.md is wrong for the real HTTP path. See the dedicated section below.
5. **Spy test: sound, one remaining gap (Minor m2).**
   - `@MockitoSpyBean ContactoRepository` replaces the bean in the test context, so `ExplotacionImportFilaService` gets the spy injected. The REQUIRES_NEW proxy wraps the fila service, not the repository, so the calls reach the spy.
   - Spies are reset after each test by default.
   - The test is not vacuous:
     - it asserts `atLeastOnce` on `findByGestoriaIdAndTelefono(eq(gestoriaId), anyString())`;
     - the implementer reports it FAILS under the finder-swap mutation;
     - I agree the black-box tests cannot distinguish that mutation, because the filter hides the foreign row either way.
6. **Error messages: clean for Contactos.**
   - No Contactos-sheet message contains "gestori". `motivoContactoDe` never falls back to `motivoDe`.
   - The pre-existing `motivoDe` text *is* a leak for the Explotaciones and Animales sheets. See I-pre1.
7. **Architecture: Minor (m1).** Acceptable, but tidy it up.
8. **Tests: genuinely two-tenant, with data in both.**
   - The E2E seeds A's contacto plus link through the real endpoints, then B imports. Assertions check the HTTP views and the DB state through repositories.
   - Cleanup:
     - `@AfterEach` in both classes deletes, in FK order, `contacto_explotacion`, `contacto`, `animal`, `explotacion`, `ganadero`, then (E2E) `suscripcion`, `usuario` and `gestoria`.
     - It covers every table these tests write.
     - The service test additionally ends the test transaction first, because REQUIRES_NEW data is really committed.
   - I found no leaked rows.
9. **Scope: clean.**
   - Task 3 touched only the listed files plus the two visibility widenings in `contacto`. Nothing under `frontend/` is modified.
   - `ImportResumenResponse` gains an additive JSON field, which is harmless to the frontend.

## Critical
None.

## Important
None in Task 3 scope.

### I-pre1 (pre-existing, NOT introduced by Task 3, not blocking): cross-tenant existence oracle in `motivoDe`
- **Where:** `ExplotacionImportService.java:265`, `"Violacion de restriccion de base de datos (posible duplicado entre gestorias)"`.
- **Scenario:** Gestoría B imports an Explotaciones row with a NIF (or `codigo_rega`, or in Animales a `crotal`) that belongs to Gestoría A.
  - `findByNif`/`findByCodigoRega`/`findByCrotal` are hidden by the filter, so the row tries an INSERT, hits the global UNIQUE, and B reads an error saying explicitly that it collides with *another Gestoría*.
  - This tells B that a given NIF is a client of a competing gestoría. That is commercially sensitive, and more explicit than Decision 1's accepted "deducible" limitation for phones.
- **Suggested fix (Task 7 or follow-up):**
  - Replace the text with a neutral one, such as "No se pudo importar esta fila: el código ya está en uso".
  - Document the inherent oracle of the global UNIQUEs on `codigo_rega`/`nif`/`crotal` next to the Decision 1 note in CLAUDE.md.
  - Separately, `procesarExplotacion`/`procesarAnimal` still use the filter-dependent `findByNif`/`findByCodigoRega`/`findByCrotal`. As the implementer noted, they are worth migrating to `...AndGestoriaId` finders.

### I-doc (documentation, owned by Task 7): the REQUIRES_NEW security rationale is wrong for HTTP
It is wrong in two places:
- CLAUDE.md (Excel importer bullet, "Non-obvious side effect of REQUIRES_NEW");
- the `ExplotacionImportFilaService` class Javadoc (`ExplotacionImportFilaService.java:33-39`, pre-existing text).

See the section below. The code behaviour is correct and the filter re-enable must stay. Only the explanation needs rewriting.

## Minor

- **m1: layering and package cycle.**
  - `ExplotacionImportService` (a service) imports `ContactoController` (the web layer) just for `MOTIVO_TELEFONO_EN_USO` (`ExplotacionImportService.java:3`, `:255`).
  - `ContactoService.comprobarMismaGestoria` is now `public static` (`ContactoService.java:142`). It throws the package-private `RecursoNoEncontradoException`, which callers outside `contacto` can only catch as `RuntimeException`.
  - Together with `contacto → explotacion` (the entity relation), this makes a package-level cycle. There is no Spring bean cycle, since both uses are static, so it is not a bug.
  - Suggested fix: move the message constant to `ContactoService`, or to a small public `contacto` constants holder or `shared/web`. Consider a neutral home for the three-way tenant check, such as a static on `ContactoExplotacion` or a `shared/tenant` helper.
- **m2: the spy guard covers only one call route.**
  - It catches the importer calling `ContactoRepository.findByTelefono`.
  - It would not catch an equivalent unscoped lookup written another way, such as a new `@Query`, an `EntityManager` JPQL/native query, or a different finder like `findFirstByTelefono`. It also does not guard other authenticated code paths such as `ContactoService`.
  - The Task 7 grep already planned covers part of this. Consider adding `findByTelefono(` to that grep over all non-webhook code, or a simple source-scan test.
- **m3: `motivoContactoDe` echoes any `IllegalStateException` message** (`ExplotacionImportService.java:257`).
  - Today every ISE comes from `procesarContacto`. But an ISE thrown by the framework (for example the shared-EM proxy's "No transactional EntityManager available", or a Hibernate internal) would put its internal text into the row error.
  - It does not leak tenant data, but it is brittle. Consider a dedicated package-private `FilaImportException` for the importer's own messages, and pass through only that type.
- **m4: the importer is a clean phone-existence oracle, consistent with Decision 1.**
  - In the importer, own active phones upsert and own inactive phones get a distinct message. The generic `MOTIVO_TELEFONO_EN_USO` therefore appears **only** when the phone belongs to another Gestoría.
  - This is equivalent to what `POST /contactos` 409 already allows: a phone not in your own list returns 409, so it belongs to someone else. It falls within the limitation Antonio accepted.
  - Make sure the Task 7 CLAUDE.md note on Decision 1 mentions the importer as well as the endpoint.
- **m5: D15 ordering.**
  - The check runs after the contacto `saveAndFlush`. It is correct thanks to the rollback, and mutation 2 in the report demonstrates it.
  - It could run before the flush with no loss, since `getGestoria().getId()` works on the reference proxy, and fail without touching the DB at all. This is optional.
- **m6: test location deviates from the plan.** The plan suggested putting the import-isolation E2E in `TenantIsolationEndToEndTest`; it lives in the new `ExplotacionImportContactosEndToEndTest`. The coverage is equivalent and the deviation is acceptable. Mention it in the Task 7 docs so the E2E inventory in CLAUDE.md stays accurate.

## Point 4: does REQUIRES_NEW really swap the open-in-view EntityManager?

**Conclusion: the implementer is right. Over real HTTP, `ExplotacionImportService` runs with no outer transaction.** Each `@Transactional(REQUIRES_NEW)` row method therefore does not suspend anything. It begins its transaction on the request's already-bound open-in-view EntityManager, where `TenantFilterActivationInterceptor` already enabled `gestoriaFilter`.

CLAUDE.md's statement ("it suspends the request's open-in-view entity manager ... and binds a brand-new one with no filter active") holds only when an outer transaction exists, or when no EM is pre-bound at all (`@DataJpaTest` after `TestTransaction.end()`, schedulers, a future async caller).

### Evidence
I read the bytecode (`javap -c`) of the jars actually on the classpath, `spring-orm-6.2.1` and `spring-tx-6.2.1`, from `~/.m2`:

1. **`OpenEntityManagerInViewInterceptor`** binds an `EntityManagerHolder` for the EMF in `TransactionSynchronizationManager`. It does not start a transaction, so the holder's `transactionActive` is false.
2. **`JpaTransactionManager.doGetTransaction`** picks up that pre-bound holder: `txObject.setEntityManagerHolder(holder, false)`, where false means not new.
3. **`isExistingTransaction`** is `txObject.hasTransaction()`, which requires `holder.isTransactionActive()`. That is false, so there is no existing transaction.
4. **`AbstractPlatformTransactionManager.getTransaction`**: with no existing transaction, REQUIRES_NEW takes the same path as REQUIRED. It runs `suspend(null)`, which suspends no resources because none are transactional, then `startTransaction`. It never reaches `handleExistingTransaction`, the only place where REQUIRES_NEW unbinds and suspends resources.
5. **`JpaTransactionManager.doBegin`** creates a new EM **only if** `!hasEntityManagerHolder() || holder.isSynchronizedWithTransaction()`. The OSIV holder is not synchronized, so doBegin **reuses the OSIV EntityManager** and begins the JPA transaction on it.
6. **`doCleanupAfterCompletion`**: for a non-new holder it logs "Not closing pre-bound JPA EntityManager after transaction" and calls `holder.clear()`. `EntityManagerHolder.clear()` resets `transactionActive=false`, and `ResourceHolderSupport.clear()` resets `synchronizedWithTransaction=false`. The next row's REQUIRES_NEW therefore reuses the same OSIV EM again.
7. **`doRollback`**: for a non-new holder it calls `entityManager.clear()` after the rollback.
   - This, not a fresh session, is what really provides "one bad row doesn't poison later rows" over HTTP. The persistence context is cleared, so no half-flushed entity or action-queue state survives into the next row.
   - `Session.clear()` does not disable enabled filters, so `gestoriaFilter` stays on.
8. **`HibernateJpaVendorAdapter`** sets `hibernate.connection.handling_mode = DELAYED_ACQUISITION_AND_HOLD`. Over HTTP, all rows therefore also run on the **same Session and the same held JDBC connection**. CLAUDE.md's "their own transaction, their own connection" is also inaccurate for HTTP: it is their own *transaction* on a shared connection.

This is consistent with the implementer's empirical mutation 2. With the finder swapped and the explicit re-enable removed, the E2E still produced the generic message, which is only possible if the OSIV EM with the interceptor's filter was in use.

### Consequences for the docs (Task 7; do not change code)
- **Keep** the filter re-enable in each REQUIRES_NEW method. It is load-bearing whenever there is an outer transaction or no pre-bound EM:
  - tests;
  - any future caller outside a request;
  - the day someone adds `@Transactional` to `ExplotacionImportService`/the controller. In that case, REQUIRES_NEW *would* suspend and create a fresh, unfiltered EM, exactly CLAUDE.md's scenario.
- The explicit `...AndGestoriaId` finders in `procesarContacto` make tenant scoping independent of *either* mechanism. That is the robust design, and the other two row methods should follow it (see I-pre1).
- Rewrite the rationale as follows:
  - Over HTTP, REQUIRES_NEW reuses the OSIV EM, and per-row isolation comes from rollback plus `EntityManager.clear()`.
  - With an outer transaction or no bound EM, it gets a fresh, unfiltered EM, hence the mandatory re-enable.
  - Also fix the "own connection" claim.
