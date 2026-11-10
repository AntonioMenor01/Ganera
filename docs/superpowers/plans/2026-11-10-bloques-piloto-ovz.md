# Plan: bloques piloto OVZ (preferencias, campos, plazos, listas)

Fecha: 2026-11-10. Parte de la auditoría de cliente + auditoría UX de OVZ (Álvaro, 24/10/2026)
y de `docs/referencias/ovz-tramites-bovino.md` / `decisiones-ovz-y-piloto.md`.

## Alcance honesto

| Bloque | Qué | ¿Se puede sin sesión de mapeo? |
|---|---|---|
| 1 Listas OVZ | Sexo, raza, tipo identificador, aptitud, categoría | **Parcial:** listas del manual (transporte, responsable). El resto queda provisional / texto libre hasta la sesión con Antonio + gestoría (credenciales nunca en el chat). |
| 2 Campos del trámite | Fecha del hecho, alta (madre/sexo/fecha nac.), destino, MER | **Sí** con valores libres; las listas se afinarán tras el mapeo. |
| 3 Preferencias gestoría | Oficina DI/MER, emisión DI, teléfono guía, transporte… | **Sí** (origen P del catálogo). |
| 4 Plazos / alertas | 7d baja, 7d confirmación, atascados, aprobado sin OVZ | **Sí** con el §5 del manual + reglas de cola. |

**No entra en este plan:** modo asistido Playwright, documentos por ganadero, varios trámites por mensaje, equipo/roles. Eso es post-mapeo / post-piloto (decisiones de socios).

## Decisiones cerradas (para implementar sin preguntar)

1. **Preferencias:** tabla `preferencia_ovz` 1:1 con gestoría (`UNIQUE(gestoria_id)`), extiende `GestoriaScopedEntity`. `GET`/`PUT /configuracion/preferencias-ovz`. Campos nullable (la gestoría rellena lo que usa). Auditoría `PREFERENCIA_OVZ_GUARDADA`.
2. **Listas conocidas del manual** (código, no BD): medio de transporte y tipo de responsable. Sexo/raza/aptitud/categoría/tipo identificador: endpoint `GET /configuracion/listas-ovz` con `provisional: true` y valores vacíos o mínimos hasta el mapeo; la UI admite texto libre si la lista está vacía.
3. **Campos en `tramite` (V26+):** `fecha_hecho` (DATE, Europe/Madrid semántica), `fecha_nacimiento`, `sexo` (texto ≤40), `crotal_madre_indicado` (normalizado como crotal), `numero_mer`, `destino_rega`, `destino_texto`, `consignatario`, `aptitud_movimiento`. Null = desconocido. PATCH los acepta; la extracción IA los rellena cuando pueda.
4. **Extracción IA:** ampliar `SalidaExtraccion` con esos campos opcionales + `fechaHecho` (ISO date). El prompt recibe la fecha de recepción del mensaje (referencia “hoy/ayer”). Invalid/blank → null. No bloquea aprobación si faltan (la ficha marca pista); la aprobación sigue con la regla A1 de crotales.
5. **Ficha OVZ:** usa detalle del trámite + preferencias de la gestoría para rellenar lo que haya; el resto sigue con `pista`.
6. **Alertas / plazos (calculadas, sin tabla):** `GET /alertas` → lista de `{id, tipo, titulo, detalle, venceEl, tramiteId?, severidad}`. Tipos iniciales:
   - `BAJA_PLAZO_7D` si tipo BAJA_MUERTE y `fecha_hecho` + 7d ≤ hoy+3
   - `CONFIRMACION_PLAZO_7D` igual para CONFIRMACION_MOVIMIENTO
   - `ENTRADAS_13D_BLOQUEO` aviso informativo si hay confirmación pendiente >10d (manual: a los 13d OVZ bloquea guías)
   - `REVISION_ATASCADA` PENDIENTE_REVISION con `created_at` > 2d
   - `APROBADO_SIN_OVZ` APROBADO > 3d
   Sin `fecha_hecho`, solo aplican atascado / aprobado-sin-OVZ.
7. **Inicio + Calendario:** KPI Alertas = `GET /alertas` count; Calendario mezcla hechos en OVZ (como hoy) + vencimientos de plazos con `fecha_hecho`.
8. **Guion de mapeo:** documento listo en `docs/referencias/guion-sesion-mapeo-ovz.md` para la reunión (sin credenciales).
9. **Multi-tenancy / auditoría:** toda escritura con `gestoriaId` explícito; inventario cerrado; tests E2E dos gestorías.
10. **PRs:** un PR por bloque o un PR único por oleada si el diff es manejable; siempre desde `origin/main` fresco (Antonio mergea en paralelo).

## Orden de commits

1. Docs (plan + guion mapeo)
2. Preferencias + listas + Config UI + ficha consume preferencias
3. Columnas trámite + PATCH + extracción + modal/ficha
4. Alertas + Inicio + Calendario
5. Tests verdes + PR
