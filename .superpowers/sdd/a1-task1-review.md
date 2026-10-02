# Prompt A1 - Task 1 review (independent reviewer)

VERDICT: Approved (with one Important fix recommended before Task 3 starts using the normalizer)

Test suite, run by the reviewer from `backend/` with `JAVA_HOME="/c/Program Files/Java/jdk-23" ./mvnw test`:
exit 0, **167 tests, 0 failures, 0 errors, 0 skipped** (counted from surefire XML reports). That matches the implementer's report (135 before this task + 32 new).

## What was verified

1. **Migration V15** matches the plan exactly. `gestoria_id BIGINT NOT NULL REFERENCES gestoria(id)` is on both tables. `activo BOOLEAN NOT NULL DEFAULT TRUE`, `DROP COLUMN tipo`, `rol VARCHAR(20) NOT NULL` and `created_at TIMESTAMP NOT NULL DEFAULT now()` are all present, with both indexes. `telefono UNIQUE` (V6) is untouched, so it stays global. Every statement is valid PostgreSQL. `ADD COLUMN ... NOT NULL` with no default only fails if the table has rows, which the plan accepts: it fails loudly, never silently. All 15 migrations applied on H2 during the suite.
2. **Entities.** Both classes extend `GestoriaScopedEntity` and no longer declare their own `id` or `createdAt`, so nothing is mapped twice. `contacto_explotacion.created_at` now exists for the inherited `createdAt`. `rol` uses `@Enumerated(STRING)` with length 20. `activo` is a primitive `boolean` that defaults to true (a test covers this). The Hibernate INSERT/SELECT column lists in the test log match the SQL. `TipoContacto` is gone: grep finds no reference in main, test, SQL or frontend.
3. **Repositories.** Every new finder takes an explicit `gestoriaId`. `findByTelefono` has a Javadoc saying it is for the 3b webhook only and that the importer and authenticated endpoints must use `findByGestoriaIdAndTelefono`. It currently has no callers in `src/main`. The generated SQL confirms the derived queries resolve correctly. For example, `findByExplotacionIdInAndGestoriaIdAndContactoActivoTrue` produces `... join contacto c1_0 ... where e1_0.id in (?,?) and g1_0.id=? and c1_0.activo`.
4. **Tests.** The `@DataJpaTest` classes do not run the tenant interceptor. That means `gestoriaFilter` is not active there, so the tests prove the explicit `gestoriaId` parameter does the isolation, not the ambient filter. That is the right thing to prove. The batch and list tests put data in both Gestorías (`listarActivosExcluyeInactivosYContactosDeOtraGestoria`, `cargaEnLotePorContactos...`, `cargaPorExplotaciones...`). No existing assertion was weakened:
   - The old `getTipo()==TITULAR` assertion was replaced with gestoria and activo checks.
   - The unique-phone test is stronger: it now checks a duplicate across Gestorías, and there is a new same-Gestoría duplicate case.
   - In `TramiteControllerTest`, the Contacto now belongs to the same Gestoría as its Trámite, including `gestoriaAjena` in the three cross-tenant tests. Those tests still assert 404 and no change.
5. **Scope.** Only the files listed in Task 1 were touched. `CLAUDE.md` was not changed (that is correctly left to Task 7).

## Findings

### Critical
None.

### Important

**I1. `TelefonoNormalizador.java:26`: the international branch accepts numbers starting with `+0`, which are not E.164.**
- `INTERNACIONAL = \+\d{8,15}` lets through country codes that start with 0. Checked with jshell against the compiled class:
  - `"+0034612345678"` gives `Optional[+0034612345678]`
  - `"+06123456789"` gives `Optional[+06123456789]`
- `+0034...` is a realistic typo in a spreadsheet: someone mixes up the `+` and `00` prefixes.
- Consequences:
  - (a) The stored phone never matches Twilio's `From` (`whatsapp:+34612345678`), so in 3b that Contacto is silently unreachable.
  - (b) It gets around the "one phone, one Contacto" invariant (Decision 1). `+34612345678` and `+0034612345678` are the same real line but pass the global `UNIQUE` as two different rows, possibly in two Gestorías.
- The plan's rule 3 says "E.164 internacional", and E.164 country codes never start with 0. So this breaks the spec's intent, even though it fits the literal "8-15 digits" wording.
- **Fix:** change it to `Pattern.compile("\\+[1-9]\\d{7,14}")`. Add `"+0034612345678"` and `"+0612345678"` to `rechazaTelefonosInvalidos`.
- Optionally, map `"+00" + rest` to the `00` branch instead of rejecting it. Rejecting is simpler and fails closed.

### Minor

**M1. `TelefonoNormalizador.java:25`: a non-breaking space (U+00A0) is not treated as a separator.**
- `\s` in Java is ASCII-only by default. `"612 345 678"` gives `Optional.empty`.
- Cells pasted into Excel from the web or from PDFs often contain NBSP (and sometimes U+2007/U+202F). Task 3 would report these as a row error even though a user sees "612 345 678".
- It fails closed, so it is not a safety issue, only a usability one.
- **Fix:** use `[\\s\\u00A0\\u2007\\u202F.\\-]` (or `\\p{Zs}`), and add a test.

**M2. `TelefonoNormalizador.java:43-50`: `"+612345678"` (9 digits, a Spanish mobile with `34` missing) is accepted as-is as a foreign number.**
- This follows rule 3 as written, so it is not a defect in Task 1.
- Flag it to Antonio: such a Contacto would silently never match incoming WhatsApp messages.
- One option: add a test that pins this behaviour so it is a deliberate choice.

**M3. `ContactoExplotacion.java` / V15: nothing checks that `contacto_explotacion.gestoria_id` matches `contacto.gestoria_id` and `explotacion.gestoria_id`.**
- There is no composite FK, and the finders filter only on the link's own `gestoria_id`.
- If a later task ever saved a link with gestoria A that points at B's Contacto or Explotación, every finder would return it to A.
- Task 1 only defines the model, so this is advisory. The Task 2 and Task 3 services must set the link's gestoria from the authenticated `gestoriaId` and load both ends with `findByIdAndGestoriaId` / `findByCodigoRegaAndGestoriaId`. The E2E test "link A's Contacto to B's Explotación → 404, no link" is the regression guard.
- A DB-level guard is possible but not required: `UNIQUE(id, gestoria_id)` on the parent tables plus composite FKs.

**M4. `ContactoExplotacionRepositoryTest.java:72-83` (`buscarPorContactoYExplotacionNuncaDevuelveFilasDeOtraGestoria`) has data only in Gestoría A.**
- It still proves the `gestoriaId` predicate, because the query with B's id returns empty while the row exists. But it is not a real two-tenant setup.
- **Fix:** also link a B contacto to a B explotación, and assert the A lookup still returns A's row with the right rol.

**M5. `gestoria/UsuarioExplotacion.java:17-20`: the Javadoc is now stale.**
- It still says it has no repository, "igual que ContactoExplotacion", but `ContactoExplotacion` now has one.
- **Fix:** reword it. This could be left to Task 7's documentation pass.

## Edge cases checked (jshell against the compiled class)

| Input | Result | Matches rule? |
|---|---|---|
| `+34 612 345 678` | `+34612345678` | yes |
| `whatsapp: +34612345678` | `+34612345678` | yes |
| `" 612345678 "`, `"+34612345678\n"`, `"612\t345\t678"` | `+34612345678` | yes |
| `0034512345678`, `34512345678` (first digit 5) | empty | yes |
| `6+12345678`, `++34612345678`, `+34+612345678` | empty | yes |
| Arabic-Indic digits `٦١٢٣٤٥٦٧٨` | empty | yes: Java `\d` is ASCII-only without `UNICODE_CHARACTER_CLASS` |
| `612/345/678`, `(612)345678`, `+34 (612) 345678` | empty | yes |
| `6123456789` (10 digits), `+3461234567890`, `00447911123456` | empty | yes (rule 6) |
| `whatsapp:`, `whatsapp:whatsapp:612345678` | empty | yes |
| `+612345678` | `+612345678` | literal rule 3, see M2 |
| `+0034612345678`, `+06123456789` | accepted | **no, see I1** |
| `612 345 678` with NBSP | empty | debatable, see M1 |
