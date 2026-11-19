package com.ganera.core.ovz;

import java.util.Locale;

/**
 * Verificación pura de la pantalla 2 de la baja en OVZ (T4): el Código RIIA leído debe ser el crotal del trámite y la
 * explotación de ubicación debe contener el REGA del trámite. Sin esto el worker NO pulsa «Aceptar». Los motivos
 * nunca llevan el crotal ni el REGA.
 */
public final class VerificacionBaja {

    static final String MOTIVO_CROTAL = "El Código RIIA leído en OVZ no coincide con el crotal del trámite.";
    static final String MOTIVO_EXPLOTACION = "La explotación de ubicación en OVZ no coincide con el REGA del trámite.";

    private VerificacionBaja() {
    }

    public static ResultadoVerificacion verificar(String codigoRiiaLeido, String crotalEsperado,
                                                  String explotacionUbicacionLeida, String regaEsperado) {
        String crotalLeido = normalizar(codigoRiiaLeido);
        String crotal = normalizar(crotalEsperado);
        if (crotalLeido.isEmpty() || crotal.isEmpty() || !crotalLeido.equals(crotal)) {
            return new ResultadoVerificacion(false, MOTIVO_CROTAL);
        }
        String rega = normalizar(regaEsperado);
        String ubicacion = normalizar(explotacionUbicacionLeida);
        if (rega.isEmpty() || ubicacion.isEmpty() || !ubicacion.contains(rega)) {
            return new ResultadoVerificacion(false, MOTIVO_EXPLOTACION);
        }
        return new ResultadoVerificacion(true, null);
    }

    /** Sin espacios y en mayúsculas (como el spike). */
    static String normalizar(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!Character.isWhitespace(c)) {
                sb.append(c);
            }
        }
        return sb.toString().toUpperCase(Locale.ROOT);
    }
}
