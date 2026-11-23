package com.ganera.core.alta;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Dispara el alta automatica de cuentas desde los pagos de la landing (rama feat/alta-automatica), al estilo
 * de {@code SuscripcionSyncScheduler}: cada {@code ganera.alta.automatica.intervalo} (30 s por defecto) procesa
 * las {@code alta_pendiente} en PENDIENTE con {@link AltaAutomaticaService#procesarPendientes()}.
 *
 * <p><b>Todo detras del flag {@code ganera.alta.automatica.activa} (default false):</b> con el flag apagado el
 * planificador NO hace nada (vuelve sin tocar la base de datos), asi que fusionar esta rama no cambia el
 * comportamiento actual — el alta sigue siendo manual (POST /internal/onboarding/gestoria) hasta que alguien
 * encienda el flag por configuracion. El guard va dentro del metodo (como {@code apiKey.isBlank()} en
 * SuscripcionSyncScheduler) para que «flag apagado = no hace nada» sea testable directamente.
 *
 * <p>Job cross-tenant sin peticion HTTP, como los demas planificadores: el gestoriaFilter nunca esta activo
 * aqui; el servicio trabaja con los ids explicitos de cada fila.
 */
// Solo en el backend web (como AltaAutomaticaService): el worker de OVZ no hace alta automatica ni tiene las
// variables del backend. Igual que SecurityConfig/RegistroConfig con @ConditionalOnWebApplication.
@Slf4j
@Component
@ConditionalOnWebApplication(type = Type.SERVLET)
class AltaAutomaticaScheduler {

    private final AltaAutomaticaService servicio;
    private final boolean activa;

    AltaAutomaticaScheduler(AltaAutomaticaService servicio,
                            @Value("${ganera.alta.automatica.activa:false}") boolean activa) {
        this.servicio = servicio;
        this.activa = activa;
    }

    @Scheduled(fixedDelayString = "${ganera.alta.automatica.intervalo:PT30S}")
    void procesarAltasPendientes() {
        if (!activa) {
            return; // flag apagado (por defecto): no hace nada.
        }
        try {
            servicio.procesarPendientes();
        } catch (RuntimeException e) {
            // Solo el tipo: el mensaje podria arrastrar datos de la alta.
            log.error("Fallo en la pasada del planificador de alta automatica: excepcion={}", e.getClass().getName());
        }
    }
}
