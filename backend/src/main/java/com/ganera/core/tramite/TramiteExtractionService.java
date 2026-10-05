package com.ganera.core.tramite;

import java.util.List;

/**
 * Abstrae el proveedor de IA que extrae {tipoTramite, crotales} de un mensaje de WhatsApp en
 * lenguaje natural. La implementacion lanza {@link ExtraccionFallidaException} si no hay una
 * extraccion utilizable; nunca devuelve una a medias.
 */
public interface TramiteExtractionService {

    TramiteExtraido extraer(String mensajeOriginal);

    /**
     * @param tipoTramite {@code null} si la IA no ha identificado el tipo con claridad.
     * @param crotales    los identificadores tal como se escribieron en el mensaje, sin normalizar
     *                    ni completar (la normalizacion con CrotalNormalizador es de quien llama).
     */
    record TramiteExtraido(TipoTramite tipoTramite, List<String> crotales) {
    }
}
