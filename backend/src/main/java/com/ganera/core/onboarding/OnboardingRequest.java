package com.ganera.core.onboarding;

/**
 * Alta manual de una Gestoria piloto. {@code stripeSubscriptionId} es OPCIONAL: si viene, la
 * Suscripcion de la nueva Gestoria se enlaza con esa Subscription de Stripe (alta que vino de la
 * landing; ver CLAUDE.md > Stripe). Si no viene, la Gestoria se crea en ACTIVA sin Stripe (piloto
 * directo, como hasta ahora).
 */
public record OnboardingRequest(String nombreGestoria, String emailUsuario, String passwordUsuario,
                                String nombreUsuario, String stripeSubscriptionId) {

    /** Compatibilidad: alta sin enlace a Stripe (la forma que ya usaban las llamadas existentes). */
    public OnboardingRequest(String nombreGestoria, String emailUsuario, String passwordUsuario, String nombreUsuario) {
        this(nombreGestoria, emailUsuario, passwordUsuario, nombreUsuario, null);
    }
}
