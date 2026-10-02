# A2 Task 9b — Independent code review (review-modal UI)

Scope of this review: correctness, accessibility semantics, tests and scope. A separate reviewer judges the visuals.

## VERDICT: CHANGES REQUESTED (two Important findings, both small fixes)

There are no Critical findings. "Approve only what was seen" holds:
- every action button is gated only by the hook's `puede*`;
- nothing bypasses the hook's single flight or its dirty rule;
- Aprobar is impossible while the form is dirty;
- nothing mentions OVZ.net;
- no business rule is predicted on the client.

The two Important findings are both in the inline-confirmation state of `TramiteReviewDialog.tsx`.
- **I1:** a "close with unsaved changes" question can stay armed invisibly. It then pops up later and steals focus while the user is typing.
- **I2:** "Sí, rechazar" can silently do nothing.

I reproduced both in a scratch copy.

## Commands (from `frontend/`, run by me)

| Command | Result |
|---|---|
| `npm test` | **33 files, 432 tests, all passing** |
| `npm run build` | OK. Only the pre-existing warning that a chunk is over 500 kB. |
| `npm run lint` | Only the 3 pre-existing `only-export-components` warnings: `button.tsx:58`, `badge.tsx:55`, `AuthContext.tsx:231` |

The numbers match the implementer's report.

## Verified, no finding

### 1. The UI is wired to the hook
- **Enabled states.**
  - Guardar, Aprobar and Rechazar use `disabled={!revision.puede*}` (`TramiteReviewDialog.tsx:550,560,567`).
  - The fields, the combobox, the tipo select and the crotal rows are disabled while `enviando !== null`.
  - "Descartar cambios" is disabled while sending.
- **Nothing bypasses the hook.**
  - "Sí, rechazar" calls `revision.rechazar()` from the current render. The hook re-checks `puedeRechazar` and the session binding.
  - No handler is stored in state, so 9a's M3 concern does not apply.
- **Dirty state.**
  - `avisoCambiosSinGuardar` renders the exact text "Guarda antes de aprobar" in a `<p id>`.
  - Aprobar and Rechazar get `aria-describedby` to it only while the form is dirty.
  - A test asserts `toHaveAccessibleDescription` and exact `textContent`.
- **After success.**
  - The modal stays open and read-only, and `onClose` is not called.
  - Focus goes to the title.
  - Read-only estados render no footer at all (a test loops over the 6 closed estados).
  - `DatosSoloLectura` renders from `detalle`, never from the form (9a N1 note).

### 2. The N1 and N2 hook changes (`useRevisionTramite.ts:497-576`)
**N1**
- `versionEnviada` is `patch.version`, which is the loaded `detalle.version`.
- The edits are kept only when `fresh.version === versionEnviada && esEditable(fresh.estado)`. Otherwise:
  - `onCambiado` is called;
  - `cargado` discards the form;
  - a `conflicto` notice is shown.
- This is correct and closes 9a's N1.

**N2**
- It only swaps the text when the strict reload succeeds **and** the trámite is no longer pending.
- It stays `exito` only when the trámite shows exactly the requested estado. Otherwise it is `error`.
- The still-pending case keeps "Inténtalo de nuevo", which is true.

**No regression.** Both changes still run inside `ejecutar`'s `await` → `finally`, so the send lock still covers the reload. The 9a lock tests (R1/R6) are unchanged and green.

**Mutations of mine that were killed:**
- N2 always `exito`;
- N1 without the `esEditable` check.

### 3. The explotación combobox
**Accessibility**
- It uses the base-ui 1.6 `Combobox` (ARIA 1.2 pattern).
- `<label htmlFor>` points to the input id, and tests query `combobox` by the name "Explotación".
- The input keeps focus and exposes `aria-controls` / `aria-expanded`, with `aria-activedescendant` on highlight.
- `Combobox.Empty` is a polite `role=status`, so "no results" is announced.
- The trigger has a Spanish label.
- Option names are readable: "REGA · nombre, ganadero: X".

**Other checks**
- **H5:** there is no Clear part and no "none" option, and `onValueChange` ignores `null`. A test and the implementer's corrected mutation cover this.
- **Loader:** while loading, a skeleton plus a status message and the saved value. On error, a destructive alert with Reintentar and **no combobox**, so there is never a partial list.
- **Client filter:** display only (`coincideExplotacion`). The value sent is always an `id` from the full list.
- **Layering:** the popup is portalled (`Combobox.Portal`), with `z-50` + `isolate` on the Positioner, appended after the dialog's portal. This is the same recipe as the existing `Select`. Esc closes only the popup (tested).
- **Visual check:** real-browser layering was not verified by me. It is in the visual reviewer's and Task 10's scope.

### 4. Crotal rows
**Labels and names**
- Each input is labelled "Crotal N".
- Each remove button is named "Quitar crotal 1234", or "Quitar crotal N (vacío)" for an empty row.
- The list is `ul aria-label="Crotales"`.
- The resolved crotal has the sr-only prefix "Crotal completo:".

**Focus**
- "Añadir crotal" focuses the new row (tested).
- Removing a row moves focus to the row that took its place, else the previous row, else "Añadir crotal" (tested).

**"Sin guardar"**
- It is decided by value, not by position (tested; the implementer's mutation was killed).
- No crotal is classified on the client: only a trim and an exact match against `crotalIndicado`.

**Every row shows "Sin guardar" when the explotación changes.** I agree with this choice.
- The backend re-resolves every stored crotal against the new explotación (`TramiteCrotalService`), so the old badges and "→ ES…" crotales would describe a resolution that no longer applies.
- Changing back to the saved explotación restores them.
- The choice is untested, though (see m2).

### 5. Inline reject confirmation
- Focus goes to Cancelar.
- Cancelar and Esc both restore the bar with focus on Rechazar.
- Esc goes through the Dialog's `onOpenChange(false, details)` → `details.cancel()` + `cancelarConfirmacion()`. It is neither swallowed nor propagated to a close: `onClose` is not called (tested).
- "Sí, rechazar" sends no body (tested).

### 6. Closing while dirty
I checked all three paths. None of them closes silently.
- **X:** calls `pedirCierre` (tested).
- **Esc:** tested.
- **Outside click:** I probed it with a click on the backdrop. `onClose` was not called and the question appeared. Esc on the question cancels it and keeps the dialog open.

### 7. Notices
- **Error notices:**
  - `mensaje` is rendered verbatim;
  - the per-action titles are correct (mutation "title always guardar" was killed);
  - they use `role=alert` via `Alert`.
- **403:** the fixed text plus an "Ir a Facturación" `Link` (tested).
- **Success:** inside an always-mounted `role=status`, with no `alert` role, so nothing is announced twice.
- **recargaFallida:** a separate `role=alert`. This is intended by decision 10 ("both errors are visible").
- **N2 wording is honest:**
  - "No hubo respuesta a tiempo, pero el trámite consta como aprobado." appears in the status region;
  - no OVZ.net wording appears anywhere (tested).

### 8. Tests
**Counts and deletions**
- Dialog: 47. Hook: 60. Page: 20. `buscarExplotacion`: 8.
- The 7 pre-9b dialog tests are all present by name:
  - etiquetas;
  - sin tipo;
  - error de carga + Reintentar;
  - 404 detalle;
  - 400 aprobar;
  - 403 aprobar;
  - version/empty body.
- 4 (Task 2) + 2 (Task 5) + 1 (9a) = 7, which is consistent.
- Caveat: these test files were never committed, so I could only compare names against the earlier reports, not diff the prior content.
- The adaptations described (MemoryRouter, Reintentar click, reject through the confirmation, "Trámite #2" name, closing after approve) do not weaken any assertion.

**My mutations**

| # | Mutation | Result |
|---|---|---|
| Mf | N2 always `exito` | killed |
| Mg | N1 without `esEditable` | killed |
| Mh | error title always "guardar" | killed |
| Mj | no "Sin guardar" next to the Explotación label | killed |
| Mb | rows ignore `explotacionCambiada` | **survived** (m2) |
| Mc | the ">100" hint removed | **survived** (m1) |
| Md | confirmation visible even while `enviando` | **survived** (see I1) |
| Mi | `motivoError` alert removed | **survived** (m5) |

### 10. Scope
- `git diff --cached` is empty, and `backend/` is unchanged.
- `package.json` and `package-lock.json` date from 09-28, so there are no new dependencies. `public/` also dates from 09-28.
- There are no hex colours in the new files.
- Files newer than the plan edit:
  - `src/features/tramites/*`, as reported;
  - `.impeccable/review/revision/*`, created at 00:07–00:10. These appeared **after** the implementer's report (00:06) and match the visual review / orchestrator, not the implementer.
- The docs, the skills and the `.impeccable/surfaces` contract were not touched in 9b.
- My scratch copy and its `node_modules` junction were deleted; the real `node_modules` is intact.

## Critical

None.

## Important

### I1. A "close with unsaved changes" question can stay armed invisibly, then pop up later and steal focus mid-typing
**Where:** `TramiteReviewDialog.tsx:72-73` (visibility is derived, but the `confirmacion` state is never cleared), with `:101-103` (focus effect) and `:120-128`.

**The cause:** `confirmacionVisible` hides a `"cerrar"` confirmation when the form is no longer dirty or while a request is in flight. But `confirmacion` stays `"cerrar"`. As soon as the form becomes dirty again, the question reappears, and the effect moves focus to "Seguir editando".

**Reproduced in jsdom, path A:**
1. Add crotal "1234".
2. Press Esc. The question appears.
3. Click "Quitar crotal 1234". The form is clean and the question hides.
4. Add a crotal and type "9".
5. The question reappears and `document.activeElement` is "Seguir editando". The rest of the user's keystrokes go to the button.

**Reproduced in jsdom, path B:**
1. Edit a crotal and click Guardar.
2. Press Esc while it shows "Guardando…". Nothing visible happens: `onClose` is not called and the question is hidden because `enviando`.
3. The save succeeds.
4. The next edit makes the question appear and steal focus.

**Why it matters:** the confirmation should only exist because of an explicit request the user can see. Focus theft while typing crotales is a real accessibility and usability bug. It is not data loss: "Seguir editando" is the focused option.

**Fix:**
- Clear the state instead of only hiding it. For example, add `if (confirmacion !== null && confirmacionVisible === null) setConfirmacion(null)` during render (the same pattern as `idPrevio`), or an effect.
- Do not arm `"cerrar"` while `enviando !== null`: in `pedirCierre`, just `detalles.cancel()` and ignore the request, or close.
- Add a test for path A and one for path B. Mutation Md, which currently survives, would then be killed too.

### I2. "Sí, rechazar" can silently do nothing
**Where:** `TramiteReviewDialog.tsx:72-73` and `:135-140`.

**The cause:** while the reject confirmation is shown, the fields stay editable. For `"rechazar"`, `confirmacionVisible` does not check `sucio` / `puedeRechazar`.

**Reproduced:**
1. Click Rechazar. The confirmation appears.
2. Type in "Crotal 1". The confirmation is still shown.
3. Click "Sí, rechazar".
4. The confirmation closes and focus goes to the title, but there is **no POST and no message**. The hook's `puedeRechazar` is false, correctly.

**Why it matters:**
- Safe, but it is a silent no-op on an explicit, irreversible confirmation.
- It contradicts decision 3 ("ningún error silencioso").
- The user may believe the trámite was rejected, since the only cue is the unchanged badge.

**Fix (either one):**
- Hide the reject confirmation whenever `!revision.puedeRechazar`, clearing the state as in I1. The bar then returns with Rechazar disabled and "Guarda antes de aprobar".
- Or disable the data fields while any confirmation is open.

Add a test for the chosen behaviour.

## Minor

### m1. The ">100" hint depends on the total, not on the matches, and is not announced
**Where:** `CampoExplotacion.tsx:96,158-162`.

**What happens:**
- With 151 explotaciones, typing "Finca 149" leaves 1 option, yet the hint still says "Se muestran hasta 100 coincidencias: escribe para acotar". I probed this.
- Matches are **never hidden silently**: the hint is always present whenever more than 100 could exist. But it is inaccurate once filtered.
- It sits outside the listbox and outside any live region, so a screen reader user with more than 100 matches never hears that the list is truncated.
- It is untested (mutation Mc survived).

**Fix:**
- Control `inputValue` and compute the match count with `coincideExplotacion`, or use base-ui's filtered-items hook.
- Show the hint only when matches exceed 100.
- Put it in `Combobox.Status`, which is a polite live region.
- Add a test with more than 100 items.

### m2. "Every row shows Sin guardar when the explotación changes" is untested
**Where:** `ListaCrotales.tsx:278`. Mutation Mb survived.

The behaviour is correct (see Verified §4), but a deliberate, non-obvious rule should be locked in.

**Fix:** a test that changes the explotación and asserts that every row shows "Sin guardar" with no old badge or "→" crotal. Then change it back and assert the badges return.

### m3. After a 409 on Aprobar, focus returns to an enabled Aprobar
**Where:** `TramiteReviewDialog.tsx:109-118`.

**What happens:**
- Probed with the keyboard: press Enter on Aprobar, get a 409, and after the reload `activeElement` is Aprobar.
- The comment says "Nunca salta a Aprobar". That is true for the other buttons, but not for Aprobar itself.
- The backend's version check still guards the next approve, and the fresh data is on screen.
- But for the "resolución cambiada" 409, whose whole point is to make the reviewer look at the new resolutions, a keyboard user is one Enter away from approving before reading.

**Fix:** after any error notice on `aprobar` (or after a 409 on any action), send focus to the title, or to the notice with `tabIndex=-1`, not back to Aprobar. Add a test.

### m4. The N2 `error` branch will be misleading after 3c
**Where:** `useRevisionTramite.ts:565-575`.

**What happens:**
- If an uncertain approve reloads as `EN_PROCESO`, `EJECUTADO_OVZ` or `ERROR_OVZ`, the notice becomes `error`, titled "No se ha aprobado el trámite", although the approve did apply.
- Unreachable today, because nothing leaves `APROBADO`.

**Fix:** treat any post-approval estado as the requested outcome. Or note it for Prompt 3c.

### m5. The `motivoError` alert in the data column is untested
**Where:** `TramiteReviewDialog.tsx:322-327`. Mutation Mi survived.

It is unreachable today (`ERROR_OVZ` needs 3c). A one-line assertion in the read-only loop (an `ERROR_OVZ` fixture with `motivoError`) would cover it.

# Re-review (fixes)

## VERDICT: APPROVED

Every item (I1, I2, m1–m5) is fixed, and each new test fails when its fix is removed. "Approve only what was seen" still holds. No new Critical or Important findings. Two Minor notes are listed at the end.

## Commands (from `frontend/`, run by me)

| Command | Result |
|---|---|
| `npm test` | **33 files, 442 tests, all passing** |
| `npm run build` | OK. Only the pre-existing warning that a chunk is over 500 kB (620.93 kB). |
| `npm run lint` | Only the 3 pre-existing `only-export-components` warnings: `badge.tsx:55`, `AuthContext.tsx:231`, `button.tsx:58` |

The numbers match the implementer's report.

## Mutations (applied in place, one at a time, each restored from a scratch copy)

Afterwards I checked every file in `src/features/tramites/` against a SHA-256 list taken before the first mutation: all byte-identical. No `.bak` files remain.

| # | Mutation | Tests run | Result |
|---|---|---|---|
| R1 | I1 path A: remove the render-time clear of a hidden confirmation | dialog | killed (I1 A, I2) |
| R2 | I1 path B: remove the clear **and** the in-flight guard in `pedirCierre` (the pre-fix code) | dialog | killed (I1 A, I1 B, I2) |
| R3 | Remove only the in-flight guard in `pedirCierre` | dialog | survived (equivalent, see I1 below) |
| R4 | I2: the reject confirmation ignores `puedeRechazar` | dialog | killed (I2) |
| R5 | m1: the hint is decided by the total (`carga.explotaciones.length`), not by the matches | dialog | killed (m1) |
| R6 | m1: the hint is in a plain `div`, not `Combobox.Status` | dialog | killed (m1) |
| R7 | m1: `limit={100}` instead of `100 + 1` | dialog | killed (m1). `useFilteredItems` is capped by `limit`, so the `+1` is load-bearing and it is tested. |
| R8 | m1: no `limit` at all | dialog | killed (m1, the "≤101 options" check) |
| R9 | m3: focus returns to Aprobar after an approve attempt | dialog | killed (focus test) |
| R10 | m4 (hook): `estado-cambiado` → `error` | hook + dialog | killed (2 hook tests + dialog m4) |
| R11 | m4 (UI): the title is chosen by action again | dialog | killed (m4) |
| R12 | m4 (UI): the `estado-cambiado` alert is `destructive` instead of the default variant | dialog | **survived** (Minor n1) |
| R13 | m2: rows ignore `explotacionCambiada` (the original review's Mb) | dialog | killed (m2) |
| R14 | m5: the `motivoError` alert is removed (the original review's Mi) | dialog | killed (m5) |
| R15 | Regression: Aprobar is enabled while dirty (only `enviando` disables it) | dialog | killed (2 tests) |
| R16 | Regression: the hook's `puedeAprobar` ignores `sucio` | hook + dialog | killed (5 tests) |

## Per-item verification

### I1. Fixed
- `TramiteReviewDialog.tsx:83` clears the stored confirmation during render whenever it is not visible. A hidden "cerrar" question can no longer come back.
- `pedirCierre` (`:137`) ignores a close request while a request is in flight and the form is dirty. In that case Esc still calls `detalles.cancel()` first (`:174`), so the dialog never closes during a save.
- The tests reproduce both paths from the original review, with focus assertions. I1 A is killed by R1. I1 B is killed by R2.

**The surviving mutation R3 really is equivalent.** Without the guard, `pedirCierre` sets `"cerrar"` while `enviando !== null`. `confirmacionVisible` is then `null`, so the same render clears it. The focus effect keyed on `confirmacionVisible` never fires, because the value never leaves `null`. There is no observable difference: R3 only adds one extra render. Keeping the guard as defence in depth is fine. The consequence is that I1 B pins only the pair (R2), not the guard alone. That is acceptable.

### I2. Fixed
- The reject confirmation is shown only while `revision.puedeRechazar` is true (`:77`). It is cleared as soon as that becomes false.
- When you edit with it open, the normal bar returns: Rechazar is disabled and described by "Guarda antes de aprobar", and focus stays in the field (tested).
- Undoing the edit does not bring the confirmation back. You must ask again, which is the right choice.
- "Sí, rechazar" is never offered when it would do nothing. The test asserts 0 POSTs. It is killed by R4.

### m1. Fixed
- The hint now reads `Combobox.useFilteredItems()`, so it follows the matches for the typed text, not the total.
- It is shown only when there are more than 100 matches. It lives in `Combobox.Status`, a polite live region.
- The test covers 150 items: the hint is shown inside `role=status` with ≤101 options; typing "Finca 149" leaves 1 option and no hint.
- Killed by R5, R6, R7 and R8.

### m2. Locked in
The test now pins both directions: every row shows "Sin guardar" with no old badge and no "ES…" crotal, and the badges return when you switch back to the saved explotación. Killed by R13.

### m3. Fixed
- After any Aprobar attempt, focus goes to the title (`:128`), never back to Aprobar.
- Guardar and Rechazar keep their previous rule: focus returns to the same button if it is still enabled, otherwise to the title.
- The test uses the keyboard: Enter on Aprobar → a 409 with re-resolved data → focus is on the heading "Trámite #7", and Aprobar is enabled but not focused. Killed by R9.

### m4. Fixed, and the wording is honest
- **Type:** the new `estado-cambiado` type is produced only after an uncertain approve or reject (network error or 5xx) when the strict reload shows a closed estado **other than** the one requested.
- **Text:** "No hubo respuesta a tiempo. El trámite consta ahora como <estado>." It states only what the backend shows.
- **Title:** "El trámite ha cambiado de estado". It is never "No se ha aprobado el trámite" (asserted on the whole dialog).
- **Role:** it is still `role=alert` (the `Alert` component), which is right for a result the user must notice. Its variant is neutral (`default`).
- The exact-match case is still `exito`, and the still-pending case is still `error` with "Inténtalo de nuevo".
- For post-3c estados the text says "consta ahora como ejecutado en OVZ.net". This is the backend's own estado label, reached only when the backend reports that estado, so it does not imply that Aprobar runs anything in OVZ.net. Today the case is unreachable.

### m5. Locked in
An `ERROR_OVZ` fixture with `motivoError` shows a "Motivo del error" alert inside the "Datos del trámite" region. Killed by R14.

## "Approve only what was seen": no regression
- **Gating.** Guardar, Aprobar and Rechazar are still gated only by the hook's `puede*` (`:566,576,583`). R15 and R16 are killed.
- **Dirty state.** `puedeAprobar = editable && !sucio && libre` is unchanged, so Aprobar stays impossible while the form is dirty.
- **Focus.** It never lands on Aprobar after an approve attempt (m3). The only automatic moves onto a button are to the safe first option of a confirmation, or back to Guardar/Rechazar.
- **OVZ.net wording.** The only mention in the 9b UI files is the code comment at `TramiteReviewDialog.tsx:28`.

## Finish fixes: correctness and accessibility only
- **#1, subgrid crotal rows.**
  - The DOM order inside each `li` is unchanged in meaning: input, badge, remove button, resolved line.
  - The tab order is still input → remove button for each row, then "Añadir crotal".
  - The labels "Crotal N" and "Quitar crotal X", the `ul aria-label="Crotales"` and the sr-only "Crotal completo:" are all unchanged.
  - The explicit `col-start-1 row-start-1` on the input, and the resolved line at `row-start-3` / `sm:row-start-2` with `col-span-full`, give no overlapping cells in either layout.
- **#2, top-anchored dialog.** Only the placement classes changed (`md:top-[8dvh]`, `md:max-h-[84dvh]`, no Y translate). Focus management and `initialFocus` are unaffected. The structure test pins the new classes.
- **#4, read-only badge.** Only the `ml-auto` wrapper was removed. The read-only loop is green.
- **#5, dashed "Sin guardar".**
  - `BadgeSinGuardar` is shared by the rows and the field labels.
  - The text is unchanged, so every test that queries "Sin guardar" still works.
  - The dashed border is tested.
- **#6, `ArrowRightIcon`.** It is `aria-hidden` and the sr-only prefix stays, so the accessible text is unchanged ("Crotal completo: ES…").

## New findings

### Critical
None.

### Important
None.

### Minor
- **n1. The neutral variant of the `estado-cambiado` notice is untested** (R12 survived).
  - The m4 test checks the title and the role, but not that the alert is non-destructive.
  - A one-line class or `data-aviso` assertion would lock it in. Optional.
- **n2. The ">100" hint may not be announced when the popup first opens.**
  - `Combobox.Status` mounts together with the popup. When the popup opens with an empty query and more than 100 items, the region is inserted already holding its text.
  - As `AvisosRevision.tsx`'s own comment notes, some screen readers do not read a live region that is inserted with content already in it.
  - Changes made while typing are announced.
  - Low impact, since the user is about to type anyway. I am noting it rather than requesting a change. The real-browser and screen-reader check belongs to Task 10.
