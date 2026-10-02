# Prompt A1 - Task 5 report (Crotales en tramites)

## Files

New (main):
- `backend/src/main/resources/db/migration/V16__create_tramite_crotal.sql` - exactly the plan's schema (id, gestoria_id FK NOT NULL, tramite_id FK NOT NULL, crotal_indicado VARCHAR(30), crotal VARCHAR(30), animal_id FK nullable, resolucion VARCHAR(20), created_at, UNIQUE(tramite_id, crotal_indicado), idx on tramite_id and gestoria_id).
- `tramite/TramiteCrotal.java` (GestoriaScopedEntity; tramite LAZY, crotalIndicado updatable=false, crotal, animal LAZY nullable, resolucion STRING).
- `tramite/ResolucionCrotal.java` {EN_INVENTARIO, AMBIGUO, NO_ENCONTRADO, SIN_EXPLOTACION}.
- `tramite/TramiteCrotalRepository.java`: `findByTramiteIdAndGestoriaIdOrderByIdAsc`, `findByTramiteIdInAndGestoriaIdOrderByIdAsc` (both explicit gestoriaId, ordered by id).
- `tramite/CrotalNormalizador.java` (pure static; nested `TipoCrotal` + record `CrotalNormalizado(valor, tipo)`), `tramite/CrotalInvalidoException.java` (message = motivo).
- `tramite/TramiteCrotalService.java`: `reemplazarCrotales`, `recalcularEnlaces`, `crotalesPorTramite` (one query).
- `tramite/TramiteCrotalResponse.java` {crotalIndicado, crotal, animalId, enInventario, resolucion}.

Modified (main):
- `explotacion/AnimalRepository.java`: + `findByExplotacionIdAndGestoriaIdAndCrotal` (Optional, exact) and `findByExplotacionIdAndGestoriaIdAndCrotalEndingWithOrderByIdAsc` (suffix). Both require explotacionId AND gestoriaId.
- `tramite/TramiteResponse.java`, `tramite/TramiteDetalleResponse.java`: + `crotales` (never null, `[]` when none). Existing fields unchanged; `from(...)` now takes the crotal list.
- `tramite/TramiteController.java`: constructor + `TramiteCrotalService`; `listar` now takes `@AuthenticationPrincipal` and loads crotales of the whole page in ONE query with the JWT gestoriaId (list query itself unchanged: `findAll`/`findByEstado` on the ambient filter); `detalle` adds crotales by tramite id + gestoriaId.

Tests:
- New `CrotalNormalizadorTest` (23 cases, pure JUnit, incl. NBSP/U+2007/U+202F, "ß1234", fullwidth digits, 31 chars).
- New `TramiteCrotalServiceTest` (18, @DataJpaTest, two Gestorias with overlapping suffixes).
- New `TramiteCrotalEndToEndTest` (4, RANDOM_PORT + TestRestTemplate + Hibernate statistics, two Gestorias).
- `TramiteControllerTest`: +4 tests, existing `listar` calls updated to pass a principal, `nuevoController()` passes the service.

## Resolution rules implemented
- Normalizer: strip every whitespace/space char (Character.isWhitespace || isSpaceChar, covers U+00A0/U+2007/U+202F); validate `[A-Za-z0-9]{1,30}` BEFORE uppercasing (so "ß"->"SS" can't sneak in); uppercase (Locale.ROOT). Digits only: <=3 -> invalid "Crotal demasiado corto: indica al menos los últimos 4 dígitos"; 4..12 -> INCOMPLETO; >=13 -> COMPLETO. Anything else valid (two letters prefix, "A1234", "12A34") -> COMPLETO.
- Service: no explotacion -> SIN_EXPLOTACION (crotal = indicado, no link). COMPLETO -> exact match in explotacion+gestoria -> EN_INVENTARIO/NO_ENCONTRADO. INCOMPLETO -> suffix in explotacion+gestoria: 1 -> EN_INVENTARIO with full crotal + link; >1 -> AMBIGUO; 0 -> NO_ENCONTRADO.
- `reemplazarCrotales` normalizes ALL first (throws before any DB access), collapses duplicates keeping first order, loads existing rows by tramite+gestoria, `deleteAll` + `flush` BEFORE inserting (IDENTITY inserts execute immediately while deletes wait for flush -> would hit UNIQUE(tramite_id, crotal_indicado)), then resolves and saves.
- `recalcularEnlaces` re-resolves every row from `crotalIndicado` (never from `crotal`) against the tramite's current explotacion.
- Defensive checks (Decision 15 spirit), all throwing `contacto.RecursoNoEncontradoException` (-> 404 convention): tramite gestoria != gestoriaId; tramite's explotacion gestoria != gestoriaId; and before linking, animal's explotacion id != tramite's explotacion id or animal gestoria != gestoriaId.

## TDD evidence
1. Tests written first; compile failed (CrotalNormalizador/TramiteCrotalService/etc. missing).
2. Types + normalizer + a stub service throwing UnsupportedOperationException: normalizer 23/23 green; TramiteCrotalServiceTest 18 red (17 errors + 1 failure), TramiteControllerTest 10 errors, TramiteCrotalEndToEndTest 4 errors.
3. Real service -> all 62 targeted tests green.

Mutation proofs (each reverted, verified by grep afterwards):
- N+1 in `listar` (one `crotalesPorTramite` call per tramite): `TramiteCrotalEndToEndTest.listadoCargaLosCrotalesDeTodaLaPaginaEnUnaSolaConsulta` fails, `expected: 2L but was: 7L` (5 tramites). Correct code: exactly 2 prepared statements (page + one crotal batch; page not full so no count).
- Unscoped suffix lookup (`animalRepository.findAll()` filtered by suffix): 6 failures in TramiteCrotalServiceTest (3 wrong resolution/AMBIGUO; 3 where the defensive explotacion/gestoria check fired: other-explotacion, other-Gestoria, recalcular).
- Batch crotal load without gestoriaId (`findAll()` filtered by tramite ids): fails `TramiteControllerTest.laCargaDeCrotalesDelListadoUsaElGestoriaIdDelUsuarioNoElFiltroAmbiente` and `TramiteCrotalServiceTest.crotalesPorTramite...`.

## Full suite
`JAVA_HOME="/c/Program Files/Java/jdk-23" ./mvnw test` -> **Tests run: 288, Failures: 0, Errors: 0, Skipped: 0, BUILD SUCCESS** (239 + 49 new: 23 + 18 + 4 + 4).

Grep: no `findById(`, `findByCodigoRega(`, `findByNif(`, `findByTelefono(`, `findByCrotal(` in new code (only pre-existing comments and the pre-existing `AnimalRepository.findByCrotal` used by the importer, untouched). All new comments ASCII.

## Deviations / concerns for the reviewer and Task 6
- Plan's file list names `AnimalRepository.findByExplotacionIdAndCrotalInAndGestoriaId`; per the Task 5 text/brief I implemented exact (`...AndCrotal`) + suffix (`...CrotalEndingWithOrderByIdAsc`) finders instead. I did not use `crotal_ultimos_digitos` for 6-digit input (optional in the plan); the suffix query covers every length and volume per explotacion is small.
- Repository method names carry `OrderByIdAsc` (deterministic order); no `deleteByTramiteIdAndGestoriaId` - replaced by find(explicit gestoriaId) + `deleteAll` + `flush` for the IDENTITY/UNIQUE ordering reason above.
- `aprobar`/`rechazar` responses now also include `crotales` (one extra query each, same helper) so `TramiteResponse` is never half-filled. Logic/ordering of those endpoints untouched.
- Service methods are `@Transactional` (join the caller's). A `CrotalInvalidoException`/`RecursoNoEncontradoException` thrown inside marks a surrounding transaction rollback-only. Task 6 should let it propagate out of its own `@Transactional` and map it in the controller (as `ContactoController` does), not catch it inside the transaction and then commit, or it will get `UnexpectedRollbackException`. Nothing is written before those exceptions are thrown.
- `comprobarTramiteDeLaGestoria` reads `tramite.getExplotacion().getGestoria().getId()`, which initializes a lazy Explotacion proxy (one query) - only on write paths, not on GET.
- `TramiteCrotalEndToEndTest` cleans `tramite_crotal` in `@AfterEach` before tramites; any future E2E that seeds crotales must do the same (FK).
- Not touched: TipoTramite, PATCH, aprobar/rechazar rules, frontend, importer, contactos, ganaderos, Stripe, Twilio, IA, `.agents/`, skills, docs. Nothing staged or committed.
