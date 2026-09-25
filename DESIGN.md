---
name: Ganera
description: Review-and-approve workspace for Spanish cattle-ranching gestorías.
colors:
  verde-monte: "#1f3d2b"
  crema-papel: "#f7f6f1"
  paja-clara: "#f1f0e8"
  paja-hover: "#e5e3d6"
  lino: "#eeede4"
  tinta: "#1c1b17"
  gris-oliva: "#6b6b60"
  borde-lino: "#e6e4da"
  blanco-tarjeta: "#ffffff"
  exito-fondo: "#eaf3de"
  exito-texto: "#27500a"
  aviso-fondo: "#faeeda"
  aviso-texto: "#854f0b"
  peligro-fondo: "#fcebeb"
  peligro-texto: "#791f1f"
typography:
  headline:
    fontFamily: "Geist Variable, sans-serif"
    fontSize: "1.25rem"
    fontWeight: 600
    lineHeight: 1.4
  metric:
    fontFamily: "Geist Variable, sans-serif"
    fontSize: "1.875rem"
    fontWeight: 600
    lineHeight: 1.2
  title:
    fontFamily: "Geist Variable, sans-serif"
    fontSize: "1rem"
    fontWeight: 500
    lineHeight: 1.375
  body:
    fontFamily: "Geist Variable, sans-serif"
    fontSize: "0.875rem"
    fontWeight: 400
    lineHeight: 1.43
  label:
    fontFamily: "Geist Variable, sans-serif"
    fontSize: "0.75rem"
    fontWeight: 500
    lineHeight: 1.33
    letterSpacing: "0.025em"
rounded:
  md: "6.86px"
  lg: "8.57px"
  xl: "12px"
  pill: "22.3px"
spacing:
  xs: "4px"
  sm: "8px"
  card-sm: "12px"
  md: "16px"
  lg: "24px"
components:
  button-primary:
    backgroundColor: "{colors.verde-monte}"
    textColor: "{colors.crema-papel}"
    rounded: "{rounded.lg}"
    height: "32px"
    padding: "0 10px"
  button-outline:
    backgroundColor: "{colors.crema-papel}"
    textColor: "{colors.tinta}"
    rounded: "{rounded.lg}"
    height: "32px"
    padding: "0 10px"
  button-outline-hover:
    backgroundColor: "{colors.lino}"
  card:
    backgroundColor: "{colors.blanco-tarjeta}"
    textColor: "{colors.tinta}"
    rounded: "{rounded.xl}"
    padding: "{spacing.md}"
  input:
    textColor: "{colors.tinta}"
    rounded: "{rounded.lg}"
    height: "32px"
    padding: "4px 10px"
  nav-bar:
    backgroundColor: "{colors.paja-clara}"
    textColor: "{colors.tinta}"
    padding: "16px 24px"
  nav-link:
    textColor: "{colors.gris-oliva}"
    rounded: "{rounded.lg}"
    padding: "6px 10px"
  nav-link-hover:
    backgroundColor: "{colors.paja-hover}"
    textColor: "{colors.tinta}"
  nav-link-active:
    backgroundColor: "{colors.verde-monte}"
    textColor: "{colors.crema-papel}"
  badge-success:
    backgroundColor: "{colors.exito-fondo}"
    textColor: "{colors.exito-texto}"
    rounded: "{rounded.pill}"
    height: "20px"
    padding: "2px 8px"
  badge-warning:
    backgroundColor: "{colors.aviso-fondo}"
    textColor: "{colors.aviso-texto}"
    rounded: "{rounded.pill}"
    height: "20px"
    padding: "2px 8px"
  badge-danger:
    backgroundColor: "{colors.peligro-fondo}"
    textColor: "{colors.peligro-texto}"
    rounded: "{rounded.pill}"
    height: "20px"
    padding: "2px 8px"
---

# Design System: Ganera

<!-- Records the visual system as it is implemented today (frontend/src/index.css and
     frontend/src/components/ui/). It describes the current system and proposes no changes. The
     palette and typeface are fixed brand commitments (see PRODUCT.md): don't alter them unless
     Antonio asks. -->

## Overview

**Creative North Star: "The Field Office Ledger"**

Ganera looks like a well-kept office ledger in a rural gestoría: warm cream paper instead of
clinical white, one deep forest green that says "this is the action", and white cards laid on the
paper with barely visible edges. It is a working tool for people who process a queue several
times a day, so density is moderate, type is small and even, and decoration is almost absent.
The brand shows in the palette and restraint, not in ornament.

Hierarchy comes from tone, not from depth. The page is cream, the navigation bar a slightly darker
straw, and content sits on white cards or tables framed by a fine 10%-ink ring. Color is reserved
for meaning: green for primary actions and the active section, and three soft paired tints for
trámite state.

**Key Characteristics:**
- A warm cream canvas, never pure white, with white content surfaces.
- One accent, the deep forest green, used for primary buttons, the active nav item, links and the
  focus ring.
- Semantic state tints come as exact background/text pairs, not opacity-derived colors.
- Flat surfaces separated by hairline borders and tone, with no ambient shadows.
- One typeface (Geist Variable) throughout, with weight rather than family carrying hierarchy.

## Colors

A warm, low-chroma paper-and-ink palette with a single deep green accent and three muted state
pairs.

### Primary
- **Verde Monte** (`--primary`): primary buttons, the active navigation item, the text logotype
  "GANERA" and its dot, text links, and the focus ring (`--ring`). It is the only saturated brand
  color on screen.

### Neutral
- **Crema Papel** (`--background`): the page canvas behind everything. It also serves as the text
  color on green (`--primary-foreground`), so the pairing reads as cream-on-green rather than
  white-on-green.
- **Paja Clara** (`--secondary`, `--sidebar`): the top navigation bar and secondary buttons.
- **Paja Hover** (`--sidebar-accent`): hover fill for inactive navigation links.
- **Lino** (`--muted`, `--accent`): hover fill for outline/ghost buttons and table rows (at 50%),
  and the card-footer fill.
- **Tinta** (`--foreground`): all primary text, a warm near-black.
- **Gris Oliva** (`--muted-foreground`): secondary text, descriptions, counts, empty-state
  messages and inactive nav links.
- **Borde Lino** (`--border`, `--input`): every divider, table rule, input stroke and table
  container border.
- **Blanco Tarjeta** (`--card`, `--popover`): cards, table containers, dialogs and popovers.

### Semantic (trámite state)
- **Éxito** (`--success` / `--success-foreground`): resolved favorably, meaning `APROBADO` and
  `EJECUTADO_OVZ`.
- **Aviso** (`--warning` / `--warning-foreground`): not resolved yet, meaning
  `PENDIENTE_EXTRACCION`, `PENDIENTE_REVISION` and `EN_PROCESO`.
- **Peligro** (`--danger` / `--danger-foreground`): resolved unfavorably, meaning `ERROR_OVZ` and
  `RECHAZADO`. `--destructive` uses the same deep red as `peligro-texto` for destructive buttons
  and alerts, applied as a 10% tint behind red text.

### Named Rules
**The Token-Only Rule.** Every color is a CSS variable in `frontend/src/index.css`, exposed to
Tailwind through `@theme inline`. Components reference tokens (`bg-primary`,
`text-muted-foreground`, `bg-success`) and never a literal hex.

**The One Green Rule.** Verde Monte is the only accent. If something needs to stand out and isn't
a primary action, the active location or a link, it uses weight or a state tint, not more green.

**The Exact Pair Rule.** Each state badge is a hand-picked background/foreground pair, not a
color at reduced opacity. Add new states by mapping them to one of the three existing pairs in
`BADGE_POR_ESTADO` (`features/tramites/types.ts`).

## Typography

**Body Font:** Geist Variable (via `@fontsource-variable/geist`, with a `sans-serif` fallback)
**Display/Heading Font:** the same family (`--font-heading` aliases `--font-sans`)

**Character:** a single neutral, slightly technical grotesque. Hierarchy comes only from size
and weight (400/500/600), which suits a data-dense review tool.

### Hierarchy
- **Headline** (600, 20px, `text-xl`): one page title per screen ("Cola de trámites",
  "Explotaciones", "Facturación"), followed by a muted 14px subtitle or count.
- **Metric** (600, 30px, `text-3xl`): the single large number in a metric card (the Explotaciones
  count).
- **Title** (500, 16px): card titles.
- **Body** (400, 14px, `text-sm`): the default for tables, cards, forms, alerts and buttons
  (buttons at 500). Inputs are 16px on mobile and 14px from `md` up.
- **Label** (500, 12px, uppercase, `tracking-wide`): eyebrow labels above metrics. Badges use
  12px/500 in sentence case.

### Named Rules
**The Single Family Rule.** Geist Variable only. Hierarchy never comes from a second typeface.

## Layout

The app shell is a full-width **top navigation bar**, not a side panel. The `--sidebar*` token
family styles this horizontal bar, and the brand pass deliberately kept it horizontal. Below it,
`SuscripcionBanner` can insert a full-bleed alert strip, then comes the page content, padded 24px
(`p-6`).

- **Page rhythm:** content stacks vertically in a flex column with a 24px gap (`gap-6`). The page
  header is a row with the title block on the left and its controls (e.g. the estado filter
  `Select`, 224px wide) on the right.
- **Nav bar:** 24px horizontal and 16px vertical padding. The logotype and links sit on the left
  with a 24px gap between them; the user's email and the "Salir" button sit on the right.
- **Cards:** internal spacing 16px (`--card-spacing`), or 12px for the `sm` size. The metric card
  shrinks to its content (`w-fit`, min 192px).
- **Tables:** full width inside a bordered white container, with 40px header rows and 8px cell
  padding.
- **Auth screens** (Login, Registro): a single card at most 384px wide, centered on the cream
  canvas.
- **Responsive:** no custom breakpoints. The layout relies on flex wrapping and Tailwind defaults
  (`md` switches input text from 16px to 14px), and the top bar isn't adapted for narrow screens
  yet.

## Elevation & Depth

The system is flat. Depth comes from tone (cream canvas, straw bar, white surfaces) and from
hairline edges, not from shadows. Cards and dialogs use a 1px ring at 10% ink
(`ring-foreground/10`), and table containers use a 1px Borde Lino border.

### Shadow Vocabulary
- **Floating menu** (`shadow-md`): only the `Select` dropdown popup, the one element that floats
  over content.
- **Modal scrim** (`bg-black/10` + `backdrop-blur-xs`): the dialog overlay, a very light dim with
  a slight blur rather than a dark curtain.

### Named Rules
**The Paper-Flat Rule.** Surfaces at rest have no shadow. Only a transient floating layer (a
dropdown) may cast one.

## Shapes

Softly rounded rectangles on a single radius scale derived from `--radius` (0.5357rem). Cards,
table containers and dialogs use 12px (`rounded-xl`, the value the base was tuned to hit).
Buttons, inputs, alerts and nav links use about 8.6px (`rounded-lg`), and small buttons about
6.9px. Badges are pills. The logotype's dot is the only circle. Edges are always a 1px hairline;
there are no thick strokes and no clipping or angled geometry.

## Components

### Buttons
Compact and quiet, 32px tall by default.
- **Shape:** gently rounded (`rounded-lg`, about 8.6px), 14px/500 text, 10px horizontal padding.
  Sizes are `xs` 24px, `sm` 28px, default 32px and `lg` 36px, plus square icon variants.
- **Primary:** Verde Monte fill with Crema Papel text. On hover the fill drops to 80% opacity.
- **Outline:** Borde Lino stroke on the cream canvas, with a Lino fill on hover (used for "Salir").
- **Secondary / Ghost / Link:** a straw fill, a transparent button that fills with Lino on hover,
  and green underlined text on hover.
- **Destructive:** deep red text on a 10% red tint, rising to 20% on hover.
- **Focus / Active:** a green border plus a 3px green ring at 50%. On press the button shifts down
  1px. When disabled it is at 50% opacity and doesn't respond to the pointer.

### Badges (trámite state)
The signature component, since state is the main thing the queue communicates.
- **Style:** a 20px-tall pill with 12px/500 text, 8px horizontal padding and a transparent border.
- **State:** `success`, `warning` and `danger` variants filled with the exact semantic pairs above,
  chosen only through `badgeVarianteDeEstado(estado)`. `default` (green), `secondary`, `outline`,
  `destructive`, `ghost` and `link` also exist but aren't used for state.

### Cards / Containers
- **Corner Style:** 12px (`rounded-xl`).
- **Background:** Blanco Tarjeta on the Crema Papel page.
- **Shadow Strategy:** none; a 1px ring at 10% ink (see Elevation & Depth).
- **Border:** the ring on cards; table containers use `rounded-xl border bg-card`.
- **Internal Padding:** 16px, or 12px for `size="sm"`. The footer is a Lino strip at 50% opacity
  above a top border.
- **Metric card:** an uppercase 12px eyebrow label above a 30px/600 number.

### Inputs / Fields
- **Style:** 32px tall, a 1px Borde Lino stroke, transparent fill (the parent surface shows
  through), `rounded-lg`, 10px horizontal padding. Placeholders are Gris Oliva.
- **Focus:** a green border plus a 3px green ring at 50%, matching buttons.
- **Error / Disabled:** `aria-invalid` gives a deep red border and ring. Disabled fields are at 50%
  opacity with a faint input-colored fill.
- **Select:** the trigger matches the input. The popup is a white panel with a 10% ink ring and
  `shadow-md`. Its trigger label comes from a render-function child, so the user sees a label, never
  a raw enum value.

### Navigation
- **Style:** the horizontal top bar in Paja Clara with a Borde Lino bottom border. The brand mark
  is a 10px Verde Monte dot followed by the "GANERA" logotype in 600 weight, `tracking-wide`, in
  green.
- **Links:** 14px/500, `rounded-lg`, 6px by 10px padding. Inactive links are Gris Oliva text and
  fill with Paja Hover plus ink text on hover. The active link is a solid Verde Monte pill with
  Crema Papel text. Transitions are color-only.
- **Mobile:** no dedicated treatment yet.

### Alerts / Subscription banner
- **Style:** `rounded-lg`, a 1px border, 14px text on a white surface. The destructive variant
  turns text deep red, with no red fill.
- **Banner use:** `SuscripcionBanner` renders the alert full-bleed under the nav bar, with no
  radius and only a bottom border. It uses the destructive variant for states that block approvals
  (`TRIAL_EXPIRADO_SIN_PAGO`, `SUSPENDIDA`, no Suscripción) and the default variant for
  `IMPAGO_GRACIA`. It informs and never blocks navigation.

### Tables
- 14px body text, 40px header row in 500 weight and ink color, Borde Lino row rules, and a 50%
  Lino fill on row hover. Rows that open the review dialog show a pointer cursor. The empty state
  is a single centered row in Gris Oliva.

### Dialog (trámite review)
- A white panel with a 12px radius and a 10%-ink ring over a light 10% black scrim with a slight
  blur, with a close button in the top-right corner.

## Do's and Don'ts

### Do:
- **Do** define every color as a CSS variable in `index.css` and use it through its Tailwind token
  (`bg-primary`, `text-success-foreground`).
- **Do** keep Crema Papel (`#f7f6f1`) as the page background and put content on white cards or
  bordered white table containers.
- **Do** show trámite state with a `Badge` whose variant comes from `badgeVarianteDeEstado`.
- **Do** keep one 20px/600 page title per screen, with a muted 14px subtitle or count under it.
- **Do** use the green focus ring (a 3px ring at 50% plus a green border) on every interactive
  control.

### Don't:
- **Don't** introduce a new palette, accent color or typeface. The brand identity is fixed unless
  Antonio asks.
- **Don't** hard-code hex values or arbitrary colors in components.
- **Don't** add ambient shadows to cards or page surfaces. Only floating menus get `shadow-md`.
- **Don't** derive state colors by opacity. Use the exact success/warning/danger pairs.
- **Don't** treat the `.dark` block in `index.css` as a designed dark theme. It holds the untouched
  shadcn neutral defaults (including a blue `--sidebar-primary`) and nothing applies the `dark`
  class. The same goes for the grayscale `--chart-*` tokens, which are scaffold leftovers with no
  current use.
