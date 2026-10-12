# Mensajes sin trámite (`SIN_TRAMITE`) y «Descartar» — plan

Fecha: 2026-10-12. Base: `main` = `origin/main` = `71bb338`. Suites de partida: backend 906, frontend 912 (43 ficheros).

**Estado: aprobado por Antonio el 2026-10-12 con las decisiones de abajo, que prevalecen sobre el resto del plan.**

## Decisiones de Antonio (2026-10-12) — prevalecen

- **A1 — Un clic + «Deshacer» sin caducidad** (D5 tal cual), con estas condiciones:
  - `deshacer-descarte` solo sobre descartados (`RECHAZADO` + `motivo_rechazo = SIN_TRAMITE`). Sobre un `RECHAZADO`
    normal → `409` y nada cambia.
  - `motivo_rechazo` guarda un **código**, nunca texto. El texto que ve el usuario sale del frontend. El `CHECK` de
    `V23` solo admite los códigos previstos: `motivo_rechazo IS NULL OR (estado = 'RECHAZADO' AND motivo_rechazo IN
    ('SIN_TRAMITE'))`.
  - **Historial de quién descartó y cuándo, y quién lo deshizo y cuándo** (hora de Madrid en la interfaz). Ver A6.
- **A2 — «Descartar» solo en el modal.** En la cola, solo el aviso «Parece un mensaje sin trámite».
- **A3 — Evaluación contra Haiku en el repo, desactivada por defecto** (`GANERA_EVAL_IA=true`): solo mensajes
  inventados, nunca en CI ni en `./mvnw test` normal, sin imprimir la clave; el informe da, por mensaje y pasada, el
  tipo devuelto.
- **A4 — Retención sin cambios** (D10); se anota en «Pendientes técnicos» de `progress.md` para el abogado.
- **A5 — Regresiones que tienen que verse en rojo antes del commit** (mutación con `cp` + `cmp`): descartar o deshacer
  sin filtrar por `gestoriaId`; `descartable` sin comprobar crotales o tipo; deshacer un rechazo normal; responder por
  WhatsApp en un descartado; quitar la red de seguridad de D2; el `CHECK` de `V23` admitiendo `motivo_rechazo` con un
  estado distinto de `RECHAZADO`.
- **A6 — Cómo se guarda el historial (sin datos personales nuevos).** Tabla nueva **`tramite_evento`** en la misma
  `V23` (`GestoriaScopedEntity`, entidad `TramiteEvento`): `id`, `gestoria_id`, `tramite_id`, `tipo`
  (`VARCHAR(30)`, `CHECK tipo IN ('DESCARTADO', 'DESCARTE_DESHECHO')`), `usuario_id` (FK a `usuario`, **del JWT**,
  nunca del cuerpo) y `creado_en` (`TIMESTAMP`, `Instant` del `Clock` truncado a microsegundos). Una fila por acción,
  así que descartar → deshacer → descartar queda entero (dos columnas en `tramite` solo guardarían la última). Solo
  referencia a un `Usuario` que ya existe y una hora: ningún dato personal nuevo, ni del ganadero ni del usuario. La
  interfaz muestra `Usuario.nombre` (nunca email ni id), como «Hecho en OVZ». Se inserta en la misma transacción que
  el cambio de estado. Sin texto, así que la retención no le afecta.
  - `TramiteDetalleResponse` gana `historialDescarte: [{tipo, usuarioNombre, en}]` (orden cronológico, `en` ISO-8601
    UTC con `Z`; lista vacía si no hay), cargado en una consulta con el usuario. La lista (`TramiteResponse`) no lo
    lleva.
  - `descartar` y `deshacer-descarte` devuelven **`TramiteDetalleResponse`** (como `PATCH`), para que el modal pinte
    la banda con quién y cuándo sin otra petición.
  - Textos: banda «Descartado como mensaje sin trámite por {nombre} el dd/mm/aaaa, hh:mm.»; historial (en el modal,
    si hay eventos, en cualquier estado) con una línea por evento: «Descartado por {nombre} el dd/mm/aaaa, hh:mm» y
    «Descarte deshecho por {nombre} el dd/mm/aaaa, hh:mm». Siempre Europe/Madrid (`fechaMadrid.ts`).
  - Limitaciones como en «Hecho en OVZ»: la FK no comprueba que el usuario sea de la gestoría del trámite (lo hace el
    servicio); sin `ON DELETE` (hoy no se borran usuarios).

**Qué es.** Desde B1 cada mensaje de un contacto activo crea un trámite, así que un «vale, gracias» o un «¿abrís el
lunes?» acaba en la cola. La IA distingue ahora `NO_IDENTIFICADO` (pide algo, pero no está claro qué) de
`SIN_TRAMITE` (no pide ningún trámite). **Nada se descarta solo:** el trámite sigue en `PENDIENTE_REVISION` con el
aviso «Parece un mensaje sin trámite», y el gestor lo quita de la cola con «Descartar» (un clic): pasa a `RECHAZADO`
con un motivo fijo y no se ofrece responder por WhatsApp.

Flujo: Superpowers (un implementador y un revisor independiente por tarea) y TDD. Cada brief lleva la regla de git de
`CLAUDE.md`. Validación con `./mvnw clean test`. Sin commit ni push hasta el visto bueno de Antonio; stage ruta a ruta.
Backend y frontend en el mismo commit (cambia el contrato).

## Decisiones propuestas (pendientes de Antonio)

- **D1 — `SIN_TRAMITE` no es un `TipoTramite`.** `TipoTramite` son los formularios de OVZ; meter ahí `SIN_TRAMITE`
  lo haría elegible en `PATCH`, en la ficha OVZ y en aprobar. Va en dos sitios:
  - en la IA, como valor nuevo de `TipoExtraido` (al lado de `NO_IDENTIFICADO`);
  - en el trámite, como valor nuevo de **`EstadoExtraccion`: `SIN_TRAMITE`** («extracción hecha: no pide ningún
    trámite»), igual que `SIN_TEXTO` ya es un resultado y no un fallo. `tipo_tramite` queda `null`.
  Así reutiliza lo que ya existe: `estadoExtraccion` sale en la lista y en el detalle, y el frontend ya pinta un aviso
  por cada valor (`TEXTOS_EXTRACCION`).
- **D2 — Defensa en el servidor contra falsos `SIN_TRAMITE`.** Si la IA dice `SIN_TRAMITE` pero devuelve **algún**
  crotal (aunque luego se descarte por inválido), se trata como `NO_IDENTIFICADO`: `COMPLETADA`, sin tipo, con los
  crotales resueltos como siempre. Un mensaje que nombra un animal casi siempre pide algo. Regla pura en
  `ExtraccionValidador` (la lista que mira es la que devolvió la IA, antes de normalizar).
- **D3 — «Descartable» lo decide el backend**, no el frontend (como `completo` en A2): un trámite es descartable si
  está en `PENDIENTE_REVISION`, `estadoExtraccion = SIN_TRAMITE`, **sin tipo y sin crotales**. Si el gestor ya le ha
  puesto tipo o crotales con `PATCH`, lo ha convertido en un trámite: el aviso desaparece y «Descartar» ya no se
  ofrece (le queda Rechazar). Se expone como `descartable: boolean` en `TramiteResponse` y `TramiteDetalleResponse`,
  calculado con los crotales que ya se cargan (sin consultas nuevas; los tests que cuentan sentencias no cambian).
- **D4 — Motivo fijo en una columna nueva**, `tramite.motivo_rechazo VARCHAR(30)` (enum `MotivoRechazo` con un solo
  valor, `SIN_TRAMITE`; `null` en un rechazo normal). Con un `CHECK` de coherencia como el de `HECHO_EN_OVZ`:
  `motivo_rechazo IS NULL OR estado = 'RECHAZADO'`. No se cierra la lista de valores con `CHECK` (ningún enum del
  esquema lo hace). Se expone como `motivoRechazo` (`"SIN_TRAMITE"`/`null`).
- **D5 — Sin confirmación; se puede deshacer (recomendado).** Argumentos:
  1. Descartar no sale de Ganera: ni WhatsApp ni OVZ. Un error cuesta un clic en «Deshacer», no un daño. Rechazar
     sí conserva su confirmación porque no se puede deshacer («¿Rechazar el trámite #N? No se puede deshacer.»).
  2. Es la acción más repetida del día (cada «gracias» de un ganadero). Una confirmación en una acción frecuente se
     acepta por reflejo y no protege de nada; un deshacer sí corrige el error cuando se nota.
  3. El riesgo real no es el clic, es que la IA se equivoque y el gestor se fíe. Eso lo cubren D2, que nada se
     descarte solo y que el botón esté **solo en el modal**, con el mensaje original a la vista (D6).
  4. El deshacer es **permanente**, no un toast de 5 segundos: la app no tiene toasts, y un aviso con tiempo se
     pierde justo cuando el gestor se da cuenta tarde.
  Coste: un endpoint más (`deshacer-descarte`) y sus tests. Alternativa más barata si Antonio la prefiere: la
  confirmación en línea de Rechazar con otro texto («¿Descartar este mensaje? Podrás deshacerlo.»), dos clics y sin
  endpoint de deshacer.
- **D6 — «Descartar» solo en el modal, no en la fila de la cola.** En la cola el aviso se ve, pero descartar sin leer
  el mensaje es justo el falso positivo que queremos evitar. Si en el piloto resulta lento, se añade a la fila después.
- **D7 — Deshacer solo el descarte.** `RECHAZADO` con `motivo_rechazo = SIN_TRAMITE` → `PENDIENTE_REVISION`,
  `motivo_rechazo = null`. Un rechazo normal sigue sin vuelta atrás (A1 D12 no cambia). Tras deshacer el trámite
  vuelve a ser descartable (sigue `SIN_TRAMITE` y sin tipo ni crotales).
- **D8 — Sin puerta de suscripción** en descartar ni en deshacer (A1 D30: solo aprobar está bloqueado).
- **D9 — WhatsApp.** Antes de descartar, la sección «Responder por WhatsApp» funciona como hoy (el gestor puede
  contestar a «¿abrís el lunes?»). Un trámite descartado **no** ofrece responder: `ReglasRespuestaWhatsapp
  .motivoNoDisponible` recibe el motivo de rechazo y devuelve un motivo propio, y `POST /tramites/{id}/respuestas-
  whatsapp` da `409` con ese motivo (no solo se oculta en la UI). Descartar no envía nada nunca. Tras deshacer, se
  vuelve a poder responder.
- **D10 — Retención: sin cambios en esta tarea.** El texto de un mensaje descartado se guarda 12 meses como el de
  cualquier trámite. Pregunta abierta para la conversación con el abogado: tratar los descartados como los mensajes
  sin trámite (30 días). Se anota en `progress.md`.

## Cambio del prompt de la IA

`TipoExtraido` gana `SIN_TRAMITE` (al final). En `PROMPT_SISTEMA` se sustituye la última línea de «Tipos» por:

```
  SIN_TRAMITE: el mensaje entero no pide ni comunica ningún trámite: es solo un saludo, un
  agradecimiento, una confirmación («vale», «ok», «gracias», «recibido») o una pregunta a la
  gestoría que no es sobre animales (horario, facturas, una cita).
  NO_IDENTIFICADO: el mensaje pide o comunica algo sobre animales o la explotación, pero no
  está claro qué trámite es.
- Usa SIN_TRAMITE solo si estás seguro. Si el mensaje menciona un animal, un crotal, un
  ternero, una muerte, una venta, un traslado, una compra o el censo, o pide a la gestoría
  que apunte, anote o haga algo, nunca es SIN_TRAMITE, aunque empiece por «vale» u «ok»,
  salude o dé las gracias: usa su tipo, o NO_IDENTIFICADO si no está claro.
  Ante la duda entre SIN_TRAMITE y NO_IDENTIFICADO, usa NO_IDENTIFICADO.
```

**Ajuste tras la primera evaluación (2026-10-12):** con la primera versión del bloque («SIN_TRAMITE: el mensaje no
pide ni comunica ningún trámite: saludos, agradecimientos, confirmaciones…» y «…aunque además salude o dé las
gracias…»), «ok, apunta el censo» salió `SIN_TRAMITE` en 1 de 3 pasadas: la lista de confirmaciones con «ok»
arrastraba el mensaje. Se cambió a «el mensaje entero… es solo…» y se añadió «o pide a la gestoría que apunte, anote o
haga algo… aunque empiece por «vale» u «ok»». Dos ejecuciones completas después: 0 falsos y 18/18 de charla.

La descripción del campo pasa a: «Tipo de trámite pedido; NO_IDENTIFICADO si no está claro; SIN_TRAMITE solo si el
mensaje seguro que no pide ningún trámite.» `TramiteExtraido` gana `boolean sinTramite`; `NO_IDENTIFICADO` y
`SIN_TRAMITE` dan los dos `tipoTramite = null` y se distinguen por ese campo.

**Cómo se evitan falsos `SIN_TRAMITE`**, en capas: (1) el prompt, con la regla asimétrica «ante la duda,
`NO_IDENTIFICADO`» y la lista de palabras que lo excluyen; (2) D2 en el servidor; (3) nada se descarta solo; (4) el
botón solo con el mensaje a la vista; (5) deshacer. Y se mide (ver Smoke): con el conjunto de prueba, **cero** falsos
`SIN_TRAMITE` en los mensajes que piden algo es requisito; acertar todos los de charla es deseable, no requisito (un
«gracias» que sale `NO_IDENTIFICADO` solo cuesta un Rechazar).

## Migración

**No hay `CHECK` de tipos** en el esquema: ni `tipo_tramite` ni `estado_extraccion` lo tienen, y `SIN_TRAMITE` no
entra en `tipo_tramite` (D1). `estado_extraccion` es `VARCHAR(20)` y `SIN_TRAMITE` tiene 11: cabe sin tocarla.

Hace falta una migración, pero por D4: **`V23__motivo_rechazo.sql`** (SQL, la siguiente libre tras `V22`):
`ADD COLUMN motivo_rechazo VARCHAR(30)` + `CONSTRAINT ck_tramite_motivo_rechazo CHECK (motivo_rechazo IS NULL OR
estado = 'RECHAZADO')`. Las filas existentes quedan con `null` y cumplen el `CHECK`. Probada en H2 y en PostgreSQL
embebido (los tests de Flyway V1–V22 pasan a V1–V23). Si Antonio elige la alternativa de D5 sin deshacer, la
migración sigue haciendo falta (el motivo fijo hay que guardarlo en algún sitio).

## Contrato backend/frontend (mismo commit)

**Respuestas.** `TramiteResponse` (lista, aprobar, rechazar, marcar/desmarcar, descartar, deshacer) y
`TramiteDetalleResponse` ganan:
- `estadoExtraccion`: un valor más, `"SIN_TRAMITE"`.
- `motivoRechazo`: `"SIN_TRAMITE"` o `null`.
- `descartable`: `boolean` (D3).

**`POST /tramites/{id}/descartar`** `{version}` (`TramiteDescartarRequest`) → `200 TramiteResponse`, `RECHAZADO`,
`motivoRechazo = SIN_TRAMITE`, `version + 1`. Orden, como rechazar:
1. `400 {motivo}` sin cuerpo / `{}` / `version: null` — en el controlador, antes de tocar la BD (misma respuesta para
   uno propio, ajeno o inexistente).
2. `404` sin cuerpo — de otra gestoría o inexistente (`findConBloqueoByIdAndGestoriaId` + `refresh`).
3. `409 {motivo}` no está en `PENDIENTE_REVISION`.
4. `409 {motivo}` `version` desfasada.
5. `409 {motivo}` no descartable (no es `SIN_TRAMITE`, o ya tiene tipo o crotales).
6. `200`.

**`POST /tramites/{id}/deshacer-descarte`** `{version}` → `200 TramiteResponse`, `PENDIENTE_REVISION`,
`motivoRechazo = null`, `version + 1`. Orden: `400` sin `version` (controlador) → `404` sin cuerpo → `409` no es un
descarte (`RECHAZADO` + `SIN_TRAMITE`) → `409` `version` desfasada → `200`.

Los dos: sin puerta de suscripción (D8), `ConcurrencyFailureException`/`DataIntegrityViolationException` → el `409`
genérico de siempre, nunca `500`; no llaman a `EnvioWhatsapp` ni a nada externo.

**`POST /tramites/{id}/respuestas-whatsapp`** sobre un descartado → `409` con el motivo de D9, sin crear fila.
`whatsapp.motivoNoDisponible` del detalle lo refleja.

**Rechazar normal** no cambia: deja `motivo_rechazo = null`. `PATCH` sobre un descartado ya da `409` (no está
pendiente).

## Aislamiento

Todo con el `gestoriaId` del JWT como parámetro explícito; nunca `findById`. Descartar o deshacer un trámite de otra
gestoría da **`404` sin cuerpo**, igual que uno inexistente, y no cambia nada (se comprueba `version` y `estado` de la
fila ajena después). Cubierto en un `@SpringBootTest` nuevo con dos gestorías (`TramiteDescarteEndToEndTest`) y a nivel
de servicio en `TramiteRevisionServiceTest`. Que el `400` sin `version` llegue antes de la BD también se prueba con un
id ajeno.

## Textos exactos

Ninguno dice ni insinúa que Ganera haya enviado, respondido o hecho nada fuera de su base de datos. Todos pasan la
lista `PROHIBIDAS` del test de honestidad del modal (`enviad|ejecutad|registrad|…|tramitad|…`).

| Dónde | Texto |
|---|---|
| Aviso en la cola (línea bajo el estado, tono neutro, no ámbar: no es un fallo) | Parece un mensaje sin trámite |
| Aviso en el modal (fijo, sin `role`) | Parece un mensaje sin trámite: un saludo, un agradecimiento o una pregunta. Si hay que contestar, responde antes de descartar. |
| Botón (modal, pie, junto a Rechazar) | Descartar |
| En curso | Descartando… |
| Deshabilitado con cambios sin guardar | (la misma pista de hoy) Guarda antes de aprobar |
| Badge de estado (cola y modal) para `RECHAZADO` + `SIN_TRAMITE` | Descartado (variante neutra `outline`, no `danger`) |
| Banda en el modal de un descartado | Descartado como mensaje sin trámite. |
| Botón de la banda | Deshacer |
| En curso | Deshaciendo… |
| Pista junto a Deshacer | Vuelve a la cola de pendientes. |
| `409` estado (descartar) | Solo se puede descartar un trámite pendiente de revisión. |
| `409` no descartable | Solo se descarta un mensaje sin trámite que nadie ha completado. Si no procede, recházalo. |
| `409` deshacer | Este trámite no está descartado; no hay nada que deshacer. |
| Motivo WhatsApp (D9) | Este mensaje se descartó como sin trámite; no se responde desde Ganera. |

El `400` sin `version` y el `409` de versión desfasada reutilizan los motivos que ya existen.

## Frontend

- `types.ts`: `EstadoExtraccion` + `SIN_TRAMITE`; `motivoRechazo`, `descartable` en los dos tipos de respuesta.
- `etiquetas.ts`: `TEXTOS_EXTRACCION.SIN_TRAMITE`; `avisosExtraccion` lo muestra **solo si `descartable`** (si el
  gestor ya lo ha completado, no hay aviso). Presentación del estado «Descartado» desde la misma fuente única.
- `api.ts`: `descartarTramite(id, version)`, `deshacerDescarte(id, version)`.
- `revisionTramite.ts` / `useRevisionTramite.ts`: `puedeDescartar` (= `descartable` y sin cambios sin guardar), las
  dos acciones con el mismo tratamiento de errores que rechazar (cualquier `409` recarga y muestra el `motivo`; `404`
  el texto de siempre). Una petición a la vez.
- `TramiteReviewDialog.tsx`: «Descartar» de un clic en el pie; tras descartar, el modal queda en solo lectura con la
  banda y «Deshacer»; foco al título mientras corre la petición y luego a «Deshacer» (y al revés al deshacer).
- `SeccionWhatsapp`: sin cambios de lógica; muestra el `motivoNoDisponible` del backend.
- Sin pase de Impeccable (patrones existentes: banda de «Hecho en OVZ», botón secundario del pie); Antonio lo revisa
  en las capturas del smoke a 375 y 1440 px. `DESIGN.md` gana una línea sobre «Descartar».

## Tareas

- **T1 — IA y extracción (backend).** `TipoExtraido`, prompt, `TramiteExtraido.sinTramite`, D2 en
  `ExtraccionValidador`, `EstadoExtraccion.SIN_TRAMITE` aplicado en `ExtraccionTramiteService.aplicarExito`.
- **T2 — Descartar y deshacer (backend).** `V23`, `MotivoRechazo`, `descartable`, los dos endpoints, la regla de
  WhatsApp, E2E de dos gestorías.
- **T3 — Frontend.**
- **T4 — Evaluación del prompt y smoke real** (abajo).

## Tests nuevos (estimación)

Backend (906 → ~955):

| Fichero | Nuevos | Qué |
|---|---|---|
| `AnthropicTramiteExtractionServiceTest` | ~4 | `SIN_TRAMITE` → `sinTramite` y tipo `null`; `NO_IDENTIFICADO` sigue sin `sinTramite`; el prompt lleva la regla «ante la duda»; el test de valores de `TipoExtraido` se actualiza |
| `ExtraccionValidadorTest` | ~4 | D2: sin crotales se mantiene; con un crotal válido o solo inválidos pasa a no-`SIN_TRAMITE` (y los inválidos se cuentan); un tipo normal no cambia |
| `ExtraccionTramiteServiceTest` | ~4 | aplica `SIN_TRAMITE` (`PENDIENTE_REVISION`, tipo `null`, `version + 1`, nunca `RECHAZADO`); reintento tras `FALLIDA` que acaba en `SIN_TRAMITE`; `SIN_TRAMITE` con crotales → `COMPLETADA` |
| `TramiteRevisionServiceTest` | ~11 | orden completo de descartar y deshacer; no descartable con tipo o con crotales; rechazar normal deja `motivo_rechazo` `null`; deshacer un rechazo normal → `409`; versión siempre leída + 1 |
| `TramiteControllerErroresTest` | ~4 | `400` sin `version` en los dos antes de la BD; mapeo de `409` y de concurrencia |
| `TramiteResponseOrigenTest` (o `TramiteDescartableTest` nuevo) | ~5 | regla pura de `descartable` |
| `ReglasRespuestaWhatsappTest` | ~2 | motivo de descartado; un rechazo normal sigue como hoy |
| `RespuestaWhatsappEndToEndTest` | ~2 | responder a un descartado → `409` sin fila; tras deshacer, se puede |
| `TramiteDescarteEndToEndTest` (nuevo, dos gestorías) | ~10 | descartar y deshacer propios `200`; ajeno e inexistente `404` sin cuerpo y nada cambia; sin JWT `401`; `SUSPENDIDA` puede descartar; la lista y el detalle exponen `motivoRechazo`/`descartable`; ninguna llamada a `EnvioWhatsapp` |
| `WhatsAppExtraccionEndToEndTest` | ~1 | de punta a punta con la IA falsa diciendo `SIN_TRAMITE` |
| Tests de migración (H2 y PostgreSQL embebido) | ~2 | `V23` aplica; el `CHECK` rechaza `motivo_rechazo` con otro estado |

Frontend (912 → ~948, 43 ficheros, ninguno nuevo):

| Fichero | Nuevos | Qué |
|---|---|---|
| `etiquetas.test.ts` | ~5 | aviso `SIN_TRAMITE` solo con `descartable`; «Descartado» vs «Rechazado» |
| `api.test.ts` | ~2 | cuerpos y rutas de las dos llamadas |
| `revisionTramite.test.ts` | ~3 | `puedeDescartar` |
| `useRevisionTramite.test.tsx` | ~5 | descartar, deshacer, `409` recarga con `motivo`, `404`, una petición a la vez |
| `TramiteReviewDialog.test.tsx` | ~12 | botón solo si descartable; un clic envía `{version}` sin confirmación; deshabilitado con cambios; banda y Deshacer; foco; lista `PROHIBIDAS` y menciones de OVZ en los estados nuevos |
| `TramiteReviewDialog.whatsapp.test.tsx` | ~3 | descartado: motivo y ningún «Responder»; tras deshacer vuelve |
| `TramitesPage.test.tsx` | ~3 | línea de aviso; badge «Descartado»; ningún botón en la fila |
| `BadgesTramite.test.tsx` | ~2 | badge «Descartado» |

## Smoke real

1. **Evaluación del prompt contra Haiku real**, en el terminal de Antonio (la clave nunca la ve Claude). Un test
   `ExtraccionSinTramiteEvalTest` **desactivado por defecto** (solo corre con `GANERA_EVAL_IA=true` y la clave en el
   entorno; `./mvnw clean test` nunca llama a la API). Cada mensaje, 3 veces:
   - Deben salir `SIN_TRAMITE`: «vale, gracias», «ok», «recibido 👍», «¿abrís el lunes?», «¿me mandáis la factura?»,
     «buenos días».
   - **No** pueden salir `SIN_TRAMITE` (requisito: cero en las 3 pasadas): «gracias, y se me ha muerto la 4521»,
     «vale, pues la 0123 sale mañana al matadero», «ha nacido un ternero», «ok, apunta el censo», «gracias, ¿qué hago
     con la vaca que murió ayer?», «la 7788», «mañana vienen dos novillas de Pepe».
   Si falla el requisito, se ajusta el prompt y se repite antes de seguir.
2. **WhatsApp de verdad** (sandbox de Twilio, móvil de Antonio, Cloudflare Quick Tunnel, H2 en fichero, datos
   inventados, gestorías A y B): «vale, gracias» → aviso en la cola de A, nada en B → abrir, Descartar → «Descartado»,
   sin sección de respuesta, nada llega al móvil → Deshacer → vuelve a pendientes y se puede responder. Un mensaje
   mixto («gracias, se ha muerto la 4521») → `BAJA_MUERTE`, sin aviso.
3. **Navegador real** (Playwright fuera del repo): gestoría B intenta `descartar` y `deshacer-descarte` sobre el id de
   A con su JWT → `404`; B en `SUSPENDIDA` descarta los suyos; capturas a 375 y 1440 px fuera del repo, en
   `C:\Users\Antonio\Desktop\capturas-ganera-sin-tramite\`. Túnel cerrado y ficheros del smoke borrados al terminar.

## Fuera de alcance

Varios trámites por mensaje, adjuntos, plantillas fuera de la ventana de 24 h, cambiar la retención de los descartados
(D10), descartar desde la fila de la cola (D6), un motivo de rechazo libre.
