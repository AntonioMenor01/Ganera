package com.ganera.core.facturacion;

import com.ganera.core.explotacion.Explotacion;
import com.ganera.core.explotacion.ExplotacionRepository;
import com.ganera.core.ganadero.Ganadero;
import com.ganera.core.ganadero.GanaderoRepository;
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
    private GanaderoRepository ganaderoRepository;
    @Autowired
    private ExplotacionRepository explotacionRepository;
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

    @Test
    void obtenerOCrearSuscripcionSinFilaPreviaCreaUnaEnTrial() {
        Gestoria gestoria = gestoriaRepository.save(new Gestoria("Gestoria sin fila previa"));

        Suscripcion suscripcion = suscripcionService.obtenerOCrearSuscripcion(gestoria.getId());

        assertThat(suscripcion.getId()).isNotNull();
        assertThat(suscripcion.getEstado()).isEqualTo(EstadoSuscripcion.TRIAL);
        assertThat(suscripcionRepository.findByGestoriaId(gestoria.getId()))
                .hasValueSatisfying(guardada -> assertThat(guardada.getId()).isEqualTo(suscripcion.getId()));
    }

    @Test
    void obtenerOCrearSuscripcionConFilaPreviaDevuelveLaExistenteSinCambiarEstado() {
        Gestoria gestoria = gestoriaRepository.save(new Gestoria("Gestoria con fila previa"));
        Suscripcion existente = new Suscripcion();
        existente.setGestoria(gestoria);
        existente.setEstado(EstadoSuscripcion.SUSPENDIDA);
        suscripcionRepository.save(existente);

        Suscripcion devuelta = suscripcionService.obtenerOCrearSuscripcion(gestoria.getId());

        assertThat(devuelta.getId()).isEqualTo(existente.getId());
        assertThat(devuelta.getEstado()).isEqualTo(EstadoSuscripcion.SUSPENDIDA);
    }

    @Test
    void obtenerOCrearSuscripcionEsIdempotenteEnLlamadaDoble() {
        Gestoria gestoria = gestoriaRepository.save(new Gestoria("Gestoria doble llamada"));

        Suscripcion primera = suscripcionService.obtenerOCrearSuscripcion(gestoria.getId());
        Suscripcion segunda = suscripcionService.obtenerOCrearSuscripcion(gestoria.getId());

        assertThat(segunda.getId()).isEqualTo(primera.getId());
        assertThat(suscripcionRepository.findAll())
                .filteredOn(s -> s.getGestoria().getId().equals(gestoria.getId()))
                .hasSize(1);
    }

    @Test
    void countByGestoriaIdCuentaSoloLasExplotacionesDeEsaGestoria() {
        Gestoria gestoriaA = gestoriaRepository.save(new Gestoria("Gestoria con dos explotaciones"));
        Gestoria gestoriaB = gestoriaRepository.save(new Gestoria("Gestoria con una explotacion"));

        Ganadero ganaderoA = nuevoGanadero(gestoriaA, "Ganadero A");
        Ganadero ganaderoB = nuevoGanadero(gestoriaB, "Ganadero B");

        nuevaExplotacion(gestoriaA, ganaderoA, "ES010000000001", "Finca A1");
        nuevaExplotacion(gestoriaA, ganaderoA, "ES010000000002", "Finca A2");
        nuevaExplotacion(gestoriaB, ganaderoB, "ES020000000001", "Finca B1");

        assertThat(explotacionRepository.countByGestoriaId(gestoriaA.getId())).isEqualTo(2);
        assertThat(explotacionRepository.countByGestoriaId(gestoriaB.getId())).isEqualTo(1);
    }

    private Ganadero nuevoGanadero(Gestoria gestoria, String nombre) {
        Ganadero ganadero = new Ganadero();
        ganadero.setGestoria(gestoria);
        ganadero.setNombre(nombre);
        return ganaderoRepository.save(ganadero);
    }

    private Explotacion nuevaExplotacion(Gestoria gestoria, Ganadero ganadero, String codigoRega, String nombre) {
        Explotacion explotacion = new Explotacion();
        explotacion.setGestoria(gestoria);
        explotacion.setGanadero(ganadero);
        explotacion.setCodigoRega(codigoRega);
        explotacion.setNombre(nombre);
        return explotacionRepository.save(explotacion);
    }
}
