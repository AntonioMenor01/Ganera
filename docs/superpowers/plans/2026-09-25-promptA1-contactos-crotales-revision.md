# Prompt A1 — Contactos, crotales en trámites y revisión editable — Implementation Plan

**Goal:** preparar el terreno de 3b (WhatsApp + IA) sin depender de OVZ.net: que el sistema sepa a qué
Explotación(es) pertenece un teléfono, que un Trámite guarde los crotales mencionados, y que la
gestoría pueda corregir el Trámite antes de aprobarlo. **Solo backend.** Nada de frontend, IA,
Twilio ni Stripe.

**Tech Stack:** igual que siempre — Java 21, Spring Boot 3.4.x, Maven (`backend/mvnw`, con
`JAVA_HOME` apuntando a un JDK 21+ solo para el comando), Flyway, JUnit 5 + AssertJ, H2 para tests.
Ejecución inline en la conversación principal (sin subagentes), TDD: test que falla primero.

## Decisiones cerradas con Antonio (Paso 0, 2026-09-25)

1. **Unicidad del teléfono — opción A.** `Contacto` pasa a ser `GestoriaScopedEntity` (`gestoria_id`
   NOT NULL) pero `telefono` **sigue siendo `UNIQUE` global**. Duplicado (en la misma u otra
   Gestoría) → rechazo genérico detectado por la violación del constraint, nunca por consulta previa.
   Se documentan en `CLAUDE.md`: (a) la limitación — un teléfono no puede estar en dos Gestorías, y
   el rechazo permite deducir que el número existe en otra; (b) el camino futuro — un número de
   WhatsApp por Gestoría, identificando la Gestoría por el número de *destino*, lo que permitiría
   `UNIQUE(gestoria_id, telefono)`.
2. **Borrado lógico de Contactos** (`activo=false`): se conserva el historial (trámites/mensajes);
   los inactivos no salen en los listados y no pueden enlazarse a Explotaciones; reactivar = volver a
   `activo=true`. `CLAUDE.md`: en 3b el webhook debe ignorar mensajes de Contactos inactivos.
3. **Rol en la relación**, no en el Contacto: `ContactoExplotacion.rol` (`RolContacto`: `TITULAR`,
   `EMPLEADO`). Se borra `TipoContacto` (y la columna `contacto.tipo`).
4. **No se limita** a un EMPLEADO a una única Explotación (se quita esa frase de `CLAUDE.md`).
5. **Normalización del teléfono** a E.164 (ver Task 1).
6. **`PATCH /tramites/{id}`** recibe la **lista completa** de crotales y reemplaza la anterior.
7. **Crotales en `GET /tramites`** cargados con **una sola consulta** para toda la página.
8. **Formato de errores:** `409` + `{ "motivo": "..." }` para conflictos de estado; `404` sin cuerpo
   para cualquier recurso de otra Gestoría o inexistente (no revela existencia); `400` para datos
   inválidos.
9. **`codigo_explotacion`** de la hoja Contactos = `codigo_rega`.
10. **Catálogo de tipos de trámite (`TipoTramite`) sin tocar** — pendiente de la lista real de Antonio.

### Cambios de Antonio al aprobar el plan

11. **`findByTelefono` (sin filtrar por Gestoría) es EXCLUSIVO del webhook de 3b.** Ni el importador
    ni ningún endpoint autenticado lo usan jamás. El importador busca el teléfono **dentro de su
    Gestoría** (`findByGestoriaIdAndTelefono`): existe y activo → enlaza; existe e inactivo → error
    de fila; no existe → INSERT, y si el INSERT choca con el `UNIQUE` global (el teléfono es de otra
    Gestoría) → error de fila **genérico**, sin revelar que existe en otra. Documentado en el propio
    método (Javadoc) y en `CLAUDE.md`. Test E2E con dos Gestorías: B importa un teléfono de A → error
    de fila, el Contacto de A no gana ningún enlace a Explotaciones de B y B no ve el Contacto de A.
12. **Aprobar y rechazar solo desde `PENDIENTE_REVISION`**; desde cualquier otro estado → `409`
    `{motivo}` (con 3c, reaprobar reenviaría el trámite a OVZ.net). Tests de aprobar y rechazar desde
    un estado no válido.
13. **Flujo Superpowers:** cada tarea la ejecuta un subagente implementador y la revisa un subagente
    revisor independiente antes de pasar a la siguiente. Briefs e informes en `.superpowers/sdd/`.
14. **(tras revisión Task 1, M2)** `TelefonoNormalizador` rechaza `+` seguido de exactamente 9
    dígitos que empiecen por 6, 7, 8 o 9 (un móvil español al que le falta el 34), con su test.
15. **(tras revisión Task 1, M3)** El servicio que crea el enlace Contacto–Explotación comprueba
    explícitamente que la Gestoría del Contacto, la de la Explotación y la del usuario autenticado
    coinciden; si no, lanza una excepción que acaba en `404` como el resto. Con su test. Aplica
    también al importador (Task 3).
16. `.agents/`, `.claude/skills/impeccable/` y `skills-lock.json` son una instalación de Antonio
    (skill Impeccable): no se tocan ni entran en los commits de A1.
17. **(tras revisión Task 3, I-pre1) en Task 7:** el importador existente responde "posible
    duplicado entre gestorias" cuando un `codigo_rega`/NIF/crotal es de otra Gestoría — fuga. Se
    sustituye por un mensaje neutro; `procesarExplotacion`/`procesarAnimal` pasan a finders con
    `gestoriaId` explícito; y `CLAUDE.md` documenta que la unicidad global de
    `codigo_rega`/NIF/crotal/teléfono sigue permitiendo deducir que un valor existe en otra Gestoría
    (limitación inherente, igual que la decisión 1).
18. **(Task 7) Nota en `ganera-prompts.md`, pendientes de 3b:** el enum `TipoTramite` se sustituirá en
    el prompt B por los tipos reales de OVZ para vacuno: `ALTA_BOVINO`, `BAJA`,
    `SOLICITUD_MOVIMIENTO`, `CONFIRMACION_MOVIMIENTO`, `DECLARACION_CENSO`, `MOD_DECLARACION_CENSO`,
    `DEMORA_CROTALIZACION`. Ocasionales para una fase posterior: anulación de guías y rechazo de
    animales en origen. **El enum NO se cambia en A1.**
19. **(Task 7)** Corregir en `CLAUDE.md` y en el Javadoc de `ExplotacionImportFilaService` la
    explicación de `REQUIRES_NEW` (sobre HTTP reutiliza el EntityManager de OSIV con el filtro ya
    activo; solo abre uno nuevo sin filtro con transacción exterior, `@DataJpaTest` o schedulers), y
    ampliar el grep de `findByTelefono(` a todo el backend salvo el webhook.
20. **Crotales incompletos (Task 5):** si un crotal llega solo con los últimos dígitos, se intenta
    completar con la Explotación del Trámite: un único Animal → se guarda completo y se enlaza; varios
    o ninguno → se guarda tal cual, sin enlazar, marcado `AMBIGUO`/`NO_ENCONTRADO` en la respuesta. Al
    cambiar la Explotación se vuelve a intentar. Criterio completo/incompleto: ver Task 5.
21. **(Task 7)** Validar `sort` en `GET /explotaciones` y `GET /tramites` con lista blanca, igual que
    en `/ganaderos` (`shared/web/OrdenacionPermitida`, 400 `{motivo}`), para que todos los listados se
    comporten igual.
22. **(Task 7)** El importador aplica la misma normalización de crotales (`CrotalNormalizador`) a
    `Animal.crotal`. Sin migración de datos existentes.
23. **(Task 6)** `PATCH` → `409` `{motivo}` si dos crotales del trámite se resuelven al mismo Animal.
    **(Task 7)** Nota en `ganera-prompts.md`, pendientes de 3b: la creación automática de trámites
    desde WhatsApp debe deduplicar los crotales que resuelvan al mismo Animal.
24. **(Task 6, paso 0)** `CrotalNormalizador` quita también guiones, puntos y barras (`-`, `.`, `/`),
    igual que los espacios.
25. **(Task 6) Regla de aprobación ampliada:** además de explotación y tipo, no se puede aprobar si
    algún crotal está `AMBIGUO` o `SIN_EXPLOTACION`, ni si alguno está `NO_ENCONTRADO` siendo
    incompleto (solo dígitos) — OVZ necesita el crotal completo. `NO_ENCONTRADO` con crotal completo
    sí se permite (entrada de animales que aún no están en inventario). `409` con un motivo que diga
    qué crotal falla y por qué.
26. **(Task 6)** Avisos del revisor de Task 5 aceptados: bloqueo del trámite al editar
    (`PESSIMISTIC_WRITE`) con conflicto → `409`, y excepciones de validación que salen del
    `@Transactional` y se mapean en el controlador (nunca capturadas dentro y commit).
27. **(Task 7a, R1)** `@Version` en `Tramite`; `PATCH` y `aprobar` exigen la versión mostrada; 409 si
    no coincide; toda escritura (incl. re-resolución al aprobar) la incrementa. **(Task 7b)** Nota en
    `ganera-prompts.md`: el prompt A2 (frontend) debe recargar el detalle y mostrar el `motivo` ante
    cualquier 409 (y enviar la `version`).
28. **(Task 7a, I2)** Aprobar un `NO_ENCONTRADO` completo exige `ES`+12 dígitos, u otro país: 2 letras
    + 8–12 dígitos. Regla provisional en `CLAUDE.md`, pendiente de confirmar longitudes de otros países.
29. **(Task 7a, M1)** `permitAll("/error")` en `SecurityConfig`, con test.
30. **(Task 7b, M4)** Se mantiene: la suscripción bloqueada solo impide aprobar (editar y rechazar
    siguen permitidos). Corregir la redacción de `CLAUDE.md`.
31. **(Task 7a, M-R2)** `LOCK_TIMEOUT` más largo en la configuración H2 de tests.

## Global Constraints

- Toda entidad nueva o convertida es `GestoriaScopedEntity`. **Nunca `findById`** ni
  `findByCodigoRega`/`findByNif` a secas en código nuevo alcanzable desde un endpoint: siempre
  finders con `gestoriaId` explícito (`findByIdAndGestoriaId`, `findByCodigoRegaAndGestoriaId`, …),
  inmunes a los dos bugs de aislamiento ya documentados (orden del interceptor y PK load). Los
  listados nuevos también usan `gestoriaId` explícito (`findByGestoriaId…`), no el filtro ambiente.
- Revisión final (Task 7): `grep` de `findById(`, `findByCodigoRega(`, `findByNif(` en todo el código
  nuevo — cero usos en endpoints nuevos.
- `ContactoRepository.findByTelefono` se **mantiene** sin scope de tenant y queda reservado
  **exclusivamente** al webhook de 3b (sin `Authentication`) — ni el importador ni ningún endpoint
  autenticado lo usan (decisión 11). Javadoc del método lo dice explícitamente.
- Migraciones Flyway, empezando en **`V15`**. Nunca `ddl-auto`.
- Nuevas escrituras por fila del importador: `REQUIRES_NEW` en `ExplotacionImportFilaService`, con
  `gestoriaFilter` reactivado con el `gestoriaId` explícito.
- Tests E2E (`@SpringBootTest(RANDOM_PORT)` + `TestRestTemplate`) con **dos Gestorías** para **cada
  endpoint nuevo**, incluidos obligatoriamente: enlazar un Contacto de A con una Explotación de B;
  asignar a un Trámite de A una Explotación de B; ver los Animales de una Explotación de B. Todos
  deben fallar (`404`) y no modificar nada.
- Ninguna respuesta de Ganadero expone `ovzUsuario`/`ovzPasswordCifrada`.
- Suite completa en verde al final; smoke test HTTP real con H2; `CLAUDE.md` y `progress.md` al día.
  **Sin commit** — resumen, diff y propuesta de mensaje para Antonio.

---

## Task 1: Modelo de Contactos + normalizador de teléfono

**Files:**
- Create: `db/migration/V15__contacto_tenant_rol_y_activo.sql`
- Create: `contacto/RolContacto.java`, `contacto/TelefonoNormalizador.java`,
  `contacto/ContactoExplotacionRepository.java`
- Modify: `contacto/Contacto.java`, `contacto/ContactoExplotacion.java`, `contacto/ContactoRepository.java`
- Delete: `contacto/TipoContacto.java`
- Test: `contacto/TelefonoNormalizadorTest.java` (nuevo, puro); ajustar `ContactoRepositoryTest`,
  `TramiteControllerTest`, `TramiteRepositoryTest`, `TenantIsolationEndToEndTest` (usan `TipoContacto`)

**Migración V15:**
```sql
ALTER TABLE contacto ADD COLUMN gestoria_id BIGINT NOT NULL REFERENCES gestoria(id);
ALTER TABLE contacto ADD COLUMN activo BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE contacto DROP COLUMN tipo;
CREATE INDEX idx_contacto_gestoria_id ON contacto(gestoria_id);
-- telefono sigue UNIQUE global (V6) -- decision A.

ALTER TABLE contacto_explotacion ADD COLUMN gestoria_id BIGINT NOT NULL REFERENCES gestoria(id);
ALTER TABLE contacto_explotacion ADD COLUMN rol VARCHAR(20) NOT NULL;
ALTER TABLE contacto_explotacion ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT now();
CREATE INDEX idx_contacto_explotacion_gestoria_id ON contacto_explotacion(gestoria_id);
```
`NOT NULL` sin backfill: no hay ningún entorno con filas en `contacto`/`contacto_explotacion` (no hay
forma de crearlas todavía — ni UI, ni importador, ni webhook de negocio). Si un Postgres local tuviera
filas sembradas a mano, la migración fallaría de forma explícita, no silenciosa.

**Entidades:**
- `Contacto extends GestoriaScopedEntity`: `telefono` (unique), `nombre`, `activo` (default true).
- `ContactoExplotacion extends GestoriaScopedEntity`: `contacto`, `explotacion`, `rol` (`RolContacto`).
  `UNIQUE(contacto_id, explotacion_id)` se mantiene (V7).

**`TelefonoNormalizador.normalizar(String): Optional<String>`** (público, estático, sin Spring — lo
reutilizará el webhook de Twilio en 3b):
1. `null`/blanco → vacío. Quitar prefijo `whatsapp:` (sin distinguir mayúsculas).
2. Quitar espacios, puntos y guiones. Cualquier otro carácter no dígito (salvo un `+` inicial) → vacío.
3. `+` + 8–15 dígitos → se acepta tal cual (E.164 internacional); si empieza por `+34`, además debe
   ser `+34` + 9 dígitos que empiecen por 6, 7, 8 o 9.
4. `0034` + 9 dígitos, o `34` + 9 dígitos (11 en total) → `+34…` (misma regla 6/7/8/9).
5. 9 dígitos que empiecen por 6, 7, 8 o 9 → `+34…`.
6. Cualquier otra cosa → vacío (rechazo).

Tests: `"612 345 678"`, `"612.345.678"`, `"612-345-678"`, `"whatsapp:+34612345678"`,
`"0034612345678"`, `"34612345678"`, `"+447911123456"` válidos; `"12345"`, `"512345678"`,
`"+3451234567"`, `"61234567a"`, `"+"`, `null`, `""` inválidos.

**Repos:**
- `ContactoRepository`: `findByIdAndGestoriaId`, `findByGestoriaIdAndTelefono`,
  `findByGestoriaIdAndActivoTrue(Long, Pageable)`, `findByGestoriaId(Long, Pageable)` (para
  `incluirInactivos`); `findByTelefono` sin cambios (webhook 3b, sin scope, documentado).
- `ContactoExplotacionRepository`: `findByContactoIdAndExplotacionIdAndGestoriaId`,
  `findByContactoIdInAndGestoriaId` (carga en lote para listados),
  `findByExplotacionIdInAndGestoriaIdAndContactoActivoTrue` (detalle de Ganadero).

## Task 2: Endpoints de Contactos

**Files:** `contacto/ContactoController.java`, `contacto/ContactoService.java`, DTOs
(`ContactoRequest`, `ContactoResponse`, `ContactoExplotacionResponse`, `EnlaceExplotacionRequest`),
`shared/web/MotivoErrorResponse.java` (`record(String motivo)`, compartido con Task 6); repos
`ExplotacionRepository.findByIdAndGestoriaId`.
**Test:** `contacto/ContactoEndToEndTest.java` (dos Gestorías, `TestRestTemplate`).

| Endpoint | Respuesta |
|---|---|
| `GET /contactos?incluirInactivos=false` (paginado) | 200 — solo activos salvo `incluirInactivos=true`; cada uno con sus Explotaciones+rol (carga en lote) |
| `POST /contactos` `{telefono, nombre}` | 201; 400 teléfono inválido/nombre en blanco; **409** `{motivo}` genérico si el teléfono ya existe (violación del constraint, misma u otra Gestoría) |
| `PUT /contactos/{id}` `{telefono, nombre}` | 200; 404 si no es de la Gestoría; 400/409 como arriba |
| `DELETE /contactos/{id}` | 204, borrado lógico (`activo=false`), idempotente; 404 si no es de la Gestoría |
| `POST /contactos/{id}/reactivar` | 200 (`activo=true`); 404 si no es de la Gestoría |
| `POST /contactos/{id}/explotaciones` `{explotacionId, rol}` | 200 con el Contacto; si el enlace ya existe, actualiza el rol (idempotente); 404 si Contacto **o** Explotación no son de la Gestoría; 409 si el Contacto está inactivo; 400 si falta/sobra el rol |
| `DELETE /contactos/{id}/explotaciones/{explotacionId}` | 204; 404 si el enlace no existe en la Gestoría |

El duplicado se detecta con `saveAndFlush` + `DataIntegrityViolationException` dentro de un
`@Transactional` del servicio que **propaga**; el controlador lo captura fuera (mismo patrón que
`RegistroGestoriaController`). Mensaje único: *"No se puede usar ese teléfono para un contacto."*

**E2E obligatorios (dos Gestorías):** listar no ve contactos de B; `PUT`/`DELETE`/`reactivar`
contacto de B → 404 y sin cambios; **enlazar contacto de A con Explotación de B → 404 y sin
enlace**; enlazar contacto de B → 404; desenlazar enlace de B → 404; crear con teléfono que ya usa B
→ 409 genérico; inactivo no aparece en el listado y no se puede enlazar (409).

## Task 3: Importador — hoja "Contactos" opcional

**Files:** `ExplotacionImportService` (lee la hoja si existe, después de Explotaciones y Animales),
`ExplotacionImportFilaService.procesarContacto(...)` (`REQUIRES_NEW` + filtro reactivado),
`ImportResumenResponse` (+ campo `contactos`, con 0s si no hay hoja — añadir un campo JSON no rompe
el frontend actual), `ExplotacionRepository.findByCodigoRegaAndGestoriaId`.
**Test:** `ExplotacionImportServiceTest` (casos nuevos; el workbook se construye en el propio test con
POI, sin fixture binario nuevo).

Columnas por posición: `telefono, nombre, codigo_explotacion (= codigo_rega), rol`. Por fila:
- Validación previa (sin BD): teléfono normalizable, nombre no vacío, rol `TITULAR`/`EMPLEADO` (sin
  distinguir mayúsculas) → si no, error de fila.
- En `REQUIRES_NEW`: Explotación por `findByCodigoRegaAndGestoriaId` (no existe → error de fila);
  Contacto por `findByGestoriaIdAndTelefono`: si existe y está **inactivo** → error de fila (no
  enlazable); si existe y activo → se añade/actualiza el enlace con el rol (cuenta como
  *actualizada*); si no existe → se crea (cuenta como *creada*) — si el teléfono es de otra Gestoría,
  la violación del constraint queda como error de esa fila sin afectar a las siguientes.

Tests: sin hoja Contactos → mismo resultado que hoy; creación + enlace; teléfono repetido en dos
filas/explotaciones → un contacto con dos enlaces; teléfono inválido, rol inválido, explotación
inexistente o de otra Gestoría → error de fila; teléfono de otra Gestoría a mitad de la hoja → error
de esa fila y las siguientes se procesan; contacto inactivo → error de fila. E2E de aislamiento del
import con hoja Contactos en `TenantIsolation…` (lo que importa A nunca aparece para B), y **E2E de
la decisión 11**: la Gestoría B importa un teléfono que pertenece a un Contacto de A → error de fila
con mensaje genérico (no menciona otra Gestoría), el Contacto de A no gana ningún enlace a
Explotaciones de B, y `GET /contactos` de B no lo muestra. **Prohibido `findByTelefono` aquí.**

## Task 4: Ganaderos y Animales (lectura)

**Files:** `ganadero/GanaderoController.java`, DTOs (`GanaderoResumenResponse`,
`GanaderoDetalleResponse`), `GanaderoRepository.findByIdAndGestoriaId`/`findByGestoriaId(Pageable)`,
`ExplotacionRepository.findByGanaderoIdAndGestoriaId`, `explotacion/ExplotacionController` (+ endpoint),
`AnimalRepository.findByExplotacionIdAndGestoriaId(Long, Long, Pageable)`, `AnimalResponse`.
**Test:** E2E en `GanaderoEndToEndTest`.

- `GET /ganaderos` (paginado): `{id, nombre, nif, numeroExplotaciones}` — nunca credenciales OVZ.
- `GET /ganaderos/{id}`: 404 si no es de la Gestoría; `{id, nombre, nif, explotaciones:[{id,
  codigoRega, nombre, contactos:[{contactoId, nombre, telefono, rol}]}]}` — solo contactos activos;
  una consulta para explotaciones y otra en lote para contactos.
- `GET /explotaciones/{id}/animales` (paginado): 404 si la Explotación no es de la Gestoría.

**E2E:** lista sin Ganaderos de B; detalle de Ganadero de B → 404; **animales de Explotación de B →
404**; un JSON de Ganadero nunca contiene `ovz`.

## Task 5: Crotales en trámites

**Files:** `V16__create_tramite_crotal.sql`, `tramite/TramiteCrotal.java` (`GestoriaScopedEntity`),
`TramiteCrotalRepository`, `tramite/CrotalNormalizador.java`, `tramite/TramiteCrotalService.java`,
`TramiteCrotalResponse`, `TramiteResponse`/`TramiteDetalleResponse` (+ `crotales`), `TramiteController`
(listado con carga en lote), `AnimalRepository.findByExplotacionIdAndCrotalInAndGestoriaId`.
**Test:** `CrotalNormalizadorTest` (puro), `TramiteCrotalServiceTest`, ampliar `TramiteControllerTest`.

```sql
CREATE TABLE tramite_crotal (
    id BIGSERIAL PRIMARY KEY,
    gestoria_id BIGINT NOT NULL REFERENCES gestoria(id),
    tramite_id BIGINT NOT NULL REFERENCES tramite(id),
    crotal_indicado VARCHAR(30) NOT NULL,   -- lo que se escribio (normalizado), nunca se pierde
    crotal VARCHAR(30) NOT NULL,            -- completo si se resolvio a un unico Animal; si no, = crotal_indicado
    animal_id BIGINT REFERENCES animal(id),
    resolucion VARCHAR(20) NOT NULL,        -- ResolucionCrotal
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (tramite_id, crotal_indicado)
);
CREATE INDEX idx_tramite_crotal_tramite_id ON tramite_crotal(tramite_id);
CREATE INDEX idx_tramite_crotal_gestoria_id ON tramite_crotal(gestoria_id);
```
Se guarda `crotal_indicado` aparte porque, al cambiar de Explotación, hay que **volver a intentar**
la resolución desde lo que se escribió, no desde el crotal completado para la Explotación anterior.

**Crotal completo vs. incompleto (decisión 20, propuesta a Antonio):**
- Normalización (`CrotalNormalizador`): quitar todos los espacios, mayúsculas; válido si
  `[A-Z0-9]{1,30}`, si no 400. Duplicados (mismo `crotal_indicado`) se colapsan.
- **Completo** = empieza por **dos letras** (código de país, p. ej. `ES` + 12 dígitos) **o** tiene
  **13 o más dígitos**. Se resuelve por igualdad exacta con `Animal.crotal`.
- **Incompleto** = **solo dígitos, entre 4 y 12**. Mínimo 4: es lo que suelen escribir los ganaderos
  (`CLAUDE.md`: "normalmente los últimos 4 dígitos"); con 3 o menos hay 1 de cada 1.000 números
  posibles y en una explotación mediana casi siempre sería ambiguo, así que se rechaza con 400
  ("Crotal demasiado corto: indica al menos los últimos 4 dígitos"). Máximo 12: un crotal español sin
  el prefijo `ES` son exactamente 12 dígitos, así que 12 dígitos sueltos también se tratan como sufijo.
- Cualquier otra forma (letras en medio, letras + menos dígitos…) se trata como completa: igualdad
  exacta; si no coincide → `NO_ENCONTRADO`, sin rechazarla (la gestoría decide).
- **Búsqueda de un incompleto:** Animales **de la Explotación del Trámite y de la Gestoría** cuyo
  `crotal` **termina** en esos dígitos (`AnimalRepository.findByExplotacionIdAndGestoriaIdAndCrotalEndingWith`).
  Desviación consciente de "usar la búsqueda que ya existe": la existente
  (`findByExplotacionIdAndCrotalUltimosDigitos`) compara por igualdad con los **6** últimos
  caracteres y no lleva `gestoriaId`, así que no encontraría un crotal escrito con 4 dígitos. Con 6
  dígitos exactos se puede usar la columna indexada `crotal_ultimos_digitos` (más la condición de
  `gestoriaId`); con otra longitud, el sufijo. El volumen por explotación es pequeño.
- **Resultado (`ResolucionCrotal`):** `EN_INVENTARIO` (un único Animal: se guarda el crotal completo
  y se enlaza `animal_id`), `AMBIGUO` (varios Animales: se guarda tal cual, sin enlace),
  `NO_ENCONTRADO` (ninguno: tal cual, sin enlace), `SIN_EXPLOTACION` (el Trámite no tiene Explotación
  todavía: tal cual, sin enlace).
- Un Animal de otra Explotación o de otra Gestoría **nunca** se enlaza ni se usa para completar.
- `TramiteCrotalService.reemplazarCrotales(tramite, lista)` y `recalcularEnlaces(tramite)` — al
  cambiar de Explotación se re-resuelve cada `crotal_indicado` desde cero.
- Respuesta: `crotales: [{crotalIndicado, crotal, animalId, enInventario, resolucion}]` en listado y
  detalle (`enInventario` = `resolucion == EN_INVENTARIO`, se mantiene porque lo pedía el prompt). En el
  listado, una sola consulta `findByTramiteIdInAndGestoriaId(idsDeLaPagina, gestoriaId)`.

## Task 6: `PATCH /tramites/{id}` + regla de aprobación

**Files:** `TramiteController`, `TramiteRevisionService` (`@Transactional`), `TramitePatchRequest`
`{explotacionId?, tipoTramite?, crotales?}` (null = no cambiar).
**Test:** ampliar `TramiteControllerTest` + E2E `TramiteRevisionEndToEndTest`.

- 404 si el Trámite no es de la Gestoría; **409** `{motivo}` si no está en `PENDIENTE_REVISION`.
- `explotacionId`: `ExplotacionRepository.findByIdAndGestoriaId` — de otra Gestoría → **404** y el
  Trámite no cambia; al cambiarla se recalculan los enlaces de crotales.
- `tipoTramite`: valor del enum actual (inválido → 400 de deserialización de Spring).
- `crotales`: lista completa → reemplaza (Task 5).
- Devuelve `TramiteDetalleResponse`.
- **Aprobar:** tras el gate de suscripción (403, sin cambios) y el 404 existentes, si
  `explotacion == null` o `tipoTramite == null` → **409** `{motivo}` explícito ("Falta asignar la
  explotación", "Falta el tipo de trámite", o ambos). Crotales no obligatorios.
- **Aprobar y rechazar solo desde `PENDIENTE_REVISION`** (decisión 12): desde cualquier otro estado →
  `409` `{motivo}`, sin cambiar nada. Orden de comprobaciones en aprobar: 403 suscripción → 404 →
  409 estado → 409 explotación/tipo.

**E2E obligatorios:** **asignar a un Trámite de A una Explotación de B → 404 y sin cambios**; PATCH
de un Trámite de B → 404; PATCH fuera de `PENDIENTE_REVISION` → 409; crotal que existe en la
Explotación → `enInventario=true`, uno inexistente → `false`; crotal de un Animal de B nunca se
enlaza aunque el texto coincida; aprobar sin explotación/tipo → 409 con motivo; aprobar resuelto → 200;
**aprobar y rechazar desde `APROBADO` (y otro estado no válido) → 409 y el estado no cambia**.

## Task 7a: cambios de código de cierre (decisiones 17, 19, 21, 22, 27–31)

- **Decisión 27 (R1, versión optimista):** migración `V17` (`tramite.version BIGINT NOT NULL DEFAULT
  0`), `@Version` en `Tramite`, `version` en `TramiteResponse` y `TramiteDetalleResponse`. `PATCH` y
  `aprobar` **exigen** la versión que tenía la pantalla (campo `version` en el cuerpo; ausente → 400
  `{motivo}`); si no coincide → 409 `{motivo}` sin cambios. Toda escritura incrementa la versión,
  **incluida la re-resolución de crotales al aprobar** (que solo toca `tramite_crotal`, así que hay que
  forzar el incremento). Test: primer `aprobar` tras cambio de inventario → 409 que guarda la nueva
  resolución e incrementa la versión; un segundo intento con la versión antigua → 409 otra vez; con la
  nueva → reglas normales. `rechazar` no exige versión pero incrementa.
- **Decisión 28 (I2):** para aprobar un crotal `NO_ENCONTRADO` completo: si empieza por `ES`, exactamente
  `ES` + 12 dígitos; con otro código de país, 2 letras + 8 a 12 dígitos. Si no → 409 nombrando el
  crotal. La clasificación de resolución no cambia. Regla provisional (documentar en `CLAUDE.md`).
- **Decisión 29 (M1):** `permitAll` para `/error` en `SecurityConfig`, de modo que un cuerpo mal
  formado dé 400 y no 401 (que cierra la sesión en el frontend). Con test E2E.
- **Decisión 30 (M4):** comportamiento sin cambios; solo docs (Task 7b).
- **Decisión 31 (M-R2):** `LOCK_TIMEOUT` generoso (p.ej. 10000 ms) en la URL H2 de
  `src/test/resources/application.yml`.
- **Decisión 17:** mensaje neutro en el importador para `codigo_rega`/NIF/crotal de otra Gestoría y
  finders con `gestoriaId` explícito en `procesarExplotacion`/`procesarAnimal`.
- **Decisión 22:** `CrotalNormalizador` aplicado a `Animal.crotal` en el importador.
- **Decisión 21:** lista blanca de `sort` en `GET /explotaciones` y `GET /tramites`.

## Task 7b: Revisión final, smoke test y documentación

1. `grep` de `findById(`, `findByCodigoRega(`, `findByNif(`, `findByCrotal(` en todo el código
   nuevo/modificado, y de `findByTelefono(` en todo el backend salvo el webhook (decisión 19).
2. `./mvnw test` completo en verde.
3. **Smoke test HTTP real** (skill `smoke-test-h2`, H2 en fichero con `AUTO_SERVER=TRUE` para poder
   sembrar por SQL): onboarding → login → importar Excel con hoja Contactos (generado para el smoke)
   → `GET /ganaderos/{id}` con contactos y rol → crear Trámite por SQL en `PENDIENTE_REVISION` sin
   explotación/tipo → `POST /aprobar` → 409 → `PATCH` (explotación, tipo, crotales: uno en inventario
   y otro no) → `POST /aprobar` → 200. Matar el `java.exe` al terminar.
4. `CLAUDE.md`: decisiones A(a)/(b), borrado lógico e ignorar inactivos en el webhook de 3b, rol en la
   relación, quitar la frase de "trabajador → exactamente una Explotación", endpoints nuevos, regla de
   aprobación, `tramite_crotal`, conteo de tests. `progress.md`: entrada de A1.
5. **Sin commit.** Resumen + diff + propuesta de mensaje para Antonio.

## Fuera de alcance (no se toca)

Frontend; IA/Twilio/Stripe; catálogo `TipoTramite`; los dos bugs de Stripe pendientes.
