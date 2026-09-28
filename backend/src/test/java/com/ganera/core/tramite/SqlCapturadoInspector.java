package com.ganera.core.tramite;

import org.hibernate.resource.jdbc.spi.StatementInspector;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * StatementInspector de Hibernate SOLO para tests: guarda el SQL que se prepara para poder
 * comprobar que la carga del Tramite en revision sale con bloqueo ("for update").
 * Se activa por propiedad en TramiteRevisionEndToEndTest.
 */
public class SqlCapturadoInspector implements StatementInspector {

    private static final List<String> SQL = new CopyOnWriteArrayList<>();

    @Override
    public String inspect(String sql) {
        SQL.add(sql);
        return sql;
    }

    static void limpiar() {
        SQL.clear();
    }

    static List<String> capturado() {
        return List.copyOf(SQL);
    }
}
