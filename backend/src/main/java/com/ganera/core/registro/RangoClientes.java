package com.ganera.core.registro;

/**
 * Rango estimado de clientes (Ganaderos) que una Gestoria elige al registrarse. Stripe sigue
 * cobrando por Explotaciones, no por clientes -- una Gestoria sabe de memoria cuantos clientes
 * tiene, no cuantas Explotaciones suman todos juntos, asi que el formulario de registro pregunta
 * por clientes y este enum traduce ese rango a una quantity ESTIMADA de Explotaciones para el
 * Checkout Session inicial. El ratio de partida (1,3 explotaciones/cliente, un ganadero normal
 * tiene 1 explotacion, algunos tienen mas) se aplica al MINIMO del rango, nunca al punto medio,
 * para no sobre-cobrar antes de ver el inventario real -- ver la limitacion conocida en CLAUDE.md
 * sobre SuscripcionSyncScheduler no reconciliando suscripciones en TRIAL.
 */
public enum RangoClientes {
    UNO_A_DIEZ(1),
    ONCE_A_TREINTA(11),
    TREINTA_UNO_A_SETENTA_Y_CINCO(31),
    SETENTA_Y_SEIS_O_MAS(76);

    private static final double RATIO_EXPLOTACIONES_POR_CLIENTE = 1.3;

    private final int clientesMinimoDelRango;

    RangoClientes(int clientesMinimoDelRango) {
        this.clientesMinimoDelRango = clientesMinimoDelRango;
    }

    public long quantityExplotacionesEstimada() {
        return (long) Math.ceil(clientesMinimoDelRango * RATIO_EXPLOTACIONES_POR_CLIENTE);
    }
}
