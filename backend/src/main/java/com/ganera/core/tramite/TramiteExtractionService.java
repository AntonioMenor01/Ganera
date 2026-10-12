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
     * @param tipoTramite {@code null} si la IA no ha identificado el tipo con claridad o dice que el
     *                    mensaje no pide ningun tramite.
     * @param crotales    los identificadores tal como se escribieron en el mensaje, sin normalizar
     *                    ni completar (la normalizacion con CrotalNormalizador es de quien llama).
     * @param sinTramite  la IA dice que el mensaje no pide ningun tramite (SIN_TRAMITE); siempre con
     *                    tipo {@code null}. Es lo que contesto la IA, sin la red de seguridad D2, que
     *                    aplica {@link ExtraccionValidador}.
     */
    record TramiteExtraido(TipoTramite tipoTramite, List<String> crotales, boolean sinTramite) {

        /** Un tipo identificado o NO_IDENTIFICADO ({@code tipoTramite == null}): nunca SIN_TRAMITE. */
        public TramiteExtraido(TipoTramite tipoTramite, List<String> crotales) {
            this(tipoTramite, crotales, false);
        }
    }
}
