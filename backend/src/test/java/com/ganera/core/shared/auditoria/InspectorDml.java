package com.ganera.core.shared.auditoria;

import org.hibernate.resource.jdbc.spi.StatementInspector;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * {@link StatementInspector} de Hibernate que, SOLO en tests, anota en que tablas se escribe
 * ({@code insert}/{@code update}/{@code delete}) durante un escenario. Lo usa {@link GuardiaDml} (la
 * guardia DML del plan 2026-10-21, T6): si una peticion/pasada escribe en una tabla de negocio y no hubo
 * ningun {@code insert into auditoria}, el escenario va en rojo — asi una escritura nueva DENTRO de un
 * endpoint ya auditado tambien rompe.
 *
 * <p>Se registra por el nombre de clase en el {@code application.yml} de tests
 * ({@code spring.jpa.properties.hibernate.session_factory.statement_inspector}); Hibernate lo instancia con
 * su constructor sin argumentos. Por eso el acumulador es ESTATICO (no un {@code ThreadLocal}): con un
 * servidor real (RANDOM_PORT) las escrituras ocurren en el hilo del servidor, no en el del test.
 *
 * <p>Pasivo por defecto: solo acumula entre {@link #armar()} y {@link #desarmar()} (lo gestiona
 * {@link GuardiaDmlExtension}), de modo que la suite completa no acumule nada fuera de los tests con guardia.
 * Los tests corren en serie (Surefire forkCount=1, sin paralelismo), asi que el acumulador estatico es seguro.
 */
public class InspectorDml implements StatementInspector {

    /** Una escritura observada: operacion ({@code insert}/{@code update}/{@code delete}) y tabla. */
    public record Escritura(String operacion, String tabla) {
    }

    // AVISO: el acumulador es ESTATICO y compartido. La guardia (armar/leer/desarmar por escenario) solo es
    // correcta si los tests corren EN SERIE (Surefire forkCount=1, sin paralelismo de JUnit). NO actives
    // junit.jupiter.execution.parallel.enabled (ni @Execution(CONCURRENT)) sin rehacer esto con estado por
    // hilo/escenario: con escenarios solapados, las escrituras de uno contarian para la guardia de otro.
    private static final List<Escritura> ESCRITURAS = new CopyOnWriteArrayList<>();
    private static volatile boolean activo = false;

    private static final Pattern INSERT = Pattern.compile("(?is)^\\s*insert\\s+into\\s+\"?([a-z_]+)");
    private static final Pattern UPDATE = Pattern.compile("(?is)^\\s*update\\s+\"?([a-z_]+)");
    private static final Pattern DELETE = Pattern.compile("(?is)^\\s*delete\\s+from\\s+\"?([a-z_]+)");

    @Override
    public String inspect(String sql) {
        if (activo && sql != null) {
            registrarSi(INSERT, "insert", sql);
            registrarSi(UPDATE, "update", sql);
            registrarSi(DELETE, "delete", sql);
        }
        return sql;
    }

    private static void registrarSi(Pattern patron, String operacion, String sql) {
        Matcher m = patron.matcher(sql);
        if (m.find()) {
            ESCRITURAS.add(new Escritura(operacion, m.group(1).toLowerCase(Locale.ROOT)));
        }
    }

    /** Arma la captura y vacia lo anterior (lo llama la guardia antes de cada escenario). */
    public static void armar() {
        ESCRITURAS.clear();
        activo = true;
    }

    /** Desarma la captura y vacia (lo llama la guardia despues de cada escenario). */
    public static void desarmar() {
        activo = false;
        ESCRITURAS.clear();
    }

    public static List<Escritura> escrituras() {
        return List.copyOf(ESCRITURAS);
    }

    /** Tablas en las que se ha escrito (cualquier operacion) desde el ultimo {@link #armar()}. */
    public static Set<String> tablasEscritas() {
        return ESCRITURAS.stream().map(Escritura::tabla).collect(Collectors.toSet());
    }

    /** Si hubo al menos una fila nueva en {@code auditoria}. */
    public static boolean huboInsertEnAuditoria() {
        return ESCRITURAS.stream().anyMatch(e -> e.operacion().equals("insert") && e.tabla().equals("auditoria"));
    }
}
