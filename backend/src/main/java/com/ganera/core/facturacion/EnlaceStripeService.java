package com.ganera.core.facturacion;

import com.stripe.exception.StripeException;
import com.stripe.model.Subscription;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

/**
 * Enlaza una {@link Suscripcion} recien creada con una Subscription de Stripe que vino de la landing
 * (alta semi-manual del piloto, ver CLAUDE.md > Stripe). Lo usa POST /internal/onboarding/gestoria
 * cuando llega un {@code stripeSubscriptionId}.
 *
 * <p>Recupera la Subscription de Stripe y fija el estado a partir de su {@code status} actual: asi el
 * resultado NO depende del orden en que llegaron los webhooks (un invoice.payment_succeeded que llego
 * antes del enlace no se pierde, el estado se recalcula aqui desde Stripe). Marca la {@link AltaPendiente}
 * correspondiente como {@link EstadoAltaPendiente#ENLAZADA}.
 *
 * <p>Convencion test-sin-mocks-externos: {@link #estadoInicialDesdeStatus} es logica pura/testable;
 * {@link #recuperarSubscription} es la unica capa que toca el SDK (Subscription.retrieve, SALIENTE:
 * Stripe responde en la version del SDK, se deserializa bien pese al desajuste de version del webhook).
 */
@Slf4j
@Service
public class EnlaceStripeService {

    private final AltaPendienteRepository altaPendienteRepository;
    private final String apiKey;

    public EnlaceStripeService(AltaPendienteRepository altaPendienteRepository,
                               @Value("${stripe.api-key:}") String apiKey) {
        this.altaPendienteRepository = altaPendienteRepository;
        this.apiKey = apiKey == null ? "" : apiKey;
    }

    /** Stripe configurado (hay clave). Si no, el onboarding con stripeSubscriptionId responde 503. */
    public boolean stripeConfigurado() {
        return !apiKey.isBlank();
    }

    /**
     * Aplica a {@code suscripcion} los datos de la Subscription de Stripe (customer, plan/periodo,
     * estado) y marca la alta_pendiente ENLAZADA. plan/periodo: de la alta_pendiente si existe (se
     * capturaron en el checkout), si no, de la metadata de la Subscription. No guarda la Suscripcion:
     * de eso se encarga quien llama, dentro de su transaccion.
     */
    public void enlazar(Suscripcion suscripcion, String stripeSubscriptionId) throws StripeException {
        DatosSubscription datos = recuperarSubscription(stripeSubscriptionId);
        Optional<AltaPendiente> alta = altaPendienteRepository.findBySubscriptionId(stripeSubscriptionId);
        String plan = alta.map(AltaPendiente::getPlan).orElse(datos.plan());
        String periodo = alta.map(AltaPendiente::getPeriodo).orElse(datos.periodo());

        suscripcion.setStripeSubscriptionId(stripeSubscriptionId);
        suscripcion.setStripeCustomerId(datos.customerId());
        suscripcion.setPlan(plan);
        suscripcion.setPeriodo(periodo);
        suscripcion.setEstado(estadoInicialDesdeStatus(datos.status()));

        alta.ifPresent(a -> {
            a.setEstado(EstadoAltaPendiente.ENLAZADA);
            altaPendienteRepository.save(a);
        });
        log.info("Suscripcion enlazada con Stripe: subscriptionId={}, estado={}, plan={}, altaPendiente={}",
                stripeSubscriptionId, suscripcion.getEstado(), plan, alta.isPresent() ? "ENLAZADA" : "no habia");
    }

    /** SALIENTE (SDK): recupera la Subscription. Sin cobertura unitaria directa. */
    DatosSubscription recuperarSubscription(String stripeSubscriptionId) throws StripeException {
        Subscription subscription = Subscription.retrieve(stripeSubscriptionId);
        Map<String, String> metadata = subscription.getMetadata();
        String plan = metadata == null ? null : metadata.get("plan");
        String periodo = metadata == null ? null : metadata.get("periodo");
        return new DatosSubscription(subscription.getStatus(), subscription.getCustomer(), plan, periodo);
    }

    /** status de Stripe -> estado inicial de la Suscripcion. Lanza si el status no se reconoce. */
    static EstadoSuscripcion estadoInicialDesdeStatus(String status) {
        if (status == null) {
            throw new IllegalStateException("La Subscription de Stripe no trae status");
        }
        return switch (status) {
            case "trialing" -> EstadoSuscripcion.TRIAL;
            case "active" -> EstadoSuscripcion.ACTIVA;
            case "past_due" -> EstadoSuscripcion.IMPAGO_GRACIA;
            case "unpaid" -> EstadoSuscripcion.SUSPENDIDA;
            case "canceled" -> EstadoSuscripcion.CANCELADA;
            default -> throw new IllegalStateException("status de Subscription de Stripe no soportado: " + status);
        };
    }

    record DatosSubscription(String status, String customerId, String plan, String periodo) {
    }
}
