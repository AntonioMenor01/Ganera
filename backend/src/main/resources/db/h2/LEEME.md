# db/h2 — migraciones solo para H2

Flyway lee `classpath:db/migration` y `classpath:db/{vendor}` (`spring.flyway.locations` en los dos
`application.yml`). Spring Boot resuelve `{vendor}` a `h2` contra H2 (la suite de tests y el smoke
local) y a `postgresql` contra PostgreSQL, así que lo que hay aquí **solo se ejecuta en H2**.

Hoy está vacía: H2 no tiene nada propio. Si alguna vez hiciera falta, la regla de numeración es la
misma que en `db/postgresql/LEEME.md`: la versión es **global** entre las carpetas (un hueco en un
motor es normal) y **nunca** dos ficheros con la misma versión.

Este fichero existe para que Maven copie la carpeta al classpath (no copia carpetas vacías y Flyway
no encontraría la ubicación); Flyway ignora los ficheros que no son migraciones ni callbacks.
