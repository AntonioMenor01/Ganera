# B2 reducido — la gestoría responde al ganadero por WhatsApp desde el trámite

**Estado:** **aprobado por Antonio el 2026-10-11** con los ajustes de la sección siguiente, que prevalecen
sobre el texto de cada decisión. Fecha: 2026-10-11. Base: `main` en `747b2ad` (backend 742, frontend 811).

## Decisiones de Antonio (2026-10-11)

- **D1, D3, D4, D5, D6, D7, D9, D11, D13 y D14:** tal cual.
- **D2:** hueco obligatorio, sí. **OVZ solo aparece en la plantilla de `HECHO_EN_OVZ`, que dice «ya está hecho
  en OVZ».** La de `APROBADO` no menciona OVZ ni da nada por presentado (p. ej. «Hemos revisado tu trámite y lo
  tramitamos en breve»). El test de honestidad se amplía para fijarlo: la única plantilla con «OVZ» es la de
  `HECHO_EN_OVZ`. **La lista de palabras prohibidas no se toca** (enviado/ejecutado/registrado/procesado/
  presentado/completado siguen prohibidas en todas las plantillas, sin excepciones).
- **D8:** a los 12 meses se vacían **el texto y también el teléfono de destino** de la respuesta.
- **D10:** sí, pero como **tarea y commit propios, después del envío, solo backend**. Firma `X-Twilio-Signature`
  fail-closed; busca **solo por `MessageSid`** y no acepta del request nada que identifique gestoría ni trámite;
  límite de tamaño del cuerpo; idempotente ante callbacks repetidos. Si se alarga, se corta y pasa a B2.1.
- **D12:** aceptado para esta tarea. Aprobada como **tarea aparte, después de B2 y antes del piloto**
  (anotada en «Pendientes técnicos» de `progress.md`): `SIN_TRAMITE` en la extracción; el trámite sigue en
  `PENDIENTE_REVISION`, con aviso y «Descartar» de un clic que lo rechaza; al descartar no se ofrece ni se envía
  respuesta al ganadero.
- **Orden de commits:** 1) `docs:`; 2) `feat:` envío (backend + frontend juntos, `V22`); 3) `feat:` callback de
  estado. Twilio falso en todos los tests **y en el smoke**; el envío real al sandbox lo hace Antonio al final,
  desde su terminal a su propio móvil. Smoke en navegador real a 375, 420 y 1440 px con dos gestorías.

## Objetivo

Desde un trámite que llegó por WhatsApp, un Usuario de la gestoría escribe (o edita un texto propuesto) y lo
envía al Contacto que escribió, por el número compartido de Twilio (sandbox mientras no haya número propio).
Ganera guarda qué se envió, quién, cuándo y qué dijo Twilio. **Nada se envía solo.**

Fuera de alcance: plantillas aprobadas por Meta, conversación con hilos, respuestas automáticas, contestar a
números desconocidos, adjuntos, varios trámites por mensaje, alta por nacimiento, fecha del hecho y medición
de uso (siguen en las notas del Prompt B).

## Lo que ya existe y condiciona el diseño

- `Tramite.contacto` es `NOT NULL`: el destinatario natural es el Contacto que escribió, no el Ganadero
  (el Ganadero no tiene teléfono; es la cuenta de OVZ).
- `MensajeCampo` guarda los entrantes con `contacto_id`, `gestoria_id` y `created_at` (con el `Clock`): de ahí
  sale la ventana de 24 h.
- Dependencia `com.twilio.sdk:twilio` 10.6.4 ya está; `twilio.account-sid` / `twilio.auth-token` ya están en
  `application.yml`. Falta el remitente (`whatsapp:+14155238886` en el sandbox).
- Lecciones previas que se aplican: cliente creado de forma perezosa y tolerante a credenciales vacías (footgun
  de Twilio), firma validada contra la URL pública configurada, `findByIdAndGestoriaId` siempre, nunca una
  llamada externa dentro de la transacción que bloquea la fila del trámite (riesgo M2 de A1 para 3c), logs
  solo con ids y teléfono enmascarado (`EnmascaradorTelefono`).

---

## Decisiones abiertas (con recomendación)

### D1. En qué trámites y estados se puede responder

| Opción | Qué implica |
|---|---|
| **a) Solo `origen = WHATSAPP`, en `PENDIENTE_REVISION`, `APROBADO`, `HECHO_EN_OVZ` y `RECHAZADO`** | `PENDIENTE_EXTRACCION` dura segundos; los tres estados reservados no se asignan nunca |
| b) Cualquier estado | Nada que ganar; abre casos raros (responder durante la extracción) |
| c) Solo estados finales | Impide lo más útil: pedir datos que faltan en `PENDIENTE_REVISION` |

**Recomendación: a).** Un trámite sin origen WhatsApp (solo los sembrados a mano) no tiene conversación ni
ventana: `409`. Un Contacto **inactivo** tampoco: `409` (coherente con que el webhook ignore sus mensajes).

### D2. Texto propuesto por estado

Recomendación: el frontend propone un texto (lógica pura en `features/tramites/respuestaWhatsapp.ts`, con
tests), siempre editable. Se rellena con el tipo en lenguaje llano y hasta 3 crotales (+ «y N más»). Ejemplos:

- `PENDIENTE_REVISION`: «Hola, {nombre}. Para tramitar la {tipo} necesitamos: ___» — el cursor queda en el
  hueco; **«Enviar» se desactiva mientras siga el `___`** (evita mandar la plantilla sin completar).
- `APROBADO`: «Hola, {nombre}. Hemos revisado tu trámite ({tipo} de {crotales}) y lo tramitamos en breve.»
- `HECHO_EN_OVZ`: «Hola, {nombre}. Tu trámite ({tipo} de {crotales}) ya está hecho en OVZ.»
- `RECHAZADO`: «Hola, {nombre}. No hemos podido tramitar tu mensaje del {fecha}. ___» (también con hueco
  obligatorio: el motivo lo escribe la persona).

Todos terminan con «— {nombre de la gestoría}» (D3). Solo la plantilla de `HECHO_EN_OVZ` menciona OVZ, y
ninguna usa las palabras prohibidas del test de honestidad (ver «Decisiones de Antonio»).

### D3. Firma de la gestoría

El número es compartido: el ganadero ve el número de Ganera, no el de su gestoría.

- **a) La firma va en el texto propuesto y es editable** — lo que se ve es lo que se envía.
- b) El servidor añade siempre una cabecera «[Gestoría X]» — garantiza la identificación, pero el texto enviado
  no es el que se vio.

**Recomendación: a)**, y el servidor no toca el texto. Con número propio por gestoría deja de importar.

### D4. Envío siempre manual

Confirmado como requisito: botón «Responder por WhatsApp» → panel con el texto propuesto → la persona edita y
pulsa «Enviar». Sin confirmación extra (enviar ya es un segundo clic deliberado); sin envío automático en
ningún cambio de estado. Límite de 1600 caracteres (el de Twilio), comprobado en frontend y backend (`400`).

### D5. Ventana de 24 h

WhatsApp solo deja mensajes libres dentro de las 24 h siguientes al **último mensaje del contacto** (no solo
del de este trámite). Fuera de ella hace falta una plantilla aprobada por Meta.

| Opción | |
|---|---|
| **a) Bloquear fuera de ventana** (`409` con motivo; botón desactivado con explicación y enlace `tel:`) | Honesto y sin coste |
| b) Avisar y dejar intentar | Twilio lo rechaza (63016) de todas formas: un fallo garantizado |
| c) Plantilla | Necesita número propio verificado y plantillas aprobadas: no existe en el sandbox |

**Recomendación: a).** `ventanaAbiertaHasta = max(mensaje_campo.created_at del contacto en su gestoría) + 24 h`,
con el `Clock`. Se informa en el detalle; con menos de 1 h, aviso «la ventana se cierra a las hh:mm». La
comprobación del servidor es la que vale (el frontend solo la refleja). El texto: «Han pasado más de 24 h desde
su último mensaje. WhatsApp no deja escribirle hasta que vuelva a escribir. Puedes llamarle: {tel}».

**Sandbox:** aplica la misma regla de 24 h, y además el destinatario tiene que haberse unido al sandbox
(«join …») y la unión caduca a las 72 h: eso da el error 63015, que se muestra con su propio texto (D9). Las
plantillas quedan anotadas para el número propio (Prompt C).

### D6. Destinatario y aislamiento

- El destinatario sale **siempre** de `tramite.contacto` cargado con `findByIdAndGestoriaId` (con el
  `gestoriaId` del JWT); el cuerpo de la petición **no tiene** campo de teléfono ni de contacto (un campo
  desconocido se ignora y un test lo comprueba enviando `telefono` en el JSON).
- Trámite de otra gestoría o inexistente → `404` sin cuerpo, antes de cualquier llamada a Twilio.
- Tests `@SpringBootTest` con dos gestorías: `POST` y `GET` cruzados `404`, el doble de envío nunca recibe
  la llamada, y la respuesta de A no aparece en el detalle de B.

### D7. Idempotencia

| Opción | |
|---|---|
| a) `version` del trámite (y subirla al enviar) | Enviar no cambia el trámite: subir la versión daría `409` falsos a quien tenga el modal abierto (lo mismo que se evitó en B1) |
| **b) Clave de idempotencia del cliente** (UUID generado al abrir el panel) con `UNIQUE(gestoria_id, clave_idempotencia)` | Un reintento o doble clic con la misma clave devuelve la respuesta ya guardada y **no** vuelve a llamar a Twilio |

**Recomendación: b)**, más el botón desactivado mientras hay una petición (patrón «una petición a la vez»).
Flujo del servidor: (1) transacción corta que inserta la fila en `ENVIANDO` (el `UNIQUE` corta la carrera de
dos peticiones simultáneas) → (2) llamada a Twilio **fuera de transacción y sin bloquear el trámite** →
(3) transacción corta que guarda SID y estado. Twilio no admite clave de idempotencia en la API de mensajes,
así que **Ganera nunca reintenta el envío por su cuenta**: un timeout o error de red deja `SIN_CONFIRMAR`
(D9). Una clave repetida con otro texto → `409`.

### D8. Persistencia y RGPD

Entidad nueva `RespuestaWhatsapp` (tabla `respuesta_whatsapp`, `GestoriaScopedEntity`, skill
`nueva-entidad-tenant`, migración `V22`):

`id, gestoria_id, tramite_id, contacto_id, telefono_destino` (copia en el momento del envío: si el contacto
cambia de teléfono, el historial dice a dónde fue)`, cuerpo TEXT, enviado_por` (FK a `usuario`, del JWT)`,
creado_en, estado, twilio_message_sid UNIQUE NULL, codigo_error_twilio NULL, actualizado_en,
clave_idempotencia`.

- `estado`: `ENVIANDO`, `ENVIADO` (aceptado por Twilio), `ENTREGADO`, `LEIDO`, `FALLIDO`, `SIN_CONFIRMAR`.
- No se guarda el cuerpo de la respuesta de Twilio, ni el `AccountSid`, ni el mensaje de error en inglés
  (solo el código).
- **Retención:** la misma que los entrantes con trámite: texto vaciado a los 12 meses
  (`ganera.retencion.texto-con-tramite`), en el job nocturno existente. Anotado para el abogado junto con los
  plazos de B1.
- **Logs:** solo `respuestaId`, `tramiteId`, `usuarioId`, SID de Twilio y código de error. Nunca el texto ni
  el teléfono (si hiciera falta, enmascarado).
- No sube la `version` del trámite (D7).

### D9. Errores de Twilio

| Caso | Estado guardado | Respuesta HTTP | Lo que ve la gestoría |
|---|---|---|---|
| Sin `TWILIO_ACCOUNT_SID`/`AUTH_TOKEN`/remitente | nada (no se crea fila) | `503 {motivo}` | «El envío por WhatsApp no está configurado. Ponte en contacto con Ganera.» |
| Twilio rechaza la petición (`ApiException`) | `FALLIDO` + código | `201` con la respuesta en `FALLIDO` | Motivo por código: 63016 fuera de ventana, 63015 no unido al sandbox, 21610 el contacto bloqueó los mensajes; otros «WhatsApp no ha aceptado el mensaje (código N)» |
| Timeout o error de red | `SIN_CONFIRMAR` | `201` | «No sabemos si ha llegado. Compruébalo antes de volver a enviarlo.» (sin reintento automático) |
| Aceptado | `ENVIADO` | `201` | «Enviado» |
| Fallo asíncrono (llega por el callback de estado, D10) | `FALLIDO` + código | — | Se ve al recargar el detalle |

El estado del **trámite** no cambia nunca por una respuesta. El texto de error se decide en Ganera por
código, nunca el mensaje en inglés de Twilio.

### D10. Callback de estado de Twilio

Muchos fallos de WhatsApp (incluido el 63016) no llegan en la llamada, sino después.

- **a) Endpoint `POST /webhooks/twilio/whatsapp/estado`** con la misma validación de firma fail-closed que el
  webhook de entrada, contra su propia URL configurada (`ganera.twilio.status-callback-url` /
  `TWILIO_STATUS_CALLBACK_URL`). Actualiza por `MessageSid`; SID desconocido → `200` sin hacer nada; estados
  solo hacia delante (`ENVIADO → ENTREGADO → LEIDO`, `FALLIDO` gana; los callbacks pueden llegar
  desordenados). Sin URL configurada se envía sin `statusCallback` y la respuesta se queda en `ENVIADO`.
- b) Sin callback: «Enviado» significaría solo «Twilio lo aceptó».

**Recomendación: a).** Es poco código (reusa la validación) y evita un fallo silencioso.

### D11. Suscripción

**Recomendación: se puede responder en cualquier estado de suscripción**, como marcar hecho en OVZ:
- La puerta de A1 (decisión 30) es solo «no aprobar»; responder no envía nada a OVZ.
- Cortar la comunicación de la gestoría con sus ganaderos perjudica al ganadero, que no tiene la culpa del impago.
- Contra: cada mensaje le cuesta dinero a Ganera, también a una gestoría que no paga. Con volumen de piloto es
  poco; se anota para el Prompt C, junto a la medición de uso (si hiciera falta, un límite diario por gestoría).

### D12. Contestaciones del ganadero a nuestra respuesta

Hoy (B1) **cada** mensaje entrante de un contacto activo crea un trámite. Si el ganadero contesta «vale,
gracias», aparece un trámite nuevo en la cola.

- **a) No se cambia en B2 reducido:** la gestoría rechaza esos trámites (la IA los dejará sin tipo). Se anota
  como limitación.
- b) Asociar al trámite anterior los mensajes que llegan dentro de X horas tras una respuesta: necesita reglas
  (¿y si es un trámite nuevo?) y cambia la recepción de B1.

**Recomendación: a)**, y anotar b) para B2 completo con datos reales de la piloto.

### D13. Dónde va en la interfaz

- **Solo en el modal de revisión** (`TramiteReviewDialog`), no en la ficha OVZ: la ficha es para copiar en OVZ
  sin distracciones. Sección «WhatsApp» debajo del mensaje original:
  - una línea con el destinatario («Juan Pérez · +34 6•• ••• •12») y el estado de la ventana;
  - historial «Respuestas enviadas» (fecha en Europe/Madrid, quién, texto, badge de estado con las variantes
    de `etiquetas.ts`);
  - botón secundario «Responder por WhatsApp» que despliega en línea (mismo patrón que la confirmación de
    Rechazar) un `Textarea` con el texto propuesto, contador de caracteres, «Enviar» y «Cancelar».
- No depende del formulario del trámite: se puede enviar con cambios sin guardar (no toca el trámite).
- Cola: sin cambios (sin columna nueva).
- Sin pase de Impeccable: patrones existentes; revisión en las capturas del smoke.

### D14. Contrato del backend

- `TramiteDetalleResponse` gana `whatsapp: { destinatarioNombre, telefonoEnmascarado, contactoActivo,
  ventanaAbiertaHasta, respuestas: [{ id, texto, estado, enviadoPorNombre, enviadoEn, motivoError }] }`
  (`null` si el trámite no es de WhatsApp). Así el modal no hace otra petición.
- `POST /tramites/{id}/respuestas-whatsapp` `{ texto, claveIdempotencia }` → `201` (nuevo) o `200` (clave
  repetida) con la respuesta. Orden: `400` texto vacío/largo o sin clave (en el controlador, antes de la BD)
  → `503` sin configurar → `404` → clave repetida (devuelve la guardada) → `409` origen no WhatsApp / estado no
  permitido / contacto inactivo / fuera de ventana → envío.
- El frontend va en el mismo commit.

---

## Tareas (Superpowers: implementador + revisor independiente cada una, TDD)

- **T1 — Backend: entidad, envío y endpoint.** `V22`, `RespuestaWhatsapp` + repositorio con `gestoriaId`
  explícito, interfaz `EnvioWhatsapp` con implementación Twilio perezosa y **un falso en todos los tests**
  (ningún test llama a Twilio; un test de arranque comprueba que el bean real no se usa en la suite), cálculo
  puro de ventana y de mapeo código → motivo (skill `test-sin-mocks-externos`), servicio en tres pasos (D7),
  endpoint, campos del detalle, retención. Regresiones obligatorias en rojo: sin `gestoriaId` en el finder,
  sin clave idempotente (doble llamada al falso), teléfono del cuerpo usado, envío dentro de la transacción
  del trámite. E2E con dos gestorías. Pruebas de la `V22` en H2 y PostgreSQL embebido.
- **T2 — Frontend** (va en el mismo commit que T1). Tipos y `api.ts`, `respuestaWhatsapp.ts` (textos propuestos, hueco obligatorio, contador),
  sección del modal, errores con el modelo `ErrorApi`, clave idempotente generada al abrir el panel y
  conservada en los reintentos, tests con MSW (doble clic → una sola petición; `409` fuera de ventana;
  `SIN_CONFIRMAR`). Actualización de los tests de honestidad sobre OVZ según D2.
- **T3 — Cierre del envío** (commit 2). `./mvnw clean test`, `npm test`, build y lint; smoke en navegador real
  con dos gestorías a 375, 420 y 1440 px (H2 en fichero, datos inventados, Twilio falso); capturas fuera del
  repo; `CLAUDE.md`, `ganera-prompts.md` y `progress.md` al día.
- **T4 — Backend: callback de estado** (commit 3, D10). Endpoint con firma `X-Twilio-Signature` fail-closed
  contra su URL configurada; busca solo por `MessageSid`; ignora cualquier otro identificador del request;
  límite de tamaño; transiciones solo hacia delante (función pura con tests) e idempotente ante repetidos;
  SID desconocido → `200` sin hacer nada. Si se alarga, se corta y pasa a B2.1.
- **Al final:** el envío real al sandbox lo hace Antonio desde su terminal a su propio móvil.

Sin commits ni push hasta el visto bueno; stage ruta a ruta desde la sesión principal.

## Configuración nueva (sin valores en el repo)

`TWILIO_WHATSAPP_FROM` (remitente, p. ej. el del sandbox) y `TWILIO_STATUS_CALLBACK_URL`. Las claves las pone
Antonio en su terminal o en la consola de Clever; nunca en el chat ni en el código.
