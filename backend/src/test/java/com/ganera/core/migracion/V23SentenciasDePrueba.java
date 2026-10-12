package com.ganera.core.migracion;

import java.util.List;

/**
 * Sentencias SQL compartidas por los tests de V23 en H2 (V23DescarteSinTramiteMigracionTest) y en
 * PostgreSQL embebido (V23DescarteSinTramitePostgresTest), para que los dos prueben exactamente lo mismo.
 * Datos inventados. Fechas TIMESTAMP literales (sin DEFAULT now()).
 */
public final class V23SentenciasDePrueba {

    private V23SentenciasDePrueba() {
    }

    /** Antes de V23: gestoria 1 con su usuario y contacto, y un tramite por estado relevante (id 1-5). */
    public static final List<String> DATOS_PREVIOS = List.of(
            "INSERT INTO gestoria (id, nombre) VALUES (1, 'Gestoría Uno')",
            "INSERT INTO usuario (id, gestoria_id, email, password_hash, nombre) "
                    + "VALUES (1, 1, 'uno@test.com', 'x', 'Usuario Uno')",
            "INSERT INTO contacto (id, gestoria_id, telefono, nombre) VALUES (1, 1, '+34600000001', 'Contacto Uno')",
            "INSERT INTO tramite (id, gestoria_id, contacto_id, estado) VALUES "
                    + "(1, 1, 1, 'PENDIENTE_EXTRACCION'),"
                    + "(2, 1, 1, 'PENDIENTE_REVISION'),"
                    + "(3, 1, 1, 'APROBADO'),"
                    + "(4, 1, 1, 'RECHAZADO')",
            "INSERT INTO tramite (id, gestoria_id, contacto_id, estado, hecho_en_ovz_por, hecho_en_ovz_en) "
                    + "VALUES (5, 1, 1, 'HECHO_EN_OVZ', 1, TIMESTAMP '2026-10-07 10:30:00')");

    /** Despues de V23: un tramite ya descartado (id 41), coherente con el CHECK. */
    public static final List<String> DATOS_TRAS_MIGRAR = List.of(
            "INSERT INTO tramite (id, gestoria_id, contacto_id, estado, motivo_rechazo) "
                    + "VALUES (41, 1, 1, 'RECHAZADO', 'SIN_TRAMITE')");

    /** motivo_rechazo con un estado distinto de RECHAZADO (A1, A5): el CHECK debe rechazarlas todas. */
    public static final List<String> MOTIVO_CON_OTRO_ESTADO = List.of(
            "UPDATE tramite SET motivo_rechazo = 'SIN_TRAMITE' WHERE id = 1",
            "UPDATE tramite SET motivo_rechazo = 'SIN_TRAMITE' WHERE id = 2",
            "UPDATE tramite SET motivo_rechazo = 'SIN_TRAMITE' WHERE id = 3",
            "UPDATE tramite SET motivo_rechazo = 'SIN_TRAMITE' WHERE id = 5",
            "INSERT INTO tramite (id, gestoria_id, contacto_id, estado, motivo_rechazo) "
                    + "VALUES (40, 1, 1, 'PENDIENTE_REVISION', 'SIN_TRAMITE')",
            // Deshacer a medias: volver a pendiente sin vaciar el motivo del descartado 41.
            "UPDATE tramite SET estado = 'PENDIENTE_REVISION' WHERE id = 41",
            "UPDATE tramite SET estado = 'APROBADO' WHERE id = 41");

    /** Un codigo que no esta en la lista cerrada, aunque el estado sea RECHAZADO. */
    public static final List<String> CODIGO_NO_PREVISTO = List.of(
            "UPDATE tramite SET motivo_rechazo = 'OTRO_MOTIVO' WHERE id = 4",
            "UPDATE tramite SET motivo_rechazo = 'sin_tramite' WHERE id = 4",
            "UPDATE tramite SET motivo_rechazo = '' WHERE id = 4",
            "UPDATE tramite SET motivo_rechazo = 'SPAM' WHERE id = 41",
            "INSERT INTO tramite (id, gestoria_id, contacto_id, estado, motivo_rechazo) "
                    + "VALUES (42, 1, 1, 'RECHAZADO', 'SPAM')");

    /** Las combinaciones validas: descartar, deshacer, un rechazo normal y un descartado nuevo. */
    public static final List<String> COHERENTES = List.of(
            "UPDATE tramite SET estado = 'RECHAZADO', motivo_rechazo = 'SIN_TRAMITE' WHERE id = 2",
            "UPDATE tramite SET estado = 'PENDIENTE_REVISION', motivo_rechazo = NULL WHERE id = 2",
            "UPDATE tramite SET estado = 'PENDIENTE_REVISION', motivo_rechazo = NULL WHERE id = 41",
            "UPDATE tramite SET motivo_rechazo = 'SIN_TRAMITE' WHERE id = 4",
            "UPDATE tramite SET motivo_rechazo = NULL WHERE id = 4",
            "INSERT INTO tramite (id, gestoria_id, contacto_id, estado, motivo_rechazo) "
                    + "VALUES (43, 1, 1, 'RECHAZADO', 'SIN_TRAMITE')");

    public static final List<String> EVENTOS_VALIDOS = List.of(
            "INSERT INTO tramite_evento (gestoria_id, tramite_id, tipo, usuario_id, creado_en) "
                    + "VALUES (1, 4, 'DESCARTADO', 1, TIMESTAMP '2026-10-12 09:00:00')",
            "INSERT INTO tramite_evento (gestoria_id, tramite_id, tipo, usuario_id, creado_en) "
                    + "VALUES (1, 4, 'DESCARTE_DESHECHO', 1, TIMESTAMP '2026-10-12 09:05:00.123456')");

    public static final String EVENTO_TIPO_NO_PREVISTO =
            "INSERT INTO tramite_evento (gestoria_id, tramite_id, tipo, usuario_id, creado_en) "
                    + "VALUES (1, 4, 'APROBADO', 1, TIMESTAMP '2026-10-12 09:00:00')";

    /** Usuario, tramite y gestoria inexistentes: las tres claves ajenas. */
    public static final List<String> EVENTOS_CON_FK_ROTA = List.of(
            "INSERT INTO tramite_evento (gestoria_id, tramite_id, tipo, usuario_id, creado_en) "
                    + "VALUES (1, 4, 'DESCARTADO', 999, TIMESTAMP '2026-10-12 09:00:00')",
            "INSERT INTO tramite_evento (gestoria_id, tramite_id, tipo, usuario_id, creado_en) "
                    + "VALUES (1, 999, 'DESCARTADO', 1, TIMESTAMP '2026-10-12 09:00:00')",
            "INSERT INTO tramite_evento (gestoria_id, tramite_id, tipo, usuario_id, creado_en) "
                    + "VALUES (999, 4, 'DESCARTADO', 1, TIMESTAMP '2026-10-12 09:00:00')");
}
