package com.ganera.core.shared.auditoria;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Ayudante de los tests E2E que comparten la H2 en memoria (plan 2026-10-21, aviso de T1). En cuanto un
 * endpoint auditado escribe en {@code auditoria}, los {@code deleteAll()} de usuarios y gestorias del
 * {@code @AfterEach} de esos tests fallan por {@code fk_auditoria_usuario} / {@code fk_auditoria_gestoria}:
 * la FK NO tiene {@code ON DELETE} (a proposito), y las filas que confirman los tests sin transaccion quedan
 * en la base compartida. {@code auditoria} tampoco tiene un repositorio con {@code delete} (a proposito), asi
 * que la unica salida es borrar por JDBC. En H2 no hay trigger de inmutabilidad (V26 solo en PostgreSQL), asi
 * que este {@code delete} funciona; en PostgreSQL embebido cada test usa una base nueva y no hace falta.
 *
 * <p>Se llama SIEMPRE antes de borrar usuarios y gestorias. Borra la tabla entera (no por gestoria): cada
 * test E2E limpia por completo su estado, asi que vaciar {@code auditoria} no afecta a nadie mas.
 */
public final class LimpiezaAuditoria {

    private LimpiezaAuditoria() {
    }

    public static void borrar(JdbcTemplate jdbc) {
        jdbc.update("delete from auditoria");
    }
}
