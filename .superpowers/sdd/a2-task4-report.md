# A2 · Task 4: Marca básica (report)

Scope: decisions 4 and 5 and H9. Frontend and `PRODUCT.md` only. `backend/`, `DESIGN.md`, `CLAUDE.md`,
`logo.jpg`, `public/preview.png`, the skills folders and `skills-lock.json` were not touched. No new
dependencies and no git operations.

## Files changed

| File | Change |
|---|---|
| `frontend/index.html` | `lang="es"`, `<title>Ganera</title>`, `<link rel="icon" type="image/png" href="/favicon-64.png">` (replaces `/favicon.svg`) |
| `frontend/src/shared/brand/LogoGanera.tsx` | **New.** The `LogoGanera` component |
| `frontend/src/index.css` | New `@utility logo-ganera` (the mask), after `body { margin: 0 }` |
| `frontend/src/shared/layout/AppLayout.tsx` | The green dot is replaced by `<LogoGanera decorativo className="size-5 text-primary" />`, and the "GANERA" text stays next to it |
| `frontend/src/features/auth/LoginPage.tsx` | `<LogoGanera decorativo className="mb-3 size-10 text-primary" />` is the first child of `CardHeader`, above the "Ganera" title |
| `frontend/src/features/auth/RegistroPage.tsx` | Same as login |
| `PRODUCT.md` | Brand Commitments (official logo, variants, mark + logotype, `logo.jpg`) and the "Stack in place" line (`sessionStorage`) |
| `frontend/src/shared/brand/LogoGanera.test.tsx` | **New.** 4 tests |
| `frontend/src/shared/brand/indexHtml.test.ts` | **New.** 3 tests |
| `frontend/src/shared/brand/marca.test.tsx` | **New.** 3 tests (navbar, `/login`, `/registro`) |

**Why `src/shared/brand/`:**
- `src/components/` holds only `ui/`, the generated shadcn primitives. Those are generic, and the CLI can overwrite them.
- `src/shared/` is where app-wide code that belongs to no single feature already lives (`layout`, `auth`, `api`).
- The brand mark is app-level and used by both `shared/layout` and `features/auth`, so `shared/brand` fits that convention.

## Mask approach

- **Component:** `LogoGanera` renders a `<span data-slot="logo-ganera" class="logo-ganera inline-block size-6 shrink-0 …">`.
  - The size comes from `className`. It goes through `cn`/tailwind-merge, so `size-10` replaces the default `size-6`.
  - The color comes from a token text class (`text-primary`).
- **CSS:** the `logo-ganera` utility in `index.css` sets:
  - `background-color: currentColor`;
  - `mask-image: url("/ganera-logo.svg")`, `mask-size: contain`, `mask-repeat: no-repeat` and `mask-position: center`;
  - each one also with the `-webkit-` prefix.

  There are no hex values in the CSS or in the components. Only one copy of the SVG path exists, `public/ganera-logo.svg`.
- **Accessibility:**
  - By default the mark is `role="img" aria-label="Ganera"`.
  - With `decorativo` it is `aria-hidden="true"` and has no role or label.
  - All three current uses are decorative, because each sits next to visible text that already names the brand: "GANERA" in the navbar and the "Ganera" card title on login and registro. Otherwise a screen reader would read "Ganera" twice.
- **Vite `base`:**
  - *Build:* Lightning CSS keeps both the prefixed and unprefixed declarations. Vite rewrites the absolute `public/` URL using `base`. This was checked with a throwaway `vite build --base=/app/` into a temp dir, which produced `mask-image:url(/app/ganera-logo.svg)` and `href="/app/favicon-64.png"`. The temp dir was deleted afterwards.
  - *Dev:* `vite` on a spare port served `/ganera-logo.svg` (200, `image/svg+xml`) and `/favicon-64.png` (200). `/src/index.css` carried the mask URL, and `/` served `lang="es"`, the title and the favicon. The server was then stopped.
- **Browser support:**
  - Unprefixed `mask-*` works in Chrome/Edge 120+, Firefox 53+ and Safari 15.4+.
  - The `-webkit-` duplicates cover older Chromium and Safari, including iOS Safari from before the standard.
  - If masks are not supported at all, the mark renders as a filled `currentColor` square. That is acceptable, because every use sits next to the visible brand name.

## Impeccable guidance applied

- **Context loaded:** `impeccable context` ran with `--target AppLayout.tsx` (PRODUCT.md, DESIGN.md), and `craft-floor.md` was read before editing.
- **Mode "Operate":** in operate mode, "brand lives in precise details" and "the tool should disappear into the task". So the mark is small and quiet: 20px in the 16px navbar next to the logotype, and 40px on the auth cards. There is no hero, no decoration and no extra copy.
- **"Refinement preserves":**
  - The "GANERA" logotype, the "Ganera" card titles and the descriptions stay as they were.
  - The mark only replaces the dot, and on auth screens it is added above the title.
  - No kicker or eyebrow was added (a craft-floor ban).
- **The brief wins / Token-Only Rule / One Green Rule:** the mark's only color is `text-primary`, which is Verde Monte, already used for the logotype.
- **Polish ("keep icon sizing and optical alignment coherent"; "useful alt text"):**
  - `size-5` sits next to 16px/600 text in an `items-center` row with the existing `gap-2`.
  - On auth screens, `mb-3` separates the mark from the title within the header's `gap-1` grid.
  - Accessible names are not duplicated (see the accessibility notes above).
- **Not done:** no visual pass in a real browser. There is no Playwright in the repo, and the browser smoke test is planned for the later smoke task (decision 18). Things to check there:
  - the optical weight of the 20px mark next to "GANERA";
  - the 40px mark in the auth card header.

## Tests

- **`LogoGanera.test.tsx`:**
  - the default accessible name is "Ganera" (`getByRole("img", { name: "Ganera" })`);
  - `decorativo` gives `aria-hidden` with no role or label;
  - it applies `logo-ganera` plus the size and color classes, and `size-6` is replaced;
  - the `@utility logo-ganera` block in `index.css` contains every mask declaration (with and without the prefix) and `background-color: currentColor`, and no hex. jsdom doesn't process Tailwind, so the CSS is checked at its source.
- **`indexHtml.test.ts`:**
  - reads `frontend/index.html` with `fs` and parses it with jsdom's `DOMParser`;
  - checks `lang="es"`, the title "Ganera", and exactly one `rel="icon"` with `type="image/png"` and `href="/favicon-64.png"`.
- **`marca.test.tsx`:**
  - mounts the real `routes` with `AuthProvider` + `createMemoryRouter`, using the same pattern as `sesion.test.tsx`;
  - **navbar:** the header has exactly one decorative `logo-ganera text-primary` mark, the "GANERA" text, and no `.rounded-full` dot;
  - **`/login` and `/registro`:** each has exactly one decorative mark, and it is immediately followed by the "Ganera" title.
- **Mutation check:** restoring the old dot in `AppLayout` made the navbar test fail (1 failed | 9 passed). After the restore, it passed again.
- **File paths:** the tests use `import.meta.dirname`, because under the jsdom environment `new URL(…, import.meta.url)` is not a `file:` URL.

## Commands (from `frontend/`)

| Command | Result |
|---|---|
| `npm test` | exit 0: `Test Files 14 passed (14)`, `Tests 126 passed (126)` (116 + 10 new) |
| `npm run build` | exit 0: `tsc -b` clean, `vite build` ✓, with only the existing chunk > 500 kB warning |
| `npm run lint` | exit 0: 0 errors, 3 warnings, all pre-existing (`button.tsx:58`, `badge.tsx:55`, `AuthContext.tsx:231`) |
| `dist/` check | See below |

What the `dist/` check found:
- `dist/` contains `favicon-64.png`, `ganera-logo.svg`, `ganera-logo-verde.svg` and `ganera-logo-512.png`.
- `dist/index.html` has `lang="es"`, `<title>Ganera</title>` and `<link rel="icon" type="image/png" href="/favicon-64.png">`.
- The built CSS contains `.logo-ganera{background-color:currentColor;-webkit-mask-image:url(/ganera-logo.svg);mask-image:url(/ganera-logo.svg);…}`.

## Notes for `DESIGN.md` (Task 10/11) and `CLAUDE.md`

**`DESIGN.md` should say:**
- **Colors → Primary:** Verde Monte colors "the brand mark and the 'GANERA' logotype", instead of "the text logotype 'GANERA' and its dot".
- **Navigation:** "The brand mark is the Ganera symbol (`LogoGanera`, 20px, `text-primary`, decorative) followed by the 'GANERA' logotype in 600 weight, `tracking-wide`, in green."
- **Layout → Auth screens:** the card header opens with the 40px mark above the "Ganera" title.
- **Shapes:** delete "The logotype's dot is the only circle".
- **A new named rule, the Brand Mark Rule:**
  - the mark is `public/ganera-logo.svg`, always rendered through `LogoGanera`, which is a CSS mask over `currentColor`;
  - its color is always a token class, never an `<img>` and never a hex;
  - it is decorative (`aria-hidden`) whenever visible text already names Ganera;
  - `ganera-logo-verde.svg` (with a hard-coded `#1F3D2B`) is only for contexts without CSS (e.g. email, external documents), and is never used in the UI.

**For `CLAUDE.md`:**
- The frontend has `shared/brand/LogoGanera`.
- `index.html` is `lang="es"`, with the favicon `favicon-64.png`.
- `PRODUCT.md` now names the official logo and says authentication survives a reload through `sessionStorage`. The "Auth is in-memory, not `localStorage`" paragraph in `CLAUDE.md` is already outdated since Task 3.
- The frontend test count is now 126.

## Candidates for deletion (Antonio decides)

- `frontend/public/favicon.svg` (Vite's default favicon, no longer referenced).
- `frontend/public/icons.svg` (a Vite scaffold leftover; nothing in `src/` or `index.html` references it).
- Both are still copied into `dist/`.

**Separate observation:** `public/preview.png` is also copied into `dist/`, because everything in `public/` is. It must never be committed, but a build made from a working copy that still has it will ship it. Either move it out of `public/` or remember it before deploying. Nothing was changed here.

## Correcciones tras la revision (aplicadas por el orquestador)

- M1: `@utility logo-ganera` incluye `@media (forced-colors: active) { forced-color-adjust: none;
  background-color: CanvasText; }` (verificado en el CSS de `dist/`).
- M3: `PRODUCT.md` precisa donde aparece el simbolo (junto a "GANERA" en la navegacion; encima del
  titulo en login y registro).
- M2 y M4 (redaccion del fallback y `DESIGN.md`) quedan para la Task 10/11.
- Suite 126/126, build y lint en verde.
