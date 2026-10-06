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
