# Mini-prompt de backend tras A2 — plan

Fecha: 2026-10-02. Base: `main` = `ae90295` (A2 cerrado). **Solo `backend/`**: el frontend no se toca
en esta sesión, ni para consumir los cambios. Suite de partida: 415 tests (`./mvnw clean test`).

Flujo: Superpowers (un implementador + un revisor independiente por tarea), TDD, E2E de dos
Gestorías (`@SpringBootTest` + `TestRestTemplate`) en cada endpoint nuevo o cambiado con lookup o
filtrado por Gestoría. Cierre: `./mvnw clean test` en verde + smoke con `curl` contra dos Gestorías
(H2 en fichero). Sin commits sin aprobación de Antonio; stage ruta a ruta.

## Compatibilidad con el frontend actual (no se toca)

| Punto | ¿Rompe el frontend de A2? |
|---|---|
| 1 `GET /explotaciones/{id}` | No (endpoint nuevo, nadie lo llama). |
| 2 `{motivo}` en el 403 de aprobar | No: el frontend usa su texto fijo para `prohibido`. |
| 3 `?q=` | No (parámetro opcional). |
| 4 `version` en rechazar | **Sí si es obligatoria**: `rechazarTramite` envía `POST` sin cuerpo → `400` siempre. Ver D4.1. |
| 5 `explotacionCodigoRega` en el listado | No (campo añadido). |
| 6 importador `{motivo}` | No: `extraerMotivo` ya lee `{motivo}` igual que el texto plano. |
| 7 registro `{motivo}` | No: el contexto "registro" enseña su propio texto uniforme e ignora el cuerpo. |
| 8 `completo` | No (campo añadido). |

## Decisiones abiertas por punto (con recomendación)

### 1. `GET /explotaciones/{id}` (H1-B)
- **D1.1 DTO.** Recomiendo **reutilizar `ExplotacionResponse`** (`id, codigoRega, nombre, ganaderoId,
  nombreGanadero`). Alternativa: un `ExplotacionDetalleResponse` con nº de animales y contactos — hoy
  no hay pantalla que lo pida (YAGNI).
- Fijo: `findByIdAndGestoriaId`; `404` sin cuerpo si es ajena o no existe; id no numérico → `400`
  de Boot. E2E de dos Gestorías.

### 2. `{motivo}` en el `403` de aprobar (H2)
- **D2.1 Texto.** Recomiendo **un único texto** igual al que ya muestra el frontend: "Tu suscripción
  no permite aprobar trámites ahora mismo (trial expirado o suspendida). Actualiza tu suscripción en
  Facturación." Alternativa: un texto por estado (`TRIAL_EXPIRADO_SIN_PAGO`, `SUSPENDIDA`, sin
  `Suscripcion`), que obliga a exponer el estado desde `SuscripcionService`; el banner ya lo dice.
- Fijo: el `403` sigue siendo lo primero (antes del `400` sin versión y del `404`), así que el
  motivo no revela nada del trámite.

### 3. `?q=` en `GET /explotaciones` (H3)
- **D3.1 Campos.** Recomiendo **`codigoRega` y `nombre` de la Explotación**. Opcional: también el
  nombre del Ganadero (join extra; el combobox del modal busca por REGA/nombre).
- **D3.2 Coincidencia.** Recomiendo **"contiene", sin distinguir mayúsculas** (`lower(...) like
  lower(:patron)`), escapando `%`, `_` y `\` del texto del usuario. Sin quitar acentos: `unaccent`
  no existe en H2 y en Postgres necesita una extensión (se anota como pendiente si molesta).
  Alternativa: "empieza por" (más barato con índice, peor para nombres).
- **D3.3 Vacío/espacios.** `q` ausente, vacío o solo espacios = sin filtro (listado de hoy). `q` se
  recorta. Longitud máxima 100 → `400 {motivo}` si se pasa (evita patrones absurdos).
- **D3.4 Índices.** Recomiendo **ninguno ahora**: un "contiene" no usa B-tree; con miles de filas
  por Gestoría y `gestoria_id` indexado el coste es asumible. `pg_trgm` queda anotado para cuando
  haga falta.
- Fijo: `gestoriaId` del JWT como parámetro real; paginación y lista blanca de `sort` se mantienen.
  E2E de dos Gestorías (un `q` que coincide con una explotación de la otra Gestoría no la devuelve,
  ni cuenta en `totalElements`).

### 4. `version` en rechazar (H4)
- **D4.1 Obligatoria ya o transitoria.** El frontend actual rechaza **sin cuerpo**.
  - **(a) Obligatoria ya** (lo que dice `ganera-prompts.md`): Rechazar en la UI da `400` hasta que el
    frontend envíe `{version}` (cambio de una línea en `rechazarTramite`). Mismo patrón que A1 dejó
    con aprobar hasta A2. Se anota como pendiente bloqueante antes de cualquier demo.
  - **(b) Validada si viene, opcional si no**: nada se rompe hoy; pasa a obligatoria cuando el
    frontend la envíe (un cambio de backend más, pequeño).
  - **Recomiendo (a)**: es la seguridad que se buscaba con H4, y (b) obliga a volver a tocar el
    backend; pero tú decides si `main` puede quedarse con Rechazar roto en la UI.
- Fijo (si a): cuerpo `@RequestBody(required = false) {version}`; orden `400` sin versión (antes de
  buscar, igual para propio/ajeno/inexistente) → `404` → `409` no pendiente → `409` versión
  desfasada → `200`, `version + 1`. Mismo `MOTIVO_FALTA_VERSION`. E2E de dos Gestorías.

### 5. `explotacionCodigoRega` en el listado de trámites (H7)
- **D5.1 Campos.** Recomiendo añadir **`explotacionCodigoRega` y `explotacionNombre`** (mismos
  nombres que `TramiteDetalleResponse`; el nombre sale gratis en el mismo join). Alternativa: solo
  el REGA.
- **D5.2 Sin N+1.** `Tramite.explotacion` es `LAZY`: se carga con `@EntityGraph(attributePaths =
  "explotacion")` en `findByGestoriaId`/`findByGestoriaIdAndEstado` (left join, una consulta por
  página). Test que cuenta sentencias con `SqlCapturadoInspector` (ya existe).
- Efecto colateral: `TramiteResponse` también es la respuesta de aprobar/rechazar, que llevarán los
  dos campos nuevos (sin coste: la explotación ya está cargada).

### 6. Importador: `400` como `{motivo}` + ficheros no `.xlsx`
- **D6.1 Detección.** Recomiendo **por contenido** (capturar las excepciones de POI al abrir el
  libro), no por extensión ni `Content-Type` (el navegador manda lo que quiere).
- **D6.2 Textos.**
  - `.xls` (formato OLE2, `OLE2NotOfficeXmlFileException`): "El fichero es un Excel antiguo (.xls).
    Ábrelo en Excel y guárdalo como .xlsx antes de importarlo."
  - Cualquier otro no `.xlsx` o corrupto (`NotOfficeXmlFileException`, `UnsupportedFileFormatException`,
    ZIP roto, fichero vacío): "El fichero no es un Excel .xlsx válido."
  - Falta una hoja: se mantiene "Falta la hoja 'X' en el Excel", ahora en `{motivo}`.
- **D6.3 Alcance de la captura.** Solo los fallos al **abrir** el libro pasan a `400`; un error
  inesperado durante el procesado sigue siendo `500` (no se disfraza un bug de "fichero inválido").
- Fijo: el mensaje técnico de POI nunca llega al cliente. Tests con un `.xls` real (generado con
  `HSSFWorkbook`), un `.txt`, un fichero vacío y un `.xlsx` sin hoja.

### 7. Registro: `{mensaje}` → `{motivo}`
- **D7.1** Usar `MotivoErrorResponse` y **borrar `RegistroErrorResponse`**. Mismo texto uniforme
  para toda causa (sin oráculo). El `503` (Stripe sin configurar) sigue sin cuerpo.

### 8. `completo` en `TramiteCrotalResponse`
- **D8.1 Origen.** Recomiendo **calcularlo al serializar** con `CrotalNormalizador.normalizar(
  crotalIndicado).tipo() == COMPLETO` (el `crotal_indicado` ya está normalizado; sin migración).
  Alternativa: columna nueva en `tramite_crotal` (`V18`) — duplica algo derivable.
- **D8.2 Semántica.** `completo` describe **lo escrito** (`crotalIndicado`), no el crotal resuelto:
  un `1234` resuelto `EN_INVENTARIO` es `completo: false`. Es lo que necesita la decisión 21 de A2
  (ámbar para `NO_ENCONTRADO` incompleto).
- **D8.3 Robustez.** Si un `crotal_indicado` antiguo no pasara la normalización (no debería), no se
  lanza: `completo: false` y se registra un aviso, para no tumbar el listado.

## Tareas

1. **T1** — puntos 1 y 3 (`ExplotacionController`/`ExplotacionRepository`): detalle + `?q=`, con E2E
   de dos Gestorías.
2. **T2** — puntos 2 y 4 (`TramiteController`/`TramiteRevisionService`): `403 {motivo}` + `version`
   en rechazar según D4.1, con E2E de dos Gestorías para rechazar.
3. **T3** — puntos 5 y 8 (`TramiteResponse`, `TramiteRepository`, `TramiteCrotalResponse`): REGA/nombre
   en el listado sin N+1 + `completo`.
4. **T4** — puntos 6 y 7 (`ExplotacionImportController`/`Service`, `RegistroGestoriaController`).
5. **T5** — cierre: greps de `findById(`, `./mvnw clean test`, smoke `curl` con dos Gestorías,
   `CLAUDE.md` + `ganera-prompts.md` + `progress.md` al día, lista de rutas para el stage.

Cada tarea: implementador con TDD (test rojo primero) → revisor independiente → arreglos → siguiente.
Informes en `.superpowers/sdd/mp-*` (no se suben).

## Smoke `curl` de cierre (dos Gestorías, H2 en fichero)

Onboarding A y B → login → importar Excel en A y en B (B con un REGA parecido) → `GET
/explotaciones/{id de A}` como A `200`, como B `404` → `?q=` como A no ve las de B → `.xls` y `.txt`
→ `400 {motivo}` en español → trámite sembrado por SQL en A → listado con `explotacionCodigoRega` y
`completo` → rechazar sin versión `400`, versión vieja `409`, buena `200` (y como B `404`) → B en
`SUSPENDIDA` aprueba → `403 {motivo}` → registro con email duplicado → `400 {motivo}` uniforme.

## Decisiones de Antonio (2026-10-02) — CERRADAS

- **1, 2, 5, 6, 7, 8:** como la recomendación de este plan (D1.1, D2.1, D5.1–D5.2, D6.1–D6.3, D7.1,
  D8.1–D8.3).
- **8 (añadido):** documentar en el Javadoc de `TramiteCrotalResponse` **y** en `CLAUDE.md` que
  `completo` describe **lo escrito** (`crotalIndicado`), no lo resuelto.
- **3:** `?q=` busca en `codigoRega`, `nombre` de la Explotación **y nombre del Ganadero**.
  "Contiene", sin distinguir mayúsculas, sin quitar acentos. Anotar en `ganera-prompts.md`
  (sección "Prompt C — notas acumuladas") la búsqueda sin acentos con Postgres (`unaccent`/`pg_trgm`).
- **4:** opción **(a)**, `version` obligatoria al rechazar, **y `main` no puede quedar con Rechazar
  roto**: la T2 incluye el cambio mínimo de frontend (`features/tramites/api.ts` envía `{version}`
  al rechazar; si hace falta, el hook/modal se la pasa), con su test. **Única excepción** a "solo
  `backend/`". Al cierre, además de `./mvnw clean test`, `npm test` y `npm run build` en verde.
