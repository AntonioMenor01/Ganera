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
  `REQUIRES_NEW` sobre el filtro de tenant; conteo de tests a 93.

**PROMPT 3d CERRADO.**
