package com.ganera.core.ganadero;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** La columna ganadero.nombre_busqueda la mantiene setNombre. */
class GanaderoBusquedaTest {

    @Test
    void setNombreRecalculaElNombreDeBusqueda() {
        Ganadero ganadero = new Ganadero();
        ganadero.setNombre("Martínez");
        assertThat(ganadero.getNombreBusqueda()).isEqualTo("martinez");

        ganadero.setNombre("José Peña-García");
        assertThat(ganadero.getNombreBusqueda()).isEqualTo("jose penagarcia");
    }

    @Test
    void laColumnaDeBusquedaNoTieneSetterPublico() {
        assertThat(java.util.Arrays.stream(Ganadero.class.getMethods()).map(java.lang.reflect.Method::getName))
                .doesNotContain("setNombreBusqueda");
    }
}
