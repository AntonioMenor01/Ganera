# A2 Task 10 — Polish from the audit: implementer report

This task is frontend only. `backend/`, `public/`, `index.html`, `logo.jpg`, `preview.png`, the docs and the plan were not touched. No dependencies were added, no git operations that change state were run, and nothing is staged. No `.bak`/`.orig` files are left (mutation backups went to `$TMP` and were deleted after each restore).

## Files

**New**
- `frontend/src/shared/ui/enlace.ts`: `CLASE_ENLACE`, moved from `features/ganaderos/estilos.ts`.
- `frontend/src/features/auth/RegistroPage.test.tsx` (2 tests).
- `frontend/src/features/facturacion/FacturacionPage.test.tsx` (10 tests).
- `frontend/src/features/explotaciones/api.test.ts` (1 test).

**Deleted**
- `frontend/src/features/ganaderos/estilos.ts`.

**Modified (production)**
- `features/auth/LoginPage.tsx`, `features/auth/RegistroPage.tsx`
- `features/explotaciones/ExplotacionesPage.tsx`, `AnimalesDeExplotacion.tsx`, `ImportarExcelSection.tsx`
- `features/ganaderos/GanaderosPage.tsx`, `GanaderoDetallePage.tsx` (import path and `text-sm` only)
- `features/facturacion/FacturacionPage.tsx`
- `features/tramites/TramitesPage.tsx`, `AvisosRevision.tsx` (import path only), `ListaCrotales.tsx`, `CampoExplotacion.tsx` (comment only)
- `src/index.css`

**Modified (tests)**
- `features/auth/LoginPage.test.tsx` (+2)
- `features/explotaciones/AnimalesDeExplotacion.test.tsx` (+1), `ImportarExcelSection.test.tsx` (+5), `ExplotacionesPage.animales.test.tsx` (+3), `todasLasExplotaciones.test.ts` (+1)
- `features/ganaderos/GanaderosPage.test.tsx` (+1, 1 adapted), `GanaderoDetallePage.test.tsx` (+2)
- `features/tramites/TramitesPage.test.tsx` (+4, 2 adapted), `TramiteReviewDialog.test.tsx` (+1)

## Item → change → test

| # | Change | Test |
|---|--------|------|
| 1 | `break-words` → `wrap-anywhere` on Explotaciones Nombre cell, the name line under REGA, the Ganadero cell, and the Ganaderos Nombre cell. `whitespace-normal` kept. | Layout, no new test. The existing Ganaderos test that asserted `break-words` was adapted to `wrap-anywhere` (an intended change). |
| 2 | Login and Registro: the outer `div` is now `<main>`; "Ganera" is `<h1 data-slot="card-title" className="font-heading text-base leading-snug font-medium">` (the same classes as `CardTitle`). `CardTitle` itself is unchanged. | `getByRole("main")` and `getByRole("heading", { level: 1, name: "Ganera" })` on both screens. |
| 3 | Always-mounted `role="status"`, outside any `aria-busy` element, whose text changes:<br>• **Animales**: the root is now a wrapper holding the sr-only status and the `aria-busy` content. The `Esqueleto` status and the conditional span are removed, and the skeleton is `aria-hidden`.<br>• **Ganaderos**: the status in skeleton row 0 is removed; an sr-only `<p role=status>` sits before the `<Table aria-busy>`.<br>• **Trámites**: the "Cargando trámites…" row is now a 5×5 skeleton (`aria-hidden` bars), with an sr-only status before the table.<br>• **Facturación**: "Cargando…" is a `<p role=status>`, visible while loading and empty afterwards.<br>• **Import**: an sr-only status reads "Importando el Excel…" while uploading, then the summary sentence, and is empty otherwise (including on error, where the `Alert` speaks). | For each component: the same node exists from the first render and its text changes (`toBe(estado)`, then `toBeEmptyDOMElement`). For the lists: `estado.closest("[aria-busy]")` is null. Animales also checks a page change (back to "Cargando animales…", then empty). Import: the full sentence; singular "1 fila con error" and "1 creada / 1 actualizada"; plural; no error clause with 0 errors; empty status on failure. Trámites: 5 skeleton rows × 5 `aria-hidden` bars, and no "Cargando trámites…" text inside the table. |
| 4 | `CeldaCrotales`: "+N más" is a `<button aria-expanded>` with `stopPropagation`, in muted text with the ring and underline on hover. Expanded, it renders every crotal through the same `ItemCrotal` markup (crotal, sr-only "Indicado", badge), the `ul` gets `flex-wrap`, and the button reads "Ver menos". The `title` and the sr-only "Además: …" are gone. State lives per row (rows are keyed by id, so a page change resets it). | Button "+2 más" with `aria-expanded="false"` and the ring class. A click shows the hidden crotales with the same markup (title + sr-only Indicado + badge, 5 `li`), with no dialog, then "Ver menos" with `aria-expanded="true"`, then a second click collapses. Enter does the same, with no dialog. The existing "crotales visibles" test was adapted: it now reads the button and asserts that the `title` and "Además" text are gone. |
| 5 | Facturación: `VARIANTE_ESTADO` maps ACTIVA/TRIAL → `success`, IMPAGO_GRACIA → `warning`, TRIAL_EXPIRADO_SIN_PAGO/SUSPENDIDA/CANCELADA → `danger`, anything else → `outline`. The in-card `Alert` is replaced by `<p className="text-sm">` with the same sentence. | `it.each` over the 6 states checks the variant class (`bg-success`/`bg-warning`/`bg-danger`) and that `bg-secondary`/`bg-destructive/10` are absent. An unknown state gives `border-border` (outline). A blocking state shows the `<p>` with no `role=alert` in the card and no "No puedes aprobar…" title. An active state doesn't show the line. |
| 6 | `CLASE_ENLACE` moved to `shared/ui/enlace.ts`. All 4 importers were updated (Explotaciones, Ganaderos, GanaderoDetalle, AvisosRevision; grep found no others). It is applied to Login "Regístrate" and Registro "Inicia sesión" (`cn("font-medium", CLASE_ENLACE)`), and to the queue `#id` button (`cn("-mx-1 px-1 font-medium tabular-nums", CLASE_ENLACE)`). | Login and Registro links have `focus-visible:ring-3`. |
| 7 | Explotaciones pagination is now `<nav aria-label="Paginación" className="flex items-center justify-between gap-3">` with a `tabular-nums` counter. `tabular-nums` is on the REGA code (desktop cell and mobile line). The sr-only "Animales" header has `id="explotaciones-col-animales"`, and the panel cell has `headers` pointing to it. | The nav is present with 2 pages and its counter is tabular. The panel `td` `headers` resolves to the `TH` "Animales". The REGA code is tabular. |
| 8 | `text-sm` on the subtitles of the queue, Ganaderos, Facturación, the Ganadero detail count and the not-found text. | None (as briefed). |
| 9 | `ListaCrotales`: `sm:grid-cols-[minmax(0,1fr)_minmax(11rem,auto)_auto]`. | None. |
| 10 | `CampoExplotacion`: one comment line naming `components/ui/input.tsx` as the source of the focus classes. | None. |
| 11 | `index.css` `@layer base`: `::selection` uses `color-mix(in oklab, var(--primary) 18%, transparent)`, and `input, textarea` get `caret-color: var(--primary)`. | None. |
| 12 | The visible import line is now "1 fila con error:" / "N filas con error:". | Both are tested. |
| 13 | Test gaps:<br>• `estado-cambiado` is the neutral alert (`data-aviso`, `text-card-foreground`, not `text-destructive`);<br>• the `AbortSignal` reaches `httpClient.get` (a spy on the real client) on every page of `cargarTodasLasExplotaciones` and in `listarAnimalesDeExplotacion`;<br>• Alt-click on an index link: the event isn't cancelled and focus doesn't move (added to the existing `it.each`);<br>• a real `userEvent.click` on "Ir a Explotaciones" lands on `/explotaciones` (memory router). | All pass on the current code. No production bug was exposed. |

## Red-first evidence

- Auth (items 2 and 6): the 4 new tests failed before the change (`Test Files 2 failed | 1 passed`, `Tests 4 failed | 32 passed`).
- Ganaderos `wrap-anywhere` (adapted test): 1 failed / 16 passed before the class change.
- Ganaderos status: `expected <table …> to be null` (the old status was inside `aria-busy`). 1 failed / 17 passed.
- Animales status: it failed on `toHaveClass("sr-only")` (the old status was the `Esqueleto` wrapper). 1 failed / 14 passed.
- TramitesPage: 6 failed / 18 passed before the change. These were the 2 adapted tests, the 3 disclosure tests and the skeleton/status test.
- Facturación: 9 failed / 1 passed. The one that passed is "can approve → no line", which is true on the old code too.
- Import: 5 failed / 2 passed.
- Explotaciones table/pagination: 3 failed / 4 passed.
- The item 13 tests cover existing behaviour, so they were green from the start. Their value was proved by mutation (below).

## Mutations (each applied in place, then restored and checked byte-identical by sha256)

| Mutation | Result |
|----------|--------|
| `AvisosRevision`: `estado-cambiado` → `"destructive"` | The n1 test fails. Restored `9a51daf0…` = `9a51daf0…`. |
| `api.ts` `listarExplotaciones` without `signal` | The `todasLasExplotaciones` signal test fails. Restored `77bf679f…` = `77bf679f…`. |
| `api.ts` `listarAnimalesDeExplotacion` without `signal` | The `api.test.ts` test fails. Restored (same hash). |
| `todasLasExplotaciones.ts`: pages ≥ 1 sent without `signal` | The "every page" test fails. Restored `5e7ab71e…` = `5e7ab71e…`. |
| `GanaderoDetallePage`: `altKey` dropped from the guard | The "con alt" case fails. Restored `dc1db04f…` = `dc1db04f…`. |
| `TramitesPage`: `stopPropagation` removed from "+N más" | The click and Enter disclosure tests fail (the dialog opens). Restored `b1530588…` = `b1530588…`. |

One slip, recovered: in the first mutation, the backup went to `$TMP` instead of the scratchpad path I meant to use. It was restored from `$TMP`, the hash was verified identical, and the backup was deleted.

## Commands (from `frontend/`)

- `npm test`: **36 files / 475 tests, all passed**. The baseline was 33 / 442, so this adds 3 files and 33 tests.
- `npm run build`: clean `tsc -b`. The only warning is the existing >500 kB chunk (622 kB).
- `npm run lint`: only the 3 existing `only-export-components` warnings. `fraseResumenImportacion` is deliberately not exported, so it adds no fourth warning.

## Deviations, with reasons

- **The `CLASE_ENLACE` JSDoc** now says "Enlace de texto de la aplicación" instead of "de Ganaderos". The rest is verbatim. The old wording was wrong once the file is shared.
- **The `#id` button** now uses `CLASE_ENLACE`. The visual is the same (green text, underline on hover, green ring on focus), with three small differences: the radius goes from `rounded-md` to `rounded-sm`, the underline uses the shared `decoration-primary/40`, and focus also underlines.
- **Status timing:** in Ganaderos, Trámites and Animales, the status says "Cargando…" whenever `cargando` is true. This includes page changes, where the old page stays dimmed, not only the first skeleton load. It matches "while loading" in the brief.
- **Facturación status:** while loading it is the visible `text-sm text-muted-foreground` line. When empty it switches to `sr-only`, so an empty `p` doesn't add a 24 px gap in the `gap-6` column. It is not hidden with `display:none`, which would take it out of the accessibility tree.
- **Import sentence:** "real plurals" is applied to creada/creadas and actualizada/actualizadas as well as to the error clause ("Contactos: 1 creada, 0 actualizadas."). The visible per-sheet lines are unchanged ("1 actualizadas" stays), as briefed.
- **Queue skeleton bars** use `max-w-28` rather than Ganaderos' `max-w-40`, because the queue has 5 narrower columns. The orchestrator can check this in the browser.
- **Badge variant assertion:** the tests check the variant's classes (`bg-success`, `bg-warning`, `bg-danger`, `border-border`). I did not rely on a `data-variant` attribute.

## Not done / notes for the orchestrator

- Layout items (1, 8, 9, 11, the skeleton widths, the disclosure wrapping) have no browser check from me. The brief leaves the re-measure at 375 / 640 / 1440 to the orchestrator.
- **`::selection` fallback:** the CSS build adds a fallback `::selection{background-color:var(--primary)}` outside the `@supports (color: color-mix(...))` block. Browsers without `color-mix` (pre-2023) would get a solid green selection. Tailwind does the same for its own `/50` opacities, so I left it.
- **Not addressed, out of scope:** the deferred P1 (top nav overflow at 375 px), and the 9b note n2 about `Combobox.Status` being announced when the list opens.
