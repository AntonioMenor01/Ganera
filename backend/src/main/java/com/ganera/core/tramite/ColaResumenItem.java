package com.ganera.core.tramite;

import java.time.Instant;

/**
 * Fila de la previa de la cola del dashboard (GET /resumen). Proyección directa de una sola consulta
 * (constructor expression en {@link TramiteRepository#previaCola}) con left join a explotación y ganadero:
 * nunca una consulta por fila. Si el trámite no tiene explotación todavía (cola ambigua),
 * explotacionCodigoRega/explotacionNombre/ganaderoNombre llegan null. tipoTramite/estado se serializan por su
 * nombre (como el resto de respuestas de trámite). El ganadero solo es alcanzable vía explotación, por eso
 * va aquí y no se expone ningún dato OVZ.
 */
public record ColaResumenItem(
        Long id,
        TipoTramite tipoTramite,
        EstadoTramite estado,
        Instant creadoEn,
        String explotacionCodigoRega,
        String explotacionNombre,
        String ganaderoNombre) {
}
