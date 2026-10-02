# Prompt A1 - Task 3 report (implementer): optional "Contactos" sheet in the importer

## Result
- Full suite: **Tests run: 217, Failures: 0, Errors: 0, Skipped: 0, BUILD SUCCESS**. That is 206 existing tests plus 11 new ones: 9 in `ExplotacionImportServiceTest` and 2 E2E.
- Nothing was staged or committed. The frontend, Stripe, Twilio, AI, Ganaderos and tramites are untouched.

## Files

### Modified, main code (`backend/src/main/java/com/ganera/core/`)
- **`explotacion/ExplotacionRepository.java`**: new `findByCodigoRegaAndGestoriaId(String, Long)`.
- **`explotacion/ImportResumenResponse.java`**: new `contactos` field (`ImportHojaResumen`) between `animales` and `errores`. It is 0/0/0 when the sheet is absent.
- **`explotacion/ExplotacionImportService.java`**:
  - New `HOJA_CONTACTOS`. The optional sheet is processed after Explotaciones and Animales.
  - Columns by position: telefono, nombre, codigo_explotacion, rol. The header row and blank rows are skipped.
  - Pre-validation needs no DB. Each failure is a row error and processing continues:
    - `TelefonoNormalizador` fails: "Teléfono no válido".
    - Blank nombre.
    - Nombre longer than 255 characters (same reason as Task 2 m2).
    - Blank codigo.
    - Rol is not TITULAR/EMPLEADO (case-insensitive).
  - New `motivoContactoDe` for this sheet only:
    - `DataIntegrityViolationException` becomes `ContactoController.MOTIVO_TELEFONO_EN_USO` ("No se puede usar ese teléfono para un contacto.").
    - An `IllegalStateException` keeps its own message.
    - Anything else becomes a generic text.
    - The existing `motivoDe` text ("posible duplicado entre gestorias") is never used for this sheet.
  - The class Javadoc now lists all three sheets.
- **`explotacion/ExplotacionImportFilaService.java`**: new `procesarContacto(gestoriaId, telefonoNormalizado, nombre, codigoRega, rol)`, `@Transactional(REQUIRES_NEW)`. In order:
  1. Re-enables `gestoriaFilter` first.
  2. Loads the explotacion with `findByCodigoRegaAndGestoriaId`. If absent, it is a row error.
  3. Looks up the contacto with `findByGestoriaIdAndTelefono`:
     - Inactive: row error "El contacto con ese teléfono está dado de baja".
     - Active: nombre is overwritten and the row counts as ACTUALIZADA.
     - Absent: a new contacto is created with gestoria from `getReferenceById` and `activo=true`, and the row counts as CREADA.
  4. `saveAndFlush`. A telefono owned by another Gestoria makes the flush throw `DataIntegrityViolationException`, which rolls back the whole row.
  5. Runs the D15 check, `ContactoService.comprobarMismaGestoria`. If it fails, the result is a row error "La explotacion 'X' no existe" and the row rolls back.
  6. Upserts the link with `findByContactoIdAndExplotacionIdAndGestoriaId`. The link's gestoria comes from `getReferenceById(gestoriaId)`.
- **`contacto/ContactoService.java`**: `comprobarMismaGestoria` is now `public static`. Behaviour is unchanged and the Javadoc notes the importer uses it. `RecursoNoEncontradoException` stays package-private; the importer wraps it.
- **`contacto/ContactoController.java`**: `MOTIVO_TELEFONO_EN_USO` is now `public` so the importer can reuse the exact text.

### Tests (`backend/src/test/java/com/ganera/core/explotacion/`)

**Modified `ExplotacionImportServiceTest.java`**
- `@AfterEach` now also cleans `contacto_explotacion` and `contacto`.
- `ContactoRepository` is now a `@MockitoSpyBean`: a real repository that calls can be verified on.
- Existing `List.of(new String[]{..})` calls became `List.<String[]>of(...)`. With a single element, the untyped form infers `List<String>`.
- 9 new tests:
  - No Contactos sheet: same result as today, contactos 0/0/0, no contacto rows.
  - Create a contacto with a normalized phone, plus its link with the rol.
  - Same telefono in 2 rows for 2 explotaciones: 1 contacto, 2 links, summary 1 created and 1 updated.
  - Re-import: nombre and rol are updated with no duplicates.
  - Invalid rows (bad phone, bad rol, blank nombre, 256-char nombre, blank codigo, nonexistent explotacion) are row errors at rows 2-7. The valid row after them is still imported.
  - Explotacion of another Gestoria: row error that does not mention a gestoria. No contacto and no link are created.
  - Telefono of another Gestoria mid-sheet:
    - That row gets the exact generic message, which does not contain "gestori".
    - The previous and following rows are still processed, including an existing contacto gaining a second link.
    - The foreign contacto keeps its name and exactly one link.
  - Inactive contacto of the same Gestoria: row error. It stays inactive with its original name and gets no link.
  - `elImportadorNuncaUsaFindByTelefonoSinScope`: the spy verifies `findByTelefono` is never called and that `findByGestoriaIdAndTelefono(gestoriaId, ..)` is called.

**New `ExplotacionImportContactosEndToEndTest.java`**: `@SpringBootTest(RANDOM_PORT)` + `TestRestTemplate`, two Gestorias onboarded, each with its own JWT.
- `importarUnTelefonoDeOtraGestoriaEsErrorGenericoYNoTocaElContactoAjeno` (the Decision 11 test):
  - Setup: A creates contacto T through `POST /contactos` and links it to its own explotacion through `POST /contactos/{id}/explotaciones`.
  - B imports T for B1, followed by a valid row for B2.
  - Asserts:
    - B gets exactly one error at hoja Contactos, row 2, with the exact generic message. It does not contain "gestori" or A's contacto name.
    - B's summary is 2 processed, 1 created.
    - In A's `GET /contactos`, T keeps its name and has exactly one link, to A1. The repositories confirm it: A's contacto has a single link, to A1.
    - B's `GET /contactos` contains only B's valid contacto and never T.
- `loQueAImportaEnSuHojaContactosNuncaApareceParaB`:
  - A imports a Contactos sheet. One of its rows points at B's explotacion and gets a row error that does not mention a gestoria.
  - B's `GET /contactos`, with and without `incluirInactivos`, is empty.
  - No link points at B's explotacion.

## TDD evidence
1. With the tests written first, compilation failed: `HOJA_CONTACTOS` and `contactos()` did not exist.
2. With a minimal stub (the field set to 0/0/0, the sheet ignored), 9 tests failed: 7 service tests and both E2E tests. The no-sheet test passed, as it should.
3. After the implementation, the 15 targeted tests were green, and the spy guard was added (16).
4. Full suite: 217 green.

### Mutation proof (`findByGestoriaIdAndTelefono` replaced by unscoped `findByTelefono`, then restored)
- **Single mutation, only the finder swapped: the black-box tests do not catch it.**
  - The first run had all 15 service and E2E tests green (the spy test did not exist yet).
  - Cause: `procesarContacto` re-enables `gestoriaFilter` before the lookup. `findByTelefono` is a derived query, so the filter hides the other Gestoria's contacto anyway. It behaves exactly like the scoped finder.
- **Mutation 2, finder swapped and the filter re-enable in `procesarContacto` removed:**
  - The service test `telefonoDeOtraGestoriaAMitadDeLaHoja...` failed: `expected: "No se puede usar ese teléfono para un contacto." but was: "La explotacion 'ES710000000001' no existe"`. The unscoped lookup found the other Gestoria's contacto, the D15 check blocked the link and the row rolled back, so D15 is a working second layer.
  - **The E2E still passed.** A debug print showed B's row still got the generic message. Over real HTTP, `ExplotacionImportService` has no outer transaction, so `REQUIRES_NEW` does not suspend anything. It begins its transaction on the request's already-bound open-in-view EntityManager. The interceptor had already enabled `gestoriaFilter` on that EntityManager, so the filter was still active.
- **Hence the direct guard `elImportadorNuncaUsaFindByTelefonoSinScope`.** With the single mutation re-applied, it FAILS: `contactoRepository.findByTelefono(<any>); Never wanted here: ... But invoked here: ...`. The other 15 stay green. Code restored; suite green.

## Grep of new code
- `findById(`, `findByCodigoRega(`, `findByNif(` and `findByTelefono(` have no uses in `procesarContacto`, `importarContactos` or the new E2E.
- The only new-code hit is `verify(..., never()).findByTelefono(any())` in the guard test, which is intentional.
- **Pre-existing, not touched (out of Task 3 scope):** `procesarExplotacion` and `procesarAnimal` still use `findByNif`, `findByCodigoRega` and `findByCrotal`, relying on the re-enabled filter. Consider migrating them to explicit `...AndGestoriaId` finders later, for example in Task 7.

## Deviations and concerns
1. **The CLAUDE.md claim about REQUIRES_NEW is inaccurate for the HTTP path.** The Excel importer bullet and the `ExplotacionImportFilaService` Javadoc say REQUIRES_NEW suspends the open-in-view EntityManager and binds a new one with no filter.
   - The empirical result above shows that, over HTTP with no outer transaction, it reuses the open-in-view EntityManager, where the interceptor's filter is still active.
   - It is true in `@DataJpaTest`, and with any outer transaction.
   - The re-enable is still correct and necessary, since it is harmless and covers those cases. Only the explanation is wrong. I did not edit the docs (Task 7 owns them); please correct them there.
2. **The E2E alone cannot prove the "no findByTelefono" rule**, because of the layering above. The rule is guarded by the spy test instead. The E2E guards the observable outcome.
3. **D15 failures in the importer read "La explotacion 'X' no existe"**, not a 404. This follows the row-error convention and does not reveal existence.
4. **Order of operations:** the nombre update is flushed before the D15 check. If D15 fails, the REQUIRES_NEW rollback undoes it (mutation 2 confirmed the row rolled back).
5. **`MOTIVO_TELEFONO_EN_USO` and `comprobarMismaGestoria` were widened to public.** This creates a package dependency from `explotacion` to `contacto` (`contacto` already depended on `explotacion`). There is no Spring bean cycle, since both are static uses.
6. **Separate Spring context:** `ExplotacionImportServiceTest` now spies `ContactoRepository`, so it gets its own Spring context. No other effect.

## Fixes after review (m1, m3)
Behaviour is unchanged. Full suite: **Tests run: 220, Failures: 0, Errors: 0, Skipped: 0, BUILD SUCCESS** (217 + 3 new).

### m1: shared constant and exception moved out of the web layer
- `MOTIVO_TELEFONO_EN_USO` moved from `ContactoController` to `ContactoService` (`public static final`, same text).
  - `ContactoController` no longer defines the constant; its 409 now uses `ContactoService.MOTIVO_TELEFONO_EN_USO`.
  - `ExplotacionImportService` no longer imports `ContactoController`.
- `RecursoNoEncontradoException` is now `public`, matching the public `ContactoService.comprobarMismaGestoria` that throws it. Javadoc updated.

### m3: only expected row errors show their message
- New package-private `explotacion/FilaImportacionException`.
- `procesarContacto` throws it for the three expected row errors:
  - explotación not found
  - contacto dado de baja
  - Decision 15 failure: the importer now catches `RecursoNoEncontradoException` specifically, where it used to catch any `RuntimeException`.
- `motivoContactoDe` is now package-private static and handles three cases:
  - `DataIntegrityViolationException` returns `ContactoService.MOTIVO_TELEFONO_EN_USO`.
  - `FilaImportacionException` returns its own message.
  - Anything else returns `MOTIVO_CONTACTO_GENERICO` ("No se pudo importar el contacto de esta fila").
- `procesarExplotacion` and `procesarAnimal` are untouched and still throw `IllegalStateException`, shown through the old `motivoDe`.

### Test (written first; it failed to compile before the change)
New `ExplotacionImportMotivoContactoTest` (3 tests):
- A `FilaImportacionException` shows its message.
- A `DataIntegrityViolationException` whose message contains internal details (`gestoria_id=7`) returns exactly the generic phone message.
- An `IllegalStateException("detalle interno: gestoria 7, SQL ...")` and a plain `RuntimeException` both return `MOTIVO_CONTACTO_GENERICO`, which contains neither "detalle interno" nor "SQL".
