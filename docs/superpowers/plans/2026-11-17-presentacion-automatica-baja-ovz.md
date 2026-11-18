# Presentación automática de la baja por muerte en OVZ (escalón 2) — plan

Escrito para: Antonio y quien implemente el escalón 2. **Solo plan, sin código de producción.** Se apoya en el
mapa real (`docs/referencias/mapa-ovz.md`) y en el spike técnico (`docs/spikes/2026-11-17-spike-ovz-presentacion-baja.md`).

## 0. Cambio de enfoque (importante)

Nuevo requisito de Antonio: **tras aprobar el gestor en Ganera, Ganera presenta el trámite en OVZ por sí sola,
desde el servidor, con las credenciales del ganadero (cifradas, M2), pulsando ella el «Aceptar» real.** La
**aprobación en Ganera es la revisión humana**: nada llega a OVZ sin ella. Después, Ganera **descarga los
documentos** que genera OVZ y los guarda en la ficha del ganadero.

Esto **sustituye**, para la baja, el enfoque anterior de la extensión de Chrome (donde el gestor pulsaba
«Aceptar»). Aquel trabajo (plan `2026-11-17-modo-asistido-baja-muerte.md` y la rama `feat/modo-asistido-baja`,
con el endpoint T1 y la lógica de extensión T2) **queda aparcado, no borrado**; aquí abajo se decide qué se
reutiliza. **Implica revisar una regla no-negociable del CLAUDE.md** (hoy: «nada se envía sin el clic del gestor
en el formulario que Ganera rellenó»): la nueva lectura es que **el clic humano es la Aprobación en Ganera**, y
el «Aceptar» de OVZ lo da Ganera automáticamente solo sobre un trámite ya aprobado. Esa reinterpretación se
anota en CLAUDE.md con este plan y se cierra al implementarlo.

## 1. Flujo

1. El gestor revisa y **aprueba** el trámite (`APROBADO`), igual que hoy. Esa es la puerta humana.
2. Al aprobar un `BAJA_MUERTE`, se **encola un trabajo de presentación** (no se presenta en la misma petición
   HTTP; nunca dentro de la transacción de aprobación ni con el lock de la fila).
3. Un **worker de OVZ** toma el trabajo (candado por ganadero, una sesión OVZ a la vez), descifra la contraseña
   del ganadero en el momento (M2), abre OVZnet, inicia sesión, navega **solo por menús** hasta la baja, escribe
   el crotal, pulsa «Buscar».
4. **Verificación antes de «Aceptar»** (bloqueante): el Código RIIA coincide con el crotal del trámite y la
   «Explotación de ubicación» coincide con el REGA del trámite. Si no cuadra → **no presenta**, `ERROR_OVZ` con
   motivo, aviso al gestor.
5. Si cuadra, Ganera pulsa **«Aceptar»** (presenta la baja). `EJECUTADO_OVZ`.
6. Ganera **descarga el justificante** (Documentos › Documentos, tipo «Baja Animal») y lo **guarda en la ficha
   del ganadero**, enlazado al trámite y la explotación.
7. El gestor ve en Ganera el estado (`EJECUTADO_OVZ`) y el justificante descargable desde la ficha.

## 2. Pantalla de credenciales OVZ por ganadero

- Se **habilita** la pantalla de alta de credenciales OVZ (hoy deshabilitada en `ConfiguracionPage` → «OVZ.net
  (Cañada)»), **por ganadero**: usuario + contraseña. La contraseña se cifra con `CifradorAesGcm.cifrar()`
  (devuelve `CredencialOvzCifrada`, único tipo que acepta `Ganadero.setOvzPasswordCifrada`; `@JsonIgnore`, nunca
  sale en respuestas ni en auditoría ni en logs). El `ovzUsuario` tampoco se expone nunca.
- Guardar/editar credenciales deja su fila de auditoría (`CREDENCIAL_OVZ_GUARDADA` / `CREDENCIAL_OVZ_MODIFICADA`,
  actor USUARIO, `EntidadAuditada.GANADERO`, **sin el valor**).
- El **descifrado** solo ocurre en el worker, vía `DescifradoCredencialOvz.descifrarPasswordParaEnvioOvz(...)`
  (único descifrador, M2), que aísla por gestoría y deja `CREDENCIAL_OVZ_DESCIFRADA` **antes** de descifrar.

## 3. Cola de trabajos con candado por ganadero

- Tabla `trabajo_ovz` (`GestoriaScopedEntity`): trámite, ganadero, tipo (de momento `BAJA_MUERTE`), estado del
  trabajo, intentos, instantes, `clave_idempotencia` (un trabajo por trámite; reencolar no duplica), y referencia
  al documento descargado.
- **Candado por ganadero** (una sesión OVZ por cuenta a la vez): el worker no procesa dos trabajos del mismo
  ganadero en paralelo. Entre ganaderos distintos puede serializar o limitar según la infra (una instancia del
  worker; ver Decisiones).
- El worker corre **fuera de cualquier petición HTTP** (como `SuscripcionSyncScheduler`): cross-tenant por
  diseño, pero cada consulta por trámite/ganadero lleva su `gestoriaId` explícito. **Nunca** abre la sesión OVZ
  dentro de la transacción que bloquea la fila del trámite.

## 4. Estados

- Se **usan** los reservados de `EstadoTramite`: `EN_PROCESO` (trabajo en curso), `EJECUTADO_OVZ` (baja
  presentada + «Aceptar» dado por Ganera), `ERROR_OVZ` (falló; con motivo y aviso al gestor).
- `HECHO_EN_OVZ` (marca manual del escalón 1) **se mantiene** para el camino manual; `EJECUTADO_OVZ` es el
  automático (su etiqueta «ejecutado» ahora es cierta: lo hizo Ganera). **Convivencia (D4):** se usa el
  automático **cuando hay credenciales guardadas**; si no las hay, cae al **manual** (`HECHO_EN_OVZ`).
- **Dos clases de `ERROR_OVZ` (D11):** **seguro** (falló **antes** de «Aceptar»: no se presentó nada) e
  **incierto** (falló **después** de «Aceptar» y la consulta en Documentos no lo resolvió). Se guardan
  distinguibles (p. ej. un sub-motivo/flag en el trabajo), porque gobiernan el reencolado (§6, D11).
- Transiciones: `APROBADO` → `EN_PROCESO` → (`EJECUTADO_OVZ` | `ERROR_OVZ` seguro | `ERROR_OVZ` incierto).
  **Nunca** se reintenta una presentación automáticamente; el reencolado manual depende de la clase de error
  (§6).

## 5. Verificación antes de «Aceptar»

Bloqueante y en el worker: **crotal** (Código RIIA leído == crotal del trámite) y **explotación** (REGA de
«Explotación de ubicación» == REGA del trámite). Si cualquiera falla, **no se pulsa «Aceptar»**, el trabajo
termina en `ERROR_OVZ` con motivo específico, y se avisa al gestor. (La lógica de verificación es la misma idea
que la T2 de la extensión, reutilizable como función pura.)

## 6. Anti-duplicado (crítico)

- Ganera **nunca reintenta una presentación** ante una respuesta incierta (caída/timeout justo tras «Aceptar»).
- Ante incertidumbre, **consulta en Documentos › Documentos** (tipo «Baja Animal», por crotal/fecha) si la baja
  ya aparece: si está → `EJECUTADO_OVZ` (y descarga el justificante); si **no se sabe** con certeza →
  `ERROR_OVZ` **incierto** con aviso al gestor para que lo compruebe a mano. Nunca un segundo «Aceptar».
- **Reencolado (D11):** desde `ERROR_OVZ` **seguro** (falló antes de «Aceptar»), el gestor **corrige y vuelve a
  aprobar** y se reencola. Desde `ERROR_OVZ` **incierto** (falló tras «Aceptar»), **solo** se reencola si el
  gestor **confirma explícitamente** que ha comprobado en OVZ que la baja **no** se presentó; esa confirmación
  **queda auditada** (actor USUARIO) y es la única vía. Sin ella, no se reencola nunca.
- La clave de idempotencia del trabajo (por trámite) evita que reencolar el mismo trámite genere dos
  presentaciones.

## 7. Caducidad de sesión y cuentas con certificado

- **Caducidad (20 min):** la presentación automática es cuestión de segundos (no hay espera humana en el lazo),
  así que la caducidad apenas aplica; aun así, si aparece `/AvisoSesion.aspx` o el login **antes** de «Aceptar»,
  el worker reintenta **la navegación** desde el login (no la presentación); si pasa **después** de «Aceptar», es
  el caso incierto del punto 6 (consultar Documentos, nunca repetir).
- **Cuentas con certificado digital: NO soportadas** en automático. Si la cuenta del ganadero usa certificado
  (no hay usuario/contraseña válidos, o la entrada exige «Entrar con Certificado»), el trabajo termina con un
  `ERROR_OVZ` de motivo claro («esta cuenta usa certificado digital; la baja se hace a mano en OVZ») y el gestor
  usa el camino manual.

## 8. Auditoría e inventario

- Nuevas `AccionAuditoria` (por enum, sin migración de `CHECK`): presentación (`OVZ_BAJA_PRESENTADA`), error
  (`OVZ_BAJA_ERROR`), credenciales (`CREDENCIAL_OVZ_GUARDADA`/`_MODIFICADA`), documento guardado
  (`DOCUMENTO_OVZ_GUARDADO`). Todas **sin datos personales** en `antes`/`despues` (`DatosAuditoria` no admite
  `String`): ni crotal, ni contraseña, ni nombre de fichero con datos.
- `CREDENCIAL_OVZ_DESCIFRADA` ya existe (M2).
- Toda ruta de escritura nueva se **declara en el inventario cerrado** (`InventarioAuditoriaTest`) y respeta la
  **guardia DML**. Los `@Scheduled`/worker se declaran como los demás planificadores.
- **Pendientes de M2 que este escalón cierra:** mapear `CredencialOvzDeOtraGestoriaException` y
  `DescifradoCredencialOvzException` a un error controlado; **descifrar fuera de la transacción** del envío (es
  `@Transactional`: capturarlo dentro marca rollback-only); y un test que vigile que `DescifradoCredencialOvz`
  siga siendo el único que descifra.

## 9. Documentos (primer alcance)

- **Descargar el justificante** de la baja: Documentos › Documentos (`/Explotaciones/wgDocumentos.aspx`, tabla
  `GVDocumentos`, columnas Documento·Tipo·Fecha Creacion, «Tipo Trámite: Baja Animal»; el enlace de la columna
  «Documento» descarga el fichero). **Formato confirmado por el mini-spike (2026-11-18): PDF, ~45 KB.** Hay
  **paginación** (13 filas por página).
- **Identificar el justificante de UNA baja (la tabla no trae crotal):** tipo «Baja Animal» + **Fecha Creación
  posterior al «Aceptar»** (más reciente primero, recorriendo la paginación) + **verificación del crotal del
  trámite dentro del PDF** (extracción de texto, p. ej. **PDFBox**; **nunca** imprimir el contenido). Si no
  aparece ninguno o hay **varios candidatos** → el documento queda **«pendiente»** y **no se adjunta** (nunca se
  adivina). **El mismo método es el de la detección «ya presentada» del anti-duplicado (D5, §6).**
- **Guardarlo en la ficha del ganadero**, enlazado al **trámite** y la **explotación**, **aislado por gestoría**
  y **auditado**. Nueva entidad `documento_ovz` (`GestoriaScopedEntity`): ganadero, trámite, explotación, tipo
  («Baja Animal»), nombre, tamaño, content-type, SHA-256, instante.
- **Dónde se almacena (D6 revisada, 2026-11-18; implementado en T3):** el binario va **en Postgres**, no en
  Supabase Storage. Dos tablas: `documento_ovz` (metadatos) y `documento_ovz_contenido` (`BYTEA`, 1:1 con la misma
  PK; separada para que el listado de la ficha no cargue el binario). **Solo PDF** (cabecera `%PDF`) y **máximo 10
  MB** (lo valida `ValidadorPdf` antes de escribir y lo refuerza un `CHECK`). La **descarga desde la ficha** la
  sirve el backend tras comprobar la gestoría (`GET /documentos-ovz/{id}/contenido`, adjunto; nunca una URL pública
  directa); el listado es `GET /ganaderos/{id}/documentos` (metadatos). Un recurso ajeno → `404` sin cuerpo +
  `ACCESO_OTRA_GESTORIA`; la descarga deja `LECTURA_DOCUMENTO_OVZ`. El guardado (lo usará el worker, T4) deja
  `OVZ_DOCUMENTO_GUARDADO` **sin el contenido** (solo el tamaño). Interruptor maestro
  `ganera.ovz.presentacion-automatica.activa` (false por defecto): apagado → no se encola al aprobar.
  *(Nota: Supabase Storage / Clever Cellar queda descartado para este alcance; el justificante es pequeño y cabe
  en la BD con el resto del aislamiento multi-tenant ya resuelto.)*
- **Si el justificante aún no está disponible** tras «Aceptar»: se **reintenta la descarga** (no la
  presentación) con una pauta acotada; si tras N intentos no está, el trámite se queda `EJECUTADO_OVZ` con el
  **documento marcado como pendiente** (la presentación fue bien; falta el papel), y se reintenta más tarde.
  **Nunca** se repite la presentación por un documento que no aparece.

## 10. Qué se reutiliza de la T1 (y T2) ya hechas

- **Se reutiliza** la **lógica de ensamblado de datos de relleno** que introdujo la T1 (`DatosBajaOvzResponse`:
  crotal, fecha `dd/MM/yyyy`, MER, REGA de ubicación) como **fuente interna** de lo que el worker escribe y
  verifica, y `PlazoBajaOvz` para el aviso de fuera de plazo. El **enum de auditoría** (`OVZ_BAJA_PREPARADA`) se
  reaprovecha o se complementa con las acciones de presentación/documento.
- **Se aparca** el **endpoint HTTP** `GET /tramites/{id}/ovz/baja-muerte` (pensado para que una extensión
  externa leyera los datos): en el modelo servidor-a-servidor el worker lee internamente, no por HTTP. Queda en
  la rama `feat/modo-asistido-baja` por si se retoma; decidir si se mantiene, se reusa como lectura interna o se
  retira (Decisión).
- **Se aparca** toda la carpeta **`extension/`** (T2): no participa en el camino automático. Se conserva sin
  borrar.

## 11. Decisiones (cerradas por Antonio el 2026-11-17)

D1–D10 se cierran tal como se recomendaban; D11 con el matiz de Antonio (dos clases de `ERROR_OVZ`).

> **Resultado del spike (cerrado con éxito).** Local: PASO 1–9 OK (login, explotación bovina, menú → baja,
> crotal + «Buscar», Código RIIA coincide, explotación leída, «Salir» sin presentar). Clever: la prueba de IP
> (endpoint temporal #38, retirado en #39) devolvió `{"codigoHttp":200}` → **OVZnet responde a la IP de Clever
> (París)**, sin bloqueo ni geofiltrado. Queda por confirmar en la implementación de D1 que **Chromium arranca
> dentro del contenedor Docker** del worker (la imagen oficial de Playwright trae las dependencias del sistema).

- **D1 — Infra del worker.** **Worker de OVZ en una instancia Docker aparte** (imagen oficial de Playwright,
  con Chromium y dependencias), no Playwright en la instancia XS Java del backend. Previo: confirmar con el
  spike que Chromium arranca en Clever y que OVZ responde a la IP de Clever. **OVZ ya respondió `200` a la IP de
  Clever (spike cerrado);** falta solo comprobar el arranque de Chromium en el contenedor al montar el worker.
- **D2 — Lenguaje/stack del worker.** **Playwright Java** en el contenedor (reaprovecha el modelo de dominio y
  la dependencia ya presente), no un worker Node.
- **D3 — Candado y concurrencia.** Candado **por ganadero**, y para empezar **un solo trabajo global a la vez**
  (serializado), por el detector de «varias instancias» de OVZ y la IP compartida. Subir a N por ganadero solo
  si se confirma que son sesiones/IPs realmente independientes.
- **D4 — Convivencia de caminos.** **Automático cuando hay credenciales guardadas**; si no las hay, cae al
  **manual** (`HECHO_EN_OVZ`). No se fuerza el automático; la ausencia de credenciales no bloquea al gestor.
- **D5 — Detección de «ya presentada»** en Documentos. Casar por **crotal + tipo «Baja Animal» + fecha**
  (ventana estrecha alrededor de la muerte/presentación). Match inequívoco → `EJECUTADO_OVZ`; cualquier duda →
  `ERROR_OVZ` incierto con aviso. Nunca asumir que se presentó.
- **D6 — Almacén de documentos. [REVISADA 2026-11-18 → Postgres; implementada en T3.]** El binario del justificante
  se guarda **en Postgres** (`documento_ovz` + `documento_ovz_contenido` `BYTEA`, 1:1), **no** en Supabase Storage.
  **Solo PDF** (cabecera `%PDF`), **máximo 10 MB**. Descarga servida por el backend tras comprobar la gestoría
  (`GET /documentos-ovz/{id}/contenido`, adjunto; sin URL pública). Se descarta Supabase Storage/Clever Cellar para
  este alcance: el justificante es pequeño y así el aislamiento multi-tenant es el mismo que el del resto del
  esquema (nada de signed URLs ni un almacén externo que asegurar aparte).
- **D7 — Reintento de la *descarga*** del justificante. Reintentos **acotados de solo la descarga** (p. ej.
  +1 min / +10 min / +1 h, máx 3–4); si no aparece, el trámite se queda `EJECUTADO_OVZ` con el **documento
  marcado como pendiente** y se reintenta más tarde. **Nunca** se reintenta la presentación por un documento
  que no aparece.
- **D8 — Detección de cuenta con certificado.** **Flag en el ganadero** (marcado al dar de alta las
  credenciales: «usa certificado») **+** detección defensiva en la pantalla de entrada. Si se detecta,
  `ERROR_OVZ` con motivo claro y camino manual; no soportado en automático.
- **D9 — Selectores reales** (login, Código RIIA, explotación, descarga en `GVDocumentos`). Confirmarlos con el
  **spike y la cuenta de Antonio antes de codificar**; localizar por **sufijo de id + texto**, nunca id
  absoluto; fallar con claridad si falta un selector.
- **D10 — Regla no-negociable del CLAUDE.md.** Redacción nueva: «el **clic humano es la Aprobación en Ganera**;
  Ganera presenta en OVZ **solo un trámite ya aprobado**, con **verificación previa** (crotal + explotación) y
  **sin reintento ante incertidumbre**». Se cierra la redacción al implementar y se reconcilia con los textos de
  honestidad del frontend.
- **D11 — Idempotencia y reencolado (con el matiz de Antonio).** Clave **por trámite** (reencolar no duplica).
  El reencolado depende de **qué clase de `ERROR_OVZ`** es (ver §4 y §6):
  - **`ERROR_OVZ` seguro** — falló **antes** de «Aceptar» (verificación que no cuadra, login, navegación,
    certificado…): no se presentó nada, así que **reencolar está permitido** tras **corregir y volver a
    aprobar** el trámite.
  - **`ERROR_OVZ` incierto** — falló **después** de «Aceptar» (caída/timeout tras pulsarlo) y la consulta en
    Documentos **no** lo resolvió: **reencolar solo si el gestor confirma explícitamente** que ha comprobado en
    OVZ que la baja **no** se presentó; esa confirmación **queda auditada** (actor USUARIO) y es la única vía de
    reintento. Sin esa confirmación, **nunca** se reencola (riesgo de doble presentación).
  - **`EJECUTADO_OVZ`** nunca se reencola.

## 12. Riesgos

- **Doble presentación**: el mayor riesgo. Mitigado por no reintentar nunca ante incertidumbre + consulta en
  Documentos + clave de idempotencia del trabajo (§6).
- **IP de Clever bloqueada por OVZ**: comprobar con el spike (curl a login) antes de montar el worker.
- **Fragilidad del DOM de OVZ**: localizar por sufijo de id + texto; fallar con claridad; confirmar selectores
  con el spike.
- **Credenciales**: solo se descifran en el worker, en el momento, vía el único descifrador (M2); nunca en logs,
  auditoría ni respuestas.
- **Aislamiento multi-tenant**: todas las consultas del worker con `gestoriaId` explícito; documentos y trabajos
  `GestoriaScopedEntity`; descarga del justificante servida por el backend tras comprobar la gestoría.

## 13. Desglose de tareas (T0–T6)

Orden aprobado por Antonio: **T0 → mini-spike de Documentos (hecho, §9) → T1 → T2 → T3 → T4 → T5 → T6**.
Forma de trabajo: tareas pequeñas/medianas en la sesión principal con **TDD + solo el revisor independiente**
(con `-Dtest` dirigido); **implementador subagente solo en T4**; suite completa una vez al final de cada tarea;
stage ruta a ruta; commits locales, sin push ni PR hasta el OK de Antonio. **Antes de crear cada migración:
`git fetch` y confirmar que el número (V32/V33/V34) sigue libre.**

**Ajuste previo — perfil del worker (hecho, commit `0ccda44`).** Perfil `worker` con
`spring.main.web-application-type=none` (ningún endpoint), planificador del worker encendido y los del backend
apagados; en el backend el worker está desactivado. **No es una T numerada**: es infraestructura del worker que
se consume en T4 (en Clever el worker arranca con `SPRING_PROFILES_ACTIVE=clever,worker`).

**T0 — Pendientes de M2 (endurecimiento del descifrado). [HECHO]** Sin migración.
- `DescifradoCredencialOvz.descifrarPasswordParaEnvioOvz` **deja de ser `@Transactional`** (descifrar **fuera de
  la transacción** del envío; la única escritura es la auditoría, que ya es `REQUIRES_NEW`). Así el worker puede
  llamarlo dentro de la transacción de su trabajo y capturar su excepción sin dejarla `rollback-only`. Test
  `DescifradoCredencialOvzTest.llamadoDentroDeOtraTransaccionNoLaMarcaRollbackOnly`.
- **Test-guardián `DescifradorUnicoTest`** (bytecode, al estilo de `ConsultasSinTenantTest`): solo
  `DescifradoCredencialOvz` llama a `CifradorAesGcm.descifrar` (cierra el pendiente de que el descifrador es el
  único; `cifrar` no se restringe).
- **El tercer pendiente de M2** (mapear `CredencialOvzDeOtraGestoriaException`/`DescifradoCredencialOvzException`
  a HTTP 404/error controlado) **se reinterpreta**: en el modelo servidor-a-servidor **no hay endpoint HTTP que
  descifre**; las captura **el worker** y las convierte en `ERROR_OVZ` **seguro** (fallo antes de «Aceptar»). Se
  cierra en **T4**.

**T1 — Credenciales OVZ por ganadero + flag certificado. Migración V32** (`ganadero.usa_certificado`, boolean,
default false). Pantalla de alta/edición de credenciales por ganadero (usuario + contraseña cifrada con
`CifradorAesGcm.cifrar`; `ovzUsuario`/contraseña nunca en respuestas, auditoría ni logs); auditoría
`CREDENCIAL_OVZ_GUARDADA`/`_MODIFICADA` **sin el valor**. Flag «usa certificado» al dar de alta (D8) + detección
defensiva en la entrada.

**T2 — Cola de trabajos + encolar al aprobar. Migración V33** (`trabajo_ovz`, `GestoriaScopedEntity`: trámite,
ganadero, tipo `BAJA_MUERTE`, estado del trabajo, intentos, instantes, `clave_idempotencia` —uno por trámite—,
**clase de error seguro/incierto** y ref al documento). Al **aprobar** un `BAJA_MUERTE`: **encolar solo si el
ganadero tiene credenciales y `usa_certificado=false`**; si no, **vía manual (`HECHO_EN_OVZ`)** como hoy (D4).
Nunca dentro de la transacción de aprobación ni con el lock de la fila. Estados `APROBADO`→`EN_PROCESO`→
(`EJECUTADO_OVZ`|`ERROR_OVZ` seguro|`ERROR_OVZ` incierto); reencolado según clase de error (D11). Candado por
ganadero (D3).

**T3 — Documentos: entidad + almacenamiento. [HECHA 2026-11-18.] Migración V34** (`documento_ovz`,
`GestoriaScopedEntity`: ganadero, trámite, explotación, tipo «Baja Animal», nombre, tamaño, content-type, SHA-256,
instante; + `documento_ovz_contenido` `BYTEA` 1:1). Almacén **en Postgres** (D6 revisada), solo PDF (cabecera
`%PDF`), máx 10 MB (`ValidadorPdf` + `CHECK`); descarga servida por el backend tras comprobar la gestoría
(`GET /documentos-ovz/{id}/contenido` adjunto; `GET /ganaderos/{id}/documentos` metadatos; ajeno → 404 +
`ACCESO_OTRA_GESTORIA`; descarga → `LECTURA_DOCUMENTO_OVZ`). Servicio de guardado (lo usará el worker) auditado con
`OVZ_DOCUMENTO_GUARDADO` sin contenido. **Verificación del crotal dentro del PDF con PDFBox** (`BuscadorCrotalEnPdf`,
lógica pura) para el método del §9 (la T4 la usa para identificar el justificante). Interruptor
`ganera.ovz.presentacion-automatica.activa` (false por defecto): apagado → no se encola al aprobar. Recuentos de
migraciones: Postgres 34 / H2 33.

**T4 — Worker Playwright (implementador subagente).** Worker Docker (perfil `clever,worker`), imagen oficial
Playwright Java (D1/D2). Login → explotación bovina → menú → baja → crotal → «Buscar» → **verificación
bloqueante** (Código RIIA == crotal; Explotación de ubicación == REGA, §5) → «Aceptar» → descarga del
justificante (§9). Descifrado con `DescifradoCredencialOvz` (T0), **fuera de la transacción del trabajo**; captura
de `CredencialOvzDeOtraGestoriaException`/`DescifradoCredencialOvzException` → `ERROR_OVZ` (cierra el mapeo de M2).
Caducidad de sesión y certificado (§7); anti-duplicado **nunca reintenta ante incertidumbre**, consulta Documentos
(D5, §9). Auditoría `OVZ_BAJA_PRESENTADA`/`OVZ_BAJA_ERROR`/`DOCUMENTO_OVZ_GUARDADO` (§8) + declarar worker/rutas
en el inventario cerrado.

**T5 — Frontend.** Estados `EN_PROCESO`/`EJECUTADO_OVZ`/`ERROR_OVZ` (seguro/incierto), aviso al gestor,
justificante descargable desde la ficha, reencolado según D11 (seguro: corregir + reaprobar; incierto:
confirmación explícita **auditada**). Textos de honestidad reconciliados con **D10**.

**T6 — Verificación final.** Suite completa backend + frontend, inventario de auditoría, guardia DML, smoke.
**Redacción final de la regla no-negociable del CLAUDE.md (D10)**: el clic humano es la Aprobación en Ganera;
Ganera presenta en OVZ solo un trámite ya aprobado, con verificación previa y sin reintento ante incertidumbre.
