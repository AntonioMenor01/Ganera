package com.ganera.core.registro;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RangoClientesTest {

    @Test
    void unoADiezDaDosExplotaciones() {
        assertThat(RangoClientes.UNO_A_DIEZ.quantityExplotacionesEstimada()).isEqualTo(2);
    }

    @Test
    void onceATreintaDaQuinceExplotaciones() {
        assertThat(RangoClientes.ONCE_A_TREINTA.quantityExplotacionesEstimada()).isEqualTo(15);
    }

    @Test
    void treintaUnoASetentaYCincoDaCuarentaYUnaExplotaciones() {
        assertThat(RangoClientes.TREINTA_UNO_A_SETENTA_Y_CINCO.quantityExplotacionesEstimada()).isEqualTo(41);
    }

    @Test
    void setentaYSeisOMasDaNoventaYNueveExplotaciones() {
        assertThat(RangoClientes.SETENTA_Y_SEIS_O_MAS.quantityExplotacionesEstimada()).isEqualTo(99);
    }
}
