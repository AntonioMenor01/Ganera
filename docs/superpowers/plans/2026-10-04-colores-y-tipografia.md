# Colores y tipografía de la marca nueva — plan (shape de Impeccable)

Fecha: 2026-10-04. Base: `main` = `b4c8fe9`. **Solo `frontend/` y `DESIGN.md`** (más PRODUCT.md si Antonio
lo aprueba, ver D8). No cambian la disposición, los componentes ni el comportamiento. El logo se queda y se
pinta con un token de marca. Suites de partida: frontend 508, backend 534.

Fuente de la marca: `https://ganera-web.vercel.app/css/tokens.css` (descargado el 2026-10-04: "Modernist
system from Claude Design", rampas completas abajo). Flujo: Impeccable desde la sesión principal (este shape
antes del craft), después Superpowers (implementador + revisor) con TDD donde haya lógica o tests de marca.
Regla de git de CLAUDE.md en todos los briefs. Sin commits sin aprobación; stage ruta a ruta.

## Decisiones aprobadas por Antonio (2026-10-04)

- **Shape aprobado, con D1a:** blanco para las tarjetas, tablas, modal y menús; Superficie para los
  bloques secundarios.
- **Los seis puntos, tal como los escribió Antonio:**
  1. Logotipo: texto en Tinta y símbolo en rojo de marca.
  2. Iconos en todas las alertas de error: sí.
  3. Favicon y PNG regenerados en rojo; ganera-logo-verde.svg se sustituye por la versión roja (renombrar y
     actualizar referencias).
  4. PRODUCT.md: actualizar la identidad visual y la línea de facturación. Única excepción a "solo frontend
     y DESIGN.md".
  5. Radios: sin cambios; decisión pendiente con el socio.
  6. Bordes de inputs: el gris más claro de la rampa que llegue a 3:1 sobre blanco y sobre Superficie,
     medido.
- **Capturas** en `C:\Users\Antonio\Desktop\capturas-ganera-marca\`.
- **El craft empieza mañana**, en una sesión nueva.
- **Medida para el punto 6** (ya hecha en el shape; sustituye a Gris 400 en la tabla de D1 y en D3):
  Gris 500 `#9B9797` da 2,89 sobre blanco y 2,38 sobre Superficie, y no llega. **Gris 600 `#7D7979`** da
  4,30 sobre blanco y 3,55 sobre Superficie, así que es el más claro que llega: `--input` = `#7D7979`.

## Shape

- **Trabajo y público:** modo **Operate**. Administrativos de gestoría en escritorio, varias veces al día, y
  el gestor desde el móvil. Lo que deben hacer no cambia: revisar y aprobar. La marca vive en detalles
  precisos, no en superficie.
- **Autoridad visual:** la marca la fija el brief (paleta + Archivo), así que no hay ronda de conceptos: una
  dirección fijada por el brief gana siempre. La traducción a una herramienta de trabajo tiene una regla
  central: **el rojo sólido actúa; el rojo oscuro teñido avisa** (D2).
- **Alcance:** tokens de `index.css`, la fuente, los pesos tipográficos, el tono del foco y del hover del
  botón principal, el token del logo y los recursos del logo (favicon). Fuera: disposición, radios,
  espaciado, componentes, textos.
- **Lo que haría que saliera mal aunque quedara bonito:** que "Aprobar" y un error se confundan; texto rojo
  de 14 px por debajo de 4,5:1; una fuente pedida a Google; Archivo, que es más ancha, rompiendo la navbar
  o la cola a 375 px.

## Rampas de la marca (tokens.css de la landing)

| Paso | Rojo (`accent`) | Gris (`neutral`) |
|---|---|---|
| 100 | `#FFF2EF` | `#F8F4F4` |
| 200 | `#FFE0D9` | `#EAE7E7` |
| 300 | `#FFC4B8` | `#D7D3D3` |
| 400 | `#FF9783` | `#BAB6B6` |
| 500 | `#FF563C` | `#9B9797` |
| 600 | `#DD2B0F` | `#7D7979` |
| 700 | `#AE1800` | `#605D5D` |
| 800 | `#7C1405` | `#444141` |
| 900 | `#4D170E` | `#2D2B2B` |

Base: Rojo Ganera `#EC3013`, Tinta `#201E1D`, Fondo `#F3F2F2`, Superficie `#EAE9E9`. La landing tiene además
un `accent-2` coral (`#E15B47`) que **no** se usa en la app.

## D1. Correspondencia de tokens

| Token (`index.css`) | Hoy (nombre) | Nuevo | Uso |
|---|---|---|---|
| `--background` | Crema Papel `#F7F6F1` | **Fondo `#F3F2F2`** | lienzo de la página |
| `--foreground`, `--card-foreground`, `--popover-foreground`, `*-foreground` neutros | Tinta `#1C1B17` | **Tinta `#201E1D`** | todo el texto principal |
| `--card`, `--popover` | Blanco Tarjeta `#FFFFFF` | **Blanco `#FFFFFF`** (D1a) | tarjetas, tablas, modal, menús |
| `--secondary`, `--sidebar` | Paja Clara `#F1F0E8` | **Superficie `#EAE9E9`** | barra de navegación, botón secundario |
| `--muted`, `--accent` | Lino `#EEEDE4` | **Superficie `#EAE9E9`** | hover de filas y botones, pie de tarjeta, cita de WhatsApp, esqueletos |
| `--sidebar-accent` | Paja Hover `#E5E3D6` | **Gris 300 `#D7D3D3`** | hover de enlaces de la navbar (sobre Superficie) |
| `--muted-foreground` | Gris Oliva `#6B6B60` | **Gris 700 `#605D5D`** | texto secundario, contadores, enlaces inactivos |
| `--border`, `--sidebar-border` | Borde Lino `#E6E4DA` | **Gris 300 `#D7D3D3`** | divisores y reglas de tabla |
| `--input` | Borde Lino `#E6E4DA` | **Gris 400 `#BAB6B6`** (D3) | borde de inputs y selects |
| `--primary`, `--sidebar-primary` | Verde Monte `#1F3D2B` | **Rojo 600 `#DD2B0F`** | botón principal, enlace activo de la navbar |
| `--primary-foreground` | Crema Papel | **Blanco `#FFFFFF`** | texto sobre rojo (D3: el Fondo sobre 600 no llega) |
| nuevo `--primary-hover` | (hover = 80 % de opacidad) | **Rojo 700 `#AE1800`** | hover del botón principal |
| nuevo `--marca` | (el logo usaba `--primary`) | **Rojo Ganera `#EC3013`** | símbolo del logo |
| nuevo `--enlace` | (los enlaces usaban `--primary`) | **Rojo 700 `#AE1800`** | enlaces de texto (`CLASE_ENLACE`) |
| `--ring`, `--sidebar-ring` | Verde Monte al 50 % | **Rojo 700 `#AE1800` al 100 %** (D3) | anillo de foco |
| `--destructive` | `#791F1F` | **Rojo 800 `#7C1405`** | alertas de error y botón "Sí, rechazar" |
| `--danger` / `--danger-foreground` | `#FCEBEB` / `#791F1F` | **Rojo 100 `#FFF2EF` / Rojo 800 `#7C1405`** | badges `RECHAZADO`, `ERROR_OVZ` |
| `--success` / `-foreground` | `#EAF3DE` / `#27500A` | **sin cambio** | badges de éxito |
| `--warning` / `-foreground` | `#FAEEDA` / `#854F0B` | **sin cambio** | badges de aviso, incl. "incompleto" (D4) |
| `::selection`, caret | verde al 18 % | **Rojo 600 al 18 %** / caret Rojo 600 | selección y cursor |
| `.dark`, `--chart-*` | restos de shadcn | **sin tocar** | no se usan |

- **D1a. ¿Tarjetas blancas o en Superficie?** Recomiendo **blanco para lo que contiene datos** (tarjetas,
  tablas, modal, menús) y Superficie para los bloques secundarios (navbar, pie, cita del mensaje, hover).
  Motivos: blanco da el máximo contraste a tablas y badges (todos medidos sobre blanco) y mantiene la
  jerarquía actual de "papel sobre lienzo". La alternativa, todo en Superficie como la landing, oscurece las
  tablas y pierde un escalón de jerarquía.
- **Logotipo "GANERA" de la navbar:** recomiendo **Tinta en 800**, con el símbolo en Rojo Ganera. El rojo de
  marca como texto de 16 px da 3,47:1 sobre Superficie, que no basta. La alternativa es Rojo 700 (5,91:1),
  con el símbolo y el texto en dos rojos distintos.

## D2. Errores frente a marca

Hoy el rojo significa error, rechazo y 409. La regla propuesta: **el rojo sólido (600) es acción o
ubicación; el rojo oscuro teñido (800 sobre 100) es problema**, y un problema **siempre lleva texto** que lo
nombra.

- **Acción / marca, en rojo vivo:** botón principal ("Aprobar", "Entrar", "Guardar"…) en 600 sólido; enlace
  activo de la navbar en 600 sólido; enlaces de texto en 700; foco en 700; símbolo del logo en `#EC3013`.
- **Problema, en rojo oscuro y nunca en sólido:** alertas `destructive` (409, 403, errores de carga, banner
  de suspendida) con texto 800, sin relleno y con título; badges `RECHAZADO` / `ERROR_OVZ` en 800 sobre 100;
  "Sí, rechazar" como tinte (800 sobre 800 al 10 %, 8,91:1), nunca sólido, así no se parece a "Aprobar".
- **Icono:** el banner ya lleva `TriangleAlertIcon`. Recomiendo que **todas las alertas de error** lleven
  icono (`CircleAlert`): el aviso de acción del modal (409/403) y los errores de carga. Es el único cambio de
  marcado del plan (un icono, sin cambiar disposición ni textos), así que necesita tu visto bueno.
- **No se usa otro tono** (por ejemplo, el coral `accent-2`) para los errores: sería un tercer rojo casi
  indistinguible del de marca. La distinción viene de sólido frente a teñido, del tono oscuro y del icono y
  el texto.

Alternativa descartada: botón principal en Tinta y el rojo solo para la marca. Separaría más, pero
contradice la guía ("rojo 600/700 en botones") y la landing.

## D3. Contraste (medido con la fórmula de WCAG 2.x)

| Uso | Par | Contraste | Veredicto |
|---|---|---|---|
| Botón principal | Blanco sobre Rojo 600 | **4,74** | AA para 14 px ✓ |
| — rechazado | Blanco sobre `#EC3013` | 4,20 | no llega ✗ |
| — rechazado | Fondo `#F3F2F2` sobre Rojo 600 (como la landing) | 4,25 | no llega ✗ |
| Hover del principal | Blanco sobre Rojo 700 | 7,17 | ✓ |
| Enlace activo navbar | Blanco sobre Rojo 600 | 4,74 | ✓ |
| Enlaces de texto | Rojo 700 sobre Blanco / Fondo / Superficie | **7,17 / 6,41 / 5,91** | ✓ |
| — rechazado | Rojo 600 sobre Fondo | 4,25 | ✗ |
| Foco (no texto, ≥ 3:1) | anillo Rojo 700 **al 100 %** sobre Blanco / Fondo / Superficie | **7,17 / 6,41 / 5,91** | ✓ |
| — rechazado | Rojo 600 / 700 al 50 % (como hoy) | 2,1–2,6 | ✗ (el verde actual al 50 % daba 2,73: ya no llegaba) |
| Símbolo del logo (no texto) | `#EC3013` sobre Superficie / Fondo / Blanco | 3,47 / 3,76 / 4,20 | ✓ ≥ 3:1 |
| Texto principal | Tinta sobre Blanco / Fondo / Superficie | 16,60 / 14,86 / 13,70 | ✓ |
| Texto secundario | Gris 700 sobre Blanco / Fondo / Superficie | **6,52 / 5,83 / 5,38** | ✓ (Gris 600 daría 3,55–4,30 ✗) |
| Badge peligro | Rojo 800 sobre Rojo 100 | 9,80 | ✓ |
| Badge éxito | `#27500A` sobre `#EAF3DE` | 8,21 | ✓ |
| Badge aviso / incompleto | `#854F0B` sobre `#FAEEDA` | 5,87 | ✓ |
| Alerta de error | Rojo 800 sobre Blanco | 10,72 | ✓ |
| Selección | Tinta sobre Rojo 600 al 18 % | 12,57 | ✓ |
| Borde de input (no texto) | Gris 400 sobre Blanco / Fondo | 2,01 / 1,80 | ✗ < 3:1 (hoy ≈ 1,2) |

- **Foco:** pasar de `ring-ring/50` a anillo sólido de 3 px en Rojo 700 en los 12 ficheros que lo usan (es
  un cambio de clase en los componentes; es el tono del foco, que está en el alcance).
- **Hover del principal:** pasar de `hover:bg-primary/80` (bajaría el contraste del blanco) a
  `hover:bg-primary-hover`.
- **Bordes de input:** Gris 400 mejora el estado actual sin llegar a 3:1. Llegar exigiría Gris 600, que pinta
  cada campo como un recuadro marcado. Recomiendo Gris 400 y dejarlo anotado.

## D4. Badges con la paleta nueva

- **Aviso / ámbar** (pendientes, "Varios animales coinciden", "Falta la explotación" y **"No está en el
  inventario · incompleto"**): **sin cambio**, `#FAEEDA` / `#854F0B` (5,87:1). El ámbar se distingue
  bien del rojo de marca y del rojo de error, y "incompleto" sigue diciendo "no se puede aprobar todavía"
  sin parecer un error.
- **Éxito** (Aprobado, Ejecutado, En inventario, Titular): sin cambio.
- **Peligro** (Rechazado, Error OVZ): pasa a la rampa de marca, 800 sobre 100 (9,80:1), para que haya un
  único "rojo de problema".
- **Outline** (No está en el inventario, Empleado, Sin guardar): Tinta sobre la superficie que toque, con
  borde Gris 300 (la de "Sin guardar" sigue discontinua al 25 % de tinta).
- Ningún badge usa el rojo de marca vivo: el 600 queda para acciones.

## D5. Tipografía

- **Archivo autoalojada** con `@fontsource-variable/archivo` (OFL-1.1, eje `wght` 100–900, subconjuntos
  latin y latin-ext por `unicode-range`). Vite la empaqueta en `assets/`: ninguna petición a Google ni a
  ninguna CDN. Sale `@fontsource-variable/geist`. Respaldo: `system-ui, sans-serif`.
- **800** (titulares): el `h1` de cada pantalla (hoy 20 px/600), el título "Ganera" de Login/Registro, el
  logotipo "GANERA" y la cifra de la tarjeta métrica.
- **600** (subtítulos y elementos de interfaz): títulos de tarjeta y de sección, título del modal,
  cabeceras de tabla, botones, enlaces de la navbar, badges y etiquetas de campo (hoy 500/600). Se cambia
  `font-medium` por `font-semibold` donde corresponda (27 usos); no se redefine `font-medium` globalmente
  para que el nombre no mienta.
- **400:** cuerpo.
- Tamaños y espaciados **sin cambio**. Los números siguen con `tabular-nums` (Archivo tiene cifras
  tabulares; se comprueba en el craft). Riesgo: Archivo es algo más ancha que Geist; el smoke a 375 px
  mira la navbar, los badges de la cola y las columnas.

## D6. Logo y recursos

- `LogoGanera` pasa de `text-primary` a `text-marca` (`#EC3013`). `marca.test.tsx` cambia primero (TDD).
- `favicon-64.png` y `ganera-logo-512.png` son verdes. Recomiendo regenerarlos en `#EC3013` desde
  `ganera-logo.svg` con el navegador headless (no hay conversor de imágenes instalado), y sustituir
  `ganera-logo-verde.svg` por `ganera-logo-rojo.svg` (para contextos sin CSS).

## D7. Fuera de alcance

- **Radios:** la landing usa esquinas rectas (`--radius-*: 0`). La app mantiene sus radios: es forma, no
  color ni tipografía. Lo anoto para decidirlo aparte.
- Ningún cambio de disposición, espaciado, textos ni comportamiento; el `.dark` sigue sin diseñar.

## D8. PRODUCT.md

Sus "Brand Commitments" fijan el verde `#1F3D2B` y la crema, y Impeccable los lee como identidad fija. Si no
se actualizan, cualquier pasada de diseño posterior defenderá la paleta vieja. Recomiendo actualizar esa
sección (y de paso la línea de "Billing", que aún habla del pago en la app). Es documentación, no código.

## Tareas (tras confirmar el shape)

- **T1 — Tokens y fuente.** `index.css` según D1/D3, `--marca`, `--enlace`, `--primary-hover`; Archivo por
  `@fontsource-variable/archivo`, sin Geist. Test de marca primero: el logo con `text-marca`.
- **T2 — Clases.** Pesos (D5), foco sólido en 700 y hover del principal (D3), `CLASE_ENLACE` con
  `--enlace`, logotipo en Tinta/800, iconos en las alertas de error (si se aprueba D2). Tests donde
  comprueban clases.
- **T3 — Recursos.** Favicon y PNG del logo en rojo, `ganera-logo-rojo.svg` (si se aprueba D6).
- **T4 — Cierre.** `npm test`, `npm run build`, `npm run lint`, `./mvnw clean test`. `impeccable detect`
  sobre lo cambiado. Smoke en navegador real a 1440 y 375 px por todas las pantallas: login, cola, modal con
  409 y 403, ganaderos, explotaciones y banner de suspendida. Capturas con datos inventados **fuera del
  repo**, en `C:\Users\Antonio\Desktop\capturas-ganera-marca\`. `DESIGN.md` reescrito con la paleta, la
  regla de rojos y los contrastes medidos; PRODUCT.md si se aprueba D8; `CLAUDE.md` y `progress.md` al día.
