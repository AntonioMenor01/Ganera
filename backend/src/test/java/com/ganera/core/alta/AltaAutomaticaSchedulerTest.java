package com.ganera.core.alta;

import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * El planificador del alta automatica solo actua con el flag encendido. Apagado (el valor por defecto) no toca
 * el servicio: fusionar la rama no cambia el comportamiento actual.
 */
class AltaAutomaticaSchedulerTest {

    @Test
    void conElFlagApagadoNoHaceNada() {
        AltaAutomaticaService servicio = mock(AltaAutomaticaService.class);
        AltaAutomaticaScheduler scheduler = new AltaAutomaticaScheduler(servicio, false);

        scheduler.procesarAltasPendientes();

        verifyNoInteractions(servicio);
    }

    @Test
    void conElFlagEncendidoProcesaLasAltasPendientes() {
        AltaAutomaticaService servicio = mock(AltaAutomaticaService.class);
        when(servicio.procesarPendientes()).thenReturn(java.util.List.of());
        AltaAutomaticaScheduler scheduler = new AltaAutomaticaScheduler(servicio, true);

        scheduler.procesarAltasPendientes();

        verify(servicio).procesarPendientes();
    }
}
