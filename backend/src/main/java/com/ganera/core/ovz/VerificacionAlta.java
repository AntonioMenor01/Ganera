package com.ganera.core.ovz;

/**
 * Verificación pura de la pantalla 2 del alta por nacimiento en OVZ (plan 2026-11-23, T4): el crotal seleccionado
 * que muestra OVZ ({@code P_lblcrotal}, el ternero elegido en la tabla de crotales repartidos) debe ser el crotal
 * del ternero del trámite. Sin esto el worker NO pulsa «Aceptar». El REGA no se verifica aquí: en el alta la tabla
 * de crotales repartidos es la de la explotación activa (no hay un campo de REGA legible en la pantalla 2); que el
 * ternero figure «Libre» en esa tabla lo comprueba el worker al navegar. El motivo nunca lleva el crotal.
 */
public final class VerificacionAlta {

    static final String MOTIVO_CROTAL =
            "El crotal seleccionado en OVZ no coincide con el del ternero del trámite.";

    private VerificacionAlta() {
    }

    public static ResultadoVerificacion verificar(String crotalSeleccionadoLeido, String crotalTerneroEsperado) {
        String leido = VerificacionBaja.normalizar(crotalSeleccionadoLeido);
        String esperado = VerificacionBaja.normalizar(crotalTerneroEsperado);
        if (leido.isEmpty() || esperado.isEmpty() || !leido.equals(esperado)) {
            return new ResultadoVerificacion(false, MOTIVO_CROTAL);
        }
        return new ResultadoVerificacion(true, null);
    }
}
