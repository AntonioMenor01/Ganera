# Spike: ¿puede Ganera presentar la baja en OVZ desde el servidor? (escalón 2)

Escrito para: Antonio. Es una **prueba técnica (spike), no producción**. Responde a dos preguntas, **sin
presentar nada** en OVZ:

1. **En local** — ¿puede un script Playwright iniciar sesión con las credenciales de un ganadero, elegir la
   explotación bovina, navegar solo por menús hasta la baja por muerte, buscar un crotal, leer el Código RIIA y
   la explotación de ubicación, y salir, **sin tocar «Aceptar»**?
2. **En Clever** — ¿responde OVZ desde la IP de Clever (solo la página de login, sin credenciales), y se puede
   ejecutar Chromium + Playwright en nuestra instancia?

Ningún código de este spike presenta nada en OVZ. El clic en «Aceptar» está **bloqueado en el código**.

## Resultado (2026-11-17): OK en local ✅ · (2026-11-18): OVZ responde desde Clever ✅

En el equipo de Antonio, **PASO 1 a 9 OK en ~10 s**: login con los id reales, explotación bovina elegida, menú
Trámites → Bóvidos → pestaña de baja, crotal escrito + «Buscar», **Código RIIA coincide** con el crotal,
explotación de ubicación leída, y **«Salir» sin presentar nada**.

**Prueba de IP desde Clever (§2): OK.** Como `clever ssh` no funcionaba (rechazaba la clave), la hicimos con un
endpoint temporal en el backend (`GET /internal/diagnostico/ovz-alcance`, PR #38), que hace una sola petición a
`https://ovznet.juntaex.es/Inicio.aspx` desde la propia instancia de Clever (París) y devuelve solo el código
HTTP. **Resultado: `{"codigoHttp":200}`** → **OVZnet responde a la IP de Clever**, sin bloqueo ni geofiltrado;
no hace falta una IP de salida distinta. El endpoint temporal se retira en la PR #39. Con esto, el spike queda
**cerrado con éxito** y la vía libre para montar el worker Docker con Playwright (§ «Alternativa recomendada» y
D1 del plan).

## 1. Spike local — `OvzSpikeBajaMuerteTest`

`backend/src/test/java/com/ganera/core/ovz/spike/OvzSpikeBajaMuerteTest.java`. Es un test JUnit **desactivado
por defecto** (mismo patrón que `ExtraccionSinTramiteEvalTest`, que solo corres tú): solo se ejecuta con
`OVZ_SPIKE=true` y las variables de entorno abajo. Nunca corre en `./mvnw clean test` normal ni en CI.

### Credenciales — en un `.env` fuera del repo (tú lo creas)

Crea un `.env` **fuera del repositorio** (p. ej. en `C:\Users\Antonio\Desktop\ganera-ovz-spike\.env`), **nunca
lo pegues en el chat, nunca lo commitees, nunca lo imprimas**. Claves:

```
OVZ_URL=https://ovznet.juntaex.es/Inicio.aspx   # la entrada de OVZnet
OVZ_USUARIO=<usuario del ganadero>
OVZ_PASSWORD=<contraseña del ganadero>          # usuario/contraseña, NO certificado
OVZ_CROTAL=<crotal completo a buscar>
OVZ_REGA=<REGA de la explotación>               # OPCIONAL: si se define, compara la explotación de ubicación
```

`OVZ_REGA` es **opcional**: con él, el PASO 8 compara también la «Explotación de ubicación» (contención, sin
mostrar valores: imprime COINCIDE/NO coincide); sin él, solo lee la explotación y compara el crotal.

### Cómo ejecutarlo (Git Bash)

Exporta las variables desde tu `.env` (no lo subas) y añade `OVZ_SPIKE=true`:

```
set -a; source /c/Users/Antonio/Desktop/ganera-ovz-spike/.env; set +a
cd backend
JAVA_HOME='C:\Users\Antonio\.jdks\temurin-21\jdk-21.0.12.1+1' OVZ_SPIKE=true \
  ./mvnw test -Dtest=OvzSpikeBajaMuerteTest
```

### Qué hace, paso a paso (y qué verás)

Headless, sin capturas. La salida son solo líneas `PASO N OK: …` o, si algo falla, `PASO FALLO: …` + el
`Selector que se buscaba: …` + un diagnóstico de estructura. **Nunca** imprime el crotal, usuario, contraseña ni
el contenido de la página (solo etiquetas de paso, selectores y estructura de controles).

1. Abre `OVZ_URL` (`Inicio.aspx`; único `goto` permitido: la entrada; dentro ya solo se navega por menús).
2. Login con `…_txtUsuario` / `…_txtClave` y el **enlace** `…_btnLinkEntrar` (no certificado); espera a
   `Presentacion.aspx` o a que aparezca el desplegable `ctl00_ucMenu1_ddlDelegado`. Si sigue en `Inicio.aspx`,
   vuelca los mensajes de error de OVZ.
3. Elige la explotación bovina en `ctl00_ucMenu1_ddlDelegado`: la opción cuya **especie** termina en «Bóvidos»
   (sin acentos ni mayúsculas), **por su value**; si hay varias → `PASO FALLO «varias explotaciones bovinas»`.
4. Menú **Trámites › Trámites** por su `href` (`…/Explotaciones/wgTramites.aspx`), sin teclear URLs.
5. Enlace **«Bóvidos»** de la tabla de especies.
6. Pestaña **«Baja de Bóvidos»** → pantalla 1 (búsqueda por crotal).
7. Escribe el crotal (no se muestra) en `…_ctl05_TxtCrotal` y pulsa **«Buscar»** (`…_ctl05_LinkBuscar`). Si tras
   «Buscar» sigue en la pantalla 1, vuelca los mensajes de OVZ (p. ej. «animal no encontrado») y para.
8. En la pantalla 2: confirma que **«Aceptar»** (`…_ctl05_btn_Aceptar`) existe —**solo lo lee, nunca lo
   pulsa**—, lee `…_ctl05_txtCodigoRIIA` y `…_ctl05_txtExplotacionUbicacion` y **compara** con `OVZ_CROTAL` (y
   `OVZ_REGA` si está): imprime COINCIDE/NO coincide, nunca los valores.
9. Pulsa **«Salir»** (`ctl00_ucBarraUsuario1_btnSalir`). Fin, sin presentar nada. Si algún paso falla a mitad,
   también intenta «Salir» (best-effort) para no dejar la sesión abierta.

### El bloqueo duro de «Aceptar»

Todos los clics pasan por `pulsar(...)` / `pulsarPorTexto(...)`, que llaman a `prohibirAceptar(...)`: si el
selector o la etiqueta contienen `_btn_Aceptar`, «aceptar» o «solicitar», **lanzan y abortan** en vez de pulsar.
El `…_btn_Aceptar` **solo se LEE** (para confirmar que la pantalla 2 cargó), nunca se pulsa. Es imposible que
este spike presente la baja aunque un paso se tuerza.

### Mecanismo de localización (cross-frame, sin exigir visibilidad)

Todos los pasos buscan el elemento en **todos los frames por JS** (como el diagnóstico) y actúan por JS
(foco+value+change para escribir, `element.click()` para pulsar, `select.value`+change para elegir), **sin
exigir que el elemento sea visible**. Así no dependemos del frame principal ni de la visibilidad. **Recordatorio
de selectores (del mapa):** el `P_` del mapa es la **forma corta del prefijo de la pestaña**
(`ctl00_ContentPlaceHolder1_ucPestanasTramites_ctl05_`), **no parte del id**; en selectores se usa el sufijo
real dentro de `_ctl05_` (p. ej. `[id*='_ctl05_'][id$='_btn_Aceptar']`).

## 2. Clever — alcance de OVZ desde su IP y Chromium/Playwright

### Paso a paso: ¿responde `https://ovznet.juntaex.es/Inicio.aspx` desde la IP de Clever? (sin credenciales)

Esto comprueba solo que OVZ no bloquea la IP de París; **no usa credenciales**.

1. **Instala `clever-tools`** (una vez), con Node en tu equipo:
   ```
   npm i -g clever-tools
   ```
2. **Inicia sesión** (abre el navegador para autorizar):
   ```
   clever login
   ```
3. **Lista tus apps** para ver el nombre/alias de la del backend:
   ```
   clever applications
   ```
4. **Entra por SSH** en la instancia del backend (sustituye `<app>` por su nombre o id):
   ```
   clever ssh --app <app>
   ```
5. **Ya dentro de la instancia**, pide **solo las cabeceras** (petición sin cuerpo, sin credenciales):
   ```
   curl -sS -o /dev/null -w "HTTP %{http_code}\n" https://ovznet.juntaex.es/Inicio.aspx
   ```
   (equivalente con cabeceras completas: `curl -sI https://ovznet.juntaex.es/Inicio.aspx | head -n 1`).

**Qué pegarme:** **solo el código HTTP** (p. ej. `HTTP 200`). Nada más. Interpretación: `200`/`3xx` → OVZ
responde a la IP de Clever; un timeout o `403`/`503` → posible bloqueo por IP o geofiltrado (OVZnet es de la
Junta de Extremadura), lo que empujaría a ejecutar el worker desde otra IP/ubicación.

*(Con navegador, el test `OvzAlcanceLoginTest` —`OVZ_SPIKE_ALCANCE=true`, `OVZ_URL=…`— abre la página de login y
comprueba que hay campo de contraseña; si Chromium no arranca en Clever, lo dice en su fallo. Pero para la
prueba de IP basta el `curl` de arriba.)*

### ¿Se puede ejecutar Chromium + Playwright en la instancia XS del backend?

**Dudoso en la instancia Java/Maven tal cual, y no recomendado.** Motivos:

- Playwright descarga los navegadores a `~/.cache/ms-playwright` en el primer `Playwright.create()`, pero
  Chromium necesita **librerías del sistema** (libnss3, libatk, libgbm, libasound2, fuentes…). En el runtime
  gestionado de Clever no se instalan paquetes del sistema con `apt`, así que el binario puede descargarse y aun
  así **no arrancar** por dependencias nativas que faltan.
- La **memoria** de una XS es ajustada para JVM + Chromium headless a la vez; una presentación mientras el
  backend sirve peticiones compite por RAM.
- El **detector de «varias instancias»** de OVZ y la necesidad de **serializar** encajan mal con una instancia
  que además sirve HTTP.

**Alternativa recomendada — un «worker de OVZ» en una instancia Docker aparte.** Una segunda app en Clever con
runtime **Docker**, a partir de la imagen oficial `mcr.microsoft.com/playwright/java` (trae Chromium y **todas**
las dependencias del sistema ya instaladas), que:

- expone una API interna mínima (solo accesible desde el backend) para «preparar y presentar una baja» de un
  trámite ya aprobado;
- corre **una sesión OVZ a la vez** (candado por ganadero, ver el plan), aislada del tráfico HTTP del backend;
- descifra la contraseña del ganadero solo en el momento (M2) y nunca la registra.

Así el backend (Paris, Java/Maven) sigue ligero y el worker (Docker con Playwright) carga con el navegador. La
decisión «dónde corre» es D1 del plan de la presentación automática. Antes de montar el worker conviene
confirmar con el `curl` de arriba que OVZ responde a la IP de Clever; si bloquea la IP de París, habría que
mirar una IP de salida distinta o ejecutar el worker en otra ubicación.
