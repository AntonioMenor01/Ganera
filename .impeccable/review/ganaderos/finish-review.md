disposition: fix

Substitution: this review was run by a fresh general-purpose agent following degraded/finish-reviewer.md, because no dedicated finish-reviewer agent exists. Unread inputs: PRODUCT.md body (existence confirmed only), DESIGN.md past line 250, the test files, api.ts and types.ts. No QUALITY BAR card, approved comp or build state exist, because this is a code-led build. The error/Reintentar states were checked in source only, since no capture covers them. Known and out of scope: the top nav overflows at 390px and widens the page (DESIGN.md defers this).

## persistence
pass. PRODUCT.md exists. DESIGN.md predates this build and matches the built world: cream canvas, white surfaces with a 10% ring or a Borde Lino border, one green, Geist only. The build is code-led, so no state.json, comp round or approval record applies. The named captures (10 files, 1440 and 390) all exist, are valid and match their filenames.

## fidelity
There is no comp, so fidelity is judged against the direction contract.
- TYPE: match. Geist Variable only. The title is 20px/600, section titles 16/500 and body 14. Weight carries the hierarchy, and NIF, REGA and phones use tabular numerals.
- MATERIAL: match. Surfaces are flat white with `ring-foreground/10` or a 1px border, with no faked physicality and no shadows.
- GROUND: match. OWN-WORLD names Crema Papel (#f7f6f1), and the captures read as that warm cream with no drift. This was judged by eye, because no pixel sampling tool was used.
- Nav "Ganaderos" link, active on the list and on the detail: match.
- List (name link, tabular NIF, right-aligned count, sort on name/NIF only with lucide arrows, "12 ganaderos" count): match at 1440. At 390 it is **missing**: the Explotaciones column is scrolled out of the table container, NIF is clipped at the edge, and nothing signals that the row scrolls (mobile_ganaderos.png).
- "← Ganaderos" back link: match.
- Header with name, gray tabular NIF and "N explotaciones": match. At 390 the NIF wraps under a long name, an acceptable adaptation (responsive reflow).
- REGA·nombre index shown only when there are more than 3 explotaciones: match (Antonio's answer). It stacks vertically at 390, an acceptable adaptation.
- Section header (REGA + name): match.
- Contact row (name, `tel:` link with Phone icon, role badge; Titular = success, Empleado = outline): match on content, contradicted on composition at 1440. The name sits at x≈40 and the phone at x≈1200, about 1,100px of eye travel. The phone column is also not aligned: phones shift about 20px between Titular and Empleado rows because the trailing badge widths differ (desktop_ganaderos_1.png, desktop_ganaderos_2-abrir.png).
- "Contactos" subsection label: acceptable adaptation. It names the list for `aria-labelledby` and is not a kicker (no heading follows it).
- "Ver animales" disclosure, collapsed by default and opening to an empty slot: match (Antonio's answer, and Task 8 fills the slot). Its button is inset 2px off the content edge, and its pressed fill bleeds 7px left of the column (desktop_ganaderos_2-abrir.png).
- Empty state with 0 explotaciones: match on copy, but it does not teach. The list's empty state points to the importer, while this one is a dead end.
- 404 "Ganadero no encontrado" plus a link back: match. The "← Ganaderos" link and "Volver a Ganaderos" are two back links stacked 110px apart.
- Explotaciones page linking to the ganadero: match (source only, no capture).

## ceiling
- The system's card-footer treatment (`CardFooter`: `bg-muted/50`, `rounded-b-xl`, `border-t`, DESIGN.md "Lino … the card-footer fill") is not used on the "Ver animales" footer, which is `border-t` only. The world's own device for "this strip is an action row, not content" is left unused.
- The list's loading state is a text row, not the pulsing-bar skeleton the detail page already uses.
- Everything else is reached: tabular numerals, the focus ring on links, headers, h2 targets and rows, the exact-pair badges, and one green.

## material_fixes
1. Contract/FORM: the contract says "Seed key: none". Per check 4, a contract without a seed key is a material finding. Corroborate the skip in FORM by quoting the exact A2 plan decision 14 line and Antonio's 2026-09-29 answers that close the direction, or run the concept roll. (.impeccable/surfaces/frontend-src-features-ganaderos.md, FORM block.)
2. Coverage at 390px: the Explotaciones count is invisible (mobile_ganaderos.png). In `frontend/src/features/ganaderos/GanaderosPage.tsx:155`, change the name cell to `<TableCell className="whitespace-normal break-words">` so long names wrap. At `:125`, shorten the header on narrow screens: `<TableHead className="text-right"><span className="sm:hidden" aria-hidden="true">Expl.</span><span className="max-sm:sr-only">Explotaciones</span></TableHead>`. All three columns then fit in 342px. Recapture mobile_ganaderos.png to confirm the count column is visible.
3. Contact row composition at desktop: `GanaderoDetallePage.tsx:256`. Replace the flex-wrap row with a grid so the phone sits beside the name and phones align down the column:
   `<li className="grid grid-cols-[minmax(0,1fr)_auto] items-center gap-x-4 gap-y-1 py-2 text-sm sm:grid-cols-[minmax(0,18rem)_11rem_auto] sm:justify-start">`
   Keep the name span without `flex-1 basis-40`, give the phone `<a>` `justify-self-start`, and give the badge wrapper `col-span-2 justify-self-start sm:col-span-1` on mobile, so the badge still wraps under the name at 390 as it does now.
4. Disclosure alignment: `GanaderoDetallePage.tsx:231`. Change `className="-mx-2"` to `className="-mx-2.5"` to match the button's `px-2.5` (size sm), so the "Ver animales" text sits on the same left edge as "Contactos" and the names.
5. Disclosure footer uses the world's card-footer fill: `GanaderoDetallePage.tsx:227`. Change `className="border-t px-4 py-2"` to `className="rounded-b-xl border-t bg-muted/50 px-4 py-2"` (same vocabulary as `CardFooter`).
6. Empty state teaches: `GanaderoDetallePage.tsx:145`. Replace it with `<div className="text-muted-foreground"><p>Este ganadero no tiene explotaciones. Se añaden al importar el Excel.</p><Link to="/explotaciones" className={cn("mt-1 inline-block", CLASE_ENLACE)}>Ir a Explotaciones</Link></div>`, mirroring GanaderosPage.tsx:142-145.
7. 404 has a duplicate back link: `GanaderoDetallePage.tsx:71-77` with `:85-87`. Render the top "← Ganaderos" link only when `vista.estado !== "no-encontrado"` (wrap it in that condition), and keep "Volver a Ganaderos" as the single recovery action under the message.
8. List loading uses a skeleton, not text: `GanaderosPage.tsx:129-135`. Replace the "Cargando ganaderos…" row with 5 rows of three cells, each `<div className="h-4 w-full max-w-40 rounded-sm bg-muted motion-safe:animate-pulse" />` (the count cell `ml-auto w-6`). Keep a `<span className="sr-only" role="status">Cargando ganaderos…</span>` for screen readers.

## keep
Keep the plain ledger reading of the file (header, index, one stacked white section per explotación with a REGA-led title, Titular/Empleado exact-pair badges, tabular numerals, green only on links and focus). Don't add metric cards, extra color or nested cards while fixing.

---

# Verdict pass (2026-09-30, recaptures over the same 10 files)

## verdict
1. FORM seed key: resolved. FORM now cites new-work.md's rule for precise extensions, quotes A2 plan decision 14 and quotes Antonio's 2026-09-29 answers verbatim; "Seed key: none (no se tiró)" is now corroborated.
2. Mobile count column: resolved. mobile_ganaderos.png shows Nombre / NIF / "Expl." inside the 342px container, the count visible on every row, and "Explotaciones Hermanos García S.L." wrapping to two lines. Nothing is clipped.
3. Contact row composition: resolved. In desktop_ganaderos_1.png and _2-abrir.png the phone sits at a fixed column (x≈344), about 300px from the name, and the badges start at one column (x≈537) for both Titular and Empleado. At 390 (mobile_ganaderos_1.png) the badge still wraps under the name.
4. Disclosure alignment: resolved. "Ver animales" starts at x≈41, on the content edge at x≈40, and the pressed fill no longer bleeds past the column (desktop_ganaderos_2-abrir.png).
5. Footer fill: resolved. Every "Ver animales" strip shows the Lino fill under a border, rounded with the card, at both widths.
6. Empty state: resolved. desktop_ganaderos_6.png shows "…Se añaden al importar el Excel." and an "Ir a Explotaciones" link.
7. 404 back link: resolved. desktop_ganaderos_999.png shows a single "Volver a Ganaderos" link with no "← Ganaderos" above it.
8. List loading skeleton: partial. The source (GanaderosPage.tsx:30-31, 142-155) shows 5 pulsing skeleton rows with an sr-only role="status", and the count cell's separate class string is a reasonable deviation. None of the 10 recaptures shows the loading state, so the fix is not visible in evidence.

Regressions: none seen in the recaptures.

## remaining
- Item 8: capture the list mid-load (for example, delay the mocked `/ganaderos` response) at 1440 and at 390 to close it.

disposition: fix

---

# Verdict pass, item 8 (loading-state recapture)

## verdict
8. List loading skeleton: resolved. desktop_ganaderos-cargando.png shows the headers (Nombre / NIF / Explotaciones) above 5 rows of skeleton bars on the Lino fill, with the count bar right-aligned and the subtitle "Cargando…". mobile_ganaderos-cargando.png shows the same 5 rows under the "Expl." header, fitting inside the container. There is no text row, and no regressions are visible.

## remaining
clear. This ship covers the eight scored fixes from the finish review. It is not a fresh review of the whole surface. The nav overflow at 390px is still deferred, as before.

disposition: ship
