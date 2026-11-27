# Backlog de mejoras — análisis «cliente crítico» (2026-11-27)

Lista viva de lo que hay que **arreglar, añadir, consolidar o pulir** en Ganera, priorizada.
Sale de un análisis de la app actuando como la gestoría ganadera que la pagaría, cruzado con:
la **Auditoría OVZ** (`Auditoría OVZ — …​.pdf`), `PRODUCT.md` (persona y principios de honestidad) y
`docs/referencias/preguntas-gestoria-piloto.md`. Se probó la app en el navegador contra el backend
real (H2) con datos sembrados (4 ganaderos, 6 explotaciones, 12 trámites en todos los estados).

**Regla de oro al atacar cada punto:** respetar el trabajo hecho y **no romper nada**. Antes de tocar,
leer el código y los tests de la zona; dejar la suite verde (`npx vitest run --no-file-parallelism`),
`tsc`/`lint`/`build` limpios; y verificar en navegador cuando sea visual. Los cambios de backend los
despliega Antonio (Clever); los de frontend van a Vercel por `main`.

## Leyenda de prioridad
- **P0 — Rompe la confianza / barato de arreglar.** Botones que engañan y erratas que contradicen
  nuestro propio discurso de «cuidado en el detalle». Es lo primero.
- **P1 — Valor núcleo o hueco grande** para el cliente real.
- **P2 — Arquitectura de información / consolidación.**
- **P3 — Pulido y mejoras menores.**
- **BLOQUEADO — depende de algo externo** (p. ej. la sesión de mapeo de OVZ).

## Índice

| ID | Prioridad | Tipo | Resumen | Tamaño |
|----|-----------|------|---------|--------|
| MEJ-01 | P0 | Placeholder que engaña | Informes: el filtro de periodo no filtra | S–M |
| MEJ-02 | P0 | Errata | Calendario: «Noviembre De 2026» (capitaliza cada palabra) | S |
| MEJ-03 | P0 | Placeholder | Config → Equipo: «Invitar compañero» muerto | S |
| MEJ-04 | P0 | Placeholder | Config → Datos de la gestoría: campos «—» no editables | S–M |
| MEJ-05 | P0 | Incoherencia | Onboarding: «6/8» vs «paso 2 de 8» | S |
| MEJ-06 | P0 | UX intrusiva | Píldora «Continuar configuración» tapa contenido | S |
| MEJ-07 | P0 | A verificar | Informes: ¿PDF/Excel exportan de verdad? | S |
| MEJ-08 | P1 | Add (valor) | Multiusuario / equipo real (da sentido a la auditoría) | L |
| MEJ-09 | P1 | Add | Nombre del ganadero en la tarjeta de trámite | M |
| MEJ-10 | P1 | Add | Avisos proactivos por email (plazos / trámites atascados) | M–L |
| MEJ-11 | P1 | Add | Recuperar contraseña en el login | M |
| MEJ-12 | P1 | Add | Buscar también trámites en Ctrl+K | S–M |
| MEJ-13 | P2 | Consolidar / IA | Tres superficies de trámites (Inicio / Control / Bandeja) | M |
| MEJ-14 | P2 | Consolidar | Ocultar lo «en obras» hasta que aporte | S |
| MEJ-15 | P3 | Pulido | «Añadir credenciales OVZ» no hace nada visible aún | S |
| MEJ-16 | P3 | Pulido | Buscador: destino de explotación/crotal va a la ficha del ganadero | S |
| MEJ-17 | BLOQUEADO | Valor núcleo | Escalón 2: rellenar OVZ para que el gestor pulse «Enviar» | L |

---

## P0 — Arreglar ya (engañan o restan credibilidad)

### MEJ-01 · Informes: el filtro de periodo no filtra
- **Problema (voz cliente):** los segmentos «Este mes / Trimestre / Año / Personalizado» salen como
  control activo, pero el subtítulo confiesa *«Totales actuales (sin filtro por trimestre todavía)»* y
  los números no cambian al pulsar otro periodo. Un control que parece funcionar y no hace nada;
  mata la confianza.
- **Dónde:** `frontend/src/features/informes/InformesPage.tsx` (`PERIODOS`, `SUB_PERIODO`, estado
  `periodo` por defecto `"trimestre"`). El dato viene de `GET /tramites?estado=&size=1`
  (`features/informes/api.ts`) y no admite rango de fechas.
- **Propuesta:** dos caminos.
  - **Rápido/honesto (recomendado ya):** quitar los segmentos de periodo (o dejar solo «Totales») y
    el subtítulo «sin filtro… todavía», hasta que el backend agregue por fechas.
  - **Completo (más adelante):** endpoint `GET /resumen?desde=&hasta=` que cuente por rango
    (`countBy…` por `gestoriaId`, patrón ya descrito en `FRONTEND-PENDIENTE.md`), y cablear los
    segmentos. Esto además desbloquearía «X de ayer» y actividad por periodo.
- **No romper:** hay tests de `InformesPage` (KPIs, gráfico, export); ajustarlos al quitar/cablar
  segmentos. Export PDF/Excel: ver MEJ-07.

### MEJ-02 · Calendario: «Noviembre De 2026»
- **Problema:** el «De» en mayúscula. Es la errata que criticamos de OVZ en nuestra propia auditoría.
- **Causa:** `className="… capitalize"` aplicado a `etiquetaMes()` que devuelve «noviembre de 2026»;
  CSS `capitalize` pone mayúscula a **cada** palabra → «Noviembre De 2026».
- **Dónde:** `frontend/src/features/calendario/CalendarioPage.tsx:157` (título de mes) y `:303`
  (`gr.etiqueta` de la lista). `etiquetaMes()` y `etiquetaDiaLargo()` en el mismo fichero (~537, ~552).
- **Propuesta:** poner la mayúscula inicial en JS (solo la primera letra) y **quitar** la clase
  `capitalize`. Un helper `mayusculaInicial(s)` reutilizable. Revisar también `etiquetaDiaLargo`.
- **No romper:** tests del calendario que comprueben el título/etiquetas; actualizar al nuevo texto.

### MEJ-03 · Config → Equipo: «Invitar compañero» está muerto
- **Problema:** input gris, deshabilitado, con «Invitaciones y roles de empleados aún no tienen
  endpoint». Enseñar un botón que no hace nada.
- **Dónde:** `frontend/src/features/configuracion/` (sección Equipo).
- **Propuesta (corto plazo):** ocultar el input de invitar hasta que exista MEJ-08; dejar solo la
  lista de usuarios reales. (Cuando se haga MEJ-08, se reactiva.)
- **No romper:** tests de Configuración/Equipo.

### MEJ-04 · Config → Datos de la gestoría: campos «—» no editables
- **Problema:** Nombre/CIF/Dirección en «—», no editables («aún no se edita desde la app»). Ni
  siquiera muestra el **nombre de la gestoría que ya conocemos** (está en el onboarding).
- **Dónde:** `frontend/src/features/configuracion/` (sección Gestoría). Backend: hoy no hay
  `GET/PUT` de datos de gestoría más allá del nombre al onboarding.
- **Propuesta (corto plazo):** al menos **mostrar el nombre real** de la gestoría (lectura) en lugar
  de «—»; ocultar los campos que no se pueden rellenar. (Edición completa = tarea aparte, backend.)
- **No romper:** tests de Configuración.

### MEJ-05 · Onboarding: «6/8» y «paso 2 de 8» a la vez
- **Problema:** el Inicio dice «Primeros pasos · 6/8» (hechos/total) y la píldora «paso 2 de 8»
  (índice del primer paso incompleto). Es coherente internamente (pasos auto-detectados fuera de
  orden), pero al cliente le parece una contradicción.
- **Dónde:** `frontend/src/features/dashboard/DashboardPage.tsx` (`{onboarding.hechos}/{total}`) y
  `frontend/src/features/onboarding/PillContinuar.tsx` (`paso {indiceActivo + 1} de {total}`). Lógica
  en `PrimerosPasosContexto.tsx`.
- **Propuesta:** unificar el lenguaje: o ambos muestran «N/8 hechos», o la píldora dice «Continuar ·
  siguiente: <nombre del paso>» sin un número que choque con el conteo.
- **No romper:** tests de onboarding (`PrimerosPasosGuia.test.tsx`, `PillContinuar`).

### MEJ-06 · Píldora «Continuar configuración» intrusiva
- **Problema:** fija abajo a la derecha en **todas** las pantallas, tapando contenido; se siente como
  nag hasta terminar el onboarding.
- **Dónde:** `frontend/src/features/onboarding/PillContinuar.tsx` (montada en `AppLayout`).
- **Propuesta:** menos intrusiva — descartable por sesión, o mostrarla solo en el Inicio, o reducir
  su presencia. Decidir con Antonio el comportamiento exacto.
- **No romper:** su test y el de `AppLayout`.

### MEJ-07 · Informes: ¿PDF/Excel exportan de verdad? (verificar)
- **Problema:** hay botones «PDF» y «Excel»; no se verificó que hagan algo real. Si son placeholders,
  suben a P0 (otro botón que engaña).
- **Dónde:** `frontend/src/features/informes/InformesPage.tsx`.
- **Acción:** probar; si no exportan, ocultarlos o implementarlos (export cliente del gráfico/tabla).

---

## P1 — Valor y huecos grandes

### MEJ-08 · Multiusuario / equipo real
- **Problema:** una gestoría son varios administrativos + el gestor; hoy es **un único login
  compartido**. Y el backend tiene una auditoría potente de «quién hizo qué» que queda **inútil** si
  todos entran como el mismo usuario.
- **Dónde:** backend `usuario` (CRUD dentro de la gestoría, `PasswordEncoder` ya existe); frontend
  Configuración → Equipo (MEJ-03).
- **Decisión de producto pendiente:** modelo de roles (`PRODUCT.md` marca roles y `modoCartera` como
  «sin decidir»). Definir: ¿todos pueden aprobar o solo el gestor? ¿invitación por email?
- **No romper:** aislamiento multi-tenant (`gestoriaId` del JWT), tests E2E de dos gestorías.

### MEJ-09 · Nombre del ganadero en la tarjeta de trámite
- **Problema:** en Inicio/Control las tarjetas lideran con el REGA (14 dígitos) y la finca, pero **no
  el ganadero**. El gestor piensa en clientes, no en códigos. El dato existe (la ficha muestra «2
  trámites pendientes» por explotación), falta traerlo a la lista.
- **Dónde:** backend `GET /tramites` **no** trae `ganaderoNombre` hoy (confirmado en
  `FRONTEND-PENDIENTE.md`); el detalle sí. Hay que añadirlo a la respuesta de lista (sin N+1,
  `@EntityGraph`/join). Frontend: `features/tramites/TarjetaTramite.tsx` y el tipo `Tramite`.
- **No romper:** contratos/tests de `GET /tramites` y del tablero; mantener el orden de la tarjeta.

### MEJ-10 · Avisos proactivos por email
- **Problema:** la campana de novedades (plazos / trámites atascados) es la idea correcta — ataca el
  fallo de OVZ (olvidar un paso de dos tiempos) — pero es **solo in-app**. El ganadero no entra nunca
  y el gestor a veces trabaja desde el móvil.
- **Dónde:** backend ya tiene `EmailService` (ver CLAUDE.md, apagado por defecto). Falta definir
  **disparadores** (trámites pendientes de ayer, atascados, plazos legales que vencen) + un
  `@Scheduled` que envíe el resumen, con preferencias por gestoría.
- **Decisión de producto:** qué se considera «alerta» y con qué frecuencia (resumen diario, etc.).
- **No romper:** inventario de auditoría (toda escritura/planificador nuevo se declara), fail-closed
  como el resto de planificadores.

### MEJ-11 · Recuperar contraseña en el login
- **Problema:** no hay «¿Olvidaste tu contraseña?». Un administrativo que la olvide se queda fuera.
- **Dónde:** `frontend/src/features/auth/LoginPage` + backend (token de reseteo + email) +
  `EmailService`.
- **Decisión de producto:** `PRODUCT.md` lo marca «fuera de alcance por decisión» — reconfirmar con
  Antonio ahora que hay varios usuarios (MEJ-08) y `EmailService`.

### MEJ-12 · Buscar también trámites en Ctrl+K
- **Problema:** el buscador global (bueno, sin tildes) cubre ganaderos/explotaciones/crotales, **no
  trámites** (por nº o tipo).
- **Dónde:** `frontend/src/features/search/` (`BuscadorGlobal`, `useBuscadorGlobal`) + endpoint de
  búsqueda backend.
- **No romper:** aislamiento por `gestoriaId`, debounce/abort ya existentes.

---

## P2 — Arquitectura de información

### MEJ-13 · Tres superficies de trámites (Inicio / Control / Bandeja)
- **Problema:** Inicio (tablero del día), Control (tablero completo) y Bandeja (bandeja WhatsApp)
  enseñan trámites. Como cliente, «¿dónde vivo yo?». Bandeja además **lista trámites**, pisando a
  Control.
- **Propuesta a debatir:** que **Bandeja = hilos de mensajes de WhatsApp** (la conversación con el
  ganadero) y **Control = trámites**, sin solape; Inicio como resumen del día. Requiere decisión de
  producto antes de tocar.
- **No romper:** es refactor de IA; hacerlo con su propio plan y tests.

### MEJ-14 · Ocultar lo «en obras»
- **Problema:** secciones medio vacías (Calendario con poco contenido real, placeholders de
  Configuración) dan sensación de producto a medias.
- **Propuesta:** criterio general — **no enseñar lo que no funciona**. Revisar Calendario (¿plotea
  plazos legales de verdad o solo hechos?) y los placeholders. Decidir caso a caso.

---

## P3 — Pulido

### MEJ-15 · «Añadir credenciales OVZ» no hace nada visible
- La ficha del ganadero deja guardar credenciales OVZ, pero el escalón 2 no está vivo, así que no
  «hacen» nada todavía. Aclarar para qué sirven (preparación) o aplazar el botón. Ligado a MEJ-17.

### MEJ-16 · Buscador: destino de explotación/crotal
- Los resultados de explotación y crotal llevan a la **ficha del ganadero**, no a la explotación/
  animal concreto. Mejorar el destino cuando existan esas vistas.

---

## BLOQUEADO

### MEJ-17 · Escalón 2 — rellenar OVZ (EL valor)
- **Problema:** hoy «Aprobar» solo cambia el estado; el gestor **sigue tecleando en OVZ a mano** y
  marca «Hecho en OVZ». La promesa «deja de teclear en OVZ» aún no se cumple.
- **Bloqueo:** necesita la **sesión de mapeo de OVZ con credenciales reales** (ver CLAUDE.md y
  `docs/referencias/guion-sesion-mapeo-ovz.md`). `OvzAutomationService` sigue siendo un esqueleto.
  No escribir scraping contra suposiciones de OVZ.
- **Nota:** la presentación automática de la **baja por muerte** ya está implementada (detrás de
  flags, en ENSAYO por defecto); el resto de tipos es modo asistido pendiente del mapeo.

---

> **Cómo seguir:** atacar por orden de prioridad. Los P0 son baratos y de alto impacto en la primera
> impresión — buen primer bloque. Marcar cada item como hecho aquí (o moverlo) al cerrarlo.
