package com.ganera.core.ovz;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Planificador del worker de OVZ (T4): cada {@code ganera.ovz.worker.intervalo} (10 s por defecto) procesa UN trabajo.
 * Solo existe en el perfil {@code worker}. {@code fixedDelay}: la siguiente pasada empieza cuando termina la anterior,
 * así que dentro de una instancia nunca se solapan; el worker es de una sola instancia (candado global, D3).
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "ganera.ovz.worker.activo", havingValue = "true")
class WorkerOvzScheduler {

    private final WorkerOvzService workerOvzService;

    WorkerOvzScheduler(WorkerOvzService workerOvzService) {
        this.workerOvzService = workerOvzService;
    }

    @Scheduled(fixedDelayString = "${ganera.ovz.worker.intervalo:PT10S}")
    void procesarPendientes() {
        try {
            workerOvzService.procesarUno();
        } catch (RuntimeException e) {
            // Solo el tipo: el mensaje podría llevar datos.
            log.error("Fallo en la pasada del worker de OVZ: excepcion={}", e.getClass().getName());
        }
    }
}
