# Prompt A1 - Task 7a review (decisions 17, 19, 21, 22, 27, 28, 29, 31) - independent reviewer

VERDICT: **Approved** (with notes). There are no Critical findings and no Important findings that block 7a. I1 is a product decision for Antonio (same category as I2 in Task 6). The Minors can go to 7b docs or a follow-up.

## Test run
`JAVA_HOME="/c/Program Files/Java/jdk-23" ./mvnw clean test` from `backend/`: **Tests run: 408, Failures: 0, Errors: 0, Skipped: 0. BUILD SUCCESS.** This matches the report.

Per class:

| Test class | Tests |
|---|---|
| TramiteRevisionServiceTest | 49 |
| TramiteRevisionEndToEndTest | 36 |
| TramiteControllerTest | 31 |
| GanaderoEndToEndTest | 21 |
| ExplotacionImportServiceTest | 18 |
| TenantIsolationEndToEndTest | 7 |
| ErrorDispatchEndToEndTest | 5 |
| ExplotacionControllerTest | 3 |

No lock timeouts appear in the log.

### Reviewer mutations
All four were applied together, then restored byte-for-byte from backups (`cmp` OK). The suite was re-run clean afterwards.

| Mutation | Tests that failed |
|---|---|
| Decision 28 regex changed to `[A-Z]{2}[0-9]{7,13}` | 2 parametrized cases of `aprobarUnNoEncontradoSinFormatoCompletoValido...` |
| Decision 22 20-character check removed | `unCrotalInvalidoEsErrorDeFila...` |
| Decision 29 `permitAll("/error")` renamed away | 3 `ErrorDispatchEndToEndTest` cases |
| Decision 21 check removed in `ExplotacionController` | `ExplotacionControllerTest` and `GanaderoEndToEndTest` sort cases |

I also confirmed that the implementer's `svc.bak` is identical to the current `TramiteRevisionService`, so their mutations were restored.

## Verified, no finding

### 1. Decision 27 (`@Version`)

**Migration and entity.**
- `V17` adds `version BIGINT NOT NULL DEFAULT 0`, so existing rows and rows seeded by SQL get 0.
- `@Version Long` is null on a new instance, which gives `persist`, not `merge`.
- `version` is returned in `TramiteResponse` (list, aprobar, rechazar) and in `TramiteDetalleResponse` (detail, PATCH).

**Check under the lock.**
- `cargarConBloqueo` does `FOR UPDATE` with `gestoriaId` as a real parameter, then `refresh`.
- `exigirVersion` runs after that, on the row just re-read, so the comparison is made under the lock and after the refresh.

**No double increment and no lost increment.**
- `incrementarVersion` flushes first:
  - If Hibernate already wrote the dirty Tramite (and so already incremented it; this includes an earlier auto-flush triggered by the `tramite_crotal` queries), then `getVersion() != versionLeida` and nothing more happens.
  - If the Tramite was not dirty, the explicit UPDATE runs.
- `Tramite` has no collections, so no association change can bump the version on its own.
- The service tests pin exactly +1 for each case: tipo only, crotales only, explotación + crotales, no changes, aprobar, and rechazar.

**Scope of the UPDATE.** It is `where id and gestoria.id and version`. It updates exactly 1 row under the lock. On 0 rows it throws `ObjectOptimisticLockingFailureException`, which extends `ConcurrencyFailureException` and so maps to 409 `MOTIVO_CONCURRENCIA`. The `TramiteControllerErroresTest` mapping for it is still in place. See N1 for a nit.

**The `noRollbackFor` path commits the increment.**
- It runs `tramiteCrotalRepository.flush()`, then `incrementarVersion`, then throws the `noRollbackFor` type.
- The E2E test reads `version = vista+1` over JDBC after the 409.
- A second attempt with `vista` returns 409 `MOTIVO_VERSION_DESFASADA` with `foto` unchanged.
- An attempt with `vista+1` goes through the normal rules and gets 200 with `vista+2`.

**An ordinary 409 reverts.**
- Every Decision 25 and state 409 is thrown before `incrementarVersion`.
- PATCH with explotación + tipo + two crotales resolving to the same animal (so the Tramite UPDATE may already have been auto-flushed) gives 409. Its `foto()` now includes `version` and stays unchanged, which proves the rollback over real HTTP.

**Order and leaks.**
- The missing-version 400 happens in the controller before any DB access, and it is the same for an own, foreign or non-existent trámite (E2E with `999999`).
- The order is 403, then 400, then 404, then 409 state, then 409 version.
- A foreign trámite returns 404 with no body for its correct version, `+1` and `+100`, and nothing changes. This is tested both in `TramiteRevisionEndToEndTest` and in `TenantIsolationEndToEndTest`, where B sends A's correct version.
- `rechazar` needs no version and increments it (E2E, and the service test).

**State before version.** This is acceptable against decision 27:
- Both are 409 with no changes, and neither one protects any write.
- The state motivo is true whatever the version.
- The approve-vs-approve concurrency test depends on this order (the implementer's mutation 3 confirms it).
- Decision 27's own 7b note already says the frontend must reload on *any* 409.

**R1 from Task 6 is closed.** A stale screen, from the same user or another user, can no longer approve after a resolution-changed 409, because the committed increment invalidates its version.

**Dropping `PESSIMISTIC_FORCE_INCREMENT` is sound.** The explicit UPDATE does not depend on Hibernate's lock-level bookkeeping.

### 2. Decision 17

**Removed finders.** `findByNif`, `findByCodigoRega` and `findByCrotal` were removed from the repositories.
- A `clean` javac build compiles.
- `src/main` has no references to them. That includes `SuscripcionSyncScheduler` (`countByGestoriaId`) and `TwilioWebhookController`.
- The webhook flow in `CLAUDE.md` resolves crotales by last digits within the Explotación, not by `findByCrotal`, so nothing planned for 3b needs them.
- In tests, only the adapted scoped calls remain.
- This makes "never bare finders" a compile-time guarantee, which is a good change.

**Scoped finders.** `procesarExplotacion` and `procesarAnimal` use `...AndGestoriaId`.

**Neutral message.**
- `MOTIVO_IDENTIFICADOR_NO_DISPONIBLE` names neither another Gestoría nor a duplicate.
- An explotación of another Gestoría in the Animales sheet gets the same "no existe" message as a non-existent one.
- The only remaining oracle is the one decision 17 accepts: an own value updates, a foreign one fails.

**The strengthened test is real.**
- The foreign Ganadero is untouched and gains no Explotaciones.
- There are exactly 2 animals.
- The crotal with separators collides after normalization.

### 3. Decision 22 (normalization in the importer)

- The crotal is normalized before `procesarAnimal`, so `crotalUltimosDigitos` is derived from the normalized value.
- A value over 20 characters (`animal.crotal VARCHAR(20)`) is a row error, and the following rows still process.
- A re-import does not duplicate the animal.
- An imported crotal resolves in a Trámite.
- A crotal of 3 digits or fewer, which was accepted before, is now a row error. That is consistent with the normalizer.
- Deviation 5 is covered in I1.

### 4. Decision 28

- The regexes are exactly `ES[0-9]{12}` when the crotal starts with `ES`, and `[A-Z]{2}[0-9]{8,12}` otherwise. They are applied to the normalized, uppercase value.
- The boundaries are tested:
  - ES + 11/13 and FR + 7 fail; FR + 8, IT + 10 and DE + 12 pass; DE + 13 fails.
  - 13 bare digits, 3 letters, and a letter among the digits all fail.
- The resolution classification (`CrotalNormalizador`) is unchanged, since that file is not in the 7a diff.

### 5. Decision 29

- `requestMatchers("/error")` matches every HTTP method, and the error dispatch keeps the original method. The tests cover GET and PATCH.
- `/error/x` and `/errores` still return 401 without a JWT, and so do all the protected routes checked.
- `application.yml` (main) does not override `server.error.*`. Boot's defaults are `include-message=never`, `include-stacktrace=never` and `include-exception=false`, so nothing internal leaks. The direct-GET test confirms this.
- A malformed body, a wrong type, and a non-numeric id all return 400, not 401.
- `/error` touches no tenant data, and the interceptor does not enable the filter without Authentication.

### 6. Decision 31

- `LOCK_TIMEOUT=10000` is in the H2 URL, so it applies to every pooled connection, and `select lock_timeout()` asserts it.
- No test relies on a lock timeout: the log has none, and the concurrency tests release on "N sessions blocked", which is deterministic.
- The only cost is that a genuine hang would now fail after 10 s instead of 1 s.

### 7. Decision 21

- The whitelists are:
  - `/tramites`: `id`, `estado`, `createdAt`
  - `/explotaciones`: `codigoRega`, `nombre`, `id`
  - `/ganaderos`: `nombre`, `nif`, `id`
  - `/explotaciones/{id}/animales`: `crotal`, `id`
- Nested sorts (`contacto.nombre`, `ganadero.nif`, `explotacion.ganadero.ovzUsuario`, `gestoria.id`) and a trailing extra `&sort=` get 400 with a motivo.
- The defaults are stable: `{createdAt desc, id desc}` (tested with a tied `created_at`), and `{codigoRega, id}`.
- The `estado` filter still works (`findByGestoriaIdAndEstado`, tested).
- `gestoriaId` is explicit in the query. `ExplotacionControllerTest` now proves this *without* the ambient filter, which makes it stronger than before.

### 8. Inherited tests

- No assertion was weakened. The removed lines in the diff are signature and type adaptations; each has an equal or stronger replacement:
  - `findByCodigoRega` changed to the scoped finder.
  - `TipoContacto` removed.
  - The first `ExplotacionControllerTest` test now checks A and B without the filter.
- `foto()` now includes `version`, so every existing "unchanged" assertion also covers the version.
- The rewrite of `laCargaDeCrotalesDelListado` fixes a test that had become vacuous.
- The mandatory decision 27 cases are all present: first aprobar 409, second with the old version 409, with the new one normal rules, missing version gives 400, stale or future version gives 409, rechazar increments, and a foreign trámite gives 404 for any version.

### 9. Greps (`src/main`)

- `findById(`: only `AuthController` `/auth/me` (id from the JWT, documented as safe) plus comments.
- `findByCodigoRega(`, `findByNif(`, `findByCrotal(`: zero.
- `findByTelefono(`: only the declaration. In tests it appears only in `ContactoRepositoryTest` and in `verify(never())`.

### 10. Scope

- Nothing is staged, and HEAD is `1ca905e`.
- The following are unmodified: `frontend/src`, `.agents/`, `.claude/skills/impeccable/`, `skills-lock.json`, `PRODUCT.md`, `DESIGN.md`, `CLAUDE.md`, `ganera-prompts.md`, `progress.md`, `TipoTramite`, `facturacion`, `whatsapp`, `ovz`.
- See N3 for the untracked `frontend/public` files.

## Critical
None.

## Important

**I1 (a decision for Antonio, not a 7a defect). The inventory can hold incomplete crotales, and approving one sends an incomplete crotal.**
- Where: `ExplotacionImportService.java:289` (`normalizarCrotalDeAnimal`) accepts any valid normalized value, including 4–12 bare digits. `TramiteRevisionService.java:322` treats `EN_INVENTARIO` as always approvable.
- This is deviation 5 in the report. It predates 7a, since the importer accepted any string before, and decision 22 only asked for normalization. But it defeats the purpose of decisions 25 and 28 ("OVZ needs the full crotal").
- Scenario:
  1. A gestoría's Excel lists crotales without the `ES` prefix (`010000001234`).
  2. A trámite with `1234` resolves `EN_INVENTARIO` to `010000001234` and gets **200 APROBADO**. In 3c, OVZ would receive a 12-digit crotal.
  3. Conversely, a trámite with the full `ES010000001234` gets `NO_ENCONTRADO`, which is COMPLETO, passes decision 28, and gets 200. The animal is treated as "not in inventory" although it is.
- Suggested fix (Antonio decides):
  - (a) In the importer, require the decision 28 format for `Animal.crotal`, with a row error otherwise; or
  - (b) in `motivoCrotalNoAprobable`, apply `tieneFormatoCompletoAprobable` to the resolved `crotal` of `EN_INVENTARIO` rows as well.

  Option (b) is a one-line change and also protects against legacy data. Either way, record it in 7b (`CLAUDE.md` provisional rule).

## Minor

**M1. `GET /contactos` has no sort whitelist and no stable default order.** This is inconsistent with decision 21's intent that every listing behave the same.
- Where: `ContactoController.java:46-52`, where the `Pageable` has no `@PageableDefault` and no `OrdenacionPermitida` check.
- Scenario: `GET /contactos?sort=noExiste` gives a Spring Data `PropertyReferenceException`, which is a 500 (now a real 500 via the public `/error`, before a 401).
- `?sort=gestoria.nombre` is accepted. It is harmless (own rows only), but it is exactly what the whitelist exists to refuse.
- With no sort, the paging order is undefined.
- Fix: `CAMPOS_ORDENACION = {"nombre", "telefono", "id"}`, `@PageableDefault(sort = {"nombre", "id"})`, the same 400 `{motivo}`, and one E2E case. Decision 21 only named `/explotaciones` and `/tramites`, so this can go in 7b or a follow-up.

**M2. New raw `EntityManager` calls bypass Spring exception translation.**
- Where: `TramiteRevisionService.java:261-277`: `entityManager.flush()`, `createQuery(...).executeUpdate()` and `refresh`.
- `TramiteRevisionService` is a `@Service`, not a `@Repository`, so a failure there surfaces as a `jakarta.persistence.*` / Hibernate exception, not a `ConcurrencyFailureException` / `DataIntegrityViolationException`. `traducirErrores` does not catch it, so the result would be a 500, not the documented 409.
- Scenario: a Hibernate `OptimisticLockException` or a constraint violation raised by the flush inside `incrementarVersion` (the Tramite UPDATE `where version=?`). In practice this is unreachable while the row lock is held, so there is no functional impact today.
- Fix (optional): flush through a repository (`tramiteRepository.flush()`, which is translated), or catch `jakarta.persistence.OptimisticLockException` and `PersistenceException` in `traducirErrores`. At minimum, note it in the Javadoc of `incrementarVersion`.

**M3. The decision 17 neutral message also covers length and other constraint violations, and then it is misleading.**
- Where: `ExplotacionImportService.java:280/297`.
- `ganadero.nif` is VARCHAR(20), `codigo_rega` is VARCHAR(50), and `nombre` is VARCHAR(255). None of them is length-validated in the Explotaciones sheet.
- Scenario: a 21-character NIF or a 300-character name produces "alguno de sus identificadores ... no está disponible", which sends the user looking for a duplicate that does not exist.
- There is no leak; it only makes support harder. This is the implementer's deviation 6.
- Fix: validate the lengths before the DB (as is already done for crotal and contact name), so that `DataIntegrityViolation` really only means UNIQUE.

**M4. Decision 22 without a data migration can duplicate animals already loaded with separators or lowercase.**
- Scenario: a pre-7a import stored `ES 0100 0000 1234`. Re-importing now inserts `ES010000001234` as a *second* Animal. There is no UNIQUE collision, because the strings differ.
- The suffix `1234` then finds 2 candidates, and every trámite for that animal is `AMBIGUO` forever. There is no UI to delete animals.
- This was accepted by the plan ("sin migración"), but put it in 7b `CLAUDE.md` / `progress.md`. If any real environment has imported data, a one-off `UPDATE animal SET crotal = upper(regexp_replace(...))` (checking for collisions first) is cheap.

**M5. Frontend impact to state explicitly in the 7b summary.** This is by design (decision 27), not a defect.
- The current `TramiteReviewDialog` calls `POST /aprobar` with no body. From now on it **always** gets 400 `{motivo: "Falta la versión..."}` and shows its generic error, so **approving from the UI is impossible until A2**.
- The report's deviation 1 says this. Make sure Antonio reads it before any demo.

## Nits

**N1.** `incrementarVersion` takes `gestoriaId` from `tramite.getGestoria().getId()`, not from the caller's JWT `gestoriaId` (`TramiteRevisionService.java:269`).
- The two are equal, because the entity was loaded with the scoped lock query, so this is safe.
- Passing the method's `gestoriaId` argument would match the "real parameter from the JWT" pattern used everywhere else.

**N2.** The decision 19 Javadoc (`ExplotacionImportFilaService.java:37`) lists "un scheduler" under "Solo cuando SI hay una transaccion exterior".
- The precise condition is: **no OSIV EntityManager bound to the thread, or an outer transaction**.
- A scheduler gets a fresh, unfiltered EntityManager even with no outer transaction.
- Use the precise wording when `CLAUDE.md` is corrected in 7b.

**N3.** These untracked files appeared on 2026-09-28 at 15:16, inside the 7a time window:
- `frontend/public/favicon-64.png`
- `ganera-logo-512.png`
- `ganera-logo-verde.svg`
- `ganera-logo.svg`
- `preview.png`

Nothing in the implementer's scratchpad scripts refers to them, so they look like Antonio's parallel branding work. Confirm they are his and keep them out of the A1 commit.

**N4.** When the client passes `?sort=estado` or `?sort=nombre`, there is no `id` tie-breaker, so paging among equal values is not deterministic. The defaults are stable, which is what decision 21 asked for.

---

# Re-review (fixes for I1, M1)

VERDICT: **Approved.** I1 and M1 are fixed as Antonio decided. No new Critical, Important or Minor findings.

## Test run
`JAVA_HOME="/c/Program Files/Java/jdk-23" ./mvnw clean test` gives **Tests run: 415, Failures: 0, Errors: 0, Skipped: 0, BUILD SUCCESS**.

| Test class | Before | After |
|---|---|---|
| TramiteRevisionServiceTest | 49 | 52 |
| TramiteRevisionEndToEndTest | 36 | 37 |
| ContactoEndToEndTest | 25 | 28 |

The total is 408 + 7.

### Reviewer mutation
- I changed the `EN_INVENTARIO` case to always pass.
- Result: 3 failures, all in the new tests (both service tests and the E2E test).
- Restored byte for byte (`cmp` OK), followed by `clean test-compile`, so no mutated classes remain in `target/`.
- My backup folder `rev/` has been deleted.

## Scope of the change
Since the first review, only 5 files changed (checked by mtime):
- `TramiteRevisionService`
- `ContactoController`
- `ContactoEndToEndTest`
- `TramiteRevisionEndToEndTest`
- `TramiteRevisionServiceTest`

The diff of `TramiteRevisionService` against my backup of the version I first reviewed contains only the `EN_INVENTARIO` case and its Javadoc.

`CrotalNormalizador` and the other main files were not touched, so the resolution classification (decision 20) is unchanged. Nothing is staged or committed.

## Verified, no finding

**I1**
- **Single source of truth.** `EN_INVENTARIO` and `NO_ENCONTRADO` both call the same `tieneFormatoCompletoAprobable`, and there is no duplicated regex.
- **It checks the Animal's crotal.** The check uses `fila.getCrotal()`, which for `EN_INVENTARIO` is the full crotal of the Animal, freshly re-resolved by `recalcularEnlaces`. It does not use `crotalIndicado`. The implementer's mutation (using the indicated crotal instead) breaks 5 tests, including the positive case `ES010000001234`.
- **Interaction with the changed-resolution 409.**
  - The check runs after the change-detection block, so a resolution change still produces its own 409, which commits the new resolution and increments the version.
  - If there is no change, the new motivo is a plain `TramiteConflictoException` thrown before `incrementarVersion`, with a full rollback.
  - The E2E test proves this over real HTTP: `foto()` (estado, explotación, tipo, **version**, crotales) is identical before and after. The row stays `1234|010000001234|EN_INVENTARIO`.
- **Motivos accumulate.** The motivo accumulates in row order with the other decision 25 motivos (tested).
- **Wording.** The motivo names both crotales and says what to do.
- **Side benefit.** Animals stored before 7a with separators or lowercase (M4) are now also blocked at approval with a clear motivo, instead of passing.
- **No test weakened.** The class counts only went up, and every existing Animal fixture already uses `ES` + 12 digits, so no test data needed changing.

**M1**
- **Allowed fields.** The whitelist is `{nombre, telefono, id}`: own fields that the DTO already exposes.
- **Rejected fields.** These get 400 with the shared `OrdenacionPermitida.MOTIVO`:
  - nested: `gestoria.*`, `explotaciones.*`
  - internal: `createdAt`, `activo`
  - unknown: `noExiste`, which was a 500 before
  - a trailing extra `&sort=`
- **Stable default.** `{nombre, id}` is tested with two contacts named "Ana" and with paging across pages.
- **Consistent with the other listings.** It follows the same pattern as `/ganaderos`, `/explotaciones` and `/tramites`: a `CAMPOS_ORDENACION` constant, the check before any query, and `ResponseEntity<?>`.
- **`incluirInactivos`.** It is untouched and still passed to the service, where the existing tests, including the cross-tenant case, still pass.

## Nits (optional)
- The service test `aprobarUnCrotalEnInventarioConFormatoIncompleto...` computes `v0` but never asserts it afterwards. This is harmless: `@DataJpaTest` cannot observe the rollback, and the E2E test covers the version.
- `sort=nombre` follows the database collation (case and accents). That is fine for now; mention it only if the frontend needs locale-aware ordering.
