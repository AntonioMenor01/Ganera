# A2 Task 9a — Review-modal logic (`useRevisionTramite`): implementer report

This task is frontend only and logic only; there is no visual design. `backend/`, `public/`,
`index.html`, the docs, the plan, `.impeccable/`, the skills, `logo.jpg` and `preview.png` were not
touched. No dependencies were added, no git operations were run, and nothing is staged.

## Files

**New**
- `frontend/src/features/tramites/revisionTramite.ts`: pure logic with no React and no network:
  - `AVISO_CAMBIOS_SIN_GUARDAR`
  - `FormularioRevision`
  - `esEditable`
  - `formularioDesdeDetalle`
  - `crotalesParaEnviar`
  - `construirPatch`
  - `estaSucio`
- `frontend/src/features/tramites/revisionTramite.test.ts`: 21 tests.
- `frontend/src/features/tramites/useRevisionTramite.ts`: a reducer plus the hook.
- `frontend/src/features/tramites/useRevisionTramite.test.tsx`: 46 tests, run with MSW against the real axios client.
- `frontend/src/features/tramites/api.test.ts`: 8 tests.

**Modified**
- `frontend/src/features/tramites/api.ts`:
  - `obtenerDetalleTramite(id, signal?)` now takes an optional abort signal.
  - New `ActualizacionTramite` type and new `actualizarTramite(id, cambios)`, which sends `version` plus only the fields that are present.
  - `aprobarTramite(id, version)` sends `{version}`.
  - `rechazarTramite(id)` sends **no body** (H4).
  - The comment "solo cambia el estado en BD, no OVZ.net" is kept, and a matching one was added to rechazar.
- `frontend/src/shared/api/errores.ts`: new context `"guardar-tramite"`.
  - Generic text: "No se han podido guardar los cambios del trámite. Inténtalo de nuevo."
  - 404 text: the trámite text.
  - `errores.test.ts` gets 2 new assertions and the context is added to the exhaustive loop.
- `frontend/src/features/tramites/TramiteReviewDialog.tsx`: **minimum change**. Approve now calls `aprobarTramite(tramiteId, detalle.version)` and returns early if `detalle` is null.
  - The look and behaviour are unchanged. It still closes on success; decision 13's "don't close" comes with 9b.
  - Approving from the UI works again.
  - The hook is **not** wired into the dialog; 9b replaces the dialog anyway.
- `frontend/src/features/tramites/TramiteReviewDialog.test.tsx`: +1 test (approve sends `{"version":3}` from the loaded detail; reject sends an empty body). The existing tests are unchanged and green.

## Hook public API (for 9b)

```ts
// features/tramites/useRevisionTramite.ts
export type AccionRevision = "guardar" | "aprobar" | "rechazar";

export type CargaRevision =
  | { estado: "inactivo" }                                   // tramiteId === null
  | { estado: "cargando" }
  | { estado: "error"; error: ErrorApi; mensaje: string }    // load or post-action reload failed
  | { estado: "no-encontrado"; mensaje: string }             // 404: "Este trámite ya no existe o no es de tu gestoría."
  | { estado: "listo" };

export type TipoAvisoRevision =
  "conflicto" | "validacion" | "prohibido" | "no-encontrado" | "error" | "exito";
export interface AvisoRevision { tipo: TipoAvisoRevision; accion: AccionRevision; mensaje: string }
export type ResultadoCambio = { aceptado: true } | { aceptado: false; motivo: string };
export interface ExplotacionAsignada { id: number; codigoRega: string | null; nombre: string | null }
export interface OpcionesRevisionTramite { onCambiado: () => void }

export interface RevisionTramite {
  carga: CargaRevision;
  detalle: TramiteDetalle | null;           // non-null only when carga.estado === "listo"
  reintentarCarga: () => void;
  explotacionAsignada: ExplotacionAsignada | null;   // the SAVED one, with its label

  formulario: FormularioRevision;           // { explotacionId: number|null; tipoTramite: string|null; crotales: string[] }
  editable: boolean;                        // listo && estado === "PENDIENTE_REVISION"
  sucio: boolean;
  puedeQuitarExplotacion: boolean;          // false once the saved value is non-null (H5)
  puedeQuitarTipo: boolean;                 // idem
  cambiarExplotacion: (explotacionId: number | null) => ResultadoCambio;
  cambiarTipoTramite: (tipo: TipoTramite | null) => ResultadoCambio;
  anadirCrotal: (valor?: string) => ResultadoCambio;      // default ""
  cambiarCrotal: (indice: number, valor: string) => ResultadoCambio;
  quitarCrotal: (indice: number) => ResultadoCambio;
  descartarCambios: () => void;

  enviando: AccionRevision | null;          // stays set through the reload that follows an action
  puedeGuardar: boolean;                    // editable && sucio && !enviando
  puedeAprobar: boolean;                    // editable && !sucio && !enviando
  puedeRechazar: boolean;                   // same as puedeAprobar
  avisoCambiosSinGuardar: string | null;    // "Guarda antes de aprobar" when editable && sucio
  guardar: () => Promise<void>;             // never throws; no-op when not allowed
  aprobar: () => Promise<void>;
  rechazar: () => Promise<void>;            // NO confirmation here: 9b adds it

  aviso: AvisoRevision | null;
  descartarAviso: () => void;
}

export function useRevisionTramite(
  tramiteId: number | null,
  opciones: OpcionesRevisionTramite,
): RevisionTramite;
```

Also exported from `revisionTramite.ts`: `AVISO_CAMBIOS_SIN_GUARDAR`, `FormularioRevision`,
`esEditable`, `formularioDesdeDetalle`, `crotalesParaEnviar`, `construirPatch` and `estaSucio`.

### Edit rejection texts (`ResultadoCambio.motivo`)
- Not editable (not loaded, or not `PENDIENTE_REVISION`): "Este trámite no se puede editar ahora."
- A request is in flight: "Espera a que termine la acción en curso."
- Bad crotal index: "Ese crotal ya no está en la lista."
- Setting the explotación to null when a value is saved: "Una vez asignada, la explotación no se puede quitar. Elige otra si es necesario."
- Setting the tipo to null when a value is saved: "Una vez asignado, el tipo de trámite no se puede quitar. Elige otro si es necesario."

9b should normally never trigger these; the inputs are simply disabled or hidden. They exist so the hook enforces the rules itself.

### Result handling, as implemented

| Case | Detail / form | `aviso` | `onCambiado` |
|---|---|---|---|
| guardar 200 | replaced by the response; form reset | cleared | yes (the queue shows tipo and crotales) |
| aprobar/rechazar 200 | returned state applied at once (read-only), then a full `GET` reload | `exito`: "Trámite aprobado." / "Trámite rechazado." | yes |
| any 409 | full `GET` reload; form **discarded** to the fresh data; never retried | `conflicto` with the backend `motivo` verbatim | yes |
| 409, then the reload fails | `carga: error` (`detalle` null, no actions) plus the conflict notice; `reintentarCarga` keeps the notice | `conflicto` | yes |
| 400 | edits kept | `validacion` with the `motivo` | no |
| 403 on aprobar | unchanged | `prohibido` with the fixed subscription text (context `aprobar-tramite`); never logs out | no |
| 404 on any action | unchanged | `no-encontrado`: "Este trámite ya no existe o no es de tu gestoría." | yes |
| network / 5xx / other | edits kept | `error` with the generic texts from `mensajeDeError` | no |

A notice persists until the next action starts (the `enviar` transition clears it) or `descartarAviso()` is called. Edits do not clear it.

## Design choices

- **Reducer plus hook.** Every response touches detail, form, notice and `enviando` together. A pure reducer makes each transition atomic, so a new detail is never shown with the old form. `rechazoDeCambio` is the single source for edit rules: the reducer uses it to ignore an invalid change, and the hook functions use it to return the reason. The async parts (requests, abort, single flight) stay in the hook.
- **Single flight** has two layers:
  - `enviando` in state, which drives the UI;
  - a synchronous `sesion.enviando` flag. Without it, two calls in the same render would both get through, because state arrives a render late. Mutation M7 proves the flag is needed.
- **Sessions per `tramiteId`.** When the id changes, the state is reset *in the same render* using the "adjust state during render" pattern, so the previous trámite is never visible under the new id. An effect then opens a new session and closes the old one:
  - the old session's reloads are aborted;
  - a late action response for the old id never touches state, but still calls `onCambiado`, because the server really changed;
  - action POSTs themselves are not aborted: once sent, the server may apply them.
- **Dirty = `construirPatch(...) !== null`.** One definition, so "dirty" and "has something to send" can never disagree.
- **Crotales:** trimmed and blanks dropped. There is no normalising, classification, dedupe or validation; the backend answers 400 with a motivo. The form starts from `crotalIndicado`, not the resolved full crotal, because the backend re-resolves from what was written.
- **Explotaciones for the selector: left to 9b.** `TramitesPage` already holds `useTodasLasExplotaciones()` for the REGA column (decision 22). 9b should pass that `carga` down to the dialog instead of loading it twice. The hook only exposes `explotacionAsignada`, the saved one with its label from the detail. For the label of a *newly chosen* id in the form, 9b looks it up in `carga.porId`.
- **`tipoTramite` in the form is `string | null`**, because a tipo unknown to the frontend (prompt B) must survive untouched. `cambiarTipoTramite` only accepts `TipoTramite | null`.

## TDD evidence

- **Red, before any implementation** (`npx vitest run src/features/tramites`): 3 files failed, 7 tests failed, 68 passed.
  - `revisionTramite.test.ts` and `useRevisionTramite.test.tsx` failed to import because the modules didn't exist.
  - In `api.test.ts`, 7 tests failed: actualizarTramite ×4 (no such export), aprobar ×2 (500 from MSW, since no body was sent), and the abort signal test.
- **Red for `guardar-tramite`**: 2 tests failed in `errores.test.ts`.
- **Green**: tramites 136, then 138 after strengthening two tests (below).
- **Final `npm test`: 32 files, 367 tests, all passing.**

## Mutations

Each mutation was applied, tested against `src/features/tramites` and `src/shared/api`, then
reverted. All 24 were killed.

| # | Mutation | Killed by |
|---|---|---|
| M1 | aprobar posts without `{version}` | api ×2, dialog |
| M2 | rechazar posts `{}` | api, dialog, hook 200 |
| M3 | crotales always sent in the PATCH | 27 tests |
| M4 | no trim | 7 |
| M5 | `construirPatch` sends a null explotación | H5 test |
| M6 | hook lets you null a saved explotación | H5 hook test |
| M7 | no synchronous single-flight flag | "dos aprobar en el mismo render" |
| M8 | approve allowed while dirty | 2 dirty-state tests |
| M9 | 409 without reload | 4 |
| M10 | 409 without `onCambiado` | 5 |
| M11 | reload doesn't reset the form | 24 |
| M12 | reload keeps the old detail/version | 3 (including resolution-changed → next approve uses v5) |
| M13 | editable in any state | 13 |
| M14 | load not aborted | 3 (id change, unmount, →null) |
| M15 | no reset on id change | 2 |
| M16 | 400 handled like a 409 (reload/discard) | 2 |
| M17 | 403 with the wrong context | 403 test |
| M18 | late action response writes into the new trámite | late-action test |
| M19 | a new action doesn't clear the old notice | 2 |
| M20 | different unsaved-changes text | 2 |
| M21 | dialog approves without a version | dialog test |
| M22 | failed reload keeps the stale detail | reload-failure-after-409 test |
| M23 | a cancellation treated as an error | id-change test |
| M24 | 404 on an action without `onCambiado` | 404 test |

Two mutants survived the first round and were fixed:
- **M19:** the original mutant removed `aviso: null` from `guardado`. That was redundant with `enviar`, so it was an equivalent mutant. The redundant clear was removed, the mutant was retargeted to `enviar`, and the test "una acción nueva quita el aviso anterior en cuanto empieza" was added.
- **M23:** the id-change test resolved the new id too fast to observe the state. It now holds the id-8 response and asserts that the state is still `cargando`, not `error`, after the id-7 abort.

## Commands (from `frontend/`)

- `npm test`: **32 files, 367 tests passed.**
- `npm run build`: OK. It shows only the pre-existing warning that a chunk is over 500 kB.
- `npm run lint`: **only the 3 pre-existing warnings**, all `only-export-components`, in `AuthContext.tsx:231`, `button.tsx:58` and `badge.tsx:55`.

## Open questions / notes for 9b and review

1. **A PATCH 404 is ambiguous.** The backend gives the same 404 for a trámite that isn't the caller's and for an explotación that isn't the caller's. The hook shows the trámite text, as specified; the context comment documents this. Showing the right text would need a backend change or a follow-up `GET` to disambiguate.
2. **A reload after a successful approve/reject that fails** leaves `carga: error` with `detalle` null, plus the `exito` notice. This is uniform with the 409 case: nothing stale stays on screen to act on. The alternative would be to keep the merged state from the response.
3. **Success notices.** `exito` notices exist for aprobar and rechazar ("Trámite aprobado." / "Trámite rechazado."). Neither mentions OVZ.net. 9b may choose not to render them, since the read-only state already shows the result.
4. **Rechazar confirmation** belongs to 9b (H4). The hook's `rechazar()` sends immediately.
5. **The existing dialog still closes on approve/reject success.** This is kept on purpose (minimum change); 9b implements decision 13.
6. **Several edits in the same event handler** are each validated against the render-time state, but the reducer re-validates against the latest state. In the worst case an edit reported as accepted is silently ignored by the reducer. This is only possible with invalid indices inside the same batch.

---

## Fixes after review

This round answers `a2-task9a-review.md`. It was done test-first:
- the 11 new or rewritten behaviour tests failed first, against the unchanged hook;
- the 2 I1 tests pass on correct code by design, and are proven by killing mutations R1 and R6.

M4 was skipped as instructed. `backend/` and the other restricted paths are untouched, no dependencies were added and no git operations were run.

### Public API changes (additive)

```ts
export interface RevisionTramite {
  // ...everything from the original API, unchanged, plus:
  /** After a 200 on aprobar/rechazar, the follow-up reload failed. The merged detail from the 200
   *  (read-only) stays on screen; this is a NON-blocking text for a "Reintentar" option. It coexists
   *  with `aviso` (which still holds the `exito` notice). null otherwise. */
  recargaFallida: string | null;
  /** Re-fetches the detail WITHOUT removing the one shown. No-op unless `recargaFallida` is set
   *  (or while a reload is already open). Success → fresh detail and `recargaFallida` null;
   *  failure → `recargaFallida` set again. */
  reintentarRecarga: () => void;
}
```

Nothing was renamed or removed.
- `reintentarCarga` is still the blocking retry for `carga.estado === "error"`.
- `reintentarRecarga` is the non-blocking retry after a 200.

**How the two notices coexist (for 9b).**
- `aviso` is the result of the action: here, "Trámite aprobado." / "Trámite rechazado.".
- `recargaFallida` is a separate, secondary line about the refresh, with its own Reintentar.
- Neither replaces the other.
- `recargaFallida` is cleared when a new action starts, on any successful load, on a retry and on an id change.

### Behaviour changes

| Case | Before | Now |
|---|---|---|
| 200 on aprobar/rechazar, then the reload fails | `carga: error`, `detalle` null | the **merged detail is kept** (estado, tipo, explotación, crotales and version from the 200; `mensajeOriginal` and the explotación label kept), `carga: listo`, read-only, `aviso: exito`, and `recargaFallida` set to the error text |
| 409, then the reload fails | `carga: error`, `detalle` null | unchanged: null-on-failure stays **only** here, and in the uncertain case below |
| 404 on aprobar/rechazar | notice only; the trámite stayed actionable | the detail is **reloaded**. A 404 on reload gives `carga: {estado: "no-encontrado", mensaje: "Este trámite ya no existe o no es de tu gestoría."}`, `detalle` null, no actions, no duplicate `aviso`, and `onCambiado`. If the reload fails for another reason: the `aviso` plus `carga: error` |
| 404 on guardar | notice with the trámite text | the detail is **reloaded**. A 404 on reload is handled as above. If the reload succeeds, the explotación was the problem: the detail is refreshed (`detalle-refrescado`), the **edits are kept**, the `aviso` is `{tipo: "no-encontrado", accion: "guardar", mensaje: "La explotación elegida ya no está disponible. Elige otra."}`, and there is **no** `onCambiado` (nothing changed on the server) |
| network/5xx on aprobar/rechazar (M5) | notice only | the outcome is uncertain, so: the `aviso` `error` with the generic text, `onCambiado`, and a strict reload. The modal shows the real state; if the reload fails too, `carga: error` and `detalle` null |
| network/5xx on guardar | notice, edits kept | unchanged: no reload, because it would throw the edits away |
| stale handler from another trámite (M3) | could act on trámite A and write A's results into B's state | `Sesion` now carries `tramiteId`, and `ejecutar` (and `reintentarRecarga`) require `sesion.tramiteId === tramiteId`. Result: no request and no state change |

The "in flight includes the follow-up reload" invariant (I1) is unchanged in the code: every reload is awaited inside `ejecutar` before `finally` clears `enviando`. It now has tests.

### Internal refactor

`recargar` was split into three functions:
- `pedirDetalle(sesion, id)`: session-guarded; it returns `null` when the session is closed or the request was cancelled.
- `recargarEstricto`: used after a 409 and in the uncertain case. It resets the form, or nulls the detail on failure.
- `recargarTrasExito`: used after a 200. It keeps the detail on failure and sets `recargaFallida`.

The new reducer events are `detalle-refrescado` (a fresh detail that keeps the form), `recarga-fallida` and `reintentando-recarga`.

### New tests (`useRevisionTramite.test.tsx`: 46 → 58)

- **I1:**
  - after a 409 with the reload GET held open: `enviando === "aprobar"`, every `puede*` is false and `aprobar()` sends nothing. After the release, the next approve sends v5.
  - after a 200 with the reload held open: `enviando` stays set, every `puede*` is false, and `aprobar()`/`rechazar()` send nothing.
- **Open question 2 / M1:**
  - a failed reload after a 200 keeps the merged detail (estado, version, tipo, crotales; label and message preserved), `exito` plus `recargaFallida`, and `reintentarRecarga` recovers without blanking;
  - a second failure restores `recargaFallida`;
  - the merged state is visible while the reload is still open.
- **Open question 1 / M2:** aprobar 404→404 and rechazar 404→404 go to no-encontrado with no actions; guardar 404→404 goes to no-encontrado; guardar 404→200 keeps the edits and shows the explotación text, with no `onCambiado`.
- **M5:** aprobar and rechazar on a network error call `onCambiado` and reload into the real state; aprobar 500 with a failed reload shows both errors and no stale detail.
- **M3:** a stored `aprobar` handler from trámite 7, called after switching to 8, sends nothing and leaves 8 untouched.
- **R2:** this one is deterministic. For that single call, `obtenerDetalleTramite` is replaced with a test-controlled promise that ignores the `AbortSignal`, simulating a transport that doesn't honour aborts. The id then changes, the promise resolves with trámite 7's data, and the test asserts that trámite 8's state is untouched. Without the `sesion.activa` guard in `pedirDetalle`, trámite 7's data lands in 8's state.
- The old combined "404 en aprobar, rechazar o guardar" test was replaced by the branch tests above, because the behaviour changed as specified. The old "recarga tras aprobar falla" test was rewritten for the new behaviour.

### Mutations (all killed)

| # | Mutation | Killed by |
|---|---|---|
| R1 | `void manejarError` (clears `enviando` before the 409 reload) | I1 409 test |
| R2 | `pedirDetalle` returns the data with no `sesion.activa` check | R2 test |
| R3 | `respuesta-accion` keeps the old estado | 4 (the merged-state tests) |
| R6 | `void recargarTrasExito` (clears `enviando` before the 200 reload) | I1 200 test |
| N1 | session not bound to `tramiteId` (M3) | M3 test |
| N2 | failed reload after a 200 nulls the detail | 2 |
| N3 | a 404 does not reload | 4 |
| N4 | PATCH 404 with a successful reload discards the form | 1 |
| N5 | network/5xx on aprobar/rechazar not treated as uncertain | 3 |
| N6 | network/5xx on guardar also reloads (loses the edits) | 1 |
| N7 | `reintentarRecarga` does not clear `recargaFallida` | 2 |
| N8 | 404→404 without `onCambiado` | 3 |
| N9 | `detalle-refrescado` doesn't update the detail | 1 |

Re-checked and still killed after the refactor, with their targets moved to the new code where needed: M6, M7, M8, M9, M10, M13, M14, M15, M16, M17, M18, M22, M23.

### Commands (from `frontend/`)

- `npm test`: **32 files, 379 tests passed** (was 367).
- `npm run build`: OK. Only the pre-existing warning that a chunk is over 500 kB.
- `npm run lint`: only the 3 pre-existing `only-export-components` warnings.

### Remaining notes

- The case "aprobar/rechazar 404 but the reload succeeds" should not be reachable. It shows the fresh detail plus the trámite 404 text, without guessing another cause.
- A reload after a 200 that itself returns 404 sets `recargaFallida` to the trámite text and keeps the merged read-only detail.
