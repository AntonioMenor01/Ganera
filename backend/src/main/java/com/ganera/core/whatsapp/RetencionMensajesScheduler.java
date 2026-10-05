package com.ganera.core.whatsapp;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Job nocturno de retencion de mensajes de WhatsApp (B1, T3, D6): cada noche a las 03:30 de Madrid
 * por defecto ({@code ganera.retencion.cron}), despues de la reconciliacion de Stripe de las 03:00.
 * La logica vive en {@link RetencionMensajesService}.
 *
 * <p>Job cross-tenant sin peticion HTTP, como SuscripcionSyncScheduler: mensaje_campo no es
 * tenant-scoped y el gestoriaFilter nunca esta activo aqui, a proposito; no es una fuga de
 * aislamiento. Se desactiva con {@code ganera.retencion.activo=false} (asi estan los tests).
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "ganera.retencion.activo", havingValue = "true", matchIfMissing = true)
class RetencionMensajesScheduler {

    private final RetencionMensajesService servicio;

    RetencionMensajesScheduler(RetencionMensajesService servicio) {
        this.servicio = servicio;
    }

    @Scheduled(cron = "${ganera.retencion.cron:0 30 3 * * *}", zone = "Europe/Madrid")
    void aplicarRetencion() {
        try {
            servicio.aplicar();
        } catch (RuntimeException e) {
            // El servicio ya captura sus fallos; esto es solo una red. Solo el tipo, nunca el mensaje.
            log.error("Fallo en la pasada de retencion de mensajes: excepcion={}", e.getClass().getName());
        }
    }
}
