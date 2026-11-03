# Auditoría base — PLAN APROBADO (2026-10-21)

## Decisiones de Antonio (2026-10-21) — prevalecen sobre el resto del plan

- **D1–D10 aprobadas tal cual**, con estas precisiones:
- **D3:** se mantiene el `404`. `existsByIdAndGestoriaIdNot` solo se ejecuta **después** de no encontrar el recurso en
  la gestoría propia y nunca devuelve datos. Riesgo apuntado en `progress.md`: alguien puede llenar la tabla con
  intentos (sin limitar en el MVP).
- **A1:** sin plazo; lo decide el abogado. El trigger se diseña para que **en el futuro** se pueda permitir borrar solo
  filas más antiguas que el plazo, solo como `ganera_propietario` y desde un proceso de retención. **No se implementa**;
  el diseño no lo impide y queda documentado (ver «Inmutabilidad en PostgreSQL»).
- **A2:** se auditan los **logins correctos y los fallidos con email existente**, en la gestoría de ese usuario. Email
  inexistente → sin fila, solo log **sin el email**. El `401` uniforme no cambia (T3).
- **A3:** recuentos y fechas en `antes`/`despues`, sí.
- **A4:** el parametrizado de T5 vale si la suite no crece más de un **20 % en tiempo** (base: 4 min 51 s con JDK 21 →
  tope ≈5 min 49 s); si no, una muestra representativa justificada.
- **A5:** `NUMERO_DESTINO_DESCONOCIDO` fuera de la auditoría; lo lleva la tarea del número por gestoría.
- **A6:** se corrige la skill `nueva-entidad-tenant` en T7. **A7:** se comprueba en el smoke.
- **IA:** si hace falta cambiar algo de la IA fuera de `ExtraccionTramiteService`, **parar y avisar** (Álvaro, `feat/mistral`).
- **Commits:** al cerrar cada tarea, con la revisión «Aprobado» y la suite completa en verde con JDK 21, commit local en
  `feat/auditoria`, stage ruta a ruta, **sin push**, y resumen corto (tarea, cifras, rutas). Al terminar T6, **antes**
  del smoke de T7, el informe completo para revisarlo.

Fecha: 2026-10-21. Parte de `main` = `origin/main` = `32b6d3f` (backend **1075**: 1074 + 1 omitido, JDK 21; frontend
**1084 en 50**; lint con los 3 avisos conocidos). Rama `feat/auditoria` creada desde `main`.
**Nada de código ni commit hasta que Antonio apruebe este plan.**

Base: «Decisiones 19/10» de `.superpowers/sdd/progress.md` (auditoría híbrida, se auditan los 403 entre gestorías y no
los 409, sin pantalla, IP = la de más a la derecha de `X-Forwarded-For`, antes/después solo ids y códigos) y el
pendiente de la migración a Supabase («el callback no revoca a `authenticator` → va en la tarea de auditoría»).

**Trabajo en paralelo:** Álvaro trabaja en la rama `feat/mistral` (IA por REST contra Mistral). Según lo acordado **no
toca migraciones** ni los ficheros de esta tarea (callback, scripts 01/02, `auditoria`, servicios de escritura). Aun así:
- esta tarea **reserva V24 y V25**; si `feat/mistral` necesitara una migración, usa V26 o posterior y se coordina antes;
- `ExtraccionTramiteService` / `ExtraccionTramiteScheduler` **se tocan aquí** (filas de auditoría del planificador de
  extracción). Si Mistral cambia `TramiteExtractionService` (la interfaz del proveedor) no hay choque; si cambia
  `ExtraccionTramiteService`, habrá un conflicto de fusión pequeño: quien fusione después lo resuelve, sin perder las
  llamadas a `AuditoriaService` (las vigila el inventario cerrado, T6).

---

## Comprobaciones hechas al preparar el plan

- **`auditoria` ya está en la lista «solo inserción»**: `solo_insercion constant text[] := ARRAY['auditoria']` en
  `db/postgresql/afterMigrate__seguridad_esquema.sql` y `solo_insercion(tabla) AS (VALUES ('auditoria'))` en
  `backend/supabase/02-comprobar-seguridad.sql`. Hoy esa clase da a `ganera_app` solo `SELECT, INSERT` y dos políticas
  (`ganera_app_lectura`, `ganera_app_insercion`); nunca `UPDATE`, `DELETE` ni `TRUNCATE`. El nombre de la tabla se
  queda en `auditoria` (no hay que tocar las dos listas).
- **El callback no revoca a `authenticator`**: `ajenos = 'PUBLIC, anon, authenticated, service_role'`. El 02 **ya**
  marca cualquier privilegio de `authenticator` (línea 11 y la cadena de roles), pero migrar no lo deshace.
- **Las «funciones» del callback ya están preparadas**: quita `EXECUTE` de todas las funciones del esquema a los ajenos.
  La función del trigger no necesita `EXECUTE` para `ganera_app`: PostgreSQL comprueba `EXECUTE` al **crear** el
  trigger (lo hace el propietario), no al dispararse.
- **`GestoriaScopedEntity`** trae `id`, `gestoria` (`updatable = false`) y `createdAt = Instant.now()`.
  `TramiteEvento` ya resolvió el «`creado_en` con el `Clock`»: `@AttributeOverride(name = "createdAt", column =
  @Column(name = "creado_en"))` y el servicio lo fija con el `Clock` truncado a microsegundos. Se repite aquí.
- **Hoy no existe ningún 403 «de otra gestoría»**: el acceso a un recurso de otra gestoría responde **`404` sin
  cuerpo**, igual que uno que no existe (A1, decisión 8). «Auditar los 403» significa auditar **el intento de acceso a
  un recurso de otra gestoría**; la respuesta **no cambia** (sigue `404`). Ver D3.
- **`TenantIsolationEndToEndTest` no cierra su inventario por reflexión**: es una lista documentada en el Javadoc. Para
  que «una escritura nueva sin auditar haga fallar los tests» hace falta algo más fuerte (T6).
- **Inventario de escrituras de hoy** (leído del código: `@PostMapping`/`@PutMapping`/`@PatchMapping`/`@DeleteMapping`,
  `@Scheduled`, `@Modifying`, `executeUpdate`, `save*`/`delete`). Además de lo que lista el encargo hay escrituras que
  no estaban en él y **entran**: `PATCH /tramites/{id}`, los siete endpoints de `/contactos`, el `409` de aprobar que
  **confirma** la re-resolución de crotales (`ResolucionCrotalesCambiadaException`, `noRollbackFor`), el guardado de
  `explotacionesContratadas` del checkout del registro y el estado final de una respuesta de WhatsApp (segunda
  transacción). La tabla completa está en «Cobertura».
- La skill `nueva-entidad-tenant` (punto 4) dice que el repositorio «no necesita añadir `gestoriaId`»: contradice
  `CLAUDE.md` (toda consulta lleva el `gestoriaId` explícito). **Prevalece `CLAUDE.md`**; ver duda A6.

## Decisiones que te pido (mi recomendación primero)

- **D1 — Migraciones: V24 común + V25 solo PostgreSQL.** `db/migration/V24__auditoria.sql` crea la tabla con tipos
  portables (la ven H2 y PostgreSQL); `db/postgresql/V25__auditoria_inmutable.sql` crea la función y el trigger (en H2
  queda el hueco de V25, que Flyway acepta: regla de numeración global de `db/{vendor}`). *Alternativa:* el trigger
  dentro del callback (se recrea si alguien lo borra), pero `CREATE TRIGGER` toma un bloqueo `SHARE ROW EXCLUSIVE` y
  el callback se ejecuta en cada arranque; preferible versionado + vigilancia (D2).
- **D2 — El callback falla cerrado si `auditoria` existe sin sus dos triggers activos** (`tgenabled = 'O'`, BEFORE;
  `auditoria_sin_cambios` UPDATE + TRUNCATE por sentencia, `auditoria_sin_borrado` DELETE por fila): la app no arranca si alguien lo ha borrado o desactivado. El 02 lo comprueba
  también (una fila por problema). Sin recrearlo en silencio.
- **D3 — Acceso a otra gestoría (los «403»):** se audita **solo cuando el recurso existe en otra gestoría**, con
  `existsByIdAndGestoriaIdNot(id, gestoriaIdDelJwt)` (consulta con el `gestoriaId` explícito, devuelve un booleano,
  nunca datos). La fila va a la gestoría **del actor** (el usuario que lo intentó), con `entidad_tipo`/`entidad_id` del
  recurso ajeno y la operación como código; **nunca** el id de la gestoría ajena. Respuesta igual que hoy (`404` sin
  cuerpo). *Alternativas:* auditar también ids inexistentes (detectaría enumeración, pero llena la tabla de ruido y un
  id que no existe no es «otra gestoría»); o escribir la fila en la gestoría víctima (le revelaría el `usuario_id` de
  otra gestoría si algún día hay pantalla).
- **D4 — Lecturas sensibles auditadas (REQUIRES_NEW):** `GET /tramites/{id}` (texto del mensaje, NIF del titular,
  contacto; es también la ficha OVZ), `GET /ganaderos/{id}` (teléfonos de los contactos sin enmascarar) y `GET
  /contactos` (teléfonos). **No** los listados de trámites, explotaciones, ganaderos ni animales (sin texto ni
  teléfonos), ni `/auth/me`, ni `/facturacion/suscripcion`.
- **D5 — IP (se reabre T2):** función pura propia, **sin** `server.forward-headers-strategy` (ver «IP del cliente»).
  Propiedad `ganera.auditoria.ip-desde-x-forwarded-for`: `false` por defecto (local: `getRemoteAddr()`, funciona sin
  cabecera), `true` **solo** en `application-clever.yml`, sin placeholder (como `hsts-siempre`).
- **D6 — Retención en bloque por gestoría:** las tres sentencias de `RetencionMensajesService` pasan a ejecutarse
  **por gestoría** (una transacción por gestoría y sentencia, con su fila de auditoría con el número de filas). Los
  mensajes sin gestoría (número desconocido, `ERROR_RECEPCION`) se siguen borrando en una sentencia aparte **sin**
  auditoría (fuera por decisión tuya).
- **D7 — Callback de estado de Twilio:** dentro de la misma transacción, `SELECT … FOR UPDATE` de una **proyección**
  (`id`, `gestoriaId`, `estado`) por SID y luego el mismo `UPDATE` condicional. Da el estado anterior exacto (hoy el log
  solo da el conjunto de posibles) y la gestoría para la fila. Es la **misma** consulta sin tenant que ya existe por SID
  (la segunda deliberada, junto a `findByTelefono`), no una nueva: se documenta en `CLAUDE.md`. *Alternativa:* dejar el
  `UPDATE` solo y, si toca 1 fila, leer la proyección después (estado anterior = el conjunto de posibles).
- **D8 — Qué cabe en `antes`/`despues`:** un objeto JSON con claves de un vocabulario fijo y valores solo de estos
  tipos: id (`Long`), código (`enum`), número entero (recuentos, `version`), booleano, instante (`Instant`, p. ej. el
  límite de una retención) y listas de ids. **Ningún `String` libre** (lo impide el tipo, ver «AuditoriaService»): ni
  textos, ni teléfonos, ni nombres, ni NIF, ni crotales, ni ids de Stripe/Twilio, ni nada cifrado.
- **D9 — Importación de Excel:** una fila por entidad **creada o cambiada** dentro de la transacción `REQUIRES_NEW` de su
  fila del Excel (si la fila falla, no queda auditoría de ella) + una fila resumen `IMPORTACION_EXCEL` con los recuentos
  de las tres hojas. Una re-importación idéntica solo deja el resumen. *Alternativa:* solo el resumen (menos filas, pero
  no se sabe qué entidad cambió).
- **D10 — `request_id`:** UUID generado por Ganera en cada petición (nunca el de una cabecera entrante), también en el
  MDC de los logs y devuelto en la cabecera `X-Request-Id` para soporte. *Alternativa:* solo en la fila (sin MDC ni
  cabecera).

## Diseño

### Tabla `auditoria` (V24, común H2/PostgreSQL)

```sql
CREATE TABLE auditoria (
    id BIGSERIAL PRIMARY KEY,
    gestoria_id BIGINT NOT NULL,
    actor_tipo VARCHAR(20) NOT NULL,
    usuario_id BIGINT,
    accion VARCHAR(60) NOT NULL,
    entidad_tipo VARCHAR(40) NOT NULL,
    entidad_id BIGINT,                 -- NULL en filas en bloque (retención) y en listados
    antes VARCHAR(4000),
    despues VARCHAR(4000),
    ip VARCHAR(45),                    -- NULL en planificadores
    request_id VARCHAR(36),            -- NULL en planificadores
    creado_en TIMESTAMP NOT NULL,      -- Instant del Clock, micros; sin DEFAULT now()
    CONSTRAINT fk_auditoria_gestoria FOREIGN KEY (gestoria_id) REFERENCES gestoria(id),
    CONSTRAINT fk_auditoria_usuario FOREIGN KEY (usuario_id) REFERENCES usuario(id),
    CONSTRAINT ck_auditoria_actor_tipo CHECK (actor_tipo IN
        ('USUARIO', 'SISTEMA', 'WEBHOOK_TWILIO', 'WEBHOOK_STRIPE', 'PLANIFICADOR')),
    CONSTRAINT ck_auditoria_usuario CHECK ((actor_tipo = 'USUARIO') = (usuario_id IS NOT NULL))
);
CREATE INDEX idx_auditoria_gestoria_creado ON auditoria (gestoria_id, creado_en);
CREATE INDEX idx_auditoria_entidad ON auditoria (entidad_tipo, entidad_id);
```

- Sin `CHECK` sobre `accion`/`entidad_tipo` (cada acción nueva obligaría a una migración); los cierra el `enum` de Java y
  el inventario (T6).
- `VARCHAR(4000)` y no `jsonb`/`TEXT`: portable y acotado. El constructor de datos limita las listas (máx. 50 ids y
  `"truncado": true`) para no pasar nunca de 4000: **una fila de auditoría demasiado larga no puede tumbar la escritura
  de negocio**.
- FK sin `ON DELETE`: cualquier acción en cascada sería un `UPDATE`/`DELETE` que el trigger rechaza. Consecuencia: una
  gestoría o un usuario con filas de auditoría no se pueden borrar (hoy no se borran).
- **Entidad `Auditoria`** (paquete `shared/auditoria`): extiende `GestoriaScopedEntity`, `@AttributeOverride` a
  `creado_en`, `@org.hibernate.annotations.Immutable`, todas las columnas `updatable = false`.
  **`AuditoriaRepository extends Repository<Auditoria, Long>`** (no `JpaRepository`): solo `save` y lecturas con el
  `gestoriaId` explícito (`findByGestoriaIdOrderByIdAsc`, para los tests). No hay `delete*` que llamar.

### Inmutabilidad en PostgreSQL (V25, `db/postgresql`)

```sql
-- UPDATE y TRUNCATE: nunca, por sentencia (también un UPDATE … WHERE false).
CREATE FUNCTION auditoria_sin_cambios() RETURNS trigger LANGUAGE plpgsql SET search_path = pg_catalog AS $$
BEGIN
    RAISE EXCEPTION 'La tabla auditoria solo admite INSERT (% rechazado).', TG_OP;
END $$;
CREATE TRIGGER auditoria_sin_cambios BEFORE UPDATE OR TRUNCATE ON auditoria
    FOR EACH STATEMENT EXECUTE FUNCTION auditoria_sin_cambios();

-- DELETE: hoy nunca; por fila, para que un futuro proceso de retención pueda decidir fila a fila (A1).
CREATE FUNCTION auditoria_sin_borrado() RETURNS trigger LANGUAGE plpgsql SET search_path = pg_catalog AS $$
BEGIN
    RAISE EXCEPTION 'La tabla auditoria solo admite INSERT (DELETE rechazado).';
END $$;
CREATE TRIGGER auditoria_sin_borrado BEFORE DELETE ON auditoria
    FOR EACH ROW EXECUTE FUNCTION auditoria_sin_borrado();
```

- **Dos triggers a propósito (A1):** `UPDATE` y `TRUNCATE` quedan prohibidos para siempre (por sentencia; `TRUNCATE`
  solo admite triggers por sentencia). El `DELETE` va aparte y **por fila**, para que el día que el abogado fije el plazo
  una migración nueva solo tenga que sustituir `auditoria_sin_borrado()` por una versión que deje pasar la fila si
  `current_user = 'ganera_propietario'`, la sesión lo marca como proceso de retención (p. ej. `SET LOCAL
  ganera.retencion_auditoria = 'on'`, que solo puede fijar quien ejecuta el proceso) y `OLD.creado_en` es anterior al
  plazo; todo lo demás, excepción. Ni la tabla, ni el otro trigger, ni los permisos de `ganera_app` cambian. **No se
  implementa ahora**; queda documentado aquí, en `CLAUDE.md` y en el comentario de V25. Un `DELETE` de 0 filas no
  dispara el trigger por fila (no borra nada: aceptable).
- Capas: `ganera_app` no tiene `UPDATE`/`DELETE`/`TRUNCATE` (callback, «solo inserción») → `permission denied`; el
  propietario (`ganera_propietario`, Flyway, SQL a mano) sí tiene el privilegio, y lo para el trigger. `TRUNCATE gestoria
  CASCADE` también lo para (los triggers de TRUNCATE se disparan en las tablas en cascada).
- **Límites que quedan (documentados, no se cierran aquí):** el propietario puede `DROP TRIGGER`, `ALTER TABLE …
  DISABLE TRIGGER` o `DROP TABLE` (S7, `DROP SCHEMA ganera CASCADE`, lo necesita para vaciar la base antes de los datos
  reales); D2 hace que la app no arranque así y el 02 lo marca. `session_replication_role = replica` salta triggers,
  pero exige superusuario (el `postgres` de Supabase no lo es); el 02 marca un `GRANT SET ON PARAMETER
  session_replication_role` a cualquier rol de Ganera o de Supabase.

### Callback y 02

- **Callback:** `ajenos` se construye en tiempo de ejecución y añade `authenticator` **si existe**
  (`to_regrole('authenticator') IS NOT NULL`; en local/docker puede no existir). Todo lo demás igual. Más la
  comprobación D2 (si existe la tabla `auditoria` y no tiene el trigger activo y exacto → `RAISE EXCEPTION`).
- **02:** filas nuevas: «falta la tabla auditoria» (si no existe tras V24), «falta el trigger / está desactivado / no
  cubre UPDATE, DELETE y TRUNCATE / no es BEFORE por sentencia / la función no es de `ganera_propietario`», y el
  `SET ON PARAMETER session_replication_role`. Lo de `authenticator` ya lo vigila; se añade el caso hostil de que una
  concesión a mano **desaparece** tras migrar.

### `AuditoriaService` (paquete `shared/auditoria`)

```java
auditoria.registrar(AccionAuditoria.TRAMITE_APROBADO, gestoriaId, Actor.usuario(principal.usuarioId()),
        EntidadAuditada.TRAMITE, tramite.getId(),
        DatosAuditoria.con(Clave.ESTADO, EstadoTramite.PENDIENTE_REVISION).y(Clave.VERSION, 3),
        DatosAuditoria.con(Clave.ESTADO, EstadoTramite.APROBADO).y(Clave.VERSION, 4));
```

- `registrar(...)` — `@Transactional(propagation = MANDATORY)`: **exige** la transacción de la escritura (si alguien lo
  llama fuera de una, falla en el test, no se pierde en silencio).
- `registrarAparte(...)` — `@Transactional(propagation = REQUIRES_NEW)`: solo para lecturas sensibles (D4) y accesos a
  otra gestoría (D3), que deben quedar aunque la operación termine en `404`/rollback. Al no haber transacción exterior en
  un GET, usa el `EntityManager` de OSIV; en un PATCH con transacción abierta abre uno nuevo (sin `gestoriaFilter`, que
  la inserción no necesita).
- `Actor`: `usuario(id)`, `sistema()`, `webhookTwilio()`, `webhookStripe()`, `planificador()`. **La gestoría siempre la
  pasa quien llama desde la entidad tocada** (planificadores y webhooks: `tramite.getGestoria().getId()`,
  `suscripcion.getGestoria().getId()`…), nunca de un contexto. Con `Actor.usuario`, el servicio comprueba que el usuario
  es de esa gestoría (con `UsuarioRepository.findByIdAndGestoriaId`), salvo en `ACCESO_OTRA_GESTORIA`, donde la gestoría
  es la del actor por definición.
- `DatosAuditoria`: inmutable, métodos `y(Clave, Long|Integer|Boolean|Enum<?>|Instant|List<Long>)`, **sin sobrecarga
  `String`, `Object` ni `CharSequence`** (un test por reflexión lo vigila). JSON canónico con Jackson (claves
  ordenadas). `Clave` es un `enum` (vocabulario cerrado).
- `ip` y `request_id` los pone el servicio desde `ContextoPeticion` (abajo); en un hilo sin petición quedan `NULL`.
- `creado_en` = `Instant` del `Clock` truncado a micros.

### IP del cliente y `request_id` (se reabre T2)

- **`ContextoPeticionFilter`** (`OncePerRequestFilter`, `Ordered.HIGHEST_PRECEDENCE`, antes de `RegistroCerradoFilter`,
  `LimiteCuerpoCallbackEstadoFilter` y Spring Security): calcula la IP y un UUID, los guarda en un `ThreadLocal`
  (`ContextoPeticion`) y en el MDC, añade `X-Request-Id` a la respuesta (D10) y lo limpia **siempre** en `finally`.
- **`IpCliente.de(remoteAddr, cabecerasXff, confiarEnXff)`**, pura:
  - `confiarEnXff = false` (por defecto, local y tests): `remoteAddr`. Funciona sin cabecera.
  - `true` (solo `clever`): todas las cabeceras `X-Forwarded-For` en orden, unidas por comas; se toma el **último**
    elemento no vacío; se acepta solo si es una IP literal IPv4 o IPv6 (analizador propio, **nunca**
    `InetAddress.getByName`, que resolvería DNS); se quitan corchetes y puerto (`[::1]:443`, `1.2.3.4:5678`). Si no
    hay cabecera o el último elemento no es una IP → `remoteAddr` (la IP del proxy de Clever: dato real, aunque no sea el
    del cliente) y un `WARN` sin el valor de la cabecera.
- **Por qué no `forward-headers-strategy`:** `framework` (`ForwardedHeaderFilter`) confía en **cualquier**
  `X-Forwarded-*` del cliente y además cambia esquema, host y puerto de la petición; `native` (`RemoteIpValve`) toma la
  de más a la derecha que no sea de un proxy interno, pero exige conocer las IP de los proxies de Clever (no publicadas
  como lista estable) y también reescribe `isSecure()`/esquema. Nada de Ganera lee hoy esquema/host (Twilio valida
  contra URLs configuradas, HSTS se manda siempre en `clever`): con una función propia la IP solo entra en la
  auditoría y **nunca** decide nada de seguridad (ni login, ni límites).
- **Suplantación:** un cliente puede mandar su propio `X-Forwarded-For: 1.1.1.1`; Clever añade la IP real a la derecha,
  así que se guarda la real. Solo es fiable si **Clever siempre añade** la cabecera y **no hay otro proxy delante**
  (Cloudflare, o un frontend en Vercel que reenviara la API con `rewrites`; hoy el navegador llama a `api.ganera.es`
  directamente). Se comprueba en el smoke real (T7).

## Cobertura (inventario cerrado)

`G` = gestoría de la fila. Todas con `ip` y `request_id` salvo los planificadores.

| Origen | Acción(es) | Actor | `G` sale de | Transacción |
|---|---|---|---|---|
| `PATCH /tramites/{id}` | `TRAMITE_EDITADO` (explotacionId, tipo, animalIds, número de crotales, version) | USUARIO | trámite | la del PATCH |
| `POST /tramites/{id}/aprobar` | `TRAMITE_APROBADO`; en el `409` que confirma la re-resolución: `TRAMITE_CROTALES_RERESUELTOS` | USUARIO | trámite | la de aprobar (`noRollbackFor`) |
| `POST …/rechazar` | `TRAMITE_RECHAZADO` | USUARIO | trámite | la misma |
| `POST …/marcar-hecho-ovz`, `…/desmarcar-hecho-ovz` | `TRAMITE_MARCADO_HECHO_OVZ`, `TRAMITE_DESMARCADO_HECHO_OVZ` | USUARIO | trámite | la misma |
| `POST …/descartar`, `…/deshacer-descarte` | `TRAMITE_DESCARTADO`, `TRAMITE_DESCARTE_DESHECHO` (además de `tramite_evento`, que se queda) | USUARIO | trámite | la misma |
| `POST …/respuestas-whatsapp` | `RESPUESTA_WHATSAPP_CREADA` (ENVIANDO) y `RESPUESTA_WHATSAPP_RESULTADO` (estado, código de error) | USUARIO | trámite | las dos transacciones cortas; la repetición con la misma clave no escribe → sin fila |
| `/contactos` (POST, PUT, DELETE, reactivar, POST/DELETE explotaciones) | `CONTACTO_CREADO`, `CONTACTO_MODIFICADO` (campos cambiados como códigos), `CONTACTO_DESACTIVADO`, `CONTACTO_REACTIVADO`, `CONTACTO_VINCULADO`, `CONTACTO_ROL_CAMBIADO`, `CONTACTO_DESVINCULADO` | USUARIO | contacto | la del servicio; un DELETE de uno ya inactivo no escribe → sin fila |
| `POST /explotaciones/importar` | por fila: `GANADERO_*`, `EXPLOTACION_*`, `ANIMAL_*`, `CONTACTO_CREADO`/`_VINCULADO`/`_ROL_CAMBIADO` (`origen: IMPORTACION`); al final `IMPORTACION_EXCEL` (recuentos) | USUARIO | JWT = entidad | `REQUIRES_NEW` de cada fila; el resumen en la suya |
| `POST /internal/onboarding/gestoria` | `GESTORIA_CREADA`, `USUARIO_CREADO`, `SUSCRIPCION_CREADA` | SISTEMA | la gestoría creada | la del controlador |
| `POST /gestorias/registro` (abierto solo en su test) | `GESTORIA_CREADA`, `USUARIO_CREADO` (`origen: REGISTRO`); en el checkout `SUSCRIPCION_CREADA` (TRIAL) y `SUSCRIPCION_CANTIDAD_FIJADA` | SISTEMA | la creada | las suyas |
| `POST /webhooks/twilio/whatsapp` | `MENSAJE_RECIBIDO` (resultado, numMedia; entidad `MENSAJE_CAMPO`) + `TRAMITE_CREADO` (estado, estadoExtraccion, explotacionId); contacto inactivo: solo `MENSAJE_RECIBIDO` | WEBHOOK_TWILIO | contacto | la de recepción. **Fuera:** número desconocido, `ERROR_RECEPCION`, duplicados |
| `POST /webhooks/twilio/whatsapp/estado` | `RESPUESTA_WHATSAPP_ESTADO_ACTUALIZADO` (antes/después exactos, D7) | WEBHOOK_TWILIO | respuesta | la del `UPDATE` por SID; sin cambio → sin fila |
| `POST /webhooks/stripe` | `SUSCRIPCION_ESTADO_CAMBIADO` (estado, explotacionesContratadas); evento antiguo o sin cambio → sin fila | WEBHOOK_STRIPE | suscripción | la del servicio |
| `SuscripcionSyncScheduler` | `SUSCRIPCION_CANTIDAD_SINCRONIZADA` | PLANIFICADOR | suscripción | por suscripción (un fallo no para las demás) |
| `ExtraccionTramiteScheduler` | `EXTRACCION_APLICADA`, `EXTRACCION_FALLIDA` (primer fallo), `EXTRACCION_INTENTO_FALLIDO` (UPDATE de reintentos sin versión), `EXTRACCION_REINTENTOS_DETENIDOS` (`dejarDeReintentarExtraccion`) | PLANIFICADOR | trámite | la de cada paso |
| `RetencionMensajesScheduler` (D6) | `RETENCION_MENSAJES_BORRADOS`, `RETENCION_TEXTO_VACIADO`, `RETENCION_RESPUESTAS_VACIADAS` (`filas`, `limite`; `entidad_id` NULL) | PLANIFICADOR | gestoría del grupo | una por gestoría y sentencia; 0 filas → sin fila. **Fuera:** los mensajes sin gestoría |
| `POST /auth/login` (A2) | `LOGIN_CORRECTO`, `LOGIN_FALLIDO` (motivo como código: contraseña, usuario inactivo); email inexistente → sin fila, log sin el email | USUARIO (el del email) | usuario | `REQUIRES_NEW` (el `401` no escribe nada más) |
| `GET /tramites/{id}`, `GET /ganaderos/{id}`, `GET /contactos` (D4) | `LECTURA_TRAMITE`, `LECTURA_GANADERO`, `LECTURA_CONTACTOS` | USUARIO | JWT | `REQUIRES_NEW` |
| Todo endpoint con un id de otra gestoría en la ruta o en el cuerpo (`explotacionId` del PATCH y de vincular) (D3) | `ACCESO_OTRA_GESTORIA` (operación como código) | USUARIO | **la del actor** | `REQUIRES_NEW` |

**Escrituras en bloque** (las pide el encargo explícitamente): el `UPDATE` forzado de `version` de PATCH/aprobar no
tiene fila propia, va dentro de la acción (`TRAMITE_EDITADO`, `TRAMITE_CROTALES_RERESUELTOS`), y su test lo comprueba;
`registrarIntentoFallidoSinCambiarVersion` y `dejarDeReintentarExtraccion` → filas del planificador de extracción;
`actualizarEstadoPorSid` → D7; las tres de retención → D6.

**Exentos, con su razón en el inventario:** todos los GET no sensibles,
`/error`, `POST /facturacion/checkout` (no existe: `404`).

## Tareas (Superpowers: un implementador + un revisor independiente por tarea; TDD: rojo → verde; cada guarda de
seguridad con su mutación demostrada y restaurada con `cp` + `cmp`, nunca con git)

### T1 — Tabla, entidad, servicio y contexto de petición (≈1 día)

- V24; `Auditoria`, `AuditoriaRepository`, `AccionAuditoria`, `EntidadAuditada`, `Actor`, `Clave`, `DatosAuditoria`,
  `AuditoriaService` (`registrar` MANDATORY, `registrarAparte` REQUIRES_NEW); `ContextoPeticion`,
  `ContextoPeticionFilter`, `IpCliente`; la propiedad de D5 en `application.yml` (false) y `application-clever.yml`
  (true).
- Tests nuevos: `IpClienteTest` (pura: sin cabecera, una, varias cabeceras, el cliente mete una falsa a la izquierda,
  IPv6, con puerto, basura, vacía, `false` ignora la cabecera — ≈14), `DatosAuditoriaTest` (JSON canónico, tipos
  permitidos, límite de listas, **reflexión: ningún método acepta `String`/`Object`/`CharSequence`** — ≈8),
  `AuditoriaServiceTest` (`@DataJpaTest`: MANDATORY sin transacción falla, REQUIRES_NEW sobrevive al rollback exterior,
  usuario de otra gestoría rechazado, `creado_en` del `Clock` — ≈7), `AuditoriaRepositoryTest` (reflexión: sin métodos
  de borrado/actualización — 1), `ContextoPeticionFilterEndToEndTest` (`X-Request-Id` en la respuesta, distinto por
  petición, `ThreadLocal` limpio después, también en `404`/`401`/`413` — ≈5), `ConfiguracionSeguridadProduccionTest` +1
  (la propiedad es `true` en `clever`, sin placeholder). **≈36.**

### T2 — Inmutabilidad en PostgreSQL, callback y 02 (≈1 día; en paralelo a T3 si hay dos implementadores)

- V25 en `db/postgresql`; callback: `authenticator` condicional + D2; 02: las filas nuevas.
- Tests nuevos (embebido 17.11, `SeguridadEsquemaPostgresTest` y uno nuevo `AuditoriaInmutablePostgresTest`; los dos
  triggers de A1, cada uno con su mutación):
  `ganera_app` INSERT/SELECT sí; UPDATE, DELETE y TRUNCATE → `permission denied`; el propietario: UPDATE, DELETE,
  `UPDATE … WHERE false` y TRUNCATE → excepción del trigger; `TRUNCATE gestoria CASCADE` → lo para; el callback falla
  si el trigger falta o está desactivado; concesión hostil a `authenticator` (tabla, secuencia, esquema) → migrar la
  quita y el 02 da 0 filas; callback sin rol `authenticator` → migra igual; 02: una fila por cada problema nuevo; H2
  migra V24 sin V25 (`FlywayH2VendorTest` +1). Los tests que migran «hasta la última» esperan ahora V1–V25 (V25 solo en
  PostgreSQL). **≈18.**

### T3 — Escrituras de usuario (≈1,5 días)

- `TramiteRevisionService` (PATCH, aprobar + el 409 que confirma, rechazar, marcar/desmarcar, descartar/deshacer),
  `RespuestaWhatsappService`, `ContactoService`, importación (`ExplotacionImportFilaService` + resumen) y el login (A2:
  correcto y fallido con email existente; el `401` sigue byte a byte igual en los tres casos y el log del email
  inexistente no lleva el email).
- Tests: un caso por acción en un `AuditoriaUsuarioEndToEndTest` (`@SpringBootTest` RANDOM_PORT, dos gestorías): la
  fila exacta (acción, actor, usuario, gestoría, entidad, antes/después, `ip` = `127.0.0.1`, `request_id` = la cabecera
  de la respuesta) y **ninguna** fila en la otra gestoría; casos sin escritura (repetición idempotente, DELETE de un
  inactivo, re-importación idéntica, 409 de versión) → sin fila; un rollback (fila del Excel que falla) → sin fila de
  esa fila. **≈30.**

### T4 — Sistema, webhooks y planificadores (≈1,5 días)

- Onboarding, registro + checkout, webhook de Twilio (recepción), callback de estado (D7), Stripe, sincronización de
  suscripciones, extracción (cuatro acciones), retención por gestoría (D6).
- Tests: `AuditoriaProcesosTest` — un caso por acción, con la gestoría sacada **de la entidad** (se comprueba con dos
  gestorías y datos cruzados: el planificador procesa las dos y cada fila cae en la suya), `ip`/`request_id` NULL en los
  planificadores; fuera de auditoría: número desconocido, `ERROR_RECEPCION`, duplicado, evento Stripe antiguo, callback
  sin cambio, retención de mensajes sin gestoría; la retención por gestoría da los mismos totales que hoy (los tests
  actuales de retención siguen en verde) y en PostgreSQL embebido (`RetencionPorGestoriaPostgresTest`). **≈28.**

### T5 — Lecturas sensibles y accesos a otra gestoría (≈1 día)

- `existsByIdAndGestoriaIdNot` en los repositorios de `Tramite`, `Contacto`, `Explotacion`, `Ganadero`; un componente
  `AccesoAjeno` que los controladores llaman al devolver `404`; las tres lecturas de D4.
- Tests: por cada endpoint con id (≈20 rutas, parametrizado): id de otra gestoría → `404` **idéntico byte a byte** al
  de un id inexistente + una fila `ACCESO_OTRA_GESTORIA` en la gestoría del actor (y ninguna en la víctima); id
  inexistente → sin fila; la fila sobrevive al rollback del PATCH/aprobar; lecturas: una fila por GET, también con la
  respuesta servida, ninguna en el listado de trámites. **≈30** (contando cada caso del parametrizado).

### T6 — Inventario cerrado (≈0,5–1 día)

- `InventarioAuditoriaTest` (`@SpringBootTest`): saca del contexto **todas** las rutas
  (`RequestMappingHandlerMapping`), todos los métodos `@Scheduled` de los beans y todos los métodos `@Modifying` de los
  repositorios, y los compara con `InventarioEscrituras` (lista en el test: cada entrada «auditada → nombre del test que
  la exige» o «exenta → razón»). **Una ruta, planificador o `@Modifying` nuevo que no esté → rojo.** Además comprueba
  por reflexión que cada test nombrado existe.
- **Guardia de DML** (`InspectorDml`, un `StatementInspector` de Hibernate solo en tests, que anota cada
  `insert`/`update`/`delete` por tabla): en cada escenario de T3–T5, si se escribió en una tabla de negocio, tiene que
  haber al menos un `insert into auditoria` en la misma petición/pasada. Así una escritura nueva **dentro** de un
  endpoint ya auditado también pone rojo.
- Mutaciones demostradas: quitar una llamada a `registrar` → rojo su test; añadir un `@PostMapping` de prueba → rojo el
  inventario; un `save` nuevo en un endpoint auditado sin auditoría → rojo la guardia de DML. **≈4** (+ los casos del
  inventario si se cuentan como parametrizados).

### T7 — Documentación y smoke real (≈0,5 día + Antonio)

- `CLAUDE.md` (bullet «Auditoría», regla «toda escritura nueva lleva su fila y su entrada en el inventario», la
  consulta por SID de D7, `existsByIdAndGestoriaIdNot`, la propiedad de IP y «nunca `GANERA_AUDITORIA_*` en Clever»),
  `progress.md`, `ganera-prompts.md`, skill `nueva-entidad-tenant` (si apruebas A6), y el kit
  `C:\Users\Antonio\Desktop\ganera-clever\` (el `seed-tramites.sql` no inserta en `auditoria`; el 02 nuevo).
- **Smoke (Antonio, contra Supabase con la app local `clever` como en T5 de Supabase, y luego en Clever):** migra V24 y
  V25, el 02 a 0 filas, `UPDATE auditoria` como propietario en el editor SQL → error del trigger, como `ganera_app` →
  permiso denegado; **en Clever:** `curl -H "X-Forwarded-For: 1.1.1.1"` a un endpoint auditado → la fila guarda la IP
  pública real de Antonio, no `1.1.1.1` ni la del proxy. Si sale la del proxy o la falsa: parar.

## Cifras esperadas

- Backend: **1075 → ≈1220** (≈1219 + 1 omitido), contando los parametrizados por caso. Ningún test actual debería
  cambiar de resultado salvo los que esperan «V1–V23» (pasan a V1–V24 en H2 y V1–V25 en PostgreSQL) y los de retención
  si cambian sus consultas (mismo resultado, otra firma).
- Frontend: **1084 en 50, sin tocar** (la cabecera `X-Request-Id` no la lee nadie). Lint con los 3 avisos conocidos.

## Dudas abiertas

- **A1 — Plazo de conservación de `auditoria`:** sin plazo; lo decide el abogado. Se apunta en «Para el abogado» de
  `progress.md`, junto con que la fila guarda la **IP** (dato personal) y el `usuario_id`, y que **no se puede borrar**
  (el plazo exigirá un procedimiento con el propietario quitando el trigger a mano, auditado fuera).
- **A2 — ¿Se audita el login** (correcto y fallido)? Fuera del encargo. Un login fallido con email desconocido no tiene
  gestoría (la tabla la exige). Propuesta: tarea aparte si se quiere.
- **A3 — ¿Valen recuentos e instantes en `antes`/`despues`** (D8)? No son ids ni códigos, pero no son datos personales;
  sin ellos las filas en bloque (retención, importación) no dicen nada.
- **A4 — Coste del parametrizado de T5** (≈20 rutas con dos gestorías y servidor real): añade ≈1–2 minutos a la suite.
- **A5 — Cuando llegue el número de WhatsApp por gestoría** (tarea siguiente a M2), `NUMERO_DESTINO_DESCONOCIDO` también
  quedará fuera de la auditoría, como el número desconocido.
- **A6 — Corregir la skill `nueva-entidad-tenant`** (punto 4 contradice `CLAUDE.md`) en T7 y añadirle «y su fila de
  auditoría».
- **A7 — Clever y `X-Forwarded-For`:** no tengo la documentación de Clever a mano para confirmar que su balanceador
  **añade** (y no sustituye) la cabecera; lo resuelve el smoke de T7 antes de dar la IP por buena.

## Estimación y orden

T1 → (T2 ∥ T3) → T4 → T5 → T6 → T7. **≈6–7 días** de trabajo + el smoke de Antonio. Sin commit ni push sin tu
aprobación; staging y commits desde la sesión principal, fichero a fichero.
