# Prompt A1 - Task 4 review (independent reviewer)

VERDICT: Approved (no Critical findings; one Important test-strength gap, fix before Task 7 closes if cheap)

## Suite
`JAVA_HOME="/c/Program Files/Java/jdk-23" ./mvnw test` from `backend/`: **Tests run: 233, Failures: 0, Errors: 0, Skipped: 0, BUILD SUCCESS**. `GanaderoEndToEndTest` ran 13 tests, all green. This matches the implementer's report.

## Checks performed
1. **Explicit gestoriaId on every query.** Confirmed.
   - `GanaderoRepository.findByIdAndGestoriaId` and `findByGestoriaId(Pageable)`.
   - `ExplotacionRepository.findByGanaderoIdAndGestoriaIdOrderByCodigoRegaAsc`.
   - `ExplotacionRepository.contarPorGanadero`: the JPQL really has `e.gestoria.id = :gestoriaId` (ExplotacionRepository.java:30).
   - `ContactoExplotacionRepository.findByExplotacionIdInAndGestoriaIdAndContactoActivoTrue`.
   - `ExplotacionRepository.findByIdAndGestoriaId` and `AnimalRepository.findByExplotacionIdAndGestoriaId`.
   - gestoriaId always comes from `@AuthenticationPrincipal`.
   - There are no `findById(`, `findByNif(`, `findByCodigoRega(` or `findByTelefono(` calls in the new code.
2. **Explotación whose gestoria differs from its Ganadero's.** The schema does not prevent it: `explotacion.gestoria_id` and `ganadero.gestoria_id` are independent columns. The new code still stays safe:
   - The detail filters the Ganadero by gestoria, then its explotaciones by gestoria, then the links by gestoria.
   - The count filters by `e.gestoria.id`.
   - So a foreign Explotación hanging off one of A's Ganaderos is never shown or counted for A. A foreign Ganadero is never reachable.
3. **`GET /explotaciones/{id}/animales`.** The 404 check (`findByIdAndGestoriaId`) runs before any animal query (ExplotacionController.java:28-31). Animals are filtered by both `explotacionId` and `gestoriaId`.
4. **OVZ credentials.** Only records are serialized (`GanaderoResumenResponse`, `GanaderoDetalleResponse` and its nested records, `AnimalResponse`). No entity or lazy proxy reaches Jackson.
5. **`@EntityGraph(attributePaths="contacto")` on `findByExplotacionIdInAndGestoriaIdAndContactoActivoTrue`.** The `ContactoActivoTrue` predicate already inner-joins `contacto`, so adding the fetch changes no semantics. The only callers are `GanaderoConsultaService` and `ContactoExplotacionRepositoryTest`; Tasks 2 and 3 do not use it.
6. **Tests.** The E2E really covers two tenants:
   - Both tenants have Ganaderos, Explotaciones, Animales and Contactos, imported over real HTTP.
   - The mandatory case (B reads animals of A's explotación → 404) is present, with an empty-body assertion, and the reverse direction is also tested.
   - All three routes return 401 without a JWT.
   - The "no ovz" test is meaningful: `ovzUsuario` and `ovzPasswordCifrada` are set on every Ganadero. Serializing an entity would emit the `ovzUsuario` key and the decrypted password, and the test would catch either.
7. **Scope.** Only the Task 4 files listed in the brief were touched, plus the constructor-arg update in `ExplotacionControllerTest`. The `TenantIsolationEndToEndTest` diff belongs to Task 1 (removal of `TipoContacto`).

## Critical
None.

## Important
**I1. The N+1 guard for `GET /ganaderos` passes trivially and would not catch a regression.**
- **Where:** `GanaderoEndToEndTest.java:351-358`.
- **Why it is too weak:**
  - Gestoría A has only 2 Ganaderos, and the default page size is 20.
  - Spring Data skips the pagination count query when page 0 is not full, so the listing issues 2 statements (page + grouped count).
  - A naive N+1 implementation (one `countBy...` per Ganadero) would issue 1 + 2 = 3 statements. That still satisfies `isLessThanOrEqualTo(3)`.
  - So the listing half of the guard does not detect the regression it claims to prevent. The implementer's mutation check only exercised the detail half (removing `@EntityGraph`).
  - The detail half is meaningful: 3 links with 2 distinct contactos give ≥5 statements without the graph.
- **Fix:** give A at least 4 Ganaderos in the fixture, or assert the listing count exactly (`isEqualTo(2)`, or `≤ 3` with ≥3 Ganaderos). Re-run the mutation by temporarily replacing `contarPorGanadero` with a per-Ganadero count to prove it goes red.

## Minor
**M1. `?sort=` accepts any entity property, including `ovzUsuario`.**
- **Where:** `GanaderoController.java:30` (`/ganaderos`); `ExplotacionController.java:26` (`/animales`).
- **Scenario:** `GET /ganaderos?sort=ovzUsuario` orders the caller's own Ganaderos by their plaintext OVZ username. That is a relative-order oracle over a credential field that the DTOs deliberately hide.
- **Impact is low:**
  - It is same-tenant only.
  - The importer never sets `ovzUsuario`, so a user cannot plant probe values.
  - `ovzPasswordCifrada` is ciphertext with a random IV, so sorting by it reveals nothing.
  - On `/animales`, a nested `explotacion.ganadero.ovzUsuario` path has a single value within one explotación.
- **Pre-existing class:** an unknown sort field yields a 500 (`PropertyReferenceException`), the same as `/explotaciones` and `/tramites`. This is not an isolation leak, because all queries also carry the explicit gestoriaId.
- **Suggested fix (can wait for a global pass):** whitelist sortable properties, or strip unknown/sensitive `Sort.Order`s before querying, and map invalid ones to 400.

**M2. The default sort `nombre` on `/ganaderos` is not unique.**
- **Where:** `GanaderoController.java:30`.
- **Scenario:** two Ganaderos with the same name can swap between pages or repeat across pages, depending on how the database breaks ties.
- **Fix:** `@PageableDefault(sort = {"nombre", "id"})`. `/animales` sorts by `crotal`, which is unique, so it is fine.

**M3. The detail does not assert the Contacto's own gestoria (defense in depth).**
- **Where:** `ContactoExplotacionRepository.java:24`.
- **Current behaviour:** the detail trusts `contacto_explotacion.gestoria_id`. Decision 15 already guarantees that the link, the Contacto and the Explotación belong to the same Gestoría on every write path (service and importer), so this is not exploitable today.
- **Suggestion:** adding `AndContactoGestoriaId` (the same gestoriaId passed twice) would make the query self-sufficient if a future write path forgets that check.

**M4. Tests do not cover A reading B's Ganadero detail, or B's own detail.**
- **Where:** `GanaderoEndToEndTest`.
- **Current coverage:** only the B→A direction is tested for `/ganaderos/{id}`. The symmetry is covered for `/animales`.
- **Suggestion:** optional. Add the reverse assertion, as was done for animals.
