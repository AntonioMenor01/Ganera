# A2 Task 8 — Animales (`AnimalesDeExplotacion`): implementer report

Frontend only. `backend/`, `public/`, `index.html`, DESIGN.md, CLAUDE.md, PRODUCT.md, the plan,
`.impeccable/`, the skills and `TramiteReviewDialog` were not touched. No dependencies were added, no
git operations were run, and nothing is staged.

## Files changed

**New**
- `frontend/src/features/explotaciones/AnimalesDeExplotacion.tsx`: the component.
- `frontend/src/features/explotaciones/AnimalesDeExplotacion.test.tsx`: 13 tests.
- `frontend/src/features/explotaciones/ExplotacionesPage.animales.test.tsx`: 4 tests for the row
  disclosure (H1-A).

**Modified**
- `frontend/src/features/explotaciones/types.ts`: new `Animal` type (`{id, crotal, crotalUltimosDigitos}`).
- `frontend/src/features/explotaciones/api.ts`: new
  `listarAnimalesDeExplotacion(explotacionId, page, size, signal?)`.
- `frontend/src/shared/api/errores.ts`: new context `"animales-explotacion"`. Its generic text is
  "No se han podido cargar los animales. Inténtalo de nuevo." and its 404 text is "Esta explotación
  no existe o no es de tu gestoría."
- `frontend/src/shared/api/errores.test.ts`: covers the new context, and adds it to the exhaustive
  non-empty-text loop.
- `frontend/src/features/explotaciones/ExplotacionesPage.tsx`: a "Ver animales" disclosure per row
  with an inline expansion row.
- `frontend/src/features/ganaderos/GanaderoDetallePage.tsx`: uses the real component behind the
  disclosure. The panel div got `pt-1 pb-2`.
- `frontend/src/features/ganaderos/GanaderoDetallePage.animales.test.tsx`: the m1 test, adapted (see
  below).
- `frontend/src/features/ganaderos/GanaderoDetallePage.test.tsx`: the "animales plegados" test gets
  an animals handler. Opening the panel now makes a real request, and MSW fails on unhandled
  requests. The assertions are unchanged.

**Deleted**
- `frontend/src/features/ganaderos/AnimalesDeExplotacionSlot.tsx`: it was a placeholder seam and no
  longer has a purpose.

## Design choices and why

- **Sort: explicit `sort=crotal,asc&sort=id,asc`.**
  - `crotal` is UNIQUE and is already the backend default, so sending nothing would work today.
  - Sending it writes the screen's order into the frontend, so a future change of `@PageableDefault`
    can't silently reorder the panel.
  - `id` is a free tie-breaker, and both fields are on the whitelist.
  - It is serialized with `URLSearchParams`, the same way as `listarGanaderos`, because axios would
    send `sort[]`.
- **Layout: an ordered grid list, not a table.**
  - It is an `<ol aria-label="Crotales">` with `grid-cols-[repeat(auto-fill,minmax(8rem,1fr))]`,
    14px tabular numerals.
  - It fills row by row, so DOM order = reading order = screen-reader order = crotal order.
  - With one data field per animal, a one-column table would be about 20 rows × 36px tall. The grid
    shows a page of 20 in about 3 lines on desktop (6–7 columns) and 10 lines at 375px (2 columns:
    8rem fits a 14-character Spanish crotal, and two tracks plus the gap fit in the roughly 295px
    panel of the Ganadero section). `break-all` covers crotales up to 20 characters.
  - The panel has no border, ring or card. It sits on the existing Lino strip, the footer of the
    Ganadero section or the expansion row on Explotaciones, so there are no nested cards.
- **`crotalUltimosDigitos`: no extra column.** It is the tail of the same string.
  - The crotal is shown whole. Its prefix is `text-muted-foreground` and its last digits are
    `text-foreground font-medium`, so someone matching the digits a Contacto typed on WhatsApp finds
    them at a glance.
  - A separate column would duplicate data and cost width at 375px.
  - If the tail isn't actually the end of the crotal (bad data), the crotal renders whole with no
    highlight.
  - Contrast: Gris Oliva `#6b6b60` on the Lino strip (about `#f7f6f2`) is about 5:1, which passes AA.
- **States.** No failure is silent.
  - **Loading:** skeleton bars (a count bar plus 6 grid cells) in a `role="status"` with the sr-only
    text "Cargando animales…". The bars are `bg-foreground/10` rather than `bg-muted`, because
    `bg-muted` is nearly invisible on the `bg-muted/50` strip. It is still a token.
  - **Page change:** the current page stays visible at `opacity-60`, with an sr-only status and
    `aria-busy`, so the panel height doesn't jump.
  - **Error:** a destructive `Alert` titled "No se han podido cargar los animales", with
    `mensajeDeError(e, "animales-explotacion")` and "Reintentar", which re-requests the same page.
    It never leaves the old page on screen as if it were the answer.
  - **404:** the plain muted text "Esta explotación no existe o no es de tu gestoría.", with no
    Reintentar because retrying can't fix it, and no pager. The text comes from the same context,
    so it lives in one place.
  - **Empty** (`totalElements === 0`): "Esta explotación no tiene animales en el inventario. Se
    cargan al importar el Excel."
- **Behaviour.**
  - The component fetches in a `useEffect` keyed on `[explotacionId, numeroPagina, intento]`, and
    each run has its own `AbortController`.
  - The cleanup aborts the request, so unmounting (closing the disclosure) or changing page cancels
    the one in flight.
  - The `then` and `catch` handlers return early if their signal was aborted. `catch` also ignores
    `esCancelacion`.
  - `totalPaginas` is kept separately (the last known total), so a failed page still shows
    "Página 2 de 3" and lets you leave it. This is the same pattern as the listings.
  - The pager (`nav aria-label="Páginas de animales"`, outline `sm` buttons) is not disabled while
    loading. Rapid clicks are safe because stale responses are dropped.
  - The contract, stated in the JSDoc: parents mount it only while open, one instance per
    explotación (keyed).
- **Ganadero detail.** The lazy-mount contract is unchanged:
  `{animalesAbiertos && <AnimalesDeExplotacion …/>}` inside the always-present `hidden` panel div
  referenced by `aria-controls`.
- **Explotaciones page (H1-A).**
  - A 4th column holds the header `<span className="sr-only">Animales</span>` and a ghost `sm`
    button per row with `aria-expanded`, a rotating chevron (`motion-reduce` respected), and
    `aria-label="Ver animales de {codigoRega}"`.
    - The label starts with the visible text, so label-in-name holds.
    - I first used a sr-only " de …" span, but the name computation trims the leading space
      ("Ver animalesde ES002"). That is why it is now an explicit `aria-label`.
  - `aria-controls` is set only while open. Closed, the panel isn't mounted (so nothing is fetched)
    and the reference would dangle.
  - The expansion row comes right after its row. It is `bg-muted/50 hover:bg-muted/50` with one
    `TableCell colSpan={4}` (`whitespace-normal px-4 pt-1 pb-4`) and wraps the panel in the
    `aria-controls` target.
  - The open data row gets `border-b-0` and already turns `bg-muted/50` through `TableRow`'s
    existing `has-aria-expanded:` style, so the row and its panel read as one strip, the same Lino
    vocabulary as the Ganadero footer.
  - **Several panels can be open at once** (`Set<number>`).
    - This matches the Ganadero detail, where each section toggles independently.
    - It lets someone compare two inventories.
    - Each open panel costs only its own lazy request.
    - Any reload of the list (page change, retry, import) closes all panels, because the rows or
      their inventory may have changed.
  - **At 375px:** the Nombre and Ganadero cells now wrap (`whitespace-normal break-words`), and the
    button collapses to its chevron (`max-sm:hidden` on the text, accessible name unchanged), so the
    4 columns have a chance to fit. Anything wider still scrolls inside the table's own
    `overflow-x-auto` container.
  - The import section, pager and ganadero link are untouched. Page changes go through the helper
    `irAPagina`, which also closes the panels.

## TDD evidence

- **RED.** I wrote the three test files first and ran them before any implementation:
  - `AnimalesDeExplotacion.test.tsx` failed to resolve `./AnimalesDeExplotacion`;
  - the 4 Explotaciones tests failed on the missing "Ver animales de ESxxx" button;
  - the adapted m1 test failed on the missing "Crotales" list.

  The run showed 5 failed tests plus 1 file that failed to import.
- **Fixes on the way to GREEN.** Two tests were wrong or unworkable, and I fixed them:
  - One expected prefix was wrong: I wrote "ES01000", but it is "ES010000".
  - MSW's `request.signal` doesn't reflect an XHR abort in jsdom (a known issue, already noted in
    `useTodasLasExplotaciones.test.tsx`). Following that file's pattern, the real
    `listarAnimalesDeExplotacion` is wrapped with `vi.fn(real)` to observe the `AbortSignal` of each
    call. The HTTP request and the cancellation mapping stay real.
- **The m1 test.** It no longer mocks the component, because there is no seam left. Its intent is
  kept and made stronger with real requests:
  - no `/explotaciones/*/animales` request before the click;
  - after opening section 2, exactly `["/ganaderos/7", "/explotaciones/2/animales"]`, and the list
    renders in section 2 only;
  - closing unmounts it;
  - reopening fires a new request.

## Mutations

Each mutation was applied to a copy of the file and the file was restored afterwards (checked with
a diff).

| # | Mutation | Result |
|---|---|---|
| M1 | no `abort()` in the cleanup | 2 fail: stale and unmount |
| M2 | drop the `signal.aborted` guard in `then` | **survives**; the abort already rejects the promise, so the guard only covers a response that resolved in the same tick as the abort. It is defensive, and not reliably testable in jsdom. |
| M3 | drop the cancellation guard in `catch` | at first it **survived** (the error flash was overwritten by the newer page). I strengthened the stale test: it holds the newer page in flight and asserts there is no alert and the current page stays. It now **fails 1**. |
| M4 | 404 treated as a generic alert | 1 fails |
| M5 | stale page kept on error | 1 fails |
| M6 | pager uses the current page's total, not the last known one | 1 fails |
| M7 | no `sort` params | 1 fails |
| M8 | no skeleton | 1 fails |
| M9 | Explotaciones panels always mounted | 4 fail |
| M10 | page change keeps panels open | 1 fails |
| M11 | no `aria-controls` | 1 fails |
| M12 | `colSpan={3}` | 1 fails |
| M13 | only one panel open, and it never closes | 2 fail |
| M14 | Ganadero panel mounted eagerly | m1 fails |

## Commands (from `frontend/`)

- Baseline before the task: `npm test` gave **26 files, 270 tests**, all passing.
- `npm test` after: **28 files, 288 tests, all passing.** The 18 new tests are 13 for the
  component, 4 for the Explotaciones page and 1 for `errores`. m1 was rewritten, not added.
- `npm run build`: OK, with only the existing chunk-size notice.
- `npm run lint`: only the 3 pre-existing warnings (badge, button, AuthContext).
- There was no real-browser check at 375px or 1440px, because there is no browser tool in this
  session. The mobile reasoning above is from the CSS. The Task 10 audit should capture the open
  panel on both pages at 375px and 1440px.

## Notes for DESIGN.md

- **Disclosure panel ("Ver animales").**
  - It is an inline expansion on the Lino strip (`bg-muted/50`): the footer of the Ganadero section,
    or a full-width expansion row under the table row on Explotaciones. The open row loses its
    bottom rule, so the row and its panel read as one strip.
  - It is chrome-less inside: no border, ring or card.
  - The trigger is a ghost `sm` button with a chevron that rotates 180° in 200ms, respecting
    `motion-reduce`.
- **Crotal grid.**
  - An ordered list in an auto-fill grid (`minmax(8rem,1fr)`), 14px tabular, with the muted count
    ("N animales") above it and a compact pager below.
  - The last digits are in ink and weight 500, and the prefix is in Gris Oliva. This is the one
    place where a value is visually split, and it mirrors how Contactos quote crotales on WhatsApp.
- **Skeleton on the Lino strip.** The bars use `bg-foreground/10`, because `bg-muted` bars
  disappear on a `bg-muted/50` surface. On white, the existing `bg-muted` bars stay.
- **Tables at mobile width.** An action column may collapse to its icon below `sm`, with the full
  accessible name kept through `aria-label`, and free-text cells wrap.

---

## Fixes after review (`a2-task8-review.md`)

m3 and the nit were left for Task 10, as instructed. The scope rules are unchanged: nothing in
`backend/` or the other protected files was touched, and there were no git operations.

### Files

**New**
- `frontend/src/features/explotaciones/useDesdeSm.ts`: the breakpoint hook.
- `frontend/src/features/explotaciones/ExplotacionesPage.movil.test.tsx`: 6 tests (4 for I1, 2 for
  m4).

**Modified**
- `frontend/src/features/explotaciones/ExplotacionesPage.tsx`: I1, m1, and a simpler
  `onImportado`.
- `frontend/src/features/explotaciones/AnimalesDeExplotacion.test.tsx`: +1 test for m2.

### I1 — no sideways scroll at 375/390px

**The choice: structure decided in JS, style in CSS.**
- `useDesdeSm()` is a `useSyncExternalStore` over `matchMedia("(min-width: 40rem)")`, the exact
  query of Tailwind v4's `sm`.
- **Below `sm`:**
  - the Nombre `<th>`/`<td>` are **not rendered**;
  - the first cell holds the REGA on one line and the name on a second
    `block whitespace-normal break-words text-muted-foreground` line;
  - every full-width row uses `colSpan = 3`.
- **From `sm` up:** the table is unchanged (4 columns, `colSpan = 4`).
- **Why not only a CSS class:**
  - Hiding the Nombre column with `max-sm:hidden` and adding a `sm:hidden` second line would put
    the name in the DOM twice. That is exactly what the coordinator asked to avoid.
  - It would also leave `colSpan=4` over 3 laid-out columns. `colSpan` can't be set from CSS, and a
    `display:none` cell leaves the table grid, so the span would no longer match the real columns.
  - `visibility: collapse` on a `<col>` would keep the grid intact, but browser support and how it
    reaches the accessibility tree are uneven.
  - With the hook, the name exists exactly once at any width, and the span always equals the
    columns that exist.
- **Changing width while a panel is open:** crossing the breakpoint re-renders the table. The open
  panel keeps its state and does not refetch, because it stays keyed by the same row.
- **No `matchMedia`** (very old browsers, jsdom): the hook falls back to desktop, so the existing
  tests are unchanged.

**Button cell below `sm`:** `max-sm:px-1` on the cell and `max-sm:px-1.5` on the ghost button. That
leaves a 28px chevron target (like `icon-sm`) plus 8px.

**Estimated width at 375px (from CSS, not measured):**
- the container is 325px wide;
- the REGA cell is about 131px (a 14-character code plus padding; the name wraps under it);
- Ganadero is about 106px, per the reviewer's measurement of its longest word;
- the button is about 36px.

That is about 273px, which fits in 325–340px. One caveat: `break-words` (`overflow-wrap:
break-word`) does not lower a table cell's min-content width. A single unbroken word longer than
about 150px in a name could still widen the table. The Task 10 audit should measure this at
375/390px with long real names. I had no browser in this session.

**Tests (`ExplotacionesPage.movil.test.tsx`, with `matchMedia` stubbed through `vi.stubGlobal` and
unstubbed in `afterEach`):**
- **Narrow:**
  - the headers are exactly `Código REGA / Ganadero / Animales`, and `matchMedia` is called with
    `(min-width: 40rem)`;
  - the row has 3 cells;
  - "Finca 2" sits in the REGA cell as a `block text-muted-foreground` element right after the REGA
    line;
  - `getAllByText("Finca 2")` has length 1;
  - the panel's `td` has `colspan="3"`.
- **Wide:**
  - 4 headers including Nombre;
  - the name in its own cell, once;
  - `colspan="4"`.
- **Crossing the breakpoint with a panel open:** 4→3→4 columns, the span follows, the panel stays
  open, and there is still just 1 animals request.
- **No `matchMedia`:** the full desktop table.

### m1 — panel on the content edge

The expansion cell is `px-2 pt-1 pb-4`, not `px-4`. "N animales" and the crotal grid now start on
the same 8px edge as the text in every other cell.

### m2 — deterministic test for the `signal.aborted` guard in `then`

- In one test, `vi.mocked(listarAnimalesDeExplotacion)` gets 3× `mockImplementationOnce`. Each one
  returns a promise resolved by hand that **ignores the signal**, so nothing leaks into other tests.
- The steps:
  1. resolve page 0;
  2. click Siguiente twice;
  3. assert page 1's signal is aborted;
  4. resolve page 2 and wait for its crotal;
  5. resolve page 1 late;
  6. page 2's crotal and "Página 3 de 3" are still shown.
- With the guard removed, the test **fails 1** (the stale page 1 overwrites the list). My report's
  earlier "not reliably testable" was wrong. It is testable this way.

### m4 — retry and import close the panels

- `onImportado` is back to `setNumeroPagina(0); recargar();`, as it was before Task 8. `recargar()`
  closes the panels, so the import path now goes through, and exercises, `recargar`'s own
  `setAbiertas(new Set())`. Before this, it also went through `irAPagina`, which hid a mutation of
  `recargar`.
- **Tests:**
  - **Import:** open a panel, upload an `.xlsx` (mocked `/explotaciones/importar`). The list is
    requested again (1 → 2 requests), the panel closes, and the button is `aria-expanded="false"`.
  - **Retry:** open a panel, import; the reload returns a list 500; click "Reintentar". The third
    list request succeeds, and the panel is closed.
- A retry is only reachable after the list failed, and at that point the rows (and their panels)
  are already unmounted. So the retry case checks the behaviour, and the import case is the one
  that pins down `recargar`'s clear.

### Mutations (each applied to a copy and restored)

| # | Mutation | Result |
|---|---|---|
| M2 | drop the `signal.aborted` guard in `then` | **now fails 1** (m2 test) |
| M15 | `recargar` no longer closes the panels | fails 2 (import and retry) |
| M16 | `colSpan` always 4 | fails 2 |
| M17 | Nombre header always rendered | fails 2 |
| M18 | name line not a muted block | fails 1 |
| M19 | the hook doesn't subscribe to `change` | fails 1 (crossing the breakpoint) |
| M20 | no `matchMedia` → mobile | fails 1 |

### Commands (from `frontend/`)

- `npm test`: **29 files, 295 tests, all passing** (288 + 6 + 1).
- `npm run build`: OK, with only the existing chunk-size notice.
- `npm run lint`: only the 3 pre-existing warnings (badge, button, AuthContext).

### Note for DESIGN.md

- In a table with a full-width expansion row, a column dropped on mobile is removed from the
  structure (`useDesdeSm`), not just hidden with CSS. That keeps `colSpan` exact and each value in
  the DOM once.
- Below `sm`, the explotación name is a muted second line under the REGA code.
