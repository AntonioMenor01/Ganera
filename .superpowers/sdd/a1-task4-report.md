# Prompt A1 - Task 4 report (implementer): Ganaderos y Animales (lectura)

## Result
- Full suite: **Tests run: 233, Failures: 0, Errors: 0, Skipped: 0, BUILD SUCCESS**. That is 220 existing tests plus 13 new ones in `GanaderoEndToEndTest`.
- Nothing was staged or committed. The frontend, Stripe, Twilio, AI, the importer, the contactos endpoints and tramites are untouched.

## Files (`backend/src/main/java/com/ganera/core/`)

### New
- **`ganadero/GanaderoController.java`**:
  - `GET /ganaderos`, with `@PageableDefault(sort = "nombre")`.
  - `GET /ganaderos/{id}`, which returns 404 with no body when the Ganadero is absent or belongs to another Gestoria.
  - gestoriaId always comes from `@AuthenticationPrincipal GaneraUserPrincipal`.
- **`ganadero/GanaderoConsultaService.java`**:
  - Read-only, `@Transactional(readOnly = true)`.
  - `listar`: `findByGestoriaId(gestoriaId, pageable)`, then one grouped count query for the page's ids. The count query is skipped when the page is empty. A Ganadero with no explotaciones counts 0.
  - `detalle`: `findByIdAndGestoriaId`, then `findByGanaderoIdAndGestoriaIdOrderByCodigoRegaAsc`, then one batch `findByExplotacionIdInAndGestoriaIdAndContactoActivoTrue`, grouped by explotacion id. Contactos within an explotacion are sorted by nombre, then id. An explotacion with no contactos gets `[]`.
- **`ganadero/GanaderoResumenResponse.java`**: `{id, nombre, nif, numeroExplotaciones}`.
- **`ganadero/GanaderoDetalleResponse.java`**: `{id, nombre, nif, explotaciones:[{id, codigoRega, nombre, contactos:[{contactoId, nombre, telefono, rol}]}]}`, built from nested records `ExplotacionDeGanadero` and `ContactoDeExplotacion`.
- **`explotacion/AnimalResponse.java`**: `{id, crotal, crotalUltimosDigitos}`.
- **`explotacion/ConteoExplotacionesPorGanadero.java`**: record for the grouped count projection.

### Modified
- **`ganadero/GanaderoRepository.java`**: added `findByIdAndGestoriaId(Long, Long)` and `findByGestoriaId(Long, Pageable)`.
- **`explotacion/ExplotacionRepository.java`**: added two methods.
  - `findByGanaderoIdAndGestoriaIdOrderByCodigoRegaAsc(Long, Long)`.
  - `@Query contarPorGanadero(gestoriaId, ganaderoIds)`: `select new ...ConteoExplotacionesPorGanadero(e.ganadero.id, count(e)) from Explotacion e where e.gestoria.id = :gestoriaId and e.ganadero.id in :ganaderoIds group by e.ganadero.id`.
- **`explotacion/AnimalRepository.java`**: added `findByExplotacionIdAndGestoriaId(Long, Long, Pageable)`.
- **`explotacion/ExplotacionController.java`**:
  - New `GET /explotaciones/{id}/animales`, with `@PageableDefault(sort = "crotal")`. It checks `ExplotacionRepository.findByIdAndGestoriaId` first and returns 404 with no body if that is empty. Otherwise it returns the page of `AnimalResponse`.
  - The constructor now also takes `AnimalRepository`.
- **`contacto/ContactoExplotacionRepository.java`**: added `@EntityGraph(attributePaths = "contacto")` to `findByExplotacionIdInAndGestoriaIdAndContactoActivoTrue`. This is the permitted repository-only change and avoids N+1 on Contacto. There are no other callers besides `ContactoExplotacionRepositoryTest`, which is still green.

### Tests (`backend/src/test/java/com/ganera/core/`)
- **New `ganadero/GanaderoEndToEndTest.java`** (13 tests):
  - Setup: `@SpringBootTest(RANDOM_PORT)` + `TestRestTemplate`, plus `hibernate.generate_statistics=true`.
  - Two Gestorias are onboarded, each with its own JWT. Each imports a POI-built `.xlsx` with Explotaciones, Animales and Contactos sheets:
    - A: Ganadero A1 with 2 explotaciones, Ganadero A2 with 1, 4 animals, and 2 contactos with 3 links. The same titular is linked to 2 explotaciones.
    - B: 1 ganadero, 1 explotacion, 2 animals, 1 contacto.
  - Every Ganadero then gets `ovzUsuario` and `ovzPasswordCifrada` through the repository.
  - Cases:
    - **List isolation and counts:** A sees exactly A1 (2 explotaciones) and A2 (1). B sees only its own ganadero, and B's body contains no A nif or name.
    - **List pagination:** `size=1&page=1` returns 1 row with totalElements 2.
    - **Detail cross-tenant:** B's token on A's ganadero returns 404 with an empty body. A nonexistent id also returns 404 with an empty body.
    - **Detail content:** A's token returns the full detail: explotaciones ordered by codigoRega, and contactos with telefono, nombre and rol (TITULAR/EMPLEADO). The same titular appears on both explotaciones.
    - **Detail empty list:** an explotacion with no contactos returns `contactos: []`.
    - **Detail after DELETE:** after `DELETE /contactos/{id}`, the inactive contacto disappears from the detail.
    - **Animales cross-tenant (mandatory):** A's explotacion with B's token returns 404 with an empty body, and the reverse too. A nonexistent explotacion also returns 404.
    - **Animales content:** only that explotacion's animals, sorted by crotal. `size=2&page=1` returns the 3rd animal, with totalElements 3 and totalPages 2. The other explotacion returns only its own animal.
    - **No OVZ in bodies:** the raw list and detail bodies contain no "ovz" (case-insensitive) and no password value.
    - **401:** all three routes return 401 without a JWT.
    - **N+1:** `listadoYDetalleNoHacenNMasUno` asserts at most 3 prepared statements each for the list and the detail request.
- **Modified `explotacion/ExplotacionControllerTest.java`**: the two `new ExplotacionController(...)` calls now pass an autowired `AnimalRepository`. This is a constructor-signature change only.

## TDD evidence
1. I wrote the E2E before any production code. It ran red at 12 of 13, with 9 failures and 3 errors. The routes did not exist, so authenticated requests fell through to a secured `/error` and returned 401. Only the 401-without-JWT test passed, which is expected.
2. After the implementation, `GanaderoEndToEndTest` and `ExplotacionControllerTest` are green.
3. **Mutation on the N+1 guard:** I removed the new `@EntityGraph(attributePaths = "contacto")`. `listadoYDetalleNoHacenNMasUno` then failed with `[consultas del detalle] ... to be less than or equal to 3`. I restored it and the test went green again.
4. Full suite: 233 green.

## N+1 verification (show-sql run of the N+1 test)
- **`GET /ganaderos` issues 2 statements:**
  1. `... from ganadero ... where g1_0.gestoria_id = ? and g2_0.id=? order by nombre fetch first ? rows`
  2. `select e1_0.ganadero_id,count(e1_0.id) from explotacion ... where ... e1_0.gestoria_id=? and e1_0.ganadero_id in (?,?) group by e1_0.ganadero_id`
  - A full page would add one more: the pagination count.
- **`GET /ganaderos/{id}` issues 3 statements:**
  1. The ganadero, by `id=? and gestoria.id=?`.
  2. Its explotaciones, by `ganadero.id=? and gestoria.id=? order by codigo_rega`.
  3. One `contacto_explotacion join contacto` query that fetches the contacto columns inline.
- In every query the explicit `gestoria_id=?` parameter appears alongside the ambient filter's `gestoria_id = ?`.

## Grep of new code
- `findById(`, `findByCodigoRega(`, `findByNif(` and `findByTelefono(` have no uses in the new or modified main code.
- The only hits in `ganadero/` are the pre-existing `GanaderoRepository.findByNif` declaration and a Javadoc mention of "findById".
- The test looks up ganaderos with `findAll().stream().filter(nif)`, not `findByNif`.

## Deviations and concerns
1. **Method name:** `findByGanaderoIdAndGestoriaIdOrderByCodigoRegaAsc` instead of the plain `findByGanaderoIdAndGestoriaId`. The brief asked for a deterministic order, and a derived `OrderBy` is the simplest way to get it.
2. **Default sorts added:** `nombre` for `/ganaderos` and `crotal` for `/animales`, through `@PageableDefault`, so pagination is stable. Neighbouring endpoints (`/explotaciones`, `/tramites`) have none. A client can still override with `?sort=`.
3. **Unknown sort field:** as on the existing paginated endpoints, a `?sort=` on a field that does not exist is not validated. Spring Data would throw, which is pre-existing behaviour.
4. **Separate Spring context:** the E2E test uses `generate_statistics=true`, so it gets its own Spring context, which adds a few seconds to the suite.
5. **Response DTOs:** the responses never serialize entities, so the OVZ fields cannot leak. `GanaderoResumenResponse.numeroExplotaciones` is a primitive `long`.

## Fixes after review (I1, M1, M2, M3; M4 skipped as instructed)
Full suite: **Tests run: 239, Failures: 0, Errors: 0, Skipped: 0, BUILD SUCCESS**. That is 233 plus 6 new tests: 5 in `GanaderoEndToEndTest` and 1 in `ContactoExplotacionRepositoryTest`. Nothing was staged or committed.

### I1: the N+1 guard for GET /ganaderos is now tight
- New test `listadoConMuchosGanaderosSigueSiendoDosConsultas`. Gestoria A imports 4 more ganaderos, 6 in total. One of them has 2 explotaciones.
- It asserts the counts for all 6 ganaderos, and **exactly 2 prepared statements**: the page query plus the grouped count. The page is not full, so no pagination count runs.
- **Mutation proof:** I temporarily replaced `contarPorGanadero` in `GanaderoConsultaService.listar` with a per-ganadero `findByGanaderoIdAndGestoriaIdOrderByCodigoRegaAsc(id, gestoriaId).size()`.
  - The new test failed: `[consultas del listado con 6 ganaderos] expected: 2L but was: 7L`.
  - The old `listadoYDetalleNoHacenNMasUno` still passed under that mutation, which confirms the reviewer's finding.
  - I restored the code and the test passes with 2.

### M1: whitelist of sortable fields
- New `shared/web/OrdenacionPermitida` exposes `esValida(Sort, Set<String>)` and `MOTIVO = "Campo de ordenación no permitido."`.
- `GanaderoController` allows `{nombre, nif, id}`. `ExplotacionController /explotaciones/{id}/animales` allows `{crotal, id}`.
- Any other sort property returns `400` with a `MotivoErrorResponse` body. The check runs before any query, so the result can never be ordered by an ovz field.
- Both handlers now return `ResponseEntity<?>`. The existing `/explotaciones` and `/tramites` list endpoints are unchanged.
- Tests:
  - `/ganaderos`: `ovzUsuario`, `ovzPasswordCifrada`, `noExiste`, and a mixed `nombre,desc&sort=ovzUsuario` all return 400 with a motivo. `nombre`, `nif,desc` and `id` return 200, and `nif,desc` actually orders by nif.
  - `/animales`: `explotacion.ganadero.ovzUsuario`, `noExiste` and `crotalUltimosDigitos` return 400 with a motivo. `crotal,desc` and `id` return 200.
  - Before the fix these tests failed (200 instead of 400).

### M2: stable default sort
- `/ganaderos` now defaults to `@PageableDefault(sort = {"nombre", "id"})`.
- Test `ordenPorDefectoDeGanaderosEsNombreYLuegoId`: two ganaderos named "Ganadero Repetido" come back in ascending id order, and all names are sorted.
- **This test also passed before the fix,** because H2 returns tied rows in insertion order. It documents the contract but cannot prove the tie-breaker on H2.

### M3: the contacto's own gestoria is required too
- `ContactoExplotacionRepository`: the finder was renamed to `findByExplotacionIdInAndGestoriaIdAndContactoGestoriaIdAndContactoActivoTrue(ids, gestoriaId, contactoGestoriaId)`. It keeps `@EntityGraph(attributePaths = "contacto")`.
- The old name has no remaining references.
- The caller `GanaderoConsultaService` passes the JWT gestoriaId twice.
- `ContactoExplotacionRepositoryTest`: the existing test now uses the new name. New test `cargaPorExplotacionesExcluyeEnlaceCuyoContactoEsDeOtraGestoria` covers an incoherent link whose gestoria is A but whose contacto belongs to B. That link is excluded, and only A's contacto is returned.
- Red evidence: both tests errored because the method did not exist yet, which is a compile-level red. They are green after the change.
- The N+1 test still passes, so the detail stays at 3 statements or fewer with the extra condition.
