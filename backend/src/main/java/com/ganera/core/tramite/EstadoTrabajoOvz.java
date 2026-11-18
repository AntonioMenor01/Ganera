package com.ganera.core.tramite;

/**
 * Estado del trabajo de presentación automática en OVZ (escalón 2, T2; tabla {@code trabajo_ovz}, V33).
 *
 * <ul>
 *   <li>{@code PENDIENTE} — encolado al aprobar la baja; a la espera de que el worker lo reclame.</li>
 *   <li>{@code EN_PROCESO} — reclamado por el worker (una sesión OVZ en curso); el trámite pasa a
 *       {@code EstadoTramite.EN_PROCESO}.</li>
 *   <li>{@code EJECUTADO} — baja presentada en OVZ (Ganera pulsó «Aceptar»); lo fija el worker (T4).</li>
 *   <li>{@code ERROR} — falló; con {@link TipoErrorOvz} SEGURO (antes de «Aceptar», no se presentó nada) o
 *       INCIERTO (tras «Aceptar», sin resolver en Documentos). Lo fija el worker (T4); gobierna el reencolado
 *       (D11).</li>
 * </ul>
 */
public enum EstadoTrabajoOvz {
    PENDIENTE,
    EN_PROCESO,
    EJECUTADO,
    ERROR
}
