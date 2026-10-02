# A2 Task 10 — Impeccable audit (every surface touched in A2)

Run by the orchestrator in the main session (decision 27), 2026-10-02. Audit only: nothing was fixed in this pass.

## Method

- **Code read in full:** `AppLayout`, Login, Registro, Facturación + `SuscripcionBanner`, `TramitesPage`, the review modal (`TramiteReviewDialog`, `CampoExplotacion`, `ListaCrotales`, `AvisosRevision`), `ExplotacionesPage`, `AnimalesDeExplotacion`, `ImportarExcelSection`, `GanaderosPage`, `GanaderoDetallePage`, `LogoGanera`, `estilos.ts`.
- **Detector:** `impeccable detect --json frontend/src` returned **0 findings**.
- **Real browser:** Chromium (Playwright, from npm, outside the repo, H8) against Vite on :5174 with a mocked API.
  - 21 scenarios × 2 viewports: 375×812 with touch, and 1440×900.
  - The scenarios: login (+ error), registro, queue (pending / all), the two banner variants, the modal (edit, dirty, combobox open with 157 explotaciones, 403, read-only), Ganaderos, the Ganadero detail (with animals, without explotaciones, 404), Explotaciones (animals open, import summary with errors) and Facturación (active, suspended, no subscription).
  - Each one ran axe-core 4.13 (`wcag2a/aa`, `wcag21a/aa`, `wcag22aa`, `best-practice`), measured horizontal overflow (page and inner scrollers), listed interactive elements under 24 px and under 44 px, and took a full-page screenshot.
- **Fixtures** include a single 27-character word without spaces ("Agroganaderaextremeñadelsur") as an explotación name and a ganadero name, to retest n1 from the Task 8 re-review.
- Script and captures: the scratchpad, not the repo.

## Audit Health Score

| # | Dimension | Score | Key finding |
|---|-----------|-------|-------------|
| 1 | Accessibility | 3 | axe: 0 contrast failures anywhere. Login/Registro have no `main` or `h1`; loading/import status messages are not reliably announced |
| 2 | Performance | 3 | Lean and motion-safe everywhere; one 621 kB JS chunk (noted in the plan, deferred) |
| 3 | Responsive Design | 2 | One long word makes the Explotaciones and Ganaderos tables scroll at 375 px; the top nav overflows to 837 px (deferred, out of A2) |
| 4 | Theming | 3 | Tokens only, detector clean; Facturación badge is off the state pairs |
| 5 | Implementation Integrity | 3 | Coherent "Field Office Ledger" system; small drifts (link recipe, pagination markup, numerals) |
| **Total** | | **14/20** | **Good** |

## Implementation Integrity Verdict

**Pass.** The implementation expresses one product-specific system.
- The cream canvas, white ledger surfaces, a single green, and the exact state pairs are used everywhere (no hex in components, detector clean).
- Domain language comes first (crotal, REGA, explotación).
- The review modal is a real collation desk (message beside data), not a generic form.
- What's left is drift at the edges: two auth screens built before the A2 recipes existed, one badge off the pairs, one pagination block without `nav`, and numerals that aren't tabular in one table.

## Executive Summary

- **Score:** 14/20 (Good).
- **Issues:** P0 0 · P1 2 (one deferred) · P2 6 · P3 10.
- **Top issues:**
  1. **Long unbroken names break reflow at 375 px** in Explotaciones (table 466 px in a 325 px container) and in Ganaderos (360 > 325, which pushes the "Expl." count out of view).
  2. **Top nav overflow at 375 px** (page 837 px wide). It is real, but out of A2 by decision.
  3. **Status messages:** the loading statuses (Animales, Ganaderos) and the import summary are not reliably announced.
  4. **Login/Registro:** no `main` landmark and no `h1`.
  5. **The queue on a phone** hides Explotación and Crotales off to the side, with "+N más" only in a `title`.
- **Next steps:** polish (P1 + P2 + the cheap P3s), then re-measure at 375 / 640 / 1440.

## Detailed Findings by Severity

### P1

**[P1] A long word without spaces makes the Explotaciones and Ganaderos tables scroll sideways at 375 px**
- **Location:**
  - `ExplotacionesPage.tsx:161,166,171` (`break-words` on Nombre, on the name line under REGA, and on Ganadero);
  - `GanaderosPage.tsx:178` (Nombre cell).
- **Category:** Responsive.
- **Measured:** Explotaciones table 466 px in a 325 px container; Ganaderos 360 px in 325 px, where the "Expl." column starts at x≈345 and is cut.
- **Impact:** at phone width the user loses columns (the explotación count, the "Ver animales" button) unless they find the inner scroll. This is the Task 8 n1 finding, still open, and it also affects Ganaderos.
- **Standard:** WCAG 1.4.10 Reflow.
- **Recommendation:** `break-words` → `wrap-anywhere` on those four elements (it lets the cell shrink below its longest word). Re-measure at 375 and 640.
- **Command:** `$impeccable polish`.

**[P1, deferred] The top navigation overflows on phones**
- **Location:** `AppLayout.tsx:30–64`.
- **Category:** Responsive.
- **Measured:** document 837 px wide at a 375 px viewport, on every authenticated page.
- **Impact:** the whole page can pan sideways, and the modal (full screen at 375) leaves the overflowing nav visible beside it in a full-page capture.
- **Standard:** WCAG 1.4.10.
- **Recommendation:** none in A2. The plan puts "la barra de navegación en móvil" outside A2. Record it for the next frontend prompt.

### P2

**[P2] Login and Registro have no `main` landmark and no `h1`**
- **Location:** `LoginPage.tsx:51–55`, `RegistroPage.tsx:61–65` (`CardTitle` is a `div`).
- **Category:** Accessibility.
- **axe:** `landmark-one-main`, `page-has-heading-one`, `region` (5 / 9 nodes).
- **Impact:** a screen-reader user landing on the first screen of the product finds no heading and no main region to jump to.
- **Standard:** WCAG 1.3.1 (best practice in axe).
- **Recommendation:** wrap the screen in `<main>` and render the "Ganera" title as an `h1` (`CardTitle render`, or an `h1` with the same classes).
- **Command:** `$impeccable polish`.

**[P2] Loading and result status messages may not be announced**
- **Location:**
  - `AnimalesDeExplotacion.tsx:102–106` (`role=status` mounted already filled, inside `aria-busy`);
  - `GanaderosPage.tsx:144–146` (the same pattern);
  - `ImportarExcelSection.tsx:79` (the import summary appears with no live region);
  - `FacturacionPage.tsx:52` ("Cargando…" is a plain `p`).
- **Category:** Accessibility.
- **Impact:**
  - after an import, a screen-reader user hears nothing, although the import's whole outcome (created, updated, row errors) is in that block;
  - the loading statuses are inserted already filled inside a busy container, which many screen-reader/browser pairs skip (Task 8 m3).
- **Standard:** WCAG 4.1.3 Status Messages.
- **Recommendation:**
  - keep an always-mounted `role=status` and change its text (the pattern the review modal's success notice already uses);
  - for the import, a status line that summarises the result ("Importación terminada: 2 explotaciones creadas, 5 actualizadas… 2 filas con error");
  - don't put the status inside the `aria-busy` element.
- **Command:** `$impeccable polish`.

**[P2] On a phone the queue hides Explotación and Crotales, and "+N más" is in a `title` only**
- **Location:** `TramitesPage.tsx:182–251, 358–362`.
- **Category:** Responsive / Accessibility.
- **Measured:** table 981 px in a 325 px container. At first view only Trámite, Tipo and Estado are visible.
- **Impact:** the gestor approving from the phone (a confirmed use in PRODUCT.md) can't scan which explotación or which crotales a row refers to without scrolling each row sideways. The REGA name and the extra crotales are also behind a `title`, which touch never shows (Task 6 M3).
- **Note:** the inner scroll is allowed by H12, so this is P2, not P1.
- **Recommendation:**
  - make "+N más" a real disclosure that takes focus and shows the remaining crotales (and "Indicado") on tap;
  - keep the table structure and its inner scroll.
- **Command:** `$impeccable polish` (`adapt` if a stacked mobile row is wanted later).

**[P2] The Facturación status badge is not one of the state pairs**
- **Location:** `FacturacionPage.tsx:80`.
- **Category:** Theming.
- **Impact:** "Activa" renders in the straw `secondary` and "Suspendida" in the destructive tint. This breaks the Exact Pair Rule, and the badge no longer means the same thing as the trámite badges beside it.
- **Recommendation:** map each `EstadoSuscripcion` to an existing pair:
  - `success`: ACTIVA, TRIAL;
  - `warning`: IMPAGO_GRACIA;
  - `danger`: TRIAL_EXPIRADO_SIN_PAGO, SUSPENDIDA, CANCELADA.
- **Command:** `$impeccable polish`.

**[P2] Text links outside the shared recipe**
- **Location:**
  - `LoginPage.tsx:99`, `RegistroPage.tsx:143` (no `outline-none` + ring, and no decoration rules);
  - `TramitesPage.tsx:224` (its own copy of the recipe);
  - `CLASE_ENLACE` itself lives in `features/ganaderos/estilos.ts`, yet Explotaciones, the modal and `AvisosRevision` import it from there.
- **Category:** Implementation Integrity / Accessibility.
- **Impact:**
  - the auth links show the browser's default outline, not the system's green ring, so focus looks different on the very first screen;
  - a shared recipe imported from a feature folder invites a third copy.
- **Standard:** WCAG 2.4.7 is met by the default outline; this is consistency, hence P2 for the recipe drift.
- **Recommendation:**
  - move `CLASE_ENLACE` to `shared/ui/enlace.ts` (or similar);
  - use it in Login, Registro and the queue's id button;
  - update the imports.
- **Command:** `$impeccable polish`.

**[P2] Facturación repeats the blocking message twice on one screen**
- **Location:** `FacturacionPage.tsx:89–96` together with `SuscripcionBanner`.
- **Category:** Implementation Integrity (content).
- **Impact:** with SUSPENDIDA or TRIAL_EXPIRADO, the banner and the card both say "No puedes aprobar trámites ahora mismo". Two `role=alert` elements announce the same title, and the page reads as louder than the state warrants.
- **Recommendation:** on Facturación, keep the banner (it explains the cause). In the card, either drop the duplicate alert or turn it into the action line next to "Actualizar suscripción" ("Actualiza el pago para volver a aprobar trámites.") with no second alert role.
- **Command:** `$impeccable polish`.

### P3

- **Explotaciones pagination** is a `div`, while the queue, Ganaderos and Animales use `nav aria-label` (`ExplotacionesPage.tsx:221`). Make it a `nav` and add `tabular-nums` to its counter.
- **The REGA code in the Explotaciones table** has no `tabular-nums` (`ExplotacionesPage.tsx:160,165`), unlike the queue, the modal and the Ganadero detail.
- **The expanded animals cell** is announced under the "Código REGA" header (`ExplotacionesPage.tsx:206`). Give the hidden "Animales" header an id and point the panel cell's `headers` at it (Task 8 detail).
- **The queue's loading row:** decide between the skeleton and the "Cargando trámites…" row (`TramitesPage.tsx:194`). Recommendation: the Ganaderos skeleton, with the status kept outside `aria-busy` (see P2). This keeps the two listings consistent.
- **Page subtitles** are `text-muted-foreground` at 16 px (queue, Ganaderos, Facturación, Ganadero detail), while DESIGN.md says the muted subtitle is 14 px. Add `text-sm`.
- **The badge column width in the crotal rows** shifts by 14 px when the widest badge changes (optional fix from the 9b visual re-review): `ListaCrotales.tsx:116`, `sm:grid-cols-[minmax(0,1fr)_minmax(11rem,auto)_auto]`.
- **The combobox focus classes** are a hand copy of `Input` (`CampoExplotacion.tsx:118,122`). Keep them, with a comment that names `Input` as the source. Don't extract a shared `Combobox` while there is a single use (9b note).
- **The ">100" hint in `Combobox.Status`** (9b re-review n2) is mounted with the popup, so it may not be read when the list opens already truncated. Changes while typing are read. A real screen reader isn't available here, so this is left as a note. Cheap hardening: also reference it from the listbox with `aria-describedby`.
- **Test gaps:**
  - no test asserts that the `estado-cambiado` notice is neutral and not destructive (9b n1);
  - no test checks that the `AbortSignal` reaches axios (`todasLasExplotaciones.ts`, `explotaciones/api.ts`);
  - the Alt-key case is missing in the index link tests;
  - there is no real click on the empty-state link of the Ganadero detail.
- **The import summary** says "fila(s) con error". Use real plural forms ("1 fila con error" / "2 filas con error").

### Verified, not findings

- **Contrast:** axe found no failures across 42 page states, including the badges, Gris Oliva on Lino, the destructive alerts and the disabled buttons.
- **Small targets:** every element under 24 px is an inline text link, which WCAG 2.5.8 exempts, plus the queue's `#id` button (41×20). The queue row itself is clickable, and axe `target-size` passes. Base-ui focus guards (1×1) and Select hidden inputs are false positives.
- **axe `region` (101) with the combobox open:** the options sit in a portal outside the landmarks. This is the normal popup pattern and a false positive.
- **Console errors:** only the HTTP statuses the scenarios simulate (401, 403, 404). No JS errors.
- **Modal at 375 px:** full screen, footer fixed with the safe-area padding, every crotal row the same layout, and the combobox popup above the dialog (the 9b pending check).
- **Modal at 1440 px:** top anchored, and the message column stays put (confirmed by the 9b visual re-review).
- **Motion:** every animation is `motion-safe:` or `motion-reduce:transition-none`. There are no layout animations.

## Patterns & Systemic Issues

- **Long-name handling is not shared.** `break-words` is the reflex, and it fails in table cells. Every cell or heading that holds a user-supplied name (ganadero, explotación) should use `wrap-anywhere`.
- **Status announcements follow two patterns.** The modal uses an always-mounted status with changing text, which is correct. The listings insert a pre-filled status inside `aria-busy`. Converge on the modal's pattern.
- **Recipes live in feature folders** (`CLASE_ENLACE`). Shared recipes belong in `shared/`.

## Positive Findings

- **Zero contrast failures**, at both widths, on every state. The palette pairs do their job.
- **"Approve only what was seen"** holds visually and in code: green is used only on Aprobar and links, nothing mentions OVZ.net, and the badges always come from saved state.
- **Honest loading and error states everywhere:** never a partial list, always a "Reintentar", and 404s that don't reveal existence.
- **Tables scroll inside their container, never the page** (H12), and the tables were designed to fit at 375 px. Only the long-word case breaks that.
- **Keyboard and screen-reader care is deep:** row buttons with full names, `aria-sort`, `aria-expanded`/`aria-controls` on disclosures, focus moved to section headings, and sr-only text for whatever lives in a `title`.

## Recommended Actions

1. **[P1] `$impeccable polish`:** `wrap-anywhere` on the four name elements, then re-measure at 375 and 640.
2. **[P2] `$impeccable polish`:**
   - `main` + `h1` on Login and Registro;
   - status regions (Animales, Ganaderos, Facturación loading, import summary);
   - a focusable "+N más" disclosure in the queue;
   - the Facturación badge pairs and the duplicate alert;
   - `CLASE_ENLACE` moved to `shared` and applied to the auth links and the queue.
3. **[P3] `$impeccable polish`:**
   - Explotaciones `nav` + `tabular-nums`;
   - panel `headers`;
   - queue skeleton;
   - subtitles at 14 px;
   - badge column min width;
   - plural forms;
   - the test gaps.
4. **Deferred, not in A2:** the mobile top nav, and code-splitting the 621 kB chunk.
