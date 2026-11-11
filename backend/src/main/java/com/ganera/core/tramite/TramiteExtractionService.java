package com.ganera.core.tramite;

import java.time.LocalDate;
import java.util.List;

/**
 * Abstrae el proveedor de IA que extrae tipo, crotales y datos del hecho de un mensaje de WhatsApp.
 * La implementacion lanza {@link ExtraccionFallidaException} si no hay una extraccion utilizable;
 * nunca devuelve una a medias.
 */
public interface TramiteExtractionService {

    TramiteExtraido extraer(String mensajeOriginal);

    /**
     * Datos opcionales del hecho (alta, baja, movimiento). Todos nullable.
     */
    record DatosHecho(
            LocalDate fechaHecho,
            LocalDate fechaNacimiento,
            String sexo,
            String crotalMadre,
            String numeroMer,
            String destinoRega,
            String destinoTexto,
            String consignatario,
            String aptitudMovimiento,
            LocalDate fechaIdentificacion) {

        public static DatosHecho vacio() {
            return new DatosHecho(null, null, null, null, null, null, null, null, null, null);
        }
    }

    /**
     * @param tipoTramite {@code null} si la IA no ha identificado el tipo con claridad o dice que el
     *                    mensaje no pide ningun tramite.
     * @param crotales    los identificadores tal como se escribieron en el mensaje, sin normalizar
     *                    ni completar (la normalizacion con CrotalNormalizador es de quien llama).
     * @param sinTramite  la IA dice que el mensaje no pide ningun tramite (SIN_TRAMITE); siempre con
     *                    tipo {@code null}. Es lo que contesto la IA, sin la red de seguridad D2, que
     *                    aplica {@link ExtraccionValidador}.
     * @param datosHecho  campos opcionales del hecho; nunca null (usar {@link DatosHecho#vacio()}).
     */
    record TramiteExtraido(TipoTramite tipoTramite, List<String> crotales, boolean sinTramite, DatosHecho datosHecho) {

        public TramiteExtraido {
            if (datosHecho == null) {
                datosHecho = DatosHecho.vacio();
            }
        }

        /** Un tipo identificado o NO_IDENTIFICADO ({@code tipoTramite == null}): nunca SIN_TRAMITE. */
        public TramiteExtraido(TipoTramite tipoTramite, List<String> crotales) {
            this(tipoTramite, crotales, false, DatosHecho.vacio());
        }

        public TramiteExtraido(TipoTramite tipoTramite, List<String> crotales, boolean sinTramite) {
            this(tipoTramite, crotales, sinTramite, DatosHecho.vacio());
        }
    }
}
