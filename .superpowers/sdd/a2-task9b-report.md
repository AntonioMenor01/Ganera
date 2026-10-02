# A2 Task 9b — Review-modal UI (`TramiteReviewDialog`): implementer report

This task is frontend only. `backend/`, `public/`, `index.html`, the docs, the plan, `.impeccable/`, `.agents/`, the skills, `logo.jpg` and `preview.png` were not touched. No dependencies were added, no git operations were run, and nothing is staged. The Impeccable skill was not invoked; I built from the approved contract, `craft-floor.md` and `operate.md`.

## Files

**New**
- `frontend/src/features/tramites/CampoExplotacion.tsx`: the explotación combobox, its loader states (loading / error with Reintentar) and `TextoExplotacion` for read-only.
- `frontend/src/features/tramites/ListaCrotales.tsx`: `ListaCrotalesEditable` (rows, "Sin guardar", add, remove, focus management) and `CrotalesSoloLectura`.
- `frontend/src/features/tramites/AvisosRevision.tsx`: the notices area.
- `frontend/src/features/tramites/buscarExplotacion.ts`, with `buscarExplotacion.test.ts` (8 tests): `coincideExplotacion` (a client-side display filter) and `etiquetaExplotacion` ("REGA · nombre").

**Rewritten**
- `frontend/src/features/tramites/TramiteReviewDialog.tsx`: the whole UI on top of `useRevisionTramite`.
- `frontend/src/features/tramites/TramiteReviewDialog.test.tsx`: 47 tests. The 7 original tests are kept; only three were adapted, and none was deleted:
  - rendering now goes through `MemoryRouter` plus the new props;
  - the load-error test now also clicks Reintentar;
  - the reject test now goes through the confirmation.

**Modified**
- `frontend/src/features/tramites/useRevisionTramite.ts`: N1 and N2 (see below).
- `frontend/src/features/tramites/useRevisionTramite.test.tsx`: now 60 tests.
  - 4 new: N1 ×2, N2 still pending, N2 in another closed estado.
  - The 2 M5 loop tests were adapted to the N2 wording.
- `frontend/src/features/tramites/TramitesPage.tsx`: passes `explotaciones={cargaExplotaciones}` and `onReintentarExplotaciones` to the modal, so the list isn't loaded twice.
- `frontend/src/features/tramites/TramitesPage.test.tsx`: now 20 tests.
  - 1 new: the modal reuses the page's list, with exactly one `GET /explotaciones`.
  - 2 adapted:
    - "Enter abre…" now asserts the dialog's accessible name "Trámite #2" instead of the old "Revisión de trámite" text. The contract's header is "Trámite #N", and craft-floor bans an eyebrow above it.
    - "N2 última página" now waits for "Trámite aprobado." and clicks Cerrar, because the modal stays open after approving (decision 13).

**New props on `TramiteReviewDialog`:** `explotaciones: CargaTodasLasExplotaciones` and `onReintentarExplotaciones: () => void`. Both are required.

## How each contract block was realised

**THESIS (a collation desk)**
- The body is `grid grid-cols-1 md:grid-cols-[minmax(0,2fr)_minmax(0,3fr)]`.
- Left: the section "Mensaje de WhatsApp", with the message as a `<blockquote>` on Lino (`bg-muted`, `whitespace-pre-wrap wrap-anywhere`).
- Right: the section "Datos del trámite".
- On desktop the message column is `md:sticky md:top-0 md:self-start`, so it stays in view while you scroll through a long crotal list. That keeps the side-by-side comparison intact.
- The data column never re-asks the question a form would: it shows what Ganera understood, in the same order in both modes.

**OWN-WORLD**
- White panel with a 10% ring from `md` up (full-bleed on mobile) over the existing light scrim.
- 32 px fields with Borde Lino strokes, reusing the `Input` and `Select` recipes. The combobox input copies the `Input` classes exactly.
- Exact badge pairs through `BadgeEstadoTramite` / `BadgeResolucionCrotal`. "Sin guardar" is the neutral `outline`.
- Verde Monte appears only on Aprobar (primary), the Facturación link (`CLASE_ENLACE`) and focus rings.
- `tabular-nums` on the title, crotal inputs, resolved crotales, REGA codes (field, options, read-only text) and the combobox input.
- Tokens only. The only shadow is `shadow-md` on the combobox popup; the Select popup already has one.

**STORY**
- The employee reads the message and corrects explotación, tipo and crotales. Each changed field or row shows "Sin guardar". The employee saves; the badges and resolved crotales then come from the PATCH response.
- Aprobar sits beside the rest of the actions.
- A 409 shows the backend's `motivo` verbatim in a persistent notice over the freshly reloaded data.

**FIRST VIEWPORT**
- **Header:** "Trámite #N" (the `DialogTitle`, which is also the dialog's accessible name) and the estado badge.
- **Notices:** between the header and the columns.
- **Columns:** as above.
- **Footer:** fixed, with Rechazar on the left and Guardar + Aprobar on the right. The body (`min-h-0 flex-1 overflow-y-auto`) is the only thing that scrolls.
- **Close button:** an "X" labelled "Cerrar", absolutely positioned top-right but last in the DOM. The tab order is therefore fields → crotales → actions → close, the same pattern as the stock `DialogContent`. The stock close button was disabled because its label is English ("Close").

## Requirement by requirement

### 1. Layout
- **Mobile, below `md`:** `top-0 left-0 h-dvh w-full max-w-none translate-x-0 translate-y-0 rounded-none ring-0`, and the columns stack with the message first.
- **From `md` up:** `md:top-1/2 md:left-1/2 md:-translate-* md:max-w-4xl md:max-h-[calc(100dvh-4rem)] md:rounded-xl md:ring-1`.
- **Footer on phones:** the bottom padding respects `env(safe-area-inset-bottom)`.
- **375 px:** every flex or grid child is `min-w-0`, long text uses `wrap-anywhere`, rows and the action bar `flex-wrap`, and the popup is capped at `max-w-(--available-width)`.
- **Not measured in a real browser here** (jsdom has no layout). This belongs in the Task 10 audit at 375 px.

### 2. Right column

**Explotación: base-ui `Combobox`** (see "Combobox choice").
- Filtering (by REGA, nombre or ganadero) is done by `coincideExplotacion`, which is:
  - case- and accent-insensitive;
  - token-based: every word must match some field.
- Options show "REGA · nombre", with the ganadero on a second muted line. An sr-only ", ganadero:" keeps the accessible name readable.
- At most 100 options are rendered, with a line saying to type to narrow the list when there are more (H3).
- **Loader states:**
  - loading: a skeleton bar, a `role=status` "Cargando explotaciones…", and the saved explotación shown as reference;
  - error: destructive alert with the message and Reintentar (calls `onReintentarExplotaciones`); the combobox is not rendered, so there is never a partial list (decision 20).
- **No clearing (H5):**
  - there is no Clear part and no "ninguna" option;
  - `onValueChange(null)`, which base-ui does emit when the text is cleared (a mutation proved it), is ignored.
- **Placeholders:**
  - when nothing is saved: "Sin asignar · busca por código REGA, nombre o ganadero";
  - when the saved explotación isn't in the loaded list: "REGA · nombre (no está en la lista cargada)".

**Tipo**
- The existing `Select` over `TIPOS_TRAMITE`, with a render-function `SelectValue`, so it shows labels and never enums.
- When nothing is set it shows "Sin determinar todavía".
- No "none" item.

**Crotales**
- `<ul aria-label="Crotales">`. Each row has:
  - an input labelled "Crotal N";
  - the saved badge or "Sin guardar";
  - an icon button "Quitar crotal 1234" (or "Quitar crotal N (vacío)" for an empty row);
  - the resolved full crotal ("→ ES…", sr "Crotal completo:") when it differs from what was typed.
- **Row state is decided by value, not position:** a row counts as saved when its trimmed value equals a saved `crotalIndicado`. So removing row 1 does not turn the rows after it into "edited".
- **When the explotación in the form differs from the saved one, every row shows "Sin guardar",** because saving will re-resolve them all. Showing the old badges would imply a resolution that no longer applies.
- **Focus:**
  - "Añadir crotal" adds a row and focuses its input;
  - removing a row moves focus to the row that takes its place, or to the previous row, or to "Añadir crotal".

**Read-only**
- A `<dl>` with the same order: plain text values, `CrotalesSoloLectura`, and **no footer at all**. The only button is Cerrar.
- It renders from the **saved** `detalle`, never from the form, which addresses the N1 note.

**Other**
- `motivoError` is shown as a destructive "Motivo del error" alert at the top of the data column.
- "Sin guardar" also appears next to the Explotación or Tipo label when that field changed. This is consistent with the rows; it is my addition, not in the contract.

### 3. Notices (`AvisosRevision`)

**Error notices: conflicto (409), validación (400), no encontrado (404), error**
- A destructive `Alert` (`role=alert`), persistent until dismissed or until the next action.
- The title depends on the action:
  - "No se han guardado los cambios"
  - "No se ha aprobado el trámite"
  - "No se ha rechazado el trámite"

  Each is clearer than a generic "No se ha podido completar".
- The body is `aviso.mensaje` verbatim: the backend's `motivo` whenever one exists.
- A "Cerrar aviso" icon button dismisses it.

**Prohibido (403):** the fixed subscription text, plus an "Ir a Facturación" `Link` (`CLASE_ENLACE`).

**Éxito:**
- "Trámite aprobado." / "Trámite rechazado." on the exact success pair with a check icon, inside a `role=status` that is **always mounted**, so screen readers announce the insertion (the earlier Task 8 m3 note).
- No mention of OVZ.net (a test asserts there is no "OVZ" text after approving).

**recargaFallida:** a secondary muted `role=alert` line with the message and a "Reintentar" link-button.

**N2: decided in the hook**, because it is state logic, not presentation. See below.

### 4. Actions

**Buttons**
- Guardar: `outline`, enabled only when `puedeGuardar`.
- Aprobar: primary, enabled only when `puedeAprobar`.
- Rechazar: `outline` as the first, uncommitted step. The irreversible "Sí, rechazar" is `destructive`, the 10% red tint that DESIGN.md gives destructive buttons.

**In flight**
- The pressed button shows "Guardando…", "Aprobando…" or "Rechazando…", and all three buttons are disabled (the hook's `puede*`).
- When the request finishes, focus returns to that button if it is still enabled, otherwise to the title.
- Focus never jumps to Aprobar, because a second Enter would approve.

**Unsaved changes**
- A line above the buttons shows exactly "Guarda antes de aprobar" (a `<p>` with that exact text content), plus a "Descartar cambios" ghost button.
- Aprobar and Rechazar get `aria-describedby` pointing to that line only while the form is dirty.

**Inline reject confirmation**
- The action bar becomes a `role=group` named "¿Rechazar el trámite #N? No se puede deshacer.", with [Cancelar] [Sí, rechazar].
- Focus moves to Cancelar. Cancelar or Esc restores the bar, with focus back on Rechazar.
- "Sí, rechazar" focuses the title and sends the reject with no body.

**After a successful approve or reject**
- The modal stays open, read-only, with the success notice.
- `onCambiado` refreshes the queue (through the hook) and focus goes to the title.

### 5. N1 (hook, test-first)
- `ejecutar` now takes the version the PATCH was sent with, and passes it through `manejarError` → `trasNoEncontrado`.
- **Edits are kept** after a PATCH 404 whose reload succeeds only when `fresh.version === versionEnviada` **and** the trámite is still `PENDIENTE_REVISION`. This is the existing "the explotación was the problem" path.
- **Otherwise it behaves like a 409:**
  - `onCambiado`;
  - `cargado` (the form is discarded);
  - `aviso {tipo: "conflicto", mensaje: "El trámite ha cambiado mientras lo editabas. Se han descartado tus cambios; revisa los datos actuales."}`.

### N2 (hook, test-first)
- After a network error or 5xx on aprobar/rechazar, the strict reload now returns its result. If the reloaded trámite is no longer pending, the notice is replaced with `No hubo respuesta a tiempo, pero el trámite consta como <estado en minúscula inicial>.`:
  - `tipo: "exito"` when the estado is exactly what was requested (APROBADO for aprobar, RECHAZADO for rechazar);
  - `tipo: "error"` when it is another closed estado (for example, approving but the trámite now shows RECHAZADO).
- If it is still pending, the original "Inténtalo de nuevo" text stays, because that is true.

### 6. Closing while dirty
- Esc, clicking outside or Cerrar while dirty turns the footer into "Tienes cambios sin guardar." with [Seguir editando] [Descartar y cerrar]. Focus goes to "Seguir editando", and Esc cancels the question.
- Implemented with base-ui's `onOpenChange(false, details)`: `details.cancel()` plus the inline question.
- **Why not discard silently:** a single stray Esc or outside click would throw away a careful correction (a crotal list, a chosen explotación) with no trace. That breaks "no silent errors" in spirit.
- **Why not auto-save:** it would change the trámite without an explicit action, and it could trigger a 409 or 400 the user never sees.
- **Why inline:** a second modal is exactly what decision 31 rejects for Rechazar. Reusing the same inline pattern keeps a single vocabulary.

### 7. Keyboard and accessibility
- **On open:** `initialFocus` goes to the title (`tabIndex=-1`).
- **Esc, in priority order:**
  1. an open combobox or select popup closes itself (base-ui floating tree; tested: the dialog stays open and `onClose` isn't called);
  2. an open confirmation is cancelled;
  3. with unsaved changes, it asks;
  4. otherwise it closes.
- **Tab order:** fields → crotales → actions → Cerrar. The message is not focusable.
- **Focus rings:** every control uses the system green ring: `Button`, `Input`, `SelectTrigger`, the combobox input and its trigger (hand-copied `focus-visible:ring-3 ring-ring/50`), and the Facturación link (`CLASE_ENLACE`).
- **Hit targets:** icon buttons are `icon-sm` (28 px); the smallest target is the 24 px "Reintentar" link-button.

## Combobox choice
`@base-ui/react` 1.6.0 (already a dependency) exports `Combobox`, and its docs describe it as "a filterable Select" that follows the ARIA 1.2 combobox pattern: an input with `role=combobox` and a popup `listbox`. The same docs point to Autocomplete only for free-text search. Our case is a closed set, so Combobox is correct.

Benefits:
- It shares positioning, portal, the floating tree (nested Esc) and focus handling with the `Select` already in use.
- No hand-rolled ARIA.
- No new dependency.

Checked in jsdom:
- opening shows the full list even with a selected value;
- typing filters through our `filter`;
- choosing updates the value;
- Esc closes only the popup;
- clearing the text emits `null`, which the H5 guard ignores.

## TDD evidence
- **Hook (N1/N2):** the 4 new tests and the 2 adapted M5 tests were written first and run against the unchanged hook: **5 failed / 55 passed** (N1 ×2, the M5 loop ×2, N2-other-estado). The N2 still-pending test passed on the old code by design. After the change: 60/60.
- **`buscarExplotacion`:** the test file was written first and failed on the missing module. After the change: 8/8.
- **Dialog:**
  - I first ran a small spike to learn how base-ui Combobox behaves in jsdom.
  - Then I wrote the full suite. The first run gave 43/46; the 3 failures were test-side:
    - `role=group` is ambiguous, because base-ui's InputGroup is also a group, so the queries now use the group's name;
    - an option's accessible name lost a trailing space inside the sr-only span, so the separator text was moved out of it. This was a real accessibility fix.
  - Final: 47/47.

## Mutations
All were run on the real files with automatic restore, against `src/features/tramites`.

| Mutation | Result |
|---|---|
| N1: skip the version/estado check | killed (3) |
| N2: don't swap the notice | killed (4) |
| Aprobar without `aria-describedby` | killed (1) |
| Rechazar without confirmation | killed (4) |
| Esc during confirmation closes the modal | killed (1) |
| Close while dirty without asking | killed (1) |
| An edited row keeps the old badge (match by position) | killed (3) |
| Filter always true | killed (2) |
| Actions also shown in read-only | killed (8) |
| No initial focus on the title | killed (1) |
| No progress text | killed (1) |
| Success notice outside `role=status` | killed (2) |
| 403 without the Facturación link | killed (1) |
| "Añadir crotal" doesn't focus the new row | killed (1) |
| Columns without `grid-cols-1`/`md:` structure | killed (1) |
| Explotación `onValueChange(null)` reaches `cambiarExplotacion` | first variant (null → saved id 3) was equivalent and survived; the corrected variant (null → 5) was **killed (2)** |

## Commands (from `frontend/`)

| Command | Result |
|---|---|
| `npm test` | **33 files, 432 tests, all passing** |
| `npm run build` | OK. Only the pre-existing chunk > 500 kB warning. |
| `npm run lint` | Exactly the 3 pre-existing `only-export-components` warnings. A new `exhaustive-deps` warning from an intermediate version was fixed. |

Per-file test counts in `features/tramites`:
- `TramiteReviewDialog`: 47
- `useRevisionTramite`: 60
- `TramitesPage`: 20
- `buscarExplotacion`: 8
- The rest are unchanged.

## Deviations and decisions to review
1. **No "Revisión de trámite" text.** The title is "Trámite #N"; craft-floor bans an eyebrow. Two tests were adapted to match.
2. **Per-action error titles** instead of a single "No se ha podido completar".
3. **"Sin guardar" next to the Explotación/Tipo labels** when changed. This is an addition.
4. **When the explotación in the form changed, every crotal row shows "Sin guardar"**, because the saved resolutions belong to the old explotación.
5. **Read-only has no footer.** Closing is the top-right X (plus Esc or clicking outside).
6. **The title turns out 600 weight**, not the 500 in `DialogTitle`'s default. It is the only headline in the modal, and DESIGN's Headline is 600. It stays at `text-base`.
7. **N2 wording is a single template** (`… pero el trámite consta como X.`), with `exito` or `error` depending on whether X is the requested outcome.
8. **Not verified in a real browser:** 375 px overflow, sticky message column, safe-area padding and popup layering over the dialog. These are for the Task 10 audit.
9. **Still open (for Task 10):**
   - `CLASE_ENLACE` is imported from `features/ganaderos` (the move to `shared/` is already pending);
   - the combobox input/trigger duplicate the `Input` focus classes by hand;
   - there is no shared `Combobox` component in `components/ui/`. If a second combobox appears, extract one.

## Notes for DESIGN.md (Task 11)
- **Dialog (trámite review):** a two-column "collation" modal.
  - Wide (`max-w-4xl`) from `md`, full-screen below it.
  - A fixed header (title + state badge) and a fixed footer on a 50% Lino strip.
  - Only the body scrolls. The message column is a Lino quoted block, sticky on desktop.
  - The close X comes last in the DOM.
- **Combobox:** base-ui `Combobox`.
  - The input matches `Input` (32 px, Borde Lino, green focus ring), with a chevron trigger inside.
  - The popup is the Select popup: white, 10% ring, `shadow-md`.
  - Options show "REGA · nombre" with the ganadero as a muted second line, capped at 100 with a "type to narrow" line.
  - Filtering is case- and accent-insensitive; every word must match.
- **"Sin guardar"** is the neutral `outline` badge. It marks an edited field or row until the backend answers, so badges never predict.
- **Inline confirmation:** the action bar swaps for a named `role=group`. The question comes first, then [safe option, focused] [destructive-tint option]. Esc cancels. Used for Rechazar and for closing with unsaved changes. Never a second modal.
- **Action notices:**
  - errors are a destructive Alert with a per-action title and the backend `motivo` verbatim, persistent and dismissible;
  - success is the exact `success` pair with a check icon, inside an always-mounted `role=status`;
  - a follow-up reload failure is a secondary muted line with a "Reintentar" link.
- **Destructive button usage:** the first step of an irreversible action is `outline`; only the confirming button is `destructive`.
- **Sentence-case field labels** (14 px/500, Gris Oliva), matching the documenter's "Subsection label" (#16).

## Fixes after review

This round answers two reviews:
- the finish review, `.impeccable/review/revision/finish-review.md` (disposition **fix**);
- the code review, `.superpowers/sdd/a2-task9b-review.md` (changes requested).

The work was test-first:
- I wrote 10 new or adapted tests first. They **failed against the unchanged code** (10 red: the focus test, I1 A and B, I2, m1, m4 ×3, finish #2 and finish #5).
- The m2 and m5 tests lock in behaviour that already existed, so they passed on the old code. Their mutations prove them (see below).
- Restrictions were unchanged: no backend changes, no dependencies, no git.
- I did not do the optional idea of highlighting the extracted digits in the message.

### Item → change → test

| Item | Change | Test (in `TramiteReviewDialog.test.tsx` unless noted) |
|---|---|---|
| **Safety: focus after Aprobar** (finish #3, code m3) | `TramiteReviewDialog.tsx`, focus effect: after any Aprobar attempt, focus goes to the title and never back to Aprobar. Guardar and Rechazar keep "the same button if it's still enabled, else the title". The comment is rewritten to explain why. No existing test pinned the old behaviour. | "foco: tras un 409 al aprobar (resolución cambiada), el foco NO vuelve a Aprobar sino al título" (keyboard Enter on Aprobar → 409 → re-resolved data; `activeElement` is the title, not Aprobar) |
| **I1** (a hidden "cerrar" confirmation comes back and steals focus) | `confirmacionVisible` is derived as before. In addition, while rendering, the stored `confirmacion` is **cleared** when it isn't visible (the same pattern as `idPrevio`). `pedirCierre` does nothing while a request is in flight and the form is dirty. | "I1 (A)": edit, Esc, undo, edit again → no question, focus stays in the field. "I1 (B)": Esc while "Guardando…", save completes, edit → no question, focus stays, `onClose` not called. |
| **I2** ("Sí, rechazar" could do nothing) | The reject confirmation is visible only while `puedeRechazar`, and is cleared as soon as that stops being true. **Why this and not disabling the fields:** it adds no second "disabled" mode, and the bar that returns already explains the situation (Rechazar disabled + "Guarda antes de aprobar" via `aria-describedby`). Undoing the edit does **not** bring the confirmation back; you must ask again. | "I2": open the confirmation, type in a crotal → "Sí, rechazar" is gone, Rechazar is disabled and described, focus stays in the field. Undo → the confirmation does not return. 0 POSTs. |
| **m1** (">100" hint) | `CampoExplotacion.tsx`: the Root uses `limit={LIMITE + 1}`. A new `AvisoMasCoincidencias` reads `Combobox.useFilteredItems()` (the matches for what was typed, including base-ui's "don't filter by the selected label" rule). It shows "Hay más de 100 coincidencias: escribe para acotar." only when there are more than 100 matches. It lives in `Combobox.Status`, a polite live region that stays mounted with the popup. Up to 101 options are shown, which is consistent with "más de 100". | "m1": 150 explotaciones → the hint is inside a `role=status` and there are ≤101 options. Typing "Finca 149" → 1 option and no hint. |
| **m2** | No code change; the behaviour was already correct. | "m2": changing the explotación → every row shows "Sin guardar", with no old badge and no resolved crotal. Going back to the saved one → the badges and the "ES…" crotal return. |
| **m4** | `useRevisionTramite.ts`: new notice type `"estado-cambiado"`. After an uncertain (network error or 5xx) approve or reject:<br>• reload shows exactly the requested estado → `exito`, "No hubo respuesta a tiempo, pero el trámite consta como aprobado.";<br>• any other closed estado → `estado-cambiado`, "No hubo respuesta a tiempo. El trámite consta ahora como <estado>.".<br>`AvisosRevision.tsx` shows it as a **neutral** `Alert` (not destructive, still `role=alert`) titled "El trámite ha cambiado de estado". It is never titled "No se ha aprobado". | Hook: "N2/m4" for RECHAZADO and EJECUTADO_OVZ (replaces the earlier `error` test). Dialog: "m4" with EN_PROCESO → neutral title, no "No se ha aprobado el trámite". |
| **m5** | No code change. | "m5": `ERROR_OVZ` with `motivoError` → a "Motivo del error" alert inside "Datos del trámite". |
| **Finish #1** (crotal-row geometry) | `ListaCrotales.tsx`: one subgrid, using the reviewer's exact classes:<br>• `ul`: `grid grid-cols-[minmax(0,1fr)_auto] gap-x-2 gap-y-2 sm:grid-cols-[minmax(0,1fr)_auto_auto]`;<br>• `li`: `col-span-full grid grid-cols-subgrid items-center gap-y-1`;<br>• Input: `col-start-1 row-start-1 min-w-0 tabular-nums`;<br>• badge: `col-start-1 row-start-2 justify-self-start sm:col-start-2 sm:row-start-1`;<br>• remove button: `col-start-2 row-start-1 sm:col-start-3`;<br>• resolved-crotal line: `col-span-full row-start-3 pl-2.5 text-sm sm:row-start-2`.<br>I added explicit `col-start-1 row-start-1` on the Input so auto-placement can't move it. I dropped the reviewer's `col-start-1` on the resolved line, because it conflicts with `col-span-full`. | Covered by the existing row tests (badge, "Sin guardar", focus, remove), all unchanged and green. Layout itself can't be checked in jsdom; it goes to the Task 10 visual audit. |
| **Finish #2** (layout shift) | `md:top-1/2` → `md:top-[8dvh]`; `md:-translate-y-1/2` removed; `md:max-h-[calc(100dvh-4rem)]` → `md:max-h-[84dvh]`. | The "móvil" structure test now asserts `md:top-[8dvh]` and `md:max-h-[84dvh]`, and the absence of `md:top-1/2` and `md:-translate-y-1/2`. |
| **Finish #4** | `CrotalesSoloLectura`: the `ml-auto` wrapper is removed, so the badge sits beside its crotal. | The read-only loop is still green. This is visual only. |
| **Finish #5** | `BadgeSinGuardar` is now exported from `ListaCrotales.tsx` with `className="border-dashed border-foreground/25"`. It is used for both the rows and the Explotación/Tipo labels (the dialog no longer imports `Badge`). | "finish #5": "Sin guardar" has `border-dashed`; the saved "No está en el inventario" doesn't. |
| **Finish #6** | `→` is replaced by `<ArrowRightIcon aria-hidden className="mr-1 inline size-3.5 align-[-2px]" />`. | Existing tests on the resolved crotal still pass. |

### Mutations (all applied in place, then restored)

| Mutation | Result |
|---|---|
| Focus returns to Aprobar | killed (focus test) |
| Hidden confirmation not cleared | killed (I1 A, I2) |
| Pre-fix code: neither cleared nor guarded in flight | killed (I1 A, I1 B, I2) |
| In-flight guard removed, clearing kept | **survived: equivalent.** Clearing at render time already drops an armed "cerrar" in the same render. The guard stays because the coordinator asked for it explicitly, as defence in depth. |
| Reject confirmation without `puedeRechazar` | killed (I2) |
| ">100" hint always shown | killed (m1) |
| ">100" hint outside `Combobox.Status` | killed (m1) |
| Rows ignore `explotacionCambiada` (the review's Mb) | killed (m2) |
| `estado-cambiado` → `error` | killed (3) |
| Title by action again | killed (m4) |
| `motivoError` alert removed (the review's Mi) | killed (m5) |
| Dashed border removed | killed |
| Dialog centred again | killed |

The review's Md mutation ("confirmation visible while `enviando`") now corresponds to the I1 fixes and is covered by I1 (B).

### Commands (from `frontend/`)

| Command | Result |
|---|---|
| `npm test` | **33 files, 442 tests, all passing** (the dialog file is now 56, the hook 61) |
| `npm run build` | OK. Only the pre-existing chunk > 500 kB warning. |
| `npm run lint` | Exactly the 3 pre-existing `only-export-components` warnings. |

Scope checks:
- `backend/` is unchanged.
- There are no hex values in the new code.
- No `.bak` files were left behind.
