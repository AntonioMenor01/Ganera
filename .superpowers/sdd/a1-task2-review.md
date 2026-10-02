# Prompt A1 - Task 2 review (independent reviewer)

VERDICT: Approved

There are no Critical or Important findings. The Minor items below are worth fixing but do not block Task 3.

## What I did
- Read `CLAUDE.md`, the plan (Decisions 1-16, Global Constraints, Task 2), Task 1 review M3, and the Task 2 report.
- Read every Task 2 file and Step 0:
  - `ContactoController`, `ContactoService`
  - the 4 DTOs and 2 exceptions
  - `shared/web/MotivoErrorResponse`
  - the `ExplotacionRepository` diff
  - `ContactoExplotacionRepository` (`@EntityGraph`)
  - `TelefonoNormalizador` and its test
  - `ContactoEndToEndTest` and `ContactoServiceMismaGestoriaTest`
  - V6/V15 and `GestoriaScopedEntity`
- Ran the full suite: `JAVA_HOME="/c/Program Files/Java/jdk-23" ./mvnw test` gave **Tests run: 204, Failures: 0, Errors: 0, Skipped: 0, BUILD SUCCESS**. That includes `ContactoEndToEndTest` 24/24, `ContactoServiceMismaGestoriaTest` 5/5, `TelefonoNormalizadorTest` 30/30 and `TenantIsolationEndToEndTest` 7/7.

## Checklist results

### 1. Endpoints vs the spec table: all match
- `GET /contactos`:
  - `incluirInactivos` defaults to false.
  - The list is paginated, and each page's links are batch-loaded in one query.
  - The `@EntityGraph` fetches `explotacion` into that same query, so there is no lazy N+1 and no lazy-init risk.
  - Returns 401 without a JWT (tested).
- `POST /contactos`:
  - Returns 201.
  - Invalid phone or blank name gives 400 `{motivo}`.
  - A duplicate phone gives 409 with the exact message from the brief.
- `PUT /contactos/{id}`:
  - Returns 200.
  - Returns 404 without a body for another Gestoria's contacto or an unknown id.
  - Returns 400 or 409 on the same rules as `POST`.
- `DELETE /contactos/{id}`:
  - Returns 204.
  - The delete is logical and idempotent (tested twice).
  - Another Gestoria's contacto gives 404.
- `POST /contactos/{id}/reactivar`:
  - Returns 200 with `activo=true`.
  - Another Gestoria's contacto gives 404.
- `POST /contactos/{id}/explotaciones`:
  - Returns 200 with the contacto.
  - Linking an existing pair updates the rol with no duplicate link (tested; the rol is case-insensitive).
  - Returns 404 if either the contacto or the explotacion is not in the caller's Gestoria.
  - An inactive contacto gives 409 `{motivo}`.
  - An invalid or missing rol, or a missing `explotacionId`, gives 400 `{motivo}`.
- `DELETE /contactos/{id}/explotaciones/{explotacionId}`:
  - Returns 204.
  - Returns 404 if the link is not in the caller's Gestoria.

### 2. Isolation: no leak or mutation path found
- Every repository call in `ContactoService` passes the JWT `gestoriaId` explicitly:
  - `findByIdAndGestoriaId` for both Contacto and Explotacion
  - `findByGestoriaId[AndActivoTrue]`
  - `findByContactoIdAndExplotacionIdAndGestoriaId`
  - `findByContactoIdInAndGestoriaId`
- There is no bare `findById`, `findByCodigoRega`, `findByNif` or `findByTelefono` (grep-verified; the only hit is Javadoc).
- New contactos and new links take their gestoria from `getReferenceById(gestoriaId)`, which comes from the JWT. `gestoria_id` is `updatable=false`, so `PUT` cannot move a contacto to another Gestoria.
- Decision 15 (`comprobarMismaGestoria`, `ContactoService.java:315`) is called at `ContactoService.java:280`. That is after both scoped loads and before the inactive check and any write.
- PUT, DELETE, reactivar and unlink all load the contacto through `findByIdAndGestoriaId` first. Unlink then also scopes the link lookup by `gestoriaId`.
- The list's batch query filters on the link's own `gestoria_id`. It could return a link whose explotacion belongs to another Gestoria only if a cross-Gestoria link row existed. The only write path (`enlazar`) makes that impossible now: both ends are scoped finders, D15 checks them, and the link gets the JWT gestoria. The DB-level guard from M3 is still absent, which Task 1's review already accepted as advisory.
- No `REQUIRES_NEW` is used, so the OSIV filter caveat does not apply.

### 3. Duplicate phone handling: correct
- The duplicate is detected only by `saveAndFlush` hitting the constraint. There is no pre-check query.
- The flush happens inside the service method in both `POST` and `PUT`. So the `DataIntegrityViolationException` is raised inside the `@Transactional` proxy. The repository proxy translates Hibernate's exception into it, the transaction rolls back, and the exception propagates.
- On rollback, `JpaTransactionManager` clears the pre-bound OSIV EntityManager, so no dirty entity is left in the session.
- The controller catches the exception outside the transaction and returns 409. `PUT` does not rely on a commit-time flush, so it cannot escape as a 500.
- The E2E test `actualizarConTelefonoInvalidoDevuelve400YConTelefonoAjenoDevuelve409` confirms both the 409 and that the stored phone is unchanged.

### 4. Enumeration oracle
- The 409 body is identical for a phone in my Gestoria and a phone in another one.
- The test asserts the body does not contain "gestor" or the other contacto's name.
- No other response distinguishes the two cases. The remaining oracle, "this phone exists somewhere", is inherent to decision A and was accepted by Antonio.

### 5. Tests
- The E2E tests are real `RANDOM_PORT` + `TestRestTemplate` tests with two onboarded Gestorias and two JWTs. Both Gestorias get real Explotaciones through the actual import endpoint.
- Cross-tenant cases assert DB state, not just the status code:
  - link count stays 0 or 1
  - the phone and name stay unchanged
  - `activo` is unchanged
- The mandatory case, linking A's contacto to B's explotacion, is covered at `ContactoEndToEndTest.java:357`. It asserts 404, an empty body and no link.
- The D15 unit test calls the real production method (package-private static), not a copy. Its gap is covered in m1.

### 6. Invalid inputs never return 500 for the spec cases
- A bad rol, a missing rol or a missing `explotacionId` gives 400 `{motivo}`, because rol is received as a String on purpose.
- An invalid phone or a blank name gives 400 `{motivo}`.

### 7. Scope
- Only Task 2 files plus Step 0 changed, on top of Task 1's uncommitted work.
- Nothing touches the frontend, Stripe, Twilio, the AI code, the importer, ganaderos or tramites.
- Nothing is staged.

## Critical
None.

## Important
None.

## Minor

**m1. The D15 check is tested, but not the fact that `enlazar` calls it.**
- Files: `ContactoServiceMismaGestoriaTest.java`, `ContactoService.java:280`.
- Failing scenario: delete line 280 (`comprobarMismaGestoria(...)`). All 204 tests still pass. The E2E cross-tenant tests are stopped earlier by the scoped finders, and the unit test only calls the helper directly. The defense-in-depth guard could disappear silently in a refactor.
- Suggested fix: add one Mockito test of `ContactoService.enlazar`:
  - The mocked `ContactoRepository.findByIdAndGestoriaId` returns a contacto of Gestoria A.
  - The mocked `ExplotacionRepository.findByIdAndGestoriaId` returns an explotacion of Gestoria B, simulating a broken finder.
  - Assert `RecursoNoEncontradoException` is thrown and `contactoExplotacionRepository.save` is never called.

**m2. A name longer than 255 characters returns 409 "No se puede usar ese teléfono para un contacto."**
- Files: `ContactoController.java:149`, `ContactoService.java:241/250`.
- Failing scenario: `nombre` is `VARCHAR(255)` (V6). A 300-character name makes the flush throw a Hibernate `DataException`. Spring translates that to a `DataIntegrityViolationException`, and the controller maps it to the phone-duplicate 409. The user is told the phone is the problem. It is not a 500, and nothing leaks.
- Suggested fix: add a length check in `validar(...)`, `nombre.strip().length() > 255`, returning 400 with a motivo.

**m3. The cross-tenant 409 test does not check that A's row is intact.**
- File: `ContactoEndToEndTest.java:160-171`.
- Failing scenario: the test asserts `count()==1` and that B's list is empty. It does not assert that the single row still belongs to Gestoria A with the name "Contacto de A". This is only theoretical, because a failed insert cannot rewrite A's row, but the check is cheap.
- Suggested fix: assert `gestoriaDe(idA)` and the stored `nombre`.

**m4. Unhandled 500s on edge inputs.**
- Race on first-time link: two concurrent first-time links of the same contacto and explotacion hit `UNIQUE(contacto_id, explotacion_id)` and return a 500. The implementer already disclosed this; it is acceptable at pilot volume.
- Invalid sort: `GET /contactos?sort=noExiste` raises `PropertyReferenceException`, which becomes a 500. `GET /explotaciones` and `GET /tramites` already behave the same way.
- Neither is in the spec's no-500 list.

**m5. Only `GET /contactos` has an explicit 401-without-JWT test.**
- The other six routes rely on the global `anyRequest().authenticated()`.
- Optional: add a one-line assertion per method.

### Step 0 (Decision 14)
- `ESPANOL_SIN_34 = \+[6789]\d{8}` is correct.
- `+612345678` and `+912345678` are rejected.
- `+512345678`, `+44791112345` and `+447911123456` still pass the international rule.
- Separators are stripped before the check, so `+6 1234 5678` is also rejected.
- Tests: 30/30.
