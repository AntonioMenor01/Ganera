package com.ganera.core.postgres;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Migracion a Supabase, T2: el script backend/supabase/01-preparar-roles-y-esquema.sql, que Antonio ejecuta en el
 * editor SQL de Supabase. Aqui lo ejecuta {@link PostgresEmbebido#nuevaBaseDeDatos()} como {@code admin_supabase}
 * (sin superusuario, como el {@code postgres} de Supabase), leido del disco.
 */
class PreparacionBaseDeDatosPostgresTest {

    private static final List<String> AJENOS = List.of("anon", "authenticated", "service_role");

    @Test
    void creaLosDosRolesDeGaneraConLosAtributosExactos() {
        JdbcTemplate jdbc = new JdbcTemplate(PostgresEmbebido.dataSource(PostgresEmbebido.nuevaBaseDeDatos()));

        for (String rol : List.of(PostgresEmbebido.PROPIETARIO, PostgresEmbebido.APP)) {
            Map<String, Object> atributos = jdbc.queryForMap(
                    "select rolsuper, rolbypassrls, rolcreaterole, rolcreatedb, rolreplication, rolcanlogin "
                            + "from pg_roles where rolname = ?", rol);
            assertThat(atributos).as(rol).containsExactlyInAnyOrderEntriesOf(Map.of(
                    "rolsuper", false, "rolbypassrls", false, "rolcreaterole", false, "rolcreatedb", false,
                    "rolreplication", false, "rolcanlogin", true));
        }
    }

    /**
     * Revision de T2, M5: si los roles ya existian con atributos indebidos (creados a mano), el 01 los corrige. Se
     * usan atributos que admin_supabase puede quitar (tiene CREATEDB y CREATEROLE). Los roles son del cluster
     * compartido: se deja todo como estaba en {@code finally}.
     */
    @Test
    void elScriptCorrigeLosAtributosDeRolesQueYaExistian() {
        // Asegura que los roles existen (la primera base del cluster los crea).
        PostgresEmbebido.nuevaBaseDeDatos();
        JdbcTemplate superusuario = new JdbcTemplate(PostgresEmbebido.dataSource(PostgresEmbebido.nuevaBaseDeDatos()));
        try {
            superusuario.execute("alter role ganera_app createdb createrole nologin");
            superusuario.execute("alter role ganera_propietario createdb");

            JdbcTemplate jdbc = new JdbcTemplate(PostgresEmbebido.dataSource(PostgresEmbebido.nuevaBaseDeDatos()));

            for (String rol : List.of(PostgresEmbebido.PROPIETARIO, PostgresEmbebido.APP)) {
                assertThat(jdbc.queryForMap("select rolcreatedb, rolcreaterole, rolcanlogin from pg_roles "
                        + "where rolname = ?", rol)).as(rol).containsExactlyInAnyOrderEntriesOf(Map.of(
                        "rolcreatedb", false, "rolcreaterole", false, "rolcanlogin", true));
            }
        } finally {
            superusuario.execute("alter role ganera_app nocreatedb nocreaterole login");
            superusuario.execute("alter role ganera_propietario nocreatedb");
        }
    }

    @Test
    void elEsquemaEsDelPropietarioYSoloGaneraAppTieneUsage() {
        JdbcTemplate jdbc = new JdbcTemplate(PostgresEmbebido.dataSource(PostgresEmbebido.nuevaBaseDeDatos()));

        assertThat(jdbc.queryForObject("select nspowner::regrole::text from pg_namespace where nspname = 'ganera'",
                String.class)).isEqualTo(PostgresEmbebido.PROPIETARIO);
        // PUBLIC es el grantee 0 en aclexplode.
        assertThat(privilegiosEnElEsquema(jdbc, "0")).isEmpty();
        for (String rol : AJENOS) {
            assertThat(privilegiosEnElEsquema(jdbc, "'" + rol + "'::regrole")).as(rol).isEmpty();
            assertThat(jdbc.queryForObject("select has_schema_privilege(?, 'ganera', 'USAGE') "
                    + "or has_schema_privilege(?, 'ganera', 'CREATE')", Boolean.class, rol, rol)).as(rol).isFalse();
        }
        assertThat(privilegiosEnElEsquema(jdbc, "'ganera_app'::regrole")).containsExactly("USAGE");
    }

    @Test
    void elScriptSeEjecutaDosVecesSinErrorYSinCambios() {
        String baseDeDatos = PostgresEmbebido.nuevaBaseDeDatos();
        JdbcTemplate jdbc = new JdbcTemplate(PostgresEmbebido.dataSource(baseDeDatos));
        List<Map<String, Object>> antes = foto(jdbc);

        PostgresEmbebido.ejecutarScript(PostgresEmbebido.dataSourceComo(PostgresEmbebido.ADMIN, baseDeDatos),
                PostgresEmbebido.SCRIPT_PREPARAR);

        assertThat(foto(jdbc)).isEqualTo(antes);
    }

    /**
     * La zona de la base se mira en el catalogo: con SHOW desde JDBC no se puede, porque pgjdbc impone la zona de
     * la JVM al conectar.
     */
    @Test
    void laBaseDeDatosQuedaEnUtc() {
        String baseDeDatos = PostgresEmbebido.nuevaBaseDeDatos();
        JdbcTemplate jdbc = new JdbcTemplate(PostgresEmbebido.dataSource(baseDeDatos));

        List<String> configuracion = jdbc.queryForList(
                "select unnest(s.setconfig) from pg_db_role_setting s join pg_database d on d.oid = s.setdatabase "
                        + "where d.datname = ? and s.setrole = 0", String.class, baseDeDatos);

        assertThat(configuracion).containsExactly("TimeZone=UTC");
    }

    private static List<String> privilegiosEnElEsquema(JdbcTemplate jdbc, String grantee) {
        return jdbc.queryForList("select a.privilege_type from pg_namespace n, "
                + "aclexplode(coalesce(n.nspacl, acldefault('n', n.nspowner))) a "
                + "where n.nspname = 'ganera' and a.grantee = " + grantee + " order by 1", String.class);
    }

    /** Lo que el script 01 decide: roles, membresias, el esquema con sus permisos y la configuracion de la base. */
    private static List<Map<String, Object>> foto(JdbcTemplate jdbc) {
        return jdbc.queryForList("""
                select 'rol' as tipo, r.rolname::text as nombre,
                       concat_ws(',', r.rolsuper, r.rolinherit, r.rolcreaterole, r.rolcreatedb, r.rolcanlogin,
                                 r.rolreplication, r.rolbypassrls, r.rolconnlimit, r.rolvaliduntil) as valor
                from pg_roles r
                where r.rolname in ('ganera_propietario', 'ganera_app', 'anon', 'authenticated', 'service_role')
                union all
                select 'miembro', m.roleid::regrole::text || '>' || m.member::regrole::text,
                       concat_ws(',', m.grantor::regrole, m.admin_option, m.inherit_option, m.set_option)
                from pg_auth_members m
                where m.roleid::regrole::text in ('ganera_propietario', 'ganera_app')
                union all
                select 'esquema', n.nspname::text, n.nspowner::regrole::text || ' ' || coalesce(n.nspacl::text, '')
                from pg_namespace n where n.nspname = 'ganera'
                union all
                select 'base', d.datname::text, array_to_string(s.setconfig, ',')
                from pg_db_role_setting s join pg_database d on d.oid = s.setdatabase
                where d.datname = current_database()
                order by 1, 2, 3
                """);
    }
}
