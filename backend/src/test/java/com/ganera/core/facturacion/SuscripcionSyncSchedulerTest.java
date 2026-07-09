package com.ganera.core.facturacion;

import com.ganera.core.gestoria.Gestoria;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Segun la convencion test-sin-mocks-externos: SuscripcionSyncScheduler.calcularAjustes es la
 * logica de negocio pura (no toca tipos del SDK de Stripe, ni siquiera Subscription.retrieve),
 * testeada aqui sin ningun mock. El push real a Stripe (pushCantidadAStripe) queda sin cobertura
 * unitaria directa -- se valida en el smoke test (Task 4).
 */
class SuscripcionSyncSchedulerTest {

    @Test
    void ignoraSuscripcionesSinStripeSubscriptionId() {
        Suscripcion sinStripe = suscripcion(1L, null, 5);

        List<SuscripcionSyncScheduler.Ajuste> ajustes =
                SuscripcionSyncScheduler.calcularAjustes(List.of(sinStripe), contador(Map.of(1L, 9L)));

        assertThat(ajustes).isEmpty();
    }

    @Test
    void noIncluyeSuscripcionCuyaCantidadYaCoincideConElCountReal() {
        Suscripcion alDia = suscripcion(1L, "sub_1", 3);

        List<SuscripcionSyncScheduler.Ajuste> ajustes =
                SuscripcionSyncScheduler.calcularAjustes(List.of(alDia), contador(Map.of(1L, 3L)));

        assertThat(ajustes).isEmpty();
    }

    @Test
    void incluyeSuscripcionCuyaCantidadDifiereDelCountRealConLaNuevaCantidad() {
        Suscripcion desactualizada = suscripcion(1L, "sub_1", 3);

        List<SuscripcionSyncScheduler.Ajuste> ajustes =
                SuscripcionSyncScheduler.calcularAjustes(List.of(desactualizada), contador(Map.of(1L, 7L)));

        assertThat(ajustes).hasSize(1);
        assertThat(ajustes.get(0).suscripcion()).isSameAs(desactualizada);
        assertThat(ajustes.get(0).nuevaCantidad()).isEqualTo(7L);
    }

    @Test
    void aplicaMaxUnoCuandoElCountRealEsCero() {
        Suscripcion sinExplotaciones = suscripcion(1L, "sub_1", null);

        List<SuscripcionSyncScheduler.Ajuste> ajustes =
                SuscripcionSyncScheduler.calcularAjustes(List.of(sinExplotaciones), contador(Map.of(1L, 0L)));

        assertThat(ajustes).hasSize(1);
        assertThat(ajustes.get(0).nuevaCantidad()).isEqualTo(1L);
    }

    @Test
    void noAjustaSiElContratadoYaEsUnoYElCountRealEsCero() {
        Suscripcion yaEnUno = suscripcion(1L, "sub_1", 1);

        List<SuscripcionSyncScheduler.Ajuste> ajustes =
                SuscripcionSyncScheduler.calcularAjustes(List.of(yaEnUno), contador(Map.of(1L, 0L)));

        assertThat(ajustes).isEmpty();
    }

    @Test
    void devuelveSoloLasSuscripcionesQueRealmenteCambianEntreVarias() {
        Suscripcion sinStripe = suscripcion(1L, null, 5);
        Suscripcion alDia = suscripcion(2L, "sub_2", 4);
        Suscripcion desactualizada = suscripcion(3L, "sub_3", 2);

        List<SuscripcionSyncScheduler.Ajuste> ajustes = SuscripcionSyncScheduler.calcularAjustes(
                List.of(sinStripe, alDia, desactualizada),
                contador(Map.of(1L, 100L, 2L, 4L, 3L, 6L)));

        assertThat(ajustes).hasSize(1);
        assertThat(ajustes.get(0).suscripcion()).isSameAs(desactualizada);
        assertThat(ajustes.get(0).nuevaCantidad()).isEqualTo(6L);
    }

    private static java.util.function.Function<Long, Long> contador(Map<Long, Long> counts) {
        return counts::get;
    }

    private static Suscripcion suscripcion(long gestoriaId, String stripeSubscriptionId, Integer explotacionesContratadas) {
        Gestoria gestoria = new Gestoria("Gestoria " + gestoriaId);
        gestoria.setId(gestoriaId);
        Suscripcion suscripcion = new Suscripcion();
        suscripcion.setGestoria(gestoria);
        suscripcion.setEstado(EstadoSuscripcion.ACTIVA);
        suscripcion.setStripeSubscriptionId(stripeSubscriptionId);
        suscripcion.setExplotacionesContratadas(explotacionesContratadas);
        return suscripcion;
    }
}
