package com.ganera.core.facturacion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.LineItem;
import com.stripe.model.checkout.Session;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Maquina de estados de Suscripcion dirigida por webhooks de Stripe
 * (Decision de diseño 4, docs/superpowers/plans/2026-07-09-prompt2.7-stripe.md) y enlace de los
 * pagos de la landing (ganera-web) con la app via {@link AltaPendiente}.
 *
 * <p>Package-private a proposito: unico punto de entrada publico es procesarEvento(Event), llamado
 * desde StripeWebhookController tras verificar la firma.
 *
 * <p><b>Desajuste de version de API (importante).</b> El webhook live esta creado con la version de
 * API 2026-09-30.endive y el SDK (stripe-java 28.2.0) usa 2024-12-18.acacia. Con ese desajuste,
 * {@code evento.getDataObjectDeserializer().getObject()} devuelve vacio y los tipos del SDK
 * (Session/Invoice/Subscription) NO se pueden deserializar desde el evento. Por eso los manejarXxx
 * extraen los campos del <b>JSON crudo</b> ({@code getDataObjectDeserializer().getRawJson()} y
 * {@code getData().getPreviousAttributes()}) con los extractores puros de mas abajo, sin depender de
 * los tipos del SDK. Las llamadas SALIENTES (listLineItems, retrieve) si usan el SDK: Stripe responde
 * en la version del SDK, asi que se deserializan bien.
 *
 * <p>Convencion test-sin-mocks-externos: los {@code procesarXxx} y los {@code extraerXxx}/
 * {@code parsearMapaPrecios}/{@code formatearDireccion} son logica pura/testable (String/Long/long,
 * nunca tipos del SDK). Los {@code manejarXxx} son la unica capa que toca el SDK (getRawJson,
 * listLineItems) -- sin cobertura unitaria directa.
 *
 * <p>Si no se puede extraer un campo imprescindible se registra ERROR (con id y tipo del evento) y se
 * lanza {@link EventoStripeNoProcesableException}, que el controller traduce en 5xx para que Stripe
 * reintente: nunca un 200 silencioso que perderia el evento.
 */
@Slf4j
@Service
class StripeWebhookService {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String SOURCE_GANERA_WEB = "ganera-web";

    private final SuscripcionService suscripcionService;
    private final SuscripcionRepository suscripcionRepository;
    private final AltaPendienteRepository altaPendienteRepository;
    private final Clock reloj;
    /** priceId -> (plan, periodo), de STRIPE_PRICE_PLAN_MAP. Solo para Payment Links (sin metadata). */
    private final Map<String, PlanPeriodo> mapaPrecios;

    StripeWebhookService(SuscripcionService suscripcionService,
                         SuscripcionRepository suscripcionRepository,
                         AltaPendienteRepository altaPendienteRepository,
                         Clock reloj,
                         // Lee la variable de entorno STRIPE_PRICE_PLAN_MAP directamente (application.yml no la
                         // mapea y tiene cambios ajenos sin commitear): si la propiedad ganera.stripe.precio-plan-map
                         // existe, se usa; si no, la variable de entorno; si no, vacio.
                         @Value("${ganera.stripe.precio-plan-map:${STRIPE_PRICE_PLAN_MAP:}}") String precioPlanMap) {
        this.suscripcionService = suscripcionService;
        this.suscripcionRepository = suscripcionRepository;
        this.altaPendienteRepository = altaPendienteRepository;
        this.reloj = reloj;
        this.mapaPrecios = parsearMapaPrecios(precioPlanMap);
    }

    // ==================== Capa SDK / glue (sin cobertura unitaria directa) ====================

    /**
     * Unico punto de entrada desde el controller. Eventos no mapeados se ignoran (200). Un fallo de
     * extraccion de un campo imprescindible lanza EventoStripeNoProcesableException (5xx).
     */
    public void procesarEvento(Event evento) {
        switch (evento.getType()) {
            case "checkout.session.completed" -> manejarCheckoutCompletado(evento);
            case "invoice.payment_succeeded" -> manejarPagoExitoso(evento);
            case "invoice.payment_failed" -> manejarPagoFallido(evento);
            case "customer.subscription.updated" -> manejarSuscripcionActualizada(evento);
            case "customer.subscription.deleted" -> manejarSuscripcionEliminada(evento);
            default -> log.debug("Evento Stripe ignorado (sin mapeo): {}", evento.getType());
        }
    }

    private void manejarCheckoutCompletado(Event evento) {
        DatosCheckout d = extraerCheckout(exigirRawJson(evento));
        if (d.sessionId() == null) {
            throw noProcesable(evento, "checkout.session.completed sin id de sesion");
        }

        if (d.clientReferenceId() != null) {
            // Camino clasico (alta creada por la propia app con client_reference_id = gestoriaId).
            Long gestoriaId;
            try {
                gestoriaId = Long.valueOf(d.clientReferenceId());
            } catch (NumberFormatException e) {
                log.warn("client_reference_id no numerico ({}), evento {}", d.clientReferenceId(), evento.getId());
                return;
            }
            procesarCheckoutCompletado(gestoriaId, d.customerId(), d.subscriptionId(), evento.getCreated());
            return;
        }

        // Alta de la landing: sin client_reference_id. Es nuestra si viene de ganera-web (metadata) o
        // si un price de la sesion esta en el mapa (Payment Links). plan/periodo: de la metadata o del mapa.
        boolean esGaneraWeb = SOURCE_GANERA_WEB.equals(d.source());
        String plan = d.planMeta();
        String periodo = d.periodoMeta();
        if (!esGaneraWeb) {
            if (mapaPrecios.isEmpty()) {
                log.debug("checkout.session.completed ajeno (sin source=ganera-web y sin mapa de precios), evento {}",
                        evento.getId());
                return;
            }
            PlanPeriodo delPrecio = resolverPorPrecio(d.sessionId(), evento);
            if (delPrecio == null) {
                log.debug("checkout.session.completed ajeno (ningun price en el mapa), evento {}", evento.getId());
                return;
            }
            plan = delPrecio.plan();
            periodo = delPrecio.periodo();
        }
        registrarAltaPendiente(d, plan, periodo);
    }

    /** SALIENTE (SDK): lee los line items de la sesion y devuelve el primer (plan, periodo) del mapa. */
    private PlanPeriodo resolverPorPrecio(String sessionId, Event evento) {
        try {
            Session session = Session.retrieve(sessionId);
            for (LineItem item : session.listLineItems().getData()) {
                if (item.getPrice() != null) {
                    PlanPeriodo pp = mapaPrecios.get(item.getPrice().getId());
                    if (pp != null) {
                        return pp;
                    }
                }
            }
            return null;
        } catch (StripeException e) {
            // Fallo transitorio al consultar Stripe: 5xx y que reintente, no clasificar a ciegas.
            throw noProcesable(evento, "no se han podido leer los line items de la sesion " + sessionId);
        }
    }

    private void manejarPagoExitoso(Event evento) {
        String subscriptionId = extraerSubscriptionIdDeInvoice(exigirRawJson(evento));
        if (subscriptionId == null) {
            throw noProcesable(evento, "invoice.payment_succeeded sin subscription");
        }
        procesarPagoExitoso(subscriptionId, evento.getCreated());
    }

    private void manejarPagoFallido(Event evento) {
        String subscriptionId = extraerSubscriptionIdDeInvoice(exigirRawJson(evento));
        if (subscriptionId == null) {
            throw noProcesable(evento, "invoice.payment_failed sin subscription");
        }
        procesarPagoFallido(subscriptionId, evento.getCreated());
    }

    private void manejarSuscripcionActualizada(Event evento) {
        DatosSuscripcion d = extraerSuscripcion(exigirRawJson(evento));
        if (d.id() == null) {
            throw noProcesable(evento, "customer.subscription.updated sin id");
        }
        procesarSuscripcionActualizada(d.id(), d.status(), extraerPreviousStatus(evento), evento.getCreated());
    }

    private void manejarSuscripcionEliminada(Event evento) {
        DatosSuscripcion d = extraerSuscripcion(exigirRawJson(evento));
        if (d.id() == null) {
            throw noProcesable(evento, "customer.subscription.deleted sin id");
        }
        // Siempre CANCELADA -- no pasa por calcularEstadoTrasActualizacion (ese mapa es para updated).
        aplicarEstadoSiNoObsoleto(d.id(), evento.getCreated(), EstadoSuscripcion.CANCELADA);
    }

    private String exigirRawJson(Event evento) {
        String raw = evento.getDataObjectDeserializer().getRawJson();
        if (raw == null || raw.isBlank()) {
            throw noProcesable(evento, "no se pudo obtener el JSON crudo del objeto del evento");
        }
        return raw;
    }

    private EventoStripeNoProcesableException noProcesable(Event evento, String motivo) {
        log.error("Evento Stripe no procesable (id={}, tipo={}): {}", evento.getId(), evento.getType(), motivo);
        return new EventoStripeNoProcesableException(motivo);
    }

    private String extraerPreviousStatus(Event evento) {
        Map<String, Object> previousAttributes = evento.getData().getPreviousAttributes();
        if (previousAttributes == null) {
            return null;
        }
        Object status = previousAttributes.get("status");
        return status == null ? null : status.toString();
    }

    // ==================== Extractores puros del JSON crudo (testables con fixtures endive) ====================

    /** priceId -> (plan, periodo). Formato "priceId=plan:periodo,priceId2=plan:periodo". Vacio -> mapa vacio. */
    static Map<String, PlanPeriodo> parsearMapaPrecios(String valor) {
        Map<String, PlanPeriodo> mapa = new LinkedHashMap<>();
        if (valor == null || valor.isBlank()) {
            return Map.copyOf(mapa);
        }
        for (String entrada : valor.split(",")) {
            String limpia = entrada.trim();
            if (limpia.isEmpty()) {
                continue;
            }
            int igual = limpia.indexOf('=');
            int dosPuntos = limpia.indexOf(':', igual + 1);
            if (igual <= 0 || dosPuntos < 0) {
                throw new IllegalArgumentException(
                        "STRIPE_PRICE_PLAN_MAP mal formado; se esperaba priceId=plan:periodo, y llego: " + limpia);
            }
            String priceId = limpia.substring(0, igual).trim();
            String plan = limpia.substring(igual + 1, dosPuntos).trim();
            String periodo = limpia.substring(dosPuntos + 1).trim();
            if (priceId.isEmpty() || plan.isEmpty() || periodo.isEmpty()) {
                throw new IllegalArgumentException(
                        "STRIPE_PRICE_PLAN_MAP mal formado; priceId/plan/periodo no pueden ir vacios: " + limpia);
            }
            mapa.put(priceId, new PlanPeriodo(plan, periodo));
        }
        return Map.copyOf(mapa);
    }

    static DatosCheckout extraerCheckout(String rawJson) {
        JsonNode n = leer(rawJson);
        JsonNode metadata = n.path("metadata");
        JsonNode detalles = n.path("customer_details");
        Boolean prueba = null;
        JsonNode pruebaNodo = metadata.get("prueba");
        if (pruebaNodo != null && !pruebaNodo.isNull()) {
            prueba = Boolean.parseBoolean(pruebaNodo.asText());
        }
        return new DatosCheckout(
                texto(n, "id"),
                texto(n, "client_reference_id"),
                texto(n, "customer"),
                texto(n, "subscription"),
                texto(metadata, "source"),
                texto(metadata, "plan"),
                texto(metadata, "periodo"),
                prueba,
                texto(detalles, "email"),
                texto(detalles, "name"),
                extraerPrimerTaxId(detalles),
                formatearDireccion(detalles.path("address")));
    }

    /**
     * Id de la subscription de un invoice. En la API vieja esta en {@code subscription}; en la nueva
     * (endive) ese campo desaparece y esta en {@code parent.subscription_details.subscription}. Se
     * soportan las dos; null si ninguna existe (el manejarXxx lo convierte en 5xx).
     */
    static String extraerSubscriptionIdDeInvoice(String rawJson) {
        JsonNode n = leer(rawJson);
        String directo = texto(n, "subscription");
        if (directo != null) {
            return directo;
        }
        return texto(n.path("parent").path("subscription_details"), "subscription");
    }

    static DatosSuscripcion extraerSuscripcion(String rawJson) {
        JsonNode n = leer(rawJson);
        return new DatosSuscripcion(texto(n, "id"), texto(n, "status"));
    }

    private static String extraerPrimerTaxId(JsonNode customerDetails) {
        JsonNode taxIds = customerDetails.path("tax_ids");
        if (taxIds.isArray() && !taxIds.isEmpty()) {
            return texto(taxIds.get(0), "value");
        }
        return null;
    }

    /** Direccion de facturacion de customer_details.address en una sola linea; null si no hay nada. */
    static String formatearDireccion(JsonNode address) {
        if (address == null || address.isMissingNode() || address.isNull()) {
            return null;
        }
        List<String> partes = new ArrayList<>();
        for (String campo : new String[] {"line1", "line2", "postal_code", "city", "state", "country"}) {
            String valor = texto(address, campo);
            if (valor != null && !valor.isBlank()) {
                partes.add(valor.trim());
            }
        }
        return partes.isEmpty() ? null : String.join(", ", partes);
    }

    private static JsonNode leer(String rawJson) {
        try {
            return JSON.readTree(rawJson);
        } catch (Exception e) {
            // Sin encadenar la causa: el JSON puede llevar datos del cliente.
            throw new EventoStripeNoProcesableException("El JSON del objeto del evento de Stripe no es valido");
        }
    }

    private static String texto(JsonNode nodo, String campo) {
        JsonNode valor = nodo.get(campo);
        return valor == null || valor.isNull() ? null : valor.asText();
    }

    /** El mapa de precios ya parseado (para tests de que STRIPE_PRICE_PLAN_MAP llega al bean). */
    Map<String, PlanPeriodo> mapaPrecios() {
        return mapaPrecios;
    }

    record PlanPeriodo(String plan, String periodo) {
    }

    record DatosCheckout(String sessionId, String clientReferenceId, String customerId, String subscriptionId,
                         String source, String planMeta, String periodoMeta, Boolean prueba,
                         String email, String nombre, String nif, String direccion) {
    }

    record DatosSuscripcion(String id, String status) {
    }

    // ==================== Logica pura/testable (sin tipos del SDK) ====================

    /** Guarda una fila alta_pendiente si no existe ya (idempotente por stripe_session_id). WARN con ids. */
    void registrarAltaPendiente(DatosCheckout d, String plan, String periodo) {
        if (altaPendienteRepository.findByStripeSessionId(d.sessionId()).isPresent()) {
            log.info("alta_pendiente ya registrada para la sesion {}, se ignora (re-entrega de Stripe)", d.sessionId());
            return;
        }
        AltaPendiente alta = new AltaPendiente();
        alta.setStripeSessionId(d.sessionId());
        alta.setCustomerId(d.customerId());
        alta.setSubscriptionId(d.subscriptionId());
        alta.setEmail(d.email());
        alta.setNombre(d.nombre());
        alta.setNif(d.nif());
        alta.setDireccionFacturacion(d.direccion());
        alta.setPlan(plan);
        alta.setPeriodo(periodo);
        alta.setPrueba(d.prueba());
        alta.setEstado(EstadoAltaPendiente.PENDIENTE);
        alta.setCreadoEn(reloj.instant().truncatedTo(ChronoUnit.MICROS));
        altaPendienteRepository.save(alta);
        // WARN con SOLO ids para que Antonio/Adolfo la vean en los logs de Clever y la enlacen a mano.
        // Nunca email/nombre/NIF/direccion (datos personales): van a la BD, no al log.
        log.warn("Nueva alta_pendiente de la landing: sessionId={}, customerId={}, subscriptionId={}, plan={}, "
                        + "periodo={}. Enlazar con POST /internal/onboarding/gestoria pasando stripeSubscriptionId.",
                d.sessionId(), d.customerId(), d.subscriptionId(), plan, periodo);
    }

    void procesarCheckoutCompletado(Long gestoriaId, String customerId, String subscriptionId, long eventCreated) {
        Suscripcion suscripcion = suscripcionService.obtenerOCrearSuscripcion(gestoriaId);
        if (esEventoObsoleto(eventCreated, suscripcion)) {
            log.info("checkout.session.completed obsoleto para gestoria {}, se descarta", gestoriaId);
            return;
        }
        suscripcion.setStripeCustomerId(customerId);
        suscripcion.setStripeSubscriptionId(subscriptionId);
        suscripcion.setEstado(EstadoSuscripcion.TRIAL);
        suscripcion.setStripeUltimoEventoEpoch(eventCreated);
        suscripcionRepository.save(suscripcion);
    }

    void procesarPagoExitoso(String subscriptionId, long eventCreated) {
        aplicarEstadoSiNoObsoleto(subscriptionId, eventCreated, EstadoSuscripcion.ACTIVA);
    }

    void procesarPagoFallido(String subscriptionId, long eventCreated) {
        Optional<Suscripcion> encontrada = suscripcionRepository.findByStripeSubscriptionId(subscriptionId);
        if (encontrada.isEmpty()) {
            log.info("invoice.payment_failed para subscription desconocida {}, se ignora", subscriptionId);
            return;
        }
        Suscripcion suscripcion = encontrada.get();
        if (esEventoObsoleto(eventCreated, suscripcion)) {
            log.info("invoice.payment_failed obsoleto para subscription {}, se descarta", subscriptionId);
            return;
        }
        // Bug 1 (CLAUDE.md > Stripe): TRIAL o TRIAL_EXPIRADO_SIN_PAGO -> TRIAL_EXPIRADO_SIN_PAGO. Un
        // segundo reintento fallido tras una prueba impagada NO debe pasar a IMPAGO_GRACIA (que
        // RE-habilitaria puedeAprobarTramites). Cualquier otro estado -> IMPAGO_GRACIA.
        EstadoSuscripcion nuevoEstado =
                (suscripcion.getEstado() == EstadoSuscripcion.TRIAL
                        || suscripcion.getEstado() == EstadoSuscripcion.TRIAL_EXPIRADO_SIN_PAGO)
                        ? EstadoSuscripcion.TRIAL_EXPIRADO_SIN_PAGO
                        : EstadoSuscripcion.IMPAGO_GRACIA;
        suscripcion.setEstado(nuevoEstado);
        suscripcion.setStripeUltimoEventoEpoch(eventCreated);
        suscripcionRepository.save(suscripcion);
    }

    void procesarSuscripcionActualizada(String subscriptionId, String status, String previousStatus, long eventCreated) {
        Optional<Suscripcion> encontrada = suscripcionRepository.findByStripeSubscriptionId(subscriptionId);
        if (encontrada.isEmpty()) {
            log.info("customer.subscription.updated para subscription desconocida {}, se ignora", subscriptionId);
            return;
        }
        Suscripcion suscripcion = encontrada.get();
        if (esEventoObsoleto(eventCreated, suscripcion)) {
            log.info("customer.subscription.updated obsoleto para subscription {}, se descarta", subscriptionId);
            return;
        }
        EstadoSuscripcion nuevoEstado = calcularEstadoTrasActualizacion(status, previousStatus, suscripcion.getEstado());
        if (nuevoEstado == null) {
            return;
        }
        suscripcion.setEstado(nuevoEstado);
        suscripcion.setStripeUltimoEventoEpoch(eventCreated);
        suscripcionRepository.save(suscripcion);
    }

    private void aplicarEstadoSiNoObsoleto(String subscriptionId, long eventCreated, EstadoSuscripcion nuevoEstado) {
        Optional<Suscripcion> encontrada = suscripcionRepository.findByStripeSubscriptionId(subscriptionId);
        if (encontrada.isEmpty()) {
            log.info("Evento Stripe para subscription desconocida {}, se ignora", subscriptionId);
            return;
        }
        Suscripcion suscripcion = encontrada.get();
        if (esEventoObsoleto(eventCreated, suscripcion)) {
            log.info("Evento Stripe obsoleto para subscription {}, se descarta", subscriptionId);
            return;
        }
        suscripcion.setEstado(nuevoEstado);
        suscripcion.setStripeUltimoEventoEpoch(eventCreated);
        suscripcionRepository.save(suscripcion);
    }

    /**
     * Mapa de customer.subscription.updated -> EstadoSuscripcion (Decision de diseño 4).
     * null significa "no cambiar nada" (status desconocido/no mapeado).
     */
    static EstadoSuscripcion calcularEstadoTrasActualizacion(String status, String previousStatus,
                                                              EstadoSuscripcion estadoActual) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case "active" -> EstadoSuscripcion.ACTIVA;
            case "past_due" -> {
                if (previousStatus != null) {
                    yield "trialing".equals(previousStatus)
                            ? EstadoSuscripcion.TRIAL_EXPIRADO_SIN_PAGO
                            : EstadoSuscripcion.IMPAGO_GRACIA;
                }
                yield estadoActual == EstadoSuscripcion.TRIAL
                        ? EstadoSuscripcion.TRIAL_EXPIRADO_SIN_PAGO
                        : EstadoSuscripcion.IMPAGO_GRACIA;
            }
            case "unpaid" -> EstadoSuscripcion.SUSPENDIDA;
            case "canceled" -> EstadoSuscripcion.CANCELADA;
            case "trialing" -> EstadoSuscripcion.TRIAL;
            default -> null;
        };
    }

    /**
     * Guard monotonico contra re-entrega tardia / desorden de webhooks (Decision de diseño 4):
     * eventCreated estrictamente menor que lo ya aplicado -> obsoleto. Sin stripeUltimoEventoEpoch
     * almacenado (primer evento) nunca es obsoleto.
     */
    static boolean esEventoObsoleto(Long eventCreated, Suscripcion suscripcion) {
        Long ultimoEpoch = suscripcion.getStripeUltimoEventoEpoch();
        if (ultimoEpoch == null) {
            return false;
        }
        return eventCreated < ultimoEpoch;
    }
}
