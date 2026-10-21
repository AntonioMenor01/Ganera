-- Solo para SeguridadEsquemaPostgresTest (migracion a Supabase, T2): una migracion "futura" que crea una tabla
-- normal y la tabla de auditoria (lista de «solo insercion y lectura» del callback afterMigrate). El test
-- comprueba que el callback las protege sin que nadie se acuerde. Nunca esta en las ubicaciones de la aplicacion.
CREATE TABLE tabla_futura (
    id     BIGSERIAL PRIMARY KEY,
    nombre VARCHAR(100)
);

CREATE TABLE auditoria (
    id      BIGSERIAL PRIMARY KEY,
    detalle VARCHAR(100)
);
