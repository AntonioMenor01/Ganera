# Prompt A1 - Task 2 report (implementer)

## Step 0 (Decision 14, closes Task 1 M2)
- `contacto/TelefonoNormalizador.java`: new `ESPANOL_SIN_34 = \+[6789]\d{8}` pattern. A `+` number is now rejected if it fails `INTERNACIONAL` (`\+[1-9]\d{7,14}`, unchanged) OR matches `ESPANOL_SIN_34`. Javadoc rule 3 updated.
- `TelefonoNormalizadorTest`: invalid cases added: `+612345678`, `+912345678`. Valid cases added to pin that the international rule still holds: `+44791112345` and `+512345678` (9 digits, first digit 1-5).
- TDD: the new cases were run first and gave `Tests run: 30, Failures: 2` (invalid [13] and [14]). After the fix: 30/30.

## Task 2 files
Created (backend/src/main/java/com/ganera/core/):
- `shared/web/MotivoErrorResponse.java`: `record(String motivo)`
- `contacto/ContactoController.java`: thin controller. It validates input, normalizes the phone, and maps exceptions to HTTP codes.
- `contacto/ContactoService.java`: all logic, with `@Transactional` on writes and `readOnly` on the list. Also holds the package-private `static comprobarMismaGestoria(...)` for Decision 15.
- `contacto/ContactoRequest.java`, `ContactoResponse.java`, `ContactoExplotacionResponse.java`, `EnlaceExplotacionRequest.java`. `rol` is taken as a String and parsed case-insensitively, so an invalid rol gives 400 `{motivo}` and never a Jackson 500.
- `contacto/RecursoNoEncontradoException.java` (maps to 404, no body) and `contacto/ContactoInactivoException.java` (maps to 409 `{motivo}`). Both are package-private and handled locally in the controller. There is no global `@ControllerAdvice`.

Modified:
- `explotacion/ExplotacionRepository.java`: added `findByIdAndGestoriaId(Long, Long)`.
- `contacto/ContactoExplotacionRepository.java`: added `@EntityGraph(attributePaths = "explotacion")` on `findByContactoIdInAndGestoriaId`. Each link's `codigoRega`/`nombre` then comes in the same single batch query. I checked this with `show-sql`: listing a page runs one `contacto` page query plus one `contacto_explotacion ... join explotacion` query, with no N+1.
- `contacto/TelefonoNormalizador.java` and its test (Step 0).

Tests created:
- `contacto/ContactoEndToEndTest.java`: 24 tests.
  - Setup: `@SpringBootTest(RANDOM_PORT)` + `TestRestTemplate`. Two Gestorias are created through `/internal/onboarding/gestoria` with the test secret, and each logs in with its own JWT. Explotaciones are created by importing an `.xlsx` built in the test with POI through `/explotaciones/importar`. Their ids are read back through `GET /explotaciones`.
  - `POST /contactos`: 201 with the phone normalized; bad phone gives 400; blank name gives 400; duplicate in the same Gestoria gives 409 generic; phone already used by the other Gestoria gives 409 with the same generic message, the body does not mention another gestoria or A's name, and B's list stays empty.
  - `GET /contactos`: B never sees A's contacts; explotaciones and rol are included; inactive contacts are hidden by default and shown with `incluirInactivos=true`; `incluirInactivos` never leaks another Gestoria's contacts; no JWT gives 401.
  - `PUT /contactos/{id}`: own contact returns 200 and the phone is normalized; editing A's contact with B's token gives 404 and it is unchanged; bad phone gives 400; phone of the other Gestoria gives 409 and the phone is unchanged; unknown id gives 404.
  - `DELETE /contactos/{id}`: logical and idempotent (204 twice); A's contact with B's token gives 404 and it stays active.
  - `POST /contactos/{id}/reactivar`: A's contact with B's token gives 404 and it stays inactive.
  - Inactive contact: linking it gives 409 `{motivo}` and no link is created; after reactivating (200, `activo=true`), linking works.
  - `POST /contactos/{id}/explotaciones`:
    - Re-linking the same explotacion updates the rol (lower-case `"titular"` accepted) without a duplicate link.
    - **A's contact to B's explotacion with A's token gives 404, empty body, no link.**
    - A's contact with B's token gives 404 and no link.
    - Invalid rol, missing rol and missing explotacionId each give 400 `{motivo}`.
    - Unknown explotacion gives 404.
  - `DELETE /contactos/{id}/explotaciones/{explotacionId}`: own link gives 204 and is removed (the other link stays); another Gestoria's link gives 404 and the link is still there; a missing link gives 404.
- `contacto/ContactoServiceMismaGestoriaTest.java`: 5 tests for Decision 15. They call `ContactoService.comprobarMismaGestoria` directly with in-memory entities whose Gestorias differ. The check is unreachable through the finders, so this proves the check itself rejects:
  - explotacion from another Gestoria
  - contacto from another Gestoria
  - both ends in the same Gestoria, but a different one from the authenticated user
  - a null gestoria on either end, or a null authenticated gestoriaId
  - the matching case passes

## Decision 15 implementation
`ContactoService.enlazar` does the following, in order:
1. Loads the contacto with `findByIdAndGestoriaId`.
2. Loads the explotacion with `findByIdAndGestoriaId`.
3. Calls `comprobarMismaGestoria`, which checks `contacto.gestoria.id == explotacion.gestoria.id == gestoriaId from the JWT`. On a mismatch it throws `RecursoNoEncontradoException`, which becomes 404.
4. Checks the contacto is active; if not, the result is 409.
5. Creates or updates the link. A new link gets its gestoria from `gestoriaRepository.getReferenceById(gestoriaId)` (the JWT), never from either end.

## TDD evidence
1. The new tests were written first and failed to compile: `ContactoService` and `RecursoNoEncontradoException` did not exist.
2. For a runtime red I added a stub: the exception class, a no-op `comprobarMismaGestoria`, and no controller. Result: `ContactoEndToEndTest` 24 run / 23 failures (only the 401 case passed), and `ContactoServiceMismaGestoriaTest` 5 run / 4 failures (only the matching case passed).
3. After the real implementation, 24/24 and 5/5 passed.

## Full suite
`JAVA_HOME="/c/Program Files/Java/jdk-23" ./mvnw test` gives **Tests run: 204, Failures: 0, Errors: 0, Skipped: 0, BUILD SUCCESS**.
That is 171 + 4 new normalizer cases + 24 E2E + 5 Decision 15.

## Rules check
- New code has no `findById(`, `findByCodigoRega(`, `findByNif(` or `findByTelefono(`. A grep over `ContactoService`/`ContactoController` only matches the Javadoc sentence that says they are not used.
- Every query takes an explicit `gestoriaId`. The list uses `findByGestoriaIdAndActivoTrue` / `findByGestoriaId`, not the ambient filter.
- Duplicate phones are detected only through `saveAndFlush` plus a `DataIntegrityViolationException` that propagates out of the service's `@Transactional` and is caught in the controller. There is no pre-check query.
- Comments are ASCII-only. User-facing message strings contain Spanish accents, as `RegistroGestoriaController` already does.
- Nothing was staged or committed. Frontend, Stripe, Twilio, IA, the importer, ganaderos, tramites, `.agents/`, `.claude/skills/impeccable/`, `skills-lock.json` and `logo.jpg` were not touched.

## Deviations / concerns
- Added `@EntityGraph` to the Task 1 repository method `findByContactoIdInAndGestoriaId`. This is needed so the batch load also covers the Explotacion data and avoids N+1. It does not change behaviour for the finder's existing test.
- `DELETE /contactos/{id}` keeps the contacto's existing links (logical delete keeps history). The Task 4 ganadero detail already filters with `ContactoActivoTrue`.
- Race condition, not handled: two concurrent first-time links of the same (contacto, explotacion) would hit `UNIQUE(contacto_id, explotacion_id)` and surface as a 500. This is acceptable at pilot volume.
- `PUT` to a phone that is already in use gives 409. The failed flush rolls back the transaction, and Spring's `JpaTransactionManager` clears the OSIV-bound EntityManager on rollback, so no dirty state leaks into the rest of the request. The E2E test confirms the stored phone is unchanged.
- Malformed JSON (for example a non-numeric `explotacionId`) still gets Spring's default 400 without a `{motivo}` body. Only the brief's cases (invalid or missing rol, missing explotacionId) return `{motivo}`.
- Error messages are:
  - "El teléfono no es válido."
  - "El nombre es obligatorio."
  - "El rol debe ser TITULAR o EMPLEADO."
  - "Falta la explotación."
  - "El contacto está dado de baja; reactívalo antes de enlazarlo."
  - "No se puede usar ese teléfono para un contacto." (duplicate phone; this exact text was specified in the brief)

## Fixes after review
- **m1 (Decision 15 is actually called):** new `contacto/ContactoServiceEnlazarTest` (Mockito mocks of the four repositories, not an external SDK).
  - `ContactoRepository.findByIdAndGestoriaId(10, A)` returns a Contacto of Gestoria A.
  - `ExplotacionRepository.findByIdAndGestoriaId(20, A)` returns an Explotacion of Gestoria B, simulating a broken scoped finder.
  - `enlazar(A, 10, 20, TITULAR)` must throw `RecursoNoEncontradoException`.
  - `ContactoExplotacionRepository.save` and `saveAndFlush` must never be called.
  - Proof: the `comprobarMismaGestoria(contacto, explotacion, gestoriaId)` call in `enlazar` is at line 105 of my file; the reviewer cited 280, probably from different line counting.
    - With that line temporarily removed, the new test **failed** ("Expecting code to raise a throwable", 1 run / 1 failure).
    - With the line restored from a byte-for-byte backup, it **passes** (1/1).
- **m2:** `ContactoController.validar` now rejects `nombre.strip().length() > 255` with 400 `{motivo: "El nombre no puede superar los 255 caracteres."}`. The constant is `LONGITUD_MAXIMA_NOMBRE = 255`.
  - New E2E test `nombreDeMasDe255CaracteresDevuelve400ConMotivoNo409` covers POST and PUT with 256 characters: 400, a motivo that is not the phone message, and nothing created or changed. It also checks that exactly 255 characters is still accepted (201).
  - Written first: before the fix it failed with "expected: 400 but was: 409".
- **m3:** the cross-Gestoria duplicate-phone 409 test now also checks that the only row is still Gestoria A's, with nombre "Contacto de A" and phone +34612345678. `onboarding()` now returns the `gestoriaId` for this check. It passed straight away, since it only strengthens assertions on existing behaviour.
- **m5:** `sinJwtDevuelve401` is replaced by `lasSieteRutasDeContactosSinJwtDevuelven401YNoTocanNada`.
  - It calls all 7 routes without a JWT, with a JSON body where the route takes one, and each returns 401.
  - It then checks nothing changed: the contacto's name and active flag, the number of contactos, and the number of links.
  - It passed straight away.
- m4 was left as is, as instructed. Nothing else was touched, and nothing is staged or committed.
- **Full suite:** `JAVA_HOME="/c/Program Files/Java/jdk-23" ./mvnw test` gives **Tests run: 206, Failures: 0, Errors: 0, Skipped: 0, BUILD SUCCESS**.
  - That is 204 + 1 (`ContactoServiceEnlazarTest`) + 1 (m2 E2E).
  - The m5 test replaces the old single-route 401 test, so it does not change the count.
