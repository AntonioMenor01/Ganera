package com.ganera.core.facturacion;

import com.ganera.core.explotacion.ExplotacionRepository;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Crea Stripe Checkout Sessions (mode SUBSCRIPTION) para que una Gestoria real
 * (no piloto) contrate el plan, con 15 dias de trial y quantity = numero de
 * Explotaciones activas de esa Gestoria.
 */
@Service
public class StripeCheckoutService {

    private static final long TRIAL_PERIOD_DIAS = 15L;

    private final SuscripcionService suscripcionService;
    private final SuscripcionRepository suscripcionRepository;
    private final ExplotacionRepository explotacionRepository;
    private final String apiKey;
    private final String priceIdExplotacion;
    private final String successUrl;
    private final String cancelUrl;

    public StripeCheckoutService(SuscripcionService suscripcionService,
                                  SuscripcionRepository suscripcionRepository,
                                  ExplotacionRepository explotacionRepository,
                                  @Value("${stripe.api-key:}") String apiKey,
                                  @Value("${stripe.price-id-explotacion:}") String priceIdExplotacion,
                                  @Value("${stripe.checkout.success-url}") String successUrl,
                                  @Value("${stripe.checkout.cancel-url}") String cancelUrl) {
        this.suscripcionService = suscripcionService;
        this.suscripcionRepository = suscripcionRepository;
        this.explotacionRepository = explotacionRepository;
        this.apiKey = apiKey;
        this.priceIdExplotacion = priceIdExplotacion;
        this.successUrl = successUrl;
        this.cancelUrl = cancelUrl;
    }

    /**
     * Crea la Checkout Session para la Gestoria y devuelve su URL, o
     * Optional.empty() si Stripe no esta configurado todavia (clave o price id
     * en blanco) -- el caller (FacturacionController) lo traduce a 503.
     *
     * Deliberadamente sin @Transactional: obtenerOCrearSuscripcion recupera de
     * una carrera de creacion via saveAndFlush + catch de
     * DataIntegrityViolationException dentro de su propia transaccion; envolver
     * esta llamada en una transaccion nueva podria marcarla rollback-only en ese
     * flush interno y convertir una recuperacion limpia en un
     * UnexpectedRollbackException.
     */
    public Optional<String> crearSesionCheckout(Long gestoriaId) throws StripeException {
        if (!configuracionCompleta()) {
            return Optional.empty();
        }
        long cantidad = cantidadAContratar(explotacionRepository.countByGestoriaId(gestoriaId));
        return crearSesionConCantidad(gestoriaId, cantidad);
    }

    /**
     * Variante para el alta publica (POST /gestorias/registro): en ese momento la Gestoria todavia
     * no tiene ninguna Explotacion real (se registra antes de importar su inventario), asi que la
     * quantity es una ESTIMACION derivada del rango de clientes elegido (ver RangoClientes), no un
     * conteo real. Esta estimacion no se autocorrige hasta que la suscripcion llegue a
     * ACTIVA/IMPAGO_GRACIA -- ver la limitacion conocida de SuscripcionSyncScheduler en CLAUDE.md.
     */
    public Optional<String> crearSesionCheckoutConCantidadEstimada(Long gestoriaId, long cantidadEstimada)
            throws StripeException {
        if (!configuracionCompleta()) {
            return Optional.empty();
        }
        return crearSesionConCantidad(gestoriaId, cantidadEstimada);
    }

    private Optional<String> crearSesionConCantidad(Long gestoriaId, long cantidad) throws StripeException {
        Suscripcion suscripcion = suscripcionService.obtenerOCrearSuscripcion(gestoriaId);
        if (suscripcion.getExplotacionesContratadas() == null) {
            suscripcion.setExplotacionesContratadas((int) cantidad);
            suscripcionRepository.save(suscripcion);
        }

        SessionCreateParams params = construirParametros(gestoriaId, cantidad);
        Session session = crearSesionEnStripe(params);
        return Optional.of(session.getUrl());
    }

    boolean configuracionCompleta() {
        return !apiKey.isBlank() && !priceIdExplotacion.isBlank();
    }

    static long cantidadAContratar(long explotacionesActivas) {
        return Math.max(1L, explotacionesActivas);
    }

    SessionCreateParams construirParametros(Long gestoriaId, long cantidad) {
        return SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                .setClientReferenceId(String.valueOf(gestoriaId))
                .setSuccessUrl(successUrl)
                .setCancelUrl(cancelUrl)
                .addLineItem(SessionCreateParams.LineItem.builder()
                        .setPrice(priceIdExplotacion)
                        .setQuantity(cantidad)
                        .build())
                .setSubscriptionData(SessionCreateParams.SubscriptionData.builder()
                        .setTrialPeriodDays(TRIAL_PERIOD_DIAS)
                        .build())
                .build();
    }

    // Unica linea que toca la red -- sin cobertura unitaria directa (convencion
    // test-sin-mocks-externos); se valida en el smoke test manual con clave test.
    Session crearSesionEnStripe(SessionCreateParams params) throws StripeException {
        return Session.create(params);
    }
}
