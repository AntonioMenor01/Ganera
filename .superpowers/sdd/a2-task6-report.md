# A2 — Task 6: Critique of the trámites queue — Implementer report

## Summary

I ran `/impeccable critique` on the queue first, following Impeccable's critique workflow: the
`impeccable context` launcher, Assessment A, then the detector. The result is
`.superpowers/sdd/a2-task6-critique.md`: 22/40, 1 P0, 2 P1 and 2 P2, ranked. I then applied every
finding that falls inside A2's queue scope, writing the tests first, and deferred the rest with a
reason for each.

**Critique run caveats**, stated in the critique's banner and Run Notes:

- **Degraded, single context.** The skill wants two sub-agents, but my harness forbids spawning
  agents without an explicit user request, and this run came from the orchestrator.
- **Static only.** There's no browser, because the Playwright MCP isn't configured (plan H8).
- **No snapshot.** I didn't write the `.impeccable/critique/` snapshot, so no new untracked
  directory appears in the repo.
- **The detector ran twice.** Once on the original three tramites files, and once on the changed
  page, as the launcher's `MANUAL_DETECTOR_REQUIRED` directive asks. Both runs returned `[]`, with
  0 findings.

## Files

**New:**
- `frontend/src/features/explotaciones/todasLasExplotaciones.ts`: `cargarTodasLasExplotaciones`
  plus its constants.
- `frontend/src/features/explotaciones/todasLasExplotaciones.test.ts`: 7 tests.
- `frontend/src/features/explotaciones/useTodasLasExplotaciones.ts`: the shared hook, which
  exports the `CargaTodasLasExplotaciones` type.
- `frontend/src/features/explotaciones/useTodasLasExplotaciones.test.tsx`: 3 tests.
- `frontend/src/features/tramites/explotacionDeTramite.ts`: `presentarExplotacion`, the pure
  translation from id to REGA.
- `frontend/src/features/tramites/explotacionDeTramite.test.ts`: 4 tests.
- `.superpowers/sdd/a2-task6-critique.md` and this report.

**Modified:**
- `frontend/src/features/explotaciones/api.ts`: `listarExplotaciones(page, size, { sort?, signal? })`.
  The third argument is optional, so `ExplotacionesPage` keeps working unchanged.
- `frontend/src/features/tramites/TramitesPage.tsx`
- `frontend/src/features/tramites/TramitesPage.test.tsx`: shared fixtures, a default
  `/explotaciones` handler in `beforeEach`, and 12 new tests. The 4 existing tests are kept as
  they were.

Nothing under `backend/`, `public/`, `index.html`, `DESIGN.md`, `CLAUDE.md`, `PRODUCT.md`, the
skills, `.agents/`, `logo.jpg` or `preview.png` was touched. There are no new dependencies, no hex
values and no git operations.

## Applied (in scope)

- **P0: rows accessible from the keyboard.**
  - The first cell holds a real `<button type="button">`. Its visible text is `#N`, and its
    `aria-label` is "Revisar trámite #N", which contains the visible text (WCAG 2.5.3).
  - Enter and Space work natively, and the button has the green focus ring (`ring-3 ring-ring/50`).
  - The row highlights when its button has keyboard focus (`has-focus-visible:bg-muted/50`).
  - The `<tr>` click stays as a mouse convenience only. The button stops propagation, so it
    doesn't open twice.
  - base-ui's Dialog returns focus to the button when it closes.
- **P1: REGA instead of the id (H7 / decision 22).** The cell comes from
  `presentarExplotacion(explotacionId, carga)`:
  - found → the código REGA (`tabular-nums`), with the explotación's name in the `title`;
  - `null` → "Sin asignar" (muted), even while the list is loading or has failed;
  - an id not in the complete list → "—" with the title "Esta explotación no está en tu lista de
    explotaciones.", plus `sr-only` text;
  - while loading → a muted 12px bar (`motion-safe:animate-pulse`) with `sr-only` "Cargando código
    REGA…";
  - on a loader error → "—" with a tooltip in each cell, plus an Alert above the table: "No se han
    podido cargar los códigos REGA", the `mensajeDeError(…, "listar-explotaciones")` text, and
    "Reintentar".
  - Raw ids never appear.
- **P1: crotales in the row.**
  - A new "Crotales" column shows the first **2** crotales (`MAX_CROTALES_EN_FILA`). Each shows
    `crotal` in `tabular-nums`, with the title "Indicado: …" when it differs from `crotalIndicado`,
    followed by its `BadgeResolucionCrotal`.
  - Any further crotales collapse into a muted "+N más", whose `title` lists each remaining one as
    "crotal · etiqueta".
  - An empty list shows "Sin crotales". A missing `crotales` field is tolerated.
  - It stays on one line, so the row doesn't get taller.
- **P2: states.**
  - The table has `aria-busy`.
  - First load and filter change show a "Cargando trámites…" row. When the page changes or the
    list reloads after an action, the old rows are dimmed (`opacity-60`) instead of flashing.
  - The empty text is contextual: "No hay trámites en «Rechazado»." when a filter is active.
  - The count uses the right singular or plural ("1 trámite" / "N trámites").
- **N1:** `cambiarFiltro` resets the page, `totalPaginas` and the shown page together.
- **N2:** if a successful reload returns a page index at or beyond `totalPages`, the page goes to
  `max(0, totalPages - 1)`. The stale page isn't shown in between, and `cargando` stays true.
- **M2 (Task 5 review):** a test opens the filter and asserts that its options are "Todos los
  estados" plus `Object.values(ESTADOS_TRAMITE).map(e => e.etiqueta)`.
- **H12 (375px):**
  - The header wraps (`flex-wrap`), and the filter is `w-full sm:w-56`.
  - The page column and the table container have `min-w-0`.
  - The cells keep `whitespace-nowrap`, so a wide row scrolls inside the existing `overflow-x-auto`
    table container.
  - This was **verified statically only**: there's no browser (H8), so it needs a real 375px check
    in Task 10 / Task 11.
- **Minor:**
  - The pager is in `<nav aria-label="Paginación">`.
  - The filter trigger's `aria-label` is "Filtrar por estado".
  - An empty tipo in the queue now reads "Sin determinar" (muted), close to the dialog's "Sin
    determinar todavía".

## Deferred

These are also listed in the critique, each with its reason:

- default filter "Pendiente de revisión" (a product question);
- a `motivoError` preview in `ERROR_OVZ` rows (a product question);
- everything inside the dialog (Task 9);
- the mobile nav bar (out of A2, H12). At 375px it can still make the *page* scroll sideways,
  independently of the queue;
- **`FacturacionPage.tsx:80`, whose `destructive` badge isn't an exact pair → Task 10**;
- `explotacionCodigoRega` in the list DTO (the backend mini-prompt, H7);
- keyboard accelerators (j/k).

## Loader design (decision 20)

- **`cargarTodasLasExplotaciones(signal?)`**, pure async and all-or-nothing:
  - Page 0 is requested with `size=500&sort=codigoRega,asc`. Its `totalElements` and `totalPages`
    become the reference.
  - Pages 1 to `totalPages - 1` are requested **sequentially**. This is simple, puts little load
    on the backend, and the first failure stops everything.
  - It throws `ErrorApi`, and never returns a list, if:
    - any page request fails (its own `ErrorApi`: red, servidor, …);
    - a later page reports a different `totalElements` or `totalPages`, meaning the list changed
      mid-load;
    - the number of items received differs from `totalElements`;
    - there are duplicate ids. Items shift between pages when something is inserted during the
      load; this can make the count match by accident, so ids are checked too.
  - The last three cases are thrown as `ErrorApi({ tipo: "desconocido" })`, so every caller uses
    the same `mensajeDeError(…, "listar-explotaciones")` path.
  - `totalPages = 0` gives `[]` after one request.
  - The loop is bounded by page 0's `totalPages`, so it can't run forever.
- **`useTodasLasExplotaciones()`** returns `{ carga, reintentar }`:
  - `carga` is a discriminated union: `cargando` | `error` (with `ErrorApi`) |
    `listo` (`explotaciones` plus a `porId` Map). Only `listo` carries a list, so a partial list
    can't exist even in the types.
  - It loads once on mount and is cached for the component's lifetime, which is the page's.
    There's a test that re-renders don't refetch.
  - `reintentar()` sets `cargando` and reloads everything.
  - An `AbortController` aborts in-flight requests on unmount, and a cancellation is never shown
    as an error.
  - The queue doesn't import, so no reload after an import is needed. Task 9's combobox can reuse
    the hook as is, or call `cargarTodasLasExplotaciones` directly.

## Tests first (evidence)

1. I wrote the 4 new or extended test files before any implementation, and ran
   `npx vitest run src/features/tramites src/features/explotaciones`. The result was **red**:
   - `todasLasExplotaciones.test.ts`, `useTodasLasExplotaciones.test.tsx` and
     `explotacionDeTramite.test.ts` failed at import, because the modules didn't exist yet;
   - `TramitesPage.test.tsx` had **11 failed** and 50 passed: the keyboard ×3, REGA ×4, crotales,
     N1, the contextual empty text and N2.
   - The M2 filter-options test passed straight away, as expected: Task 5 already derived the
     options from `ESTADOS_TRAMITE`, so this test is a regression guard, as the review asked.
2. After the implementation, the same run was green: 9 files, 75/75.
3. **Mutation check.** Each mutation was applied in place with a backup and then restored; the
   final run was 75/75.

   | Mutation | Result |
   |---|---|
   | Drop the N1 resets | 1 test fails |
   | Disable the N2 clamp | 1 test fails |
   | Disable the loader's count and duplicate check | 3 tests fail |

## Command results (from `frontend/`)

| Command | Result |
|---|---|
| `npm test` | **20 files, 193/193 passed**. Before Task 6 it was 17 files and 166; this adds 27 new tests. No MSW unhandled-request output. |
| `npm run build` | Green: `tsc -b` + `vite build`. Only the usual > 500 kB chunk notice (540.69 kB). |
| `npm run lint` | 0 errors, **3 warnings, all pre-existing** `only-export-components`: `AuthContext.tsx:231`, `badge.tsx:55`, `button.tsx:58`. |

## Notes for DESIGN.md (Task 11)

- **Tables:** replace "Rows that open the review dialog show a pointer cursor" with the current
  pattern:
  - the first cell holds a link-style button (green, `tabular-nums`, focus ring) with
    `aria-label` "Revisar trámite #N";
  - the whole row is still clickable with the mouse;
  - the row highlights when its button has keyboard focus.
- **Tables, states:**
  - loading: a centered muted "Cargando trámites…" row, or dimmed rows at 60% during a reload;
    the table has `aria-busy`;
  - empty: the text names the active filter;
  - tables scroll horizontally inside their container at narrow widths;
  - numerals in tabular data use `tabular-nums`.
- **Placeholder for a value that's still loading:** a 12px-tall `bg-muted` bar, `rounded-sm`, with
  `motion-safe:animate-pulse` and `sr-only` text.
- **Queue columns:**
  - Trámite / Tipo / Estado / Explotación (código REGA, name in the tooltip) / Crotales (2 plus
    "+N más", each with its resolution badge);
  - the "Sin asignar" / "—" conventions for the Explotación cell.
- **Layout:** the page header wraps, and the filter goes full-width below `sm`.

## Fixes after review (I1, M1–M4) + decision 28

Frontend only; `backend/` untouched. No dependencies added, no git operations. Every mutation below
was applied to the real file, the tests were run, and the file was restored from a backup copy in
the session scratchpad (each restore checked with `diff` = identical).

### Files changed
- `frontend/src/features/tramites/TramitesPage.tsx`: decision 28 (`FILTRO_INICIAL = "PENDIENTE_REVISION"`), M3 (`sr-only` text), M4 (reworded tooltip and `sr-only` text). The helper `textoCrotalResumido` is shared by the "+N más" `title` and its `sr-only` text.
- `frontend/src/features/tramites/TramitesPage.test.tsx`: new and adapted tests, listed below.
- `frontend/src/features/explotaciones/todasLasExplotaciones.test.ts`: I1.
- `frontend/src/features/explotaciones/useTodasLasExplotaciones.test.tsx`: M2.
- No production change in `todasLasExplotaciones.ts` or `useTodasLasExplotaciones.ts`. Those guards were already correct; only their tests were missing.

### I1: "total changed between pages" guard
- **Changed test.** It is renamed "si totalElements cambia entre páginas, es un error aunque lo recibido cuadre con la página 0". Page 0 is `{ids:[1,2], totalElements:4, totalPages:2}` and page 1 is `{ids:[3,4], totalElements:5, totalPages:2}`. The count (4) equals the reference total and no id repeats, so only the between-pages guard can catch it.
- **New test.** "si solo totalPages cambia entre páginas, también es un error" uses page 0 `{[1,2], 4, 2}` and page 1 `{[3,4], 4, 3}`.
- **Mutations** on `todasLasExplotaciones.ts:34`:

| Mutation | Result |
|---|---|
| Drop the `totalElements` half | 1 fail (the totalElements test) |
| Drop the `totalPages` half | 1 fail (the totalPages test) |
| Drop the whole guard (`if (false)`) | 2 fail |
| Restored | 8/8 pass |

### M1: N1 page reset
- **Adapted test.** The N1 test now clicks "Siguiente", checks it is on "Página 2 de 5", switches to "Rechazado" (a filter that also has 5 pages, so N2 can't hide the bug), and asserts:
  - every "Rechazado" request was `page=0` (`pedidasRechazado` equals `["0"]`);
  - "Página 1 de 5" is shown.
- **Mutation.** Removing `setNumeroPagina(0)` from `cambiarFiltro` makes 1 test fail (N1). The production code already had the reset; the test was green before and after, so the mutation is the evidence.

### M2: hook cancellation
- **Why the test spies on the loader.** MSW's `request.signal` does not reflect an XHR abort in jsdom: a first attempt that observed it stayed `aborted=false` even with the real hook. So the test file wraps the real loader, with `vi.mock("./todasLasExplotaciones")` and `vi.fn(real.cargarTodasLasExplotaciones)`, only to capture the `AbortSignal` the hook passes. The HTTP request (MSW) and httpClient's mapping of the cancel to `ErrorApi{cancelado}` stay real.
- **"desmontar con la petición en vuelo la cancela".** The response is held behind a gate. Before unmount the signal is not aborted; after unmount it is.
- **"Reintentar con la petición en vuelo cancela la anterior, y esa cancelación nunca es un error".**
  1. The first request is held.
  2. `reintentar()` is called. The first signal is aborted and the second one isn't.
  3. After the rejection settles (50 ms), the state is still `cargando`.
  4. The second request is released and the state reaches `listo`.
  5. The rendered-state history never contains `error`.

| Mutation | Result |
|---|---|
| Cleanup `abort()` removed | 2 fail |
| `if (aborted \|\| esCancelacion(err)) return` removed | 1 fails (the retry test), with "expected 'error' to be 'cargando'" |

### M3: information that lived only in `title`
Following the existing `sr-only` pattern of the "—" cells, `title` is kept for mouse users and nothing new is visible, so the row doesn't get heavier:
- **Explotación name.** A sibling `<span class="sr-only">, {nombre}</span>` follows the REGA code.
- **"Indicado: …".** An `sr-only` span with the same text follows the crotal, only when it differs from what was written.
- **"+N más".** An `sr-only` "Además: crotal · etiqueta; …" is added inside the `li`.

**Tests.** The REGA test now asserts that the row has an `sr-only` element with "Finca La Dehesa". The crotales test now asserts `sr-only` "Indicado: 789012" and "Además: ES000000000001 · No está en el inventario". Both were red before the change.

**Mutations.** Removing each `sr-only` separately makes 1 test fail each time: the REGA test, the crotales test, and the crotales test again.

Sighted touch users still don't get these without opening the trámite. The modal shows everything. A focusable tooltip can be decided in the Task 10 audit.

### M4: "not in your list"
- **Old text.** The tooltip said "Esta explotación no está en tu lista de explotaciones." and the `sr-only` text said "Explotación no encontrada en tu lista".
- **New tooltip.** "No aparece en la lista de explotaciones cargada al abrir la página. Si se importó después, recarga la página."
- **New `sr-only` text.** "Explotación no encontrada en la lista cargada. Si se importó después, recarga la página."
- There is no automatic retry.
- **Test.** The REGA test now looks the cell up by the new `title` and checks that the new `sr-only` text is there. It was red before the change.

### Decision 28: the queue opens on "Pendiente de revisión"
- **New test: "la cola se abre filtrada por «Pendiente de revisión»".**
  - There is exactly 1 initial request, with `estado=PENDIENTE_REVISION&page=0`.
  - The trigger (`combobox` "Filtrar por estado") shows "Pendiente de revisión".
  - The empty state reads "No hay trámites en «Pendiente de revisión».".
- **New test: "«Todos los estados» sigue disponible y, al elegirlo, no manda estado".**
  - The second request has no `estado` and has `page=0`.
  - The trigger shows "Todos los estados".
  - The empty state reads "No hay trámites que mostrar.".
- **Empty-state wording.** The default empty state reuses the existing named-filter wording, "No hay trámites en «Pendiente de revisión».". It reads correctly, so it wasn't changed.
- **Mutations.**

| Mutation | Result |
|---|---|
| `FILTRO_INICIAL = "TODOS"` | 5 fail: both new tests and the 3 adapted empty-state tests |
| Always send `filtroEstado` (so "Todos" would send `estado=TODOS`) | the "Todos" test fails |

- **TDD.** Both new tests were red before the change. The first run after writing the tests had 7 red: D28 ×2, M3 ×2, M4 in the REGA test, and adapted tests B and C/D, which awaited the new default.

### Adapted tests (none deleted)

| Test | Before | After |
|---|---|---|
| A. "la cola muestra la etiqueta del estado y del tipo, nunca el enum" | `findByText("Pendiente de revisión")` and `getByText("Ejecutado en OVZ.net")` searched the whole screen. That is ambiguous now that the filter trigger shows the same label. | Waits for row #1, then `within(table)` looks up both badges with the same class checks. The intent (badge label, never the enum) is unchanged. |
| B. "si falla la carga de la cola, lo dice y permite reintentar" | After "Reintentar", expected "No hay trámites que mostrar." | Expects "No hay trámites en «Pendiente de revisión».", the empty state of the default filter. |
| C. "las opciones del filtro salen de ESTADOS_TRAMITE" | Initial wait was `findByText("No hay trámites que mostrar.")`. | Initial wait is `findByText("No hay trámites en «Pendiente de revisión».")`. The option list assertion ("Todos los estados" plus every label) is unchanged. |
| D. "con un filtro activo y sin resultados, el vacío lo nombra" | Initial wait was "No hay trámites que mostrar.", then it chose "Rechazado". | Initial wait is the default empty state, then it chooses "Rechazado" and expects "No hay trámites en «Rechazado».". |
| E. "N2: si aprobar vacía la última página…" | `queryByText("No hay trámites que mostrar.")` absent. That became vacuous, because the default empty text is different. | `queryByText(/^No hay trámites/)` absent, which covers any empty-state wording. |
| F. N1 (also M1) | Changed filter from page 0 and checked that the old rows and total disappear. | Goes to page 2 first, the new filter has 5 pages, and the test also asserts `page=0` and "Página 1 de 5". See M1. |

The other tests use handlers that ignore `estado`, so they are valid with either default and were left unchanged.

### Verification (from `frontend/`)
1. `npm test`: **20 files, 198/198 passed**. That is 193 + 5 new: I1 ×1 (the other I1 case is a rewrite), M2 ×2, D28 ×2.
2. `npm run build`: green. Only the usual chunk > 500 kB notice.
3. `npm run lint`: exit 0, **3 warnings, all pre-existing** (`only-export-components` in `badge.tsx`, `button.tsx` and `AuthContext.tsx`).

## R2 de la re-revision (aplicado por el orquestador)

- `TramitesPage.tsx`: los textos `sr-only` llevan separador (`", Indicado: …"`, `". Además: …"`),
  para que el lector de pantalla no los pegue al texto visible. Tests actualizados. 198/198.

## Pendientes que pasan a la Task 10 (audit + polish)

- R1 (re-revision): ningun test comprueba que el `AbortSignal` llega a axios
  (`todasLasExplotaciones.ts:23`, `explotaciones/api.ts:18`).
- M3 (revision): en movil, el nombre de la explotacion, "Indicado" y los crotales de "+N mas" solo
  se ven abriendo el tramite (tooltip enfocable).
- `FacturacionPage.tsx:80`: badge `destructive` que no usa un par exacto de DESIGN.md.
