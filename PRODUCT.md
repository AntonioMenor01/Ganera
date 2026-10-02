# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users

Employees of Spanish cattle-ranching **gestorías** (agricultural agencies), the only people who
log in. Two confirmed usage situations:

- **Administrativos at a desk** work through the trámite queue several times a day alongside
  their other paperwork: open a trámite, check the original WhatsApp message against what was
  extracted, fix the Explotación or crotales if needed, approve or reject.
- **The gestor/titular** supervises the same queue and sometimes approves from a phone, away from
  the office.

Not users of the interface: **Ganaderos** (the gestoría's clients) and their **Contactos**
(titulares or trabajadores). They never log in; they only send WhatsApp messages.

## Product Purpose

Ganera automates bovine paperwork (trámites: alta, baja, censo, movimiento, demora) for
gestorías. Today a gestoría gets these requests from its ganaderos and types each one into OVZ.net,
the official government system, by hand. With Ganera, the ganadero's WhatsApp message becomes a
trámite ready for review, and the gestoría approves it with one click.

Success means the gestoría stops typing into OVZ.net. Its job becomes reviewing and approving,
with no loss of control over what reaches the Administración.

## Positioning

The ganadero already sends WhatsApp messages, so Ganera turns that message into a trámite ready to
review and approve with one click, with no typing in OVZ.net. AI speeds up the work, but the
gestoría always reviews and approves: nothing reaches the Administración without its sign-off.

## Operating Context

- **Input channel:** WhatsApp through one Twilio number shared by every gestoría. Contactos write
  in natural language and refer to animals by the last digits of their crotal
  (e.g. `ES123456789012`).
- **Review queue:** every trámite passes through human review. Ambiguous cases (more than one
  possible Explotación, an unmatched or duplicate crotal) arrive as `PENDIENTE_REVISION` with the
  missing data empty, and an employee resolves them in the UI.
- **Trámite states:** `PENDIENTE_EXTRACCION`, `PENDIENTE_REVISION`, `APROBADO`, `EN_PROCESO`,
  `EJECUTADO_OVZ`, `ERROR_OVZ`, `RECHAZADO`. Errors are always visible and never fail silently.
- **Inventory loading:** today the only way to create Ganaderos, Explotaciones and Animales is the
  Excel importer (`.xlsx`). A read-only sync from OVZ.net is planned but blocked.
- **Billing:** Stripe, priced by number of active Explotaciones, with a 15-day trial. Customers sign
  up by client-count range. Some subscription states make the account read-only: browsing still
  works, but approving is blocked.

## Capabilities and Constraints

- **Built:** login; public gestoría self-registration; Explotaciones dashboard plus Excel import;
  Trámites queue with state filter, review modal and Aprobar/Rechazar; Facturación status page
  with a subscription banner.
- **Not built, deliberately:** a manual Ganadero/Explotación creation form (Excel is the only
  creation path) and an OVZ-credentials onboarding screen (deferred).
- **Hard rule:** no trámite runs against OVZ.net until an employee explicitly clicks "Aprobar",
  whatever the AI's confidence.
- **Honesty rule:** real OVZ.net execution isn't implemented yet. For now "Aprobar" only changes
  the state, so the UI must never suggest that anything happens in OVZ.net.
- **Read-only accounts:** a subscription without approval rights never hides the rest of the app.
  Only the approve action is blocked, and the UI shows the real reason.
- **Login and registration errors are uniform:** they never reveal why a login or a sign-up was
  rejected.
- **Stack in place:** Vite + React 19 + TypeScript + Tailwind v4 + shadcn/ui (base-nova style,
  `@base-ui/react`), `lucide-react` icons, Geist Variable font. Session authentication in
  `sessionStorage`: it survives a reload and ends when the tab is closed.
- **Terminology** stays in the domain's Spanish: Gestoría, Ganadero, Explotación, Animal, Crotal,
  Contacto, Trámite, código REGA, OVZ.net.
- **Undecided:** per-employee portfolio (`modoCartera`, data model only), roles, a WhatsApp reply
  loop to the ganadero, and a real admin panel.

## Brand Commitments

- **Name:** Ganera. The app shows the brand mark next to the text logotype "GANERA" in the
  navigation bar, and above the "Ganera" title on the login and registration screens.
- **Official logo:** `frontend/public/ganera-logo.svg` (`fill="currentColor"`, so it takes its
  color from a token). Variants: `ganera-logo-verde.svg` (fixed green, for contexts without CSS),
  `ganera-logo-512.png` and `favicon-64.png` (the favicon).
- **Voice:** Spanish, addressing the user with tú ("tu suscripción").
- **Fixed visual identity (only change it when Antonio asks):** primary green `#1F3D2B`, cream
  background `#F7F6F1`, sidebar `#F1F0E8`, white cards with a thin border, and colored badges per
  trámite state. Colors are always defined as Tailwind tokens or CSS variables, never hard-coded
  in individual components. Don't propose a different palette or typeface unless asked.
- `logo.jpg` (at the repo root) is **not** the logo and is not committed.

## Evidence on Hand

- **There are no pilot gestorías yet.** The first one is still being sought.
- **No customers, pilots, testimonials, customer logos, usage figures or public price exist.**
  Never invent any of them. (The Stripe `Price` isn't created yet either.)

## Product Principles

1. **The human approves, always.** Design exists to make reviewing fast and reliable, never to
   hide or skip the approval step.
2. **Tell the truth about system state.** Every state, error and restriction is shown with its
   real cause. Never imply actions that don't happen, and never let anything fail silently.
3. **Let employees check the extraction against the original message.** The WhatsApp message and
   what was extracted from it have to be easy to compare side by side.
4. **Restrict, don't block.** A read-only account still browses everything and loses only what
   the backend actually blocks.
5. **The domain's language comes first.** Use the terms gestorías already use (crotal, REGA,
   explotación), not generic SaaS jargon.
