# Frontend pendiente — estado tras la sesión autónoma (2026-10-30)

Este documento recoge, con honestidad, qué se construyó, qué quedó sin empezar, los bloqueos y las
decisiones de producto pendientes de confirmar con Antonio/Álvaro. Es el resumen que pide el prompt
de construcción del frontend.

## Contexto importante de la sesión

- **Rama de trabajo:** `feat/frontend-dashboard`, creada como **git worktree aislado desde `main`**
  en `~/Desktop/Ganera-work`. Se eligió `main` (línea desplegable y destino de los PR) y un worktree
  separado para **no tocar** el checkout principal, que estaba en `feat/stripe-enlace-alta` con
  trabajo sin commitear (páginas de Stripe éxito/cancelación). Ese trabajo de Stripe no se ha
  tocado.
- **Tiempo real disponible:** mucho menor del previsto. El arranque (baseline) consumió casi toda la
  ventana, así que se priorizó **entregar la Fase 1 completa, verde y verificada** antes que empezar
  muchas fases a medias.

## Diagnóstico del baseline (léelo: afecta a cómo se corren los tests)

La base de `main` está **sana**. Pero en esta máquina la suite de Vitest es **inestable cuando corre
en paralelo**:

- La corrida completa por defecto (`npm test`, forks en paralelo) tardó **~23 min** y dio
  11 fallos + 4 errores `Failed to start forks worker / Timeout waiting for worker to respond`.
- Reejecutados en aislamiento (`--no-file-parallelism`), casi todos pasan y el conjunto de fallos
  **cambia en cada corrida** (flakiness por saturación de CPU: workers que no arrancan a tiempo y
  asserts de *timing* de debounce).
- **Excepción, pre-existente en `main` y no causada por este trabajo:** el test
  `TramiteReviewDialog.test.tsx > avisos de extracción (…) > FALLIDA: aviso fijo en ámbar …` falla
  **de forma consistente en esta máquina, incluso aislado** — su `toHaveFocus()` espera que base-ui
  ponga el foco en el título del diálogo al abrir, y en este entorno (jsdom/headless) ese foco no se
  mueve de forma fiable. Ese fichero **no se ha tocado** en esta rama (el diff solo añade `dashboard`
  y edita `router.tsx`/`AppLayout.tsx`), así que el fallo es ajeno a la Fase 1. *Follow-up:*
  estabilizar ese assert de foco (o envolverlo en `waitFor`). La suite completa secuencial da
  **1106/1107** con ese único rojo.

**Recomendación:** verificar con `npx vitest run --no-file-parallelism` (fiable, ~1–2 min por grupo),
o limitar workers. No tomar un fallo de la corrida paralela como rotura real sin reejecutar aislado.
Sería un buen *follow-up* fijar `poolOptions`/`maxWorkers` o `fileParallelism: false` en
`vitest.config.ts`, o estabilizar los asserts de foco, para que CI no dé falsos rojos.

## Fase 1 — Dashboard / Inicio ✅ COMPLETA (en esta rama)

Pantalla de inicio en la ruta `/` (antes `/` redirigía a `/tramites`; ahora esa cola sigue en su
sección y el inicio la previsualiza).

Incluye:

- **Saludo por hora de Madrid** («Buenos días/tardes/noches, <nombre de pila>») y **fecha larga**
  («Viernes, 30 de octubre de 2026»). Lógica pura y testeada en `saludo.ts`.
- **4 tarjetas KPI**:
  - **Trámites pendientes** (dato real: `totalElements` de `GET /tramites?estado=PENDIENTE_REVISION`),
    clicable → `/tramites`.
  - **Ganaderos** (dato real: `totalElements` de `GET /ganaderos?size=1`), clicable → `/ganaderos`.
  - **Mensajes nuevos** y **Alertas activas**: **estado vacío honesto** («Próximamente»), sin número
    inventado, porque la bandeja de WhatsApp (Fase 5) y las alertas (Fase 4) **todavía no existen**
    como datos reales (regla de honestidad de `PRODUCT.md`).
- **Cola de trabajo**: previa de los trámites pendientes de revisión más recientes (hasta 5), con
  tipo + explotación + nº de crotales + badge de estado, cada fila enlaza a la cola, más
  «Ver todos los trámites». Con **estados de carga (skeleton), error (con Reintentar) y vacío**
  (mensaje honesto).
- **La marca «GANERA» de la barra** ahora enlaza al inicio (`/`), con nombre accesible «Ir al
  inicio». Se dejó **fuera del `<nav>`** para no alterar la tira de navegación ni sus tests.

### Decisión de diseño / limitación consciente de la Fase 1

- **No se añadió un enlace «Inicio» a la tira de navegación** (sí al logo). Motivo: la tira es un
  componente responsive muy afinado con tests basados en posiciones en píxeles; meter un 4.º enlace
  obliga a reescribir esa aritmética y arriesga romper el baseline. *Follow-up* de bajo riesgo:
  añadir «Inicio» como primer enlace del nav (icono tipo `LayoutDashboard`/`Home`) y actualizar
  `AppLayout.test.tsx`.
- **No hay endpoint `/resumen` dedicado.** El dashboard compone endpoints ya **auditados para
  multi-tenant** (`GET /tramites`, `GET /ganaderos`) con `size` pequeño → conteos reales y baratos y
  **cero riesgo de aislamiento**. *Follow-up recomendado:* un `GET /resumen` que agregue por
  `gestoriaId` en una sola llamada (contadores con `countBy...`, sin traer filas), que además
  permitiría: el desglose **«X de ayer»** del KPI de pendientes (hoy omitido por honestidad, no se
  puede sacar del `totalElements`), el nombre del **ganadero** en cada fila de la cola (hoy
  `GET /tramites` no lo trae, se muestra la explotación), y en su día los contadores de Mensajes y
  Alertas. Patrón backend a seguir: `ResumenController` con `@AuthenticationPrincipal
  GaneraUserPrincipal principal`, DTO `record`, `tramiteRepository.countByGestoriaIdAndEstadoIn(...)`
  y `ganaderoRepository.countByGestoriaId(...)`, más un test E2E de dos gestorías (aislamiento).

## Fases 2–8 — SIN EMPEZAR

Resumen de cada una con el bloqueo o la decisión pendiente más importante.

### Fase 2 — Alta/edición manual de ganadero ⚠️ CONFLICTO DE PRODUCTO
`PRODUCT.md` dice explícitamente que la creación manual de Ganadero/Explotación está **deliberadamente
NO construida**: «Excel es el único camino de creación» (y el onboarding de credenciales OVZ está
aplazado). El botón «Añadir ganadero» aparece en el Figma, pero construir alta manual **contradice una
decisión de producto vigente**. **Pendiente de confirmar con Antonio:** ¿se abre el alta manual o se
mantiene solo Excel/WhatsApp? Hasta confirmarlo no se implementa (evita abrir un camino de creación
que el producto cerró a propósito). Si se aprueba: `POST /ganaderos` con validación de NIF, UNIQUE por
gestoría, `gestoriaId` del JWT, DTOs y tests de validación + aislamiento.

### Fase 3 — Buscador global (Ctrl+K)
Sin empezar. Necesita endpoint de búsqueda que cruce ganaderos, explotaciones (REGA) y crotales,
filtrado por `gestoriaId`, con límite + debounce en el front. Ya existe lógica de búsqueda de
explotaciones (`ExplotacionBusquedaTest`, `buscarExplotacion.ts`) y de ganaderos
(`GanaderoBusquedaTest`) que se puede reutilizar como base.

### Fase 4 — Alertas y notificaciones
Sin empezar. **Decisión de producto pendiente:** definir qué es una «alerta» (p. ej. carencias/periodos
de retirada que vencen, trámites de ayer sin revisar, trámites atascados). Hasta definirlo, el KPI
«Alertas activas» del dashboard queda como «Próximamente» (honesto).

### Fase 5 — Bandeja / Chat de WhatsApp
Sin empezar. Hay dominio `whatsapp` y tablas de mensajes en el backend (`MensajeCampoRepository`,
`RespuestaWhatsappRepository`, retención de mensajes). La **vista de lectura** es viable con lo que hay
en BD; el **envío real** depende de Twilio (puede no estar configurado) → dejarlo tras un flag. Cuidar
retención y no exponer datos personales de más (ya hay `EnmascaradorTelefono`). El KPI «Mensajes
nuevos» del dashboard se conectará aquí.

### Fase 6 — Ajustes + subusuarios (empleados)
Sin empezar. CRUD de `usuario` dentro de la gestoría, control de rol y `gestoriaId`, contraseñas con
el `PasswordEncoder` (BCrypt) existente. **Decisión de producto pendiente:** modelo de roles
(`PRODUCT.md` marca roles y `modoCartera` como «sin decidir»).

### Fase 7 — Estadísticas / informes
Sin empezar. Agregaciones por `gestoriaId` (trámites por estado/tipo/mes, tiempos de revisión).

### Fase 8 — Calendario
Sin empezar (la última, como indica el prompt).

## Extras transversales
- Skeletons/errores/vacíos: aplicados en el dashboard; el resto de pantallas ya los tenían.
- `npm audit`: **no revisado en esta sesión** (el prompt avisa de vulnerabilidades heredadas en deps;
  no se tocó para no arriesgar el verde).
- No se añadió ninguna dependencia nueva.

## Cómo seguir
1. En el worktree `~/Desktop/Ganera-work` (rama `feat/frontend-dashboard`).
2. Verificar: `cd frontend && npx tsc -b && npm run lint && npx vitest run --no-file-parallelism`.
3. Abrir PR de la Fase 1 contra `main`. Luego decidir Fase 2 (conflicto de producto) o saltar a la
   Fase 3.
