disposition: fix

Substitution: run as a fresh general-purpose agent (the harness has no impeccable-finish-reviewer type). Code-led build: no comp, no QUALITY BAR card and no state.json, all expected for this path. Not read: `useRevisionTramite.ts` beyond its `puede*` lines (it was reviewed separately), `buscarExplotacion.ts`, the tests, and the `socio/` captures. No capture shows the success state ("stays open after success") or the open combobox. Neither was a required capture, so neither was judged.

Evidence (check 0): all 12 required captures are present and valid. The desktop captures are 1440x900. The mobile captures are full page, and the dialog occupies 0–390 px. The extra document width (795 px) comes from the top nav. That overflow is known and deferred, so it is not counted here.

## persistence

Pass.
- PRODUCT.md and DESIGN.md exist. DESIGN.md predates this build, and its "Dialog (trámite review)" entry (white panel, 12 px radius, 10 %-ink ring, light scrim, close button top right) matches the render.
- This is a code-led build: no comp round, no `.impeccable/mocks/` approvals and no state file are needed.
- FORM says "Seed key: none". It justifies this by quoting new-work.md:41 ("Never run the script for a local extension…"). I checked that line and it exists, so the skipped roll is corroborated and is not a finding.

## fidelity

There is no comp, so each element is judged against the direction contract and Antonio's three answers.

| Element | Verdict |
|---|---|
| Header: "Trámite #N" plus state badge, close button top right | match |
| Two columns, message left / data right (2fr / 3fr); stacked and full screen on mobile | match (Antonio's answer, 2026-09-30) |
| Message as a quoted Lino block; empty text "Aún no hay mensaje: este trámite no llegó por WhatsApp." | match (verbatim) |
| Message column sticky on desktop while you scroll the crotales | match (it serves THESIS "mesa de cotejo") |
| Explotación combobox (search by REGA, name or ganadero), Tipo select, 32 px Borde Lino fields | match |
| Crotal rows: input, resolved full crotal, badge, remove; "Añadir crotal" | match in content. The geometry is **contradicted**: input width depends on the badge beside it. Desktop inputs are ~372 / ~300 / ~314 px (desktop-edicion). On mobile, the "Varios animales coinciden" row wraps its badge onto its own line under a full-width input, while the other rows keep theirs inline (mobile-edicion, mobile-409). The orchestrator's observation is confirmed. The column no longer reads as one ledger. |
| "Sin guardar" replaces the old badge on an edited or new row | match (desktop/mobile-sucio). Minor: it uses the same outline pair as the saved "No está en el inventario", so the provisional marker and a saved result look the same. See fix 5. |
| Avisos between header and columns; 409 motivo shown verbatim over fresh data | match (desktop/mobile-409) |
| Footer: Rechazar left, Guardar + Aprobar right; fixed on mobile with a safe-area inset | match |
| "Guarda antes de aprobar" + "Descartar cambios" while dirty; Aprobar/Rechazar disabled | match |
| Inline reject confirmation in the action bar, focus on "Cancelar" | match |
| Read-only (APROBADO): no inputs, no footer | match. Minor: the badge is pushed to the far right edge, ~290 px from its crotal (desktop-solo-lectura). See fix 4. |
| Honesty: nothing implies OVZ.net; badges come from saved state (`guardadoDe` matches by value, and a changed explotación marks every row "Sin guardar") | match |
| **Layout stability** | **contradicted**. The dialog is vertically centred, so any height change moves the whole panel. The first keystroke adds the "Guarda antes de aprobar" line and the panel jumps up 18 px under the caret (header at y=202 in edicion, y=184 in sucio). A 409 aviso moves it again (y=163). The field being typed in moves while the user types. |
| TYPE | match. One family (Geist), hierarchy from weight, tabular numerals on crotales, REGA and the ids. |
| MATERIAL | match. Flat, ring and tone only, no faux material. |
| GROUND | match. OWN-WORLD names white panel, Lino quote block and light scrim; the render keeps all three, with no drift. |

## ceiling

Reached for a Restrained Operate surface, with one native device unused. The thesis is a comparison table ("alineado para comprobarlo de un vistazo"), but the quoted message never marks the digits Ganera extracted. Lightly highlighting "4471", "5068" and "ES370120009932" in the Lino block (a `<mark>` in a slightly darker Lino tint, not green) would join the two columns visually. This is optional, not a required fix.

## material_fixes

1. **Fidelity, crotal-row geometry.** `ListaCrotales.tsx:108–145`. Replace the per-row `flex flex-wrap` with a shared grid so every input has the same width and the badge sits in a fixed column.
   - `ul` (l.108): `className="grid grid-cols-[minmax(0,1fr)_auto] gap-x-2 gap-y-2 sm:grid-cols-[minmax(0,1fr)_auto_auto]"`.
   - `li` (l.113): `className="col-span-full grid grid-cols-subgrid items-center gap-y-1"`.
   - Move the badge and the remove button out of the shared `<span>` (l.127–139) into separate grid children:
     - `<Input className="min-w-0 tabular-nums" …/>` (drop `flex-1 basis-40`);
     - badge wrapper `<span className="col-start-1 row-start-2 justify-self-start sm:col-start-2 sm:row-start-1">`;
     - remove `<Button className="col-start-2 row-start-1 sm:col-start-3">`.
   - Resolved-crotal line (l.141): `className="col-span-full pl-2.5 text-sm"`. On mobile, give it `sm:row-start-2` so it sits next to the badge row (`col-start-1 row-start-3 sm:row-start-2`).
   - Result:
     - desktop: one input width, one badge column sized to the widest badge;
     - mobile: input + remove button on every row, with the badge always on the line below. The inconsistent wrap is gone.
2. **Craft, layout shift.** `TramiteReviewDialog.tsx:168`. Anchor the panel's top instead of centring it: in the className, replace `md:top-1/2` with `md:top-[8dvh]`, delete `md:-translate-y-1/2`, and replace `md:max-h-[calc(100dvh-4rem)]` with `md:max-h-[84dvh]`. The header and fields then stay still when the dirty hint or a 409 aviso adds height (the panel grows downward).
3. **Contract/honesty ("nunca se aprueba otra cosa que lo que se ve").** `TramiteReviewDialog.tsx:116–117`. After a failed Aprobar (a 409 where the crotal resolution changed and fresh data just arrived), focus returns to the still-enabled Aprobar, so an impatient second Enter approves the re-resolved state before it is read. Change l.116 to `const boton = antes === "aprobar" ? null : { guardar: guardarRef, aprobar: aprobarRef, rechazar: rechazarRef }[antes].current;` so that after an Aprobar attempt, focus falls back to `tituloRef`. The `role="alert"` still announces the motivo. Adjust the l.105–107 comment and the test that pins this behaviour.
4. **Craft, read-only pairing.** `ListaCrotales.tsx:49`. Drop `ml-auto` (use `<span>` with no class, or remove the wrapper) so the badge sits beside its crotal, as it does in the editable rows and in the queue.
5. **Contract ("Sin guardar" must read as provisional, not as a result).** `ListaCrotales.tsx:35` and `TramiteReviewDialog.tsx:359`. Add `className="border-dashed border-foreground/25"` to the "Sin guardar" `Badge`, so it doesn't look identical to the saved outline badge "No está en el inventario" (see desktop-sucio, where both sit in adjacent rows).
6. **Floor (glyph standing in for an icon).** `ListaCrotales.tsx:28`. Replace `<span aria-hidden>→ </span>` with `<ArrowRightIcon aria-hidden className="mr-1 inline size-3.5 align-[-2px]" />` (import it from `lucide-react` alongside `PlusIcon, XIcon`), so this arrow matches the rest of the drawn icon set.

## keep

Keep the side-by-side comparison layout (sticky Lino quote on the left, saved-state badges on the right), the verbatim 409 motivo between the header and the columns, the fixed footer with the inline reject confirmation, and green used on Aprobar alone. None of the fixes should add colour, cards or new wording around Aprobar.

## re-review (2026-10-02)

disposition: pass

Inputs: the 13 re-taken captures in `revision/` (2026-10-01 00:31), the 3 in `socio/`, `ListaCrotales.tsx`, `TramiteReviewDialog.tsx`, and the "Fixes after review" section of `a2-task9b-report.md`.

| Fix | Verdict | Evidence |
|---|---|---|
| 1. Crotal-row geometry | resolved | `ListaCrotales.tsx:114–154` uses one subgrid with the classes from the review. Desktop-edicion: all three inputs span x=648–943 and every badge starts at x=952. Desktop-sucio: 648–957 and 966. Both the inputs and the badge column are equal within each capture. Mobile (edicion, sucio, 409, confirmar-rechazo): every row is input (x=16–338) + remove (x=360), with the badge always on the line below at x=16. The "Varios animales coinciden" row no longer wraps differently. |
| 2. Layout shift | resolved | `TramiteReviewDialog.tsx:184`: `md:top-[8dvh]`, `md:max-h-[84dvh]`, no `-translate-y-1/2`. The panel top is at y=72 (8 % of 900) and the header text at y=95 in edicion, sucio, 409 and confirmar-rechazo. The dirty hint and the 409 aviso now make the panel grow downward (bottom edge 568 → 604 → 644). |
| 3. Focus after a failed Aprobar | resolved | `TramiteReviewDialog.tsx:128`: `antes === "aprobar" ? null : …`, so focus falls back to `tituloRef` (l.129). It is covered by the new focus test. In desktop-409, Aprobar shows no focus ring. |
| 4. Read-only pairing | resolved | `ListaCrotales.tsx:49–56` has no `ml-auto`. In desktop-solo-lectura, the badge sits at x=781, 13 px after its crotal (it was ~290 px away before). Mobile-solo-lectura matches. |
| 5. "Sin guardar" provisional | resolved | `BadgeSinGuardar` (`ListaCrotales.tsx:36–42`) adds `border-dashed border-foreground/25`. In mobile-sucio the dashed outline is clearly visible, next to the solid "No está en el inventario". |
| 6. Arrow glyph | resolved | `ListaCrotales.tsx:28` uses `ArrowRightIcon`. It renders as the drawn lucide arrow before "ES370120004471" in every editable and 409 capture. |

### Keep list

There are no regressions:
- The sticky Lino quote on the left and the saved-state badges on the right are unchanged.
- The 409 motivo still sits verbatim between the header and the columns.
- The fixed footer and the inline reject confirmation ("¿Rechazar el trámite #214? No se puede deshacer.", Cancelar / Sí, rechazar) still work.
- Green is still on Aprobar alone. The only other greens are the status badge tints that existed before.
- No new wording mentions OVZ.net.

### New findings

- Minor, not blocking. The badge column is sized by the widest badge on screen, so its width changes when that badge changes. In desktop-edicion, typing in the "5068" row replaces "Varios animales coinciden" with "Sin guardar", and every input widens by 14 px (right edge 943 → 957). The caret and the text are left-aligned and do not move, so this is only a small right-edge twitch. To remove it, give the column a floor in `ListaCrotales.tsx:116`: `sm:grid-cols-[minmax(0,1fr)_minmax(11rem,auto)_auto]`.
- Capture artefact, not a finding. In mobile-409, Aprobar is lighter, at rgb(74,98,82) against (31,61,43) elsewhere. That value is exactly `primary/80` over the footer, which is the hover state left behind by the emulated tap. Real touch devices do not match `(hover: hover)`.
- I saw no overflow, unexpected wrapping or contrast problem inside the dialog at 390 px. The top-nav overflow is known and deferred, so it is not counted.

Conclusion: all 6 material fixes are resolved and the keep list holds. The one remaining note (the badge-column width) is optional polish.
