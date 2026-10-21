# SDD Progress — Ganera

Registro de tareas cerradas (qué se hizo, commit, estado de revisión). Una línea por hito.

## 2026-07-09 — Sesión autónoma (Prompt 2.5)

- **Diagnóstico inicial**: la sesión anterior se cortó tras commitear solo los planes
  (`a8e56df` — planes de Prompt 2 y 2.5). No había código de Prompt 2.5 (ni paquete `auth` ni
  `onboarding`), ni archivos sin commitear, ni procesos java huérfanos (el único java.exe era el
  language server de VS Code — no se tocó). `.superpowers/sdd/` no existía; se crea con este
  archivo. Nota: la sección "Current status" de `CLAUDE.md` estaba adelantada a la realidad
  (describía 2.5 y 2.7 como completos sin estarlo) — pendiente de corregir al cierre de 2.5.
- Decisión: empezar Task 1 de Prompt 2.5 de cero (no había nada que reutilizar).
- **Prompt 2.5 / Task 1** (`AuthService` + `AuthController`, `POST /auth/login` + `GET /auth/me`):
  implementada TDD según el plan, commit `2d49f3e`, suite 36 tests en verde. Revisión: **Approved**
  con 3 Minor. El Minor 1 (password null → 500, oráculo de enumeración) se arregló en `5eb2b97`
  (guard + test nuevo, 37 tests). Minor 2 (`orElseThrow` en /auth/me si el Usuario se borrara) y
  Minor 3 (javadoc atribuye a gestoriaFilter lo que garantiza la firma del JWT) anotados, sin acción.
- **Prompt 2.5 / Task 2** (`OnboardingController`, `POST /internal/onboarding/gestoria`): implementada
  TDD según el plan, commit `d87d207`, suite 43 tests en verde, smoke test H2 E2E completo
  (onboarding 200 → login 200 → /auth/me 200; sin token 401, password mala 401, secreto malo 401),
  proceso java del smoke limpiado. CLAUDE.md corregido: ya no afirma que Prompt 2.7/Stripe esté
  hecho (estaba adelantado a la realidad). Revisión: **Approved** con 2 Minor (nulls/email duplicado
  → 500 en endpoint interno protegido; aceptado, fuera de alcance del plan). **PROMPT 2.5 CERRADO.**
- **Prompt 2.7 (Stripe) — SOLO PLAN, sin implementar**: brainstorming resuelto y plan completo
  escrito en `docs/superpowers/plans/2026-07-09-prompt2.7-stripe.md`, commit `5466616`. 4 tareas
  (V13 + obtenerOCrearSuscripcion; checkout; máquina de estados webhook; scheduler nocturno + smoke).
  **PARADO AQUÍ a propósito**, como pediste: no se dispatchó ningún subagente de implementación de
  2.7 — el plan tiene un gate humano explícito en cabecera (revisarlo contigo antes de tocar Stripe;
  solo claves test; el Price del dashboard lo creas tú a mano).
- Estado final de la sesión: rama `main` limpia (salvo `.superpowers/` sin trackear, deliberado),
  43 tests en verde, ningún java.exe huérfano. Nada pendiente de mi lado salvo tu revisión del plan 2.7.

## 2026-07-09 — Prompt 2.7 (Stripe) — ejecución aprobada por Antonio

- Antonio revisó el plan, pidió añadir la decisión sobre re-entrega/desorden de webhooks (guard
  monotónico por `event.created`, sin dedupe por `event.id` en esta fase — commit `2abf564`) y
  aprobó dispatchar. Restricciones recordadas a cada implementador: solo claves `sk_test_`/`whsec_`,
  el Price del dashboard lo crea Antonio a mano.
- **Prompt 2.7 / Task 1** (V13 + campos Stripe en `Suscripcion` + `obtenerOCrearSuscripcion`
  idempotente + `countByGestoriaId`): commit `c0ff6a5`, suite 47 tests en verde, V13 verificada en
  vivo con H2+Flyway ("Successfully applied 13 migrations"). Revisión: **Approved** con 2 Minor.
  Minor 2 (comentario sobre el camino de FK violation) aplicado a mano. Minor 1 (si Task 2 envuelve
  el checkout en `@Transactional`, el saveAndFlush fallido del perdedor de la carrera marcaría
  rollback-only → `UnexpectedRollbackException`) → trasladado como advertencia al implementador de
  Task 2: NO envolver el checkout en una transacción propia.
- **Hallazgo preexistente del revisor (no de esta task, anotado para no perderlo):**
  `MensajeCampo.cuerpo` es `@Lob` pero `V9` lo creó como `TEXT` — en H2 modo PostgreSQL con
  `ddl-auto=validate` la validación de esquema falla (CLOB vs VARCHAR). No afecta al smoke
  documentado (usa `ddl-auto=update` sin Flyway) ni a Postgres real (`ddl-auto=none`). Tenerlo en
  cuenta si algún smoke usa `validate`.

## 2026-07-09 (noche) — Continuación tras corte por créditos en otro dispositivo

- **Diagnóstico**: esta sesión (dispositivo distinto, misma cuenta) tenía HEAD en `f73fbca`
  (cierre de Prompt 2), 9 commits por detrás de `origin/main`. `git fetch` + comparación confirmó
  los 9 commits remotos (Prompt 2.5 completo, plan de 2.7 aprobado con la decisión de webhooks,
  Task 1 de 2.7 con su fix de revisión) terminando en `1e1b2a3`, tal como describió Antonio.
  `git status` local limpio (nada que perder) → `git pull --ff-only origin main` sin conflicto.
  Verificado tras el pull: suite completa 47/47 en verde (`BUILD SUCCESS`), migraciones V1-V13
  presentes. No existía ningún `task-2-brief`/`task-2-report` de Prompt 2.7 en `.superpowers/sdd/`
  ni archivos sin commitear — la Task 2 (`StripeConfig`/`StripeCheckoutService`/`CheckoutResponse`/
  `FacturacionController`) no se había empezado en absoluto (ni local ni remoto). Decisión: arrancar
  Task 2 de cero siguiendo el plan tal cual, sin recrear nada de Task 1.
- Advertencia trasladada por el revisor de Task 1 (Minor 1, aún vigente): NO envolver
  `crearSesionCheckout` en una transacción propia — el `saveAndFlush` fallido del perdedor de la
  carrera en `obtenerOCrearSuscripcion` marcaría la transacción como rollback-only si hubiera una
  envolvente, y lanzaría `UnexpectedRollbackException` en vez de recuperarse limpiamente.
- **Prompt 2.7 / Task 2** (`StripeConfig` + `StripeCheckoutService` + `POST /facturacion/checkout`):
  commit `e1c784e`, suite 52/52 en verde, `mvnw compile` limpio, smoke test H2 manual confirmó 401
  (sin JWT) y 503 (Stripe sin configurar). Firmas del SDK stripe-java 28.2.0 verificadas con `javap`
  contra el jar real — sin desviaciones del plan. Revisión: **Approved** sin Critical/Important, 1
  Minor (success/cancel URL hardcodeadas sin `${ENV:default}` — aceptado, el brief no pedía
  respaldo por variable de entorno ahí; revisar cuando exista la página real de facturación en el
  frontend). El servicio añadió `SuscripcionRepository` como 4ª dependencia (no listada en el brief)
  para persistir `explotacionesContratadas` sobre la entidad detached que devuelve
  `obtenerOCrearSuscripcion` — verificado correcto por el revisor (no reintroduce el riesgo de
  transacción de la advertencia de Task 1, no hay `@Transactional` en ningún lado de esta task).
- **Prompt 2.7 / Task 3** (`StripeWebhookService` — máquina de estados + guard monotónico por
  `event.created`): commit `1b4d753`, suite 73/73 en verde. Tabla de 9 casos de
  `calcularEstadoTrasActualizacion` y `esEventoObsoleto` verificadas contra la Decisión de diseño 4
  del plan, con test de integración que prueba el efecto real del guard (reentrega tardía de
  `checkout.session.completed` tras un `payment_succeeded` más reciente NO regresa el estado ni el
  epoch). `StripeWebhookController` solo ganó la inyección + el despacho — verificación de firma
  intacta byte a byte. Revisión: **Approved** sin Critical/Important, 2 Minor sin acción (un helper
  de test muerto sin llamar; `customer.subscription.deleted`→`CANCELADA` sin test dedicado del
  guard en ese path concreto, consistente con que los `manejarXxx` no llevan cobertura directa).
- **Prompt 2.7 / Task 4** (`SuscripcionSyncScheduler` + cierre del prompt): `calcularAjustes`
  (pura, sin tipos del SDK de Stripe) verificada con 6 casos: ignora sin `stripeSubscriptionId`,
  no ajusta cuando la cantidad ya coincide, ajusta cuando difiere, aplica `max(1, count)` cuando el
  count real es 0 (con y sin ya estar en 1), y filtra correctamente entre varias suscripciones
  mezcladas. `pushCantidadAStripe` (retrieve + update de quantity del primer `SubscriptionItem`,
  proración por defecto — sin `setProrationBehavior` explícito) queda sin cobertura unitaria directa
  por convención. Suite completa 79/79 en verde (73 + 6 nuevos). Smoke test H2: app arranca limpio
  con `@EnableScheduling` y el nuevo bean sin errores de wiring; `POST /facturacion/checkout` → `401`
  sin JWT y `503` con JWT real (onboarding→login) por falta de credenciales Stripe;
  `POST /webhooks/stripe` → `400` con firma inválida; full-suite confirma las 13 migraciones
  validando contra H2 real. `CLAUDE.md` y `ganera-prompts.md` actualizados para reflejar Prompt 2.7
  como completado, incluyendo la limitación conocida del guard por `event.created` (sin dedupe por
  `event.id`) y que `STRIPE_PRICE_ID_EXPLOTACION` sigue pendiente de la acción manual de Antonio.
  Commit `7a434d9`. Revisión: **Approved** sin Critical/Important. Firmas del SDK (`Subscription.retrieve`,
  `SubscriptionItemCollection`, `SubscriptionUpdateParams.Item`) re-verificadas de forma independiente
  por el revisor contra el jar real — coinciden. Try/catch por-ajuste confirmado dentro del bucle (no
  envolviendo el batch entero), evitando el mismo tipo de riesgo señalado en Task 1. 2 Minor sin
  acción (narrowing `long`→`int` sin `Math.toIntExact`; una frase de `ganera-prompts.md` podría leerse
  como que el checkout real ya se probó end-to-end, pero va seguida de la aclaración correcta). El
  smoke test/79-suite en sí no es re-verificable desde el diff (requiere arrancar/matar una JVM) — el
  revisor lo marcó plausible pero no confirmado, consistente con logs previos del proyecto.

**PROMPT 2.7 (STRIPE) CERRADO — plan completo, 4/4 tareas aprobadas.**

## 2026-07-14 — Prompt 3d (importador Excel + controladores REST básicos)

- Antonio pidió continuar con el siguiente prompt del roadmap. 3a/3b/3c siguen bloqueados (sin
  credenciales OVZ.net todavía); 3d no depende de OVZ.net, así que se adelantó.
- **Confirmación previa (sin plan escrito aparte — el prompt de Antonio, refinado con 3 preguntas
  de aclaración, hizo de spec):** revisé entidades/migraciones reales antes de tocar código y
  encontré que el formato de Excel propuesto asumía columnas (`nif_ganadero`, `especie`) sin campo
  correspondiente en `Ganadero`/`Animal`. Antonio confirmó: añadir `nif` a `Ganadero` (migración,
  clave de upsert) y mantener `especie` en el Excel solo como validación (Ganera es bovino-only,
  no se persiste); y seguir el patrón de test ya establecido (llamada directa al controlador con
  `MockMultipartFile`, sin introducir MockMvc).
- **Implementación** (sin subagentes, en la conversación principal): migración `V14`
  (`ganadero.nif`, único); `GanaderoRepository.findByNif`, `ExplotacionRepository.findByCodigoRega`,
  `AnimalRepository.findByCrotal`, `TramiteRepository.findByEstado`; `ExplotacionImportService`
  (Apache POI, upsert por fila con `saveAndFlush` + catch por fila, nunca aborta el fichero
  completo); `POST /explotaciones/importar`, `GET /explotaciones`, `GET /tramites` (filtrable por
  `estado`), `POST /tramites/{id}/aprobar` (primera llamada real a
  `SuscripcionService.puedeAprobarTramites`), `POST /tramites/{id}/rechazar`. Fixture
  `inventario-prueba.xlsx` generado con Python/openpyxl (no había Excel real de Antonio) con datos
  ficticios cubriendo import limpio, especie no soportada, y explotación inexistente.
- **Footgun encontrado en la primera corrida de tests** (no en el plan, imprevisto): añadir
  `poi`/`poi-ooxml` 5.3.0 rompió en runtime con `NoSuchMethodError` en
  `BoundedInputStream.builder()` — Twilio arrastra `commons-io:2.14.0` transitivamente, Maven lo
  elige por nearest-wins sobre lo que POI necesita (`commons-io` 2.16+), y esto es invisible en
  `mvn compile`, solo se ve leyendo un fichero real. Arreglado fijando `commons-io:2.16.1` como
  dependencia directa en `backend/pom.xml` (gana la mediación por ser más cercana que la de
  Twilio). Documentado en `CLAUDE.md` para que no se repita la sorpresa.
- **Verificación:** suite completa 91/91 en verde (79 + 12 nuevos: 3 del importador, 2 de
  `ExplotacionController` incluyendo aislamiento de tenant con `session.enableFilter`, 7 de
  `TramiteController`). Smoke test H2 manual end-to-end **por HTTP real** (no solo
  `@DataJpaTest`): arranque con las 14 migraciones reales, onboarding → login → `GET /explotaciones`
  y `GET /tramites` vacíos → 401 sin JWT → 404 en trámite inexistente → importar el `.xlsx` real de
  test-resources vía multipart real → resumen exacto (`2 creadas / 0 actualizadas` explotaciones,
  `2 creadas / 2 errores` animales) → `GET /explotaciones` lista las 2 → reimportar el mismo fichero
  → `0 creadas / 2 actualizadas`, sin duplicar (confirmado por conteo). Proceso `java.exe` limpiado
  con `taskkill` al terminar.
- `CLAUDE.md` y `ganera-prompts.md` actualizados: Prompt 3d marcado completo, conteo de tests a 91,
  bullet nuevo de "Excel inventory importer" bajo Technical decisions, footgun de `commons-io`
  documentado junto a los de Twilio/Lombok, "Next pending step" limpiado (3d ya no aparece como
  pendiente de redactar, solo 3a/3b/3c bloqueados por OVZ.net y Prompt 4 sin especificar).

- **Revisión de Antonio antes de commitear (mismo día):** pidió verificar un riesgo concreto antes
  de cerrar el prompt — si una violación real de constraint a mitad de fichero (no solo los errores
  de validación ya cubiertos) dejaba la sesión de Hibernate inutilizable para las filas
  posteriores. Se escribió primero el test de la colisión real (un `codigo_rega` ya existente en
  OTRA Gestoria, oculto por el filtro de tenant) **antes** de tocar el fix, como se pidió
  explícitamente — confirmó el problema: `AssertionFailure: don't flush the Session after an
  exception occurs`, la fila siguiente (sin ningún problema propio) fallaba igualmente. Arreglado
  extrayendo el trabajo por fila a un bean nuevo, `ExplotacionImportFilaService`, con
  `@Transactional(propagation = REQUIRES_NEW)` por fila (necesita ser un bean separado porque la
  auto-invocación no pasa por el proxy de Spring). Al implementarlo se encontró un SEGUNDO problema
  no pedido explícitamente pero descubierto por análisis propio: `REQUIRES_NEW` suspende el
  `EntityManager` de la request (donde `TenantFilterActivationInterceptor` activa `gestoriaFilter`)
  y ata uno nuevo sin filtro — se habría roto el aislamiento multi-tenant en silencio para todo el
  importador. Arreglado reactivando el filtro dentro de cada método `REQUIRES_NEW` con el
  `gestoriaId` recibido como parámetro. Además: escribir el test de la colisión reveló que
  `@DataJpaTest` envuelve cada test en una transacción de rollback que las transacciones
  `REQUIRES_NEW` (con su propia conexión real) no pueden ver — hubo que usar
  `TestTransaction.flagForCommit()+end()` para comprometer de verdad los datos de setup antes de
  llamar al importador, y un `@AfterEach` que limpia las 4 tablas explícitamente (los datos
  comprometidos por `REQUIRES_NEW` no se deshacen con el rollback normal del test). Segundo hallazgo
  de la revisión: `IllegalArgumentException` por hoja de Excel faltante no estaba capturada en
  `ExplotacionImportController` → `500` genérico; arreglado a `400` con el mensaje. Suite completa
  93/93 en verde (91 + el test de la colisión + un test nuevo del controlador para el 400). Smoke
  test H2 repetido por HTTP real tras el fix: import normal idéntico a antes del refactor, fichero
  sin hoja "Animales" → `400` con el mensaje (antes `500`). `CLAUDE.md` y `ganera-prompts.md`
  actualizados con el diseño de `ExplotacionImportFilaService` y el efecto no obvio de
  `REQUIRES_NEW` sobre el filtro de tenant; conteo de tests a 93. Commit `1c190e6`.
- **Verificación adicional pedida por Antonio antes de dar el prompt por cerrado del todo:** pidió
  ver el contenido completo de `ExplotacionImportFilaService` y del test de la colisión cross-tenant,
  y preguntó explícitamente si el caso **same-tenant** (dos filas del mismo Excel y la misma
  Gestoria con el mismo `codigo_rega`, ej. una fila duplicada por error humano) queda aislado igual
  de bien, o si solo estaba probado el caso cross-tenant. Razonamiento verificado con un test nuevo
  en vez de asumirlo: con `REQUIRES_NEW`, cada fila hace COMMIT real al terminar (no solo flush);
  para cuando la fila 2 arranca su propia transacción (conexión distinta), el commit de la fila 1
  ya es visible bajo aislamiento read-committed, así que `findByCodigoRega` la encuentra y la trata
  como actualización — nunca como INSERT duplicado. Test nuevo
  `dosFilasDelMismoFicheroYLaMismaGestoriaConElMismoCodigoRegaSeTratanComoCreacionYActualizacion`
  confirma exactamente eso: `errores` vacío, `creadas=1`, `actualizadas=1`,
  `explotacionRepository.count()==1`, la fila 2 gana (`nombre="Finca version 2"`). Suite completa
  94/94 en verde. Commit separado (no se reescribió el commit anterior), a petición explícita de
  Antonio.

**PROMPT 3d CERRADO.**

## 2026-07-14 (continuación) — Prompt 4 (frontend funcional, solo lo no bloqueado por OVZ.net)

- Antonio pidió continuar con Prompt 4, con restricción explícita no negociable (igual que 3a/3d):
  nada de pantalla de onboarding OVZ de un Ganadero nuevo (ni mock ni placeholder), nada de
  formulario manual alternativo de alta de Ganadero/Explotación (el importador Excel de 3d sigue
  siendo la única vía), y el botón Aprobar de la cola de trámites solo cambia estado en BD, sin dar
  a entender que se ejecuta algo real en OVZ.net (`OvzAutomationService.ejecutarTramite()` sigue
  sin implementar).
- **Antes de generar código:** confirmé que no existía ningún endpoint de estado de Suscripción
  (`FacturacionController` solo tenía `POST /checkout`) y pregunté el diseño antes de inventarlo.
  Antonio aclaró la regla de negocio (Gestoría sin Suscripción = mismo tratamiento que un estado
  bloqueante, con opción de trial de 15 días una vez por gestoría) pero su respuesta sonaba más
  estricta que el diseño ya cerrado en Prompt 2 (`IMPAGO_GRACIA` = acceso completo con aviso,
  `TRIAL_EXPIRADO_SIN_PAGO`/`SUSPENDIDA` = solo lectura, no bloqueo total) — pregunté explícitamente
  el nivel de bloqueo de UI en vez de asumir el más estricto, y confirmó la opción coherente con lo
  ya documentado (solo bloquea Aprobar, nunca la navegación).
- **Backend, dos añadidos pequeños** dirigidos directamente por las necesidades del frontend (no
  especulativos): `GET /facturacion/suscripcion` (404 fail-closed sin crear nada si nunca hubo
  Suscripción) y `GET /tramites/{id}` (detalle con mensaje original de WhatsApp vía
  `MensajeCampoRepository.findFirstByTramiteIdOrderByCreatedAtDesc` + Explotación resuelta —
  encontré que `TramiteResponse` del listado no tenía ni el mensaje ni el nombre de la explotación,
  necesarios para el modal de revisión que pedía el prompt).
- **Frontend, desde cero** (scaffold de Prompt 1 reutilizado: Vite/React/Tailwind/shadcn,
  `httpClient.ts`, `AppLayout.tsx`, `router.tsx`): auth en memoria (no `localStorage`, sesión de SPA
  a propósito, con `authSession.ts` fuera del árbol de React para que el interceptor de axios lea
  el token); `AppLayout` con navegación real y banner de suscripción compartido vía Outlet context
  (evita refetch duplicado en Facturación); dashboard de Explotaciones con el importador Excel en
  primer plano; cola de Trámites con filtro por estado y modal de revisión; página de Facturación.
  shadcn instalado es el preset **"base-nova"** (`@base-ui/react`, no Radix pese a lo que decía
  `CLAUDE.md` — corregido).
- **Verificación:** suite backend completa 100/100 en verde (93 + 7 nuevos). Frontend sin skill de
  proyecto para "correr la app" todavía — usé el patrón genérico de la skill `run` (Playwright vía
  npm, instalado ad hoc, Chromium ya cacheado localmente) para conducir un Chromium headless real
  contra el backend real: login → import Excel (resumen idéntico al del backend, verificado dato a
  dato) → filtro de trámites (confirmé la query real disparada, `?estado=APROBADO`) → facturación →
  logout → redirect a `/login`, con `console --errors` limpio en cada paso.
- **2 bugs reales encontrados SOLO por la verificación en navegador real** (invisibles para
  `curl`/tests de request, que no envían `Origin` como un navegador):
  1. CORS: toda petición del frontend fallaba en el preflight del navegador antes de llegar a
     ningún controller. Arreglado con `SecurityConfig.corsConfigurationSource`
     (`FRONTEND_ORIGEN`/`ganera.frontend.origen`, default `http://localhost:5173`).
  2. El `Select` del filtro de trámites mostraba el valor crudo (`"TODOS"`) en vez de la etiqueta —
     `@base-ui/react/select` no resuelve la etiqueta sola, necesita `children` función en
     `SelectValue`. Reproduje el primer intento con `page.goto()` para navegar entre pantallas y
     pensé que era un bug (redirigía a `/login`) — en realidad era el comportamiento correcto (JWT
     en memoria, recarga de página exige login de nuevo); corregido el script de verificación para
     navegar con clics reales, no con `goto`.
- **No verificado en vivo, dicho explícitamente en vez de asumir cobertura:** el contenido del modal
  de revisión con un mensaje real, el 403 al aprobar, y el banner de "sin suscripción" — no hay
  forma todavía de crear un Trámite real ni un estado de Suscripción no-ACTIVA a través de la app en
  marcha (3b y el alta pública no existen). Cubierto por tests de integración del backend y revisión
  manual del código únicamente.
- `CLAUDE.md` y `ganera-prompts.md` actualizados: Prompt 4 (parcial) marcado completo, sección de
  frontend reescrita con el diseño real, conteo de tests a 100, "Next pending step" sigue apuntando
  a 3a/3b/3c bloqueados por OVZ.net.

## 2026-07-14 (continuación) — Verificación final antes de comitear Prompt 4: bug crítico de multi-tenancy encontrado y arreglado

- Antes de dar Prompt 4 por cerrado del todo, Antonio pidió verificar en vivo (sembrando datos
  directamente por SQL contra el H2 del smoke test, sin pasar por lógica de negocio nueva) los 3
  casos que solo estaban cubiertos por tests de backend/revisión de código: el modal de revisión
  con un Trámite real (mensaje de WhatsApp + Explotación resuelta), el bloqueo 403 al aprobar
  (Suscripción `SUSPENDIDA`), y el banner de "sin suscripción" (Gestoría sin fila de `Suscripcion`)
  — más el caso de mensaje/Explotación nulos, otra vez, pero de verdad.
- **Interrupción por corte de contexto/límite de ejecución** a mitad de la investigación (justo
  después de sembrar los datos y detectar algo raro en el escenario 3); retomado en una sesión
  nueva reiniciando el backend sobre el mismo fichero H2 (`AUTO_SERVER=TRUE`), que conservó todos
  los datos sembrados sin necesidad de rehacer nada.
- **Hallazgo:** sembrar una SEGUNDA Gestoría real (para probar el banner de "sin suscripción")
  reveló que `GET /tramites` y `GET /explotaciones` devolvían filas de la OTRA Gestoría —
  confirmado decodificando el JWT de la segunda Gestoría a mano (byte a byte) para descartar un
  error de prueba antes de asumir un bug real, y reproducido dos veces por vías independientes
  (`curl` directo y una sesión de navegador real completamente aislada).
- Investigación bloqueada un buen rato por una indisponibilidad temporal del clasificador de
  seguridad que autoriza comandos de red (Bash/PowerShell); comandos triviales sin red seguían
  funcionando. Se avisó a Antonio del bloqueo y se continuó con revisión de código mientras tanto
  (`TenantFilterActivationInterceptor`, `WebMvcTenantConfig`, `JwtService`, decodificación manual
  del JWT) hasta que el servicio se recuperó.
- **Causa raíz:** `WebMvcTenantConfig` registraba `TenantFilterActivationInterceptor` sin orden
  explícito. Spring Boot registra su propio `OpenEntityManagerInViewInterceptor` (de
  `open-in-view=true`) como otro `HandlerInterceptor` de MVC, y el orden relativo entre dos
  `WebMvcConfigurer` distintos no está garantizado sin `.order(...)`. Cuando el nuestro corría
  antes de que OSIV atara el `EntityManager` real de la request al hilo, el `EntityManager`
  compartido inyectado por `@PersistenceContext` creaba uno temporal y no transaccional solo para
  esa llamada — `enableFilter(...)` se aplicaba a una `Session` que se descartaba al momento, sin
  ningún efecto sobre las queries reales que ejecutaba el controller después. `gestoriaFilter`
  **nunca se aplicaba de verdad** en ninguna request HTTP real — llevaba así desde que se creó el
  interceptor (Prompt 1), invisible para toda la suite porque
  `TenantFilterActivationInterceptorTest` invoca `preHandle()` a mano (sin pasar por el
  registro/orden real de Spring MVC) y ningún smoke test manual anterior había comparado dos
  Gestorías reales con datos solapados en la misma ejecución — con una sola Gestoría, una query
  sin filtrar y una filtrada devuelven exactamente lo mismo.
- **Arreglado** con `.order(Ordered.LOWEST_PRECEDENCE)` explícito en `WebMvcTenantConfig`.
- **Verificado con TDD real, no solo confiando en el razonamiento:** se escribió
  `TenantIsolationEndToEndTest` (`@SpringBootTest(webEnvironment=RANDOM_PORT)` +
  `TestRestTemplate` — el único tipo de test que ejercita el registro/orden real de interceptors
  de Spring MVC; ni `@DataJpaTest` ni una llamada directa al interceptor lo hacen). Se revirtió el
  `.order(...)` momentáneamente, se confirmó que el test **falla** exactamente como se esperaba
  (Gestoría B ve la Explotación de Gestoría A), y se restauró el fix confirmando que el test
  **pasa**. Esto de paso sacó a la luz dos huecos más en `test/resources/application.yml` (nunca
  antes se había arrancado el contexto COMPLETO de Spring en un test, solo slices
  `@DataJpaTest`): faltaban `stripe.checkout.success-url`/`cancel-url` y
  `spring.ai.anthropic.api-key` (Spring AI no tolera una key en blanco al construir su bean, a
  diferencia de Stripe/Twilio que sí lo hacen a propósito) — añadidos con valores de prueba nunca
  usados de verdad.
- Suite completa backend re-verificada en verde: **101/101** (100 + el test de regresión nuevo).
  Los 4 escenarios pedidos por Antonio re-confirmados en un navegador real contra el backend ya
  arreglado: modal con mensaje/Explotación reales ✓, modal con mensaje/Explotación nulos (sin
  romperse) ✓, 403 al aprobar con mensaje claro ✓, banner de "sin suscripción" con conteos
  correctos (ya no filas ajenas) ✓.
- `CLAUDE.md` y `ganera-prompts.md` actualizados con la causa raíz completa (bullet de
  Multi-tenancy en CLAUDE.md) y la advertencia para el futuro: cualquier `HandlerInterceptor`
  nuevo que toque datos `GestoriaScopedEntity` vía el `EntityManager` compartido necesita un orden
  explícito relativo a OSIV, o su propio test end-to-end de dos tenants — no basta con un test
  unitario que invoque `preHandle()` a mano.

**PROMPT 4 (PARCIAL — SIN LO BLOQUEADO POR OVZ.NET) CERRADO — con un fix crítico de aislamiento
multi-tenant encontrado y arreglado en la verificación final, antes de comitear.**

## 2026-07-14 (continuación) — Auditoría dirigida: segundo bug crítico independiente encontrado y arreglado

- Antes de dar el fix del interceptor por suficiente, Antonio pidió una auditoría completa: listar
  TODOS los endpoints que devuelven/modifican datos de una entidad `GestoriaScopedEntity` (repo
  por repo) y confirmar, uno por uno, si `TenantIsolationEndToEndTest` (o un test equivalente con
  servidor embebido real y dos Gestorías con datos solapados) lo cubre ya — rechazando
  explícitamente "usa el mismo Repository, ya está cubierto" como argumento válido, porque el bug
  de hoy fue de orden de interceptor, no de query, y cada endpoint necesita su propia prueba.
- **Inventario completo** (grep de `@GetMapping`/`@PostMapping`/etc en todo `src/main/java`, 13
  endpoints en total): de los que tocan una `GestoriaScopedEntity`
  (`Ganadero`/`Explotacion`/`Animal`/`Tramite`/`Usuario`/`Suscripcion`), se distinguieron dos
  categorías: los que dependen del filtro AMBIENTE de Hibernate (`findAll`, `findByEstado` —
  vulnerables al bug de hoy) y los que usan un parámetro `gestoriaId` EXPLÍCITO en la query
  derivada (`findByGestoriaId`, `countByGestoriaId` — inmunes por construcción, sea cual sea el
  estado del interceptor).
- **Al escribir la cobertura que faltaba** (`GET /tramites`, `GET /tramites/{id}`,
  `POST /tramites/{id}/aprobar`, `POST /tramites/{id}/rechazar`, `GET /auth/me`,
  `POST /explotaciones/importar`) se confirmó un **SEGUNDO bug crítico, independiente del de hoy**:
  tres tests fallaron con `200` (datos ajenos, o modificándolos) en vez de `404` esperado.
  **`gestoriaFilter` nunca se aplica a `findById(id)`** — un comportamiento de Hibernate
  completamente distinto del bug del interceptor (no es de orden, es que una carga por clave
  primaria vía `EntityManager.find()` simplemente no pasa por el filtro de resultado-de-query).
  Esto significa que CUALQUIER usuario autenticado de CUALQUIER Gestoría podía ver, aprobar o
  rechazar el trámite de OTRA Gestoría con solo adivinar/enumerar su id numérico — y esto seguía
  así incluso DESPUÉS de aplicar el fix de `WebMvcTenantConfig` de antes, porque es un problema
  distinto a nivel de query, no de interceptor.
- **Arreglado** añadiendo `TramiteRepository.findByIdAndGestoriaId(Long id, Long gestoriaId)` (con
  `gestoriaId` como parámetro real de la query derivada, inmune al filtro ambiente igual que
  `findByGestoriaId`) y usándolo en `detalle()`/`aprobar()`/`rechazar()` de `TramiteController` en
  vez de `findById(id)` a secas. `GET /auth/me` se dejó con `findById(principal.usuarioId())` tal
  cual: el id viene del JWT firmado, nunca de un path variable controlado por el atacante, así que
  no hay superficie de fuga real ahí aunque técnicamente use el mismo mecanismo sin scope.
- **Confirmado con TDD real, otra vez:** los tests nuevos de `TenantIsolationEndToEndTest`
  (`tramiteDetallePorIdDeOtraGestoriaDevuelve404NoLosDatos`,
  `aprobarTramiteDeOtraGestoriaDevuelve404YNoCambiaSuEstado`,
  `rechazarTramiteDeOtraGestoriaDevuelve404YNoCambiaSuEstado`) se escribieron y se vio que fallaban
  ANTES del fix (tal y como pidió Antonio: escribir el test primero, confirmar que falla, luego
  arreglar), y pasan después. Añadidos también 3 tests de aislamiento cross-tenant a nivel de
  `TramiteControllerTest` (unitario, sin servidor embebido) como capa adicional de defensa.
- `GET /facturacion/suscripcion` y `POST /facturacion/checkout` quedaron confirmados EXENTOS de
  ambos bugs por diseño: ambos resuelven la Suscripción vía
  `SuscripcionRepository.findByGestoriaId(gestoriaId)`/`ExplotacionRepository.countByGestoriaId(gestoriaId)`,
  con el `gestoriaId` como parámetro explícito de la query — no dependen del filtro ambiente ni del
  interceptor en ningún momento.
- Suite completa backend re-verificada en verde: **110/110** (101 + 6 nuevos casos E2E + 3 checks
  unitarios cross-tenant en `TramiteControllerTest`).
- `CLAUDE.md` y `ganera-prompts.md` actualizados con la causa raíz de este segundo bug y la regla
  general para el futuro: cualquier lookup de una `GestoriaScopedEntity` por un id que venga de un
  path variable/body (no del propio JWT del caller) debe usar una query tipo
  `findByIdAndGestoriaId`, nunca `findById(id)` a secas.

**Auditoría completa cerrada — tabla resumen entregada a Antonio antes del commit final.**

## 2026-07-14 (continuación) — Identidad visual de marca

- Paleta real de Ganera aplicada vía variables CSS en `frontend/src/index.css` (verde `#1F3D2B`,
  fondo crema `#F7F6F1`, sidebar `#F1F0E8`), sin tocar la estructura de componentes shadcn/ui.
- Variantes `success`/`warning`/`danger` en `Badge`, mapeadas desde los 7 `EstadoTramite`
  (`badgeVarianteDeEstado` en `features/tramites/types.ts`); barra de navegación con logotipo
  textual GANERA; tarjeta de métrica en Explotaciones; limpieza del CSS heredado de Vite.
- Verificado con `tsc -b`, `oxlint` y sesión real de navegador contra H2 sembrado con los 7
  estados. Commit `9a81219`.

## 2026-07-14 (continuación) — Alta pública de Gestorías (`POST /gestorias/registro`)

- Paquete `registro` nuevo: `RangoClientes` (rango de clientes → quantity estimada de
  explotaciones, mínimo del rango × 1,3 → 2/15/41/99), `RegistroGestoriaValidacion` (pura, sin
  `@Valid`), `RegistroGestoriaService` (`@Transactional`, deja propagar la violación de
  `UNIQUE(email)` para no dejar `Gestoria` huérfana), `RegistroGestoriaController` (mismo `400`
  genérico para cualquier fallo, `503` si Stripe no está configurado). `SecurityConfig`: `permitAll`
  para `/gestorias/registro`. `StripeCheckoutService.crearSesionCheckoutConCantidadEstimada`.
- Frontend: `RegistroPage` + `features/auth/api.ts`, enlace desde `LoginPage`, ruta `/registro`.
- Verificación: suite completa **135/135** (110 + 25 nuevos), smoke test H2 por HTTP real y
  navegador real. Redirección real a Stripe Checkout sin verificar (falta el `Price` real).
- Documentado en `CLAUDE.md` (bullet "Public self-registration") y `ganera-prompts.md`.
- **Quedó sin commitear** al cerrar la sesión; se commitea en la sesión del 2026-09-25.

## 2026-09-25 — Revisión completa del proyecto + puesta al día de documentación

- Revisión de todo el repo: suite backend 135/135 en verde, frontend `npm run build` limpio,
  `oxlint` solo con los 3 avisos preexistentes de `only-export-components`.
- **Dos bugs de Stripe detectados, anotados como PENDIENTES en `CLAUDE.md` (sin arreglar):**
  1. `invoice.payment_failed` sobre una suscripción ya en `TRIAL_EXPIRADO_SIN_PAGO` la pasa a
     `IMPAGO_GRACIA` (el plan 2.7 decía mantener `TRIAL_EXPIRADO_SIN_PAGO`) — reabre la aprobación
     de trámites tras el reintento de cobro de Stripe.
  2. `obtenerOCrearSuscripcion` crea la fila en `TRIAL` antes de completar el checkout: un checkout
     abandonado deja la Gestoría en `TRIAL` indefinidamente, con aprobación permitida.
- Documentación puesta al día: contradicciones sobre el registro público eliminadas de
  `CLAUDE.md`, `/gestorias/registro` añadido a las rutas públicas, cabecera de estado y repo
  (`AntonioMenor01/Ganera.git`) corregidos en `ganera-prompts.md`, skill `smoke-test-h2`
  alineada con `CLAUDE.md` (`./mvnw`, Flyway activo, `ddl-auto=none`).

## 2026-09-25 → 2026-09-28 — Prompt A1 (contactos, crotales en trámites, revisión editable) — solo backend

- **Paso 0 (2026-09-25):** plan `docs/superpowers/plans/2026-09-25-promptA1-contactos-crotales-revision.md`
  con 10 decisiones cerradas con Antonio antes de empezar (teléfono `UNIQUE` global — opción A —,
  borrado lógico, rol en la relación, sin límite de una explotación por empleado, E.164, PATCH con
  lista completa, formato de errores 409 `{motivo}`/404 sin cuerpo/400, `TipoTramite` sin tocar) y
  21 más añadidas durante la ejecución (11–31), casi todas a raíz de las revisiones. Flujo
  Superpowers: un subagente implementador + un revisor independiente por tarea; informes y
  revisiones en `.superpowers/sdd/a1-task*-report.md` / `a1-task*-review.md`. Suite de partida: 135.
- **Task 1** (modelo de Contactos, `V15`, `RolContacto`, `TelefonoNormalizador`,
  `ContactoExplotacionRepository`; se borra `TipoContacto`): 135 → 167 → **171** tras la revisión
  (**Approved**; I1 `+0…` no es E.164 y M1 espacio no separable arreglados; M2 → decisión 14:
  rechazar `+` + 9 dígitos españoles sin `34`; M3 → decisión 15: comprobar que Contacto,
  Explotación y usuario son de la misma Gestoría).
- **Task 2** (CRUD `/contactos`, enlazar/desenlazar, `MotivoErrorResponse`): 204 → **206**.
  **Approved** sin Critical/Important.
- **Task 3** (hoja "Contactos" opcional del importador, `procesarContacto` en `REQUIRES_NEW`):
  217 → **220**. **Approved**. Decisión 11 verificada con E2E de dos Gestorías y una mutación
  (`findByTelefono` en vez de `findByGestoriaIdAndTelefono` rompe el test). Dos hallazgos para la
  Task 7: **I-pre1** (preexistente) — el importador respondía "posible duplicado entre gestorias"
  cuando un `codigo_rega`/NIF/crotal era de otra Gestoría → decisión 17; **I-doc** — la explicación
  de `REQUIRES_NEW` en `CLAUDE.md` era incorrecta sobre HTTP (reutiliza el EntityManager de OSIV
  con el filtro ya activo) → decisión 19.
- **Task 4** (`/ganaderos`, `/ganaderos/{id}`, `/explotaciones/{id}/animales`): 233 → **239**.
  **Approved**; I1 (el guard de N+1 de `GET /ganaderos` pasaba trivialmente) arreglado; M1 lista
  blanca de `sort` (aceptaba `ovzUsuario`), M2 orden estable, M3 comprobar la Gestoría del Contacto.
- **Task 5** (`tramite_crotal` `V16`, `CrotalNormalizador`, `TramiteCrotalService`, crotales en el
  listado con una consulta por página): **288**. **Approved**; I1 (preexistente): el importador
  guardaba `Animal.crotal` sin normalizar → decisión 22.
- **Task 6** (`PATCH /tramites/{id}`, `TramiteRevisionService`, regla de aprobación): 352 →
  **358**. Revisión: **Changes required** por I1 — aprobar re-resolvía crotales y podía aprobar un
  animal distinto del que vio el revisor. Arreglado con `ResolucionCrotalesCambiadaException`
  (409 que confirma la nueva resolución, `noRollbackFor`), refresh tras el bloqueo (M3) y test de
  concurrencia aprobar-contra-aprobar (M6). Re-revisión **Approved** con **R1** residual (la
  garantía era por petición, no por revisor) → decisión 27 (`@Version`). I2 (el "completo" de la
  regla era demasiado laxo, `ES1234` pasaba) → decisión 28. M1 (cuerpo mal formado → 401 que cierra
  la sesión) → decisión 29. M4 (PATCH/rechazar no bloqueados por suscripción) → decisión 30, solo
  docs.
- **Task 7a** (decisiones 17, 19, 21, 22, 27, 28, 29, 31). **La sesión se cortó a mitad.** Al
  retomarla, el recuento que se daba por bueno ("387 tests, 13 fallos + 6 errores") venía de clases
  compiladas por VS Code (ECJ) en `target/`: un `./mvnw clean test` dio **BUILD FAILURE en
  testCompile** (8 errores de `javac` en `ExplotacionControllerTest` y `TramiteControllerTest`). Se
  auditó lo heredado, se reforzaron sus tests y se completó: finders sin scope
  (`findByCodigoRega`/`findByNif`/`findByCrotal`) **eliminados** de los repositorios (volver a
  usarlos no compila), mensaje neutro en el importador, crotales normalizados en el importador,
  `@Version` con incremento exacto +1 en toda escritura (UPDATE explícito: `PESSIMISTIC_FORCE_INCREMENT`
  lo omitía Hibernate 6.6 en silencio), formato provisional de crotal, `/error` público, listas
  blancas de `sort` en `/explotaciones` y `/tramites`, `LOCK_TIMEOUT=10000` en el H2 de tests.
  **408** en verde. Revisión **Approved** con I1 (un inventario con crotales sin `ES` permitía
  aprobar un crotal incompleto) → Antonio eligió aplicar la regla de formato también al crotal del
  Animal `EN_INVENTARIO` (en vez de endurecer el importador), y M1 (`/contactos` sin lista blanca de
  `sort`, `?sort=noExiste` daba 500). Ambos arreglados con TDD → **415**; re-revisión **Approved**.
- **Task 7b (2026-09-28, cierre):**
  - Greps en `backend/src/main`: `findById(` solo en `AuthController` `/auth/me` (id del JWT) y en
    comentarios; `findByCodigoRega(`/`findByNif(`/`findByCrotal(` cero (ya no existen);
    `findByTelefono(` solo su declaración (sin llamadas; el webhook aún no lo usa).
  - `./mvnw clean test`: **415 tests, 0 fallos, 0 errores**, BUILD SUCCESS.
  - Smoke test HTTP real con H2 en fichero (`AUTO_SERVER=TRUE`): onboarding → login → importar un
    Excel con hojas Explotaciones/Animales/Contactos (crotal con separadores normalizado a
    `ES010000001234`; teléfonos `612 345 678` → `+34612345678`; un teléfono inválido como error de
    fila) → `GET /ganaderos/1` con contactos y rol → `GET /contactos` y `?sort=noExiste` 400 →
    Trámite sembrado por SQL en `PENDIENTE_REVISION` → aprobar sin `version` 400, con `version` 0
    409 "Falta asignar la explotación. Falta el tipo de trámite." → PATCH (explotación, `ALTA`,
    `1234` + `ES 0100 0000 7777`) 200, versión 1, `EN_INVENTARIO` + `NO_ENCONTRADO` → PATCH y
    aprobar con versión 0 → 409 → aprobar con 1 → 200 `APROBADO`, versión 2 → aprobar/rechazar de
    nuevo 409 → cuerpo mal formado, tipo erróneo e id no numérico → 400, no 401. Estado final
    comprobado por SQL. `java.exe` del smoke matado por PID (solo queda el del language server de
    VS Code); BD H2 y ficheros temporales borrados.
  - `CLAUDE.md`, `ganera-prompts.md` y este fichero puestos al día (nuevo bullet "Prompt A1",
    decisiones 1/11/17/19/30, "read-only" → "no approvals", `/error` público, riesgos para 3c,
    frontend roto hasta A2, `./mvnw clean test`).
- **Sin commit** — pendiente de la aprobación de Antonio. **Aviso antes de cualquier demo:** aprobar
  desde el frontend actual da siempre 400 hasta el Prompt A2 (envía `aprobar` sin `version`), salvo
  el 403 de suscripción bloqueada o inexistente, que va antes y sigue mostrando su mensaje.

## 2026-09-28 → 2026-10-02 — Prompt A2 (frontend de A1: Ganaderos, animales, revisión editable) — solo frontend

Plan: `docs/superpowers/plans/2026-09-28-promptA2-frontend-revision.md` (31 decisiones). Un
implementador + un revisor independiente por tarea; las pasadas de Impeccable, desde la sesión
principal (decisión 27). Informes en `.superpowers/sdd/a2-*` (no se suben). `backend/` sin tocar.

- **Task 1** (decisión 17): Vitest + jsdom + Testing Library + MSW, `npm test`. Revisión Approved.
- **Task 2** (decisión 2, H11): `ErrorApi` + `mensajeDeError`, interceptores, migración de los usos
  existentes. Revisión Approved tras correcciones (M4, el mensaje de POI en inglés, pasa al
  mini-prompt de backend).
- **Task 3** (decisión 1): sesión en `sessionStorage`, arranque con `/auth/me`, aviso de sesión
  caducada. Approved con un minor (R1).
- **Task 4** (decisiones 4, 5, H9): `index.html`, `LogoGanera`, `PRODUCT.md`. Approved con minors.
- **Task 5** (decisión 6, H10): `etiquetas.ts`, badges con texto, tipos al día, Contactos en el
  resumen del importador. Approved.
- **Task 6** (critique de la cola, decisión 16): filas accesibles por teclado, REGA en vez del id,
  crotales en la fila. Tras la Task 6, decisiones 27–31 de Antonio (cola filtrada por pendientes,
  Impeccable desde la sesión principal…). Approved.
- **Task 7** (craft de Ganaderos, decisiones 14 y 30): listado + detalle + enlace en la barra.
  Revisión de código + finish review de Impeccable; Approved tras correcciones (I1: ganadero
  anterior pintado bajo el id nuevo).
- **Task 8** (decisión 15, H1-A): `AnimalesDeExplotacion` en Explotaciones y en el detalle.
  Approved tras I1 (columna quitada en móvil del marcado, `useDesdeSm`).
- **Task 9a** (lógica del modal, TDD): `useRevisionTramite` + `revisionTramite.ts`. Approved.
- **Task 9b** (craft del modal, decisión 31): combobox, tipo, crotales, avisos, confirmación en
  línea. Dos re-revisiones (código e Impeccable) aprobadas el 2026-10-02.
- **Task 10** (audit + polish): audit 14/20, 0 fallos de contraste; polish con regiones `status`,
  esqueleto en la cola, "+N más" desplegable, `CLASE_ENLACE` en `shared/ui`, `wrap-anywhere`.
  Approved sin Critical ni Important.
- **Task 11 (2026-10-02, cierre):**
  - `npm test` **475/475** (36 ficheros); `npm run build` en verde (aviso del chunk de 622 kB);
    `npm run lint` con los 3 avisos `only-export-components` de siempre; `./mvnw clean test`
    **415/415**, BUILD SUCCESS.
  - Smoke en navegador real (Playwright 1.63 por npm en el scratchpad, Vite en :5173, backend con
    H2 en fichero `AUTO_SERVER=TRUE`), dos gestorías dadas de alta por `/internal/onboarding`,
    trámites y mensajes sembrados por SQL. Gestoría A, 1440 px: login → recarga sin perder la
    sesión → importación desde la UI con hoja Contactos (3/27/4 filas; reimportar no duplica: 27
    animales) → Ganaderos → detalle (3 enlaces `tel:`, roles) → "Ver animales" paginado (20 + 5) →
    cola filtrada por pendientes → T1: asignar explotación y tipo, `PATCH {version:0,…}` 200,
    crotales `1234`/`5678` → `EN_INVENTARIO`, aprobar `{version:1}` 200 y solo lectura → T2:
    aprobar sin tipo `409` "Falta el tipo de trámite." → T6: `PATCH` externo con la misma versión y
    después Guardar en la UI → `409`, recarga con el tipo nuevo y motivo "El trámite ha cambiado
    desde que lo abriste…", aprobar con la versión nueva 200 → T3: rechazar con confirmación en
    línea → T4 `APROBADO` sin botones → Facturación "Activa". Gestoría B (`SUSPENDIDA`, 375 px):
    banner, solo su trámite en la cola, aprobar `403` con el texto de suscripción y sin cerrar
    sesión, combobox por encima del modal a pantalla completa, `/ganaderos/2` (de A) → `404` y
    "Ganadero no encontrado", "Salir" borra `ganera.token`. Estado final comprobado por SQL.
  - Falso positivo descartado: una primera foto tras importar mostraba la tabla vacía; era la foto
    tomada antes de pintar la recarga. Repetido con espera real (reimportación y una tercera
    gestoría vacía): la tabla se refresca bien.
  - Detalle anotado (sin arreglar): la tarjeta del resumen del importador dice "1 filas" /
    "1 actualizadas"; la frase anunciada ya usa plurales reales.
  - Procesos `java`/`node` del smoke parados por PID; H2, Excel y temporales borrados (solo queda el
    `java.exe` del language server de VS Code).
  - `CLAUDE.md`, `ganera-prompts.md`, `DESIGN.md` y este fichero puestos al día.
- **Sin commit** — pendiente de la aprobación de Antonio. La rama `a2-wip` (commit `5543045`, con
  los informes) se conserva hasta que Antonio diga lo contrario; el commit final se monta desde
  `main` ruta a ruta.

## 2026-10-02 — Mini-prompt de backend tras A2

Plan y decisiones cerradas con Antonio: `docs/superpowers/plans/2026-10-02-mini-prompt-backend-tras-a2.md`
(1, 2, 5, 6, 7 y 8 como se recomendaba; 3 también por nombre del ganadero, sin quitar acentos →
nota en "Prompt C"; 4 opción (a), `version` obligatoria al rechazar, con el cambio mínimo de
frontend para que Rechazar no quede roto en `main`). Un implementador + un revisor independiente por
tarea; informes en `.superpowers/sdd/mp-*`.

- **T1** (`GET /explotaciones/{id}`, `?q=`): 415 → **443**. Approved; minors: N+1 preexistente en
  el listado sin `q`, `/explotaciones/importar` por GET da 400 en vez de 405, longitud de `q` en
  unidades UTF-16, el test E2E con `generate_statistics` crea otro contexto Spring.
- **T2** (`403 {motivo}`, `version` en rechazar + `api.ts`/`useRevisionTramite`): **450**, frontend
  **476**. Approved; mutaciones A–E detectadas. Minors: comentario desfasado en `errores.ts`, falta
  un test de "rechazar tras guardar envía la versión nueva", el E2E cross-tenant de aprobar no aísla
  el lookup (un 404 posterior lo tapa).
- **T3** (`explotacionCodigoRega`/`explotacionNombre` con `@EntityGraph`, `completo`): **457**.
  Approved; m1 (el test de N+1 dependía del formato del SQL) arreglado en la T4.
- **T4** (importador y registro con `{motivo}`, detección por contenido): **471**. Approved; minor:
  un `IOException` real del servidor dentro de `new XSSFWorkbook` se vería como fichero inválido.
- **T5 (cierre):** `./mvnw clean test` **471/471**; `npm test` **476/476**; `npm run build` en verde
  (aviso del chunk); `npm run lint` con los 3 avisos de siempre. Smoke `curl` con dos gestorías en
  H2 en fichero, 10/10 en verde (`mp-t5-smoke.md`). Aprendido: el `curl -d '…'` de Git Bash rompe
  los caracteres no ASCII en Windows; los cuerpos JSON van con `--data-binary @fichero`. Procesos
  del smoke parados por PID; temporales borrados. `CLAUDE.md`, `ganera-prompts.md` y este fichero
  al día.
- **Antes del commit (visto bueno de Antonio):** arreglado el N+1 preexistente de `GET /explotaciones`
  sin `q` con `@EntityGraph(attributePaths = "ganadero")` en `findByGestoriaId`, con TDD (el test
  nuevo de `ExplotacionEndToEndTest` falló primero con 2 cargas sueltas de Ganadero y pasó tras el
  arreglo). `./mvnw clean test` **472/472**. El smoke de la T5 fue anterior a este arreglo. La
  siguiente tarea de frontend queda completa en `ganera-prompts.md` (7 puntos).
- Commit con el visto bueno de Antonio (2026-10-02), sin push.

## 2026-10-03 → 2026-10-04 — Tarea de frontend antes del piloto (solo frontend)

Plan con las decisiones cerradas con Antonio: `docs/superpowers/plans/2026-10-03-tarea-frontend-antes-piloto.md`
(navbar (a) en dos filas < `md`, email oculto < `md` y truncado desde `md`; regresión de tildes y
palabras del `?q=` aceptada con la condición de anotarla como bloqueante de backend antes del piloto
en `ganera-prompts.md`; 400 de `q` con su `motivo`, sin `maxLength`; resto como se recomendaba). Un
implementador + un revisor independiente por tarea; informes en `.superpowers/sdd/fp-*`.

- **T1** (plurales del importador, test del `403` con `motivo`, comentarios de `errores.ts`): 476 →
  **480**. Approved with minors; m1/m2 (rutas de `httpClient.test.ts`) arreglados en la sesión
  principal.
- **T2** (`completo` → "No está en el inventario · incompleto" en ámbar, `presentacionCrotal`):
  **493**. Approved with minors (DESIGN.md y el posible recorte del badge, comprobado en el smoke: no
  se recorta).
- **T3** (columna REGA de la cola desde `explotacionCodigoRega`; `explotacionDeTramite.ts` borrado):
  **490**. Approved with minors (m2/m3 pasados a la T4).
- **T4** (combobox con `GET /explotaciones?q=`, `useBuscarExplotaciones`; lista completa borrada;
  etiqueta de la explotación tomada de la respuesta de aprobar/rechazar): **486**. Approved with
  minors; m3 (etiqueta con espacios en D3d) arreglado con TDD en la sesión principal (**487**).
- **T5** (navbar móvil; Impeccable shape aprobado por Antonio, que además pidió `scrollLeft` en vez de
  `scrollIntoView` y comprobar `scrollY`): **501**. Approved with minors; minors 1–3
  (`ResizeObserver`, aserciones de clases, restauración del espía) resueltos por el implementador:
  **505**.
- **Mutaciones de la contraseña de OVZ (2026-10-07, pedidas por Antonio antes del commit, desde la sesión principal):** A, la
  contraseña en claro (getter tras el converter) en un campo de nombre neutro de `TramiteDetalleResponse` → falla
  `niElGetNiElPatch…:166`; B, el texto cifrado guardado (mapeo de solo lectura de la columna sin converter en `Ganadero`) en ese
  campo → falla `…:168`. Copias en el scratchpad, restaurado con `cp` + `cmp` (idénticos); `./mvnw clean test` **706/706** después.
  El clasificador no bloqueó esta vez.
- **Aceptado por Antonio (2026-10-07):** el check de fila copiada en tinta (no gris), y que una ficha ya abierta conserve la
  sesión anterior si en la ventana principal se entra con otro usuario: da «Trámite no encontrado», sin filtrar datos.
- **T6 (cierre):** `npm test` **505/505** (36 ficheros); `npm run build` en verde (aviso del chunk);
  `npm run lint` con los 3 avisos de siempre; `./mvnw clean test` **472/472**. Smoke en navegador real
  (Playwright en el scratchpad, H2 en fichero, dos gestorías, B `SUSPENDIDA`), A–F OK
  (`fp-t6-smoke.md`). Hallazgos no bloqueantes: N1, el foco de Tab puede caer en un enlace de la tira
  cortado por el borde a 375 px; "Reintentar" del combobox no se alcanza con Tab (m1 de T4); la lista
  del combobox se vacía mientras busca (m5 de T4). Procesos parados por PID; H2 y Excel borrados.
  `DESIGN.md` (navbar, resolución de crotal, combobox), `CLAUDE.md`, `ganera-prompts.md` y este
  fichero al día.
- **Antes del commit (visto bueno de Antonio a las 42 rutas):** arreglado N1 con TDD. La tira aplica
  el mismo ajuste de `scrollLeft` (`mostrarEnTira`, compartido con el del enlace activo) al enlace
  que recibe el foco (`onFocus` del `nav`). El test nuevo falló primero (64 en vez de 0) y pasa tras
  el arreglo. Navegador real a 375 px (Vite + API simulada con `page.route`): con la tira desplazada
  (`scrollLeft` 46), el primer Tab entra en "Trámites" entero, a 16 px del borde, y `scrollY` no
  cambia con la tira a la vista (20 → 20). Con la página bajada 200 px la barra queda fuera de
  pantalla y el navegador sube a 0 al enfocar. Ocurre igual a 1440 px, donde el ajuste no hace nada:
  es el comportamiento nativo del foco, no el arreglo. `npm test` **506/506**; build y lint en verde
  (avisos de siempre). Vite parado por PID. "Reintentar" con Tab y el salto de altura del combobox
  quedan anotados.
- **Sin commit**: pendiente de la aprobación de Antonio.

## 2026-10-04 — Backend bloqueante antes del piloto: búsqueda sin tildes y por palabras (solo backend)

Plan: `docs/superpowers/plans/2026-10-04-busqueda-explotaciones-sin-tildes.md`. Las recomendaciones D1–D5
se aprobaron con tres ajustes de Antonio:
- **A1:** la V18 solo con JDBC.
- **A2:** las palabras que quedan vacías se descartan.
- **A3:** nada de `fetch` en la Specification; `@EntityGraph` en `findAll(spec, pageable)`.

Tras la revisión de T1, Antonio decidió además quitar los acentos sueltos (`\p{Sk}`) sin dejar hueco.
En cada tarea hubo un implementador y un revisor independiente; los informes están en `.superpowers/sdd/bt-*`.

- **T1** (`NormalizadorBusqueda`, puro): 472 → **498**. Revisión: Approved with minors.
  - m2 (el acento suelto partía la palabra) lo decidió Antonio. m1 y m3 (tests del trozo que se parte y del
    espacio Unicode) se corrigieron. m4 (`ß`, `ł`, `ø`, `æ` no se pliegan) queda anotado en el Javadoc.
  - Resultado: **502**.
- **T2** (columnas por entidad, setters explícitos, V18 en Java con JDBC): **512**. Revisión: Approved with minors.
  - Se corrigieron m1 (ciclo de paquetes; el normalizador pasa a `shared/texto`), m3 (test de más de 500
    filas) y m5 (el test de la migración sale del paquete `db.migration`).
  - m2 (paso 0 en el plan) se documentó. m4 (`fetchSize`) y m6 (la siembra por SQL debe rellenar las
    columnas) quedan en `CLAUDE.md`.
  - Resultado: **513**.
- **T3** (Specification + EntityGraph, límite de 8 palabras, página vacía): **532**. Revisión: Approved with minors.
  - Se corrigieron m1 (`@DataJpaTest` de dos gestorías sin filtro que protege el predicado `gestoria.id`;
    sin él, los E2E no lo detectan) y m2 (Javadoc: toda spec debe llevar `gestoria.id`).
  - Resultado: **533**.
  - m3 (el JSDoc de `frontend/src/features/explotaciones/api.ts` describía la búsqueda vieja): corregido
    antes del commit, como única excepción a "solo backend" que autorizó Antonio (un comentario, sin código).
    `npm test` 506/506 y `npm run lint` con los 3 avisos de siempre.
  - m4 (una etiqueta retocada de un nombre largo puede pasar de 8 palabras; visto en el smoke): se queda
    así, por decisión de Antonio.
  - m5 (el informe atribuía al EntityGraph que `ganadero` se uniera una sola vez también en el count; allí
    lo hace el join implícito del path): solo afecta al informe y no hay nada que cambiar en el código.
- **T4 (cierre):**
  - `./mvnw clean test` **533/533**; `npm test` **506/506**.
  - Smoke con `curl` contra dos gestorías, en dos fases sobre la misma H2 en fichero. Primero, el backend
    de `main` (`git archive`) importa en la V17 y reproduce el fallo (`martinez` → 0). Después, el backend
    nuevo aplica la V18 y todos los casos pasan (`bt-t4-smoke.md`).
  - Procesos parados por PID y temporales borrados. `CLAUDE.md`, `ganera-prompts.md` y este fichero al día.
- **Sin commit**: pendiente de la aprobación de Antonio.

## 2026-10-04 — Quitar el pago de la app (backend y frontend en el mismo commit)

Plan: `docs/superpowers/plans/2026-10-04-quitar-pago-de-la-app.md`. Antonio aprobó el inventario "se quita /
se queda" y las decisiones D1–D5:
- textos de contacto con Ganera, sin enlace ni `mailto:`;
- comodín `*` hacia `/tramites`;
- `features/facturacion` conserva su nombre;
- E2E del checkout eliminado.

Los dos bugs de Stripe y `SuscripcionSyncScheduler` quedan anotados en las notas del Prompt C. Cada tarea tuvo un
implementador y un revisor independiente; los informes están en `.superpowers/sdd/qp-*`.

- **T1 (backend):** fuera `POST /facturacion/checkout`, `CheckoutResponse` y `crearSesionCheckout(gestoriaId)`.
  El Registro conserva su checkout con cantidad estimada. Motivo nuevo del 403. E2E `CheckoutEliminadoEndToEndTest`
  (404 con JWT, 401 sin él, sin crear `Suscripcion`). 533 → **534**.
  - Revisión: Approved with minors. Los tres eran comentarios y quedaron corregidos.
  - Nota: con Stripe sin configurar, el checkout antiguo tampoco creaba la `Suscripcion`, así que esa aserción es
    una guarda hacia delante.
- **T2 (frontend):** fuera `FacturacionPage`, la ruta, el enlace de la navbar, `AppLayoutContext`,
  `crearSesionCheckout`, el contexto `checkout` y el enlace del 403. Comodín `*`. Banner con textos de contacto
  y sin acción. `DESIGN.md` al día. 506 → **508**.
  - Revisión: Approved with minors. Se corrigieron m1 (el verbo se repetía en los textos del banner: "terminando
    en" quería decir sustituir el final; Antonio dio los textos) y m3 (una aserción de `httpClient.test.ts` ya no
    probaba nada).
  - Queda m2 para Antonio: el título del banner sin `Suscripcion` pasa a "No puedes aprobar trámites ahora mismo".
  - Incidencias, resueltas en el momento: el implementador hizo un `git rm --cached` y lo deshizo; el revisor
    restauró `AppLayout.tsx` con `git checkout` y lo recuperó desde su copia (mismo blob `fd4b36d`, comprobado
    después).
- **T3 (cierre):**
  - `./mvnw clean test` **534/534**; `npm test` **508/508**; build en verde (aviso del chunk); lint con los 3 avisos
    de siempre.
  - Smoke en navegador real con A `ACTIVA` y B `SUSPENDIDA` a 1440 y 375 px, todo PASS (`qp-t3-smoke.md`).
  - Procesos parados por PID y temporales borrados. `CLAUDE.md`, `ganera-prompts.md` (orden nuevo) y este fichero
    al día.
- **Sin commit**: pendiente de la aprobación de Antonio.

## 2026-10-05 — Colores y tipografía de la marca nueva (frontend y documentación)

Plan: `docs/superpowers/plans/2026-10-04-colores-y-tipografia.md`. Antonio aprobó el shape de Impeccable el
2026-10-04 con D1a (tarjetas blancas) y sus seis puntos: logotipo en Tinta con el símbolo rojo, iconos en
todas las alertas de error, favicon y PNG en rojo con `ganera-logo-rojo.svg`, PRODUCT.md al día, radios sin
tocar, y `--input` en Gris 600 `#7D7979` (medido: el más claro que llega a 3:1). El craft se hizo desde la
sesión principal y los subagentes solo implementaron y revisaron. Informes en `.superpowers/sdd/ct-*`.

Decisión de craft de la sesión principal: 800 en titulares, 600 en elementos de interfaz y 500 solo para el
énfasis de datos en tablas (dígitos del crotal, crotal indicado, `#id`, nombre del ganadero), para que no
compita con las cabeceras.

- **T1** (tokens de `index.css`, `--marca`/`--enlace`/`--primary-hover`, foco base al 100 %, Archivo por
  `@fontsource-variable/archivo` sin Geist, logo con `text-marca` con TDD): 508/508. Approved with minors.
  Los dos minors eran estados intermedios que cerraba T2.
- **T2** (pesos, foco sólido `ring-ring` en todos los controles, foco de "Sí, rechazar" sin el override al 20 %,
  `bg-primary-hover`, `CLASE_ENLACE` en `text-enlace`, "GANERA" en Tinta 800, `CircleAlertIcon` en las
  alertas de error con TDD): **510**. Approved with minors. Se corrigieron m1 (el test exige el icono como
  primer hijo), m2 (test del foco de "Sí, rechazar") y el nit 4 (`badge` `destructive` sin `ring-destructive/20`).
  Se aceptaron tres desviaciones: icono también en "Motivo del error", `badge` `link` en `text-enlace` y
  (luego corregida) el anillo del `badge` `destructive`.
- **T3** (`ganera-logo-rojo.svg` en lugar del verde; `favicon-64.png` y `ganera-logo-512.png` en `#EC3013`
  con el mismo encuadre, generados con Chromium headless fuera del repo; test de recursos): **512** (37
  ficheros). Approved.
- **T4 (cierre):**
  - `npm test` **512/512**; build en verde (aviso del chunk); lint con los 3 avisos de siempre;
    `./mvnw clean test` **534/534**; `impeccable detect` sin hallazgos.
  - Smoke en navegador real con A `ACTIVA` y B `SUSPENDIDA` a 1440 y 375 px, todo PASS (`ct-t4-smoke.md`).
    32 capturas con datos inventados en `C:\Users\Antonio\Desktop\capturas-ganera-marca\`. La sesión
    principal revisó las capturas: no hubo nada que corregir.
  - `DESIGN.md` reescrito (paleta, regla de los dos rojos, contrastes medidos, pesos); `PRODUCT.md`
    (identidad, facturación fuera de la app, Archivo); los tres surface briefs de `.impeccable/surfaces`;
    `CLAUDE.md`, `ganera-prompts.md` y este fichero al día.
  - Observaciones para Antonio: las columnas de enlaces (el `#id` de la cola y los ganaderos de
    Explotaciones) quedan en rojo 700; y el anillo de foco sobre la píldora activa de la navbar se lee como
    un borde más oscuro (cumple 5,91:1).
- **Decisiones de Antonio sobre las observaciones (2026-10-05):** las columnas de enlaces en tablas van en
  Tinta, con Rojo 700 y subrayado solo en hover y foco; el foco sobre la píldora activa se queda así; los
  radios siguen pendientes con su socio.
- **T5** (`CLASE_ENLACE_TABLA` en `shared/ui/enlace.ts`, aplicada solo al `#id` de la cola, al nombre de la
  tabla de Ganaderos y al ganadero de la tabla de Explotaciones; tests primero; `DESIGN.md` y `CLAUDE.md`):
  **513**. Approved with minors; corregidos m1 (dos frases de `DESIGN.md`) y m2 (`not.toHaveClass` partido
  por clase). Capturas 05 y 15 repetidas, más 05b (hover) y 15b (foco), con los datos nuevos de la app de
  prueba: reposo rgb(32, 30, 29); hover y foco rgb(174, 24, 0) con subrayado y anillo de 3 px.
  `npm test` 513/513, build y lint con los avisos de siempre.
- **Sin commit**: pendiente de la aprobación de Antonio.

## 2026-10-05 — Prompt B1: WhatsApp + IA, lo mínimo (backend y ajuste mínimo de frontend)

Plan: `docs/superpowers/plans/2026-10-05-promptB1-whatsapp-ia.md`. Antonio aprobó R1–R4 y D1–D8 con los
añadidos A1–A5 (defectos primero; la fecha del hecho pasa a B2 porque `Tramite` no tiene campo de fecha;
"Extracción en curso" en la cola; número desconocido → `<Response/>` vacío; orden de cierre del smoke).
Informes en `.superpowers/sdd/b1-*`.

- **T0 (los tres defectos):** firma de Twilio con todos los parámetros y `TWILIO_WEBHOOK_URL`; fail-closed
  (`503` sin token o URL, `403` con firma mala, nada guardado); Spring AI sustituido por
  `com.anthropic:anthropic-java` 2.68.0 con salida estructurada nativa (`output_config.format`), modelo
  `claude-haiku-4-5-20251001`, cliente perezoso. Tests sin mocks del SDK (HMAC-SHA1 propio; `HttpServer`
  del JDK como API). Arranque real con H2 sin credenciales: arranca y el webhook da `503`. 534 → **563**.
  El primer intento del implementador se cortó por un 529 de la API sin haber tocado nada; se reanudó.
  - Revisión: Approved with minors. Corregidos m1 (orden 403 antes que 400, con tests), m2 (la excepción
    no encadena la causa del SDK, que lleva la respuesta del modelo), m3 (`logLevel(OFF)`: `ANTHROPIC_LOG`
    volcaría el texto), m4 (test que compara `TipoExtraido` con `TipoTramite`), n1, n2 (`@PreDestroy`),
    n4 (log de arranque con solo el host). **572**.
  - n3: Antonio decidió las descripciones de los tipos (venta y matadero = MOVIMIENTO; BAJA = muerte o
    sacrificio en la explotación; CENSO y DEMORA descritos). Aplicado en el prompt de sistema desde la
    sesión principal; `./mvnw clean test` **572/572**.
  - n5 (`CLAUDE.md` aún dice Spring AI) y el riesgo de Jackson 2.18.2 frente a 2.19.4 del SDK: al cierre.
- **T1 (recepción, enrutado, idempotencia y acuse):** V19 (todas las columnas de D3), `MensajeEntranteService`,
  trámite en la gestoría del Contacto con la explotación de D2, `SIN_TEXTO` sin IA, TwiML con el acuse de D7 o
  `<Response/>` vacío (desconocido, inactivo, duplicado), violación de `UNIQUE(message_sid)` propagada y confirmada
  con `existsByMessageSid`, teléfono enmascarado en logs, `@Lob` fuera. E2E con dos gestorías y carrera real de
  duplicados. 572 → **603**.
  - Revisión: Approved with minors. Corregidos m1 (un fallo al crear el trámite ya no pierde el mensaje: se
    rescata en `REQUIRES_NEW` con `ERROR_RECEPCION` y `<Response/>`; si el rescate falla, 500 y log con el
    `MessageSid`), m2 (`From` de más de 30 caracteres truncado → desconocido), n2 (`toString` sin datos), n4
    (`createdAt` del `Clock`). **611**. Pendientes: n1 (`logServerErrorDetail=false` en la URL de Postgres de
    producción) al cierre; n3 (comparar con un `Instant` del `Clock`) en el brief de T2.
- **T2 (extracción en segundo plano):** `ExtraccionValidador` (puro), `ExtraccionTramiteService` +
  `ExtraccionTramiteScheduler` (cola en BD, `Instant` del `Clock`, IA fuera de toda transacción, aplicación bajo
  `findConBloqueoByIdAndGestoriaId` + `refresh` con nueva comprobación, dedupe por Animal, reintentos 1/5/30 min y
  4 intentos, contabilidad de reintentos por JPQL sin tocar `version`). Un error que no viene de la IA también
  cuenta como fallo (si no, bloquearía la cabeza de la cola; documentado). 611 → **645**.
  - Revisión: Approved with minors. Corregidos I1 (test de PATCH durante el reintento: gana el empleado), m1 (la
    acción del empleado en otro hilo con timeout: prueba de verdad que no hay bloqueo durante la IA), m2
    (`spring.task.scheduling.pool.size: 3`, para que la IA no retrase el job de Stripe ni el de retención), m3
    (documentado), m4 (test de los `UPDATE` con otra gestoría → 0 filas), n1 (`clearAutomatically`), n2, n3.
    **650**. n4 (`TramiteRevisionService.actualizar` lee el mensaje sin `gestoriaId`; anterior a B1, el trámite
    ya viene resuelto con su gestoría) queda anotado.
- **T3 (respuestas y retención):** `origen`, `estadoExtraccion` y `crotalesDescartados` en `TramiteResponse` y
  `TramiteDetalleResponse`; `RetencionMensajesService` + `RetencionMensajesScheduler` (03:30 Madrid; borra a los
  30 días los mensajes sin trámite, vacía a los 12 meses el texto de los que tienen trámite; plazos `Period`
  configurables, pendientes del abogado). 650 → **673**. El implementador se cortó por el límite de uso durante el
  arranque real (su cron de prueba con guiones bajos no era válido y la app no llegó a arrancar). La sesión principal
  comprobó que no quedaba proceso ni mutación aplicada (las 4 copias de `t3mut` idénticas con `cmp`), limpió el
  scratchpad, repitió el arranque (cron por variable de entorno: dos pasadas sin errores en hilos distintos del pool;
  parado por PID) y escribió el informe con el contrato JSON.
  - Revisión: Approved with minors. Corregidos m1 (test de límite con cambio de hora), m2 (no vaciar un `SIN_TEXTO`),
    m3 (E2E de PATCH/aprobar/rechazar con los tres campos), n3 (ruta robusta). **676**. n1 (datos que la retención no
    cubre) y n2 (lotes) anotados en las notas del Prompt C; n4 (TIMESTAMP sin zona en Postgres real) sin probar: no
    hay Postgres aquí. Alerta de `ERROR_RECEPCION` anotada en el Prompt C a petición de Antonio.
- **T4 (frontend mínimo, excepción explícita):** shape de Impeccable desde la sesión principal, confirmado por
  Antonio (en el plan). Tipos, `ORIGENES_TRAMITE`/`TEXTOS_EXTRACCION`/`avisosExtraccion()` en `etiquetas.ts`, icono
  de WhatsApp junto al `#id` (fuera del botón), línea de extracción bajo el estado, "N descartados" en Crotales,
  avisos fijos ámbar en el modal (texto estático sin `role`, tras el título enfocado), solo con el trámite pendiente.
  513 → **556**.
  - Revisión: Approved with minors. Corregidos m2 (test del `mb-4`), n1 (test de los tres campos tras aprobar o
    rechazar) y n2 ("Recibido por WhatsApp" se anuncia una vez; `title` en un span `aria-hidden`). **558**. m1 y n3:
    sesión principal (`DESIGN.md` con el indicador, la línea de extracción y los avisos, y la nota de que el aviso de
    `FALLIDA` sigue tras guardar a propósito).
  - Pase visual en navegador real (Vite + API simulada con `page.route`, datos inventados, Playwright 1.58 en el
    scratchpad, headless shell 1243) a 1440 y 375 px: sin scroll horizontal de página, la tabla hace scroll en su
    contenedor, un "Recibido por WhatsApp" por fila, foco en el título al abrir el modal, ámbar sin rojo, el #105
    aprobado sin aviso. Un hallazgo corregido con TDD desde la sesión principal: "Extracción en curso" en el modal,
    con borde y del alto de un campo, se leía como un input vacío; pasa a una línea en Gris Texto sin recuadro.
    `npm test` **558/558**, lint con los 3 avisos de siempre. Vite parado por PID.
- **T5 (cierre):** `./mvnw clean test` **676/676**; `npm test` **558/558**; build (aviso del chunk) y lint (3 avisos)
  en verde. Smoke real con el sandbox de Twilio desde el móvil de Antonio, con Cloudflare Quick Tunnel y H2 en
  fichero, sobre dos gestorías: los cuatro escenarios PASS (`b1-t5-smoke.md`). Incidencia de configuración:
  `TWILIO_AUTH_TOKEN` tenía una clave de Anthropic; el webhook falló cerrado (`403`), Antonio lo corrigió y el
  backend se reinició sin cortar el túnel. Cierre en el orden de A5: URL del sandbox vaciada por Antonio, túnel
  parado y comprobado (530/1033), procesos parados por PID y scratchpad entero borrado (H2 con su número
  incluida). `CLAUDE.md` (bullet de B1, proveedor de IA, flujo, estado y conteos), `DESIGN.md`, el surface brief
  del modal, `ganera-prompts.md` y este fichero al día.
- **Sin commit**: pendiente de la aprobación de Antonio.

## 2026-10-06 — C mínimo: demo desplegada en la UE (backend, frontend y datos de demo fuera del repo)

Plan: `docs/superpowers/plans/2026-10-06-promptC-minimo-demo-desplegada.md`. Decisiones de Antonio: D1 Clever
Cloud (París), D2 PostgreSQL XXS Small, D3 frontend en Clever Static, D4 `ganera.registro.abierto` cerrado por
defecto (fail-closed) y backend + frontend en el mismo commit, D5 trámites por SQL en Adminer (no tiene `psql`),
D6 el smoke lo ejecuta Antonio en su terminal y Claude solo ve PASS/FAIL, D7 WhatsApp al final y opcional, D8
tras T1 (sin `V20`; SQL a mano con `SET TIME ZONE 'UTC'` y `created_at` explícito; `TZ=UTC`; `ALTER DATABASE …
SET timezone TO 'UTC'` comprobado con `SHOW timezone`). Informes en `.superpowers/sdd/c0-*`.

- **T1** (PostgreSQL 16 embebido con zonky, solo test): Flyway V1–V19 + `validate`, V18 con 1203 filas, `Instant`
  en `TIMESTAMP` sin zona en UTC y Madrid. La hipótesis de desfase del plan §7 **no se cumple** (sensibilidad
  comprobada forzando `preferred_instant_jdbc_type=TIMESTAMP`); solo `DEFAULT now()` depende de la zona de sesión.
  676 → **684**. Approved with minors; m1 (`@Disabled` → test de caracterización) y m2 (Javadoc) corregidos.
- **T2** (`RegistroCerradoFilter` + `RegistroConfig`, `application-clever.yml`, `.clever.json` en `.gitignore`):
  **690**. Approved with minors; el revisor probó 38 variantes de ruta con socket crudo. M1 (el plan aún decía
  `registro.activo`) corregido en el plan; N3 (no definir `GANERA_REGISTRO_*` en Clever) añadido al plan.
- **T3** (enlace "Regístrate" fuera, `/registro` → `/login`, `RegistroPage` y su API borradas, `noindex`):
  558 → **557** (36 ficheros). Approved with minors; `DESIGN.md` (tres frases) y el test del anillo de foco de
  `CLASE_ENLACE` corregidos desde la sesión principal. `PRODUCT.md` (alta pública, l. 53/59/70/83) pendiente de
  la excepción de Antonio.
- **T4** (fuera del repo, `C:\Users\Antonio\Desktop\ganera-demo\`: generador, dos Excel, `seed-tramites.sql`,
  `alta-gestorias.sh`, `smoke.sh`, `LEEME.md`): ensayo contra PostgreSQL real, smoke 20/20. Approved with minors;
  m1–m7, n1, n2 y n4 corregidos (CORS sin `Origin` daba PASS, PATCH con `version`, salida de error del alta,
  crotales del trámite "sin explotación" de una misma explotación, NIF con letra imposible) y ensayo repetido:
  smoke **21/21**.
- **Cierre previo al despliegue (sesión principal):** `npm test` 557/557, build y lint con los avisos de siempre;
  `./mvnw clean test` **690/690** (0 fallos, 0 saltados) en la sesión principal, sin `postgres.exe` al acabar. `CLAUDE.md`, `ganera-prompts.md` y este fichero al día.
- **Commit `0d761f3`** con el visto bueno de Antonio (37 rutas añadidas una a una), push a `origin main` comprobado
  con `git ls-remote`.
- **Parado sin desplegar (2026-10-06, decisión de Antonio):** por ahora no se paga hosting. El despliegue en Clever
  (cuenta, add-on PostgreSQL, las dos apps, variables, `ALTER DATABASE … SET timezone TO 'UTC'`, `clever deploy`,
  siembra y smoke) queda pendiente, para hacerlo con los créditos gratuitos o antes del piloto. Pasos y variables
  completos en `ganera-prompts.md`, "Despliegue en Clever Cloud — pendiente". Los datos y scripts de demo siguen en
  `C:\Users\Antonio\Desktop\ganera-demo\` (fuera del repo).
- **Dominio:** Ganera ya tiene `ganera.es` en IONOS, sin configurar todavía.

## 2026-10-06 — Ficha lista para OVZ (escalón 1 de OVZ)

Análisis del manual de OVZNET (Junta de Extremadura, rev. 13.01/13.02; el PDF fuera del repo, `docs/referencias/*.pdf`
en `.gitignore`) en `docs/referencias/ovz-tramites-bovino.md`. Plan: `docs/superpowers/plans/2026-10-06-ficha-ovz.md`.
Decisiones de Antonio:
- D1 sí, con dos condiciones: `V20`, prompt de la IA y frontend en el mismo commit; `MOVIMIENTO` →
  `SOLICITUD_MOVIMIENTO`, con test de migración también en PostgreSQL embebido.
- D2, D4, D6 y D7, como se recomiendan.
- D3 sí, comprobando la sesión en la ventana nueva: `window.open` sin `noopener` y `opener = null` al montar;
  red de seguridad con el login de vuelta a la ficha. Se prueba en un navegador real en T5.
- D5 ("Marcar como registrado en OVZ"), fuera: es la siguiente tarea antes del piloto, anotada en `ganera-prompts.md`.
- El guion de la "sesión de mapeo de OVZ" (listas de valores y preferencias de la gestoría) queda en
  `ganera-prompts.md`.
Informes en `.superpowers/sdd/fo-*`.

- **T1** (tipos de OVZ, `V20` + IA + frontend): backend 690 → **697**, frontend 557 → **563**. Approved with minors.
  M1 (el test de H2 aceptaba cualquier `SQLException` antes de la V20; ahora exige SQLState `22001`) corregido desde la
  sesión principal; tests de la V20 en verde. M2 (las etiquetas largas se recortan en el `Select`, por el `nowrap`) se
  mira en el smoke de T6. Notas: finales de línea LF en el árbol (no ensucian el diff); `ganera-demo/seed-tramites.sql`
  sigue con `'BAJA'`/`'MOVIMIENTO'`; backend y frontend se despliegan juntos.
- **T2** (titular en el detalle: `ganaderoNombre`/`ganaderoNif` en `TramiteDetalleResponse`): backend 697 → **702**.
  Approved with minors. M1: la regresión con `ovzPasswordCifrada` que pedía el brief del revisor **no se ejecutó** (la
  bloqueó el clasificador de permisos); la del implementador con `ovzUsuario` sí (3 tests en rojo); queda para Antonio.
  M2: el detalle carga el `Ganadero` entero y descifra la contraseña de OVZ en cada GET/PATCH (un valor corrupto o una
  rotación de `ENCRYPTION_KEY` daría `500`; ya pasa igual en `/ganaderos`); pendiente de decisión de Antonio
  (aceptarlo o proyección JPQL con `gestoriaId`). N1/N2 opcionales.
  **Decisión de Antonio (2026-10-07):** M2 se acepta; queda pendiente del resto del bloque C, antes del piloto: «descifrar la contraseña de OVZ solo bajo demanda, no al cargar el ganadero; hoy una cifra corrupta o un cambio de ENCRYPTION_KEY da 500 en el detalle y en /ganaderos»
  (anotado en `ganera-prompts.md`, notas del Prompt C). La prueba de la contraseña de OVZ, autorizada como test automático con
  contraseña y clave inventadas (sin tocar `ENCRYPTION_KEY`, `.env` ni secretos reales).
- **T3** (catálogo puro `fichaOvz.ts`, `modoFicha` de D4, `ganaderoNombre`/`ganaderoNif` en `TramiteDetalle`): frontend
  563 → 645 (37 ficheros). Approved with minors; corregidos por el implementador: M1 (`CampoFicha.escrito` con el
  `crotalIndicado`, nunca se copia), M2 (bloque "Crotales del trámite" aparte en el alta, no obligatorio), M3
  (`satisfies` sobre `TipoTramite`, `buscar` exportado de `etiquetas.ts`), N1 (avisos de 15 días MER, pestañas
  Internos/Externos, 13 días), N3 y N4. **668** (37 ficheros), build y lint con los avisos de siempre.
- **T2b** (la contraseña de OVZ nunca sale en el detalle; autorizada por Antonio el 2026-10-07):
  `CredencialesOvzDetalleEndToEndTest`, con contraseña y clave inventadas en el propio test. Backend 702 → **706**. Pasan las 4
  comprobaciones: cifrado real en BD ≠ claro; GET y PATCH sin claro, cifrado, `ovzUsuario` ni claves, ni en el cuerpo ni en los logs
  capturados; `404` cruzado; cifrado corrupto → `500` con el cuerpo por defecto de Boot y sin el texto cifrado en cuerpo ni logs
  (caracteriza el M2). **Las dos pruebas de regresión (campo `ovzPasswordCifrada` en la respuesta y `log.warn` del cifrado) NO se
  ejecutaron: las bloqueó el clasificador de permisos** ("Credential Leakage"), pese a la autorización de Antonio; queda en sus manos.
- **T4** (`shared/ui/BotonCopiar`): cortado por el límite de uso y retomado el 2026-10-07 en el orden de Antonio (mutación con
  componente vacío → 14 tests en rojo; pasa; regresión del temporizador; suite). Approved with minors; corregidos I1 (tests de
  desmontar con la copia pendiente y de vaciar la región), M1 (fuera el shim `globalThis.jest`: `act` + `fireEvent`), M2 (se vuelve a
  anunciar con `flushSync` + siguiente tick), M3 (`Input` de `components/ui`), aviso "Ctrl+C o ⌘C" y `mockRestore` sobrantes; cada
  corrección con su mutación. Frontend **685** (38 ficheros). Para la T5: WCAG 2.5.3 con "Copiado" visible y una región compartida
  o una por botón.
- **T5** (página `/tramites/:id/ovz`, desde la sesión principal con Impeccable; shape aprobado por Antonio: sin barra, marca de
  filas copiadas, mensaje abierto y plegable). TDD: `BotonCopiar` siempre dice "Copiar" (WCAG 2.5.3) + `onCopiado`; `abrirFichaOvz`
  (`window.open` sin `noopener`, nombre fijo; mutación con `noopener` → rojo); `FichaOvzPage` (mutación vista previa copiable → rojo);
  ruta fuera de `AppLayout`; botones en el modal. El test de honestidad del modal pasa de "sin OVZ" a "la única mención es el
  botón y nada dice enviado/ejecutado/registrado…". Revisión independiente: Approved with minors; corregidos M1 (asterisco en
  tinta), M2 (vista previa desactivada con cambios sin guardar), M3/M4 (tests de varios bloques en vista previa, estado
  desconocido y título restaurado), N5 ("(se abre en otra ventana)" con `aria-label`), N6 (texto de "no disponible") y N7 (regex).
  Anotados para Antonio: N1 (cabecera COOP en el Prompt C), N2 (la ventana reutilizada conserva el token si en la principal se
  entra con otro usuario: no filtra, da 404), N3 (el check de fila copiada es de tinta, el shape decía gris). Frontend **720** (40).
- **T6 (cierre):** smoke en navegador real (H2 en fichero, dos gestorías inventadas sembradas por la API real, Playwright en el
  scratchpad): **36/36 PASS** en dos pasadas (sesión heredada en Chromium y Firefox, copiar y pegar de verdad, todos los tipos y
  estados, `404` cruzado, sin scroll lateral a 375/420/1440, login y vuelta desde una pestaña sin opener). `./mvnw clean test`
  **706/706** sin `postgres.exe` colgado; `npm test` **720/720**, build y lint con los avisos de siempre. Servidores parados por PID;
  H2 y secreto del smoke borrados. `CLAUDE.md`, `DESIGN.md`, `ganera-prompts.md` y el plan al día. **Commit `db0d234`** con el visto
  bueno de Antonio. **Pendiente:** regenerar `ganera-demo/seed-tramites.sql` con los tipos nuevos, que pasa a ser un paso de la tarea de despliegue
  (anotado en `ganera-prompts.md`, "Despliegue en Clever Cloud — pendiente").

## 2026-10-07 — Hecho en OVZ (D5 de la ficha OVZ)

Plan: `docs/superpowers/plans/2026-10-07-hecho-en-ovz.md`. Decisiones de Antonio: D1 estado nuevo `HECHO_EN_OVZ` +
`hecho_en_ovz_por`/`hecho_en_ovz_en` + `CHECK`; D2 se puede deshacer (marcar sin confirmación, quitar con confirmación
en línea); botón en la ficha y en el modal; badges «Aprobado · falta OVZ» (warning) y «Hecho en OVZ» (success); filtro
sin `EN_PROCESO`/`EJECUTADO_OVZ`/`ERROR_OVZ` (el enum no se toca); sin bloqueo de suscripción; quién marca sale del JWT;
la ficha se abre en `HECHO_EN_OVZ` en modo consulta; PATCH en `HECHO_EN_OVZ` → `409` «quita la marca»; fecha en
Europe/Madrid; sin pase de Impeccable (patrones existentes, revisión en las capturas). Informes en `.superpowers/sdd/ho-*`.

- **T1** (backend: `V21`, estado, dos endpoints, campos en las respuestas, mensaje con `gestoriaId` en el detalle y el
  PATCH): 706 → **742**. Cuatro regresiones en rojo (sin `CHECK`, sin asignar el usuario, finder sin `gestoriaId`, sin
  el `@EntityGraph`). Approved with minors: N1 (comentario de los estados reservados) corregido desde la sesión
  principal; M1, M2 y N3 anotados en `CLAUDE.md` como limitaciones conocidas.
- **T2** (frontend + `DESIGN.md`): 720 → 794. Changes requested por I1 (foco y anuncio en la ficha); corregidos I1 y
  M1–M4 → 810; segunda pasada Approved with minors; M5 (test de que la ficha no roba el foco), N7 y N9 corregidos →
  **811** (41 ficheros). Sin «Cerrar» visible en el pie de `HECHO_EN_OVZ` (la X ya se llama «Cerrar»).
- **T3** (smoke delegado; `ho-t3-smoke.md`): H2 en fichero, A `ACTIVA` con tres usuarios y B `SUSPENDIDA`, Chromium y
  Firefox: **13/13 PASS** (27 comprobaciones). 42 capturas en `C:\Users\Antonio\Desktop\capturas-ganera-hecho-ovz\`.
  N8 (que el aviso de error enfocable con `role="alert"` se lea dos veces) no se pudo comprobar sin lector de pantalla.
  Procesos parados por PID y scratchpad borrado.
- **Para el despliegue demo:** `ganera-demo/seed-tramites.sql` y `smoke.sh` (fuera del repo) deben incluir
  `HECHO_EN_OVZ` (con las dos columnas rellenas, o el `CHECK` lo rechaza) y probar marcar y quitar la marca (anotado en
  `ganera-prompts.md`, paso 10 del despliegue).
- **Revisión de Antonio (2026-10-11):** el rojo de «Sí, quitar la marca» se queda; sin «Cerrar» en el pie de
  `HECHO_EN_OVZ`; se acepta que la BD no compruebe la gestoría de quien marca; lo de `ON DELETE` se decidirá con el
  borrado de usuarios y el RGPD.
- **Commit** con el visto bueno de Antonio, después de un commit `docs:` aparte con la corrección de «pendiente de
  commit» de la ficha (ya estaba en `db0d234`).

## 2026-10-11 — B2 reducido: la gestoría responde al ganadero por WhatsApp

Plan: `docs/superpowers/plans/2026-10-11-promptB2-reducido-responder-whatsapp.md` (aprobado con las «Decisiones de
Antonio» de arriba del plan). Antes, commit `docs:` `e7aa8a3` (alcance de B2, OVZ siempre asistido, correcciones).
Orden de commits: docs → envío (backend + frontend, `V22`) → callback de estado (solo backend). Informes en
`.superpowers/sdd/b2-*`.

- **T1** (backend del envío): 742 → 787. **Changes requested** por I1: el SDK de Twilio 10.6.4 reintenta los 5xx
  (`reliableRequest`) y un 5xx se guardaba `FALLIDO`; corregido con `HttpClientSinReintentos` y 5xx → `SIN_CONFIRMAR`,
  más M1 (clave repetida gana a los 409), N3 (contacto de otra gestoría → 404) y N4 (503 igual para ajeno) → **796**.
  Segunda pasada **Approved with minors** (N1, N2, N5 como notas).
- **T2** (frontend): 811 → 884 (43 ficheros). **Approved with minors** (M1, N1–N8). Vocabulario sin «enviado» por el
  test de honestidad (la lista no se toca): «Aceptado por WhatsApp», «No ha llegado», «Sin confirmar»…
- **Corte por límite de sesión** con las correcciones en paralelo sin empezar (comprobado: ningún fichero cambió
  tras la revisión). Hechas desde la sesión principal con TDD: N4 (texto del 409 «…Escribe otra.»), M1, N1, N5, N7
  y N8 (decisión de Antonio: preguntar al cerrar con la respuesta editada) → 903. Revisión independiente **Approved
  with minors**: M1b/N-d (el modal no se cerraba si acababa en «no encontrado» con el panel abierto), corregido.
- **T3** (smoke, Twilio falso compilado fuera del repo, H2 en fichero, trámites por el webhook real con firma): 11/12
  PASS; falló el 9 (N2: el foco caía en `body` al desactivarse «Enviar» y tras un 409). Corregido («Enviando…» solo
  `aria-disabled`; foco a la alerta tras un error) → 910. Re-smoke del foco (T3b) 5/5 PASS en Chromium y Firefox; Firefox no desplazaba el modal hasta la alerta enfocada: `scrollIntoView({ block: "nearest" })` tras el foco → 911; test de un segundo clic sobre «Enviando…» (en rojo sin la guarda) → frontend **912/912**; comprobación del desplazamiento en navegador (T3c) 8/8 PASS (Chromium y Firefox, 1440 y 375 px). Nota: el anillo de foco de la alerta queda bajo el pie por abajo (posible `scroll-mb-3`), a decidir. Doble clic con «Enviando…» como `aria-disabled` (T3d): 12/12 PASS en Chromium y Firefox a 1440 y 375 px (ratón, clics forzados y por JS, teclado): una sola llamada al Twilio falso en cada pasada.
- Decisiones de Antonio en esta tarea: N3 y N6 quedan como notas; callback de estado en el commit 3.
- **Commit `f816f6e`** (envío, 66 ficheros) con el visto bueno de Antonio, tras confirmar en navegador real el doble
  clic con «Enviando…» como `aria-disabled` (T3d, 12/12) y cuadrar las rutas contra `git status`.
- **T4 — callback de estado de Twilio** (plan `docs/superpowers/plans/2026-10-11-b2-callback-estado-twilio.md`, D1–D3
  con los ajustes de Antonio: vaciar el código al corregir un `FALLIDO`, SID aún no guardado → `200` sin cambios,
  rutas públicas exactas en vez de `/webhooks/**`, `ErrorCode` acotado). Backend 796 → **906**; frontend sin cambios
  (912). Cinco regresiones en rojo (firma, tope de tamaño, `UPDATE` que retrocede, fila por otro dato que el SID,
  volver a abrir `/webhooks/**`). Revisión independiente **Approved with minors** (M1: el log da el conjunto de estados
  anteriores posibles, no el exacto; N1–N6 notas; N4: usar `https` en la URL de callback), aceptados y anotados en
  `CLAUDE.md`.

### Traspaso del commit 3 (2026-10-11, sesión cortada por límite; sin commit ni push)

- **Hecho y verificado:** código de la T4 (implementador) con `./mvnw clean test` **906/906** según el implementador y,
  de forma independiente, según el revisor (Approved with minors, cinco regresiones repetidas en rojo). `npm test`
  **912/912** ejecutado por la sesión principal tras el último cambio; `frontend/` sin cambios (`git status` vacío).
  Diff de los dos `application.yml` revisado: solo `status-callback-url: ${TWILIO_STATUS_CALLBACK_URL:}` (main) y `""`
  (test), sin secretos.
- **Verificado también por la sesión principal:** su propio `./mvnw clean test` terminó con **906/906, BUILD SUCCESS**
  (leído tras escribir esta nota; el código no cambió desde entonces, solo documentación).
- **Revisado el 2026-10-12 (sesión nueva):** `./mvnw clean test` **906/906** otra vez; desglose de +110 por los informes de
  surefire: `CallbackEstadoWhatsappTest` 75 (17 métodos, casi todos parametrizados), `TwilioEstadoWebhookEndToEndTest` 22,
  `TwilioEstadoWebhookSinConfiguracionEndToEndTest` 6 (3 clases anidadas × 2), `WebhooksPublicosEndToEndTest` 4,
  `CallbackEstadoConcurrenciaPostgresTest` 1, `TwilioEnvioWhatsappArranqueTest` +2. Diff de `CLAUDE.md`, `ganera-prompts.md` y
  `progress.md` revisado contra el código (transiciones, URL de callback, orden del filtro, rutas públicas, cuerpos vacíos).
- **Falta:** enseñar a Antonio `git diff --cached --stat`; commit (`feat: callback de estado de Twilio para las respuestas por
  WhatsApp`) y push solo con su visto bueno; después, el envío real al sandbox lo hace Antonio.
- **Rutas del commit 3 (21, contadas contra `git status`; fuera `.superpowers/sdd/b2-*`, `capturaTramites.jpeg`,
  `logo.jpg`, `preview.png`):** `.superpowers/sdd/progress.md`, `CLAUDE.md`, `ganera-prompts.md`,
  `docs/superpowers/plans/2026-10-11-b2-callback-estado-twilio.md`; backend modificados:
  `shared/security/SecurityConfig.java`, `whatsapp/RespuestaWhatsappRepository.java`, `whatsapp/TwilioEnvioWhatsapp.java`,
  `src/main/resources/application.yml`, `src/test/resources/application.yml`, tests
  `whatsapp/ClasificadorEnvioWhatsappTest.java`, `whatsapp/TwilioEnvioWhatsappArranqueTest.java`; backend nuevos:
  `whatsapp/CallbackEstadoWhatsapp.java`, `whatsapp/EstadoRespuestaWhatsappService.java`,
  `whatsapp/LimiteCuerpoCallbackEstadoFilter.java`, `whatsapp/TwilioEstadoWebhookController.java`,
  `whatsapp/WhatsappFiltrosConfig.java`, tests `postgres/CallbackEstadoConcurrenciaPostgresTest.java`,
  `shared/security/WebhooksPublicosEndToEndTest.java`, `whatsapp/CallbackEstadoWhatsappTest.java`,
  `whatsapp/TwilioEstadoWebhookEndToEndTest.java`, `whatsapp/TwilioEstadoWebhookSinConfiguracionEndToEndTest.java`.
- **Commit `5eeb39e`** (callback de estado, 21 ficheros) con el visto bueno de Antonio el 2026-10-12, tras `npm test`
  912/912 (43 ficheros; la referencia de 911 de Antonio era anterior al test del segundo clic, que ya está en
  `f816f6e`) y `git diff --cached` idéntico a la lista de 21.

### B2 reducido cerrado: prueba real con el sandbox de Twilio (2026-10-12)

**B2 reducido queda cerrado** (envío `f816f6e` + callback de estado `5eeb39e`). Prueba real de Antonio, desde su terminal
y a su propio móvil, con Cloudflare Quick Tunnel, H2 en fichero fuera del repo
(`C:\Users\Antonio\Desktop\ganera-prueba-b2\`), una gestoría creada por el onboarding interno y un Excel inventado con su
móvil como `TITULAR`: respuesta enviada desde el modal → **«Aceptado por WhatsApp»**; callbacks con el **mismo SID** →
**`ENTREGADO`** y después **`LEIDO`**, con `codigo=null`; en el log **ni teléfono ni texto**. Claude no ejecutó nada de la
prueba (solo preparó los comandos y el Excel). **Incidencia de secretos:** durante la prueba, el Auth Token de Twilio
apareció por error en el chat del asesor (no en la sesión de Claude Code); Antonio lo rotó en la consola de Twilio
(secondary → promote) antes de seguir.

**Correcciones a los comandos que se prepararon, para la próxima prueba real:**
- **Onboarding:** necesita el backend arrancado, así que se hace desde **otra** terminal. Por eso `ONBOARDING_SECRET` **no
  se genera al azar**: se introduce a mano con `Read-Host` (con `-AsSecureString` y el paso a texto de 5.1) **con el mismo
  valor en las dos terminales** (la del backend y la del `Invoke-RestMethod`).
- **Frontend:** si la política de ejecución de PowerShell bloquea `npm.ps1`, usar **`npm.cmd run dev`**.
- **`Read-Host`:** las respuestas se pegan **línea a línea**, una por pregunta (pegar varias líneas de golpe las reparte
  mal entre las preguntas).
- **Nunca escribir un valor dentro de un comando; los datos solo se escriben cuando `Read-Host` los pide.**
- **Cuerpos JSON en PowerShell 5.1: nada de funciones auxiliares que devuelvan bytes** (smoke de SIN_TRAMITE,
  2026-10-12). `function Json($o) { [Text.Encoding]::UTF8.GetBytes(($o | ConvertTo-Json -Depth 5)) }` dio un `400`
  genérico en el onboarding: una función que devuelve un `byte[]` lo **desenrolla** por la tubería y `-Body` recibe un
  `object[]` de bytes sueltos, no JSON. Funciona montando el cuerpo en línea, como en B2:
  `-Body ([Text.Encoding]::UTF8.GetBytes((@{ … } | ConvertTo-Json)))` (o, si se quiere función, `return ,$bytes`). La
  prueba en seco de la sesión principal usó `GetBytes` en línea y no cubrió la función: **probar en seco exactamente
  los comandos que se entregan.**

### SIN_TRAMITE en curso (2026-10-12; sin commit ni push)

Plan aprobado: `docs/superpowers/plans/2026-10-12-sin-tramite.md` (decisiones A1–A6 de Antonio arriba). Briefs e
informes en `.superpowers/sdd/st-*`.
- **T1 (IA y extracción):** hecha. 920 + 1 omitido (la evaluación). Prompt ajustado por la sesión principal tras la
  primera evaluación (falso `SIN_TRAMITE` en «ok, apunta el censo»). **Falta su revisión** (el primer revisor se cortó
  por el límite; trabajaba en una copia del scratchpad, sin tocar el repo).
- **Evaluación contra Haiku:** tres ejecuciones reales de la sesión principal (clave de las variables de usuario de
  Windows, nunca vista): 1.ª con 1 falso; 2.ª y 3.ª con 0 falsos y charla 18/18; 0 `FALLO`. Coste estimado ≈ 0,15 $. El
  informe todo `FALLO` es una mutación del revisor sin clave (no es una ejecución real). **Arreglo pendiente:** que
  cualquier `FALLO` haga fallar la evaluación. **Desde ahora la evaluación solo la ejecuta Antonio** con las
  instrucciones de la sesión principal.
- **T2 (descartar, deshacer, V23, historial):** a medias, **solo tests** (fase roja). Sin código de producción ni `V23`.
  El backend no compila hasta terminarla.
- **T3 (frontend):** hecha (965 en 44 ficheros). **Falta su revisión** y cuatro ajustes de Antonio: pista propia de
  «Descartar» desactivado; ficha OVZ de un descartado («Descartado como mensaje sin trámite»); explicar/recortar el
  bundle (644 → 662 kB); E2E del contrato real backend ↔ frontend.
- **Orden pendiente:** T2 → revisor T2 → revisor T1 → revisor T3 → instrucciones del smoke y de la evaluación para
  Antonio.
- **Actualización (2026-10-12, tarde):** T2 terminada (informe `st-t2-report.md`, mutaciones A5 en rojo). Hechos por
  la sesión principal: evaluación que falla con cualquier `FALLO` y anota la causa; los cuatro ajustes de T3 (pista
  «Para descartar el mensaje, quita antes los cambios con “Descartar cambios”.», tras la revisión de T3; ficha OVZ «Descartado como mensaje sin trámite» / «Un
  mensaje descartado no se pasa a OVZ.»; bundle: `HEAD` ya pesaba 657,93 kB, SIN_TRAMITE suma +4,7 kB, todo código
  propio, nada que recortar; contrato compartido `frontend/src/test/contrato/descarte-sin-tramite.json` comprobado por
  `TramiteDescarteEndToEndTest.lasRespuestasRealesCumplenElContratoQueUsaElFrontend` y `contratoDescarte.test.tsx`,
  con mutaciones en rojo). Suites: backend **966** (965 ejecutados + 1 omitido; surefire cuenta el omitido en «Tests run»), frontend **971 en 45**, build y lint limpios.
  **Siguiente:** revisor T2 → revisor T1 → revisor T3.
- **Revisores:** T2 **Approved with minors** (m1 test del usuario ajeno al deshacer, m2 contrato estricto con `null` y
  tamaños: corregidos y comprobados con mutación); T1 **Approved with minors** (m1 tipo + sinTramite → manda el tipo,
  m2 constructor sin uso quitado, m3 Javadoc, m4 la evaluación anota cualquier excepción: corregidos). Backend **968** (967 +
  1 omitido). **Siguiente:** revisor T3, luego instrucciones del smoke y de la evaluación.
- **T3 revisada: Approved with minors.** Corregidos: pista de «Descartar» → «Para descartar el mensaje, quita antes
  los cambios con “Descartar cambios”.»; tras un fallo incierto al descartar/deshacer, si la recarga no muestra ni lo
  pedido ni el estado de partida, aviso neutro «consta ahora como …» (la mutación M7 sale ahora en rojo); honestidad de
  la ficha OVZ en rechazado y descartado. Frontend **975 en 45**, build y lint limpios. `CLAUDE.md` y
  `ganera-prompts.md` al día. Instrucciones para Antonio en
  `C:\Users\Antonio\Desktop\ganera-prueba-sin-tramite\INSTRUCCIONES.md` (arranque probado en seco con valores falsos).
  **Pendiente:** visto bueno de Antonio, commit, su evaluación y su smoke.
- **Evaluación y smoke de Antonio superados (2026-10-12, su terminal):** evaluación con 0 falsos SIN_TRAMITE, charla
  18/18, 0 fallos («novillas»: CONFIRMACION, CONFIRMACION, SOLICITUD). Smoke con el sandbox de Twilio, pasos 1–9 PASS:
  aviso en la cola, «Descartar» con su pista, descartar en un clic sin nada en el móvil, `404` desde B al descartar y al
  deshacer, ficha OVZ del descartado, «Deshacer» con historial, y «gracias, se ha muerto la 4521» como baja con
  `ES990000004521`. Incidencia: la función `Json` de `INSTRUCCIONES.md` (ver lecciones de pruebas reales). Commit
  aprobado por Antonio.
- **Alcance del modo asistido (escalón 2), aclarado por Antonio el 2026-10-12 — no se implementa ahora:** Ganera
  rellena el formulario de OVZ (con credenciales cifradas; de quién es la cuenta, gestoría o ganadero, queda pendiente
  del mapeo); el gestor revisa y pulsa
  «Enviar»; Ganera descarga el justificante. **Nunca se envía sin ese clic, y el envío es de uno en uno.** El botón
  «Enviar a OVZ» y los estados de envío del diseño de Álvaro son para ese momento; **en el diseño fase 1 no aparecen.**
  **Corregido en `CLAUDE.md` el 2026-10-12, en el commit de SIN_TRAMITE, solo texto** (decisiones de Antonio: 2, 3 y
  4 corregidos y sin «approve and commit, then execute asynchronously»; 1 y 5: **pendiente del mapeo** si la cuenta de
  OVZ es de la gestoría o de cada ganadero, «cuenta del gestor» no está decidido; los tres sitios cortos completados;
  «Prompt 3c» → «escalón 2 (modo asistido)»; además `:413` y `:513`, de la misma familia). Lo que se encontró:
  1. *Domain model → Ganadero* (líneas ~25-27): «Holds the OVZ.net credentials (`ovzUsuario` /
     `ovzPasswordCifrada`) — one login per Ganadero». **Pendiente del mapeo** si la cuenta de OVZ es de la gestoría o de
     cada ganadero (Antonio: la «cuenta del gestor» de la aclaración no tenía base; no está decidido). Las columnas
     siguen en `ganadero` desde `V3` y pueden cambiar según el resultado; ningún cambio de esquema en SIN_TRAMITE.
  2. *Non-negotiable rules* (líneas ~98-99): «No trámite is ever executed against OVZ.net without an employee
     explicitly clicking "Aprobar."». El clic que envía es el **«Enviar» del gestor tras revisar el formulario
     rellenado**, uno a uno; aprobar no envía nada.
  3. *Non-negotiable rules → frontend* (líneas ~129-133): «`OvzAutomationService.ejecutarTramite()` is unimplemented
     (Prompt 3c)… until 3c is real». Da a entender que, cuando exista 3c, aprobar ejecutará en OVZ.
  4. *Prompt A1 → Risks to respect in 3c* (línea ~557): «approve and commit, then execute asynchronously». Contradice
     «nunca sin el clic»; lo que sigue valiendo es no abrir la sesión de OVZ dentro de la transacción con el bloqueo.
  5. *No manual alta de Ganadero / OVZ-credentials screen* (líneas ~140-148), *Prompt 4* (línea ~1258) y *Still
     blocked* (línea ~1536): «OVZ-credentials-onboarding screen for a new Ganadero» y «until a real Ganadero is ready
     to hand over credentials». Las credenciales son del gestor, no del ganadero.
  6. Completar (no contradicen, pero falta): *Operational flow* paso 7 (líneas ~88-93), *Automation* (línea ~220) y
     *Still blocked* (línea ~1530) no dicen que la cuenta es la del gestor, que se descarga el justificante ni que el
     envío es de uno en uno.
  `DESIGN.md`: sin contradicciones. Describe los estados `EN_PROCESO`/`EJECUTADO_OVZ`/`ERROR_OVZ` como reservados para
  «Prompt 3c» y fuera del filtro «until they can happen», y ningún texto habla de un botón de envío. Como mucho,
  renombrar «Prompt 3c» a «escalón 2 (modo asistido)» al corregir `CLAUDE.md`.

### Diseño de Álvaro, fase 1 — CERRADA (2026-10-14): `5c157a9` (tokens, fuente Archivo y shell) y `558ea5c` (login, cola, modal, ficha OVZ y Explotaciones), con push

Plan aprobado: `docs/superpowers/plans/2026-10-12-diseno-alvaro-fase1.md` (prioridad 1, T1–T8, 4,0 días). Decisiones de
Antonio: nombre y «Cerrar sesión» abajo en la barra lateral en escritorio y arriba a la derecha en móvil; «Guardar»
secundario (solo «Aprobar» en rojo sólido); foco con el anillo actual, recto; «Abrir #N» solo si P1 cierra en 3,5 días.
Referencias de diseño y capturas fuera del repo (`C:\Users\Antonio\Desktop\ganera-diseno\`,
`C:\Users\Antonio\Desktop\capturas-ganera-diseno-fase1\`). Informes en `.superpowers/sdd/df-*`.
**Cierre (2026-10-14):** T1–T8 hechas, cada una con implementador + revisor independiente (todas Aprobado con menores;
informes `df-*`, sin commitear). Frontend 1058 en 47 ficheros, build y lint limpios; backend 968 (967 + 1 omitido), sin
cambios. Capturas para Álvaro en `C:\Users\Antonio\Desktop\capturas-ganera-diseno-fase1\final-*.png` (login, cola,
modal con el panel de WhatsApp abierto, ficha OVZ y banner; 375 y 1280 px). **Siguiente tarea: tarjetas-fila de la cola
en móvil (~1 día), con la columna de crotales a 1280 px incluida** (ver «Pendientes técnicos»), en una sesión nueva con
Paso 0.

Historial de la fase:
- **T1 hecha y revisada** (Aprobado con menores; `df-t1-report.md`, `df-t1-review.md`): `index.css` (Archivo `wdth.css`
  local, `font-stretch: 90%`, `--radius: 0px`, rampas gris/rojo, par neutro, `--divisor`, `--shadow-lg`), pesos
  700/500 en todo `src`, primitivos `components/ui` con la piel de v2, badge `outline` = par neutro. 976/976, build y
  lint limpios; fuente latin 34,9 → 90,1 KB. Menor 1 arreglado por la sesión principal (tarjetas de
  `GanaderoDetallePage` con `border border-border`). Menores abiertos: el banner de suscripción pierde su raya inferior
  (mirarlo en el smoke de T8) y las cabeceras de tabla en mayúsculas con lector de pantalla (T8).
- **T2 hecha y revisada** (2026-10-13; Aprobado con menores; `df-t2-report.md`, `df-t2-review.md`): barra lateral desde
  `lg` con el usuario abajo, barra superior por debajo con el usuario arriba a la derecha, mismo DOM y orden de
  tabulación, iconos lucide, `iniciales.ts` (NFC tras el menor 1), «Cerrar sesión». 987/987 en 46 ficheros, build y lint
  limpios; navegador real a 375/768/1023/1024/1280/1440 sin scroll lateral. Abierto: nombre en 700 (brief) o 500 (plan).
- **Commit intermedio T1+T2: `5c157a9`** (2026-10-13, aprobado por Antonio; nombre de la barra lateral en 500), con push.
- **T3 hecha y revisada** (Aprobado con menores; `df-t3-report.md`, `df-t3-review.md`), sin commitear: login con panel
  Rojo 600 desde `lg`, banda roja en móvil, h1 «Entrar en Ganera», campos de 44 px con icono. 990 en 46 ficheros.
  Menores como nota: el test de `aria-hidden` de los iconos no caza su retirada (lucide lo pone por defecto); dos
  símbolos juntos por debajo de `lg` (opción de diseño); `DESIGN.md` del login → T8.
- **T4 hecha y revisada** (Aprobado con menores; `df-t4-report.md`, `df-t4-review.md`), sin commitear: iconos por tipo y
  estado en `etiquetas.ts` (TDD), `IconoTramite.tsx`, badge de estado con icono (también en modal y ficha), piel de la
  tabla. 1035 en 46. Dos selectores de `TramitesPage.test.tsx` → `svg:not([data-icono])` (equivalente exacto).
- **T5 hecha y revisada** (Aprobado con menores; `df-t5-report.md`, `df-t5-review.md`), sin commitear: piel del modal,
  «Rechazar» `destructive`, pies blancos con raya de 2 px, bandas rectas con sus pares, `scroll-mb-3` + `scroll-pb-3`
  (cierra el pendiente B2 T3c del anillo de la alerta de WhatsApp). Nuevo `TramiteReviewDialog.piel.test.tsx` (9).
  Pregunta abierta para Antonio: «Enviar por WhatsApp» sigue en rojo sólido (de B2).
- **T6 hecha y revisada** (Aprobado con menores; `df-t6-report.md`, `df-t6-review.md`), sin commitear: piel de la ficha OVZ
  (cabecera con divisor 2 px, tarjetas rectas, cita sobre Fondo, pares neutros), sin tocar tests existentes; 1046 en 47.
- **T7 hecha y revisada** (Aprobado con menores; `df-t7-report.md`, `df-t7-review.md`), sin commitear: «creados/
  actualizados» en Animales y Contactos (frase anunciada y visible), cabecera «Animales» visible desde `sm` y a la
  derecha, contenedores rectos. 1052 en 47.
- **T8 hecha y revisada** (Aprobado con menores; `df-t8-report.md`, `df-t8-review.md`), sin commitear: `DESIGN.md` y el
  brief del shell al día; smoke Chromium + Firefox a 1440/1024/768/375 en 12 pantallas sin scroll lateral y con axe a 0;
  cabeceras en mayúsculas solo por CSS; Tab en el modal ≥ 16 px del pie. Popup del `Select` a `shadow-md`. Suites:
  frontend 1052 en 47, backend 968 (967 + 1 omitido). Build: CSS 60,9 KB, JS 669,8 KB, fuentes 90,1/86,2/34,5 KB.
- **Decisiones de Antonio (2026-10-14), aplicadas con TDD:** raya del banner opción A (`CLASE_BANNER` =
  `rounded-none border-x-0 border-t-0 border-b-2 border-b-divisor` en los cinco `<Alert>` de `SuscripcionBanner.tsx`,
  `it.each` en su test, verificado en Chromium y Firefox a 1440/375: 2 px sólido); «Enviar por WhatsApp» pasa a
  `outline` (test en `.piel`: con el panel abierto, el único `bg-primary` es «Aprobar»). `DESIGN.md` al día. Frontend
  **1058 en 47** (987 en `5c157a9` + 71), build y lint limpios. «Abrir #N» (P2) no entró. Tarjetas-fila en móvil:
  estimación ~1 día (render alternativo por debajo de `md`, TDD, revisión y smoke), tarea propia antes de la demo.
- **Incidente (2026-10-14) y lección:** `npx vitest list --json <ruta>` toma `<ruta>` como **fichero de salida**: al
  contar tests sobrescribió `LoginPage.test.tsx` (de T3, sin commitear) con el JSON. Recuperado desde el `git diff` que
  el revisor de T3 dejó en su transcripción, aplicado sobre `5c157a9`; diff idéntico byte a byte al revisado y 6/6 en
  verde. Ningún otro fichero tocado. Para contar tests por fichero: `npx vitest run <fichero>` y leer «Tests … (N)»;
  nunca `--json` con una ruta del repo. `--reporter=json` también escribe en `frontend/.vitest/` (borrado).
- **Pendientes:** T3 login, T4 lista, T5 modal (incl. `scroll-mb-3` y el popup de `CampoExplotacion` con
  `ring-foreground/10`), T6 ficha OVZ (sus `ring-foreground/10`), T7 «creados» + cabecera «Animales», T8 DESIGN.md +
  smoke + informe final. Línea base del build antes de T1: CSS 57,5 KB, JS 662,7 KB, fuentes wght 34,9/32,6/13,2 KB.

### Tarjetas-fila de la cola — CERRADA (2026-10-14)

Plan aprobado por Antonio con sus decisiones: «+N más» con el mismo nombre accesible en todos los anchos («+3 más: 3
crotales más», «+1 más: 1 crotal más»; «Ver menos» desplegado), «REGA · nombre» en la tarjeta (nombre recortado con «…»,
`title`), la tabla tal cual. **Corte de tarjetas en 1280 px (`xl`, `(min-width: 80rem)`), no en `lg`:** medido en
Chromium, a 1024 la tabla con 1 crotal mide 869 px en 718 de hueco (la barra lateral de 240 + `lg:p-8` quitan 304);
cortando línea en Crotales, 752; también en Tipo, 718 en 718 sin margen. Antonio eligió tarjetas hasta 1280. Resultado:
- < 1280: `ul aria-label="Trámites"` de tarjetas, 1 crotal + «+N más». Página 375/375, 768/768, 1024/1024; lista
  325/325 (375) y 718/718 (768 y 1024); nombre largo recortado; Tab #N → «+N más» → siguiente tarjeta; desplegar no abre
  el modal.
- 1280–1439: tabla con 1 crotal, 974 px en 974 de hueco (natural 869, 105 de margen, sin cortar línea).
- ≥ 1440 (`(min-width: 90rem)`): como antes, 2 crotales, 1134/1134.
Un solo marcado en el DOM cada vez (`shared/layout/useConsultaAncho.ts`; `useDesdeSm` pasa a usarlo); tabla y tarjetas
comparten `BotonRevisar`, `Tipo`, `CeldaCrotales` (con `maximo`) y las celdas de antes. En `TramitesPage.test.tsx` solo
cambian 6 selectores de nombre. Revisión independiente **Aprobado con menores** (el `aria-hidden` del «·» no tenía test
y la mutación pasaba; el esqueleto anunciaba 5 elementos vacíos; DESIGN.md), arreglados con TDD y la mutación
comprobada en rojo; 6 mutaciones más del revisor en rojo. Frontend **1075 en 49** (46 + 14 + 3 nuevos en la cola y el
hook), build y lint limpios; backend 968 (967 + 1 omitido), sin cambios. Capturas en
`C:\Users\Antonio\Desktop\capturas-ganera-tarjetas-fila\` (375/768/1024/1280/1440 y desplegado a 375/768/1024).
~~Siguiente tarea: M2~~ — **aplazada (decisión de Antonio, 2026-10-14):** M2 (descifrar la contraseña de OVZ solo bajo
demanda; ver «Credenciales de OVZ bajo demanda» en `ganera-prompts.md` y la ficha OVZ T2 M2 arriba) pasa a **justo antes
del modo asistido (escalón 2)**. Hoy no hay ninguna credencial de OVZ guardada (no hay pantalla ni importador que las
rellene), así que el `500` por cifra corrupta o `ENCRYPTION_KEY` cambiada no puede darse; y la sesión de mapeo de OVZ
decidirá si la cuenta es de la gestoría o de cada ganadero (las columnas `ovz_usuario`/`ovz_password_cifrada` pueden
moverse o desaparecer). Comprobado el mismo día: `ovzPasswordCifrada` no sale en ninguna respuesta (todas son DTO
explícitos, `Ganadero` no tiene `toString` de Lombok, el conversor no registra nada y sus excepciones no llevan el valor,
no hay log de SQL ni de parámetros; lo cubre `CredencialesOvzDetalleEndToEndTest`).
**Siguiente tarea: despliegue en Clever Cloud (demo y piloto)**: plan en
`docs/superpowers/plans/2026-10-14-despliegue-clever.md`, **aprobado el 2026-10-14** con las decisiones D1–D6 al principio
del plan (dos subdominios `app.`/`api.ganera.es`, PostgreSQL XXS Small en París, base de demo que se tira, copias de 7
días). **Código de T1–T5 hecho el 2026-10-14, sin commit (pendiente de aprobación de Antonio):** T1 sin
`UserDetailsService` por defecto; T2 HSTS en todas las respuestas solo con el perfil `clever` (sin
`forward-headers-strategy`, ver el plan); T3 CORS fail-closed (`OrigenFrontend`, `FRONTEND_ORIGEN` obligatoria y `https`
en `clever`); T4 web instalable (manifiesto e iconos, sin service worker; Chromium 151 y Edge 155 sin errores de
instalabilidad); T5 kit nuevo en `C:\Users\Antonio\Desktop\ganera-clever\`. TDD; mutaciones en rojo: T1 2, T2 4, T3 8
(restauradas con `cp` + `cmp`). Revisión independiente por tarea, las cinco **Aprobado con menores** (T5 con un
Importante: el smoke no se recuperaba de un descarte a medias), todo arreglado; informes en `.superpowers/sdd/dc-t*-review.md`.
Backend **1001** (1000 + 1 omitido), frontend **1084 en 50**, build y lint limpios (avisos conocidos). Ensayo del kit
contra PostgreSQL 16 embebido con el perfil `clever`: 39 PASS / 0 FAIL, repetido 38, y tras simular una demo 38 con
recuperación; log sin teléfonos ni textos. Siguiente: aprobación y commit, y el despliegue real (pasos de Antonio y su
socio en §8 del plan). Sin commit ni push sin aprobación de Antonio; la demo del 2026-10-15 va desde
`C:\Users\Antonio\Desktop\ganera-demo\app` (worktree fijado en `b4b786c`), que no se toca.

### Decisiones 19/10 (sobre el informe de impacto de arquitectura)

- **Orden:** commit de Clever (T1–T5) → Supabase → auditoría base → M2 → número de WhatsApp por gestoría → modelo
  ganadero/empleados → documentos → Mistral. Cada tarea en una sesión nueva con Paso 0.
- **Despliegue:** Supabase sustituye solo a la BD; backend en Clever (París), frontend en Vercel (`app.ganera.es`), API en
  `api.ganera.es`. HSTS, CORS, sin usuario por defecto y PWA se mantienen.
- **Supabase:** pooler en modo sesión (5432) para la app y Flyway, esquema `ganera`, `REVOKE` a `anon`/`authenticated`,
  `verify-full` con la CA en el classpath, Hikari a 5, RLS con políticas `TO ganera_app USING (true)` y un test que exija
  RLS en todas las tablas. Sin restricción por IP. Proyecto ya creado en eu-west-1 (Irlanda), plan Free hasta antes del
  ensayo; la Data API la desactiva Antonio; la versión de PostgreSQL la da Antonio en esa tarea.
- **Auditoría:** híbrida (servicio explícito + inmutabilidad en BD): roles propietario y `ganera_app`, trigger en
  `db/{vendor}`. Se auditan los 403 de acceso a otra gestoría, no los 409. Sin pantalla para la gestoría. IP: se reabre
  T2, la de más a la derecha de `X-Forwarded-For`. Antes/después: solo ids y códigos.
- **Número por gestoría:** una cuenta de Twilio con varios remitentes. Al cambiar de número, el antiguo se desactiva y lo
  que llegue a él queda como `NUMERO_DESTINO_DESCONOCIDO`.
- **Modelo:** opción B (el titular sigue siendo un `Contacto`, enlazado desde el ganadero). Un número puede ser titular de
  un ganadero y empleado en otra explotación; si es ambiguo, `PENDIENTE_REVISION` sin explotación. Migración: con varios
  titulares se queda el más antiguo y se registra el resto. Excel: `telefono_ganadero` en «Explotaciones» (vacía =
  conservar); una fila `TITULAR` en «Contactos» es error de fila con mensaje claro. Formularios de alta y edición de
  ganadero, explotaciones y empleados (camino de escritura nuevo, auditado); la regla de CLAUDE.md se actualiza en esa tarea.
- **Documentos:** escalón 1, subida manual opcional al marcar «Hecho en OVZ»; escalón 2, descarga automática. Supabase
  Storage, bucket privado, solo desde el backend; PDF/JPG/PNG de hasta 10 MB.
- **IA:** REST directo contra Mistral; modelos fechados por configuración; el modelo concreto se elige con la evaluación.

### Pendientes técnicos

- **Antes del primer dato real: crear una base nueva y empezar desde cero** (decisión de Antonio, 2026-10-14, D3 del
  plan de despliegue en Clever). ~~La base de PostgreSQL que se crea ahora en Clever es **para la demo** (dos gestorías
  inventadas). Pasos: add-on PostgreSQL nuevo (v16, XXS Small, París) → enlazarlo a `ganera-api` en lugar del de la
  demo → redesplegar (Flyway V1–V23) → `ALTER DATABASE … SET timezone TO 'UTC'` → alta de las gestorías reales con un
  `ONBOARDING_SECRET` temporal que se borra al acabar → borrar el add-on de la demo.~~
  **Ahora en Supabase (migración del 19/10, S7):** la base de la demo es la del proyecto de Supabase. Pasos: en el editor
  SQL, `SET ROLE ganera_propietario; DROP SCHEMA ganera CASCADE; RESET ROLE;` → el script
  `backend/supabase/01-preparar-roles-y-esquema.sql` otra vez (los roles y sus contraseñas se conservan; la zona UTC la
  pone el 01) → redesplegar `ganera-api` (Flyway V1–V23 y el callback) → el 02 a 0 filas → alta de las gestorías reales
  con un `ONBOARDING_SECRET` temporal que se borra al acabar. S7 se hace también justo después del smoke real (T5),
  antes de sembrar la demo en Clever.
- **Para el abogado, antes de los datos reales — retención de las copias de seguridad** (D4): hoy las copias diarias de
  PostgreSQL en Clever se guardan **7 días** (lo que incluye el plan; soporte de Clever puede ampliarlo). La retención
  se decide con el abogado antes del primer dato real, junto con los plazos de los mensajes (30 días / 12 meses).
- **Para el abogado — datos que salen de la UE** (D5, 2026-10-14). La base de datos, la app y sus copias están en Clever
  Cloud, zona París (UE, empresa francesa). Salen de la UE:
  - **Twilio** (EE. UU.): recibe y entrega cada mensaje de WhatsApp —texto, teléfono del ganadero o de su empleado y
    nombre del perfil— y las respuestas de la gestoría.
  - **Meta / WhatsApp** (EE. UU.): todo mensaje pasa por su red; es el canal.
  - **Anthropic** (EE. UU.): recibe el **texto** de cada mensaje (sin teléfono ni nombre, sin inventario) para extraer
    el tipo de trámite y los crotales.
  Hacen falta los DPA (con cláusulas contractuales tipo) de Twilio y Anthropic, el de Clever (se acepta en su consola) y
  revisar las condiciones de WhatsApp Business de Meta; mencionarlo en la información a las gestorías y los ganaderos.

- **HECHO (2026-10-14, ver «Tarjetas-fila de la cola — CERRADA»). Antes de la demo — tarjetas-fila de la cola en
  móvil** (fuera de la fase 1 del diseño por plazo, decisión de Antonio
  2026-10-12): a 375 px la tabla de `TramitesPage` mide **1.099 px** y se ven **325** (30 %): número y tipo visibles,
  estado cortado, explotación y crotales fuera sin pista de que hay más. Dejando cortar línea con CSS sigue en 680 px y
  los badges se parten en tres líneas. Se puede abrir un trámite pero no triar la cola. Propuesta: por debajo de `md`, un
  render alternativo en lista (`ul` de tarjetas-fila con #id + estado, tipo, REGA · nombre, crotales y avisos); en
  jsdom sin `matchMedia` se sigue pintando la tabla, así que los tests actuales no cambian. Capturas en
  `C:\Users\Antonio\Desktop\capturas-ganera-diseno-fase1\cola-375-*.png`. **Estimación: ~1 día** (render alternativo
  por debajo de `md`, TDD, revisión y smoke). **Es la siguiente tarea** (tras cerrar la fase 1, 2026-10-14).
  **Incluye la columna de crotales a 1280 px — regresión de la fase 1, comprobada el 2026-10-14** con un worktree de
  `aac93ee` fuera del repo y los mismos datos simulados (7 trámites de `cola375.js`), en Chromium: en `aac93ee` la tabla
  cabía a 1280 px (1230 px de tabla en 1230 visibles) y a 1440; en `558ea5c` a 1280 px la tabla mide **1045 px y solo
  hay 974** (la barra lateral de 240 px + `lg:p-8` quitan ~256 px), así que la columna de crotales se corta y se desplaza
  dentro del contenedor (sin scroll de página); a 1440 cabe (1134/1134). Afecta a todo el tramo `lg` hasta ~1350 px.
  Capturas `comparacion-cola-aac93ee-1280.png` y `comparacion-cola-main-1280.png` en esa carpeta. Opciones para decidir
  en esa sesión (sin implementar): que las celdas de crotales corten línea desde `lg`, limitar los crotales visibles a
  uno + «+N más» por debajo de `xl`, o usar las tarjetas-fila también hasta `xl`.
- **Antes de la demo — quitar el `UserDetailsService` por defecto** (visto en la prueba real de B2): al arrancar, Spring
  Boot imprime «Using generated security password: …» porque no hay ningún bean `UserDetailsService`/
  `AuthenticationProvider` y su `UserDetailsServiceAutoConfiguration` crea un `InMemoryUserDetailsManager` con un usuario
  `user` y una contraseña aleatoria. No se usa (la app es JWT sin `httpBasic` ni `formLogin`), pero deja una contraseña en
  el log y un usuario que no debería existir. **Propuesta (sin implementar):** excluir
  `org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration` en
  `@SpringBootApplication(exclude = …)` de la clase principal. **Test:** un `@SpringBootTest` con `OutputCaptureExtension`
  que compruebe que la salida del arranque no contiene «Using generated security password» y que el contexto no tiene
  ningún bean `UserDetailsService`; regresión: quitar el `exclude` → rojo. Revisar que los E2E de login y de rutas públicas
  siguen en verde.
- **Aviso de Flyway con H2 2.3.232 (solo local):** al arrancar contra H2, Flyway avisa de que esa versión de H2 es más
  nueva que las que tiene probadas. Solo afecta a los tests y al smoke local (H2 es de ámbito `test`); en Clever es
  PostgreSQL 16. Revisarlo si se sube Flyway o H2.
- **Resumen de importación con el género mal:** dice «Animales: 2 creadas» y debería ser «creados» (y «actualizados»).
  `ImportarExcelSection.tsx` usa las mismas palabras femeninas para las tres hojas, pero solo «Explotaciones» es
  femenino: «Animales» y «Contactos» son masculinos. Pasar el género por hoja (tarjeta y frase anunciada), con su test.
  **Entra en el diseño fase 1** (decisión de Antonio, 2026-10-12): texto «creados».
- **Diseño fase 1 — encontrar un descartado reciente** (smoke de SIN_TRAMITE, 2026-10-12; posible mejora de
  usabilidad, sin decidir): un descartado sale de la cola de pendientes (que abre filtrada por «Pendiente de
  revisión»), así que para «Deshacer» hay que buscarlo con el filtro de estado. Que un descartado reciente sea fácil de
  encontrar (p. ej. un acceso a «descartados hoy» o un aviso tras descartar).
- **Columna «Animales» de la lista de Explotaciones:** en la prueba pareció vacía. Según el código no muestra ningún
  recuento: es la columna del botón «Ver animales», sin título visible (`ID_CABECERA_ANIMALES` en
  `ExplotacionesPage.tsx`), y `ExplotacionResponse` no trae número de animales. **Entra en el diseño fase 1** (decisión de
  Antonio, 2026-10-12): cabecera visible o accesible para la columna del botón «Ver animales»; **sin recuento de
  animales**.

- **Anillo de foco de la alerta del panel de WhatsApp tapado por abajo** (T3c de B2 reducido): con
  `scrollIntoView({ block: "nearest" })` el borde de la alerta queda pegado al pie del modal y el `ring-3` no se ve
  por abajo. Posible `scroll-mb-3` en la alerta; va con la pasada de tokens del diseño (decisión de Antonio,
  2026-10-11).
- **Lector de pantalla no verificado:** que el aviso de error enfocable de la ficha y del modal (`role="alert"` con
  `tabIndex={-1}`, que recibe el foco tras un error) no se lea dos veces. Comprobarlo con NVDA (N8 de la revisión de
  la T2 de «Hecho en OVZ»).
- **Bloque C:** endurecer el aislamiento en BD con FK compuestas `(id, gestoria_id)` de forma global (hoy, por ejemplo,
  `tramite.hecho_en_ovz_por` no garantiza en BD que el usuario sea de la gestoría del trámite; solo lo garantiza el
  servicio).
- **Antes del piloto, después de B2 — contestaciones del ganadero crean trámites (B2 reducido, D12; tarea aparte
  aprobada por Antonio el 2026-10-11):** desde B1 cada mensaje de un contacto activo crea un trámite, así que un
  «vale, gracias» a una respuesta de la gestoría aparece en la cola. Tarea: separar en la IA `NO_IDENTIFICADO` (no
  está claro) de un `SIN_TRAMITE` nuevo (agradecimientos, confirmaciones, charla; hoy el prompt los mete en
  `NO_IDENTIFICADO`). Con `SIN_TRAMITE` y ningún crotal, el trámite **sigue en `PENDIENTE_REVISION`** (no se oculta
  nada por un error de la IA), con un aviso «Parece un mensaje sin trámite» y un «Descartar» de un clic que lo
  rechaza con su `version`. **Al descartar no se ofrece ni se envía ninguna respuesta al ganadero.**
  **En curso desde el 2026-10-12** con el plan `docs/superpowers/plans/2026-10-12-sin-tramite.md`.
- **Para el abogado — retención de los mensajes descartados** (SIN_TRAMITE, A4, decisión de Antonio 2026-10-12): hoy
  el texto de un mensaje descartado como «sin trámite» se guarda **12 meses**, como el de cualquier mensaje con trámite
  (`ganera.retencion.texto-con-tramite`). Pregunta: tratarlo como un mensaje sin trámite (**30 días**,
  como `ganera.retencion.mensajes-sin-tramite`, que hoy los borra). Sin cambios hasta su respuesta. El historial de descartes (`tramite_evento`) no
  guarda texto ni datos del ganadero.
- **Acuse de B1 también con «vale, gracias»** (visto en SIN_TRAMITE, decisión de Antonio 2026-10-12): cualquier
  mensaje de un contacto activo que crea trámite recibe «Recibido, tu gestoría lo revisará.», también un
  agradecimiento, porque la IA decide después de contestar a Twilio. **Se queda así en el piloto.** Idea para después:
  no repetir el acuse si a ese contacto ya se le mandó uno hace poco (ventana corta por contacto, con el `Clock`).
- **«Mañana vienen dos novillas de Pepe»: la IA duda entre `CONFIRMACION_MOVIMIENTO` y `SOLICITUD_MOVIMIENTO`**
  (evaluación de SIN_TRAMITE, 2026-10-12: una de tres pasadas salió `SOLICITUD_MOVIMIENTO`). No afecta a SIN_TRAMITE;
  **revisarlo con la sesión de mapeo de OVZ** (cómo describir en el prompt la llegada frente a la salida de animales).
- **Antes del piloto, tras la sesión de mapeo de OVZ:** la fecha del hecho y los campos propios del alta por
  nacimiento (crotal de la madre, sexo y fecha de nacimiento: extracción por la IA y edición en el modal), juntos.

### Migración a Supabase — código hecho, CA real (T0) y smoke real (T5) hechos, sin commit (2026-10-19 → 2026-10-21)

Plan `docs/superpowers/plans/2026-10-19-migracion-supabase.md` (decisiones de Antonio S1–S7 al principio). Rama
`supabase`, **sin commit**. Supabase sustituye solo a la base de datos (eu-west-1, Irlanda; PostgreSQL 17.11; plan Free;
Data API desactivada); el backend sigue en Clever (París) y el frontend irá a Vercel (tarea aparte). Informes y
revisiones en `.superpowers/sdd/su-t1-*` … `su-t4-*`.

- **T1 — PostgreSQL 17 en los tests y `db/{vendor}`: Aprobado con menores.** BOM de binarios 17.11.0; ubicaciones
  `classpath:db/migration,classpath:db/{vendor}` en los dos `application.yml`; carpetas `db/postgresql` y `db/h2` (con
  `LEEME.md`); `PostgresEmbebido.flyway`. Hallazgo: Flyway 10 solo avisa en DEBUG de una ubicación que no existe (de ahí
  `fail-on-missing-locations: true` en T2). 1005 tests.
- **T2 — roles, esquema, RLS y zona horaria: Cambios pedidos → corregido → Aprobado con menores.** Scripts
  `backend/supabase/01-preparar-roles-y-esquema.sql` y `02-comprobar-seguridad.sql`; callback
  `db/postgresql/afterMigrate__seguridad_esquema.sql` (RLS en todas las tablas, `ganera_app_todo`, lista «solo inserción»
  para la futura auditoría); `PostgresEmbebido` con `admin_supabase` (no superusuario) y el 01 por base;
  `docker-compose` con `postgres:17-alpine` y el 01 en `initdb`. Las correcciones: el 02 ve membresías y atributos de rol
  (I1), RLS solo si falta (bloqueos), idempotencia probada, privilegios por columna, el 01 corrige roles ya existentes y
  ADMIN sobre los roles de Ganera (N1). 1032 tests.
- **T3 — perfil `clever` contra Supabase: Cambios pedidos → corregido → Aprobado con menores.** `application-clever.yml`
  con las siete `SUPABASE_*`, `verify-full` + la CA en el classpath, `currentSchema=ganera`, Hikari 5, Flyway como
  `ganera_propietario`; certificados de prueba (S5); `ConexionBaseDeDatosEstricta` (una variable que falta **o está
  definida pero vacía** no deja arrancar; I1); el 02 marca un `SET` sobre `ganera_propietario` de un rol de Supabase (I2)
  y vigila a `authenticator` (M1). 1070 tests, 1 fallo esperado (`CaSupabaseTest`, sin la CA real).
- **T4 — documentación y kit (esta entrada).** `.env.example` (bloque «Perfil clever (Supabase)» y las dos de Twilio que
  faltaban), `CLAUDE.md` (bullet «Supabase», C mínimo y Clever al día, nota de `TestRestTemplate` y XML, cifras,
  estado), plan del 14/10 con las marcas «(migración a Supabase, 2026-10-19)», este `progress.md` y `ganera-prompts.md`.
  Kit `C:\Users\Antonio\Desktop\ganera-clever\`: `LEEME.md` (editor SQL de Supabase, `psql` 17 solo con las
  herramientas de línea de comandos y `\password`), `generador/generar.py` y `seed-tramites.sql` con `SET ROLE
  ganera_propietario; SET search_path TO ganera;` al principio, y `prueba-supabase.sh` nuevo (paso 5 de T5). Ensayo
  contra PostgreSQL 17 embebido imitando Supabase (SSL `verify-full`, SCRAM obligatorio para los dos roles, 01 como
  `admin_supabase`): `prueba-supabase.sh` 5/5 PASS (y re-ejecutable), alta, los dos Excel, siembra desde una sesión no
  UTC, `smoke.sh` 39 y 38 PASS, 02 con 0 filas. Suite: **1070** (1068 + 1 omitido + 1 fallo esperado de la CA); frontend
  **1084 en 50**, sin tocar.
- **Hallazgo del ensayo para T5, paso 6:** `SET ROLE ganera_app` desde el editor SQL (como `postgres`) **no funciona**
  («permission denied to set role»), y es lo correcto: nadie debe poder hacerse pasar por la app (el 02 lo marcaría). La
  comprobación de lo que ve la app se hace con `psql` conectado como `ganera_app.<ref>` (está en el `LEEME.md` del kit).
  `SET ROLE anon` → «permission denied for schema ganera».

**Pendientes de la migración:**
- **HECHO — T0 (Antonio, 2026-10-20):** CA en `backend/src/main/resources/supabase/ca-raiz.crt` (*Supabase Root 2021 CA*,
  Supabase Inc, `CA:TRUE`, válida hasta el 26/04/2031). `CaSupabaseTest` 2/2. **Suite completa con JDK 21 (Temurin
  21.0.12.1, zip oficial con SHA-256 comprobado, en `~/.jdks/temurin-21`, solo por comando): 1075, 0 fallos, 1
  omitido (la evaluación).** Frontend 1084 en 50, build y lint con los 3 avisos conocidos.
- **HECHO — T5, smoke real contra Supabase (Antonio, 2026-10-20 → 2026-10-21), todo PASS:**
  1. Script 01 en el editor SQL como `postgres`: «Success» (también el `ALTER DATABASE … SET timezone`).
  2. `psql` 17.11 (solo «Command Line Tools») como `postgres.<ref>` al **pooler en modo sesión** (5432) con
     `sslmode=verify-full` y la CA real: conecta, `\conninfo` con SSL. **El pooler acepta `rol.<ref>` y la CA del panel
     valida el certificado del pooler**: los dos riesgos que paraban el smoke, descartados.
  3. Backend **local** con el perfil `clever` y **JDK 21** contra Supabase: «Successfully applied 23 migrations to schema
     "ganera"», el callback `afterMigrate`, Hikari, Tomcat, `Started GaneraApplication`; 0 WARN de Flyway, 0 ERROR, sin
     «generated security password». Rearranque: «is up to date» y el callback otra vez.
  4. `prueba-supabase.sh` 5/5 PASS (alta, login, `inventario-norte.xlsx` con 15 explotaciones/150 animales/15
     contactos y 0 errores, `GET /explotaciones?q=castanos`, `GET /tramites`).
  5. Editor SQL: el **02 da 0 filas** (también la cláusula de cadenas de roles: ningún falso positivo con los roles
     reales de Supabase); `SHOW timezone` → `UTC`; `installed_by` = `ganera_propietario` × 23 (con `SET ROLE
     ganera_propietario`); `SET ROLE anon` → *permission denied for schema ganera*.
  6. Como `ganera_app` por el pooler: ve **15** explotaciones (RLS con `ganera_app_todo`) y **no** puede leer
     `flyway_schema_history`.
  7. **S7:** `SET ROLE ganera_propietario; DROP SCHEMA ganera CASCADE; RESET ROLE;` y el 01 otra vez → esquema vacío
     (0 tablas), roles conservados, 02 con 0 filas.
  **Lecciones del smoke (para el despliegue en Clever):**
  - **Pegar contraseñas a ciegas en un prompt de consola** (`\password`, la contraseña de `psql`, `Read-Host
    -AsSecureString`) dio valores que no coincidían con los del fichero: dos «password authentication failed» seguidos,
    uno por rol. Lo fiable fue **leerlas del portapapeles** (`Get-Clipboard`) y fijarlas con el **hash SCRAM**
    calculado en PowerShell (`Rfc2898DeriveBytes` SHA-256, 4096 iteraciones; comprobado contra una implementación de
    referencia en Python y contra Supabase) y pegado en el editor SQL: `ALTER ROLE … PASSWORD 'SCRAM-SHA-256$…'`. La
    contraseña en claro nunca llega a Supabase.
  - **Un aviso `Read-Host` normal muestra lo que se pegue en él:** dos contraseñas de los roles acabaron en el chat de
    la sesión. Se cambiaron las dos al momento (`rotar-contrasenas.ps1`); las expuestas ya no valen. Los avisos de los
    scripts del kit son ahora `-AsSecureString` (no muestran nada aunque se pegue).
  - Git Bash dio problemas para pegar; los pasos 3 y 4 quedaron en un script de **PowerShell 5.1**.
  - La contraseña de `postgres` no la sabía Antonio: restablecerla en el panel no afecta a nada de Ganera (la app y
    Flyway usan sus roles; la Data API está apagada).
  **Kit** (`C:\Users\Antonio\Desktop\ganera-clever\`, fuera del repo), dos scripts nuevos: `smoke-supabase-local.ps1`
  (pasos 3–4: comprueba las dos contraseñas contra el pooler, arranca la app con JDK 21 y `clever` en otra ventana,
  revisa el log, lanza `prueba-supabase.sh` y para la app) y `rotar-contrasenas.ps1` (genera contraseñas nuevas para
  los dos roles, las deja para el fichero, fija los hashes SCRAM por el editor SQL y comprueba los logins, las 15
  explotaciones como `ganera_app` y que no lee `flyway_schema_history`).
- **El callback no revoca a `authenticator` → va en la TAREA DE AUDITORÍA** (decisión de Antonio, 2026-10-20; menor
  de la segunda revisión de T3): por defecto no recibe nada en `ganera`, y el 02 detecta una concesión a mano, pero
  migrar no la deshace. Cerrar allí con un `REVOKE` condicional (`to_regrole('authenticator') IS NOT NULL`) y su caso
  hostil antes de migrar, como los de `anon`.
- **HECHO (2026-10-20, sesión principal, TDD): el 02 sigue las cadenas de roles** (N1 de la segunda revisión de T3).
  Cláusula nueva: un rol de Supabase llega a `ganera_app` o `ganera_propietario` si puede hacerse algún rol R (él
  mismo o por una cadena de SET, `pg_has_role(…, 'SET')`) que hereda el rol de Ganera (`'USAGE'`), puede hacer SET
  ROLE a él o tiene ADMIN sobre él, directo o heredado; sale como «llega por una cadena de roles». No repite lo que ya
  marcan las cláusulas directas (membresía directa con INHERIT, SET o ADMIN); una directa sin opciones no tapa la
  cadena. Test `SeguridadEsquemaPostgresTest.laComprobacionDetectaCadenasDeRolesHastaLosRolesDeGanera` (5 casos con un
  rol puente: SET, SET con `ganera_app`, solo INHERIT, ADMIN en el intermedio, directa sin opciones). Lo concedido a
  `anon`, `authenticated` o `service_role` le llega también a `authenticator` (en Supabase puede hacerse cualquiera de
  los tres), así que seis casos del parametrizado de membresías esperan ahora esa fila además de la suya. Primera
  versión revisada (`su-pghasrole-review.md`, **Aprobado con menores**): m1 (la mitad USAGE sin test), m2 (ADMIN en un
  rol intermedio no salía, explotable), m3 (una directa sin opciones tapaba la cadena) y m4 (`drop role if exists`
  antes de crear el puente), todos corregidos con TDD: rojo inicial 6, verde, y tres mutaciones (sin USAGE, sin ADMIN,
  tapar con cualquier directa) en rojo, restauradas con `cp` + `cmp`. **Para T5:** si en Supabase sale «authenticator
  llega por una cadena de roles», no es un falso positivo: es una capacidad real de Supabase (p. ej. si
  `authenticator` pudiera hacerse un superusuario); parar y mirarlo con las dos consultas de diagnóstico de
  `su-pghasrole-review.md`.
- **Decisiones de Antonio (2026-10-20):** T3 y T4 aprobadas; una variable definida pero vacía impide arrancar también
  en local (se queda así); en `.env.example` todas las variables sin valor van comentadas (12 líneas comentadas, con
  una nota de cuáles hacen falta en local).
- Notas: la validación contra la CA solo la vigila el texto de la URL (M2 de T3); el mensaje encadenado de Spring lleva
  el valor sin resolver de la propiedad (M4 de T3); `docker-compose` no probado (no hay Docker; su usuario `ganera` es
  superusuario y migra en el esquema `ganera`).

**Para el abogado (migración a Supabase):**
- **Supabase Inc. es una empresa estadounidense**, aunque los datos estén en Irlanda (AWS eu-west-1): hace falta su
  **DPA con cláusulas contractuales tipo**. Se suma a los de Twilio, Anthropic y Clever.
- **Copias de seguridad en el plan Free:** Antonio lo comprueba en el panel; probablemente no hay copias descargables
  (ni PITR) hasta el plan Pro. Afecta a la retención de copias (D4) que se decide antes de los datos reales.
- **Riesgo de demo:** en el plan Free el proyecto **se pausa tras 7 días sin actividad**.

### Auditoría base — en curso (2026-10-21, rama `feat/auditoria`, sin push)

Plan aprobado: `docs/superpowers/plans/2026-10-21-auditoria-base.md` (D1–D10 y las precisiones de Antonio al
principio). Parte de `main` = `32b6d3f` (backend 1075: 1074 + 1 omitido con JDK 21, 4 min 51 s; frontend 1084 en 50;
lint 3 avisos). Commit local por tarea cerrada, sin push. Álvaro en `feat/mistral` (sin migraciones); esta tarea
reserva V24 (tabla) y V25 (triggers, solo PostgreSQL). Informes en `.superpowers/sdd/au-*`.

- **Riesgo aceptado (D3, MVP sin limitar):** los accesos a un recurso de otra gestoría dejan una fila
  `ACCESO_OTRA_GESTORIA` cada uno, sin límite de frecuencia; un usuario autenticado podría llenar la tabla con intentos
  (ids de otras gestorías que existen). La fila no se puede borrar (trigger). Revisar con un límite por usuario y
  minuto si se ve en el piloto.
- **Para el abogado — plazo de conservación de `auditoria`** (A1): sin plazo. La tabla guarda la IP (dato personal) y
  el `usuario_id`; hoy no se puede borrar ninguna fila. El trigger de DELETE va por fila para que un proceso de
  retención futuro (solo `ganera_propietario`) pueda borrar las más antiguas que el plazo sin tocar nada más.
