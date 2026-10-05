package com.ganera.core.tramite;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Planificador de la extraccion por IA (B1, T2): cada {@code ganera.extraccion.intervalo} (5 s por
 * defecto) procesa un lote de la cola con {@link ExtraccionTramiteService#procesarPendientes()}.
 *
 * <p>Job cross-tenant sin peticion HTTP, como SuscripcionSyncScheduler: el gestoriaFilter nunca esta
 * activo aqui, a proposito; el servicio usa el gestoriaId explicito de cada Tramite.
 *
 * <p>{@code fixedDelay} (no fixedRate): la siguiente pasada empieza cuando termina la anterior, asi
 * que dentro de una instancia nunca se solapan. Con varias instancias si podrian (limite conocido,
 * ver el Javadoc del servicio). Se desactiva con {@code ganera.extraccion.planificador.activo=false}
 * (asi estan los tests, que llaman al servicio cuando quieren).
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "ganera.extraccion.planificador.activo", havingValue = "true", matchIfMissing = true)
class ExtraccionTramiteScheduler {

    private final ExtraccionTramiteService servicio;

    ExtraccionTramiteScheduler(ExtraccionTramiteService servicio) {
        this.servicio = servicio;
    }

    @Scheduled(fixedDelayString = "${ganera.extraccion.intervalo:PT5S}")
    void procesarPendientes() {
        try {
            servicio.procesarPendientes();
        } catch (RuntimeException e) {
            // Solo el tipo: el mensaje de la excepcion podria llevar datos del mensaje.
            log.error("Fallo en la pasada del planificador de extraccion: excepcion={}", e.getClass().getName());
        }
    }
}
