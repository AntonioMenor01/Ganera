# Tarea de frontend antes del piloto — plan

Fecha: 2026-10-03. Base: `main` = `b960703` (A2 y el mini-prompt de backend cerrados). **Solo
`frontend/`**: si algún punto necesitara backend, se para y se consulta a Antonio. Suite de partida:
`npm test` 476/476, `./mvnw clean test` 472/472.

Flujo: Superpowers (un implementador + un revisor independiente por tarea), TDD (Vitest + MSW). La
navbar móvil se diseña con Impeccable desde la sesión principal y respeta `DESIGN.md`; la propuesta
de shape se enseña a Antonio **antes** del craft. Informes en `.superpowers/sdd/fp-*` (no se suben).
Sin commits sin aprobación de Antonio; stage ruta a ruta.

Cierre: `npm test`, `npm run build`, `npm run lint` en verde y `./mvnw clean test` (nada roto); smoke
en navegador real (Playwright por npm fuera del repo) con dos gestorías, a 1440, 640 y 375 px
(navbar, aprobar desde el móvil, combobox con búsqueda); `DESIGN.md` (quitar el "defecto conocido"
de la navbar), `CLAUDE.md`, `progress.md` y `ganera-prompts.md` al día.

## Decisiones cerradas

### 1. Barra de navegación en móvil
- **D1 Disposición:** por debajo de `md`, **dos filas**: arriba marca + "Salir"; abajo los 4 enlaces
  siempre visibles, en una tira con **scroll horizontal interno** (nunca de la página). Sin
  hamburguesa ni barra inferior. Desde `md`, la barra actual.
- **D1a Email:** oculto por debajo de `md`.
- **D1b Punto de corte:** `md` (768 px) — a 640 px la barra de escritorio no cabe.
- Sin cambiar paleta ni tipografía. Shape de Impeccable enseñado a Antonio antes del craft.
  Verificado en navegador real a 375 y 640 px (y 1440 sin regresión).

### 2. Plurales del resumen del importador
- La tarjeta de `ImportarExcelSection` usa el `plural()` existente: "1 fila · 1 creada · 1
  actualizada".

### 3. `?q=` en el combobox de explotación del modal de revisión
- **D3a Cuándo se pide:** solo con el desplegable abierto; espera de 300 ms entre pulsaciones;
  `AbortController` cancela la petición anterior; una cancelación nunca se enseña.
- **D3b Regresión aceptada:** el backend no quita tildes ("martinez" ya no encuentra "Martínez") y
  busca la cadena entera en un solo campo ("ES12 Pérez" ya no encuentra nada). Aceptada **con la
  condición** de anotarla en `ganera-prompts.md` como **bloqueante antes del piloto**, en una sección
  propia de backend (no en las notas del Prompt C): columna de búsqueda normalizada calculada en Java
  (minúsculas, sin tildes, con REGA + nombre de la explotación + nombre del ganadero) y búsqueda por
  palabras en AND, independiente de la base de datos, sin `unaccent`.
- **D3c Sin texto:** primera página (20, orden por defecto del backend, `codigoRega`); si
  `totalElements` > 20, aviso "Hay N coincidencias; escribe para acotar" (región `status`).
- **D3d Con una explotación elegida:** si el texto del input es la etiqueta de la elegida
  ("REGA · nombre"), se trata como consulta vacía.
- **D3e Más de 100 caracteres:** se envía y se enseña el `motivo` del `400`. Sin `maxLength`.
- **D3f Error de búsqueda:** dentro del desplegable, con "Reintentar"; la explotación guardada sigue
  a la vista. Estados de carga, error y vacío como siempre.

### 4. `explotacionCodigoRega` en la cola
- La celda Explotación de `TramitesPage` usa `explotacionCodigoRega` (nombre para lectores de
  pantalla) o "Sin asignar". Desaparecen los estados cargando/no disponible/no encontrada y la alerta
  "No se han podido cargar los códigos REGA".
- Tras 3 y 4 se eliminan `todasLasExplotaciones.ts`, `useTodasLasExplotaciones.ts`,
  `explotacionDeTramite.ts` y el filtro en cliente de `buscarExplotacion.ts` (con sus tests);
  `etiquetaExplotacion` se conserva.

### 5. `completo` en los crotales
- `NO_ENCONTRADO` + `completo: false` → ámbar, "No está en el inventario · incompleto";
  `completo: true` → neutro como hoy. En la cola y en el modal.
- **D5b** `completo` ausente → neutro (no se clasifica en el frontend).

### 6. `motivo` del 403 de aprobar
- Ya funciona (`mensajeDeError` prioriza el `motivo`; el texto fijo es la reserva). Se añade un test
  que lo fija (un `motivo` distinto del fijo se ve tal cual, con el enlace a Facturación) y se corrige
  el comentario "llega sin cuerpo".

### 7. Comentarios desfasados de `errores.ts`
- Se corrigen los tres (importador, registro con `{mensaje}`/`MENSAJE_REGISTRO_INVALIDO`, 403 sin
  cuerpo). El soporte de cuerpo en texto plano de `extraerMotivo` se mantiene como defensa; el
  registro sigue con `ignorarMotivo`.

## Tareas

| Tarea | Puntos |
|---|---|
| T1 | 2, 6, 7 — plurales, test del 403 con `motivo`, comentarios |
| T2 | 5 — `completo` |
| T3 | 4 — cola con `explotacionCodigoRega` |
| T4 | 3 — combobox con `?q=` + eliminación de la lista completa |
| T5 | 1 — navbar (Impeccable shape → Antonio → craft; implementador + revisor + finish review) |
| T6 | Cierre: comandos, smoke con dos gestorías (1440/640/375), documentación |
