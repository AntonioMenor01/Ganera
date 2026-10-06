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
  blanco: "#ffffff"
  exito-fondo: "#eaf3de"
  exito-texto: "#27500a"
  aviso-fondo: "#faeeda"
  aviso-texto: "#854f0b"
typography:
  headline:
    fontFamily: "Archivo Variable, system-ui, sans-serif"
    fontSize: "1.25rem"
    fontWeight: 800
    lineHeight: 1.4
  metric:
    fontFamily: "Archivo Variable, system-ui, sans-serif"
    fontSize: "1.875rem"
    fontWeight: 800
    lineHeight: 1.2
  title:
    fontFamily: "Archivo Variable, system-ui, sans-serif"
    fontSize: "1rem"
    fontWeight: 600
    lineHeight: 1.375
  body:
    fontFamily: "Archivo Variable, system-ui, sans-serif"
    fontSize: "0.875rem"
    fontWeight: 400
    lineHeight: 1.43
  label:
    fontFamily: "Archivo Variable, system-ui, sans-serif"
    fontSize: "0.75rem"
    fontWeight: 600
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
    backgroundColor: "{colors.rojo-accion}"
    textColor: "{colors.blanco}"
    rounded: "{rounded.lg}"
    height: "32px"
    padding: "0 10px"
  button-primary-hover:
    backgroundColor: "{colors.rojo-enlace}"
  button-outline:
    backgroundColor: "{colors.fondo}"
    textColor: "{colors.tinta}"
    rounded: "{rounded.lg}"
    height: "32px"
    padding: "0 10px"
  button-outline-hover:
    backgroundColor: "{colors.superficie}"
  button-destructive:
    backgroundColor: "rgba(124, 20, 5, 0.1)"
    textColor: "{colors.rojo-problema}"
    rounded: "{rounded.lg}"
    height: "32px"
    padding: "0 10px"
  card:
    backgroundColor: "{colors.blanco}"
    textColor: "{colors.tinta}"
    rounded: "{rounded.xl}"
    padding: "{spacing.md}"
  input:
    textColor: "{colors.tinta}"
    rounded: "{rounded.lg}"
    height: "32px"
    padding: "4px 10px"
  nav-bar:
    backgroundColor: "{colors.superficie}"
    textColor: "{colors.tinta}"
    padding: "16px 24px" # from md; 12px 16px 4px below md
  nav-link:
    textColor: "{colors.gris-texto}"
    rounded: "{rounded.lg}"
    padding: "6px 10px"
  nav-link-hover:
    backgroundColor: "{colors.gris-borde}"
    textColor: "{colors.tinta}"
  nav-link-active:
    backgroundColor: "{colors.rojo-accion}"
    textColor: "{colors.blanco}"
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
    backgroundColor: "{colors.rojo-tinte}"
    textColor: "{colors.rojo-problema}"
    rounded: "{rounded.pill}"
    height: "20px"
    padding: "2px 8px"
---

# Design System: Ganera

<!-- Records the visual system as it is implemented today (frontend/src/index.css,
     frontend/src/components/ui/ and the patterns the feature screens share), brought up to date
     after the new brand colours and typography (2026-10-05, plan
     docs/superpowers/plans/2026-10-04-colores-y-tipografia.md). The palette comes from the Ganera
     landing (https://ganera-web.vercel.app/css/tokens.css) and the typeface is Archivo: both are
     fixed brand commitments (see PRODUCT.md); don't alter them unless Antonio asks. -->

## Overview

**Creative North Star: "The Red-Stamp Ledger"**

Ganera looks like a well-kept office ledger that carries the brand's red stamp: a light warm-grey
canvas, white sheets laid on it with hairline edges, near-black ink, and one vivid red that means
"this is the action". It is a working tool for people who process a queue several times a day, so
density is moderate, type is small and even, and decoration is almost absent. The brand shows in
the red, in Archivo's heavy headlines and in restraint, not in ornament.

Hierarchy comes from tone and weight, not from depth. The page is Fondo, the navigation bar the
slightly darker Superficie, and content sits on white cards or tables framed by a fine 10%-ink
ring. Color is reserved for meaning, and red has two jobs that never mix: **solid red acts, dark
tinted red warns.**

**Key Characteristics:**
- A light warm-grey canvas (Fondo), never pure white, with white content surfaces.
- One action red (Rojo Acción, 600) for primary buttons and the active nav item; a deeper red
  (Rojo Enlace, 700) for text links, the primary hover and the focus ring; the vivid Rojo Ganera
  only on the brand mark.
- Problems use a dark red (Rojo Problema, 800), never as a solid fill, and always with text that
  names them; error alerts also carry an icon.
- Semantic state tints come as exact background/text pairs, not opacity-derived colors.
- Flat surfaces separated by hairline borders and tone, with no ambient shadows.
- One typeface (Archivo Variable, self-hosted) throughout, with weight (400/600/800) carrying
  hierarchy.

## Colors

A low-chroma grey-and-ink palette from the Ganera landing, with one red ramp split by job and two
muted state pairs (success, warning). The landing's coral `accent-2` (`#E15B47`) is **not** used in
the app: it would be a third red, almost indistinguishable from the brand one.

### Red (one ramp, four jobs)
- **Rojo Ganera** (`--marca`, `#EC3013`): the brand mark only (`LogoGanera` with `text-marca`).
  Never text: at 16px it gives 3.47:1 on Superficie. As a non-text graphic it passes 3:1 on every
  surface (3.47 / 3.76 / 4.20 on Superficie / Fondo / white).
- **Rojo Acción** (`--primary`, `--sidebar-primary`, ramp 600 `#DD2B0F`): the primary button
  ("Aprobar", "Entrar", "Guardar", "Importar Excel") and the active nav link, always as a solid fill
  with white text (`--primary-foreground`, 4.74:1). It also tints text selection (18%, mixed in
  oklab) and colors the caret. Never used as text on its own (4.25:1 on Fondo is short).
- **Rojo Enlace** (`--enlace`, `--primary-hover`, `--ring`, ramp 700 `#AE1800`): text links
  (`CLASE_ENLACE`, `CLASE_ENLACE_TABLA` for link columns inside tables, `Button variant="link"`),
  the primary button's hover fill (white on it, 7.17:1) and the focus ring, always at 100%
  (7.17 / 6.41 / 5.91 on white / Fondo / Superficie).
- **Rojo Problema** (`--destructive`, `--danger-foreground`, ramp 800 `#7C1405`) with **Rojo Tinte**
  (`--danger`, ramp 100 `#FFF2EF`): error alerts (text on white, 10.72:1), the `RECHAZADO` /
  `ERROR_OVZ` badges (9.80:1) and the confirming "Sí, rechazar" button as a 10% tint (8.91:1).

### Neutral
- **Fondo** (`--background`, `#F3F2F2`): the page canvas behind everything, and the fill of outline
  buttons.
- **Superficie** (`--secondary`, `--sidebar`, `--muted`, `--accent`, `#EAE9E9`): the top navigation
  bar, secondary buttons, the hover fill of outline/ghost buttons and table rows (at 50%), the
  card-footer strip, the quoted WhatsApp message and skeleton bars.
- **Blanco** (`--card`, `--popover`, `#FFFFFF`): cards, table containers, dialogs and popovers,
  everything that holds data, so tables and badges get the most contrast.
- **Tinta** (`--foreground` and the neutral `*-foreground`, `#201E1D`): all primary text and the
  "GANERA" logotype (16.60 / 14.86 / 13.70 on white / Fondo / Superficie).
- **Gris Texto** (`--muted-foreground`, ramp 700 `#605D5D`): secondary text, descriptions, counts,
  empty-state messages and inactive nav links (6.52 / 5.83 / 5.38). Ramp 600 would fail (3.55–4.30).
- **Gris Borde** (`--border`, `--sidebar-border`, `--sidebar-accent`, ramp 300 `#D7D3D3`): every
  divider, table rule and table container border, and the hover fill of inactive nav links.
- **Gris Input** (`--input`, ramp 600 `#7D7979`): the stroke of inputs, selects and the combobox,
  the lightest grey of the ramp that reaches 3:1 as a non-text boundary (4.30 on white, 3.85 on
  Fondo, 3.55 on Superficie; ramp 500 gave 2.89 / 2.38).

### Semantic (trámite state)
- **Éxito** (`--success` / `--success-foreground`, unchanged): resolved favorably, meaning
  `APROBADO` and `EJECUTADO_OVZ`.
- **Aviso** (`--warning` / `--warning-foreground`, unchanged): not resolved yet, meaning
  `PENDIENTE_EXTRACCION`, `PENDIENTE_REVISION` and `EN_PROCESO`. The amber stays clearly apart from
  both reds.
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
color at reduced opacity. Add new states by mapping them to one of the three existing pairs, or to
the neutral `outline`, in the domain's constant in `features/tramites/etiquetas.ts`
(`ESTADOS_TRAMITE`, `RESOLUCIONES_CROTAL`, `ROLES_CONTACTO`). Measured contrast (WCAG 2.x, AA at
12px/600): success 8.21:1, warning 5.87:1, danger 9.80:1, and `outline` (ink on whatever surface it
sits on) at least 13.70:1.

## Typography

**Body Font:** Archivo Variable (self-hosted via `@fontsource-variable/archivo`, OFL-1.1, `wght`
100–900, latin and latin-ext by `unicode-range`; fallback `system-ui, sans-serif`). Vite bundles the
`.woff2` files into `assets/`: no request ever goes to Google or a CDN.
**Display/Heading Font:** the same family (`--font-heading` aliases `--font-sans`)

**Character:** a sturdy, slightly wide grotesque, the landing's typeface. Hierarchy comes only from
size and three weights: 800 for headlines, 600 for interface elements, 400 for body. Archivo is a
little wider than the previous Geist; the nav bar, queue badges and columns were checked at 375px.

### Hierarchy
- **Headline** (800, 20px, `text-xl font-extrabold`): one page title per screen ("Cola de
  trámites", "Ganaderos", "Explotaciones", a ganadero's name), followed by a muted 14px subtitle or
  count. The "Ganera" title on Login (16px) and the "GANERA" logotype are also 800.
- **Metric** (800, 30px, `text-3xl`): the single large number in a metric card (the Explotaciones
  count).
- **Title** (600, 16px): card, section and dialog titles.
- **Interface** (600, `font-semibold`): buttons, badges, field labels, table headers, alert titles,
  nav links and the text links on the auth screens.
- **Body** (400, 14px, `text-sm`): the default for tables, cards, forms and alerts. Inputs are 16px
  on mobile and 14px from `md` up.
- **Data emphasis** (500, `font-medium`): kept on purpose for data inside tables and lists (the
  last digits of a crotal, the written crotal in the queue, the queue's `#id` and the ganadero's
  name in its table), so it never competes with the 600 headers. `font-medium` is not redefined
  globally.
- **Label** (600, 12px, uppercase, `tracking-wide`): eyebrow labels above metrics. Badges use
  12px/600 in sentence case. The eyebrow and the metric card belong to the Explotaciones count
  only; newer screens (Ganaderos, the review dialog) deliberately don't use them.
- **Subsection / field label** (600, 14px, Gris Texto, sentence case): a real heading or label
  over the block below it ("Contactos", "Explotación", "Tipo de trámite"). It is never an
  uppercase eyebrow.
- **Data numerals:** códigos REGA, NIF, crotales, phones, counts and pagers use `tabular-nums`
  (Archivo has tabular figures: "1111" and "8888" measure the same).

### Named Rules
**The Single Family Rule.** Archivo Variable only. Hierarchy never comes from a second typeface.

## Layout

The app shell is a full-width **top navigation bar**, not a side panel. The `--sidebar*` token
family styles this horizontal bar, and the brand pass deliberately kept it horizontal. Below it,
`SuscripcionBanner` can insert a full-bleed alert strip, then comes the page content, padded 24px
(`p-6`).

- **Page rhythm:** content stacks vertically in a flex column with a 24px gap (`gap-6`). The page
  header is a row with the title block on the left and its controls (e.g. the estado filter
  `Select`, 224px wide) on the right.
- **Nav bar:** from `md`, one row with 24px horizontal and 16px vertical padding. The brand mark,
  logotype and links sit on the left with a 24px gap between them; the user's email (truncated) and
  the "Salir" button sit on the right. Below `md`, two rows with 16px horizontal padding (see
  Components → Navigation → Mobile).
- **Detail pages** read top to bottom as a file: a back link, the record's title, a muted count,
  then stacked record sections with a 16px gap (see Components → Record sections).
- **Cards:** internal spacing 16px (`--card-spacing`), or 12px for the `sm` size. The metric card
  shrinks to its content (`w-fit`, min 192px).
- **Tables:** full width inside a bordered white container, with 40px header rows and 8px cell
  padding.
- **Auth screen** (Login): a single card at most 384px wide, centered on the Fondo
  canvas, inside a `main`. The card header opens with the 40px brand mark above the "Ganera"
  title, which is the page's `h1`.
- **Responsive:** no custom breakpoints; Tailwind defaults only (`md` switches input text from
  16px to 14px). The page header wraps and a header control (the estado filter) goes full width
  below `sm`. Tables scroll inside their container, never the page. Name cells wrap anywhere
  (`wrap-anywhere`) so a long single-word name can't push numeric columns out. A long header may
  shorten below `sm` ("Expl."), with the full name kept for screen readers, and a column dropped on
  mobile is removed from the markup (`useDesdeSm`), not hidden with CSS, so `colSpan` stays exact.
  The review dialog goes full screen below `md`. The top bar switches to two rows below `md`, with
  its links in a strip that scrolls inside itself, so it never widens the page.

## Elevation & Depth

The system is flat. Depth comes from tone (Fondo canvas, Superficie bar, white surfaces) and from
hairline edges, not from shadows. Cards and dialogs use a 1px ring at 10% ink
(`ring-foreground/10`), and table containers use a 1px Gris Borde border.

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
- **Shape:** gently rounded (`rounded-lg`, about 8.6px), 14px/600 text, 10px horizontal padding.
  Sizes are `xs` 24px, `sm` 28px, default 32px and `lg` 36px, plus square icon variants.
- **Primary:** Rojo Acción fill with white text. On hover the fill turns Rojo Enlace
  (`hover:bg-primary-hover`), never an opacity drop, which would lower the white text's contrast.
- **Outline:** Gris Borde stroke on a Fondo fill, with a Superficie fill on hover (used for "Salir"
  and "Rechazar").
- **Secondary / Ghost / Link:** a Superficie fill, a transparent button that fills with Superficie
  on hover, and Rojo Enlace text underlined on hover.
- **Destructive:** Rojo Problema text on a 10% tint of itself, rising to 20% on hover. Never a
  solid fill, so it can't be mistaken for the primary action.
- **Focus / Active:** a Rojo Enlace border plus a solid 3px Rojo Enlace ring (`ring-ring`, 100%),
  the same on every variant, "Sí, rechazar" included. On press the button shifts down
  1px. When disabled it is at 50% opacity and doesn't respond to the pointer.

- **Destructive usage:** the first step of an irreversible action (Rechazar) is `outline`; only
  the button that confirms it ("Sí, rechazar") is `destructive`.

### Badges (state)
The signature component, since state is the main thing the queue communicates.
- **Style:** a 20px-tall pill with 12px/600 text, 8px horizontal padding and a transparent border.
- **Variants:** `success`, `warning` and `danger` use the exact semantic pairs above. `outline`
  (ink text, Gris Borde stroke, no fill) is the neutral for values that aren't a problem or that
  the frontend doesn't know. `default` (Rojo Acción), `secondary`, `destructive`, `ghost` and `link`
  exist but aren't used for state.
- **Always a label:** a badge shows the Spanish label from `features/tramites/etiquetas.ts`, never
  the raw enum, and is rendered through `BadgeEstadoTramite`, `BadgeResolucionCrotal` or
  `BadgeRolContacto`. Meaning is never carried by color alone.
- **Trámite state:** aviso for `PENDIENTE_EXTRACCION`, `PENDIENTE_REVISION` and `EN_PROCESO`;
  éxito for `APROBADO` and `EJECUTADO_OVZ`; peligro for `ERROR_OVZ` and `RECHAZADO`.
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
- **"Sin guardar":** an `outline` badge with a dashed, 25%-ink border. It marks an edited field or
  crotal row until the backend answers, replacing the saved badge, so badges never predict what the
  backend will say.

### Cards / Containers
- **Corner Style:** 12px (`rounded-xl`).
- **Background:** Blanco on the Fondo page.
- **Shadow Strategy:** none; a 1px ring at 10% ink (see Elevation & Depth).
- **Border:** the ring on cards; table containers use `rounded-xl border bg-card`.
- **Internal Padding:** 16px, or 12px for `size="sm"`. The footer is a Superficie strip at 50% opacity
  above a top border.
- **Metric card:** an uppercase 12px eyebrow label above a 30px/600 number.

### Inputs / Fields
- **Style:** 32px tall, a 1px Gris Input stroke (3:1 or more on every surface), transparent fill (the parent surface shows
  through), `rounded-lg`, 10px horizontal padding. Placeholders are Gris Texto.
- **Focus:** a Rojo Enlace border plus a solid 3px Rojo Enlace ring, matching buttons.
- **Error / Disabled:** `aria-invalid` gives a Rojo Problema border and ring. Disabled fields are at 50%
  opacity with a faint input-colored fill.
- **Select:** the trigger matches the input. The popup is a white panel with a 10% ink ring and
  `shadow-md`. Its trigger label comes from a render-function child, so the user sees a label, never
  a raw enum value.
- **Combobox** (base-ui `Combobox`, the explotación picker): the input matches `Input`, with a
  chevron trigger inside it; the popup is the Select popup. Options read "REGA · nombre" with the
  ganadero as a muted second line. The search runs in the backend (`GET /explotaciones?q=`), only
  while the popup is open, 300ms after the last keystroke, cancelling the previous request. It shows
  the first 20 results, with a "Hay N coincidencias; escribe para acotar." line when there are more.
  Searching, error (with "Reintentar") and empty states live inside the popup, in one status region.
  The backend search is accent-insensitive and word by word (since 2026-10-04).

### Brand mark
- The mark is `public/ganera-logo.svg`, always rendered through `LogoGanera`
  (`shared/brand/LogoGanera.tsx`) as a CSS mask over `currentColor`, colored with its own token
  (`text-marca`, Rojo Ganera; never `text-primary`). It is 20px in the nav bar and 40px on the auth cards.

**The Brand Mark Rule.** The mark is never an `<img>` and never a hex. It is decorative
(`aria-hidden`) wherever visible text already names Ganera. `ganera-logo-rojo.svg` (hard-coded
`#EC3013`) is only for contexts without CSS, such as email or external documents, and is never used
in the UI. The favicon is `favicon-64.png`.

### Navigation
- **Style:** the horizontal top bar in Superficie with a Gris Borde bottom border. The brand mark
  (20px, Rojo Ganera, decorative) is followed by the "GANERA" logotype in 800 weight,
  `tracking-wide`, in Tinta: red as text would fall short (3.47:1), so the red stays in the mark.
- **Links, in working order:** Trámites, Ganaderos, Explotaciones. That is, the daily queue, then
  who it belongs to (a Ganadero, then their explotaciones). There is no Facturación link: payment
  is not handled in the app, and `/facturacion` or any unknown route redirects to Trámites. A section
  stays active on its sub-routes (`/ganaderos/:id` marks Ganaderos).
- **Link style:** 14px/600, `rounded-lg`, 6px by 10px padding. Inactive links are Gris Texto
  and fill with Gris Borde plus ink text on hover. The active link is a solid Rojo Acción pill with
  white text. Transitions are color-only.
- **Focus:** links show the standard solid 3px Rojo Enlace ring (on the active pill it reads as a
  darker rim, still 5.91:1 on Superficie); the active one has `aria-current="page"`.
  There is a single `nav` ("Principal") in the DOM for every width, and links come before "Salir"
  in tab order.
- **Mobile (below `md`):** two rows in one Superficie bar, with no rule between them. The first row
  holds the brand and "Salir"; the email is hidden. The second row is the link strip: full-bleed to
  the screen edges with 16px inner padding, scrolling horizontally inside itself (hidden scrollbar,
  `overscroll-x-contain`), never the page. The hint that there is more is the last link cut at the
  edge: no fades, no arrows. Links are 36px tall. The active link is brought into view by setting
  the strip's `scrollLeft` (on load, on navigation and when the strip resizes), and a link that
  receives keyboard focus is brought into view the same way, never with
  `scrollIntoView`, so the page never scrolls vertically.
- **From `md`:** one row as described above. The email is truncated with an ellipsis and keeps the
  full address in `title`; its max width (112px at `md`, 320px from `lg`) keeps a long email from
  overflowing at 768px.

### Text links
- **Recipe:** Rojo Enlace text (`text-enlace`) that underlines on hover or keyboard focus, with a
  40% Rojo Enlace underline 4px below the text, and the standard 3px focus ring on a `rounded-sm` corner. It lives
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
- **Style:** `rounded-lg`, a 1px border, 14px text on a white surface. The destructive variant
  turns text Rojo Problema, with no red fill, and every error alert opens with a 16px
  `CircleAlertIcon` (decorative, `aria-hidden`, first child so the alert grid places it): login
  errors, load errors, the import error, the dialog's action notices (409/403) and the
  `ERROR_OVZ` reason. The subscription banner keeps its `TriangleAlertIcon`. Neutral alerts ("El
  trámite ha cambiado de estado", "Aviso de pago") carry no new icon.
- **Banner use:** `SuscripcionBanner` renders the alert full-bleed under the nav bar, with no
  radius and only a bottom border. It uses the destructive variant for states that block approvals
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
- 14px body text, 40px header row in 600 weight and ink color, Gris Borde row rules, and a 50%
  Superficie fill on row hover.
- **Rows that open something** (the trámites queue): the first cell holds a real button styled as
  a table link (`CLASE_ENLACE_TABLA`: Tinta at rest, Rojo Enlace on hover or focus; `#N`, tabular,
  `aria-label` "Revisar trámite #N"). The whole row stays clickable with
  the mouse, and the row highlights while its button has keyboard focus.
- **Sortable headers:** a text button in the `th` with a small arrow icon, ink on the active column
  (up/down) and Gris Texto on the others (up-down), with a Superficie fill on hover. Only the active
  column carries `aria-sort`. Clicking the active column reverses it; clicking another starts
  ascending and returns to page 1. Sort only on columns the backend whitelists.
- **Loading:** a skeleton, not text. Superficie (`bg-muted`) bars on a `rounded-sm` corner, pulsing only
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
- A detail page has one white section per child record (12px radius, 10% ink ring, no nested
  cards), stacked with a 16px gap. The header strip (16px by 12px padding, Gris Borde bottom rule)
  holds a 16px title: the identifier in 600 weight with tabular numerals, then ` · nombre` in 400.
  The body lists rows divided by Gris Borde. An action strip closes the section with the card-footer
  treatment: a 50% Superficie fill, a top hairline and rounded bottom corners.
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
- **Shape:** a two-column "collation" modal over a light 10% black scrim with a slight blur. From
  `md` it is a white panel up to `max-w-4xl` with a 12px radius and a 10% ink ring, anchored 8dvh
  from the top (so it doesn't jump when its content grows) and at most 84dvh tall. Below `md` it
  fills the screen and the columns stack.
- **Structure:** a fixed header (title + state badge), a scrolling body and a fixed footer on a 50%
  Superficie strip with the actions. The WhatsApp message column is a Superficie quoted block, sticky on
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
- **Extraction notices (B1):** facts about the data, not results of an action, so they sit after the
  action notices, carry no close button and no live `role` (they are read in order after the focused
  title; an `alert` would interrupt it and repeat on every reload). Failed extraction, a message
  without text and discarded identifiers use the exact warning pair (`bg-warning` /
  `text-warning-foreground`) with a decorative warning icon; "Extracción en curso" is neutral. They show
  only while the trámite is pending, and a failed-extraction notice **stays after the reviewer fixes
  and saves** the trámite: it records that the AI didn't extract it, which is still true. Don't
  "fix" that by hiding it on save.

## Do's and Don'ts

### Do:
- **Do** define every color as a CSS variable in `index.css` and use it through its Tailwind token
  (`bg-primary`, `text-success-foreground`).
- **Do** keep Fondo (`#f3f2f2`) as the page background and put content on white cards or
  bordered white table containers.
- **Do** show state with the domain badge components (`BadgeEstadoTramite`,
  `BadgeResolucionCrotal`, `BadgeRolContacto`), whose label and variant come from `etiquetas.ts`.
- **Do** keep one 20px/600 page title per screen, with a muted 14px subtitle or count under it.
- **Do** use the solid Rojo Enlace focus ring (a 3px `ring-ring` at 100% plus a Rojo Enlace border) on every interactive
  control, and `CLASE_ENLACE` for every text link
  (`CLASE_ENLACE_TABLA` for link columns inside tables).
- **Do** give every load a visible loading, error (with "Reintentar") and empty state, and show
  the backend's `motivo` verbatim when there is one.
- **Do** announce loading and results through an always-mounted `role="status"` outside any
  `aria-busy` element.

### Don't:
- **Don't** introduce a new palette, accent color or typeface. The brand identity is fixed unless
  Antonio asks.
- **Don't** hard-code hex values or arbitrary colors in components.
- **Don't** use Rojo Acción as text or in a badge, fill a problem with solid red, or bring in the
  landing's coral `accent-2`. Solid red acts; dark tinted red warns.
- **Don't** lighten the focus ring or the primary hover with opacity (`ring-ring/50`,
  `bg-primary/80`): both fall below their contrast minimum.
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
