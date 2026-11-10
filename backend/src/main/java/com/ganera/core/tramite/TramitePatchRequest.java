package com.ganera.core.tramite;

import java.time.LocalDate;
import java.util.List;

/**
 * Cuerpo de PATCH /tramites/{id}. version (decision 27) es OBLIGATORIA: la que mostraba la pantalla
 * (TramiteDetalleResponse.version); ausente -> 400, distinta de la actual -> 409 sin cambios.
 * El resto: un campo null significa "no cambiar". tipoTramite llega como texto y lo valida el
 * controlador (400 { "motivo" } si no es un valor de TipoTramite). crotales es la lista COMPLETA y
 * sustituye a la anterior (decision 6); una lista vacia los quita todos.
 * Campos OVZ (fechas, alta, destino, MER…): null = no cambiar. Una cadena en blanco limpia el
 * texto; las fechas no se pueden vaciar con este PATCH (mismo criterio que la explotación).
 */
public record TramitePatchRequest(
        Long version,
        Long explotacionId,
        String tipoTramite,
        List<String> crotales,
        LocalDate fechaHecho,
        LocalDate fechaNacimiento,
        String sexo,
        String crotalMadreIndicado,
        String numeroMer,
        String destinoRega,
        String destinoTexto,
        String consignatario,
        String aptitudMovimiento,
        LocalDate fechaIdentificacion) {

    /** Compatibilidad con tests y clientes que solo mandan version/explotacion/tipo/crotales. */
    public TramitePatchRequest(Long version, Long explotacionId, String tipoTramite, List<String> crotales) {
        this(version, explotacionId, tipoTramite, crotales,
                null, null, null, null, null, null, null, null, null, null);
    }
}
