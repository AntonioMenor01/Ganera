# Worker de OVZ en Clever Cloud (escalón 2, T4)

El worker presenta las bajas por muerte aprobadas en OVZnet (plan `docs/superpowers/plans/2026-11-17-presentacion-automatica-baja-ovz.md`).
Es la **misma base de código** que el backend, arrancada con el perfil `worker`: **no sirve HTTP de la aplicación**
(no hay Tomcat ni endpoints) y solo ejecuta su planificador. T6 lo despliega; esto deja el contrato.

## Servidor de salud (puerto 8080)

Clever exige que **toda** aplicación (también las Docker) escuche en `0.0.0.0:8080`, o rechaza el despliegue con
«Your application is not listening on 8080». Como el worker arranca sin servidor web, un **servidor de salud
independiente de Spring MVC** (`ServidorSaludWorker`, `com.sun.net.httpserver` del JDK) abre ese puerto: escucha en
`0.0.0.0:${PORT:8080}` y responde `200 ok` (texto plano, sin datos) a `GET /` y `404` a todo lo demás (otra ruta u
otro método). No levanta Tomcat ni expone ningún endpoint de la aplicación, y se cierra al parar. Solo arranca en el
perfil `worker` (en el backend normal Tomcat ya ocupa el 8080). Clever inyecta el puerto en la variable `PORT`.

## Aplicación

- Aplicación **Docker**, tamaño **S**, región **París**, **una sola instancia** (el candado de cola es global, D3:
  `FOR UPDATE SKIP LOCKED` reclama un trabajo a la vez; con más instancias habría varias sesiones OVZ simultáneas).
- Dockerfile: `backend/Dockerfile.worker` (`APP_FOLDER=backend`, `CC_DOCKERFILE=Dockerfile.worker`, o el mecanismo
  equivalente de Clever). Base `mcr.microsoft.com/playwright/java:v1.49.0-noble` (Playwright 1.49.0 = el del `pom.xml`).
  **Verificado en T6 (2026-11-19):** esa imagen instala `openjdk-21-jdk` (Dockerfile oficial de playwright-java
  v1.49.0), así que el jar `--release 21` arranca sin instalar otro JDK. Chromium arranca con `--no-sandbox`
  (lo pasa `PlaywrightNavegadorOvz`), necesario porque el worker corre como root en el contenedor.

## Variables de entorno

| Variable | Valor |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `clever,worker` (clever aporta Supabase + TLS; worker quita el servidor web, apaga Flyway y activa el planificador) |
| `SUPABASE_DB_HOST` / `_PORT` / `_NAME` / `_USER` / `_PASSWORD` | las **mismas** que el backend. El worker conecta **solo** como la aplicación (`ganera_app`) |
| `ENCRYPTION_KEY` | la **MISMA** que el backend. Sin ella el worker no puede descifrar las contraseñas OVZ |
| `JWT_SECRET` | un **valor aleatorio DISTINTO del backend**. El worker no emite ni valida tokens (no hay web), pero `JwtService` se construye igual y falla el arranque si está vacío; no comparte secreto con el backend |
| `GANERA_OVZ_WORKER_MODO` | `ENSAYO` (por defecto) hasta validar; `REAL` solo cuando Antonio lo decida |
| `GANERA_OVZ_WORKER_URL` | URL de entrada de OVZnet |
| `TZ` | `UTC` |

**El worker NO migra** (`spring.flyway.enabled=false` en el perfil `worker`): la base de datos la migra solo el
backend, así que el worker **no** necesita `SUPABASE_FLYWAY_USER`/`_PASSWORD` (esas son de `ganera_propietario`).
Nunca debe haber dos migradores concurrentes sobre el mismo esquema.

`GANERA_OVZ_WORKER_MODO` vacío, ausente o con cualquier valor que no sea `REAL` se trata como `ENSAYO`.

## Qué hace cada modo

- **ENSAYO** (por defecto): reclama el trabajo, inicia sesión, navega por menús hasta la baja, **verifica** crotal y
  explotación, rellena fecha (y MER) y sale con «Salir». **Nunca pulsa «Aceptar».** El trabajo queda `ENSAYO_OK`
  (terminal) y el trámite vuelve a `APROBADO`. Deja una fila de auditoría `OVZ_BAJA_ENSAYO`.
- **REAL**: igual, y pulsa «Aceptar» (con la doble comprobación id `_btn_Aceptar` + texto «Aceptar»), localiza el
  justificante en Documentos y lo guarda en la ficha. Un fallo **antes** de «Aceptar» es `ERROR/SEGURO`; **después**,
  `ERROR/INCIERTO` salvo que Documentos confirme la baja. **Nunca se reintenta una presentación.**

## Notas

- Logs: solo pasos y códigos. Nunca crotal, REGA, usuario, contraseña ni contenido de PDF.
- El worker no ejecuta los planificadores del backend (extracción por IA, retención, sincronización con Stripe).
- Prueba manual del navegador de producción: `OvzWorkerEnsayoManualTest` (`OVZ_WORKER_ENSAYO=true` + variables `OVZ_*`).
