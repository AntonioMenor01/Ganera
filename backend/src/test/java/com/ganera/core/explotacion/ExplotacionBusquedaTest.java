package com.ganera.core.explotacion;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** La columna explotacion.busqueda la mantienen los setters de codigoRega y nombre. */
class ExplotacionBusquedaTest {

    @Test
    void setNombreYSetCodigoRegaRecalculanLaBusqueda() {
        Explotacion explotacion = new Explotacion();
        explotacion.setCodigoRega("ES120000000001");
        assertThat(explotacion.getBusqueda()).isEqualTo("es120000000001");

        explotacion.setNombre("Finca La Peña");
        assertThat(explotacion.getBusqueda()).isEqualTo("es120000000001 finca la pena");

        explotacion.setCodigoRega("ES-34 0000");
        assertThat(explotacion.getBusqueda()).isEqualTo("es34 0000 finca la pena");

        explotacion.setNombre("Martín-Pérez");
        assertThat(explotacion.getBusqueda()).isEqualTo("es34 0000 martinperez");
    }

    @Test
    void elOrdenDeLosSettersNoImporta() {
        Explotacion explotacion = new Explotacion();
        explotacion.setNombre("Finca La Peña");
        assertThat(explotacion.getBusqueda()).isEqualTo("finca la pena");
        explotacion.setCodigoRega("ES120000000001");
        assertThat(explotacion.getBusqueda()).isEqualTo("es120000000001 finca la pena");
    }

    @Test
    void laColumnaDeBusquedaNoTieneSetterPublico() {
        assertThat(java.util.Arrays.stream(Explotacion.class.getMethods()).map(java.lang.reflect.Method::getName))
                .doesNotContain("setBusqueda");
    }
}
