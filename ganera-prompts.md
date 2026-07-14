# Ganera — Prompts para Claude Code

Documento de referencia con todos los prompts, en orden, tal como se han ido cerrando. Cada uno se lanza en la **misma sesión continua** de Claude Code (para que mantenga el contexto), salvo que se indique lo contrario.

Estado actual: **Prompts 0, 1, 2, 2.5 y 2.7 completados y verificados end-to-end.** Repo en GitHub (`github.com/AntonioMenor01/ganera-core`). Login, JWT, onboarding manual de gestorías piloto y la integración completa de Stripe (checkout, webhook, job nocturno) probados con curl/H2 y funcionando. Pendiente de Antonio: crear el `Price` real en el dashboard de Stripe (test mode) para poder probar un checkout real de principio a fin. Siguiente paso: Prompt 3a/3b (catálogo OVZ.net, bloqueado a la espera de credenciales de Antonio).

---

## Prompt 0 — Alineación (✅ completado)

```
Actúa como un Arquitecto de Software Senior experto en Spring Boot y React. Vamos a construir "Ganera" desde cero como código propietario (viene de una migración desde una plataforma no-code, pero empezamos limpio).

CONTEXTO DE NEGOCIO:
Ganera es una plataforma que automatiza trámites bovinos para gestorías ganaderas españolas. El modelo de negocio es:

- Una GESTORÍA (asesoría) contrata el servicio y paga una suscripción mensual/anual vía Stripe, con precio por número de explotaciones activas.
- La Gestoría tiene EMPLEADOS (Usuario, con login) que gestionan la plataforma. No hay roles diferenciados por ahora: todo usuario de una gestoría puede operar dentro de su gestoría (más adelante podríamos añadir reparto de cartera entre empleados, pero no ahora).
- Cada Gestoría tiene GANADEROS como clientes. Los ganaderos NUNCA acceden a ninguna web ni tienen login: su única interacción con el sistema es mandar mensajes de WhatsApp.
- Cada Ganadero tiene una o más EXPLOTACIONES (granjas), y cada explotación tiene ANIMALES identificados por crotal (formato tipo ES123456789012, donde los últimos 4 dígitos son los que el ganadero suele mencionar por WhatsApp).
- Quien escribe por WhatsApp es un CONTACTO (puede ser el titular o un trabajador), identificado por su teléfono. Un trabajador está vinculado a una única explotación (sin ambigüedad). El titular puede estar vinculado a varias explotaciones de sus ganaderías.

FLUJO OPERATIVO CORE:
1. Un Contacto manda un mensaje de WhatsApp (vía Twilio) describiendo un trámite (alta, baja, censo, movimiento, demora) en lenguaje natural, mencionando animales normalmente por los últimos dígitos del crotal.
2. El sistema identifica al Contacto por teléfono y resuelve su(s) explotación(es) asociada(s). Si hay ambigüedad (el contacto tiene varias explotaciones y el mensaje no aclara cuál), el trámite queda marcado para revisión manual sin asignar explotación.
3. Se usa un modelo de IA (a decidir el proveedor, pero debe quedar abstraído detrás de una interfaz para poder cambiarlo) para extraer un JSON estructurado: tipo de trámite y array de crotales mencionados (por sus últimos dígitos).
4. Se buscan los crotales completos comparando los últimos dígitos dentro de la explotación resuelta. Si no hay coincidencia exacta o hay múltiples posibles, el trámite NUNCA se autoaprueba: siempre pasa a un estado de revisión manual.
5. REGLA NO NEGOCIABLE: ningún trámite se ejecuta contra la web externa OVZ.net sin que un humano (empleado de la Gestoría) pulse "Aprobar" explícitamente en la interfaz. No hay umbral de confianza de IA que salte este paso, nunca.
6. Una vez aprobado, un servicio de automatización web (Playwright) ejecuta el trámite real en OVZ.net, usando las credenciales del Ganadero (cifradas en base de datos). Este servicio también se usa en modo lectura para sincronizar el inventario inicial de explotaciones/animales de un ganadero recién dado de alta (evitando tener que cargarlo a mano).

FACTURACIÓN:
La Gestoría paga vía Stripe, por número de explotaciones activas contratadas, con un trial de 15 días. Si el trial expira sin pago, o si hay un impago tras el periodo de gracia, el acceso de lectura se mantiene pero se bloquea la posibilidad de aprobar trámites (no ejecutar nada más en OVZ.net) hasta regularizar el pago.

ARQUITECTURA:
Monorepo con dos carpetas: `/backend` (Spring Boot 3.4+, Java 21, monolito modular con paquetes separados por contexto) y `/frontend` (React + Vite + TypeScript + Tailwind + shadcn/ui).

Por favor, confírmame que entiendes el alcance de negocio y el flujo técnico antes de que te dé el código del Paso 1. No generes archivos todavía, solo confírmame tu comprensión y si ves algún punto que no queda claro o que crees que debería aclararse antes de seguir.
```

**Respuesta a las dudas que planteó (multi-tenancy, estados, fallos de Playwright, ambigüedad, idempotencia, proveedor IA):**

```
Buenas observaciones, aquí las decisiones cerradas:

1. Multi-tenancy: row-level con columna gestoria_id en las entidades correspondientes, usando Hibernate Filters (@Filter/@FilterDef) para aplicar el filtrado automáticamente en las queries.

2. Estados del Tramite (enum completo): PENDIENTE_EXTRACCION, PENDIENTE_REVISION, APROBADO, EN_PROCESO, EJECUTADO_OVZ, ERROR_OVZ, RECHAZADO. El caso de explotación ambigua o crotal sin coincidencia cae en PENDIENTE_REVISION con el campo explotacion en null, no es un estado aparte.

3. Fallo de automatización Playwright contra OVZ.net: 1 reintento automático inmediato. Si vuelve a fallar, el trámite pasa a ERROR_OVZ guardando el motivo del error, visible de inmediato en la Cola. Nunca debe fallar en silencio. No hace falta sistema de notificaciones push por ahora.

4. Explotación ambigua (contacto con varias explotaciones y mensaje no aclara cuál): se resuelve manualmente por el empleado de la Gestoría desde la UI de revisión. No hay flujo automático de vuelta al ganadero por WhatsApp pidiendo aclaración (eso queda para una fase futura).

5. Idempotencia de WhatsApp: usar el MessageSid de Twilio como clave única en MensajeCampo. Si Twilio reintenta el webhook con el mismo MessageSid, se ignora sin crear un trámite duplicado.

6. Proveedor de IA: Claude Haiku 4.5 vía Spring AI (Anthropic), detrás de una interfaz propia (ej. TramiteExtractionService) para poder cambiar de proveedor sin tocar el resto de la lógica.

Con esto ya cerrado, ¿tienes alguna otra duda antes de que te dé el Prompt 1 de infraestructura?
```

---

## Prompt 1 — Infraestructura (✅ completado)

```
Continuamos con Ganera. Ya confirmaste el alcance de negocio y el flujo técnico en el paso anterior. Ahora vamos a generar el Paso 1: el andamiaje de infraestructura del monorepo. NO implementes lógica de negocio todavía (eso vendrá en pasos posteriores) — este paso es solo estructura, configuración y esqueleto compilable.

ALCANCE DE ESTE PASO:

1. ESTRUCTURA DEL MONOREPO:
   - `/backend` — Spring Boot 3.4+, Java 21, Maven, monolito modular con paquetes por contexto de negocio: `gestoria`, `ganadero`, `explotacion`, `contacto`, `tramite`, `facturacion`, `whatsapp`, `ovz` (automatización), `shared` (config transversal: seguridad, multi-tenancy, excepciones).
   - `/frontend` — React + Vite + TypeScript + Tailwind + shadcn/ui.
   - Un `docker-compose.yml` en la raíz con PostgreSQL (la BD principal) para desarrollo local.

2. BASE DE DATOS Y MULTI-TENANCY:
   - PostgreSQL. Usa Flyway para migraciones versionadas (no Hibernate `ddl-auto`).
   - Crea las migraciones iniciales para las entidades base: Gestoria, Usuario (empleado de gestoría, con login), Ganadero, Explotacion, Animal, Contacto, Tramite.
   - Todas las tablas que dependen de una Gestoría deben tener columna `gestoria_id`.
   - Configura Hibernate Filters (`@FilterDef`/`@Filter`) a nivel de las entidades correspondientes para que el filtrado por `gestoria_id` se aplique automáticamente en todas las queries, activado según el usuario autenticado en cada request (vía interceptor/aspect que active el filtro de Hibernate con el `gestoria_id` del contexto de seguridad).
   - El enum de estados del Tramite debe ser exactamente: PENDIENTE_EXTRACCION, PENDIENTE_REVISION, APROBADO, EN_PROCESO, EJECUTADO_OVZ, ERROR_OVZ, RECHAZADO.
   - Añade una columna `motivo_error` (nullable) en Tramite para guardar el motivo cuando el estado sea ERROR_OVZ.
   - MensajeCampo (mensajes entrantes de Twilio) debe tener una columna única para `message_sid` de Twilio (constraint UNIQUE) para garantizar idempotencia.

3. SEGURIDAD:
   - Spring Security con autenticación JWT para los Usuarios (empleados de gestoría). No hace falta OAuth2/SSO por ahora.
   - El JWT debe incluir el `gestoria_id` del usuario autenticado, que es lo que activará el Hibernate Filter en cada request.
   - Los ganaderos y contactos NUNCA tienen login — no crees entidades de autenticación para ellos.

4. INTEGRACIÓN CON SPRING AI (ANTHROPIC):
   - Añade la dependencia de Spring AI para Anthropic.
   - Configura en `application.yml` el modelo exacto `claude-haiku-4-5-20251001` (snapshot fijo, no el alias).
   - Crea la interfaz `TramiteExtractionService` (solo la interfaz y su implementación esqueleto por ahora, sin lógica de prompt todavía) en el paquete `tramite`, preparada para usar Structured Outputs (JSON Schema) de la API de Anthropic.

5. INTEGRACIÓN CON TWILIO:
   - Añade la dependencia del SDK de Twilio.
   - Crea el controlador REST que recibirá el webhook de WhatsApp entrante (sin lógica de negocio aún, solo el endpoint, la validación de firma de Twilio, y el guardado idempotente del mensaje crudo en MensajeCampo usando `message_sid`).

6. INTEGRACIÓN CON STRIPE:
   - Añade la dependencia del SDK de Stripe.
   - Crea el controlador REST que recibirá los webhooks de Stripe (validación de firma, sin lógica de negocio de facturación todavía).

7. PLAYWRIGHT (AUTOMATIZACIÓN OVZ.NET):
   - En el backend, prepara el módulo `ovz` con la dependencia de Playwright para Java.
   - Solo el esqueleto de un servicio `OvzAutomationService` con métodos vacíos: uno para sincronizar inventario inicial (modo lectura) y otro para ejecutar un trámite (modo escritura).

8. FRONTEND:
   - Scaffold de Vite + React + TypeScript + Tailwind + shadcn/ui.
   - Estructura de carpetas por feature: `features/tramites`, `features/ganaderos`, `features/explotaciones`, `features/facturacion`, `shared/`.
   - Cliente HTTP base preparado para adjuntar el JWT en cada request.
   - Sin pantallas funcionales todavía — solo el layout base y el router.

9. DOCKER COMPOSE:
   - Servicio de PostgreSQL con volumen persistente.
   - Variables de entorno vía `.env` (no hardcodees credenciales).

Antes de generar código, dime si tienes alguna duda sobre este alcance, y confírmame qué gestor de build usarás en el backend (Maven o Gradle) si no tienes preferencia ya decidida por mi parte.
```

**Correcciones aplicadas tras la review del código generado:**

```
Antes de seguir, hay un problema de diseño: la tabla `contacto` tiene 
UNIQUE(gestoria_id, telefono), pero eso asume que ya sabemos el gestoria_id 
antes de identificar al contacto por su teléfono. Vamos a usar UN ÚNICO 
número de WhatsApp de Twilio compartido para todas las gestorías. Cambia 
la constraint a UNIQUE(telefono) a secas, y que el flujo de identificación 
busque el Contacto directamente por teléfono en toda la base de datos.
```

```
Antes de seguir, dos correcciones y una tarea:

1. Las credenciales de OVZ.net (ovz_usuario, ovz_password_cifrada) deben 
   estar en Ganadero, no en Explotacion — un Ganadero tiene un único login 
   de OVZ.net que cubre todas sus explotaciones. Mueve esas columnas y 
   campos de la entidad Explotacion a la entidad Ganadero.

2. ovz_password_cifrada es ahora mismo un VARCHAR sin cifrado real. 
   Implementa un AttributeConverter de JPA con AES-256-GCM para cifrar/
   descifrar ese campo de forma transparente. La clave de cifrado debe 
   venir de una variable de entorno (ENCRYPTION_KEY), nunca hardcodeada.

3. Inicializa git en la raíz del monorepo y haz el primer commit con todo 
   el scaffold actual.

Sí, inicializa git ahora e instala los navegadores de Playwright también.

Cuando termines, confírmame que backend y frontend siguen compilando.
```

**✅ Correcciones aplicadas y verificadas:**
- Credenciales OVZ.net movidas de `Explotacion` a `Ganadero` (sin referencias huérfanas).
- Cifrado real: `EncryptedStringConverter` (AES-256-GCM, IV aleatorio de 12 bytes por valor), aplicado explícitamente vía `@Convert` sobre `ovzPasswordCifrada` (confirmado manualmente). Clave desde `ENCRYPTION_KEY`; si falta, el arranque falla explícitamente (sin fallback inseguro).
- Git inicializado, primer commit hecho (76 archivos, excluyendo `.claude/settings.local.json` que es config local de máquina).
- Playwright: navegadores instalados (Chromium, Firefox, WebKit, FFMPEG).
- Backend y frontend compilan/buildan correctamente tras todos los cambios.

**Nota de estado (2026-07-09):** el monorepo completo (`/backend` + `/frontend` + `docker-compose.yml`)
fue **reconstruido desde cero** en esta fecha, sin backend/frontend previos que reutilizar, siguiendo
el plan `docs/superpowers/plans/2026-07-08-paso1-infraestructura.md` (9 tareas). El plan cubre y cierra
todo el alcance original de este Prompt 1 más las correcciones históricas ya documentadas arriba
(`UNIQUE(telefono)` global, credenciales OVZ en `Ganadero`, cifrado AES-256-GCM real desde el día uno),
además de los esqueletos de integración de Prompts 2, 2.5 y 2.7 (JWT, onboarding manual, Stripe) que ya
estaban resueltos quedaron incorporados en la reconstrucción. Verificado end-to-end con el smoke test
H2 en memoria (modo PostgreSQL, con Flyway habilitado): las 9 migraciones `V1`-`V9` se aplicaron
limpiamente y la aplicación completa arrancó sin errores de Hibernate ni de Spring Security — ver
`.superpowers/sdd/task-9b-report.md` para el detalle. El frontend (`npm run build`) compila limpio.

**Bug real encontrado y corregido durante este smoke test:** `EncryptedStringConverter` (cifrado
AES-256-GCM de credenciales OVZ, Prompt 1) declara dos constructores — el público usado por Spring
(`@Value("${ganera.encryption.key:}")`) y uno package-private solo para tests (`Supplier<String>`).
Sin ninguno marcado `@Autowired`, Spring no podía elegir cuál usar al construir el bean `@Component`
y caía a instanciación sin argumentos, que no existe — la app entera fallaba al arrancar con
`BeanCreationException: ... No default constructor found`, aunque `mvn test` (con `@DataJpaTest`,
que no carga el contexto completo) nunca lo había mostrado. Corregido añadiendo `@Autowired`
explícito al constructor público; no afecta al test existente, que sigue invocando el constructor
package-private directamente. Backend y frontend en su estado post-reconstrucción, listos para
continuar con Prompt 2.7 (lógica de negocio) tal como estaba planificado antes de esta reconstrucción.

---

## Prompt 2 — Cierre del modelo de datos (✅ completado)

Nota: gran parte del modelo ya había quedado cubierta en el Prompt 1 (Claude Code se adelantó). Este prompt cerró solo lo que faltaba: estados de `Suscripcion`, gate de aprobación, y cartera opcional.

```
Continuamos con Ganera. El Paso 1 (infraestructura) está cerrado y confirmado — hay un CLAUDE.md en la raíz con todo el contexto del proyecto.

Paso 2: cerrar el modelo de datos que falta.

1. ESTADOS DE SUSCRIPCION:
   Ampliar EstadoSuscripcion a estos 6, exactos: TRIAL, TRIAL_EXPIRADO_SIN_PAGO, ACTIVA, IMPAGO_GRACIA, SUSPENDIDA, CANCELADA, con la semántica de cada uno (trial, bloqueo por trial vencido, activo, impago en gracia, impago suspendido, baja).

2. GATE DE APROBACIÓN POR SUSCRIPCIÓN:
   Método puedeAprobarTramites(gestoriaId) en un SuscripcionService: false si TRIAL_EXPIRADO_SIN_PAGO o SUSPENDIDA, true en cualquier otro caso. No conectar aún a ningún endpoint.

3. CARTERA OPCIONAL POR EMPLEADO:
   Campo modoCartera (boolean, default false) en Gestoria + tabla/entidad usuario_explotacion (ManyToMany Usuario-Explotacion), sin lógica de filtrado todavía.

Antes de generar código, dime si tienes alguna duda.
```

**Decisión tomada durante la ejecución:** si una Gestoría no tiene ninguna `Suscripcion` asociada, `puedeAprobarTramites` devuelve **false** (fail-closed).

**Resultado:** `EstadoSuscripcion` con los 6 valores, `SuscripcionService.puedeAprobarTramites()` con test unitario (7 casos), `Gestoria.modoCartera`, entidad `UsuarioExplotacion`, migración `V7`. Backend compila, tests pasan. CLAUDE.md actualizado. Commiteado y pusheado a GitHub.

---

## Prompt 2.5 — Autenticación y roles (✅ completado)

**Contexto añadido antes de lanzarlo:** las primeras gestorías son pilotos gratuitos dados de alta manualmente por Antonio — no hace falta registro público todavía.

```
Continuamos con Ganera. Paso 2 cerrado (estados de Suscripcion, gate de aprobación, cartera opcional). CLAUDE.md está al día.

Paso 2.5: autenticación real de Usuario + alta manual de Gestorías piloto.

CONTEXTO: no hay registro público todavía — las primeras gestorías son pilotos gratuitos que yo mismo doy de alta a mano. No construyas ninguna pantalla ni flujo de self-signup.

1. LOGIN:
   - Endpoint POST /auth/login que reciba email + password.
   - Valida contra Usuario (BCryptPasswordEncoder ya configurado en SecurityConfig), comprueba que el usuario esté activo.
   - Si es válido, genera y devuelve un JWT usando el JwtService ya existente (con usuarioId, gestoriaId, email).
   - Si no es válido, devuelve 401 sin distinguir el motivo exacto en la respuesta.
   - Añade también GET /auth/me (autenticado) que devuelva los datos básicos del Usuario actual a partir del JWT.

2. ALTA MANUAL DE GESTORÍAS PILOTO (sin UI, sin registro público):
   - Endpoint interno POST /internal/onboarding/gestoria que reciba los datos de una Gestoria nueva + su primer Usuario y cree ambos en una transacción, junto con una Suscripcion en estado ACTIVA sin stripeSubscriptionId.
   - Protégelo con un secreto compartido simple (header X-Internal-Secret comparado contra ONBOARDING_SECRET). Solución temporal — dilo explícitamente en un comentario en el código.
   - Devuelve el id de la Gestoria y el Usuario creados (sin password ni hash).

3. SEGURIDAD:
   - Actualiza SecurityConfig: /auth/login público; /auth/me requiere autenticación; /internal/** público a nivel de Spring Security pero el propio controlador rechaza si el header del secreto no coincide.

Antes de generar código, dime si tienes alguna duda. Cuando termines, confirma que compila y pasa los tests, y actualiza CLAUDE.md si corresponde.
```

**Resultado:** `POST /auth/login`, `GET /auth/me`, `POST /internal/onboarding/gestoria` (secreto comparado en tiempo constante con `MessageDigest.isEqual`), `authenticationEntryPoint` explícito para 401 en vez de 403, tests unitarios (`AuthServiceTest`, 4 casos). Backend compila, 11 tests pasan en total. Commiteado.

**✅ Verificación E2E realizada (smoke test con H2 en memoria, ya que el entorno no tenía Docker/Postgres disponible):** los 3 pasos (onboarding → login → `/auth/me`) funcionan correctamente, más los 3 casos negativos de seguridad (401 sin token, 401 password incorrecta, 401 secreto de onboarding incorrecto).

**Bug real encontrado y corregido durante la prueba:** `TwilioWebhookController` construía el `RequestValidator` de Twilio en el constructor, que lanza excepción con una clave vacía — como `TWILIO_AUTH_TOKEN` es opcional hoy (sin cuenta de Twilio aún), **la aplicación entera no arrancaba en absoluto**, aunque el problema estuviera en un módulo sin lógica de negocio conectada todavía. Corregido moviendo la construcción del validador dentro del método del endpoint (por request, no al arrancar). Commit separado: `fix: TwilioWebhookController no debe impedir el arranque sin TWILIO_AUTH_TOKEN`.

**Nota:** el smoke test usó H2 en memoria, no PostgreSQL real — válida la lógica de negocio pero no las migraciones de Flyway contra el motor real. Pendiente (no bloqueante) probarlo alguna vez con Docker+Postgres antes de producción.

**Nota de estado (2026-07-09):** este prompt fue **re-implementado desde cero** en esta fecha (la
sesión anterior se cortó tras commitear solo el plan, sin código), siguiendo
`docs/superpowers/plans/2026-07-09-prompt2.5-autenticacion.md` con subagent-driven development
(implementador → revisor por tarea, ambas Approved). Commits `2d49f3e`, `5eb2b97` (fix Minor del
revisor: guard de credenciales null para 401 uniforme) y `d87d207`. Suite completa: 43 tests en
verde. Smoke test H2 E2E re-verificado: onboarding → login → `/auth/me` en 200 y los 3 negativos
en 401. Detalle de la sesión en `.superpowers/sdd/progress.md`.

---

## Prompt 2.7 — Integración Stripe completa (✅ completado)

Plan escrito y aprobado por Antonio en persona (gate humano explícito, ver
`docs/superpowers/plans/2026-07-09-prompt2.7-stripe.md`) antes de dispatchar ningún subagente de
implementación. Ejecutado en 4 tareas sobre el scaffolding de Prompt 2, subagent-driven development
(implementador → revisor por tarea, las 3 revisadas Approved):

- **Task 1** (`c0ff6a5`): migración `V13` — `stripeSubscriptionId` (UNIQUE), `explotacionesContratadas`
  (nullable), `stripeUltimoEventoEpoch` en `Suscripcion`; `SuscripcionService.obtenerOCrearSuscripcion`
  idempotente ante doble-click/carrera vía `saveAndFlush` + catch de la violación `UNIQUE(gestoria_id)`.
- **Task 2** (`e1c784e`): `StripeConfig` (fija `Stripe.apiKey` estático, tolera clave en blanco al
  arrancar — misma lección que el footgun de Twilio), `StripeCheckoutService` (Checkout Session modo
  `SUBSCRIPTION`, `client_reference_id = gestoriaId`, `quantity = max(1, nº Explotaciones)`, trial de
  15 días en `subscription_data`), `POST /facturacion/checkout` → `503` si Stripe no está configurado
  (clave o price id en blanco), nunca `500`.
- **Task 3** (`1b4d753`): `StripeWebhookService` — máquina de estados completa dirigida por los
  eventos de Stripe (`checkout.session.completed` → `TRIAL`, `invoice.payment_succeeded` → `ACTIVA`,
  `invoice.payment_failed` → `TRIAL_EXPIRADO_SIN_PAGO` o `IMPAGO_GRACIA` según el estado almacenado,
  `customer.subscription.updated` con el mapa de 6 estados por `status`/`previous_attributes.status`,
  `customer.subscription.deleted` → `CANCELADA`), con guard monotónico por `event.created`
  (`stripeUltimoEventoEpoch`, comparación estrictamente-menor) contra re-entrega tardía/desorden de
  webhooks. **Limitación conocida y aceptada:** no hay dedupe por `event.id` — dos eventos distintos
  que caigan en el mismo epoch-second pueden aplicarse en cualquier orden; aceptable para volumen
  piloto, a reconsiderar antes de escalar (ver también CLAUDE.md).
- **Task 4** (cierre del prompt): `SuscripcionSyncScheduler` — job nocturno (`@Scheduled(cron = "0 0
  3 * * *", zone = "Europe/Madrid")`) que reconcilia `explotacionesContratadas` para toda `Suscripcion`
  en `ACTIVA`/`IMPAGO_GRACIA` contra el conteo real de Explotaciones, con proración por defecto de
  Stripe al empujar el cambio de `quantity`; decisión de negocio: nocturno, no por webhook ni por
  alta/baja de Explotación, para no acoplar ese CRUD a Stripe. Corre sin request HTTP → sin
  `gestoriaFilter` activo → sus queries son cross-tenant a propósito (documentado en el código, no
  una fuga). Un fallo al empujar una Suscripción concreta no aborta el resto del batch (try/catch
  por iteración, logueado). `@EnableScheduling` añadido a `GaneraApplication`.

**Resultado:** suite completa backend en verde a **79 tests** (subida desde 73 tras las 6 pruebas
puras de `SuscripcionSyncSchedulerTest`, sin mocks del SDK de Stripe — convención
`test-sin-mocks-externos` en las 4 tareas). Smoke test H2 manual (Flyway deshabilitado, `ddl-auto`
para arrancar sin Postgres real; el propio full-suite sí valida las 13 migraciones reales contra
H2) con todo lo de Stripe en blanco: `POST /facturacion/checkout` → `401` sin JWT, `503` con un JWT
real (onboarding → login) por falta de `STRIPE_API_KEY`/`STRIPE_PRICE_ID_EXPLOTACION`;
`POST /webhooks/stripe` → `400` con una firma inválida. Ninguna llamada real a Stripe en ningún
momento (tests ni smoke) — solo claves `sk_test_...`/`whsec_...` de test, nunca usadas de verdad en
este entorno. `puedeAprobarTramites` (de Prompt 2) sigue sin conectarse a ningún endpoint — llega
con la lógica de negocio de trámites (Prompt 3d), tal y como estaba planificado, no es un olvido de
este prompt. **Pendiente de Antonio:** crear el `Price` real en el dashboard de Stripe (modo test
primero) y fijar `STRIPE_PRICE_ID_EXPLOTACION` — sin eso, un checkout real de principio a fin contra
Stripe test-mode no se puede probar todavía; ninguna tarea de este prompt lo hace por él.

---

## Prompt 3a — Sincronización inicial OVZ.net, modo lectura (pendiente)

**Bloqueado por:** catálogo de tipos de trámite y estructura de OVZ.net (pendiente de que Antonio entre con sus credenciales).

---

## Prompt 3b — Webhook Twilio + extracción IA (pendiente)

**Bloqueado por:** mismo catálogo que 3a. Incluirá el prompt de sistema real para `TramiteExtractionService` con ejemplos de mensajes reales de ganaderos.

---

## Prompt 3c — Automatización real OVZ.net, modo escritura (pendiente)

**Bloqueado por:** mismo catálogo. Implementará la lógica real de `PlaywrightOvzAutomationService.ejecutarTramite()` con 1 reintento automático y paso a `ERROR_OVZ` si falla.

---

## Prompt 3d — Importador Excel + controladores REST (completado, 2026-07-14)

Fallback manual para cargar inventario cuando la sincronización automática (3a) no esté disponible o falle, más los controladores REST básicos de trámites (sin frontend todavía).

**No bloqueado por OVZ.net** (no toca el paquete `ovz`), así que se adelantó mientras 3a/3b/3c siguen
esperando las credenciales reales.

Antes de generar código se confirmaron 3 huecos que el formato de Excel propuesto por Antonio no
cubría (ver `CLAUDE.md`, bullet "Excel inventory importer" bajo Technical decisions, para el diseño
completo):
- `Ganadero` no tenía campo NIF → se añadió (`V14`, `nif` globalmente único, misma convención que
  `codigoRega`/`crotal`) y se usa como clave de upsert.
- `Animal` no tiene (ni necesita) campo `especie` — Ganera solo gestiona bovino por ahora — pero el
  Excel puede traer una especie incorrecta a mano: se valida (debe ser blanco o una etiqueta bovina)
  y se marca como error de fila si no, sin persistirla nunca.
- No existía ningún test MockMvc en el proyecto — se siguió el patrón ya establecido (llamada directa
  al controlador con `GaneraUserPrincipal`/`MockMultipartFile` construidos a mano, sin HTTP real).

Entregado: `POST /explotaciones/importar` (Apache POI, upsert por `codigoRega`/`crotal`/`nif`, nunca
aborta el fichero completo por una fila mala), `GET /explotaciones`, `GET /tramites` (filtrable por
`estado`), `POST /tramites/{id}/aprobar` (primera llamada real a
`SuscripcionService.puedeAprobarTramites`, `403` si no puede) y `POST /tramites/{id}/rechazar` — estos
dos últimos solo cambian `EstadoTramite` en BD, sin tocar `OvzAutomationService` todavía (eso es
Prompt 3c). Suite completa en verde a 91 tests (79 + 12 nuevos); smoke test H2 end-to-end real por
HTTP (onboarding → login → importar un `.xlsx` de prueba real → listar → reimportar el mismo fichero
sin duplicar → 401 sin JWT → 404 en un trámite inexistente).

**Footgun encontrado y arreglado:** añadir POI causó un `NoSuchMethodError` en tiempo de ejecución
(`BoundedInputStream.builder()`) porque Twilio arrastra una versión de `commons-io` más vieja que la
que POI necesita, y Maven la elegía por mediación "nearest-wins" — invisible en `mvn compile`, solo
se ve al leer un fichero real. Arreglado fijando `commons-io` como dependencia directa en
`backend/pom.xml` (detalle completo en `CLAUDE.md`).

**Revisión de Antonio antes de commitear — 2 problemas reales encontrados y arreglados:**
1. Con un único método `@Transactional` cubriendo todo el fichero, una violación real de
   constraint en `saveAndFlush()` (no solo los errores "de validación" ya cubiertos por los tests)
   dejaba la sesión de Hibernate inutilizable para el resto del fichero — las filas *posteriores*
   fallaban con un error genérico ajeno a sus propios datos. Confirmado primero con un test que
   fuerza esa colisión real (un `codigo_rega` que ya existe en otra Gestoría, oculto por el filtro
   de tenant) antes de tocar el fix, tal y como se pidió. Arreglado extrayendo el trabajo por fila a
   `ExplotacionImportFilaService`, un bean aparte con `@Transactional(REQUIRES_NEW)` por fila —
   necesario en un bean separado porque la auto-invocación dentro de la misma clase no pasa por el
   proxy de Spring. Al hacerlo se detectó un segundo efecto no obvio: `REQUIRES_NEW` suspende el
   `EntityManager` de la request (donde estaba activo `gestoriaFilter`) y ata uno nuevo sin ningún
   filtro — se corrigió reactivando el filtro dentro de cada método `REQUIRES_NEW` con el
   `gestoriaId` recibido, o se habría roto el aislamiento multi-tenant en silencio.
2. `IllegalArgumentException` por hoja de Excel faltante no estaba capturada en el controlador →
   `500` genérico. Arreglado: `400` con el mensaje de la excepción.

Suite completa 93/93 en verde (91 + el test de la colisión real + un test nuevo de
`ExplotacionImportControllerTest` para el 400 de hoja faltante). Verificado también por HTTP real
(smoke test H2): import normal sigue funcionando igual tras el refactor, y un fichero sin hoja
"Animales" da `400` con el mensaje en vez de `500`.

---

## Prompt 4 — Frontend funcional, solo lo que no depende de OVZ.net (completado, 2026-07-14)

Del roadmap original de Prompt 4 se dejó fuera **a propósito** todo lo que necesita OVZ.net real:
la pantalla de onboarding de un Ganadero nuevo (conectar credenciales OVZ + disparar sincronización
inicial) — ni siquiera como mock/placeholder visual — y cualquier formulario manual alternativo de
alta de Ganadero/Explotación (el importador Excel de 3d sigue siendo la única vía). Ambas cosas
esperan a un Ganadero real dispuesto a dar sus credenciales, igual que 3a/3b/3c.

Antes de generar código se confirmó con Antonio que no existía ningún endpoint que expusiera el
estado de la Suscripción (había que añadirlo) y se aclaró el nivel de bloqueo de UI para Gestorías
sin acceso: solo se bloquea el botón Aprobar (con mensaje claro), nunca la navegación — coherente
con el diseño ya cerrado en el Prompt 2 (`IMPAGO_GRACIA` = acceso completo con aviso;
`TRIAL_EXPIRADO_SIN_PAGO`/`SUSPENDIDA` = solo lectura, no bloqueo total). Una Gestoría que nunca tuvo
Suscripción recibe el mismo aviso que un estado bloqueante, con opción de empezar el trial de 15
días (mismo `POST /facturacion/checkout` ya existente, sin lógica nueva).

Entregado:
- **Backend** (dos añadidos pequeños, pedidos directamente por las necesidades del frontend):
  `GET /facturacion/suscripcion` (404 fail-closed si nunca hubo Suscripción, sin crear nada como
  efecto secundario) y `GET /tramites/{id}` (detalle con el mensaje original de WhatsApp y la
  Explotación resuelta, separado del listado paginado para no meter un N+1 ahí).
- **Frontend**: login con JWT en memoria (no `localStorage`, sesión de SPA a propósito);
  `AppLayout` con navegación real (Explotaciones/Trámites/Facturación) y banner persistente de
  suscripción; dashboard de Explotaciones con el importador Excel en primer plano; cola de Trámites
  con filtro por estado y modal de revisión (maneja mensaje/explotación nulos sin parecer roto,
  ya que 3b tampoco está implementado); Aprobar/Rechazar con mensaje claro en el 403; página de
  Facturación con el aviso y el botón de trial/actualizar.

Suite completa backend en verde a **101 tests** (93 + 3 de `GET /facturacion/suscripcion` + 4 de
`GET /tramites/{id}` + 1 test crítico de regresión, ver más abajo). Frontend: `tsc -b` y `oxlint`
limpios; verificado además con una sesión real de Chromium headless (paquete npm de Playwright,
instalado ad hoc — no había skill de proyecto para esto todavía) contra el backend real: login →
import Excel (resumen idéntico al del backend) → filtro de trámites (confirmando la query real
`?estado=APROBADO`) → facturación → logout → redirect a `/login`.

**2 bugs de frontend encontrados solo gracias a la verificación en navegador real (invisibles para
`curl`/tests de request):**
1. **CORS**: el backend no permitía peticiones cross-origin desde `:5173` — cualquier request
   fallaba en el preflight del navegador antes de llegar a ningún controller, JWT válido o no.
   Arreglado con `SecurityConfig.corsConfigurationSource` (origen configurable por
   `FRONTEND_ORIGEN`, default `http://localhost:5173`).
2. El `Select` del filtro de trámites mostraba el valor crudo (`"TODOS"`) en vez de la etiqueta —
   la primitiva `@base-ui/react/select` (el preset shadcn instalado es "base-nova", no Radix pese a
   lo que decía este archivo) no resuelve la etiqueta automáticamente; necesita un `children` de
   tipo función en `SelectValue`.

**Verificación final pedida por Antonio antes de comitear — y el hallazgo más importante de todo
el prompt.** Antes de dar el prompt por cerrado, Antonio pidió verificar en vivo (no solo con tests
de integración/revisión de código) los tres casos que habían quedado sin exercitar: el modal de
revisión con un mensaje real de WhatsApp + Explotación resuelta, el mensaje de error 403 al
aprobar, y el banner de "sin suscripción" — más el caso de mensaje/Explotación nulos otra vez, pero
de verdad. Como no hay forma de crear un Trámite ni un estado de Suscripción no-ACTIVA a través de
la app en marcha (3b y el alta pública no existen), los datos se sembraron directamente por SQL
contra la instancia H2 del smoke test (H2 en fichero con `AUTO_SERVER=TRUE` para que un proceso
`org.h2.tools.RunScript` separado pudiera escribir en la misma base de datos viva — sin pasar por
ninguna lógica de negocio de la app) y se condujo desde un navegador real. Los cuatro se veían
bien — **pero sembrar una SEGUNDA Gestoría real con su propio login para probar el banner de "sin
suscripción" sacó a la luz un bug crítico, real y hasta ahora no detectado**: `GET /tramites` y
`GET /explotaciones` devolvían filas de *cualquier* Gestoría, no solo la autenticada — confirmado
con el JWT de la segunda Gestoría decodificado a mano, byte a byte, para descartar un error de
prueba. Causa raíz: `TenantFilterActivationInterceptor` no tenía un orden explícito frente al
`OpenEntityManagerInViewInterceptor` de Spring Boot (`open-in-view=true`); cuando el nuestro corría
antes de que Spring atara el `EntityManager` real de la request al hilo, `enableFilter(...)` se
aplicaba a una `Session` temporal que se descartaba al momento, sin ningún efecto sobre las queries
reales — `gestoriaFilter` **nunca se aplicaba de verdad** en ninguna request HTTP real. Llevaba así
desde que se creó el interceptor (Prompt 1), invisible porque el test unitario invoca
`preHandle()` a mano (sin pasar por el registro/orden real de Spring MVC) y ningún smoke test
manual anterior había comparado dos Gestorías reales con datos solapados en la misma ejecución — con
una sola Gestoría, una query sin filtrar y una filtrada devuelven lo mismo. Arreglado con
`.order(Ordered.LOWEST_PRECEDENCE)` explícito en `WebMvcTenantConfig`. Verificado con TDD real:
se escribió `TenantIsolationEndToEndTest` (el único tipo de test — `@SpringBootTest` con servidor
embebido real — que puede detectar un bug de orden de interceptores), se confirmó que **falla**
sin el fix (revirtiendo el `.order(...)` momentáneamente) y que **pasa** con él. Suite completa
re-verificada en verde (101/101) y los 4 escenarios re-confirmados en el navegador contra el
backend ya arreglado. Detalle completo de la causa raíz en `CLAUDE.md`, bullet de Multi-tenancy.

---

## Decisiones de negocio ya cerradas (para referencia rápida)

- **Mensajería:** Twilio, número de WhatsApp único compartido entre gestorías (a matizar más adelante).
- **IA:** Claude Haiku 4.5 (`claude-haiku-4-5-20251001`) vía Spring AI, con Structured Outputs.
- **OVZ.net:** automatización real con Playwright (lectura y escritura), nunca sin aprobación humana previa.
- **Cifrado:** AES-256-GCM para credenciales de OVZ.net, vía JPA `AttributeConverter`.
- **Facturación:** Stripe, paga la Gestoría, por número de explotaciones activas, trial de 15 días. Impago → periodo de gracia → bloqueo de aprobación de trámites (no de lectura).
- **Jerarquía:** `Gestoria → Usuario` (empleados, con login) y `Gestoria → Ganadero → Explotacion → Animal` (sin login, solo WhatsApp vía `Contacto`).
- **Multi-tenancy:** row-level con `gestoria_id` + Hibernate Filters.
- **Regla no negociable:** ningún trámite se ejecuta en OVZ.net sin aprobación manual explícita de un empleado de la Gestoría.