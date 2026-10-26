package com.ganera.core.facturacion;

/** Estado de una fila de {@link AltaPendiente}. */
public enum EstadoAltaPendiente {
    /** El pago llego de la landing pero todavia no se ha enlazado a ninguna Gestoria. */
    PENDIENTE,
    /** Ya se enlazo (via POST /internal/onboarding/gestoria con stripeSubscriptionId). */
    ENLAZADA
}
