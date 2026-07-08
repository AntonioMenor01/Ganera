package com.ganera.core.facturacion;

import com.ganera.core.gestoria.Gestoria;
import com.ganera.core.gestoria.GestoriaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
@Import(SuscripcionService.class)
class SuscripcionServiceTest {

    private static final Set<EstadoSuscripcion> ESTADOS_QUE_BLOQUEAN =
            EnumSet.of(EstadoSuscripcion.TRIAL_EXPIRADO_SIN_PAGO, EstadoSuscripcion.SUSPENDIDA);

    @Autowired
    private GestoriaRepository gestoriaRepository;
    @Autowired
    private SuscripcionRepository suscripcionRepository;
    @Autowired
    private SuscripcionService suscripcionService;

    @ParameterizedTest
    @EnumSource(EstadoSuscripcion.class)
    void puedeAprobarTramitesSegunElEstado(EstadoSuscripcion estado) {
        Gestoria gestoria = gestoriaRepository.save(new Gestoria("Gestoria de prueba " + estado));

        Suscripcion suscripcion = new Suscripcion();
        suscripcion.setGestoria(gestoria);
        suscripcion.setEstado(estado);
        suscripcionRepository.save(suscripcion);

        boolean esperado = !ESTADOS_QUE_BLOQUEAN.contains(estado);

        assertThat(suscripcionService.puedeAprobarTramites(gestoria.getId())).isEqualTo(esperado);
    }

    @Test
    void sinSuscripcionAsociadaNoPuedeAprobarTramitesFailClosed() {
        Gestoria gestoria = gestoriaRepository.save(new Gestoria("Gestoria sin suscripcion"));

        assertThat(suscripcionService.puedeAprobarTramites(gestoria.getId())).isFalse();
    }
}
