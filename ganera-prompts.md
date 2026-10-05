# Ganera — Prompts para Claude Code

Documento de referencia con todos los prompts, en orden, tal como se han ido cerrando. Cada uno se lanza en la **misma sesión continua** de Claude Code (para que mantenga el contexto), salvo que se indique lo contrario.

Estado actual (2026-10-02): **Prompts 0, 1, 2, 2.5, 2.7, 3d, 4 (parcial, sin lo bloqueado por OVZ.net), la identidad visual de marca, el alta pública de gestorías, el Prompt A1 (contactos, crotales en trámites y revisión editable, solo backend), el Prompt A2 (su frontend, commit `ae90295`) y el mini-prompt de backend tras A2 completados y verificados.** Repo en GitHub (`github.com/AntonioMenor01/Ganera.git`). Suite backend en verde a 472 tests (`./mvnw clean test`); frontend con 476 tests (`npm test`), `npm run build` y `npm run lint` en verde. Aprobar desde la UI vuelve a funcionar (A2 envía la `version`). Pendientes de Antonio: crear el `Price` real en el dashboard de Stripe (test mode) para poder probar un checkout real de principio a fin, y aportar credenciales de OVZ.net. Siguiente paso no bloqueado: la tarea de frontend antes del piloto (barra de navegación en móvil, singulares del resumen del importador y consumir lo nuevo del mini-prompt). Prompts 3a/3b/3c siguen bloqueados a la espera de las credenciales. Dos bugs de Stripe detectados en la revisión completa del 2026-09-25 quedan pendientes (ver "Known bugs" en `CLAUDE.md`, bullet de Stripe).

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
- Quien escribe por WhatsApp es un CONTACTO (puede ser el titular o un trabajador), identificado por su teléfono. Un trabajador está vinculado a una única explotación (sin ambigüedad). El titular puede estar vinculado a varias explotaciones de sus ganaderías. [Nota posterior: superado por A1, decisión 4 — el rol va en la relación y un empleado puede estar en varias explotaciones.]

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

**Pendientes heredados del Prompt A1 (tenerlos en cuenta al implementar 3b):**
- **Contactos inactivos (decisión 2):** el webhook debe ignorar los mensajes de un `Contacto` con `activo=false` (borrado lógico): no se crea ningún Trámite.
- **`findByTelefono` (decisión 11):** `ContactoRepository.findByTelefono` (sin scope de Gestoría) es exclusivo de este webhook; nada más lo usa. Normalizar el `From` de Twilio con `TelefonoNormalizador` antes de buscar.
- **Finder sin scope heredado (revisión 7b, I1) — resuelto:** `AnimalRepository.findByExplotacionIdAndCrotalUltimosDigitos` (sin `gestoriaId`, sin llamadas, anterior a A1) se eliminó en un commit aparte justo después de A1, por decisión de Antonio. Para resolver crotales desde WhatsApp, usar `findByExplotacionIdAndGestoriaIdAndCrotalEndingWithOrderByIdAsc`, como A1.
- **Crotales duplicados (decisión 23):** la creación automática de trámites desde WhatsApp debe deduplicar los crotales que resuelvan al mismo Animal (el `PATCH` ya responde `409` en ese caso, y aprobar también lo bloquea).
- **Catálogo de tipos (decisión 18):** el enum `TipoTramite` actual (`ALTA`, `BAJA`, `CENSO`, `MOVIMIENTO`, `DEMORA`) se sustituirá en el **prompt B** por los tipos reales de OVZ para vacuno: `ALTA_BOVINO`, `BAJA`, `SOLICITUD_MOVIMIENTO`, `CONFIRMACION_MOVIMIENTO`, `DECLARACION_CENSO`, `MOD_DECLARACION_CENSO`, `DEMORA_CROTALIZACION`. Ocasionales, para una fase posterior: anulación de guías y rechazo de animales en origen. **En A1 el enum NO se cambió.**

---

## Prompt 3c — Automatización real OVZ.net, modo escritura (pendiente)

**Bloqueado por:** mismo catálogo. Implementará la lógica real de `PlaywrightOvzAutomationService.ejecutarTramite()` con 1 reintento automático y paso a `ERROR_OVZ` si falla.

**Pendiente heredado del Prompt A1 (revisión de la Task 6, M2):** nunca ejecutar Playwright/OVZ.net dentro de la transacción que tiene el bloqueo de fila del Trámite (`PESSIMISTIC_WRITE` de `TramiteRevisionService`): se aprueba y se confirma, y la ejecución va después, de forma asíncrona. Si se configura un `statement_timeout` en Postgres, un `57014` acabaría en `500`; un `lock_timeout` (`55P03`) sí se traduce a `409`.

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
`TRIAL_EXPIRADO_SIN_PAGO`/`SUSPENDIDA` = solo lectura, no bloqueo total — superado por A1, decisión 30: solo se bloquea aprobar; editar, rechazar e importar siguen permitidos). Una Gestoría que nunca tuvo
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

**Auditoría dirigida pedida antes de dar esto por cerrado del todo, y un SEGUNDO bug crítico
independiente.** Antonio no aceptó el fix anterior como suficiente: pidió listar TODOS los
endpoints que tocan una entidad `GestoriaScopedEntity` y confirmar, uno por uno, si tenían
cobertura E2E real con dos Gestorías — explícitamente rechazando "usa el mismo Repository, ya
está cubierto" como argumento válido, porque el bug de hoy fue de orden de interceptor, no de
query. Al escribir esa cobertura (`GET /tramites`, `GET /tramites/{id}`,
`POST /tramites/{id}/aprobar`, `POST /tramites/{id}/rechazar`, `GET /auth/me`,
`POST /explotaciones/importar`), tres tests fallaron con `200` en vez de `404`: **`gestoriaFilter`
nunca se aplica a `findById(id)`** — es un comportamiento de Hibernate totalmente distinto e
independiente del bug de hoy (no tiene que ver con el orden de interceptores, es que Hibernate no
aplica el filtro a una carga por clave primaria). Cualquier usuario autenticado de CUALQUIER
Gestoría podía ver, aprobar o rechazar el trámite de OTRA Gestoría adivinando su id numérico —
esto ya estaba así incluso DESPUÉS del fix de `WebMvcTenantConfig`. Arreglado añadiendo
`TramiteRepository.findByIdAndGestoriaId(id, gestoriaId)` (con `gestoriaId` como parámetro real de
la query, inmune al filtro ambiente) y usándolo en los tres endpoints en vez de `findById(id)` a
secas. `GET /facturacion/suscripcion` y `POST /facturacion/checkout` quedaron confirmados exentos
por diseño (usan `findByGestoriaId(gestoriaId)`/`countByGestoriaId(gestoriaId)`, parámetros
explícitos de la query, inmunes a los dos bugs). Suite completa: **110/110** en verde. Detalle
completo en `CLAUDE.md`.

**Identidad visual de marca (commit `9a81219`, 2026-07-14):** paleta real de Ganera (verde
`#1F3D2B`, fondo crema `#F7F6F1`, sidebar `#F1F0E8`) vía variables CSS en `index.css`, variantes
`success`/`warning`/`danger` en `Badge` mapeadas desde los 7 `EstadoTramite`, barra de navegación
con el logotipo textual GANERA, tarjeta de métrica en Explotaciones y limpieza del CSS heredado del
scaffold de Vite. Verificado con `tsc -b`, `oxlint` y navegador real.

---

## Alta pública de Gestorías (completado, 2026-07-14)

Vía adicional de alta para clientes reales, **en paralelo** a `/internal/onboarding/gestoria` (que
se mantiene para pilotos y soporte, sin cambios).

Entregado:
- **Backend** (paquete `registro`): `POST /gestorias/registro`, público (sin JWT ni secreto
  compartido, `permitAll` en `SecurityConfig`). Crea `Gestoria` + primer `Usuario` en una
  transacción (`RegistroGestoriaService`) y a continuación abre una Stripe Checkout Session con 15
  días de prueba vía la nueva `StripeCheckoutService.crearSesionCheckoutConCantidadEstimada`
  (`503` si Stripe no está configurado, igual que el checkout autenticado).
- **Rango de clientes en vez de nº de explotaciones:** una gestoría sabe cuántos clientes tiene, no
  cuántas explotaciones suman. `RangoClientes` (1-10, 11-30, 31-75, 76+) traduce el **mínimo** del
  rango × 1,3 explotaciones/cliente (redondeo hacia arriba) a una quantity **estimada**: 2 / 15 /
  41 / 99. Se usa el mínimo para no sobre-cobrar antes de ver el inventario real; la estimación no
  se autocorrige mientras la suscripción siga en `TRIAL` (limitación documentada en `CLAUDE.md`).
- **Sin oráculo de enumeración:** email duplicado, contraseña débil (< 8), email mal formado o campo
  en blanco devuelven el mismo `400` con el mismo mensaje genérico (mismo principio que el `401`
  uniforme del login). La violación real de `UNIQUE(email)` propaga fuera del `@Transactional` para
  que se deshaga también la `Gestoria` (sin huérfanas) y la captura el controlador.
- **Frontend:** `RegistroPage` (misma identidad visual), enlazada desde `LoginPage` ("¿No tienes
  cuenta? Regístrate"); redirige a la URL de Stripe Checkout.

**Verificación:** suite completa a **135 tests** (110 + 25 nuevos: `RangoClientesTest`,
`RegistroGestoriaValidacionTest`, `RegistroGestoriaServiceTest`, `RegistroGestoriaControllerTest`,
`RegistroGestoriaEndToEndTest` y uno más en `StripeCheckoutServiceTest`). Smoke test H2 por HTTP
real (ruta realmente pública + login/`/auth/me` con las credenciales recién registradas) y
navegador real (etiquetas del `Select`, mismo mensaje para contraseña débil y email duplicado,
mensaje de "facturación no configurada"). **Pendiente:** redirección real a Stripe Checkout, que
necesita el `Price` real de Antonio.

**Bugs detectados después, en la revisión completa del 2026-09-25 (pendientes):**
`invoice.payment_failed` sobre una suscripción ya en `TRIAL_EXPIRADO_SIN_PAGO` la pasa a
`IMPAGO_GRACIA` (reabre la aprobación), y una prueba cuyo checkout se abandona queda en `TRIAL`
para siempre (la fila se crea antes de completar el checkout, también desde este alta pública).
Detalle en `CLAUDE.md`.

---

## Prompt A1 — Contactos, crotales en trámites y revisión editable (completado, 2026-09-28, commit `2538583`)

Prepara el terreno de 3b (WhatsApp + IA) **sin depender de OVZ.net**: que el sistema sepa a qué Explotación(es) pertenece un teléfono, que un Trámite guarde los crotales mencionados y que la gestoría pueda corregir el Trámite antes de aprobarlo. **Solo backend**: nada de frontend, IA, Twilio ni Stripe. Plan con las 31 decisiones cerradas con Antonio en `docs/superpowers/plans/2026-09-25-promptA1-contactos-crotales-revision.md`; ejecutado con el flujo Superpowers (un subagente implementador y un revisor independiente por tarea; informes en `.superpowers/sdd/a1-*`).

Entregado (detalle completo en `CLAUDE.md`, bullet "Prompt A1" bajo Technical decisions):
- **Contactos (Tasks 1–2):** `Contacto` pasa a `GestoriaScopedEntity` (`V15`) con `telefono` todavía `UNIQUE` global (decisión 1: limitación y camino futuro documentados); rol en la relación (`ContactoExplotacion.rol`, `RolContacto` `TITULAR`/`EMPLEADO`; se borra `TipoContacto`); sin límite de una Explotación por empleado; borrado lógico `activo`; `TelefonoNormalizador` a E.164. CRUD `/contactos` (listar con `incluirInactivos`, crear, editar, borrar lógico, reactivar, enlazar/desenlazar Explotaciones).
- **Importador (Task 3):** hoja "Contactos" opcional (`telefono, nombre, codigo_explotacion, rol`), por fila en `REQUIRES_NEW`, siempre con `findByGestoriaIdAndTelefono`, nunca `findByTelefono`. Un teléfono de otra Gestoría da un error de fila genérico.
- **Lectura (Task 4):** `GET /ganaderos`, `GET /ganaderos/{id}` (explotaciones con contactos activos y rol, sin credenciales OVZ), `GET /explotaciones/{id}/animales`.
- **Crotales en trámites (Task 5):** tabla `tramite_crotal` (`V16`), `CrotalNormalizador` (completo/incompleto), resolución `EN_INVENTARIO`/`AMBIGUO`/`NO_ENCONTRADO`/`SIN_EXPLOTACION` dentro de la Explotación del Trámite; crotales en `GET /tramites` con una sola consulta por página.
- **Revisión editable (Task 6):** `PATCH /tramites/{id}` (explotación, tipo, lista completa de crotales), bloqueo `PESSIMISTIC_WRITE` + refresh, aprobar/rechazar solo desde `PENDIENTE_REVISION`, regla de aprobación ampliada (explotación y tipo obligatorios; `AMBIGUO`, `SIN_EXPLOTACION` y `NO_ENCONTRADO` incompleto bloquean; dos crotales al mismo Animal → `409`) y re-resolución al aprobar (`ResolucionCrotalesCambiadaException`: el único `409` que confirma datos).
- **Cierre (Task 7a):** versión optimista `@Version` (`V17`; `PATCH` y aprobar exigen `version`), formato provisional de crotal para aprobar (`ES` + 12 dígitos, u otro país 2 letras + 8–12, también sobre el crotal del Animal en inventario), `/error` público (un cuerpo mal formado da `400` y no `401`), listas blancas de `sort` en todos los listados, mensaje neutro del importador para identificadores de otra Gestoría y eliminación de los finders sin scope (`findByCodigoRega`/`findByNif`/`findByCrotal`), normalización de crotales en el importador.

**Verificación:** suite completa a **415 tests** con `./mvnw clean test` (135 antes de A1). Smoke test HTTP real con H2 en fichero (`AUTO_SERVER=TRUE` para sembrar un Trámite por SQL): onboarding → login → importar un Excel con hoja Contactos → `GET /ganaderos/{id}` con contactos y rol → `GET /contactos` (y `?sort=noExiste` → `400`) → aprobar sin `version` `400`, con ella `409` con motivo → `PATCH` `200` (versión 0 → 1) → `PATCH`/aprobar con versión antigua `409` → aprobar con la nueva `200 APROBADO` → cuerpo mal formado `400`, no `401`.

**Hallazgo de proceso:** la sesión de la Task 7a se cortó a mitad; al retomarla, un `./mvnw clean test` reveló que el código heredado **no compilaba con `javac`** (el recuento de "387 tests" venía de clases compiladas por VS Code en `target/`). Desde entonces se valida siempre con `clean` (anotado en `CLAUDE.md`).

**Pendientes para el Prompt A2 (frontend) — todos resueltos en A2:**
- **Aprobar estaba roto en la UI hasta A2:** el frontend llama a `aprobar` sin cuerpo → `400` siempre (decisión 27), salvo el `403` de suscripción bloqueada o inexistente, que va antes y sigue mostrando su mensaje específico. A2 debe enviar la `version` (de `TramiteDetalleResponse.version`) en `PATCH` y `aprobar`.
- Ante **cualquier** `409`: recargar el detalle y mostrar el `motivo` del backend, no un "Inténtalo de nuevo" genérico (revisiones R1 y M5).
- Ocultar o deshabilitar Aprobar/Rechazar fuera de `PENDIENTE_REVISION`.
- UI de `PATCH` para asignar explotación, tipo y crotales. Ojo (M7): `PATCH` no puede vaciar la explotación ni el tipo (`null` = no cambiar); `crotales: []` sí quita todos los crotales.

**Otras notas abiertas:** los `Animal.crotal` importados antes de A1 no se migraron (un crotal antiguo con separadores o minúsculas puede duplicar el Animal al reimportar, y queda bloqueado al aprobar); la regla de formato de crotal de otros países es provisional; el mensaje neutro del importador también cubre violaciones que no son de unicidad (NIF o nombre demasiado largos).

---

## Prompt A2 — Frontend: Ganaderos, animales y revisión editable de trámites (completado, 2026-10-02, commit `ae90295`)

Pone la UI al día con el backend de A1. **Solo frontend**: `backend/` no se tocó. Plan con 31 decisiones cerradas con Antonio en `docs/superpowers/plans/2026-09-28-promptA2-frontend-revision.md`; flujo Superpowers (implementador + revisor independiente por tarea; informes en `.superpowers/sdd/a2-*`, que no se suben). Diseño **solo con la skill Impeccable** (`critique` de la cola, `craft` de Ganaderos y del modal, `audit` + `polish` al final), ejecutada desde la sesión principal (decisión 27); contratos de dirección en `.impeccable/surfaces/`.

Entregado (detalle en `CLAUDE.md`, "Architecture notes (frontend)", y patrones visuales en `DESIGN.md`):
- **Tests (Task 1):** Vitest + jsdom + Testing Library + MSW (la API se simula a nivel HTTP y se prueba el cliente axios real). `npm test`.
- **Cliente HTTP y errores (Task 2):** `ErrorApi { tipo, status?, motivo? }` y `mensajeDeError`; ninguna pantalla usa `axios.isAxiosError`; el `401` de `/auth/login` no cierra sesión; el texto plano del `400` del importador se toma como `motivo`.
- **Sesión (Task 3):** token en `sessionStorage` (`ganera.token`), comprobado con `GET /auth/me` al arrancar; un `401` avisa de sesión caducada; un corte de red no desloguea.
- **Marca (Task 4):** `LogoGanera` (máscara CSS sobre `currentColor`) en la barra, login y registro; `index.html` en español con `favicon-64.png`; se borraron `favicon.svg`/`icons.svg` de Vite y `preview.png` salió de `public/` (decisión 26).
- **Etiquetas y badges (Task 5):** una sola fuente, `features/tramites/etiquetas.ts` (`TIPOS_TRAMITE` es la constante que cambiará en el prompt B); el resumen del importador muestra la hoja Contactos.
- **Cola (Task 6):** critique aplicada; abre filtrada por "Pendiente de revisión" (decisión 28); filas accesibles por teclado; código REGA en vez del id (lista completa de explotaciones, decisiones 20 y 22); crotales con su badge.
- **Ganaderos (Task 7) y animales (Task 8):** `/ganaderos` (ordenable por nombre y NIF) y `/ganaderos/:id` (secciones por explotación, contactos con `tel:` y rol, índice con más de 3 explotaciones, "Ganadero no encontrado" para un `404`); panel "Ver animales" paginado en Explotaciones y en el detalle (no hay ruta `/explotaciones/:id`, H1).
- **Modal de revisión (Tasks 9a y 9b):** editable solo en `PENDIENTE_REVISION`; combobox de explotación, tipo y lista de crotales; `PATCH` y aprobar con `version`; cualquier `409` recarga el detalle y muestra el `motivo`; un `400` conserva las ediciones; rechazar con confirmación en línea; "Sin guardar" en vez de badges que predicen.
- **Audit + polish (Task 10):** 0 fallos de contraste (axe) a 375, 640 y 1440 px; regiones `status` siempre montadas; esqueleto de carga en la cola; "+N más" desplegable; `CLASE_ENLACE` en `shared/ui`; sin scroll lateral en tablas con nombres largos.

**Verificación (Task 11):** `npm test` **475/475** (36 ficheros); `npm run build` en verde (solo el aviso del chunk de 622 kB); `npm run lint` con los 3 avisos `only-export-components` de siempre; `./mvnw clean test` **415/415**. Smoke en navegador real (Playwright por npm fuera del repo, Vite, backend con H2 en fichero) con dos gestorías: login y recarga sin perder la sesión → importación desde la UI con hoja Contactos (reimportar no duplica) → Ganaderos → detalle con contactos y `tel:` → animales paginados → cola filtrada → `PATCH` con `version` (crotales `EN_INVENTARIO`) → aprobar con `version` `200` → aprobar sin tipo `409` con su motivo → `409` de versión desfasada con recarga y motivo → rechazar con confirmación → `APROBADO` en solo lectura → Facturación. Gestoría B (suscripción `SUSPENDIDA`, a 375 px): banner, solo su trámite en la cola, aprobar `403` con el texto de suscripción y sin cerrar sesión, `/ganaderos/{id de A}` → "Ganadero no encontrado", "Salir" borra el token. Procesos parados y H2/temporales borrados.

**Pendientes que deja A2 (ninguno bloquea):** la barra de navegación en móvil (ver más abajo); code-splitting del chunk de 622 kB; el aviso de "más de 100 coincidencias" del combobox sin comprobar con un lector de pantalla real (n2 de la 9b); los minors m1–m4 de la revisión de la Task 10; la tarjeta del resumen del importador dice "1 filas" / "1 actualizadas" (la frase anunciada ya usa plurales reales).

---

## Mini-prompt de backend tras A2 (completado, 2026-10-02)

Huecos pequeños de la API que el frontend de A2 rodea. Plan con las decisiones cerradas con Antonio en `docs/superpowers/plans/2026-10-02-mini-prompt-backend-tras-a2.md`; flujo Superpowers (T1–T4 con implementador + revisor independiente, todas **Approved** solo con minors; informes en `.superpowers/sdd/mp-*`, que no se suben). Sin migraciones.
- `GET /explotaciones/{id}` (H1-B) con `findByIdAndGestoriaId`, mismo DTO que el listado; `404` sin cuerpo si es ajena o no existe.
- `{motivo}` en el `403` de aprobar (H2), con el mismo texto que ya enseña el frontend; sigue siendo la primera comprobación.
- `?q=` en `GET /explotaciones` (H3): "contiene", sin distinguir mayúsculas, sobre código REGA, nombre de la explotación y nombre del ganadero; **sin quitar acentos** (ver Prompt C); `%`, `_` y `!` literales; más de 100 caracteres → `400 {motivo}`.
- `version` obligatoria al rechazar (H4): `400` sin versión (antes de buscar) → `404` → `409` estado → `409` versión → `200`. **Única excepción a "solo backend":** `rechazarTramite` en el frontend envía `{version}` para que Rechazar no quede roto en `main`.
- `explotacionCodigoRega` y `explotacionNombre` en `TramiteResponse` (H7), con `@EntityGraph` y un test de recuento de SQL contra el N+1.
- **N+1 preexistente arreglado (añadido por Antonio antes del commit):** `GET /explotaciones` sin `q` cargaba el ganadero de cada fila por separado; ahora `findByGestoriaId` lleva `@EntityGraph(attributePaths = "ganadero")`, con un test que cuenta las consultas (cero cargas sueltas, página + count).
- `400` del importador como `{motivo}`; el tipo de fichero se detecta por contenido: `.xls` → "Excel antiguo… guárdalo como .xlsx"; cualquier otro → "El fichero no es un Excel .xlsx válido."; nunca el mensaje de POI.
- `400` del registro como `{motivo}`, con el mismo texto uniforme (se borra `RegistroErrorResponse`).
- `completo` en `TramiteCrotalResponse`: describe **lo escrito** (`crotalIndicado`), no lo resuelto.

**Verificación:** `./mvnw clean test` **472/472** (415 antes); `npm test` **476/476**; `npm run build` y `npm run lint` en verde (avisos conocidos). Smoke con `curl` contra dos gestorías (H2 en fichero): 10/10 pasos en verde (`.superpowers/sdd/mp-t5-smoke.md`; hecho antes del arreglo del N+1, que cubre su propio test).

**Pendientes que deja (ninguno bloquea):**
- **Frontend sin consumir lo nuevo:** pasa a la tarea de frontend antes del piloto (ver más abajo).
- Minors de las revisiones: `GET /explotaciones/importar` da `400` en vez de `405`; la longitud de `q` cuenta unidades UTF-16; un `IOException` real del servidor al leer el fichero subido se vería como "fichero no válido".

---

## Tarea de frontend antes del piloto (completada, 2026-10-04)

Solo `frontend/` (el backend ya está listo desde el mini-prompt tras A2). Cada punto con su test (Vitest + MSW); `npm test`, `npm run build` y `npm run lint` en verde, y lo visual verificado en un navegador real (Playwright por npm fuera del repo).

1. **Barra de navegación en móvil.** La barra superior no está adaptada a pantallas estrechas: a 375 px desborda y ensancha la página (scroll lateral). El gestor aprueba a veces desde el teléfono (`PRODUCT.md`), así que hay que resolverlo antes de la gestoría piloto. Con la skill Impeccable, sin cambiar paleta ni tipografía, y verificado en un navegador real a 375 y 640 px.
2. **Plurales del resumen del importador.** La tarjeta del resumen (`ImportarExcelSection`) dice "1 filas" y "1 actualizadas"; debe usar singular con 1, como ya hace la frase anunciada a lectores de pantalla ("1 fila con error", "1 creada / 1 actualizada").
3. **`?q=` en el combobox de explotación del modal de revisión.** Buscar en el backend (`GET /explotaciones?q=`, que busca en código REGA, nombre de la explotación y nombre del ganadero, sin distinguir mayúsculas ni quitar acentos) en vez de cargar la lista completa con `todasLasExplotaciones.ts` y filtrar en cliente. Con espera entre pulsaciones, cancelación de la petición anterior y los estados de carga, error y vacío de siempre; más de 100 caracteres da `400 {motivo}`.
4. **`explotacionCodigoRega` del listado en la cola.** La columna Explotación de `TramitesPage` usa `explotacionCodigoRega`/`explotacionNombre` de `GET /tramites` en vez de cruzar ids con la lista completa. Si tras los puntos 3 y 4 nadie más usa `todasLasExplotaciones.ts`, se elimina.
5. **`completo` en los crotales.** Pintar `NO_ENCONTRADO` incompleto en ámbar ("No está en el inventario · incompleto") a partir de `TramiteCrotalResponse.completo` (decisión 21 de A2), sin clasificar crotales en el frontend. `completo` describe lo escrito (`crotalIndicado`), no lo resuelto.
6. **`motivo` del 403 de aprobar.** Mostrar el `motivo` que ya manda el backend en vez del texto fijo del contexto (hoy son el mismo texto; el fijo queda como reserva si no llega `motivo`).
7. **Comentarios desfasados de `frontend/src/shared/api/errores.ts`.** Dicen que el 400 del importador es texto plano, que el registro devuelve `{mensaje}` (y nombran `MENSAJE_REGISTRO_INVALIDO`) y que el 403 de aprobar no trae cuerpo; los tres son ya `{motivo}`. El registro sigue enseñando su propio texto uniforme.

Plan con las decisiones cerradas: `docs/superpowers/plans/2026-10-03-tarea-frontend-antes-piloto.md`.

**Cerrada el 2026-10-04:** los 7 puntos, con un implementador y un revisor independiente por tarea (T1–T5, todas aprobadas con minors) y la navbar con Impeccable (Antonio aprobó el shape antes del craft). Verificado con `npm test` 506/506, `npm run build` y `npm run lint` en verde, `./mvnw clean test` 472/472 y un smoke en navegador real con dos gestorías a 1440/768/640/375 px (`.superpowers/sdd/fp-t6-smoke.md`). El N1 del smoke (el foco de Tab caía en un enlace de la tira cortado por el borde) se arregló antes del commit. Quedan anotados, por decisión de Antonio, dos detalles no bloqueantes: "Reintentar" del combobox no se alcanza con Tab, y la lista del combobox se vacía mientras busca (salto de altura).

---

## Backend — bloqueante antes del piloto (hecho, 2026-10-04)

1. **Búsqueda de explotaciones sin tildes y por palabras.** Al pasar el combobox del modal de revisión a `GET /explotaciones?q=` (punto 3 de la tarea de frontend), se perdió lo que hacía el filtro en cliente: hoy "martinez" no encuentra "Martínez", y "ES12 Pérez" no encuentra nada porque `q` se busca como una sola cadena dentro de un único campo. Antonio aceptó la regresión solo hasta el piloto. Propuesta:
   - una columna de búsqueda normalizada en `explotacion`, calculada en Java al guardar (minúsculas, sin tildes, con el código REGA, el nombre de la explotación y el nombre del ganadero), y recalculada cuando cambie cualquiera de los tres, incluido el nombre del ganadero (que vive en otra tabla);
   - `q` se normaliza igual en Java, se parte en palabras y cada palabra debe aparecer (AND) en esa columna;
   - independiente de la base de datos (mismo comportamiento en H2 y PostgreSQL), **sin `unaccent`**;
   - migración que rellene la columna de las filas existentes.

   Sustituye a la nota de `unaccent` de las notas del Prompt C.

   **Hecho (2026-10-04)**, con el plan `docs/superpowers/plans/2026-10-04-busqueda-explotaciones-sin-tildes.md`.
   Al final no hay una columna desnormalizada, sino **una por entidad**: `explotacion.busqueda` (REGA +
   nombre) y `ganadero.nombre_busqueda`. Cada una se recalcula en los setters de su propia entidad,
   así que renombrar un ganadero no deja nada desfasado. La regla está en `shared/texto/NormalizadorBusqueda`:
   - quita los acentos sueltos antes de NFKD; quita tildes y diéresis; pliega ñ→n y ç→c; pasa a minúsculas;
   - elimina sin dejar hueco guiones, signos y comodines;
   - no pliega `ß`, `ł`, `ø` ni `æ`, a sabiendas.

   La búsqueda va por palabras en AND, con un máximo de 8 (más → `400` con motivo); si todas las palabras
   quedan vacías, devuelve una página vacía. La migración Java `V18` rellena las filas existentes por JDBC.
   Tests: 533/533. Smoke en dos fases: datos importados con `main` en la V17 y después la V18 aplicada sobre
   esa misma base. En el frontend solo se ha cambiado el JSDoc de `frontend/src/features/explotaciones/api.ts`
   (un comentario, sin código), como excepción explícita de Antonio.

---

## Orden de trabajo desde el 2026-10-04 (cambio de rumbo: el cobro pasa a la landing)

1. **Quitar el pago de la app** (hecho el 2026-10-04; backend 534/534, frontend 508/508, smoke en navegador con dos gestorías; plan `docs/superpowers/plans/2026-10-04-quitar-pago-de-la-app.md`).
   Salen la página de Facturación, su enlace en la navbar y `POST /facturacion/checkout`. Se quedan los
   webhooks de Stripe, los estados de suscripción, el `SuscripcionBanner` y el 403 al aprobar. Los textos que
   mandaban a Facturación pasan a pedir que se contacte con Ganera. El Registro no se toca hasta el C.
2. **Colores y tipografía** de la marca nueva (hecho el 2026-10-05; frontend 512/512, backend 534/534,
   smoke en navegador a 1440 y 375 px; plan `docs/superpowers/plans/2026-10-04-colores-y-tipografia.md`).
   Paleta roja y gris de la landing con la regla "el rojo sólido actúa, el rojo oscuro teñido avisa",
   Archivo autoalojada, iconos en las alertas de error, favicon y logos en rojo. `DESIGN.md` y `PRODUCT.md`
   al día. **Pendiente:** los radios (la landing usa esquinas rectas), a decidir con el socio.
3. **Prompt B** (WhatsApp + IA, sin OVZ), al que se añade el **alta por nacimiento con crotal de la madre,
   sexo y fecha de nacimiento**: extracción y campos en el modal de revisión.
4. **Prompt C**, después la app de escritorio, el MVP a la gestoría piloto y, tras el piloto, OVZ 3a/3c.

## Prompt B — notas acumuladas (pendiente)

- **Alta por nacimiento (2026-10-04):** tipo de trámite con el crotal de la madre, el sexo y la fecha de
  nacimiento del ternero. La IA los extrae y el modal de revisión tiene campos para verlos y corregirlos.
  Venta con comprador, censo y demoras quedan para más adelante.
- **Requisito: la IA solo extrae del mensaje (2026-10-05):** tipo, crotales o últimos dígitos, fechas… Nunca
  recibe el inventario de animales; el cruce con el inventario lo hace el servidor. Así se evitan coste y
  envío de datos de más.

Además del catálogo real de tipos de trámite (ver los pendientes heredados en el Prompt 3b):
- **Medir el uso de IA por gestoría:** número de mensajes procesados y tokens consumidos (entrada y salida) por Gestoría, para conocer el coste real por cliente.
- **Si la API de Anthropic falla, no se pierde nada:**
  - el mensaje de WhatsApp se guarda igual (`MensajeCampo`, con la misma idempotencia por `MessageSid`);
  - el trámite se crea con el texto en bruto, en `PENDIENTE_REVISION`, para revisión manual;
  - la extracción se reintenta después;
  - la cola muestra un aviso visible en esos trámites ("no se ha podido extraer automáticamente"), para que nadie los confunda con una extracción vacía.

---

## Prompt C — notas acumuladas (pendiente)

- **Stripe: bugs conocidos, pendientes (detectados el 2026-09-25; siguen fuera de "quitar el pago de la app"):**
  1. `invoice.payment_failed` sobre una suscripción que ya está en `TRIAL_EXPIRADO_SIN_PAGO` la pasa a
     `IMPAGO_GRACIA`, lo que vuelve a permitir aprobar. Arreglarlo empezando por el test.
  2. Una prueba cuyo Checkout se abandona se queda en `TRIAL`, con derecho a aprobar, para siempre. Desde que
     se quitó el pago de la app, solo puede ocurrir por el Registro. Antes de arreglarlo hay que decidir el
     diseño; encaja con el alta desde la landing (prueba de 15 días sin cobro automático).
- **`SuscripcionSyncScheduler`:** cada noche pasa a Stripe la cantidad de explotaciones de las suscripciones
  `ACTIVA`/`IMPAGO_GRACIA`. Hay que revisarlo cuando el cobro pase a planes por número de ganaderos
  (25/75/200). Tampoco corrige nunca una suscripción en `TRIAL`.
- **Prueba de volumen antes del piloto (2026-10-05):** con datos inventados realistas (unos 75 ganaderos y
  200 animales por explotación), midiendo importación, listados, cola y modal.

- **Índice de búsqueda de explotaciones, si va lenta (2026-10-04):** en PostgreSQL, un `pg_trgm` GIN sobre
  `explotacion.busqueda` y `ganadero.nombre_busqueda`. Esas columnas ya están normalizadas, así que no hace
  falta `unaccent`. H2 no lo tiene: tendría que ser una migración solo para Postgres. Hoy no hay índice:
  `like '%p%'` no usa B-tree, y la consulta va acotada por `gestoria_id`.
- **Superada (2026-10-03):** pasa a "Backend — bloqueante antes del piloto", con otra solución (columna normalizada en Java, sin `unaccent`). Se conserva como contexto. **Búsqueda sin acentos en `GET /explotaciones?q=`:** hoy "Maria" no encuentra "María". En PostgreSQL, con la extensión `unaccent` (p. ej. `lower(unaccent(...)) like lower(unaccent(:patron))`, con una función `IMMUTABLE` envoltorio si se indexa) y, si la búsqueda se vuelve lenta con miles de explotaciones, un índice `pg_trgm` (GIN) sobre esa expresión. H2 no tiene `unaccent`: los tests necesitarán un alias de función en H2 o una normalización equivalente en Java para el patrón.

---

## Preguntas abiertas para la gestoría piloto

- ¿Los ganaderos mandan **texto, audios o fotos** por WhatsApp? Cambia el alcance de la extracción (transcripción de audio, lectura de imágenes de crotales o documentos) y del modal de revisión.

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