package com.ganera.core.postgres;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Auditoria base, T2: la tabla {@code auditoria} es inmutable en PostgreSQL. Dos capas: {@code ganera_app} no tiene
 * UPDATE, DELETE ni TRUNCATE (callback, lista «solo insercion») y al propietario lo paran los dos triggers de
 * {@code db/postgresql/V25__auditoria_inmutable.sql}. Ademas, el callback {@code afterMigrate__seguridad_esquema.sql}
 * falla cerrado si los triggers faltan, estan desactivados o no son los de V25 (D2), revoca tambien a
 * {@code authenticator} cuando existe, y el script 02 marca cada problema nuevo con una fila.
 */
class AuditoriaInmutablePostgresTest {

    /** SQLState de "permission denied" (insufficient_privilege). */
    private static final String SIN_PERMISO = "42501";

    /** SQLState de un RAISE EXCEPTION sin codigo propio (raise_exception). */
    private static final String EXCEPCION_DEL_TRIGGER = "P0001";

    /** Principio del mensaje del callback cuando los triggers no son los de V25. */
    private static final String MENSAJE_D2 = "La tabla auditoria no tiene exactamente sus dos triggers de V25";

    /** Una base migrada hasta la ultima, con una fila de auditoria, para los casos que no cambian nada. */
    private static String baseComun;

    @BeforeAll
    static void migrarUnaBaseConUnaFila() {
        baseComun = migrarComoPropietario();
        insertarFila(baseComun);
    }

    // --- capa 1: ganera_app ---

    @Test
    void ganeraAppInsertaYLeePeroNoPuedeCambiarNiBorrarNiVaciarLaTabla() {
        DataSource app = PostgresEmbebido.dataSourceComo(PostgresEmbebido.APP, baseComun);
        JdbcTemplate comoApp = new JdbcTemplate(app);
        int antes = comoApp.queryForObject("select count(*) from ganera.auditoria", Integer.class);

        insertarFila(baseComun);
        assertThat(comoApp.queryForObject("select count(*) from ganera.auditoria", Integer.class))
                .isEqualTo(antes + 1);

        assertSinPermiso(app, "update ganera.auditoria set accion = 'CAMBIADA'");
        assertSinPermiso(app, "delete from ganera.auditoria");
        assertSinPermiso(app, "truncate ganera.auditoria");
        assertThat(comoApp.queryForObject("select count(*) from ganera.auditoria", Integer.class))
                .isEqualTo(antes + 1);
    }

    // --- capa 2: el propietario y los triggers ---

    /**
     * El propietario tiene el privilegio (es el dueno de la tabla); lo para el trigger. {@code UPDATE … WHERE false}
     * tambien (trigger por sentencia: se dispara aunque no toque ninguna fila), y {@code TRUNCATE gestoria CASCADE}
     * tambien (los triggers de TRUNCATE se disparan en las tablas que caen en cascada).
     */
    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '"', value = {
            "update ganera.auditoria set accion = 'CAMBIADA' | La tabla auditoria solo admite INSERT (UPDATE rechazado).",
            "update ganera.auditoria set accion = 'CAMBIADA' where false"
                    + " | La tabla auditoria solo admite INSERT (UPDATE rechazado).",
            "delete from ganera.auditoria | La tabla auditoria solo admite INSERT (DELETE rechazado).",
            "truncate ganera.auditoria | La tabla auditoria solo admite INSERT (TRUNCATE rechazado).",
            "truncate ganera.gestoria cascade | La tabla auditoria solo admite INSERT (TRUNCATE rechazado)."
    })
    void elPropietarioNoPuedeCambiarNiBorrarNiVaciarLaTabla(String sentencia, String mensaje) {
        DataSource propietario = PostgresEmbebido.dataSourceComo(PostgresEmbebido.PROPIETARIO, baseComun);
        JdbcTemplate superusuario = new JdbcTemplate(PostgresEmbebido.dataSource(baseComun));
        int antes = superusuario.queryForObject("select count(*) from ganera.auditoria", Integer.class);
        assertThat(antes).isPositive();

        assertFallaCon(propietario, sentencia, EXCEPCION_DEL_TRIGGER, mensaje);

        assertThat(superusuario.queryForObject("select count(*) from ganera.auditoria", Integer.class))
                .isEqualTo(antes);
        assertThat(superusuario.queryForObject("select count(*) from ganera.auditoria where accion = 'CAMBIADA'",
                Integer.class)).isZero();
    }

    // --- callback (D2) y 02: triggers que faltan, desactivados o cambiados ---

    /**
     * Si alguien (el propietario) borra, desactiva o cambia un trigger, el siguiente migrate (el del arranque) falla
     * con el mensaje del callback, y el 02 da exactamente una fila por problema.
     */
    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '"', value = {
            "drop trigger auditoria_sin_cambios on ganera.auditoria"
                    + " | auditoria_sin_cambios: falta"
                    + " | trigger auditoria.auditoria_sin_cambios: falta",
            "drop trigger auditoria_sin_borrado on ganera.auditoria"
                    + " | auditoria_sin_borrado: falta"
                    + " | trigger auditoria.auditoria_sin_borrado: falta",
            "alter table ganera.auditoria disable trigger auditoria_sin_cambios"
                    + " | auditoria_sin_cambios: desactivado o con otra definicion"
                    + " | trigger auditoria.auditoria_sin_cambios: desactivado (tgenabled = D, debe ser O)",
            "alter table ganera.auditoria disable trigger auditoria_sin_borrado"
                    + " | auditoria_sin_borrado: desactivado o con otra definicion"
                    + " | trigger auditoria.auditoria_sin_borrado: desactivado (tgenabled = D, debe ser O)",
            // Sin TRUNCATE: un TRUNCATE del propietario vaciaria la tabla.
            "drop trigger auditoria_sin_cambios on ganera.auditoria;"
                    + " create trigger auditoria_sin_cambios before update on ganera.auditoria"
                    + " for each statement execute function ganera.auditoria_sin_cambios()"
                    + " | auditoria_sin_cambios: desactivado o con otra definicion"
                    + " | trigger auditoria.auditoria_sin_cambios: eventos UPDATE (deben ser UPDATE,TRUNCATE)",
            // Por sentencia en vez de por fila: el futuro proceso de retencion no podria decidir fila a fila.
            "drop trigger auditoria_sin_borrado on ganera.auditoria;"
                    + " create trigger auditoria_sin_borrado before delete on ganera.auditoria"
                    + " for each statement execute function ganera.auditoria_sin_borrado()"
                    + " | auditoria_sin_borrado: desactivado o con otra definicion"
                    + " | trigger auditoria.auditoria_sin_borrado: no es FOR EACH ROW",
            "drop trigger auditoria_sin_cambios on ganera.auditoria;"
                    + " create trigger auditoria_sin_cambios after update or truncate on ganera.auditoria"
                    + " for each statement execute function ganera.auditoria_sin_cambios()"
                    + " | auditoria_sin_cambios: desactivado o con otra definicion"
                    + " | trigger auditoria.auditoria_sin_cambios: no es BEFORE",
            // Solo para una columna: un UPDATE de las demas pasaria.
            "drop trigger auditoria_sin_cambios on ganera.auditoria;"
                    + " create trigger auditoria_sin_cambios before update of accion or truncate on ganera.auditoria"
                    + " for each statement execute function ganera.auditoria_sin_cambios()"
                    + " | auditoria_sin_cambios: desactivado o con otra definicion"
                    + " | trigger auditoria.auditoria_sin_cambios: solo para algunas columnas",
            // Con una condicion que nunca se cumple: el trigger existe, pero no para nada.
            "drop trigger auditoria_sin_borrado on ganera.auditoria;"
                    + " create trigger auditoria_sin_borrado before delete on ganera.auditoria"
                    + " for each row when (false) execute function ganera.auditoria_sin_borrado()"
                    + " | auditoria_sin_borrado: desactivado o con otra definicion"
                    + " | trigger auditoria.auditoria_sin_borrado: tiene condicion WHEN",
            // Que llama a otra funcion (una que no hace nada).
            "create function ganera.no_hace_nada() returns trigger language plpgsql as $f$ begin return null; end $f$;"
                    + " revoke all on function ganera.no_hace_nada() from public;"
                    + " drop trigger auditoria_sin_borrado on ganera.auditoria;"
                    + " create trigger auditoria_sin_borrado before delete on ganera.auditoria"
                    + " for each row execute function ganera.no_hace_nada()"
                    + " | auditoria_sin_borrado: desactivado o con otra definicion"
                    + " | trigger auditoria.auditoria_sin_borrado: no llama a ganera.auditoria_sin_borrado()",
            // Un tercero no esperado (p. ej. uno que cambie las filas al insertarlas).
            "create trigger otro_mas before insert on ganera.auditoria"
                    + " for each row execute function ganera.auditoria_sin_borrado()"
                    + " | otro_mas: no esperado"
                    + " | trigger auditoria.otro_mas: no esperado"
    })
    void siLosTriggersNoSonLosDeV25ElCallbackFallaYEl02LoMarca(String preparar, String enElCallback,
            String filaDel02) throws SQLException {
        String baseDeDatos = migrarComoPropietario();
        ejecutar(PostgresEmbebido.dataSourceComo(PostgresEmbebido.PROPIETARIO, baseDeDatos), preparar);

        assertThatThrownBy(() -> PostgresEmbebido.flywayPropietario(baseDeDatos).load().migrate())
                .hasStackTraceContaining(MENSAJE_D2)
                .hasStackTraceContaining(enElCallback);
        assertThat(SeguridadEsquemaPostgresTest.problemas(baseDeDatos)).containsExactly(filaDel02);
    }

    /** Sin la tabla (una base migrada con target 23) el callback no comprueba los triggers: migra igual. */
    @Test
    void sinLaTablaAuditoriaElCallbackNoCompruebaLosTriggers() {
        String baseDeDatos = PostgresEmbebido.nuevaBaseDeDatos();

        assertThat(PostgresEmbebido.flywayPropietario(baseDeDatos).target("23").load().migrate().migrationsExecuted)
                .isEqualTo(23);
        assertThat(new JdbcTemplate(PostgresEmbebido.dataSource(baseDeDatos))
                .queryForObject("select to_regclass('ganera.auditoria') is null", Boolean.class)).isTrue();
        // Y volver a migrar (solo el callback) tampoco falla.
        assertThat(PostgresEmbebido.flywayPropietario(baseDeDatos).target("23").load().migrate().migrationsExecuted)
                .isZero();
    }

    // --- 02: problemas que no paran el arranque ---

    /**
     * El 02 marca lo que el callback no vigila: la tabla que falta, una funcion de los triggers que no es de
     * {@code ganera_propietario}, y cualquier rol vigilado que pueda cambiar {@code session_replication_role} (con
     * {@code replica} se saltaria los triggers). Los privilegios sobre parametros son del cluster compartido: se
     * deshacen en {@code finally} como superusuario.
     */
    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '"', value = {
            "drop table ganera.auditoria | - | tabla auditoria: no existe (la crea V24)",
            // Cambiar el dueno de una funcion exige CREATE sobre el esquema para el nuevo dueno (se concede y se
            // revoca aqui; CREATE para admin_supabase no lo mira el 02). El superusuario ejecuta el ALTER.
            "grant create on schema ganera to admin_supabase;"
                    + " alter function ganera.auditoria_sin_cambios() owner to admin_supabase"
                    + " | alter function ganera.auditoria_sin_cambios() owner to ganera_propietario;"
                    + " revoke create on schema ganera from admin_supabase"
                    + " | funcion ganera.auditoria_sin_cambios(): es de admin_supabase (debe ser de ganera_propietario)",
            "grant create on schema ganera to admin_supabase;"
                    + " alter function ganera.auditoria_sin_borrado() owner to admin_supabase"
                    + " | alter function ganera.auditoria_sin_borrado() owner to ganera_propietario;"
                    + " revoke create on schema ganera from admin_supabase"
                    + " | funcion ganera.auditoria_sin_borrado(): es de admin_supabase (debe ser de ganera_propietario)",
            "grant set on parameter session_replication_role to anon"
                    + " | revoke set on parameter session_replication_role from anon"
                    + " | parametro session_replication_role: anon puede cambiarlo (SET; con replica se saltaria los"
                    + " triggers de auditoria)",
            "grant set on parameter session_replication_role to authenticated"
                    + " | revoke set on parameter session_replication_role from authenticated"
                    + " | parametro session_replication_role: authenticated puede cambiarlo (SET; con replica se"
                    + " saltaria los triggers de auditoria)",
            "grant set on parameter session_replication_role to service_role"
                    + " | revoke set on parameter session_replication_role from service_role"
                    + " | parametro session_replication_role: service_role puede cambiarlo (SET; con replica se"
                    + " saltaria los triggers de auditoria)",
            "grant set on parameter session_replication_role to authenticator"
                    + " | revoke set on parameter session_replication_role from authenticator"
                    + " | parametro session_replication_role: authenticator puede cambiarlo (SET; con replica se"
                    + " saltaria los triggers de auditoria)",
            "grant set on parameter session_replication_role to ganera_app"
                    + " | revoke set on parameter session_replication_role from ganera_app"
                    + " | parametro session_replication_role: ganera_app puede cambiarlo (SET; con replica se"
                    + " saltaria los triggers de auditoria)",
            "grant set on parameter session_replication_role to ganera_propietario"
                    + " | revoke set on parameter session_replication_role from ganera_propietario"
                    + " | parametro session_replication_role: ganera_propietario puede cambiarlo (SET; con replica se"
                    + " saltaria los triggers de auditoria)"
    })
    void laComprobacionDetectaLosProblemasNuevosDeLaAuditoria(String preparar, String deshacer, String esperado)
            throws SQLException {
        String baseDeDatos = migrarComoPropietario();
        DataSource superusuario = PostgresEmbebido.dataSource(baseDeDatos);
        try {
            ejecutar(superusuario, preparar);

            assertThat(SeguridadEsquemaPostgresTest.problemas(baseDeDatos)).containsExactly(esperado);
        } finally {
            if (!deshacer.equals("-")) {
                ejecutar(superusuario, deshacer);
            }
        }
    }

    /** No vacuo: en la base correcta los dos triggers estan, como los espera el 02, y no hay ninguna fila. */
    @Test
    void enLaBaseCorrectaEstanLosDosTriggersExactosYEl02NoDevuelveNada() {
        JdbcTemplate jdbc = new JdbcTemplate(PostgresEmbebido.dataSource(baseComun));

        assertThat(jdbc.queryForList("select tg.tgname || ' ' || tg.tgtype::text || ' ' || tg.tgenabled::text || ' '"
                + " || tg.tgfoid::regprocedure::text || ' ' || pg_get_userbyid(p.proowner) "
                + "from pg_trigger tg join pg_proc p on p.oid = tg.tgfoid "
                + "where tg.tgrelid = 'ganera.auditoria'::regclass and not tg.tgisinternal order by 1", String.class))
                .containsExactly(
                        // 11 = ROW (1) + BEFORE (2) + DELETE (8); 50 = BEFORE (2) + UPDATE (16) + TRUNCATE (32).
                        "auditoria_sin_borrado 11 O ganera.auditoria_sin_borrado() ganera_propietario",
                        "auditoria_sin_cambios 50 O ganera.auditoria_sin_cambios() ganera_propietario");
        assertThat(SeguridadEsquemaPostgresTest.problemas(baseComun)).isEmpty();
    }

    // --- authenticator ---

    /**
     * El callback revoca tambien a {@code authenticator} (el rol con LOGIN de la Data API de Supabase): una concesion
     * hecha a mano en el esquema, una tabla, una secuencia o una funcion desaparece al migrar.
     */
    @Test
    void lasConcesionesHostilesAAuthenticatorDesaparecenAlMigrar() throws SQLException {
        String baseDeDatos = migrarComoPropietario();
        ejecutar(PostgresEmbebido.dataSourceComo(PostgresEmbebido.PROPIETARIO, baseDeDatos),
                "grant all on schema ganera to authenticator",
                "grant all on table ganera.gestoria to authenticator",
                "grant all on table ganera.auditoria to authenticator",
                "grant all on sequence ganera.gestoria_id_seq to authenticator",
                "grant execute on function ganera.auditoria_sin_cambios() to authenticator");
        // No vacuo: el 02 las ve.
        assertThat(SeguridadEsquemaPostgresTest.problemas(baseDeDatos))
                .anyMatch(p -> p.startsWith("esquema ganera: privilegio") && p.endsWith("para authenticator"))
                .anyMatch(p -> p.startsWith("tabla gestoria: privilegio") && p.endsWith("para authenticator"))
                .anyMatch(p -> p.startsWith("tabla auditoria: privilegio") && p.endsWith("para authenticator"))
                .anyMatch(p -> p.startsWith("secuencia gestoria_id_seq: privilegio") && p.endsWith("para authenticator"))
                .anyMatch(p -> p.contains("auditoria_sin_cambios()") && p.endsWith("EXECUTE para authenticator"));

        PostgresEmbebido.flywayPropietario(baseDeDatos).load().migrate();

        assertThat(SeguridadEsquemaPostgresTest.problemas(baseDeDatos)).isEmpty();
        JdbcTemplate jdbc = new JdbcTemplate(PostgresEmbebido.dataSource(baseDeDatos));
        assertThat(jdbc.queryForObject("select has_schema_privilege('authenticator', 'ganera', 'USAGE')"
                + " or has_table_privilege('authenticator', 'ganera.gestoria', 'SELECT')"
                + " or has_table_privilege('authenticator', 'ganera.auditoria', 'SELECT')"
                + " or has_sequence_privilege('authenticator', 'ganera.gestoria_id_seq', 'USAGE')"
                + " or has_function_privilege('authenticator', 'ganera.auditoria_sin_cambios()', 'EXECUTE')",
                Boolean.class)).isFalse();
    }

    /**
     * En una base sin el rol {@code authenticator} (local, docker-compose) el callback migra igual. Los roles son del
     * cluster y el compartido si lo tiene, asi que se usa un PostgreSQL embebido aparte con admin_supabase, anon,
     * authenticated y service_role, preparado con el script 01 como en {@link PostgresEmbebido#nuevaBaseDeDatos()}.
     */
    @Test
    void sinElRolAuthenticatorElCallbackMigraIgual() throws Exception {
        try (EmbeddedPostgres aparte = EmbeddedPostgres.builder().setLocaleConfig("encoding", "UTF8").start()) {
            PostgresEmbebido.crearRolesDeSupabase(aparte.getPostgresDatabase(),
                    List.of("anon", "authenticated", "service_role"));
            ejecutar(aparte.getPostgresDatabase(), "create database sin_authenticator owner " + PostgresEmbebido.ADMIN);
            PostgresEmbebido.ejecutarScript(aparte.getDatabase(PostgresEmbebido.ADMIN, "sin_authenticator"),
                    PostgresEmbebido.SCRIPT_PREPARAR);
            JdbcTemplate superusuario = new JdbcTemplate(aparte.getDatabase(PostgresEmbebido.USUARIO,
                    "sin_authenticator"));
            assertThat(superusuario.queryForObject("select to_regrole('authenticator') is null", Boolean.class))
                    .isTrue();

            assertThat(Flyway.configure()
                    .dataSource(aparte.getDatabase(PostgresEmbebido.PROPIETARIO, "sin_authenticator"))
                    .locations(PostgresEmbebido.UBICACIONES_FLYWAY)
                    .defaultSchema(PostgresEmbebido.ESQUEMA).schemas(PostgresEmbebido.ESQUEMA).createSchemas(false)
                    .load().migrate().migrationsExecuted).isEqualTo(25);

            assertThat(superusuario.query(PostgresEmbebido.leer(PostgresEmbebido.SCRIPT_COMPROBAR),
                    (fila, i) -> fila.getString("objeto") + ": " + fila.getString("problema"))).isEmpty();
        }
    }

    // --- ayudantes ---

    private static String migrarComoPropietario() {
        String baseDeDatos = PostgresEmbebido.nuevaBaseDeDatos();
        assertThat(PostgresEmbebido.flywayPropietario(baseDeDatos).load().migrate().migrationsExecuted)
                .isEqualTo(25);
        return baseDeDatos;
    }

    /** Una fila valida minima, insertada como ganera_app (lo unico que puede hacer la app). */
    private static void insertarFila(String baseDeDatos) {
        JdbcTemplate comoApp = new JdbcTemplate(PostgresEmbebido.dataSourceComo(PostgresEmbebido.APP, baseDeDatos));
        Long gestoria = comoApp.queryForObject(
                "insert into ganera.gestoria (nombre) values ('Gestoria auditoria') returning id", Long.class);
        comoApp.update("insert into ganera.auditoria (gestoria_id, actor_tipo, accion, entidad_tipo, creado_en) "
                + "values (?, 'SISTEMA', 'GESTORIA_CREADA', 'GESTORIA', now())", gestoria);
    }

    /**
     * Para los casos del trigger: SQLState P0001 y el mensaje en castellano (es texto nuestro, no del servidor, asi
     * que no cambia con la configuracion regional del PostgreSQL embebido).
     */
    private static void assertFallaCon(DataSource dataSource, String sql, String sqlState, String mensaje) {
        assertThatThrownBy(() -> ejecutar(dataSource, sql))
                .isInstanceOfSatisfying(SQLException.class, e -> {
                    assertThat(e.getSQLState()).isEqualTo(sqlState);
                    assertThat(e.getMessage()).contains(mensaje);
                });
    }

    /** Solo el SQLState 42501: el texto de «permiso denegado» lo traduce el servidor segun su configuracion regional. */
    private static void assertSinPermiso(DataSource dataSource, String sql) {
        assertThatThrownBy(() -> ejecutar(dataSource, sql))
                .isInstanceOfSatisfying(SQLException.class, e -> assertThat(e.getSQLState()).isEqualTo(SIN_PERMISO));
    }

    private static void ejecutar(DataSource dataSource, String... sentencias) throws SQLException {
        try (Connection conexion = dataSource.getConnection(); Statement st = conexion.createStatement()) {
            for (String sentencia : sentencias) {
                st.execute(sentencia);
            }
        }
    }
}
