# A2 — Task 1: Test infrastructure — Independent review

**VERDICT: Approved** (no Critical or Important findings; 3 Minor ones, which later tasks can pick up)

## Command results (run from `frontend/`, Node 22.17.1)

| Command | Result |
|---|---|
| `npm test` | exit 0 — `Test Files 1 passed (1)`, `Tests 2 passed (2)`, about 3–4 s |
| `npm run build` | exit 0 — `tsc -b` covers the app, node and test projects; `vite build` ✓; the only warning is the existing "chunk > 500 kB" one (`index-*.js` 527 kB, as before) |
| `npm run lint` | exit 0 — 0 errors, 3 warnings, all pre-existing `only-export-components` (`button.tsx:58`, `badge.tsx:55`, `AuthContext.tsx:65`) |

## Verified, no finding

**Dependencies**
- The 6 approved packages are all in `devDependencies`: vitest 5.0.2, jsdom 29.1.1, RTL 16.3.3, user-event 14.6.7, jest-dom 7.0.1, msw 2.15.0.
- `dependencies` is unchanged. The lockfile's root `dependencies` are identical to `HEAD`.
- The lockfile has the same non-dev package set as `HEAD` (344 entries), with no removals. It adds 120 entries, all dev.
- The only changes to existing entries are 2 semver-compatible transitive bumps: `@jridgewell/sourcemap-codec` 1.5.5→1.6.0 and `picomatch` 4.0.5→4.0.7.
- Peer and engine requirements are compatible:
  - vitest needs `vite ^6||^7||^8` and Node `^22.12`;
  - RTL needs `react ^18||^19`;
  - jest-dom needs Node `>=22` and vitest `>=0.32`;
  - msw needs `typescript >=4.8`;
  - there is a single deduplicated copy of msw (it is shared with `@vitest/mocker`).
- **jsdom 29:** this is a reasonable choice. jsdom 30 needs Node `^22.22.2`, and this machine has 22.17.1. jsdom 29.1.1 declares `^22.13.0`.
- **msw 2.x:** also reasonable. It is the line vitest 5 uses internally, its API is stable, and nothing requires 3.x.
- No test library ends up in the bundle: a grep of `dist/assets/*.js` for msw, vitest and testing-library finds nothing.

**Setup**
- `setup.ts` uses `onUnhandledRequest: "error"`, `resetHandlers()` and `cleanup()` in `afterEach`, and `close()` in `afterAll`.
- `vitest.config.ts` uses `mergeConfig(viteConfig, …)`. `vite.config.ts` is untouched, and the `@` alias works (the smoke test imports `@/shared/api/httpClient`).
- `VITE_API_BASE_URL` is pinned only in the vitest `test.env`.
  - **Checked with a probe:** in a scratchpad copy with a `.env.local` set to `VITE_API_BASE_URL=http://otro:9999`, the tests still hit `http://localhost:8080`.
  - It does not leak into the build: `vite build` doesn't read `vitest.config.ts`.
- **No test types leak into the app.** I checked with a probe: I added an app file (`src/leak.ts`) that used `process.env`, `describe` and `toBeInTheDocument`, then ran `tsc -p tsconfig.app.json`. It fails on all three, so the app project gets no Node, vitest-globals or jest-dom types.
- `tsconfig.test.json` extends the app config and covers `*.test.ts(x)` and `src/test`, so the test types are checked by `tsc -b`.

**Smoke test**
- It is not vacuous. It uses the real `httpClient` against MSW and asserts both the status and the body.
- If MSW didn't intercept, the request would go to `localhost:8080` for real and fail, because no backend is running.
- The implementer's negative check (a request with no handler throws) matches the configuration.

**Fitness for later tasks.** I ran a temporary probe file in a scratchpad copy, not in the repo; 11/11 passed.
- In jsdom, axios picks the `xhr` adapter (`['xhr','http','fetch']`), and MSW intercepts it.
- **Decision 23:** a `400` with a `text/plain` body arrives as `e.response.data === "Falta la hoja Animales"` (a string). A JSON `409` arrives as an object.
- A network error (`HttpResponse.error()`) gives `ERR_NETWORK` with `response` undefined.
- **Decision 20:** a handler that branches on `?page=` works: pages 0 and 1 return OK and page 2 returns 500.
- The real interceptors run: the header is `Authorization: Bearer <token>`, and a `401` calls the `setUnauthorizedHandler` handler.
- `sessionStorage` exists in jsdom.
- `userEvent.setup()` and `click` work with React 19 state updates.

**Scope**
- Only these files changed:
  - `package.json`, `package-lock.json`, the three `tsconfig*.json` files;
  - new files: `tsconfig.test.json`, `vitest.config.ts`, `src/test/*`.
- `git diff HEAD` shows nothing under:
  - `frontend/src` (apart from `src/test`), `frontend/public`;
  - `backend/`, `.agents/`, `.claude/`, `skills-lock.json`;
  - `PRODUCT.md`, `DESIGN.md`, `logo.jpg`.
- The untracked files in `public/` were already there before the task.
- Nothing is staged (`git diff --cached` is empty).

## Critical
None.

## Important
None.

## Minor

1. **`sessionStorage` and module-level session state are not reset between tests.** `src/test/setup.ts`, `afterEach`.
   - In the probe, a value written with `sessionStorage.setItem` in one test was still there in the next test in the same file.
   - `authSession.ts` also keeps the token and the 401 handler in module variables, which likewise persist within a file.
   - Task 3 (session persistence) will create order-dependent tests unless this is handled.
   - **Fix:** add `sessionStorage.clear()` and `localStorage.clear()` to the global `afterEach`. Task 3 can also give `authSession` a reset, for example `setAuthToken(null)` and `setUnauthorizedHandler(null)` in the same `afterEach` or in that suite's `beforeEach`.
2. **The smoke test doesn't assert which URL the request went to.** `src/test/smoke.test.tsx`.
   - It is fine as it is: without MSW the request would fail.
   - **Optional hardening:** capture `request.url` in the handler and assert it equals `apiUrl("/ping")`. That would pin the baseURL contract explicitly, in case a local backend is running when the test runs.
3. **Small inaccuracies in the report.** `a2-task1-report.md`.
   - It says "122 new entries"; I count 120 new `packages` entries in the lockfile.
   - It says "~33 s" per run; I measured 3–4 s.
   - Neither affects the result.

**For later tasks (not a finding):** if a test combines `vi.useFakeTimers()` with user-event, it needs `userEvent.setup({ advanceTimers: vi.advanceTimersByTime })`.
