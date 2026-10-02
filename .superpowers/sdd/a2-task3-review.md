# A2 — Task 3: persistent session. Independent review

## VERDICT

**APPROVED WITH MINOR FINDINGS.** I found nothing Critical or Important. Decision 1 and H11 are implemented correctly, and the tests are real. They mount the real `routes` with the real `httpClient` and MSW. The Minor findings below are four narrow races or hardening gaps, plus three missing tests. None of them is reachable in normal use today. Fix M1 and M2 cheaply, now or in Task 10.

## Commands (run by me, from `frontend/`)

| Command | Result |
|---|---|
| `npm test` | exit 0. `Test Files 10 passed (10)`, `Tests 78 passed (78)` |
| `npm run build` | exit 0. `tsc -b` is clean and `vite build` passes. The only warning is the chunk > 500 kB one, which was already there |
| `npm run lint` | exit 0. 0 errors and 3 warnings, all `only-export-components` (`badge.tsx:55`, `button.tsx:58`, `AuthContext.tsx:192`). All three existed before; `useAuth` only moved line |

## Verified, no finding

### Storage
- Only `sessionStorage["ganera.token"]` is written (`authSession.ts:16,33`). Nothing is written about the user or the email, and nothing goes to `localStorage`. The test spies on `Storage.prototype.setItem` and checks that the only write is the token (`sesion.test.tsx:140-174`).
- Every read, write and remove is in a try/catch, including the `window.sessionStorage` getter (`authSession.ts:21-40`).
- A blank stored value is treated as absent.
- The in-memory fallback works end to end: the login succeeds with the getter throwing, and the Bearer header is still sent (`sesion.test.tsx:264`).
- Storage is cleared by all three paths:
  - Salir (`cerrarSesion("manual")`);
  - the 401 handler (`cerrarSesion("caducada")`);
  - a failed `/auth/me` right after login (`setAuthToken(null)` in `login`, `AuthContext.tsx:150`). The test at `sesion.test.tsx:209` covers it.

### Startup
- There is no flash of `/login`. `useState(() => restaurarTokenGuardado())` makes the first render `comprobando`. The test checks `role="status"` synchronously and that `/login` never appears in the router history.
- The deep link is preserved: path and query survive (`/ganaderos?pagina=2`).
  - `from` includes path, search and hash (`RequireAuth.tsx:52`).
  - After a login, the user returns to it.
- A 401 on `/auth/me` at startup clears storage, shows the notice once and lands on `/login`. The request interceptor fires the handler first, but it is a no-op because `sesionActivaRef` is false; `comprobarSesion` then closes the session. The result is a single close.
- A network error or 5xx keeps the token and shows Reintentar and Salir. Reintentar re-runs `/auth/me` with the in-memory token, and the retry works (tested).
- StrictMode:
  - `comprobacionInicialHecha` is a ref, so it survives React 19's simulated unmount and remount. Only one `/auth/me` call is made.
  - The `useState` initializers are idempotent.
  - The unauthorized-handler effect cleans up and re-registers correctly.
  - `comprobacionRef` discards stale results of a superseded check (a retry, or a close while checking).
- Race with Salir or a 401 from elsewhere while `/auth/me` is pending:
  - While the check is pending, the Salir button is not rendered, and neither are protected children, so no other requests go out.
  - A late 401 after Salir hits the `sesionActivaRef` guard and is ignored.
  - `cerrarSesion` bumps `comprobacionRef`, so a pending check cannot resurrect the session.
  - Several concurrent 401s during use close the session once (the first one flips the ref).

### Notice
- It shows only after `cerrarSesion("caducada")`, which requires a validated session or a saved token that gets a 401 at startup.
- It is not shown on a fresh visit, after Salir, or on a login error. `handleSubmit` also hides it on submit.
- It is shown once: `LoginPage` snapshots it into local state on mount and consumes it in an effect (tested by navigating away and back).
- It is StrictMode-safe: the effect re-run is idempotent and the local state is preserved across the simulated remount.
- Keeping it in the context rather than in `history.state` is the right call: it avoids repeating the notice on reload or back.

### Redirect `from`
- `from` travels in `history.state`, never in the URL, so an external link cannot set it.
- `//evil.com`, non-strings, non-`/` values, `/` and `/login…` are all rejected.
- After Salir, `RequireAuth` omits `from`, so the next login goes to `/tramites`.
- `/login` and `/registro` sit outside `RequireAuth`, so they can never be captured as `from`.

### Login and registro texts, and H11
- The texts are unchanged: "Email o contraseña incorrectos." (tested), and the network and generic texts come through `mensajeDeError(…, "login")`.
- H11 holds. `httpClient.esPeticionDeLogin` skips `notifyUnauthorized` for `POST …/auth/login`, and that is tested in `httpClient.test.ts:220`.
- The `/auth/me` 401 inside `login` is handled by the rollback plus the handler guard, so it never produces the notice.

### `router.tsx`
It is a pure extraction to `export const routes`, followed by `createBrowserRouter(routes)`. The route tree is byte-identical, so routing behaviour does not change.

### Test hygiene
- The global `afterEach` (`test/setup.ts`) resets `sessionStorage`, `localStorage`, the module token (`setAuthToken(null)`) and the unauthorized handler, and calls `cleanup()`. `cleanup()` unmounts `AuthProvider`, which drops the context flag and refs.
- The describe-level `vi.restoreAllMocks()` runs before the global hook (Vitest's `stack` hook order). That order is required: otherwise the getter spy would make `sessionStorage.clear()` throw. The suite passes in any order.

### Surviving mutation M4
I agree with the implementer that M4 (the handler ignoring `sesionActivaRef`) is **near-equivalent** in every reachable path:
- at startup, the interceptor's close bumps `comprobacionRef` and `comprobarSesion` then bails out;
- after a login `/auth/me` 401, `LoginPage` is mounted and silently consumes the motive.

The guard only matters in the edge case described in M4 below. It is defensive, and keeping it is correct.

### Scope
- `git status` shows nothing modified under `backend/`, `CLAUDE.md`, `PRODUCT.md`, `DESIGN.md`, `.agents`, `.claude` or `index.html`.
- The untracked `frontend/public/*` files are dated 15:16, before this task (22:59–23:03), so they are not Task 3's.
- `package.json` changes only the Task 1 test tooling; Task 3 adds no dependency.
- There are no hex colours in the touched files, and nothing is staged (`git diff --cached` is empty).

## Findings

### Critical
None.

### Important
None.

### Minor

**M1. A pending startup check can wipe a fresh login's token**
- **Where:** `frontend/src/shared/auth/AuthContext.tsx:138-160` (`login`) against `:108-128` (`comprobarSesion`).
- **Scenario:**
  1. There is a saved token, and the user is on `/login`. They got there by typing the URL, or the backend is slow.
  2. The startup `/auth/me` is still pending when they submit the login form.
  3. `login` only bumps `comprobacionRef` *after* its own `/auth/me` succeeds. So if the old check then returns 401, `intento === comprobacionRef` still holds.
  4. `cerrarSesion("caducada")` runs, and `setAuthToken(null)` wipes the **new** token from memory and storage.
  5. `login` then completes and sets `estado="activa"` with `sesionActivaRef=true`, but the module token is `null`.
  6. The first protected request goes out without a Bearer, gets a 401 and kicks the user out with "sesión caducada".
- **Mirror case:** the login's `/auth/me` fails while the old check is pending. `setAuthToken(null)` then wipes the saved token, the old check may still succeed, and the result is `activa` with no module token.
- **Reachability:** very unlikely, because the check normally resolves in milliseconds, before a user can type.
- **Fix:** do `comprobacionRef.current += 1` at the **start** of `login`, before the POST, so any in-flight check is discarded.

**M2. The login rollback leaves React state inconsistent when a session already existed**
- **Where:** `AuthContext.tsx:150`.
- **Scenario:**
  1. A user whose session is `activa` reloads the tab on `/login`.
  2. The startup check restores `activa`.
  3. The user logs in again, the POST succeeds, but `/auth/me` fails with a 5xx.
  4. `setAuthToken(null)` clears the module token and storage. React still has `estado="activa"`, `token=<old>` and `sesionActivaRef=true`.
  5. If the user then reaches a protected route, every request gets a 401 and the user sees "sesión caducada" instead of staying logged in.
- **Fix:** on rollback, either restore the previous token (`setAuthToken(tokenPrevio)`), or call a silent full reset (the `cerrarSesion` body without setting a motive).

**M3. A stale "caducada" notice after landing on `/registro`**
- **Where:** `AuthContext.tsx:118` together with `LoginPage.tsx:38-46`.
- **Scenario:**
  1. There is a saved token, and the tab is reloaded on `/registro`.
  2. `/auth/me` returns 401, so `motivoCierre="caducada"` is set, but no `LoginPage` is mounted to consume it.
  3. Much later, the user follows a link to `/login` and sees "Tu sesión ha caducado", which they never experienced in this view.
- **Opposite case:** the same reload on `/login` itself never shows the notice. The snapshot is taken at mount, before the check resolves, and the effect consumes the motive silently.
- **Fix (optional):** only set `"caducada"` from `comprobarSesion` when the current route is protected. Alternatively, clear `motivoCierre` when `RegistroPage` mounts.

**M4. The `sesionActivaRef` guard is untested in the one path where it matters**
- **Where:** `AuthContext.tsx:92`.
- **Scenario:**
  1. The user clicks Salir while a request is in flight, then goes to `/registro`.
  2. The late request returns 401.
  3. Without the guard, the motive would flip from `"manual"`/`null` to `"caducada"`.
  4. Returning to `/login` would then show a false notice.
- **Fix:** add a test for this case. After Salir, navigate to `/registro`, call `notifyUnauthorized()` (or let a delayed handler answer 401), navigate to `/login` and assert that there is no notice. This test kills M4.

**M5. `from` validation is hardening without tests, and it misses a backslash**
- **Where:** `frontend/src/features/auth/LoginPage.tsx:22-31`.
- **Missing tests:** no test exercises `//evil.com` or an external value, so removing `from.startsWith("//")` would survive.
- **Backslash:** `"/\evil.com"` passes the validator, and I confirmed that `new URL("/\\evil.com", origin)` resolves to `https://evil.com/`.
- **Why it is not exploitable today:**
  - `from` is only ever produced by `RequireAuth` from a *matched* protected route;
  - the navigation uses `replace`, and RR's `replaceState` has no `location.assign` fallback, so a cross-origin value throws instead of redirecting.
- **Fix:**
  - reject `\` anywhere (or anything whose `new URL(from, location.origin).origin !== location.origin`);
  - explicitly reject `/registro` for symmetry;
  - export `destinoTrasLogin` and unit-test `//evil.com`, `/\evil.com`, `https://evil.com`, `/login?x`, `/registro` and a valid `/tramites?estado=X`.

**M6. "After Salir, login goes to `/tramites`" is not tested**
- **Where:** `frontend/src/shared/auth/sesion.test.tsx:249-262`.
- **Gap:** the Salir test stops at `/login`. A mutation that always sets `from` in `RequireAuth.tsx:49-52` would survive.
- **Fix:** extend the test. Log in again after Salir from `/ganaderos`, and assert the user lands on `/tramites`.

**M7. The H11 check in `sesion.test.tsx` is vacuous at this level**
- **Where:** `frontend/src/shared/auth/sesion.test.tsx:195-207`.
- **Gap:** even if `notifyUnauthorized` fired for `/auth/login`, the notice could not appear. The handler guard ignores it with no session, and `LoginPage` snapshots the notice only at mount.
- **Why it is only informational:** H11 is properly covered in `httpClient.test.ts:220`, so no coverage is lost.
- **Fix:** none needed. Optionally, add a navigate-away-and-back assertion like the one in the test at line 209.

### Not a finding, noted for the record
Visiting `/login` with a valid saved token shows the form and does not auto-redirect. The implementer already flagged this, and it is out of scope.

# Re-review (fixes)

## VERDICT

**APPROVED WITH ONE MINOR FINDING (R1).** The fixes for M1–M6 are correct, and none of them introduces a regression. The one new finding (R1) is a gap in the new `destinoTrasLogin` checks. It cannot be reached today and is not an open redirect, but the fix is two lines and should go in before closing Task 3, or in Task 10.

## Commands (run by me, from `frontend/`)

| Command | Result |
|---|---|
| `npm test` | exit 0. `Test Files 11 passed (11)`, `Tests 113 passed (113)` |
| `npm run build` | exit 0. `tsc -b` is clean and `vite build` passes. The only warning is the chunk > 500 kB one, which was already there |
| `npm run lint` | exit 0. 0 errors and 3 warnings, the same three as before (`AuthContext.tsx:231` is `useAuth`, which only moved line) |

Scope is still clean:
- nothing is staged;
- nothing changed under `backend/`, `CLAUDE.md`, `PRODUCT.md`, `DESIGN.md`, `index.html` or the skills;
- `package.json` is identical to the first review, so there are no new dependencies;
- there are no hex colours.

## Verified, no finding

### M1: stale-token guard (`authSession.ts:76-79`, `httpClient.ts` `tokenDeLaPeticion`)
I looked for a case where a real expiry could be wrongly ignored and found none:
- **Same token value set again.** The guard compares by value. A late 401 carrying a token identical to the current one is treated as current. That is correct: it is the same credential, so if the backend rejected it, the current session is equally invalid. The guard can therefore never cause a wrong ignore, or a wrong close, in this case.
- **Two requests with one token.** The first 401 closes the session and sets `currentToken=null`. The second carries the old token, which no longer matches, so it is ignored. The session is already closed, so nothing is lost.
- **Header read failing.** If reading the header ever failed, `null !== token` would make every real 401 get ignored, which would be the dangerous failure mode. It works:
  - the request interceptor sets `config.headers.Authorization` as a string;
  - axios's `mergeConfig` copies the headers for the adapter, so `error.config.headers.Authorization` keeps the original key;
  - the in-use 401 test (`sesion.test.tsx`, "un 401 en mitad del uso…") goes through the real interceptors and would fail if the read returned `null`;
  - `httpClient.test.ts` has a positive case ("401 … con el token actual sí avisa") and a negative case that is correctly timed.
- **A request with no Bearer while a token exists.** This only happens if the token was set after the request left, and ignoring that 401 is correct.
- No other code sets `Authorization` (grep).

### M1: `login` changes (`AuthContext.tsx:163-197`)
- `++comprobacionRef` before the POST discards any pending startup check.
- The catch only resets if no newer operation happened.
- A successful login sets the token again only if something cleared it.
- Both M1 race tests drive real timing through MSW promise gates, and are not vacuous: mutation B+C dies.

### M2: full silent reset on login failure
- `cerrarSesion(null)` keeps memory, storage and React state consistent.
- The M2 test proves that `/ganaderos` redirects to `/login` afterwards.
- Accepted trade-off: a failed login attempt, even a wrong password or a network error, wipes a still-valid session in that tab. That is reasonable, since the user is explicitly logging in again, and it matches what the login screen says.

### M3: `registrarRutaProtegida`
- **StrictMode safety.** `useEffect(() => registrarRutaProtegida(), …)` returns the matching decrement. Under React 19's simulated double mount the counter goes +1 → −1 → +1, and settles at 1. Both steps happen synchronously during commit, so no async 401 can observe the intermediate 0. `registrarRutaProtegida` is stable (`useCallback([])`), so the effect never re-runs spuriously.
- **Effect order.** Child effects run before parent effects, so `RequireAuth` registers before `AuthProvider`'s startup check starts.
- **Every protected path still counts as a protected route:**
  - the startup 401 while `comprobando`;
  - the 401 during Reintentar in `error-comprobacion`;
  - the in-use 401.
- The existing expired-notice tests still pass.

### M4 and M6 tests
- **M6** (`sesion.test.tsx`, the Salir test extended): it logs in again after Salir and asserts `/tramites`. It would fail if `from` were kept after Salir (mutation G), because the user would land on `/ganaderos`. It is real.
- **M4:** it calls `notifyUnauthorized()` with no argument, which bypasses the token layer on purpose. With M3 in place it is only killed by F+E, which fits a two-layer defence, and it pins the intended observable behaviour.
- The `afterEach` isolation still holds.

### `destinoTrasLogin` (28 tests)
- Backslash, control characters, `//`, cross-origin, `javascript:` and the excluded routes (with a trailing slash, query or hash) are all handled.
- The tests cover the variants that include a path.

## Findings

### Critical
None.

### Important
None.

### Minor

**R1. Dot-segment normalisation reintroduces `//` after validation**
- **Where:** `frontend/src/features/auth/destinoTrasLogin.ts:30-45`.
- **Cause:** the `//` check runs on the raw `from`, but the function returns the **normalised** `url.pathname`. Dot segments collapse into a leading `//`. I confirmed this with Node's WHATWG URL:
  - `"/.//evil.com"` → pathname `"//evil.com"`, same origin;
  - `"/a/..//evil.com"` → `"//evil.com"`;
  - `"/%2e//evil.com"` → `"//evil.com"`;
  - `"/ganaderos/..//evil.com/x"` → `"//evil.com/x"`.
- **Scenario:** each of these inputs passes every check (it starts with `/` but not `//`, has no `\` and no control character, has the same origin, and is not an excluded route). `destinoTrasLogin` then returns `"//evil.com"`.
- **Not an open redirect today:**
  - `navigate(…, { replace: true })` goes through `history.replaceState("//evil.com")`, which throws `SecurityError` cross-origin (RR's `replace` has no `location.assign` fallback);
  - `from` is only produced by `RequireAuth` from a browser-normalised, matched pathname.
- **Why it still matters:** it breaks the function's own invariant ("only same-origin internal paths"), would become exploitable if the navigation switched to push or `<Link>`, and none of the 28 tests covers it.
- **Fix:**
  1. After parsing, also reject when `url.pathname.startsWith("//")` (or `url.pathname.includes("//")`). Returning the validated raw `from` instead of the normalised one would also work, but the explicit check is simpler.
  2. Add `"/.//evil.com"`, `"/a/..//evil.com"` and `"/%2e//evil.com"` to `destinoTrasLogin.test.ts`, expecting `/tramites`.
