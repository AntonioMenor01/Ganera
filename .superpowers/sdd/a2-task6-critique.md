⚠️ DEGRADED: single-context (the harness forbids spawning sub-agents without an explicit request from the user; this run was requested by the orchestrating agent, not by Antonio, so Assessments A and B ran sequentially in one context — A was written to the scratchpad before the detector ran)

# `/impeccable critique` — Cola de trámites (A2, Task 6)

**Target:** `frontend/src/features/tramites/TramitesPage.tsx` (and how a row opens
`TramiteReviewDialog.tsx`), as it stood **before** Task 6 (after Tasks 1–5).
**Mode:** Operate (app UI: the visitor completes a task — triage and review the queue).
**Evidence:** static only. The code, `PRODUCT.md` and `DESIGN.md` (loaded with `impeccable context`).
There was no live browser: the Playwright MCP isn't configured (plan H8), so there were no
screenshots and no overlay injection.

## Design Health Score

| # | Heuristic | Score | Key Issue |
|---|-----------|-------|-----------|
| 1 | Visibility of System Status | 2 | On first load, the only feedback is "Cargando…" in the subtitle; the table body is blank. When the filter or page changes, the previous rows stay on screen, and so does the previous filter's pager (N1). Emptying the last page by approving leaves an out-of-range empty page and hides the pager (N2). |
| 2 | Match System / Real World | 2 | The Explotación column shows an internal id (`#3`). Gestorías think in **código REGA**. "Sin resolver" is system language; the domain wording is "sin asignar". |
| 3 | User Control and Freedom | 3 | The filter, the pager and Esc on the dialog all work. N2 is a dead end: the only way out is to change the filter. |
| 4 | Consistency and Standards | 3 | The queue says "Sin resolver" and "—"; the dialog says "Sin resolver — hay que asignar…" and "Sin determinar todavía". The badges are consistent now (Task 5). |
| 5 | Error Prevention | 2 | While a new filter loads, the old filter's rows are still clickable, so you can open a trámite that doesn't belong to the filter you just chose. |
| 6 | Recognition Rather Than Recall | 1 | You have to open each trámite to learn which animals it concerns and whether its crotales resolved. The explotación is an id you'd have to look up elsewhere. |
| 7 | Flexibility and Efficiency | 1 | Rows can't be reached from the keyboard at all. There's no accelerator for the main path (open → review). |
| 8 | Aesthetic and Minimalist Design | 3 | Calm and on-brand. The ID column takes the most prominent first position, although it's the least useful. |
| 9 | Error Recovery | 3 | A load error is an Alert with the real message and "Reintentar", and the pager stays available (M3 of Task 2). |
| 10 | Help and Documentation | 2 | No hint that a row opens a review, and no explanation of what the estados mean for the user's next step. |
| **Total** | | **22/40** | **Acceptable** (significant improvements needed) |

## Design Specificity Verdict

**LLM assessment:** the palette, typeface and badges are Ganera's, but the *structure* is a stock
shadcn data table (ID / Tipo / Estado / Explotación) that any CRUD admin could reuse unchanged.
What makes this queue Ganera's is exactly what's missing from the row: **which animals** (crotales,
the thing the ganadero actually typed) and **which farm** (código REGA). The queue is the
product's core surface (PRODUCT.md: "Administrativos … work through the trámite queue several
times a day"), yet it tells the reviewer almost nothing before they open a trámite. The single
biggest opportunity is to make each row a real triage line: estado, tipo, REGA and the crotales
with their resolution, so the reviewer can see at a glance which trámites need hands-on work
(`Varios animales coinciden`, `Falta la explotación`) and which are routine.

**Deterministic scan:** `impeccable detect --json` over `TramitesPage.tsx`,
`TramiteReviewDialog.tsx` and `BadgesTramite.tsx` returned `[]`: **0 findings**. That's expected.
The detector catches visual anti-patterns (gradients, glassmorphism, hex-in-component and the
like), and this surface has none. None of the issues below are detector-visible: they're
interaction, state and information issues. There are no false positives to discard.

**Visual overlays:** not available. There's no browser automation in this session (plan H8), so
nothing was injected.

## Overall Impression

A clean, restrained table that's honest about errors since Task 2. But it's **mouse-only**, it
**hides the review data**, and it **speaks in ids**. The biggest single win is making rows
keyboard-reachable and showing REGA + crotales in them.

## What's Working

- **Error honesty.** A failed load shows the real `mensajeDeError` text with "Reintentar", never a
  blank table, and the pager survives a failed page (Task 2 M3). This follows Principle 2 ("Tell
  the truth about system state").
- **State badges with words.** Since Task 5 the estado badge always carries its Spanish label and
  one of the exact semantic pairs. Colour is never the only signal, and the filter options come
  from the same `ESTADOS_TRAMITE` source.
- **Restraint.** No decoration competes with the data. The page follows DESIGN.md's rhythm
  (20px/600 title, muted count, 24px gaps, bordered white table container).

## Priority Issues

**[P0] Rows only open with a mouse.**
- **Why it matters:** `onClick` sits on the `<tr>`, and there's no focusable control, `role`,
  `tabIndex` or key handler. A keyboard or screen-reader user can't open any trámite, so they
  can't review or approve at all. This blocks the product's core task for them (Sam), and it
  forces the desk power user (Alex) onto the mouse.
- **Fix:** put a real `<button>` in the first cell with a label that includes the visible text
  ("Revisar trámite #12"), so that Tab, Enter and Space work natively and it gets DESIGN.md's
  green focus ring. Keep the row click as a mouse convenience only.
- **Suggested command:** `$impeccable harden`

**[P1] The Explotación column shows an internal id.**
- **Why it matters:** `#3` means nothing to a gestoría, which identifies farms by código REGA.
  It breaks Principle 5 ("the domain's language first") and forces recall.
- **Fix:** translate `explotacionId` into `codigoRega` using the **complete** list of explotaciones
  (decisions 20 and 22). Show "Sin asignar" for null, a neutral "—" with a tooltip for an id that
  isn't in the list, and a subtle placeholder while it loads. If the list can't be loaded
  completely, show a visible error with "Reintentar", never ids and never a partial list.
- **Suggested command:** `$impeccable clarify`

**[P1] Crotales are invisible in the queue.**
- **Why it matters:** the crotales are what the ganadero wrote and what most often needs fixing.
  Without them, a reviewer can't tell a routine trámite from one with an ambiguous or missing
  animal without opening every row. That's Recognition over Recall, and a context switch.
- **Fix:** add a compact Crotales column. Show up to two crotales, each with its
  `BadgeResolucionCrotal`, then "+N más". Show the resolved crotal, with the indicated one in a
  tooltip when they differ. Show "Sin crotales" when the list is empty. Keep it to one line so rows
  don't get heavy.
- **Suggested command:** `$impeccable layout`

**[P2] Loading, empty and pagination states lie a little.**
- **Why it matters:** the first load is a blank body under the headers. A filter change keeps the
  previous filter's rows and pager ("Página 1 de 5") on screen (N1). Approving the last trámite of
  the last page leaves "No hay trámites que mostrar." on an out-of-range page with no pager (N2).
  The empty text ignores the active filter, and the count says "1 trámites".
- **Fix:**
  - show a loading row in the table and set `aria-busy`;
  - on a filter change, reset the page, `totalPaginas` and the shown page together;
  - after a reload returns an empty out-of-range page, go back to the last valid page;
  - make the empty text contextual ("No hay trámites en «Rechazado».");
  - use singular and plural correctly in the count.
- **Suggested command:** `$impeccable harden`

**[P2] At 375px, the header doesn't wrap and the table would push the page.**
- **Why it matters:** the gestor sometimes approves from a phone (PRODUCT.md, H12). The title and
  the 224px filter share one non-wrapping row. A wider row (crotal badges) must scroll inside its
  container, not make the whole page scroll sideways.
- **Fix:** let the header wrap (`flex-wrap`, filter full-width on small screens), keep table cells
  `whitespace-nowrap` inside the existing `overflow-x-auto` table container, and put `min-w-0` on
  the page column so the container, not the page, absorbs the width.
- **Suggested command:** `$impeccable adapt`

## Persona Red Flags

**Sam (keyboard / screen reader):**
- Tab goes from the filter straight to the pager. No row is ever focusable, so Sam can't open a
  trámite, and every review and approval is impossible.
- A loading table isn't announced (no `aria-busy`, no status text in the table).
- The Explotación cell reads out "número 3".

**Alex (desk administrativo, power user):**
- Alex has to open every trámite to see its crotales, which means 20 modal round-trips to triage
  one page.
- There's no keyboard path from the list to the review.
- After approving the last item of page 2, Alex lands on an empty page with no pager and has to
  change the filter to get back.

**Casey (gestor on the phone):**
- The title and the filter compete for one row at 375px.
- A long crotal and its badge have to scroll inside the table rather than breaking the page.
- The row tap target is fine, but the top bar itself isn't adapted (out of scope, see Deferred).

## Minor Observations

- The pager isn't a `nav` landmark ("Paginación").
- The ID column is first and in full ink, although it's the lowest-value datum. Muted tabular
  numerals would suit it better.
- The queue defaults to "Todos los estados", although the daily work is `PENDIENTE_REVISION`.
- `ERROR_OVZ` rows don't hint at their `motivoError` until opened.
- The empty `tipoTramite` renders as "—" in the queue and as "Sin determinar todavía" in the dialog.

## Questions to Consider

(A structured interview isn't possible here: this implementer runs non-interactively and reports
to the orchestrator. The Task 6 brief already answers the scope question: apply what's inside
A2's queue scope and defer the rest. These are the questions still open for Antonio.)

1. **Default filter:** should the queue open on "Pendiente de revisión" (the actual work) instead
   of "Todos los estados"? Options:
   - (a) yes, default to Pendiente de revisión;
   - (b) keep Todos;
   - (c) remember the last filter per session.
2. **Crotal density:** the row shows two crotales plus "+N más". Options:
   - (a) keep 2;
   - (b) show all up to 4;
   - (c) show only the count plus the worst resolution. Option (c) would need a severity order,
     which is a product call, not a frontend one.
3. **Errores en OVZ.net:** should `ERROR_OVZ` rows preview their `motivoError`? Options:
   - (a) a truncated second line;
   - (b) a tooltip;
   - (c) leave it for the dialog.

## Applied in Task 6 (in scope)

| Finding | Applied |
|---|---|
| P0 rows mouse-only | A real `<button>` in the first cell ("Revisar trámite #N", visible text "#N"), native Enter/Space, with the button's focus ring. The row click stays as a mouse convenience. |
| P1 REGA | Shared complete loader `cargarTodasLasExplotaciones` / `useTodasLasExplotaciones`, plus `presentarExplotacion`. The cell shows REGA (name in the tooltip), "Sin asignar", "—" with a tooltip, a subtle placeholder while loading, and an error Alert with Reintentar. |
| P1 crotales | Crotales column: first 2 crotales with `BadgeResolucionCrotal`, "+N más" with the rest in the tooltip, "Sin crotales" when empty. |
| P2 states | Loading row plus `aria-busy`. A contextual empty text. Singular/plural count. N1 (filter change resets the page, the total and the shown page). N2 (clamp to the last valid page after an out-of-range empty reload). |
| P2 375px | The header wraps and the filter is full-width on small screens. The page column has `min-w-0`, so the table scrolls inside its container. |
| Minor | The pager is in `<nav aria-label="Paginación">`. The ID is no longer an inert first column: it's the row's action, a link-style button in Verde Monte (One Green Rule: links are green) with `tabular-nums`. The queue's empty tipo now says "Sin determinar", like the dialog. The filter has an accessible name ("Filtrar por estado"). |

## Deferred (with reason)

- **Default filter "Pendiente de revisión"**: a product decision (question 1). Not asked for in A2.
- **`motivoError` preview in `ERROR_OVZ` rows**: a product call (question 3). The dialog already
  shows it.
- **Everything inside the review dialog** (editable form, `version`, not closing itself after
  approving, confirming a reject): Task 9 (decisions 7–13).
- **Top navigation bar on mobile**: explicitly out of A2's scope (plan H12, "La barra superior en
  móvil queda fuera"). The bar can still make the *page* scroll sideways at 375px because of the
  nav, even though the queue itself no longer does.
- **`FacturacionPage.tsx:80`**: the subscription-state badge uses the `destructive` variant (an
  opacity tint, `bg-destructive/10`), not one of the exact semantic pairs (The Exact Pair Rule).
  It predates A2. **For Task 10.**
- **`explotacionCodigoRega` in the `GET /tramites` DTO**: this would make the client-side
  translation unnecessary. It's already in the backend mini-prompt (H7). The complete-list loader
  stays until then, and the combobox (Task 9) needs it anyway.
- **Keyboard accelerators (j/k, Enter on the focused row)**: beyond the accessible baseline. Not
  requested. A candidate for `$impeccable polish` once the review flow (Task 9) settles.

## Run Notes

- **Target slug:** `frontend-src-features-tramites-tramitespage-tsx` (`critique-storage slug`,
  exit 0).
- **Ignore list:** `.impeccable/critique/ignore.md` doesn't exist, so nothing was ignored.
- **Assessment independence:** degraded, single context (see the banner). A was written to the
  scratchpad before B ran.
- **CLI detector:** ran. `[]`, 0 findings.
- **Browser visibility and overlay injection:** skipped. No browser automation (plan H8).
- **Live server:** none started, so nothing to clean up.
- **Snapshot persistence:** skipped on purpose. The Task 6 deliverable is this file, and writing
  `.impeccable/critique/` would add a new untracked directory to the repo outside the task's
  deliverables. There's no trend, because this is the first run for this target.
- **Temp files:** the scratchpad notes only, outside the repo.
