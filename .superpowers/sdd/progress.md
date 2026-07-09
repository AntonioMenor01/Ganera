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
