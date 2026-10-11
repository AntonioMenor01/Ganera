# Marcar un trámite como «hecho en OVZ» (D5 de la ficha OVZ) — plan

Fecha: 2026-10-07. Base: `main` = `2a76f0f`. Suites de partida: backend 706, frontend 720 (40 ficheros).

**Estado (2026-10-07): T1–T3 hechas y verificadas, pendiente de commit.** Backend 742/742, frontend 811/811 (41
ficheros), smoke en navegador real 13/13 (Chromium y Firefox). Detalle en `.superpowers/sdd/progress.md`.

**Qué es.** Hoy un trámite acaba en `APROBADO`, que solo significa «datos revisados». Se añade un paso: el gestor
indica que **él** ya lo ha metido en OVZ, y Ganera guarda quién y cuándo. Así la cola distingue «aprobado, falta
OVZ» de «terminado». **Ganera no envía nada a OVZ**: el botón solo deja constancia de lo que hizo la persona, y
ningún texto puede sugerir que Ganera lo registró (`decisiones-ovz-y-piloto.md`: no hay escalón 3).

Flujo: Superpowers (un implementador y un revisor independiente por tarea) y TDD. Cada brief lleva la regla de git
de `CLAUDE.md`. Validación con `./mvnw clean test`. Sin pase de Impeccable con aprobación previa: se siguen los
patrones visuales existentes y Antonio lo revisa en las capturas del smoke (375, 420 y 1440 px). Sin commits ni
push hasta el visto bueno de Antonio; stage ruta a ruta. Backend y frontend en el mismo commit (cambia el
contrato).

## Decisiones cerradas con Antonio (2026-10-07)

- **D1 — Estado nuevo `HECHO_EN_OVZ`** + columnas `hecho_en_ovz_por` (FK a `usuario`) y `hecho_en_ovz_en`
  (`TIMESTAMP`, `Instant` del `Clock`), con un `CHECK` de coherencia: con `HECHO_EN_OVZ` las dos llenas; con
  cualquier otro estado, las dos `null`. `EJECUTADO_OVZ` no se reutiliza (su etiqueta da a entender que lo hizo
  Ganera). El enum no pierde ningún valor.
- **`hecho_en_ovz_por` sale siempre del usuario autenticado** (JWT), nunca del cuerpo de la petición.
- **D2 — Se puede deshacer:** `HECHO_EN_OVZ → APROBADO`, con `version`, cualquier usuario de la gestoría, sin
  límite de tiempo; vacía quién y cuándo y deja un log `INFO` (`tramiteId`, `usuarioId`). Marcar **sin**
  confirmación; quitar la marca **con** confirmación en línea (como Rechazar). Texto: «En OVZ no cambia nada».
- **D3 — Endpoints:** `POST /tramites/{id}/marcar-hecho-ovz {version}` y
  `POST /tramites/{id}/desmarcar-hecho-ovz {version}` → `TramiteResponse`. Orden: `400` sin `version` (en el
  controlador, antes de la BD) → `404` sin cuerpo (otra gestoría o no existe) → `409` estado (`APROBADO` para
  marcar, `HECHO_EN_OVZ` para desmarcar) → `409` versión desfasada → `200`, `version + 1`. **Sin bloqueo de
  suscripción** (A1 D30: solo aprobar está bloqueado).
- **Editar un trámite en `HECHO_EN_OVZ` → `409` con su motivo** («quita la marca para editar»).
- **D4 — Botón en la ficha y en el modal**, con la misma llamada.
- **La ficha se abre también en `HECHO_EN_OVZ`**, en modo consulta: datos y botones de copiar, con la franja
  «Hecho en OVZ por Ana Pérez el 07/10/2026, 12:30» arriba para que nadie lo meta dos veces. Fecha en
  **Europe/Madrid**.
- **D5 — Cola y filtro:** badges «Aprobado · falta OVZ» (`warning`) y «Hecho en OVZ» (`success`). En la cola solo
  el badge. El filtro pierde `EN_PROCESO`, `EJECUTADO_OVZ` y `ERROR_OVZ` (siguen en el enum y en las etiquetas).
  El filtro por defecto sigue siendo «Pendiente de revisión».
- **Textos:** la persona es siempre el sujeto («hecho en OVZ», «marcado por…»). Nunca «registrado», «enviado»,
  «ejecutado» ni «procesado». El test de honestidad del modal se amplía a las menciones nuevas sin aflojar la
  lista de palabras prohibidas.
- **Lectura del mensaje sin `gestoriaId`:** T1 toca `TramiteRevisionService.actualizar`, así que se corrige allí
  y en `GET /tramites/{id}` (`findFirstByTramiteIdAndGestoriaIdOrderByCreatedAtDesc`), con test de dos gestorías.
- **Limitación conocida (se anota en `CLAUDE.md`):** una ventana de ficha con la sesión antigua (otra persona entró
  después en la principal) marcaría con el usuario anterior. Es de la misma gestoría, no filtra nada.
- **Despliegue demo:** `seed-tramites.sql` y `smoke.sh` (fuera del repo) deben incluir `HECHO_EN_OVZ` (nota en
  `progress.md`).
- **Docs:** la corrección de «pendiente de commit» de la ficha (ya está en `db0d234`) en `CLAUDE.md`,
  `progress.md`, `ganera-prompts.md` y el plan de la ficha va en un commit `docs:` aparte.

## Tareas

### T1 — Backend

- `EstadoTramite.HECHO_EN_OVZ`; `Tramite` con `hechoEnOvzPor` (`@ManyToOne(LAZY)` a `Usuario`) y `hechoEnOvzEn`
  (`Instant`).
- `V21__hecho_en_ovz.sql`: las dos columnas (nullable), FK a `usuario(id)` y el `CHECK`. Tests en H2 (Flyway
  programático: filas previas en cada estado quedan con `null`; el `CHECK` rechaza las combinaciones incoherentes)
  y en PostgreSQL embebido (lo mismo + `FlywayPostgresTest` hasta V21 con `validate`).
- `TramiteRevisionService.marcarHechoEnOvz` / `desmarcarHechoEnOvz` (bloqueo + `refresh`, estado, versión,
  `incrementarVersion`), usuario cargado con `gestoriaId` explícito, hora del `Clock`.
- `actualizar` en `HECHO_EN_OVZ` → `409` con el motivo propio; el resto de estados, como hoy.
- `TramiteResponse` y `TramiteDetalleResponse` ganan `hechoEnOvzEn` (ISO-8601 UTC) y `hechoEnOvzPorNombre`. En la
  lista, sin N+1 (`@EntityGraph` + test que cuenta sentencias).
- Mensaje con `gestoriaId` en `actualizar` y en `GET /tramites/{id}`.
- Tests: servicio, controlador, E2E con dos gestorías (cada endpoint `404` cruzado; marcar con un cuerpo que
  intenta poner otro usuario → queda el autenticado; gestoría `SUSPENDIDA` puede marcar y desmarcar; GET del
  detalle en `HECHO_EN_OVZ` con quién y cuándo).

### T2 — Frontend

- Tipos y API (`marcarHechoEnOvz`, `desmarcarHechoEnOvz`), etiquetas (`APROBADO` → «Aprobado · falta OVZ»
  `warning`; `HECHO_EN_OVZ` → «Hecho en OVZ» `success`), filtro sin los tres estados de Playwright.
- Fecha en Europe/Madrid con `Intl.DateTimeFormat("es-ES", { timeZone: "Europe/Madrid" })`, función pura con
  tests (incluido el cambio de hora de octubre).
- Ficha: `modoFicha(HECHO_EN_OVZ)` = consulta (copiable + franja). En `APROBADO`, «Marcar como hecho en OVZ» al
  final con la pista «Púlsalo cuando lo hayas terminado tú en OVZ. Ganera no envía nada.». En `HECHO_EN_OVZ`,
  franja y «Quitar la marca» con confirmación en línea. `409` → recarga y enseña el `motivo`.
- Modal: en `APROBADO`, «Marcar como hecho en OVZ» en el pie; en `HECHO_EN_OVZ`, la franja, «Quitar la marca» con
  confirmación en línea y «Abrir ficha para OVZ». `409` → recarga, descarta y enseña el `motivo` (como hoy).
- Test de honestidad ampliado (modal y ficha). `DESIGN.md` con los patrones nuevos.

### T3 — Cierre

- `./mvnw clean test`, `npm test`, `npm run build`, `npm run lint`.
- Smoke en navegador real con dos gestorías (A `ACTIVA`, B `SUSPENDIDA`): marcar desde la ficha y desde el modal,
  deshacer, `409` por versión desfasada (dos ventanas), `404` cruzado en los dos endpoints, filtro y badges en la
  cola, B marca sin bloqueo, capturas a 375, 420 y 1440 px.
- `CLAUDE.md`, `DESIGN.md`, `ganera-prompts.md`, `progress.md` al día; lista de rutas a stagear.
