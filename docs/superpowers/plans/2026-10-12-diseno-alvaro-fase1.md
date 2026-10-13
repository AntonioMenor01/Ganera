# Diseño de Álvaro — fase 1 (solo estética) · plan v2, ≤ 4 días

**Estado:** para aprobar. Sin código ni commit hasta tu visto bueno.
**Base:** `main` = `origin/main` = `aac93ee`. Backend 968 (967 + 1 omitido), frontend 976 en 45
ficheros, comprobados hoy.
**Referencias (fuera del repo):** `C:\Users\Antonio\Desktop\ganera-diseno\` (v2 manda sobre
`styles.css`).
**Capturas de este plan (fuera del repo):** `C:\Users\Antonio\Desktop\capturas-ganera-diseno-fase1\`.

## Decisiones cerradas (2026-10-12)

- **Sin pantalla de Configuración.** El nombre del usuario y «Cerrar sesión» van en la cabecera.
- **Ganaderos** cambia solo por los tokens globales, sin rediseño propio.
- **Login:** panel rojo con «Los mensajes de WhatsApp de tus ganaderos llegan como trámites listos
  para revisar.». Sin Google, sin recuperar contraseña y sin ayuda por WhatsApp.
- **Modal:** se mantiene el orden actual de las columnas (mensaje a la izquierda, datos a la derecha).
- **Fuera por el contrato:** las columnas «Ganadero» y «Fecha» de la cola, el bloque de la gestoría
  en la barra lateral y la confianza de la IA en el modal.
- **Gris 700 (#605D5D)** para todo texto que no llegue a AA. Archivo con el eje de ancho, en local
  (+55 KB la primera carga).
- **Fuera:** reordenar columnas y cualquier cambio de estructura que obligue a reescribir los tests de
  la lista o del modal más allá de clases y textos.

---

## Impeccable · shape (shell, lista y modal) — para aprobar antes de implementar

Las tres superficies son **refinamiento** dentro del mundo de Ganera, en modo **Operate**. Se
conservan la identidad, el comportamiento, los textos y el DOM; cambia la piel, con v2 como
autoridad visual.

**Común a las tres:**
- radio 0 en todo;
- Archivo al 90 % de ancho, con pesos 700 (títulos), 500 (interfaz) y 400 (lectura);
- tarjetas blancas con borde fino Gris 300 sobre Fondo;
- divisores de 2 px en tinta al 40 % para separar zonas;
- badges rectos de 24 px con icono a la izquierda y los pares de estado;
- cifras tabulares;
- el foco se queda como hoy: anillo sólido Rojo 700, ahora recto.

### Shell (`AppLayout`)

- **Tesis:** el mismo `<header>`, la misma `nav` y el mismo orden (marca → enlaces → usuario). Desde
  `lg` (1024 px) ese header se convierte en la **barra lateral** de v2; por debajo sigue siendo la
  barra superior de hoy, solo que con la piel de v2. Sin DOM duplicado.
- **Escritorio (≥ `lg`):**
  - columna fija de 240 px en Superficie con borde derecho de 2 px;
  - arriba, el símbolo en marca y «GANERA» 700;
  - **Trámites, Ganaderos y Explotaciones** con icono lucide (`clipboard-list`, `users`, `warehouse`)
    y 40 px de alto; la activa en Rojo 600 con texto blanco, y las demás con hover en Gris 300;
  - **abajo del todo**, en el sitio donde v2 pone el bloque de la gestoría, la tarjeta blanca de
    usuario: iniciales en un cuadro Rojo 100/800, el nombre (500) y el email (12 px, Gris 700,
    truncado), y debajo «Cerrar sesión» como botón secundario a todo el ancho;
  - el contenido ocupa el resto, con el banner de suscripción arriba, en los pares de estado.
- **Móvil y tableta (< `lg`):** la barra de dos filas de hoy (por debajo de `md`) o de una fila (`md`),
  con la piel de v2:
  - fondo Superficie y borde inferior de 2 px;
  - enlaces rectos con icono y el activo en Rojo 600;
  - la tira con scroll interno y ajuste del activo (`tiraNavegacion`), sin cambios;
  - a la derecha, las iniciales y «Cerrar sesión». El nombre y el email solo desde `md`, truncados.
- **Fuera:** búsqueda Ctrl K, campana, contadores y plegar el menú.
- **La única decisión de forma que te pido:** en escritorio, el nombre y «Cerrar sesión» van **abajo
  en la barra lateral**, no arriba a la derecha como en v2.
  - Así no se duplica el DOM, el orden de tabulación no cambia y los tests de `AppLayout` solo
    cambian selectores.
  - Ponerlos arriba a la derecha exige sacar el bloque de usuario del `<header>` en escritorio (otro
    contenedor y otro landmark). Lo estimo en +0,5 días y en reescribir tests de orden de
    tabulación.

### Lista de trámites (`TramitesPage`)

- **Tesis:** la tabla de v2 sobre nuestras columnas y en nuestro orden (Trámite · Tipo · Estado ·
  Explotación · Crotales), con los mismos textos y el mismo botón «Revisar trámite #N» en la primera
  celda.
- **Cabecera:** se quedan h1 «Cola de trámites» y «N trámites» debajo, con un divisor de 2 px
  bajo los dos.
- **Filtro:** el `Select` actual, con la piel de v2 (blanco, recto, 38 px).
- **Tabla:**
  - contenedor blanco con borde Gris 300;
  - cabeceras de 11 px, 500, en mayúsculas, con 0,08em de espaciado, en Gris 700, y raya de 2 px;
  - filas de 56 px con borde Gris 300 y hover tinta al 4 %;
  - `#id` tabular;
  - **Tipo:** icono lucide + etiqueta en 500 (`log-in` alta, `log-out` baja, `truck` solicitud de
    movimiento, `arrow-down-to-line` confirmación, `list-ordered` censo, `tag` demora); «Sin
    determinar» sin icono;
  - **Estado:** badge con icono (`sparkles` extracción, `eye` revisión, `check` aprobado,
    `circle-check` hecho en OVZ, `x` rechazado, `archive` descartado). **Los textos y los pares no
    cambian:** «Aprobado · falta OVZ» sigue en aviso.
  - avisos de extracción y «Parece un mensaje sin trámite» en 12 px con el par de aviso o Gris 700,
    como hoy;
  - Explotación (REGA) y Crotales, tabulares.
- **Vacío, carga y error:** los mismos, con la piel nueva. El esqueleto se queda.
- **Paginación:** botones secundarios rectos de 36 px.
- **Móvil:** sin cambios de estructura en P1; ver P2.

### Modal de revisión (`TramiteReviewDialog`)

- **Tesis:** se conserva la «mesa de cotejo» del brief existente (mensaje a la izquierda, datos a la
  derecha, pie fijo). Solo cambia la piel, con el lenguaje de tarjetas del detalle de v2.
- **Panel:** recto, blanco, con sombra `lg` (la única capa flotante que la lleva) sobre un velo
  Gris 900 al 50 %. El ancho se queda en `md:max-w-4xl`; a pantalla completa por debajo de `md`.
- **Cabecera:** «Trámite #N» en 700 con cifras tabulares y el badge de estado con icono, sobre un
  divisor de 2 px.
- **Columnas:**
  - la del mensaje como cita sobre Fondo con borde Gris 300 y su título en 11 px mayúsculas, Gris 700;
  - los campos en 40 px, blancos, con borde Gris 600;
  - las filas de crotal con divisores Gris 300, el crotal en 500 tabular y el badge con su par;
  - las bandas («Hecho en OVZ», descartado) rectas, con los pares;
  - la sección de WhatsApp con la misma piel: textarea blanca y recta, y los estados como badges
    con su par. **Sin** respuestas rápidas, adjuntar ni Ctrl+Intro.
- **Pie:** blanco con borde superior de 2 px.
  - «Aprobar»: primario Rojo 600, sin cambios.
  - «Guardar»: pasa a secundario (blanco con borde Gris 600), como el «Guardar borrador» de v2, para
    que solo «Aprobar» sea rojo sólido.
  - «Rechazar» y «Sí, rechazar»: peligro (blanco con borde y texto Rojo 800, hover Rojo 100). Nunca
    rojo sólido.
  - El resto, como hoy.
  - **Sin** el escudo «Nada llega a OVZ.net hasta que lo envías».
- **Detalle:** `scroll-mb-3` en los enfocables del cuerpo con scroll, para que un campo enfocado no
  quede pegado al pie.
- **Intocable:** la lista de menciones de OVZ por estado (`TramiteReviewDialog.test.tsx` y
  `FichaOvzPage.test.tsx`), las confirmaciones en línea, el orden del DOM y los textos.

---

## Prioridad 1 (obligatoria) — tabla de tareas

Cada tarea pasa por implementador + revisor independiente (Superpowers), con TDD donde hay lógica.

| # | Tarea | Qué incluye | Días |
|---|---|---|---|
| T1 | Tokens, fuente y primitivos | `index.css`: radio 0, `wdth.css`, `font-stretch: 90%`, rampas Gris/Rojo como tokens, par neutro; `button`, `badge`, `input`, `select`, `card`, `table`, `dialog` y `alert` con la piel de v2; pesos 800/600 → 700/500 en los componentes (42 usos) | 1,0 |
| T2 | Shell | §shape: barra lateral desde `lg`, barra superior en móvil con la piel de v2, bloque de usuario e iniciales (función pura, TDD), «Salir» → «Cerrar sesión» | 0,75 |
| T3 | Login | Panel Rojo 600 desde `lg` (logo, «GANERA», la frase aprobada); banda roja corta en móvil; h1 «Entrar en Ganera»; campos de 44 px con icono; error con el par «problema» y el texto uniforme de siempre | 0,25 |
| T4 | Lista de trámites | §shape; iconos por tipo y por estado en `etiquetas.ts` (pura, TDD) | 0,5 |
| T5 | Modal de revisión | §shape, incluido `scroll-mb-3` | 0,75 |
| T6 | Ficha OVZ | Misma piel: cabecera fija en Superficie con divisor de 2 px, secciones como tarjetas rectas, «Copiar» secundario de 30 px, bandas con los pares. Sin cambios de texto ni de orden | 0,25 |
| T7 | Explotaciones: dos detalles | «Animales» y «Contactos» en masculino (2 creados, 1 actualizado) en la frase anunciada y en la visible (TDD: rojo primero, 0/1/2 por hoja); cabecera de la columna de «Ver animales» visible como «Animales» desde `sm` (debajo, `sr-only` como hoy) | 0,25 |
| T8 | Cierre | `DESIGN.md` (radio, pesos, ancho, barra lateral, par neutro) y brief del shell; smoke en navegador real (Chromium y Firefox; 1440, 1024, 768 y 375 px; contraste con axe; Tab en el modal para `scroll-mb-3`) | 0,25 |
| | **Total P1** | | **4,0** |

**Por qué cabe:**
- La estructura no cambia. Todo lo que no tiene lógica son clases.
- `text-muted-foreground` y los placeholders **ya son Gris 700** (#605D5D, ≥ 5,38:1). El trabajo de
  contraste es no importar los grises translúcidos de v2: tinta al 55 %, 60 % y 50 %, y
  placeholder #7D7979 (4,30:1).
- El foco se queda con el anillo actual: así no se tocan las ~10 aserciones `focus-visible:ring-ring`.

**Riesgo:** T1 marca el ritmo. Si los primitivos se complican, T6 y T8 se aprietan; no recorto
pruebas.

### Tests que espero tocar (solo selectores, clases y textos; ningún comportamiento)

| Fichero | Qué cambia | Aprox. |
|---|---|---|
| `shared/brand/marca.test.tsx` | `font-extrabold` → `font-bold` en «GANERA»; el título del login pasa a «Entrar en Ganera» | 2–3 |
| `shared/layout/AppLayout.test.tsx` | clases de la tira (`md:overflow-visible`, `md:shrink-0` → su equivalente en `lg`), el email truncado (otras `max-w`), «Salir» → «Cerrar sesión» | 4–6 |
| `shared/auth/sesion.test.tsx` | «Salir» → «Cerrar sesión» | ~4 |
| `features/auth/LoginPage.test.tsx` | el título, si lo busca por texto | 0–2 |
| `features/tramites/BadgesTramite.test.tsx` | el badge `outline` → par neutro (`border-border` → la clase neutra) | 2–4 |
| `features/tramites/TramitesPage.test.tsx` | `border-border` de los badges outline (3); lo demás (`bg-warning`, `sr-only`, `tabular-nums`, `text-enlace`…) se mantiene | 3–5 |
| `features/tramites/TramiteReviewDialog.test.tsx` | `md:rounded-xl` → recto; bandas `bg-muted` → par neutro; `border-border` del outline | 4–8 |
| `features/tramites/TramiteReviewDialog.whatsapp.test.tsx` | badges de estado de la respuesta | 0–2 |
| `features/tramites/FichaOvzPage.test.tsx` | `border-border` del outline | 1 |
| `features/explotaciones/ImportarExcelSection.test.tsx` | «creadas» → «creados» en Animales/Contactos: **este sí es un cambio buscado**, con el test en rojo primero | ~6 |
| `features/explotaciones/ExplotacionesPage.movil.test.tsx` | cabecera «Animales» visible desde `sm` y la frase del resumen | 2–3 |
| `features/ganaderos/GanaderoDetallePage.test.tsx` | `border-border` del outline (rol) | 0–1 |
| `features/tramites/etiquetas.test.ts` | **nuevos**: icono por tipo y por estado | +8 |
| `shared/layout/iniciales.test.ts` (nuevo) | **nuevos**: iniciales | +5 |

Sin cambios previstos: `useRevisionTramite.test.tsx`, `contratoDescarte.test.tsx`, `tiraNavegacion.test.ts`,
`GanaderosPage.test.tsx` y `AnimalesDeExplotacion.test.tsx`. Si alguno de los tests de la lista o del
modal necesitara cambiar algo más que un selector, una clase o un texto, **paro y te lo digo**.

---

## Prioridad 2 (solo si sobra tiempo dentro de los 4 días)

| Tarea | Coste | Estado |
|---|---|---|
| **«Abrir #N» del descartado reciente.** Al cerrar el modal tras descartar, la cola enseña una banda neutra «Descartado como mensaje sin trámite: #N.» con «Abrir #N», que reabre el modal donde está «Deshacer». `sessionStorage` solo con el id (`try`, que se pueda cerrar). TDD en la función pura. | 0,5 | Entra solo si P1 cierra en 3,5 días. Si se va de ½ día, fuera. |
| **Tarjetas-fila en móvil.** | 0,75–1 | **No cabe en los 4 días.** Se propone como tarea propia después. |

**La captura de las tarjetas-fila** (cola real con la API simulada y datos inventados, Chromium a
375 px):
- `cola-375-pantalla.png`: la tabla mide **1.099 px** y se ven **325**, es decir, el **30 %**. Se ven
  el número y el tipo, y el estado cortado («Pendiente d…»). La explotación y los crotales quedan
  fuera, sin ninguna pista de que hay más a la derecha.
- `cola-375-desplazada.png`: hay que deslizar casi 800 px para ver los crotales.
- `cola-375-solo-clases.png`: dejando cortar línea en todas las celdas (solo CSS), la tabla sigue
  midiendo **680 px** y los badges se parten en tres líneas. **Solo con clases no se arregla.**

**Conclusión:** a 375 px se puede abrir un trámite (el botón «Revisar trámite #N» se ve), pero no se
puede **triar** la cola, porque estado, explotación y crotales no se ven. Las tarjetas-fila serían un
render alternativo por debajo de `md`. En jsdom, sin `matchMedia`, se sigue pintando la tabla, así
que no habría que reescribir los tests actuales, solo añadir los suyos. Aun así no caben en el plazo.

---

## Forma de trabajo

- **Aprobación previa:** la shape de arriba. Sin direction contract nuevo; el brief del shell se
  actualiza en T8.
- **Orden:** T1 → T2 → T3–T7 → T8. Briefs e informes en `.superpowers/sdd/df-*`, sin commitear. Los
  subagentes no tocan el índice de git.
- **Cierre de cada tarea:** `npm test`, `npm run build`, `npm run lint` (solo los avisos conocidos) y,
  al final, `./mvnw clean test` (968, sin cambios: no se toca el backend).
- **Sin commit ni push sin tu aprobación.**
