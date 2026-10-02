# A2 — Task 2: Cliente HTTP único y modelo de errores — Revisión independiente

**VERDICT: Approved with changes.** No Critical findings. There is 1 Important finding (latent, one-line fix) and 5 Minor ones. Fix I1 before Task 3; the Minor ones can go to later tasks or the backend mini-prompt.

## Command results (from `frontend/`)

| Command | Result |
|---|---|
| `npm test` | exit 0 — `Test Files 5 passed (5)`, `Tests 45 passed (45)`, 4.85 s. No unhandled rejections or errors in the output. |
| `npm run build` | exit 0 — `tsc -b` is clean and `vite build` ✓. The only warning is the existing chunk > 500 kB (`index-*.js` 531.16 kB). |
| `npm run lint` | exit 0 — 0 errors and 3 warnings, all pre-existing `only-export-components` (`badge.tsx:55`, `button.tsx:58`, `AuthContext.tsx:65`). |

The probes below were run in a **scratchpad copy** of `frontend/` (node_modules junctioned, junction removed afterwards). Nothing in the repo was modified except this file.

## Verified, no finding

**`ErrorApi` and motivo parsing (`errores.ts`)**
- `{motivo}`, `{mensaje}`, plain text, HTML (by content-type or a leading `<`), blank, whitespace, `{motivo:null}`, `{motivo:42}` and `null` bodies all behave as the report says. The MSW tests go through the real client.
- Parsing never throws: `extraerMotivo` has a try/catch and only does `typeof` checks. `cabecera` works on `AxiosHeaders`, because the xhr adapter's `parseHeaders` lowercases keys.
- **Spring's default error JSON** (`{timestamp,status,error,path}`) gives no motivo. There's a test for it. `error: "Bad Request"` and `error: "Unauthorized"` never become user text. Spring's `message` field is not read (only `mensaje`), and ProblemDetail is not enabled (`detail` is not read either).
- The `{mensaje}` fallback is justified:
  - `RegistroErrorResponse(String mensaje)` is the only backend DTO with a `mensaje` field (grep of `backend/src/main`);
  - every other error body is `MotivoErrorResponse(motivo)`, an empty body, the plain-text importer 400, or Spring's `/error` JSON.
- **The Spring "String body + `Accept: application/json`" quirk:**
  - `ResponseEntity.badRequest().body(String)` may go out as `Content-Type: application/json` with a raw, unquoted body.
  - Probe: axios's `silentJSONParsing` hands back the raw string, so it still becomes the motivo ("Falta la hoja 'Animales' en el Excel").
- **Blob and ArrayBuffer bodies:**
  - no caller uses `responseType` (grep);
  - by code, an object with no `motivo`/`mensaje` string gives `undefined`;
  - a jsdom/MSW probe couldn't run Blob (an environment limitation: `object.stream is not a function`), so this is verified by reading the code, not at runtime.

**`mensajeDeError`**
- **Login 401 text is unchanged:** "Email o contraseña incorrectos.".
  - The backend's `AuthController.login` returns `ResponseEntity.status(401).build()` (no body), and `/auth/login` is `permitAll`, so the entry point / `/error` is never involved.
  - Probe with the real `LoginPage` + `AuthProvider` + MSW 401: that exact text is shown.
  - Probe with a Spring-style 401 JSON: the same text is shown.
  - (See I1 for the latent gap.)
- **Registro stays uniform:**
  - the backend sends the same `MENSAJE_REGISTRO_INVALIDO` for every 400 cause, so showing it verbatim does not enable enumeration;
  - 503 → "La facturación todavía no está configurada…" (same text as before).
- **The 403 approve text** is byte-identical to the old literal. `TramiteReviewDialog.test.tsx` covers it.
- **No text mentions or implies OVZ.net.** `aprobarTramite` keeps its "solo cambia el estado en BD" comment, and no button text or toast was added.
- Every context × every tipo gives a non-empty string. There is a test for this, and the `Record<ContextoError,…>` type forces a `generico`.

**Interceptor (`httpClient.ts`)**
- A 401 calls `notifyUnauthorized` except on `POST …/auth/login`. The path goes through `httpClient.getUri(config)` → `new URL(...).pathname`, so the baseURL is included and the query string is dropped.
- Probe, POST variants that do **not** notify:
  - `/auth/login/` (trailing slash);
  - `auth/login?x=1`;
  - the absolute URL.
- Probe, cases that **do** notify:
  - `GET /auth/login`;
  - `/auth/me` (tested).
- A 403 never logs out (tested).
- **Network errors map to `red` (tested).**
  - Timeouts (`ECONNABORTED`/`ETIMEDOUT`) have no `response`, so they also map to `red` in code. No timeout is configured today.
  - An MSW `delay` probe didn't trigger the xhr timeout in jsdom (environment limitation).
- `ERR_CANCELED` maps to `desconocido`. No caller uses `AbortController` or `signal` today (grep), so this is never shown today. See M5 for the future.
- The request interceptor (Bearer) is unchanged (tested).

**Migration**
- `isAxiosError` / `response?.status` appear only in `errores.ts` and `httpClient.ts`. No other file imports `axios`.
- Every remaining `catch` / `.catch` displays or propagates the error:
  - `facturacion/api.ts` re-throws anything other than the 404;
  - `AuthContext.login` lets it bubble to `LoginPage`.
- There are no `.then` calls without `.catch` and no empty catches.
- **Races:** every effect that loads data has a `cancelado` flag, checked in `.then`, `.catch` and `.finally`:
  - Explotaciones, Tramites, the review dialog and `useSuscripcionEstado`;
  - `setErrorCarga(null)` runs at the start of each effect, so changing a filter or page, or retrying, clears the old error;
  - a stale response after unmount, or after a filter change, is dropped.
- Dialog: `tramiteId → null` resets `detalle`, `errorAccion` and `errorCarga`. A row can't be clicked while the modal is open, so A→B without passing through `null` can't happen.
- Aprobar/Rechazar are disabled while there is no `detalle`. That's a sensible hardening.
- Clearing the previous page on a failed list load is acceptable: stale data never passes for current data, and the Alert has "Reintentar". The pagination side effect is covered in M3.
- The `SuscripcionEstadoHook.error` type changed from `boolean` to `ErrorApi | null`. Every consumer (`SuscripcionBanner`, `FacturacionPage`) was updated, and `tsc -b` is clean.
- The only double error presentation is the `/facturacion` one the report already notes.

**Tests**
- They use the real `httpClient` against MSW (`onUnhandledRequest: "error"`), with no axios mocks.
- The global `afterEach` clears `sessionStorage`/`localStorage`, `setAuthToken(null)` and `setUnauthorizedHandler(null)`. This resolves Task 1 Minor #1. The smoke test now checks `request.url` (Task 1 Minor #2).
- **Mutation claims reproduced in the scratch copy:**
  - removing the login exception fails H11 (1/38 in `src/shared`);
  - removing the dialog's `setErrorCarga` fails 2/5 in `features/tramites`.
- No test is vacuous. Each one asserts concrete text, `tipo` and `status`.

**Scope**
- `git status` shows nothing modified under `backend/`, `index.html`, `.agents/`, `.claude/`, `skills-lock.json`, `PRODUCT.md` or `DESIGN.md`. The untracked `public/*` files predate the task (Task 1 review).
- The `package.json` changes are Task 1's approved dev dependencies only. Task 2 adds none.
- There are no hex colours in the changed files (grep). Only the existing `Alert` and `Button` components are used.
- Nothing is staged (`git diff --cached` is empty).

## Critical
None.

## Important

**I1. The login context lets a backend motivo override the uniform 401 text.** `frontend/src/shared/api/errores.ts:212`

`if (errorApi.motivo) return errorApi.motivo;` runs before the `login` context's `noAutorizado`.

- **Scenario (probe):** `POST /auth/login` → `401 {"motivo":"Usuario inactivo"}`. `mensajeDeError(e, "login")` returns `"Usuario inactivo"`. The same happens with a `{mensaje}` or plain-text 401 body.
- **Why it matters today:**
  - the backend sends an empty 401, so nothing leaks right now;
  - but CLAUDE.md makes "login never reveals why" non-negotiable, and this brief requires that *no* backend motivo can change it;
  - the backend mini-prompt planned after A2 is about adding and unifying `{motivo}` bodies, which is exactly the change that would open this silently;
  - the report itself flags it as "habría que revisar".
- **Fix:**
  - in `mensajeDeError`, when `contexto === "login"`, ignore `motivo` at least for `no-autorizado`. Simplest is to ignore it for every tipo in `login`, since a login 400 has no useful user motivo either.
  - Example: `if (errorApi.motivo && contexto !== "login") return errorApi.motivo;`
  - add a test: `new ErrorApi({tipo:"no-autorizado", status:401, motivo:"X"})` with `"login"` returns `"Email o contraseña incorrectos."`.

## Minor

**M1. A plain-text body on a 5xx becomes the motivo and overrides the server and 503 texts.** `errores.ts:85-89`

- **Scenario (probe):** `500 text/plain "java.lang.NullPointerException at ..."` → the user sees that string instead of `TEXTO_ERROR_SERVIDOR`.
- **More realistic:** a gateway such as Envoy or GCP LB answers `503 text/plain "no healthy upstream"`. In `checkout`/`registro`, that replaces "La facturación todavía no está configurada…".
- Decision 23 only asks for the importer's **400** text.
- Spring itself doesn't produce text/plain 5xx for this client, so this depends on the deployment.
- **Fix:** accept string bodies (and preferably any motivo) only for 4xx. For example, pass `status` into `extraerMotivo` and return `undefined` when `status >= 500`. Add a test.

**M2. Two of the formerly silent paths have no test.**

- **Scenario (mutations in the scratch copy):** each of these leaves the suite at 45/45 green:
  - replacing `setErrorCarga(aErrorApi(err))` in `ExplotacionesPage.tsx:42` with a no-op;
  - disabling the `if (error)` branch in `SuscripcionBanner.tsx`.
- `LoginPage`, `RegistroPage`, `ImportarExcelSection` and `FacturacionPage` also have no component tests. Their behaviour does follow from the tested `mensajeDeError`, and the `LoginPage` 401 was probed OK.
- **Fix:** add one component test each for the Explotaciones list-load error + Reintentar and for the banner error + Reintentar. Optionally add a `LoginPage` 401 test that pins the uniform text end to end (pairs with I1).

**M3. A failed page load hides the pagination, so the user can't leave that page.** `ExplotacionesPage.tsx:41,116` and `TramitesPage.tsx:65,159`

- `setPagina(null)` removes the pager (`pagina && pagina.totalPages > 1`), while `numeroPagina` stays at N.
- **Scenario:** Explotaciones page 3 fails repeatedly. The only action left is "Reintentar", which reloads page 3 again.
  - Explotaciones has no filter, so there is no way back to page 1 short of navigating away.
  - Trámites can escape via the estado filter.
- **Fix:** when the error is on a page > 0, add a "Volver a la primera página" button to the Alert. Alternatively, keep the last known `totalPages` so the pager stays rendered.

**M4. The importer can surface a technical English POI message as the motivo (pre-existing, backend).** `backend/.../ExplotacionImportController.java:40-41`, `ExplotacionImportService.java:74`

- POI's `UnsupportedFileFormatException` extends `IllegalArgumentException` (checked in the `poi-5.3.0.jar` constant pool). Its subclasses include `NotOfficeXmlFileException` and `OLE2NotOfficeXmlFileException`.
- **Scenario:** uploading a `.xls` or a non-Excel file makes `new XSSFWorkbook(...)` throw one of these. The controller's `catch (IllegalArgumentException)` returns it as a plain-text 400, for example "The supplied data appears to be in the OLE2 Format. You are calling the part of POI that deals with OOXML…".
- The frontend now formally shows plain text verbatim.
- This behaviour is the same as before Task 2, and the backend is out of scope here.
- **Fix:** in the backend mini-prompt, have the service throw its own exception type (or map `UnsupportedFileFormatException` to a Spanish motivo such as "El fichero no es un Excel .xlsx válido"). This should go together with the planned `{motivo}` unification.

**M5. Cancellation maps to `desconocido`, which displays as a generic error.** `errores.ts:67`

- It is harmless today, because no caller aborts.
- If Tasks 7–9 introduce `AbortController`, for example for the explotación combobox or the animal panels, a normal abort would show "Ha ocurrido un error inesperado…" wherever the caller renders `mensajeDeError`.
- **Fix:** give cancellations their own marker (for example `esCancelacion(error)`, or a `cancelado` tipo) that callers ignore. Document it in `errores.ts` before the first abort-based caller lands.

# Re-review (fixes)

**VERDICT: Approved.** I1, M1, M2, M3 and M5 are fixed correctly, and each fix has a test that fails if the fix is reverted. There are no regressions. Two Minor notes are left, neither blocking: N1 is the implementer's leftover, with a one-line fix; N2 is pre-existing. M4 stays with the backend mini-prompt.

## Command results (from `frontend/`)

| Command | Result |
|---|---|
| `npm test` | exit 0: `Test Files 8 passed (8)`, `Tests 57 passed (57)` |
| `npm run build` | exit 0: `tsc -b` clean, `vite build` ✓; only the existing chunk > 500 kB warning |
| `npm run lint` | exit 0: 0 errors; the same 3 pre-existing `only-export-components` warnings |

## Mutation check

I ran this in a fresh scratchpad copy, with `node_modules` junctioned and the junction removed afterwards. Each mutation was applied on its own and then reverted; the repo was untouched.

| Mutation | Result |
|---|---|
| Drop `&& !textos?.ignorarMotivo` (I1) | 3 fail: errores login, errores registro, `LoginPage` e2e |
| Read the motivo on every status, not only 4xx (M1) | 3 fail: 500 text, 503 gateway text in checkout, 500 `{motivo}` |
| Cancel detection → `false` (M5) | 1 fails: the real `AbortController` request |
| `ExplotacionesPage` `.catch` no longer sets the error (M2) | 2 fail |
| `SuscripcionBanner` error branch disabled (M2) | 1 fails |
| Pager gated on `pagina` again, in Explotaciones (M3) | 1 fails |
| Pager gated on `pagina` again, in Trámites (M3) | 1 fails |

## Verified, no finding

**I1, uniform login and registro**
- `mensajeDeError` skips the motivo when the context has `ignorarMotivo`, and both `login` and `registro` set it.
- A login 401 always shows "Email o contraseña incorrectos.". The e2e `LoginPage.test.tsx` proves this with a real `AuthProvider` and MSW `401 {"motivo":"Usuario inactivo"}`, and also asserts that "Usuario inactivo" is absent.
- Red and servidor are still told apart, and registro's 503 text is unchanged (`porStatus` runs after the skipped motivo).
- **The registro text is byte-identical to the backend.** I compared the backend's `MENSAJE_REGISTRO_INVALIDO` (`RegistroGestoriaController.java:24-25`) with the frontend `registro.generico` using an exact shell string comparison: both are UTF-8, same accents, same final period. So the screen is unchanged for every 400.
- **Removing the `{mensaje}` fallback is safe:**
  - `RegistroErrorResponse` is the only backend DTO with a `mensaje` field;
  - no frontend code reads `.mensaje` (the only match is `detalle.mensajeOriginal`, unrelated);
  - a test pins that `{mensaje}` is not read.

**M1, 5xx responses never give a motivo**
- `aErrorApi` only calls `extraerMotivo` when `400 <= status < 500`, so 5xx and other statuses get `motivo: undefined`.
- The 503 checkout and registro texts survive a gateway's plain-text body (tested).
- The importer's 400 plain-text motivo is unaffected (its existing test still passes).

**M2, the missing tests**
- They use the real hook, component and httpClient, with MSW.
- They assert the concrete text, and that "Reintentar" recovers and clears the error.
- They are not vacuous: see the mutation table.

**M3, pagination after a failed page**
- `totalPaginas` only updates on a successful, non-cancelled load.
- The pager uses `numeroPagina + 1` and `totalPaginas`, so after a failure "Anterior", "Siguiente" and "Reintentar" all stay usable.
- Stale responses are still dropped by the `cancelado` flag.
- Both pages have a test for this.

**M5, `esCancelacion`**
- `ERR_CANCELED` or `axios.isCancel` produces `cancelado: true` with `tipo: "desconocido"`, so the tipo union is unchanged.
- `esCancelacion` is exported and documented, and `mensajeDeError` still returns non-empty text.
- It is tested with a real aborted request.
- No caller aborts yet, so nothing needs wiring now.

**Scope**
- Nothing is staged.
- Nothing changed under `backend/`, `index.html`, `.agents/`, `.claude/`, `skills-lock.json`, `PRODUCT.md` or `DESIGN.md`.
- `package.json` only has Task 1's approved dev dependencies; no new ones.
- No hex colours.

## Minor

**N1. While a new estado filter is loading, the pager shows the previous filter's total (the implementer's leftover).** `TramitesPage.tsx`, the filter's `onValueChange` and the `totalPaginas > 1` pager.

- **Scenario:** "TODOS" has 5 pages, and the user switches to a filter that has 1 page.
- **What happens:** during the load, the pager shows "Página 1 de 5" with "Siguiente" enabled.
  - If the user clicks it before the response arrives, the in-flight request is cancelled and page index 1 of the new filter is requested. Spring answers with an empty page and `totalPages: 1`.
  - The pager then hides itself (`totalPaginas > 1` is false), and the table says "No hay trámites que mostrar." on an out-of-range page. The user can only escape by changing the filter again.
  - If the new filter's load fails instead, the pager shows the stale "de 5".
- **Assessment:** a narrow timing window, so non-blocking. Before M3 the old `pagina` stayed on screen during the load too, so this is not a new class of problem.
- **Fix (one line):** call `setTotalPaginas(0)` next to `setNumeroPagina(0)` in the filter's `onValueChange`. Nothing is lost, because a filter change already goes back to page 0.

**N2. An out-of-range empty page hides the pager (pre-existing, not introduced by Task 2).**

- **Scenario:** the "Pendiente de revisión" filter has 21 trámites. The user is on page 2 (1 item) and approves it.
- **What happens:** `recargar` asks for page index 1 again, and Spring returns `content: []` with `totalPages: 1`. The pager hides, and the table says "No hay trámites que mostrar." although page 1 has 20.
- **Fix, for Task 8 or Task 10:** when a successful load returns empty `content` with `number > 0` and `number >= totalPages`, clamp `numeroPagina` to `max(0, totalPages - 1)`.
