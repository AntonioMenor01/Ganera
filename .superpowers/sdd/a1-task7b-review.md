# Prompt A1 - Task 7b review (closing: greps, smoke, docs, commit proposal) - independent reviewer

VERDICT: **Changes required**. There is one Important finding: a false statement in a non-negotiable rule of `CLAUDE.md`. It is a one-sentence documentation fix. Everything else is Minor or a nit. No code change is needed.

## Test run

From `backend/`, I ran `JAVA_HOME="/c/Program Files/Java/jdk-23" ./mvnw clean test`.
- Result: **`Tests run: 415, Failures: 0, Errors: 0, Skipped: 0` — BUILD SUCCESS**, exit 0.
- This matches the report and the documented count.
- My Maven and Surefire JVMs exited afterwards. Only PID 14128, the VS Code language server, is still running.

## Verified, no finding

**Against the code (not the reports)**

- **PATCH `/tramites/{id}` order** (`TramiteController.actualizar`, then `TramiteRevisionService.actualizar`):
  1. 400 when `version` is missing. This happens before any DB access.
  2. 400 for an invalid `tipoTramite`. The controller parses it.
  3. 404 via `findConBloqueoByIdAndGestoriaId`.
  4. 409 when the trámite is not pending.
  5. 409 for a stale version.
  6. 404 for a foreign Explotación. It is resolved before the crotales, so it wins over a 400 for an invalid crotal.
  7. 409 when two crotales resolve to the same Animal.
  8. 200 with `version + 1`.

  The docs are also right on these points: null means "don't change", `crotales: []` clears the list, the Explotación cannot be cleared, and changing only the Explotación re-resolves the crotales.
- **Aprobar order:**
  1. 403 from `puedeAprobarTramites`.
  2. 400 for no body, `{}` or `version:null`. The body is `@RequestBody(required=false)`.
  3. 404.
  4. 409 for estado.
  5. 409 for version.
  6. Re-resolution, then `ResolucionCrotalesCambiadaException`. It extends `TramiteConflictoException`, and `@Transactional(noRollbackFor=…)` is set on `aprobar` only. It flushes, increments the version, and throws.
  7. One accumulated 409 for: Explotación, tipo, AMBIGUO/SIN_EXPLOTACION, an incomplete NO_ENCONTRADO, format, and same Animal.
  8. 200 APROBADO.

  No other 409 in the codebase commits data. The duplicate-phone 409 in Contactos rolls back, and the inactive 409 writes nothing.
- **Decision 28, provisional.** `ES[0-9]{12}` when the crotal starts with `ES`, otherwise `[A-Z]{2}[0-9]{8,12}`. It is applied to the written crotal for `NO_ENCONTRADO`, and to `fila.getCrotal()` (the Animal's crotal, freshly re-resolved) for `EN_INVENTARIO` (7a I1, option a).
- **Rechazar:** only from `PENDIENTE_REVISION`, needs no version, and increments it.
- **Locking and version:**
  - Lock plus `refresh` in `cargarConBloqueo`.
  - The exact +1 increment is forced with a JPQL `UPDATE … version = version + 1` after a flush.
  - `ConcurrencyFailureException` and `DataIntegrityViolationException` map to 409.
  - The 7a M2 caveat (raw `EntityManager` calls are not translated) is documented.
- **`CrotalNormalizador`:**
  - Strips whitespace including NBSP, plus `-` `.` `/`, then uppercases.
  - Valid means `[A-Za-z0-9]{1,30}`, validated before uppercasing. Documenting it as `[A-Z0-9]` after uppercasing is equivalent.
  - 3 or fewer digits gives 400. Digits only, 4–12, is INCOMPLETO. Everything else is COMPLETO.
- **Resolution** (`TramiteCrotalService`): COMPLETO resolves by exact match and INCOMPLETO by suffix, always with `explotacionId` and `gestoriaId`. Duplicates collapse. The Explotación is always re-resolved from `crotal_indicado`.
- **`tramite_crotal`** (V16): the columns, `UNIQUE(tramite_id, crotal_indicado)` and the indexes match the docs.
- **Sort whitelists, exact fields and defaults:**

  | Endpoint | Allowed fields | Default |
  |---|---|---|
  | `/explotaciones` | `codigoRega, nombre, id` | `codigoRega, id` |
  | `/tramites` | `id, estado, createdAt` | `createdAt, id` DESC |
  | `/ganaderos` | `nombre, nif, id` | `nombre, id` |
  | `/explotaciones/{id}/animales` | `crotal, id` | `crotal` |
  | `/contactos` | `nombre, telefono, id` | `nombre, id` |

  The motivo "Campo de ordenación no permitido." matches `OrdenacionPermitida.MOTIVO`.
- **`/error`:** it is `permitAll` in `SecurityConfig`, with a comment that matches decision 29.
- **REQUIRES_NEW (decision 19):**
  - `ExplotacionImportService` and `ExplotacionImportController` have no `@Transactional`.
  - `open-in-view: true`.
  - All three `procesar*` methods re-enable the filter and use explicit `gestoriaId` finders.
  - The new CLAUDE.md wording is precise ("no EM bound OR outer transaction").
  - The N2 Javadoc nit is honestly flagged as still pending.
- **Finders:** `findByCodigoRega`, `findByNif` and `findByCrotal` no longer exist. The scoped replacements (`findByCodigoRegaAndGestoriaId`, `findByNifAndGestoriaId`, `findByCrotalAndGestoriaId`) are used. `findByTelefono` has only its declaration and Javadoc.
- **Neutral importer message:** the text matches `MOTIVO_IDENTIFICADOR_NO_DISPONIBLE` exactly. The 20-character `animal.crotal` limit, the normalization, and the last 6 digits taken from the normalized crotal are also correct.
- **Contactos endpoints:**
  - 201, 400, 404 and 204 as documented.
  - 409 "No se puede usar ese teléfono para un contacto." is caught in the controller.
  - Enlazar returns 409 for an inactive Contacto, and checks the same Gestoría (decision 15).
  - Listing and the Ganadero detail filter on `activo`.
- **`TelefonoNormalizador`:** the documented rules match the code, including `+[1-9]\d{7,14}` and the rejection of `+[6789]\d{8}`.
- **Migrations:** V15, V16 and V17 match the descriptions.
- **Frontend claims:** they match `TramiteReviewDialog.tsx` and `api.ts`. `aprobar` sends no body, the buttons show in every estado, and a 409 gives a generic "Inténtalo de nuevo".
- **Test count:** 415, confirmed.
- **`TipoTramite` enum:** unchanged (`ALTA, BAJA, CENSO, MOVIMIENTO, DEMORA`), as `ganera-prompts.md` says.

**Coverage.** Every item appears in `CLAUDE.md` or `ganera-prompts.md`:
- Decisions 1a/1b, 2, 3, 4 (the "trabajador → exactly one Explotación" sentence is gone from `CLAUDE.md`), 11, 17 with the M3 caveat, 18, 19, 23, 27 with R1 and the A2 note, 28 marked provisional and including EN_INVENTARIO, 29, and 30.
- Decision 30: "read-only" became "no approvals" in the non-negotiable rule, the EstadoSuscripcion bullet and the banner note. The remaining "read-only" hits (lines 86 and 202) refer to OVZ sync, which is correct.
- 6-M2 (3c risks), 6-M3, 6-M4, 6-M5, 6-M7, 6-M-R3 ("the only 409 that persists data"), 7a-I1, 7a-M1, 7a-M2, 7a-M4, 7a-N2.
- The frontend being unable to approve until A2.
- `clean test`, in both Commands and Architecture notes.
- The stale "not wired to any endpoint yet" and "rely solely on the ambient filter" statements were corrected.
- "Next pending step" now names A2 as the unblocked step.

**progress.md.** The per-task counts match the reports: 135 → 171, 204 → 206, 217 → 220, 233 → 239, 288, 352 → 358, 408 → 415. The verdicts are correct, including Task 6 "Changes required" followed by Approved. The 7a session cut, and `clean` failing at testCompile with 8 javac errors, match `a1-task7a-report.md:7-11`.

**Commit path list.** I compared the report's 79 paths with `git status --porcelain -uall`:
- Nothing is missing and nothing extra is included.
- The only paths left out are the 15 `a1-*` reports, `capturaTramites.jpeg`, `logo.jpg` and the 5 files in `frontend/public/*`.
- `.agents/` and `skills-lock.json` are already committed in `1ca905e` and unchanged. `.claude/skills/impeccable` is in `.gitignore`.
- The deletion of `TipoContacto.java` is included.

**Commit message.** It follows the repo convention: `feat: Prompt X - …`, a Spanish body without accents, bullets, and the `Co-Authored-By: Claude Opus 5.5` trailer. Its technical claims match the code, apart from the two points in Minor M3.

**Clean state**
- Only `java.exe` 14128 (the VS Code JDT language server) is alive. No smoke-test JVM is left.
- Nothing is staged.
- No `*.mv.db`, `*.trace.db`, seed or token files are in the repo. The only `.xlsx` is the tracked fixture `inventario-prueba.xlsx`.
- The only files newer than `a1-task7a-review.md` (15:55:08) are `CLAUDE.md`, `ganera-prompts.md`, `progress.md` and `a1-task7b-report.md`.
- 7b did not touch production code, tests, `frontend/`, `.agents/`, skills, `skills-lock.json`, `logo.jpg`, `PRODUCT.md` or `DESIGN.md`. The `frontend/public/*` files date from 15:16, before 7a closed, and N3 already covered them.

## Critical

None.

## Important

**I1. `CLAUDE.md` falsely claims `findByTelefono` is the only unscoped finder left.**
- Where: `CLAUDE.md:106-109` (non-negotiable rules).
- Current text: "…the only unscoped finder left is `ContactoRepository.findByTelefono`, reserved for the 3b webhook."
- The problem:
  - `AnimalRepository.java:11` still declares `List<Animal> findByExplotacionIdAndCrotalUltimosDigitos(Long explotacionId, String crotalUltimosDigitos)`. It has no `gestoriaId`, and it predates A1.
  - The 7b report knows this: section 1, and open question 4. The rule text contradicts that.
  - This is exactly the finder 3b is most likely to reach for. It matches by `crotalUltimosDigitos`, which is "what Contactos actually type in WhatsApp" (Domain model).
  - A rule that says no such finder remains invites that use.
- Fix: replace the sentence with:

  > …and the only unscoped finder meant to be used is `ContactoRepository.findByTelefono`, reserved for the 3b webhook (see Operational flow step 2). One pre-A1 unscoped finder still exists with no callers — `AnimalRepository.findByExplotacionIdAndCrotalUltimosDigitos` (no `gestoriaId`); don't call it (A1 resolves crotales with `findByExplotacionIdAndGestoriaIdAndCrotalEndingWithOrderByIdAsc`) — remove or scope it before 3b.

  Optionally add the same note to the 3b "Pendientes heredados del Prompt A1" in `ganera-prompts.md`.

## Minor

**M1. "Every Aprobar from the UI gets 400" is not true for a blocked subscription.**
- Where:
  - `CLAUDE.md:124-126`: "so every 'Aprobar' from the UI gets `400`…"
  - `CLAUDE.md:636`: "it always gets `400`"
  - `ganera-prompts.md:546`: "→ `400` siempre"
  - `progress.md` final bullet: "da siempre 400"
  - the commit message warning
- The problem: the controller checks `puedeAprobarTramites` first. A Gestoría in `TRIAL_EXPIRADO_SIN_PAGO`, in `SUSPENDIDA`, or without a `Suscripcion` still gets 403, and the UI shows its specific subscription message.
- Fix: add "(except a Gestoría whose subscription blocks approving, which still gets the `403` and its specific message)" in `CLAUDE.md:125`. Alternatively, reword to "every Aprobar that passes the subscription gate gets 400". In the Spanish texts, "400 siempre (salvo el 403 de suscripción, que va antes)" is enough.

**M2. Inactive Contactos: "(409)" is attributed to the importer too.**
- Where: `CLAUDE.md:47-49`: "…and can't be linked to Explotaciones (409), whether through the API or the importer."
- The problem: the importer gives a row error (`MOTIVO_CONTACTO_INACTIVO`, "El contacto con ese teléfono está dado de baja"), not a 409.
- Fix: "…can't be linked to Explotaciones (`409` via the API, a row error in the importer)."

**M3. Commit message details.**
- The path is elided: "Plan con 31 decisiones en docs/superpowers/plans/2026-09-25-promptA1-...md". Use the full path, `docs/superpowers/plans/2026-09-25-promptA1-contactos-crotales-revision.md`, so it can be searched in `git log`.
- The approval bullet lists "formato provisional ES+12 u otro pais 2 letras+8-12" as if it applied only to NO_ENCONTRADO. Antonio's 7a I1 decision extends it to the Animal's crotal for EN_INVENTARIO. Add "(tambien al crotal del Animal en inventario)".
- M1 above also applies to the "Aviso" paragraph: "aprobar desde la UI da 400 (salvo el 403 de suscripcion)".

## Nits (optional)

- **N1.** `CLAUDE.md:60-62` says "the importer and every authenticated endpoint use `ContactoRepository.findByGestoriaIdAndTelefono` instead".
  - The importer does. The `/contactos` endpoints do no phone lookup at all; they rely on the `UNIQUE` violation, as the next sentence says.
  - More precise: "no authenticated code path ever uses it (the importer looks up with `findByGestoriaIdAndTelefono`; `/contactos` relies on the UNIQUE violation, never a lookup)".
- **N2.** `ganera-prompts.md:21` (the verbatim Prompt 0: "Un trabajador está vinculado a una única explotación") and `:398` (the Prompt 4 narrative: "`SUSPENDIDA` = solo lectura") still carry the old wording.
  - Both are historical transcripts, so they are not contradictions of current state.
  - A short "(superado por A1, decisión 4 / decisión 30)" annotation would stop a future reader from taking them as current.
- **N3.** The 7b report's open questions for Antonio are sensible. None of them blocks the commit: `capturaTramites.jpeg`, `frontend/public/*`, whether to commit the `a1-*` reports (precedent says no), the unscoped Animal finder (see I1), the provisional decision 28, and the M4 data `UPDATE`.

---

# Re-review (doc fixes)

VERDICT: **Approved.** Every finding (I1, M1, M2, M3, N1, N2) is fixed in the real text, and the fixes are accurate against the code. The fixes add no false claims.

I did not re-run the suite: nothing under `backend/` or `frontend/` changed since the first review.

## Verified

**I1**
- `CLAUDE.md:106-113` now says `findByTelefono` is the only unscoped finder *meant to be used*.
- It names `AnimalRepository.findByExplotacionIdAndCrotalUltimosDigitos` as the pre-A1 finder: no `gestoriaId` and no callers. This is still true: `AnimalRepository.java:11`, zero callers in `src/main`.
- It points to the scoped finder A1 actually uses, `findByExplotacionIdAndGestoriaIdAndCrotalEndingWithOrderByIdAsc` in `TramiteCrotalService`.
- It defers the remove-or-scope choice to Antonio.
- The same item was added to the 3b pending items in `ganera-prompts.md:317`.

**M1.** The 403 exception is now stated in all five places:
- `CLAUDE.md:123-128`
- `CLAUDE.md:641-644`
- `ganera-prompts.md:547`
- `progress.md:480-481`
- the commit-message "Aviso"

This matches `TramiteController.aprobar`, which checks `puedeAprobarTramites` before the version. A Gestoría with no `Suscripcion` is also covered, because the check is fail-closed.

**M2.** `CLAUDE.md:48-49` now reads "`409` via the API, a row error in the importer". This matches `ContactoController` and `ExplotacionImportFilaService.MOTIVO_CONTACTO_INACTIVO`.

**M3.** The commit message now has:
- the full plan path;
- the provisional format rule "tambien sobre el crotal del Animal en inventario", which matches `motivoCrotalNoAprobable` / `EN_INVENTARIO`.

**N1.** `CLAUDE.md:60-63` is correct. Only the importer looks up by phone, using `findByGestoriaIdAndTelefono`. `ContactoService` does no phone lookup and relies on the UNIQUE violation.

**N2.** `ganera-prompts.md:21` and `:399` now have "superado por A1" annotations, and the historical text is kept.

**Scope**
- Since the first review, the only files modified are `CLAUDE.md`, `ganera-prompts.md`, `progress.md` and `a1-task7b-report.md`, plus this review.
- No code, tests, `frontend/` or skills were touched, and nothing is staged.

**Commit path list**
- The same 79 paths.
- Compared with `git status --porcelain -uall`, nothing is missing and nothing extra is listed.
- The only paths left out are the 16 `a1-*` reports (including this review), `capturaTramites.jpeg`, `logo.jpg` and the 5 files in `frontend/public/*`.

## Nits (cosmetic, non-blocking)
- `CLAUDE.md:63` runs to about 135 characters. The rest of the file wraps at about 100.
- `CLAUDE.md:643-644` has an awkward "— except … — (approving …)" sandwich.
- One commit-message body line ("crotal del Animal en inventario; dos crotales al mismo animal -> 409); re-resolucion al aprobar con 409 que confirma la") is over 72 characters.

Re-wrapping these is optional.
