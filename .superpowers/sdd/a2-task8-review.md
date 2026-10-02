# A2 Task 8 — Animales: independent review

## VERDICT

**APPROVED WITH FIXES.** Nothing is Critical. There is one Important finding: at 375px the Explotaciones table still overflows sideways, and opening a panel scrolls the REGA code out of view. Fix it now, or log it explicitly as a Task 10 item. There are also four Minor findings. The API contract, lazy loading, cancellation, states, label-in-name and scope are all correct.

## Commands (from `frontend/`, run by the reviewer)

| Command | Result |
|---|---|
| `npm test` | **28 files, 288 tests, all passed** (vitest 5.0.2, 15.2s) |
| `npm run build` | OK. Only the existing notice that one chunk is over 500 kB (559 kB). |
| `npm run lint` | 0 errors. The same 3 warnings as before (`AuthContext.tsx:231`, `badge.tsx:55`, `button.tsx:58`). |

Extra checks. All were run on a scratchpad copy or in a throwaway script, and all were deleted afterwards. Nothing in the repo changed.
- **Built app at 375, 390 and 640px:** Playwright against `vite preview`, with the API mocked by the same synthetic data as the screenshots. I measured the table container, the column widths and the computed colors.
- **The M2 mutant:** a deterministic test that kills it (see m2).

## Verified, no finding

**API contract**
- `sort=crotal,asc&sort=id,asc`, `page` and `size=20` are serialized as repeated params through `URLSearchParams`. The test asserts `getAll("sort")`.
- Both fields are on `CAMPOS_ORDENACION_ANIMALES = Set.of("crotal","id")` (`ExplotacionController.java:46`).
- The DTO `{id, crotal, crotalUltimosDigitos}` matches `AnimalResponse`.
- A 404 with no body (`ExplotacionController.java:60-61`) maps to the plain muted text "Esta explotación no existe o no es de tu gestoría." It has no Reintentar and no pager. That is correct, because retrying can't fix it.

**Lazy loading**
- **Explotaciones:** the panel is only mounted while `abierta`.
- **Ganadero:** `{animalesAbiertos && …}` sits inside the `hidden` div.
- **Explotaciones test:** no request before the click, then exactly one request, for that explotación only.
- **Ganadero test (m1):** exactly `["/ganaderos/7", "/explotaciones/2/animales"]`, and reopening fires a new request.
- **Cancellation:** closing or changing page aborts, through the effect cleanup's `AbortController`. The stale test asserts that page 2's signal is aborted and page 3's is not. With page 3 still in flight, there is no alert and the current page stays visible. The late page-2 answer doesn't overwrite page 3.
- **Unmounting:** unmounting while a request is in flight shows no alert, no status and no `console.error`.

**States**
- **Loading:** a skeleton plus a `role="status"` with the sr-only text "Cargando animales…".
- **Page change:** the old page stays visible, dimmed, with an sr-only status.
- **Error:** a destructive Alert with its own title, `mensajeDeError(e, "animales-explotacion")`, and a Reintentar that re-requests the same page.
- **Empty:** the text points to the Excel importer.
- **After a failed page:**
  - the old page is cleared, so it never poses as the answer;
  - the pager keeps the last known total ("Página 2 de 3") and both buttons stay usable.
- The `errores.ts` context has both texts, and it was added to the loop that checks every context has non-empty text.

**Accessibility**
- **Explotaciones disclosure:**
  - it is a real `<button>` with `aria-expanded`;
  - its name is `aria-label="Ver animales de {REGA}"` (measured: "Ver animales de ES120000000001"). That name **starts with** the visible text "Ver animales", so WCAG 2.5.3 label-in-name holds. Below `sm` only the chevron is visible, and the name stays complete.
- **`aria-controls` only while open:** acceptable. ARIA forbids an IDREF to a missing element. The Ganadero page keeps its always-present `hidden` target, which is also valid. The difference between the two pages is justified by their DOM, since a `<tr>` can't stay mounted-but-hidden cheaply here.
- **Expansion row:**
  - it is the immediate `nextElementSibling` of its data row, and the test asserts that;
  - it is one `td` with `colSpan={COLUMNAS}` (4), which the test compares with the real `columnheader` count.
- **Panel behaviour:** several panels can be open at once (`Set`). `irAPagina` and `recargar` (retry and import) clear the set. The code is right, but only the page change is tested (see m4).
- **Crotal list:** it is an `<ol aria-label="Crotales">`. The two `<span>` parts sit next to each other with no whitespace between them (JSX fragment), so `textContent` is the single crotal string. The test asserts `/^ES010000001234$/` on the `li`. Screen readers read inline spans as one string.
- **Chevron:** it is `aria-hidden`, and its rotation respects `motion-reduce`.

**Visual**
- **Grey on the Lino strip:** `--muted-foreground #6b6b60` over `--muted #eeede4` at 50% over white (the strip is ≈ `#f6f6f2`) gives **4.97:1**. That passes AA for 14px text. Measured in the browser, the computed color is `rgb(107,107,96)` and the row background is `color-mix(muted 50%)` over the white table container. Ink on the strip is 15.9:1.
- **Skeleton `bg-foreground/10`:**
  - this does **not** break the Exact Pair Rule. DESIGN.md scopes that rule to state badges (`BADGE_POR_ESTADO`) and to "state colors" ("Don't derive state colors by opacity"), and a skeleton carries no state meaning;
  - it is still a token, and DESIGN.md already uses 10% ink for rings (`ring-foreground/10`);
  - the reason holds: `bg-muted` on the `muted/50` strip is 1.08:1, which is invisible, while `foreground/10` is 1.22:1, a faint placeholder.
- **Ganadero footer:**
  - the panel content starts at x≈40, on the section's content edge (px-4), under "Contactos" and the names. Desktop and mobile screenshots match;
  - at 375px it has 2 grid tracks of about 166px;
  - it is chrome-less on the Lino footer, with no nested card.
- **Built CSS:** `has-aria-expanded:bg-muted/50` compiles to `:has([aria-expanded=true])`, so only the open row gets the fill. `max-sm:hidden` and the `auto-fill,minmax(8rem,1fr)` grid compile too.
- **Explotaciones at 640px and up:** everything fits (client width = scroll width). The chevron-only button works below `sm`.

**Tests**
- They use real axios and MSW. `vi.fn(real)` wraps the function only to observe the signal, which is justified by the known jsdom/XHR abort gap.
- The mutation table in the report is credible. I re-ran none of it except M2, which I confirmed.
- **m1:** the intent is kept and made stronger:
  - it used to check no mount before the click;
  - it now checks no *request* before the click, the right section only, and that closing unmounts and reopening reloads.
- The `GanaderoDetallePage.test.tsx` change only adds a handler, and its assertions are unchanged.

**Scope**
- These files have mtimes older than the Task 7 review: `backend/`, `public/`, `index.html`, DESIGN.md, CLAUDE.md, `package.json` and `package-lock.json`, and `TramiteReviewDialog.tsx`.
- The files newer than the Task 7 review, in `frontend/src`, are exactly the ones the report lists.
- `AnimalesDeExplotacionSlot.tsx` is gone.
- No hex in the new files, and no new dependency.
- `git diff --cached` is empty, so nothing is staged.
- The only `.impeccable/` files newer than the report are the 4 orchestrator screenshots.

## Critical

None.

## Important

**I1. At 375px the Explotaciones table still overflows sideways, and opening a panel scrolls the REGA code off screen.**
- **Where:** `ExplotacionesPage.tsx:150` (REGA cell, `whitespace-nowrap` inherited from `TableCell`) and `:158-179` (4th column).
- **Measured on the built app:**
  - the container is 325px wide at 375px, but the table is **380px**. The columns are 142 / 79 / 106 / 54: REGA, Nombre, Ganadero (min width set by "Explotaciones"), and the button;
  - at 390px it is 340 against 380.
- **Scenario:**
  - tapping the first row's chevron scrolls the table container (scrollLeft 55 at 375px, 32 at 390px);
  - the REGA text then starts at x = −22, so it is cut off. That is the row's identifier, and the header reads "digo REGA";
  - the first crotal column in the panel is clipped too (x = −14);
  - `mobile_explotaciones-abrir.png` shows exactly this.
- **Why it matters here:** the report says the column changes give "4 columns a chance to fit". They don't, even with the screenshot's own synthetic names, and real Spanish ganadero or finca names are often longer.
- **Fix (one option):**
  - below `sm`, hide the Nombre column (`max-sm:hidden` on its `th` and `td`) and show the name as a muted second line under the REGA code in the first cell. That saves about 79px, and `colSpan={4}` stays valid;
  - also, or instead, tighten the button cell below `sm` (`max-sm:px-1`, or an icon-size button) to save about 18px;
  - check it at 375px with the long names.
- If you would rather batch this into the Task 10 375px audit (H12), add it to "Pendientes acumulados para la Task 10" now so it isn't lost.

## Minor

**m1. The Explotaciones panel is indented 8px past the row's content edge.**
- **Where:** `ExplotacionesPage.tsx:184`. The expansion cell uses `px-4` (16px), while every other cell uses `TableCell`'s `p-2` (8px).
- **Measured:** at 640px the REGA text starts at x=33 and the first crotal at x=41. The desktop screenshot shows it too: "7 animales" sits right of "ES120000000001".
- **Fix:** `px-2` (keep `pt-1 pb-4`), so "N animales" and the crotal grid line up under the REGA code. That is the same "content edge" rule already applied in the Ganadero footer.

**m2. The M2 guard is testable deterministically, so the report's "not reliably testable" is wrong.**
- **Where:** `AnimalesDeExplotacion.tsx:54`.
- **Scenario:** a response has already resolved when the abort fires. Axios can't reject it any more, so only the `signal.aborted` check stops it from overwriting the newer page.
- **Proof:** I ran this on a scratchpad copy.
  - The test mocks `./api` with a `vi.fn()` whose implementation returns hand-resolved promises that **ignore the signal**.
  - It resolves page 0, clicks Siguiente twice, resolves page 2, and waits for its crotal.
  - It then resolves page 1 late and asserts that page 2's crotal is still the one shown.
  - It passes on the real code and **fails on the M2 mutant** (the list shows the stale page).
- **Fix:** add that test to `AnimalesDeExplotacion.test.tsx`. It fits the existing `vi.mocked(listarAnimalesDeExplotacion)` setup: use `mockImplementation` in one test, and `restoreMocks` resets it.

**m3. `aria-busy` wraps the live status that announces the loading, and the status regions are mounted already filled.**
- **Where:** `AnimalesDeExplotacion.tsx:72` (`aria-busy={cargando}` on the root) and `:102-106` and `:168` (`role="status"` mounted with its text).
- **Scenario:**
  - screen readers that honour `aria-busy` hold back changes inside a busy subtree until it clears, and by then the status has been removed;
  - many screen readers also don't announce a live region that is inserted with its content already in it.
  - So the "Cargando animales…" announcement may never be heard. Nothing is silent visually, so this is Minor.
- **Fix:** drop `aria-busy` from the root, or move it to the `<ol>` only. Keep one persistent sr-only `role="status"` whose text changes ("Cargando animales…" → "N animales" / "").
- The same pattern exists in `GanaderosPage.tsx:144`, so it is fine to handle it in the Task 10 audit.

**m4. Only the page change is tested for closing the Explotaciones panels; retry and import are not.**
- **Where:** `ExplotacionesPage.animales.test.tsx`. The M10 mutation only covered `irAPagina`.
- **Scenario:** a future refactor of `recargar` (`ExplotacionesPage.tsx:46-49`) that drops `setAbiertas(new Set())` would pass the suite. A panel would then stay open over an inventory the import just changed.
- **Fix:** add two short cases:
  - open a panel, trigger a list 500, then Reintentar, and assert it is closed;
  - open a panel, import a file (mock `/explotaciones/importar`), and assert it is closed and the list re-requested.

Nit, not required: in table-navigation mode, a screen reader will announce the expansion cell under the "Código REGA" header. Adding `headers=""` or an `aria-label` on the cell is optional, and can be left for the Task 10 audit.

# Re-review (fixes)

## VERDICT

**APPROVED.**
- I1, m1, m2 and m4 are fixed and verified. The new tests fail against the mutations they guard.
- There is one new Minor, a residual edge case of I1: an unbroken word of about 27 characters. It can go to the Task 10 audit.
- m3 and the nit stay deferred to Task 10, as agreed.

## Commands (from `frontend/`, run by the reviewer)

| Command | Result |
|---|---|
| `npm test` | **29 files, 295 tests, all passed** (288 + 6 + 1) |
| `npm run build` | OK. Only the existing notice that one chunk is over 500 kB. |
| `npm run lint` | 0 errors. The same 3 warnings as before (badge, button, AuthContext). |

I also ran two kinds of extra check.
- **Mutations**, on a scratchpad copy:
  - removing the `signal.aborted` guard in `then`: the m2 test **fails**;
  - removing `setAbiertas(new Set())` from `recargar`: **both** m4 tests fail, import and retry.
- **Measurements**, in real Chromium: a scratch build served by `vite preview`, with a mocked API. It has one explotación with long names, and the first panel opened.

After that I deleted the scratch copy and the script, and stopped the server. `AnimalesDeExplotacion.tsx` has a newer mtime but the same content (180 lines, identical logic). The implementer restored it after the mutation runs.

## I1: verified

**Real names at the four narrow widths**

The explotación was "Finca La Vega de Arriba del Río Guadalquivir", with the ganadero "Explotaciones Hermanos García S.L.".

| Viewport | Table `scrollWidth` / container | `scrollLeft` | Columns |
|---|---|---|---|
| 375 | 325 / 325 | 0 | REGA 162, Ganadero 125, button 38; `colspan` 3 |
| 390 | 340 / 340 | 0 | — |
| 639 | 589 / 589 | 0 | 3 columns |
| 640 | 590 / 590 | 0 | 4 columns; `colspan` 4 |

**Other measurements**
- The REGA text and the first crotal both start at x=33, so the row text and its panel share one edge.
- The chevron button is 30×28, above the 24px minimum of WCAG 2.5.8 (AA).
- The updated `mobile_explotaciones-abrir.png` matches: no clipping, and the name is a muted line under the REGA.
- The page-level `scrollWidth` 642 is the navigation bar, which is known and out of scope.

**Hook against a pure CSS approach (`useDesdeSm`, `useSyncExternalStore` over `matchMedia("(min-width: 40rem)")`)**
- **SSR and hydration:** not applicable. `main.tsx` uses `createRoot`, not `hydrateRoot`, so `getServerSnapshot` (`() => true`) is never used.
- **Flash:** none. `getSnapshot` reads `matchMedia` synchronously during the first render, so a phone paints the 3-column table from the first frame.
- **Snapshot:** it is a primitive boolean, so there is no re-render loop.
- **Subscription:** `suscribir` is module-level and stable, so there is no resubscription on every render.
- **Crossing the breakpoint with a panel open:** measured in the real browser, 640 → 600 → 800 goes 4 → 3 → 4 headers, with `colspan` following. The crotal list stays mounted, and the jsdom test shows no second request.
  - The panel's `<tr>` keeps its position and element type inside the keyed `Fragment`, so `AnimalesDeExplotacion` keeps its state.
- **Breakpoint consistency:** `40rem` in `matchMedia` and Tailwind's `max-sm` (`not all and (width>=40rem)`) resolve the same way. Media-query `rem` uses the browser's initial font size in both, so JS structure and CSS styling can't disagree at 639/640. I checked 639 and 640 directly.
- **The trade-off is justified:**
  - CSS alone would either duplicate the name in the DOM (both copies read by screen readers), or leave a `colSpan=4` over 3 laid-out columns;
  - the hook's only cost is a JS decision about layout, limited to this one structural choice, with the fallback without `matchMedia` going to desktop.

## m1: verified

The expansion cell is now `px-2 pt-1 pb-4` (`ExplotacionesPage.tsx`). The first crotal lines up with the cell text (x=33 = x=33) at 375, 390, 640 and 1440.

## m2: verified

- The new test is in `AnimalesDeExplotacion.test.tsx`, in the describe block "respuesta que llega ya resuelta tras cancelarla (m2)".
- It uses three `mockImplementationOnce` promises that ignore the signal. It resolves page 0, clicks Siguiente twice, asserts page 1's signal is aborted, resolves page 2, and then resolves page 1 late. Page 2 and "Página 3 de 3" stay on screen.
- It is deterministic, since there are no timers.
- It **fails without the guard**, which I confirmed on the scratch copy.

## m4: verified

- **`onImportado` is now `setNumeroPagina(0); recargar()`. It is correct.**
  - React batches both updates into one effect run for `[numeroPagina, version]`, so there is exactly one reload, including when already on page 0 (only `version` changes then).
  - `recargar` clears the panels.
  - Dropping the `irAPagina` call removes a redundant clear that used to hide a regression in `recargar`.
- **Import test:** 1 → 2 list requests, the panel closes, and the button is `aria-expanded="false"`.
- **Retry test:** it is meaningful after all. Its comment says the rows are unmounted while the list is in error. They are, but the `abiertas` set **survives that unmount**. Without the clear, the successful retry would bring the row back already open. That is why the mutation fails this test as well.

## No regressions

- The existing Explotaciones, Ganadero and animales tests all pass.
- Label-in-name, `aria-controls` only while open, and the adjacency of the expansion row are unchanged.
- Nothing is staged. Since my first review, the only changed files are the 5 in `frontend/src/features/explotaciones` that the report lists.

## New finding

**Minor**

**n1. A single unbroken word of about 27 characters in a name still brings back horizontal scroll.**
- **Where:** `ExplotacionesPage.tsx`, the name `span` (`break-words`) in the mobile REGA cell, and the Nombre and Ganadero cells.
- **Measured:** with the explotación name "Agroganaderaextremeñadelsur" and the ganadero "Agropecuariasextremeñas S.L.", both unbroken:

  | Viewport | Table `scrollWidth` / container | `scrollLeft` after opening | Row text starts at |
  |---|---|---|---|
  | 375 | 436 / 325 | 111 | x = −78 |
  | 640 | 674 / 590 | 76 | x = −43 |

  At 640 the overflow comes from the 4-column desktop layout.
- **Cause:** as the implementer noted, `overflow-wrap: break-word` doesn't reduce a table cell's min-content width.
- **Impact:** real Spanish names with ordinary words (up to about 16 characters, such as "Aprovechamientos") fit, so this is an edge case.
- **Fix:** `wrap-anywhere` (Tailwind v4, `overflow-wrap: anywhere`), which *does* lower min-content. Use it instead of `break-words` on those three name elements, then re-measure at 375 and 640 with such a word.
- It can go to the Task 10 audit (H12) and doesn't block Task 8.
