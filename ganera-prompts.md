# Ganera — Prompts para Claude Code

Documento de referencia con todos los prompts, en orden, tal como se han ido cerrando. Cada uno se lanza en la **misma sesión continua** de Claude Code (para que mantenga el contexto), salvo que se indique lo contrario.

Estado actual: **Prompts 0, 1, 2 y 2.5 completados y verificados end-to-end.** Repo en GitHub (`github.com/AntonioMenor01/ganera-core`). Login, JWT y onboarding manual de gestorías piloto probados con curl y funcionando. Siguiente paso: Prompt 2.7 (Stripe).

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

---

## Prompt 2.7 — Integración Stripe completa (pendiente de redactar)

Contenido a incluir:
- Checkout de Stripe (por número de explotaciones, `licensed` price, trial de 15 días).
- Webhook completo: `checkout.session.completed`, `invoice.payment_failed` → `IMPAGO_GRACIA`, `customer.subscription.updated` (unpaid) → `SUSPENDIDA`.
- Job nocturno de sincronización de `quantity` (nº de explotaciones activas) con Stripe.
- Método `puedeAprobarTramites(gestoriaId)` que bloquea el botón de aprobar trámites cuando el estado es `TRIAL_EXPIRADO_SIN_PAGO` o `SUSPENDIDA` (pero mantiene acceso de solo lectura).

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

## Prompt 3d — Importador Excel + controladores REST (pendiente de redactar)

Fallback manual para cargar inventario cuando la sincronización automática (3a) no esté disponible o falle. Dashboard, cola de trámites, endpoint de aprobación.

---

## Prompt 4 — Frontend completo (pendiente de redactar)

Incluirá, además de lo ya scaffoldeado en el Prompt 1:
- Onboarding de un Ganadero nuevo (conectar credenciales OVZ + disparar sincronización inicial).
- Pantalla de suscripción/facturación con aviso visible de impago o trial expirado.
- Cola de trámites con modal de revisión (texto original WhatsApp + JSON extraído + edición manual + resolución de explotación ambigua).

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