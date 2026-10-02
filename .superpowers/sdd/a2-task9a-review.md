# A2 Task 9a — Independent review (review-modal logic)

## VERDICT: APPROVED, with one Important test gap to close before 9b builds on it

No Critical findings. The logic is correct on every path that decides what gets approved.
- `aprobar` always sends the version of the detail that is loaded and shown.
- It is impossible while the form is dirty, while a request (including its follow-up reload) is in flight, or while no detail is loaded.
- Every 409 reloads the detail and discards the form before anything else can be sent.
- The backend's version check is a second safety net under all of this.

The Important finding is a test gap on exactly that "in flight includes the reload" invariant: three mutants of it survive the whole suite.

## Commands (from `frontend/`, run by me)

| Command | Result |
|---|---|
| `npm test` | **32 files, 367 tests, all passing** |
| `npm run build` (`tsc -b && vite build`) | OK. Only the pre-existing warning that a chunk is over 500 kB. |
| `npm run lint` (oxlint) | Only the 3 pre-existing `only-export-components` warnings: `button.tsx:58`, `badge.tsx:55`, `AuthContext.tsx:231` |

These numbers match the implementer's report.

## Verified, no finding

### Payload
- **PATCH** (`api.ts:38-49`, `revisionTramite.ts:118-140`):
  - it sends `version` plus only the fields that changed;
  - `crotales` is the complete list, sent only when the trimmed, non-blank list differs from the saved `crotalIndicado` values;
  - undefined keys are never serialised.
  
  A null explotación or tipo is never sent, and the reducer rejects nulling a saved value (`useRevisionTramite.ts:183-185`), so H5 holds. The backend treats `null` as "don't change", which matches.
- **Crotal processing.** `crotalesParaEnviar` only trims and drops blank entries. Nothing normalises, dedupes or classifies crotales. The form starts from `crotalIndicado`, which is correct because the backend re-resolves from what was written.
- **Dirty state.** `estaSucio` is defined as `construirPatch !== null`, so "dirty" and "something to send" can never disagree. Blank rows are not dirty.
- **`rechazar`** posts with no body (`api.ts:61-64`), as H4 requires; api and dialog tests assert it.
- **`aprobar`** sends `{version}`, captured from the render's loaded `detalle` (`useRevisionTramite.ts:429-431`). This is only permitted when `editable && !sucio && libre`, so the version is always the loaded, unedited, current one.

### Approve only what was seen
- **Two-layer single flight.**
  - The synchronous `sesion.enviando` flag (`:406-407`) blocks a double click in the same render; M7 was confirmed by the implementer and the test exists at `:515`.
  - The state `enviando` stays set until `finally`, which runs after the awaited `manejarError` → `recargar` (409) or after the awaited `recargar` (200).
  - Because of that, while the post-409 reload is in flight, every closure the UI holds has `puedeAprobar = false`.
- **After a 409.** `cargado` replaces both `detalle` and `formulario` atomically. The next render's `aprobar` closure uses the fresh version; the resolution-changed test at `:752` checks that the next approve sends v5.
- **Id change mid-flight.**
  - The state is reset in the same render (`:331-335`).
  - The old session is closed and its reloads are aborted.
  - Late action responses and reload results are guarded by `sesion.activa`, but `onCambiado` is still called, which is correct because the server did change.
  - The new trámite's `detalle` is null until it loads, so none of its actions can run.
- **A failed reload** (after a 409 or after a 200) sets `detalle` to null, so nothing stale stays on screen to act on.

### Result handling
Checked against decisions 10–13 and the implementer's table, which matches the code:
- **200 guardar:** the detail is replaced, the form reset and `onCambiado` called.
- **200 aprobar/rechazar:** the returned state is merged in (read-only), then the detail is reloaded; `exito` notice and `onCambiado`.
- **409 on any action:** reload, form discarded, `motivo` shown verbatim, no retry, `onCambiado`.
- **409 followed by a failed reload:** both errors are visible and there are no actions.
- **400:** the edits are kept and the `motivo` shown.
- **403 on aprobar:** the fixed subscription text from context `aprobar-tramite`. The client only logs out on a 401, so there is no logout.
- **404:** the trámite text and `onCambiado`.
- **Network or 5xx:** the generic texts from `mensajeDeError`, and the edits are kept.

### Other checks
- **Success notices.** "Trámite aprobado." and "Trámite rechazado." say nothing about OVZ.net. The api comments ("solo cambia el estado en BD") are kept.
- **Dialog minimal change:**
  - approve sends `detalle.version` and returns early without a detail;
  - both buttons are disabled with `!detalle`;
  - the existing tests are kept, plus one new test (`{"version":3}` on approve, empty body on reject).
  - The dialog still shows Aprobar/Rechazar for every estado and still closes on success. Both are acceptable until 9b.
- **Scope.**
  - Nothing under `backend/` changed.
  - Nothing is staged (`git diff --cached` is empty).
  - No dependency changes: the `package.json` and lock mtimes are from 09-28, and the `public/` assets are from 09-28.
  - Two out-of-scope files are newer than the 9a work: `docs/.../2026-09-28-promptA2-frontend-revision.md` (decision 31) and `.impeccable/surfaces/features-tramites-tramitereviewdialog-tsx-930a9a22.md`. Both match the orchestrator's 9b craft session, not this task. **The orchestrator should confirm it made these edits.**

## Mutations I ran

I ran these in a scratch copy with a junction to `node_modules`, against `src/features/tramites` and `src/shared/api` (196 tests). The scratch copy has since been deleted.

| # | Mutation | Result |
|---|---|---|
| R1 | `catch` does `void manejarError(...)` instead of `await`, so `enviando` is cleared before the post-409 reload | **SURVIVED** |
| R2 | `recargar` dispatches `cargado` without the `sesion.activa` check | **SURVIVED** (defence in depth, since reloads are also aborted) |
| R3 | `respuesta-accion` keeps the old `estado` instead of the returned one | **SURVIVED** |
| R4 | saved crotales compared against the resolved `crotal` instead of `crotalIndicado` | killed (1) |
| R5 | a 404 on an action no longer calls `onCambiado` | killed (1) |
| R6 | the 200 path does `void recargar(...)`, so `enviando` is cleared before the reload | **SURVIVED** |

## Critical

None.

## Important

**I1. The "in flight includes the follow-up reload" invariant has no test.**
- Where: `useRevisionTramite.ts:411-416` (`catch` → `await manejarError` → `await recargar`, then `finally`) and `:439`.
- Evidence: mutants R1 and R6 survive.
- Scenario for R1:
  1. `aprobar` (or `guardar`) gets a 409.
  2. Suppose `enviando` were cleared before the reload returned.
  3. For the length of the reload, the modal shows the **stale** detail (old version, and for a PATCH conflict the user's dirty form) with Aprobar enabled.
  4. A click then sends the stale version.
  
  The backend's version check would answer 409 again, so nothing wrong gets approved. But it breaks decision 10 ("fresh data before anything else"), the approve-only-what-was-seen rule and the hook's own documented contract ("`enviando` stays set through the reload that follows an action").
- Why nothing catches it: every current test resolves the reload inside the same `act`, so the in-between state is never observed.
- Today's code is correct. 9b or a later refactor could break it silently.
- **Fix:** add two tests that hold the reload's GET open with a deferred MSW handler, one after a 409 and one after a 200. While the reload is pending, assert:
  - `enviando !== null`;
  - `puedeAprobar`, `puedeGuardar` and `puedeRechazar` are all `false`;
  - calling `aprobar()` sends no POST.
  
  Then release the reload and assert that the next `aprobar` sends the fresh version.

## Minor

**M1. The merged state from the 200 response is never observed by a test** (`useRevisionTramite.ts:251-266`; mutant R3 survives). Today this is nearly dead code: the reload overwrites it at once, and a failed reload nulls it. If open question 2 is resolved as I recommend below, the merge becomes load-bearing and needs a test: with the reload held open or failed, `detalle.estado === "APROBADO"`, `editable === false`, and the new version.

**M2. A 404 on an action leaves the trámite on screen as pending and actionable** (`useRevisionTramite.ts:386-397`).
- After a 404 on aprobar or rechazar, `detalle` is kept (still `PENDIENTE_REVISION`), so the buttons stay enabled and the user can click again and get another 404.
- Decision 11 only requires the text and a queue refresh, and a 404 here is practically unreachable, because trámites can't be deleted and the gestoría can't change mid-session.
- It still reads oddly next to the 409 path, which is carefully honest.
- **Fix:**
  - for aprobar and rechazar, a 404 sets `carga: {estado: "no-encontrado"}` with `detalle` null;
  - for PATCH, see open question 1.

**M3. The session check doesn't bind the session to the id** (`useRevisionTramite.ts:405-406`).
- `ejecutar` takes the *latest* `sesionRef.current` but the *closure's* `tramiteId`, `detalle` and `formulario`.
- Scenario: a stale handler from trámite A's render is invoked after the hook has switched to B. For example, 9b stores `revision.rechazar` in state for its inline confirmation, or calls it from a timeout.
- The stale call would pass the session check and act on A with A's version. That version is still what the user saw for A, so it isn't wrong in itself.
- But it would then dispatch A's response and A's reload into **B's** state, showing A's data under B.
- Normal `onClick` binding can't reach this.
- **Fix:** store `tramiteId` in `Sesion` and require `sesion.tramiteId === tramiteId` in `ejecutar`. Alternatively, 9b must always call the handler from the current render, and never store it.

**M4. The legacy dialog doesn't reset `detalle` on a direct A→B id change** (`TramiteReviewDialog.tsx:35-58`).
- Scenario: B's load fails, `detalle` still holds A, and the buttons are enabled. `handleAprobar` would send B's id with A's version.
- This is unreachable today, because `TramitesPage` goes A→null→B via `onClose`, and 9b replaces the dialog anyway.
- It is pre-existing, and only noted so 9b doesn't copy the pattern. The new hook handles this correctly.

**M5. Network or 5xx on aprobar/rechazar don't call `onCambiado`.** A timeout after the server committed would leave the queue stale until the next refresh. It is safe, because a retry hits the version or estado check and gets a 409, which does reload and refresh. Optional fix: call `onCambiado` on `red` and `servidor` for aprobar and rechazar too.

## Open questions: recommendations

**(1) A 404 on PATCH is ambiguous.**
- The explotación id always comes from the caller's own complete list (decisions 20 and 22 loader), and explotaciones cannot be deleted by any endpoint.
- So "an explotación of another gestoría" is realistically unreachable from this UI. The trámite reading is the only one that can happen, and even that is practically unreachable today.
- The trámite text is the right default, and the `errores.ts:245-246` comment documents the ambiguity honestly.
- Recommendation: keep the text. In 9b, cheaply disambiguate by treating a PATCH 404 like the other actions' 404 plus a reload:
  - if the `GET` also returns 404 → `carga: no-encontrado` (trámite gone; this also fixes M2);
  - if the `GET` returns 200 → the explotación was the problem. Keep the edits and show "La explotación elegida ya no está disponible. Elige otra."
- This needs no backend change. Otherwise, leave it as it is; it is acceptable.

**(2) A failed reload after a successful approve or reject.**
- Setting `detalle` to null is honest: it never shows the trámite as pending. But it is **worse** than keeping the merged state.
- The merged state is exactly what the server returned in the 200 response: `estado` APROBADO/RECHAZADO, `crotales`, `version`, `tipoTramite`, `explotacionId`.
- The only fields not refreshed (`mensajeOriginal`, the explotación label) cannot change on approve or reject.
- Keeping it is therefore accurate. It satisfies decision 13 ("the modal shows the new state read-only") even when the network hiccups, and it is still safe, because `estado` is no longer `PENDIENTE_REVISION`, so `editable` is false and there are no actions.
- Recommendation:
  - on a reload failure **after a 200**, keep the merged detail;
  - leave `carga` "listo" with a non-blocking reload notice and Reintentar (e.g. a separate `recargaFallida` flag, or an `aviso` of kind `error`);
  - add the M1 test;
  - keep the current null-on-failure behaviour for the **409** path, where the held detail really is stale.

## Success notices

"Trámite aprobado." and "Trámite rechazado." are fine: no OVZ.net wording, no suggestion of execution. 9b may render them or rely on the read-only estado badge.

# Re-review (fixes)

## VERDICT: APPROVED

All the fixes check out against the real code and tests. "Approve only what was seen" still holds and there are no regressions. Two new Minor notes follow; neither blocks 9b.

The orchestrator has confirmed that the edits to the plan (decision 31) and to `.impeccable/surfaces/...tramitereviewdialog...md` are its own 9b craft work, so the scope note from the first review is closed.

## Commands (from `frontend/`, run by me)

| Command | Result |
|---|---|
| `npm test` | **32 files, 379 tests, all passing** |
| `npm run build` | OK. Only the pre-existing warning that a chunk is over 500 kB. |
| `npm run lint` | Only the 3 pre-existing `only-export-components` warnings |

- Nothing is staged.
- `backend/` is unchanged.
- The scratch copy was deleted and `node_modules` is intact.

## Mutations I re-ran

I ran these in a scratch copy against `src/features/tramites` and `src/shared/api` (208 tests).

| # | Mutation | Result |
|---|---|---|
| R1 | `void manejarError` (clears `enviando` before the 409 reload) | killed (I1 409 test) |
| R6 | `void recargarTrasExito` (clears `enviando` before the 200 reload) | killed ("tras un 200 de aprobar, mientras la recarga está abierta…") |
| R2 | `pedirDetalle` returns the data with no `sesion.activa` check | killed (deterministic R2 test) |
| X1 | the uncertain network/5xx path keeps the stale pending detail when the reload fails | killed ("aprobar 500 y la recarga también falla") |
| X2 | a PATCH 404 followed by a successful reload calls `onCambiado` | killed |
| X3 | `ejecutar` without the `sesion.tramiteId === tramiteId` check | killed (M3 test) |
| X4 | a PATCH 404 followed by a successful reload discards the form (`cargado` instead of `detalle-refrescado`) | killed |

## Verified

**I1: the reload after an action is part of the send.**
- Code: every reload is awaited inside `ejecutar` before `finally` clears `enviando`.
- Tests: both lock tests hold the reload's GET open and assert:
  - `enviando` stays set;
  - every `puede*` is false;
  - `aprobar()` / `rechazar()` send nothing.
- The 409 test then approves again with the fresh v5.
- R1 and R6 now fail.

**Q2 and M1: a failed reload after a 200.**
- `recargarTrasExito` dispatches `recarga-fallida`, which keeps the merged detail and only sets `recargaFallida`.
- The test asserts:
  - `carga: listo`;
  - estado APROBADO, version 5, and the tipo and crotales from the 200;
  - the explotación label and `mensajeOriginal` are preserved;
  - `editable` and `puedeAprobar` are false;
  - the `exito` notice and `recargaFallida` coexist.
- `reintentarRecarga` recovers without blanking the detail, and a second failure sets `recargaFallida` again.
- The merged state is also asserted while the reload is still open (M1).
- `reintentarRecarga` runs outside `ejecutar`, so `enviando` is not set during it. That is safe: it only runs when `recargaFallida` is set, which only happens after a 200, when the estado is no longer `PENDIENTE_REVISION` and there are no actions. It is also session- and id-guarded, and won't start while another reload is open.

**Q1 and M2: a 404 on an action.**
- A 404 on aprobar, rechazar or guardar reloads the detail.
- **Reload also 404:**
  - `carga: no-encontrado` with the trámite text;
  - `detalle` is null, so there are no actions;
  - `onCambiado` is called;
  - the text is not repeated in `aviso`.
- **PATCH 404, reload 200:** the detail is refreshed, the edits are kept, the notice shows "La explotación elegida ya no está disponible. Elige otra.", and `onCambiado` is **not** called.
- **Not calling `onCambiado` there is correct.** `TramiteRevisionService.actualizar` throws `RecursoNoEncontradoException` inside its `@Transactional` (`findByIdAndGestoriaId` on the explotación, `TramiteRevisionService.java:93-94`), and `TramiteController.traducirErrores` maps it *after* the rollback. So nothing changed on the server, and the queue has nothing new to show.

**M3: session bound to the trámite.**
- `Sesion.tramiteId` is checked in both `ejecutar` and `reintentarRecarga`.
- The test stores trámite 7's `aprobar`, switches to 8 and calls the stored handler. Result: no POST, trámite 8 untouched, no `onCambiado`.
- In the render where the id has changed but the new session effect hasn't run yet, the old session's id doesn't match the new handler, so nothing runs. That is the safe side.

**M5: network error or 5xx on aprobar/rechazar.**
- The result is treated as uncertain: `onCambiado` is called, then a strict reload.
- **If the reload fails,** `detalle` is set to null with `carga: error`, next to the action's error notice.
- **That is the honest choice:** the only detail held is the pre-action `PENDIENTE_REVISION` one, which may now be false. Keeping it would risk showing pending for a trámite that is actually approved or rejected, and would leave Aprobar enabled with a possibly stale version.
- It never shows pending, and X1 proves a test locks this in.
- Guardar on a network error or 5xx still keeps the edits and does not reload; the N6 test covers this.

**R2.** The test replaces `obtenerDetalleTramite` for one call with a test-controlled promise that ignores the `AbortSignal`, switches to trámite 8, releases the promise and asserts that 8 is untouched. The mutation fails it. It is deterministic.

**The two replaced tests are not weakened.**
- The old combined "404 en aprobar, rechazar o guardar" test only checked the notice text and `onCambiado`. It is now four branch tests: aprobar and rechazar 404→404, guardar 404→404, and guardar 404→200. Together they assert more: carga, detalle, every `puede*`, `enviando`, the form, `sucio`, and the `onCambiado` count.
- The old "recarga tras aprobar falla" test was rewritten for the specified new behaviour, with stronger assertions: merged fields, coexisting notices, and a retry path with a GET count.

**Approve only what was seen: no regressions.**
- `aprobar` still captures `detalle.version` from a render where `editable && !sucio && libre`.
- Every path that could leave a stale pending detail on screen now does one of two things:
  - reloads under the send lock (409, 404, uncertain);
  - leaves a non-pending, server-confirmed detail (after a 200).
- The PATCH 404 → 200 path keeps a *pending* detail. It shows the freshly loaded detail and version, and the form is still dirty, so Aprobar stays disabled until the user saves or discards.

## Minor (new, non-blocking)

**N1. After a PATCH 404 followed by a successful reload, the kept form is diffed against a possibly different fresh detail** (`useRevisionTramite.ts`, `trasNoEncontrado`, the `detalle-refrescado` branch).
- Scenario:
  1. Between the 404 and the reload, another user saves the trámite, e.g. changing the tipo from ALTA to CENSO, which bumps the version.
  2. The form is kept with its old tipo ALTA.
  3. Against the fresh detail that now counts as a change, so the next Guardar sends `tipoTramite: ALTA` with the *fresh* version. The backend accepts it and silently reverts the other user's edit.
- The window is tiny, and a PATCH 404 is already realistically unreachable from this UI. There is no approval impact, because approving still needs a clean form and the fresh version.
- If the fresh estado is no longer `PENDIENTE_REVISION`, the form is kept but `editable` is false. 9b should render the saved detail, not the form, in read-only mode.
- **Fix:** keep the edits only if `resultado.detalle.version === detalle.version` (the version the PATCH was sent with) and the trámite is still `PENDIENTE_REVISION`. Otherwise handle it like a 409: dispatch `cargado`, which discards the form.

**N2. Wording after an uncertain outcome.**
- Scenario: a network error on aprobar, then the reload shows the trámite was in fact APROBADO. The `aviso` still reads "No se ha podido conectar con Ganera. Inténtalo de nuevo en unos segundos." next to an APROBADO estado.
- It isn't dishonest: the badge shows the truth, and there are no actions. But "Inténtalo de nuevo" contradicts what the screen shows.
- **Fix:** 9b can word this case differently, for example "No se pudo confirmar la respuesta; este es el estado actual del trámite." Alternatively, the hook can swap the `aviso` when the reload shows a non-pending estado.
