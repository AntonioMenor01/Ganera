# A2 — Handoff para la Task 11 (cierre)

Escrito el 2026-10-02 al terminar la Task 10. La sesión siguiente arranca aquí.

## Estado

- **Tasks 1–10 cerradas.** Cada una tiene su informe y su revisión aprobada en `.superpowers/sdd/a2-taskN-*`.
  - 9b: re-revisión de código APPROVED, al final de `a2-task9b-review.md`; pasada de veredicto de
    Impeccable `disposition: pass`, al final de `.impeccable/review/revision/finish-review.md`.
  - 10: `a2-task10-audit.md` (Impeccable audit, 14/20), `a2-task10-report.md` (polish) y
    `a2-task10-review.md` (APPROVED, sin Critical ni Important).
- **Números de frontend** (`frontend/`, 2026-10-02):
  - `npm test`: 36 ficheros / 475 tests en verde;
  - `npm run build`: OK, solo el aviso del chunk de más de 500 kB (622 kB, anotado en el plan para
    más adelante);
  - `npm run lint`: solo los 3 avisos `only-export-components` de siempre.
- **Git:** sin commits ni staging. Nada en `backend/` tocado en A2.
- **Vite:** apagado; no queda nada escuchando en 5173, 5174 ni 8080.

## Qué hizo la Task 10 (resumen)

**Medición en navegador real** (Chromium por npm fuera del repo, API simulada, axe-core 4.13):
- 22 escenarios a 375, 640 y 1440 px; 0 fallos de contraste.
- Tras el polish, Login y Registro sin violaciones de axe.
- Explotaciones y Ganaderos ya no hacen scroll lateral a 375 ni a 640 con un nombre de 27
  caracteres sin espacios.

**Cambios** (lista de ficheros en `a2-task10-report.md`):
- `wrap-anywhere` en los nombres de las tablas;
- `main` + `h1` en Login y Registro;
- regiones `status` siempre montadas, fuera de `aria-busy`, en Animales, Ganaderos, la cola,
  Facturación y el importador (con un resumen anunciado y plurales reales);
- esqueleto de carga en la cola;
- "+N más" es un desplegable (`aria-expanded`) dentro de la fila;
- el badge de Facturación usa los pares de estado, y se quitó la alerta duplicada;
- `CLASE_ENLACE` está en `src/shared/ui/enlace.ts` y se aplica a los enlaces de Login, Registro
  y el `#id` de la cola;
- `nav` + `tabular-nums` en Explotaciones, y `headers` en la celda del panel de animales;
- subtítulos a 14 px;
- columna de badges fija en las filas de crotales;
- selección y cursor con el verde de la paleta;
- los huecos de tests de la lista de pendientes.

## Pendiente fuera de A2 (no hacer en la Task 11)

- **Navbar en móvil:** la página mide 837 px a 375 y a 640. Está fuera de A2 por el plan.
- **Code-splitting** del chunk de 622 kB.
- **n2 de la 9b:** el aviso de ">100 coincidencias" vive en `Combobox.Status` y puede no leerse al
  abrir la lista. Necesita un lector de pantalla real.
- **Minors de la revisión de la Task 10 (m1–m4):**
  - "Animales: N creadas" en el anuncio;
  - el desplegable sin `aria-controls`;
  - tests con `getByRole("status")` sin nombre;
  - el estado dice "Cargando…" también en recargas.
- **Mini-prompt de backend tras A2:** la lista está en el plan, "Fuera de alcance".

## Task 11 — qué falta (del plan)

1. `npm test`, build y lint, y `./mvnw clean test` en `backend/`. Ver en `CLAUDE.md` la nota de
   `JAVA_HOME` y la de usar siempre `clean`.
2. Smoke en un navegador real (decisión 18 del plan) contra el backend real (H2, skill
   `smoke-test-h2`). Recorrido:
   - login;
   - Ganaderos → detalle → animales;
   - Explotaciones + importación;
   - la cola → el modal: PATCH, `aprobar` con `version`, 409 con su motivo, rechazar;
   - Facturación.
3. Poner al día `CLAUDE.md`, `ganera-prompts.md` y `progress.md`:
   - quitar la "Known breakage since Prompt A1" de Aprobar (A2 ya envía la `version`);
   - quitar la nota de `TramiteReviewDialog` desfasado;
   - documentar Vitest/MSW (ya hay herramientas de test de frontend);
   - la sesión en `sessionStorage`;
   - las pantallas de Ganaderos;
   - el mini-prompt de backend.
4. DESIGN.md (decisión del plan): los textos acumulados en
   `.impeccable/review/ganaderos/documenter.md` y las notas "for DESIGN.md" de los informes
   `a2-task4` a `a2-task9b`.
5. La lista exacta de rutas para el commit, y una propuesta de mensaje. **Sin commit hasta que
   Antonio lo apruebe.** El commit lleva los 4 ficheros del logo de `frontend/public/` y nunca
   lleva `preview.png`, `logo.jpg`, `capturaTramites.jpeg` ni los informes `a1-*`/`a2-*` (decisión
   19). No están en `.gitignore`: hay que añadir ruta a ruta.

## Reglas que siguen en vigor

- Sin commits sin la aprobación de Antonio.
- Nada en `backend/`.
- `preview.png` y `logo.jpg` nunca se suben.
- Las pasadas de Impeccable las hace el orquestador en la sesión principal (decisión 27).
