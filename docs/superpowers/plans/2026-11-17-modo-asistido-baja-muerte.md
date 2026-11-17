# Modo asistido de la baja por muerte (escalón 2) — plan

Escrito para: Antonio y quien implemente el escalón 2 de OVZ. Es **solo plan**, sin código. Se apoya en
el mapa real de OVZnet (`docs/referencias/mapa-ovz.md`, leído el 2026-11-16 con una cuenta de ganadero,
sin presentar nada) y en el catálogo del manual (`docs/referencias/ovz-tramites-bovino.md`, §6 del cruce).
Todo lo que quede por decidir va numerado al final (**D1…**), no se cierra aquí.

## 1. Qué es y qué NO es

El **modo asistido** (escalón 2) sustituye el copia-y-pega manual del escalón 1 (la ficha OVZ): en vez de
que el gestor teclee los datos en OVZnet, **Ganera abre OVZnet, navega y rellena el formulario de la baja
por muerte, y se para antes de «Aceptar»**. La persona revisa el formulario ya relleno y **pulsa ella misma
«Aceptar»**. Ganera **nunca** presenta nada por su cuenta (regla no-negociable del CLAUDE.md).

- **Un solo trámite a la vez**, iniciado por una acción explícita del gestor sobre un trámite ya `APROBADO`
  de tipo `BAJA_MUERTE`. No hay lotes, ni cola automática, ni reintento autónomo.
- **Solo la baja por muerte** en esta primera entrega. Alta, movimiento, confirmación y censo quedan para
  después, reutilizando lo que aquí se monte.
- Tras el «Aceptar» del gestor, el trámite sigue el flujo que ya existe: el gestor pulsa **«Marcar como hecho
  en OVZ»** (estado `HECHO_EN_OVZ`), que registra quién y cuándo. El modo asistido **no** inventa un estado de
  «enviado por Ganera»: Ganera no envía.

## 2. El formulario de la baja en OVZnet (del mapa, sección A)

Ruta exacta, **solo por menús** (ver §4): elegir en «Lista de explotaciones» la entrada «`<REGA>` - Bóvidos»
→ menú **Trámites › Trámites** → pulsar el enlace **«Bóvidos»** de la tabla de especies → pestaña **«Baja de
Bóvidos»**. Prefijo de los ids: `ctl00_ContentPlaceHolder1_ucPestanasTramites_ctl05_` (el índice de pestaña
`ctl05` está pendiente de confirmar que sea estable, D13).

Dos pantallas:

**Pantalla 1 — Búsqueda.** Un único campo de texto para el crotal (`…_TxtCrotal`, `ES`+12 dígitos, sin
maxlength) y el enlace **«Buscar»** (`…_LinkBuscar`, postback que **no guarda**). Buscar sin crotal también
vale y deja elegir de una lista (no lo usaremos: Ganera siempre tiene el crotal).

**Pantalla 2 — Formulario.** OVZ rellena solo y en **solo lectura** 10 campos del animal (Código RIIA, fecha
de nacimiento, explotación de nacimiento y de ubicación, Código RIIA madre, país, especie, raza, sexo,
identificación de lidia). Editables / que Ganera toca:

| Campo | id (sufijo) | Qué pone Ganera | Obligatorio |
|---|---|---|---|
| **Fecha de muerte** | `_TextFechaMuerte` (+ calendario `_imgIni`) | `tramite` → fecha del hecho, `dd/mm/aaaa` | **sí (`*`)** |
| Nº Documento MER | `_TxtMer` (maxlength 80) | solo si viene en el trámite; si no, se deja vacío | no |
| Oficina Veterinaria de Zona | `_DDLOVZ` (**desactivado**) | **no se toca**: viene preseleccionada de la explotación | — |
| Adjunto MER (PDF ≤ 5 MB) | `_FUpload` / «Añadir» `_BtnGrabar` | **no se toca** en esta entrega (D9) | no |

- **No pulsar** «Añadir» (sube el adjunto), ni «Mostrar/Ocultar Documentos», ni «Volver».
- **No pulsar** «Aceptar» (`…_btn_Aceptar`): es un postback **sin confirmación** que presenta la baja. Ese
  clic es **de la persona**.
- Antes de parar, Ganera **verifica** que el crotal del Código RIIA (solo lectura) coincide con el del
  trámite y, si puede, que la explotación de ubicación es la del trámite (defensa contra un crotal que
  resuelva a otro animal). Si no cuadra, aborta y avisa, sin tocar más campos (D6).

Avisos fijos que Ganera debe mostrar al gestor junto al formulario (los da OVZ): plazo de 7 días desde la
muerte, entrega de la copia del MER en 15 días, y qué hacer si el MER no identifica al animal.

## 3. Credenciales por ganadero

El mapa se obtuvo con **una cuenta de ganadero**: el login de OVZnet es **por ganadero** (usuario y
contraseña, o certificado digital; no hay doble factor con usuario/contraseña). Encaja con el modelo que ya
existe: `Ganadero.ovzUsuario` y `Ganadero.ovzPasswordCifrada` (columnas desde `V3`), cifrado AES-256-GCM con
`CifradorAesGcm` y clave de `ENCRYPTION_KEY`.

- **Pantalla de alta de credenciales OVZ** (hoy deshabilitada en `ConfiguracionPage` → sección «OVZ.net
  (Cañada)»): se habilita para **guardar usuario + contraseña del ganadero**. La contraseña se cifra con
  `CifradorAesGcm.cifrar()` (devuelve `CredencialOvzCifrada`, el único tipo que acepta
  `Ganadero.setOvzPasswordCifrada`; `@JsonIgnore`, nunca sale en respuestas ni en auditoría). El `ovzUsuario`
  tampoco se enseña nunca en respuestas (ya cubierto por los tests de no-fuga). Guardar o cambiar credenciales
  deja su fila de auditoría (D4). Esta pantalla es **por ganadero**, no por gestoría.
- **Login que hace Ganera:** `DescifradoCredencialOvz.descifrarPasswordParaEnvioOvz(ganadero, principal)` es
  el **único** sitio que descifra, ya pensado para esto. Aísla por gestoría (la del ganadero debe ser la del
  JWT) y deja la fila `CREDENCIAL_OVZ_DESCIFRADA` **antes** de descifrar. Pendientes que este plan activa
  (ya anotados en M2): mapear `CredencialOvzDeOtraGestoriaException` → `404` sin cuerpo y
  `DescifradoCredencialOvzException` → error controlado; llamar al descifrado **fuera** de la transacción del
  envío (es `@Transactional`: capturarlo dentro marca la transacción rollback-only); y declarar el endpoint de
  envío en el inventario cerrado (`InventarioAuditoriaTest`).
- **Dos modos de login (D2):** (a) **Ganera entra sola** con las credenciales descifradas; (b) **el gestor
  entra a mano** en el navegador visible (útil para certificado digital, o si la gestoría no quiere dar la
  contraseña del ganadero a Ganera). El modo (b) no necesita credenciales guardadas ni descifrado. Conviene
  soportar los dos; la certificado-digital **solo** es viable en (b).

## 4. Navegación: las reglas del detector de «varias instancias»

Crítico y no evitable (mapa §2): OVZnet **invalida la sesión** (redirige a `/AvisoSesion.aspx`) si se **escribe
una URL**, se pulsa **Atrás**, o hay **dos pestañas/contextos** con OVZnet abiertos. Por tanto, el driver de
Playwright debe:

- Entrar por `Presentacion.aspx` tras el login y **navegar solo pulsando enlaces y menús** (los submenús se
  abren al pasar el ratón; los enlaces se localizan por `href`, no por id, que no tienen).
- Usar **un solo `BrowserContext` y una sola `Page`**. Nunca `page.goto(url)` a una pantalla interna, nunca
  `goBack()`, nunca abrir una segunda pestaña. Los botones son `__doPostBack`/`WebForm_DoPostBackWithOptions`;
  **la URL no cambia** entre pantallas, así que hay que esperar al postback (networkidle / a que aparezca el
  selector de la pantalla siguiente), no a un cambio de URL.
- Los ids `ctl00_…` llevan un índice de pestaña (`ctl02`, `ctl05`): seleccionar por `[id$="_<Sufijo>"]`
  **dentro** del prefijo de su pestaña, para no confundir `_DDLOVZ`/`_imgIni` que se repiten entre pestañas.
- Si en cualquier paso aparece `/AvisoSesion.aspx`, **abortar** y empezar de cero desde el login (D7).

## 5. Dónde corre Playwright (la decisión central)

La regla no-negociable es que **la persona** revise el formulario relleno y **dé el clic de envío**. El mapa
dice que «Aceptar» de OVZ no pide confirmación: un clic presenta. Hay dos arquitecturas (**D1**):

**Opción A — Playwright en el backend (Clever), headless + vista en streaming.**
El backend abre OVZnet headless, rellena hasta antes de «Aceptar», y envía al navegador del gestor una
**vista** del formulario (capturas periódicas o vídeo; no interactiva). El gestor revisa y pulsa un botón de
**Ganera** «Enviar a OVZ»; ese clic le dice al backend que pulse el «Aceptar» real y descargue el justificante.
- A favor: nada que instalar en la gestoría; el backend ya tiene la dependencia Playwright (1.49.0) y los
  navegadores. Encaja con el despliegue actual (Clever Paris, una instancia).
- En contra: **Ganera acaba pulsando el «Aceptar» de OVZ** (aunque tras el «Enviar» explícito del gestor).
  Cumple la letra de la regla («nada sin el clic de Enviar del gestor en el formulario que Ganera rellenó»),
  pero **no** la lectura literal de este encargo («el «Aceptar» que pulsa la persona»). La sesión OVZ vive en
  Clever: la IP y el navegador no son los del gestor (puede chocar con OVZ, D11). Una sola instancia XS
  sirviendo Playwright headless + streaming es pesada; habría que acotar a 1 sesión asistida concurrente.

**Opción B — Playwright en el ordenador del gestor, navegador visible.**
Un **ayudante local** (p. ej. una pequeña app de escritorio, o Playwright en modo headed lanzado en la máquina
del gestor) abre un navegador **visible**. Ganera (desde el backend, por un canal con ese ayudante) le indica
los pasos de relleno; el ayudante los ejecuta en el navegador del gestor y **se para antes de «Aceptar»**. El
gestor ve su navegador con el formulario relleno y **pulsa el «Aceptar» real de OVZ con su ratón**.
- A favor: cumple la regla **al pie de la letra** (la persona pulsa el botón real de OVZ); la sesión OVZ corre
  con la IP/navegador del gestor (menos fricción con OVZ y con el certificado digital); el backend no carga con
  navegadores headless.
- En contra: hay que **distribuir y mantener un ayudante local** (instalación, actualizaciones, firma,
  soporte). Más complejo de operar en el piloto.

**Recomendación del plan:** para el piloto, **Opción B** por la garantía literal de que la persona pulsa
«Aceptar» y por evitar la IP de Clever en OVZ; si el coste del ayudante local es inasumible a corto plazo,
**Opción A** con el matiz de que el «Enviar» de Ganera es el consentimiento explícito. **Queda abierta (D1).**

## 6. Caducidad de 20 minutos

OVZnet caduca a los **20 minutos sin actividad** (mapa §2). El relleno automático es cuestión de segundos, pero
**la revisión y el clic del gestor pueden tardar**. Estrategia:

- Ganera prepara el formulario en una **ráfaga corta** y, a partir de ahí, arranca un **contador de 20 min**
  visible para el gestor.
- No se puede «mantener viva» la sesión sin actividad real de navegación (y navegar reiniciaría el formulario).
  Así que: si el gestor no pulsa «Aceptar» a tiempo, la sesión muere. Al detectarlo (aparece
  `/AvisoSesion.aspx` o el login), Ganera ofrece **rehacer** la preparación desde el login (re-login →
  re-navegación por menús → re-relleno), nunca por URL ni Atrás (**D7**: ¿rehacer automático o pedir
  confirmación al gestor?).
- Un envío **nunca** se reintenta solo. Si Ganera no sabe si el «Aceptar» llegó (caída justo después), **no**
  reintenta: lo marca como incierto y el gestor lo comprueba en OVZ (Documentos › «Baja Animal») (**D10**).

## 7. Cómo ve y pulsa «Aceptar» el gestor

- **Opción A:** en la pantalla de Ganera, junto a la vista del formulario relleno (captura/vídeo), un botón
  **«Enviar a OVZ»** (deshabilitado hasta que la preparación termina y el gestor marca que ha revisado). Tras
  pulsarlo, Ganera pulsa el «Aceptar» real, espera el postback y **descarga el justificante** (Documentos →
  «Baja Animal», PDF probable, D8). El gestor ve el resultado y luego marca **«Hecho en OVZ»**.
- **Opción B:** el gestor mira **su propio navegador** con el formulario relleno y pulsa el «Aceptar» de OVZ.
  Ganera (vía el ayudante) detecta que la baja se presentó (cambio de pantalla / aparición del justificante) y
  lo refleja en la app; luego el gestor marca «Hecho en OVZ». El justificante se descarga desde OVZ.
- En ambos, los textos de Ganera respetan la lista de honestidad del CLAUDE.md: nada dice «enviado/ejecutado
  por Ganera». El botón se llama **«Enviar a OVZ»** y es del escalón 2; «Aprobar» sigue sin ejecutar nada.

## 8. Auditoría

Regla no-negociable: toda escritura nueva deja su fila **y** su entrada en el inventario cerrado
(`InventarioAuditoriaTest`). Para este flujo:

- **Una fila por cada preparación** del formulario: nueva `AccionAuditoria` (p. ej. `OVZ_BAJA_PREPARADA`),
  actor `USUARIO` (del JWT), `EntidadAuditada.TRAMITE`, `entidadId` = tramiteId. **Sin datos personales en el
  contenido**: `antes`/`despues` van `null` o con `Clave`s cerradas (nunca el crotal, la fecha ni la
  contraseña — `DatosAuditoria` no admite `String` libre). La fila se deja **al preparar**, no espera al clic
  del gestor.
- El **descifrado** de la contraseña ya deja `CREDENCIAL_OVZ_DESCIFRADA` (M2), en el modo (a) de login.
- Guardar/editar las **credenciales OVZ** del ganadero deja su propia fila (D4: ¿`CREDENCIAL_OVZ_GUARDADA`?),
  sin el valor.
- El **envío** efectivo (opción A, cuando Ganera pulsa «Aceptar» tras el «Enviar» del gestor): fila propia
  (p. ej. `OVZ_BAJA_ENVIADA`), actor `USUARIO`. En la opción B no hay escritura de Ganera en el momento del
  clic (lo pulsa la persona en OVZ); el rastro es el «Hecho en OVZ» posterior, que ya se audita.
- El endpoint nuevo (preparar / enviar) se **declara** en el inventario cerrado como auditado→su test. La
  guardia DML exigirá la fila `insert into auditoria` en cada escenario de negocio.

## 9. Modelo de datos y estados (propuesta, D3)

- Reutilizar `EstadoTramite.EN_PROCESO` (reservado) para «preparación en curso / a la espera del clic del
  gestor», o **no** cambiar de estado y llevar la preparación en una entidad aparte. Decisión abierta.
- Posible entidad `PreparacionOvz` (tenant-scoped) con: tramite, ganadero, estado (PREPARANDO, LISTA_PARA_ENVIAR,
  ENVIADA, FALLIDA, CADUCADA, INCIERTA), instante de preparación, caducidad (preparación + 20 min), usuario que
  la lanzó, y el id/ruta del justificante descargado. El justificante **no** es dato personal de auditoría pero
  sí hay que decidir dónde se guarda (D8).
- El crotal y la fecha de muerte **ya** están en el trámite (`tramite_crotal`, campos OVZ): la preparación no
  duplica datos, los lee del trámite aprobado.

## 10. Riesgos a respetar (del CLAUDE.md y del mapa)

- **Nunca** abrir la sesión OVZ (Playwright) dentro de la transacción que bloquea la fila del trámite (otros
  revisores/aprobadores se bloquearían toda la sesión OVZ). El envío va fuera de ese lock, un trámite a la vez.
- Una sola instancia en Clever y el detector de varias instancias de OVZ obligan a **serializar**: como mucho
  **una sesión asistida por gestoría** (o por ganadero) a la vez. Decidir el candado (D12).
- No teclear URLs, no Atrás, no dos pestañas (ya en §4).
- Los ids con índice de pestaña (`ctl02`/`ctl05`) y `ClaveEspecie=1` para bovino **no** están confirmados como
  estables (mapa, pendientes generales 1 y 2): el driver debe localizar por texto/href siempre que pueda y
  fallar con claridad si no encuentra el selector, no seguir a ciegas (D13).

## 11. Alcance de la primera entrega

1. Pantalla de alta de credenciales OVZ por ganadero (cifrado, auditada).
2. Driver Playwright de la baja por muerte: login (modo a y/o b, D2), navegación por menús, relleno hasta
   antes de «Aceptar», verificación del crotal, parada.
3. La vista para el gestor y el botón «Enviar a OVZ» (opción A) o la detección del clic (opción B), según D1.
4. Caducidad de 20 min con contador y rehacer.
5. Auditoría (preparación, envío, credenciales) + inventario cerrado.
6. Descarga del justificante y enlace «Hecho en OVZ».

Fuera de esta entrega: alta, movimiento, confirmación, censo, demora; adjuntar el MER; lotes; cualquier envío
automático o reintento.

## Decisiones abiertas

- **D1. Dónde corre Playwright y quién pulsa el «Aceptar» real:** Opción A (backend Clever, headless +
  streaming, Ganera pulsa «Aceptar» tras el «Enviar» del gestor) vs Opción B (ordenador del gestor, navegador
  visible, el gestor pulsa el «Aceptar» de OVZ). El plan recomienda B; A es el camino barato con el matiz del
  consentimiento por «Enviar».
- **D2. Modo de login:** ¿solo Ganera con credenciales descifradas, solo el gestor a mano, o los dos? El
  certificado digital obliga al modo manual.
- **D3. Estado/modelo:** ¿reutilizar `EN_PROCESO`, una entidad `PreparacionOvz` aparte, o nada de estado?
- **D4. Auditoría de credenciales:** nombre y forma de la fila al guardar/editar credenciales OVZ.
- **D5. Qué verifica Ganera tras «Buscar»** antes de parar: ¿solo crotal, también explotación de ubicación,
  fecha de nacimiento…?, y qué hace si no cuadra.
- **D6. Aborto seguro:** mensajes y estado cuando el animal no aparece, está en otra explotación o ya tiene
  baja.
- **D7. Caducidad:** ¿rehacer la preparación automáticamente al caducar, o pedir confirmación al gestor?
- **D8. Justificante:** formato (PDF, pendiente de confirmar en el mapa), dónde se guarda y si se adjunta al
  trámite o solo se descarga.
- **D9. Adjunto MER:** si en una segunda iteración Ganera sube el PDF del MER (`_FUpload` + «Añadir»).
- **D10. Envío incierto:** cómo se marca y se resuelve una caída justo después del «Aceptar» (sin reintento).
- **D11. IP/navegador de OVZ:** si OVZ tolera la IP de Clever (opción A) o exige la del gestor (empuja a B).
- **D12. Concurrencia:** candado para no tener dos sesiones OVZ a la vez (por gestoría, por ganadero o global).
- **D13. Selectores:** confirmar en la sesión de mapeo con la gestoría piloto que `ctl05`/`ctl02` y
  `ClaveEspecie=1` son estables; estrategia de localización por texto/href y de fallo claro.
- **D14. Pendientes de M2 que este escalón cierra:** mapear `CredencialOvzDeOtraGestoriaException` → `404` sin
  cuerpo y `DescifradoCredencialOvzException` → error controlado; llamar al descifrado fuera de la transacción
  del envío; vigilar que `DescifradoCredencialOvz` siga siendo el único que descifra.
