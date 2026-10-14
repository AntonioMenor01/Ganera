---
name: Ganera
description: Review-and-approve workspace for Spanish cattle-ranching gestorías.
colors:
  rojo-ganera: "#ec3013"
  rojo-accion: "#dd2b0f"
  rojo-enlace: "#ae1800"
  rojo-problema: "#7c1405"
  rojo-tinte: "#fff2ef"
  tinta: "#201e1d"
  fondo: "#f3f2f2"
  superficie: "#eae9e9"
  gris-borde: "#d7d3d3"
  gris-input: "#7d7979"
  gris-texto: "#605d5d"
  neutro-fondo: "#eae7e7"
  neutro-texto: "#444141"
  divisor: "rgba(32, 30, 29, 0.4)"
  blanco: "#ffffff"
  exito-fondo: "#eaf3de"
  exito-texto: "#27500a"
  aviso-fondo: "#faeeda"
  aviso-texto: "#854f0b"
typography:
  headline:
    fontFamily: "Archivo Variable, system-ui, sans-serif"
    fontSize: "1.25rem"
    fontWeight: 700
    fontStretch: "90%"
    lineHeight: 1.4
  metric:
    fontFamily: "Archivo Variable, system-ui, sans-serif"
    fontSize: "1.875rem"
    fontWeight: 700
    fontStretch: "90%"
    lineHeight: 1.2
  title:
    fontFamily: "Archivo Variable, system-ui, sans-serif"
    fontSize: "1rem"
    fontWeight: 700
    fontStretch: "90%"
    lineHeight: 1.375
  body:
    fontFamily: "Archivo Variable, system-ui, sans-serif"
    fontSize: "0.875rem"
    fontWeight: 400
    fontStretch: "90%"
    lineHeight: 1.43
  interface:
    fontFamily: "Archivo Variable, system-ui, sans-serif"
    fontSize: "0.875rem"
    fontWeight: 500
    fontStretch: "90%"
    lineHeight: 1.43
  table-header:
    fontFamily: "Archivo Variable, system-ui, sans-serif"
    fontSize: "11px"
    fontWeight: 500
    fontStretch: "90%"
    letterSpacing: "0.08em"
    textTransform: "uppercase" # CSS only; the DOM text stays in sentence case
  label:
    fontFamily: "Archivo Variable, system-ui, sans-serif"
    fontSize: "0.75rem"
    fontWeight: 500
    fontStretch: "90%"
    lineHeight: 1.33
    letterSpacing: "0.025em"
rounded:
  all: "0px" # --radius: 0px since design phase 1 (2026-10-13); every rounded-* resolves to 0
spacing:
  xs: "4px"
  sm: "8px"
  card-sm: "12px"
  md: "16px"
  lg: "24px"
components:
  button-primary:
    backgroundColor: "{colors.rojo-accion}"
    textColor: "{colors.blanco}"
    rounded: "{rounded.all}"
    height: "36px"
    padding: "0 12px"
  button-primary-hover:
    backgroundColor: "{colors.rojo-enlace}"
  button-outline:
    backgroundColor: "{colors.blanco}"
    textColor: "{colors.tinta}"
    borderColor: "{colors.gris-input}"
    rounded: "{rounded.all}"
    height: "36px"
    padding: "0 12px"
  button-outline-hover:
    backgroundColor: "{colors.neutro-fondo}"
  button-destructive:
    backgroundColor: "{colors.blanco}"
    textColor: "{colors.rojo-problema}"
    borderColor: "{colors.rojo-problema}"
    rounded: "{rounded.all}"
    height: "36px"
    padding: "0 12px"
  button-destructive-hover:
    backgroundColor: "{colors.rojo-tinte}"
  card:
    backgroundColor: "{colors.blanco}"
    textColor: "{colors.tinta}"
    borderColor: "{colors.gris-borde}"
    rounded: "{rounded.all}"
    padding: "{spacing.md}"
  input:
    backgroundColor: "{colors.blanco}"
    textColor: "{colors.tinta}"
    borderColor: "{colors.gris-input}"
    rounded: "{rounded.all}"
    height: "36px" # 40px in the review dialog, 44px on Login
    padding: "4px 10px"
  sidebar: # AppLayout from lg (1024px)
    backgroundColor: "{colors.superficie}"
    textColor: "{colors.tinta}"
    width: "240px"
    borderRight: "2px {colors.divisor}"
  nav-bar: # AppLayout below lg
    backgroundColor: "{colors.superficie}"
    textColor: "{colors.tinta}"
    borderBottom: "2px {colors.divisor}"
    padding: "16px 24px" # from md; 12px 16px 4px below md
  nav-link:
    textColor: "{colors.tinta}"
    rounded: "{rounded.all}"
    height: "40px" # from lg; 32px from md to lg; 36px touch target below md
    padding: "0 10px"
  nav-link-hover:
    backgroundColor: "{colors.gris-borde}"
  nav-link-active:
    backgroundColor: "{colors.rojo-accion}"
    textColor: "{colors.blanco}"
  badge-success:
    backgroundColor: "{colors.exito-fondo}"
    textColor: "{colors.exito-texto}"
    rounded: "{rounded.all}"
    height: "24px"
    padding: "2px 8px"
  badge-warning:
    backgroundColor: "{colors.aviso-fondo}"
    textColor: "{colors.aviso-texto}"
    rounded: "{rounded.all}"
    height: "24px"
    padding: "2px 8px"
  badge-danger:
    backgroundColor: "{colors.rojo-tinte}"
    textColor: "{colors.rojo-problema}"
    rounded: "{rounded.all}"
    height: "24px"
    padding: "2px 8px"
  badge-neutral:
    backgroundColor: "{colors.neutro-fondo}"
    textColor: "{colors.neutro-texto}"
    rounded: "{rounded.all}"
    height: "24px"
    padding: "2px 8px"
---

# Design System: Ganera

<!-- Records the visual system as it is implemented today (frontend/src/index.css,
     frontend/src/components/ui/ and the patterns the feature screens share), brought up to date
     after the new brand colours and typography (2026-10-05, plan
     docs/superpowers/plans/2026-10-04-colores-y-tipografia.md). The palette comes from the Ganera
     landing (https://ganera-web.vercel.app/css/tokens.css) and the typeface is Archivo: both are
     fixed brand commitments (see PRODUCT.md); don't alter them unless Antonio asks.
     Updated again after "Diseño de Álvaro, fase 1" (2026-10-13, plan
     docs/superpowers/plans/2026-10-12-diseno-alvaro-fase1.md): square corners, Archivo at 90% width
     with weights 400/500/700, grey/red ramps and the neutral pair as tokens, 2px divisors, the side
     bar from lg and the new Login. Only the skin changed: structure, DOM order, texts and behaviour
     are the same. The visual reference is Álvaro's "Ganera App v2" (kept outside the repo). -->

## Overview

**Creative North Star: "The Red-Stamp Ledger"**

Ganera looks like a well-kept office ledger that carries the brand's red stamp: a light warm-grey
canvas, white sheets laid on it with hairline edges and square corners, near-black ink, and one
vivid red that means "this is the action". It is a working tool for people who process a queue
several times a day, so density is moderate, type is small and even, and decoration is almost
absent. The brand shows in the red, in Archivo's bold, slightly condensed headlines and in
restraint, not in ornament.

Hierarchy comes from tone and weight, not from depth. The page is Fondo, the side bar (top bar
below `lg`) the slightly darker Superficie, and content sits on white cards or tables framed by a
1px Gris Borde border. Zones (a page header, a dialog header or footer, the side bar's edge) are
separated by a 2px divisor in 40% ink. Color is reserved for meaning, and red has two jobs that
never mix: **solid red acts, dark tinted red warns.**

**Key Characteristics:**
- A light warm-grey canvas (Fondo), never pure white, with white content surfaces.
- One action red (Rojo Acción, 600) for primary buttons and the active nav item; a deeper red
  (Rojo Enlace, 700) for text links, the primary hover and the focus ring; the vivid Rojo Ganera
  only on the brand mark.
- Problems use a dark red (Rojo Problema, 800), never as a solid fill, and always with text that
  names them; error alerts also carry an icon.
- Semantic state tints come as exact background/text pairs, not opacity-derived colors.
- Flat, square surfaces (radius 0 everywhere) separated by hairline borders, 2px divisors and
  tone, with no ambient shadows; floating menus get a small one and only the dialog panel the large one.
- One typeface (Archivo Variable with its width axis at 90%, self-hosted) throughout, with weight
  (400/500/700) carrying hierarchy.
- Secondary text is always Gris 700 (`text-muted-foreground`), never a translucent grey or ink.

## Colors

A low-chroma grey-and-ink palette from the Ganera landing, with one red ramp split by job and two
muted state pairs (success, warning). The landing's coral `accent-2` (`#E15B47`) is **not** used in
the app: it would be a third red, almost indistinguishable from the brand one.

### Red (one ramp, four jobs)
- **Rojo Ganera** (`--marca`, `#EC3013`): the brand mark only (`LogoGanera` with `text-marca`).
  Never text: at 16px it gives 3.47:1 on Superficie. As a non-text graphic it passes 3:1 on every
  surface (3.47 / 3.76 / 4.20 on Superficie / Fondo / white).
- **Rojo Acción** (`--primary`, `--sidebar-primary`, ramp 600 `#DD2B0F`): the primary button
  ("Aprobar", "Entrar", "Importar Excel", "Marcar como hecho en OVZ"), the
  active nav link and the Login panel, always as a solid fill with white text
  (`--primary-foreground`, 4.74:1). Only the main action of a screen or footer is solid red:
  "Guardar" and "Enviar por WhatsApp" in the review dialog are `outline` since phase 1, so "Aprobar"
  is the only red there. It
  also tints text selection (18%, mixed in oklab) and colors the caret. **Never text on a grey**
  (4.25:1 on Fondo, 3.9:1 on Superficie are short).
- **Rojo Enlace** (`--enlace`, `--primary-hover`, `--ring`, ramp 700 `#AE1800`): text links
  (`CLASE_ENLACE`, `CLASE_ENLACE_TABLA` for link columns inside tables, `Button variant="link"`),
  the primary button's hover fill (white on it, 7.17:1) and the focus ring, always at 100%
  (7.17 / 6.41 / 5.91 on white / Fondo / Superficie).
- **Rojo Problema** (`--destructive`, `--danger-foreground`, ramp 800 `#7C1405`) with **Rojo Tinte**
  (`--danger`, ramp 100 `#FFF2EF`): error alerts (filled Rojo Tinte with Rojo Problema text,
  9.80:1), the `RECHAZADO` / `ERROR_OVZ` badges (9.80:1) and the danger button ("Rechazar", "Sí,
  rechazar", "Descartar y cerrar"…): white with a Rojo Problema border and text (10.72:1), Rojo
  Tinte on hover (9.80:1). Never a solid fill.

### Ramps (tokens)
Both landing ramps are tokens in `index.css` and Tailwind colors: `gris-100 … gris-900`
(`#F8F4F4`, `#EAE7E7`, `#D7D3D3`, `#BAB6B6`, `#9B9797`, `#7D7979`, `#605D5D`, `#444141`, `#2D2B2B`)
and `rojo-100 … rojo-900` (`#FFF2EF`, `#FFE0D9`, `#FFC4B8`, `#FF9783`, `#FF563C`, `#DD2B0F`,
`#AE1800`, `#7C1405`, `#4D170E`). The semantic tokens (`--primary`, `--border`, `--input`,
`--muted-foreground`…) keep pointing at the same values; use them first, and a ramp step only where
no semantic token fits (the initials square `bg-rojo-100 text-rojo-800`, the outline/ghost hover
`bg-gris-200`, the dialog scrim `bg-gris-900/50`).

### Neutral
- **Fondo** (`--background`, `#F3F2F2`): the page canvas behind everything, the quoted WhatsApp
  message (with a Gris Borde border) and the WhatsApp reply panel.
- **Superficie** (`--secondary`, `--sidebar`, `--muted`, `--accent`, `#EAE9E9`): the side bar / top
  bar, the ficha's sticky header, secondary buttons, the Ganadero section's action strip (at 50%)
  and skeleton bars.
- **Blanco** (`--card`, `--popover`, `#FFFFFF`): cards, table containers, dialogs, dialog footers,
  popovers, inputs and outline/danger buttons, everything that holds data, so tables and badges get
  the most contrast.
- **Par neutro** (`--neutral` / `--neutral-foreground`, Gris 200 `#EAE7E7` with Gris 800 `#444141`,
  8.22:1; `bg-neutral text-neutral-foreground`): the fourth state pair, for badges with no semantic
  state (`outline` and `neutral` variants), the discarded band, the ficha's preview strip and the
  "Quitar la marca" confirmation. Rojo Enlace on it still gives 5.83:1.
- **Divisor** (`--divisor`, 40% ink, `border-divisor`): the 2px rule between zones (side bar edge,
  top bar bottom, page header, dialog header and footers, the ficha's header and its "Marcar" block,
  table header rule). Decorative (about 2.5:1), so it is never the boundary of a control.
- **Tinta** (`--foreground` and the neutral `*-foreground`, `#201E1D`): all primary text and the
  "GANERA" logotype (16.60 / 14.86 / 13.70 on white / Fondo / Superficie).
- **Gris Texto** (`--muted-foreground`, ramp 700 `#605D5D`): secondary text, descriptions, counts,
  empty-state messages, table headers, placeholders and decorative icons (6.52 / 5.83 / 5.38). It
  is the **only** secondary text color: Álvaro's v2 uses ink at 50/55/60% and `#7D7979` for small
  text, which fall short (Gris 600 gives 4.30 on white, 3.55 on Superficie); they are not imported.
- **Gris Borde** (`--border`, `--sidebar-border`, `--sidebar-accent`, ramp 300 `#D7D3D3`): every
  hairline (cards, table containers, table rows, record sections, the combobox popup, crotal rows),
  and the hover fill of inactive nav links.
- **Gris Input** (`--input`, ramp 600 `#7D7979`): the stroke of inputs, selects and the combobox,
  the lightest grey of the ramp that reaches 3:1 as a non-text boundary (4.30 on white, 3.85 on
  Fondo, 3.55 on Superficie; ramp 500 gave 2.89 / 2.38).

### Semantic (trámite state)
- **Éxito** (`--success` / `--success-foreground`, unchanged): resolved favorably, meaning
  `HECHO_EN_OVZ` and `EJECUTADO_OVZ`.
- **Aviso** (`--warning` / `--warning-foreground`, unchanged): not resolved yet, meaning
  `PENDIENTE_EXTRACCION`, `PENDIENTE_REVISION`, `APROBADO` (data reviewed, still to be done in OVZ)
  and `EN_PROCESO`. The amber stays clearly apart from both reds.
- **Peligro** (`--danger` / `--danger-foreground`): resolved unfavorably, meaning `ERROR_OVZ` and
  `RECHAZADO`, in Rojo Problema on Rojo Tinte, so there is a single "problem red".

### Named Rules
**The Token-Only Rule.** Every color is a CSS variable in `frontend/src/index.css`, exposed to
Tailwind through `@theme inline`. Components reference tokens (`bg-primary`, `text-enlace`,
`text-marca`, `bg-primary-hover`, `text-muted-foreground`, `bg-success`) and never a literal hex.

**The Two Reds Rule.** Solid red acts; dark tinted red warns. Rojo Acción appears only as a solid
fill on an action or the current location; Rojo Enlace only as link text, hover or focus (in a
table column the link waits in Tinta and the red appears on pointing); Rojo
Problema never as a solid fill and always with words that name the problem (plus an icon on error
alerts). A primary action and an error can never be confused. No badge uses Rojo Acción. If
something needs to stand out and isn't an action, the location, a link or a problem, it uses
weight, not more red.

**The Exact Pair Rule.** Each state badge is a hand-picked background/foreground pair, not a
color at reduced opacity. Add new states by mapping them to one of the three semantic pairs, or to
the neutral pair (`outline`), in the domain's constant in `features/tramites/etiquetas.ts`
(`ESTADOS_TRAMITE`, `RESOLUCIONES_CROTAL`, `ROLES_CONTACTO`). Measured contrast (WCAG 2.x, AA at
12px/500): success 8.21:1, warning 5.87:1, danger 9.80:1, neutral 8.22:1.

**The No-Translucent-Text Rule.** Text is Tinta, Gris 700, white on a solid fill, or the
foreground of a pair. Never `text-foreground/xx`, never a ramp step lighter than 700 as text, never
a translucent grey. Translucency is allowed only for fills and rules (the divisor, the row hover at
4% ink, the scrim).

## Typography

**Body Font:** Archivo Variable with its width axis (self-hosted via
`@fontsource-variable/archivo/wdth.css`, OFL-1.1, `wght` 100–900 and `wdth` 62–125%, latin,
latin-ext and vietnamese by `unicode-range`; fallback `system-ui, sans-serif`). The app uses it at
**90% width** (`html { font-stretch: 90% }` in `@layer base`; the preflight's `font: inherit`
carries it into buttons and inputs). Vite bundles the `.woff2` files into `assets/`: no request
ever goes to Google or a CDN. Only `latin` downloads for Spanish (90 KB, +55 KB over the old
`wght`-only file).
**Display/Heading Font:** the same family (`--font-heading` aliases `--font-sans`)

**Character:** a sturdy grotesque, the landing's typeface, slightly condensed to match Álvaro's v2.
Hierarchy comes only from size and three weights: **700** for titles, **500** for interface
elements, **400** for reading. 800 and 600 are no longer used (`font-extrabold` and
`font-semibold` don't appear in `src`).

### Hierarchy
- **Headline** (700, 20px, `text-xl font-bold`): one page title per screen ("Cola de trámites",
  "Ganaderos", "Explotaciones", a ganadero's name), followed by a muted 14px subtitle or count. The
  Login `h1` "Entrar en Ganera" is 32px/700 (`tracking-tight`), and the "GANERA" logotype is 700
  with `tracking-wide`.
- **Metric** (700, 30px, `text-3xl`): the single large number in a metric card (the Explotaciones
  count).
- **Title** (700, 16px): card, section and dialog titles ("Trámite #N", record section titles, each
  sheet of the import summary).
- **Interface** (500, `font-medium`): buttons, badges, field labels (`Label` is 13px/500 in ink),
  nav links, the user's name in the side bar, alert action text and the text links on the auth
  screen. Alert titles are 700.
- **Body** (400, 14px, `text-sm`): the default for tables, cards, forms and alerts. Inputs are 16px
  on mobile and 14px from `md` up (Login included).
- **Data emphasis** (500, `font-medium`): data inside tables and lists (the last digits of a
  crotal, the crotal inputs, the queue's tipo label and `#id`, the ganadero's name in its table).
  Since phase 1 it shares its weight with interface text; it is set apart by tabular numerals and
  position, not by weight.
- **Table headers** (500, 11px, Gris 700, `tracking-[0.08em]`, uppercase **by CSS**): every
  `TableHead`, and the "Mensaje de WhatsApp" title in the review dialog. The DOM text stays in
  sentence case ("Trámite", "Código REGA"), so screen readers and tests read the real word, never
  "TRÁMITE" (checked in Chromium and Firefox: `textTransform` is `uppercase`, `textContent` is not).
- **Label** (500, 12px, uppercase by CSS, `tracking-wide`): the eyebrow above the Explotaciones
  metric only. Badges use 12px/500 in sentence case. Newer screens don't use eyebrows.
- **Subsection / field label** (500, 14px, Gris Texto, sentence case; `CLASE_ETIQUETA` in the
  review dialog): a real heading or label over the block below it ("Contactos", "Explotación",
  "Tipo de trámite"). It is never an uppercase eyebrow.
- **Data numerals:** códigos REGA, NIF, crotales, phones, counts and pagers use `tabular-nums`
  (Archivo has tabular figures: "1111" and "8888" measure the same).

### Named Rules
**The Single Family Rule.** Archivo Variable only. Hierarchy never comes from a second typeface.

**The CSS-Uppercase Rule.** Uppercase is a style, never content: write the label in sentence case
in the JSX and add `uppercase`. A label typed in capitals is read letter by letter as an acronym
and breaks text queries.

## Layout

The app shell is one `<header>` that is a **fixed 240px side bar from `lg`** (1024px) and a **top
bar below `lg`**, styled by the `--sidebar*` tokens in both cases (see Components → Navigation).
From `lg` the content column starts at 240px (`lg:pl-60`). At the top of the content column
`SuscripcionBanner` can insert a full-width alert strip, then comes the page content, padded 24px
(`p-6`), 32px from `lg` (`lg:p-8`).

- **Page rhythm:** content stacks vertically in a flex column with a 24px gap (`gap-6`). The page
  header is a row with the title block on the left and its controls (e.g. the estado filter
  `Select`, 224px wide, 38px tall) on the right; in the queue a 2px divisor closes the header.
- **Shell:** a side bar from `lg`; below it, one row from `md` (24px by 16px padding; brand, links,
  then the user block pushed right) and two rows below `md` (16px horizontal padding). Same DOM and
  tab order at every width (see Components → Navigation).
- **Detail pages** read top to bottom as a file: a back link, the record's title, a muted count,
  then stacked record sections with a 16px gap (see Components → Record sections).
- **Cards:** internal spacing 16px (`--card-spacing`), or 12px for the `sm` size. The metric card
  shrinks to its content (`w-fit`, min 192px).
- **Tables:** full width inside a bordered white container, with 40px header rows, body rows of at
  least 56px (`h-14`; a row with a 12px notice under its badge grows to about 62px) and 8px cell
  padding.
- **Auth screen** (Login): see Components → Login. A Rojo Acción panel and the form on Fondo, no
  card.
- **Responsive:** no custom breakpoints; Tailwind defaults only (`md` switches input text from
  16px to 14px). The page header wraps and a header control (the estado filter) goes full width
  below `sm`. Tables scroll inside their container, never the page. Name cells wrap anywhere
  (`wrap-anywhere`) so a long single-word name can't push numeric columns out. A long header may
  shorten below `sm` ("Expl."), with the full name kept for screen readers, and a column dropped on
  mobile is removed from the markup (`useDesdeSm`), not hidden with CSS, so `colSpan` stays exact.
  The review dialog goes full screen below `md`. The shell is a side bar from `lg`, one row at
  `md` and two rows below `md`, with its links in a strip that scrolls inside itself, so it never
  widens the page. The Login panel is a column from `lg` and a 56px band below. Checked on
  2026-10-13 in Chromium and Firefox at 1440, 1024, 768 and 375px: no page-level horizontal scroll
  on any screen.

## Elevation & Depth

The system is flat. Depth comes from tone (Fondo canvas, Superficie bar, white surfaces), from
hairline edges and from 2px divisors, not from shadows. Cards, table containers and record sections
use a 1px Gris Borde border (`border border-border`; the old `ring-foreground/10` is gone).

### Shadow Vocabulary
- **Dialog panel** (`shadow-lg`, `0 12px 32px` of Gris 900 at 22%): the review dialog's panel, the only
  layer with the large shadow.
- **Floating menu:** the `Combobox` popup (the explotación picker) has a 1px Gris Borde border and
  `shadow-md`; the `Select` popup has a 1px Gris Borde ring and the same `shadow-md`.
- **Modal scrim** (`bg-gris-900/50` + `backdrop-blur-xs`): the dialog overlay, Gris 900 at 50% with
  a slight blur.

### Named Rules
**The Paper-Flat Rule.** Surfaces at rest have no shadow. Only a transient floating layer (a
dropdown, the dialog) may cast one, and only the dialog panel gets `shadow-lg`.

## Shapes

**Square, everywhere.** `--radius` is `0px` (not a unitless `0`, which would make
`calc(var(--radius) * 1.4)` an invalid length), and every `rounded-sm … rounded-4xl` derives from
it, so cards, tables, dialogs, buttons, inputs, badges, alerts, nav links, the initials square and
the focus ring are all right-angled. No component in `components/ui` carries a literal radius.
Edges are a 1px hairline, or a 2px divisor between zones; there is no clipping or angled geometry.
Leftover `rounded-*` classes in screens are harmless (they resolve to 0) but new code shouldn't add
them.

## Components

### Buttons
Compact and square, 36px tall by default.
- **Shape:** radius 0, 14px/500 text, 12px horizontal padding. Sizes are `xs` 24px, `sm` 30px
  (Copiar, Reintentar, "Cerrar sesión"), default 36px (pagination, dialog footers) and `lg` 44px,
  plus square icon variants of 24/30/36/44px. Login's "Entrar" is a full-width 48px primary with a
  trailing arrow.
- **Primary:** Rojo Acción fill with white text. On hover the fill turns Rojo Enlace
  (`hover:bg-primary-hover`), never an opacity drop, which would lower the white text's contrast.
  **Only the main action is solid red**: "Aprobar" in the review dialog, "Marcar como hecho en
  OVZ", "Entrar", "Importar Excel". "Enviar por WhatsApp" is `outline` (phase 1), so the dialog keeps
  one solid red.
- **Outline (secondary):** white fill (`bg-card`) with a 1px Gris 600 stroke (`border-input`) and a
  Gris 200 fill on hover. Used for "Guardar", "Cerrar sesión", "Descartar", "Quitar la marca",
  "Abrir ficha para OVZ", "Copiar", "Reintentar" and pagination.
- **Secondary / Ghost / Link:** a Superficie fill, a transparent button that fills with Gris 200 on
  hover, and Rojo Enlace text underlined on hover.
- **Destructive (danger):** white fill with a Rojo Problema border and text, Rojo Tinte on hover.
  Never a solid fill, so it can't be mistaken for the primary action.
- **Focus / Active:** a Rojo Enlace border plus a solid 3px Rojo Enlace ring (`ring-ring`, 100%),
  square, the same on every variant. On press the button shifts down 1px. When disabled it is at
  50% opacity and doesn't respond to the pointer.

- **Destructive usage:** both steps of an irreversible action use the danger button: "Rechazar"
  and its confirming "Sí, rechazar" (also "Descartar y cerrar" for an unsent WhatsApp reply and
  "Sí, quitar la marca"). The confirmation's safe option comes first and takes focus.

### Badges (state)
The signature component, since state is the main thing the queue communicates.
- **Style:** a square 24px-tall tag with 12px/500 text, 8px horizontal padding, a transparent
  border, and an optional 14px icon on the left (`[&>svg]:size-3.5`, 6px gap) that takes the
  pair's text color.
- **Variants:** `success`, `warning` and `danger` use the exact semantic pairs above. `outline` is
  the **neutral pair** (`bg-neutral text-neutral-foreground`, no visible border) for values that
  aren't a problem or that the frontend doesn't know; `neutral` is the same pair under an explicit
  name. `default` (Rojo Acción), `secondary`, `destructive`, `ghost` and `link` exist but aren't
  used for state.
- **Icons by trámite state** (`iconoEstadoTramite` in `etiquetas.ts`, drawn by `IconoTramite`, always
  `aria-hidden` with a `data-icono`; `BadgeEstadoTramite` puts it first, so the accessible name is
  still only the label). Labels and pairs did not change:

  | State | Icon (lucide) | Pair |
  |---|---|---|
  | `PENDIENTE_EXTRACCION` | `sparkles` | aviso |
  | `PENDIENTE_REVISION` | `eye` | aviso |
  | `APROBADO` ("Aprobado · falta OVZ") | `check` | aviso |
  | `HECHO_EN_OVZ` | `circle-check` | éxito |
  | `RECHAZADO` | `x` | peligro |
  | Discarded (`RECHAZADO` + `SIN_TRAMITE`, "Descartado") | `archive` | neutro |
  | `EN_PROCESO`, `EJECUTADO_OVZ`, `ERROR_OVZ` (reserved) and unknown | none | as before |

  Resolution and role badges carry no icon.
- **Always a label:** a badge shows the Spanish label from `features/tramites/etiquetas.ts`, never
  the raw enum, and is rendered through `BadgeEstadoTramite`, `BadgeResolucionCrotal` or
  `BadgeRolContacto`. Meaning is never carried by color alone.
- **Trámite state:** aviso for `PENDIENTE_EXTRACCION`, `PENDIENTE_REVISION`, `APROBADO` ("Aprobado ·
  falta OVZ") and `EN_PROCESO`; éxito for `HECHO_EN_OVZ` ("Hecho en OVZ": the gestor says they typed it
  into OVZ themselves) and `EJECUTADO_OVZ`; peligro for `ERROR_OVZ` and `RECHAZADO`. In the queue the
  badge is all there is: who marked it and when live in the dialog and the ficha, never in the row.
  The queue's state filter offers Todos, the two pending states, "Aprobado · falta OVZ", "Hecho en OVZ"
  and "Rechazado"; the automation states (`EN_PROCESO`, `EJECUTADO_OVZ`, `ERROR_OVZ`, escalón 2
  (modo asistido)) keep their labels but stay out of the filter until they can happen.
- **Crotal resolution:** "En inventario" success; "Varios animales coinciden" and "Falta la
  explotación" warning; "No está en el inventario" `outline`, since it can still be approvable (the
  backend decides). A `NO_ENCONTRADO` crotal whose written value is incomplete (`completo: false`
  from the backend) reads "No está en el inventario · incompleto" in warning, because it can't be
  approved. The frontend never classifies crotales itself: `presentacionCrotal` only reads
  `completo`, and a missing `completo` stays `outline`. `BadgeResolucionCrotal` takes the whole
  crotal.
- **Contact role:** Titular `success`, Empleado `outline`. A role is information, not a problem, so
  it never uses warning or danger.
- **Subscription state:** there is no subscription badge any more. Payment is no longer handled in
  the app (it moved to the Ganera landing page), so the Facturación page that showed it is gone; the
  state reaches the user only through the subscription banner (see Alerts / Subscription banner).
- **"Sin guardar":** an `outline` (neutral pair) badge with a dashed, 25%-ink border. It marks an
  edited field or crotal row until the backend answers, replacing the saved badge, so badges never
  predict what the backend will say.

### Cards / Containers
- **Corner Style:** square (radius 0).
- **Background:** Blanco on the Fondo page.
- **Shadow Strategy:** none (see Elevation & Depth).
- **Border:** 1px Gris Borde (`border border-border`), on cards and on table containers
  (`border border-border bg-card`). A `CardHeader` with `.border-b` gets a 2px divisor.
- **Internal Padding:** 16px, or 12px for `size="sm"`. `CardFooter` is a Superficie strip at 50%
  above a top border (unused by the current screens).
- **Metric card:** an uppercase-by-CSS 12px eyebrow label above a 30px/700 number.

### Inputs / Fields
- **Style:** 36px tall by default (40px in the review dialog: explotación, tipo and crotal fields;
  44px on Login with a leading 18px Gris 700 icon), a 1px Gris Input stroke (3:1 or more on every
  surface), **white fill** (`bg-card`, so a field stands out on Fondo), square, 10px horizontal
  padding. Placeholders are Gris Texto (never v2's `#7D7979`, 4.30:1). `Label` is 13px/500 in ink.
- **Focus:** a Rojo Enlace border plus a solid 3px Rojo Enlace ring, matching buttons.
- **Error / Disabled:** `aria-invalid` gives a Rojo Problema border and ring. Disabled fields are at 50%
  opacity with a faint input-colored fill.
- **Select:** the trigger matches the input (white, square, 36px; 38px for the queue's estado
  filter, 40px in the dialog, 30px for `size="sm"`). The popup is a white square panel with a 1px
  Gris Borde ring and `shadow-lg`. Its trigger label comes from a render-function child, so the user
  sees a label, never a raw enum value.
- **Combobox** (base-ui `Combobox`, the explotación picker): the input matches `Input` (40px,
  white), with a chevron trigger inside it; the popup is a white square panel with a 1px Gris Borde
  border and `shadow-md`. Options read "REGA · nombre" with the
  ganadero as a muted second line. The search runs in the backend (`GET /explotaciones?q=`), only
  while the popup is open, 300ms after the last keystroke, cancelling the previous request. It shows
  the first 20 results, with a "Hay N coincidencias; escribe para acotar." line when there are more.
  Searching, error (with "Reintentar") and empty states live inside the popup, in one status region.
  The backend search is accent-insensitive and word by word (since 2026-10-04).

### Brand mark
- The mark is `public/ganera-logo.svg`, always rendered through `LogoGanera`
  (`shared/brand/LogoGanera.tsx`) as a CSS mask over `currentColor`, colored with its own token
  (`text-marca`, Rojo Ganera; never `text-primary`). It is 20px in the top bar, 22px in the side
  bar, 20px in the ficha header and 36px above the Login title. On the Login's red panel it is white
  (`text-primary-foreground`, 24px in the mobile band, 28px from `lg`): Rojo Ganera on Rojo Acción
  would vanish.

**The Brand Mark Rule.** The mark is never an `<img>` and never a hex. It is decorative
(`aria-hidden`) wherever visible text already names Ganera. `ganera-logo-rojo.svg` (hard-coded
`#EC3013`) is only for contexts without CSS, such as email or external documents, and is never used
in the UI. The favicon is `favicon-64.png`.

### Navigation
- **One `<header>`, two shapes, no duplicated DOM:** brand → `nav` → user block, in that DOM and
  tab order at every width. From `lg` (1024px) it is a **fixed side bar** 240px wide; below `lg` it
  is the **top bar**. Both in Superficie (`bg-sidebar`) with a 2px divisor (right edge for the side
  bar, bottom edge for the top bar). The brand mark (Rojo Ganera, decorative) is followed by the
  "GANERA" logotype in 700, `tracking-wide`, in Tinta: red as text would fall short (3.47:1), so the
  red stays in the mark.
- **Links, in working order:** Trámites, Ganaderos, Explotaciones. That is, the daily queue, then
  who it belongs to (a Ganadero, then their explotaciones). There is no Facturación link: payment
  is not handled in the app, and `/facturacion` or any unknown route redirects to Trámites. A section
  stays active on its sub-routes (`/ganaderos/:id` marks Ganaderos).
- **Link style:** square, 14px/500, Tinta, each with a decorative lucide icon on the left
  (`clipboard-list`, `users`, `warehouse`; 16px, 18px from `lg`; the accessible name is only the
  text). Inactive links fill with Gris Borde on hover; the active link is a solid Rojo Acción block
  with white text. Transitions are color-only.
- **Side bar (from `lg`):** a 64px brand zone at the top with a 2px divisor under it (22px mark,
  16px logotype); the links as a column of 40px full-width rows with 12px padding around the list
  (which scrolls vertically if it ever has to); and, pinned to the bottom above a 2px divisor, the
  **user card**: white with a Gris Borde border, a 32px initials square (`bg-rojo-100
  text-rojo-800`, 12px/700, `aria-hidden`; `iniciales()` in `shared/layout/iniciales.ts`, NFC,
  letters only, email as fallback, "?" last), the user's name (13px/500, truncated, email in
  `title`), the email (12px, Gris 700, truncated) and "Cerrar sesión" (`outline sm`) full width.
  The content column starts at 240px.
- **Focus:** links show the standard solid 3px Rojo Enlace ring, square (on the active block it
  reads as a darker rim, still 5.91:1 on Superficie); the active one has `aria-current="page"`.
  There is a single `nav` ("Principal") in the DOM for every width, and links come before "Cerrar
  sesión" in tab order.
- **Mobile (below `md`):** two rows in one Superficie bar, with no rule between them. The first row
  holds the brand and, on the right, the initials square, the truncated name (max 96px, email in
  `title`) and "Cerrar sesión"; the email is hidden. The second row is the link strip: full-bleed to
  the screen edges with 16px inner padding, scrolling horizontally inside itself (hidden scrollbar,
  `overscroll-x-contain`), never the page. The hint that there is more is the last link cut at the
  edge: no fades, no arrows. Links are 36px tall. The active link is brought into view by setting
  the strip's `scrollLeft` (on load, on navigation and when the strip resizes), and a link that
  receives keyboard focus is brought into view the same way, never with
  `scrollIntoView`, so the page never scrolls vertically.
- **From `md` to `lg`:** one row: brand, links, then the user block pushed right (initials, name up
  to 160px over the email up to 160px, both truncated with the full address in `title`, and
  "Cerrar sesión"). A long name and email don't overflow at 768 or 1023px.
- **Out of scope by decision:** Ctrl K search, bell, counters, collapsing the side bar, and the
  gestoría block v2 puts at the bottom of its side bar (the user card takes that place).

### Login
- **From `lg`:** a two-column grid. The left column is a **Rojo Acción panel** (`bg-primary`, white
  text, first in the DOM, no heading and nothing focusable): the white mark and "GANERA" at the top,
  and at the bottom the sentence "Los mensajes de WhatsApp de tus ganaderos llegan como trámites
  listos para revisar." (20px, 4.74:1). The right column centers the form on Fondo.
- **Below `lg`:** the panel shrinks to a 56px red band with the white mark and "GANERA"; the sentence
  is hidden, and the form sits under the band (top-aligned, 40px from it).
- **Form:** no card, at most 400px wide, 16px side gutters (24px from `sm`). The 36px Rojo Ganera
  mark, then the only `h1`, "Entrar en Ganera" (32px/700), the muted description, the Email and
  Contraseña fields (44px, white, with a decorative mail/lock icon inside on the left), the error
  Alert when there is one, and "Entrar" (48px, full width, arrow on the right; its name is exactly
  "Entrar" / "Entrando…"). The error is the uniform "No se ha podido iniciar sesión" / "Email o
  contraseña incorrectos." in the problem pair, with its icon; it never says why.
- **Out of scope by decision:** Google sign-in, password recovery, help by WhatsApp, a show-password
  toggle, sign-up links.

### Text links
- **Recipe:** Rojo Enlace text (`text-enlace`) that underlines on hover or keyboard focus, with a
  40% Rojo Enlace underline 4px below the text, and the standard 3px focus ring (square). It lives
  once in `CLASE_ENLACE` (`shared/ui/enlace.ts`) and is used everywhere outside table columns:
  the ganadero detail (index, `tel:` links, back link), the empty-state links and the dialog's
  notices.
- **Table variant:** links that form a column inside a table rest in Tinta (`text-foreground`) and
  turn Rojo Enlace, with the same underline, only on hover or keyboard focus (same focus ring).
  It lives in `CLASE_ENLACE_TABLA` (`shared/ui/enlace.ts`) and is used for the queue's `#id`
  button, the ganadero name in Ganaderos and the ganadero link in Explotaciones. Red on every row
  would fill the table with red and read as an error; the color appears where the pointer is.
- **Phone numbers** are `tel:` links with the exact E.164 value, preceded by a 14px phone icon.
  They are grouped for reading (`+34 612 345 678`) only for Spanish 9-digit numbers; other numbers
  are shown as stored.
- **Back link:** a detail page opens with a small link to its list: 14px, a 14px left arrow, and
  the list's name ("← Ganaderos").

### Alerts / Subscription banner
- **Style:** square, 14px text. The default variant is white with a 1px Gris Borde border. The
  destructive variant is the **problem pair**: a Rojo Tinte fill with Rojo Problema title and
  description (9.80:1) and a transparent border, and every error alert opens with a 16px
  `CircleAlertIcon` (decorative, `aria-hidden`, first child so the alert grid places it): login
  errors, load errors, the import error, the dialog's action notices (409/403) and the
  `ERROR_OVZ` reason. The subscription banner keeps its `TriangleAlertIcon`. Neutral alerts ("El
  trámite ha cambiado de estado", "Aviso de pago") carry no new icon.
- **Banner use:** `SuscripcionBanner` renders the alert across the content column (under the top
  bar below `lg`; from `lg`, at the top of the column right of the side bar), with only a bottom
  border: every banner (also the load error) is square with the 2px divisor rule
  (`rounded-none border-x-0 border-t-0 border-b-2 border-b-divisor`, `CLASE_BANNER`); without it the
  destructive banners were told apart from the Fondo only by the Rojo Tinte fill (about 1.02:1). It uses the destructive variant for states that block approvals
  (`TRIAL_EXPIRADO_SIN_PAGO`, `SUSPENDIDA`, no Suscripción; title "No puedes aprobar trámites ahora
  mismo") and the default variant for `IMPAGO_GRACIA` (title "Aviso de pago"). It informs and never
  blocks navigation. **It has no action:** no button, link or `mailto:` — the app no longer takes
  payments, so each description closes by asking the user to contact Ganera ("Ponte en contacto
  con Ganera para regularizar la suscripción", "…para regularizarla", "…para regularizarlo cuanto
  antes", "…para activarla"), without repeating the verb earlier in the message. The only banner with an action is the
  load-error one, with its "Reintentar". The approval `403` in the review dialog follows the same
  rule: the backend's `motivo` verbatim, with no link.
- **Load errors:** a failed load shows a destructive Alert with a title, the message (from
  `mensajeDeError`, or the backend's `motivo` verbatim) and an `outline sm` "Reintentar". Stale data
  is cleared rather than left looking current, and pagination stays visible so the user can move
  off a failing page. No load fails silently.

### Tables
- A white container with a 1px Gris Borde border (square). 14px body text. A 40px header row in
  11px/500 Gris 700, uppercase by CSS with 0.08em tracking, closed by a 2px divisor and with no
  hover. Body rows at least 56px tall (`h-14`), with Gris Borde rules and a 4% ink fill on hover.
  Numbers (`#id`, REGA, crotales, counts) are tabular.
- **Tipo column** (queue): a 16px Gris 700 lucide icon before the label in 500
  (`iconoTipoTramite` in `etiquetas.ts`): `log-in` Alta por nacimiento, `log-out` Baja por muerte,
  `truck` Solicitud de movimiento, `arrow-down-to-line` Confirmación de entrada, `list-ordered`
  Declaración de censo, `tag` Demora de crotalización. "Sin determinar" and an unknown type have no
  icon. The Estado column's badge carries the state icon (see Badges).
- **Button columns:** the Explotaciones "Ver animales" column is headed "Animales", visible from
  `sm` and right-aligned with its buttons (`sr-only` below `sm`, same element and text).
- **Rows that open something** (the trámites queue): the first cell holds a real button styled as
  a table link (`CLASE_ENLACE_TABLA`: Tinta at rest, Rojo Enlace on hover or focus; `#N`, tabular,
  `aria-label` "Revisar trámite #N"). The whole row stays clickable with
  the mouse, and the row highlights while its button has keyboard focus.
- **Sortable headers:** a text button in the `th` with a small arrow icon, ink on the active column
  (up/down) and Gris Texto on the others (up-down), with a Superficie fill on hover. Only the active
  column carries `aria-sort`. Clicking the active column reverses it; clicking another starts
  ascending and returns to page 1. Sort only on columns the backend whitelists.
- **Loading:** a skeleton, not text. Superficie (`bg-muted`) bars, square, pulsing only
  when motion is allowed: 16px in table cells, 5 skeleton rows for a list (a number cell is a short
  right-aligned bar), 12px for an inline value still loading (a REGA in the queue). On a Superficie strip
  the bars use `bg-foreground/10`, since `bg-muted` would vanish there. Bars are `aria-hidden`; one
  always-mounted `role="status"`, outside any `aria-busy` element, announces the load and is
  emptied afterwards. A reload over existing data dims the rows to 60% instead.
- **Empty states teach:** they say why the list is empty (naming the active filter) and where the
  data comes from, with a link there ("Se crean al importar el Excel de explotaciones." → "Ir a
  Explotaciones").
- **Overflowing cell content:** the queue shows two crotales and a "+N más" button
  (`aria-expanded`) that expands the rest inside the row; it never opens a tooltip, which doesn't
  work on touch.
- **Origin marker (WhatsApp, B1):** a 14px speech-bubble icon in Gris Texto next to the `#N` button,
  outside it (the button keeps its own `aria-label`), with a `title` for the mouse and "Recibido por
  WhatsApp" announced once, as hidden text. It is not a badge: badges are states.
- **Extraction line (B1):** a 12px line under the state badge, only while the trámite is pending
  (`PENDIENTE_EXTRACCION` or `PENDIENTE_REVISION`): "Extracción en curso" in Gris Texto with no icon
  (work in progress, not a problem); "No se ha podido extraer" and "Mensaje sin texto" with a
  decorative warning icon in the warning text color (`text-warning-foreground`). In the Crotales cell,
  "N descartados" uses the same treatment. Amber, never red: a failed extraction is pending work, not
  an error. No new column; labels live in `etiquetas.ts`.

### Record sections
- A detail page has one white section per child record (square, 1px Gris Borde border, no nested
  cards), stacked with a 16px gap. The header strip (16px by 12px padding, Gris Borde bottom rule)
  holds a 16px title: the identifier in 700 weight with tabular numerals, then ` · nombre` in 400.
  The body lists rows divided by Gris Borde. An action strip closes the section with the card-footer
  treatment: a 50% Superficie fill and a top hairline. (Ganaderos took only the global tokens in
  phase 1, no redesign of its own.)
- **Index:** with more than 3 sections, a compact inline index of links (`REGA · nombre`, wrapping,
  16px by 6px gaps), labeled as a `nav`, sits between the header and the first section. Following a
  link (or a `#explotacion-N` deep link, once the data arrives) scrolls to the section and moves
  focus to its title.
- **Name / value / badge rows** align in columns: on desktop the name gets up to 18rem and the value
  11rem, then the badge; on mobile the name and value share a line and the badge wraps below.

### Disclosure
- A ghost `sm` button with a chevron that turns 180° in 200ms (no transition under reduced motion),
  with `aria-expanded` and `aria-controls`. It sits flush with the content edge (its negative margin
  equals its own padding). The panel is collapsed by default and mounts its content, and makes its
  request, only when opened.
- "Ver animales" opens inline on the Superficie strip: the footer of a Ganadero section, or a full-width
  expansion row under the Explotaciones row (the open row loses its bottom rule so both read as one
  strip). Inside, no border, ring or card.
- **Crotal grid:** an ordered list in an auto-fill grid (`minmax(8rem,1fr)`), 14px tabular, with a
  muted count above ("N animales") and a compact pager below. The last digits are ink at 500 and the
  prefix is Gris Texto: the one place a value is visually split, mirroring how Contactos quote
  crotales on WhatsApp.

### Not found
- A 404 on a detail route (another gestoría's record and a nonexistent one are deliberately
  indistinguishable) shows the normal page title ("Ganadero no encontrado"), one muted sentence
  ("Este ganadero no existe o no es de tu gestoría.") and a single recovery link ("Volver a …"). The
  top back link is hidden in this state. Malformed ids are treated as not found without calling the
  API.

### Dialog (trámite review)
- **Shape:** a two-column "collation" modal over a Gris 900 scrim at 50% with a slight blur. From
  `md` it is a square white panel up to `max-w-4xl` with `shadow-lg` (the only layer that has it),
  anchored 8dvh from the top (so it doesn't jump when its content grows) and at most 84dvh tall.
  Below `md` it fills the screen and the columns stack.
- **Structure:** a fixed header ("Trámite #N" in 700, tabular, and the state badge with its icon)
  over a 2px divisor, a scrolling body, and a fixed **white footer with a 2px divisor on top**
  holding the actions (the same in the editable, `APROBADO` and `HECHO_EN_OVZ` footers). The WhatsApp
  message column is a quote on Fondo with a 1px Gris Borde border, under the title "Mensaje de
  WhatsApp" in the table-header style (11px/500, uppercase by CSS, Gris 700), sticky on desktop; the
  editable data (explotación combobox, tipo select, crotal rows; all 40px, white, Gris 600 stroke)
  sits on the right. The close X comes last in the DOM.
- **Footer buttons:** "Aprobar" is the only solid red; "Guardar" is `outline`; "Rechazar" and "Sí,
  rechazar" are the danger button; "Descartar" stays `outline`. No "Nada llega a OVZ.net…" shield
  and nothing from escalón 2.
- **Focus never sticks to the footer:** the scrolling body has `scroll-pb-3` and its focusables
  `scroll-mb-3`. `scroll-margin` alone only acts when focus causes a scroll; the container's
  `scroll-padding` covers the "fits exactly above the footer" case. Measured in Chromium and Firefox
  at 1440/1024/768/375px: Tab through the body leaves every focused control at least 16px above the
  footer. (Firefox also stops on the scrolling body itself, a native tab stop for scroll containers.)
- **Crotal rows:** one row per crotal with its input (500, tabular), the resolution badge (or "Sin
  guardar"), the resolved full crotal on its own line when it differs, and a remove button, plus
  "Añadir crotal". Rows are divided by Gris Borde. The badge column has a fixed minimum width so the
  rows don't shift.
- **Editable only in `PENDIENTE_REVISION`;** any other state is read-only with no review actions.
  `APROBADO` keeps a footer with "Abrir ficha para OVZ" and "Marcar como hecho en OVZ" (with its hint);
  `HECHO_EN_OVZ` shows the "Hecho en OVZ" strip in the body and a footer with "Quitar la marca" and
  "Abrir ficha para OVZ" (see Ficha para OVZ for both patterns). The only mentions of OVZ in the
  dialog are those buttons, the hint, the state badge and the strip; the success notices after marking
  ("Marca guardada.", "Marca quitada.") don't name OVZ.
  With unsaved changes, Aprobar and Rechazar are disabled with "Guarda antes de aprobar". While a
  request is in flight, every action is disabled and the active button reads "Guardando…",
  "Aprobando…", "Rechazando…", "Marcando…" or "Quitando la marca…". Nothing in the dialog implies
  anything happens in OVZ.net.
- **Inline confirmation:** the action bar swaps for a named `role="group"` with the question first,
  then [safe option, focused] [danger option]. Esc cancels. Used for Rechazar, for "Quitar
  la marca" and for closing with unsaved changes. Never a second modal.
- **Bands** ("Hecho en OVZ" in the success pair, the discarded band in the neutral pair) are square.
- **"Descartar" (SIN_TRAMITE):** only in the dialog's footer, next to Rechazar, and only when the backend says `descartable` (the queue just shows the neutral line "Parece un mensaje sin trámite"); one click, no confirmation, because "Deshacer" in the neutral-pair band (`bg-neutral text-neutral-foreground`) of a discarded trámite undoes it with no time limit; its badge is "Descartado" (neutral pair, `archive` icon).
- **Action notices:** an error is a destructive Alert with a per-action title and the backend's
  `motivo` verbatim, persistent and dismissible. Success is the exact `success` pair with a check
  icon inside an always-mounted `role="status"`. A follow-up reload failure is a secondary muted
  line with a "Reintentar" link. After an uncertain approve/reject (network error or 5xx) whose
  reload shows another closed state, a neutral (non-destructive) Alert says "El trámite ha cambiado
  de estado".
- **Extraction notices (B1):** facts about the data, not results of an action, so they sit after the
  action notices, carry no close button and no live `role` (they are read in order after the focused
  title; an `alert` would interrupt it and repeat on every reload). Failed extraction, a message
  without text and discarded identifiers use the exact warning pair (`bg-warning` /
  `text-warning-foreground`) with a decorative warning icon; "Extracción en curso" is neutral. They show
  only while the trámite is pending, and a failed-extraction notice **stays after the reviewer fixes
  and saves** the trámite: it records that the AI didn't extract it, which is still true. Don't
  "fix" that by hiding it on save.

### Responder por WhatsApp (review dialog)
- **Where:** only in the review dialog (never in the queue or the ficha), as a "WhatsApp" section under
  the original message, and only when the detail brings a `whatsapp` block (trámites that arrived by
  WhatsApp). With the section present, the message column stops being sticky (it could outgrow the
  dialog). Replying never touches the trámite: it works with unsaved edits and doesn't change the
  `version`.
- **Recipient and window:** one line "{nombre} · {teléfono enmascarado}" exactly as the backend sends
  it. An open window is a muted line "Puedes escribirle hasta el dd/mm/aaaa, hh:mm" (Madrid time); with
  less than one hour left it becomes the exact warning pair, "La ventana se cierra a las hh:mm". When the
  backend gives `motivoNoDisponible` (24 h passed, contact inactive, state, not WhatsApp) that text is a
  fixed warning-pair notice with no live `role` (like the extraction notices) and there is **no**
  reply button — never a disabled button without an explanation.
- **Vocabulary:** the honesty word list applies to the whole dialog, this section included, so nothing
  visible says "enviado/enviada". Response states (`etiquetas.ts`): "Enviando…" and "Aceptado por
  WhatsApp" neutral pair (`outline`), "Entregado" and "Leído" `success`, "No ha llegado" `danger`, "Sin
  confirmar" `warning`. Buttons: "Responder por WhatsApp" and "Enviar por WhatsApp", both
  `outline` ("Aprobar" stays the dialog's only solid red). Only the `HECHO_EN_OVZ` proposed text mentions OVZ ("ya está hecho en OVZ").
- **History:** "Respuestas por WhatsApp", chronological; each item is a square row with a Gris Borde border, with date (Madrid),
  who, the state badge on the right, the text with its line breaks (breaking anywhere at 375 px) and the
  `motivoError` muted below. Hidden when empty.
- **Inline panel:** like the inline confirmations, never a second modal: a square panel on Fondo with a Gris Borde border and the
  `Textarea` labelled "Respuesta" (white and square like `Input`, solid `ring-ring` focus), a muted hint (the
  backend's own 400 texts: "Completa el hueco ___ antes de enviar.", empty, too long) and the "N / 1600"
  counter, then [Cancelar] [Enviar por WhatsApp]. On open, focus goes to the text with the `___` gap
  selected (typing replaces it), or to the end. Enviar is disabled while the text has a problem and when
  the window has closed by the device clock with the panel open (the warning already says so). While a
  request runs it reads "Enviando…" and is only `aria-disabled` (same look, clicks ignored): a real
  `disabled` would drop the keyboard focus to `body`. After a request error, focus goes to the panel's
  error Alert (`tabIndex={-1}`, like the ficha's), which also brings it into view.
- **Esc and closing:** with the proposal untouched, Esc (or a click outside) closes only the panel and the
  X closes the dialog. Once the text differs from the proposal, Esc, a click outside and the X all ask
  inline, in place of the panel's buttons: "Tienes una respuesta sin enviar." [Seguir escribiendo]
  (focused; back to the text) [Descartar y cerrar] (destructive; closes the dialog, and if the trámite
  also has unsaved edits their own "Tienes cambios sin guardar." follows). A second Esc withdraws the
  question. If the trámite turns "no encontrado" the panel no longer counts and the dialog closes as usual.
- **Results:** accepted → a discreet muted line with a check in an always-mounted `role="status"`
  ("Respuesta aceptada por WhatsApp."); not delivered (`FALLIDO`) → destructive Alert with
  `CircleAlertIcon`, "La respuesta no ha llegado" and the backend's reason; `SIN_CONFIRMAR` → warning
  pair inside the status region. Request errors stay in the panel (text kept) as a destructive Alert; a
  network error or 5xx without reason says "No se ha podido comprobar si ha salido. Puedes reintentar: no
  se mandará dos veces." and the retry reuses the same idempotency key.

### Ficha para OVZ (auxiliary window)
- **What it is:** `/tramites/:id/ovz`, a companion window the gestor keeps next to OVZ.net
  (360–480 px wide) while typing a trámite there by hand. Ganera sends nothing to OVZ; the page says so
  in one muted line ("Para copiar en OVZ. Ganera no envía nada.").
- **Shell:** no app nav bar. Inside `RequireAuth`, outside `AppLayout`: a sticky Superficie header closed by a 2px divisor,
  with the 20px brand mark, "Ficha para OVZ", the `#id` (tabular, muted) and the state badge, then a
  single column up to `max-w-xl`, 16px gutters, 20px gap.
- **Order is OVZ's order:** title = the OVZ form name (700), "En OVZ: " plus the menu path, then the
  manual's deadlines as a quiet list (Info icon, Gris Texto; never an `Alert`). The WhatsApp message
  is a disclosure, open by default, with the quote on Fondo and a Gris Borde border (as in the dialog). Then record sections (square white cards with a Gris Borde border and Gris Borde row rules): "Entrar en
  OVZ" (titular, NIF, REGA) and one section per OVZ block, in `fichaOvz.ts` order.
- **Field rows** (`dl`): the exact OVZ label in Gris Texto 14px with an ink `*` (500) for mandatory, not red: solid
  red only acts (and "obligatorio" for screen readers); the value below in ink, 500, tabular, breaking anywhere; Copiar
  (outline `sm`, 30px) on the right. Missing data is plain Gris Texto, "Falta · {pista}", never amber or red:
  most fields are missing until B2, and coloring them would turn the page into alarms. A crotal shows
  what was written ("escrito: 1234") under the resolved value, or inside the missing text.
- **Copy:** the button always reads "Copiar" (WCAG 2.5.3: its name, "Copiar {etiqueta}", contains
  the visible text); success swaps the icon to a check for 2s and is announced in a polite live region.
  A copied row keeps a small ink check before its label until reload (memory only, nothing saved).
  Without the Clipboard API the value appears in a read-only `Input`, selected and focused, with
  "Selecciona el texto y cópialo (Ctrl+C o ⌘C)".
- **States (D4):** only `APROBADO` and `HECHO_EN_OVZ` copy. `PENDIENTE_REVISION` is a preview: a neutral-pair
  strip (Eye icon) "Vista previa: aprueba el trámite antes de pasarlo a OVZ." and no Copiar buttons.
  Rejected, extracting, no type, unknown type, censo and 404 use the "Not found" pattern (title, one
  sentence, "Volver a trámites"); a discarded one shows that pattern inside a neutral-pair band ("Descartado
  como mensaje sin trámite"). `HECHO_EN_OVZ` is the same ficha in consultation mode: Copiar stays
  (to check what is in OVZ) under the "Hecho en OVZ" strip.
- **"Hecho en OVZ" strip:** the record that the person already did it in OVZ, so nobody types it twice.
  The exact success pair (`bg-success` / `text-success-foreground`), square, 14px/500 text with a
  decorative `CircleCheckIcon`: "Hecho en OVZ por {nombre} el {dd/mm/aaaa, hh:mm}" (Madrid time,
  `fechaHechoEnOvz` in `features/tramites/hechoEnOvz.ts`; without a name, "Hecho en OVZ el …").
  Never red, never an `Alert`, no live `role` (it is a fact of the data). In the ficha it sits at the
  top, before the form title, with "Quitar la marca" (outline `sm`) inside the strip; in the dialog it
  opens the body, above the two columns. The person is always the subject: never "registrado",
  "enviado", "ejecutado" or "procesado".
- **"Marcar como hecho en OVZ":** primary button, no confirmation (it only records what the person
  did, and it can be undone). In the ficha it closes the form (after the last section, above a 2px
  divisor), in `APROBADO` only; in the dialog it is the primary action of the `APROBADO` footer, right of
  "Abrir ficha para OVZ". Its hint, always visible and tied with `aria-describedby`, reads "Púlsalo
  cuando lo hayas terminado tú en OVZ. Ganera no envía nada." While the request runs it reads
  "Marcando…" and everything is disabled. A failure is a destructive Alert with the backend's `motivo`
  (or the context's generic text): in the ficha it sits next to the action, titled "No se ha podido
  marcar el trámite" / "No se ha podido quitar la marca"; in the dialog it is the usual action notice,
  titled per action like the others ("No se ha marcado el trámite" / "No se ha quitado la marca"). No
  title names OVZ. A 409 reloads the trámite first, and so does a network error or 5xx (if the reload
  shows the requested state, it counts as done); a 404 becomes "Trámite no encontrado".
- **Focus and announcement in the ficha:** the result is announced in an always-mounted polite
  `role="status"` (sr-only: "Marca guardada." / "Marca quitada."). Focus never drops to the `body`:
  after marking it moves to the strip (`tabIndex={-1}`); on "Sí, quitar la marca" it moves to the strip
  while the request runs, then to "Marcar como hecho en OVZ"; after a failure it moves to the error
  Alert (`tabIndex={-1}`). In the dialog, after marking or removing the mark focus goes to the title, as
  after Aprobar.
- **"Quitar la marca":** outline, confirmed inline like Rechazar: a named `role="group"` with "¿Quitar
  la marca de hecho en OVZ? En OVZ no cambia nada.", then [Cancelar, focused] ["Sí, quitar la marca",
  danger], on the neutral pair in the ficha. Esc and Cancelar return focus to "Quitar la marca".
- **Entry:** from the review dialog footer, "Abrir ficha para OVZ" (outline, external-link icon) when
  approved or done in OVZ, and "Vista previa de la ficha" (ghost) next to Rechazar while pending. Both call
  `window.open` with a fixed window name and no `noopener`, so the window inherits the session; their
  accessible name adds "(se abre en otra ventana)". The preview is disabled while there are unsaved
  edits (it would show what is saved), like Aprobar.

## Do's and Don'ts

### Do:
- **Do** define every color as a CSS variable in `index.css` and use it through its Tailwind token
  (`bg-primary`, `text-success-foreground`).
- **Do** keep Fondo (`#f3f2f2`) as the page background and put content on white cards or
  bordered white table containers.
- **Do** show state with the domain badge components (`BadgeEstadoTramite`,
  `BadgeResolucionCrotal`, `BadgeRolContacto`), whose label and variant come from `etiquetas.ts`.
- **Do** keep one 20px/700 page title per screen, with a muted 14px subtitle or count under it.
- **Do** use the solid Rojo Enlace focus ring (a 3px `ring-ring` at 100% plus a Rojo Enlace border) on every interactive
  control, and `CLASE_ENLACE` for every text link
  (`CLASE_ENLACE_TABLA` for link columns inside tables).
- **Do** give every load a visible loading, error (with "Reintentar") and empty state, and show
  the backend's `motivo` verbatim when there is one.
- **Do** announce loading and results through an always-mounted `role="status"` outside any
  `aria-busy` element.
- **Do** use Gris 700 (`text-muted-foreground`) for every secondary text, hint, placeholder and
  table header.
- **Do** write labels in sentence case and get capitals from CSS (`uppercase`), as in table headers.
- **Do** separate zones with the 2px divisor (`border-2 border-divisor` on the side that needs it)
  and frame data surfaces with a 1px Gris Borde border.
- **Do** keep a single solid-red button per screen or footer: the main action. Secondary actions
  are `outline`, irreversible ones the danger button.

### Don't:
- **Don't** introduce a new palette, accent color or typeface. The brand identity is fixed unless
  Antonio asks.
- **Don't** hard-code hex values or arbitrary colors in components (hex lives only in `index.css`;
  a ramp step such as `bg-gris-200` or `text-rojo-800` is a token, a literal `#…` is not).
- **Don't** use a translucent grey or ink as text (`text-foreground/50`, v2's 55/60% inks, `#7D7979`
  for small text): it falls below AA. Secondary text is Gris 700.
- **Don't** type a label in capitals ("TRÁMITE"); uppercase only by CSS.
- **Don't** put Rojo Acción (600) as text on any grey (4.25:1 on Fondo, 3.91:1 on Superficie). Rojo
  Ganera only for the mark or a headline of 24px or more.
- **Don't** add a radius (`rounded-*`) to new code: the system is square.
- **Don't** use Rojo Acción as text or in a badge, fill a problem with solid red, or bring in the
  landing's coral `accent-2`. Solid red acts; dark tinted red warns.
- **Don't** lighten the focus ring or the primary hover with opacity (`ring-ring/50`,
  `bg-primary/80`): both fall below their contrast minimum.
- **Don't** add ambient shadows to cards or page surfaces. Floating menus (Combobox and
  Select popups) get `shadow-md`; only the dialog panel gets `shadow-lg`.
- **Don't** derive state colors by opacity. Use the exact success/warning/danger pairs.
- **Don't** show a raw enum value, or a badge that predicts what the backend will decide: until a
  change is saved, the row says "Sin guardar".
- **Don't** open a second modal to confirm something inside a dialog; confirm inline in the
  action bar.
- **Don't** add a metric card, kicker or uppercase eyebrow to new screens.
- **Don't** treat the `.dark` block in `index.css` as a designed dark theme. It holds the untouched
  shadcn neutral defaults (including a blue `--sidebar-primary`) and nothing applies the `dark`
  class. The same goes for the grayscale `--chart-*` tokens, which are scaffold leftovers with no
  current use.
