# Plan — WhatsApp: un número por gestoría (fase 1)

**Fecha:** 2026-11-16 · **Rama base:** `main` = `13ae210` (#33, crotal-madre) · **Rama nueva:** pendiente (no crear hasta el OK)
**Flujo:** Superpowers (un implementador + un revisor independiente por tarea). **TDD.**
**Migración:** `V31` (confirmada libre: la última es `V30`). Común H2 + PostgreSQL.

> Escrito para: implementador y revisor de cada tarea (y Antonio). Sin código hasta el OK de Antonio.

---

## Decisiones cerradas (de Antonio, 2026-11-16)

1. **Un número por gestoría siempre** (el suyo propio o uno que damos nosotros), **nunca compartido entre gestorías nuevas**. La gestoría se resuelve **por el `To`** (destino).
2. El **modo COMPARTIDO actual se mantiene solo para lo existente y las pruebas**; ninguna gestoría nueva entra por él.
3. **Fase 1:** todos los números bajo **la WABA de Ganera**, una **sola cuenta de Twilio**, **un solo token**.
4. **Plantillas comunes de Ganera.**
5. **Se aplaza** `UNIQUE(gestoria_id, telefono)`: el teléfono del contacto **sigue siendo `UNIQUE` global** en fase 1.
6. **Números configurados internamente** (patrón del onboarding: `X-Internal-Secret`).
7. **Los trámites existentes se responden por el número compartido actual** (`ganera.twilio.whatsapp-from`).
8. **El `To` se guarda en `mensaje_campo` y en `tramite`.**
9. **Auditoría:** el **id de `numero_whatsapp` (Long)**, nunca el teléfono. Declarar conscientemente cualquier campo nuevo con teléfono en `InventarioLecturasTelefonoTest`.

**Firma de Twilio (consecuencia de la decisión 7):** una sola cuenta y un solo `auth-token`, todos los números apuntando a la misma `TWILIO_WEBHOOK_URL` → **la validación de firma NO cambia** (`TwilioWebhookController` sigue con un token y una URL). No hay multi-token en fase 1.

---

## Modelo de datos (lo que introduce `V31`)

**Tabla `numero_whatsapp`** — **solo números DEDICADO** (ajuste D-d de Antonio: el COMPARTIDO no se siembra, se identifica por configuración, ver más abajo). Registro/config; **no** `GestoriaScopedEntity` (el webhook la consulta sin tenant), pero **sí** con gestoría siempre:
- `id` BIGINT PK
- `numero` VARCHAR(30) **UNIQUE NOT NULL** — el número de WhatsApp (el `To`), **normalizado E.164** con `contacto.TelefonoNormalizador` (quita el prefijo `whatsapp:`).
- `gestoria_id` BIGINT **NOT NULL** FK → `gestoria` (sin `ON DELETE`) — la gestoría dueña. (Al ser todos DEDICADO, nunca `NULL`; por eso la auditoría del alta siempre tiene gestoría.)
- `activo` BOOLEAN NOT NULL DEFAULT true.
- `created_at` TIMESTAMP NOT NULL (`Instant` del `Clock`).

(No hay columna `tipo` ni enum `TipoNumeroWhatsapp`: la tabla es por definición DEDICADO; COMPARTIDO es un caso de configuración, no una fila.)

**Columnas nuevas:**
- `mensaje_campo.telefono_destino` VARCHAR(30) **NULL** — el `To` normalizado que llamó Twilio (incluso si el número no está registrado); null en mensajes anteriores. Nunca sale en un DTO de respuesta; en logs va enmascarado.
- `tramite.numero_whatsapp_id` BIGINT **NULL** FK → `numero_whatsapp` (sin `ON DELETE`) — el número **por el que entró** el trámite. `NULL` = trámite COMPARTIDO/legacy → se responde por el número compartido (decisión 7). Guardar la FK (y no el `To` crudo) cubre a la vez «guardar el To en tramite», el **Long para auditoría** (decisión 9) y la **selección del remitente** al responder. **Invariante (ajuste de Antonio):** si `numero_whatsapp_id` no es `NULL`, la fila `numero_whatsapp` referida pertenece a **la misma gestoría** que el trámite (la bifurcación lo garantiza: en DEDICADO la gestoría del trámite ES la dueña del número). Cubierto por un test + una mutación (ver T2).

**Recuentos de migración tras `V31`** (común, sin trozo PG-only): PostgreSQL **31** (V1–V31), H2 **30** (hueco en V26: V1–V25, V27–V31). Ajustar los **5 tests** (ajuste de Antonio): `FlywayPostgresTest`, `FlywayH2VendorTest`, `SeguridadEsquemaPostgresTest`, `AuditoriaInmutablePostgresTest`, `PerfilCleverPostgresTest`. **`SeguridadEsquemaPostgresTest` ya cubre `numero_whatsapp`** por su bucle genérico sobre todas las tablas (RLS + política `ganera_app_todo` + privilegios `rwid` de `ganera_app`), al ser tabla de negocio normal (no «solo inserción»); solo hay que subir el recuento (línea del `count(*) from flyway_schema_history` 30→31). El callback de seguridad la protege sola (no se toca).

**Consulta sin tenant nueva:** `NumeroWhatsappRepository.findByNumero(String)` — la consulta deliberadamente sin scope de tenant, junto a `ContactoRepository.findByTelefono` y las dos por SID de `RespuestaWhatsappRepository`. Documentarla igual (Javadoc). **Guardia (ajuste de Antonio):** hoy **no existe** una guardia global que impida llamar a `findByTelefono` desde otro sitio — solo hay un `verify(...).never()` por llamador en `ExplotacionImportServiceTest`. Hay que **crear** una guardia (p. ej. `ConsultasSinTenantTest`, por reflexión/escaneo como `InventarioAuditoriaTest`) que compruebe que **ningún** otro bean de producción referencia `findByNumero` salvo `MensajeEntranteService` (la vía del webhook), y extenderla a `findByTelefono`. Mecanismo a elección del implementador (no hay ArchUnit en el classpath; se puede escanear el bytecode/fuentes como ya hace el inventario). Ver T2.

---

## Resolución de la gestoría (lógica nueva en `MensajeEntranteService`)

El webhook lee `To` (hoy se ignora) y lo pasa en `MensajeEntrante`. En el servicio, tras normalizar `To` con `TelefonoNormalizador` (quita `whatsapp:`), **en este orden** (ajuste D-d de Antonio — el COMPARTIDO se identifica por configuración, no por una fila):

1. **`To` == `ganera.twilio.whatsapp-from` actual** (comparados ambos normalizados a E.164) → **COMPARTIDO = flujo de hoy**: `findByTelefono(from)` (global). `tramite.numeroWhatsapp` = `NULL` (no hay fila; se responde por el compartido, decisión 7).
2. Si no, **`NumeroWhatsappRepository.findByNumero(toNormalizado)` presente** → **DEDICADO**: gestoría = la dueña del número. Contacto por **`findByGestoriaIdAndTelefono(gestoriaId, from)`** (scoped, **no** `findByTelefono`). No encontrado → `NUMERO_DESCONOCIDO` (sin trámite). Encontrado y activo → trámite en esa gestoría, `tramite.numeroWhatsapp` = esa fila.
3. Si **ninguno de los dos** (ni el compartido ni una fila DEDICADO), o `To` ausente → **desconocido**, **como hoy**: sin gestoría, se guarda `mensaje_campo` con `telefono_destino`, resultado `NUMERO_DESCONOCIDO`, **sin fila de auditoría** (no hay gestoría a la que colgarla — exactamente el comportamiento actual), TwiML vacío. **Añadido (D-a):** un `log.warn` **sin el número** avisando de que llegó un mensaje a un `To` no registrado ni compartido (para detectar números mal dados de alta).

En todos los caminos se guarda `mensaje_campo.telefono_destino = toNormalizado` (D-c).

**Aislamiento (clave):** un contacto de X que escribe al número **DEDICADO** de Y → la gestoría es Y (por el `To`), el contacto se busca **dentro de Y** con `findByGestoriaIdAndTelefono`, no está → **desconocido para Y**, sin trámite, **sin fuga cross-tenant**. El `To` va firmado por Twilio, así que es de fiar una vez validada la firma.

---

## Selección del remitente al responder (`RespuestaWhatsappService` + `TwilioEnvioWhatsapp`)

- `EnvioWhatsapp.enviar(...)` pasa a recibir el **remitente** (`from`): `enviar(String from, String telefonoE164, String texto)`.
- El servicio resuelve `from` = `tramite.numeroWhatsapp?.numero` (→ `whatsapp:+…`), y si es `null` (legacy) → el **`ganera.twilio.whatsapp-from`** configurado (decisión 7).
- `configurado()` = `account-sid` + `auth-token` presentes (el `from` se resuelve por envío; el `whatsapp-from` global se mantiene como fallback). Si no hay `from` resoluble → `Rechazado` (y el endpoint responde como hoy). `ClasificadorEnvioWhatsapp.normalizarRemitente` se reutiliza para el `from` por envío.
- Estado/versión del trámite no cambian. Auditoría de la respuesta puede añadir el id de `numero_whatsapp` usado (opcional, Long).

---

## Auditoría (decisión 9)

- **Entrante:** `TRAMITE_CREADO` (ya existe, actor `WEBHOOK_TWILIO`) añade `Clave.NUMERO_WHATSAPP_ID` (**Long**, nunca el teléfono).
- **Alta interna de número DEDICADO (escritura auditada, ajuste de Antonio):** `AccionAuditoria.NUMERO_WHATSAPP_REGISTRADO` (nueva) + `EntidadAuditada.NUMERO_WHATSAPP` (nueva), actor `SISTEMA` (endpoint por secreto, sin JWT), `gestoria_id` = la dueña, `entidadId` = id del número; **sin el teléfono en texto** (solo el id Long; `DatosAuditoria` ya impide `String` libre). Declararla **auditada** en `InventarioAuditoriaTest`, y su E2E lleva `@ExtendWith(GuardiaDmlExtension)` (toda escritura de negocio deja su fila `insert into auditoria`).
- **Enums nuevos:** `AccionAuditoria.NUMERO_WHATSAPP_REGISTRADO`, `EntidadAuditada.NUMERO_WHATSAPP`, `Clave.NUMERO_WHATSAPP_ID` (Long). **No** hay `TipoNumeroWhatsapp` (D-d) ni `ResultadoMensaje` nuevo (D-a: se reutiliza `NUMERO_DESCONOCIDO`).
- **Teléfonos:** `mensaje_campo.telefono_destino` es columna de entidad, **no** un DTO de respuesta — no dispara `InventarioLecturasTelefonoTest`, pero **no debe exponerse** en ninguna respuesta en fase 1. Si en el futuro algún `*Response` lleva un campo de teléfono (p. ej. «número por el que entró»), **declararlo** en esa guardia. El valor del número **nunca** va a `antes`/`despues` (solo el id Long; `DatosAuditoria` ya impide `String` libre).

---

## Tareas (TDD, flujo Superpowers)

> **Regla para TODOS los revisores (Antonio):** **no** ejecutar la suite Maven completa. Las comprobaciones de mutación se hacen con **`-Dtest` dirigido** (p. ej. `./mvnw clean test -Dtest=MensajeEntranteServiceTest`). La **suite completa se ejecuta una sola vez al final** (T5 / verificación final). Los subagentes **no** tocan el índice de git (sin `add/rm/checkout/reset/stash/commit/push`); las mutaciones se restauran con `cp` + `cmp` desde una copia previa.

### T1 — Registro `numero_whatsapp` + columnas de destino (datos, sin comportamiento)
Migración `V31` (tabla + las dos columnas), entidad `NumeroWhatsapp` (no `GestoriaScopedEntity`; `gestoria` **NOT NULL**; sin enum `tipo`), `NumeroWhatsappRepository.findByNumero` (unscoped, Javadoc como `findByTelefono`), mapeo de `telefonoDestino` en `MensajeCampo` y de `numeroWhatsapp` (`@ManyToOne(LAZY)`) en `Tramite`.
**Tests (primero):** `@DataJpaTest` del repo (alta y lookup por numero; `numero` UNIQUE; `gestoria_id` obligatorio); recuentos de migración actualizados (PG 31 / H2 30) en los tests existentes; persistencia de las dos columnas nuevas. **Sin** cambio de comportamiento del webhook todavía.
**Revisor:** `-Dtest` sobre el test del repo y los de recuento.

### T2 — Resolución por `To` + guardar el `To` + guardia `findByNumero` + invariante
`TwilioWebhookController` lee `To` y lo pasa en `MensajeEntrante` (`desdeFormulario`). `MensajeEntranteService` bifurca en el orden de arriba: (1) `To == whatsapp-from` → COMPARTIDO (`findByTelefono`, `numeroWhatsapp` null); (2) `findByNumero(To)` → DEDICADO (`findByGestoriaIdAndTelefono`, `numeroWhatsapp` = la fila); (3) ninguno → desconocido. Rellena `mensaje_campo.telefono_destino` y `tramite.numero_whatsapp_id`. `TRAMITE_CREADO` añade `Clave.NUMERO_WHATSAPP_ID` (Long).
**Tests (primero):**
- `MensajeEntranteServiceTest` por rama: COMPARTIDO como hoy (`numeroWhatsapp` null); DEDICADO ok (trámite en la gestoría dueña, `numeroWhatsapp` = la fila); **aislamiento** — contacto de otra gestoría al número DEDICADO → desconocido, sin trámite; `To` no registrado → desconocido, sin fila de auditoría; `To` ausente → desconocido. Idempotencia por `MessageSid` intacta; logs con teléfono enmascarado.
- **Invariante** (ajuste de Antonio): un test que afirme que, tras crear el trámite DEDICADO, `tramite.numeroWhatsapp.gestoria == tramite.gestoria`. **Mutación dirigida:** forzar en el servicio que el trámite tome el número sin comprobar la gestoría (o colgar un número de otra gestoría) debe poner el test en rojo.
- **Guardia `ConsultasSinTenantTest`** (nueva): ningún bean de producción salvo `MensajeEntranteService` referencia `findByNumero`; extendida a `findByTelefono`. **Mutación:** añadir una llamada ficticia a `findByNumero` desde otro bean debe ponerla en rojo (restaurar con `cp`+`cmp`).
**Revisor:** `-Dtest=MensajeEntranteServiceTest` (+ el del controlador + `ConsultasSinTenantTest`). Mutación de aislamiento: quitar el filtro por `gestoriaId` en el contacto → rojo.

### T3 — Selección del remitente al responder
`EnvioWhatsapp.enviar` recibe el `from`; `TwilioEnvioWhatsapp` lo usa; `RespuestaWhatsappService` resuelve `from` = número del trámite o el `whatsapp-from` global (legacy). `configurado()` ajustado. Auditoría de respuesta opcionalmente con el id del número.
**Tests (primero):** `EnvioWhatsappFalso` captura el `from`; trámite DEDICADO → envía desde su número; trámite legacy (`numero_whatsapp_id` null) → desde el compartido; sin `from` resoluble → `Rechazado`. `ClasificadorEnvioWhatsappTest` sigue verde.
**Revisor:** `-Dtest` sobre los tests de `RespuestaWhatsappService`/envío.

### T4 — Alta interna de números (DEDICADO) + auditoría
`POST /internal/whatsapp/numeros` (patrón `OnboardingController`: `X-Internal-Secret`, comparación en tiempo constante, fail-closed; público en Spring Security bajo `/internal/**`). Body `{numero, gestoriaId}` (todos DEDICADO; no hay `tipo`). Valida: `numero` normalizable; **`numero` == `ganera.twilio.whatsapp-from` (normalizado) → 400** (no se registra el compartido como DEDICADO); `numero` ya existente → **409**; `gestoriaId` obligatorio y existente. Deja **`NUMERO_WHATSAPP_REGISTRADO`** (actor `SISTEMA`, gestoría dueña, `entidadId` = id del número, sin el teléfono en texto).
**Tests (primero):** alta 201 + fila de auditoría; `numero` == `whatsapp-from` → **400** sin crear ni fila; `numero` duplicado → **409**; sin secreto → 401/403; `gestoriaId` inexistente → 404/400; declaración **auditada** en `InventarioAuditoriaTest`; el E2E con **`@ExtendWith(GuardiaDmlExtension)`**.
**Revisor:** `-Dtest` sobre el test del endpoint + `InventarioAuditoriaTest`.

### T5 — E2E de las dos modalidades + aislamiento (y **suite completa final**)
`@SpringBootTest(RANDOM_PORT)` con dos gestorías (A y B) y sus números DEDICADO dados de alta por el endpoint interno; el COMPARTIDO = el `whatsapp-from` configurado del test. Escenarios: (a) DEDICADO: contacto de A al número de A → trámite en A, auditado con el id del número; (b) **aislamiento**: contacto de A al número DEDICADO de B → desconocido para B, sin trámite, nada de A aparece en B; (c) COMPARTIDO: al número == `whatsapp-from` → resuelto por teléfono como hoy (`numeroWhatsapp` null); (d) `To` no registrado (ni DEDICADO ni el compartido) → desconocido; (e) respuesta: trámite DEDICADO → remitente = número de A; trámite COMPARTIDO/legacy → remitente = `whatsapp-from`; (f) ninguna fila de auditoría contiene el teléfono (solo el id Long).
**Cierre:** aquí (y solo aquí) `./mvnw clean test` completo + `npm test` (el frontend no cambia en fase 1) + smoke H2 con la skill `smoke-test-h2`.

---

## Decisiones abiertas (menores, con recomendación)

Todas **cerradas** por Antonio (2026-11-16); resumen de lo aplicado:
- **D-a — `To` no registrado:** reutilizar `NUMERO_DESCONOCIDO`, sin fila de auditoría, como hoy (toca auditoría → confirmado: es el comportamiento actual, no lo cambia). `To` enmascarado en logs.
- **D-b:** `tramite` guarda la **FK** `numero_whatsapp_id`.
- **D-c:** `mensaje_campo.telefono_destino` se guarda **siempre** (enmascarado en logs).
- **D-d (ajuste de Antonio):** **no** se siembra la fila COMPARTIDO; el compartido se identifica por **configuración** (`To == whatsapp-from`). `numero_whatsapp` solo tiene DEDICADO con `gestoria_id NOT NULL`.
- **D-e:** actor de `NUMERO_WHATSAPP_CREADO` = `SISTEMA` (toca auditoría → ya en el plan T4 aprobado; confirmado).
- **D-f:** remitente sin resolver (trámite sin número y sin `whatsapp-from`) → `Rechazado` (503 en el endpoint, como hoy sin configurar).

## Estimación

**5 tareas** (implementador + revisor cada una) + smoke final. **Backend solo** (sin frontend en fase 1; como mucho, más adelante, mostrar por qué número entró el trámite → entonces declarar el teléfono en la guardia). Una migración (`V31`). Sin cambios en la firma/validación de Twilio (decisión 3/7). **`CLAUDE.md` se actualiza en el MISMO commit** (ajuste de Antonio): la consulta sin tenant nueva (`findByNumero`), el registro de números y las dos modalidades.

---

## Anexo — Meta/Twilio: coexistencia y pasos de consola (para empezar ya)

Fuente: doc oficial de Twilio (`migrate-numbers-and-senders`, `self-sign-up`) leída el 2026-11-16.

### Coexistencia: **NO**
Mover a Twilio un número que está en la **app de WhatsApp Business** exige **borrar esa cuenta de WhatsApp** y **se pierde la app en ese número** (verbatim de Twilio): «*Delete the WhatsApp account to make your phone number available for the WhatsApp Business Platform with Twilio*» y «*You won't be able to continue using WhatsApp or WhatsApp Business App with the same phone number*». `self-sign-up`: «*The phone number must not already be registered with WhatsApp*». No hay ruta app↔API que conserve la app. El historial de chats del número **no** se traslada.
**Implicación para las gestorías:** si una gestoría usa hoy ese número en la app de WhatsApp Business, al pasarlo a Ganera/Twilio **pierde la app**. Si necesita conservar la app, hay que usar **otro número** para Ganera. Avisar de esto en el alta.

### Requisitos previos (Meta)
- Cuenta de Twilio creada y *upgraded*.
- Acceso de administrador a un **Meta Business Portfolio** (se puede crear durante el alta).
- **Verificación de negocio de Meta**: no hace falta para empezar, **sí para producción** (sube límites, permite >2 remitentes y pedir OBA). **Puede tardar varias semanas** → empezar ya.
- El número **no** debe estar registrado en WhatsApp y debe poder **recibir SMS o voz** (para el OTP).

### Pasos de consola para dar de alta un número (Self Sign-up, cliente directo)
0. Liberar el número si estuviera en WhatsApp (comprobar con `https://wa.me/<numero>?text=hi` o error 63110 en Twilio) → **borrar la cuenta de WhatsApp** (irreversible para la app).
1. Twilio Console → **Communications → Numbers & Senders → Overview → pestaña WhatsApp** (o *WhatsApp Senders*) → **Create new sender**.
2. «*Select a phone number to register*»: elegir un número de Twilio o uno propio → **Continue**.
3. «*Link WhatsApp Business Account with your number*» → **Continue with Facebook** (se abre el pop-up de Self Sign-up; mismo navegador, no compartir la URL).
4. En el pop-up: iniciar sesión en Facebook → revisar permisos → **Get started**.
5. **Business info**: crear o elegir el **Business Portfolio** → Next.
6. **WABA**: para el **primer** remitente, **crear una WABA nueva** (no una creada fuera de Twilio). Marcar la opción de eventos de pedido/lead → Next. *(Los siguientes remitentes deben usar el MISMO Portfolio y la MISMA WABA, o da error — esto encaja con «todos bajo la WABA de Ganera».)*
7. **Profile**: nombre de WABA (interno), **display name** (visible al cliente, reglas de Meta) y categoría. Descripción y web opcionales.
8. **Añadir el número** (no «usar solo display name»): número de Twilio con SMS → copiar el código del Console al pop-up; con solo voz → apuntar el webhook de voz a un *Voicemail Twimlet* y recibir el código por email (hasta 10 min); número no-Twilio → SMS o llamada con OTP.
9. «*Review Twilio's access request*» → **Confirm**. El alta tarda «unos minutos» y el Console muestra el remitente.
10. **Verificación de negocio de Meta** (si el Portfolio es nuevo/sin verificar), vía Meta Security Center.
11. Opcional: webhooks de entrada, foto/perfil, pertenencia a Messaging Service («Edit Sender»).

### Tiempos y riesgos
- Alta del remitente: «unos minutos». OTP por voz/email: hasta 10 min.
- **Verificación de negocio de Meta: varias semanas** (por región) → el cuello de botella; arrancarlo cuanto antes.
- **Display name**: Meta lo revisa tras el alta; si lo rechaza, el número queda limitado a **250 mensajes iniciados por el negocio / 24 h** y Meta puede desconectarlo.
- **Plantillas**: comunes de Ganera (decisión 4), creadas en la WABA de Ganera (Content Template Builder / Content API), aprobadas por Meta; categoría la decide Meta. Necesarias para escribir **fuera de la ventana de 24 h**.
- Una WABA por cuenta/subcuenta de Twilio; varios números (remitentes) bajo esa WABA comparten identidad de negocio (encaja con fase 1: todos bajo la WABA de Ganera).

Fuentes: [Twilio — Migrate numbers and senders](https://www.twilio.com/docs/whatsapp/migrate-numbers-and-senders) · [Twilio — Self sign-up](https://www.twilio.com/docs/whatsapp/self-sign-up) · [Twilio — Key concepts](https://www.twilio.com/docs/whatsapp/key-concepts) · [Twilio — Register senders (ISV)](https://www.twilio.com/docs/whatsapp/isv/register-senders)
