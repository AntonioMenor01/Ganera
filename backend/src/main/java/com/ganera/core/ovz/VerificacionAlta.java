package com.ganera.core.ovz;

/**
 * Verificación pura de la pantalla 2 del alta por nacimiento en OVZ (plan 2026-11-23, T4): el crotal seleccionado
 * que muestra OVZ ({@code P_lblcrotal}, el ternero elegido en la tabla de crotales repartidos) debe ser el crotal
 * del ternero del trámite. Sin esto el worker NO pulsa «Aceptar». Que el ternero figure «Libre» en la tabla de
 * crotales repartidos lo comprueba el worker al navegar. El motivo nunca lleva el crotal.
 *
 * <p><b>GATE de REAL (H2, pendiente de la sesión en vivo con credenciales).</b> A diferencia de la baja
 * ({@link VerificacionBaja}, que casa el REGA de pantalla con el del trámite), esta verificación NO comprueba el
 * REGA en pantalla. Hoy el aislamiento por explotación descansa en {@code PlaywrightNavegadorOvz.elegirExplotacionBovina},
 * que es fail-closed (lanza si hay 0 o >1 explotaciones bovinas y nunca presenta en una ambigua); el modo ENSAYO,
 * por defecto, nunca presenta. Riesgo residual: una cuenta con EXACTAMENTE una explotación bovina cuyo REGA ≠ al del
 * trámite se presentaría sin detectarlo. Por eso <b>el modo REAL del alta NO debe activarse</b>
 * ({@code GANERA_OVZ_WORKER_MODO=REAL}) hasta añadir aquí la verificación del REGA en pantalla como en
 * {@link VerificacionBaja} —tras confirmar en vivo si la pantalla 2 expone un campo de REGA legible—.
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
