# A2 — Task 6 (critique of the trámites queue): independent review

## VERDICT: APPROVED, CONDITIONAL ON ONE TEST-ONLY FIX (I1)

The production code is correct:
- the complete loader is all-or-nothing;
- the REGA translation never shows an id;
- rows are keyboard-reachable;
- N1 and N2 are fixed;
- scope is clean.

There's no Critical finding. One Important finding: the loader's "total changed between pages" guard has no test that fails when it's removed, because the only test for it passes through the count check instead. On a decision Antonio made explicitly ("never a partial list"), that guard needs its own regression test. The fix is a one-line fixture change. Everything else is Minor.

## Command results (from `frontend/`, run by the reviewer)

| Command | Result |
|---|---|
| `npm test` | **20 files, 193/193 passed** (17.4 s). No MSW unhandled-request output. |
| `npm run build` | Green (`tsc -b && vite build`). Only the usual > 500 kB chunk notice (540.69 kB JS, 47.84 kB CSS). This rebuilt the git-ignored `dist/`. |
| `npm run lint` | 0 errors, **3 warnings, all pre-existing** (`only-export-components`: `AuthContext.tsx:231`, `badge.tsx:55`, `button.tsx:58`). |

These match the implementer's report exactly.

## Mutation testing

**Method.**
- A copy of `frontend/` (without `node_modules` or `dist`) was put in the scratchpad, with `node_modules` junctioned in.
- Each mutation was applied to the copy, followed by `vitest run src/features src/shared` (191 tests), and then restored.
- The copy was deleted afterwards. The junction was removed with `rmdir`, and the real `node_modules` is intact.

| Mutation | Result |
|---|---|
| Loader: "total changed between pages" check disabled | **0 fail** → I1 |
| Loader: duplicate-id check disabled | 1 fails |
| Loader: count check disabled | 0 fail. This is expected, not a gap: the duplicate-id check (`idsDistintos !== totalElements`) also catches any count mismatch, so the count check is redundant but harmless. |
| N1: only the `setTotalPaginas(0)` reset removed | 1 fails |
| N1: only the `setPagina(null)` reset removed | 1 fails |
| N1: only the `setNumeroPagina(0)` reset removed | **0 fail** → M1 |
| N2: clamp disabled | 1 fails |
| Row button `stopPropagation()` removed | 0 fail. Harmless, because the row and the button set the same id (see Verified). |
| Hook: `aborted`/`esCancelacion` guard removed | 0 fail → M2 |
| Hook: `abort()` in cleanup removed | 0 fail → M2 |
| `aria-busy` removed | 0 fail. Not worth a test. |

## Verified, no finding

**1. Complete loader (decision 20)**

`todasLasExplotaciones.ts`:
- Page 0 sets the reference `totalElements`/`totalPages`. Pages 1…`totalPages-1` are fetched sequentially and bounded by page 0's `totalPages`, so the loop can't run forever.
- The first failed page aborts the loop by rethrowing its own `ErrorApi`. A test checks that no further pages are requested.
- It throws, and returns no list, when:
  - `totalElements` or `totalPages` changes between pages;
  - the number of items received ≠ `totalElements`;
  - an id is duplicated.

Edge cases:
- **`totalElements = 0`:** Spring returns `totalPages = 0`, so there's one request and `[]`. Tested.
- **Exactly 500:** 1 page. The count matches.
- **501:** 2 pages. Covered structurally by the 3-page test.
- **Empty page in the middle:** the count falls short, so it throws.
- **Backend clamping `size`:** the loop uses the server's own `totalPages`, so a clamp would be absorbed transparently. There's no custom page-size config: `backend/src/main/resources/application.yml` has no `spring.data.web.pageable.*`, and there's no `PageableHandlerMethodArgumentResolver` or `setMaxPageSize` in the Java code. Spring's default maximum page size of 2000 applies, so 500 isn't clamped.
- **Sort:** `codigoRega` is on the `ExplotacionController.CAMPOS_ORDENACION` whitelist (`codigoRega`, `nombre`, `id`), so the request never gets a 400. `codigo_rega` is UNIQUE, so the offset paging order is stable.
- **Page serialization:** there's no `@EnableSpringDataWebSupport(VIA_DTO)`, so `totalElements`/`totalPages` sit at the top level, which matches `Pagina<T>`.

`useTodasLasExplotaciones.ts`:
- The discriminated union means only `listo` carries a list, so a partial list is impossible even at the type level.
- The list loads once per mount, and `reintentar()` sets `cargando` and reloads everything. Both are tested.
- `AbortController` aborts in-flight requests on unmount or retry. `httpClient`'s response interceptor maps `ERR_CANCELED` to `ErrorApi{cancelado:true}`. The `.catch` returns silently when `signal.aborted || esCancelacion(err)`, so a cancellation never becomes an error.
- **Retry** works, both in the hook test and in the page test (the Alert's "Reintentar" completes the translation).
- **"Total changed → error" rule.** This is acceptable for real use. Only a concurrent import (or deletion) during the few hundred milliseconds of the load triggers it. The result is a visible Alert with "Reintentar", never a silent partial list, and the next attempt succeeds once the import finishes. The error text is the generic `listar-explotaciones` message: "No se han podido cargar las explotaciones. Inténtalo de nuevo." It's fine for a retry.

**2. REGA translation (decision 22)**

`presentarExplotacion` handles each case:
- null or undefined → `sin-asignar`, regardless of the load state;
- loading → `cargando`;
- error → `no-disponible`;
- not in `porId` → `no-encontrada`;
- found → `codigoRega` plus `nombre`.

Neither the type nor the rendering ever carries the id. Tests assert that neither `#3` nor `77` appears.

The error state shows a destructive Alert ("No se han podido cargar los códigos REGA", with the real message and "Reintentar") plus "—" in each cell with `sr-only` "Código REGA no disponible".

The loading state is a `bg-muted` bar with `motion-safe:animate-pulse` and `sr-only` text.

**3. Keyboard rows**
- The row's opener is a real `<button type="button">`, so Enter and Space work natively. Both are tested and open the correct trámite.
- The accessible name "Revisar trámite #N" contains the visible "#N" (WCAG 2.5.3). Tab reaches it (tested).
- The focus ring is `focus-visible:ring-3 focus-visible:ring-ring/50`, the `--ring` token. There's no border, which is acceptable for an inline link-style control; DESIGN.md's "border plus ring" describes boxed controls.
- `has-focus-visible:bg-muted/50` compiles, as confirmed in the built CSS: `:has(:focus-visible)` gives `var(--muted)` at 50%.
- `<tr onClick>` has no role or tabIndex, so it isn't an interactive element and the button isn't nested inside one. `Badge` renders a non-interactive span, and nothing interactive is nested anywhere.
- **No double open.** The button stops propagation, and even without that, both handlers call `setTramiteSeleccionado(sameId)`, which is idempotent.

**4. Crotales in the row**
- The row shows 2 crotales (`MAX_CROTALES_EN_FILA`), then "+N más". The tooltip lists "crotal · etiqueta" for the rest.
- An empty list shows "Sin crotales", and a missing `crotales` field is tolerated.
- Badges come only from `crotal.resolucion` via `BadgeResolucionCrotal`/`presentacionResolucion`, so no backend rule is duplicated.
- `crotal || crotalIndicado` is a display fallback, not a classification. `enInventario` isn't used.
- The flex `ul` doesn't wrap, so the row stays one line tall.

**5. N1, N2 and M2**
- **N1:** `cambiarFiltro` resets the page, `totalPaginas` and the shown page. The test fails if `totalPaginas` or `setPagina(null)` is reverted, but not the page reset; see M1.
- **N2:** after a reload, `numeroPagina > 0 && numeroPagina >= totalPages` gives `setNumeroPagina(max(0, totalPages-1))`, with `pagina = null` and `cargando` kept true. It can't loop, because the new index is always lower than the old one. The test fails if this is reverted.
- **M2:** the filter-options test compares against `Object.values(ESTADOS_TRAMITE).map(e => e.etiqueta)`, which works as a regression guard.

**6. States**
- **First load or filter change:** a "Cargando trámites…" row, with `aria-busy` on the table.
- **Reload with rows present:** `opacity-60` dimming, and the rows stay readable.
- **Empty:** "No hay trámites en «X»." with a filter, and the generic text without one.
- **Count:** singular and plural ("1 trámite", "N trámites").
- **Load error:** an Alert with the real message and "Reintentar". The stale page is cleared, and the pager survives (M3).

I found no silent failure path.

**7. 375px (static check)**
- `TableCell` and `TableHead` have `whitespace-nowrap`.
- `Table` wraps the table in `overflow-x-auto`.
- The page column and table wrapper have `min-w-0`.
- The header uses `flex-wrap`, and the filter is `w-full sm:w-56`.
- The top nav bar is out of scope (H12).

This still needs a real browser check in Task 10 or 11, as the report states.

**8. Tokens and design rules**
- The touched files have no hex values and no `shadow-*`, which follows the Paper-Flat Rule.
- All colours are existing tokens: `text-primary`, `text-muted-foreground`, `bg-muted`, `ring-ring/50`, `bg-card`, and the badge variants.
- There are no new colours.

**9. The critique follows the Impeccable format reasonably.**
- **Structure:** it has the required first-line `⚠️ DEGRADED: single-context (…)` banner with an honest reason, and the Design Health Score table (10 heuristics, 22/40, band). It also has:
  - the Design Specificity Verdict, with the detector result (`[]`);
  - Overall Impression and What's Working;
  - 5 Priority Issues with P-tags, Why, Fix and Suggested command;
  - Persona Red Flags and Minor Observations;
  - Questions to Consider, each with 2–3 concrete options, which satisfies the final-question gate;
  - Run Notes covering slug, ignore list, independence, detector, browser, overlays, live server and temp files.
- **Deviations:**
  - it skipped snapshot persistence to `.impeccable/critique/`, which is justified and stated;
  - it has no post-answer Action Summary, which is fine, because the answers are Antonio's to give.
- **Scope of findings:** it separates applied from deferred findings, with a reason for each.

**10. Scope**
- Since the Task 5 review, the only files changed in the repo are:
  - the 3 new loader/translation modules and their 3 tests;
  - `explotaciones/api.ts`, whose options argument is optional and backward-compatible with `ExplotacionesPage`;
  - `TramitesPage.tsx` and its test;
  - `tramites/etiquetas.ts`, which is listed under Minor as M5;
  - the two `.superpowers` reports.
- Nothing changed under `backend/`, `public/`, `index.html`, `DESIGN.md`, `CLAUDE.md`, `PRODUCT.md`, `.claude/skills` or `.agents`. Their modifications all predate Task 6 (timestamps from Tasks 1–5).
- `package.json` and `package-lock.json` date from Task 1 (22:26–22:27), so there are no new dependencies.
- `git diff --cached` is empty, so nothing is staged.
- `.impeccable/` already existed (config.local.json, 2026-09-25), is git-ignored, and has no `critique/` subfolder, so no new folder was created.

## Findings

### Critical

None.

### Important

**I1. The "total changed between pages" guard has no effective test.**

- **Where:** `frontend/src/features/explotaciones/todasLasExplotaciones.test.ts:118-125`, which guards `todasLasExplotaciones.ts:34-36`.
- **Why the test doesn't cover the guard:** the fixture is page 0 `[1,2]` with total 4, then page 1 `[3,4,5]` with total 5. That fails through the count check (5 received ≠ 4), so disabling lines 34-36 leaves all tests green (mutation above).
- **Scenario the guard exists for:**
  1. Page 0 reports total 4 and returns `[1,2]`.
  2. A concurrent import adds explotación 5 (or it's inserted before page 1's window).
  3. Page 1 reports total 5 and returns `[3,4]`.
  4. The count (4) equals the *reference* total, and no id is duplicated.
- **Consequence:** without the guard, the loader silently returns a 4-item list while the gestoría has 5. That's exactly the partial list decision 20 forbids. The code is correct today, but a future refactor could delete the guard unnoticed.
- **Fix:** change the fixture to page 1 `{ ids: [3, 4], totalElements: 5, totalPages: 2 }`, or add that as a second case, so only the between-pages check can catch it. Also add a case where only `totalPages` changes.

### Minor

**M1. N1's page reset isn't covered by a test.**

- **Where:** `TramitesPage.tsx:110` (`setNumeroPagina(0)` in `cambiarFiltro`) and `TramitesPage.test.tsx:393-419`.
- **Why it's untested:** the N1 test changes the filter while on page 0, so removing the page reset passes.
- **Scenario:**
  1. The user is on page 3 of "Todos".
  2. They switch to "Pendiente de revisión", which has 5 pages.
  3. They land on page 3 of the new filter instead of page 1.
  4. N2 only rescues the case where the new filter has fewer pages.
- **Fix:** in the N1 test, click "Siguiente" once before changing the filter, and assert that the request for the new filter has `page=0`.

**M2. The hook's cancellation has no test.**

- **Where:** `useTodasLasExplotaciones.ts:44` (guard) and `:47` (cleanup abort).
- **Why it's untested:** removing either passes all tests. The behaviour is correct by inspection: the interceptor maps `ERR_CANCELED` to `ErrorApi{cancelado:true}`.
- **Scenario:** in dev StrictMode, the first effect is aborted. Without the guard, its cancellation would briefly set `estado: "error"` and flash the red Alert before the second request resolves.
- **Fix:** add a `renderHook` test that:
  1. holds the `/explotaciones` response behind a gate;
  2. unmounts, or calls `reintentar()` while the request is in flight;
  3. asserts that the request's `signal` was aborted (MSW `request.signal.aborted`) and that no `error` state was ever observed.

**M3. Tooltip-only information isn't reachable by keyboard or touch.**

- **Where:** `TramitesPage.tsx:287` (the explotación name in `title`), `:331-333` ("Indicado: …"), `:341-345` (the remaining crotales in the "+N más" `title`), and `:302`/`:311` (the "—" explanations).
- **Scenario:** Casey, on a phone, sees "+2 más" but can't find out which crotales they are. Sam, on a keyboard, can't reach the explotación name, because `title` on a non-focusable span is not reliably announced and never shows on focus.
- **Severity:** Minor, because every piece is supplementary. The "—" cells already have `sr-only` text, and the modal shows the full crotal list and the explotación.
- **Fix:** add `sr-only` text for the remaining crotales and the "Indicado" value, like the "—" cells already do, and optionally the explotación name. A focusable tooltip component can wait for Task 10's audit.

**M4. "Not in your list" can be a stale-cache artefact.**

- **Where:** `TramitesPage.tsx:307-316` and `useTodasLasExplotaciones.ts` (cached for the page's lifetime).
- **Scenario:**
  1. The queue stays open for hours while a colleague imports new explotaciones.
  2. `recargar` refreshes the trámites, but not the explotaciones.
  3. A trámite for a newly imported explotación shows "—" with the tooltip "Esta explotación no está en tu lista de explotaciones." That's false: it is in the list, just newer than the cache.
- **Fix, either one:**
  - reword the tooltip to "No aparece en la lista cargada; recarga la página";
  - have the page call `reintentar()` once when a trámite references an unknown id (at most once per load, to avoid loops).

  This can be deferred to the Task 9 combobox work, which reuses the hook.

**M5. `tramites/etiquetas.ts` was modified but isn't listed in the report.**

- **Where:** the mtime of `frontend/src/features/tramites/etiquetas.ts` is 23:48:41, 27 s after `a2-task5-review.md` was written.
- **What I can't tell:** the file is untracked, so I can't diff it to attribute the change. It may be a Task 5 post-review edit.
- **What I checked:** its current exports (`presentacionResolucion`, `ESTADOS_TRAMITE`, …) are exactly what `TramitesPage` uses, and its tests pass.
- **Fix:** the orchestrator should confirm which task owns that edit, so the Task 11 file list is accurate.

**Informational, not a defect.** Offset paging can't detect a delete-plus-insert that keeps the total equal and shifts an item across a page boundary. The result would be an item skipped and a deleted one kept, with the count equal and no duplicates. No client-side check can catch this. It's rare and self-heals on the next load. The real fix is the planned backend `explotacionCodigoRega` in the trámite DTO (H7) and `?q=` (H3), which are already in the mini-prompt.

# Re-review (fixes + decision 28)

## VERDICT: APPROVED

- I1 and M1–M4 are fixed correctly. Each fix has a test that fails when the fix is reverted; I checked this myself in a scratch copy.
- Decision 28 is implemented as specified.
- The adapted tests keep their original intent, and no test was deleted.
- I found no regressions.
- Two new Minor notes remain (R1 and R2). Neither blocks approval.

## Command results (from `frontend/`, run by the reviewer)

| Command | Result |
|---|---|
| `npm test` | **20 files, 198/198 passed**. That's 193 + 5 (I1 +1, M2 +2, D28 +2). No stray stderr and no MSW warnings. |
| `npm run build` | Green (`tsc -b && vite build`). Only the usual > 500 kB chunk notice. |
| `npm run lint` | 0 errors, **3 pre-existing warnings** (`only-export-components`: `button.tsx:58`, `AuthContext.tsx:231`, `badge.tsx:55`). |

## Mutation testing

**Method.**
- I used a scratch copy of `frontend/` with `node_modules` junctioned in.
- For each mutation I ran `vitest run src/features`, then restored the file.
- I deleted the copy afterwards; the real `node_modules` is intact.

| Mutation | Result |
|---|---|
| I1: drop the `totalElements` half of the between-pages guard | 1 fails ("si totalElements cambia…") |
| I1: drop the `totalPages` half | 1 fails ("si solo totalPages cambia…") |
| M1: drop `setNumeroPagina(0)` in `cambiarFiltro` | 1 fails (N1) |
| M2: drop the `aborted \|\| esCancelacion` guard | 1 fails (retry test) |
| M2: drop the cleanup `abort()` | 2 fail (unmount and retry tests) |
| M3: drop each of the three `sr-only` spans | 1 fails for each |
| D28: `FILTRO_INICIAL = "TODOS"` | 5 fail |
| D28: `FILTRO_INICIAL = "RECHAZADO"` | 6 fail |
| D28: "Todos" sends `estado=TODOS` | 1 fails (the "Todos" test) |
| Loader stops passing `signal` to `listarExplotaciones` | **0 fail** → R1 |
| `listarExplotaciones` stops passing `signal` to axios | **0 fail** → R1 |

## Verified, no finding

- **I1.** In both new fixtures the count check and the duplicate check pass: 4 items arrive, matching page 0's total, and no id repeats. Only the between-pages guard can catch them, and the mutations above confirm it.

- **M1.** The N1 test first goes to "Página 2 de 5". It then switches to "Rechazado", which also has 5 pages, so N2 can't mask the bug. It asserts that `pedidasRechazado` equals `["0"]` and that "Página 1 de 5" is shown.

- **M2: `vi.mock` + `vi.fn(real)` is sound and not vacuous.** The spy only records the argument. The loader, the axios request and the interceptor mapping stay real.
  - The handler counters (`enVuelo` and `peticiones`) prove the real HTTP request happens.
  - If the hook passed no signal, `senal.aborted` would throw.
  - Both production guards are mutation-killed (see the table).
  - The retry test is meaningful. Axios rejects as soon as the signal aborts (`CanceledError`), and the interceptor maps that to `ErrorApi{cancelado}`. The 50 ms wait lets the rejection reach the `.catch`, which the guard mutation proves, and the `estados` history shows that `error` never rendered.

- **M3: what a screen reader hears.**
  - **REGA cell:** "ES280790000123, Finca La Dehesa". The leading ", " separates the two, so this reads correctly.
  - **Crotal cell and "+N más":** the `sr-only` text has no separator from the visible text before it. See R2.
  - **Duplication:** the DOM text never duplicates. The `title` repeats the `sr-only` text, so screen readers that announce `title` as the description (JAWS, VoiceOver) may say it twice. That's tolerable, and it's the same pattern the "—" cells already used.

- **M4.** The tooltip now says "No aparece en la lista de explotaciones cargada al abrir la página. Si se importó después, recarga la página.", and a matching `sr-only` text says the same. It no longer claims the explotación doesn't exist, and it tells the user how to fix it. The test was updated to match.

- **Decision 28.**
  - `FILTRO_INICIAL = "PENDIENTE_REVISION"`, and the only initial request is `estado=PENDIENTE_REVISION&page=0`.
  - The trigger (the "Filtrar por estado" combobox) shows "Pendiente de revisión", from the `SelectValue` render function over `ESTADOS`.
  - "Todos los estados" sends no `estado` at all: `undefined` is dropped by axios, and the test asserts `has("estado") === false`.
  - The default empty state reads "No hay trámites en «Pendiente de revisión».", which is correct and matches every other named filter.
  - After an approval, the approved row drops out of the default filter on reload. N2 still handles a last page that ends up empty.

- **The adapted tests A–F keep their intent.**
  - **A:** the badges are now looked up inside the table, because the trigger shows the same label. It still checks the classes and still checks that the raw enum never appears.
  - **B, C, D:** only the awaited empty text changed, to the new default's wording. C's assertion on the filter options is unchanged. D still switches to "Rechazado" and checks its named empty state.
  - **E:** now matches `/^No hay trámites/`, which is stricter because it covers any empty-state wording.
  - **F:** the N1 test, strengthened as described under M1.

- **No test was deleted.** I compared the test names against the ones I recorded in my first review.

  | File | Before | Now |
  |---|---|---|
  | `TramitesPage.test.tsx` | 17 | 19: all 17 are still present; only the N1 title was renamed ("…vuelve a la página 1 y no queda a la vista nada del filtro anterior"); 2 D28 tests added |
  | `todasLasExplotaciones.test.ts` | 7 | 8: all 7 are still present; "si el total cambia entre páginas" became the `totalElements` case with a corrected fixture; the `totalPages` case was added |
  | `useTodasLasExplotaciones.test.tsx` | 3 | 5: all 3 are still present; 2 added for M2 |

- **Regressions:** none found.
  - The production changes are limited to `TramitesPage.tsx`: the default filter, the `sr-only` spans, the tooltip wording, and a `textoCrotalResumido` helper that feeds both the `title` and the `sr-only` text.
  - The loader and hook sources haven't changed since my first review.

- **Scope.**
  - Nothing is staged.
  - `backend/`, `DESIGN.md`, `CLAUDE.md` and `.agents` have no Task 6 changes.
  - There are no new dependencies.
  - `.impeccable/` now contains `surfaces/frontend-src-features-ganaderos.md` (written at 23:20). It's git-ignored and comes from the orchestrator's Task 7 craft pass (decision 27), not from Task 6, so it isn't a Task 6 finding.

## New findings

### Critical / Important

None.

### Minor

**R1. No test checks that the abort actually reaches the HTTP request.**

- **Where:** `todasLasExplotaciones.ts:23` and `explotaciones/api.ts:18`.
- **The gap:** the M2 tests prove that the hook aborts the signal it passes to the loader. They don't prove that the loader and `listarExplotaciones` pass that signal on to axios. Dropping `signal` in either place leaves every test green.
- **Impact today:** nothing the user can see. The hook's `aborted` guards still stop any stale state update. The only effect is that an in-flight request (up to 500 rows) would run to completion for nothing after an unmount or a retry.
- **Fix (optional):** a loader-level test with a gated MSW handler:
  1. call `cargarTodasLasExplotaciones(controller.signal)`;
  2. call `controller.abort()`;
  3. expect the promise to reject promptly, with `esCancelacion(err) === true`, *before* the gate opens.

  This works in jsdom, because axios rejects on the signal itself. It fits in Task 10 or in the Task 9 combobox work.

**R2. Two `sr-only` texts run straight into the visible text.**

- **Where:** `TramitesPage.tsx:348-351` (the crotal, then `sr-only` "Indicado: …") and `:358-359` ("+{n} más", then `sr-only` "Además: …"). Neither has a separator.
- **What it can sound like:**
  - "ES123456789012Indicado: 789012"
  - "+1 másAdemás: ES000000000001 · No está en el inventario"
- **When:** whenever the accessible text is flattened, for example the cell's name in table navigation, or some screen readers' virtual buffers. `sr-only` uses absolute positioning, which often (but not always) makes it a separate text chunk.
- **Fix:** start both `sr-only` strings with a separator, the way the REGA cell already does (`, {nombre}`):
  - `{", "}Indicado: …` or ` (indicado: …)`
  - ` Además: …` or `. Además: …`

  Then update the two exact-string matches in the tests. This can wait for the Task 10 audit.
