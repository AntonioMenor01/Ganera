# Documenter pass: Ganaderos (list and detail)

Substitution: run by a fresh general-purpose agent following `reference/degraded/documenter.md`, because no dedicated documenter agent exists.

Scope: **ordinary extension** of the established Ganera world (new-work.md section 7). The incumbent system is preserved. `DESIGN.md`, `PRODUCT.md`, `.impeccable/design.json` and all source files were **not modified**. This file is the only output. The proposed wording below is for Antonio to review in Task 11.

Result: **No changes to DESIGN.md in this pass.**

## Evidence checked

- `DESIGN.md` (full), the direction contract `.impeccable/surfaces/frontend-src-features-ganaderos.md`, and the finish review `.impeccable/review/ganaderos/finish-review.md` (disposition **ship**; it covers only the eight scored fixes).
- `frontend/src/features/ganaderos/`: `GanaderosPage.tsx`, `GanaderoDetallePage.tsx`, `AnimalesDeExplotacionSlot.tsx`, `estilos.ts`, `telefono.ts`, `ordenGanaderos.ts`.
- `frontend/src/shared/layout/AppLayout.tsx`, `frontend/src/features/tramites/BadgesTramite.tsx`, `frontend/src/features/tramites/etiquetas.ts`, and the working-tree diff of `frontend/src/features/explotaciones/ExplotacionesPage.tsx`.
- System primitives: `frontend/src/index.css` (`--primary`, `--muted`, `--radius`, `--sidebar-primary`), `components/ui/badge.tsx`, `components/ui/card.tsx` (`CardFooter`), and `components/ui/table.tsx` (`TableRow`, `TableHead`, `TableCell`).
- A grep across `features/` for literal hex values and arbitrary colors (none in the Ganaderos files), for skeleton bars, and for link recipes.
- Earlier A2 notes aimed at DESIGN.md: `.superpowers/sdd/a2-task4-report.md` §"Notes for DESIGN.md", `a2-task5-report.md` §"DESIGN.md updates needed", `a2-task6-report.md` §"Notes for DESIGN.md", and `a2-task7-report.md` §"Notes for DESIGN.md". Items already raised there are cross-referenced below rather than repeated.

## 1. Consistency with DESIGN.md

The build is **consistent** with DESIGN.md.

- **Tokens:** only Tailwind tokens appear (`text-primary`, `text-muted-foreground`, `bg-card`, `bg-muted`, `ring-foreground/10`, `ring-ring/50`, `border`), with no hex and no arbitrary color. The Token-Only Rule holds.
- **One Green:** Verde Monte appears only on text links (`CLASE_ENLACE`), the active nav pill (`bg-sidebar-primary` = `#1f3d2b` in `:root`) and the focus ring. Headings, counts and NIF use ink or Gris Oliva.
- **Paper-Flat:** there are no shadows.
  - Each explotación section is `rounded-xl bg-card ring-1 ring-foreground/10`, which is the card recipe.
  - The list container is `rounded-xl border bg-card`, which is the table-container recipe.
  - The only elevation vocabulary used is the 10% ring and the Borde Lino hairline.
- **Components:**
  - Links, buttons and alerts reuse the existing `Button` (`outline sm` for Reintentar and pagination, `ghost sm` for the disclosure), `Alert variant="destructive"` and `Table`.
  - The footer reuses the `CardFooter` vocabulary (`rounded-b-xl border-t bg-muted/50`), as DESIGN.md "Cards → footer" describes.
- **Badges:** `BadgeRolContacto` goes through `presentacionRolContacto`. It uses the exact `success` pair (Titular) and the neutral `outline` (Empleado), and always shows a text label. The Exact Pair Rule holds.
- **Type:**
  - Geist is the only family (Single Family Rule holds).
  - Page titles are 20px/600 (`text-xl font-semibold`), one per screen, with a muted 14px subtitle or count.
  - Section titles are 16px/500 (`text-base font-medium`), and body text is 14px (`text-sm`).
  - NIF, REGA, phones and counts use `tabular-nums`.
  - There is no metric card and no uppercase eyebrow. Ganaderos deliberately doesn't use them (a2-task7 note).
- **Spacing:**
  - The page stack uses `gap-6` (24px), and sections are stacked with `gap-4` (16px).
  - Section padding is `px-4 py-3` and the footer is `px-4 py-2`, both on the 4/8/12/16/24 scale.
  - Tables keep the 40px header row and 8px cell padding.
- **Focus:** a 3px green ring at 50% on links, sortable headers, section `h2` targets and rows (`has-focus-visible:bg-muted/50`), matching the Do's.

Divergence between the contract and the build: none that changes the world. The contract's "un único contenedor blanco con anillo al 10 % por bloque" is realized as one white ringed `<section>` per explotación, with no nested cards.

## 2. Additions DESIGN.md does not yet describe (proposed wording for Task 11)

Items already proposed in earlier reports are referenced, with any Ganaderos-specific detail added. Everything else is new here.

1. **Nav order and membership** (extends a2-task7 "Nav: Ganaderos is now in the nav"; the brand-mark wording is in a2-task4). Under Components → Navigation, add:
   > "Links, in working order: Trámites, Ganaderos, Explotaciones, Facturación. That is, the daily queue, then who it belongs to (a Ganadero, then their explotaciones), then the account. A section stays active on its sub-routes (`/ganaderos/:id` marks Ganaderos)."

2. **Text links** (a2-task7 already proposes `CLASE_ENLACE`). Add the scope detail under Components (new "Text links" entry):
   > "Text links are Verde Monte and underline on hover or keyboard focus, with a 40% green underline 4px below the text. They carry the standard 3px focus ring on a 4px (`rounded-sm`) corner. The recipe lives once in `CLASE_ENLACE` and is shared by the Ganaderos screens and the ganadero link in Explotaciones."

3. **Sortable table headers** (a2-task7 already proposes this). Add the precise form under Tables:
   > "A sortable column's header is a text button with a small arrow icon. The arrow is ink on the active column (up/down) and Gris Oliva on inactive ones (up-down). On hover it gets a Lino fill, on a 6.9px corner. Only the active column carries `aria-sort`. Clicking the active column reverses it, and clicking another column starts ascending and returns to page 1. Sort only on columns the backend whitelists."

4. **Role badge pair** (a2-task7 already proposes this; it also needs a2-task5's correction that `outline` is now a state variant). Under Badges, add:
   > "Contact role: Titular uses `success`, Empleado uses `outline`, both through `BadgeRolContacto` / `presentacionRolContacto` (`features/tramites/etiquetas.ts`). A role is information, not a problem, so it never uses warning or danger."

5. **Stacked record sections with a Lino footer** (a2-task7's "Record sections" covers the container; the footer and header detail are new). Add under Components (new "Record sections" entry):
   > "A detail page reads top to bottom as a file. It has one white section per child record (12px radius, 10% ink ring, no nested cards), stacked with a 16px gap. The header strip (16px by 12px padding, Borde Lino bottom rule) holds a 16px title: the identifier in 500 weight with tabular numerals, then ` · name` in 400. The body lists rows divided by Borde Lino. An action strip closes the section and uses the card-footer treatment: a 50% Lino fill, a top hairline, and rounded bottom corners."

6. **Disclosure** (a2-task7 already proposes the ghost sm button with a rotating chevron). Add:
   > "A disclosure's button sits flush with the content edge: its negative margin equals its own horizontal padding. The panel is collapsed by default and mounts its content (and makes its request) only when opened. The chevron turns 180° in 200ms, with no transition under reduced motion. The button uses `aria-expanded` and `aria-controls`."

7. **Skeleton loading** (a2-task6 proposes the 12px REGA placeholder, and a2-task7 notes the detail's bars). Unify them under Tables/states:
   > "Loading shows a skeleton, not text. Placeholders are Lino (`bg-muted`) bars on a 4px corner, pulsing only when motion is allowed. They are 12px tall inline and 16px in table cells, with 5 skeleton rows for a list (a number cell is a short right-aligned bar), and 24px plus 16px bars and an empty ringed section for a detail. Visible bars are `aria-hidden`, and one `sr-only` `role="status"` text announces the load. A reload over existing data dims the rows to 60% instead."
   - This **replaces** a2-task6's "a centered muted 'Cargando trámites…' row" for new lists. Whether the Trámites queue should adopt the skeleton rows is left to Antonio.

8. **Index of REGA links** (new). Add under Record sections:
   > "When a record has more than 3 child sections, a compact inline index of links (`REGA · nombre`, tabular REGA, wrapping, 16px by 6px gaps) sits between the header and the first section, labeled as a `nav`. Following a link scrolls to the section (24px top margin) and moves focus to its title. A deep link with `#explotacion-N` does the same once the data arrives."

9. **`tel:` links** (new). Add under Text links:
   > "A phone number is a text link to `tel:` with the exact E.164 value, preceded by a 14px phone icon. It shows grouped for reading (`+34 612 345 678`) only for Spanish 9-digit numbers. Other numbers are shown as stored."

10. **Contact rows as a grid** (new, from finish-review fix 3). Add under Record sections:
    > "Rows with a name, a value and a badge align in columns. On desktop the name gets up to 18rem, the value 11rem, then the badge, so values line up regardless of badge width. On mobile the name and value share a row and the badge wraps below."

11. **Not-found pattern** (new). Add under Components (new "Not found" entry):
    > "A 404 on a detail route (another gestoría's record and a nonexistent one are deliberately indistinguishable) shows the normal page title ('Ganadero no encontrado'), one muted sentence, and a single recovery link ('Volver a …'). The top back link is hidden in this state, so there is only one way back. Malformed ids are treated as not found without calling the API."

12. **Back link** (new). Add under Text links:
    > "A detail page opens with a small back link to its list: 14px, a 14px left arrow, and the list's name ('← Ganaderos')."

13. **Empty states that teach** (new as a system rule). Add under Tables/states:
    > "An empty state says why it is empty and where the data comes from, with a link there. For example: 'Se crean al importar el Excel de explotaciones.' followed by 'Ir a Explotaciones'."

14. **Load errors** (new as a system rule). Add under Alerts:
    > "A failed load shows a destructive Alert with a title, the message, and an outline sm 'Reintentar'. Stale data is cleared rather than left looking current, and pagination stays visible so the user can move off a failing page."

15. **Narrow-screen table headers** (new). Add under Tables/Responsive:
    > "Tables scroll inside their container, never the page. A long header may shorten below `sm` (for example 'Expl.'), with the full name kept for screen readers. Name cells wrap so numeric columns stay visible."

16. **Subsection label** (new, clarification). Add under Typography:
    > "A block inside a section may have a 14px/500 Gris Oliva sentence-case heading ('Contactos') that labels the list below it. It is a real heading, not an uppercase eyebrow."

17. **Radius `sm`** (new token observation). `rounded-sm` (about 4px) is now reused for skeleton bars, link focus corners and the focusable `h2`. DESIGN.md's `rounded` scale lists only md, lg, xl and pill. Consider adding `sm` to the frontmatter scale, with the exact computed value from `--radius`.

## 3. Pre-existing drift (reported, not repaired)

- **Brand mark:** DESIGN.md still describes "a 10px Verde Monte dot followed by the 'GANERA' logotype". AppLayout renders `LogoGanera` at 20px (a2-task4 already has the wording).
- **Badges/Exact Pair Rule:** DESIGN.md still cites `BADGE_POR_ESTADO` in `features/tramites/types.ts` and says `outline` "isn't used for state". Both are outdated (a2-task5).
- **Tables:** "Rows that open the review dialog show a pointer cursor" is outdated (a2-task6).
- **Link recipes diverge:**
  - `LoginPage.tsx:99` and `RegistroPage.tsx:143` use `font-medium text-primary hover:underline`, with no focus ring and no underline color or offset.
  - `TramitesPage.tsx:224` repeats a hand-written variant of the recipe instead of `CLASE_ENLACE`.
  - `CLASE_ENLACE` lives in `features/ganaderos/` but Explotaciones imports it (a2-task7 suggests moving it to `shared/`).
- **FacturacionPage.tsx:80:** the state badge uses `secondary`/`destructive`, not an exact pair (a2-task6).
- **Nav at 390px:** the top bar overflows and widens the page. DESIGN.md says "no dedicated treatment yet", and the finish review deferred it.
- **`.dark` block:** `--sidebar-primary` is blue there. DESIGN.md already records the block as undesigned scaffold, so this is noted only.

## Five-line system summary

- **Palette:** Crema Papel canvas, Paja Clara bar, white surfaces with a 10% ink ring or a Borde Lino hairline, Verde Monte as the single accent, and three exact state pairs plus a neutral outline.
- **Type:** Geist Variable only. Headline 20/600, title 16/500, body 14/400, badges 12/500, tabular numerals for data.
- **Layout:** top bar, 24px page padding and gap, 16px between stacked sections, tables scrolling inside their containers.
- **Named rules upheld:** Token-Only, One Green, Exact Pair, Single Family, Paper-Flat.
- **New patterns awaiting Task 11:** record sections with a Lino action footer, sortable headers, role badges, disclosure, skeleton loading, REGA index, `tel:` and back links, the 404 pattern and the nav order.

Not canonized: the nav overflow at 390px (a known defect, deferred) and the divergent link recipes on Login/Registro/Trámites (drift, not a rule). No craft-floor refusal (kicker, hero metric, eyebrow) appears in this build, so none is at risk of being canonized.
