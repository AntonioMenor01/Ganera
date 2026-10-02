# Prompt A2 — Frontend: Ganaderos, animales y revisión editable de trámites — Plan

**Goal:** poner la UI al día con el backend de A1 (commits `2538583`, `880d3ca`): sesión que
sobrevive a la recarga, errores siempre visibles con su `motivo`, Ganaderos, animales de una
explotación y el modal de revisión editable con `version`. **Solo frontend** — `backend/` no se toca.

**Stack:** Vite + React 19 + TypeScript + Tailwind v4 + shadcn/ui base-nova (`@base-ui/react`),
axios, react-router-dom 7. Diseño: **solo la skill Impeccable** (`craft`, `critique`, `audit`,
`polish`); nunca frontend-design. Flujo Superpowers: implementador + revisor por tarea, TDD, informes
en `.superpowers/sdd/a2-*`.

Estado de partida (2026-09-28): `main` = `origin/main` = `880d3ca`; sin cambios trackeados.
Sin trackear: informes `a1-*`, `capturaTramites.jpeg`, `logo.jpg` y los 5 ficheros nuevos de
`frontend/public/` (`favicon-64.png`, `ganera-logo.svg`, `ganera-logo-verde.svg`,
`ganera-logo-512.png` y `preview.png`, que **no se sube nunca**).

---

## 1. Contrato real de la API (leído de los controllers y DTOs, no de la documentación)

Todas las rutas exigen JWT (`Authorization: Bearer`) salvo `/auth/login`. Formato de error:
`400`/`409` → `{ "motivo": string }`; **`404` sin cuerpo**; **`403` sin cuerpo**; `401` sin cuerpo.
Paginación Spring: `?page=` (0-based), `?size=` (por defecto **20**, máximo 2000 — no hay
configuración propia), `?sort=campo,asc|desc`. Respuesta `Page`:
`{content, totalElements, totalPages, number, size, …}`. Un `sort` fuera de la lista blanca →
`400 {motivo: "Campo de ordenación no permitido."}`.

| Endpoint | Cuerpo | Respuesta 200 | Errores | Sort permitido / por defecto | `version` |
|---|---|---|---|---|---|
| `POST /auth/login` | `{email, password}` | `{token}` | `401` sin cuerpo (siempre el mismo) | — | — |
| `GET /auth/me` | — | `{id, email, nombre, gestoriaId, activo}` | `401` | — | — |
| `GET /ganaderos` | — | `Page<{id, nombre, nif, numeroExplotaciones}>` | `400` sort | `nombre`, `nif`, `id` / `nombre,id` | — |
| `GET /ganaderos/{id}` | — | `{id, nombre, nif, explotaciones:[{id, codigoRega, nombre, contactos:[{contactoId, nombre, telefono, rol: "TITULAR"\|"EMPLEADO"}]}]}` (solo contactos activos; explotaciones por `codigoRega`) | `404` sin cuerpo (otra Gestoría o inexistente) | — | — |
| `GET /explotaciones` | — | `Page<{id, codigoRega, nombre, ganaderoId, nombreGanadero}>` | `400` sort | `codigoRega`, `nombre`, `id` / `codigoRega,id` | — |
| `GET /explotaciones/{id}` | **NO EXISTE** (ver hueco H1) | | | | |
| `GET /explotaciones/{id}/animales` | — | `Page<{id, crotal, crotalUltimosDigitos}>` | `404` sin cuerpo; `400` sort | `crotal`, `id` / `crotal` | — |
| `POST /explotaciones/importar` | multipart `archivo` | `{explotaciones, animales, contactos: {filasProcesadas, creadas, actualizadas}, errores:[{hoja, fila, motivo}]}` | `400` con **texto plano** (no `{motivo}`) si falta una hoja obligatoria | — | — |
| `GET /tramites?estado=` | — | `Page<{id, explotacionId, tipoTramite, estado, motivoError, crotales:[Crotal], version}>` | `400` sort / estado inválido | `id`, `estado`, `createdAt` / `createdAt,id DESC` | — |
| `GET /tramites/{id}` | — | `{id, tipoTramite, estado, motivoError, explotacionId, explotacionCodigoRega, explotacionNombre, mensajeOriginal, crotales:[Crotal], version}` | `404` sin cuerpo | — | devuelve la actual |
| `PATCH /tramites/{id}` | `{version, explotacionId?, tipoTramite?, crotales?}` (null = no cambiar; `crotales` = lista **completa**, `[]` los quita todos) | detalle (igual que `GET /tramites/{id}`), `version+1` | `400` sin version / tipo inválido / crotal inválido (motivo del normalizador); `404` trámite o explotación ajenos; `409` no pendiente / versión desfasada / dos crotales al mismo animal / concurrencia | — | **obligatoria** |
| `POST /tramites/{id}/aprobar` | `{version}` | `TramiteResponse` (lista), `APROBADO`, `version+1` | **orden:** `403` sin cuerpo (suscripción) → `400` sin version → `404` → `409` no pendiente → `409` versión desfasada → `409` resolución de crotales cambiada (**se guarda y la versión sube**) → `409` explotación/tipo/crotales no aprobables (motivos acumulados, rollback) | — | **obligatoria** |
| `POST /tramites/{id}/rechazar` | ninguno (se ignora) | `TramiteResponse`, `RECHAZADO`, `version+1` | `404`; `409` no pendiente / concurrencia. **Sin 403**: rechazar no está limitado por la suscripción | — | **no la admite**, pero la incrementa |
| `GET /facturacion/suscripcion` | — | (sin cambios respecto a Prompt 4) | `404` si no hay Suscripción | — | — |

`Crotal` = `{crotalIndicado, crotal, animalId, enInventario, resolucion}`, donde:
- `crotalIndicado` es lo escrito, normalizado;
- `crotal` es el completo si `EN_INVENTARIO` y, en otro caso, igual a `crotalIndicado`;
- `animalId` es null salvo en `EN_INVENTARIO`;
- `resolucion` ∈ `EN_INVENTARIO | AMBIGUO | NO_ENCONTRADO | SIN_EXPLOTACION`.

Otros valores:
- `tipoTramite` ∈ `ALTA | BAJA | CENSO | MOVIMIENTO | DEMORA` (cambiará en el prompt B).
- `estado` ∈ los 7 de `EstadoTramite`.

Confirmaciones pedidas:
- **Rechazar NO exige `version`**, pero sí la incrementa.
- **409 "resolución cambiada"** de aprobar: el backend guarda la nueva resolución e incrementa la
  `version`; un segundo aprobar con la versión antigua vuelve a dar 409. Solo tras recargar el
  detalle (versión nueva) se aplican las reglas normales.
- **403 de aprobar no trae `motivo`**: es un `403` vacío (ver H2).

## 2. Estado actual del frontend

- **Estructura:** `features/{auth,tramites,explotaciones,facturacion,ganaderos(vacío)}`,
  `shared/{api,auth,layout}`, `components/ui` (alert, badge, button, card, dialog, input, label,
  select, table). Router en `router.tsx`: `/login`, `/registro` y, detrás de `RequireAuth` +
  `AppLayout`, `/tramites`, `/ganaderos` (placeholder "Ganaderos (pendiente)", **sin enlace en la
  navegación**), `/explotaciones` y `/facturacion`.
- **Cliente HTTP:** `shared/api/httpClient.ts` es axios con `baseURL`. Tiene dos interceptores:
  - el de petición añade el token;
  - el de respuesta llama a `notifyUnauthorized()` ante cualquier `401`, **incluido el del propio
    `/auth/login`**.
  
  No normaliza errores: cada pantalla interpreta `AxiosError` por su cuenta.
- **JWT:** módulo `authSession.ts` en memoria (variable de módulo) + estado de `AuthContext`.
  **Se pierde al recargar** porque nada lo persiste: al recargar, la variable y el estado de React
  vuelven a `null`, y `RequireAuth` manda a `/login`.
- **Pantallas que tocan trámites:** `TramitesPage` (tabla + filtro de estado; filas clicables con
  `onClick` en `<tr>`, sin foco de teclado) y `TramiteReviewDialog` (solo lectura). Este último:
  - llama a `aprobar`/`rechazar` sin `version`, así que hoy aprobar da **400**;
  - muestra Aprobar/Rechazar en **cualquier** estado;
  - trata como "Inténtalo de nuevo" todo lo que no sea 403;
  - el `GET` del detalle no tiene manejo de error: un fallo deja el modal en blanco sin aviso.
- **Tipos desfasados respecto a A1:** a `Tramite`/`TramiteDetalle` les faltan `crotales` y
  `version`, y a `ImportResumen` le falta `contactos`.
- **Tests:** **no hay infraestructura** (ni Vitest ni Testing Library). Solo `tsc -b` + `oxlint`.
- **`index.html`:** `lang="en"`, `<title>frontend</title>`, favicon `/favicon.svg` (el de Vite).
- **Logo:** el nav muestra un punto verde + texto "GANERA"; login y registro no tienen logo.
- **`frontend/public/`:** están `ganera-logo.svg` (`fill="currentColor"`),
  `ganera-logo-verde.svg` (`fill="#1F3D2B"` fijo), `ganera-logo-512.png` y `favicon-64.png`.
  También `preview.png` (no se sube) y los antiguos `favicon.svg`/`icons.svg` de Vite.
  `logo.jpg` sigue en la raíz, sin trackear.
- **Playwright MCP: no está configurado** en esta máquina (ni `.mcp.json` en el repo ni servidor
  `playwright` en la configuración de usuario). Ver hueco H8.

## 3. Huecos y ambigüedades (con propuesta)

- **H1. No existe `GET /explotaciones/{id}`**, ni tampoco una pantalla de detalle de explotación
  (el prompt habla de "el detalle existente"; no existe).
  - **Propuesta A (recomendada, sin backend):** los animales se muestran en un **panel desplegable
    "Ver animales"** por explotación, en dos sitios: la tabla de Explotaciones y el detalle de
    Ganadero. Los datos de cabecera (código REGA, nombre) ya vienen de la fila. Es un único
    componente `AnimalesDeExplotacion(explotacionId)` que pagina `GET /explotaciones/{id}/animales`.
    Sin ruta propia, así que no hay recarga sin cabecera.
  - **Alternativa B:** ruta `/explotaciones/:id` con cabecera propia. Necesita el endpoint
    `GET /explotaciones/{id}` en backend (pequeño, con `findByIdAndGestoriaId` y un E2E de dos
    Gestorías) → un prompt de backend aparte, antes o después de A2.
- **H2. El 403 de aprobar no trae `motivo`** (`ResponseEntity.status(FORBIDDEN).build()`).
  - **Propuesta:** el cliente HTTP devuelve el 403 como error de tipo `prohibido` sin `motivo`, y el
    modal muestra el texto fijo actual ("Tu suscripción no permite aprobar trámites ahora mismo…
    Facturación"), que es el único 403 posible hoy.
  - Anotarlo para un prompt de backend: devolver `{motivo}` también en el 403. Entonces el frontend
    mostraría el del backend si existe y, si no, el fijo.
- **H3. El selector de explotación no tiene búsqueda en la API.** `GET /explotaciones` solo pagina
  (máximo 2000 por página), no admite `?q=` y no permite limitarse a las explotaciones del Contacto
  (el detalle del trámite no incluye el Contacto).
  - **Propuesta:** combobox que carga la lista ordenada por `codigoRega`. Pide páginas de 500 hasta
    completar el total, se cachea mientras el modal está abierto, y se filtra en cliente por texto
    (código REGA, nombre o ganadero). Filtrar la lista mostrada **no** es una regla de negocio.
  - Límite anotado: para gestorías con miles de explotaciones hará falta `?q=` en backend.
- **H4. Rechazar no admite `version`.** Un rechazo hecho desde una pantalla desfasada se aplica
  igualmente si el trámite sigue en `PENDIENTE_REVISION`.
  - **Propuesta:** el frontend no envía `version` al rechazar (el backend la ignoraría).
  - Anotar para un prompt de backend: exigir `version` también en rechazar.
  - Mitigación en UI: pedir confirmación antes de rechazar (el rechazo es irreversible, porque solo
    se sale de `PENDIENTE_REVISION`).
- **H5. `PATCH` no puede vaciar explotación ni tipo** (`null` = no cambiar). Los selectores no
  ofrecen una opción "ninguno" una vez hay valor.
- **H6. Aprobar aprueba lo guardado, no lo que hay en el formulario.**
  - **Propuesta:** con cambios sin guardar, Aprobar y Rechazar se desactivan con el aviso "Guarda
    o descarta los cambios antes de aprobar o rechazar".
  - Es estado de la UI, no una regla de negocio: la regla de aprobación la sigue decidiendo el
    backend.
- **H7. El listado de trámites solo tiene `explotacionId`** (sin código REGA). Hoy la columna
  enseña el id.
  - **Propuesta:** mantenerla hasta que el backend añada `explotacionCodigoRega` al DTO del listado
    (anotado). No se cruza con `GET /explotaciones` en cliente, porque sería lógica duplicada.
  - Alternativa: quitar la columna.
- **H8. Playwright MCP no disponible.** Opciones:
  - (a) configuras el servidor Playwright MCP antes de la tarea de smoke;
  - (b) como en el Prompt 4, instalo Playwright (npm) **fuera del repo**, en el scratchpad, y lo
    manejo con un script.
  
  **Propuesta: (b)** salvo que prefieras (a).
- **H9. `ganera-logo.svg` usa `currentColor`, que no funciona dentro de un `<img>`.**
  - **Propuesta:** componente `LogoGanera` que pinta el SVG como `mask-image: url(/ganera-logo.svg)`
    sobre `bg-current`, con el color por token (`text-primary`). Así no se duplica el path y no hay
    hex en componentes.
  - `ganera-logo-verde.svg` lleva el hex fijo; se reserva para contextos sin CSS. En A2 no hay
    ninguno en la UI, así que queda disponible y se documenta.
  - `favicon-64.png` pasa a ser el favicon. Los antiguos `favicon.svg`/`icons.svg` de Vite **no se
    borran** sin tu visto bueno.
- **H10. Contactos en el importador:** el resumen del Excel no muestra la hoja Contactos, que existe
  desde A1.
  - **Propuesta:** añadir su fila al resumen (tipo + UI). Es pequeño y evita un dato silencioso.
- **H11. 401 en `/auth/login`** dispara hoy `notifyUnauthorized`. El cliente nuevo no tratará ese
  401 como "sesión caducada" (no hay sesión que cerrar ni aviso que dar); el login sigue mostrando
  su error uniforme.
- **H12. Uso desde el móvil** (PRODUCT.md: el gestor aprueba a veces desde el teléfono). No es
  alcance explícito de A2.
  - **Propuesta:** que el modal y las tablas nuevas sean usables a 375 px (scroll horizontal en
    tablas, modal a pantalla completa en móvil), verificado en el `audit`.
  - La barra superior en móvil queda fuera y anotada.

## 4. Decisiones propuestas

1. **Sesión en `sessionStorage`** (clave `ganera.token`, solo el token):
   - sobrevive a la recarga y se borra al cerrar la pestaña;
   - al arrancar, si hay token: estado "comprobando sesión" (sin parpadeo de login) → `GET /auth/me`;
   - un `200` restaura el usuario; un `401` limpia el token y manda a `/login` con el aviso "Tu
     sesión ha caducado. Vuelve a iniciar sesión.";
   - un error de red mantiene el token y muestra un error con "Reintentar" (no se desloguea por un
     corte de red);
   - justificación frente a `localStorage`: no persiste entre pestañas ni tras cerrar el navegador,
     que es lo más cercano al "solo sesión" actual. Ninguna de las dos protege frente a XSS; la
     alternativa robusta (cookie httpOnly) requiere cambios de backend.
2. **Cliente HTTP único** (`shared/api/httpClient.ts` + `shared/api/errores.ts`) que convierte todo
   fallo en `ErrorApi { tipo, status?, motivo? }`, con `tipo` ∈:
   - `no-autorizado` (401): logout + aviso, salvo en `/auth/login`;
   - `prohibido` (403);
   - `validacion` (400);
   - `no-encontrado` (404);
   - `conflicto` (409);
   - `servidor` (5xx);
   - `red` (sin respuesta);
   - `desconocido`.
   
   `motivo` se toma de `{motivo}` si existe, o del cuerpo si es texto plano (el `400` del importador
   devuelve texto, no `{motivo}`; anotado para unificarlo en backend). Un helper `mensajeDeError(error, contexto)` da el texto
   que se muestra:
   - con `motivo` → el `motivo` tal cual;
   - `prohibido` al aprobar → el texto de suscripción (H2);
   - `no-encontrado` → "No encontrado" contextual;
   - `red`/`servidor` → "No se ha podido conectar con Ganera. Inténtalo de nuevo en unos segundos." /
     "Ha fallado algo en el servidor. Inténtalo de nuevo; si se repite, avísanos.".
   
   Ninguna pantalla usa `axios.isAxiosError` directamente.
3. **Ningún error silencioso:** toda carga (listados, detalles, animales, suscripción) tiene tres
   estados visibles —cargando, error (con "Reintentar") y vacío—, además del contenido.
4. **`index.html`:** `lang="es"`, `<title>Ganera</title>`, `<link rel="icon" type="image/png"
   href="/favicon-64.png">`.
5. **Logo:** componente `LogoGanera` (H9). Se usa en la navbar (sustituye al punto verde; se
   mantiene el texto "GANERA" junto al símbolo), login y registro, siempre con color de token.
   `PRODUCT.md`: el logo oficial es `ganera-logo.svg` (y sus variantes); `logo.jpg` no es el logo y
   no se sube.
6. **Etiquetas en una sola fuente por dominio** (`features/tramites/etiquetas.ts`):
   - `TIPOS_TRAMITE` (**única constante**; cambiará en el prompt B):
     `ALTA` "Alta", `BAJA` "Baja", `CENSO` "Censo", `MOVIMIENTO` "Movimiento", `DEMORA` "Demora".
   - `ESTADOS_TRAMITE` (etiqueta + variante):
     - "Pendiente de extracción" (aviso)
     - "Pendiente de revisión" (aviso)
     - "Aprobado" (éxito)
     - "En proceso" (aviso)
     - "Ejecutado en OVZ.net" (éxito)
     - "Error en OVZ.net" (peligro)
     - "Rechazado" (peligro)
   - `RESOLUCIONES_CROTAL` (textos definitivos propuestos):
     - `EN_INVENTARIO` → **"En inventario"** (éxito)
     - `AMBIGUO` → **"Varios animales coinciden"** (aviso)
     - `NO_ENCONTRADO` → **"No está en el inventario"** (neutro `outline`: es aprobable si el crotal
       es completo, así que no se pinta como problema; lo decide el backend)
     - `SIN_EXPLOTACION` → **"Falta la explotación"** (aviso)
   - `ROLES_CONTACTO`: `TITULAR` "Titular", `EMPLEADO` "Empleado".
   
   Los badges muestran siempre el texto (nunca solo color). Pares de color: los tres de DESIGN.md,
   que cumplen AA con 12 px/500 (se verifica en el `audit`).
7. **Modal de revisión editable solo en `PENDIENTE_REVISION`**; en cualquier otro estado, solo
   lectura y sin botones de acción.
   - Formulario: explotación (combobox H3), tipo (select con `TIPOS_TRAMITE`) y crotales (lista:
     añadir, quitar y corregir cada uno).
   - Cada crotal muestra lo indicado, el crotal completo resuelto si difiere y el badge de
     resolución. Los badges reflejan **lo guardado** (respuesta de la API), no una predicción: tras
     editar, se ven al guardar.
8. **Guardar** = `PATCH` con `version` + solo los campos que cambiaron (`crotales` completo si la
   lista cambió). Sin cambios, "Guardar" desactivado. El `200` reemplaza el detalle y la versión con
   la respuesta.
9. **Aprobar** = `POST …/aprobar {version}`. **Rechazar** = `POST …/rechazar` sin cuerpo (H4), con
   confirmación. Con cambios sin guardar, ambos se desactivan (H6).
10. **Cualquier `409`** (PATCH, aprobar, rechazar):
    - se recarga el detalle (`GET /tramites/{id}`);
    - se descarta el formulario y se vuelve a los datos frescos;
    - se muestra el `motivo` tal cual en un aviso persistente dentro del modal;
    - nunca se reintenta automáticamente.
    
    Si la recarga falla, se muestran los dos errores.
11. **`400`** (crotal inválido, tipo inválido, falta versión): se muestra el `motivo` y se
    **conservan** las ediciones para que el usuario las corrija. **`404`** del trámite → "Este
    trámite ya no existe o no es de tu gestoría" y la lista se refresca al cerrar. **`403`** → texto
    de suscripción (H2), sin cerrar sesión.
12. **Sin doble envío:** mientras hay una petición en curso, Guardar/Aprobar/Rechazar se desactivan
    y el botón activo indica progreso (texto "Guardando…/Aprobando…"). El texto nunca sugiere nada
    en OVZ.net.
13. **Tras aprobar o rechazar con éxito:** el modal muestra el nuevo estado en solo lectura (los
    botones desaparecen) y la cola se refresca. El modal no se cierra solo, así el usuario ve el
    resultado.
14. **Ganaderos:**
    - `/ganaderos`: tabla paginada (nombre, NIF, nº de explotaciones), ordenable **solo por nombre y
      NIF** con las cabeceras (`sort=nombre|nif,asc|desc`), 20 por página.
    - `/ganaderos/:id`: cabecera (nombre, NIF) y una tarjeta por explotación (código REGA, nombre,
      contactos con teléfono y badge de rol, y el panel "Ver animales").
    - `404` → estado "Ganadero no encontrado" con enlace a la lista, nunca una pantalla rota.
    - Enlace "Ganaderos" en la navegación. Cada Ganadero enlaza con su detalle, y el nombre del
      ganadero en Explotaciones enlaza también.
15. **Animales:** componente `AnimalesDeExplotacion` (H1-A), paginado (20), ordenado por `crotal`,
    con estados de carga, error y vacío.
16. **Cola de trámites:** primero `/impeccable critique`; dentro de A2 se aplica lo que encaje con
    este alcance (badges con etiqueta, filas accesibles por teclado, errores visibles, crotales
    visibles en la fila), y lo demás se anota.
17. **Tests (necesita tu aprobación antes de instalar):** `vitest`, `jsdom`,
    `@testing-library/react`, `@testing-library/user-event`, `@testing-library/jest-dom` y `msw`
    (simula la API a nivel HTTP; se prueba el cliente axios real), todas en `devDependencies`, con
    script `npm test` (`vitest run`). TDD para:
    - el cliente HTTP (401/403/404/409/400/red/5xx; 401 en login sin logout);
    - la persistencia y restauración de sesión;
    - el 409 con recarga y `motivo`;
    - el envío de `version`;
    - la visibilidad de botones por estado;
    - la desactivación durante el envío y con cambios sin guardar;
    - las etiquetas.
18. **Verificación final:**
    - `npm test`, `npm run build` y `npm run lint` en verde;
    - `./mvnw clean test` 415/415 (sin cambios en backend);
    - smoke en navegador real (H8) con dos gestorías, H2 y Excel con hoja Contactos: los 9 pasos del
      prompt, más "B abre por URL un ganadero de A → no encontrado";
    - al terminar se matan los procesos y se borran la H2 y los temporales.
19. **Git:** sin commits sin tu aprobación; stage ruta a ruta. El commit incluye los 4 ficheros del
    logo (`ganera-logo.svg`, `ganera-logo-verde.svg`, `ganera-logo-512.png`, `favicon-64.png`);
    **nunca** `preview.png` ni `logo.jpg` (ni `capturaTramites.jpeg` ni los informes `a1-*`/`a2-*`).

### Ajustes de Antonio al aprobar el plan (2026-09-28)

20. **H3 — carga completa de verdad.** El loader de explotaciones recorre **todas** las páginas de
    `GET /explotaciones` hasta `totalPages`. Si alguna página falla, o el número de elementos
    recibidos no cuadra con `totalElements`, es un **error visible** (con "Reintentar"): nunca una
    lista parcial. Con test (varias páginas; fallo en la página N → error y ninguna lista).
21. **Decisión 6, `NO_ENCONTRADO`:** gris neutro cuando el crotal es completo; ámbar con "No está en
    el inventario · incompleto" cuando lo indicado estaba incompleto. **PENDIENTE:** el backend
    nunca devuelve `crotal` vacío (para `NO_ENCONTRADO`, `crotal = crotalIndicado`), así que la
    respuesta no dice si es incompleto. **Decidido por Antonio: opción (a)** — en A2 todo
    `NO_ENCONTRADO` va en gris neutro; el mini-prompt de backend añade `completo` a
    `TramiteCrotalResponse` y entonces se pinta el ámbar.
26. **Assets de `public/`:** todo lo que hay en `frontend/public/` acaba en `dist/`. Por eso
    `preview.png` se movió a la raíz del repo, junto a `logo.jpg` (sigue sin subirse nunca), y se
    borraron `favicon.svg` e `icons.svg` de la plantilla de Vite, que ya no se usaban.
22. **H7 — código REGA en la cola.** La cola traduce `explotacionId` → código REGA con la lista
    completa de explotaciones (el mismo loader de la decisión 20, compartido con el combobox). Si
    esa carga falla, la columna muestra un error visible, no ids.
23. **Texto plano en errores.** El cliente HTTP toma como `motivo` el cuerpo de texto cuando la
    respuesta de error no es JSON (el `400` del importador). Nunca falla ni deja el mensaje vacío.
    Con test.
24. **H6 — aviso exacto:** con cambios sin guardar, Aprobar y Rechazar desactivados y el aviso
    "Guarda antes de aprobar".
25. **Huecos de backend** → un **mini-prompt de backend tras A2** (ver "Fuera de alcance").

Aprobado tal cual: H1 panel desplegable, H2 texto fijo, H4 confirmación al rechazar, H8 Playwright
por npm fuera del repo, H9 máscara CSS y dependencias de test (decisión 17).

### Decisiones de Antonio tras la Task 6 (2026-09-29)

27. **Impeccable desde la sesión principal.** Las pasadas de la skill (`craft` en las Tasks 7 y 9,
    `audit` + `polish` en la Task 10) las ejecuta el orquestador en la conversación principal, no
    un subagente, para que la skill funcione completa (sus subagentes independientes y el
    detector). El implementador de cada tarea trabaja a partir de ese resultado.
28. **La cola se abre filtrada por "Pendiente de revisión".** "Todos" sigue disponible en el
    filtro. Con test. Los tests que asumían "Todos" al arrancar se adaptan, no se eliminan.
30. **Detalle de Ganadero (respuestas de Antonio en el craft de la Task 7):**
    - las explotaciones van en secciones apiladas, sin tarjetas anidadas;
    - con más de 3 explotaciones, arriba hay un índice compacto de enlaces "código REGA · nombre"
      a cada sección;
    - los animales están plegados por defecto;
    - los teléfonos son enlaces `tel:`.
    
    El contrato de dirección está en `.impeccable/surfaces/frontend-src-features-ganaderos.md`.
31. **Modal de revisión (respuestas de Antonio en el craft de la Task 9b, 2026-09-30):**
    - **Diseño:** modal ancho de dos columnas, con el mensaje de WhatsApp a la izquierda y los
      datos editables a la derecha. En móvil ocupa la pantalla completa y las columnas se apilan.
    - **Crotales:** una fila por crotal, con su campo, el crotal resuelto, el badge y un botón para
      quitarlo, más "Añadir crotal". Una fila editada o nueva muestra "Sin guardar" en vez del
      badge antiguo.
    - **Rechazar:** se confirma en línea en la barra de acciones.
    - **Contrato:** `.impeccable/surfaces/features-tramites-tramitereviewdialog-tsx-930a9a22.md`.
29. **Avance de `motivoError` en las filas `ERROR_OVZ`: diferido** hasta la integración con OVZ.net
    (Prompt 3c). Hoy ningún trámite llega a `ERROR_OVZ`.

### Pendientes acumulados para la Task 10 (audit + polish)

- **Tests** (revisiones de las Tasks 6 y 7):
  - ningún test comprueba que el `AbortSignal` llega a axios (`todasLasExplotaciones.ts`,
    `explotaciones/api.ts`);
  - falta el caso de la tecla Alt en los tests de los enlaces del índice;
  - falta un clic real en el enlace del estado vacío del detalle de Ganadero.
- **Móvil:** en la cola, el nombre de la explotación, "Indicado" y los crotales de "+N más" solo se
  ven abriendo el trámite. Falta un tooltip que reciba foco (revisión de la Task 6, M3).
- **Badge de Facturación:** `FacturacionPage.tsx:80` usa `secondary`/`destructive`, que no son un
  par exacto de DESIGN.md.
- **Enlaces sin la receta común:** Login y Registro, más uno en `TramitesPage.tsx:224`, no usan la
  receta común de enlace (`CLASE_ENLACE`), y los de Login y Registro no tienen anillo de foco.
  `CLASE_ENLACE` vive en `features/ganaderos` y habría que moverlo a `shared` (documentador de la
  Task 7).
- **Esqueleto de carga:** decidir si la cola de trámites pasa de la fila "Cargando…" al esqueleto
  de carga, como Ganaderos.
- **Anuncio de carga (revisión de la Task 8, m3):** el `role="status"` "Cargando…" se inserta ya
  con texto y dentro de un contenedor `aria-busy`, así que puede no anunciarse. Afecta a
  `AnimalesDeExplotacion.tsx` y a `GanaderosPage.tsx:144`.
- **Detalle menor (revisión de la Task 8):** en la tabla de Explotaciones, la celda expandida se
  anuncia bajo la cabecera "Código REGA".
- **Palabra larga en la tabla de Explotaciones (re-revisión de la Task 8, n1):** un nombre con una
  sola palabra de unos 27 caracteres, sin espacios ("Agroganaderaextremeñadelsur"), vuelve a
  provocar scroll horizontal (436 px en un contenedor de 325 a 375 px; 674 en 590 a 640 px).
  `break-words` no deja encoger la celda por debajo de su palabra más larga. Arreglo: cambiar
  `break-words` por `wrap-anywhere` en los tres elementos con nombre (la línea bajo el código REGA
  y las celdas Nombre y Ganadero) y volver a medir a 375 y 640 px.
- **Modal de revisión (Task 9b, cerrada el 2026-10-02 con las dos re-revisiones aprobadas):**
  - comprobar en un navegador real los 375 px, la columna del mensaje fija, el margen de zona
    segura del pie y el popup del combobox por encima del modal;
  - mover `CLASE_ENLACE` a `shared` (ver arriba);
  - las clases de foco del combobox están copiadas a mano de `Input`;
  - no hay un `Combobox` compartido en `components/ui/` (extraerlo solo si aparece otro);
  - re-revisión de código, n1: ningún test fija que el aviso `estado-cambiado` es neutro y no
    destructivo;
  - re-revisión de código, n2: el aviso de "más de 100 coincidencias" vive en `Combobox.Status`,
    que se monta al abrir la lista; comprobar si un lector de pantalla lo lee al abrir;
  - re-revisión visual (opcional): la columna de badges cambia de ancho (14 px) cuando cambia el
    badge más ancho; arreglo propuesto en `ListaCrotales.tsx:116`:
    `sm:grid-cols-[minmax(0,1fr)_minmax(11rem,auto)_auto]`.
- **Hash en la URL:** abrir el detalle con `#explotacion-N` ya está resuelto en la Task 7 (m3).
- **Textos para DESIGN.md (Task 11):**
  - `.impeccable/review/ganaderos/documenter.md` (17 patrones);
  - las notas de los informes `a2-task4` a `a2-task7`.
- **Fuera de A2:** la barra de navegación en móvil.
- **Task 10 cerrada (2026-10-02):** audit en `.superpowers/sdd/a2-task10-audit.md` (14/20) y
  polish con informe y revisión aprobada (`a2-task10-report.md`, `a2-task10-review.md`). Resuelto
  todo lo de esta lista salvo lo siguiente, que queda para después:
  - la barra de navegación en móvil (fuera de A2), que hace la página de 837 px a 375 y a 640;
  - n2 de la 9b (aviso de ">100" en `Combobox.Status` al abrir): sin lector de pantalla real no
    se ha podido comprobar;
  - minors de la revisión de la Task 10 (m1–m4), sin bloqueo; ver la revisión.
  Decisiones tomadas en la Task 10: la cola usa esqueleto de carga como Ganaderos; "+N más" es un
  desplegable dentro de la fila (no un tooltip, que no se abre en táctil); el nombre de la
  explotación sigue en `title` + texto sr-only (en táctil se ve abriendo el trámite).
- **Tamaño del paquete (anotado 2026-10-02, no se toca en A2):** el JS principal ha crecido a
  621 kB (200 kB gzip) y `npm run build` avisa de que pasa de 500 kB. Más adelante, valorar
  code-splitting (por ejemplo, `import()` dinámico por ruta).

## 5. Tareas

Cada tarea: implementador (TDD donde haya lógica) → revisor independiente → correcciones →
siguiente. Informes en `.superpowers/sdd/a2-taskN-report.md` / `-review.md`.

- **Task 1 — Infraestructura de tests** (tras aprobar la decisión 17): dependencias,
  `vitest.config`/setup, MSW, un test de humo. Build y lint en verde.
- **Task 2 — Cliente HTTP y errores** (decisión 2, H11): `ErrorApi`, interceptores y
  `mensajeDeError`, con tests primero. Se migran los usos existentes (`facturacion/api.ts` 404/503,
  login, registro, import) sin cambiar su comportamiento visible salvo para mostrar errores.
- **Task 3 — Sesión persistente** (decisión 1): `sessionStorage`, arranque con `/auth/me`, aviso de
  sesión caducada en login. Con tests.
- **Task 4 — Marca básica** (decisiones 4, 5): `index.html`, `LogoGanera`, navbar/login/registro y
  `PRODUCT.md`.
- **Task 5 — Etiquetas y badges** (decisión 6): `etiquetas.ts`, badges de estado con texto legible
  en cola y modal, y tipos de API al día (`crotales`, `version`, `contactos` del import — H10). Con
  tests de etiquetas.
- **Task 6 — Critique de la cola** (`/impeccable critique`, decisión 16): informe; aplicar lo que
  entra en alcance.
- **Task 7 — Ganaderos** (`/impeccable craft`, decisiones 14, 3): listado + detalle + enlace de nav.
- **Task 8 — Animales** (decisión 15, H1-A): `AnimalesDeExplotacion` en Explotaciones y en el
  detalle de Ganadero.
- **Task 9a — Lógica del modal de revisión** (TDD, sin diseño): hook/estado del trámite (carga,
  formulario, diff para PATCH, `version`, 409 → recarga + motivo, 400 conserva ediciones, envío
  único, visibilidad por estado, bloqueo con cambios sin guardar).
- **Task 9b — UI del modal** (`/impeccable craft`, decisiones 7–13): combobox de explotación (H3),
  selector de tipo, lista de crotales con badges, avisos y acciones.
- **Task 10 — `/impeccable audit` + `/impeccable polish`** de todo lo tocado en A2 (incluye AA y
  375 px, H12).
- **Task 11 — Cierre:**
  - `npm test`/build/lint y `./mvnw clean test`;
  - smoke en navegador real (decisión 18);
  - `CLAUDE.md`, `ganera-prompts.md` y `progress.md`;
  - lista exacta de rutas + propuesta de commit. **Sin commit.**

## Fuera de alcance

Cualquier cambio en `backend/`. **Mini-prompt de backend tras A2** (se anota en
`ganera-prompts.md`), todos pequeños:
- `GET /explotaciones/{id}` (H1-B);
- `motivo` en el 403 de aprobar (H2);
- `?q=` en `GET /explotaciones` (H3);
- `version` en rechazar (H4);
- `explotacionCodigoRega` en el listado de trámites (H7);
- `400` del importador como `{motivo}` en vez de texto plano; y un `.xls` (o cualquier fichero no
  `.xlsx`) no debe devolver el mensaje técnico en inglés de POI (`UnsupportedFileFormatException`),
  sino un motivo en español (revisión A2 Task 2, M4);
- unificar el `400` del registro (`{mensaje}`) a `{motivo}`, manteniendo el texto uniforme;
- un campo `completo` (boolean) en `TramiteCrotalResponse` que diga si el crotal indicado es
  completo o incompleto, para pintar `NO_ENCONTRADO` incompleto en ámbar ("No está en el
  inventario · incompleto") sin clasificar en el frontend (decisión 21).

También fuera de alcance: la navbar en móvil, la UI de alta/edición de Contactos, la pantalla de
credenciales OVZ, y cualquier cambio de paleta o tipografía.
