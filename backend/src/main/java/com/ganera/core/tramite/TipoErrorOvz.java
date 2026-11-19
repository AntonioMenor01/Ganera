package com.ganera.core.tramite;

/**
 * Clase de un {@code ERROR} de presentación automática (D11). Gobierna el reencolado:
 *
 * <ul>
 *   <li>{@code SEGURO} — falló <b>antes</b> de «Aceptar» (verificación que no cuadra, login, navegación,
 *       certificado…): no se presentó nada, así que reencolar está permitido.</li>
 *   <li>{@code INCIERTO} — falló <b>después</b> de «Aceptar» (caída/timeout tras pulsarlo) y la consulta en
 *       Documentos no lo resolvió: reencolar <b>solo</b> con confirmación explícita y auditada del gestor de que
 *       la baja no se presentó (riesgo de doble presentación).</li>
 * </ul>
 */
public enum TipoErrorOvz {
    SEGURO,
    INCIERTO
}
