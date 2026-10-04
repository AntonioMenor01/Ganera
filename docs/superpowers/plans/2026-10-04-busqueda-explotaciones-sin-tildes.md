# Búsqueda de explotaciones sin tildes y por palabras — plan

Fecha: 2026-10-04. Base: `main` = `7a5b58e`. **Solo `backend/`**: el contrato de
`GET /explotaciones?q=` no cambia (mismos parámetros, mismo DTO, mismos `400`) y el frontend no se
toca. Suite de partida: backend 472, frontend 506.

Flujo: Superpowers (un implementador + un revisor independiente por tarea), TDD, E2E de dos
Gestorías (`@SpringBootTest` + `TestRestTemplate`) en la búsqueda. Cierre: `./mvnw clean test`,
`npm test` y smoke con `curl` contra dos Gestorías (H2 en fichero, cuerpos con `--data-binary`).
Sin commits sin aprobación de Antonio; stage ruta a ruta.

## Ajustes de Antonio al aprobar el plan (2026-10-04)

Visto bueno a D1–D5 con estos ajustes, que prevalecen sobre el texto de abajo:

- **A1 (V18):** solo JDBC, sin entidades ni repositorios. Puede usar `NormalizadorBusqueda`
  porque es una función pura sin Spring.
- **A2 (palabras de la consulta):** `q` se parte primero por espacios y cada palabra se normaliza
  por separado; una palabra que queda vacía se descarta (`martin - perez` busca `martin` y
  `perez`). Solo si **todas** quedan vacías se devuelve la página vacía. Con test.
- **A3 (consulta):** **nada de `fetch` dentro de la `Specification`** (rompe el count). El Ganadero
  se trae con `@EntityGraph(attributePaths = "ganadero")` en
  `findAll(Specification, Pageable)` redeclarado en `ExplotacionRepository`. Test de recuento de
  consultas (exactamente página + count, ninguna carga suelta de Ganadero) y test de aislamiento
  con dos Gestorías.

## Situación de partida

- `ExplotacionRepository.buscarPorTexto`: JPQL con `lower(campo) like lower(:patron) escape '!'`
  sobre `e.codigoRega`, `e.nombre` y `g.nombre`; `q` entero como una sola cadena.
- Quién escribe esos campos hoy: **solo el importador** (`ExplotacionImportFilaService`).
  Explotación: se crea, o se actualiza `nombre` y `ganadero` por `codigoRega`. Ganadero: **solo se
  crea** (un NIF existente no cambia de nombre al reimportar). No hay endpoint que edite ninguno.
  En el futuro: la sincronización de OVZ (3a) o una pantalla de edición podrían renombrar.

## Decisiones (con recomendación)

### D1. Dónde se guarda lo normalizado

**Recomiendo una columna por entidad**, no una desnormalizada:

- `explotacion.busqueda` = normalizar(`codigoRega`) + `" "` + normalizar(`nombre`).
- `ganadero.nombre_busqueda` = normalizar(`nombre`).
- Consulta, por cada palabra `p`: `(e.busqueda like %p% or g.nombreBusqueda like %p%)`, todas en AND.
  El join con `ganadero` ya existe hoy (es el `join fetch` del DTO), así que no cuesta nada más.

Por qué: cada columna depende **solo de campos de su propia fila**, así que se recalcula en los
setters de su entidad y no puede quedarse desfasada. Con la columna única en `explotacion`,
renombrar un Ganadero obliga a recalcular todas sus explotaciones desde otro sitio (un servicio, un
listener de Hibernate o un `UPDATE` masivo), y cualquier vía nueva que cambie el nombre (3a, una
pantalla futura, un `UPDATE` a mano) la deja obsoleta sin que falle nada: el típico fallo silencioso.
La única ventaja de la columna única, un `like` menos por palabra, no se nota a este volumen.

Mantenimiento: setters explícitos en `Explotacion.setCodigoRega/setNombre` y `Ganadero.setNombre`
que recalculan la columna (Lombok no genera el setter si ya existe). Sin setter público para la
columna. No uso `@PrePersist/@PreUpdate`: un cambio hecho dentro de `@PreUpdate` no siempre se
persiste en Hibernate y depende del orden del dirty checking; el setter es determinista y se prueba
con un test unitario de la entidad.

Columnas `VARCHAR` sin longitud y `NOT NULL` (tras el relleno): NFKD puede alargar un texto
(ligaduras, fracciones), así que no ato la longitud a la del original. Funciona igual en H2 y
PostgreSQL.

### D2. Cómo se rellenan las filas existentes

**Recomiendo una migración Flyway en Java**, `V18__busqueda_normalizada` (clase
`db.migration.V18__busqueda_normalizada` en `src/main/java`, que la `location`
`classpath:db/migration` ya recoge): `ALTER TABLE … ADD COLUMN` (nullable), recorre las filas con
JDBC y las actualiza en lotes con el **mismo** `NormalizadorBusqueda` que usan las entidades, y
después `ALTER COLUMN … SET NOT NULL`. Todo en la misma migración y la misma transacción en
PostgreSQL.

Por qué no al arrancar: un `ApplicationRunner` se ejecuta en cada arranque, recorre toda la tabla
siempre, compite si algún día hay dos instancias, y no puede dejar la columna `NOT NULL` (la
columna tendría que existir antes, vacía). La migración corre una vez, queda registrada en
`flyway_schema_history` y deja el esquema en su estado final.

Consecuencia a anotar en el Javadoc: si algún día cambia la regla de normalización, hace falta una
migración nueva que recalcule (las migraciones Java no tienen checksum de contenido; la V18 no se
vuelve a ejecutar).

Test de la migración: Flyway programático sobre una H2 en memoria propia, `target("17")`, insertar
Gestoría/Ganadero/Explotación con tildes por SQL, migrar al final y comprobar las columnas y el
`NOT NULL`.

### D3. Regla exacta de normalización (`NormalizadorBusqueda`, puro, sin Spring)

En este orden:

0. (Decisión de Antonio tras la revisión de T1.) Se eliminan, sin dejar hueco, los símbolos
   modificadores sueltos (`\p{Sk}`: `´`, `¨`, `¸`, `` ` ``, `^`…) **antes** del NFKD, que si no
   los convierte en espacio + marca y parte la palabra: `Mart´in` → `martin`.
1. `Normalizer.normalize(texto, NFKD)` y quitar las marcas combinantes (`\p{M}`): `á→a`, `ü→u`,
   **`ñ→n`**, **`ç→c`**, `º→o`, `ª→a`, espacio duro (U+00A0) → espacio normal.
2. `toLowerCase(Locale.ROOT)`.
3. **Se eliminan** (sin dejar hueco) todos los caracteres que no sean letra (`\p{L}`), número
   (`\p{N}`) o espacio: guiones, puntos, apóstrofos, barras, `%`, `_`, `!`…
4. Todo el espacio en blanco (Unicode) se colapsa a un solo espacio y se recorta.

Para la consulta (ajuste A2): se parte `q` por espacios, cada palabra se normaliza con la misma
regla, las que quedan vacías se descartan y se quitan las repetidas.

- **ñ y ç se pliegan** (`peña` y `pena` se encuentran mutuamente). Es una búsqueda para encontrar,
  no para distinguir: quien escribe desde un teclado sin ñ, o con prisa, encuentra igual; el falso
  positivo como mucho añade una fila que se descarta a la vista.
- **Guiones y demás puntuación se eliminan, no se convierten en espacio.** Con "contiene" y AND es
  lo más tolerante: `Martín-Pérez` → `martinperez`, que encuentran `martin`, `perez`,
  `martin perez`, `martin-perez` y `martinperez` (si el guion pasara a espacio, `martinperez` no
  lo encontraría). `ES-12` → `es12` encuentra
  `ES123…`. `S.L.` → `sl`. Los campos se unen con espacio, así que una palabra nunca encaja
  "a caballo" entre REGA y nombre.
- **Comodines `%`, `_`, `!`:** se eliminan en el paso 3, así que nunca llegan al `LIKE` como
  comodín. Cambio de comportamiento observable (sin cambio de contrato): hoy `q=50%` busca un `%`
  literal; después equivale a `q=50`. El escape con `!` en el `LIKE` se mantiene como defensa por si
  la regla cambia (coste nulo), y el test de `patronContiene` se conserva.
- **`q` en el que todas las palabras quedan vacías** tras normalizar (p. ej. `q=%%` o `q=---`): **página vacía**
  (nada "contiene" eso), no el listado completo. Solo `q` ausente o en blanco es "sin filtro", como
  hoy.
- El límite de 100 caracteres se sigue comprobando sobre `q` recortado **antes** de normalizar,
  igual que hoy (mismo `400`, mismo orden tras la lista blanca de `sort`).

A sabiendas, no se pliegan letras que NFKD no descompone, ajenas al español (`ß`, `ł`, `ø`, `æ`,
`œ`, `đ`…). `NormalizadorBusqueda` vive en `shared/texto` (revisión de T2, m1: evita el ciclo
`ganadero ↔ explotacion`).

### D4. Límite de palabras e índice

- **Límite: 8 palabras distintas** tras normalizar; más → `400 {motivo}` "La búsqueda admite como
  máximo 8 palabras." (comprobado después de los 100 caracteres). Es un `400` nuevo con la forma que
  el combobox ya enseña tal cual; 8 cubre de sobra "Explotación familia Pérez García ES12…".
  Alternativa: ignorar las palabras sobrantes en silencio (sin `400`, pero amplía resultados sin
  avisar). Sin límite, 100 caracteres dan hasta 50 palabras y 100 `like` por fila.
- **Índice: ninguno.** Un `like '%p%'` no usa un B-tree; la consulta ya está acotada por
  `gestoria_id` (indexado) y a volumen de piloto son cientos o pocos miles de filas por Gestoría.
  Si un día va lento, en PostgreSQL un `pg_trgm` GIN sobre las columnas normalizadas (ya están en
  minúsculas y sin tildes: no hace falta `unaccent` ni función `IMMUTABLE`); se anota en el
  Prompt C. Las columnas ya están en minúsculas, así que la consulta no aplica `lower()`.

### D5. Cómo se construye la consulta

Un número variable de palabras no cabe en un `@Query` fijo. **Recomiendo una `Specification`**
(`JpaSpecificationExecutor<Explotacion>`): `gestoria.id = :gestoriaId` como predicado explícito (no
depende del filtro ambiente) y un `and` por palabra, con un `join` simple al Ganadero para el
predicado; **sin `fetch`** (ajuste A3): el Ganadero llega por `@EntityGraph` en
`findAll(spec, pageable)`, que Spring Data aplica solo a la consulta de datos, no al count. El `Sort` del `Pageable` sigue aplicándose a la raíz. `buscarPorTexto` se elimina.
Alternativa: `@Query` con 8 parámetros fijos rellenando los sobrantes con `%`: menos código, pero
predicados inútiles y más difícil de leer.

## Tareas

**T1 — `NormalizadorBusqueda` (puro, `explotacion` package).** Dos funciones:
`normalizar(String)` (texto de una columna) y `palabras(String q)` (lista de palabras de la
consulta, ajuste A2). Tests primero: tildes, mayúsculas, ñ/ç, diéresis, `º`/`ª`, guiones, puntos,
apóstrofos, `%`/`_`/`!`, espacio duro, espacios múltiples, `null`/vacío/solo puntuación,
`palabras("martin - perez")` = `[martin, perez]`, `palabras("%% --")` = `[]`, repetidas
(`Pérez PEREZ` = `[perez]`).

**T2 — Columnas, setters y migración V18 (solo JDBC, ajuste A1).** Tests primero: setters de `Explotacion`/`Ganadero`
recalculan; persistir por el importador deja las columnas correctas (incluido reimportar con otro
nombre); test de la migración con Flyway programático (`target 17` → datos → migrar → valores y
`NOT NULL`).

**T3 — Búsqueda por palabras.** `Specification`, controlador (`palabras` vacías → página vacía;
límite de 8 → `400`). Tests E2E de dos Gestorías, nuevos y explícitos:
- `martinez` encuentra `Martínez` (y `MARTÍNEZ`, `martínez`).
- `ES12 perez` encuentra la explotación `ES12…` de Pérez y no otra `ES12…` de otro ganadero ni
  otra explotación de Pérez con otro REGA.
- una palabra que solo existe en la otra Gestoría no encuentra nada (ni cuenta en `totalElements`),
  y dos palabras que juntas solo se cumplen en la otra Gestoría tampoco.
- `peña` ↔ `pena`; `martin-perez`/`martinperez`; `martin - perez` (palabra vacía descartada);
  `q=%%` → vacío; 9 palabras → `400` con motivo.
- recuento de consultas con `q` de varias palabras: exactamente página + count, ninguna carga
  suelta de Ganadero (ajuste A3).
- se mantienen los E2E actuales (100 caracteres, `sort`, en blanco, sin JWT, N+1).

**T4 — Cierre.** `./mvnw clean test`, `npm test`, smoke `curl` contra dos Gestorías en H2 en
fichero (importar con tildes → buscar sin tildes, por palabras, palabra ajena → vacío, 9 palabras →
`400`), parar procesos por PID. Actualizar `CLAUDE.md`, `ganera-prompts.md` (la sección bloqueante
pasa a hecha; nota de `pg_trgm` al Prompt C) y `.superpowers/sdd/progress.md`.
