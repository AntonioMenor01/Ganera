package com.ganera.core.facturacion;

import com.ganera.core.explotacion.ExplotacionRepository;
import com.ganera.core.shared.auditoria.AccionAuditoria;
import com.ganera.core.shared.auditoria.Actor;
import com.ganera.core.shared.auditoria.AuditoriaService;
import com.ganera.core.shared.auditoria.Clave;
import com.ganera.core.shared.auditoria.DatosAuditoria;
import com.ganera.core.shared.auditoria.EntidadAuditada;
import com.stripe.exception.StripeException;
import com.stripe.model.Subscription;
import com.stripe.param.SubscriptionUpdateParams;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.function.Function;

/**
 * Reconciliacion nocturna de `explotacionesContratadas` contra Stripe (Decision de diseño 7,
 * docs/superpowers/plans/2026-07-09-prompt2.7-stripe.md): en vez de empujar un cambio de
 * quantity a Stripe en cada alta/baja de Explotacion (acopla el CRUD de Explotaciones a Stripe
 * y multiplica llamadas), se reconcilia una vez al dia a las 03:00 Europe/Madrid.
 *
 * Este job corre fuera de cualquier request HTTP, asi que el gestoriaFilter de Hibernate
 * (activado por TenantFilterActivationInterceptor a partir del JWT) nunca esta activo aqui.
 * Sus queries (findByEstadoIn, countByGestoriaId) ven filas de todas las Gestorias a proposito
 * -- es un job cross-tenant por diseño, no una fuga de aislamiento multi-tenant.
 */
@Slf4j
@Component
class SuscripcionSyncScheduler {

    private final SuscripcionRepository suscripcionRepository;
    private final ExplotacionRepository explotacionRepository;
    private final AuditoriaService auditoriaService;
    private final TransactionTemplate transaccion;
    private final String apiKey;

    SuscripcionSyncScheduler(SuscripcionRepository suscripcionRepository,
                              ExplotacionRepository explotacionRepository,
                              AuditoriaService auditoriaService,
                              PlatformTransactionManager transactionManager,
                              @Value("${stripe.api-key:}") String apiKey) {
        this.suscripcionRepository = suscripcionRepository;
        this.explotacionRepository = explotacionRepository;
        this.auditoriaService = auditoriaService;
        this.transaccion = new TransactionTemplate(transactionManager);
        this.apiKey = apiKey;
    }

    @Scheduled(cron = "0 0 3 * * *", zone = "Europe/Madrid")
    void reconciliarCantidadesConStripe() {
        if (apiKey.isBlank()) {
            log.info("Stripe no configurado (stripe.api-key en blanco); se omite la reconciliacion nocturna");
            return;
        }

        List<Suscripcion> suscripcionesActivas = suscripcionRepository.findByEstadoIn(
                List.of(EstadoSuscripcion.ACTIVA, EstadoSuscripcion.IMPAGO_GRACIA));
        List<Ajuste> ajustes = calcularAjustes(suscripcionesActivas, explotacionRepository::countByGestoriaId);

        for (Ajuste ajuste : ajustes) {
            // Un fallo en una Suscripcion (red, credenciales, subscription borrada en Stripe...)
            // no debe abortar la reconciliacion del resto del batch: se loguea y se continua.
            try {
                pushCantidadAStripe(ajuste.suscripcion().getStripeSubscriptionId(), ajuste.nuevaCantidad());
                // El save y su fila de auditoria van en UNA transaccion por suscripcion (registrar es MANDATORY),
                // por eso el push a Stripe (red) queda fuera. Un fallo de una no para las demas (el catch de abajo).
                // Actor PLANIFICADOR, sin peticion -> ip/request_id NULL; gestoria = la de la propia suscripcion.
                Suscripcion suscripcion = ajuste.suscripcion();
                Integer cantidadAntes = suscripcion.getExplotacionesContratadas();
                transaccion.executeWithoutResult(estado -> {
                    suscripcion.setExplotacionesContratadas((int) ajuste.nuevaCantidad());
                    suscripcionRepository.save(suscripcion);
                    auditoriaService.registrar(AccionAuditoria.SUSCRIPCION_CANTIDAD_SINCRONIZADA,
                            suscripcion.getGestoria().getId(), Actor.planificador(), EntidadAuditada.SUSCRIPCION,
                            suscripcion.getId(),
                            DatosAuditoria.con(Clave.EXPLOTACIONES_CONTRATADAS, cantidadAntes),
                            DatosAuditoria.con(Clave.EXPLOTACIONES_CONTRATADAS, (int) ajuste.nuevaCantidad()));
                });
            } catch (Exception e) {
                log.error("Fallo reconciliando quantity en Stripe para subscription {}",
                        ajuste.suscripcion().getStripeSubscriptionId(), e);
            }
        }
    }

    /**
     * Logica pura/testable (convencion test-sin-mocks-externos): dada la lista de Suscripciones
     * candidatas y una funcion gestoriaId -> count real de Explotaciones, devuelve solo los
     * ajustes necesarios (cantidad nueva != explotacionesContratadas almacenada), con
     * max(1, count) porque Stripe exige quantity >= 1. Ignora suscripciones sin
     * stripeSubscriptionId (nunca llegaron a completar un checkout) y las de PLAN FIJO
     * (plan != null, altas de la landing): esas cobran un precio fijo por plan, no por numero de
     * explotaciones, asi que empujarles quantity=nº de explotaciones multiplicaria el cobro.
     */
    static List<Ajuste> calcularAjustes(List<Suscripcion> suscripciones, Function<Long, Long> contadorExplotaciones) {
        return suscripciones.stream()
                .filter(s -> s.getStripeSubscriptionId() != null)
                .filter(s -> s.getPlan() == null)
                .map(s -> new Ajuste(s, Math.max(1L, contadorExplotaciones.apply(s.getGestoria().getId()))))
                .filter(a -> a.suscripcion().getExplotacionesContratadas() == null
                        || a.suscripcion().getExplotacionesContratadas() != a.nuevaCantidad())
                .toList();
    }

    // Unica pieza que toca la red de Stripe -- sin cobertura unitaria directa (convencion
    // test-sin-mocks-externos); se valida en el smoke test manual con clave test.
    void pushCantidadAStripe(String stripeSubscriptionId, long nuevaCantidad) throws StripeException {
        Subscription subscription = Subscription.retrieve(stripeSubscriptionId);
        String itemId = subscription.getItems().getData().get(0).getId();
        SubscriptionUpdateParams params = SubscriptionUpdateParams.builder()
                .addItem(SubscriptionUpdateParams.Item.builder()
                        .setId(itemId)
                        .setQuantity(nuevaCantidad)
                        .build())
                // Sin setProrationBehavior: se deja el default de Stripe (create_prorations),
                // pedido explicitamente por el plan ("proracion por defecto").
                .build();
        subscription.update(params);
    }

    record Ajuste(Suscripcion suscripcion, long nuevaCantidad) {
    }
}
