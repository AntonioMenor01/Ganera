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
  sm: "5.14px"
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

<!-- Records the visual system as it is implemented today (frontend/src/index.css,
     frontend/src/components/ui/ and the patterns the feature screens share), brought up to date
     after Prompt A2 (2026-10-02). It describes the current system and proposes no changes. The
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
- **Verde Monte** (`--primary`): primary buttons, the active navigation item, the brand mark and
  the "GANERA" logotype, text links, and the focus ring (`--ring`). It also tints text selection
  (18%, mixed in oklab) and colors the text caret in inputs. It is the only saturated brand color on
  screen.

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
color at reduced opacity. Add new states by mapping them to one of the three existing pairs, or to
the neutral `outline`, in the domain's constant in `features/tramites/etiquetas.ts`
(`ESTADOS_TRAMITE`, `RESOLUCIONES_CROTAL`, `ROLES_CONTACTO`). Measured contrast (AA at 12px/500):
success 8.21:1, warning 5.87:1, danger 8.98:1, and `outline` (ink on whatever surface it sits on)
at least 15.9:1.

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
  12px/500 in sentence case. The eyebrow and the metric card belong to the Explotaciones count
  only; newer screens (Ganaderos, the review dialog) deliberately don't use them.
- **Subsection / field label** (500, 14px, Gris Oliva, sentence case): a real heading or label
  over the block below it ("Contactos", "Explotación", "Tipo de trámite"). It is never an
  uppercase eyebrow.
- **Data numerals:** códigos REGA, NIF, crotales, phones, counts and pagers use `tabular-nums`.

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
- **Nav bar:** 24px horizontal and 16px vertical padding. The brand mark, logotype and links sit
  on the left with a 24px gap between them; the user's email and the "Salir" button sit on the
  right.
- **Detail pages** read top to bottom as a file: a back link, the record's title, a muted count,
  then stacked record sections with a 16px gap (see Components → Record sections).
- **Cards:** internal spacing 16px (`--card-spacing`), or 12px for the `sm` size. The metric card
  shrinks to its content (`w-fit`, min 192px).
- **Tables:** full width inside a bordered white container, with 40px header rows and 8px cell
  padding.
- **Auth screens** (Login, Registro): a single card at most 384px wide, centered on the cream
  canvas, inside a `main`. The card header opens with the 40px brand mark above the "Ganera"
  title, which is the page's `h1`.
- **Responsive:** no custom breakpoints; Tailwind defaults only (`md` switches input text from
  16px to 14px). The page header wraps and a header control (the estado filter) goes full width
  below `sm`. Tables scroll inside their container, never the page. Name cells wrap anywhere
  (`wrap-anywhere`) so a long single-word name can't push numeric columns out. A long header may
  shorten below `sm` ("Expl."), with the full name kept for screen readers, and a column dropped on
  mobile is removed from the markup (`useDesdeSm`), not hidden with CSS, so `colSpan` stays exact.
  The review dialog goes full screen below `md`. **The top bar isn't adapted for narrow screens
  yet:** at 375px it still widens the page (a known defect, planned as a small task before the
  pilot).

## Elevation & Depth

The system is flat. Depth comes from tone (cream canvas, straw bar, white surfaces) and from
hairline edges, not from shadows. Cards and dialogs use a 1px ring at 10% ink
(`ring-foreground/10`), and table containers use a 1px Borde Lino border.

### Shadow Vocabulary
- **Floating menu** (`shadow-md`): only the `Select` and `Combobox` popups, the elements that
  float over content.
- **Modal scrim** (`bg-black/10` + `backdrop-blur-xs`): the dialog overlay, a very light dim with
  a slight blur rather than a dark curtain.

### Named Rules
**The Paper-Flat Rule.** Surfaces at rest have no shadow. Only a transient floating layer (a
dropdown) may cast one.

## Shapes

Softly rounded rectangles on a single radius scale derived from `--radius` (0.5357rem). Cards,
table containers and dialogs use 12px (`rounded-xl`, the value the base was tuned to hit).
Buttons, inputs, alerts and nav links use about 8.6px (`rounded-lg`), and small buttons about
6.9px. The smallest step, about 5.1px (`rounded-sm`), is for skeleton bars, the focus corner of
text links and focusable section titles. Badges are pills. Edges are always a 1px hairline; there
are no thick strokes and no clipping or angled geometry.

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

- **Destructive usage:** the first step of an irreversible action (Rechazar) is `outline`; only
  the button that confirms it ("Sí, rechazar") is `destructive`.

### Badges (state)
The signature component, since state is the main thing the queue communicates.
- **Style:** a 20px-tall pill with 12px/500 text, 8px horizontal padding and a transparent border.
- **Variants:** `success`, `warning` and `danger` use the exact semantic pairs above. `outline`
  (ink text, Borde Lino stroke, no fill) is the neutral for values that aren't a problem or that
  the frontend doesn't know. `default` (green), `secondary`, `destructive`, `ghost` and `link`
  exist but aren't used for state.
- **Always a label:** a badge shows the Spanish label from `features/tramites/etiquetas.ts`, never
  the raw enum, and is rendered through `BadgeEstadoTramite`, `BadgeResolucionCrotal` or
  `BadgeRolContacto`. Meaning is never carried by color alone.
- **Trámite state:** aviso for `PENDIENTE_EXTRACCION`, `PENDIENTE_REVISION` and `EN_PROCESO`;
  éxito for `APROBADO` and `EJECUTADO_OVZ`; peligro for `ERROR_OVZ` and `RECHAZADO`.
- **Crotal resolution:** "En inventario" success; "Varios animales coinciden" and "Falta la
  explotación" warning; "No está en el inventario" `outline`, since it can still be approvable (the
  backend decides).
- **Contact role:** Titular `success`, Empleado `outline`. A role is information, not a problem, so
  it never uses warning or danger.
- **Subscription state** (Facturación): ACTIVA and TRIAL success, IMPAGO_GRACIA warning,
  TRIAL_EXPIRADO_SIN_PAGO, SUSPENDIDA and CANCELADA danger, anything else `outline`.
- **"Sin guardar":** an `outline` badge with a dashed, 25%-ink border. It marks an edited field or
  crotal row until the backend answers, replacing the saved badge, so badges never predict what the
  backend will say.

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
- **Combobox** (base-ui `Combobox`, the explotación picker): the input matches `Input`, with a
  chevron trigger inside it; the popup is the Select popup. Options read "REGA · nombre" with the
  ganadero as a muted second line. Filtering is case- and accent-insensitive and every typed word
  must match. The list is capped at 100 options, with a "Hay más de 100 coincidencias: escribe para
  acotar." line when there are more.

### Brand mark
- The mark is `public/ganera-logo.svg`, always rendered through `LogoGanera`
  (`shared/brand/LogoGanera.tsx`) as a CSS mask over `currentColor`, colored with a token class
  (`text-primary`). It is 20px in the nav bar and 40px on the auth cards.

**The Brand Mark Rule.** The mark is never an `<img>` and never a hex. It is decorative
(`aria-hidden`) wherever visible text already names Ganera. `ganera-logo-verde.svg` (hard-coded
`#1F3D2B`) is only for contexts without CSS, such as email or external documents, and is never used
in the UI. The favicon is `favicon-64.png`.

### Navigation
- **Style:** the horizontal top bar in Paja Clara with a Borde Lino bottom border. The brand mark
  (20px, Verde Monte, decorative) is followed by the "GANERA" logotype in 600 weight,
  `tracking-wide`, in green.
- **Links, in working order:** Trámites, Ganaderos, Explotaciones, Facturación. That is, the daily
  queue, then who it belongs to (a Ganadero, then their explotaciones), then the account. A section
  stays active on its sub-routes (`/ganaderos/:id` marks Ganaderos).
- **Link style:** 14px/500, `rounded-lg`, 6px by 10px padding. Inactive links are Gris Oliva text
  and fill with Paja Hover plus ink text on hover. The active link is a solid Verde Monte pill with
  Crema Papel text. Transitions are color-only.
- **Mobile:** no dedicated treatment yet; the bar overflows at 375px (see Layout → Responsive).

### Text links
- **Recipe:** Verde Monte text that underlines on hover or keyboard focus, with a 40% green
  underline 4px below the text, and the standard 3px focus ring on a `rounded-sm` corner. It lives
  once in `CLASE_ENLACE` (`shared/ui/enlace.ts`) and is used everywhere: Ganaderos, the ganadero
  link in Explotaciones, the queue's `#id` button, Login/Registro and the dialog's notices.
- **Phone numbers** are `tel:` links with the exact E.164 value, preceded by a 14px phone icon.
  They are grouped for reading (`+34 612 345 678`) only for Spanish 9-digit numbers; other numbers
  are shown as stored.
- **Back link:** a detail page opens with a small link to its list: 14px, a 14px left arrow, and
  the list's name ("← Ganaderos").

### Alerts / Subscription banner
- **Style:** `rounded-lg`, a 1px border, 14px text on a white surface. The destructive variant
  turns text deep red, with no red fill.
- **Banner use:** `SuscripcionBanner` renders the alert full-bleed under the nav bar, with no
  radius and only a bottom border. It uses the destructive variant for states that block approvals
  (`TRIAL_EXPIRADO_SIN_PAGO`, `SUSPENDIDA`, no Suscripción) and the default variant for
  `IMPAGO_GRACIA`. It informs and never blocks navigation.
- **Load errors:** a failed load shows a destructive Alert with a title, the message (from
  `mensajeDeError`, or the backend's `motivo` verbatim) and an `outline sm` "Reintentar". Stale data
  is cleared rather than left looking current, and pagination stays visible so the user can move
  off a failing page. No load fails silently.

### Tables
- 14px body text, 40px header row in 500 weight and ink color, Borde Lino row rules, and a 50%
  Lino fill on row hover.
- **Rows that open something** (the trámites queue): the first cell holds a real button styled as
  a text link (`#N`, tabular, `aria-label` "Revisar trámite #N"). The whole row stays clickable with
  the mouse, and the row highlights while its button has keyboard focus.
- **Sortable headers:** a text button in the `th` with a small arrow icon, ink on the active column
  (up/down) and Gris Oliva on the others (up-down), with a Lino fill on hover. Only the active
  column carries `aria-sort`. Clicking the active column reverses it; clicking another starts
  ascending and returns to page 1. Sort only on columns the backend whitelists.
- **Loading:** a skeleton, not text. Lino (`bg-muted`) bars on a `rounded-sm` corner, pulsing only
  when motion is allowed: 16px in table cells, 5 skeleton rows for a list (a number cell is a short
  right-aligned bar), 12px for an inline value still loading (a REGA in the queue). On a Lino strip
  the bars use `bg-foreground/10`, since `bg-muted` would vanish there. Bars are `aria-hidden`; one
  always-mounted `role="status"`, outside any `aria-busy` element, announces the load and is
  emptied afterwards. A reload over existing data dims the rows to 60% instead.
- **Empty states teach:** they say why the list is empty (naming the active filter) and where the
  data comes from, with a link there ("Se crean al importar el Excel de explotaciones." → "Ir a
  Explotaciones").
- **Overflowing cell content:** the queue shows two crotales and a "+N más" button
  (`aria-expanded`) that expands the rest inside the row; it never opens a tooltip, which doesn't
  work on touch.

### Record sections
- A detail page has one white section per child record (12px radius, 10% ink ring, no nested
  cards), stacked with a 16px gap. The header strip (16px by 12px padding, Borde Lino bottom rule)
  holds a 16px title: the identifier in 500 weight with tabular numerals, then ` · nombre` in 400.
  The body lists rows divided by Borde Lino. An action strip closes the section with the card-footer
  treatment: a 50% Lino fill, a top hairline and rounded bottom corners.
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
- "Ver animales" opens inline on the Lino strip: the footer of a Ganadero section, or a full-width
  expansion row under the Explotaciones row (the open row loses its bottom rule so both read as one
  strip). Inside, no border, ring or card.
- **Crotal grid:** an ordered list in an auto-fill grid (`minmax(8rem,1fr)`), 14px tabular, with a
  muted count above ("N animales") and a compact pager below. The last digits are ink at 500 and the
  prefix is Gris Oliva: the one place a value is visually split, mirroring how Contactos quote
  crotales on WhatsApp.

### Not found
- A 404 on a detail route (another gestoría's record and a nonexistent one are deliberately
  indistinguishable) shows the normal page title ("Ganadero no encontrado"), one muted sentence
  ("Este ganadero no existe o no es de tu gestoría.") and a single recovery link ("Volver a …"). The
  top back link is hidden in this state. Malformed ids are treated as not found without calling the
  API.

### Dialog (trámite review)
- **Shape:** a two-column "collation" modal over a light 10% black scrim with a slight blur. From
  `md` it is a white panel up to `max-w-4xl` with a 12px radius and a 10% ink ring, anchored 8dvh
  from the top (so it doesn't jump when its content grows) and at most 84dvh tall. Below `md` it
  fills the screen and the columns stack.
- **Structure:** a fixed header (title + state badge), a scrolling body and a fixed footer on a 50%
  Lino strip with the actions. The WhatsApp message column is a Lino quoted block, sticky on
  desktop; the editable data (explotación combobox, tipo select, crotal rows) sits on the right. The
  close X comes last in the DOM.
- **Crotal rows:** one row per crotal with its input, the resolution badge (or "Sin guardar"), the
  resolved full crotal on its own line when it differs, and a remove button, plus "Añadir crotal".
  The badge column has a fixed minimum width so the rows don't shift.
- **Editable only in `PENDIENTE_REVISION`;** any other state is read-only with no action buttons.
  With unsaved changes, Aprobar and Rechazar are disabled with "Guarda antes de aprobar". While a
  request is in flight, every action is disabled and the active button reads "Guardando…",
  "Aprobando…" or "Rechazando…". Nothing in the dialog implies anything happens in OVZ.net.
- **Inline confirmation:** the action bar swaps for a named `role="group"` with the question first,
  then [safe option, focused] [destructive-tint option]. Esc cancels. Used for Rechazar and for
  closing with unsaved changes. Never a second modal.
- **Action notices:** an error is a destructive Alert with a per-action title and the backend's
  `motivo` verbatim, persistent and dismissible. Success is the exact `success` pair with a check
  icon inside an always-mounted `role="status"`. A follow-up reload failure is a secondary muted
  line with a "Reintentar" link. After an uncertain approve/reject (network error or 5xx) whose
  reload shows another closed state, a neutral (non-destructive) Alert says "El trámite ha cambiado
  de estado".

## Do's and Don'ts

### Do:
- **Do** define every color as a CSS variable in `index.css` and use it through its Tailwind token
  (`bg-primary`, `text-success-foreground`).
- **Do** keep Crema Papel (`#f7f6f1`) as the page background and put content on white cards or
  bordered white table containers.
- **Do** show state with the domain badge components (`BadgeEstadoTramite`,
  `BadgeResolucionCrotal`, `BadgeRolContacto`), whose label and variant come from `etiquetas.ts`.
- **Do** keep one 20px/600 page title per screen, with a muted 14px subtitle or count under it.
- **Do** use the green focus ring (a 3px ring at 50% plus a green border) on every interactive
  control, and `CLASE_ENLACE` for every text link.
- **Do** give every load a visible loading, error (with "Reintentar") and empty state, and show
  the backend's `motivo` verbatim when there is one.
- **Do** announce loading and results through an always-mounted `role="status"` outside any
  `aria-busy` element.

### Don't:
- **Don't** introduce a new palette, accent color or typeface. The brand identity is fixed unless
  Antonio asks.
- **Don't** hard-code hex values or arbitrary colors in components.
- **Don't** add ambient shadows to cards or page surfaces. Only floating menus get `shadow-md`.
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
