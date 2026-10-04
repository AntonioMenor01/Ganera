# Quitar el pago de la app — plan

Fecha: 2026-10-04. Base: `main` = `d65e446`. **Backend y frontend en el mismo commit** (excepción
explícita de Antonio: cambia un contrato, `POST /facturacion/checkout` desaparece, y `main` no puede
quedar con la app llamando a algo que ya no existe). Suites de partida: backend 533, frontend 506.

Motivo: el cobro pasa a la landing de Ganera con Stripe. La app deja de iniciar pagos, pero el
servidor sigue necesitando saber quién ha pagado (webhooks, estados, bloqueo de aprobar).

Flujo: Superpowers (implementador + revisor independiente por tarea), TDD. Cierre:
`./mvnw clean test`, `npm test`, `npm run build`, `npm run lint` y smoke en navegador con dos
Gestorías (una `SUSPENDIDA`). Sin commits sin aprobación de Antonio; stage ruta a ruta.

## Decisiones cerradas con Antonio (2026-10-04)

Visto bueno a la clasificación y a D1–D5: textos como en D2, banners **sin enlace ni `mailto:`**;
comodín `*` hacia `/tramites` con su test (D5); `features/facturacion` conserva su nombre; E2E de
`POST /facturacion/checkout` (404 con JWT, 401 sin él, sin crear `Suscripcion`). Los dos bugs de
Stripe y `SuscripcionSyncScheduler` quedan fuera, anotados en las notas del Prompt C de
`ganera-prompts.md`.

## Inventario

### Backend (`facturacion`, `registro` y referencias)

| Elemento | Decisión | Por qué |
|---|---|---|
| `POST /facturacion/checkout` (`FacturacionController.crearCheckout`) | **Se quita** | Es el pago iniciado desde la app. |
| `CheckoutResponse` | **Se quita** | Solo lo usa ese endpoint. |
| `StripeCheckoutService.crearSesionCheckout(gestoriaId)` (cantidad = nº real de explotaciones) | **Se quita** | Solo lo llama el endpoint que se quita. Con él sale la dependencia de `ExplotacionRepository` si queda sin uso. |
| `StripeCheckoutService.crearSesionCheckoutConCantidadEstimada` + helper privado + `configuracionCompleta()` | **Se queda** | Lo usa `POST /gestorias/registro`, y el Registro no se toca hasta el Prompt C (D1). |
| `GET /facturacion/suscripcion` + `SuscripcionEstadoResponse` | **Se queda** (ruta y forma iguales) | Lo lee el `SuscripcionBanner` en cada carga del layout (D3). |
| `StripeWebhookController` / `StripeWebhookService` (`/webhooks/stripe`, firma, máquina de estados) | **Se queda** | Así sabe el servidor quién ha pagado; la landing cobrará con el mismo Stripe. |
| `Suscripcion`, `EstadoSuscripcion` (6 estados), `SuscripcionRepository`, V10/V13 | **Se queda** | Modelo de datos de quién ha pagado. |
| `SuscripcionService.puedeAprobarTramites` | **Se queda** | Es el bloqueo de aprobar (403). |
| `SuscripcionService.obtenerOCrearSuscripcion` | **Se queda** | Lo sigue usando el checkout del Registro. |
| `SuscripcionSyncScheduler` (cantidad nocturna a Stripe) | **Se queda** | Es del servidor, no de la app. El cobro por planes de ganaderos (25/75/200) lo cambiará en el Prompt C; anotado. |
| `StripeConfig`, `stripe-java` en `pom.xml`, `STRIPE_*` en `application.yml` y `.env.example` | **Se queda** | Los necesitan los webhooks y el Registro. |
| `OnboardingController` (crea `Suscripcion` en `ACTIVA`) | **Se queda** | Alta manual del piloto, sin pago. |
| `TramiteController.MOTIVO_SUSCRIPCION_NO_PERMITE_APROBAR` | **Se queda, texto cambiado** | Hoy dice "Actualiza tu suscripción en Facturación" (D2). |
| `TramiteRevisionService` / `TramiteController` (gate del 403) | **Se queda** | Regla no negociable. |
| Javadoc de `RegistroGestoriaResponse` ("misma forma que CheckoutResponse") y comentarios de `TenantIsolationEndToEndTest`/`RegistroGestoriaEndToEndTest` que citan el checkout | **Se queda, comentario corregido** | Nombran algo que ya no existe. |
| Tests: casos de checkout en `FacturacionControllerTest` y `StripeCheckoutServiceTest` | **Se quitan** | Prueban lo que se quita. Se conservan los de `GET /facturacion/suscripcion` y los de la variante estimada. |
| Test nuevo: `POST /facturacion/checkout` con JWT → `404`, sin JWT → `401`, y que no crea ninguna `Suscripcion` | **Se añade** | Fija que el endpoint ha desaparecido de verdad (E2E con `@SpringBootTest`). |
| Bugs conocidos de Stripe (pago fallido sobre `TRIAL_EXPIRADO_SIN_PAGO`; prueba abandonada que nunca expira) | **Fuera de alcance** | Siguen pendientes; el segundo solo puede venir ya del Registro. |

### Frontend

| Elemento | Decisión | Por qué |
|---|---|---|
| `features/facturacion/FacturacionPage.tsx` + `.test.tsx` | **Se quita** | Es la página de Facturación con el pago. |
| Ruta `facturacion` en `router.tsx` | **Se quita** | Página eliminada. Ver D5 para quien llegue a `/facturacion`. |
| Enlace "Facturación" de la navbar (`AppLayout.tsx`) | **Se quita** | La navbar queda en Trámites, Ganaderos, Explotaciones. |
| `AppLayoutContext` / `<Outlet context={{ suscripcion }}>` | **Se quita** si nadie más lo lee | Existía para que `FacturacionPage` reutilizara el fetch; el banner lo recibe por props. |
| `crearSesionCheckout` en `facturacion/api.ts` | **Se quita** | Llama al endpoint que se quita. |
| `obtenerEstadoSuscripcion`, `useSuscripcionEstado`, `types.ts` | **Se queda** | Alimentan el banner. `explotacionesContratadas` sale del tipo si ya nadie lo lee (el backend lo sigue enviando). |
| `SuscripcionBanner` | **Se queda, textos cambiados** | Sin botón "Ir a facturación"; dice que se contacte con Ganera (D2). |
| Enlace "Ir a Facturación" del 403 en `AvisosRevision.tsx` | **Se quita** | Página eliminada; queda el `motivo` del backend. |
| `errores.ts`: contexto `checkout` | **Se quita** | Ya no hay checkout en la app. |
| `errores.ts`: texto de reserva del 403 de aprobar | **Se queda, texto cambiado** | Debe ser idéntico al nuevo motivo del backend (D2). |
| `errores.ts`: `registro` con su 503 "facturación no configurada" | **Se queda** | El Registro no se toca. |
| `RegistroPage` y `features/auth/api.ts` (redirección a Stripe) | **Se queda** | Fuera de alcance hasta el C; solo se corrige el comentario que cita `crearSesionCheckout`. |
| Tests de `httpClient.test.ts` que usan `/facturacion/checkout` como ruta de ejemplo | **Se cambian de ruta** | Prueban el interceptor (503, error genérico), no el checkout. |
| Tests de `AppLayout.test.tsx` que usan "Facturación" como último enlace (geometría de la tira a 375 px) | **Se reescriben** con Explotaciones como último enlace | Prueban el scroll de la tira, que se mantiene. |
| Mocks de `/facturacion/suscripcion` en otros tests | **Se quedan** | El endpoint sigue. |
| `TramiteReviewDialog.test`, `useRevisionTramite.test`, `errores.test` (texto y enlace del 403) | **Se cambian** | Texto nuevo y sin enlace. |
| `DESIGN.md` (navbar con Facturación, estados de suscripción en Facturación) | **Se actualiza** | Describe pantallas que cambian. |

## Decisiones (con recomendación)

- **D1. Registro.** Se queda tal cual, con su redirección a Stripe Checkout, como pediste. Por eso
  el servicio de checkout no desaparece entero: solo la variante que usaba la app. El Prompt C
  sustituirá el Registro por el alta desde la landing.
- **D2. Textos.** Recomiendo un texto común y sin datos de contacto concretos (no hay todavía un
  email o teléfono de soporte acordado):
  - Motivo del 403 (backend) y reserva del frontend, idénticos: "Tu suscripción no permite aprobar
    trámites ahora mismo (prueba terminada o suscripción suspendida). Ponte en contacto con Ganera
    para regularizarla."
  - Banner `TRIAL_EXPIRADO_SIN_PAGO` / `SUSPENDIDA`: el mensaje actual terminando en "Ponte en
    contacto con Ganera para regularizarla."
  - Banner sin `Suscripcion`: "Todavía no tienes una suscripción activa. Ponte en contacto con Ganera
    para activarla." (hoy invita a empezar la prueba desde Facturación).
  - Banner `IMPAGO_GRACIA`: el mensaje actual terminando en "Ponte en contacto con Ganera para
    regularizarlo."; el título "Aviso de facturación" pasa a "Aviso de pago".
  - Si tenéis un email de contacto, se puede poner como `mailto:` en el banner (una constante). Si
    no, va sin enlace y se añade en el C.
- **D3. `GET /facturacion/suscripcion`.** Se queda con la misma ruta y la misma respuesta (incluido
  `explotacionesContratadas`). Renombrarla a `/suscripcion` no aporta nada ahora y cambiaría otro
  contrato.
- **D4. Carpeta `features/facturacion`.** Se queda con ese nombre, aunque ya solo tenga el banner y
  el estado. Renombrarla a `features/suscripcion` es más limpio pero mueve ficheros sin cambiar
  nada; lo dejaría para cuando se toque otra vez.
- **D5. Quien entre en `/facturacion`** (marcador guardado, historial). Hoy no hay ruta comodín y
  React Router enseñaría su página de error en bruto. Recomiendo un comodín `*` dentro del layout
  autenticado que redirija a `/tramites` (con `replace`); cubre `/facturacion` y cualquier ruta
  mal escrita. Alternativa mínima: redirigir solo `/facturacion`.

## Tareas

**T1 — Backend.** Tests primero: E2E de `POST /facturacion/checkout` → `404` con JWT y `401` sin él,
sin crear `Suscripcion`; `GET /facturacion/suscripcion` sigue igual (los tests actuales, incluido
su caso de dos Gestorías); el motivo nuevo del 403 en `TramiteControllerTest` y
`TramiteRevisionEndToEndTest`. Quitar el endpoint, `CheckoutResponse` y `crearSesionCheckout`;
corregir los comentarios.

**T2 — Frontend.** Tests primero: la navbar sin Facturación (tres enlaces, la geometría de la tira
con Explotaciones como último); `/facturacion` redirige a `/tramites` (D5); banner con los textos
nuevos y sin enlaces a `/facturacion`; 403 en el modal con el `motivo` y sin enlace; ningún
`/facturacion/checkout` en el código (`grep`). Quitar la página, la ruta, el enlace, el contexto
del `Outlet` si queda sin uso, `crearSesionCheckout` y el contexto `checkout` de `errores.ts`.
Actualizar `DESIGN.md`.

**T3 — Cierre.** `./mvnw clean test`, `npm test`, `npm run build`, `npm run lint`. Smoke en
navegador real (Playwright fuera del repo, Vite + backend en H2 en fichero) con dos Gestorías, A
`ACTIVA` y B `SUSPENDIDA` (por SQL), a 1440 y 375 px:
- navbar sin Facturación;
- `/facturacion` lleva a Trámites;
- B ve el banner con el texto de contacto y sin botón;
- B aprueba y recibe el `403` con el motivo nuevo, sin enlace y sin cerrar sesión;
- A aprueba sin problema y no ve el banner;
- `POST /facturacion/checkout` con `curl` da `404`.

Actualizar `CLAUDE.md`, `ganera-prompts.md` (orden nuevo: esto → colores y tipografía → B con el
alta por nacimiento) y `.superpowers/sdd/progress.md`.
