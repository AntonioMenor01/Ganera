# Worker de OVZ en Clever Cloud (escalón 2, T4)

El worker presenta las bajas por muerte aprobadas en OVZnet (plan `docs/superpowers/plans/2026-11-17-presentacion-automatica-baja-ovz.md`).
Es la **misma base de código** que el backend, arrancada con el perfil `worker`: **no sirve HTTP** (no hay Tomcat ni
endpoints) y solo ejecuta su planificador. T6 lo despliega; esto deja el contrato.

## Aplicación

- Aplicación **Docker**, tamaño **S**, región **París**, **una sola instancia** (el candado de cola es global, D3:
  `FOR UPDATE SKIP LOCKED` reclama un trabajo a la vez; con más instancias habría varias sesiones OVZ simultáneas).
- Dockerfile: `backend/Dockerfile.worker` (`APP_FOLDER=backend`, `CC_DOCKERFILE=Dockerfile.worker`, o el mecanismo
  equivalente de Clever). Base `mcr.microsoft.com/playwright/java:v1.49.0-noble` (Playwright 1.49.0 = el del `pom.xml`).
  **Comprobar en T6 que la imagen trae un JDK 21.**

## Variables de entorno

| Variable | Valor |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `clever,worker` (clever aporta Supabase + TLS; worker quita el servidor web y activa el planificador) |
| `SUPABASE_DB_HOST` / `_PORT` / `_NAME` / `_USER` / `_PASSWORD` / `SUPABASE_FLYWAY_USER` / `_PASSWORD` | las **mismas** que el backend |
| `ENCRYPTION_KEY` | la **MISMA** que el backend. Sin ella el worker no puede descifrar las contraseñas OVZ |
| `GANERA_OVZ_WORKER_MODO` | `ENSAYO` (por defecto) hasta validar; `REAL` solo cuando Antonio lo decida |
| `GANERA_OVZ_WORKER_URL` | URL de entrada de OVZnet |
| `TZ` | `UTC` |

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
