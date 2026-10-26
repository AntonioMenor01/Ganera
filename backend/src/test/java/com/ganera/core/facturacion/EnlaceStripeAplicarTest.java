package com.ganera.core.facturacion;

import com.ganera.core.facturacion.EnlaceStripeService.DatosSubscription;
import com.ganera.core.gestoria.Gestoria;
import com.ganera.core.gestoria.GestoriaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

/**
 * Enlazar una Subscription sin metadata ni alta_pendiente deja un plan NO nulo ("desconocido"), de
 * modo que SuscripcionSyncScheduler SIEMPRE la excluye de la reconciliacion de quantity (no se le
 * puede multiplicar el cobro por numero de explotaciones). Se prueba `aplicar` (sin el SDK) con el
 * repositorio real; recuperarSubscription (Subscription.retrieve) queda sin cobertura unitaria directa.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
class EnlaceStripeAplicarTest {

    @Autowired
    private AltaPendienteRepository altaPendienteRepository;
    @Autowired
    private GestoriaRepository gestoriaRepository;

    @Test
    void enlazarSinMetadataNiAltaPendienteDejaPlanNoNuloYElSchedulerLaExcluye() {
        EnlaceStripeService servicio = new EnlaceStripeService(altaPendienteRepository, "", "");
        Gestoria gestoria = gestoriaRepository.save(new Gestoria("Gestoria sin plan"));
        Suscripcion suscripcion = new Suscripcion();
        suscripcion.setGestoria(gestoria);
        suscripcion.setEstado(EstadoSuscripcion.TRIAL);

        // Subscription activa, sin metadata de plan/periodo y sin price en el mapa (mapa vacio).
        servicio.aplicar(suscripcion, "sub_sin_plan",
                new DatosSubscription("active", "cus_1", null, null, null));

        assertThat(suscripcion.getPlan()).isNotNull().isEqualTo("desconocido");
        assertThat(suscripcion.getStripeSubscriptionId()).isEqualTo("sub_sin_plan");
        assertThat(suscripcion.getEstado()).isEqualTo(EstadoSuscripcion.ACTIVA);

        // Con plan != null, calcularAjustes NUNCA la reconcilia (aunque la cantidad difiera).
        List<SuscripcionSyncScheduler.Ajuste> ajustes = SuscripcionSyncScheduler.calcularAjustes(
                List.of(suscripcion), gestoriaId -> 99L);
        assertThat(ajustes).isEmpty();
    }

    @Test
    void enlazarUsaElMapaDePreciosCuandoNoHayMetadataNiAlta() {
        EnlaceStripeService servicio = new EnlaceStripeService(
                altaPendienteRepository, "", "price_fijo=gestion:anual");
        Gestoria gestoria = gestoriaRepository.save(new Gestoria("Gestoria por price"));
        Suscripcion suscripcion = new Suscripcion();
        suscripcion.setGestoria(gestoria);
        suscripcion.setEstado(EstadoSuscripcion.TRIAL);

        servicio.aplicar(suscripcion, "sub_price",
                new DatosSubscription("active", "cus_2", null, null, "price_fijo"));

        assertThat(suscripcion.getPlan()).isEqualTo("gestion");
        assertThat(suscripcion.getPeriodo()).isEqualTo("anual");
    }
}
