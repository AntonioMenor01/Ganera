# A2 — Task 1: Infraestructura de tests — Informe del implementador

## Dependencias (devDependencies, `npm install -D`, sin `--force` ni `--legacy-peer-deps`)

| Paquete | Rango en package.json | Instalada |
|---|---|---|
| vitest | ^5.0.2 | 5.0.2 |
| jsdom | ^29.1.1 | 29.1.1 |
| @testing-library/react | ^16.3.3 | 16.3.3 |
| @testing-library/user-event | ^14.6.7 | 14.6.7 |
| @testing-library/jest-dom | ^7.0.1 | 7.0.1 |
| msw | ^2.15.0 | 2.15.0 |

- `@testing-library/dom@10.4.2` entra como peer obligatorio de RTL/user-event/jest-dom (npm lo
  instala solo en el lockfile; no se añadió a `package.json` porque no está en la lista aprobada).
- La instalación terminó **sin ningún aviso de peer dependencies** ni de `EBADENGINE`.
- Peers comprobados: vitest 5 (`vite ^6||^7||^8`), RTL 16.3 (`react/react-dom ^18||^19`),
  msw (`typescript >=5.9`, opcional) → compatibles con React 19.2, Vite 8.1 y TypeScript 6.0.
- `msw`: npm eligió 2.15.0 (no la 3.0.0 recién publicada), por el rango que ya usa internamente
  `@vitest/mocker@5.0.2`. Una sola copia, deduplicada.
- Efectos secundarios en el lockfile: dos subidas transitivas menores
  (`@jridgewell/sourcemap-codec` 1.5.5→1.6.0, `picomatch` 4.0.5→4.0.7), para satisfacer las nuevas
  dependencias; 122 entradas nuevas.
- `npm audit`: 14 vulnerabilidades (4 moderadas y 10 altas), **las mismas 14 que ya había en `HEAD`**. Lo
  comprobé ejecutando `npm audit --package-lock-only` sobre el lockfile de `HEAD` en el scratchpad.
  Esta tarea no introduce ninguna. No ejecuté `npm audit fix` porque queda fuera de alcance.

## Ficheros creados o modificados

**Creados:**
- `frontend/vitest.config.ts`: un fichero separado, con `mergeConfig(viteConfig, …)`. Hereda de
  `vite.config.ts` los plugins (react y tailwind) y el alias `@`, así que `vite.config.ts` queda
  intacto y el alias funciona en tests sin duplicarlo. Configura `environment: "jsdom"`,
  `setupFiles`, `include: src/**/*.test.{ts,tsx}` y `restoreMocks`. También fija
  `env.VITE_API_BASE_URL` a `http://localhost:8080`, para que la baseURL de `httpClient` sea
  determinista aunque alguien tenga un `.env.local` con otro valor.
- `frontend/tsconfig.test.json`: extiende `tsconfig.app.json` (mismas reglas estrictas y `paths`),
  con `types: ["vite/client", "node"]` (msw/node), e incluye `src/**/*.test.ts(x)` y `src/test`.
  Así `tsc -b` también comprueba los tipos de los tests, y los tipos de los matchers de jest-dom
  llegan por el import de `setup.ts`.
- `frontend/src/test/setup.ts`: importa `@testing-library/jest-dom/vitest` y arranca MSW
  (`server.listen({ onUnhandledRequest: "error" })`). En cada `afterEach` ejecuta `resetHandlers()`
  y también `cleanup()`. Sin `globals: true`, Testing Library no desmonta nada entre tests por su
  cuenta. `afterAll` ejecuta `close()`.
- `frontend/src/test/server.ts`: el `setupServer(...handlers)` compartido.
- `frontend/src/test/handlers.ts`: los handlers por defecto. Está vacío a propósito: cada test
  declara sus handlers con `server.use`, y cualquier otra petición falla.
- `frontend/src/test/apiBaseUrl.ts`: define `API_BASE_URL` y `apiUrl(path)` para los handlers. Es
  la única fuente del valor, y la importan tanto `vitest.config.ts` como los tests.
- `frontend/src/test/smoke.test.tsx`: el test de humo, con dos casos.
  - Renderiza un componente trivial y comprueba `toBeInTheDocument()`.
  - Llama a `httpClient.get("/ping")` (el cliente real, sin cambios) contra un handler de MSW en
    `http://localhost:8080/ping`, y comprueba el `status` 200 y el cuerpo.

**Modificados:**
- `frontend/package.json`: las devDependencies y los scripts `"test": "vitest run"` y
  `"test:watch": "vitest"`.
- `frontend/package-lock.json`: lo regeneró npm.
- `frontend/tsconfig.json`: añade la referencia a `./tsconfig.test.json`.
- `frontend/tsconfig.app.json`: añade
  `"exclude": ["src/**/*.test.ts", "src/**/*.test.tsx", "src/test"]`. Los tipos de test y de Node
  no entran en el proyecto de la app.
- `frontend/tsconfig.node.json`: incluye `vitest.config.ts` y `src/test/apiBaseUrl.ts`, que importa
  la configuración. Con `module: nodenext`, los imports relativos de `vitest.config.ts` llevan la
  extensión `.ts` explícita, algo que ya permite `allowImportingTsExtensions`.

No he tocado el código de aplicación existente en `src/`, ni `vite.config.ts`, ni `httpClient.ts`.

## Estructura: dónde van los tests

- **Los tests de verdad van junto al código** (`foo.ts` → `foo.test.ts`), empezando por
  `src/shared/api/httpClient.test.ts` en la Task 2. Así se ve de un vistazo qué módulos tienen
  tests, y los imports quedan cortos.
- **`src/test/` guarda solo la infraestructura compartida:** setup, servidor y handlers de MSW,
  helpers de URL y el test de humo. El test de humo va aquí porque prueba la infraestructura, no un
  módulo concreto, y así no ocupa el nombre `httpClient.test.ts` que usará la Task 2.

## Verificación (desde `frontend/`)

- **`npm test`**: sale con código 0.
  ```
  RUN  v5.0.2
  Test Files  1 passed (1)
       Tests  2 passed (2)
  ```
- **`npm run build`**: sale con código 0.
  - `tsc -b` compila tres proyectos: `tsconfig.app.json`, `tsconfig.node.json` y
    `tsconfig.test.json`, todos sin errores.
  - `vite build` termina con `✓ built`.
  - Aparece el aviso "chunk > 500 kB" (`index-*.js` de 527 kB). Ya salía antes: el bundle de la
    app no ha cambiado.
- **`npm run lint`**: sale con código 0 y da exactamente 3 avisos, los 3 `only-export-components`
  que ya existían: `badge.tsx:55`, `button.tsx:58` y `AuthContext.tsx:65`. Ningún error.
- **Prueba en negativo (manual, ya borrada):** escribí un test temporal que llamaba a
  `httpClient.get("/sin-handler")` sin handler. Falló con
  `[MSW] Error: intercepted a request without a matching request handler`, así que
  `onUnhandledRequest: "error"` funciona de verdad. Después borré el fichero.

## Desviaciones y riesgos

- **jsdom está fijado a `^29.1.1`, no a la última versión (30.1.1).** jsdom 30 exige Node
  `^22.22.2 || ^24.15 || >=26`, y esta máquina tiene **Node 22.17.1**. La 29.1.1 declara
  `^22.13.0`, que sí se cumple. Cuando Node se actualice, se puede subir a jsdom 30.
- `msw` quedó en la 2.x (2.15.0), por la resolución de npm explicada arriba. El API
  (`http`, `HttpResponse`, `setupServer`) es el mismo que usará la Task 2.
- En jsdom, axios usa el adaptador XHR, y MSW lo intercepta con su interceptor de
  `XMLHttpRequest`. Por eso jsdom no aplica CORS a estas peticiones simuladas. Es lo esperado: los
  problemas de CORS reales solo aparecen en un navegador de verdad (ver CLAUDE.md).
- Arrancar el entorno jsdom tarda unos 20 s de los 33 s totales que dura esta ejecución en Windows.
  Es aceptable ahora; conviene vigilarlo si la suite crece.
- No he hecho ninguna operación git de escritura. Solo usé `git diff` y `git show`, que son de
  lectura, para comparar con `HEAD`.
