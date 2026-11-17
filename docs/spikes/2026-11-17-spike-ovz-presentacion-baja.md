# Spike: ¿puede Ganera presentar la baja en OVZ desde el servidor? (escalón 2)

Escrito para: Antonio. Es una **prueba técnica (spike), no producción**. Responde a dos preguntas, **sin
presentar nada** en OVZ:

1. **En local** — ¿puede un script Playwright iniciar sesión con las credenciales de un ganadero, navegar solo
   por menús hasta la baja por muerte, buscar un crotal, leer el Código RIIA y la explotación, y salir, **sin
   tocar «Aceptar»**?
2. **En Clever** — ¿se puede ejecutar Chromium + Playwright en nuestra instancia, y responde OVZ desde la IP de
   Clever (solo la página de login, sin credenciales)?

Ningún código de este spike presenta nada en OVZ. El clic en «Aceptar» está **bloqueado en el código**.

## 1. Spike local — `OvzSpikeBajaMuerteTest`

`backend/src/test/java/com/ganera/core/ovz/spike/OvzSpikeBajaMuerteTest.java`. Es un test JUnit **desactivado
por defecto** (mismo patrón que `ExtraccionSinTramiteEvalTest`, que solo corres tú): solo se ejecuta con
`OVZ_SPIKE=true` y las variables de entorno abajo. Nunca corre en `./mvnw clean test` normal ni en CI.

### Credenciales — en un `.env` fuera del repo (tú lo creas)

Crea un `.env` **fuera del repositorio** (p. ej. en `C:\Users\Antonio\Desktop\ganera-ovz-spike\.env`), **nunca
lo pegues en el chat, nunca lo commitees, nunca lo imprimas**. Claves:

```
OVZ_URL=<la URL de entrada de OVZnet>      # el mapa no la guarda a propósito
OVZ_USUARIO=<usuario del ganadero>
OVZ_PASSWORD=<contraseña del ganadero>     # usuario/contraseña, NO certificado
OVZ_CROTAL=<crotal completo a buscar>
```

### Cómo ejecutarlo (Git Bash)

Exporta las variables desde tu `.env` (no lo subas) y añade `OVZ_SPIKE=true`:

```
set -a; source /c/Users/Antonio/Desktop/ganera-ovz-spike/.env; set +a
cd backend
JAVA_HOME='C:\Users\Antonio\.jdks\temurin-21\jdk-21.0.12.1+1' OVZ_SPIKE=true \
  ./mvnw test -Dtest=OvzSpikeBajaMuerteTest
```

### Qué hace, paso a paso (y qué verás)

Headless, sin capturas. La salida son solo líneas `PASO N OK: …` o, si algo falla, `PASO FALLO: …`. Nunca
imprime tu crotal, usuario, contraseña ni el contenido de la página (a lo sumo, el selector o el tipo de error,
que no llevan datos reales).

1. Abre `OVZ_URL` (único `goto` permitido: la entrada; dentro ya solo se navega por enlaces/menús).
2. Login con usuario y contraseña (no certificado); espera `…/Presentacion.aspx`.
3. Menú **Trámites › Trámites** (por texto de enlace, no por URL).
4. Enlace **«Bóvidos»**.
5. Pestaña **«Baja de Bóvidos»**. Comprueba que el botón «Aceptar» **existe** (pantalla correcta) pero **no lo
   pulsa jamás**.
6. Escribe el crotal (no se muestra) y pulsa **«Buscar»** (`P_LinkBuscar`, postback que no guarda).
7. Lee el **Código RIIA** y la **explotación** (sin mostrarlos; solo confirma que se pueden leer).
8. Pulsa **«Salir»** (`ctl00_ucBarraUsuario1_btnSalir`). Fin, sin presentar nada.

### El bloqueo duro de «Aceptar»

Todos los clics pasan por `clicSeguro(...)`, que llama a `prohibirAceptar(...)`: si el selector o la etiqueta
contienen `P_btn_Aceptar`, «aceptar» o «solicitar», **lanza una excepción y aborta** en vez de pulsar. El
selector de «Aceptar» no se usa para pulsar en ningún sitio del script. Es imposible que este spike presente la
baja aunque un paso se tuerza.

### Nota de fragilidad (esperable en un spike)

Los ids de los **campos de login** y de los **campos de solo lectura** (Código RIIA, explotación) **no están en
el mapa**. El script los localiza por heurística (tipo de input, texto visible, sufijo de id). Si un paso falla
por un selector, el log dirá en cuál; con eso afinamos el selector real. No des por bueno ningún selector de
estos hasta la primera ejecución real (es justo lo que confirma la sesión de mapeo, D13 del plan de la
extensión).

## 2. Clever — Chromium/Playwright y alcance de OVZ

### ¿Responde OVZ desde la IP de Clever? (sin credenciales)

Primero, lo barato y sin navegador: desde una shell/one-off en la instancia de Clever, un `curl` a la página de
entrada para ver si la IP de París no está bloqueada:

```
curl -sS -o /dev/null -w "HTTP %{http_code}\n" "$OVZ_URL"
```

Un `200`/`3xx` indica que OVZ responde a la IP de Clever. Un timeout o `403/503` sugiere bloqueo por IP o
geofiltrado (OVZnet es de la Junta de Extremadura; podría restringir). Esto se prueba **sin credenciales**.

Con navegador, el test `OvzAlcanceLoginTest` (`OVZ_SPIKE_ALCANCE=true`, `OVZ_URL=...`) abre la página de login y
comprueba que hay campo de contraseña (señal de que cargó la pantalla, no un error de proxy). Si Chromium no
arranca en Clever, este test lo dirá en su mensaje de fallo.

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
decisión «dónde corre» se cierra en el plan (D-«infra»). Antes de montar el worker conviene confirmar con el
`curl` de arriba que OVZ responde a la IP de Clever; si bloquea la IP de París, habría que mirar una IP de
salida distinta o ejecutar el worker en otra ubicación.

### Resultado esperado del spike (qué me dices)

- Local: en qué `PASO N` quedó (idealmente `PASO 8 OK`), o el primer `PASO FALLO` y su selector.
- Clever: el `HTTP` del `curl` a la página de login, y si `OvzAlcanceLoginTest` arranca Chromium o no.

Con eso decidimos los selectores reales y si el worker Docker es el camino, antes de escribir nada de
producción.
