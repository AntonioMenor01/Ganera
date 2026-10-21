package com.ganera.core.postgres;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.ganera.core.GaneraApplication;

/**
 * Un unico PostgreSQL 17 real embebido (io.zonky.test:embedded-postgres, binarios desde Maven
 * Central, sin Docker) compartido por toda la JVM de tests (Prompt C, T1). Se arranca la primera
 * vez que se pide y se para con un shutdown hook. Cada test pide su propia base de datos vacia
 * ({@link #nuevaBaseDeDatos()}), asi que no comparten datos ni esquema.
 *
 * <p>Migracion a Supabase, T2: al arrancar crea el rol {@value #ADMIN} ({@code LOGIN CREATEROLE CREATEDB}, SIN
 * superusuario), que imita al {@code postgres} de Supabase, y los roles de Supabase {@code anon},
 * {@code authenticated} ({@code NOLOGIN}), {@code service_role} ({@code NOLOGIN BYPASSRLS}) y {@code authenticator}
 * ({@code LOGIN NOINHERIT}, miembro de los otros tres), como alli. Cada base
 * nueva es de {@value #ADMIN} y recibe el script {@code backend/supabase/01-preparar-roles-y-esquema.sql}, leido
 * del disco y ejecutado COMO {@value #ADMIN}: un permiso que en Supabase faltaria tambien falta aqui. Los tests de
 * antes siguen conectando como {@value #USUARIO} (superusuario) al esquema {@code public}.
 */
final class PostgresEmbebido {

    static final String USUARIO = "postgres";

    /** Imita al usuario {@code postgres} de Supabase: puede crear roles y bases, pero no es superusuario. */
    static final String ADMIN = "admin_supabase";

    static final String PROPIETARIO = "ganera_propietario";

    static final String APP = "ganera_app";

    static final String ESQUEMA = "ganera";

    /** Los scripts que ejecuta Antonio en Supabase, leidos del disco (los tests corren desde backend/). */
    static final Path SCRIPT_PREPARAR = Path.of("supabase/01-preparar-roles-y-esquema.sql");

    static final Path SCRIPT_COMPROBAR = Path.of("supabase/02-comprobar-seguridad.sql");

    /** Certificados SOLO DE PRUEBA (S5), leidos del disco. */
    static final String SSL_PRUEBAS = "src/test/resources/ssl-pruebas";

    /** Cualquier contrasena vale: la autenticacion del embebido es trust. No esta vacia para que se vea que se pasa. */
    static final String CONTRASENA_IGNORADA = "ignorada-auth-trust";

    /** Las mismas que {@code spring.flyway.locations} con vendor = postgresql (lo vigila un test). */
    static final String[] UBICACIONES_FLYWAY = {"classpath:db/migration", "classpath:db/postgresql"};

    private static EmbeddedPostgres postgres;

    private PostgresEmbebido() {
    }

    private static synchronized EmbeddedPostgres instancia() {
        if (postgres == null) {
            try {
                // UTF8 como el PostgreSQL gestionado (Supabase): sin esto, initdb en Windows toma la
                // codificacion de la configuracion regional (WIN1252 en esta maquina).
                //
                // ssl=on con el certificado SOLO DE PRUEBA (T3, S5): el perfil clever se prueba con
                // sslmode=verify-full, como contra Supabase. Los demas tests siguen igual (pgjdbc usa
                // sslmode=prefer por defecto: cifra, pero no valida el certificado).
                Path[] ssl = ficherosSslDePrueba();
                postgres = EmbeddedPostgres.builder()
                        .setLocaleConfig("encoding", "UTF8")
                        .setServerConfig("ssl", "on")
                        .setServerConfig("ssl_cert_file", rutaParaPostgres(ssl[0]))
                        .setServerConfig("ssl_key_file", rutaParaPostgres(ssl[1]))
                        .start();
            } catch (IOException e) {
                throw new UncheckedIOException("No arranca el PostgreSQL embebido", e);
            }
            crearRolesDeSupabase(postgres);
            EmbeddedPostgres arrancado = postgres;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    arrancado.close();
                } catch (IOException ignorada) {
                    // la JVM se esta cerrando
                }
            }));
        }
        return postgres;
    }

    /**
     * Copia el certificado y la clave de prueba (src/test/resources/ssl-pruebas) a un directorio temporal: PostgreSQL
     * exige en Linux/macOS que la clave sea del usuario y con permisos 0600 (en Windows no lo comprueba), y en el
     * repositorio no se pueden fijar permisos de forma portable.
     */
    private static Path[] ficherosSslDePrueba() throws IOException {
        Path directorio = Files.createTempDirectory("ganera-ssl-pruebas");
        Path certificado = directorio.resolve("servidor.crt");
        Path clave = directorio.resolve("servidor.key");
        Files.copy(Path.of(SSL_PRUEBAS, "servidor-PRUEBA-localhost.crt"), certificado);
        Files.copy(Path.of(SSL_PRUEBAS, "servidor-PRUEBA-localhost.key"), clave);
        if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            Files.setPosixFilePermissions(clave, EnumSet.of(PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE));
        }
        for (Path fichero : new Path[] {certificado, clave, directorio}) {
            fichero.toFile().deleteOnExit();
        }
        return new Path[] {certificado, clave};
    }

    /** Ruta absoluta con barras normales: PostgreSQL las admite tambien en Windows y no hay que escapar nada. */
    private static String rutaParaPostgres(Path fichero) {
        return fichero.toAbsolutePath().toString().replace('\\', '/');
    }

    /**
     * Los roles que en Supabase ya existen: {@value #ADMIN} (su {@code postgres}), anon, authenticated, service_role
     * y authenticator (miembro de los tres). Idempotente. Los crea el superusuario: {@code BYPASSRLS} no lo puede dar
     * {@value #ADMIN}.
     */
    private static void crearRolesDeSupabase(EmbeddedPostgres servidor) {
        crearRolesDeSupabase(servidor.getPostgresDatabase(),
                List.of("anon", "authenticated", "service_role", "authenticator"));
        // Como en Supabase: authenticator (el rol con LOGIN de la Data API) puede ser anon, authenticated y
        // service_role. Repetir el GRANT no falla (solo avisa).
        try (Connection conexion = servidor.getPostgresDatabase().getConnection();
             Statement st = conexion.createStatement()) {
            st.execute("GRANT anon, authenticated, service_role TO authenticator");
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudieron dar a authenticator los roles de Supabase", e);
        }
    }

    /** Crea {@value #ADMIN} y los roles de Supabase indicados (solo los que falten). */
    static void crearRolesDeSupabase(DataSource comoSuperusuario, List<String> rolesSupabase) {
        StringBuilder sql = new StringBuilder("DO $r$ BEGIN" + System.lineSeparator());
        sql.append(crearSiFalta(ADMIN, "LOGIN CREATEROLE CREATEDB NOSUPERUSER NOREPLICATION NOBYPASSRLS"));
        for (String rol : rolesSupabase) {
            String atributos = switch (rol) {
                case "service_role" -> "NOLOGIN BYPASSRLS";
                case "authenticator" -> "LOGIN NOINHERIT";
                default -> "NOLOGIN";
            };
            sql.append(crearSiFalta(rol, atributos));
        }
        sql.append("END $r$;");
        try (Connection conexion = comoSuperusuario.getConnection(); Statement st = conexion.createStatement()) {
            st.execute(sql.toString());
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudieron crear los roles de Supabase", e);
        }
    }

    private static String crearSiFalta(String rol, String atributos) {
        return "IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '" + rol + "') THEN CREATE ROLE " + rol + " "
                + atributos + "; END IF;" + System.lineSeparator();
    }

    /**
     * Crea una base de datos vacia con nombre unico, de {@value #ADMIN}, le ejecuta el script 01 como
     * {@value #ADMIN} y devuelve su nombre.
     */
    static String nuevaBaseDeDatos() {
        String nombre = "t_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection conexion = instancia().getPostgresDatabase().getConnection();
             Statement st = conexion.createStatement()) {
            st.execute("CREATE DATABASE " + nombre + " OWNER " + ADMIN);
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo crear la base de datos " + nombre, e);
        }
        ejecutarScript(dataSourceComo(ADMIN, nombre), SCRIPT_PREPARAR);
        return nombre;
    }

    static DataSource dataSource(String baseDeDatos) {
        return instancia().getDatabase(USUARIO, baseDeDatos);
    }

    /** Conexion como el rol indicado (la autenticacion del embebido es trust: sin contrasena). */
    static DataSource dataSourceComo(String rol, String baseDeDatos) {
        return instancia().getDatabase(rol, baseDeDatos);
    }

    /** Lee el script del disco y lo ejecuta entero, de una vez (como el editor SQL de Supabase o psql -f). */
    static void ejecutarScript(DataSource dataSource, Path script) {
        try (Connection conexion = dataSource.getConnection(); Statement st = conexion.createStatement()) {
            st.execute(leer(script));
        } catch (SQLException e) {
            throw new IllegalStateException("Fallo el script " + script, e);
        }
    }

    static String leer(Path fichero) {
        try {
            return Files.readString(fichero, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Flyway programatico con las mismas ubicaciones que Boot resuelve en PostgreSQL
     * ({@code classpath:db/migration,classpath:db/{vendor}} con vendor = postgresql): asi los tests de
     * migraciones tambien ejecutan lo que vaya en db/postgresql. Devuelve la configuracion para que cada test
     * pueda anadir su {@code target}.
     */
    static FluentConfiguration flyway(DataSource dataSource) {
        return Flyway.configure().dataSource(dataSource).locations(UBICACIONES_FLYWAY);
    }

    /**
     * Flyway como lo usa el perfil clever en Supabase: conectado como {@value #PROPIETARIO}, sobre el esquema
     * {@value #ESQUEMA}, que ya existe (lo crea el script 01; Flyway no lo crea).
     */
    static FluentConfiguration flywayPropietario(String baseDeDatos) {
        return flyway(dataSourceComo(PROPIETARIO, baseDeDatos))
                .defaultSchema(ESQUEMA).schemas(ESQUEMA).createSchemas(false);
    }

    static String jdbcUrl(String baseDeDatos) {
        return instancia().getJdbcUrl(USUARIO, baseDeDatos);
    }

    /**
     * Arranca la aplicacion completa (sin servidor web) contra esa base de datos: Flyway migra al
     * arrancar y Hibernate usa el {@code ddl-auto} indicado. El resto de la configuracion es la de
     * src/test/resources/application.yml (planificadores apagados). Los argumentos van como linea de
     * comandos para que tengan prioridad sobre el application.yml de test (que apunta a H2).
     */
    static ConfigurableApplicationContext arrancarAplicacion(String baseDeDatos, String ddlAuto, String... extra) {
        List<String> argumentos = new ArrayList<>(List.of(
                "--spring.datasource.url=" + jdbcUrl(baseDeDatos),
                "--spring.datasource.driver-class-name=org.postgresql.Driver",
                "--spring.datasource.username=" + USUARIO,
                "--spring.datasource.password=",
                "--spring.jpa.hibernate.ddl-auto=" + ddlAuto,
                "--spring.main.web-application-type=none",
                // Un nombre de pool propio: evita colisiones de JMX con otros contextos de la suite.
                "--spring.datasource.hikari.pool-name=pg-" + baseDeDatos,
                "--spring.jmx.enabled=false"));
        argumentos.addAll(List.of(extra));
        return new SpringApplicationBuilder(GaneraApplication.class).run(argumentos.toArray(String[]::new));
    }

    /** Puerto del PostgreSQL embebido (escucha en localhost). */
    static int puerto() {
        return instancia().getPort();
    }

    /**
     * Las siete variables del perfil clever (application-clever.yml) apuntando a esa base del embebido, con el host
     * indicado: la app como {@value #APP} y Flyway como {@value #PROPIETARIO}, como en Supabase (alli con
     * {@code rol.<ref>} en el pooler). Mapa ordenado y modificable, para que un test quite o cambie una.
     */
    static Map<String, String> variablesSupabase(String baseDeDatos, String host) {
        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("SUPABASE_DB_HOST", host);
        variables.put("SUPABASE_DB_PORT", String.valueOf(puerto()));
        variables.put("SUPABASE_DB_NAME", baseDeDatos);
        variables.put("SUPABASE_DB_USER", APP);
        variables.put("SUPABASE_DB_PASSWORD", CONTRASENA_IGNORADA);
        variables.put("SUPABASE_FLYWAY_USER", PROPIETARIO);
        variables.put("SUPABASE_FLYWAY_PASSWORD", CONTRASENA_IGNORADA);
        return variables;
    }

    static Map<String, String> variablesSupabase(String baseDeDatos) {
        return variablesSupabase(baseDeDatos, "localhost");
    }

    /**
     * Arranca la aplicacion completa con el perfil {@code clever} (servidor web en un puerto libre) y las variables
     * indicadas como argumentos de linea de comandos (no variables de entorno reales). Ninguna propiedad
     * {@code spring.datasource.*} ni {@code spring.flyway.*}: salen de application-clever.yml. El contexto es propio
     * del test, asi que el perfil no se activa en el resto de la suite (que sigue en H2).
     */
    static ConfigurableApplicationContext arrancarPerfilClever(Map<String, String> variables, String... extra) {
        List<String> argumentos = new ArrayList<>();
        argumentos.add("--spring.profiles.active=clever");
        variables.forEach((nombre, valor) -> argumentos.add("--" + nombre + "=" + valor));
        argumentos.add("--server.port=0");
        // Un nombre de pool propio: evita colisiones de JMX con otros contextos de la suite.
        argumentos.add("--spring.datasource.hikari.pool-name=pg-clever-" + UUID.randomUUID());
        argumentos.add("--spring.jmx.enabled=false");
        argumentos.addAll(List.of(extra));
        return new SpringApplicationBuilder(GaneraApplication.class).run(argumentos.toArray(String[]::new));
    }

    /** {@link #arrancarPerfilClever(Map, String...)} con las siete variables contra esa base y FRONTEND_ORIGEN. */
    static ConfigurableApplicationContext arrancarPerfilClever(String baseDeDatos) {
        return arrancarPerfilClever(variablesSupabase(baseDeDatos), "--FRONTEND_ORIGEN=https://app.ganera.es");
    }
}
