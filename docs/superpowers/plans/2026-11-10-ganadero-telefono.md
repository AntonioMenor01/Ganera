# Teléfono en la ficha del ganadero (opción B, primer campo) — plan

Fecha: 2026-11-10. Base: `main` = `origin/main` = `204baf0`. Rama: `feat/ganadero-telefono`.
Suites de partida: backend 1338 (1337 + 1 skip, JDK 21), frontend 1117 (54 ficheros).

**Estado: brief aprobado por Antonio el 2026-11-10 con las decisiones de abajo, que prevalecen sobre el resto del plan.**

## Decisiones de Antonio (2026-11-10) — prevalecen

- **D1 — Opcional.** `ganadero.telefono` nullable; en blanco o ausente = `null`.
- **D2 — Sin `UNIQUE` ni índice**, ni global ni por gestoría. Ningún finder por teléfono en `GanaderoRepository`.
- **D3 — Edición con `PUT /ganaderos/{id}/telefono` `{telefono}`**; solo toca ese campo (`null`/blanco lo borra).
  Nombre y NIF siguen sin editarse.
- **D4 — Gana la última escritura** (sin `version` en `Ganadero`).
- **D5 — `GANADERO_CREADO` sin cambios** (no deja constancia de si se informó teléfono).
- **D6 — Sin Impeccable:** patrones visuales existentes; capturas a Antonio al final.
- **D7 — Texto de ayuda bajo el campo, literal:** «Para que Ganera reconozca sus WhatsApp, añade este número como
  contacto de una explotación».
- **D8 — Migración `V27`.** Álvaro lo confirma Antonio. **Antes del `git add`, recordar a Antonio que confirme el
  número de migración.**
- **D9 — Sin teléfono en listados ni buscador** (`GET /ganaderos`, `GET /buscar`, detalle de trámite). Por eso la
  respuesta del `POST /ganaderos` (`GanaderoResumenResponse`) tampoco lo lleva.
- **D10 — Superpowers:** T1 backend, T2 frontend, un implementador + un revisor independiente por tarea.

### Ajustes

- **A1 — El `PUT` deja solo `GANADERO_MODIFICADO`**, aunque su respuesta incluya el teléfono: ninguna fila
  `LECTURA_GANADERO` adicional. Con test que lo compruebe (recuento exacto de filas de auditoría tras el PUT).
- **A2 — `CredencialOvzCifradaTipoTest`:** primero enseñarlo en rojo (con el campo añadido y la lista sin tocar),
  después ampliar la lista con `setTelefono` y añadir el test de comportamiento con centinela.
- **A3 — Mutaciones mínimas (copia previa con `cp`, restauración con `cp` + `cmp`, nunca git):**
  1. `findByIdAndGestoriaId` → `findById` en el PUT → rojo.
  2. Quitar el `registrar` de `GANADERO_MODIFICADO` → rojo (test o guardia DML).
  3. Meter el teléfono en `despues` → no compila.
- **A4 — Aviso de Surefire** («kill self fork JVM»): comprobado, ver «Notas» al final.
- **A5 — Smoke:** local con H2 (como M2). En producción, sobre el ganadero de prueba **11** de la gestoría de
  pruebas, con el número ficticio `+34600000000`: PUT → `200`, el detalle muestra el número, el PUT desde otra
  gestoría → `404`. Los `curl` los ejecuta la sesión principal leyendo un `.env` que crea Antonio.
- **A6 — Borrado explícito (aprobación del plan, 2026-11-10; prevalece sobre el «Diseño»):** el teléfono solo se
  borra con `{"telefono": null}` o `{"telefono": ""}` (o solo espacios) → `200`. **Sin cuerpo o con `{}` (sin la
  clave `telefono`) → `400 {motivo}`, sin cambios y sin fila.** El frontend («Quitar teléfono») envía `null`
  explícito.
- **A7 — Stage:** el plan entra en el commit; `.superpowers/sdd/` y las capturas, no.
- **A8 — Smoke de producción:** después del merge, solo gestoría de pruebas, ganadero 11, `+34600000000`, que **se
  queda puesto**. **Sin caso entre gestorías en producción** (lo cubren el E2E y el smoke local).
- **A9 — Smoke local:** además, ni el log de la app ni las respuestas de los listados (`GET /ganaderos`,
  `GET /buscar`) contienen el número.
- **A10 — Surefire:** vigilarlo; si persiste, anotarlo en `CLAUDE.md` como ruido conocido.
- Sin commits ni push sin aprobación de Antonio.

## Diseño

### Modelo y migración

- `Ganadero.telefono` (`@Column(name = "telefono", length = 30)`, nullable). El `@Setter` de clase genera
  `setTelefono(String)`.
- `V27__add_telefono_to_ganadero.sql` en `db/migration` (común H2/PostgreSQL; V26 vive en `db/postgresql` y la
  numeración es global): `ALTER TABLE ganadero ADD COLUMN telefono VARCHAR(30);`. Sin backfill.
- Normalización con `contacto.TelefonoNormalizador` (E.164, mismas reglas que Contactos). Se guarda siempre
  normalizado.

### Endpoints

- **`POST /ganaderos`** `{nombre, nif, telefono?}`. Orden: `400` nombre → `400` NIF → **`400` teléfono inválido**
  (`{motivo}` «El teléfono no es válido. Revísalo e inténtalo de nuevo.») → `409` NIF → `201`
  (`GanaderoResumenResponse`, sin teléfono). Un teléfono no vacío que no normaliza es `400`; `null`/blanco se acepta.
  Auditoría: la fila `GANADERO_CREADO` de siempre (D5).
- **`GET /ganaderos/{id}`**: `GanaderoDetalleResponse` gana `telefono` (nullable). Sigue dejando `LECTURA_GANADERO`.
- **`PUT /ganaderos/{id}/telefono`** `{telefono}` (cuerpo opcional para Spring y leído como JSON en bruto, para
  distinguir la clave ausente de `null`; A6: `null` o en blanco = borrar; sin cuerpo, `{}`, o un valor que no sea
  texto ni `null` = `400`). Orden: `400 {motivo}` sin la clave `telefono` / teléfono no vacío e inválido (en el controlador, antes de tocar la BD:
  misma respuesta para propio, ajeno o inexistente) → `404` sin cuerpo (ajeno o inexistente; si existe en otra
  gestoría, fila `ACCESO_OTRA_GESTORIA` con `OperacionAuditada.EDITAR_GANADERO`, nueva) → `200` con
  `GanaderoDetalleResponse`.
  - Servicio nuevo `GanaderoEdicionService.cambiarTelefono(gestoriaId, usuarioId, ganaderoId, telefonoNormalizado)`,
    `@Transactional`, carga con `findByIdAndGestoriaId`, `saveAndFlush`, y `auditoriaService.registrar` (MANDATORY)
    de `GANADERO_MODIFICADO`: actor USUARIO del JWT, `EntidadAuditada.GANADERO`, `antes = null`,
    `despues = {origen: MANUAL, campos_cambiados: [...]}` con el enum nuevo `CampoGanadero { TELEFONO }`. Siempre
    deja fila; la lista va vacía si el valor no cambia (igual que `CONTACTO_MODIFICADO`).
  - El detalle de la respuesta se construye reutilizando `GanaderoConsultaService` (sin `LECTURA_GANADERO`, A1).
  - `gestoriaId` y `usuarioId` solo del JWT. El valor del teléfono nunca va a auditoría ni a logs.
- **No se toca:** importador Excel (no escribe el teléfono; reimportar no lo borra), `whatsapp/*`,
  `MensajeEntranteService`, `ContactoRepository`, `Contacto`, listados y buscador.

### Frontend

- `types.ts`: `GanaderoDetalle.telefono: string | null`. `api.ts`: `crearGanadero(nombre, nif, telefono?)` y
  `cambiarTelefonoGanadero(id, telefono | null): Promise<GanaderoDetalle>`.
- `AltaGanaderoDialog`: campo «Teléfono (opcional)» (`type="tel"`, `inputMode="tel"`, `autoComplete="off"`) con el
  texto de ayuda de D7 enlazado por `aria-describedby`; el `400` de teléfono marca ese campo (`aria-invalid`).
- `GanaderoDetallePage`: en la cabecera de la ficha, el teléfono con `formatearTelefono`/`hrefTelefono` (enlace
  `tel:`, `CLASE_ENLACE`) o «Sin teléfono», y un botón «Añadir teléfono» / «Cambiar teléfono» que abre una edición en
  línea (Input + ayuda D7 + «Guardar» / «Cancelar», y «Quitar teléfono» si lo hay). Patrones existentes: `Input`,
  `Button` outline/sm, `Alert` destructivo con `CircleAlertIcon`. Una petición a la vez; `400` conserva lo escrito y
  muestra el `motivo`; `404` → «Ganadero no encontrado»; red/5xx → mensaje con lo escrito intacto. Tras el `200` la
  ficha se pinta con la respuesta del PUT (sin otra petición) y el foco vuelve al botón.

## Tareas (TDD: cada test se ve en rojo antes de implementar)

### T1 — Backend

1. **Guardia de tipo en rojo (A2).** Añadir solo el campo `telefono` a `Ganadero` + `V27`. Ejecutar
   `CredencialOvzCifradaTipoTest` → **rojo** en `losUnicosMetodosDeGanaderoQueRecibenUnStringSonLosSettersConocidos`
   (guardar la salida para Antonio). Después: ampliar la lista con `setTelefono` y añadir el test de comportamiento:
   para **cada** setter `String` de la lista, invocarlo por reflexión con un centinela sobre (a) un Ganadero sin
   credencial y (b) uno con credencial puesta con `CifradorAesGcm.cifrar`; `getOvzPasswordCifrada()` no cambia y
   nunca contiene el centinela. Verde.
2. **Migración.** Tests que migran «hasta la última» pasan a V27 (`FlywayPostgresTest`, `PerfilCleverPostgresTest`
   y los que cuenten versiones, H2 incluido); `ddl-auto=validate` en PostgreSQL embebido valida la columna.
3. **Alta con teléfono** (`GanaderoAltaEndToEndTest`, rojo → verde): sin teléfono sigue igual; con teléfono escrito a
   mano (`612 345 678`) se guarda `+34612345678`; inválido → `400` con el motivo y **nada creado, ninguna fila de
   auditoría**; orden de los `400`; la respuesta `201` no trae `telefono`; `GANADERO_CREADO` idéntico (D5).
4. **Detalle** (`GanaderoEndToEndTest`): `telefono` presente y `null` cuando no hay. `GET /ganaderos` y `GET /buscar`
   **no** lo llevan (aserción explícita de ausencia del campo y del valor en el cuerpo, D9).
5. **PUT** (test E2E nuevo `GanaderoTelefonoEndToEndTest`, dos gestorías, guardia DML activa):
   - poner, cambiar y borrar (`{"telefono": null}` y `{"telefono": ""}`) → `200` con el detalle; persistido
     normalizado; **sin cuerpo y `{}` → `400 {motivo}`, sin cambios y sin fila** (A6);
   - inválido → `400 {motivo}`, sin cambios, sin fila; mismo `400` para id ajeno e inexistente;
   - inexistente → `404` sin cuerpo, sin fila; ajeno → `404` sin cuerpo, fila de A intacta, una fila
     `ACCESO_OTRA_GESTORIA` (`operacion: EDITAR_GANADERO`) en la gestoría del actor, ninguna en A;
   - auditoría: **exactamente una** fila nueva tras el PUT, `GANADERO_MODIFICADO`, con
     `despues = {"campos_cambiados":["TELEFONO"],"origen":"MANUAL"}` y `antes` nulo; lista vacía si el valor no
     cambia; **cero `LECTURA_GANADERO`** (A1); el JSON de la fila no contiene el teléfono;
   - sin JWT → `401`.
6. **Inventario cerrado:** `InventarioAuditoriaTest` en rojo por la ruta nueva → declarar
   `PUT /ganaderos/{id}/telefono` como auditada por `ganadero.GanaderoTelefonoEndToEndTest`. `AccesoAjenoTest` si
   enumera las operaciones.
7. **No exposición:** `GanaderoSinExposicionCredencialTest` sigue verde con el PUT (la respuesta nunca trae
   `ovzUsuario`/`ovzPasswordCifrada`); se añade el PUT a sus rutas si las enumera.
8. **Mutaciones (A3)**, cada una con copia previa y restauración `cp` + `cmp`:
   1. `findById` en el PUT → rojo en el caso ajeno de `GanaderoTelefonoEndToEndTest`.
   2. Sin `registrar` → rojo (aserción de la fila y/o guardia DML).
   3. `DatosAuditoria.con(Clave.…, ganadero.getTelefono())` → error de compilación (no hay sobrecarga `String`).
9. `./mvnw clean test` completo (JDK 21). Nunca dos builds de Maven a la vez.

Revisor independiente de T1: bloqueante cualquier `findById` a secas, cualquier vía que saque el teléfono a
auditoría/logs/listados, y cualquier cambio en `whatsapp/*` o `contacto/*`.

### T2 — Frontend

1. Handlers MSW (`POST /ganaderos` con teléfono, `PUT /ganaderos/:id/telefono`) y tipos.
2. `AltaGanaderoDialog` (tests primero): el campo y la ayuda D7 literal; se envía `telefono` solo si se escribió;
   `400` de teléfono marca el campo y conserva lo escrito; el alta sin teléfono no cambia.
3. `GanaderoDetallePage` (tests primero): con teléfono → enlace `tel:` con el número agrupado; sin teléfono → «Sin
   teléfono» + «Añadir teléfono»; editar → PUT con el valor; quitar → PUT con `null`; `400`/`404`/red; foco; una
   petición a la vez; el detalle no vuelve a pedirse tras el `200`.
4. `GanaderosPage` y buscador: sin cambios (test de que la fila no enseña teléfono, si no lo hay ya).
5. `npm test`, `npm run build`, `npm run lint` (solo los avisos conocidos).

Revisor independiente de T2.

### T3 — Smoke y capturas (sesión principal)

1. **Local, H2 en fichero** (skill `smoke-test-h2`), dos gestorías inventadas: alta con y sin teléfono, PUT
   poner/cambiar/borrar, `400`, `404` desde la otra gestoría, filas de auditoría por SQL (una `GANADERO_MODIFICADO`
   por PUT, ninguna `LECTURA_GANADERO` del PUT, sin el número en `antes`/`despues`).
2. **Navegador real** (Playwright fuera del repo) a 1440 y 375 px: diálogo de alta y ficha (sin teléfono, editando,
   con teléfono, error). Capturas fuera del repo en `C:\Users\Antonio\Desktop\capturas-ganera-telefono\` (D6).
3. **Producción (A5)** — solo **después** del despliegue (por tanto, después del commit y el merge aprobados):
   ganadero 11 de la gestoría de pruebas, `+34600000000`; PUT → `200`; GET detalle muestra el número; PUT con el
   JWT de otra gestoría → `404`. `.env` de Antonio, nunca impreso. A tener en cuenta: deja filas de auditoría
   **inmutables** en producción (`GANADERO_MODIFICADO`, `LECTURA_GANADERO` del GET y `ACCESO_OTRA_GESTORIA` en la
   segunda gestoría) y el número ficticio queda puesto salvo que Antonio pida quitarlo con otro PUT (otra fila).

### Cierre

- `CLAUDE.md` (Domain model, regla «Manual alta de Ganadero», bloque Auditoría, recuentos de tests), `progress.md`,
  `DESIGN.md` si la edición en línea añade un patrón.
- **Antes del `git add`: recordar a Antonio que confirme `V27`** (D8). Stage ruta a ruta por la sesión principal;
  commit y push solo con su aprobación.

## Notas

- **Surefire «kill self fork JVM» (A4):** salió en el Paso 0, que se ejecutó sobre `204baf0` sin ningún cambio (la
  rama recién creada, árbol limpio): **no lo introduce esta tarea**. Aparece al final, con todos los tests ya
  ejecutados y contados: el fork tarda más de 30 s en terminar tras `System.exit(0)` porque los *shutdown hooks*
  cierran los contextos de Spring en caché (54 pools Hikari cerrados en ese log) y el PostgreSQL embebido. No cambia
  el resultado (BUILD SUCCESS, 1338/0/0/1). Se vigila si se repite en las ejecuciones de esta tarea.
- **Costumbre del repo con los planes:** `docs/superpowers/plans/` **sí se versiona** (23 planes en git); lo que no
  se sube son los informes de `.superpowers/sdd/`. Este plan entraría en el commit salvo que Antonio diga lo
  contrario.
- Conocido y aceptado: un mensaje de WhatsApp desde el teléfono de la ficha, si no es además un Contacto, sigue
  siendo `NUMERO_DESCONOCIDO` (D7 lo avisa en la interfaz). El teléfono se guarda en claro y sin caducidad, como
  `contacto.telefono`: pregunta para la abogada.
