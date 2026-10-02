# A2 — Task 7: Ganaderos (list + detail + nav) — Implementer report

## Summary

This task builds `/ganaderos` (a paginated list, sortable only by nombre and NIF) and
`/ganaderos/:id` (the ganadero's record, with stacked sections per explotación). It also adds the
"Ganaderos" link to the nav, a link from the ganadero name in Explotaciones, a `BadgeRolContacto`
through `etiquetas.ts`, and two new error contexts. The work was test-first and is frontend only.

I built what the direction contract `.impeccable/surfaces/frontend-src-features-ganaderos.md`
describes. The Impeccable skill was not invoked, and no browser check was done (see Deviations).

## Files

**New (`frontend/src/features/ganaderos/`):**
- `types.ts`: `GanaderoResumen`, `GanaderoDetalle`, `ExplotacionDeGanadero` and
  `ContactoDeExplotacion`.
  - Checked against `GanaderoResumenResponse.java` and `GanaderoDetalleResponse.java`.
  - `nif` is typed `string | null`, because the DB column (V14) is nullable.
  - Nothing about OVZ is typed.
- `api.ts`: `listarGanaderos(page, size, { sort?: string[], signal? })` and
  `obtenerGanadero(id, signal?)`. `sort` goes through `URLSearchParams`, because axios would
  serialize an array as `sort[]=`.
- `ordenGanaderos.ts` + `.test.ts` (8 tests): the pure sort state (`ORDEN_INICIAL = null`,
  `siguienteOrden`, `parametrosSort`, `ariaSortDe`).
- `telefono.ts` + `.test.ts` (9 tests):
  - `formatearTelefono` groups only Spanish `+34` + 9 digits, as `+34 612 345 678`. Anything
    else is shown as is.
  - `hrefTelefono` returns `tel:` + the exact E.164 value.
- `estilos.ts`: `CLASE_ENLACE`, the text-link style shared by Ganaderos and Explotaciones.
  - Colour `text-primary`, with the underline on hover/focus-visible in `decoration-primary/40`
    at `underline-offset-4`.
  - Focus ring `ring-3 ring-ring/50`.
  - Everything comes from tokens.
- `GanaderosPage.tsx` + `.test.tsx` (16 tests).
- `GanaderoDetallePage.tsx` + `.test.tsx` (19 tests).
- `AnimalesDeExplotacionSlot.tsx`: the Task 8 seam. It returns `null` for now (see below).
- `navegacionGanaderos.test.tsx` (2 tests): the real app with its real routes and `AuthProvider`.

**Modified:**
- `features/tramites/etiquetas.ts`: `presentacionRolContacto(rol)`.
  - The label comes from `ROLES_CONTACTO`, so labels aren't duplicated.
  - The variant comes from a new `VARIANTES_ROL_CONTACTO` (`TITULAR` → `success`,
    `EMPLEADO` → `outline`). An unknown role is shown as is, in `outline`.
  - `ROLES_CONTACTO` keeps its shape, so its existing test is unchanged.
  - Tests: 2 in `etiquetas.test.ts`.
- `features/tramites/BadgesTramite.tsx`: `BadgeRolContacto`, with 2 tests in
  `BadgesTramite.test.tsx`.
- `shared/api/errores.ts`:
  - New contexts `"listar-ganaderos"` and `"detalle-ganadero"`. The detail context's 404 text is
    "Este ganadero no existe o no es de tu gestoría."
  - That text is exported as `TEXTO_GANADERO_NO_ENCONTRADO`.
  - Tests: 1 new test in `errores.test.ts`, and both contexts added to the exhaustive loop.
- `router.tsx`: the placeholder is replaced by `ganaderos` → `GanaderosPage` and
  `ganaderos/:id` → `GanaderoDetallePage`.
- `shared/layout/AppLayout.tsx`: `ENLACES` is now Trámites, Ganaderos, Explotaciones,
  Facturación. The comment explains why: the daily queue first, then whose it is (ganadero →
  explotaciones), then the account. NavLink without `end` marks Ganaderos active on
  `/ganaderos/5`.
- `features/explotaciones/ExplotacionesPage.tsx`: the ganadero name is a `<Link>` to
  `/ganaderos/{ganaderoId}` with `CLASE_ENLACE`. Nothing else changed.
- `features/explotaciones/ExplotacionesPage.test.tsx`:
  - The 2 existing tests are now mounted inside a memory router, because the page has a `<Link>`.
    Their assertions are unchanged.
  - 1 new test for the ganadero link.
- `shared/auth/sesion.test.tsx`: it used the placeholder text "Ganaderos (pendiente)" as a marker
  for "the authenticated route rendered".
  - A `beforeEach` now registers an empty `/ganaderos` page.
  - The marker is now the real `<h1>Ganaderos</h1>`, through `findByRole` / `queryByRole`.
  - No session logic or assertions changed.
- `shared/brand/marca.test.tsx`: an empty-page `/ganaderos` handler, because the route now makes a
  request.

**Not touched:** `backend/`, `public/`, `index.html`, `DESIGN.md`, `CLAUDE.md`, `PRODUCT.md`,
the plan, `.impeccable/`, `.agents/`, `.claude/skills/`, `TramiteReviewDialog`, `logo.jpg` and
`preview.png`. There are no new dependencies, no git operations and no hex values.

## How each contract block was realised

**THESIS (the record read top to bottom):** the detail is one vertical column.
1. The back link.
2. Name + NIF.
3. The explotaciones count.
4. The optional index.
5. One `<section>` per explotación.

There is no metric card on either page, no drawer and no master-detail.

**OWN-WORLD:**
- The canvas is unchanged. Each explotación is a single white container (`rounded-xl bg-card
  ring-1 ring-foreground/10`), the card recipe, with no card inside it.
- The inner blocks (header / contacts / footer) are separated by Borde Lino rules (`border-b`,
  `border-t`, `divide-y`).
- Verde Monte appears only on links (the ganadero name, `tel:`, the index, the back link) and the
  focus ring. The sort headers and the "Ver animales" button are ink, not green.
- Role badges are exact pairs through `BadgeRolContacto`: Titular = `success`, Empleado =
  `outline`.
- `tabular-nums` is on the REGA, NIF, phones, counts and pager.
- There is no kicker. "Contactos" is a 14px/500 muted `h3` in sentence case: a sub-heading, not an
  eyebrow.

**STORY:**
- *Sortable list → record:* the name is a real `<Link>` (keyboard, middle-click, new tab).
  - A click anywhere on the row navigates too, as a mouse convenience only. It ignores modifier
    keys, non-left buttons and active text selection, so ctrl/cmd-click is left to the link.
  - The link stops propagation, so there is one history entry. That is tested.
  - There are no nested interactive elements.
- *Identify by REGA:* the section's `h2` is `REGA · nombre`, with the REGA first.
- *See and call contacts:* `tel:` links with the exact E.164 href and a grouped display text, plus
  a decorative lucide `Phone` icon.
- *Animals only when needed:* a collapsed disclosure.

**FIRST VIEWPORT:**
- **Back link:** a small green "← Ganaderos", using lucide `ArrowLeft` (`aria-hidden`), with the
  text "Ganaderos".
- **Title row:** the `h1` holds the name at 20px/600. The NIF sits beside it, muted and tabular,
  with an sr-only "NIF " prefix; it shows "Sin NIF" when null.
- **Count:** the muted subtitle "N explotaciones", or "1 explotación".
- **Index:** shown with more than 3 explotaciones, as
  `<nav aria-label="Explotaciones de este ganadero">` with an inline wrapping list of
  `<a href="#explotacion-{id}">REGA · nombre</a>`.
  - On click it calls `preventDefault`, scrolls the section into view (`scroll-mt-6`), and focuses
    the section's `h2` (`tabIndex=-1`, themed focus-visible ring).
  - Keyboard users land on the heading.
- **Section:** a header with REGA + name, then the Contactos list (name · phone · badge,
  flex-wrap so it stacks at 375px). The footer holds the "Ver animales" ghost button.
  - It is a `<button aria-expanded aria-controls>`, with a chevron that rotates in 200ms and
    `motion-reduce:transition-none`.
  - It controls a `hidden` div.
- **Empty and not-found states:**
  - No contacts → "Sin contactos activos en esta explotación."
  - No explotaciones → "Este ganadero no tiene explotaciones."
  - 404 → `h1` "Ganadero no encontrado", the no-existence/other-gestoría text and a "Volver a
    Ganaderos" link. A non-numeric, zero, negative or decimal id makes no request.
- **Loading and errors:**
  - Loading is a `role="status"` skeleton (title bar, subtitle bar, one empty section frame,
    `motion-safe:animate-pulse`) with the sr-only text "Cargando ganadero…".
  - Any other error shows a destructive Alert with `mensajeDeError(..., "detalle-ganadero")` and
    "Reintentar".
  - Requests are cancelled with an AbortController on unmount or id change, and a cancellation is
    never shown.

**List page:**
- The title is "Ganaderos", with the muted count "N ganaderos" / "1 ganadero" underneath, or
  "Cargando…" / "—" as in the queue.
- There are 20 per page. The pager keeps the last known total when a page fails (tested).
- **Sorting:** only the Nombre and NIF `th`s hold a `<button>`, with `aria-sort` on the active
  column only and lucide `ArrowUp` / `ArrowDown` / `ArrowUpDown` icons (`aria-hidden`).
  - By default no `sort` is sent, and Nombre shows as ascending.
  - Clicking the active column toggles it; another column starts ascending.
  - Changing the sort resets to page 0 and clears the old rows.
- Explotaciones is right-aligned, tabular and not sortable.
- States:
  - Loading is the queue's pattern (a "Cargando ganaderos…" row, then `opacity-60` on reload,
    `aria-busy`).
  - Errors show an Alert with "Reintentar" (`"listar-ganaderos"`).
  - The empty state has the exact text plus an "Ir a Explotaciones" link.
- The table scrolls inside its container (`min-w-0` + the `Table`'s own `overflow-x-auto`), and
  the empty cell wraps (`whitespace-normal`).

**Task 8 seam:** `AnimalesDeExplotacionSlot({ explotacionId })` returns `null`. It is mounted
only while the panel is open, so Task 8's `AnimalesDeExplotacion` will fetch on expand, not on
page load. Task 8 replaces the import or the component, and nothing else in the page needs to
change.

## TDD evidence

- The tests for the pages, navigation, etiquetas, badges and errores were written first, and the
  red run was recorded before implementing:
  - `Test Files 6 failed | 2 passed (8)`, `Tests 7 failed | 69 passed`.
  - The two page files failed at import, because the pages didn't exist.
  - The nav tests failed on the placeholder.
  - `presentacionRolContacto`, `BadgeRolContacto` and the contexts were missing.
- The pure helpers (`telefono`, `ordenGanaderos`) had their tests written first, but I wrote the
  modules before the first run. There is no recorded red run for those two files.
- The Explotaciones ganadero-link test was written right after the one-line change, not before
  it.
- After implementation, `src/features` passed with 172 tests. The full suite then showed the 5
  `sesion.test.tsx` tests that relied on the placeholder (the red run for the placeholder
  removal); after the fixture update, everything is green.

## Commands (from `frontend/`)

- `npm test`: **25 files, 258 tests, all passed.** 60 are new: 54 in `features/ganaderos`, plus 2
  in etiquetas, 2 in badges, 1 in errores and 1 in Explotaciones. There are no MSW
  unhandled-request warnings.
- `npm run build`: OK (`tsc -b` clean; the >500 kB chunk notice was already there).
- `npm run lint`: only the 3 pre-existing `only-export-components` warnings (`badge.tsx`,
  `button.tsx`, `AuthContext.tsx`).

## Deviations

1. **`id` tie-breaker in `sort`.** The requests are `sort=nombre,desc&sort=id,desc` (and likewise
   for nif), not only `sort=nombre,desc`.
   - The reason: with duplicate names, or several null NIFs, a sort without a unique key is
     unstable across pages, so a ganadero could appear twice or never. CLAUDE.md flags this for
     client-chosen sorts.
   - `id` is in the backend whitelist.
   - The default (no `sort`) is unchanged; the backend's `nombre,id` already has the tie-breaker.
   - It is easy to revert in `parametrosSort` if Antonio prefers the literal form.
2. **Nav order.** It is now Trámites, Ganaderos, Explotaciones, Facturación, the order the brief
   suggested; before, Explotaciones came first.
3. **Two older tests (`sesion`, `marca`) were adjusted.** They used `/ganaderos` because it was a
   request-free placeholder. Only fixtures and markers changed; the behaviour under test did not.
4. **No real-browser or 375px visual check.** No browser tool is configured in this harness, as
   in Task 6. Responsiveness rests on the classes: flex-wrap rows, a table scroll container,
   `break-words` on titles and names. That should be covered in Task 10 or Task 11.
5. **The "Ver animales" label doesn't change when open.** `aria-expanded` and the chevron convey
   the state; a constant label is the recommended disclosure pattern.
6. **The index link doesn't update the URL hash.** It calls `preventDefault` to control focus and
   scroll deterministically. The `href` is still there for copy and middle-click semantics.

## Notes for DESIGN.md (Task 10/11)

- **Text links:** `CLASE_ENLACE` (`features/ganaderos/estilos.ts`) is the text-link recipe: Verde
  Monte, `hover:underline` with `decoration-primary/40` and `underline-offset-4`, and the standard
  focus ring. It is used by Ganaderos and the Explotaciones ganadero link. Consider promoting it to
  `shared/` if more screens adopt it.
- **Sortable table headers:** a button in the `th`, `aria-sort` on the active column only, and
  lucide arrows (ink when active, Gris Oliva when inactive).
- **Record sections:** one white card-recipe container per explotación, with inner blocks divided
  by Borde Lino and no nested cards. The `h2` is `REGA · nombre`.
- **Role badges:** Titular = `success`, Empleado = `outline`, through `presentacionRolContacto` /
  `BadgeRolContacto`.
- **Disclosures:** a ghost `sm` button with a rotating chevron (200ms, reduced-motion safe).
- **Loading skeletons:** the detail uses `bg-muted motion-safe:animate-pulse` bars, the same as the
  queue's REGA placeholder.
- **Nav:** Ganaderos is now in the nav.
- **Metric card:** the Label/eyebrow rule and the metric card in DESIGN.md describe the existing
  Explotaciones card only. Ganaderos deliberately doesn't use them (the craft-floor refuses the
  hero-metric and the kicker).

## Fixes after review

Two reviews came back: the code review (`a2-task7-review.md`) and the Impeccable finish review
(`.impeccable/review/ganaderos/finish-review.md`, disposition fix). I wrote the tests first and
recorded a red run before the code changes: `Tests 12 failed | 54 passed (66)`. The m1 test
and the slow-response test for I1 passed from the start, because they guard behaviour that was
already correct; the mutations below show what they catch. After the fixes, `src/features/ganaderos`
passes 66/66.

### Code review

- **I1 (the previous ganadero rendered under the new id).**
  - **Change:** in `GanaderoDetallePage.tsx` the load state is `Carga = Vista & { id }`, and
    `vista` is only the loaded state when `carga.id === id`; otherwise it is "cargando". The
    `esCancelacion` guard stays.
  - **Tests** (`GanaderoDetallePage.test.tsx`, "cambio de ganadero"):
    - Going `/1` → `/2` with a gated response for 2: a `useLayoutEffect` probe records every
      commit before passive effects run and never sees "Ganadero 1" under `/ganaderos/2`.
    - A slow response for 1 that lands after navigating to 2 is never shown, as data or as an
      alert.
    - Under StrictMode the first request is aborted, and it never becomes an alert.
  - **Mutations:**
    - Without the id check, the probe test fails.
    - Without `esCancelacion`, the StrictMode test fails.
- **m1 (the Task 8 slot).**
  - **Test:** `GanaderoDetallePage.animales.test.tsx` mocks `AnimalesDeExplotacionSlot` with a
    component that records its mounts. Until "Ver animales" is clicked, nothing mounts and the
    only request is `/ganaderos/7` (checked with `server.events` `request:start`). After the
    click, only that section's slot mounts, and collapsing the panel unmounts it.
  - **Mutation:** always mounting the slot makes it fail.
- **m2 (index links with modifiers).**
  - **Change:** `alPulsar` returns early on `button !== 0` or ctrl, meta, shift or alt, so it
    doesn't call `preventDefault` and the browser decides. `preventDefault` only runs when the
    section exists.
  - **Tests:** `it.each` over ctrl, cmd, shift and the middle button. `fireEvent.click` isn't
    cancelled and the section `h2` isn't focused. A plain click is cancelled and focuses the
    section.
  - **Mutation:** removing the guard fails 4 tests.
- **m3 (landing on `#explotacion-N`).**
  - **Change:** once the data is loaded, `Ficha` reads `useLocation().hash` a single time (with a
    `useRef` flag). A match scrolls to that section and focuses its `h2` through the shared
    `irASeccion`. An unknown hash does nothing.
  - **Tests:** `/ganaderos/7#explotacion-3` focuses section 3's `h2`, and `#explotacion-99`
    leaves focus on `body`.
  - **Mutation:** removing the hash handling fails the test.

### Impeccable finish review

- **(2) Mobile list.**
  - **Change:** the name cell is `whitespace-normal break-words`. The Explotaciones header shows
    `<span className="sm:hidden" aria-hidden="true">Expl.</span><span className="max-sm:sr-only">Explotaciones</span>`,
    exactly as the review gives it.
  - **Test:** the sort test now checks the accessible names (`toHaveAccessibleName`), plus
    `aria-hidden` on "Expl." and `max-sm:sr-only`; it used to check `textContent`, which is now
    "Expl.Explotaciones". A new test checks the wrapping classes on the name cell.
- **(3) Contact rows.** The row is now a grid, with the review's classes:
  - the `li` is
    `grid grid-cols-[minmax(0,1fr)_auto] items-center gap-x-4 gap-y-1 py-2 text-sm sm:grid-cols-[minmax(0,18rem)_11rem_auto] sm:justify-start`;
  - the name has no `flex-1 basis-40`, and the phone gets `justify-self-start`;
  - the badge is wrapped in `col-span-2 justify-self-start sm:col-span-1`.

  The existing contact tests still pass unchanged. This is layout only; there are no class
  assertions.
- **(4)** The disclosure button is `-mx-2.5`.
- **(5)** The footer is `rounded-b-xl border-t bg-muted/50 px-4 py-2`.
- **(6) Empty state with 0 explotaciones.**
  - **Change:** it reads "Este ganadero no tiene explotaciones. Se añaden al importar el Excel."
    plus an "Ir a Explotaciones" link (`mt-1 inline-block` + `CLASE_ENLACE`), mirroring the list's
    empty state.
  - **Test:** the updated test checks the text and the link's `href`.
- **(7) 404 with a single back link.**
  - **Change:** the top "← Ganaderos" link only renders when the state isn't "no-encontrado".
  - **Test:** the 404 test checks there is exactly one link, "Volver a Ganaderos", and no
    "Ganaderos" link. The invalid-id tests go through the same state.
- **(8) List loading as a skeleton.**
  - **Change:** 5 rows of three bars (`h-4 w-full max-w-40 rounded-sm bg-muted motion-safe:animate-pulse`).
    The count bar is `ml-auto h-4 w-6 …`: I wrote it as its own full class string instead of
    appending `w-6` to the shared one, because without `cn()` a `max-w-40` would compete with it.
    The first cell holds `<span className="sr-only" role="status">Cargando ganaderos…</span>`.
  - **Test:** the loading test was adapted, not deleted. It checks `role="status"` with the text,
    that the text is `sr-only`, `aria-busy`, 5 body rows, 3 pulsing bars in a row, and that the
    skeleton goes away once the data arrives.
- **(1)** Nothing to do on my side: it concerned the design contract, which the orchestrator already
  fixed.

None of the review's classes conflicted with the tests.

### Commands (from `frontend/`)

- `npm test`: **26 files, 270 tests, all passed.** That is 12 more than before: 1 in the list
  test, 10 in the detail test (3 for I1, 4 + 1 for m2, 2 for m3) and 1 in the new
  `GanaderoDetallePage.animales.test.tsx`.
- `npm run build`: OK (only the existing chunk-size notice).
- `npm run lint`: only the 3 existing warnings.

Still no real-browser check at 390 or 1440 px, because there is no browser tool here.
