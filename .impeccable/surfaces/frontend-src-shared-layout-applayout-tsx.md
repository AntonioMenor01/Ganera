---
version: 1
slug: "frontend-src-shared-layout-applayout-tsx"
primary_target: "frontend/src/shared/layout/AppLayout.tsx"
related_targets: []
---

# Barra de navegación (AppLayout)

Scope: el `<header>` de `AppLayout` en todas las pantallas autenticadas: **barra lateral fija desde
`lg`** y barra superior por debajo (desde el 2026-10-13; ver la sección fechada al final, que manda
sobre lo anterior donde choquen). Mode: Operate.
Refinamiento: mismo mundo visual (DESIGN.md); la historia de abajo explica la barra superior
(2026-10-03/04), que sigue valiendo por debajo de `lg` salvo la piel.

Audience and job: el gestor/titular que aprueba desde el móvil fuera de la oficina, y los
administrativos en escritorio. Tienen que llegar a Trámites (y a cualquier sección) con un toque y
la página nunca se desplaza en horizontal.

Decided with Antonio (2026-10-03/04, plan `docs/superpowers/plans/2026-10-03-tarea-frontend-antes-piloto.md`):
- Por debajo de `md` (768 px), dos filas en una sola barra Superficie (sin línea entre filas, borde
  Gris Borde abajo como hoy): fila 1 marca + "GANERA" a la izquierda, "Salir" (`outline sm`) a la
  derecha, sin email; fila 2 los 4 enlaces en orden de trabajo.
- La tira de enlaces va a sangre hasta los bordes de la pantalla (margen interior de 16 px), con
  scroll horizontal solo interno (scrollbar oculta, `overscroll-x-contain`). La pista de "hay más"
  es el último enlace cortado en el borde: **sin degradado, sin flechas**.
- El enlace activo se trae a la vista ajustando el `scrollLeft` de la tira (al cargar y al navegar),
  **nunca con `scrollIntoView`**: la página no se mueve en vertical.
- Objetivo táctil de unos 36 px en móvil; el pill no cambia de forma ni color.
- Desde `md`, la barra actual en una fila; el email se trunca con puntos suspensivos y lleva el
  email completo en `title`, con un ancho máximo que garantiza que no desborda a 768 px con un
  email largo.
- Un solo `<nav aria-label="Principal">` en el DOM (recolocado por CSS), mismo orden de tabulación
  en móvil y escritorio; foco visible en los enlaces con el anillo estándar (3 px Rojo Enlace al 100 %);
  `aria-current="page"` en el activo.

## Direction contract

THESIS: la barra de una herramienta de trabajo que cabe en la mano: todas las secciones a la vista
y a un toque, sin menú que abrir. Rechaza la hamburguesa, la barra inferior y los iconos decorativos.

OWN-WORLD (marca nueva, 2026-10-05): Superficie, borde Gris Borde, pill Rojo Acción con texto blanco
para el activo, Gris Texto para los inactivos con Gris Borde al pasar; símbolo en Rojo Ganera y
"GANERA" en Tinta 800; Archivo 14/600; transiciones solo de color.

ANTI-GOALS: degradados o sombras para indicar overflow; sticky; cambios de paleta, tipografía,
textos u orden; duplicar el menú en el DOM; cualquier desplazamiento horizontal de la página.

VERIFY: navegador real a 375, 640, 768 (email largo) y 1440 px: `scrollWidth === clientWidth` del
documento en todos; a 375 px, en Facturación el activo queda visible; `window.scrollY` no cambia al
navegar entre secciones a 375 px; Tab recorre los enlaces con foco visible.

## 2026-10-13 — Diseño de Álvaro, fase 1: barra lateral desde `lg`

Plan `docs/superpowers/plans/2026-10-12-diseno-alvaro-fase1.md` (T2), referencia visual «Ganera App v2»
(fuera del repo). Solo piel y topología por punto de corte: **mismo `<header>`, misma `nav
aria-label="Principal"`, mismo orden del DOM y de tabulación** (marca → enlaces → usuario), sin DOM
duplicado ni `display: contents`. `tiraNavegacion`, el `useLayoutEffect` con `ResizeObserver` y el
`onFocus` de la tira no cambian.

Decidido con Antonio:
- **Desde `lg` (1024 px), barra lateral** fija de 240 px (`lg:fixed lg:inset-y-0 lg:left-0 lg:w-60`), en
  Superficie con raya derecha de 2 px `border-divisor`; el contenido empieza en 240 px (`lg:pl-60`).
  - Arriba, zona de marca de 64 px con raya inferior de 2 px: símbolo 22 px en Rojo Ganera y «GANERA»
    16 px 700.
  - Enlaces en columna, filas de 40 px a todo el ancho, con icono lucide decorativo (`clipboard-list`,
    `users`, `warehouse`, 18 px); el activo en bloque Rojo 600 con texto blanco, los demás en tinta con
    hover Gris 300. La lista tiene `p-3` (deja sitio al anillo) y scroll vertical propio si hiciera falta.
  - **Abajo del todo**, sobre una raya de 2 px, la tarjeta de usuario (blanca, borde Gris 300): iniciales
    en cuadro de 32 px Rojo 100/800 (`aria-hidden`, `iniciales.ts`), nombre 13 px **500** (decisión de
    Antonio: texto de interfaz, no 700), email 12 px Gris 700, ambos truncados con el email en `title`, y
    «Cerrar sesión» (`outline sm`) a todo el ancho. El usuario va abajo y no arriba a la derecha como en
    v2: así no se duplica el DOM ni cambia el orden de tabulación.
- **Por debajo de `lg`**, la barra superior de siempre con la piel de v2: Superficie con raya inferior de
  2 px `border-divisor`; enlaces rectos con icono de 16 px, 14 px/500 en tinta, activo Rojo 600; a la
  derecha las iniciales, el nombre (`max-w-24`, 160 px desde `md`) y «Cerrar sesión»; el email solo desde
  `md`. Por debajo de `md`, dos filas (marca + usuario arriba con `gap-x-3`; la tira de enlaces a sangre
  debajo, sin cambios de comportamiento).
- «Salir» pasa a «Cerrar sesión» (único texto cambiado).
- Fuera: búsqueda Ctrl K, campana, contadores, plegar el menú y el bloque de la gestoría de v2.

### Direction contract (2026-10-13; sustituye a OWN-WORLD y amplía VERIFY)

OWN-WORLD: Superficie con rayas de 2 px en tinta al 40 %; todo recto (radio 0); enlaces 14/500 en tinta con
icono, activo en bloque Rojo Acción con texto blanco, hover Gris Borde; símbolo en Rojo Ganera y «GANERA» en
Tinta 700; Archivo al 90 % de ancho; transiciones solo de color; foco con el anillo sólido de 3 px Rojo Enlace.

ANTI-GOALS (además de los anteriores): sacar el bloque de usuario del `<header>` en escritorio; un segundo
landmark de navegación; grises translúcidos para texto; iconos que cambien el nombre accesible de un enlace.

VERIFY: navegador real a 375, 768, 1023, 1024, 1280 y 1440 px con nombre y email largos: `scrollWidth ===
clientWidth`; desde 1024 el header mide 240 px de ancho y todo el alto, enlaces de 40 px, `main` desde x = 240,
tarjeta de usuario abajo; orden de Tab Trámites → Ganaderos → Explotaciones → «Cerrar sesión» a 375 y 1440;
anillo de foco entero (sin recorte) en los enlaces de la barra lateral y en «Cerrar sesión». Hecho en T2 y
repetido en el smoke de T8 (Chromium y Firefox, 1440/1024/768/375, axe sin violaciones).
