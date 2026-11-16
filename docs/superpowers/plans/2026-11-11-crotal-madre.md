# Plan — Balance OVZ (modo asistido) + resolver el crotal de la madre

Fecha: 2026-11-11. Rama: `feat/crotal-madre` (desde `origin/main` = `a446c4e`, tras la #31).
Parte del brief de balance de OVZ para el modo asistido y de
[2026-11-10-bloques-piloto-ovz.md](2026-11-10-bloques-piloto-ovz.md) /
[../../referencias/ovz-tramites-bovino.md](../../referencias/ovz-tramites-bovino.md) /
[../../referencias/decisiones-ovz-y-piloto.md](../../referencias/decisiones-ovz-y-piloto.md).

Este documento guarda el **balance** y las **decisiones** acordadas, y define la **primera tarea**
(resolver el crotal de la madre). Va en el commit de esa tarea, no por separado.

---

## 1. Balance de OVZ para el modo asistido (alta por nacimiento)

### Qué existe hoy (ya está, no solo esqueleto)

El bloque piloto OVZ está implementado end-to-end: la cadena **IA → `DatosHecho` → columnas del
trámite (V27) → ficha OVZ** funciona.

- **Esqueleto Playwright:** `OvzAutomationService` es interfaz; `PlaywrightOvzAutomationService`
  lanza `UnsupportedOperationException` en los dos métodos. Es *el* trabajo del escalón 2, bloqueado
  hasta el mapeo.
- **Descifrado OVZ (M2):** `DescifradoCredencialOvz` deja auditoría antes de descifrar; sin llamador
  de producción; dos excepciones aún sin mapear a HTTP.
- **Campos de trámite (V27):** `fecha_hecho, fecha_nacimiento, sexo, crotal_madre_indicado,
  numero_mer, destino_rega, destino_texto, consignatario, aptitud_movimiento, fecha_identificacion`.
- **Extracción IA:** el prompt pide esos campos; `ExtraccionTramiteService.aplicarDatosHecho` los
  vuelca. El `crotal_madre` se guarda **solo normalizado como texto** (`crotalMadreIndicado`), **sin
  resolver contra el inventario** → ESTA tarea lo cierra.
- **PATCH** acepta los 9 campos; **preferencias OVZ** (`preferencia_ovz`, `GET/PUT
  /configuracion/preferencias-ovz`); **listas OVZ** (`GET /configuracion/listas-ovz`:
  transporte/responsable/emisión DI definitivas; sexo/raza/tipo id/aptitud/categoría `provisional`,
  vacías); **ficha OVZ** consume detalle + preferencias; **alertas** (`GET /alertas`:
  `REVISION_ATASCADA`, `APROBADO_SIN_OVZ`, `BAJA_PLAZO_7D`, `CONFIRMACION_PLAZO_7D`).

### Alta por nacimiento, campo a campo (OVZ §2.1 vs. Ganera)

| Campo OVZ | Obl. | Hoy en Ganera | ¿Listo para solo revisar? |
|---|---|---|---|
| Explotación (REGA) | sí | `explotacionCodigoRega` | ✅ |
| Cuenta OVZ (titular) | sí | nombre+NIF del Ganadero | ✅ (login manual) |
| Crotal del ternero | sí | **Siempre falta** (no hay repartidos ni inventario del ternero) | Piloto: lo teclea el gestor (dec. 3) |
| Crotal de la Madre | sí | Solo lo escrito, **sin resolver** | **Esta tarea** lo resuelve |
| Fecha Nacimiento | * | IA | ✅ |
| Identificador de Lidia | no | Gestor (raro) | ✅ |
| Sexo | * | IA (texto) | Texto libre hasta el mapeo (dec. 4) |
| Fecha Identificación | * | IA | ✅ |
| Tipo Identificador | * | Preferencia | ✅ si configurada |
| Raza | * | Lista provisional vacía | Texto libre hasta el mapeo (dec. 4) |
| Emisión del DI | sí | Preferencia | ✅ si configurada |
| Oficina DI | sí | Preferencia | Lista de oficinas a validar en el mapeo |

### Sin credenciales vs. depende del mapeo

- **Ya, sin OVZ:** resolver el crotal de la madre (esta tarea); mapear excepciones del descifrado +
  declarar el endpoint de envío en el inventario de auditoría.
- **Depende del mapeo (RGPD):** listas reales (sexo/raza/tipo id/aptitud/categoría); estructura real
  del formulario (nombres internos, orden, validaciones, pasos hasta Enviar); país/CA desde el REGA;
  documentos que genera cada trámite; plazo real del alta por nacimiento.
- **Esqueleto de «Enviar» contra un OVZ falso:** **no** se hace hasta tener el mapeo (dec. acordada).

### Pendientes del escalón 2 (CLAUDE.md) y encaje

`ejecutarTramite` será el primer llamador de `DescifradoCredencialOvz` (mapear sus excepciones es
prerrequisito); respetar que el descifrado va fuera de la transacción del envío y que el OVZ nunca se
abre dentro del bloqueo de fila del trámite; pantalla de credenciales OVZ pendiente de la gestoría;
botón «Enviar» + estados de envío (diseño de Álvaro, fuera de fase 1); documentos por ganadero
(~2–3 días sobre el asistido).

---

## 2. Decisiones acordadas (prevalecen)

1. **Manual:** el mismo OVZNET de Extremadura. Si se pasa el PDF, se compara **solo la revisión**.
2. **Crotal de la madre:** se resuelve **ya** contra el inventario (esta tarea).
3. **Crotal del ternero:** en el piloto lo teclea el gestor en OVZ; **sin** sync 3a.
4. **Sexo y raza:** texto libre hasta el mapeo.
5. **Credenciales:** pendientes de la gestoría; **no** se construye la pantalla.
6. **Primer asistido:** baja por muerte (salvo que la gestoría diga otra cosa).
7. **País/CA:** deducido del REGA, a validar en el mapeo.
- El esqueleto de «Enviar» contra un OVZ falso **no** se hace hasta tener el mapeo.

---

## 3. Tarea: resolver el crotal de la madre (plan TDD)

### Objetivo

Que `crotal_madre_indicado` se resuelva contra el inventario de la explotación del trámite **con el
mismo mecanismo que los demás crotales**, y que la ficha OVZ muestre el crotal completo de la madre
cuando esté en el inventario (y una pista clara cuando no).

### Hallazgo que condiciona el diseño

**El inventario (`Animal`) no guarda el sexo** — solo `crotal`, `crotalUltimosDigitos`,
`explotacion`. Por tanto **no se puede comprobar que el animal de la madre sea hembra**; se resuelve
solo por crotal. El campo `sexo` del trámite es el del **ternero**, no el de la madre. Limitación
documentada; si 3a trae el sexo, se podrá añadir una pista blanda más adelante (fuera de alcance).

### Diseño

- **Reutilizar el mecanismo:** extraer de `TramiteCrotalService.resolver(...)` un método puro de
  resolución `ResultadoResolucionCrotal resolver(String crotalIndicadoNormalizado, Explotacion,
  gestoriaId)` que devuelva `{resolucion, crotalCompletado, animalId?}`. `resolver(fila, ...)` pasa a
  delegar en él → la madre y los crotales del trámite comparten EXACTAMENTE la misma lógica
  (COMPLETO = igualdad exacta; INCOMPLETO = sufijo; EN_INVENTARIO/AMBIGUO/NO_ENCONTRADO;
  SIN_EXPLOTACION si el trámite no tiene explotación). Siempre con `explotacionId` **y** `gestoriaId`
  explícitos (aislamiento heredado).
- **Dónde se guarda:** dos columnas nullable en `tramite`: `crotal_madre` (crotal completado,
  VARCHAR(30)) y `crotal_madre_resolucion` (VARCHAR(20), el enum `ResolucionCrotal`). **Sin FK al
  animal** (la madre es un campo de referencia del formulario; no entra en la lista de animales del
  trámite; evita el problema de `ON DELETE`). `crotalMadreIndicado` se conserva como está.
  → **Migración `V30` común (H2 + PostgreSQL).** *(Confirmar el número antes de implementar.)*
- **Cuándo se resuelve — en AMBOS (extraer y PATCH), y además en alta y aprobar.** Los mismos
  puntos del ciclo de vida que los crotales de la lista, con un único método nuevo
  `TramiteCrotalService.resolverMadre(Tramite, gestoriaId)` (lee `crotalMadreIndicado`; null/blank →
  ambas columnas null; si no, normaliza, resuelve y fija `crotal_madre` + `crotal_madre_resolucion`):
  - **Al extraer:** `ExtraccionTramiteService.aplicarExito`, justo después de `aplicarDatosHecho`
    (que es quien fija `crotalMadreIndicado` desde la IA).
  - **Al hacer PATCH:** `TramiteRevisionService`, después de `aplicarCamposOvz` (que fija el
    `crotalMadreIndicado` corregido) **y** también cuando cambia la explotación aunque no se toque la
    madre (igual que `recalcularEnlaces`: se re-resuelve desde lo **indicado**, nunca desde el crotal
    completado para la explotación anterior).
  - **Al dar de alta a mano:** `TramiteAltaService`, tras fijar `crotalMadreIndicado`.
  - **Al aprobar: NO se resuelve** (ajuste de Antonio). La instantánea `crotal_madre` /
    `crotal_madre_resolucion` es la que se fijó al extraer o al revisar, para que **lo aprobado
    coincida exactamente con lo que el revisor vio**. Aprobar re-resuelve la **lista** de crotales
    del trámite (regla A1 existente), pero **no** la madre. (La madre `AMBIGUO`/`NO_ENCONTRADO` no
    bloquea aprobar — decisión B.)
- **Si el gestor corrige la madre a mano:** el PATCH trae el nuevo `crotalMadreIndicado`,
  `aplicarCamposOvz` lo normaliza y guarda, y `resolverMadre` lo vuelve a resolver en la misma
  transacción → `crotal_madre` + `crotal_madre_resolucion` quedan recalculados a partir de **lo que
  el gestor escribió** (borrar la madre → vacío → ambas columnas a null). La instantánea siempre
  refleja la última corrección humana.
- **Respuesta:** `TramiteDetalleResponse` gana `crotalMadre` (completado) y `crotalMadreResolucion`.
  `TramiteDetalleResponse.from` solo añade dos lecturas (`tramite.getCrotalMadre()` /
  `getCrotalMadreResolucion()`); sin cambio de firma ni de llamadores.
- **Ficha OVZ (frontend):** `alta-crotal-madre` usa `crotalMadre` cuando la resolución es
  `EN_INVENTARIO` (o COMPLETO encontrado); si no, queda `null` con pista: `AMBIGUO` → "Varias madres
  posibles en el inventario"; `NO_ENCONTRADO` → la pista actual del crotal; `SIN_EXPLOTACION` →
  `PISTA_EXPLOTACION`. Se mantiene lo escrito como `escrito`/contexto.
- **Auditoría:** **no se añade acción nueva** y, como los demás crotales, **el crotal de la madre en
  claro NUNCA entra en auditoría**. Las filas existentes (`TRAMITE_EDITADO`, `EXTRACCION_APLICADA`)
  solo guardan recuentos/estados vía el vocabulario cerrado `Clave` (`NUM_CROTALES`, `ANIMAL_IDS`,
  `CROTALES_DESCARTADOS`…), nunca el texto del crotal; `DatosAuditoria` no admite `String` libre, así
  que ni siquiera cabría. La resolución de la madre es estado derivado **dentro de esas mismas
  transacciones ya auditadas** (extracción, PATCH, alta, aprobar); no añade su propia fila. No se
  crea endpoint, `@Scheduled` ni `@Modifying` nuevo → el **inventario cerrado** (`InventarioAuditoriaTest`)
  y la **guardia DML** no cambian (se verifican en verde). **Sin `Clave.CROTAL_MADRE_RESOLUCION` ni
  ninguna otra traza propia** (ajuste de Antonio): la resolución de la madre no deja rastro en
  auditoría más allá de la fila de la operación que ya existía.
- **Recuentos de migración (en el MISMO commit):** V30 es común (`db/migration`), aplica en los dos
  motores. Tras V30: **PostgreSQL aplica 30** (V1–V30, con V26 solo-Postgres incluida); **H2 aplica
  29** (el mismo conjunto menos V26 → hueco en 26: V1–V25, V27–V30). Hay que actualizar en el mismo
  commit los tests de recuento (embedded-Postgres y H2) y la nota de CLAUDE.md.

### Restricciones del refactor (ajustes de Antonio)

- **El refactor de `resolver` no toca los tests existentes.** Los tests actuales de
  `TramiteCrotalService` y de los crotales del trámite **siguen en verde sin modificarlos**. El método
  compartido extraído conserva el comportamiento exacto (misma firma de resultado interno, mismos
  finders con `explotacionId` + `gestoriaId`).
- **Mutación del filtro compartido:** quitar el `explotacionId` **o** el `gestoriaId` del método
  compartido debe poner en **rojo** tanto el test de aislamiento de la **madre** como los de los
  **crotales normales** (se verifica con cp/cmp antes de cerrar).
- **Lista exacta de campos de `TramiteDetalleResponse`:** los tests que la fijan (p. ej.
  `RespuestaWhatsappEndToEndTest`, `TramiteAltaEndToEndTest` y cualquier contrato) se actualizan **en
  el mismo commit** con los dos campos nuevos. Además, **el contrato de descarte del frontend**
  (`frontend/src/test/contrato/descarte-sin-tramite.json` ↔ `TramiteDescarteEndToEndTest`) debe
  seguir cuadrando; si el detalle real cambia de forma, se regenera desde `backend/target/contrato-real/`.

### Plan TDD (tests primero)

Backend (unidad, `@DataJpaTest` donde toque inventario):
1. `TramiteCrotalServiceMadreTest` (unidad del mecanismo):
   - COMPLETO que existe en la explotación → `EN_INVENTARIO`, `crotalMadre` = crotal del inventario.
   - INCOMPLETO (sufijo) con **una** coincidencia → `EN_INVENTARIO` + crotal completo.
   - INCOMPLETO con **varias** → `AMBIGUO`, `crotalMadre` = lo indicado, sin completar.
   - **Ninguna** coincidencia → `NO_ENCONTRADO`.
   - Trámite **sin explotación** → `SIN_EXPLOTACION`.
   - **Aislamiento:** un Animal con el mismo crotal/sufijo en **otra explotación** o en **otra
     gestoría** NO se usa para completar ni enlazar (→ `NO_ENCONTRADO`).
   - `crotalMadreIndicado` null/blank → ambas columnas null.
   - Crotal de madre inválido → no rompe (se queda sin resolver; el PATCH ya valida el formato).
2. Re-resolución al **cambiar la explotación** en PATCH: la madre se recalcula desde lo indicado
   (no desde el crotal completado para la explotación anterior).
3. Aplicación en extracción: un mensaje con madre por sufijo deja `crotalMadre` resuelto.
4. `TramiteDetalleResponse` expone `crotalMadre` y `crotalMadreResolucion`.

2b. **Aprobar NO resuelve la madre** (ajuste de Antonio): fijada la madre `EN_INVENTARIO`, si el
   inventario cambia (se borra/añade el animal) y luego se aprueba, `crotal_madre` y
   `crotal_madre_resolucion` quedan **idénticos** a la instantánea previa a aprobar (la lista de
   crotales sí se re-resuelve, pero la madre no).

Backend E2E (dos gestorías, `@SpringBootTest`):
5. `CrotalMadreEndToEndTest`: alta/seed en gestoría A, PATCH fija la madre por sufijo → GET detalle
   trae `crotalMadre` completo y `EN_INVENTARIO`; una madre con sufijo que coincide en gestoría B
   **no** se resuelve para A (aislamiento sobre HTTP); aprobar no cambia la instantánea de la madre.

Frontend (Vitest):
6. `fichaOvz.test.ts`: con `crotalMadre` + `EN_INVENTARIO` el campo "Crotal de la Madre" se rellena
   y se copia; con `AMBIGUO`/`NO_ENCONTRADO` queda la pista; con `SIN_EXPLOTACION`, la de explotación.
   Tipos nuevos en `types.ts`.

### Verificación

`./mvnw clean test` (incluye guardia DML + inventario de auditoría en verde) y `npm test`.
Mutación (cp/cmp): romper el filtro por `explotacionId`/`gestoriaId` del método reutilizado debe
poner en rojo el test de aislamiento.

### Decisiones (inclinación de Antonio, pendiente de su OK final)

- **A — SÍ.** Migración **`V30`** que guarda `crotal_madre` + `crotal_madre_resolucion` (sin FK),
  como **instantánea** de lo que se resolvió al revisar. (Alternativa descartada: calcular al leer el
  detalle, sin migración, pero tocaría la firma de `TramiteDetalleResponse.from` y sus llamadores.)
- **B — SÍ.** La madre `AMBIGUO`/`NO_ENCONTRADO` **no** bloquea aprobar; es solo una pista en la
  ficha OVZ. Se revisa tras el mapeo.
