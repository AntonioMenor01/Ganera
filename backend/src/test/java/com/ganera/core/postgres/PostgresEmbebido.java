package com.ganera.core.postgres;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.ganera.core.GaneraApplication;

/**
 * Un unico PostgreSQL 16 real embebido (io.zonky.test:embedded-postgres, binarios desde Maven
 * Central, sin Docker) compartido por toda la JVM de tests (Prompt C, T1). Se arranca la primera
 * vez que se pide y se para con un shutdown hook. Cada test pide su propia base de datos vacia
 * ({@link #nuevaBaseDeDatos()}), asi que no comparten datos ni esquema.
 */
final class PostgresEmbebido {

    static final String USUARIO = "postgres";

    private static EmbeddedPostgres postgres;

    private PostgresEmbebido() {
    }

    private static synchronized EmbeddedPostgres instancia() {
        if (postgres == null) {
            try {
                // UTF8 como el PostgreSQL gestionado de Clever: sin esto, initdb en Windows toma la
                // codificacion de la configuracion regional (WIN1252 en esta maquina).
                postgres = EmbeddedPostgres.builder().setLocaleConfig("encoding", "UTF8").start();
            } catch (IOException e) {
                throw new UncheckedIOException("No arranca el PostgreSQL embebido", e);
            }
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

    /** Crea una base de datos vacia con nombre unico y devuelve su nombre. */
    static String nuevaBaseDeDatos() {
        String nombre = "t_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection conexion = instancia().getPostgresDatabase().getConnection();
             Statement st = conexion.createStatement()) {
            st.execute("CREATE DATABASE " + nombre);
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo crear la base de datos " + nombre, e);
        }
        return nombre;
    }

    static DataSource dataSource(String baseDeDatos) {
        return instancia().getDatabase(USUARIO, baseDeDatos);
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
}
