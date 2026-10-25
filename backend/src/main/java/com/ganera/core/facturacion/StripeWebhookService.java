package com.ganera.core.facturacion;

import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.Invoice;
import com.stripe.model.StripeObject;
import com.stripe.model.Subscription;
import com.stripe.model.checkout.Session;
import com.ganera.core.shared.auditoria.AccionAuditoria;
import com.ganera.core.shared.auditoria.Actor;
import com.ganera.core.shared.auditoria.AuditoriaService;
import com.ganera.core.shared.auditoria.Clave;
import com.ganera.core.shared.auditoria.DatosAuditoria;
import com.ganera.core.shared.auditoria.EntidadAuditada;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

/**
 * Maquina de estados de Suscripcion dirigida por webhooks de Stripe
 * (Decision de diseño 4, docs/superpowers/plans/2026-07-09-prompt2.7-stripe.md).
 *
 * Package-private a proposito: unico punto de entrada publico es procesarEvento(Event),
 * llamado desde StripeWebhookController tras verificar la firma.
 *
 * Convencion test-sin-mocks-externos: los procesarXxx de abajo son la logica de
 * negocio pura/testable (reciben String/Long/long, nunca tipos del SDK de Stripe).
 * Los manejarXxx son la unica capa que toca tipos del SDK (deserializacion via
 * EventDataObjectDeserializer, extraccion de client_reference_id y de
 * previous_attributes.status) -- sin cobertura unitaria directa, se validan en
 * el smoke test end-to-end (Task 4).
 */
@Slf4j
@Service
class StripeWebhookService {

    private final SuscripcionService suscripcionService;
    private final SuscripcionRepository suscripcionRepository;
    private final AuditoriaService auditoriaService;

    StripeWebhookService(SuscripcionService suscripcionService, SuscripcionRepository suscripcionRepository,
                         AuditoriaService auditoriaService) {
        this.suscripcionService = suscripcionService;
        this.suscripcionRepository = suscripcionRepository;
        this.auditoriaService = auditoriaService;
    }

    // ==================== Capa SDK (sin cobertura unitaria directa) ====================

    /**
     * Unico punto de entrada desde el controller. Eventos no mapeados se ignoran
     * (200 igualmente -- decidido a nivel de controller, no reintentar redelivery
     * de eventos que no nos interesan).
     *
     * Sin dedupe por event.id (limitacion conocida y aceptada, Decision de diseño 4):
     * la mitigacion de re-entrega/desorden es el guard monotonico por event.created
     * (ver esEventoObsoleto), no una tabla de eventos ya vistos.
     */
    @Transactional
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
        StripeObject stripeObject = deserializar(evento);
        if (!(stripeObject instanceof Session session)) {
            return;
        }
        String clientReferenceId = session.getClientReferenceId();
        if (clientReferenceId == null) {
            log.warn("checkout.session.completed sin client_reference_id, evento {}", evento.getId());
            return;
        }
        Long gestoriaId;
        try {
            gestoriaId = Long.valueOf(clientReferenceId);
        } catch (NumberFormatException e) {
            log.warn("client_reference_id no numerico ({}), evento {}", clientReferenceId, evento.getId());
            return;
        }
        procesarCheckoutCompletado(gestoriaId, session.getCustomer(), session.getSubscription(), evento.getCreated());
    }

    private void manejarPagoExitoso(Event evento) {
        StripeObject stripeObject = deserializar(evento);
        if (!(stripeObject instanceof Invoice invoice)) {
            return;
        }
        procesarPagoExitoso(invoice.getSubscription(), evento.getCreated());
    }

    private void manejarPagoFallido(Event evento) {
        StripeObject stripeObject = deserializar(evento);
        if (!(stripeObject instanceof Invoice invoice)) {
            return;
        }
        procesarPagoFallido(invoice.getSubscription(), evento.getCreated());
    }

    private void manejarSuscripcionActualizada(Event evento) {
        StripeObject stripeObject = deserializar(evento);
        if (!(stripeObject instanceof Subscription subscription)) {
            return;
        }
        procesarSuscripcionActualizada(subscription.getId(), subscription.getStatus(),
                extraerPreviousStatus(evento), evento.getCreated());
    }

    private void manejarSuscripcionEliminada(Event evento) {
        StripeObject stripeObject = deserializar(evento);
        if (!(stripeObject instanceof Subscription subscription)) {
            return;
        }
        // customer.subscription.deleted siempre significa CANCELADA -- no pasa por
        // calcularEstadoTrasActualizacion (ese mapa es para customer.subscription.updated).
        aplicarEstadoSiNoObsoleto(subscription.getId(), evento.getCreated(), EstadoSuscripcion.CANCELADA);
    }

    private StripeObject deserializar(Event evento) {
        EventDataObjectDeserializer deserializer = evento.getDataObjectDeserializer();
        Optional<StripeObject> stripeObject = deserializer.getObject();
        if (stripeObject.isEmpty()) {
            log.warn("No se pudo deserializar el objeto del evento {} (tipo {}); posible mismatch de "
                    + "version de API entre el evento y el SDK", evento.getId(), evento.getType());
            return null;
        }
        return stripeObject.get();
    }

    private String extraerPreviousStatus(Event evento) {
        Map<String, Object> previousAttributes = evento.getData().getPreviousAttributes();
        if (previousAttributes == null) {
            return null;
        }
        Object status = previousAttributes.get("status");
        return status == null ? null : status.toString();
    }

    // ==================== Logica pura/testable (sin tipos del SDK) ====================

    void procesarCheckoutCompletado(Long gestoriaId, String customerId, String subscriptionId, long eventCreated) {
        Suscripcion suscripcion = suscripcionService.obtenerOCrearSuscripcion(gestoriaId);
        if (esEventoObsoleto(eventCreated, suscripcion)) {
            log.info("checkout.session.completed obsoleto para gestoria {}, se descarta", gestoriaId);
            return;
        }
        EstadoSuscripcion estadoAntes = suscripcion.getEstado();
        suscripcion.setStripeCustomerId(customerId);
        suscripcion.setStripeSubscriptionId(subscriptionId);
        suscripcion.setEstado(EstadoSuscripcion.TRIAL);
        suscripcion.setStripeUltimoEventoEpoch(eventCreated);
        suscripcionRepository.save(suscripcion);
        auditarCambioEstado(suscripcion, estadoAntes);
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
        EstadoSuscripcion estadoAntes = suscripcion.getEstado();
        EstadoSuscripcion nuevoEstado = suscripcion.getEstado() == EstadoSuscripcion.TRIAL
                ? EstadoSuscripcion.TRIAL_EXPIRADO_SIN_PAGO
                : EstadoSuscripcion.IMPAGO_GRACIA;
        suscripcion.setEstado(nuevoEstado);
        suscripcion.setStripeUltimoEventoEpoch(eventCreated);
        suscripcionRepository.save(suscripcion);
        auditarCambioEstado(suscripcion, estadoAntes);
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
        EstadoSuscripcion estadoAntes = suscripcion.getEstado();
        EstadoSuscripcion nuevoEstado = calcularEstadoTrasActualizacion(status, previousStatus, suscripcion.getEstado());
        if (nuevoEstado == null) {
            return;
        }
        suscripcion.setEstado(nuevoEstado);
        suscripcion.setStripeUltimoEventoEpoch(eventCreated);
        suscripcionRepository.save(suscripcion);
        auditarCambioEstado(suscripcion, estadoAntes);
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
        EstadoSuscripcion estadoAntes = suscripcion.getEstado();
        suscripcion.setEstado(nuevoEstado);
        suscripcion.setStripeUltimoEventoEpoch(eventCreated);
        suscripcionRepository.save(suscripcion);
        auditarCambioEstado(suscripcion, estadoAntes);
    }

    /**
     * Una fila SUSCRIPCION_ESTADO_CAMBIADO (actor WEBHOOK_STRIPE, gestoria de la propia suscripcion) solo si el
     * estado ha cambiado de verdad: un evento que vuelve a fijar el mismo estado (p. ej. un checkout que confirma
     * un TRIAL ya en TRIAL) no deja fila. El guard de epoch y las subscriptions desconocidas ya han salido antes.
     * antes/despues solo llevan codigos y un recuento (D8): nunca ids de Stripe.
     */
    private void auditarCambioEstado(Suscripcion suscripcion, EstadoSuscripcion estadoAntes) {
        if (estadoAntes == suscripcion.getEstado()) {
            return;
        }
        auditoriaService.registrar(AccionAuditoria.SUSCRIPCION_ESTADO_CAMBIADO, suscripcion.getGestoria().getId(),
                Actor.webhookStripe(), EntidadAuditada.SUSCRIPCION, suscripcion.getId(),
                DatosAuditoria.con(Clave.ESTADO, estadoAntes),
                DatosAuditoria.con(Clave.ESTADO, suscripcion.getEstado())
                        .y(Clave.EXPLOTACIONES_CONTRATADAS, suscripcion.getExplotacionesContratadas()));
    }

    /**
     * Mapa de customer.subscription.updated -> EstadoSuscripcion (Decision de diseño 4).
     * null significa "no cambiar nada" (status desconocido/no mapeado).
     */
    static EstadoSuscripcion calcularEstadoTrasActualizacion(String status, String previousStatus,
                                                              EstadoSuscripcion estadoActual) {
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
     * Guard monotonico contra re-entrega tardia / desorden de webhooks (Decision de
     * diseño 4): eventCreated estrictamente menor que lo ya aplicado -> obsoleto.
     * Empates de epoch-second (igual) SE aplican (< estricto, no <=) -- limitacion
     * conocida y aceptada, no un bug: dos eventos distintos en el mismo epoch-second
     * pueden aplicarse en cualquier orden. Sin stripeUltimoEventoEpoch almacenado
     * (primer evento) nunca es obsoleto.
     */
    static boolean esEventoObsoleto(Long eventCreated, Suscripcion suscripcion) {
        Long ultimoEpoch = suscripcion.getStripeUltimoEventoEpoch();
        if (ultimoEpoch == null) {
            return false;
        }
        return eventCreated < ultimoEpoch;
    }
}
