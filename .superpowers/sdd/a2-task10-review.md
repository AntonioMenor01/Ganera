# A2 Task 10 — Polish from the audit: independent review

## VERDICT: APPROVED

No Critical or Important findings. Four Minor notes are listed below; none of them blocks the task.

## Commands (from `frontend/`)

- `npm test`: **36 files / 475 tests, all passed**. This matches the report.
- `npm run build`: `tsc -b` is clean. The only warning is the existing >500 kB chunk (`index-*.js` 622.02 kB).
- `npm run lint`: 0 errors and only the 3 existing `only-export-components` warnings (`button.tsx`, `badge.tsx`, `AuthContext.tsx`).

## Scope

- `git diff --cached` is empty.
- `backend/`, `frontend/public/`, `index.html`, `package.json`, `preview.png` and `logo.jpg` all have mtimes from 2026-09-28 or earlier, which is before this task. Their working-tree diffs belong to earlier A2 work, not to Task 10. No backend file is newer than the audit.
- No new dependencies.
- No `.bak` or `.orig` files under `frontend/src`.
- No hex values: `index.css` and `enlace.ts` use only tokens (`var(--primary)`, `text-primary`, `ring-ring/50`).

## Verified items

1. **`wrap-anywhere`.** It is on the Explotaciones Nombre cell, the mobile name line and the Ganadero cell, and on the Ganaderos Nombre cell, all with `whitespace-normal` kept. The remaining `break-words` uses are in GanaderoDetalle, which is out of the brief's list.
2. **Login and Registro.** The outer element is `<main>`. "Ganera" is `<h1 data-slot="card-title" className="font-heading text-base leading-snug font-medium">`. That is the same as `CardTitle`'s classes, minus `group-data-[size=sm]/card:text-sm`, which doesn't apply because these cards aren't `size=sm`. `CardTitle` itself is unchanged.
3. **Status regions.** All five are always mounted, and only their text changes.
   - Animales: the root wrapper holds the sr-only `<p role=status>` as a sibling of the `aria-busy` div. The skeleton is `aria-hidden` and the old inner statuses are gone.
   - Ganaderos and Trámites: the status sits before `<Table aria-busy>`, inside a plain card `div`.
   - Facturación: a visible line while loading, `sr-only` when empty (still in the accessibility tree).
   - Import: as described in the next point.

   I grepped for every `aria-busy` in `src/features`: the only ones are the Animales div and the two tables, so no status has a busy ancestor. The Explotaciones table that hosts Animales has no `aria-busy`.
   - **Import status.**
     - While uploading it reads "Importando el Excel…". After a success it reads "Importación terminada. Explotaciones: …. Animales: …. Contactos: …." with real singular and plural forms.
     - The error clause is added only when `errores.length > 0` ("1 fila con error." / "N filas con error.").
     - `resumen` is cleared at the start of each upload, so a re-import goes "Importando…" → summary, and the text changes even when the result is identical.
     - On error the status is empty and the `Alert` speaks.
4. **"+N más".**
   - It is a `<button type="button" aria-expanded>` with `stopPropagation`, in `text-muted-foreground`, with an underline on hover and the ring on focus.
   - Expanded, it renders every crotal through `ItemCrotal`, which keeps the `title`, the sr-only ", Indicado: …" and the badge. The `ul` gets `flex-wrap` and the button reads "Ver menos".
   - The state lives in `CeldaCrotales`, and rows are keyed by `tramite.id`, so it resets with the page.
   - The `title` and the sr-only "Además" text are removed.
   - The row has no `onKeyDown`, so Enter on the button only toggles it.
5. **Facturación.**
   - `VARIANTE_ESTADO` maps ACTIVA and TRIAL to `success`, IMPAGO_GRACIA to `warning`, and TRIAL_EXPIRADO_SIN_PAGO, SUSPENDIDA and CANCELADA to `danger`. Any other value falls back to `outline`.
   - The in-card `Alert` is replaced by `<p className="text-sm">` with the briefed sentence and no role.
   - `SuscripcionBanner` still explains TRIAL_EXPIRADO_SIN_PAGO, SUSPENDIDA and the no-subscription case.
6. **`CLASE_ENLACE`.**
   - It now lives in `src/shared/ui/enlace.ts` and `features/ganaderos/estilos.ts` is deleted. A grep finds no import of `estilos` anywhere.
   - All 6 importers point to `@/shared/ui/enlace`: Explotaciones, Ganaderos, GanaderoDetalle, AvisosRevision, Login, Registro and TramitesPage.
   - It is applied to the Login and Registro links (`cn("font-medium", CLASE_ENLACE)`) and to the queue `#id` button, as briefed.
7. **Explotaciones table.**
   - The pagination is `<nav aria-label="Paginación">` with a `tabular-nums` counter.
   - `tabular-nums` is on the REGA code (desktop and mobile).
   - The sr-only "Animales" `TableHead` has `id="explotaciones-col-animales"`, and the panel `TableCell` has `headers` pointing to it.
8. **Subtitles.** `text-sm` is on the subtitles of the queue, Ganaderos, Facturación, the detail count and the not-found text.
9. **`ListaCrotales`.** It uses `sm:grid-cols-[minmax(0,1fr)_minmax(11rem,auto)_auto]`.
10. **`CampoExplotacion`.** Only the comment line was added.
11. **`index.css`.** `::selection` uses `color-mix(in oklab, var(--primary) 18%, transparent)`, and `input, textarea` get `caret-color: var(--primary)`.
12. **Visible import line.** It reads "1 fila con error:" / "N filas con error:". The `ResumenHoja` line is unchanged, as briefed.
13. **Test gaps.** The n1 neutral-alert test, the `AbortSignal` spies, the Alt-click case and the real click on "Ir a Explotaciones" are all present and pass.

**Non-negotiable rules.**
- Nothing touches the Aprobar wording or the approval gating.
- No OVZ.net implication was added.
- The error alerts are kept (Trámites, Facturación, Import).
- No list was truncated: the disclosure shows all crotales.

**Adapted tests.**
- Ganaderos: `break-words` → `wrap-anywhere`.
- Trámites "crotales visibles": it now asserts the button, `aria-expanded="false"`, no `title` and no "Además". It still checks the sr-only Indicado and the badges.
- Trámites filter change: it now asserts the status text plus the absence of stale "#2", the counter and "Siguiente".

All three keep their intent, and none was weakened.

## Mutations (in place, each restored byte-identical; sha256 checked before and after)

| Mutation | Result |
|----------|--------|
| Animales status mounted only while loading | 1 fail (Animales 4.1.3 test) |
| Ganaderos: `aria-busy` on the card div holding the status | 1 fail |
| Ganaderos status mounted only while loading | 1 fail |
| Trámites: `aria-busy` on the card div holding the status | 1 fail |
| Trámites status mounted only while loading | 1 fail |
| "+N más" without `stopPropagation` | 2 fail (click and Enter: the dialog opens) |
| "+N más" without `aria-expanded` | 4 fail |
| Expanded crotales rendered as plain `li` (no sr-only Indicado) | 1 fail |
| Facturación badge back to `secondary`/`destructive` | 6 fail (it.each) |
| Facturación replacement line given `role="alert"` | 1 fail |
| Facturación status mounted only while loading | 1 fail |
| Import plural back to "N fila(s) con error" | 3 fail |
| Import error clause always added | 1 fail |
| Import status mounted only after an upload | 2 fail |
| Explotaciones panel cell without `headers` | 1 fail |
| Explotaciones pagination `nav` → `div` | 1 fail |
| Login `h1` → `div` | 1 fail |
| Registro `main` → `div` | 1 fail |
| Login link without `CLASE_ENLACE` | 1 fail |
| AvisosRevision `estado-cambiado` → `destructive` | 1 fail (n1) |

Every mutation was caught. The file hashes matched after every restore, and the scratch backups were removed.

## Findings

### Critical
None.

### Important
None.

### Minor

- **m1 `ImportarExcelSection.tsx:25` (`fraseHoja`).**
  - **Scenario:** the announced sentence says "Animales: 40 creadas, 3 actualizadas.". The participles agree with the implied "filas", not with "animales", which reads slightly off in Spanish when heard.
  - **Status:** the brief explicitly asked for this wording, so it is not a defect of the task.
  - **Optional fix:** "Animales: 40 filas creadas, …", or masculine participles per sheet.
- **m2 `TramitesPage.tsx:363` (`CeldaCrotales`).**
  - **Scenario:** the disclosure button has `aria-expanded` but no `aria-controls`.
  - **Status:** that is acceptable here, because the revealed items appear in the same list right before the button.
  - **Optional fix:** give the `ul` an id and set `aria-controls`.
- **m3 `TramitesPage.test.tsx:441` and others.**
  - **Scenario:** several tests use `getByRole("status")` / `findByRole("status")` without a name. If another status region is ever added to the page (for example a Combobox status), these tests will throw on multiple matches rather than fail meaningfully.
  - **Optional fix:** select the status by its text, or scope it with `within(...)`. This is not needed now.
- **m4 `GanaderosPage.tsx` / `TramitesPage.tsx` / `AnimalesDeExplotacion.tsx`.**
  - **Scenario:** the status says "Cargando…" on every reload, including the `recargar` after Rechazar and page changes, not only on the first load. That is a deliberate, reported deviation and it matches "while loading". It may add one extra announcement after a review action.
  - **Status:** no change is needed; recorded so the orchestrator is aware of it.
