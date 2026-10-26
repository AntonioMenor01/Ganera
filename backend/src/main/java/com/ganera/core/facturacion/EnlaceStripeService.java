package com.ganera.core.facturacion;

import com.ganera.core.facturacion.StripeWebhookService.PlanPeriodo;
import com.stripe.exception.StripeException;
import com.stripe.model.Subscription;
import com.stripe.model.SubscriptionItem;
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
 * <p><b>plan nunca queda null al enlazar:</b> todo lo enlazado por {@code stripeSubscriptionId} es de
 * plan fijo (precio por plan, no por explotaciones). plan se resuelve en orden: alta_pendiente →
 * metadata de la Subscription → mapa de precios (STRIPE_PRICE_PLAN_MAP) por el price de la Subscription →
 * {@code "desconocido"}. Si fuese null, {@link SuscripcionSyncScheduler} reconciliaria la quantity y
 * multiplicaria el cobro; con un plan no nulo queda SIEMPRE excluida de esa reconciliacion.
 *
 * <p>Convencion test-sin-mocks-externos: {@link #estadoInicialDesdeStatus}, {@link #resolverPlan} y
 * {@link #aplicar} son logica pura/testable; {@link #recuperarSubscription} es la unica capa que toca el
 * SDK (Subscription.retrieve, SALIENTE: Stripe responde en la version del SDK, se deserializa bien pese
 * al desajuste de version del webhook).
 */
@Slf4j
@Service
public class EnlaceStripeService {

    private static final String PLAN_DESCONOCIDO = "desconocido";

    private final AltaPendienteRepository altaPendienteRepository;
    private final String apiKey;
    private final Map<String, PlanPeriodo> mapaPrecios;

    public EnlaceStripeService(AltaPendienteRepository altaPendienteRepository,
                               @Value("${stripe.api-key:}") String apiKey,
                               @Value("${ganera.stripe.precio-plan-map:${STRIPE_PRICE_PLAN_MAP:}}") String precioPlanMap) {
        this.altaPendienteRepository = altaPendienteRepository;
        this.apiKey = apiKey == null ? "" : apiKey;
        this.mapaPrecios = StripeWebhookService.parsearMapaPrecios(precioPlanMap);
    }

    /** Stripe configurado (hay clave). Si no, el onboarding con stripeSubscriptionId responde 503. */
    public boolean stripeConfigurado() {
        return !apiKey.isBlank();
    }

    /** Recupera la Subscription de Stripe (SALIENTE) y aplica sus datos a la Suscripcion. */
    public void enlazar(Suscripcion suscripcion, String stripeSubscriptionId) throws StripeException {
        aplicar(suscripcion, stripeSubscriptionId, recuperarSubscription(stripeSubscriptionId));
    }

    /**
     * Aplica a {@code suscripcion} los datos ya recuperados: customer, plan/periodo y estado, y marca la
     * alta_pendiente ENLAZADA. No toca Stripe ni guarda la Suscripcion (de eso se encarga quien llama,
     * dentro de su transaccion). Package-private para testarlo sin el SDK.
     */
    void aplicar(Suscripcion suscripcion, String stripeSubscriptionId, DatosSubscription datos) {
        Optional<AltaPendiente> alta = altaPendienteRepository.findBySubscriptionId(stripeSubscriptionId);
        String planConocido = alta.map(AltaPendiente::getPlan).orElse(datos.plan());
        String periodo = alta.map(AltaPendiente::getPeriodo).orElse(datos.periodo());

        String plan = resolverPlan(planConocido, datos.priceId(), mapaPrecios);
        if (periodo == null && planConocido == null) {
            PlanPeriodo delPrecio = datos.priceId() == null ? null : mapaPrecios.get(datos.priceId());
            if (delPrecio != null) {
                periodo = delPrecio.periodo();
            }
        }

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

    /**
     * plan de una suscripcion de plan fijo, NUNCA null: el conocido (alta_pendiente o metadata) si lo hay,
     * si no el del mapa de precios por el price de la Subscription, y si tampoco {@code "desconocido"}.
     */
    static String resolverPlan(String planConocido, String priceId, Map<String, PlanPeriodo> mapaPrecios) {
        if (planConocido != null) {
            return planConocido;
        }
        if (priceId != null) {
            PlanPeriodo delPrecio = mapaPrecios.get(priceId);
            if (delPrecio != null) {
                return delPrecio.plan();
            }
        }
        return PLAN_DESCONOCIDO;
    }

    /** SALIENTE (SDK): recupera la Subscription. Sin cobertura unitaria directa. */
    DatosSubscription recuperarSubscription(String stripeSubscriptionId) throws StripeException {
        Subscription subscription = Subscription.retrieve(stripeSubscriptionId);
        Map<String, String> metadata = subscription.getMetadata();
        String plan = metadata == null ? null : metadata.get("plan");
        String periodo = metadata == null ? null : metadata.get("periodo");
        return new DatosSubscription(subscription.getStatus(), subscription.getCustomer(), plan, periodo,
                primerPriceId(subscription));
    }

    private static String primerPriceId(Subscription subscription) {
        if (subscription.getItems() == null || subscription.getItems().getData() == null) {
            return null;
        }
        for (SubscriptionItem item : subscription.getItems().getData()) {
            if (item.getPrice() != null && item.getPrice().getId() != null) {
                return item.getPrice().getId();
            }
        }
        return null;
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

    record DatosSubscription(String status, String customerId, String plan, String periodo, String priceId) {
    }
}
