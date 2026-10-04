---
version: 1
slug: "frontend-src-shared-layout-applayout-tsx"
primary_target: "frontend/src/shared/layout/AppLayout.tsx"
related_targets: []
---

# Barra de navegación (AppLayout)

Scope: la barra superior de `AppLayout` en todas las pantallas autenticadas. Mode: Operate.
Refinamiento: mismo mundo visual (DESIGN.md), solo cambia la topología por debajo de `md`.

Audience and job: el gestor/titular que aprueba desde el móvil fuera de la oficina, y los
administrativos en escritorio. Tienen que llegar a Trámites (y a cualquier sección) con un toque y
la página nunca se desplaza en horizontal.

Decided with Antonio (2026-10-03/04, plan `docs/superpowers/plans/2026-10-03-tarea-frontend-antes-piloto.md`):
- Por debajo de `md` (768 px), dos filas en una sola barra Paja Clara (sin línea entre filas, borde
  Lino abajo como hoy): fila 1 marca + "GANERA" a la izquierda, "Salir" (`outline sm`) a la
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
  en móvil y escritorio; foco visible en los enlaces con el anillo estándar (3 px verde al 50 %);
  `aria-current="page"` en el activo.

## Direction contract

THESIS: la barra de una herramienta de trabajo que cabe en la mano: todas las secciones a la vista
y a un toque, sin menú que abrir. Rechaza la hamburguesa, la barra inferior y los iconos decorativos.

OWN-WORLD: Paja Clara, borde Lino, pill Verde Monte con texto Crema Papel para el activo, Gris
Oliva para los inactivos con Paja Hover al pasar; Geist 14/500; transiciones solo de color.

ANTI-GOALS: degradados o sombras para indicar overflow; sticky; cambios de paleta, tipografía,
textos u orden; duplicar el menú en el DOM; cualquier desplazamiento horizontal de la página.

VERIFY: navegador real a 375, 640, 768 (email largo) y 1440 px: `scrollWidth === clientWidth` del
documento en todos; a 375 px, en Facturación el activo queda visible; `window.scrollY` no cambia al
navegar entre secciones a 375 px; Tab recorre los enlaces con foco visible.
