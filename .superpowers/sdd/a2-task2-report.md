# A2 — Task 2: Cliente HTTP único y modelo de errores — Informe del implementador

## Ficheros

**Creados**
- `frontend/src/shared/api/errores.ts`: `ErrorApi`, `TipoErrorApi`, `esErrorApi`, `aErrorApi`,
  `tipoDeStatus`, `mensajeDeError`, `ContextoError`, `TEXTO_ERROR_RED` y `TEXTO_ERROR_SERVIDOR`.
- `frontend/src/shared/api/errores.test.ts`: 14 tests (modelo y `mensajeDeError`).
- `frontend/src/shared/api/httpClient.test.ts`: 24 tests (MSW contra el `httpClient` real).
- `frontend/src/features/tramites/TramiteReviewDialog.test.tsx`: 4 tests de componente.
- `frontend/src/features/tramites/TramitesPage.test.tsx`: 1 test de componente.

**Modificados**
- `frontend/src/shared/api/httpClient.ts`: el interceptor de respuesta convierte todo fallo en
  `ErrorApi`, y el 401 exceptúa `POST /auth/login`. El interceptor de petición (Bearer) no cambia.
- `frontend/src/test/setup.ts`: el `afterEach` global limpia `sessionStorage` y `localStorage` y
  llama a `setAuthToken(null)` y `setUnauthorizedHandler(null)`.
- `frontend/src/test/smoke.test.tsx`: comprueba que `request.url === apiUrl("/ping")`.
- Llamadores migrados (tabla más abajo): `LoginPage.tsx`, `RegistroPage.tsx`,
  `ImportarExcelSection.tsx`, `ExplotacionesPage.tsx`, `facturacion/api.ts`, `FacturacionPage.tsx`,
  `useSuscripcionEstado.ts`, `SuscripcionBanner.tsx`, `TramitesPage.tsx` y `TramiteReviewDialog.tsx`.

**Sin cambios**
- `authSession.ts`: no hizo falta un helper de reset, porque los setters que ya existían bastan.
  La Task 3 cambiará el almacenamiento, y el `sessionStorage.clear()` del setup ya cubre esa parte.
- `backend/`, `public/`, `index.html` y todo lo demás que estaba restringido.
- Tampoco hay dependencias nuevas ni operaciones git.

## Diseño

### `ErrorApi`: una clase, no un objeto etiquetado

`class ErrorApi extends Error { tipo; status?; motivo? }`.

Por qué una clase:
- Es un `Error` de verdad: tiene pila, `instanceof` es fiable en cualquier `catch` y la consola y los
  boundaries lo tratan bien.
- La guarda `esErrorApi` es trivial.
- El discriminante sigue siendo `tipo`, un union exhaustivo.

Otros detalles:
- El constructor normaliza `motivo`: si no es un string, o está vacío o en blanco, queda `undefined`.
  Si trae texto, se guarda recortado.
- Por `erasableSyntaxOnly` no uso parameter properties: los campos se declaran de forma explícita.

### Status → tipo

| Status | Tipo |
|---|---|
| 400 | `validacion` |
| 401 | `no-autorizado` |
| 403 | `prohibido` |
| 404 | `no-encontrado` |
| 409 | `conflicto` |
| ≥500 | `servidor` |
| Cualquier otro status (p. ej. 418 o 413) | `desconocido`, conservando el `status` |
| Sin respuesta | `red` |

Casos especiales:
- Una petición cancelada (`ERR_CANCELED`) es `desconocido`.
- Algo que no viene de axios también es `desconocido`.

### Extracción del motivo (nunca lanza)

- `{motivo}` (el formato del backend).
- Si no hay `motivo`, se usa `{mensaje}`: es el cuerpo del `400` de `POST /gestorias/registro`
  (`RegistroErrorResponse(mensaje)`). Sin él, el registro habría perdido su texto actual.
- Un string es el motivo (el `400` en texto plano del importador, decisión 23), **salvo** que la
  respuesta sea `text/html` o empiece por `<`. Así una página de error de un proxy no se enseña
  como motivo.
- Una respuesta en blanco, `{motivo: ""}`, `null`, un número o el JSON por defecto de Spring
  (`{error: "Internal Server Error"}`) dan `motivo` undefined.

### `mensajeDeError(error: unknown, contexto?: ContextoError): string`

Acepta cualquier cosa, porque pasa antes por `aErrorApi`. Orden de resolución:
1. el `motivo`, tal cual;
2. `porStatus[status]` del contexto (p. ej. 503 → "La facturación todavía no está configurada…");
3. `red` o `servidor` → los dos textos genéricos del plan;
4. `no-autorizado`, `prohibido` o `no-encontrado` → el texto del contexto si lo hay; si no, uno
   genérico de ese tipo ("Tu sesión ha caducado…", "No tienes permiso…" o "No se ha encontrado…");
5. el resto → el `generico` del contexto o, sin contexto, "Ha ocurrido un error inesperado.
   Inténtalo de nuevo.".

Nunca devuelve una cadena vacía, y hay un test que recorre cada contexto × cada tipo.

### Contextos

`ContextoError` es un union de strings, y `TEXTOS: Record<ContextoError, TextosContexto>` guarda
`{ generico (obligatorio), noAutorizado?, prohibido?, noEncontrado?, porStatus? }`. Como el
`Record` es exhaustivo, las Tasks 7–9 añaden un nombre al union (p. ej. `guardar-tramite`,
`listar-ganaderos`, `detalle-ganadero` o `animales-explotacion`) y TypeScript las obliga a darle al
menos el texto genérico.

Contextos de hoy:

| Contexto | Textos propios |
|---|---|
| `login` | 401: "Email o contraseña incorrectos." |
| `registro` | 503: facturación no configurada |
| `checkout` | 503: facturación no configurada |
| `suscripcion` | solo el genérico |
| `importar-excel` | solo el genérico |
| `listar-explotaciones` | solo el genérico |
| `listar-tramites` | solo el genérico |
| `detalle-tramite` | 404: "Este trámite ya no existe o no es de tu gestoría." |
| `aprobar-tramite` | 404: el mismo que `detalle-tramite`; 403: el texto de suscripción literal de `TramiteReviewDialog` (H2) |
| `rechazar-tramite` | 404: el mismo que `detalle-tramite` |

### Interceptor de respuesta

- Rechaza siempre con `aErrorApi(error)`.
- Llama a `notifyUnauthorized()` si el tipo es `no-autorizado` y la petición **no** es
  `POST …/auth/login`, algo que se comprueba con `httpClient.getUri(config)` → `pathname`.
- Un 403 nunca cierra la sesión.

## Llamadores migrados

| Llamador | Antes | Después | ¿Era silencioso? |
|---|---|---|---|
| `LoginPage` | `isAxiosError` + 401 → credenciales; el resto → "No se ha podido conectar con el servidor…" | `mensajeDeError(err, "login")`: el 401 da el mismo texto uniforme; la red y el 5xx dan los textos del plan | No |
| `RegistroPage` | 400 → `data.mensaje` o genérico; 503 → facturación; el resto → "conectar" | `mensajeDeError(err, "registro")`: el 400 muestra `mensaje` (vía `motivo`); el 503 igual que antes | No |
| `ImportarExcelSection` | un cuerpo string → se mostraba tal cual; el resto → genérico | `mensajeDeError(err, "importar-excel")`: el texto plano llega como `motivo` | No |
| `facturacion/api.ts` | `isAxiosError` + 404 → `null` | `esErrorApi(error) && error.tipo === "no-encontrado"` → `null` | No |
| `FacturacionPage`, checkout | 503 → facturación; el resto → "No se ha podido iniciar el pago…" | `mensajeDeError(err, "checkout")` | No |
| `FacturacionPage`, carga | Alert con el título y "Reintentar", **sin decir por qué** | añade `mensajeDeError(error, "suscripcion")` al Alert | Parcial |
| `useSuscripcionEstado` | `error: boolean` | `error: ErrorApi \| null` (con `aErrorApi`) | — |
| `SuscripcionBanner` | con error → `return null` (**no se veía nada**) | con error → Alert "No se ha podido comprobar tu suscripción" + motivo + "Reintentar" | **Sí** |
| `ExplotacionesPage`, listado | `.then().finally()` **sin `.catch`**: rechazo no gestionado y tabla vacía o vieja | `.catch` → Alert con el mensaje + "Reintentar"; sin página vieja a la vista | **Sí** |
| `TramitesPage`, cola | igual, **sin `.catch`**; el subtítulo se quedaba en "Cargando…" | `.catch` → Alert con el mensaje + "Reintentar"; el subtítulo muestra "—" | **Sí** |
| `TramiteReviewDialog`, detalle | **sin `.catch`**: el modal quedaba en blanco, con Aprobar/Rechazar activos | Alert "No se ha podido cargar el trámite" + mensaje (404 contextual); Aprobar/Rechazar desactivados sin detalle | **Sí** |
| `TramiteReviewDialog`, aprobar | 403 → suscripción; el resto → genérico (**el 400 escondía el motivo**) | `mensajeDeError(err, "aprobar-tramite")`: el 400 enseña el `motivo` del backend y el 403 sigue igual | Sí (ocultaba el motivo) |
| `TramiteReviewDialog`, rechazar | `catch {}` → siempre genérico | `mensajeDeError(err, "rechazar-tramite")` | Sí (ocultaba el motivo) |
| `AuthContext.login` | deja subir el error | sin cambios: ahora sube un `ErrorApi` | — |

`grep -rn "isAxiosError\|response?.status\|from \"axios\""` fuera de los tests solo encuentra
`shared/api/errores.ts` y `shared/api/httpClient.ts`.

## TDD: evidencia

Escribí los tests antes de tocar el código. Primera ejecución: `Test Files 3 failed | 1 passed`,
`Tests 3 failed | 3 passed`.
- `errores.test.ts` y `httpClient.test.ts` fallaron porque `@/shared/api/errores` no existía.
- De `TramiteReviewDialog.test.tsx` fallaron 3 de 4:
  - error 500 del detalle: no aparecía el texto;
  - error 404 del detalle: tampoco;
  - error 400 de aprobar: no se veía el motivo.
  
  Vitest informó además de **rechazos no gestionados** (`AxiosError: Request failed with status
  code 500/404`), que prueban el hueco silencioso.
- El test del 403 de aprobar ya pasaba, porque es un test de "el comportamiento se mantiene".

Después implementé `errores.ts` y `httpClient.ts`: los 38 tests de `src/shared` pasaron en verde.
Luego migré los llamadores, y todo quedó en verde.

Pruebas de mutación, revertidas después:
- Quitar la excepción del login en el interceptor hace fallar el test H11 (1 de 23 falla).
- Quitar `setErrorCarga` del `.catch` de `TramitesPage` hace fallar su test.

## Resultados (desde `frontend/`)

| Comando | Resultado |
|---|---|
| `npm test` | exit 0: `Test Files 5 passed (5)`, `Tests 45 passed (45)` (antes eran 2) |
| `npm run build` | exit 0: `tsc -b` limpio y `vite build` ✓. Solo aparece el aviso de chunk > 500 kB que ya existía (531 kB) |
| `npm run lint` | exit 0: 0 errores y los mismos 3 avisos previos `only-export-components` (`button.tsx:58`, `badge.tsx:55`, `AuthContext.tsx:65`) |

## Desviaciones y riesgos

- **Se lee también `{mensaje}`, no solo `{motivo}`.** Es necesario para no perder el texto del 400
  del registro, que usa `RegistroErrorResponse(mensaje)`. Encaja en el mini-prompt de backend:
  unificarlo en `{motivo}`.
- **Textos genéricos que cambian un poco de redacción:** el login, el registro y el checkout
  mostraban "No se ha podido conectar con el servidor. Inténtalo de nuevo." para cualquier fallo.
  Ahora distinguen entre red ("No se ha podido conectar con Ganera…") y 5xx ("Ha fallado algo en el
  servidor…"), como pide la decisión 2. Los textos del 401 del login, del 503 y del 403 de aprobar
  son idénticos a los de antes.
- **Motivo verbatim y login uniforme:** si algún día el backend mandara un cuerpo en el 401 del
  login, se mostraría. Hoy el 401 del login es vacío (contrato de la sección 1). Si eso cambiara,
  habría que revisar `mensajeDeError` para ese contexto.
- **Doble aviso en `/facturacion` si falla la carga de la suscripción:** saldrían el banner del
  layout y el Alert de la página. Es visible y correcto, pero está duplicado; lo dejo para el pulido
  de la Task 10.
- **Aprobar/Rechazar desactivados sin detalle cargado:** es un cambio pequeño de comportamiento,
  para que nadie actúe sobre un trámite que no ha podido ver. Aprobar sigue dando 400 por falta de
  `version`. Ahora muestra el `motivo` y la Task 9 arregla la causa.
- **Listados:** si una carga falla, se vacía la página anterior (`setPagina(null)`), para no
  presentar datos viejos como actuales. Tras aprobar con éxito, una recarga fallida deja la cola
  vacía con el error y "Reintentar".
- **HTML como cuerpo:** se descarta por `content-type` o si el texto empieza por `<`. Un texto plano
  legítimo que empiece por `<` perdería su motivo y mostraría el genérico. Es un riesgo aceptable.
- **Sin cambios de diseño:** solo se reutilizan `Alert` y `Button` que ya existían, y todo con
  tokens, sin colores fijos.

## Fixes after review (`a2-task2-review.md`)

Every fix was test-first. I wrote 13 new tests before touching the code. The first run gave
`Test Files 5 failed | 3 passed (8)` and `Tests 11 failed | 46 passed (57)`: the 11 I1, M1, M3 and
M5 tests failed for the expected reason. The 2 M2 tests passed straight away, because the
handling already existed. I proved they are real with the mutations described below.

### I1 — Uniform login and registro, whatever the backend sends
- `TextosContexto` gains `ignorarMotivo`, set on `login` and `registro`. In those contexts the
  backend `motivo` is never shown; the uniform context text is shown, with red and servidor still
  telling the two apart.
- **Registro:** the text is now a frontend constant, a literal copy of the backend's
  `MENSAJE_REGISTRO_INVALIDO` ("No se ha podido completar el registro con esos datos. Revisa el
  email y la contraseña e inténtalo de nuevo."). The screen shows exactly the same as before.
- **Why this is safe:** the text no longer depends on the backend. If a backend change ever added
  a specific `motivo` or `mensaje` (for example "email ya registrado"), it would not leak, so there
  is no enumeration oracle.
- The `{mensaje}` fallback in `extraerMotivo` is **removed**. It only existed for registro, and no
  other endpoint uses it.
- Tests:
  - `errores.test.ts`:
    - a login 401 and a login 400 that both carry a motivo;
    - a registro 400 with and without a motivo.
  - `httpClient.test.ts`: `{mensaje}` is not read.
  - `LoginPage.test.tsx` (new, end to end with `AuthProvider` + MSW): a 401
    `{"motivo":"Usuario inactivo"}` shows "Email o contraseña incorrectos." and never "Usuario
    inactivo".

### M1 — A motivo only on 4xx
- `aErrorApi` only calls `extraerMotivo` when `400 <= status < 500`.
- On a 5xx, no body is ever read, whether JSON or text.
- Tests:
  - `500 text/plain` with a Java stack trace → no motivo, and `TEXTO_ERROR_SERVIDOR` is shown;
  - `503 text/plain "no healthy upstream"` in `checkout` → "La facturación todavía no está
    configurada…";
  - `500 {motivo}` JSON → no motivo.

### M2 — Tests for the error paths that were missing
- `ExplotacionesPage.test.tsx`: a list 500 shows the Alert with the server text, and "Reintentar"
  loads the data.
- `SuscripcionBanner.test.tsx`: uses the real hook plus the banner, as `AppLayout` mounts them. A
  network error shows "No se ha podido comprobar tu suscripción" with the message, and
  "Reintentar" loads the state.
- **Mutations, reverted afterwards:**

  | Change | Result |
  |---|---|
  | Remove `setErrorCarga(aErrorApi(err))` in `ExplotacionesPage` | 2/2 tests fail |
  | Change the banner's `if (error)` to `if (false)` | 1/1 test fails |

### M3 — Pagination survives a failed page
- `ExplotacionesPage` and `TramitesPage` keep `totalPaginas`, the last known total, and only update
  it on a successful load.
- The pager renders when `totalPaginas > 1` and shows `numeroPagina + 1`. So after a failure the
  user can still use "Anterior", "Siguiente" or "Reintentar".
- Test on both pages: page 2 of 3 fails, then:
  - the Alert is shown;
  - "Página 2 de 3" is still shown;
  - "Anterior" works and the error disappears.

### M5 — Cancellations marked
- `ErrorApi.cancelado` (a boolean) and `esCancelacion(error)`.
- `ERR_CANCELED` / `axios.isCancel` → `{tipo: "desconocido", cancelado: true}`.
- It's documented in `errores.ts` that whoever cancels checks `esCancelacion` in their `catch` and
  shows nothing. `mensajeDeError` still returns non-empty text, just in case.
- I didn't add a new `tipo`, so the list of 8 tipos stays as the brief defines it.
- Tests:
  - `AbortController.abort()` on a real request (MSW `delay("infinite")`) → `esCancelacion`
    is true;
  - a unit test of `esCancelacion`.

### M4
Not touched. It is a backend issue and goes to the backend mini-prompt.

### Results after the fixes (from `frontend/`)

| Command | Result |
|---|---|
| `npm test` | exit 0: `Test Files 8 passed (8)`, `Tests 57 passed (57)` (45 → 57) |
| `npm run build` | exit 0: `tsc -b` is clean and `vite build` ✓. Only the chunk > 500 kB warning, which already existed |
| `npm run lint` | exit 0: 0 errors, and only the 3 pre-existing `only-export-components` warnings |

### Files touched in this round
- **Modified:**
  - `src/shared/api/errores.ts`
  - `src/shared/api/errores.test.ts`
  - `src/shared/api/httpClient.test.ts`
  - `src/features/explotaciones/ExplotacionesPage.tsx`
  - `src/features/tramites/TramitesPage.tsx`
  - `src/features/tramites/TramitesPage.test.tsx`
- **New:**
  - `src/features/explotaciones/ExplotacionesPage.test.tsx`
  - `src/features/facturacion/SuscripcionBanner.test.tsx`
  - `src/features/auth/LoginPage.test.tsx`
- Nothing in `backend/`, no dependencies, no git.

### Remaining risk
While a filter change is loading in Trámites, the pager keeps the previous filter's total until the
response arrives. If the new filter's load fails, the pager shows that previous total. That's
acceptable: going back to page 1 or retrying is still possible.
