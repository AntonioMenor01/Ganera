package com.ganera.core.tramite;

import java.util.List;

/**
 * Cuerpo de PATCH /tramites/{id}. version (decision 27) es OBLIGATORIA: la que mostraba la pantalla
 * (TramiteDetalleResponse.version); ausente -> 400, distinta de la actual -> 409 sin cambios.
 * El resto: un campo null significa "no cambiar". tipoTramite llega como texto y lo valida el
 * controlador (400 { "motivo" } si no es un valor de TipoTramite). crotales es la lista COMPLETA y
 * sustituye a la anterior (decision 6); una lista vacia los quita todos.
 */
public record TramitePatchRequest(Long version, Long explotacionId, String tipoTramite, List<String> crotales) {
}
