package com.ganera.core.postgres;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * Migracion a Supabase, T2: el test de guardia de RLS y permisos. Migra como en el perfil clever (Flyway como
 * {@code ganera_propietario} sobre el esquema {@code ganera}, creado por el script 01) y comprueba lo que deja el
 * callback {@code db/postgresql/afterMigrate__seguridad_esquema.sql}, con el script
 * {@code backend/supabase/02-comprobar-seguridad.sql} leido del disco (la misma consulta que ejecuta Antonio en
 * Supabase) y con pruebas reales conectado como {@code ganera_app}.
 */
class SeguridadEsquemaPostgresTest {

    /** SQLState de "permission denied" (insufficient_privilege). */
    private static final String SIN_PERMISO = "42501";

    /** Texto de la fila del 02 para un rol que llega por una cadena de roles. */
    private static final String CADENA = "llega por una cadena de roles (hereda sus permisos, puede hacer SET ROLE o tiene ADMIN)";

    @Test
    void trasMigrarElScriptDeComprobacionNoDevuelveNingunProblema() {
        String baseDeDatos = migrarComoPropietario();
        JdbcTemplate jdbc = new JdbcTemplate(PostgresEmbebido.dataSource(baseDeDatos));

        // No vacuo: las 23 migraciones estan en ganera, aplicadas por el propietario.
        assertThat(jdbc.queryForObject("select count(*) from ganera.flyway_schema_history "
                + "where success and installed_by = 'ganera_propietario' and version is not null", Integer.class))
                .isEqualTo(23);
        assertThat(problemas(baseDeDatos)).isEmpty();
    }

    @Test
    void cadaTablaTieneRlsYLaPoliticaExactaParaGaneraApp() {
        String baseDeDatos = migrarComoPropietario();
        JdbcTemplate jdbc = new JdbcTemplate(PostgresEmbebido.dataSource(baseDeDatos));
        List<String> tablas = tablas(jdbc);

        assertThat(tablas).contains("gestoria", "tramite", "flyway_schema_history").hasSizeGreaterThan(10);
        for (String tabla : tablas) {
            assertThat(jdbc.queryForObject("select relrowsecurity from pg_class where oid = ?::regclass",
                    Boolean.class, "ganera." + tabla)).as(tabla).isTrue();
            List<Map<String, Object>> politicas = politicas(jdbc, tabla);
            if (tabla.equals("flyway_schema_history")) {
                assertThat(politicas).as(tabla).isEmpty();
            } else {
                assertThat(politicas).as(tabla).containsExactly(Map.of(
                        "policyname", "ganera_app_todo", "permissive", "PERMISSIVE", "cmd", "ALL",
                        "roles", "{ganera_app}", "qual", "true", "with_check", "true"));
            }
        }
    }

    @Test
    void ganeraAppTieneSoloLosPrivilegiosJustosYNoPuedeCrearTablas() throws SQLException {
        String baseDeDatos = migrarComoPropietario();
        JdbcTemplate jdbc = new JdbcTemplate(PostgresEmbebido.dataSource(baseDeDatos));

        for (String tabla : tablas(jdbc)) {
            List<String> esperados = tabla.equals("flyway_schema_history")
                    ? List.of() : List.of("DELETE", "INSERT", "SELECT", "UPDATE");
            assertThat(privilegiosDeGaneraApp(jdbc, tabla, 'r')).as(tabla).isEqualTo(esperados);
        }
        List<String> secuencias = jdbc.queryForList("select c.relname from pg_class c join pg_namespace n "
                + "on n.oid = c.relnamespace where n.nspname = 'ganera' and c.relkind = 'S'", String.class);
        assertThat(secuencias).contains("gestoria_id_seq");
        for (String secuencia : secuencias) {
            assertThat(privilegiosDeGaneraApp(jdbc, secuencia, 's')).as(secuencia).containsExactly("USAGE");
        }

        // Pruebas reales conectado como ganera_app.
        DataSource app = PostgresEmbebido.dataSourceComo(PostgresEmbebido.APP, baseDeDatos);
        assertThat(new JdbcTemplate(app).queryForObject("select count(*) from ganera.gestoria", Integer.class))
                .isZero();
        assertSinPermiso(app, "select count(*) from ganera.flyway_schema_history");
        assertSinPermiso(app, "create table ganera.x (id int)");
    }

    @Test
    void lasConcesionesHostilesHechasAntesDeMigrarQuedanRevocadas() throws SQLException {
        String baseDeDatos = migrarComoPropietario();
        DataSource propietario = PostgresEmbebido.dataSourceComo(PostgresEmbebido.PROPIETARIO, baseDeDatos);
        ejecutar(propietario,
                "grant all on table ganera.gestoria to anon, authenticated, service_role, public",
                "grant all on sequence ganera.gestoria_id_seq to anon, public",
                "grant all on schema ganera to anon, authenticated, service_role, public",
                "grant all on schema ganera to ganera_app",
                "grant all on table ganera.flyway_schema_history to ganera_app with grant option",
                "grant truncate, references, trigger on table ganera.tramite to ganera_app");
        // No vacuo: la guardia ve las concesiones.
        assertThat(problemas(baseDeDatos)).anyMatch(p -> p.contains("anon")).anyMatch(p -> p.contains("PUBLIC"))
                .anyMatch(p -> p.contains("flyway_schema_history"));

        PostgresEmbebido.flywayPropietario(baseDeDatos).load().migrate();

        assertThat(problemas(baseDeDatos)).isEmpty();
        JdbcTemplate jdbc = new JdbcTemplate(PostgresEmbebido.dataSource(baseDeDatos));
        for (String rol : List.of("anon", "authenticated", "service_role", "public")) {
            assertThat(jdbc.queryForObject("select has_table_privilege(?, 'ganera.gestoria', 'SELECT') "
                    + "or has_schema_privilege(?, 'ganera', 'USAGE')", Boolean.class, rol, rol)).as(rol).isFalse();
        }
        assertSinPermiso(PostgresEmbebido.dataSourceComo(PostgresEmbebido.APP, baseDeDatos),
                "create table ganera.x (id int)");
    }

    @Test
    void unaTablaFuturaQuedaProtegidaYLaAuditoriaSoloAdmiteLecturaEInsercion() throws SQLException {
        String baseDeDatos = PostgresEmbebido.nuevaBaseDeDatos();
        PostgresEmbebido.flywayPropietario(baseDeDatos)
                .locations(UBICACIONES_CON_TABLAS_FUTURAS)
                .load().migrate();
        JdbcTemplate jdbc = new JdbcTemplate(PostgresEmbebido.dataSource(baseDeDatos));

        assertThat(jdbc.queryForObject("select relrowsecurity from pg_class where oid = 'ganera.tabla_futura'::regclass",
                Boolean.class)).isTrue();
        assertThat(politicas(jdbc, "tabla_futura")).containsExactly(Map.of(
                "policyname", "ganera_app_todo", "permissive", "PERMISSIVE", "cmd", "ALL",
                "roles", "{ganera_app}", "qual", "true", "with_check", "true"));
        assertThat(privilegiosDeGaneraApp(jdbc, "tabla_futura", 'r'))
                .containsExactly("DELETE", "INSERT", "SELECT", "UPDATE");

        assertThat(jdbc.queryForObject("select relrowsecurity from pg_class where oid = 'ganera.auditoria'::regclass",
                Boolean.class)).isTrue();
        assertThat(privilegiosDeGaneraApp(jdbc, "auditoria", 'r')).containsExactly("INSERT", "SELECT");
        assertThat(politicas(jdbc, "auditoria")).containsExactly(
                Map.of("policyname", "ganera_app_insercion", "permissive", "PERMISSIVE", "cmd", "INSERT",
                        "roles", "{ganera_app}", "qual", "", "with_check", "true"),
                Map.of("policyname", "ganera_app_lectura", "permissive", "PERMISSIVE", "cmd", "SELECT",
                        "roles", "{ganera_app}", "qual", "true", "with_check", ""));

        DataSource app = PostgresEmbebido.dataSourceComo(PostgresEmbebido.APP, baseDeDatos);
        JdbcTemplate comoApp = new JdbcTemplate(app);
        comoApp.update("insert into ganera.auditoria (detalle) values ('uno')");
        comoApp.update("insert into ganera.tabla_futura (nombre) values ('uno')");
        comoApp.update("update ganera.tabla_futura set nombre = 'dos'");
        assertThat(comoApp.queryForObject("select count(*) from ganera.auditoria", Integer.class)).isEqualTo(1);
        assertSinPermiso(app, "update ganera.auditoria set detalle = 'cambiado'");
        assertSinPermiso(app, "delete from ganera.auditoria");

        assertThat(problemas(baseDeDatos)).isEmpty();
    }

    @Test
    void laComprobacionDetectaUnaTablaSinRls() throws SQLException {
        String baseDeDatos = migrarComoPropietario();
        ejecutar(PostgresEmbebido.dataSourceComo(PostgresEmbebido.PROPIETARIO, baseDeDatos),
                "alter table ganera.explotacion disable row level security");

        assertThat(problemas(baseDeDatos)).containsExactly("tabla explotacion: sin RLS");
    }

    /**
     * Sin el rol ganera_app la migracion falla con el mensaje del callback. Los roles son del cluster, asi que se
     * usa un PostgreSQL embebido aparte (unos segundos) en vez de tocar el compartido: tiene admin_supabase, anon,
     * authenticated y service_role, pero no ganera_app (nunca se ejecuta el script 01).
     */
    @Test
    void sinElRolGaneraAppLaMigracionFallaConElMensajeDelCallback() throws Exception {
        try (EmbeddedPostgres aparte = EmbeddedPostgres.builder().setLocaleConfig("encoding", "UTF8").start()) {
            PostgresEmbebido.crearRolesDeSupabase(aparte.getPostgresDatabase(),
                    List.of("anon", "authenticated", "service_role"));

            assertThatThrownBy(() -> PostgresEmbebido.flyway(aparte.getPostgresDatabase()).load().migrate())
                    .hasStackTraceContaining("Faltan los roles ganera_app en la base de datos")
                    .hasStackTraceContaining("backend/supabase/01-preparar-roles-y-esquema.sql");
        }
    }

    /**
     * Revision de T2, I1: el 02 ve las membresias y atributos de rol indebidos. Un {@code GRANT ganera_app TO anon}
     * daria a anon todo lo de la app (y sus politicas). Los roles son del cluster compartido: cada caso se deshace
     * en {@code finally} como superusuario. La membresia automatica de PostgreSQL 16+ (ADMIN, sin INHERIT ni SET) de
     * quien crea el rol no cuenta: no da ningun privilegio. Varias filas esperadas van separadas por " ; ": lo que se
     * concede a anon o a service_role le llega tambien a authenticator (puede hacerse cualquiera de los dos), y el 02
     * lo marca como cadena de roles (pg_has_role).
     */
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "grant ganera_app to anon | revoke ganera_app from anon"
                    + " | rol ganera_app: tiene como miembro a anon (hereda sus permisos o puede hacer SET ROLE)"
                    + " ; rol ganera_app: authenticator " + CADENA,
            "grant ganera_app to service_role with inherit false, set true | revoke ganera_app from service_role"
                    + " | rol ganera_app: tiene como miembro a service_role (hereda sus permisos o puede hacer SET ROLE)"
                    + " ; rol ganera_app: authenticator " + CADENA,
            "grant ganera_propietario to ganera_app with inherit false, set true"
                    + " | revoke ganera_propietario from ganera_app | rol ganera_app: es miembro de ganera_propietario",
            "grant ganera_propietario to authenticated with inherit true, set false"
                    + " | revoke ganera_propietario from authenticated"
                    + " | rol ganera_propietario: authenticated hereda sus permisos (INHERIT; solo debe tener SET)"
                    + " ; rol ganera_propietario: authenticator " + CADENA,
            "alter role ganera_app superuser | alter role ganera_app nosuperuser | rol ganera_app: tiene SUPERUSER",
            "alter role ganera_propietario bypassrls | alter role ganera_propietario nobypassrls"
                    + " | rol ganera_propietario: tiene BYPASSRLS",
            "alter role ganera_app createrole | alter role ganera_app nocreaterole | rol ganera_app: tiene CREATEROLE",
            "alter role ganera_propietario createdb | alter role ganera_propietario nocreatedb"
                    + " | rol ganera_propietario: tiene CREATEDB",
            "alter role ganera_app replication | alter role ganera_app noreplication | rol ganera_app: tiene REPLICATION",
            // Segunda revision, N1: con solo ADMIN, anon podria concederse INHERIT o SET a si mismo despues.
            "grant ganera_app to anon with admin true, inherit false, set false | revoke ganera_app from anon"
                    + " | rol ganera_app: anon tiene ADMIN (podria darse INHERIT o SET a si mismo)"
                    + " ; rol ganera_app: authenticator " + CADENA,
            "grant ganera_propietario to authenticated with admin true, inherit false, set false"
                    + " | revoke ganera_propietario from authenticated"
                    + " | rol ganera_propietario: authenticated tiene ADMIN (podria darse INHERIT o SET a si mismo)"
                    + " ; rol ganera_propietario: authenticator " + CADENA,
            // Revision de T3, I2: con SET (aunque sin INHERIT), quien pueda ser anon hace SET ROLE ganera_propietario
            // y es dueno de todas las tablas (la RLS no le aplica).
            "grant ganera_propietario to anon with inherit false, set true | revoke ganera_propietario from anon"
                    + " | rol ganera_propietario: anon puede hacer SET ROLE (seria dueno de todas las tablas)"
                    + " ; rol ganera_propietario: authenticator " + CADENA,
            // Revision de T3, M1: authenticator (en Supabase LOGIN NOINHERIT, puede ser anon/authenticated/
            // service_role) es el rol de Supabase con LOGIN: tambien en la lista cerrada.
            "grant ganera_propietario to authenticator with inherit false, set true"
                    + " | revoke ganera_propietario from authenticator"
                    + " | rol ganera_propietario: authenticator puede hacer SET ROLE (seria dueno de todas las tablas)",
            "grant ganera_app to authenticator with admin true, inherit false, set false"
                    + " | revoke ganera_app from authenticator"
                    + " | rol ganera_app: authenticator tiene ADMIN (podria darse INHERIT o SET a si mismo)",
            "grant select on ganera.gestoria to authenticator | revoke select on ganera.gestoria from authenticator"
                    + " | tabla gestoria: privilegio SELECT para authenticator"
    })
    void laComprobacionDetectaMembresiasYAtributosDeRolIndebidos(String preparar, String deshacer, String esperado)
            throws SQLException {
        String baseDeDatos = migrarComoPropietario();
        DataSource superusuario = PostgresEmbebido.dataSource(baseDeDatos);
        try {
            ejecutar(superusuario, preparar);

            assertThat(problemas(baseDeDatos)).containsExactlyInAnyOrder(esperado.split(" ; "));
        } finally {
            ejecutar(superusuario, deshacer);
        }
        assertThat(problemas(baseDeDatos)).isEmpty();
    }

    /**
     * Segunda revision de T3, N1, y su revision: el 02 sigue las cadenas de roles, no solo las membresias directas.
     * Un rol de Supabase llega a un rol de Ganera si puede hacerse algun rol (el mismo o por una cadena de SET) que
     * hereda el de Ganera, puede hacer SET ROLE a el o tiene ADMIN sobre el (directo o heredado). Montaje: anon es
     * miembro de un rol puente, y el puente del rol de Ganera. authenticator (puede hacerse anon) llega tambien. El
     * puente (ni de Supabase ni de Ganera) solo lo marca la clausula directa que ya existia para ganera_app.
     */
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            // SET en cada eslabon.
            "ganera_propietario | inherit false, set true | inherit true, set true | -"
                    + " | rol ganera_propietario: anon " + CADENA + " ; rol ganera_propietario: authenticator " + CADENA,
            "ganera_app | inherit false, set true | inherit true, set true | -"
                    + " | rol ganera_app: tiene como miembro a ganera_prueba_puente (hereda sus permisos o puede hacer"
                    + " SET ROLE) ; rol ganera_app: anon " + CADENA + " ; rol ganera_app: authenticator " + CADENA,
            // Solo INHERIT (la mitad USAGE): authenticator llega haciendose anon, que hereda.
            "ganera_app | inherit true, set false | inherit true, set false | -"
                    + " | rol ganera_app: tiene como miembro a ganera_prueba_puente (hereda sus permisos o puede hacer"
                    + " SET ROLE) ; rol ganera_app: anon " + CADENA + " ; rol ganera_app: authenticator " + CADENA,
            // ADMIN en el intermedio: quien pueda ser el puente se concede el propietario.
            "ganera_propietario | admin true, inherit false, set false | inherit true, set true | -"
                    + " | rol ganera_propietario: anon " + CADENA + " ; rol ganera_propietario: authenticator " + CADENA,
            // Una membresia directa sin opciones (no la marca ninguna clausula directa) no tapa la cadena.
            "ganera_propietario | inherit false, set true | inherit true, set true"
                    + " | grant ganera_propietario to anon with inherit false, set false"
                    + " | rol ganera_propietario: anon " + CADENA + " ; rol ganera_propietario: authenticator " + CADENA
    })
    void laComprobacionDetectaCadenasDeRolesHastaLosRolesDeGanera(String rolDeGanera, String opcionesDelPuente,
            String opcionesDeAnon, String extra, String esperado) throws SQLException {
        String baseDeDatos = migrarComoPropietario();
        DataSource superusuario = PostgresEmbebido.dataSource(baseDeDatos);
        try {
            // Por si quedo de una ejecucion interrumpida (el cluster es compartido).
            ejecutar(superusuario, "drop role if exists ganera_prueba_puente",
                    "create role ganera_prueba_puente nologin",
                    "grant " + rolDeGanera + " to ganera_prueba_puente with " + opcionesDelPuente,
                    "grant ganera_prueba_puente to anon with " + opcionesDeAnon);
            if (!extra.equals("-")) {
                ejecutar(superusuario, extra);
            }

            assertThat(problemas(baseDeDatos)).containsExactlyInAnyOrder(esperado.split(" ; "));
        } finally {
            // Borrar el rol quita tambien sus membresias; la extra se deshace aparte.
            ejecutar(superusuario, "drop role if exists ganera_prueba_puente",
                    "revoke " + rolDeGanera + " from anon");
        }
        assertThat(problemas(baseDeDatos)).isEmpty();
    }

    /** Revision de T2, M4: el 02 ve los privilegios por columna (attacl), y el callback los quita. */
    @Test
    void laComprobacionDetectaPrivilegiosPorColumna() throws SQLException {
        String baseDeDatos = migrarComoPropietario();
        ejecutar(PostgresEmbebido.dataSourceComo(PostgresEmbebido.PROPIETARIO, baseDeDatos),
                "grant select (version) on ganera.flyway_schema_history to ganera_app",
                "grant select (id) on ganera.gestoria to anon",
                "grant update (nombre) on ganera.gestoria to public");

        assertThat(problemas(baseDeDatos)).containsExactlyInAnyOrder(
                "columna flyway_schema_history.version: ganera_app tiene SELECT",
                "columna gestoria.id: privilegio SELECT para anon",
                "columna gestoria.nombre: privilegio UPDATE para PUBLIC");

        PostgresEmbebido.flywayPropietario(baseDeDatos).load().migrate();

        assertThat(problemas(baseDeDatos)).isEmpty();
    }

    /**
     * Revision de T2, M3: el callback es idempotente. Migrar otra vez (tambien con una tabla normal nueva y con la de
     * solo insercion) no recrea ninguna politica (mismos OID) y deja el 02 vacio.
     */
    @Test
    void migrarDosVecesNoRecreaNingunaPolitica() {
        String baseDeDatos = PostgresEmbebido.nuevaBaseDeDatos();
        JdbcTemplate jdbc = new JdbcTemplate(PostgresEmbebido.dataSource(baseDeDatos));
        PostgresEmbebido.flywayPropietario(baseDeDatos).locations(UBICACIONES_CON_TABLAS_FUTURAS).load().migrate();
        List<Map<String, Object>> antes = politicasConOid(jdbc);
        assertThat(antes).extracting(f -> f.get("tabla") + "." + f.get("polname"))
                .contains("tabla_futura.ganera_app_todo", "auditoria.ganera_app_lectura",
                        "auditoria.ganera_app_insercion", "gestoria.ganera_app_todo");

        assertThat(PostgresEmbebido.flywayPropietario(baseDeDatos).locations(UBICACIONES_CON_TABLAS_FUTURAS)
                .load().migrate().migrationsExecuted).isZero();

        assertThat(politicasConOid(jdbc)).isEqualTo(antes);
        assertThat(problemas(baseDeDatos)).isEmpty();
    }

    /**
     * Revision de T2, M2: volver a migrar una base ya protegida no toma ACCESS EXCLUSIVE en las tablas (en Clever la
     * instancia vieja sigue sirviendo mientras arranca la nueva). Con una transaccion abierta que lee una tabla, el
     * migrate con lock_timeout corto tiene que terminar.
     */
    @Test
    void volverAMigrarNoBloqueaLasTablasEnUso() throws Exception {
        String baseDeDatos = migrarComoPropietario();
        try (Connection enUso = PostgresEmbebido.dataSourceComo(PostgresEmbebido.APP, baseDeDatos).getConnection()) {
            enUso.setAutoCommit(false);
            enUso.createStatement().executeQuery("select count(*) from ganera.gestoria").close();

            assertTimeoutPreemptively(Duration.ofSeconds(60), () -> PostgresEmbebido.flywayPropietario(baseDeDatos)
                    .initSql("SET lock_timeout = '3s'").load().migrate());
            enUso.rollback();
        }
        assertThat(problemas(baseDeDatos)).isEmpty();
    }

    // --- ayudantes ---

    private static final String[] UBICACIONES_CON_TABLAS_FUTURAS = {
            "classpath:db/migration", "classpath:db/postgresql", "classpath:db/test-tablas-futuras"};

    private static List<Map<String, Object>> politicasConOid(JdbcTemplate jdbc) {
        return jdbc.queryForList("select p.oid::bigint as oid, c.relname::text as tabla, p.polname::text as polname "
                + "from pg_policy p join pg_class c on c.oid = p.polrelid join pg_namespace n on n.oid = c.relnamespace "
                + "where n.nspname = 'ganera' order by 2, 3");
    }

    private static String migrarComoPropietario() {
        String baseDeDatos = PostgresEmbebido.nuevaBaseDeDatos();
        assertThat(PostgresEmbebido.flywayPropietario(baseDeDatos).load().migrate().migrationsExecuted)
                .isEqualTo(23);
        return baseDeDatos;
    }

    /** El script 02 leido del disco: una linea "objeto: problema" por fila. */
    static List<String> problemas(String baseDeDatos) {
        return new JdbcTemplate(PostgresEmbebido.dataSource(baseDeDatos)).query(
                PostgresEmbebido.leer(PostgresEmbebido.SCRIPT_COMPROBAR),
                (fila, i) -> fila.getString("objeto") + ": " + fila.getString("problema"));
    }

    private static List<String> tablas(JdbcTemplate jdbc) {
        return jdbc.queryForList("select c.relname from pg_class c join pg_namespace n on n.oid = c.relnamespace "
                + "where n.nspname = 'ganera' and c.relkind in ('r', 'p') order by 1", String.class);
    }

    private static List<Map<String, Object>> politicas(JdbcTemplate jdbc, String tabla) {
        return jdbc.queryForList("select policyname::text, permissive, cmd, roles::text, coalesce(qual, '') as qual, "
                + "coalesce(with_check, '') as with_check from pg_policies "
                + "where schemaname = 'ganera' and tablename = ? order by policyname", tabla);
    }

    private static List<String> privilegiosDeGaneraApp(JdbcTemplate jdbc, String relacion, char tipo) {
        return jdbc.queryForList("select distinct a.privilege_type from pg_class c, "
                + "aclexplode(coalesce(c.relacl, acldefault(?::\"char\", c.relowner))) a "
                + "where c.oid = ?::regclass and a.grantee = 'ganera_app'::regrole order by 1",
                String.class, String.valueOf(tipo), "ganera." + relacion);
    }

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
