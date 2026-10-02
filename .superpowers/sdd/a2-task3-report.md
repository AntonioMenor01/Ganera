# A2 — Task 3: Sesión persistente (decisión 1) — Informe del implementador

## Ficheros

**Creados**
- `frontend/src/shared/api/authSession.test.ts`: 8 tests unitarios (clave, escritura, borrado, restaurar y los 3 fallos del almacenamiento).
- `frontend/src/shared/auth/sesion.test.tsx`: 13 tests de integración. Montan `AuthProvider` + las **rutas reales** (`routes`) en un `createMemoryRouter`, contra el `httpClient` real y MSW.

**Modificados**
- `frontend/src/shared/api/authSession.ts`:
  - `CLAVE_TOKEN = "ganera.token"`;
  - `setAuthToken` escribe en memoria y en `sessionStorage`, o borra la clave si recibe `null`;
  - nuevo `restaurarTokenGuardado()`;
  - todo acceso al almacenamiento va en try/catch, incluido el getter de `window.sessionStorage`.

  `getAuthToken` sigue leyendo la memoria, y `httpClient` no cambia.
- `frontend/src/shared/auth/AuthContext.tsx`: añade `estado` (`EstadoSesion`), `errorComprobacion`, `motivoCierre`, `reintentarComprobacion` y `olvidarMotivoCierre`. También hay cambios en:
  - la comprobación inicial con `/auth/me`;
  - el rollback del login;
  - el guard del 401.
- `frontend/src/shared/auth/RequireAuth.tsx`:
  - estado de carga (`role="status"`, "Comprobando tu sesión…");
  - pantalla de error con "Reintentar" y "Salir";
  - `Navigate` a `/login` con `state.from`;
  - exporta el tipo `EstadoRedireccionLogin`.
- `frontend/src/features/auth/LoginPage.tsx`:
  - aviso de sesión caducada (una sola vez);
  - redirección a `from` validado, o a `/tramites`;
  - los textos de error no cambian.
- `frontend/src/shared/api/errores.ts`: nuevo contexto `"comprobar-sesion"`, con el genérico "No se ha podido comprobar tu sesión. Inténtalo de nuevo.". Red y 5xx siguen usando `TEXTO_ERROR_RED` y `TEXTO_ERROR_SERVIDOR`.
- `frontend/src/router.tsx`: exporta `routes: RouteObject[]` y construye `router = createBrowserRouter(routes)`. Es un refactor estructural, sin cambio de comportamiento, para que los tests monten las rutas reales.

**Sin cambios:** `httpClient.ts` (H11 ya exceptuaba `/auth/login`), `main.tsx`, `AppLayout.tsx` ("Salir" sigue llamando a `logout`), `test/setup.ts` (su `afterEach` ya limpia `sessionStorage`/`localStorage` y los setters), `backend/`, `public/`, `index.html` y las skills. No hay dependencias nuevas ni operaciones git.

## Diseño

### Estados (`EstadoSesion`)

| Estado | Cuándo | Qué renderiza `RequireAuth` |
|---|---|---|
| `anonima` | Sin token, tras un 401 o tras "Salir" | `<Navigate to="/login" state={{from}}>` (sin `from` tras "Salir") |
| `comprobando` | Al arrancar con un token guardado, o al pulsar Reintentar | `<p role="status" aria-live="polite">Comprobando tu sesión…</p>`. Nunca el login, y la URL no cambia |
| `error-comprobacion` | `/auth/me` falla por red, 5xx u otro motivo que no sea 401 | `Alert` con `mensajeDeError(err, "comprobar-sesion")`, más "Reintentar" y "Salir". **El token se conserva** |
| `activa` | `/auth/me` devuelve 200 (al arrancar o tras el login) | `<Outlet/>`: la ruta pedida, con su query (el deep link se mantiene) |

Detalles:
- El `useState` inicial lee el almacenamiento con `restaurarTokenGuardado()`. Por eso el primer render ya es `comprobando` y no hay parpadeo del login.
- La comprobación inicial se lanza una sola vez, protegida con un ref, así que no se duplica bajo StrictMode.
- `comprobacionRef` descarta la respuesta de una comprobación anterior (un reintento, o un cierre mientras comprueba).
- **Login:**
  1. `POST /auth/login`.
  2. `setAuthToken(token)`, que lo guarda en memoria y storage para que `/auth/me` lleve el Bearer.
  3. `GET /auth/me`. Si falla, `setAuthToken(null)` (rollback: nada queda guardado) y se relanza el error, así que el login muestra su error uniforme.
  4. Solo con usuario se pasa a `activa`.
- **Guard del 401:** el manejador de `notifyUnauthorized` solo cierra la sesión si `sesionActivaRef.current` (hay sesión validada). El 401 de la comprobación inicial lo trata `comprobarSesion`; el del `/auth/me` del login lo trata `login`; y el del propio `/auth/login` no llega nunca (H11).

### Cómo viaja el aviso: con un flag en el contexto, no con el state del router

`motivoCierre: "caducada" | "manual" | null` vive en `AuthProvider`. Por qué no uso el state del router:
1. El 401 en uso llega desde `httpClient`, fuera del router. `AuthProvider` envuelve a `RouterProvider` y no tiene `navigate`. Quien redirige es `RequireAuth`, cuando se re-renderiza con `anonima`.
2. El state del router se guarda en `history.state`. Sobrevive a una recarga de `/login` y al botón atrás/adelante, así que el aviso se repetiría. Eso rompe el "una sola vez".

El flag está en memoria. `LoginPage` lo copia a su estado local al montarse (`useState(() => motivoCierre === "caducada")`) y lo consume en un efecto (`olvidarMotivoCierre()`). Así:
- una segunda visita a `/login` no muestra el aviso;
- una visita normal nunca lo muestra, porque el flag solo se pone con una sesión existente (el 401 con `sesionActivaRef` o el 401 del arranque con token).

El aviso desaparece al enviar el formulario. `"manual"` ("Salir") además hace que `RequireAuth` no guarde `from`: el siguiente login, quizá de otra persona, empieza en `/tramites`.

Lo que sí va en el state del router es `from` (path + search + hash), porque eso pertenece a la navegación. `LoginPage` lo valida antes de usarlo:
- tiene que empezar por `/`;
- no puede ser `//…`;
- no puede ser `/login` ni `/`.

Si no pasa la validación, el destino es `/tramites`. Antes era `navigate("/")`, que acababa en el mismo sitio.

### Almacenamiento y fallback
- Solo se guarda `sessionStorage["ganera.token"]`: nada del usuario y nada en `localStorage`. Un test espía `Storage.prototype.setItem` y comprueba que la única escritura del login es `["ganera.token", "tok-nuevo"]`.
- Hay try/catch en lectura, escritura y borrado, y en el propio getter `window.sessionStorage` (modo privado o cookies bloqueadas lanzan `SecurityError` ahí). Si falla, la sesión vive solo en la variable de módulo, exactamente como antes de esta tarea; solo se pierde la supervivencia a la recarga.
- Un valor guardado en blanco se trata como ausente.
- **Varias pestañas:** `sessionStorage` es por pestaña a propósito y no se añade sincronización.
  - Una pestaña nueva pide login. Ojo: "Duplicar pestaña" en Chrome/Firefox **sí** copia el `sessionStorage`; es el comportamiento estándar.
  - "Salir" en una pestaña no cierra las demás; cada una caduca por su cuenta con su 401.
  - Está documentado en el comentario de `authSession.ts`.

## Evidencia TDD

1. Primero hice el refactor estructural de `router.tsx` (export de `routes`), para que los tests fallaran por comportamiento y no por imports.
2. Después escribí los 20 tests iniciales, antes de cualquier implementación.
3. **RED** (`npx vitest run src/shared/api/authSession.test.ts src/shared/auth/sesion.test.tsx`): `Tests 15 failed | 5 passed (20)`. Cómo fallaron:
   - `authSession.test.ts`:
     - `expected undefined to be 'ganera.token'` (no existía `CLAVE_TOKEN`);
     - `expected null to be 'tok-1'` (no se escribía en `sessionStorage`);
     - `TypeError: restaurarTokenGuardado is not a function` (4 tests).
   - `sesion.test.tsx`:
     - recarga con token: `Unable to find an element with the text: Ganaderos (pendiente)`, porque rebotaba a `/login`;
     - carga: `Unable to find an accessible element with the role "status"`;
     - 401 al arrancar y 401 en uso: `Unable to find ... Tu sesión ha caducado...`;
     - red y 5xx: no aparecían `TEXTO_ERROR_RED` ni `TEXTO_ERROR_SERVIDOR`;
     - "Salir": `Unable to find role="button" and name "Salir"`, porque nunca llegaba a la app;
     - login con vuelta a `from` y login con storage inaccesible: acababan en `/tramites` y no en `/ganaderos`.
   - Los 5 que pasaron sin implementación eran regresiones esperadas del comportamiento actual:
     - `setAuthToken(null)`;
     - escribir en un storage que lanza (antes no se escribía);
     - `/login` sin aviso;
     - login directo → `/tramites`;
     - error uniforme del login.
4. **GREEN** tras la implementación: `Tests 20 passed (20)`.
5. Después añadí un test más (rollback del login si `/auth/me` falla), por la mutación M4 de abajo. Pasa con la implementación y falla con M5.

## Mutaciones (aplicadas a mano y revertidas desde una copia; ficheros verificados idénticos con `diff`)

| # | Mutación | Resultado |
|---|---|---|
| M1 | El 401 en uso cierra con `"manual"` en vez de `"caducada"` | **Muere**: falla "un 401 en mitad del uso… avisa una sola vez" |
| M2 | Cualquier fallo de `/auth/me` (no solo el 401) desloguea | **Muere**: fallan "error de red… Reintentar" y "5xx… tampoco cierra la sesión" |
| M3 | Sin try/catch en `guardar()` | **Muere**: fallan 4 tests (3 unitarios de storage que lanza + "con sessionStorage inaccesible, el login funciona en memoria") |
| M4 | El manejador del 401 ignora `sesionActivaRef` | **Sobrevive, equivalente**. Ver la nota debajo de la tabla |
| M4+ | M4 junto con que `LoginPage` solo consuma `"manual"` | **Muere**: fallan "si /auth/me falla justo tras el login…" y "401 en mitad del uso…" |
| M5 | Sin rollback `setAuthToken(null)` en el login | **Muere**: falla "si /auth/me falla justo tras el login, no queda token guardado…" |

Nota sobre M4: en todos los caminos alcanzables el resultado es el mismo.
- En el arranque, `comprobarSesion` hace el mismo cierre.
- En el login, `LoginPage` ya está montada y consume cualquier motivo que llegue.

El guard es una segunda capa defensiva. M4+ demuestra que los tests detectan su ausencia en cuanto falla la otra capa.

## Comandos (desde `frontend/`)

| Comando | Resultado |
|---|---|
| `npm test` | exit 0: `Test Files 10 passed (10)`, `Tests 78 passed (78)` (57 previos + 21 nuevos). Sin avisos de `act(...)` ni rechazos sin manejar |
| `npm run build` | exit 0: `tsc -b` limpio, y `vite build` ✓ con solo el aviso ya existente de chunk > 500 kB |
| `npm run lint` | exit 0: 0 errores y 3 warnings, las 3 preexistentes `only-export-components` (`badge.tsx:55`, `button.tsx:58`, `AuthContext.tsx:192`; la última es el mismo `useAuth`, que solo ha cambiado de línea) |

## Riesgos y notas

- **XSS:** el token en `sessionStorage` es legible por cualquier script que se ejecute en la página. Frente al estado anterior (variable de módulo), la exposición ante XSS es prácticamente la misma: un script inyectado también puede leer la memoria o hacer peticiones con el interceptor. Lo que cambia es que ahora el token sobrevive a la recarga dentro de la pestaña. Ni `sessionStorage` ni `localStorage` protegen frente a XSS. La alternativa robusta, una cookie `httpOnly` + `SameSite` (con CSRF), requiere cambios de backend: queda fuera de A2, como dice la decisión 1.
- **`sessionStorage` frente a `localStorage`:**
  - se borra al cerrar la pestaña y no se comparte entre pestañas, que es lo más cercano al "solo sesión" anterior;
  - "Duplicar pestaña" copia el token (estándar del navegador);
  - "Restaurar sesión" del navegador también puede restaurarlo.
- **Caducidad del JWT:** no hay refresh token. Una recarga con un token caducado da 401 en `/auth/me`, que muestra el aviso y lleva a `/login`: comportamiento correcto.
- **`/login` con token guardado:** `/login` está fuera de `RequireAuth`, así que visitar `/login` con un token válido guardado muestra el formulario mientras la comprobación corre de fondo. No redirige solo a la app. No se pedía; lo anoto por si la Task de marca o `polish` lo quiere.
- **Estado de error de la comprobación:** incluye "Salir" como vía de escape si el backend sigue caído; limpia el token y no muestra aviso. El diseño es mínimo, con tokens de Tailwind y sin hex; el pulido queda para Impeccable.
- **`from`** viaja en `history.state` (no en la URL), así que no es controlable por un enlace externo. Aun así se valida, para evitar un open-redirect por un `//` futuro.

## Lo que `CLAUDE.md` tendrá que decir (Task 11)

Hay que sustituir el bullet "Auth is in-memory, not `localStorage`, on purpose…" de *Architecture notes (frontend)*. El nuevo texto debe decir:
- El token (solo el token) está en `sessionStorage["ganera.token"]` a través de `shared/api/authSession.ts`, que sigue siendo la única fuente que lee `httpClient`. Todo acceso va en try/catch, con fallback a memoria.
- Al arrancar con token, `AuthProvider` pasa por `comprobando` → `GET /auth/me`:
  - 200 → `activa` (se mantiene el deep link);
  - 401 → `/login` con el aviso de caducada;
  - red/5xx → `error-comprobacion` con "Reintentar", sin desloguear.
- El aviso de caducada viaja como `motivoCierre` en el contexto, no en el state del router, y `LoginPage` lo consume una vez. Solo `from` va en el state del router.
- Es por pestaña, sin sincronización entre pestañas, a propósito. No protege frente a XSS: la alternativa es una cookie httpOnly, que requiere backend.
- `router.tsx` exporta `routes` para los tests.
- El número de tests del frontend es ahora 78 (113 tras las correcciones de abajo).

---

## Correcciones tras la revisión

Cubre `.superpowers/sdd/a2-task3-review.md` (M1–M7). He trabajado test primero: cada test que acompaña a un arreglo se escribió antes y falló por el motivo esperado. M4 y M6 solo pedían tests; demuestro que son reales con mutaciones.

### Ficheros (además de los de arriba)
- **Nuevos:**
  - `frontend/src/features/auth/destinoTrasLogin.ts`: `destinoTrasLogin`, sacado de `LoginPage.tsx`. Primero lo moví tal cual (refactor puro), para que los tests fallaran por comportamiento y no por imports; después lo endurecí.
  - `frontend/src/features/auth/destinoTrasLogin.test.ts`: 28 casos.
- **Modificados:** `AuthContext.tsx`, `RequireAuth.tsx`, `LoginPage.tsx` (importa el módulo nuevo), `authSession.ts`, `httpClient.ts`, `sesion.test.tsx` (+5 tests y el de Salir ampliado para M6) y `httpClient.test.ts` (+2 tests).

### Qué cambió, hallazgo por hallazgo

**M1: una comprobación del arranque pendiente frente a un login enviado entretanto.**
- `login` hace ahora `const intento = ++comprobacionRef.current` **antes** del POST, así que el resultado de una comprobación pendiente se descarta.
- El RED destapó un segundo camino al mismo fallo que la revisión no nombraba. El 401 tardío de la comprobación vieja también pasa por el **manejador global del 401**, y para entonces `sesionActivaRef` ya es true por el login nuevo, así que cerraba la sesión nueva igualmente. El arreglo es general:
  - `httpClient` pasa el token con el que salió la petición fallida (`tokenDeLaPeticion(config)`, leído de su cabecera `Authorization`);
  - `notifyUnauthorized(tokenUsado)` ignora un 401 cuyo token ya no es el actual.
  - Esto protege frente a cualquier 401 tardío de un token viejo, no solo frente a esta carrera.
- Un login correcto manda: vuelve a fijar el token solo si algo lo borró mientras esperaba (así no hay escritura duplicada en el almacenamiento) y fija el estado de React en el mismo paso.

**M2: falla el `/auth/me` del login cuando ya había una sesión.** He elegido un **reinicio completo, coherente y silencioso**: `cerrarSesion(null)` ante cualquier fallo del login limpia a la vez memoria, almacenamiento y estado de React, sin aviso. Por qué no restaurar el token anterior:
1. con M1, la comprobación de la sesión anterior puede haber quedado descartada, así que no sabemos si ese token es válido;
2. quien está entrando puede ser otra persona, y no hay que darle la identidad anterior;
3. deja un invariante simple, "tras un login fallido no hay sesión", que coincide con lo que la pantalla de login le dice al usuario.

Si no había sesión, el reinicio no cambia nada.

**M3: aviso de "caducada" rancio tras `/registro`.**
- `RequireAuth` se registra mientras está montada (`registrarRutaProtegida`, un contador en un ref del contexto).
- Un 401 al arrancar o en uso pone `"caducada"` solo si hay una ruta protegida en pantalla; si no, cierra sin aviso (`motivoDe401()`).
- En `/registro` o `/login` no hay nada que avisar, y no queda nada pendiente para una visita posterior.

Lo prefiero a "limpiar al montar `RegistroPage`", porque la comprobación puede resolverse *después* de que esa página se monte.

**M4: test del guard `sesionActivaRef`.** El test hace Salir → `/registro` → `notifyUnauthorized()` (un 401 tardío) → `/login` y comprueba que no hay aviso. Pasaba antes y después, porque solo es un test. Tras el arreglo de M3, el guard vuelve a ser una de dos capas; ver las mutaciones F y F+E.

**M5: `from` endurecido.** Solo acepta rutas internas del mismo origen:
- tiene que empezar por `/` y no por `//`;
- sin `\` en ninguna parte;
- sin caracteres de control (el parser de URL quita `\t`/`\n`, y eso convierte `/\t/evil.com` en `//evil.com`);
- `new URL(from, window.location.origin).origin` tiene que ser igual al origen actual;
- se rechazan `/`, `/login` y `/registro`, con o sin barra final, query o hash.

Devuelve path + search + hash normalizados por el parser de URL. Los tests unitarios cubren:
- aceptados: `/ganaderos/5?x=1`, `/tramites?estado=APROBADO` y `/explotaciones#arriba`;
- rechazados, incluidas las variantes **con ruta**: `//evil.com`, `/\evil.com`, `\\evil.com`, `/ganaderos\..\..\evil.com`, `https://evil.com`, `javascript:alert(1)`, `/\t/evil.com` y `/\n/evil.com`. La ruta importa: sin ella, `/\evil.com` resuelve al path `/`, y la exclusión de `/` taparía que falta la comprobación de origen;
- también rechazados: `/login` (con `?x=1`, `#a` y `/`), `/registro` (con `?x=1`), `/`, `""` y `ganaderos`;
- `state` que no es texto o que falta.

**M6:** el test de Salir sigue: volver a entrar lleva a `/tramites`, no a la página anterior.

**M7:** informativo, sin cambios.

### Evidencia TDD (RED antes de cada arreglo)
1. **Primer RED.** Comando: `npx vitest run src/features/auth/destinoTrasLogin.test.ts src/shared/auth/sesion.test.tsx` → `Tests 9 failed | 32 passed (41)`.
   - M5, 6 casos: `expected '/\evil.com' to be '/tramites'`, y lo mismo para `/ganaderos\..\..\evil.com`, `/\t/evil.com`, `/login/`, `/registro` y `/registro?x=1`.
   - M3: el aviso aparecía en `/login` (`expect(element).not.toBeInTheDocument()`).
   - M1: `expected null to be 'tok-nuevo'`: el token del login nuevo se había borrado.
   - M2: `expected '/ganaderos' to be '/login'`: React seguía creyendo que la sesión estaba activa.
   - M4 y M6 pasaban: son tests de comportamiento existente y se demuestran con mutaciones.
2. **M1 seguía fallando tras el primer arreglo.** Con solo el `++comprobacionRef` al principio de `login`, M1 seguía en RED (`expected null to be 'tok-nuevo'`). Eso llevó al guard del 401 con token viejo.
3. **El test del propio guard.** Escribí primero el test de `httpClient`, y falló: `expected "vi.fn()" to not be called at all, but actually been called 1 times`.
   - Su primera versión tenía un fallo de tiempos: axios ejecuta los interceptores de petición de forma asíncrona, así que el cambio al token nuevo ocurría antes de que saliera la petición.
   - Lo corregí para cambiar el token solo cuando el servidor ya ha recibido `Bearer tok-viejo`. La mutación A confirma que ahora falla sin el guard.
4. **GREEN:** todo pasa.

### Mutaciones (aplicadas de una en una y revertidas desde una copia; ficheros verificados idénticos con `diff`)

| # | Mutación | Resultado |
|---|---|---|
| A | `notifyUnauthorized` sin la comprobación del token viejo | **Muere**: el test de `httpClient` + M1 (comprobación tardía) |
| B | `login` no invalida la comprobación pendiente | Sobrevive sola (por capas, ver B+C) |
| C | Un login correcto no vuelve a fijar el token | Sobrevive sola (por capas, ver B+C) |
| B+C | Las dos | **Muere**: "M1: la comprobación vieja responde 401 en mitad del login…" |
| D | El rollback del login solo hace `setAuthToken(null)` (lo original) | **Muere**: M2 |
| E | Un 401 siempre es `"caducada"`, sea cual sea la ruta | **Muere**: M3 |
| F | El manejador del 401 ignora `sesionActivaRef` | Sobrevive sola (sin ruta protegida el motivo ya es `null`) |
| F+E | Sin guard y siempre `"caducada"` | **Muere**: M4 + M3 |
| G | `RequireAuth` guarda `from` también tras Salir | **Muere**: test de Salir (M6) |
| H1 | Sin rechazo de la barra invertida | **Muere**: `/ganaderos\..\..\evil.com` |
| H2 | Sin comprobación de origen | Sobrevive sola (por capas, ver las combinaciones) |
| H3 | `/registro` no excluida | **Muere**: `/registro`, `/registro?x=1` |
| H4 | Sin rechazo de caracteres de control | Sobrevive sola (por capas) |
| H1+H2 | | **Muere**: `/\evil.com/ganaderos`, `/ganaderos\..\..\evil.com` |
| H4+H2 | | **Muere**: `/\t/evil.com/ganaderos`, `/\n/evil.com/ganaderos` |

Los mutantes que sobreviven solos son capas de defensa en profundidad. Cada capa muere en cuanto se quita su capa compañera.

### Comandos (desde `frontend/`)

| Comando | Resultado |
|---|---|
| `npm test` | exit 0: `Test Files 11 passed (11)`, `Tests 113 passed (113)` (78 + 35 nuevos: 28 de `destinoTrasLogin` + 5 de `sesion` + 2 de `httpClient`). Sin avisos de `act(...)` ni peticiones de MSW sin manejar |
| `npm run build` | exit 0: `tsc -b` limpio, y `vite build` ✓ con solo el aviso ya existente de chunk > 500 kB |
| `npm run lint` | exit 0: 0 errores y 3 warnings, todas preexistentes (`badge.tsx:55`, `button.tsx:58`, `AuthContext.tsx:231`; la última es `useAuth`, que solo ha cambiado de línea) |

### Añadidos para `CLAUDE.md` (Task 11)
Hay que sumar estos puntos a las notas de la sección anterior:
- `httpClient` pasa a `notifyUnauthorized` el token de la petición, y se ignora un 401 de un token que ya no es el actual.
- Un login fallido deja siempre "sin sesión".
- El aviso de "caducada" solo se pone cuando el 401 llega con una ruta protegida en pantalla.
- `destinoTrasLogin` (`features/auth/destinoTrasLogin.ts`) es el único validador de la ruta de vuelta.
- El número de tests del frontend es ahora 113.

## R1 (re-revision) - aplicado por el orquestador

- `destinoTrasLogin.ts`: tras parsear, se rechaza tambien `url.pathname.startsWith("//")` (los
  segmentos `.`/`..`/`%2e` se colapsan: `/.//evil.com` -> `//evil.com`).
- Tests nuevos en `destinoTrasLogin.test.ts`: `/.//evil.com`, `/a/..//evil.com`, `/%2e//evil.com`
  -> `/tramites`. Fallaban los 3 antes del arreglo (3 failed | 28 passed).
- Suite completa: 116/116; build y lint en verde (3 avisos preexistentes).
