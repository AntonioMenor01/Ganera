# Prompt B1 — WhatsApp + IA, lo mínimo que funciona — plan

Fecha: 2026-10-05. Base: `main` = `a6fd4c1`. Suites de partida: backend 534, frontend 513.
**Backend y un ajuste mínimo de frontend en el mismo commit**, por excepción explícita de Antonio:
la cola tiene que mostrar que el trámite viene de WhatsApp y avisar cuando la IA falló.

Fuera de B1, para un **B2** aparte: respuesta a números desconocidos, varios trámites por mensaje,
adjuntos (fotos y audios), medición de uso de IA por gestoría, catálogo real de tipos OVZ y alta por
nacimiento. `TipoTramite` **no cambia** en B1.

Flujo: Superpowers (un implementador y un revisor independiente por tarea), TDD. La regla de git de
`CLAUDE.md` va copiada en cada brief: los subagentes no tocan el índice ni restauran ficheros. Hay
tests de aislamiento con dos gestorías en cada tarea de backend. La IA se simula en todos los tests;
la de verdad solo se usa en el smoke. Sin commits sin la aprobación de Antonio; stage ruta a ruta.

## Decisiones cerradas con Antonio (2026-10-05)

Visto bueno al plan. D1 sí (SDK oficial, comprobando arranque real); D2 sí; D3, D4 y D7 como se
recomiendan; D5 sí; D6 sí, configurables, y los plazos **quedan anotados para que los confirme el
abogado de Ganera junto con los DPA de Anthropic y Twilio** (notas del Prompt C); D8 sí (aviso
también en el modal). Añadidos de Antonio:

- **A1:** los tres defectos (firma con todos los parámetros y la URL pública, rechazo si falta
  `TWILIO_AUTH_TOKEN`, salida estructurada nativa) van **primero**, en la tarea T0, cada uno con su test.
- **A2:** fecha del hecho en el esquema **solo si el trámite tiene campo de fecha**. `Tramite` no lo
  tiene (contacto, explotación, tipo, estado, motivo, versión): **queda anotado para el B2**, junto
  al alta por nacimiento, que también trae fecha. El esquema de B1 no cambia.
- **A3:** mientras la extracción está pendiente (`estado_extraccion = PENDIENTE`), la cola muestra
  "Extracción en curso" (T4).
- **A4:** un número desconocido recibe `<Response/>` vacío: ni acuse ni nada (ya era así en R2; se
  fija con test).
- **A5:** cierre del smoke en este orden: vaciar la URL del sandbox, parar el túnel, comprobar que no
  responde y borrar la H2 (lleva el número de Antonio).

Antonio configura él mismo las variables de entorno y avisa.

---

## Lo que hay hoy (punto de partida)

- `TwilioWebhookController` (`POST /webhooks/twilio/whatsapp`) valida la firma, guarda un
  `MensajeCampo` si el `MessageSid` es nuevo y responde `200` vacío. No crea trámites ni llama a la IA.
- `TramiteExtractionService` es una interfaz; `AnthropicTramiteExtractionService` lanza
  `UnsupportedOperationException`.
- `MensajeCampo` no extiende `GestoriaScopedEntity` (`gestoria_id`, `contacto_id` y `tramite_id`
  admiten null). `EstadoTramite.PENDIENTE_EXTRACCION` existe y el frontend ya tiene su etiqueta.

### Tres defectos encontrados al leer el código (se arreglan en B1)

1. **La firma de Twilio fallaría siempre con Twilio de verdad.** El validador solo mete `MessageSid`,
   `From` y `Body`, pero Twilio firma **todos** los parámetros del POST (`AccountSid`, `To`,
   `NumMedia`, `ProfileName`, `WaId`…). Además usa `request.getRequestURL()`, que detrás de un túnel
   es `http://localhost:8080/...`, no la URL `https://` que Twilio firmó. Arreglo: validar con todos
   los parámetros del formulario y con una URL pública configurada (`TWILIO_WEBHOOK_URL`).
2. **La validación falla en abierto.** Con `TWILIO_AUTH_TOKEN` vacío no se valida nada y se guarda
   cualquier POST. Arreglo: sin token o sin URL, `503` y no se guarda nada (fail-closed, como
   `ONBOARDING_SECRET`).
3. **La extracción con Spring AI no sería salida estructurada de verdad.** He mirado el jar de
   `spring-ai-anthropic` 1.0.3: la petición no tiene `output_config` (ni `tool_choice`), así que
   `.entity(...)` se limita a añadir instrucciones de formato al prompt y parsear la respuesta. Eso
   contradice lo que dice `CLAUDE.md` ("never relying on prompt instructions alone"). Haiku 4.5 sí
   admite salida estructurada nativa (`output_config.format` con JSON Schema, GA, sin cabecera
   beta), pero hay que pedirla con el SDK oficial `com.anthropic:anthropic-java`. Ver D1.

Además, `MensajeCampo.cuerpo` tiene `@Lob` sobre una columna `TEXT`. Ya estaba anotado en
`progress.md` (falla con `ddl-auto=validate` en H2) y en PostgreSQL leer un `@Lob String` puede dar
problemas. Se quita el `@Lob`.

---

## Recomendaciones que pediste

### R1 — Recibir el webhook en local para el smoke (túnel temporal) y cerrarlo

**Recomiendo un Cloudflare Quick Tunnel** (`cloudflared`):
- no pide cuenta ni token;
- da una URL `https://<aleatorio>.trycloudflare.com` que muere al cerrar el proceso;
- no deja nada configurado en ninguna cuenta.

ngrok también serviría, pero exige cuenta y guardar un authtoken en el equipo. Hoy no hay ninguno de
los dos instalado.

Instalación, una vez: `winget install --id Cloudflare.cloudflared`.

Durante el smoke:
1. Backend en `:8080` sobre H2 en fichero, solo con datos inventados.
2. `cloudflared tunnel --url http://localhost:8080`; copias la URL que imprime.
3. Antes de arrancar el backend, pon esa URL en `TWILIO_WEBHOOK_URL` **solo para esa terminal**
   (`$env:TWILIO_WEBHOOK_URL = "https://…/webhooks/twilio/whatsapp"`). No es un secreto.
4. Consola de Twilio → Messaging → Try it out → *Send a WhatsApp message* → *Sandbox settings* →
   "When a message comes in": la misma URL, método `POST`.

Riesgo, dicho claro: el túnel expone **todo** el backend, no solo el webhook (login, registro y
onboarding interno). Por eso la H2 lleva solo datos inventados, el `ONBOARDING_SECRET` y el
`JWT_SECRET` del smoke son aleatorios y de usar y tirar, y el túnel se abre solo mientras dura el smoke.

Cierre seguro, en este orden:
1. En la consola de Twilio, deja vacía la URL del sandbox. Así Twilio deja de mandar mensajes a una
   URL que alguien podría reutilizar: los subdominios `trycloudflare` son aleatorios, pero no son tuyos.
2. Para `cloudflared` por su PID (`Stop-Process -Id …`) y comprueba que no queda ninguno
   (`Get-Process cloudflared` → nada).
3. Comprueba desde fuera que la URL ya no responde (`curl` → error 530/1033 de Cloudflare).
4. Para el backend por el PID del `java.exe` hijo (el footgun de Windows de siempre) y borra la H2
   en fichero y los Excel del smoke.
5. Opcional: `winget uninstall Cloudflare.cloudflared`.

### R2 — Síncrono en el webhook o en segundo plano

**Recomiendo segundo plano con una cola en la propia base de datos**, no `@Async` ni una cola en memoria:

- **En el webhook, síncrono, en una sola transacción corta (milisegundos):**
  1. validar la firma;
  2. normalizar el teléfono;
  3. guardar el `MensajeCampo` (idempotencia);
  4. resolver el Contacto;
  5. crear el Trámite en `PENDIENTE_EXTRACCION` con el texto en bruto;
  6. confirmar la transacción y responder con el acuse.

  Antes de llamar a la IA, el mensaje ya está guardado y el trámite ya existe.
- **En segundo plano:** un `@Scheduled` (cada 5 s; ya hay `@EnableScheduling`) busca los trámites
  con extracción pendiente y con `proximo_intento_extraccion <= now()`. Para cada uno llama a la IA
  **fuera de cualquier transacción y sin bloqueo de fila**, y después aplica el resultado en una
  transacción corta con `findConBloqueoByIdAndGestoriaId`. Es el mismo principio que la nota M2
  para el 3c: nunca se tiene un bloqueo mientras dura una llamada de red.

Por qué no síncrono: Twilio corta a los 15 s, la IA tarda entre 1 y 5 s (más con reintentos), y un
fallo de la IA no puede tumbar la recepción.

Por qué no `@Async` en memoria: si el proceso se reinicia, lo que estaba en cola se pierde. Con la
cola en la BD, un reinicio a mitad de llamada deja el trámite pendiente y el planificador lo
recoge otra vez. La IA se llama *al menos una vez*, y aplicar el resultado es idempotente porque se
comprueba el estado bajo bloqueo.

El planificador es un job sin petición HTTP, como `SuscripcionSyncScheduler`: no hay
`gestoriaFilter`. Recorre trámites de todas las gestorías (es intencional), pero cada operación
sobre un trámite usa su `gestoriaId` explícito en todas las consultas.

**Reintentos (D4):**
- Al **primer** fallo, el trámite pasa a `PENDIENTE_REVISION` con `estado_extraccion = FALLIDA`, el
  texto en bruto y el aviso. Así se ve enseguida en la cola, que por defecto filtra por pendientes.
- Se reintenta a 1 min, 5 min y 30 min (4 intentos en total).
- Si un reintento acierta **y nadie ha tocado el trámite** (sigue en `PENDIENTE_REVISION` con la
  misma `version` que dejó el fallo), se aplica el resultado y se quita el aviso. Si alguien lo
  editó, lo aprobó o lo rechazó, el resultado se descarta y se deja de reintentar: nunca se pisa
  una corrección humana.
- Si un empleado tiene abierto el modal mientras se aplica un reintento, al guardar recibe el `409`
  de siempre ("se recarga y muestra el motivo"). Es el comportamiento que ya existe para escrituras
  concurrentes.

**Acuse:** va en la propia respuesta del webhook, en TwiML
(`<Response><Message>Recibido, tu gestoría lo revisará.</Message></Response>`). No hace falta llamar
a la API de Twilio para enviar, ni `TWILIO_ACCOUNT_SID`, y el acuse sale solo si la transacción se
confirmó. A un número desconocido, a un Contacto inactivo y a un `MessageSid` repetido se responde
`<Response/>` vacío: sin respuesta, y sin segundo acuse en los duplicados.

### R3 — Esquema de salida de la IA y campos dudosos o vacíos

La IA recibe **solo el texto del mensaje**: ni inventario, ni explotaciones, ni nombres, ni teléfono.
El prompt de sistema le pide copiar los identificadores tal como están escritos, sin completarlos ni
inventarlos, y tratar el mensaje como datos, no como instrucciones (defensa contra la inyección de
prompt). No hay herramientas. Esquema (salida estructurada nativa, `additionalProperties: false`):

```json
{
  "tipoTramite": "ALTA | BAJA | CENSO | MOVIMIENTO | DEMORA | NO_IDENTIFICADO",
  "crotales": ["string, tal como lo escribió el ganadero"]
}
```

- **Sin campo de "confianza":** ningún umbral se salta la aprobación, y la confianza que declara un
  modelo pequeño no está calibrada. Lo dudoso se expresa como "no sé".
- **Tipo dudoso:** `NO_IDENTIFICADO`, que se guarda como `tipoTramite = null`. Es un valor
  explícito en vez de `null`, para que "no lo sé" sea una respuesta y no un hueco. La regla de
  aprobar ya bloquea un trámite sin tipo.
- **Crotales vacíos:** el trámite queda sin crotales. Es válido, porque los crotales no son obligatorios.
- **Validación en el servidor, siempre** (las restricciones de longitud y número no se pueden
  expresar en la salida estructurada):
  - cada crotal pasa por `CrotalNormalizador`;
  - los inválidos (3 dígitos o menos, caracteres raros, más de 30) se descartan y se cuentan en
    `crotales_descartados`, y la cola avisa: "Hay N identificadores que no parecen crotales; revisa
    el mensaje" (D5);
  - como mucho 50 crotales: de ahí en adelante, se descartan y cuentan igual;
  - después se resuelven contra la explotación del trámite con `TramiteCrotalService`;
  - si dos resuelven al mismo Animal, se queda el primero (decisión 23 de A1).
- **Fallo de IA:** error de red, 5xx o 429, clave inválida, `stop_reason` `refusal` o `max_tokens`,
  JSON que no valida. Todos cuentan igual como fallo y van a reintento. `max_tokens`: 1024.
- **Mensaje sin texto** (solo foto o audio; los adjuntos son de B2): no se llama a la IA. El
  trámite queda en `PENDIENTE_REVISION` con `estado_extraccion = SIN_TEXTO` y su propio aviso
  ("El mensaje no tiene texto; puede traer una foto o un audio, que todavía no se procesan").

### R4 — Qué se guarda del mensaje y cuánto tiempo (RGPD)

**Se guarda** (en `mensaje_campo`):
- `MessageSid`;
- el teléfono de origen normalizado;
- el texto;
- `NumMedia` (solo el número, sin URLs de adjuntos);
- la fecha de recepción;
- el resultado (`TRAMITE_CREADO`, `NUMERO_DESCONOCIDO`, `CONTACTO_INACTIVO`);
- los enlaces a la gestoría, el contacto y el trámite.

**No se guarda:**
- `ProfileName` (el nombre de perfil de WhatsApp);
- `WaId`;
- `AccountSid`;
- las URLs de adjuntos;
- el formulario completo.

**Logs:** nunca el texto ni el teléfono completo (enmascarado: `+34*****123`); nunca claves ni
firmas; solo ids y resultados.

**Números desconocidos:** se guardan en la misma tabla con `gestoria_id = null`. Ninguna gestoría
puede verlos: el único endpoint que lee `mensaje_campo` es el detalle del trámite, y busca por
`tramiteId`. Están "aparte" de todo lo que ve la gestoría.

**Retención (D6), con un job nocturno:**
- Mensajes de número desconocido o de contacto inactivo: se **borran** a los **30 días**.
- Mensajes con trámite: el texto se **vacía** a los **12 meses** de recibirse (queda "[texto
  eliminado por antigüedad]" y los metadatos). El trámite y sus datos estructurados se quedan,
  porque son el registro del trabajo de la gestoría.

Estos plazos los decide el negocio y conviene que los confirme un asesor de protección de datos.
Los pongo como valores configurables por propiedad.

**Fuera del código, pero necesario antes del piloto (anotado para C):**
- Ganera es encargada del tratamiento de la gestoría. Anthropic y Twilio son subencargados: hacen
  falta sus DPA y mencionarlos en el contrato con la gestoría.
- La API de Anthropic no entrena con estos datos por defecto, pero los retiene un tiempo limitado
  según su política.
- Twilio guarda sus propios logs de mensajes; hay que revisar su retención en la consola.

---

## Decisiones para Antonio

- **D1 — SDK de IA:** recomiendo cambiar `spring-ai-starter-model-anthropic` por el SDK oficial
  `com.anthropic:anthropic-java`, para tener salida estructurada nativa (`outputConfig(Clase.class)`)
  con el modelo fijo `claude-haiku-4-5-20251001`. Solo lo usa `AnthropicTramiteExtractionService`,
  que queda detrás de la misma interfaz. Riesgo: choques de dependencias (OkHttp, Jackson y Kotlin
  stdlib frente a Boot 3.4.1 y Twilio), como ya pasó con `commons-io`. El implementador lo
  comprueba con `dependency:tree` y arrancando la app de verdad. La alternativa es quedarse con
  Spring AI y aceptar instrucciones en el prompt más una validación estricta en el servidor; en ese
  caso habría que corregir la frase de `CLAUDE.md`.
- **D2 — Explotación:** si el Contacto activo tiene **exactamente una** explotación enlazada en su
  gestoría, se asigna. Con cero o varias, `null` (y se asigna a mano, como dice el flujo). En B1 la
  IA no recibe códigos REGA para desambiguar.
- **D3 — Columnas nuevas (V19, SQL):**
  - en `tramite`: `origen` (`WHATSAPP`, null en las filas antiguas), `estado_extraccion`
    (`PENDIENTE`, `COMPLETADA`, `FALLIDA`, `SIN_TEXTO`, null en las antiguas),
    `intentos_extraccion`, `proximo_intento_extraccion`, `version_tras_fallo` y
    `crotales_descartados`, más un índice para el planificador;
  - en `mensaje_campo`: `resultado`, `num_media` y un índice por `created_at` para la purga.

  `TramiteResponse` y `TramiteDetalleResponse` ganan `origen`, `estadoExtraccion` y
  `crotalesDescartados`. El error técnico de la IA solo va a los logs; nunca se muestra.
- **D4 — Reintentos:** 1, 5 y 30 min (4 intentos), configurables. Al primer fallo, el trámite pasa
  a revisión con aviso, como en R2.
- **D5 — Aviso de crotales descartados:** sí, con un contador (es barato y evita fiarse de una
  lista incompleta). Si no lo quieres, se quita la columna.
- **D6 — Retención:** 30 días y 12 meses, configurables, con job nocturno en B1. Ver R4.
- **D7 — Textos visibles:**
  - acuse: "Recibido, tu gestoría lo revisará." (tal cual lo diste);
  - indicador de origen en la cola: icono de WhatsApp + texto oculto "Recibido por WhatsApp";
  - aviso de fallo: "No se ha podido extraer automáticamente. Revisa el mensaje original.";
  - sin texto: el de R3.
- **D8 — Alcance del frontend:** el indicador de origen y el aviso en la cola, y el **mismo aviso
  dentro del modal de revisión** (en `AvisosRevision`). Es donde se corrige, y no tiene sentido
  que el aviso desaparezca al abrirlo. El pase de diseño va por Impeccable, como manda `CLAUDE.md`.
  Nada más del frontend cambia.

Regla que se mantiene: un mensaje **nunca** crea un trámite aprobado ni llama a OVZ.net. Todo
trámite de WhatsApp entra en `PENDIENTE_EXTRACCION` y pasa a `PENDIENTE_REVISION`. Una gestoría
`SUSPENDIDA` sigue recibiendo trámites (el bloqueo es solo al aprobar, decisión 30 de A1).

---

## Tareas

### T0 — Los tres defectos, primero (backend; añadido A1)

1. **Firma con todos los parámetros y la URL pública.** Se valida con todos los parámetros del
   formulario (`request.getParameterMap()`) y con `ganera.twilio.webhook-url` (`TWILIO_WEBHOOK_URL`),
   nunca con `getRequestURL()`. Test: firma HMAC-SHA1 calculada en el test según la especificación
   pública de Twilio (URL + parámetros ordenados por nombre, clave = auth token, Base64), con
   parámetros extra (`AccountSid`, `To`, `NumMedia`, `ProfileName`, `WaId`) → aceptada; un parámetro
   alterado, o la firma de otra URL → `403` y nada guardado. `Body` pasa a opcional (un mensaje solo
   con foto no lo trae).
2. **Fail-closed sin configuración.** `TWILIO_AUTH_TOKEN` o `TWILIO_WEBHOOK_URL` vacíos → `503`, nada
   guardado, un `warn` sin datos del mensaje. Firma ausente o inválida → `403`. La app sigue
   arrancando sin ellos (footgun de Twilio). Test de cada caso.
3. **Salida estructurada nativa.** Se sustituye `spring-ai-starter-model-anthropic` por
   `com.anthropic:anthropic-java` (D1). `AnthropicTramiteExtractionService` pide `output_config.format`
   (JSON Schema derivado de un record con `tipoTramite` enum incl. `NO_IDENTIFICADO` y `crotales`),
   modelo fijo `claude-haiku-4-5-20251001`, `max_tokens` 1024, sin herramientas. Cliente perezoso: la
   app arranca sin `ANTHROPIC_API_KEY`. `refusal`/`max_tokens`/JSON inválido → excepción propia
   (`ExtraccionFallidaException`). Test **sin mocks del SDK**: un servidor HTTP local del JDK
   (`com.sun.net.httpserver.HttpServer`) hace de API; el SDK real apunta a él por `baseUrl`; el test
   comprueba que el cuerpo enviado lleva `output_config.format` con el esquema y el modelo fijado, que
   una respuesta válida se convierte en el record, y que `refusal`, `max_tokens`, un 500 y un JSON
   que no valida acaban en `ExtraccionFallidaException`. `dependency:tree` sin choques (Jackson,
   OkHttp, Kotlin) y **arranque real** de la app (H2) sin `ANTHROPIC_API_KEY`.

Fuera de T0: crear trámites, el planificador, V19. El webhook sigue solo guardando `MensajeCampo`.

### T1 — Recepción: webhook, idempotencia, enrutado y acuse (backend)

- V19 (la parte de `mensaje_campo` y `tramite`), entidades y `@Lob` fuera.
- Firma: la de T0.
- `MensajeEntranteService` (`@Transactional`):
  - inserta el `MensajeCampo` con `saveAndFlush`; un duplicado se detecta por la violación del
    `UNIQUE(message_sid)`, que se propaga y deshace todo, y el controlador la captura (patrón
    `RegistroGestoriaController`), además de la comprobación previa barata;
  - normaliza `From` con `TelefonoNormalizador`;
  - resuelve con `ContactoRepository.findByTelefono`, el único uso permitido;
  - si el Contacto está inactivo, no se crea trámite;
  - el Trámite se crea en la gestoría **del Contacto**, con la explotación de D2 y todos los
    finders con `gestoriaId`.
- Respuesta TwiML (`text/xml`): acuse o `<Response/>`.
- Tests:
  - firma calculada en el test con HMAC-SHA1 real siguiendo la especificación de Twilio, sin mocks
    (skill `test-sin-mocks-externos`);
  - duplicado y carrera de duplicado;
  - número desconocido, Contacto inactivo, cuerpo vacío;
  - E2E `@SpringBootTest` con dos gestorías: el mensaje del contacto de A crea el trámite solo en
    A, B no lo ve en `GET /tramites` y recibe `404` en `GET /tramites/{id}`, y el número
    desconocido no crea nada en ninguna;
  - el controlador nunca escribe el texto ni el teléfono en los logs (test con un appender de captura).

### T2 — Extracción en segundo plano con reintentos (backend)

- D1 (dependencia y `AnthropicTramiteExtractionService`, con el cliente construido de forma perezosa
  para que la app arranque sin `ANTHROPIC_API_KEY`, como los footguns de Twilio y Stripe).
- `ExtraccionValidador` (puro): esquema → tipo y lista de crotales normalizados + descartados.
- `ExtraccionTramiteScheduler` + `ExtraccionTramiteService` (aplicar bajo bloqueo, reintentos y
  "no pisar lo editado").
- Tests:
  - con `TramiteExtractionService` falso y programable (devuelve un resultado o lanza);
  - éxito, fallo → `PENDIENTE_REVISION` + `FALLIDA`, reintento que acierta sobre un trámite sin
    tocar, reintento descartado tras una edición, agotar intentos, deduplicar por Animal, descartados;
  - aislamiento: un Animal de **B** con los mismos últimos dígitos nunca se enlaza a un trámite de
    A, aunque el planificador procese trámites de las dos en la misma pasada.

### T3 — Respuestas y retención (backend)

- `origen`, `estadoExtraccion` y `crotalesDescartados` en las dos respuestas.
- Job nocturno de retención (D6), con sus tests (incluido que no borra mensajes de otras
  categorías ni antes de plazo).

### T4 — Frontend mínimo (excepción explícita)

- Tipos, indicador de origen y aviso en la fila de la cola, aviso en `AvisosRevision` (D8), desde
  `etiquetas.ts`. Con `estadoExtraccion = PENDIENTE`, la fila muestra "Extracción en curso" (A3).
- Tests con MSW. Pase de Impeccable sobre la fila y el aviso. `DESIGN.md` al día.

**Brief de Impeccable (shape), confirmado por Antonio el 2026-10-05.** Se queda en el mundo actual: sin
colores nuevos y nunca Rojo Acción en un aviso.
- **Cola, origen:** un icono de bocadillo de 14 px en Gris Texto junto al `#id`, fuera del botón (que tiene
  su propio `aria-label`), con texto oculto "Recibido por WhatsApp" y `title`. No es un badge: los badges
  son estados.
- **Cola, extracción:** una línea de 12 px bajo el badge de Estado:
  - `PENDIENTE` → "Extracción en curso", en Gris Texto y sin icono;
  - `FALLIDA` → icono de aviso + "No se ha podido extraer", en el ámbar del par de aviso;
  - `SIN_TEXTO` → icono de aviso + "Mensaje sin texto", en ámbar.
  En la celda de Crotales, si `crotalesDescartados > 0`, icono de aviso + "N descartados" (con singular),
  en ámbar.
- **Modal:** avisos fijos en la zona de avisos (`AvisosRevision`), sin botón de cerrar, porque describen
  los datos y no el resultado de una acción. Van en el ámbar del par de aviso, no en rojo: no es un error
  del usuario, es trabajo pendiente.
  - `FALLIDA`: "No se ha podido extraer automáticamente. Revisa el mensaje original."
  - `SIN_TEXTO`: "El mensaje no tiene texto; puede traer una foto o un audio, que todavía no se procesan."
  - Descartados: "Hay N identificadores que no parecen crotales; revisa el mensaje." (con singular)
  - `PENDIENTE`: "Extracción en curso", neutro.
- **Visibilidad:** solo mientras el trámite está pendiente (`PENDIENTE_EXTRACCION` o `PENDIENTE_REVISION`).
  En un trámite aprobado o rechazado desaparecen.
- **Etiquetas:** en `etiquetas.ts`, como el resto.
- **Sin refresco automático:** "Extracción en curso" cambia al recargar, como hoy.

### T5 — Cierre

- `./mvnw clean test`, `npm test`, `npm run build`, `npm run lint`.
- **Smoke real** con el sandbox de Twilio desde tu móvil, H2 en fichero y datos inventados
  (gestorías A y B):
  1. **Mensaje válido:** tu móvil es un Contacto de A (importado con la hoja Contactos). Envías
     "baja del 1234" → acuse en el móvil → trámite en la cola de A con el indicador de WhatsApp,
     tipo y crotal resueltos → nada en B.
  2. **Duplicado:** se reenvía el mismo POST con el mismo `MessageSid` y una firma válida calculada
     por un script del scratchpad que lee `TWILIO_AUTH_TOKEN` del entorno sin imprimirlo. Twilio no
     reenvía a voluntad. Resultado esperado: `<Response/>`, sin un segundo trámite ni un segundo acuse.
  3. **Número desconocido:** se cambia el teléfono de tu Contacto por uno inventado
     (`PUT /contactos/{id}`) y envías otro mensaje → sin respuesta, sin trámite en A ni en B,
     `mensaje_campo` con `NUMERO_DESCONOCIDO`.
  4. **Fallo de la IA:** se arranca el backend con `$env:ANTHROPIC_API_KEY = "invalida"` solo en esa
     terminal (no toca tu variable de usuario) → mensaje → acuse → trámite en revisión con el aviso
     → se reinicia con la clave real → el reintento lo completa y el aviso desaparece.
- Cierre del túnel (R1), procesos parados por PID, H2 y Excel borrados.
- `CLAUDE.md`, `ganera-prompts.md` y `progress.md` al día.
- Stage ruta a ruta y lista para Antonio.

---

## Credenciales: dónde y cómo, sin pasármelas

Solo como **variables de entorno de usuario de Windows**: nunca en el repo, en `.env` (aunque está
ignorado), en logs ni en el chat.

1. Win+R → `rundll32 sysdm.cpl,EditEnvironmentVariables` → "Variables de usuario" → *Nueva*. Con
   la ventana gráfica no queda nada en el historial de PowerShell (`setx` desde la consola sí
   dejaría el valor en el historial de PSReadLine).
   - `ANTHROPIC_API_KEY`: crea en console.anthropic.com una clave **solo para Ganera dev**, con
     límite de gasto mensual bajo. Así, si se filtra, se revoca sin afectar a nada más.
   - `TWILIO_AUTH_TOKEN`: Twilio Console → Account Info. Sirve para validar la firma.
   - `TWILIO_ACCOUNT_SID`: no hace falta en B1 (el acuse va en TwiML). Puedes ponerlo o no.
   - `TWILIO_WEBHOOK_URL`: **no** va aquí. Cambia en cada túnel y se pone solo en la terminal del smoke.
2. Cierra y vuelve a abrir VS Code (y sus terminales) para que las vea.
3. Para comprobarlo yo sin ver el valor, ejecutaré solo esto, que imprime `True` o `False`:
   `[bool]$env:ANTHROPIC_API_KEY; [bool]$env:TWILIO_AUTH_TOKEN`.
4. Al terminar el piloto o si sospechas una fuga: revocar la clave en Anthropic y rotar el auth
   token en Twilio.
