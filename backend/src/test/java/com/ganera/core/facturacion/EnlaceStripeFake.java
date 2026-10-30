package com.ganera.core.facturacion;

/**
 * Doble de {@link EnlaceStripeService} para tests (convencion test-sin-mocks-externos): no toca el SDK de
 * Stripe. Sobrescribe la unica capa SALIENTE ({@link EnlaceStripeService#recuperarSubscription}) para
 * devolver un {@code status}/plan/periodo elegidos, de modo que {@code enlazar} aplique a la Suscripcion el
 * estado real que saldria de Stripe (p. ej. {@code past_due} -> IMPAGO_GRACIA) sin ninguna llamada de red.
 *
 * <p>Vive en el paquete {@code facturacion} porque {@code recuperarSubscription} y el record
 * {@code DatosSubscription} son package-private. Se construye con una clave no vacia para que
 * {@code stripeConfigurado()} sea {@code true} (el onboarding con {@code stripeSubscriptionId} lo exige).
 */
public class EnlaceStripeFake extends EnlaceStripeService {

    private final String status;
    private final String plan;
    private final String periodo;

    public EnlaceStripeFake(AltaPendienteRepository altaPendienteRepository, String status, String plan, String periodo) {
        super(altaPendienteRepository, "sk_test_fake", "");
        this.status = status;
        this.plan = plan;
        this.periodo = periodo;
    }

    @Override
    DatosSubscription recuperarSubscription(String stripeSubscriptionId) {
        return new DatosSubscription(status, "cus_fake", plan, periodo, null);
    }
}
