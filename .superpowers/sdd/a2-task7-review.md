# A2 — Task 7: Ganaderos — Independent code review

Reviewer: independent code-review subagent (correctness, API contract, a11y semantics, tests, scope).
The visual design is judged separately.

## VERDICT

**APPROVE WITH ONE FIX (I1).** The contract, the tests and the scope are sound. One check from the
brief fails: changing the route from `/ganaderos/1` to `/ganaderos/2` commits one render that shows
ganadero 1 under the URL of ganadero 2. No in-app path reaches that transition today, and it stays
inside one gestoría, so this is not a security leak. The fix and its regression test are small, and
they should land before Task 8 (or any cross-link between ganaderos) makes the transition reachable.
The three Minor findings are optional.

## Commands (from `frontend/`, run by the reviewer)

| Command | Result |
|---|---|
| `npm test` | **25 files, 258 tests, all passed** (15.3 s) |
| `npm run build` | OK. `tsc -b` is clean; only the existing >500 kB chunk notice |
| `npm run lint` | Only the 3 existing `only-export-components` warnings (`button.tsx`, `badge.tsx`, `AuthContext.tsx`) |

These match the implementer's report.

## Mutation testing

The mutations ran in a scratch copy of `frontend/` (junctioned `node_modules`). The copy was
restored and diffed identical after each run, then deleted.

| # | Mutation | Result |
|---|---|---|
| M1 | `parametrosSort` without the `id` tie-breaker | caught (4 failures) |
| M2 | `hrefTelefono` returns the formatted text | caught (3) |
| M2b | `hrefTelefono` drops the `+` | caught (3) |
| M3 | `formatearTelefono` without grouping | caught (3) |
| M4 | `ariaSortDe` with inverted direction | caught (5) |
| M5 | `api.ts` passes `sort` as an array in `params` (axios sends `sort[]=`) | caught (3) |
| M6 | link without `stopPropagation` | survives. Not a gap: the row already ignores `defaultPrevented`, and Link calls `preventDefault` |
| M6b | no `stopPropagation` **and** no `defaultPrevented` guard | caught (the one-history-entry test) |
| M7 | slot mounted even when the panel is closed | **survives** (see m1) |
| M8 | Explotaciones ganadero name as plain text, not a `<Link>` | caught |
| M8b | Explotaciones link uses `explotacion.id` instead of `ganaderoId` | caught |
| M9 | "Ganaderos" removed from `ENLACES` | caught |
| M10 | `esCancelacion` guard removed in the detail page | **survives** (covered by the I1 test) |
| M11 | the index link doesn't focus the section `h2` | caught |
| M12 | `TITULAR` variant set to `outline` | caught (3) |

For the gaps the report admits:
- The phone and sort helpers have no recorded red, and the Explotaciones link test was written after
  the change. M1–M5, M8 and M8b show that these tests do catch the regressions they claim to.
- The recorded red run for the pages, nav, etiquetas, badges and errores is credible: the page files
  failed at import, and the nav test failed on the placeholder.

## Verified, no finding

**API contract**
- `types.ts` matches `GanaderoResumenResponse`, `GanaderoDetalleResponse`, `ExplotacionDeGanadero`
  and `ContactoDeExplotacion` field by field.
  - `nif: string | null` is correct: nothing in the DTO or DB forces it to be present.
  - `rol: RolContacto | (string & {})` is a safe widening.
  - The explotación `nombre` and the contacto `nombre` are `NOT NULL` in V4 and V6.
- Sorting:
  - Only `nombre` and `nif` are offered in the UI.
  - `GanaderoController.CAMPOS_ORDENACION = {nombre, nif, id}` accepts the `id` tie-breaker.
  - Spring reads repeated `sort` params as a multi-key `Sort`.
- Axios serialisation:
  - `URLSearchParams` in `params` is sent verbatim (`buildURL`: `isURLSearchParams(params) ?
    params.toString()`, axios 1.18.1), as `sort=nombre%2Cdesc&sort=id%2Cdesc`.
  - Spring decodes `%2C`.
  - The tests read `searchParams.getAll("sort")` from the real request URL. They would return `[]` for
    `sort[]=`, which M5 proves.
- The default request sends no `sort`, `size=20` and `page=0` (tested).
- A `404` without a body becomes the "no-encontrado" state.
- `abc`, `7x`, `0`, `-3` and `1.5` make no request (tested). Unsafe integers are also rejected.
- Nothing from OVZ is typed, and extra `ovz*` fields in a response are never rendered (tested on both
  pages).

**Tenant and security**
- One copy covers both "other gestoría" and "nonexistent": "Este ganadero no existe o no es de tu
  gestoría."
- `detalle-ganadero` has no `porStatus` that could tell them apart.
- On the list page, a stale response is dropped by the `cancelado` flag.
- On the detail page, the request is aborted on id change or unmount, and a cancellation is never
  shown.

**Accessibility**
- `aria-sort`:
  - It is only on the active `th`, as `ascending` or `descending`.
  - With no sort sent, Nombre shows `ascending`, which matches the backend default `nombre,id`.
  - The icons are `aria-hidden`.
  - The button's name is just the column label.
- Row click:
  - There is no nested interactive element; the name `<Link>` is the only one.
  - The row handler ignores modifier keys, non-left buttons, `defaultPrevented` and a text selection.
  - Middle-click fires `auxclick` and never reaches it.
  - There is one history entry (tested by `navigate(-1)` landing back on the list).
- Index:
  - `href="#explotacion-{id}"` matches the `section` id (tested).
  - A click focuses the `h2` (`tabIndex=-1`, `focus-visible` ring), which M11 tests.
- Disclosure:
  - The button has `aria-expanded` (tested both ways) and `aria-controls`.
  - `aria-controls` points at a panel `div` that is always in the DOM (`hidden` when closed), and
    each section has its own id (tested).
- `tel:` uses the exact E.164 value, whitespace-stripped (tested with `+34…` and `+44…`).
- The nav is ordered Trámites, Ganaderos, Explotaciones, Facturación. "Ganaderos" has
  `aria-current="page"` on `/ganaderos/5`, because it is a NavLink without `end` (tested in the real
  app).

**Labels**
- `presentacionRolContacto` takes the label only from `ROLES_CONTACTO` through
  `etiquetaRolContacto`. There is no duplicate "Titular" or "Empleado" string outside `etiquetas.ts`
  (grep).
- An unknown role is shown as is, in `outline`.
- The lookup goes through `hasOwnProperty`, so `"toString"` is safe.
- All of this is tested.

**Task 8 seam**
- `AnimalesDeExplotacionSlot` is mounted only when `animalesAbiertos` is true, inside the
  `aria-controls` panel.
- Task 8 only has to replace the component. The page needs no other change.

**States**
- Detail page:
  - The loading state is a `role="status"` skeleton with sr-only text, and it shows no data.
  - A non-404 error shows an Alert with "Reintentar", which re-fetches.
  - "0 explotaciones" and a section with no contacts both have a text.
- List page:
  - The loading row and `aria-busy` are there.
  - An error shows an Alert with "Reintentar".
  - The empty state has its text and an "Ir a Explotaciones" link.
- The pager keeps the last known `totalPaginas` when a page fails, the same as `TramitesPage` and
  `ExplotacionesPage` (tested).
- Changing the sort goes back to page 0 and clears the rows (tested).

**Older tests**
- In `sesion.test.tsx`, only the fixture changed (an empty `/ganaderos` page) and the marker, from
  the placeholder text to the real `h1`. Every session assertion is unchanged.
- `marca.test.tsx` only gained the `/ganaderos` handler.

**Scope**
- Everything modified after the Task 6 review is under `frontend/src`.
- `backend/`, `public/`, `index.html`, `package.json`, `package-lock.json`, `DESIGN.md`,
  `CLAUDE.md`, `PRODUCT.md` and `TramiteReviewDialog.tsx` all have mtimes from before Task 7.
  - `TramitesPage(.test).tsx` changed at 23:29, which matches the Task 6 fix round, not this task.
  - `.impeccable/review/ganaderos/*` and the surface file changed during this review. That is the
    orchestrator's craft/finish pass (decision 27), not the implementer.
- There are no new dependencies and no hex literals in the touched files.
- `git diff --cached` is empty.

## Critical

None.

## Important

**I1. A route change between ganaderos commits the previous ganadero's record under the new URL.**

- **Where:** `frontend/src/features/ganaderos/GanaderoDetallePage.tsx:47-67`.
- **What happens:** React Router keeps the same `GanaderoDetallePage` instance when `:id` changes.
  1. On the first render with the new `id`, `carga` still holds `{estado: "listo", ganadero: 1}`.
  2. `vista` is `carga`, so that render commits the ganadero 1 record under `/ganaderos/2`.
  3. Only the passive effect then resets it to "cargando".
- **Evidence:** in the scratch copy, a `useLayoutEffect` probe recorded
  `["/ganaderos/2 => Ganadero 1"]` for `router.navigate("/ganaderos/2")`.
  - For a non-discrete navigation (popstate, programmatic), React can paint before it flushes the
    passive effects, so this is a visible flash.
  - The same happens via an invalid id: `/1` → `/abc` (the effect returns early, so the stale `listo`
    stays) → `/2`.
  - M10 shows the related gap: without the `esCancelacion` guard, the aborted request of the previous
    id would overwrite the new load with an error, and no test would notice.
- **Why it is Important and not Critical:**
  - It stays within the same gestoría.
  - No in-app path goes from one detail to another today; the list sits in between and remounts the
    page.
  - The brief required it explicitly, and Task 8 or a future cross-link makes it reachable.
- **Fix (either one):**
  - Remount per id: a tiny route wrapper, `const { id } = useParams(); return <GanaderoDetallePage
    key={id} />;`.
  - Or tag the state: `{estado: "listo", id, ganadero}`, and derive `vista` as "cargando" when
    `carga.id !== id`. Do the same for "error".
- **Test to add:** navigate `/ganaderos/1` → `/ganaderos/2` with a gated response for 2, and assert
  that "Ganadero 1" is never in the DOM once the location is `/ganaderos/2`. The strict form is a
  `useLayoutEffect` probe as above; alternatively, check synchronously right after `act(navigate)`
  while 2 is still pending. Add one more test: a slow response for 1 resolving after navigating to 2
  never shows 1.

## Minor

**m1. Nothing tests that the Task 8 slot mounts only while the panel is open.**

- **Where:** `GanaderoDetallePage.tsx:245-248`.
- **The gap:** mutation M7 (always mount the slot) leaves every test green, because the slot renders
  `null`.
- **Impact:** if Task 8 drops the `animalesAbiertos &&`, every section would fetch its animals when
  the record loads.
- **Fix:** in Task 8, assert that no `GET /explotaciones/{id}/animales` request is made until "Ver
  animales" is clicked, and that exactly one is made for that explotación after the click.

**m2. The index links block ctrl/cmd-click to a new tab.**

- **Where:** `GanaderoDetallePage.tsx:158-166`.
- **What happens:** `irASeccion` always calls `preventDefault()`, so ctrl+click or shift+click only
  scrolls in place. Middle-click works, because it goes through `auxclick`.
- **Report note:** the report says the `href` is kept "for copy and middle-click semantics".
- **Fix:** return early on `evento.button !== 0 || evento.ctrlKey || evento.metaKey ||
  evento.shiftKey || evento.altKey`, the same guard as the list row.

**m3. A direct hash URL doesn't land on its section.**

- **Where:** `GanaderoDetallePage.tsx`.
- **What happens:** opening `/ganaderos/7#explotacion-3` (a copied index link, or a middle-click from
  m2) doesn't scroll to the section. The content arrives asynchronously, after the browser has already
  tried to scroll to the hash.
- **Fix (optional):** after the "listo" load, if `location.hash` matches a section, scroll to it and
  focus its `h2`. Or leave it as a Task 10 note.

# Re-review (fixes)

## VERDICT

**APPROVE.** I1, m1, m2 and m3 are fixed in the code, and each fix has a test that fails when the fix
is removed. The design fixes I was asked to check for correctness are right, and no test was deleted.
There are no regressions and nothing is out of scope. There are no new findings, only two optional
nits.

## Commands (from `frontend/`, run by the reviewer)

| Command | Result |
|---|---|
| `npm test` | **26 files, 270 tests, all passed.** That is 12 more than the 258 before, matching the report |
| `npm run build` | OK (`tsc -b` clean; only the existing chunk-size notice) |
| `npm run lint` | Only the 3 existing `only-export-components` warnings |

## Mutations of the fixes

These ran in a scratch copy, which was restored and diffed identical after each run, then deleted.
All results are over the 66 tests in `src/features/ganaderos`.

| Mutation | Result |
|---|---|
| I1: `vista` without the `carga.id === id` check | caught (the per-render probe test) |
| I1: `esCancelacion` guard removed | caught (the StrictMode test) |
| m1: slot always mounted | caught (`GanaderoDetallePage.animales.test.tsx`) |
| m2: modifier-key guard removed | caught (3: ctrl, cmd, shift; the middle button is covered by the `button !== 0` check) |
| m3: hash handling removed | caught |
| (7): the top "← Ganaderos" link also shown on a 404 | caught |

## Findings, item by item

**I1 — fixed** (`GanaderoDetallePage.tsx:30-32, 51, 76-82`)
- **Change:** the load result now carries its `id` (`Carga = Vista & { id }`), and `vista` is
  "cargando" until `carga.id === id`.
  - This also covers `/1` → `/abc` → `/2`: an invalid id is "no-encontrado" directly, and the stale
    `carga.id` (1) doesn't match 2.
  - A retry keeps the same id and resets to "cargando" inside the effect, which is correct.
- **Tests** ("cambio de ganadero …"):
  - A `useLayoutEffect` probe records every commit before the passive effects run, and never sees
    "Ganadero 1" under `/ganaderos/2`.
  - A slow response for 1 that arrives after navigating to 2 is never shown, as data or as an alert.
  - Under StrictMode, the aborted first request never becomes an alert.

**m1 — fixed.** The slot is mocked with a component that records its mounts.
- Until "Ver animales" is clicked, nothing mounts, and the only request is `/ganaderos/7` (checked
  with `server.events` `request:start`).
- After the click, only that section's slot mounts, and collapsing the panel unmounts it.
- The MSW listener is removed in a `finally`.

**m2 — fixed** (`GanaderoDetallePage.tsx:203-208`)
- The guard ignores non-left buttons and ctrl, meta, shift or alt.
- `preventDefault` now runs only when the section exists, so a missing section falls back to the
  browser's own hash navigation.
- **Tests:** ctrl, cmd, shift and the middle button leave the event uncancelled and don't move focus.
  A plain click is cancelled and focuses the heading.

**m3 — fixed** (`GanaderoDetallePage.tsx:141-151`)
- After the load, a matching `#explotacion-N` scrolls to that section and focuses its `h2` once.
  - This goes through the shared `irASeccion`.
  - The `useRef` flag prevents a double run under StrictMode.
  - `Ficha` remounts per ganadero (the "cargando" state sits in between), so the flag resets
    correctly.
- An unknown hash leaves focus on `body`.
- Both cases are tested.

**Design fixes (correctness only)**
- **Skeletons have an sr-only status:**
  - The list skeleton has a single `role="status"` sr-only span, "Cargando ganaderos…", in the first
    row. The bars are `aria-hidden`.
  - The detail skeleton (`FichaCargando`) is still `role="status"` with sr-only text.
- **Adapted tests keep their intent:**
  - The list loading test still checks the loading text (now as `status` + `sr-only`), the
    "Cargando…" subtitle, `aria-busy` and that the skeleton goes away. It now also checks the 5 rows
    and 3 bars.
  - The header test moved from `textContent` to `toHaveAccessibleName`. It still checks that only
    Nombre and NIF have buttons and the default `aria-sort`.
  - "Expl." is `aria-hidden` and "Explotaciones" is `max-sm:sr-only`, so the column's accessible
    name is always "Explotaciones".
  - No test was deleted: the count grew by exactly the 12 new tests.
- **404 has a single back link:** the top link is hidden in "no-encontrado". The test asserts exactly
  one link, "Volver a Ganaderos", and the mutation proves it. The error and loading states keep the
  top link, which is still tested.
- **Empty-state link:** "Ir a Explotaciones" with `href="/explotaciones"` (tested).

**Scope**
- Only 5 files under `frontend/src/features/ganaderos` changed after my first review.
- Nothing in `backend/`, `public/`, `index.html`, `package.json`, `DESIGN.md`, `CLAUDE.md`,
  `PRODUCT.md`, `docs/` or `TramiteReviewDialog.tsx` changed.
- Nothing is staged.

## Nits (optional, not blocking)

- **n1:** the m2 `it.each` doesn't include `altKey`. The guard handles it; adding a case would cover
  it.
- **n2:** the detail's empty-state test checks the link's `href`, but doesn't click through the way
  the list's empty-state test does. Fine as it is.
