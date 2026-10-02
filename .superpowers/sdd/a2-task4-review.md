# A2 · Task 4: Marca básica (independent review)

## VERDICT: APPROVED WITH MINOR FINDINGS

There are no Critical or Important findings. The task meets decisions 4 and 5 and H9. The four Minor findings below can be fixed in Task 10 (audit/polish) or Task 11 (closure) without reopening this task.

## Commands (run by the reviewer, from `frontend/`)

| Command | Result |
|---|---|
| `npm test` | exit 0: `Test Files 14 passed (14)`, `Tests 126 passed (126)`. This matches the report. |
| `npm run build` | exit 0. `tsc -b` is clean and `vite build` passes (✓ built). The only warning is the existing chunk > 500 kB. |
| `npm run lint` | exit 0: 0 errors, 3 warnings. All three are pre-existing: `button.tsx:58`, `badge.tsx:55` and `AuthContext.tsx:231`. |

**`dist/`**
- It contains `favicon-64.png`, `ganera-logo.svg`, `ganera-logo-verde.svg` and `ganera-logo-512.png`, plus the legacy `favicon.svg`, `icons.svg` and `preview.png`.
- `dist/index.html` has `lang="es"`, `<title>Ganera</title>` and `<link rel="icon" type="image/png" href="/favicon-64.png">`.
- The built CSS has `.logo-ganera{background-color:currentColor;-webkit-mask-image:url(/ganera-logo.svg);mask-image:url(/ganera-logo.svg);…}`, and both the prefixed and unprefixed forms survive.
- `dist/` already existed before my run, from the implementer's build. I rebuilt it in place and did **not** delete it. It is git-ignored (`frontend/.gitignore`: `dist`).

**Base probe**
- I ran `vite build --base=/app/` into a scratchpad directory, then deleted that directory.
- It produced `mask-image:url(/app/ganera-logo.svg)` (both prefixes) and `href="/app/favicon-64.png"`.
- So the asset path respects Vite's `base`.

## Verified, no finding

- **`index.html`**
  - `lang="es"` and `<title>Ganera</title>` are set.
  - There is exactly one icon: `type="image/png" href="/favicon-64.png"`, which is a real 64×64 RGBA PNG.
  - `/favicon.svg` is no longer referenced.
- **LogoGanera: mask**
  - The `@utility logo-ganera` block (`src/index.css:172-182`) sets `background-color: currentColor` and every `mask-*` property, each with its `-webkit-` duplicate.
  - The color comes only from a token class (`text-primary`).
- **LogoGanera: no hex**
  - `git diff` of the changed files and a grep for `#[0-9a-fA-F]{3,6}` across `shared/brand/*`, `AppLayout.tsx`, `LoginPage.tsx`, `RegistroPage.tsx`, `index.html` and the added lines of `index.css` are both clean.
  - There is only one copy of the SVG path, in `public/ganera-logo.svg`.
- **LogoGanera: accessibility**
  - By default the mark is `role="img" aria-label="Ganera"`, and `role` is excluded from the props.
  - With `decorativo` it is `aria-hidden="true"` with no role or label.
  - All three uses are decorative and each sits next to visible brand text: "GANERA" in the navbar and the "Ganera" `CardTitle` on login and registro. So no name is read twice. This is correct.
- **Sizing**
  - `cn`/tailwind-merge replaces the default `size-6` with the caller's size, and a test covers this.
  - In the auth `CardHeader` grid, the extra first child lands in the explicit `auto` rows. The description falls into an implicit `auto-rows-min` row, and that doesn't clip it.
- **Placement**
  - **Navbar:** the green dot is replaced by a 20px (`size-5`) `text-primary` mark in the existing `flex items-center gap-2` row, and "GANERA" is kept (600 weight, `tracking-wide`, green).
  - **Auth screens:** a 40px (`size-10`) mark with `mb-3` is the first child of `CardHeader`, above the "Ganera" title, on both login and registro.
  - No kicker or eyebrow and no extra copy were added, which is consistent with Impeccable's operate mode and the craft-floor rules.
  - **Nav links:** the only links are Explotaciones, Trámites and Facturación. **There is no Ganaderos link**, which is correct because it arrives in Task 7.
- **Tokens:** only `text-primary` is used. The One Green and Token-Only rules hold.
- **`PRODUCT.md`**
  - The diff is minimal and keeps the existing style.
  - The logo files and variants are accurate: `ganera-logo.svg` really has `fill="currentColor"`, and the file list matches `public/`.
  - "`logo.jpg` is not the logo and is not committed" is true; the file is untracked.
  - The `sessionStorage` line is accurate: `authSession.ts` stores `ganera.token` in `sessionStorage`.
- **Tests**
  - They are meaningful:
    - The accessible name and role are asserted through `getByRole`.
    - The decorative variant has no role or name.
    - The size override is checked.
    - The CSS utility is checked at its source (acceptable, since jsdom doesn't run Tailwind).
    - `index.html` is parsed from disk.
    - The real routes are mounted for the navbar, `/login` and `/registro`.
  - On the auth screens, the mark is asserted to be the element immediately before the title.
  - The implementer's mutation check (restoring the dot makes the test fail) is credible given the `.rounded-full` and count assertions.
  - The existing tests are still green (126/126).
- **Scope**
  - `backend/`, `DESIGN.md`, `CLAUDE.md`, `skills-lock.json`, `.agents/` and `.claude/` have no diff.
  - `logo.jpg` and `public/preview.png` are untracked and untouched.
  - The `package.json` diff comes from Task 1 (test tooling); Task 4 adds no new dependency.
  - Nothing is staged (`git diff --cached` is empty).
  - `public/favicon.svg` and `public/icons.svg` still exist.

## Critical

None.

## Important

None.

## Minor

### M1. The mark disappears in forced-colors (Windows High Contrast) mode

**Where:** `frontend/src/index.css:172-182`

**Scenario**
- In forced-colors mode, browsers override `background-color` with the system `Canvas` color and keep only the alpha.
- The masked shape is then painted in the same color as the surface behind it, so the mark becomes invisible.
- It does not become a block, because the mask still applies.
- The three current uses are decorative and the brand name is still shown as text, so the user loses no information.
- A future standalone use (the non-decorative `role="img"` variant) would, however, expose an invisible image.

**Fix (in Task 10's audit)**

```css
@media (forced-colors: active) {
  .logo-ganera { forced-color-adjust: none; background-color: CanvasText; }
}
```

Or the equivalent inside the `@utility`. This keeps the mark visible in the user's high-contrast text color.

### M2. The report is wrong about how the mark degrades

**Where:** `.superpowers/sdd/a2-task4-report.md` ("Browser support"); `src/index.css:167-170`

**Scenario**
- The report says that without masks the mark "renders as a filled `currentColor` square".
- That only happens in a browser with *no* mask support at all, which is practically none of the targets in 2026.
- A more likely failure is that the SVG fails to load (a 404 or a wrong `base`). Per CSS Masking, an unloadable mask image is a transparent black layer, so **the mark disappears** instead.
- Both outcomes are acceptable here because every use sits next to visible brand text. No code fallback is needed.

**Fix:** correct the wording when the Brand Mark Rule is written into `DESIGN.md` (Task 10/11). If a standalone, non-decorative use is ever added, give it a visible text fallback.

### M3. `PRODUCT.md:80` describes only the navbar

**Where:** `PRODUCT.md:80`

**Scenario**
- The line reads "The app shows the brand mark next to the text logotype 'GANERA'". That is true in the navbar.
- On login and registro, however, the mark sits *above* the "Ganera" card title, not next to "GANERA".
- The line is not false, but it is incomplete.

**Fix (optional):** "The app shows the brand mark next to the 'GANERA' logotype in the navigation bar, and above the 'Ganera' title on the login and registration screens."

### M4. `DESIGN.md` now contradicts the UI

**Where:** `DESIGN.md:153-154`, `DESIGN.md:258` and `DESIGN.md:302-304`

**Scenario**
- `DESIGN.md` still describes a "10px Verde Monte dot" and says "The logotype's dot is the only circle".
- Leaving it untouched in this task was intended, because `DESIGN.md` is out of scope for it.
- The report lists the exact edits needed. This finding is here only so Task 10/11 doesn't drop them.

**Fix:** apply the report's `DESIGN.md` notes in Task 10/11:
- the Primary color line;
- the Navigation section;
- the auth layout;
- delete the "only circle" line;
- add a new Brand Mark Rule.

## Not verified here

There was no visual pass in a real browser: the optical weight of the 20px mark next to 16px "GANERA", and the 40px mark in the card header. The report defers this to the smoke test in decision 18 / Task 11, and I agree.

The candidates for deletion (`favicon.svg`, `icons.svg`) and the fact that `preview.png` ships in `dist/` are correctly left to Antonio.
