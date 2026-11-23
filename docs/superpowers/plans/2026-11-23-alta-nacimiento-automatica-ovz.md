# Alta por nacimiento automática en OVZ (escalón 2) — plan

**Fecha:** 2026-11-23. **Rama:** `feat/alta-nacimiento-automatica` (a crear desde `main`).
**Antecedente:** calca la baja por muerte ya en producción
(`docs/superpowers/plans/2026-11-17-presentacion-automatica-baja-ovz.md`, D1–D11; PRs #40–#42).
Mapa del formulario: `docs/referencias/mapa-ovz.md` **sección B. Alta por nacimiento**.

## 0. Principio

Reutilizar toda la infraestructura genérica de la baja (cola `trabajo_ovz`, worker, interruptor por
gestoría, modo ENSAYO por defecto, anti-duplicado estricto, descifrado bajo demanda, documentos en
Postgres). El alta **solo añade** lo específico de su formulario. **No se duplica infraestructura.**

Mismo aislamiento que la baja: el worker opera **dentro de la explotación del trámite** (REGA del
ganadero); nunca cruza explotaciones ni gestorías.

## 1. Flujo

1. El gestor **aprueba** un `ALTA_NACIMIENTO` en Ganera (ese clic **es** la revisión humana; nada
   llega a OVZ sin él).
2. Si el interruptor maestro está activo **y** la gestoría está habilitada **y** el ganadero tiene
   credenciales usuario+contraseña (sin certificado) **y** el alta es autopresentable (ver §5),
   se encola un `TrabajoOvz` tipo `ALTA_NACIMIENTO` (fuera del lock de fila, en la transacción de la
   aprobación, `MANDATORY`). Si no, cae a la vía manual (`HECHO_EN_OVZ`), como la baja.
3. El worker (instancia Docker aparte, perfil `clever,worker`, Playwright Java) reclama UN trabajo
   PENDIENTE global, descifra las credenciales del ganadero (solo en el worker, nunca en logs),
   entra en OVZ, navega por menús hasta la pestaña **«Alta Individual»** (`ctl02_`), **verifica** y
   —solo en modo REAL— pulsa «Aceptar». **Por defecto ENSAYO**: recorre todo hasta ANTES de
   «Aceptar» y sale (`ENSAYO_OK`). REAL solo con `GANERA_OVZ_WORKER_MODO=REAL`.
4. Tras presentar (REAL), intenta descargar el justificante «Alta Bovino» (best-effort, §6) y lo
   guarda en la ficha del ganadero (Postgres). **La ausencia de justificante NO marca el alta como
   fallida.**

## 2. Datos que ya existen y los que faltan

Ya en `Tramite`: `crotalMadre`/`crotalMadreResolucion`, `fechaNacimiento`, `sexo`,
`fechaIdentificacion`. Ya en `PreferenciaOvz`: `emisionDi`, `oficinaDi`, `tipoIdentificador`.
Ya en `ListasOvz`: `RAZAS` (134, por valor), `SEXO`, `TIPOS_IDENTIFICADOR`, `EMISIONES_DI`,
`OFICINAS_DI`.

**Falta: `raza`** (obligatoria en OVZ; hoy el gestor la elige a mano en OVZ en la ficha escalón 1).

## 3. Decisiones (cerradas por Antonio el 2026-11-23)

- **D1 — Raza = Opción A.** Columna `raza` en `Tramite` (código del catálogo `ListasOvz.RAZAS`,
  mapeada **por valor**, no por texto). Migración **V42** (siguiente libre: V40/V41 en main, hueco
  V35–V39 reservado a Antonio; confirmado con `git fetch`). Vendor-neutral (`db/migration`, común
  H2+Postgres: `ALTER TABLE tramite ADD COLUMN raza VARCHAR(...)`). **Sin default en
  `PreferenciaOvz`.** La raza es **obligatoria para aprobar** un `ALTA_NACIMIENTO`.
  **La edición de raza en la ficha + su envío en PATCH van en el MISMO commit que el cambio de
  contrato**, para no dejar `main` roto (backend y frontend despliegan juntos).
- **D2 — Ternero vs. madre.** `datosPresentacionAlta` exige **exactamente un `tramite_crotal`
  completo = el ternero** + `crotalMadre` completo. Si falta cualquiera → error **SEGURO** sin abrir
  OVZ. Además: el crotal del ternero **debe existir y estar «Libre»** en la tabla de crotales
  repartidos de la pantalla 1; si no aparece/no está libre → error **SEGURO** sin presentar.
- **D3 — Justificante.** Best-effort por **crotal + tipo «Alta Bovino» + fecha**, marcado
  **baja-confianza**. **Su ausencia NO marca el alta como fallida** (el DI lo puede emitir la OVZ
  con tasas o imprimirlo el ganadero). Formato real **diferido** a la sesión con credenciales.
- **D4 — Selectores `ctl02_`.** Implementar y testear con `NavegadorOvzFalso` + modo ENSAYO. **REAL
  queda para una sesión con credenciales** (como la baja). **ENSAYO por defecto; nunca pulsa el
  «Aceptar» del alta (`P_btnAceptar`) en ENSAYO.**
- **D5 — Defaults.** Trashumante **sin marcar**; Explotación Nacimiento **en blanco**. Emisión DI
  desde `PreferenciaOvz.emisionDi`; **si no está puesta → por defecto imprimir desde «Animales sin
  DI» (OVZNET, sin tasas)**; la oficina DI **solo cuenta si la emisión es OVZ**. **Tipo
  Identificador y Fecha Identificación obligatorios**; si faltan → **SEGURO** sin abrir OVZ.
- **D6 — Vía manual forzada.** Si **Trashumante** o **raza = LIDIA** → el alta va a **vía manual**
  (no se autopresenta). *(Trashumante no se modela aún; raza=LIDIA sí se comprueba por el código de
  LIDIA en `ListasOvz.RAZAS`. La condición de trashumante queda documentada para cuando se modele.)*
- **D7 — Auditoría.** Añadir códigos **`OVZ_ALTA_*`** paralelos (`OVZ_ALTA_ENSAYO`,
  `OVZ_ALTA_PRESENTADA`, `OVZ_ALTA_ERROR`). **No renombrar `OVZ_BAJA_*`.** Declarar los nuevos en el
  inventario cerrado **sin tocar** las entradas de la baja. El encolado reutiliza
  `OVZ_TRABAJO_ENCOLADO` (ya genérico).

## 4. Qué se reutiliza y qué se añade

| Pieza | Acción |
|---|---|
| `trabajo_ovz` (tabla) | **Sin migración**: ya tiene `tipo` y `UNIQUE(tramite_id)`. |
| `TipoTrabajoOvz` | + valor `ALTA_NACIMIENTO`. |
| `TrabajoOvzService.encolarSiProcede` | Generalizar a baja+alta (condición propia del alta, §5). |
| `TrabajoOvzService.datosPresentacion*` | + `datosPresentacionAlta(...)` → `DatosPresentacionAlta`. |
| `TrabajoOvzService.sePresentaraAutomaticamente` | Admitir `ALTA_NACIMIENTO`. |
| `TrabajoOvzService.finalizar*` | `finalizarEjecutado` guarda doc `ALTA_BOVINO`; auditoría `OVZ_ALTA_*` según tipo. |
| `WorkerOvzService.procesarUno` | Ramificar por `trabajo.getTipo()`: baja (ya) / alta (nueva), misma estructura ENSAYO/REAL/seguro/incierto/nunca-reintenta. |
| `NavegadorOvz` (puerto) + `PlaywrightNavegadorOvz` + `NavegadorOvzFalso` | + `prepararAlta()`, `crotalTerneroLibre(crotal)`, `seleccionarCrotalTernero(crotal)`, `leerCrotalSeleccionado()`, `rellenarFormularioAlta(DatosFormularioAlta)`, `pulsarAceptarAlta()`, `listarDocumentosAltaBovino()`. |
| `VerificacionAlta` (nuevo, puro) | crotal seleccionado (`lblcrotal`) == ternero; + (en el worker) comprobación de «Libre» en la tabla. |
| `TipoDocumentoOvz` | + `ALTA_BOVINO`. |
| `AccionAuditoria` | + `OVZ_ALTA_ENSAYO`, `OVZ_ALTA_PRESENTADA`, `OVZ_ALTA_ERROR`. |
| `PresentacionAutomaticaProperties`, `DescifradoCredencialOvz`, `ListasOvz`, `PreferenciaOvz`, `IdentificadorJustificante`/`BuscadorCrotalEnPdf`, `ValidadorPdf`, `WorkerOvzScheduler`, `DocumentoOvzService`, perfil `clever,worker`, modo ENSAYO | **Reuso tal cual.** |

## 5. Condición de autopresentación del alta (`encolarSiProcede` / `sePresentaraAutomaticamente`)

`ALTA_NACIMIENTO` ∧ interruptor habilitado para la gestoría ∧ explotación con REGA ∧ ganadero con
credenciales usuario+contraseña (sin certificado) ∧ **raza presente y ≠ LIDIA** ∧ **no trashumante**.
Los demás obligatorios (1 crotal ternero, crotal madre, fecha nacimiento, sexo, tipo identificador,
fecha identificación) se exigen en `datosPresentacionAlta` (faltante → SEGURO sin abrir OVZ); se
comprueban también al **aprobar** (regla de aprobación del alta, T1) para no encolar algo
impresentable.

## 6. Verificación y justificante

- **Antes de «Aceptar»:** `VerificacionAlta.verificar(crotalSeleccionado, crotalTernero)`;
  el worker además exige que el crotal del ternero figure **«Libre»** en la tabla de repartidos. Si
  algo no cuadra → `AceptarException`/`NavegacionOvzException` (SEGURO; aún no se pulsó).
- **Justificante (REAL):** `IdentificadorJustificante` sobre `listarDocumentosAltaBovino()` (tipo
  «Alta Bovino»), casado por crotal + fecha posterior al clic. Reintentos acotados **solo de la
  descarga**. No encontrado → el alta queda **EJECUTADO igualmente** (documento pendiente, D3).

## 7. Estados (reuso)

`TrabajoOvz`: PENDIENTE → EN_PROCESO → {ENSAYO_OK | EJECUTADO | ERROR(SEGURO|INCIERTO)}.
`Tramite`: APROBADO → EN_PROCESO → {APROBADO (ensayo) | EJECUTADO_OVZ | ERROR_OVZ}.
Anti-duplicado: una presentación **nunca** se reintenta ante respuesta incierta; reencolado manual
igual que la baja (SEGURO directo; INCIERTO solo con confirmación explícita auditada).

## 8. Desglose TDD (sin implementador subagente; revisor con `-Dtest` dirigido)

- **T0** — Este plan. *(hecho)*
- **T1** — `raza` en el modelo + contrato (un commit): `V42`, `Tramite.raza`, `TramitePatchRequest`,
  `TramiteDetalleResponse`/`TramiteResponse`, validación al aprobar (`TramiteRevisionService`:
  `ALTA_NACIMIENTO` exige raza válida de `ListasOvz`), ficha frontend (`fichaOvz.ts` editable +
  envío en PATCH). Ajustar `FlywayH2VendorTest` (35→36) y `FlywayPostgresTest` (36→37).
- **T2** — Cola: `TipoTrabajoOvz.ALTA_NACIMIENTO`, `encolarSiProcede` genérico (§5),
  `datosPresentacionAlta` + `DatosPresentacionAlta` (record con `toString()` redactado),
  `sePresentaraAutomaticamente`. Tests de `TrabajoOvzService`.
- **T3** — `VerificacionAlta` (puro) + tests.
- **T4** — Puerto `NavegadorOvz` + `NavegadorOvzFalso` + rama alta en `WorkerOvzService`
  (ENSAYO/REAL, seguro/incierto, nunca «Aceptar» en ENSAYO). Tests con falso.
- **T5** — `PlaywrightNavegadorOvz` métodos de alta (selectores `ctl02_`), sin test automático
  (como la baja; validación en vivo diferida a la sesión con credenciales).
- **T6** — `TipoDocumentoOvz.ALTA_BOVINO` + guardado del justificante; `AccionAuditoria` `OVZ_ALTA_*`
  + inventario cerrado (`InventarioAuditoriaTest`) sin tocar la baja.
- **Cierre** — `./mvnw clean test` completo (dar cifras) + smoke H2. Stage ruta a ruta, commits
  locales, **sin push ni PR hasta el OK de Antonio**.

## 9. Riesgos

- Selectores `ctl02_` y formato del justificante «Alta Bovino» **no validados en vivo**: REAL
  diferido. ENSAYO y toda la lógica de dominio sí se cubren con tests.
- Raza por valor: cuidado con los dos «DESCONOCIDA» (901/955) y «PARDE/PARDA DE MONTAÑA»
  (1088/1322) — se guarda el **código**, nunca el texto.
- `main` no debe quedar roto: el cambio de contrato de raza (back+front) en un solo commit (D1).
