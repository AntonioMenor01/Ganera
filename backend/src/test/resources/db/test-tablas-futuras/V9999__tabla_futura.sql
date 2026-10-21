-- Solo para SeguridadEsquemaPostgresTest (migracion a Supabase, T2): una migracion "futura" que crea una tabla
-- normal. El test comprueba que el callback afterMigrate la protege sin que nadie se acuerde. Nunca esta en las
-- ubicaciones de la aplicacion. (Hasta la auditoria base, T1, tambien creaba una tabla auditoria de mentira para
-- probar la lista «solo insercion»; desde V24 la tabla auditoria es la real y este fichero ya no la crea.)
CREATE TABLE tabla_futura (
    id     BIGSERIAL PRIMARY KEY,
    nombre VARCHAR(100)
);
